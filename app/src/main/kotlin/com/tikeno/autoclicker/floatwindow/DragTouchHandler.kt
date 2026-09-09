package com.tikeno.autoclicker.floatwindow

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * DragTouchHandler — 悬浮窗拖动手势（架构 §2.5 #113 / 调研 §2.3.4）。
 *
 * 要点（调研 §2.3.4 结论）：
 *  - 拖动用 translationX/Y 逐帧更新（纯 RenderThread 属性，零跨进程调用），
 *    松手时才回调 onDragEnd 一次性 updateViewLayout 落位；
 *  - 不依赖 OnClickListener（会被拖动误触发）：位移 < touchSlop 且
 *    时长 < 300ms 判定为点击；
 *  - ACTION_DOWN 记录 raw 坐标与 translation 起点，MOVE 实时跟随。
 */
class DragTouchHandler(
    private val view: View,
    private val onClick: () -> Unit,
    private val onDragEnd: ((View) -> Unit)? = null,
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L
    private var startTransX = 0f
    private var startTransY = 0f
    private var dragging = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downTime = event.downTime
                startTransX = view.translationX
                startTransY = view.translationY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging) {
                    // 超过 touchSlop 才进入拖动态（点击判定不受抖动影响）
                    if (dx * dx + dy * dy > touchSlop * touchSlop.toLong()) {
                        dragging = true
                    }
                }
                if (dragging) {
                    // 逐帧 translation：纯 RenderThread 属性更新，无跨进程事务
                    view.translationX = startTransX + dx
                    view.translationY = startTransY + dy
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    val elapsed = event.eventTime - downTime
                    if (event.actionMasked == MotionEvent.ACTION_UP && elapsed < 300L) {
                        onClick()   // 点击（< touchSlop 且 < 300ms）
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    // 拖动结束：由宿主把 translation 落回 params.x/y（一次 updateViewLayout）
                    onDragEnd?.invoke(view)
                }
                dragging = false
                return true
            }
            else -> return false
        }
    }

    /** 本次手势是否发生了拖动（落位回调内部判断用） */
    fun isDragging(): Boolean = dragging
}
