package com.tikeno.autoclicker.floatwindow

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

import com.tikeno.autoclicker.AppContainer
import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.core.EngineState
import com.tikeno.autoclicker.util.Logx

/**
 * FloatWindowManager — 悬浮窗总控（架构 §2.5 #110 / 调研 §2.3）。
 *
 * 窗口参数（调研 §2.3.1/§2.3.2）：
 *  - 类型：API 26+ 用 TYPE_APPLICATION_OVERLAY；API 24/25 回退 TYPE_PHONE（已废弃但唯一可用）；
 *  - Flag：NOT_FOCUSABLE（必选）| NOT_TOUCH_MODAL（区域外透传）|
 *          WATCH_OUTSIDE_TOUCH（点外部收起）| LAYOUT_NO_LIMITS（配合钳制允许贴边）；
 *  - 该组合"面板区域自消费事件、区域外透传"，不受 Android 12 不可信触摸限制。
 *
 * 拖动落位（调研 §2.3.4）：拖动期 DragTouchHandler 只改 translationX/Y，
 * 松手回调 onDragEnd 在此一次性 updateViewLayout 写回 params.x/y 并边缘吸附；
 * 球位置经 PrefsManager 持久化。
 *
 * attach 宿主：前台服务（后台启动限制，§2.3.4）。
 */
class FloatWindowManager(
    private val context: Context,
    private val app: AppContainer,
) {

    companion object {
        private const val TAG = "Tikeno/Float"
        private const val SNAP_MARGIN_PX = 12   // 边缘吸附留白
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inflater = LayoutInflater.from(context)

    private var ball: FloatBallView? = null
    private var panel: FloatPanelView? = null
    private var expanded = false
    private var attached = false

    private var lastState: EngineState = EngineState.IDLE

    // ========================================================================
    // 生命周期
    // ========================================================================

    /** 挂载悬浮球（须已有悬浮窗权限；由前台服务调用） */
    fun attach() {
        if (attached || !Settings.canDrawOverlays(context)) {
            return
        }
        try {
            val ballView = inflater.inflate(R.layout.float_ball, null) as FloatBallView
            val params = buildParams().apply {
                width = FloatBallView.DEFAULT_SIZE_PX
                height = FloatBallView.DEFAULT_SIZE_PX
                x = app.prefs().floatBallX()
                y = app.prefs().floatBallY()
            }
            ballView.setOnTouchListener(
                DragTouchHandler(
                    view = ballView,
                    onClick = { showPanel() },
                    onDragEnd = { v -> settleBall(v, params) },
                )
            )
            wm.addView(ballView, params)
            ball = ballView
            attached = true
            setBallState(lastState)
            Logx.i(TAG, "悬浮球已挂载")
        } catch (e: Exception) {
            Logx.e(TAG, "悬浮球挂载失败", e)
        }
    }

    /** 摘除全部悬浮窗（前台服务 onDestroy；幂等） */
    fun detach() {
        try {
            ball?.let { wm.removeViewImmediate(it) }
            panel?.let { wm.removeViewImmediate(it) }
        } catch (e: Exception) {
            Logx.w(TAG, "悬浮窗摘除异常", e)
        }
        ball = null
        panel = null
        expanded = false
        attached = false
    }

    fun isAttached(): Boolean = attached

    /** 引擎状态 → 悬浮球颜色 + 面板文本（StateStore 监听/心跳调用） */
    fun setBallState(state: EngineState) {
        lastState = state
        ball?.setState(
            when (state) {
                EngineState.RUNNING -> FloatBallView.STATE_RUNNING
                EngineState.PAUSED -> FloatBallView.STATE_PAUSED
                EngineState.ERROR -> FloatBallView.STATE_ERROR
                else -> FloatBallView.STATE_IDLE
            }
        )
        if (expanded) {
            panel?.updateStatus(state, app.stateStore().getProgress())
        }
    }

    // ========================================================================
    // 收起球 ⇄ 展开面板
    // ========================================================================

    private fun showPanel() {
        if (expanded || !attached) {
            return
        }
        val b = ball ?: return
        val ballLp = b.layoutParams as? WindowManager.LayoutParams ?: return
        try {
            wm.removeViewImmediate(b)
        } catch (e: Exception) {
            Logx.w(TAG, "球摘除异常", e)
        }
        val panelView = FloatPanelView(context)
        val params = buildParams().apply {
            x = ballLp.x
            y = ballLp.y
        }
        panelView.bind(app) { showBall() }
        panelView.setOnTouchListener(
            DragTouchHandler(
                view = panelView,
                onClick = { /* 面板空白区点击不折叠 */ },
                onDragEnd = { v -> settle(v, params) },
            )
        )
        wm.addView(panelView, params)
        panel = panelView
        expanded = true
        panelView.updateStatus(lastState, app.stateStore().getProgress())
    }

    private fun showBall() {
        val p = panel ?: return
        val panelLp = p.layoutParams as? WindowManager.LayoutParams
        try {
            wm.removeViewImmediate(p)
        } catch (e: Exception) {
            Logx.w(TAG, "面板摘除异常", e)
        }
        panel = null
        expanded = false
        val b = ball ?: return
        (b.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            panelLp?.let { lp.x = it.x; lp.y = it.y }
            try {
                wm.updateViewLayout(b, lp)
            } catch (e: Exception) {
                Logx.w(TAG, "球复位失败", e)
            }
        }
    }

    // ========================================================================
    // 落位与吸附（拖动结束一次性 updateViewLayout）
    // ========================================================================

    private fun settleBall(v: View, lp: WindowManager.LayoutParams) {
        settle(v, lp)
        app.prefs().setFloatBallPos(lp.x, lp.y)
    }

    private fun settle(v: View, lp: WindowManager.LayoutParams) {
        // translation → params（translation 以视图原位为基准）
        lp.x += v.translationX.toInt()
        lp.y += v.translationY.toInt()
        v.translationX = 0f
        v.translationY = 0f
        edgeSnap(lp)
        try {
            wm.updateViewLayout(v, lp)   // 一次跨进程事务落位
        } catch (e: Exception) {
            Logx.w(TAG, "落位失败", e)
        }
    }

    /** 边缘吸附：钳制回屏幕安全区；球贴左右边（最近者） */
    private fun edgeSnap(lp: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val isBall = expanded.not()
        if (isBall) {
            val maxX = (dm.widthPixels - FloatBallView.DEFAULT_SIZE_PX).coerceAtLeast(0)
            val maxY = (dm.heightPixels - FloatBallView.DEFAULT_SIZE_PX).coerceAtLeast(0)
            lp.x = lp.x.coerceIn(0, maxX)
            lp.y = lp.y.coerceIn(0, maxY)
            lp.x = if (lp.x + FloatBallView.DEFAULT_SIZE_PX / 2 < dm.widthPixels / 2) {
                0   // 贴左边
            } else {
                maxX   // 贴右边
            }
        } else {
            // 面板：不精确测宽，仅保证不越出屏幕左/上边界
            lp.x = lp.x.coerceAtLeast(0)
            lp.y = lp.y.coerceAtLeast(0)
        }
    }

    /** 通用窗口参数（调研 §2.3.1/§2.3.2） */
    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE   // API 24/25 回退
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }
}
