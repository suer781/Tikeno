package com.tikeno.autoclicker.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.tikeno.autoclicker.databinding.ItemConfigBinding
import com.tikeno.autoclicker.ui.common.ConfigUiItem
import com.tikeno.autoclicker.ui.common.formatIntervalNs

/**
 * ConfigListAdapter — 配置列表 Adapter（架构 §2.5 #98，ListAdapter + DiffUtil）。
 *
 * 交互：单击 → 编辑；长按 → 删除（回调由 MainActivity 提供）。
 */
class ConfigListAdapter(
    private val onClick: (ConfigUiItem) -> Unit,
    private val onLongClick: (ConfigUiItem) -> Unit,
) : ListAdapter<ConfigUiItem, ConfigListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConfigUiItem>() {
            override fun areItemsTheSame(old: ConfigUiItem, new: ConfigUiItem): Boolean =
                old.id == new.id

            override fun areContentsTheSame(old: ConfigUiItem, new: ConfigUiItem): Boolean =
                old == new
        }
    }

    class VH(val binding: ItemConfigBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvConfigName.text = item.name
        holder.binding.tvConfigSummary.text =
            "${item.actionSummary} · ${formatIntervalNs(item.intervalMs * 1_000_000L)}"
        holder.binding.tvConfigLoop.text = item.loopSummary
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }
}
