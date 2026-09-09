package com.tikeno.autoclicker.core;

/**
 * EventId — 内部事件 ID 常量（架构 §2.4.1 #50，避免字符串魔法值）。
 * 用于 StateStore 观察者之外的显式接口回调整别。
 */
public final class EventId {

    // —— 引擎相关 ——
    public static final int ENGINE_STATE_CHANGED = 1;
    public static final int ENGINE_FINISHED = 2;
    public static final int ENGINE_ERROR = 3;
    public static final int TIER_DOWNGRADED = 4;
    public static final int TIER_CHANGED = 5;

    // —— 统计回传（StatsReporter 200ms 节流，架构 §6.2）——
    public static final int STATS_TICK = 10;

    // —— 配置相关 ——
    public static final int CONFIG_LIST_CHANGED = 20;

    // —— 权限/探测 ——
    public static final int PROBE_DONE = 30;

    private EventId() {
        // 常量类禁止实例化
    }
}
