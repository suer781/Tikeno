package com.tikeno.autoclicker;

import android.app.Application;

import com.tikeno.autoclicker.jni.NativeLibrary;
import com.tikeno.autoclicker.jni.NativeEngine;
import com.tikeno.autoclicker.util.Logx;

/**
 * TikenoApp — Application（Java，架构 §2.4.1 #45）。
 *
 * 骨架轮次职责：构建 AppContainer、加载 native 库、全局异常钩子。
 * 版本/ABI 校验（nativeGetBuildInfo）在 loadLibrary 后立即验证，
 * Logcat 出现 "Tikeno/Native: build=..." 即 JNI 注册成功（T01 验收项 2）。
 */
public class TikenoApp extends Application {

    private static final String TAG = "Tikeno/App";

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        // 全局异常钩子：崩溃前至少留一条可读日志（Release 亦可定位）
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Logx.e(TAG, "未捕获异常 thread=" + t.getName(), e);
            if (prev != null) {
                prev.uncaughtException(t, e);
            }
        });

        NativeLibrary.load();
        // JNI 注册自检：取版本串（失败抛 UnsatisfiedLinkError，异常钩子兜底）
        final String buildInfo = NativeEngine.nativeGetBuildInfo();
        Logx.i(TAG, "native build=" + buildInfo);

        container = new AppContainer(this);
        container.init();
    }

    public AppContainer getContainer() {
        return container;
    }

    @Override
    public void onTerminate() {
        if (container != null) {
            container.shutdown();
        }
        super.onTerminate();
    }
}
