package com.tikeno.autoclicker.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * ModelTest — 数据模型层纯 JVM 单元测试。
 *
 * 覆盖 PointModel / ActionModel / LoopPolicy / ActionSequence / ClickConfig
 * 的工厂方法与基本语义。不依赖 Android SDK，可直接在本地 JVM 运行。
 */
public class ModelTest {

    // ------------------------------------------------------------------
    // PointModel
    // ------------------------------------------------------------------

    @Test
    public void testPointPxAbsolute() {
        PointModel p = PointModel.px(100, 200);
        assertFalse("px 坐标 percent 应为 false", p.percent);
        assertEquals(100, p.x);
        assertEquals(200, p.y);
        assertEquals("px 坐标在 1080 宽下应原样返回", 100, p.toPxX(1080));
        assertEquals("px 坐标在 2400 高下应原样返回", 200, p.toPxY(2400));
    }

    @Test
    public void testPointPercentConversion() {
        PointModel p = PointModel.percent(500, 250);
        assertTrue("percent 坐标 percent 应为 true", p.percent);
        assertEquals(500, p.x);
        assertEquals(250, p.y);
        assertEquals("千分比 500/1000 × 1080 = 540", 540, p.toPxX(1080));
        assertEquals("千分比 250/1000 × 2400 = 600", 600, p.toPxY(2400));
    }

    // ------------------------------------------------------------------
    // ActionModel
    // ------------------------------------------------------------------

    @Test
    public void testTapFactory() {
        PointModel p = PointModel.px(10, 20);
        ActionModel a = ActionModel.tap(p, 200_000_000L);
        assertEquals("tap 类型应为 TYPE_TAP", ActionModel.TYPE_TAP, a.type);
        assertEquals("tap 应包含 1 个点", 1, a.pointCount());
        assertEquals("tap 间隔应为传入值", 200_000_000L, a.intervalNs);
        assertSame("tap 的点应与传入 PointModel 相同", p, a.pointsView().get(0));
    }

    @Test
    public void testWaitMsFactory() {
        ActionModel a = ActionModel.waitMs(500);
        assertEquals("wait 类型应为 TYPE_WAIT", ActionModel.TYPE_WAIT, a.type);
        assertEquals("wait 时长应为 500ms", 500, a.durationMs);
        assertEquals("wait 不应叠加额外间隔", 0L, a.intervalNs);
        assertEquals("wait 不应包含点", 0, a.pointCount());
    }

    @Test
    public void testGlobalFactory() {
        ActionModel a = ActionModel.global(ActionModel.GLOBAL_BACK, 100L);
        assertEquals("global 类型应为 TYPE_GLOBAL", ActionModel.TYPE_GLOBAL, a.type);
        assertEquals("globalAction 应为 GLOBAL_BACK", ActionModel.GLOBAL_BACK, a.globalAction);
        assertEquals("global 间隔应为传入值", 100L, a.intervalNs);
    }

    // ------------------------------------------------------------------
    // LoopPolicy
    // ------------------------------------------------------------------

    @Test
    public void testInfinitePolicy() {
        LoopPolicy p = LoopPolicy.infinite();
        assertEquals("infinite kind 应为 KIND_INFINITE", LoopPolicy.KIND_INFINITE, p.kind);
        assertEquals("infinite maxCount 应为 0", 0, p.maxCount);
        assertEquals("infinite maxDurationNs 应为 0", 0L, p.maxDurationNs);
    }

    @Test
    public void testFixedCountPolicy() {
        LoopPolicy p = LoopPolicy.fixedCount(5);
        assertEquals("fixedCount kind 应为 KIND_FIXED_COUNT", LoopPolicy.KIND_FIXED_COUNT, p.kind);
        assertEquals("fixedCount maxCount 应为 5", 5, p.maxCount);
        assertEquals("fixedCount maxDurationNs 应为 0", 0L, p.maxDurationNs);
    }

    // ------------------------------------------------------------------
    // ActionSequence
    // ------------------------------------------------------------------

    @Test
    public void testSequenceAddAndCount() {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        assertEquals("空序列 actionCount 应为 0", 0, seq.actionCount());

        seq.addAction(ActionModel.tap(PointModel.px(1, 1), 0L));
        seq.addAction(ActionModel.waitMs(100));
        seq.addAction(ActionModel.global(ActionModel.GLOBAL_HOME, 0L));
        assertEquals("添加 3 个动作后 actionCount 应为 3", 3, seq.actionCount());
    }

    @Test
    public void testSequenceActionsViewUnmodifiable() {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(ActionModel.waitMs(10));

        // actionsView 返回只读视图：任何修改都应抛 UnsupportedOperationException
        try {
            seq.actionsView().add(ActionModel.tap(PointModel.px(0, 0), 0L));
            fail("actionsView 不应允许 add 操作");
        } catch (UnsupportedOperationException expected) {
            // 预期异常
        }

        try {
            seq.actionsView().clear();
            fail("actionsView 不应允许 clear 操作");
        } catch (UnsupportedOperationException expected) {
            // 预期异常
        }
    }

    @Test
    public void testSequencePolicy() {
        LoopPolicy policy = LoopPolicy.fixedCount(7);
        ActionSequence seq = new ActionSequence(policy);
        assertSame("policy() 应返回构造时传入的策略对象", policy, seq.policy());
    }

    @Test
    public void testSequenceNullPolicyDefaultsToInfinite() {
        ActionSequence seq = new ActionSequence(null);
        assertNotNull("传入 null 策略时不应返回 null", seq.policy());
        assertEquals("传入 null 策略应回退为 KIND_INFINITE",
                LoopPolicy.KIND_INFINITE, seq.policy().kind);
    }

    // ------------------------------------------------------------------
    // ClickConfig
    // ------------------------------------------------------------------

    @Test
    public void testMinimalSingleTap() {
        ClickConfig cfg = ClickConfig.minimalSingleTap(200, 400);
        assertEquals("id 应为 minimal", "minimal", cfg.id);
        assertNotNull("name 不应为空", cfg.name);
        assertTrue("name 应为非空字符串", cfg.name.length() > 0);
        assertNotNull("sequence 不应为 null", cfg.sequence);
        assertEquals("sequence 应包含 1 个动作", 1, cfg.sequence.actionCount());
        assertEquals("defaultIntervalNs 应为 200ms", 200_000_000L, cfg.defaultIntervalNs);
        assertEquals("powerProfile 应为 1（均衡）", 1, cfg.powerProfile);
        assertFalse("pauseOnScreenOff 应为 false", cfg.pauseOnScreenOff);

        ActionModel action = cfg.sequence.actionsView().get(0);
        assertEquals("动作类型应为 TYPE_TAP", ActionModel.TYPE_TAP, action.type);
        assertEquals("动作点应为 (200,400)", 200, action.pointsView().get(0).x);
        assertEquals("动作点 y 应为 400", 400, action.pointsView().get(0).y);
    }
}