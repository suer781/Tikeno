package com.tikeno.autoclicker.floatwindow

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

import com.tikeno.autoclicker.AppContainer
import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.core.EngineState
import com.tikeno.autoclicker.model.ClickConfig

/**
 * FloatPanelView — 展开悬浮面板（架构 §2.5 #111）。
 *
 * 内容：开始/暂停/停止、间隔±、状态与进度文本。
 * 所有按钮经 ClickTaskController 下发（停止 = 紧急停止入口 1，§6.3）；
 * 状态文本由 FloatWindowManager.updatePanelTexts 驱动（StateStore 监听）。
 */
class FloatPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val INTERVAL_STEP_MS = 50L
        const val INTERVAL_MIN_MS = 5L
        const val INTERVAL_MAX_MS = 3_600_000L
        const val DEFAULT_INTERVAL_MS = 200L
    }

    private var statusText: TextView? = null
    private var progressText: TextView? = null
    private var intervalText: TextView? = null
    private var intervalMs = DEFAULT_INTERVAL_MS

    init {
        inflate(context, R.layout.float_panel, this)
        statusText = findViewById(R.id.tv_float_status)
        progressText = findViewById(R.id.tv_float_progress)
        intervalText = findViewById(R.id.tv_float_interval)
    }

    /**
     * 绑定按钮行为。
     *
     * @param onCollapse 面板折叠回调（停止按钮触发，切回收起球）
     */
    fun bind(app: AppContainer, onCollapse: () -> Unit) {
        val ctl = app.controller()
        findViewById<Button>(R.id.btn_float_play).setOnClickListener {
            if (app.stateStore().getState() == EngineState.IDLE) {
                val dm = context.resources.displayMetrics
                // T03 最小验收路径：单点屏幕中心、200ms、无限循环
                ctl.start(ClickConfig.minimalSingleTap(dm.widthPixels / 2, dm.heightPixels / 2))
            } else {
                ctl.resume()
            }
        }
        findViewById<Button>(R.id.btn_float_pause).setOnClickListener { ctl.pause() }
        findViewById<Button>(R.id.btn_float_stop).setOnClickListener {
            ctl.stop()
            onCollapse()
        }
        findViewById<Button>(R.id.btn_float_interval_down).setOnClickListener {
            intervalMs = (intervalMs - INTERVAL_STEP_MS).coerceAtLeast(INTERVAL_MIN_MS)
            ctl.updateIntervalNs(intervalMs * 1_000_000L)
            renderInterval()
        }
        findViewById<Button>(R.id.btn_float_interval_up).setOnClickListener {
            intervalMs = (intervalMs + INTERVAL_STEP_MS).coerceAtMost(INTERVAL_MAX_MS)
            ctl.updateIntervalNs(intervalMs * 1_000_000L)
            renderInterval()
        }
        renderInterval()
    }

    /** 状态文本刷新（悬浮窗管理器在 StateStore 变化时调用） */
    fun updateStatus(state: EngineState, execCount: Int) {
        statusText?.setText(
            when (state) {
                EngineState.RUNNING -> R.string.float_status_running
                EngineState.PAUSED -> R.string.float_status_paused
                EngineState.ERROR -> R.string.float_status_error
                else -> R.string.float_status_idle
            }
        )
        progressText?.text =
            context.getString(R.string.float_progress_fmt, execCount)
    }

    private fun renderInterval() {
        intervalText?.text = "${intervalMs}ms"
    }
}
