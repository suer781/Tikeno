package com.tikeno.autoclicker.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast

import androidx.fragment.app.FragmentManager

import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.SheetActionEditBinding
import com.tikeno.autoclicker.model.ActionModel
import com.tikeno.autoclicker.ui.common.toast

/**
 * ActionEditBottomSheet — 单动作编辑弹层（架构 §2.5 #103）。
 *
 * 覆盖动作类型：点击/长按/滑动/多指/等待/全局动作（T04 范围，条件 P1 不含）。
 * 坐标在编辑器页取点（弹层仅编辑数值属性），滑动/多指点数在此设置
 * （新增点默认取上一个点坐标，便于微调）。
 */
class ActionEditBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetActionEditBinding? = null
    private val binding get() = _binding!!

    /** 编辑结果回调（由 show 传入） */
    private var onResult: ((ActionModel) -> Unit)? = null
    private var existing: ActionModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetActionEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val base = existing ?: ActionModel.tap(com.tikeno.autoclicker.model.PointModel.px(0, 0), 200_000_000L)

        binding.tvSheetType.text = typeName(ctx, base.type)

        // 时长（长按/滑动/等待）
        binding.etSheetDuration.setVisible(base.type == ActionModel.TYPE_LONG_PRESS ||
                base.type == ActionModel.TYPE_SWIPE ||
                base.type == ActionModel.TYPE_WAIT)
        binding.etSheetDuration.setText(base.durationMs.toString())

        // 间隔（全局/点击等）
        binding.etSheetInterval.setText((base.intervalNs / 1_000_000L).toString())

        // 重复次数
        binding.etSheetRepeat.setText(base.repeat.toString())

        // 全局动作下拉
        binding.spSheetGlobalAction.setVisible(base.type == ActionModel.TYPE_GLOBAL)
        val globalNames = arrayOf(
            ctx.getString(R.string.global_back),
            ctx.getString(R.string.global_home),
            ctx.getString(R.string.global_recents),
            ctx.getString(R.string.global_notifications),
        )
        binding.spSheetGlobalAction.adapter =
            ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, globalNames)
        binding.spSheetGlobalAction.setSelection((base.globalAction - 1).coerceIn(0, 3))

        binding.btnSheetCancel.setOnClickListener { dismiss() }
        binding.btnSheetOk.setOnClickListener {
            val edited = buildEdited(base)
            onResult?.invoke(edited)
            dismiss()
        }
    }

    private fun buildEdited(base: ActionModel): ActionModel {
        val durationMs = binding.etSheetDuration.text.toString().toIntOrNull() ?: 0
        val intervalMs = binding.etSheetInterval.text.toString().toLongOrNull() ?: 200L
        val repeat = binding.etSheetRepeat.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
        base.durationMs = durationMs
        base.intervalNs = intervalMs * 1_000_000L
        base.repeat = repeat
        if (base.type == ActionModel.TYPE_GLOBAL) {
            base.globalAction = binding.spSheetGlobalAction.selectedItemPosition + 1
        }
        return base
    }

    private fun typeName(ctx: android.content.Context, type: Int): String = when (type) {
        ActionModel.TYPE_TAP -> ctx.getString(R.string.action_type_tap)
        ActionModel.TYPE_LONG_PRESS -> ctx.getString(R.string.action_type_long_press)
        ActionModel.TYPE_SWIPE -> ctx.getString(R.string.action_type_swipe)
        ActionModel.TYPE_MULTI_TOUCH -> ctx.getString(R.string.action_type_multi_touch)
        ActionModel.TYPE_WAIT -> ctx.getString(R.string.action_type_wait)
        ActionModel.TYPE_GLOBAL -> ctx.getString(R.string.action_type_global)
        else -> ctx.getString(R.string.action_type_condition)
    }

    private fun View.setVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_EDIT_INDEX = "edit_index"

        /**
         * 显示编辑弹层。
         * @param fm Activity 的 supportFragmentManager
         * @param existing 待编辑动作（null=新建）
         * @param onResult 编辑完成回调（参数为编辑后的动作）
         */
        fun show(fm: FragmentManager, existing: ActionModel?, onResult: (ActionModel) -> Unit) {
            val sheet = ActionEditBottomSheet()
            sheet.existing = existing
            sheet.onResult = onResult
            sheet.show(fm, "ActionEditBottomSheet")
        }
    }
}