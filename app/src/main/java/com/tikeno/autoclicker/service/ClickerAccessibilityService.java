package com.tikeno.autoclicker.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;

import com.tikeno.autoclicker.TikenoApp;
import com.tikeno.autoclicker.util.Logx;

/**
 * ClickerAccessibilityService — 无障碍服务（架构 §2.4.6 #80 / 调研 §2.1.1）。
 *
 * 职责：
 *  1. dispatchGesture 宿主（L3 档注入通道）；
 *  2. setServiceInfo() 动态开关 canRetrieveWindowContent
 *     （默认 false；取点器需要控件解析时才临时开启，合规要求）；
 *  3. performGlobalAction 宿主（全局动作）；
 *  4. 取点器遮罩宿主（TYPE_ACCESSIBILITY_OVERLAY，T04 接入）。
 *
 * 静态实例模式：服务由系统绑定，实例经 sInstance 暴露给
 * InjectionDispatcher/ClickTaskController（onServiceConnected/onUnbind 同步）。
 */
public class ClickerAccessibilityService extends AccessibilityService {

    private static final String TAG = "Tikeno/Acc";

    private static volatile ClickerAccessibilityService sInstance;

    /** 当前是否已连接（InjectionEngine.isAvailable 判定依据） */
    public static boolean isConnected() {
        return sInstance != null;
    }

    /** 取当前实例（可空 —— 服务未开启时为 null） */
    public static ClickerAccessibilityService peek() {
        return sInstance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        // 默认关闭窗口内容读取（合规：默认最小权限，架构 T03 验收标准 3）
        setNodeContentEnabled(false);
        Logx.i(TAG, "无障碍服务已连接");
        final TikenoApp app = (TikenoApp) getApplication();
        if (app.getContainer() != null) {
            app.getContainer().controller().onAccessibilityConnected(this);
        }
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        Logx.w(TAG, "无障碍服务已断开");
        final TikenoApp app = (TikenoApp) getApplication();
        if (app.getContainer() != null) {
            app.getContainer().controller().onAccessibilityDisconnected();
        }
        sInstance = null;
        return super.onUnbind(intent);
    }

    /**
     * 动态开关窗口内容读取（架构 §2.4.6 #80）。
     * 实现：FLAG_RETRIEVE_INTERACTIVE_WINDOWS 标志位经 setServiceInfo 热更新。
     * 注：canRetrieveWindowContent 是"能力位"（manifest 静态声明，运行期只读），
     * 运行态以标志位门控事件/节点投递（公开 API 的标准做法）。
     */
    public void setNodeContentEnabled(boolean enabled) {
        final AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            return;
        }
        if (enabled) {
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        } else {
            info.flags &= ~(AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS);
        }
        setServiceInfo(info);
        Logx.i(TAG, "窗口内容读取 → " + enabled);
    }

    /** 全局动作转发（GLOBAL_ACTION_BACK/HOME/RECENTS/NOTIFICATIONS） */
    public boolean dispatchGlobalAction(int globalAction) {
        return performGlobalAction(globalAction);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 本服务不消费无障碍事件（仅做手势注入宿主）；零成本空实现
    }

    @Override
    public void onInterrupt() {
        // 系统要求的中断回调；注入场景无中断恢复动作
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        Logx.w(TAG, "无障碍服务已销毁");
        super.onDestroy();
    }
}
