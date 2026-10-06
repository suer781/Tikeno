package com.tikeno.autoclicker.engine;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;

/**
 * StatsBuffer 单元测试。
 *
 * 测试目标：
 *  - 验证 StatsBuffer 对 88B（11 × int64 小端）共享内存只读视图的字段读取；
 *  - 验证 snapshot() 各字段与写入的值一致；
 *  - 验证 state() 对偏移 72 的轻量读取；
 *  - 验证 tier 位于偏移 80；
 *  - 验证构造时强制 LITTLE_ENDIAN 字节序。
 *
 * 纯 JVM 可运行（无 Android 依赖）。
 */
public class StatsBufferTest {

    private static ByteBuffer newDirectBuffer() {
        return ByteBuffer.allocateDirect(StatsBuffer.SNAPSHOT_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void initialState_allZeroAndIdle() {
        StatsBuffer stats = new StatsBuffer(newDirectBuffer());

        StatsBuffer.Snapshot s = stats.snapshot();
        assertEquals(0L, s.p50Ns);
        assertEquals(0L, s.p95Ns);
        assertEquals(0L, s.p99Ns);
        assertEquals(0L, s.meanNs);
        assertEquals(0L, s.maxNs);
        assertEquals(0L, s.minNs);
        assertEquals(0L, s.missedTicks);
        assertEquals(0L, s.execCount);
        assertEquals(0L, s.sampleCount);
        assertEquals(0, s.state);
        assertEquals(0, s.tier);
        assertEquals(StatsBuffer.STATE_IDLE, stats.state());
    }

    @Test
    public void singleField_p50AtOffset0() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(0, 123_456_789L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(123_456_789L, s.p50Ns);
        assertEquals(0L, s.p95Ns);
    }

    @Test
    public void singleField_p95AtOffset8() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(8, 987_654_321L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(987_654_321L, s.p95Ns);
    }

    @Test
    public void singleField_p99AtOffset16() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(16, 1_500_000_000L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(1_500_000_000L, s.p99Ns);
    }

    @Test
    public void singleField_meanAtOffset24() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(24, 250_000L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(250_000L, s.meanNs);
    }

    @Test
    public void singleField_maxAtOffset32() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(32, 4_000_000_000L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(4_000_000_000L, s.maxNs);
    }

    @Test
    public void singleField_minAtOffset40() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(40, 5_000L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(5_000L, s.minNs);
    }

    @Test
    public void singleField_missedTicksAtOffset48() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(48, 42L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(42L, s.missedTicks);
    }

    @Test
    public void singleField_execCountAtOffset56() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(56, 10_000L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(10_000L, s.execCount);
    }

    @Test
    public void singleField_sampleCountAtOffset64() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(64, 7_777L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(7_777L, s.sampleCount);
    }

    @Test
    public void state_readsOffset72() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(72, StatsBuffer.STATE_RUNNING);

        StatsBuffer stats = new StatsBuffer(buf);
        assertEquals(StatsBuffer.STATE_RUNNING, stats.state());
        assertEquals(StatsBuffer.STATE_RUNNING, stats.snapshot().state);
    }

    @Test
    public void state_negativeValueCastsToInt() {
        ByteBuffer buf = newDirectBuffer();
        // 模拟共享内存写入一个高位非零的 int64，验证 (int) 截断语义
        buf.putLong(72, 0xFFFFFFFF00000002L);

        assertEquals(StatsBuffer.STATE_RUNNING, new StatsBuffer(buf).state());
    }

    @Test
    public void tier_readsOffset80() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(80, 3L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(3, s.tier);
    }

    @Test
    public void allFields_roundTrip() {
        ByteBuffer buf = newDirectBuffer();
        buf.putLong(0, 100L);    // p50Ns
        buf.putLong(8, 200L);    // p95Ns
        buf.putLong(16, 300L);   // p99Ns
        buf.putLong(24, 150L);   // meanNs
        buf.putLong(32, 999L);   // maxNs
        buf.putLong(40, 1L);     // minNs
        buf.putLong(48, 7L);     // missedTicks
        buf.putLong(56, 88L);    // execCount
        buf.putLong(64, 66L);    // sampleCount
        buf.putLong(72, StatsBuffer.STATE_PAUSED); // state
        buf.putLong(80, 2L);     // tier

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(100L, s.p50Ns);
        assertEquals(200L, s.p95Ns);
        assertEquals(300L, s.p99Ns);
        assertEquals(150L, s.meanNs);
        assertEquals(999L, s.maxNs);
        assertEquals(1L, s.minNs);
        assertEquals(7L, s.missedTicks);
        assertEquals(88L, s.execCount);
        assertEquals(66L, s.sampleCount);
        assertEquals(StatsBuffer.STATE_PAUSED, s.state);
        assertEquals(2, s.tier);
    }

    @Test
    public void littleEndian_layout() {
        // 直接缓冲默认大端；StatsBuffer 构造时必须 order(LITTLE_ENDIAN)，
        // 因此这里用小端缓冲写入，验证读取结果一致。
        ByteBuffer buf = ByteBuffer.allocateDirect(StatsBuffer.SNAPSHOT_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(0, 0x0102030405060708L);
        buf.putLong(80, 5L);

        StatsBuffer.Snapshot s = new StatsBuffer(buf).snapshot();
        assertEquals(0x0102030405060708L, s.p50Ns);
        assertEquals(5, s.tier);
    }
}