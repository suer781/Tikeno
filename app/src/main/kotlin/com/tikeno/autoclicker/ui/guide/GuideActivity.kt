package com.tikeno.autoclicker.ui.guide

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ActivityGuideBinding

/**
 * GuideActivity — 分步权限引导页（T05 引导与合规）。
 *
 * 三步：悬浮窗 → 无障碍 → 通知。每步描述更新 + action 按钮跳转对应系统设置；
 * 最后一步点击请求通知权限（API 33+），权限回调后 finish() 返回 MainActivity；
 * 跳过按钮直接 finish()。
 */
class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding
    private lateinit var descView: TextView
    private lateinit var actionButton: Button

    /** 当前步骤（0=悬浮窗，1=无障碍，2=通知） */
    private var step: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        descView = binding.tvGuideDesc
        actionButton = binding.btnGuideAction

        binding.btnGuideSkip.setOnClickListener { finish() }
        binding.btnGuideAction.setOnClickListener { onActionClicked() }

        renderStep(step)
    }

    private fun renderStep(step: Int) {
        when (step) {
            0 -> descView.setText(R.string.guide_step_overlay_desc)
            1 -> descView.setText(R.string.guide_step_a11y_desc)
            else -> descView.setText(R.string.guide_step_notification_desc)
        }
        // 每步 action 按钮统一使用「下一步」文本（契约 T05）
        actionButton.setText(R.string.guide_next)
    }

    private fun onActionClicked() {
        when (step) {
            0 -> {
                openOverlaySettings()
                advance()
            }
            1 -> {
                openAccessibilitySettings()
                advance()
            }
            else -> requestNotificationPermission()
        }
    }

    /** 步骤 0：打开悬浮窗授权页 */
    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    /** 步骤 1：打开无障碍设置页 */
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** 步骤 2：请求通知权限（API 33+），随后结束引导 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATION,
            )
        } else {
            finish()
        }
    }

    private fun advance() {
        if (step >= 2) {
            finish()
            return
        }
        step++
        renderStep(step)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION) {
            finish()
        }
    }

    companion object {
        private const val REQ_NOTIFICATION = 1001
    }
}