package com.tikeno.autoclicker.engine;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;

import com.tikeno.autoclicker.service.ClickerAccessibilityService;
import com.tikeno.autoclicker.util.Logx;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * L3AccessibilityInjector — 免 Root 档注入器（架构 §2.4.4 #67 / 调研 §2.1.1）。
 *
 * 核心：GestureDescription 构建 + service.dispatchGesture(gesture, cb, handler)。
 * 回调统一投递到注入线程 Handler（tikeno.inject），不占主线程。
 *
 * 已知语义约束（调研 §2.1.1）：dispatchGesture 会取消在途手势 ——
 * InjectionLooper 侧通过 lastGestureDone 门控避免高频互相取消；
 * 多指并发手势以独立 dispatch 实现（逐指派发），并发精度随 T04/T05 完善。
 */
public final class L3AccessibilityInjector implements InjectionEngine {

    private static final String TAG = "Tikeno/InjL3";

    /** 单点手势最小时长（GestureDescription 拒绝 0 时长 stroke） */
    private static final long MIN_STROKE_MS = 1L;
    /** 长按判定阈值（无移动 + 时长 ≥ 400ms → longPress 语义） */
    private static final long LONG_PRESS_MS = 400L;

    private final ClickerAccessibilityService service;
    private final Handler injectHandler;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean lastGestureDone = new AtomicBoolean(true);

    // —— 槽位笔画聚合（10 槽预分配，执行路径零分配）——
    private static final int MAX_POINTS = 256;   // 单笔画最大点数
    private final float[] xs = new float[MAX_POINTS];
    private final float[] ys = new float[MAX_POINTS];
    private final long[] dns = new long[MAX_POINTS];   // 各点相对上一点的延迟
    private final int[] pointCount = new int[10];
    private final boolean[] active = new boolean[10];

    public L3AccessibilityInjector(ClickerAccessibilityService service, Handler injectHandler) {
        this.service = service;
        this.injectHandler = injectHandler;
    }

    @Override
    public int code() {
        return 3;   // InjectionTier.L3_ACCESSIBILITY.code()
    }

    @Override
    public boolean isAvailable() {
        return service != null && service.isConnected();
    }

    /** 上一手势是否已完成（InjectionLooper 门控高频派发用） */
    public boolean lastGestureCompleted() {
        return lastGestureDone.get();
    }

    @Override
    public void beginStroke(int slot, int x, int y, long delayNs) {
        if (slot < 0 || slot >= 10) {
            return;
        }
        pointCount[slot] = 0;
        appendPoint(slot, x, y, delayNs);
        active[slot] = true;
    }

    @Override
    public void moveStroke(int slot, int x, int y, long delayNs) {
        if (slot < 0 || slot >= 10 || !active[slot]) {
            return;
        }
        appendPoint(slot, x, y, delayNs);
    }

    @Override
    public void endStroke(int slot, long delayNs) {
        if (slot < 0 || slot >= 10 || !active[slot]) {
            return;
        }
        active[slot] = false;
        final int count = pointCount[slot];
        if (count == 0) {
            return;
        }
        // 总时长 = 各点延迟之和（UP 点的 delayNs 已由调用方传入 appendPoint）
        long totalNs = 0;
        for (int i = 0; i < count; i++) {
            totalNs += dns[i];
        }
        final long durationMs = Math.max(MIN_STROKE_MS, totalNs / 1_000_000L);

        if (count == 1) {
            // 纯点击：DOWN→UP 无移动
            final int x = (int) xs[0];
            final int y = (int) ys[0];
            pointCount[slot] = 0;
            dispatchTapOrLongPress(x, y, durationMs);
            return;
        }
        // 滑动/带轨迹：构建路径派发
        final Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < count; i++) {
            path.lineTo(xs[i], ys[i]);
        }
        pointCount[slot] = 0;
        dispatchSwipe(path, durationMs);
    }

    // —— 完整手势 API（UI 层直接调用）——

    /** 单击 */
    public void tap(int x, int y) {
        dispatchTapOrLongPress(x, y, MIN_STROKE_MS);
    }

    /** 长按 */
    public void longPress(int x, int y, long durationMs) {
        dispatchTapOrLongPress(x, y, Math.max(durationMs, LONG_PRESS_MS));
    }

    /** 滑动（点序列 + 总时长） */
    public void swipe(int[] xs, int[] ys, int count, long durationMs) {
        if (count < 2 || xs == null || ys == null || xs.length < count || ys.length < count) {
            return;
        }
        final Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < count; i++) {
            path.lineTo(xs[i], ys[i]);
        }
        dispatchSwipe(path, Math.max(durationMs, MIN_STROKE_MS));
    }

    @Override
    public void globalAction(int globalAction) {
        if (!isAvailable() || cancelled.get()) {
            return;
        }
        service.performGlobalAction(globalAction);
    }

    @Override
    public void cancelPending() {
        cancelled.set(true);
        // 在途手势由系统 onCancel 回调收尾（UP 已由 C++ 补发语义保证，§6.3）
    }

    @Override
    public void resumeDispatch() {
        cancelled.set(false);
        lastGestureDone.set(true);
    }

    // —— 内部：手势派发（统一入口，回调全在 injectHandler）——

    private void dispatchTapOrLongPress(int x, int y, long durationMs) {
        if (!guardDispatch()) {
            return;
        }
        final Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + 0.1f, y + 0.1f);   // 零长度路径被系统拒绝，给 0.1px 位移
        dispatchSwipe(path, durationMs);
    }

    private void dispatchSwipe(Path path, long durationMs) {
        if (!guardDispatch()) {
            return;
        }
        final GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, durationMs);
        final GestureDescription gesture =
                new GestureDescription.Builder().addStroke(stroke).build();
        final boolean ok = service.dispatchGesture(gesture, new GestureResultAdapter() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                lastGestureDone.set(true);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                lastGestureDone.set(true);
                if (!cancelled.get()) {
                    Logx.w(TAG, "手势被系统取消（非停止语义）");
                }
            }
        }, injectHandler);
        if (!ok) {
            // 派发被拒（服务未连接/参数非法）：不阻塞调度循环
            lastGestureDone.set(true);
            Logx.w(TAG, "dispatchGesture 拒绝派发");
        }
    }

    private boolean guardDispatch() {
        if (cancelled.get() || !isAvailable()) {
            return false;
        }
        return true;
    }

    private void appendPoint(int slot, int x, int y, long delayNs) {
        final int idx = pointCount[slot];
        if (idx >= MAX_POINTS) {
            return;   // 超长轨迹截断（轨迹采样上限由 C++ 侧控制，此为兜底）
        }
        xs[idx] = x;
        ys[idx] = y;
        dns[idx] = Math.max(0L, delayNs);
        pointCount[slot] = idx + 1;
    }

    /** GestureResultAdapter 的轻量封装（避免匿名类重复样板） */
    private static abstract class GestureResultAdapter
            extends AccessibilityService.GestureResultCallback {
    }
}
