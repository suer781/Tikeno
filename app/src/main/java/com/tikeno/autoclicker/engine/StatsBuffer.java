package com.tikeno.autoclicker.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * StatsBuffer — statsBuf 共享内存的 Java 只读视图（架构 §2.4.4 #72 / §4.3）。
 *
 * 【布局契约】88 字节有效区（11 × int64，与 C++ TkStatsSnapshot 一致）：
 *   p50Ns/p95Ns/p99Ns/meanNs/maxNs/minNs/missedTicks/execCount/
 *   sampleCount/state/tier —— 偏移 0/8/16/24/32/40/48/56/64/72/80。
 *
 * 【线程归属】C++ 调度线程每 200ms 写一次 → Java 任意线程直读（无 JNI、无锁）。
 * 读到的瞬态值可能新旧交错（非原子快照），对 UI 展示场景可接受；
 * state 字段作为引擎真实状态的旁路校验源（架构 §7.2）。
 */
public final class StatsBuffer {

    public static final int SNAPSHOT_BYTES = 88;

    // 状态值（与 C++ TkEngineState / Java EngineState 一致）
    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARED = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_PAUSED = 3;
    public static final int STATE_STOPPING = 4;
    public static final int STATE_ERROR = 5;

    private final ByteBuffer buf;

    public StatsBuffer(ByteBuffer direct) {
        this.buf = direct.order(ByteOrder.LITTLE_ENDIAN);
    }

    /** 不可变快照（主线程 UI 消费） */
    public static final class Snapshot {
        public final long p50Ns;
        public final long p95Ns;
        public final long p99Ns;
        public final long meanNs;
        public final long maxNs;
        public final long minNs;
        public final long missedTicks;
        public final long execCount;
        public final long sampleCount;
        public final int state;
        public final int tier;

        Snapshot(long p50, long p95, long p99, long mean, long max, long min,
                 long missed, long exec, long samples, int state, int tier) {
            this.p50Ns = p50;
            this.p95Ns = p95;
            this.p99Ns = p99;
            this.meanNs = mean;
            this.maxNs = max;
            this.minNs = min;
            this.missedTicks = missed;
            this.execCount = exec;
            this.sampleCount = samples;
            this.state = state;
            this.tier = tier;
        }
    }

    /** 读取当前快照（任意线程可调用） */
    public Snapshot snapshot() {
        return new Snapshot(
                buf.getLong(0), buf.getLong(8), buf.getLong(16),
                buf.getLong(24), buf.getLong(32), buf.getLong(40),
                buf.getLong(48), buf.getLong(56), buf.getLong(64),
                (int) buf.getLong(72), (int) buf.getLong(80));
    }

    /** 轻量读取：仅引擎状态（不构造快照对象） */
    public int state() {
        return (int) buf.getLong(72);
    }
}
