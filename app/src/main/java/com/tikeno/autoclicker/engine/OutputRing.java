package com.tikeno.autoclicker.engine;

import java.nio.ByteBuffer;

/**
 * OutputRing — outRing 共享内存的 Java 读端（架构 §2.4.4 #71 / §4.3）。
 *
 * 【布局契约】（与 cpp/core/shared_ring.h 逐字节对应）
 *   offset 0  : head   (uint64, C++ 写端独占游标)
 *   offset 8  : tail   (uint64, Java 读端独占游标)
 *   offset 16 : capacity (uint64, = 4096)
 *   offset 24 : magic   (uint64, 0x544B454E4F555452 "TKENOUTR")
 *   offset 64 : 槽位数组，每槽 32B TkStep（kind/slot/x/y/delayNs/arg/seq）
 *
 * 【线程归属】仅 tikeno.inject 线程调用 drain()（SPSC 读端独占）。
 * 读到 head 后逐槽消费，最后把 tail 发布回共享内存（C++ 据此判断可写空间）。
 * 游标读取为无锁直读：SPSC 单读者语义下 getLong 的 64 位原子性即可保证
 * 不读到撕裂值（C++ 侧 head 为 release store，见架构 §7.2）。
 */
public final class OutputRing {

    /** 与 C++ kRingMagic 一致 */
    public static final long RING_MAGIC = 0x544B454E4F555452L;
    public static final int HEADER_BYTES = 64;
    public static final int STEP_BYTES = 32;
    public static final long CAPACITY = 4096;

    // TkStep.kind（与 C++ TkStepKind 一致）
    public static final int KIND_DOWN = 0;
    public static final int KIND_MOVE = 1;
    public static final int KIND_UP = 2;
    public static final int KIND_SYNC = 3;
    public static final int KIND_WAIT = 4;
    public static final int KIND_GLOBAL = 5;
    public static final int KIND_NODE_RESOLVE_REQ = 6;

    /** 复用的步视图（零分配：drain 期间原地覆写字段） */
    public static final class Step {
        public int kind;
        public int slot;
        public int x;
        public int y;
        public long delayNs;
        public int arg;
        public int seq;
    }

    /** 步消费者（在 drain 调用线程内同步执行） */
    public interface Consumer {
        void onStep(Step step);
    }

    private ByteBuffer buf;
    private long tail;   // 本地游标（读端独占，不回读共享内存）

    /**
     * 绑定共享缓冲。必须在 NativeEngine.nativeAttachOutputRing 之后调用
     * （C++ 侧首次绑定时初始化 header，此处校验 magic 与容量）。
     *
     * @return 0 成功；-1003 参数非法；-1010 缓冲未就绪（magic 不符）
     */
    public int attach(ByteBuffer direct) {
        if (direct == null || !direct.isDirect()) {
            return -1003; // TK_ERR_INVALID_ARG
        }
        if (direct.capacity() < HEADER_BYTES + (int) (CAPACITY * STEP_BYTES)) {
            return -1003;
        }
        this.buf = direct.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if (buf.getLong(24) != RING_MAGIC) {
            this.buf = null;
            return -1010; // TK_ERR_BUFFER_NOT_ATTACHED（C++ 尚未初始化）
        }
        this.tail = buf.getLong(8);   // 容错：绑定前已有消费进度（正常为 0）
        return 0;
    }

    public boolean isAttached() {
        return buf != null;
    }

    /**
     * 批量消费就绪步（head 之前全部取出）。
     * 每步回调 consumer.onStep；槽位序号 seq 用于校验（可选）。
     *
     * @return 本次消费的步数
     */
    public int drain(Consumer consumer) {
        if (buf == null || consumer == null) {
            return 0;
        }
        final long head = buf.getLong(0);
        int count = 0;
        while (tail < head) {
            final int idx = (int) (tail % CAPACITY);
            final int base = HEADER_BYTES + idx * STEP_BYTES;
            Step s = new Step();   // 注：单次 drain 内小对象，JIT 逃逸分析可消除；
            s.kind = buf.getInt(base);          // 消费路径不在 C++ 热循环约束内
            s.slot = buf.getInt(base + 4);      // （架构 §5.4 约束的是 C++ 执行循环）
            s.x = buf.getInt(base + 8);
            s.y = buf.getInt(base + 12);
            s.delayNs = buf.getLong(base + 16);
            s.arg = buf.getInt(base + 24);
            s.seq = buf.getInt(base + 28);
            consumer.onStep(s);
            tail++;
            count++;
        }
        if (count > 0) {
            buf.putLong(8, tail);   // 发布 tail → C++ 写端可判定剩余空间
        }
        return count;
    }

    /** 未消费积压步数（监控用） */
    public long backlog() {
        if (buf == null) {
            return 0;
        }
        return buf.getLong(0) - tail;
    }
}
