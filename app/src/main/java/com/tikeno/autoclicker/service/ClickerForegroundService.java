package com.tikeno.autoclicker.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import com.tikeno.autoclicker.TikenoApp;
import com.tikeno.autoclicker.floatwindow.FloatWindowManager;
import com.tikeno.autoclicker.util.Logx;

/**
 * ClickerForegroundService — 前台服务（架构 §2.4.6 #81 / 调研 §2.3.4）。
 *
 * 职责：
 *  1. Android 14 specialUse 类型 startForeground + 常驻控制通知；
 *  2. 悬浮窗 attach 宿主（后台启动限制：addView 必须在前台服务内，§2.3.4）；
 *  3. 15s 无障碍存活心跳（被杀则日志告警，UI 引导重新开启）；
 *  4. 屏幕开关接收器（按配置自动暂停/恢复）。
 *
 * 启停入口：MainActivity / QuickTileService / 通知。
 */
public class ClickerForegroundService extends Service {

    private static final String TAG = "Tikeno/Fgs";

    private static final long HEARTBEAT_MS = 15_000L;   // 无障碍存活心跳周期

    private NotificationHelper notificationHelper;
    private FloatWindowManager floatWindow;
    private ScreenStateReceiver screenReceiver;
    private Handler heartbeatHandler;
    private boolean screenReceiverRegistered;
    private volatile boolean foregroundStarted;

    /** 启动前台服务（幂等） */
    public static void start(Context context) {
        final Intent i = new Intent(context, ClickerForegroundService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    }

    /** 停止前台服务（幂等） */
    public static void stop(Context context) {
        context.stopService(new Intent(context, ClickerForegroundService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationHelper = new NotificationHelper(this);
        notificationHelper.ensureChannel();
        heartbeatHandler = new Handler(getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundWithType();
        registerScreenReceiver();
        attachFloatWindowIfNeeded();
        scheduleHeartbeat();
        Logx.i(TAG, "前台服务已启动");
        return START_STICKY;   // 被杀重建（任务状态由 controller 持有，重建仅恢复宿主）
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;   // 不支持绑定
    }

    @Override
    public void onDestroy() {
        heartbeatHandler.removeCallbacksAndMessages(null);
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver);
            screenReceiverRegistered = false;
        }
        if (floatWindow != null) {
            floatWindow.detach();
            floatWindow = null;
        }
        notificationHelper.cancelControl();
        foregroundStarted = false;
        Logx.i(TAG, "前台服务已停止");
        super.onDestroy();
    }

    /** specialUse 类型 startForeground（Android 14 硬性要求，T03 验收标准 2） */
    private void startForegroundWithType() {
        if (foregroundStarted) {
            return;
        }
        final Notification notif = notificationHelper.buildControlNotification(
                getString(com.tikeno.autoclicker.R.string.notif_state_idle));
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NotificationHelper.CONTROL_NOTIFICATION_ID, notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NotificationHelper.CONTROL_NOTIFICATION_ID, notif, 0);
        } else {
            startForeground(NotificationHelper.CONTROL_NOTIFICATION_ID, notif);
        }
        foregroundStarted = true;
    }

    /** 悬浮窗 attach（须有悬浮窗权限且用户开启；后台启动限制见调研 §2.3.4） */
    private void attachFloatWindowIfNeeded() {
        final TikenoApp app = (TikenoApp) getApplication();
        if (app.getContainer() == null || floatWindow != null) {
            return;
        }
        if (!android.provider.Settings.canDrawOverlays(this)
                || !app.getContainer().prefs().isFloatEnabled()) {
            return;
        }
        floatWindow = new FloatWindowManager(this, app.getContainer());
        floatWindow.attach();
    }

    /** 屏幕开关 → 按配置自动暂停/恢复（配置开关随 T04 设置页接入，默认关闭） */
    private void registerScreenReceiver() {
        if (screenReceiverRegistered) {
            return;
        }
        screenReceiver = new ScreenStateReceiver();
        final IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
        screenReceiverRegistered = true;
    }

    /** 15s 心跳：无障碍服务被杀 → 告警日志 + 悬浮球置灰（UI 恢复由用户操作） */
    private void scheduleHeartbeat() {
        heartbeatHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!ClickerAccessibilityService.isConnected()) {
                    Logx.w(TAG, "心跳：无障碍服务未连接（可能被系统关闭）");
                    if (floatWindow != null) {
                        floatWindow.setBallState(com.tikeno.autoclicker.core.EngineState.IDLE);
                    }
                }
                heartbeatHandler.postDelayed(this, HEARTBEAT_MS);
            }
        }, HEARTBEAT_MS);
    }
}
