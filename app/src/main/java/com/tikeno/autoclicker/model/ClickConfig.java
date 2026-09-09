package com.tikeno.autoclicker.model;

/**
 * ClickConfig — 一次点击任务的完整配置（架构 §2.4 数据模型）。
 * ClickTaskController.start(config) 的入参；T04 编辑器产出该结构。
 */
public final class ClickConfig {

    /** 配置唯一 id（持久化主键；运行期可由 uuid 生成） */
    public final String id;
    /** 展示名 */
    public final String name;
    /** 动作序列 */
    public final ActionSequence sequence;
    /** 全局默认动作间隔（纳秒；动作自身 intervalNs<=0 时回退到该值） */
    public final long defaultIntervalNs;
    /** 功耗档：0=精度 1=均衡 2=省电（与 TkPowerProfile 一致） */
    public final int powerProfile;
    /** 全局抖动百分比（0=关闭） */
    public final float jitterPct;
    /** 屏幕关闭时自动暂停（ScreenStateReceiver 使用） */
    public final boolean pauseOnScreenOff;

    public ClickConfig(String id, String name, ActionSequence sequence,
                       long defaultIntervalNs, int powerProfile, float jitterPct,
                       boolean pauseOnScreenOff) {
        this.id = id;
        this.name = name;
        this.sequence = sequence;
        this.defaultIntervalNs = defaultIntervalNs;
        this.powerProfile = powerProfile;
        this.jitterPct = jitterPct;
        this.pauseOnScreenOff = pauseOnScreenOff;
    }

    /**
     * 最小验收配置（T03 验收标准 4）：单点屏幕中心、200ms、无限循环。
     * 坐标为绝对像素，由调用方按实际屏幕传入。
     */
    public static ClickConfig minimalSingleTap(int x, int y) {
        ActionSequence seq = new ActionSequence(LoopPolicy.infinite());
        seq.addAction(ActionModel.tap(PointModel.px(x, y), 200_000_000L));
        return new ClickConfig("minimal", "最小验收配置", seq,
                200_000_000L, 1 /* 均衡 */, 0f, false);
    }
}
