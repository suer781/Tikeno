package com.tikeno.autoclicker.floatwindow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * FloatBallView — 收起态悬浮球（架构 §2.5 #112）。
 *
 * 状态色（架构 §6.2）：
 *  - 灰 #9E9E9E：空闲（引擎 IDLE）
 *  - 绿 #4CAF50：运行中（RUNNING）
 *  - 黄 #FFC107：暂停（PAUSED）
 *  - 红 #E53935：错误
 * Canvas 直绘圆 + 高光环（无图片资源，体积与加载开销最小）。
 */
class FloatBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val STATE_IDLE = 0
        const val STATE_RUNNING = 1
        const val STATE_PAUSED = 2
        const val STATE_ERROR = 3

        const val COLOR_IDLE = 0xFF9E9E9E.toInt()
        const val COLOR_RUNNING = 0xFF4CAF50.toInt()
        const val COLOR_PAUSED = 0xFFFFC107.toInt()
        const val COLOR_ERROR = 0xFFE53935.toInt()
        const val COLOR_RING = 0x66FFFFFF.toInt()

        /** 球默认尺寸（px；布局层负责密度换算） */
        const val DEFAULT_SIZE_PX = 96
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = COLOR_RING
    }

    private var state = STATE_IDLE

    fun setState(newState: Int) {
        if (state != newState) {
            state = newState
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - ringPaint.strokeWidth
        fillPaint.color = colorFor(state)
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r - ringPaint.strokeWidth / 2f, ringPaint)
    }

    private fun colorFor(state: Int): Int = when (state) {
        STATE_RUNNING -> COLOR_RUNNING
        STATE_PAUSED -> COLOR_PAUSED
        STATE_ERROR -> COLOR_ERROR
        else -> COLOR_IDLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = resolveSize(DEFAULT_SIZE_PX, widthMeasureSpec)
        setMeasuredDimension(size, size)
    }
}
