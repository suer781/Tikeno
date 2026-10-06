package com.tikeno.autoclicker.ui.guide

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.TikenoApp

/**
 * ComplianceDialog — 首次启动合规声明（T05 引导与合规）。
 *
 * 从 [TikenoApp] 容器读取 [com.tikeno.autoclicker.core.PrefsManager.isComplianceAcked]；
 * 未确认时弹出不可取消的 AlertDialog，用户点击「同意并继续」后落库并回调 [onAck]。
 */
object ComplianceDialog {

    /**
     * 按需展示合规弹窗。
     *
     * @param activity 宿主 Activity（AppCompatActivity）
     * @param onAck    已确认合规后回调（若无需弹窗则立即回调）
     */
    @JvmStatic
    fun showIfNeeded(activity: AppCompatActivity, onAck: () -> Unit) {
        val app = activity.application as? TikenoApp
        val prefs = app?.container?.prefs()
        if (prefs?.isComplianceAcked() == true) {
            onAck.invoke()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.compliance_title)
            .setMessage(R.string.compliance_message)
            .setCancelable(false)
            .setPositiveButton(R.string.compliance_accept) { _, _ ->
                prefs?.setComplianceAcked(true)
                onAck.invoke()
            }
            .show()
    }
}