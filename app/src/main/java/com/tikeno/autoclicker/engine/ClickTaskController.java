package com.tikeno.autoclicker.engine;

import android.content.Context;
import android.view.Display;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.tikeno.autoclicker.AppContainer;
import com.tikeno.autoclicker.core.EngineState;
import com.tikeno.autoclicker.core.InjectionTier;
import com.tikeno.autoclicker.core.StateStore;
import com.tikeno.autoclicker.jni.NativeEngine;
import com.tikeno.autoclicker.model.ClickConfig;
import com.tikeno.autoclicker.util.ErrorCodes;
import com.tikeno.autoclicker.util.Logx;

/**
 * ClickTaskController — 业务门面（架构 §2.4.4 #75 / §6.2 时序）。
 *
 * 职责：协调 StateStore + NativeEngine + InjectionLooper + 通知，对外暴露
 * start/pause/resume/stop/updateIntervalNs/setPowerProfile。
 *
 * 资源生命周期（架构 §7.3）：
 *   ensureInitialized() 幂等创建：seqBuf/outRing/statsBuf（DirectByteBuffer）
 *   → cmdEfd/outEfd → nativeCreate + attach* → InjectionLooper → StatsReporter；
 *   shutdown() 逆序释放：先 stop → looper.stop → nativeDestroy → efd.close。
 *
 * 状态真值源：C++（statsBuf.state 旁路校验，StatsReporter 周期同步）。
 */
public final class ClickTaskController {

    private static final String TAG = "Tikeno/Ctl";

    private final Context appContext;
    private final AppContainer app;
    private final StateStore stateStore;

    // —— 共享缓冲与 fd（ensureInitialized 创建，shutdown 释放）——
    private ByteBuffer seqBuf;
    private ByteBuffer outRingBuf;
    private ByteBuffer statsBuf;
    private EventFdBridge cmdEfd;
    private EventFdBridge outEfd;
    private OutputRing outputRing;
    private StatsBuffer statsBuffer;

    // —— 引擎与组件 ——
    private long handle;   // native 句柄，0=未创建
    private InjectionLooper looper;
    private InjectionDispatcher dispatcher;
    private StatsReporter statsReporter;
    private ClickConfig currentConfig;

    private final Object lock = new Object();
    private volatile boolean initialized;
    private volatile boolean stoppedFlag;   // 紧急停止 Java 侧立即生效（§6.3）

    public ClickTaskController(Context appContext, AppContainer app) {
        this.appContext = appContext.getApplicationContext();
        this.app = app;
        this.stateStore = app.stateStore();
    }

    // ========================================================================
    // 初始化（幂等）
    // ========================================================================

    /**
     * 惰性初始化全部引擎资源。失败返回非 0 错误码（ErrorCodes）。
     * 已初始化时直接返回 0。
     */
    public int ensureInitialized() {
        synchronized (lock) {
            if (initialized) {
                return ErrorCodes.TK_OK;
            }
            try {
                return ensureInitializedLocked();
            } catch (Exception e) {
                Logx.e(TAG, "引擎初始化异常", e);
                stateStore.setState(EngineState.ERROR);
                return ErrorCodes.TK_ERR_LIB_NOT_LOADED;
            }
        }
    }

