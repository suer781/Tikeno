package com.tikeno.autoclicker.ui.benchmark

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.TikenoApp
import com.tikeno.autoclicker.core.InjectionTier
import com.tikeno.autoclicker.databinding.ActivityBenchmarkBinding
import com.tikeno.autoclicker.engine.ClickTaskController
import com.tikeno.autoclicker.engine.StatsBuffer
import com.tikeno.autoclicker.model.ClickConfig
import com.tikeno.autoclicker.ui.common.formatJitterNs
import com.tikeno.autoclicker.ui.common.viewBinding
import com.tikeno.autoclicker.util.ErrorCodes
import com.tikeno.autoclicker.util.Logx
import java.lang.reflect.Field

/**
 * BenchmarkActivity — 基准测试页（架构 §2.5）。
 *
 * 模拟三档（L1 Root / L2 Shell / L3 无障碍）× 频率上限的基准测试：
 * 点击「开始测试」后通过 AppContainer.controller() 启动最小配置任务
 * （ClickConfig.minimalSingleTap(200, 400)），运行 2 秒后停止，读取
 * StatsBuffer 快照，将 P50/P95/P99 抖动展示到 tv_benchmark_result。
 *
 * ClickTaskController.statsBuffer 为 private 字段且无公开 getter，
 * 此处通过反射尝试暴露；若无法暴露则降级展示「基准测试框架就绪
 * （需真机联调）」占位文本并打印异常日志。
 */
class BenchmarkActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivityBenchmarkBinding::inflate)

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前是否正在运行基准（防止重复点击） */
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.btnBenchmarkStart.setOnClickListener { runBenchmark() }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (running) {
            (application as? TikenoApp)?.container?.controller()?.stop()
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // 基准测试流程（架构 §6.2：start → 运行 2s → stop → 读取快照）
    // ------------------------------------------------------------------

    private fun runBenchmark() {
        if (running) {
            return
        }
        val app = (application as? TikenoApp)?.container
        if (app == null) {
            showPlaceholder("TikenoApp 容器不可用")
            return
        }
        val controller = app.controller()

        // 1) 启动最小配置任务（start 内部 ensureInitialized 会创建 statsBuffer）
        val config = ClickConfig.minimalSingleTap(200, 400)
        val rc = controller.start(config)
        if (rc != ErrorCodes.TK_OK) {
            showPlaceholder("任务启动失败 rc=" + rc)
            return
        }

        // 2) 尝试暴露 statsBuffer（无公开 getter，反射桥接）
        val statsBuffer = resolveStatsBuffer(controller)
        if (statsBuffer == null) {
            controller.stop()
            showPlaceholder("ClickTaskController 无法暴露 statsBuffer")
            return
        }

        // 3) 运行 2 秒后停止，读取统计快照
        running = true
        binding.tvBenchmarkStatus.setText(R.string.benchmark_running)
        binding.btnBenchmarkStart.isEnabled = false
        mainHandler.postDelayed({
            try {
                controller.stop()
                renderResult(statsBuffer.snapshot())
                binding.tvBenchmarkStatus.setText(R.string.benchmark_done)
            } catch (e: Exception) {
                Logx.e(TAG, "基准测试读取统计快照异常", e)
                showPlaceholder("读取统计快照异常：" + e.message)
            } finally {
                binding.btnBenchmarkStart.isEnabled = true
                running = false
            }
        }, BENCHMARK_RUN_MS)
    }

    /** 反射读取 ClickTaskController.statsBuffer（无公开 getter 时的桥接） */
    private fun resolveStatsBuffer(controller: ClickTaskController): StatsBuffer? {
        return try {
            val field: Field = ClickTaskController::class.java.getDeclaredField("statsBuffer")
            field.isAccessible = true
            field.get(controller) as? StatsBuffer
        } catch (e: Exception) {
            Logx.e(TAG, "ClickTaskController 无法暴露 statsBuffer", e)
            null
        }
    }

    // ------------------------------------------------------------------
    // 结果渲染
    // ------------------------------------------------------------------

    /** 展示三档 × 频率的 P50/P95/P99（数据来自同一次运行快照） */
    private fun renderResult(snapshot: StatsBuffer.Snapshot) {
        val sb = StringBuilder()
        sb.append(getString(R.string.benchmark_tier))
            .append('\t')
            .append(getString(R.string.benchmark_freq))
            .append("\tP50\tP95\tP99\n")
        for (tier in BENCHMARK_TIERS) {
            sb.append(tier.displayName())
                .append('\t')
                .append(tier.maxPerSecond())
                .append("/s\t")
                .append(formatJitterNs(snapshot.p50Ns))
                .append('\t')
                .append(formatJitterNs(snapshot.p95Ns))
                .append('\t')
                .append(formatJitterNs(snapshot.p99Ns))
                .append('\n')
        }
        binding.tvBenchmarkResult.text = sb.toString()
    }

    /** 降级占位：框架就绪但需真机联调 */
    private fun showPlaceholder(reason: String) {
        Logx.e(TAG, reason)
        binding.tvBenchmarkStatus.text = PLACEHOLDER_TEXT
        binding.tvBenchmarkResult.text = ""
        binding.btnBenchmarkStart.isEnabled = true
        running = false
    }

    companion object {
        private const val TAG = "Tikeno/Benchmark"

        /** 单档运行时长（需求契约：2 秒） */
        private const val BENCHMARK_RUN_MS = 2_000L

        /** 占位文本（无对应字符串资源，按需求硬编码） */
        private const val PLACEHOLDER_TEXT = "基准测试框架就绪（需真机联调）"

        /** 模拟三档：Root / Shell / 无障碍（对应 InjectionTier 频率上限） */
        private val BENCHMARK_TIERS = listOf(
            InjectionTier.L1_EVDEV,
            InjectionTier.L2_SHELL,
            InjectionTier.L3_ACCESSIBILITY,
        )
    }
}