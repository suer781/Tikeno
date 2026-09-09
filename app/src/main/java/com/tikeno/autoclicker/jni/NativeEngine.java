package com.tikeno.autoclicker.jni;

import java.nio.ByteBuffer;

/**
 * NativeEngine — 全部 native 方法声明（架构 §2.4.5 #78 / §5.2）。
 *
 * 约定（架构 §10.2）：
 *  - 注册方式：JNI_OnLoad 内 RegisterNatives（签名见架构 §5.3）；
 *  - 句柄：所有方法第一个参数为 long handle，非法句柄返回 TK_ERR_BAD_HANDLE；
 *  - 缓冲：所有 ByteBuffer 参数必须是 allocateDirect；
 *  - 错误：C++ 不抛异常，一律返回错误码（util/ErrorCodes 一一对应）。
 *
 * T03 起由 ClickTaskController 封装调用；本轮供 MainActivity 冒烟验证
 * （nativeGetBuildInfo → 库加载与注册成功）。
 */
public final class NativeEngine {

    // —— 生命周期（控制面）——
    /** 创建引擎。engineConfig 为 64B 扁平配置（见 cpp/core/types.h），返回 handle，失败 0 */
    public static native long nativeCreate(ByteBuffer engineConfig);
    /** 销毁引擎（停止运行线程、释放注入器）；返回 TK_OK/错误码 */
    public static native int nativeDestroy(long handle);

    // —— 缓冲与 fd 挂载（初始化期一次性完成）——
    public static native int nativeAttachSequenceBuffer(long handle, ByteBuffer seqBuf);
    public static native int nativeAttachOutputRing(long handle, ByteBuffer outRing);
    public static native int nativeAttachStatsBuffer(long handle, ByteBuffer statsBuf);
    public static native int nativeAttachFds(long handle, java.io.FileDescriptor cmdFd,
                                             java.io.FileDescriptor outFd);

    // —— 序列装载（每次 start 前调用一次）——
    public static native int nativeLoadSequence(long handle, int actionCount);

    // —— 运行控制（控制面）——
    public static native int nativeStart(long handle);
    public static native int nativePause(long handle);
    public static native int nativeResume(long handle);
    public static native int nativeStop(long handle);
    public static native int nativePostCommand(long handle, int cmd, long arg0, long arg1);

    // —— 档位与参数 ——
    public static native int nativeSetInjectorTier(long handle, int tier, String devicePath);
    public static native int nativeSetPowerProfile(long handle, int profile);
    public static native int nativeSetSpinThresholdNs(long handle, long ns);
    public static native int nativeSetIntervalNs(long handle, long ns);
    public static native int nativeNotifyResolution(long handle, int seq, int x, int y);

    // —— 查询（非热路径）——
    public static native int nativeGetState(long handle);
    public static native int nativeGetTier(long handle);
    public static native long nativeGetMissedTicks(long handle);
    /** 版本串：MainActivity 骨架验证点（Tikeno/Native: build=...） */
    public static native String nativeGetBuildInfo();

    private NativeEngine() {
        // 静态工具类禁止实例化
    }
}