    private int ensureInitializedLocked() throws Exception {
        // 1) 共享缓冲（架构 §4.3：Java allocateDirect 分配，C++ 缓存裸地址）
        seqBuf = ByteBuffer.allocateDirect(256 * 1024).order(ByteOrder.LITTLE_ENDIAN);
        outRingBuf = ByteBuffer.allocateDirect(256 * 1024).order(ByteOrder.LITTLE_ENDIAN);
        statsBuf = ByteBuffer.allocateDirect(256).order(ByteOrder.LITTLE_ENDIAN);

        // 2) 唤醒通道（Java 公开 API 无 eventfd → Os.pipe() 单向通道；
        //    cmd：Java 写端 → C++ 读端；out：C++ 写端 → Java 读端）
        cmdEfd = EventFdBridge.create(true);
        outEfd = EventFdBridge.create(false);

        // 3) native 引擎（64B 扁平配置，见 cpp/core/types.h TkEngineConfigFlat）
        final ByteBuffer cfg = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
        cfg.putInt(0, 1);                          // schemaVersion
        cfg.putInt(4, app.prefs().getPowerProfile()); // powerProfile
        cfg.putLong(8, 200_000_000L);              // defaultIntervalNs（start 时可覆盖）
        cfg.putLong(16, 2_000_000L);               // spinThresholdNs（均衡档 2ms）
        cfg.putInt(24, 3);                         // tier = L3（桥接）
        cfg.putInt(28, 0);                         // timerBackend = timerfd
        cfg.putLong(32, 200_000_000L);             // statsPeriodNs = 200ms
        cfg.putInt(40, screenW());
        cfg.putInt(44, screenH());
        cfg.putFloat(48, 0f);                      // jitterPct
        cfg.putInt(52, 0);
        cfg.putLong(56, 0L);

        handle = NativeEngine.nativeCreate(cfg);
        if (handle == 0L) {
            Logx.e(TAG, "nativeCreate 失败");
            return ErrorCodes.TK_ERR_LIB_NOT_LOADED;
        }

        // 4) 挂载缓冲与 fd（顺序：缓冲 → fd；C++ 侧在 attachFds 时记录 efd）
        int rc = NativeEngine.nativeAttachSequenceBuffer(handle, seqBuf);
        if (rc != ErrorCodes.TK_OK) { return fail(rc); }
        rc = NativeEngine.nativeAttachOutputRing(handle, outRingBuf);
        if (rc != ErrorCodes.TK_OK) { return fail(rc); }
        rc = NativeEngine.nativeAttachStatsBuffer(handle, statsBuf);
        if (rc != ErrorCodes.TK_OK) { return fail(rc); }
        rc = NativeEngine.nativeAttachFds(handle, cmdEfd.fd(), outEfd.fd());
        if (rc != ErrorCodes.TK_OK) { return fail(rc); }

        // 5) 档位：T03 阶段固定 L3（CapabilityProbe 随后续轮次接入）
        rc = NativeEngine.nativeSetInjectorTier(handle, InjectionTier.L3_ACCESSIBILITY.code(), null);
        if (rc != ErrorCodes.TK_OK) { return fail(rc); }

        // 6) Java 侧组件
        outputRing = new OutputRing();
        rc = outputRing.attach(outRingBuf);   // C++ attach 已初始化 header（magic）
        if (rc != ErrorCodes.TK_OK) { return fail(rc); }
        statsBuffer = new StatsBuffer(statsBuf);

        dispatcher = new InjectionDispatcher(null /* 待服务绑定后注入 Handler */);
        looper = new InjectionLooper(outputRing, outEfd, dispatcher);
        looper.start();

        statsReporter = new StatsReporter(statsBuffer, stateStore, app.mainHandler());

        initialized = true;
        stateStore.setState(EngineState.IDLE);
        Logx.i(TAG, "引擎初始化完成 handle=" + handle);
        return ErrorCodes.TK_OK;
    }

    private int fail(int rc) {
        Logx.e(TAG, "初始化失败 rc=" + rc + " (" + ErrorCodes.toString(rc) + ")");
        stateStore.setState(EngineState.ERROR);
        return rc;
    }

    // ========================================================================
    // 运行控制（架构 §6.2 / §6.3）
    // ========================================================================

    /**
     * 启动任务：flatten → nativeLoadSequence → nativeStart。
     * 验收最小路径：ClickConfig.minimalSingleTap(x, y) → start。
     */
    public int start(ClickConfig config) {
        if (config == null || config.sequence == null
                || config.sequence.actionCount() <= 0) {
            return ErrorCodes.TK_ERR_INVALID_ARG;
        }
        synchronized (lock) {
            int rc = ensureInitialized();
            if (rc != ErrorCodes.TK_OK) {
                return rc;
            }
            stoppedFlag = false;
            dispatcher.setRequestedTier(InjectionTier.L3_ACCESSIBILITY.code());
            dispatcher.active().resumeDispatch();

            // 功耗档（可能被 Prefs 更新）
            NativeEngine.nativeSetPowerProfile(handle, config.powerProfile);
            NativeEngine.nativeSetIntervalNs(handle, config.defaultIntervalNs);

            // 1) 展开序列 → seqBuf（零拷贝）
            rc = SequenceFlattener.flatten(config.sequence, screenW(), screenH(), seqBuf);
            if (rc != ErrorCodes.TK_OK) {
                Logx.e(TAG, "序列展开失败 rc=" + rc);
                return rc;
            }
            // 2) 装载（C++ 解析 flat → StepPool，状态 → PREPARED）
            rc = NativeEngine.nativeLoadSequence(handle, config.sequence.actionCount());
            if (rc != ErrorCodes.TK_OK) {
                Logx.e(TAG, "nativeLoadSequence 失败 rc=" + rc);
                stateStore.setState(EngineState.ERROR);
                return rc;
            }
            currentConfig = config;
            stateStore.setState(EngineState.RUNNING);
            // 3) 启动调度线程（C++ pthread "tikeno.sched"）
            rc = NativeEngine.nativeStart(handle);
            if (rc != ErrorCodes.TK_OK) {
                Logx.e(TAG, "nativeStart 失败 rc=" + rc);
                stateStore.setState(EngineState.ERROR);
                return rc;
            }
            statsReporter.start();
            Logx.i(TAG, "任务已启动 config=" + config.name
                    + " actions=" + config.sequence.actionCount());
            return ErrorCodes.TK_OK;
        }
    }

