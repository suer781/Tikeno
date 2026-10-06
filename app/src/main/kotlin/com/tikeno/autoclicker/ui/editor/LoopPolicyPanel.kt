package com.tikeno.autoclicker.ui.editor

import android.widget.EditText
import android.widget.RadioGroup

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.model.LoopPolicy

/**
 * LoopPolicyPanel — 循环策略与终止条件面板（架构 §2.5 #104）。
 *
 * 纯静态工具：绑定 RadioGroup（无限/固定次数/固定时长）与两个输入框的显隐联动，
 * 并在 [collect] 时按当前选中项组装 [LoopPolicy]。无自身视图，由
 * ConfigEditorActivity 直接传入布局控件。
 */
object LoopPolicyPanel {

    /**
     * 绑定循环策略控件：
     * - 按 [initialKind] 选中对应 RadioButton（未知 kind 回退为无限）；
     * - 同步输入框显隐；
     * - 注册 RadioGroup 监听，切换时联动显隐。
     */
    fun bind(
        kindGroup: RadioGroup,
        countInput: EditText,
        durationInput: EditText,
        initialKind: Int,
        initialCount: Int,
        initialDurationSec: Int,
    ) {
        val checkedId = when (initialKind) {
            LoopPolicy.KIND_FIXED_COUNT -> R.id.loop_fixed_count
            LoopPolicy.KIND_FIXED_DURATION -> R.id.loop_fixed_duration
            else -> R.id.loop_infinite
        }
        kindGroup.check(checkedId)

        countInput.setText(initialCount.toString())
        durationInput.setText(initialDurationSec.toString())
        syncVisibility(kindGroup, countInput, durationInput)

        kindGroup.setOnCheckedChangeListener { group, _ ->
            syncVisibility(group, countInput, durationInput)
        }
    }

    /**
     * 收集当前循环策略：
     * - 无限 → [LoopPolicy.infinite]；
     * - 固定次数 → [LoopPolicy.fixedCount]，非法输入钳制为 1；
     * - 固定时长 → `LoopPolicy(KIND_FIXED_DURATION, 0, durationSec * 1_000_000_000L)`，
     *   非法输入钳制为 1 秒。
     */
    fun collect(
        kindGroup: RadioGroup,
        countInput: EditText,
        durationInput: EditText,
    ): LoopPolicy = when (kindGroup.checkedRadioButtonId) {
        R.id.loop_fixed_count -> LoopPolicy.fixedCount(
            countInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
        )
        R.id.loop_fixed_duration -> {
            val durationSec = durationInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            LoopPolicy(LoopPolicy.KIND_FIXED_DURATION, 0, durationSec * 1_000_000_000L)
        }
        else -> LoopPolicy.infinite()
    }

    private fun syncVisibility(
        kindGroup: RadioGroup,
        countInput: EditText,
        durationInput: EditText,
    ) {
        val checkedId = kindGroup.checkedRadioButtonId
        countInput.visibility = if (checkedId == R.id.loop_fixed_count) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        durationInput.visibility = if (checkedId == R.id.loop_fixed_duration) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
}