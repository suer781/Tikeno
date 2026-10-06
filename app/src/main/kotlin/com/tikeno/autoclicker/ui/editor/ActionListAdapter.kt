package com.tikeno.autoclicker.ui.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ItemActionBinding
import com.tikeno.autoclicker.model.ActionModel

/**
 * ActionListAdapter — 动作列表（架构 §2.5 #102；拖拽排序随 T05，先支持删除/取点）。
 */
class ActionListAdapter(
    private val onPick: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
) : ListAdapter<ActionModel, ActionListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ActionModel>() {
            override fun areItemsTheSame(old: ActionModel, new: ActionModel): Boolean = old === new
            override fun areContentsTheSame(old: ActionModel, new: ActionModel): Boolean =
                old.type == new.type && old.durationMs == new.durationMs &&
                        old.intervalNs == new.intervalNs && old.repeat == new.repeat &&
                        old.points.size == new.points.size &&
                        old.globalAction == new.globalAction
        }
    }

    class VH(val binding: ItemActionBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(list: List<ActionModel>) {
        submitList(list.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemActionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ctx = holder.binding.root.context
        val a = getItem(position)
        val typeText = when (a.type) {
            ActionModel.TYPE_TAP -> ctx.getString(R.string.action_type_tap)
            ActionModel.TYPE_LONG_PRESS -> ctx.getString(R.string.action_type_long_press)
            ActionModel.TYPE_SWIPE -> ctx.getString(R.string.action_type_swipe)
            ActionModel.TYPE_MULTI_TOUCH -> ctx.getString(R.string.action_type_multi_touch)
            ActionModel.TYPE_WAIT -> ctx.getString(R.string.action_type_wait)
            ActionModel.TYPE_GLOBAL -> ctx.getString(R.string.action_type_global)
            else -> ctx.getString(R.string.action_type_condition)
        }
        val detail = when (a.type) {
            ActionModel.TYPE_WAIT ->
                ctx.getString(R.string.action_detail_wait, a.durationMs)
            ActionModel.TYPE_GLOBAL ->
                ctx.getString(R.string.action_detail_global, globalName(ctx, a.globalAction))
            else -> {
                val p = a.points.firstOrNull()
                if (p != null) {
                    ctx.getString(R.string.action_detail_point, p.x, p.y)
                } else {
                    ctx.getString(R.string.action_detail_no_point)
                }
            }
        }
        holder.binding.tvActionType.text = typeText
        holder.binding.tvActionDetail.text = detail
        holder.binding.btnPick.setOnClickListener {
            if (a.type != ActionModel.TYPE_WAIT && a.type != ActionModel.TYPE_GLOBAL) {
                onPick(position)
            }
        }
        holder.binding.btnDelete.setOnClickListener { onDelete(position) }
    }

    private fun globalName(ctx: android.content.Context, code: Int): String = when (code) {
        ActionModel.GLOBAL_BACK -> ctx.getString(R.string.global_back)
        ActionModel.GLOBAL_HOME -> ctx.getString(R.string.global_home)
        ActionModel.GLOBAL_RECENTS -> ctx.getString(R.string.global_recents)
        ActionModel.GLOBAL_NOTIFICATIONS -> ctx.getString(R.string.global_notifications)
        else -> ctx.getString(R.string.global_back)
    }
}
