package com.tikeno.autoclicker.core;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * StateStore — 单一状态源（架构 §2.4.1 #47 / §7.2）。
 *
 * 手写观察者（CopyOnWriteArrayList，非框架）；监听器回调只做轻量赋值，
 * 耗时操作 postMain（架构 §7.3 死锁防护规则 2：回调不持锁）。
 * 状态变更同步引擎实际值（NativeEngine.nativeGetState 直读共享快照可交叉校验）。
 */
public final class StateStore {

    /** 状态变化监听器（UI/悬浮窗/磁贴各自注册） */
    public interface Listener {
        void onStateChanged(EngineState newState);
    }

    public interface TierListener {
        void onTierChanged(InjectionTier newTier);
    }

    private final CopyOnWriteArrayList<Listener> stateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<TierListener> tierListeners = new CopyOnWriteArrayList<>();

    private volatile EngineState state = EngineState.IDLE;
    private volatile InjectionTier tier = InjectionTier.L3_ACCESSIBILITY;
    private volatile int progress;   // 已执行步数
    private volatile int total;      // 序列总步数（0=无限/未知）

    public EngineState getState() {
        return state;
    }

    public synchronized void setState(EngineState newState) {
        if (state == newState) {
            return;
        }
        state = newState;
        for (Listener l : stateListeners) {   // 锁外遍历快照（COW 语义）
            l.onStateChanged(newState);
        }
    }

    public InjectionTier getTier() {
        return tier;
    }

    public synchronized void setTier(InjectionTier newTier) {
        if (tier == newTier) {
            return;
        }
        tier = newTier;
        for (TierListener l : tierListeners) {
            l.onTierChanged(newTier);
        }
    }

    public void setProgress(int executed, int totalCount) {
        this.progress = executed;
        this.total = totalCount;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public void addStateListener(Listener l) {
        if (l != null) {
            stateListeners.add(l);
        }
    }

    public void removeStateListener(Listener l) {
        if (l != null) {
            stateListeners.remove(l);
        }
    }

    public void addTierListener(TierListener l) {
        if (l != null) {
            tierListeners.add(l);
        }
    }

    public void removeTierListener(TierListener l) {
        if (l != null) {
            tierListeners.remove(l);
        }
    }
}
