package com.tikeno.autoclicker.ui.stats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import com.tikeno.autoclicker.TikenoApp
import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ActivityStatsBinding
import com.tikeno.autoclicker.engine.StatsBuffer
import com.tikeno.autoclicker.engine.StatsReporter
import com.tikeno.autoclicker.ui.common.formatCount
import com.tikeno.autoclicker.ui.common.formatJitterNs
import com.tikeno.autoclicker.ui.common.setVisible

/**
 * StatsActivity — 执行统计页（架构 §2.5 #115）。
 *
 * 数据来源：引擎 statsBuf 由 [StatsReporter] 每 200ms 轮询一次并以主线程回调
 * 推送给 [StatsReporter.Listener]；本页通过
 * `container.controller().statsReporter().addListener(...)` 注册监听，在
 * [onStats] 中缓存并渲染最新快照。任务停止后引擎不再推送，页面保持
 * 最近一次快照；从未收到过快照时显示"暂无统计数据"。
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    /** 最近一次快照（仅主线程访问） */
    private var latestSnapshot: StatsBuffer.Snapshot? = null

    private val statsListener = StatsReporter.Listener { snapshot ->
        latestSnapshot = snapshot
        render(snapshot)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRefresh.setOnClickListener { refresh() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        controller()?.statsReporter()?.addListener(statsListener)
    }

    override fun onPause() {
        super.onPause()
        controller()?.statsReporter()?.removeListener(statsListener)
    }

    /** 应用容器 → 业务门面（未初始化时可能为 null） */
    private fun controller() =
        (application as? TikenoApp)?.container?.controller()

    /** 刷新：用最近一次快照重渲染；无数据则显示空态 */
    private fun refresh() {
        val snapshot = latestSnapshot
        if (snapshot == null) {
            renderEmpty()
        } else {
            render(snapshot)
        }
    }

    private fun render(snapshot: StatsBuffer.Snapshot) {
        binding.tvStatsEmpty.setVisible(false)
        binding.jitterChart.setVisible(true)

        binding.tvTotalExec.text =
            getString(R.string.stats_total_exec) + "：" + formatCount(snapshot.execCount)
        binding.tvMissed.text =
            getString(R.string.stats_missed) + "：" + formatCount(snapshot.missedTicks)
        binding.tvP50.text =
            getString(R.string.stats_p50) + "：" + formatJitterNs(snapshot.p50Ns)
        binding.tvP95.text =
            getString(R.string.stats_p95) + "：" + formatJitterNs(snapshot.p95Ns)
        binding.tvP99.text =
            getString(R.string.stats_p99) + "：" + formatJitterNs(snapshot.p99Ns)
        binding.tvMean.text =
            getString(R.string.stats_mean) + "：" + formatJitterNs(snapshot.meanNs)
        binding.tvMax.text =
            getString(R.string.stats_max) + "：" + formatJitterNs(snapshot.maxNs)

        // 柱状图：P50 / P95 / P99 / Mean / Max（转 ms 的 Float 序列）
        binding.jitterChart.data = listOf(
            snapshot.p50Ns / 1_000_000f,
            snapshot.p95Ns / 1_000_000f,
            snapshot.p99Ns / 1_000_000f,
            snapshot.meanNs / 1_000_000f,
            snapshot.maxNs / 1_000_000f,
        )
    }

    private fun renderEmpty() {
        binding.tvStatsEmpty.setVisible(true)
        binding.jitterChart.setVisible(true)
        binding.jitterChart.data = emptyList()

        binding.tvTotalExec.text = getString(R.string.stats_total_exec) + "：-"
        binding.tvMissed.text = getString(R.string.stats_missed) + "：-"
        binding.tvP50.text = getString(R.string.stats_p50) + "：-"
        binding.tvP95.text = getString(R.string.stats_p95) + "：-"
        binding.tvP99.text = getString(R.string.stats_p99) + "：-"
        binding.tvMean.text = getString(R.string.stats_mean) + "：-"
        binding.tvMax.text = getString(R.string.stats_max) + "：-"
    }
}