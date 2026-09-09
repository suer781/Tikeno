package com.tikeno.autoclicker.jni;

/**
 * NativeCallback — native 侧控制面回调接口（架构 §2.4.5 #79）。
 *
 * 仅控制面回调（onStateChanged / onFinished / onError / onTierDowngraded），
 * 执行循环内零 upcall（架构 §1.2 难点 1 / §5.4）。
 * T03 引入 JNI upcall 通道时由 AppContainer 注册到 NativeEngine。
 */
public interface NativeCallback {

    /** 引擎状态变化（Idle/Prepared/Running/Paused/Stopping/Error） */
    void onStateChanged(int newState);

    /** 序列正常执行完毕（含循环策略终止） */
    void onFinished(long execCount, long missedTicks);

    /** 执行出错（errorCode 见 util/ErrorCodes） */
    void onError(int errorCode, String message);

    /** 注入档位发生降级（L0→L1→L2→L3），actualTier 为降级后的档位 */
    void onTierDowngraded(int requestedTier, int actualTier);
}
