package com.tikeno.autoclicker.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

import com.tikeno.autoclicker.R
import kotlin.math.max

/**
 * JitterChartView — 抖动柱状图（架构 §2.5 #116，Canvas 自绘，无图表库）。
 *
 * 数据语义：[data] 为各分位/统计指标对应的抖动值（单位 ms），例如
 * [P50, P95, P99, Mean, Max]。绘制时按当前最大值归一化柱高；
 * 无数据时绘制居中占位文字。
 */
class JitterChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    /** 抖动值序列（单位 ms）。setter 触发重绘。 */
    var data: List<Float> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x1E, 0x88, 0xE5)
        style = Paint.Style.FILL
    }

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x9E, 0x9E, 0x9E)
        textSize = 14f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x21, 0x21, 0x21)
        textSize = 10f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) {
            drawEmpty(canvas)
            return
        }
        drawBars(canvas)
    }

    private fun drawEmpty(canvas: Canvas) {
        val text = context.getString(R.string.stats_empty)
        val baseline = height / 2f - (emptyPaint.descent() + emptyPaint.ascent()) / 2f
        canvas.drawText(text, width / 2f, baseline, emptyPaint)
    }

    private fun drawBars(canvas: Canvas) {
        val maxValue = data.maxOrNull() ?: return
        if (maxValue <= 0f) {
            drawEmpty(canvas)
            return
        }

        val barCount = data.size
        val padding = 8f * resources.displayMetrics.density
        val labelHeight = 18f * resources.displayMetrics.density
        val chartBottom = height - padding - labelHeight
        val chartTop = padding
        val chartHeight = max(1f, chartBottom - chartTop)
        val slot = width / barCount.toFloat()
        val barWidth = slot * 0.6f
        val labelBaseline = height - padding

        for (i in data.indices) {
            val value = data[i]
            val barHeight = chartHeight * (value / maxValue)
            val left = i * slot + (slot - barWidth) / 2f
            val right = left + barWidth
            val top = chartBottom - barHeight
            canvas.drawRoundRect(left, top, right, chartBottom, 4f, 4f, barPaint)

            // 柱顶数值（ms，1 位小数）
            val valueLabel = if (value >= 1000f) {
                String.format("%.1fs", value / 1000f)
            } else {
                String.format("%.1f", value)
            }
            canvas.drawText(valueLabel, left + barWidth / 2f, top - 4f * resources.displayMetrics.density, labelPaint)
        }

        // 柱子标签（P50/P95/P99/Mean/Max，与数据顺序一一对应）
        val labels = listOf("P50", "P95", "P99", "Mean", "Max")
        for (i in labels.indices) {
            if (i >= data.size) break
            val centerX = i * slot + slot / 2f
            canvas.drawText(labels[i], centerX, labelBaseline, labelPaint)
        }
    }
}