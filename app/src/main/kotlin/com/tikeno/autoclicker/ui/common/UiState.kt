package com.tikeno.autoclicker.ui.common

import com.tikeno.autoclicker.core.InjectionTier
import com.tikeno.autoclicker.model.ClickConfig

/**
 * UiState — UI 渲染用不可变数据类（架构 §2.5 #93）。
 * 全部为主线程构造、主线程消费的纯值对象。
 */

/** 权限卡片状态 */
data class PermissionUiState(
    val overlayGranted: Boolean,       // 悬浮窗（SYSTEM_ALERT_WINDOW）
    val accessibilityEnabled: Boolean, // 无障碍服务已开启
    val notificationsGranted: Boolean, // 通知权限（API 33+；低版本恒 true）
) {
    fun allGranted(): Boolean =
        overlayGranted && accessibilityEnabled && notificationsGranted
}

/** 配置列表项 */
data class ConfigUiItem(
    val id: String,
    val name: String,
    val actionSummary: String,   // 如 "3 个动作 · 单点/等待/全局"
    val intervalMs: Long,
    val loopSummary: String,     // 如 "无限循环" / "10 次"
) {
    companion object {
        fun from(config: ClickConfig, summary: String, loop: String): ConfigUiItem =
            ConfigUiItem(
                id = config.id,
                name = config.name,
                actionSummary = summary,
                intervalMs = config.defaultIntervalNs / 1_000_000L,
                loopSummary = loop,
            )
    }
}

/** 档位徽章状态 */
data class TierUiState(
    val tier: InjectionTier,
    val detail: String,      // 探测明细（如 "su + /dev/uinput 可写"）
    val probing: Boolean,
)