    /** 暂停（C++ 状态机 Running→Paused；保持 deadline 上下文） */
    public int pause() {
        synchronized (lock) {
            if (!initialized) {
                return ErrorCodes.TK_ERR_BAD_STATE;
            }
            if (stoppedFlag) {
                return ErrorCodes.TK_OK;
            }
            stateStore.setState(EngineState.PAUSED);
            return NativeEngine.nativePause(handle);
        }
    }

    /** 恢复 */
    public int resume() {
        synchronized (lock) {
            if (!initialized) {
                return ErrorCodes.TK_ERR_BAD_STATE;
            }
            if (stoppedFlag) {
                return ErrorCodes.TK_OK;
            }
            return NativeEngine.nativeResume(handle);
        }
    }

    /**
     * 停止（架构 §6.3 紧急停止语义）：
     * Java 侧 stoppedFlag 立即生效 → StateStore(STOPPING) → nativeStop（≤5ms）。
     * C++ 补发在途 stroke 的 UP+SYNC 后回 IDLE；StatsReporter 轮询收敛状态。
     */
    public int stop() {
        synchronized (lock) {
            stoppedFlag = true;
            if (!initialized) {
                stateStore.setState(EngineState.IDLE);
                return ErrorCodes.TK_OK;
            }
            stateStore.setState(EngineState.STOPPING);
            dispatcher.active().cancelPending();
            final int rc = NativeEngine.nativeStop(handle);
            if (rc != ErrorCodes.TK_OK) {
                Logx.e(TAG, "nativeStop 失败 rc=" + rc);
            }
            stateStore.setState(EngineState.IDLE);
            currentConfig = null;
            Logx.i(TAG, "任务已停止");
            return rc;
        }
    }

    /** 运行中更新动作间隔（kSetParam 命令，立即生效） */
    public int updateIntervalNs(long intervalNs) {
        synchronized (lock) {
            if (!initialized) {
                return ErrorCodes.TK_ERR_BAD_STATE;
            }
            return NativeEngine.nativeSetIntervalNs(handle, intervalNs);
        }
    }

    /** 功耗档热切换（0=精度 1=均衡 2=省电） */
    public int setPowerProfile(int profile) {
        synchronized (lock) {
            if (!initialized) {
                return ErrorCodes.TK_ERR_BAD_STATE;
            }
            app.prefs().setPowerProfile(profile);
            return NativeEngine.nativeSetPowerProfile(handle, profile);
        }
    }

    // ========================================================================
    // 访问器
    // ========================================================================

    public StateStore stateStore() {
        return stateStore;
    }

    public StatsReporter statsReporter() {
        return statsReporter;   // ensureInitialized 后可用；未初始化为 null
    }

    public InjectionDispatcher dispatcher() {
        return dispatcher;
    }

    public ClickConfig currentConfig() {
        return currentConfig;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** 无障碍服务绑定/解绑（由 ClickerAccessibilityService 生命周期回调） */
    public void onAccessibilityConnected(com.tikeno.autoclicker.service.ClickerAccessibilityService s) {
        if (dispatcher != null) {
            dispatcher.bindAccessibility(s);
        }
    }

    public void onAccessibilityDisconnected() {
        if (dispatcher != null) {
            dispatcher.unbindAccessibility();
        }
    }

    /** 统一释放（TikenoApp.onTerminate；幂等、逆序） */
    public void shutdown() {
        synchronized (lock) {
            if (!initialized) {
                return;
            }
            stop();
            if (statsReporter != null) {
                statsReporter.stop();
            }
            if (looper != null) {
                looper.stop();
            }
            if (handle != 0L) {
                NativeEngine.nativeDestroy(handle);
                handle = 0L;
            }
            if (cmdEfd != null) { cmdEfd.close(); cmdEfd = null; }
            if (outEfd != null) { outEfd.close(); outEfd = null; }
            seqBuf = outRingBuf = statsBuf = null;
            outputRing = null;
            statsBuffer = null;
            dispatcher = null;
            statsReporter = null;
            initialized = false;
            Logx.i(TAG, "引擎资源已全部释放");
        }
    }

    // —— 屏幕尺寸（flatten 百分比换算；每次调用实时取，旋转后自动正确）——
    private int screenW() {
        final Display d = appContext.getDisplay();
        android.util.DisplayMetrics m = new android.util.DisplayMetrics();
        if (d != null) {
            d.getRealMetrics(m);
            return m.widthPixels;
        }
        appContext.getResources().getDisplayMetrics();
        return appContext.getResources().getDisplayMetrics().widthPixels;
    }

    private int screenH() {
        final Display d = appContext.getDisplay();
        android.util.DisplayMetrics m = new android.util.DisplayMetrics();
        if (d != null) {
            d.getRealMetrics(m);
            return m.heightPixels;
        }
        return appContext.getResources().getDisplayMetrics().heightPixels;
    }
}
