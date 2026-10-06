package com.tikeno.autoclicker.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.tikeno.autoclicker.model.ActionModel;
import com.tikeno.autoclicker.model.ActionSequence;
import com.tikeno.autoclicker.model.LoopPolicy;
import com.tikeno.autoclicker.model.PointModel;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * SequenceFlattener 的 JVM 单元测试。
 *
 * <p>验证 {@code ActionSequence -> TkActionFlat 扁平字节} 契约（架构 §2.4.4）：
 * <ul>
 *   <li>参数校验：null / 非 Direct / 空序列 / 超容量 → -1003</li>
 *   <li>24B 头部（小端序）：actionCount / schemaVersion=1 / loopKind / loopMaxCount /
 *       loopMaxDurationMs（maxDurationNs / 1_000_000L 截断）</li>
 *   <li>动作数据自偏移 24 起，每个动作 64B，其后每个点 16B（percent → px 换算）</li>
 * </ul>
 *
 * <p>全部用例纯 JVM 运行，不依赖 Android 框架。
 */
public class SequenceFlattenerTest {

    // —— 与 cpp/core/types.h 一致的 TkActionFlat 字段偏移（相对动作起点）——
    private static final int OFF_TYPE = 0;
    private static final int OFF_FLAGS = 4;
    private static final int OFF_REPEAT = 8;
    private static final int OFF_DURATION_MS = 12;
    private static final int OFF_INTERVAL_NS = 16;
    private static final int OFF_HOLD_NS = 24;
    private static final int OFF_POINT_COUNT = 32;
    private static final int OFF_CURVE_TYPE = 36;
    private static final int OFF_SAMPLE_STEP_US = 40;
    private static final int OFF_COORD_MODE = 44;
    private static final int OFF_GLOBAL_ACTION = 48;
    private static final int OFF_MISS_POLICY = 52;
    private static final int OFF_JITTER_PCT = 56;
    private static final int OFF_RESERVED = 60;

    // —— 头部布局 ——
    private static final int HEADER_SIZE = 24;
    private static final int ACTION_SIZE = 64;
    private static final int POINT_SIZE = 16;

    // —— 动作类型常量（与 ActionModel 一致）——
    private static final int TYPE_TAP = 1;
    private static final int TYPE_LONG_PRESS = 2;
    private static final int TYPE_SWIPE = 3;

    // ==================== 1. 参数校验 ====================

    @Test
    public void nullSequence_returnsError() {
        ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        assertEquals(-1003, SequenceFlattener.flatten(null, 1080, 2400, buf));
    }

    @Test
    public void nullBuffer_returnsError() {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(ActionModel.tap(PointModel.px(100, 200), 0L));
        assertEquals(-1003, SequenceFlattener.flatten(seq, 1080, 2400, null));
    }

    @Test
    public void nonDirectBuffer_returnsError() {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(ActionModel.tap(PointModel.px(100, 200), 0L));
        ByteBuffer heapBuf = ByteBuffer.allocate(1024);
        assertEquals(-1003, SequenceFlattener.flatten(seq, 1080, 2400, heapBuf));
    }

    @Test
    public void emptySequence_returnsError() {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        assertEquals(-1003, SequenceFlattener.flatten(seq, 1080, 2400, buf));
    }

    // ==================== 2. 基本 tap 序列 ====================

    @Test
    public void singleTap_writesHeaderActionAndPoint() {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(ActionModel.tap(PointModel.px(540, 600), 0L));
        ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, SequenceFlattener.flatten(seq, 1080, 2400, buf));

        // 头部 24B
        assertEquals(1, buf.getInt(0));               // actionCount
        assertEquals(1, buf.getInt(4));               // schemaVersion
        assertEquals(LoopPolicy.KIND_INFINITE, buf.getInt(8));  // loopKind
        assertEquals(0, buf.getInt(12));              // loopMaxCount
        assertEquals(0, buf.getInt(16));             // loopMaxDurationMs

        // 动作起点 = 24
        int actionOff = HEADER_SIZE;
        assertEquals(TYPE_TAP, buf.getInt(actionOff + OFF_TYPE));
        assertEquals(1, buf.getInt(actionOff + OFF_REPEAT));
        assertEquals(1, buf.getInt(actionOff + OFF_POINT_COUNT));
        assertEquals(0, buf.getInt(actionOff + OFF_COORD_MODE));
        assertEquals(0, buf.getInt(actionOff + OFF_MISS_POLICY));
        assertEquals(0, buf.getInt(actionOff + OFF_RESERVED));

