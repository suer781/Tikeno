package com.tikeno.autoclicker.engine;

import android.os.Handler;

import com.tikeno.autoclicker.core.EngineState;
import com.tikeno.autoclicker.core.StateStore;
import com.tikeno.autoclicker.util.Logx;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * StatsReporter — 统计聚合与节流回传（架构 §2.4.4 #76 / §6.2）。
 *
 * 每 200ms 从 statsBuf 直读快照（无 JNI）：
 *  1. 旁路校验引擎真实状态 → StateStore 同步（含 STOPPING→IDLE 收敛）；
 *  2. execCount → StateStore 进度；
 *  3. 把快照投递到已注册 Listener（保证主线程回调）。
 */
public final class StatsReporter {

    private static final String TAG = "Tikeno/Stats";
    private static final long PERIOD_MS = 200L;   // 架构 §6.2 节流周期

    /** 统计监听（UI/悬浮面板/磁贴注册；回调在主线程） */
    public interface Listener {
        void onStats(StatsBuffer.Snapshot snapshot);
    }

    private final StatsBuffer statsBuffer;
    private final StateStore stateStore;
    private final Handler mainHandler;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private boolean polledOnce;   // 首轮用于强制同步一次状态

    public StatsReporter(StatsBuffer statsBuffer, StateStore stateStore, Handler mainHandler) {
        this.statsBuffer = statsBuffer;
        this.stateStore = stateStore;
        this.mainHandler = mainHandler;
    }

    public void addListener(Listener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        if (l != null) {
            listeners.remove(l);
        }
    }

    /** 启动轮询（幂等） */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        polledOnce = false;
        mainHandler.postDelayed(tick, PERIOD_MS);
    }

    /** 停止轮询（幂等） */
    public synchronized void stop() {
        running = false;
        mainHandler.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            try {
                final StatsBuffer.Snapshot s = statsBuffer.snapshot();
                // —— 旁路状态同步（C++ 为状态真值源）——
                final EngineState nativeState = mapState(s.state);
                if (!polledOnce || stateStore.getState() != nativeState) {
                    polledOnce = true;
                    // Stopping 由 Java 主动设置，避免覆盖后立即回跳；
                    // 其余状态以 C++ 快照为准
                    if (stateStore.getState() != EngineState.STOPPING
                            || nativeState == EngineState.IDLE
                            || nativeState == EngineState.ERROR) {
                        stateStore.setState(nativeState);
                    }
                }
                stateStore.setProgress((int) Math.min(Integer.MAX_VALUE, s.execCount), 0);
                // —— 主线程投递快照 ——
                for (Listener l : listeners) {
                    l.onStats(s);
                }
            } catch (Exception e) {
                Logx.w(TAG, "统计轮询异常", e);
            }
            mainHandler.postDelayed(this, PERIOD_MS);
        }
    };

    private static EngineState mapState(int nativeState) {
        switch (nativeState) {
            case StatsBuffer.STATE_PREPARED: return EngineState.PREPARED;
            case StatsBuffer.STATE_RUNNING: return EngineState.RUNNING;
            case StatsBuffer.STATE_PAUSED: return EngineState.PAUSED;
            case StatsBuffer.STATE_STOPPING: return EngineState.STOPPING;
            case StatsBuffer.STATE_ERROR: return EngineState.ERROR;
            default: return EngineState.IDLE;
        }
    }
}
