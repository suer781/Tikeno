package com.tikeno.autoclicker.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

import androidx.core.content.ContextCompat

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ViewPermissionCardBinding
import com.tikeno.autoclicker.service.ClickerAccessibilityService
import com.tikeno.autoclicker.ui.common.PermissionUiState

/**
 * PermissionCardBinder — 权限卡片渲染与跳转（架构 §2.5 #99）。
 *
 * 三项权限：悬浮窗（Settings 授权页）→ 无障碍（系统设置页）→ 通知（API 33+ 运行时）。
 * 全部就绪后卡片折叠为一行"已就绪"（点击仍可展开复核）。
 */
class PermissionCardBinder(private val binding: ViewPermissionCardBinding) {

    /** 读当前权限状态（主线程；均为本地查询，无阻塞） */
    fun readState(context: Context): PermissionUiState = PermissionUiState(
        overlayGranted = Settings.canDrawOverlays(context),
        accessibilityEnabled = ClickerAccessibilityService.isConnected(),
        notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true,
    )

    /** 渲染 + 绑定跳转（每次 onResume 调用刷新） */
    fun render(context: Context, state: PermissionUiState) {
        binding.rowOverlay.setOnClickListener { if (!state.overlayGranted) openOverlaySettings(context) }
        binding.rowAccessibility.setOnClickListener { if (!state.accessibilityEnabled) openAccessibilitySettings(context) }
        binding.rowNotification.setOnClickListener {
            if (!state.notificationsGranted) requestNotificationPermission(context)
        }

        binding.tvPermStateOverlay.setPermState(context, state.overlayGranted)
        binding.tvPermStateA11y.setPermState(context, state.accessibilityEnabled)
        binding.tvPermStateNotif.setPermState(context, state.notificationsGranted)

        binding.tvPermissionSummary.setText(
            if (state.allGranted()) R.string.permission_all_ready else R.string.permission_missing
        )
    }

    private fun openOverlaySettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun requestNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context is android.app.Activity) {
            context.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    /** 行状态渲染：已授权显示 ✓（绿色），未授权显示 ✗（红色），并保留跳转 */
    private fun android.widget.TextView.setPermState(context: Context, granted: Boolean) {
        setText(if (granted) R.string.perm_ok else R.string.perm_missing)
        setTextColor(
            ContextCompat.getColor(
                context,
                if (granted) R.color.perm_ok else R.color.perm_missing,
            )
        )
    }
}
