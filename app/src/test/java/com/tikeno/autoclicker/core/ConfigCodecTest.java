package com.tikeno.autoclicker.core;

import com.tikeno.autoclicker.model.ActionModel;
import com.tikeno.autoclicker.model.ActionSequence;
import com.tikeno.autoclicker.model.ClickConfig;
import com.tikeno.autoclicker.model.LoopPolicy;
import com.tikeno.autoclicker.model.PointModel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * ConfigCodecTest — ConfigCodec（ClickConfig ⇄ JSON）纯 JVM 单元测试。
 *
 * 覆盖 roundtrip 序列化/反序列化、LoopPolicy 还原、容错解析与版本校验。
 * 使用 org.json（JVM 可用），不依赖 Android SDK。
 */
public class ConfigCodecTest {

    // ------------------------------------------------------------------
    // 空动作序列 roundtrip
    // ------------------------------------------------------------------

    @Test
    public void testEmptySequenceRoundtrip() throws Exception {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        ClickConfig cfg = new ClickConfig("cfg-empty", "空配置", seq,
                150_000_000L, 2, 5.5f, true);

        String json = ConfigCodec.toJsonString(cfg);
        ClickConfig back = ConfigCodec.fromJson(json);

        assertEquals("id 应相等", cfg.id, back.id);
        assertEquals("name 应相等", cfg.name, back.name);
        assertEquals("intervalNs 应相等", cfg.defaultIntervalNs, back.defaultIntervalNs);
        assertEquals("powerProfile 应相等", cfg.powerProfile, back.powerProfile);
        assertEquals("jitterPct 应相等", cfg.jitterPct, back.jitterPct, 1e-6);
        assertEquals("pauseOnScreenOff 应相等", cfg.pauseOnScreenOff, back.pauseOnScreenOff);
        assertEquals("动作数量应相等（均为 0）", 0, back.sequence.actionCount());
    }

    // ------------------------------------------------------------------
    // 带动作的配置 roundtrip
    // ------------------------------------------------------------------

    @Test
    public void testActionsRoundtrip() throws Exception {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(ActionModel.tap(PointModel.px(100, 200), 250_000_000L));
        seq.addAction(ActionModel.waitMs(500));
        ClickConfig cfg = new ClickConfig("cfg-actions", "动作配置", seq,
                200_000_000L, 1, 0f, false);

        String json = ConfigCodec.toJsonString(cfg);
        ClickConfig back = ConfigCodec.fromJson(json);

        assertEquals("动作数量应相等", 2, back.sequence.actionCount());

        ActionModel a0 = back.sequence.actionsView().get(0);
        assertEquals("动作 0 类型应为 TYPE_TAP", ActionModel.TYPE_TAP, a0.type);
        assertEquals("动作 0 间隔应为 250ms", 250_000_000L, a0.intervalNs);
        assertEquals("动作 0 应包含 1 个点", 1, a0.pointCount());
        PointModel p0 = a0.pointsView().get(0);
        assertEquals("动作 0 点 x 应为 100", 100, p0.x);
        assertEquals("动作 0 点 y 应为 200", 200, p0.y);
        assertFalse("动作 0 点应为绝对像素", p0.percent);

        ActionModel a1 = back.sequence.actionsView().get(1);
        assertEquals("动作 1 类型应为 TYPE_WAIT", ActionModel.TYPE_WAIT, a1.type);
        assertEquals("动作 1 时长应为 500ms", 500, a1.durationMs);
    }

    @Test
    public void testRepeatClampedToAtLeastOne() throws Exception {
        // repeat=0 是非法的（约定 ≥1），fromJson 应钳制为 1
        String json = "{\"version\":1,\"id\":\"r\",\"name\":\"repeat\",\"intervalNs\":200000000,"
                + "\"powerProfile\":1,\"jitterPct\":0,\"pauseOnScreenOff\":false,"
                + "\"policy\":{\"kind\":0,\"maxCount\":0,\"maxDurationNs\":0},"
                + "\"actions\":[{\"type\":1,\"repeat\":0,\"durationMs\":0,\"intervalNs\":100000000,"
                + "\"holdNs\":0,\"curveType\":0,\"sampleStepUs\":8000,\"globalAction\":0,"
                + "\"jitterPct\":0,\"points\":[{\"x\":10,\"y\":20,\"percent\":false}]}]}";

        ClickConfig back = ConfigCodec.fromJson(json);
        assertEquals("repeat 传 0 时 fromJson 后应钳制为 1", 1,
                back.sequence.actionsView().get(0).repeat);
    }

    // ------------------------------------------------------------------
    // LoopPolicy roundtrip
    // ------------------------------------------------------------------

    @Test
    public void testFixedCountPolicyRoundtrip() throws Exception {
        ActionSequence seq = new ActionSequence(LoopPolicy.fixedCount(10));
        ClickConfig cfg = new ClickConfig("cfg-count", "固定次数", seq,
                200_000_000L, 1, 0f, false);

        ClickConfig back = ConfigCodec.fromJson(ConfigCodec.toJsonString(cfg));
        assertEquals("kind 应为 KIND_FIXED_COUNT", LoopPolicy.KIND_FIXED_COUNT,
                back.sequence.policy().kind);
        assertEquals("maxCount 应为 10", 10, back.sequence.policy().maxCount);
        assertEquals("maxDurationNs 应为 0", 0L, back.sequence.policy().maxDurationNs);
    }

