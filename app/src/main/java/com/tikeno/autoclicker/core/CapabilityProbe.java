package com.tikeno.autoclicker.core;

import android.content.Context;

import com.tikeno.autoclicker.util.Logx;

import java.io.DataInputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CapabilityProbe — 注入档位探测（架构 §2.4.3 #61 / 调研 §2.1.6）。
 *
 * 探测链（先到先得，带 3s 单项超时、结果缓存、静默降级）：
 *   L0 Root：`su -c id` 可执行 → 检查 /dev/uinput 可写
 *   L1 Root：su 可执行 → /dev/input/eventX 可写
 *   L2 Shell：Shizuku 已安装且已授权（simplified：安装 + ping）
 *   L3 无障碍：兜底（恒可用）
 *
 * 线程归属：probe() 必须在 IO 线程调用（阻塞探测）；结果缓存于原子引用。
 * T03/T04 阶段探测结果仅用于 UI 档位徽章展示；实际档位切换（含
 * L2ShellInjector 与设备 chmod）随 T05 接入。
 */
public final class CapabilityProbe {

    private static final String TAG = "Tikeno/Probe";
    private static final long PROBE_TIMEOUT_MS = 3000L;

    /** 探测结果（不可变） */
    public static final class Result {
        public final InjectionTier tier;      // 探测到的最高可用档
        public final String detail;           // 可读说明（UI 展示）

        Result(InjectionTier tier, String detail) {
            this.tier = tier;
            this.detail = detail;
        }
    }

    private static final AtomicReference<Result> CACHE =
            new AtomicReference<>(new Result(InjectionTier.L3_ACCESSIBILITY, "未探测"));

    private CapabilityProbe() {
    }

    /** 最近一次探测结果（主线程可读） */
    public static Result cached() {
        return CACHE.get();
    }

    /**
     * 执行探测（阻塞；务必在 IO 线程调用）。任何单项超时/异常按该档不可用
     * 处理，最终兜底 L3（静默降级，不抛异常）。
     */
    public static Result probe(Context appContext) {
        Result result = probeInner(appContext);
        CACHE.set(result);
        Logx.i(TAG, "档位探测结果：" + result.tier.displayName() + "（" + result.detail + "）");
        return result;
    }

    private static Result probeInner(Context appContext) {
        // —— L2：Shizuku（安装 + 可 ping；授权态检查随 T05 深化）——
        boolean shizukuOk = withTimeout(() -> isShizukuAvailable(appContext), 800L);
        // —— Root（su）探测：L0/L1 前置 ——
        boolean suOk = withTimeout(CapabilityProbe::hasSu, 1500L);
        if (suOk) {
            if (withTimeout(CapabilityProbe::isUinputWritable, 500L)) {
                return new Result(InjectionTier.L0_UINPUT, "su + /dev/uinput 可写");
            }
            if (withTimeout(CapabilityProbe::isEventXWritable, 500L)) {
                return new Result(InjectionTier.L1_EVDEV, "su + /dev/input/eventX 可写");
            }
            return new Result(InjectionTier.L3_ACCESSIBILITY, "su 可用但注入设备不可写");
        }
        if (shizukuOk) {
            return new Result(InjectionTier.L2_SHELL, "Shizuku 已就绪");
        }
        return new Result(InjectionTier.L3_ACCESSIBILITY, "无 Root/Shizuku，使用无障碍档");
    }

    // ------------------------------------------------------------------
    // 单项探测（阻塞实现，由 withTimeout 包裹）
    // ------------------------------------------------------------------

    private interface BlockingCheck {
        boolean run() throws Exception;
    }

    /** 带超时执行（守护线程 + join；超时按 false，线程泄漏上限为探测频度） */
    private static boolean withTimeout(BlockingCheck check, long timeoutMs) {
        final AtomicReference<Boolean> ok = new AtomicReference<>(Boolean.FALSE);
        final Thread t = new Thread(() -> {
            try {
                ok.set(check.run());
            } catch (Exception e) {
                ok.set(Boolean.FALSE);
            }
        }, "tikeno.probe.item");
        t.setDaemon(true);
        t.start();
        try {
            t.join(timeoutMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        // 超时：不 join 死等，返回 false（守护线程自行消亡）
        return Boolean.TRUE.equals(ok.get()) && !t.isAlive();
    }

    /** `su -c id` 是否可执行（L0/L1 前置） */
    private static boolean hasSu() throws Exception {
        final Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
        try (DataInputStream in = new DataInputStream(p.getInputStream())) {
            final byte[] buf = new byte[64];
            in.readFully(buf, 0, 4);
        }
        p.waitFor();
        p.destroy();
        return true;
    }

    /** /dev/uinput 存在且可写（L0） */
    private static boolean isUinputWritable() {
        final File uinput = new File("/dev/uinput");
        if (!uinput.exists()) {
            return false;
        }
        try (OutputStream os = new java.io.FileOutputStream(uinput)) {
            return true;   // 打开成功即可写（不写数据立即关闭）
        } catch (Exception e) {
            return false;
        }
    }

    /** /dev/input/event0 存在且可写（L1） */
    private static boolean isEventXWritable() {
        final File ev = new File("/dev/input/event0");
        if (!ev.exists()) {
            return false;
        }
        try (OutputStream os = new java.io.FileOutputStream(ev)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Shizuku 是否安装（授权检测随 T05；此处仅判安装） */
    private static boolean isShizukuAvailable(Context appContext) {
        try {
            appContext.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
