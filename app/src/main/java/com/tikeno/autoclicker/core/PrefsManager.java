package com.tikeno.autoclicker.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * PrefsManager — SharedPreferences 封装（架构 §2.4.2 #59）。
 * 骨架轮次：功耗档、合规确认标记、最近配置 id；悬浮窗位置等随 T04 扩展。
 */
public final class PrefsManager {

    private static final String PREFS_NAME = "tikeno_prefs";
    private static final String KEY_POWER_PROFILE = "power_profile";
    private static final String KEY_COMPLIANCE_ACK = "compliance_ack";
    private static final String KEY_LAST_CONFIG_ID = "last_config_id";
    private static final String KEY_FLOAT_ENABLED = "float_enabled";

    private final SharedPreferences prefs;

    public PrefsManager(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 功耗档：0=精度优先 1=均衡 2=省电 */
    public int getPowerProfile() {
        return prefs.getInt(KEY_POWER_PROFILE, 1);
    }

    public void setPowerProfile(int profile) {
        prefs.edit().putInt(KEY_POWER_PROFILE, profile).apply();
    }

    /** 首次启动强制合规弹窗已确认（T05 合规流程使用） */
    public boolean isComplianceAcked() {
        return prefs.getBoolean(KEY_COMPLIANCE_ACK, false);
    }

    public void setComplianceAcked(boolean acked) {
        prefs.edit().putBoolean(KEY_COMPLIANCE_ACK, acked).apply();
    }

    public String getLastConfigId() {
        return prefs.getString(KEY_LAST_CONFIG_ID, null);
    }

    public void setLastConfigId(String id) {
        prefs.edit().putString(KEY_LAST_CONFIG_ID, id).apply();
    }

    public boolean isFloatEnabled() {
        return prefs.getBoolean(KEY_FLOAT_ENABLED, true);
    }

    public void setFloatEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FLOAT_ENABLED, enabled).apply();
    }
}
