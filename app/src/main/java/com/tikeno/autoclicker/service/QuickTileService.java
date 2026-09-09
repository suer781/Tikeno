package com.tikeno.autoclicker.service;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.tikeno.autoclicker.TikenoApp;
import com.tikeno.autoclicker.core.EngineState;
import com.tikeno.autoclicker.core.StateStore;
import com.tikeno.autoclicker.util.Logx;

/**
 * QuickTileService — QS 快捷磁贴（架构 §2.4.6 #82 / API 24+）。
 *
 * 一键启停 + 状态回显（灰=停止 / 蓝=运行）：
 *  - onClick：RUNNING/PAUSED → stop；IDLE → start 最小配置（T04 起用最近配置）；
 *  - onStartListening 注册状态监听，onStopListening 注销（磁贴可见期才回调）。
 *  停止入口之三（架构 §6.3）。
 */
public class QuickTileService extends TileService {

    private static final String TAG = "Tikeno/Tile";

    private final StateStore.Listener stateListener = state -> updateTile(state);

    @Override
    public void onStartListening() {
        super.onStartListening();
        final TikenoApp app = (TikenoApp) getApplication();
        if (app.getContainer() != null) {
            app.getContainer().stateStore().addStateListener(stateListener);
        }
        updateTile(currentState());
        Logx.d(TAG, "磁贴开始监听");
    }

    @Override
    public void onStopListening() {
        final TikenoApp app = (TikenoApp) getApplication();
        if (app.getContainer() != null) {
            app.getContainer().stateStore().removeStateListener(stateListener);
        }
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        final TikenoApp app = (TikenoApp) getApplication();
        if (app.getContainer() == null) {
            return;
        }
        final EngineState state = currentState();
        if (state == EngineState.RUNNING || state == EngineState.PAUSED) {
            Logx.i(TAG, "磁贴停止（紧急停止入口 3）");
            app.getContainer().controller().stop();
        } else {
            Logx.i(TAG, "磁贴启动（最小验收配置 · 屏幕中心）");
            final android.util.DisplayMetrics m = getResources().getDisplayMetrics();
            // 最小验收配置：单点屏幕中心、200ms、无限循环（T03 验收标准 4）
            final com.tikeno.autoclicker.model.ClickConfig cfg =
                    com.tikeno.autoclicker.model.ClickConfig.minimalSingleTap(
                            m.widthPixels / 2, m.heightPixels / 2);
            app.getContainer().controller().start(cfg);
        }
        updateTile(currentState());
    }

    private EngineState currentState() {
        final TikenoApp app = (TikenoApp) getApplication();
        if (app.getContainer() != null) {
            return app.getContainer().stateStore().getState();
        }
        return EngineState.IDLE;
    }

    private void updateTile(EngineState state) {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        final boolean active = state == EngineState.RUNNING || state == EngineState.PAUSED;
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(state == EngineState.RUNNING
                ? getString(com.tikeno.autoclicker.R.string.tile_state_running)
                : getString(com.tikeno.autoclicker.R.string.tile_state_idle));
        tile.updateTile();
    }
}
