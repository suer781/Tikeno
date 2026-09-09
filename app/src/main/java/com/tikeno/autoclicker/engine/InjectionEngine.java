package com.tikeno.autoclicker.engine;

/**
 * InjectionEngine — Java 侧注入接口（架构 §2.4.4 #66）。
 *
 * 两类 API：
 *  1. 完整手势：tap / longPress / swipe / multiTouch —— 供 UI 层直接调用；
 *  2. 原子步流（beginStroke/moveStroke/endStroke）：供 InjectionLooper 消费
 *     outRing 的 TkStep 流（DOWN/MOVE/UP 按槽位聚合后还原为完整手势）。
 *
 * 实现方保证：dispatch 回调全部投递到注入线程 Handler，不占主线程
 * （架构 §2.4.4 #67 注释 / §7.3）。
 */
public interface InjectionEngine {

    /** 当前档位 */
    InjectionTierHolder tier();

    /** 注入通道是否可用（如 L3 需无障碍服务已连接） */
    boolean isAvailable();

    // —— 原子步流 API（InjectionLooper 使用）——

    /** 槽位开始笔画（对应 TkStep.KIND_DOWN） */
    void beginStroke(int slot, int x, int y, long delayNs);

    /** 槽位追加移动点（对应 TkStep.KIND_MOVE） */
    void moveStroke(int slot, int x, int y, long delayNs);

    /** 槽位结束笔画并派发手势（对应 TkStep.KIND_UP） */
    void endStroke(int slot, long delayNs);

    /** 全局动作（arg 与 AccessibilityService.GLOBAL_ACTION_* 同值） */
    void globalAction(int globalAction);

    /** 取消在途手势并暂停新派发（紧急停止语义，架构 §6.3） */
    void cancelPending();

    /** 恢复派发（新序列开始前调用，与 cancelPending 配对） */
    void resumeDispatch();

    /**
     * 注入器档位标识（避免直接依赖 core.InjectionTier 造成包耦合方向倒置；
     * 值与 core.InjectionTier.code() 一致）。
     */
    interface InjectionTierHolder {
        int code();
    }
}
