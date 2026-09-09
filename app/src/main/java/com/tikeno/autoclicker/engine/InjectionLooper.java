package com.tikeno.autoclicker.engine;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.MessageQueue;
import android.system.ErrnoException;

import com.tikeno.autoclicker.util.Logx;
import com.tikeno.autoclicker.util.Threadx;

/**
 * InjectionLooper — tikeno.inject 线程（架构 §2.4.4 #70 / §6.2 并行段）。
 *
 * 机制：HandlerThread + MessageQueue.addOnFileDescriptorEventListener(outFd,
 * EVENT_INPUT) —— C++ 桥接档写 outEfd（eventfd）→ 本线程被唤醒 →
 * drain eventfd 计数 → OutputRing.drain 批量消费 TkStep → 聚合为手势派发。
 *
 * 【线程归属】本类全部公开方法线程安全；消费回调固定运行在
 * tikeno.inject 线程（架构分层不变式：注入回调不占主线程）。
 */
public final class InjectionLooper {

    private static final String TAG = "Tikeno/Looper";

    private final OutputRing ring;
    private final EventFdBridge outEfd;
    private final InjectionDispatcher dispatcher;
    private final InjectionEngine.InjectionTierHolder tierHolder = () -> 3;

    private HandlerThread thread;
    private Handler handler;
    private volatile boolean running;

    // —— 高频派发门控：dispatchGesture 会取消在途手势，必须等上次完成 ——
    private static final long GESTURE_WAIT_MS = 5L;
    private static final int GESTURE_MAX_RETRIES = 12;

    public InjectionLooper(OutputRing ring, EventFdBridge outEfd, InjectionDispatcher dispatcher) {
        this.ring = ring;
        this.outEfd = outEfd;
        this.dispatcher = dispatcher;
    }

    /** 启动线程并注册 fd 监听（幂等） */
    public synchronized void start() {
        if (running) {
            return;
        }
        thread = Threadx.newNamedHandlerThread("tikeno.inject",
                android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
        handler = new Handler(thread.getLooper());
        running = true;
        handler.post(this::registerFdListener);
        Logx.i(TAG, "tikeno.inject 线程已启动");
    }

    /** 停止（注销 fd 监听、退出线程；幂等） */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (handler != null) {
            handler.post(() -> {
                try {
                    LooperHolder.myQueue().removeOnFileDescriptorEventListener(fdListener);
                } catch (Exception e) {
                    Logx.w(TAG, "fd 监听注销异常", e);
                }
            });
            handler.removeCallbacksAndMessages(null);
            handler.post(() -> { /* 允许注销任务先执行 */ });
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        handler = null;
        Logx.i(TAG, "tikeno.inject 线程已停止");
    }

    private final MessageQueue.OnFileDescriptorEventListener fdListener =
            (fd, events) -> {
                // eventfd 可读：消费计数并批量取步（非阻塞，必须快速返回）
                outEfd.drain();
                processSteps();
                return MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT;
            };

    private void registerFdListener() {
        try {
            LooperHolder.myQueue().addOnFileDescriptorEventListener(
                    outEfd.fd(),
                    MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT,
                    fdListener);
        } catch (ErrnoException | IllegalStateException e) {
            Logx.e(TAG, "fd 监听注册失败", e);
        }
    }

    /** 批量消费 outRing 并派发（tikeno.inject 线程内执行） */
    private void processSteps() {
        if (!running) {
            return;
        }
        ring.drain(this::onStep);
    }

    /** 单步处理：按 kind 分发（stroke 聚合在 InjectionEngine 内完成） */
    private void onStep(OutputRing.Step s) {
        final InjectionEngine engine = dispatcher.active();
        switch (s.kind) {
            case OutputRing.KIND_DOWN:
                engine.beginStroke(s.slot, s.x, s.y, s.delayNs);
                break;
            case OutputRing.KIND_MOVE:
                engine.moveStroke(s.slot, s.x, s.y, s.delayNs);
                break;
            case OutputRing.KIND_UP:
                dispatchEndStroke(engine, s.slot, s.delayNs);
                break;
            case OutputRing.KIND_SYNC:
                // 同步帧：本地档无意义；桥接档由 endStroke 已派发，no-op
                break;
            case OutputRing.KIND_WAIT:
                // 纯等待步：间隔节奏由 C++ 调度器推进，Java 侧无需动作
                break;
            case OutputRing.KIND_GLOBAL:
                engine.globalAction(s.arg);
                break;
            case OutputRing.KIND_NODE_RESOLVE_REQ:
                // 节点解析回填随 NodeResolver 轮次接入；T03 忽略（C++ 按跳过策略）
                break;
            default:
                break;
        }
    }

    /**
     * 派发完成笔画。门控：上一手势未完成时延迟重试（dispatchGesture 会
     * 取消在途手势，直接派发会互相打断）。
     */
    private void dispatchEndStroke(InjectionEngine engine, int slot, long delayNs) {
        if (engine instanceof L3AccessibilityInjector) {
            final L3AccessibilityInjector l3 = (L3AccessibilityInjector) engine;
            if (!l3.lastGestureCompleted()) {
                retryEndStroke(l3, slot, delayNs, 0);
                return;
            }
        }
        engine.endStroke(slot, delayNs);
    }

    private void retryEndStroke(final L3AccessibilityInjector l3, final int slot,
                                final long delayNs, final int attempt) {
        if (!running || attempt >= GESTURE_MAX_RETRIES) {
            // 超过等待上限：直接派发（宁可小概率打断，不可积压丢步）
            l3.endStroke(slot, delayNs);
            return;
        }
        if (l3.lastGestureCompleted()) {
            l3.endStroke(slot, delayNs);
        } else if (handler != null) {
            handler.postDelayed(() -> retryEndStroke(l3, slot, delayNs, attempt + 1),
                    GESTURE_WAIT_MS);
        }
    }

    /** Looper 引用隔离（便于单测替换） */
    private static final class LooperHolder {
        static MessageQueue myQueue() {
            return android.os.Looper.myQueue();
        }
    }
}
