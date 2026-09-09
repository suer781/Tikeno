package com.tikeno.autoclicker.model;

/**
 * LoopPolicy — 序列循环策略（架构 §2.4 数据模型，对应 C++ TkLoopPolicy）。
 *
 * T03 阶段：C++ SequencePlayer 默认无限循环（TkLoopPolicy 未接入 JNI 入参），
 * kind 取 INFINITE 与最小验收配置一致；固定次数/时长策略随 T04/T05 接入。
 */
public final class LoopPolicy {

    /** 循环类型（与 C++ TkLoopPolicy.kind 一致） */
    public static final int KIND_INFINITE = 0;      // 无限循环（手动停止）
    public static final int KIND_FIXED_COUNT = 1;   // 固定次数
    public static final int KIND_FIXED_DURATION = 2; // 固定时长
    public static final int KIND_COMBINED = 3;      // 组合（先到先停）

    public final int kind;
    public final int maxCount;        // KIND_FIXED_COUNT：总执行次数上限
    public final long maxDurationNs;  // KIND_FIXED_DURATION：总时长上限

    public LoopPolicy(int kind, int maxCount, long maxDurationNs) {
        this.kind = kind;
        this.maxCount = maxCount;
        this.maxDurationNs = maxDurationNs;
    }

    public static LoopPolicy infinite() {
        return new LoopPolicy(KIND_INFINITE, 0, 0L);
    }

    public static LoopPolicy fixedCount(int count) {
        return new LoopPolicy(KIND_FIXED_COUNT, count, 0L);
    }
}
