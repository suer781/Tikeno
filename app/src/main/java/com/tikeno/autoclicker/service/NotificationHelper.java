package com.tikeno.autoclicker.service;

import android.app.Notification;
import android.content.Context;

import com.tikeno.autoclicker.util.Logx;

/**
 * NotificationHelper — 通知渠道与控制通知（架构 §2.4.6 #83）。
 *
 * 渠道：API 26+ 创建（低重要性 RING，不弹横幅不打扰）；控制通知带
 * 播放/暂停/停止三个 Action（PendingIntent → NotificationActionReceiver）。
 */
public final class NotificationHelper {

    private static final String TAG = "Tikeno/Notif";

    public static final String CHANNEL_ID = "tikeno_control";
    public static final int CONTROL_NOTIFICATION_ID = 1001;

    // 通知按钮动作（NotificationActionReceiver 分发）
    public static final String ACTION_PLAY = "com.tikeno.autoclicker.action.PLAY";
    public static final String ACTION_PAUSE = "com.tikeno.autoclicker.action.PAUSE";
    public static final String ACTION_STOP = "com.tikeno.autoclicker.action.STOP";

    private final Context context;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 幂等创建通知渠道（API 26+；低版本无渠道概念） */
    public void ensureChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            final android.app.NotificationManager nm =
                    context.getSystemService(android.app.NotificationManager.class);
            if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
                return;
            }
            final android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    CHANNEL_ID,
                    context.getString(com.tikeno.autoclicker.R.string.notif_channel_name),
                    android.app.NotificationManager.IMPORTANCE_LOW);   // 常驻但不响铃
            ch.setDescription(
                    context.getString(com.tikeno.autoclicker.R.string.notif_channel_desc));
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            Logx.d(TAG, "通知渠道已创建");
        }
    }

    /**
     * 构建控制通知：内容点击回主界面；三个 Action（播放/暂停/停止）。
     *
     * @param stateText 当前状态文案（如"运行中 · 200ms"）
     */
    public Notification buildControlNotification(String stateText) {
        ensureChannel();
        final android.content.Intent main = new android.content.Intent(
                context, com.tikeno.autoclicker.ui.MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        final android.app.PendingIntent contentPi = android.app.PendingIntent.getActivity(
                context, 0, main,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | pendingImmutableFlag());

        final Notification.Builder b = (android.os.Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(context.getString(com.tikeno.autoclicker.R.string.notif_title))
                .setContentText(stateText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPi);

        b.addAction(0,
                context.getString(com.tikeno.autoclicker.R.string.action_play),
                actionPi(ACTION_PLAY, 1));
        b.addAction(0,
                context.getString(com.tikeno.autoclicker.R.string.action_pause),
                actionPi(ACTION_PAUSE, 2));
        b.addAction(0,
                context.getString(com.tikeno.autoclicker.R.string.action_stop),
                actionPi(ACTION_STOP, 3));
        return b.build();
    }

    /** 发送/更新控制通知 */
    public void notifyControl(String stateText) {
        final android.app.NotificationManager nm =
                context.getSystemService(android.app.NotificationManager.class);
        if (nm != null) {
            nm.notify(CONTROL_NOTIFICATION_ID, buildControlNotification(stateText));
        }
    }

    /** 撤销控制通知 */
    public void cancelControl() {
        final android.app.NotificationManager nm =
                context.getSystemService(android.app.NotificationManager.class);
        if (nm != null) {
            nm.cancel(CONTROL_NOTIFICATION_ID);
        }
    }

    private android.app.PendingIntent actionPi(String action, int requestCode) {
        final android.content.Intent i = new android.content.Intent(context,
                NotificationActionReceiver.class).setAction(action);
        return android.app.PendingIntent.getBroadcast(
                context, requestCode, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | pendingImmutableFlag());
    }

    /** API 23+ 强制 FLAG_IMMUTABLE（Android 12 起必需） */
    private static int pendingImmutableFlag() {
        return android.app.PendingIntent.FLAG_IMMUTABLE;
    }
}
