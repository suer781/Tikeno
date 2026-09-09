package com.tikeno.autoclicker.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tikeno.autoclicker.TikenoApp;
import com.tikeno.autoclicker.core.EngineState;
import com.tikeno.autoclicker.util.Logx;

/**
 * ScreenStateReceiver — 屏幕开关广播（架构 §2.4.6 #85）。
 *
 * 熄屏：按配置 pauseOnScreenOff 自动暂停（合规省电）；
 * 亮屏解锁（USER_PRESENT）：自动恢复。
 * 由前台服务动态注册（RECEIVER_NOT_EXPORTED，仅系统广播可达）。
 */
public class ScreenStateReceiver extends BroadcastReceiver {

    private static final String TAG = "Tikeno/Screen";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final TikenoApp app = (TikenoApp) context.getApplicationContext();
        if (app.getContainer() == null) {
            return;
        }
        final EngineState state = app.getContainer().stateStore().getState();
        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            Logx.d(TAG, "屏幕熄灭");
            if (state == EngineState.RUNNING && app.getContainer().prefs().isPauseOnScreenOff()) {
                Logx.i(TAG, "按屏幕策略暂停任务");
                app.getContainer().controller().pause();
            }
        } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
            Logx.d(TAG, "用户解锁");
            if (state == EngineState.PAUSED) {
                Logx.i(TAG, "按屏幕策略恢复任务");
                app.getContainer().controller().resume();
            }
        }
    }
}
