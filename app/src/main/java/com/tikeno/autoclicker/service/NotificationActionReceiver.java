package com.tikeno.autoclicker.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tikeno.autoclicker.TikenoApp;
import com.tikeno.autoclicker.util.Logx;

/**
 * NotificationActionReceiver — 通知按钮广播接收（架构 §2.4.6 #84）。
 *
 * 播放/暂停/停止三个 Action（PendingIntent 显式 Intent，exported=false）。
 * 全部为停止类入口之一（架构 §6.3：停止预算 ≤100ms，收到即调 controller）。
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "Tikeno/NotifRx";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            return;
        }
        final TikenoApp app = (TikenoApp) context.getApplicationContext();
        if (app.getContainer() == null) {
            return;
        }
        switch (action) {
            case NotificationHelper.ACTION_PLAY:
                // 播放：无已加载配置时回退最小配置由 UI 层发起；
                // 此处仅在有任务上下文时 resume
                Logx.d(TAG, "通知动作：PLAY");
                app.getContainer().controller().resume();
                break;
            case NotificationHelper.ACTION_PAUSE:
                Logx.d(TAG, "通知动作：PAUSE");
                app.getContainer().controller().pause();
                break;
            case NotificationHelper.ACTION_STOP:
                Logx.d(TAG, "通知动作：STOP（紧急停止入口 2）");
                app.getContainer().controller().stop();
                break;
            default:
                break;
        }
    }
}