    @Test
    public void testFixedDurationPolicyRoundtrip() throws Exception {
        LoopPolicy policy = new LoopPolicy(LoopPolicy.KIND_FIXED_DURATION, 0, 5_000_000_000L);
        ActionSequence seq = new ActionSequence(policy);
        ClickConfig cfg = new ClickConfig("cfg-duration", "固定时长", seq,
                200_000_000L, 1, 0f, false);

        ClickConfig back = ConfigCodec.fromJson(ConfigCodec.toJsonString(cfg));
        assertEquals("kind 应为 KIND_FIXED_DURATION", LoopPolicy.KIND_FIXED_DURATION,
                back.sequence.policy().kind);
        assertEquals("maxDurationNs 应为 5s", 5_000_000_000L,
                back.sequence.policy().maxDurationNs);
        assertEquals("maxCount 应为 0", 0, back.sequence.policy().maxCount);
    }

    // ------------------------------------------------------------------
    // 容错解析
    // ------------------------------------------------------------------

    @Test
    public void testMissingActionsDefaultsToEmptySequence() throws Exception {
        String json = "{\"version\":1,\"id\":\"no-actions\",\"name\":\"无动作\","
                + "\"intervalNs\":200000000,\"powerProfile\":1,\"jitterPct\":0,"
                + "\"pauseOnScreenOff\":false,"
                + "\"policy\":{\"kind\":0,\"maxCount\":0,\"maxDurationNs\":0}}";

        ClickConfig back = ConfigCodec.fromJson(json);
        assertNotNull("sequence 不应为 null", back.sequence);
        assertEquals("缺少 actions 字段时动作序列应为空", 0, back.sequence.actionCount());
    }

    @Test
    public void testInvalidActionTypeDefaultsToTap() throws Exception {
        String json = "{\"version\":1,\"id\":\"bad-type\",\"name\":\"非法类型\","
                + "\"intervalNs\":200000000,\"powerProfile\":1,\"jitterPct\":0,"
                + "\"pauseOnScreenOff\":false,"
                + "\"policy\":{\"kind\":0,\"maxCount\":0,\"maxDurationNs\":0},"
                + "\"actions\":[{\"type\":\"not-an-int\",\"repeat\":1,\"durationMs\":0,"
                + "\"intervalNs\":100000000,\"holdNs\":0,\"curveType\":0,"
                + "\"sampleStepUs\":8000,\"globalAction\":0,\"jitterPct\":0,"
                + "\"points\":[{\"x\":5,\"y\":6,\"percent\":false}]}]}";

        ClickConfig back = ConfigCodec.fromJson(json);
        assertEquals("非法 type 字段应回退为 TYPE_TAP", ActionModel.TYPE_TAP,
                back.sequence.actionsView().get(0).type);
        assertEquals("回退后动作点仍应保留", 1, back.sequence.actionsView().get(0).pointCount());
    }

    @Test
    public void testMissingPolicyDefaultsToInfinite() throws Exception {
        String json = "{\"version\":1,\"id\":\"no-policy\",\"name\":\"无策略\","
                + "\"intervalNs\":200000000,\"powerProfile\":1,\"jitterPct\":0,"
                + "\"pauseOnScreenOff\":false,"
                + "\"actions\":[]}";

        ClickConfig back = ConfigCodec.fromJson(json);
        assertNotNull("policy 不应为 null", back.sequence.policy());
        assertEquals("缺少 policy 字段时应为无限循环", LoopPolicy.KIND_INFINITE,
                back.sequence.policy().kind);
        assertEquals("无限循环 maxCount 应为 0", 0, back.sequence.policy().maxCount);
        assertEquals("无限循环 maxDurationNs 应为 0", 0L, back.sequence.policy().maxDurationNs);
    }

    // ------------------------------------------------------------------
    // 版本校验
    // ------------------------------------------------------------------

    @Test
    public void testUnsupportedVersionThrows() {
        String json = "{\"version\":99}";
        try {
            ConfigCodec.fromJson(json);
            fail("版本不匹配时应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue("异常信息应包含版本号",
                    expected.getMessage().contains("99"));
        } catch (Exception unexpected) {
            fail("应抛出 IllegalArgumentException，而不是 " + unexpected.getClass().getName());
        }
    }

    // ------------------------------------------------------------------
    // 百分比坐标 roundtrip
    // ------------------------------------------------------------------

    @Test
    public void testPercentPointRoundtrip() throws Exception {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(ActionModel.tap(PointModel.percent(500, 250), 100_000_000L));
        ClickConfig cfg = new ClickConfig("cfg-percent", "百分比坐标", seq,
                200_000_000L, 1, 0f, false);

        ClickConfig back = ConfigCodec.fromJson(ConfigCodec.toJsonString(cfg));

        PointModel p = back.sequence.actionsView().get(0).pointsView().get(0);
        assertTrue("percent 标志应保持为 true", p.percent);
        assertEquals("x 千分比应为 500", 500, p.x);
        assertEquals("y 千分比应为 250", 250, p.y);

        // 进一步验证反序列化后仍可正确换算像素
        assertEquals("换算后 px 应为 540", 540, p.toPxX(1080));
        assertEquals("换算后 py 应为 600", 600, p.toPxY(2400));
    }
}