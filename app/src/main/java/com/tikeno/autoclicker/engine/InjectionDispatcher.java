package com.tikeno.autoclicker.engine;

import android.os.Handler;

import com.tikeno.autoclicker.service.ClickerAccessibilityService;
import com.tikeno.autoclicker.util.Logx;

/**
 * InjectionDispatcher — 按档位选择注入器（架构 §2.4.4 #69）。
 *
 * 档位决策：
 *   - L3 → L3AccessibilityInjector（唯一本就绪实现）；
 *   - L0/L1/L2 → 自动降级 L3 并告警（CapabilityProbe 与 L2ShellInjector
 *     随后续轮次接入；降级符合架构"静默降级"原则）。
 *
 * 热切换：setTier() 可在运行中调用（下一次笔画生效，不中断在途手势）。
 */
public final class InjectionDispatcher {

    private static final String TAG = "Tikeno/Dispatch";

    /** 档位降级监听（StateStore.TIER_DOWNGRADED 事件源） */
    public interface DowngradeListener {
        void onTierDowngraded(int requestedTier, int actualTier);
    }

    private final Handler injectHandler;
    private volatile L3AccessibilityInjector l3;
    private volatile int requestedTier = 3;
    private volatile DowngradeListener downgradeListener;

    public InjectionDispatcher(Handler injectHandler) {
        this.injectHandler = injectHandler;
    }

    public void setDowngradeListener(DowngradeListener l) {
        this.downgradeListener = l;
    }

    /** 绑定无障碍服务（服务 onServiceConnected 后调用；幂等） */
    public synchronized void bindAccessibility(ClickerAccessibilityService service) {
        if (l3 == null && service != null) {
            l3 = new L3AccessibilityInjector(service, injectHandler);
            Logx.i(TAG, "L3 注入器已绑定");
        } else if (l3 != null && service != null) {
            l3 = new L3AccessibilityInjector(service, injectHandler);   // 服务重连：刷新实例
        }
    }

    /** 解绑（服务 onUnbind 时调用） */
    public synchronized void unbindAccessibility() {
        l3 = null;
        Logx.w(TAG, "L3 注入器已解绑（无障碍服务断开）");
    }

    /** 请求档位（0..3；L0/L1/L2 将降级为 L3） */
    public void setRequestedTier(int tier) {
        this.requestedTier = tier;
    }

    /** 当前生效的注入器（永不为 null —— 兜底返回不可用的 L3 占位，调用方以 isAvailable 判定） */
    public InjectionEngine active() {
        L3AccessibilityInjector injector = l3;
        if (injector == null) {
            return UNAVAILABLE;
        }
        if (requestedTier != 3) {
            final DowngradeListener l = downgradeListener;
            if (l != null) {
                l.onTierDowngraded(requestedTier, 3);
            }
        }
        return injector;
    }

    /** 占位注入器（服务未连接时返回；所有操作 no-op） */
    private static final InjectionEngine UNAVAILABLE = new InjectionEngine() {
        @Override public int code() { return 3; }
        @Override public boolean isAvailable() { return false; }
        @Override public void beginStroke(int s, int x, int y, long d) { }
        @Override public void moveStroke(int s, int x, int y, long d) { }
        @Override public void endStroke(int s, long d) { }
        @Override public void globalAction(int a) { }
        @Override public void cancelPending() { }
        @Override public void resumeDispatch() { }
    };
}
