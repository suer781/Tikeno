package com.tikeno.autoclicker;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;

import com.tikeno.autoclicker.core.PrefsManager;
import com.tikeno.autoclicker.core.StateStore;
import com.tikeno.autoclicker.util.Logx;
import com.tikeno.autoclicker.util.Threadx;

/**
 * AppContainer — 手写依赖容器（架构 §2.4.1 #46；禁 DI 框架，PRD P0）。
 *
 * 骨架轮次：mainHandler + ioExecutor + StateStore + PrefsManager + 统一释放。
 * 注入器/任务门面/探测线程（InjectionLooper / ClickTaskController /
 * CapabilityProbe / ConfigRepository）随 T03 扩展。
 * 生命周期：TikenoApp.onCreate 构造，App.shutdown 时 shutdown()。
 */
public final class AppContainer {

    private static final String TAG = "Tikeno/App";

    private final Handler mainHandler;
    private final ExecutorService ioExecutor;
    private final StateStore stateStore;
    private final PrefsManager prefsManager;
    private volatile boolean shutdown;

    public AppContainer(android.content.Context context) {
        mainHandler = new Handler(Looper.getMainLooper());
        ioExecutor = Threadx.newNamedSingleExecutor("tikeno.io");
        stateStore = new StateStore();
        prefsManager = new PrefsManager(context);
    }

    public void init() {
        Logx.i(TAG, "AppContainer 初始化完成");
    }

    /** 主线程投递（架构分层不变式：UI 只被 mainHandler 更新） */
    public void postMain(Runnable r) {
        if (!shutdown) {
            mainHandler.post(r);
        }
    }

    /** 延迟主线程投递（悬浮窗节流等场景） */
    public void postMainDelayed(Runnable r, long delayMs) {
        if (!shutdown) {
            mainHandler.postDelayed(r, delayMs);
        }
    }

    /** IO 单线程执行器（配置读写 / CSV 导出；T03 扩展使用） */
    public ExecutorService io() {
        return ioExecutor;
    }

    public StateStore stateStore() {
        return stateStore;
    }

    public PrefsManager prefs() {
        return prefsManager;
    }

    public Handler mainHandler() {
        return mainHandler;
    }

    /** 统一释放（架构 §7.3 泄漏防护：顺序明确、幂等） */
    public void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        mainHandler.removeCallbacksAndMessages(null);
        ioExecutor.shutdown();
        Logx.i(TAG, "AppContainer 已释放");
    }
}
