package com.tikeno.autoclicker.ui.main

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.core.CapabilityProbe
import com.tikeno.autoclicker.core.InjectionTier
import com.tikeno.autoclicker.databinding.ViewTierBadgeBinding
import com.tikeno.autoclicker.ui.common.TierUiState

/**
 * TierBadgeBinder — 档位徽章渲染（架构 §2.5 #100）。
 *
 * 展示探测到的最高可用档位 + 明细；点击触发重新探测（IO 线程回调刷新）。
 */
class TierBadgeBinder(private val binding: ViewTierBadgeBinding) {

    /** 绑定重探测回调（MainActivity 提供线程调度） */
    var onReprobe: (() -> Unit)? = null

    fun render(state: TierUiState) {
        binding.tvTierName.text = state.tier.displayName()
        binding.tvTierDetail.text = if (state.probing) {
            binding.root.context.getString(R.string.tier_probing)
        } else {
            state.detail
        }
        val color = when (state.tier) {
            InjectionTier.L0_UINPUT -> R.color.tier_l0
            InjectionTier.L1_EVDEV -> R.color.tier_l1
            InjectionTier.L2_SHELL -> R.color.tier_l2
            InjectionTier.L3_ACCESSIBILITY -> R.color.tier_l3
        }
        binding.badgeTierDot.setBackgroundResource(R.drawable.bg_badge)
        binding.badgeTierDot.background.setTint(
            binding.root.context.getColor(color)
        )
    }

    fun renderCached() {
        val c = CapabilityProbe.cached()
        render(TierUiState(c.tier, c.detail, probing = false))
        binding.root.setOnClickListener { onReprobe?.invoke() }
    }
}
