package com.tikeno.autoclicker.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tikeno.autoclicker.util.Logx;

/**
 * BootReceiver — 开机广播（架构 §2.4.6 #86）。
 *
 * 合规要求（调研 §2.5）：开机【不】自动启动任何点击任务；
 * 仅重建通知渠道（保证通知入口可用）并记录启动日志。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "Tikeno/Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Logx.i(TAG, "开机完成：仅重建通知渠道，不自动启动任务（合规）");
        new NotificationHelper(context).ensureChannel();
    }
}
