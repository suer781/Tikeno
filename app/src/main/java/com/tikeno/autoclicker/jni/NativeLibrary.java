package com.tikeno.autoclicker.jni;

/**
 * NativeLibrary — native 库加载与校验（架构 §2.4.5 #77）。
 *
 * 骨架轮次职责：System.loadLibrary("tikeno") + 可读的加载异常包装。
 * 版本/ABI 校验（nativeGetBuildInfo 与 Java 期望版本比对）在 T03 接入
 * AppContainer 初始化时启用。
 */
public final class NativeLibrary {

    private static volatile boolean sLoaded = false;

    private NativeLibrary() {
        // 工具类禁止实例化
    }

    /** 加载 libtikeno.so（幂等）；失败抛 UnsatisfiedLinkError 由调用方兜底 */
    public static synchronized void load() {
        if (sLoaded) {
            return;
        }
        System.loadLibrary("tikeno");
        sLoaded = true;
    }

    public static boolean isLoaded() {
        return sLoaded;
    }
}