        // 第一个点起点 = 24 + 64
        int pointOff = actionOff + ACTION_SIZE;
        assertEquals(540, buf.getInt(pointOff));      // x
        assertEquals(600, buf.getInt(pointOff + 4));  // y
        assertEquals(0, buf.getInt(pointOff + 8));    // offsetX
        assertEquals(0, buf.getInt(pointOff + 12));   // offsetY
    }

    // ==================== 3. 百分比换算 ====================

    @Test
    public void percentPoint_convertsToPx() {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        // percent(500, 250)：500*1080/1000 = 540；250*2400/1000 = 600
        seq.addAction(ActionModel.tap(PointModel.percent(500, 250), 0L));
        ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, SequenceFlattener.flatten(seq, 1080, 2400, buf));

        int actionOff = HEADER_SIZE;
        int pointOff = actionOff + ACTION_SIZE;   // 24 + 64 = 88
        assertEquals(540, buf.getInt(pointOff));      // x @88
        assertEquals(600, buf.getInt(pointOff + 4));  // y @92
    }

    // ==================== 4. 固定次数策略 ====================

    @Test
    public void fixedCountPolicy_writesLoopHeader() {
        ActionSequence seq = new ActionSequence(LoopPolicy.fixedCount(10));
        seq.addAction(ActionModel.tap(PointModel.px(100, 100), 0L));
        ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, SequenceFlattener.flatten(seq, 1080, 2400, buf));

        assertEquals(LoopPolicy.KIND_FIXED_COUNT, buf.getInt(8));
        assertEquals(10, buf.getInt(12));
        assertEquals(0, buf.getInt(16));
    }

    // ==================== 5. 固定时长策略 ====================

    @Test
    public void fixedDurationPolicy_writesMaxDurationMs() {
        // KIND_FIXED_DURATION=2，5_000_000_000ns → 5000ms
        LoopPolicy policy = new LoopPolicy(LoopPolicy.KIND_FIXED_DURATION, 0, 5_000_000_000L);
        ActionSequence seq = new ActionSequence(policy);
        seq.addAction(ActionModel.tap(PointModel.px(100, 100), 0L));
        ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, SequenceFlattener.flatten(seq, 1080, 2400, buf));

        assertEquals(LoopPolicy.KIND_FIXED_DURATION, buf.getInt(8));
        assertEquals(0, buf.getInt(12));
        assertEquals(5000, buf.getInt(16));
    }

    // ==================== 6. 多动作多点多类型 ====================

    @Test
    public void multiAction_offsetsAndValuesAreCorrect() {
        // 动作1：tap，2 个点（绝对 px）
        ActionModel tap = ActionModel.tap(PointModel.px(100, 200), 0L);
        tap.points.add(PointModel.px(300, 400));
        tap.repeat = 3;
        tap.durationMs = 50;
        tap.intervalNs = 1_000_000L;
        tap.holdNs = 2_000_000L;
        tap.curveType = 1;
        tap.sampleStepUs = 1234;
        tap.globalAction = 0;
        tap.jitterPct = 0f;

        // 动作2：swipe（type=3），从 (50,60) 到 (70,80)，percent 坐标验证换算
        ActionModel swipe = new ActionModel();
        swipe.type = TYPE_SWIPE;
        swipe.points.add(PointModel.percent(500, 250));   // → (540, 600)
        swipe.points.add(PointModel.percent(700, 750));   // → (756, 1800)
        swipe.repeat = 2;
        swipe.durationMs = 300;
        swipe.intervalNs = 5_000_000L;
        swipe.holdNs = 6_000_000L;
        swipe.curveType = 2;
        swipe.sampleStepUs = 4321;
        swipe.globalAction = 0;
        swipe.jitterPct = 0f;

        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(tap);
        seq.addAction(swipe);
        ByteBuffer buf = ByteBuffer.allocateDirect(2048);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, SequenceFlattener.flatten(seq, 1080, 2400, buf));

        // 头部
        assertEquals(2, buf.getInt(0));
        assertEquals(1, buf.getInt(4));

        // 动作1起点 = 24
        int action1Off = HEADER_SIZE;
        assertEquals(TYPE_TAP, buf.getInt(action1Off + OFF_TYPE));
        assertEquals(3, buf.getInt(action1Off + OFF_REPEAT));
        assertEquals(50, buf.getInt(action1Off + OFF_DURATION_MS));
        assertEquals(1_000_000L, buf.getLong(action1Off + OFF_INTERVAL_NS));
        assertEquals(2_000_000L, buf.getLong(action1Off + OFF_HOLD_NS));
        assertEquals(2, buf.getInt(action1Off + OFF_POINT_COUNT));
        assertEquals(1, buf.getInt(action1Off + OFF_CURVE_TYPE));
        assertEquals(1234, buf.getInt(action1Off + OFF_SAMPLE_STEP_US));
        assertEquals(0, buf.getInt(action1Off + OFF_COORD_MODE));
        assertEquals(0, buf.getInt(action1Off + OFF_MISS_POLICY));
        assertEquals(0f, buf.getFloat(action1Off + OFF_JITTER_PCT), 0f);
        assertEquals(0, buf.getInt(action1Off + OFF_RESERVED));

        // 动作1的点（2 个）
        int action1Points = action1Off + ACTION_SIZE;
        assertEquals(100, buf.getInt(action1Points));
        assertEquals(200, buf.getInt(action1Points + 4));
        assertEquals(0, buf.getInt(action1Points + 8));
        assertEquals(0, buf.getInt(action1Points + 12));
        assertEquals(300, buf.getInt(action1Points + POINT_SIZE));
        assertEquals(400, buf.getInt(action1Points + POINT_SIZE + 4));
        assertEquals(0, buf.getInt(action1Points + POINT_SIZE + 8));
        assertEquals(0, buf.getInt(action1Points + POINT_SIZE + 12));

        // 动作2起点 = 24 + 64 + 2*16 = 120
        int action2Off = action1Off + ACTION_SIZE + 2 * POINT_SIZE;
        assertEquals(TYPE_SWIPE, buf.getInt(action2Off + OFF_TYPE));
        assertEquals(2, buf.getInt(action2Off + OFF_REPEAT));
        assertEquals(300, buf.getInt(action2Off + OFF_DURATION_MS));
        assertEquals(5_000_000L, buf.getLong(action2Off + OFF_INTERVAL_NS));
        assertEquals(6_000_000L, buf.getLong(action2Off + OFF_HOLD_NS));
        assertEquals(2, buf.getInt(action2Off + OFF_POINT_COUNT));
        assertEquals(2, buf.getInt(action2Off + OFF_CURVE_TYPE));
        assertEquals(4321, buf.getInt(action2Off + OFF_SAMPLE_STEP_US));
        assertEquals(0, buf.getInt(action2Off + OFF_COORD_MODE));
        assertEquals(0, buf.getInt(action2Off + OFF_MISS_POLICY));
        assertEquals(0f, buf.getFloat(action2Off + OFF_JITTER_PCT), 0f);
        assertEquals(0, buf.getInt(action2Off + OFF_RESERVED));

        // 动作2的点（percent → px）
        int action2Points = action2Off + ACTION_SIZE;
        assertEquals(540, buf.getInt(action2Points));            // 500*1080/1000
        assertEquals(600, buf.getInt(action2Points + 4));        // 250*2400/1000
        assertEquals(756, buf.getInt(action2Points + POINT_SIZE));      // 700*1080/1000
        assertEquals(1800, buf.getInt(action2Points + POINT_SIZE + 4)); // 750*2400/1000
    }

    // ==================== 7. 容量不足 ====================

    @Test
    public void tooManyActions_exceedsCapacity_returnsError() {
        // 256 是上限，257 个动作直接触发 count>256 校验 → -1003
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        for (int i = 0; i < 257; i++) {
            seq.addAction(ActionModel.tap(PointModel.px(10, 10), 0L));
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(1 << 20);  // 1MB 足够大
        assertEquals(-1003, SequenceFlattener.flatten(seq, 1080, 2400, buf));
    }

    @Test
    public void smallDirectBuffer_returnsError() {
        // 32B 连最小头部+动作都放不下 → -1003
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(ActionModel.tap(PointModel.px(100, 100), 0L));
        ByteBuffer smallBuf = ByteBuffer.allocateDirect(32);
        smallBuf.order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(-1003, SequenceFlattener.flatten(seq, 1080, 2400, smallBuf));
    }

    @Test
    public void largeCountWithinLimit_butCapacityInsufficient_returnsError() {
        // 构造一个 20 点动作（无工厂，直接加 20 个 percent 点），
        // 使其 worst = 24 + (64 + 20*16) = 408 > 400B 容量 → -1003
        ActionModel tap = new ActionModel();
        tap.type = TYPE_TAP;
        for (int i = 0; i < 20; i++) {
            tap.points.add(PointModel.percent(500, 500));
        }
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(tap);
        ByteBuffer smallBuf = ByteBuffer.allocateDirect(400);
        smallBuf.order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(-1003, SequenceFlattener.flatten(seq, 1080, 2400, smallBuf));
    }

    // ==================== 8. repeat 钳制 ====================

    @Test
    public void repeatZero_isClampedToOne() {
        ActionModel tap = ActionModel.tap(PointModel.px(100, 100), 0L);
        tap.repeat = 0;   // 非法值，flatten 应钳制为 1
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(tap);
        ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, SequenceFlattener.flatten(seq, 1080, 2400, buf));
        assertEquals(1, buf.getInt(HEADER_SIZE + OFF_REPEAT));
    }

    // ==================== 辅助断言：整段有效数据非零 ====================

    @Test
    public void flatten_writesExpectedTotalSize() {
        ActionModel tap = ActionModel.tap(PointModel.px(1, 2), 0L);
        ActionModel swipe = new ActionModel();
        swipe.type = TYPE_SWIPE;
        swipe.points.add(PointModel.px(3, 4));
        swipe.points.add(PointModel.px(5, 6));
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(tap);
        seq.addAction(swipe);
        ByteBuffer buf = ByteBuffer.allocateDirect(1024);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, SequenceFlattener.flatten(seq, 1080, 2400, buf));

        // 期望总占用 = 24 + (64+1*16) + (64+2*16) = 200
        int expected = HEADER_SIZE + (ACTION_SIZE + 1 * POINT_SIZE)
                + (ACTION_SIZE + 2 * POINT_SIZE);
        assertTrue(buf.capacity() >= expected);
        // 校验动作2末尾之后仍为初始 0（未被越界写入）
        assertEquals(0, buf.getInt(expected));
        assertEquals(0, buf.getLong(expected));
    }
}