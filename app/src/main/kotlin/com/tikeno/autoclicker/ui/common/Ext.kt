package com.tikeno.autoclicker.ui.common

import android.content.Context
import android.view.View
import android.widget.Toast

/** Ext — UI 扩展（架构 §2.5 #94）：dp/px、可见性、Toast、格式化。 */

/** dp → px（密度换算） */
fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

fun Float.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

/** View 可见性快捷（链式调用友好） */
fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun Context.toast(msg: String, long: Boolean = false) {
    Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

/** 间隔格式化：≥1000ms 显示秒，否则 ms */
fun formatIntervalNs(ns: Long): String {
    val ms = ns / 1_000_000L
    return if (ms >= 1000L) String.format("%.1fs", ms / 1000.0) else "${ms}ms"
}

/** 步数格式化：千分位 */
fun formatCount(n: Long): String = String.format("%,d", n)

/** 纳秒抖动格式化：≥1ms 显示 ms（1 位小数），否则 μs */
fun formatJitterNs(ns: Long): String =
    if (ns >= 1_000_000L) String.format("%.2fms", ns / 1_000_000.0)
    else "${ns / 1000L}μs"
