package com.tikeno.autoclicker.ui.common

import android.content.Context
import androidx.appcompat.app.AlertDialog

import com.tikeno.autoclicker.R

/** ConfirmDialogs — 通用确认/输入对话框（架构 §2.5 #96）。 */
object ConfirmDialogs {

    /** 双按钮确认框 */
    fun confirm(
        context: Context,
        title: String,
        message: String,
        positiveText: String = context.getString(R.string.dialog_ok),
        negativeText: String = context.getString(R.string.dialog_cancel),
        onPositive: () -> Unit,
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onPositive() }
            .setNegativeButton(negativeText, null)
            .show()
    }

    /** 单文本输入框 */
    fun inputText(
        context: Context,
        title: String,
        initial: String = "",
        onConfirm: (String) -> Unit,
    ) {
        val input = android.widget.EditText(context).apply {
            setText(initial)
            setSingleLine(true)
            setSelection(text.length)
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                onConfirm(input.text.toString().trim())
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
