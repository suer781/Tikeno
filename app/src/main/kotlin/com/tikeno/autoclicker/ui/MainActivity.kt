package com.tikeno.autoclicker.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

import androidx.recyclerview.widget.LinearLayoutManager

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ActivityMainBinding
import com.tikeno.autoclicker.model.ClickConfig
import com.tikeno.autoclicker.service.ClickerForegroundService
import com.tikeno.autoclicker.ui.common.BaseActivity
import com.tikeno.autoclicker.ui.common.ConfigUiItem
import com.tikeno.autoclicker.ui.common.ConfirmDialogs
import com.tikeno.autoclicker.ui.common.TierUiState
import com.tikeno.autoclicker.ui.common.setVisible
import com.tikeno.autoclicker.ui.common.toast
import com.tikeno.autoclicker.ui.editor.ConfigEditorActivity
import com.tikeno.autoclicker.ui.main.ConfigListAdapter
import com.tikeno.autoclicker.ui.main.PermissionCardBinder
import com.tikeno.autoclicker.ui.main.TierBadgeBinder
import com.tikeno.autoclicker.ui.settings.SettingsActivity
import com.tikeno.autoclicker.ui.stats.StatsActivity
import com.tikeno.autoclicker.util.Logx

/**
 * MainActivity — 主界面（架构 §2.5 #97 / T04 最小可用路径）。
 *
 * 结构：权限卡片 → 档位徽章 → 配置列表（单击编辑/长按删除）→ 底部导航（统计/设置）。
 * 最小路径：新建配置 → 编辑器取点 → 保存 → 悬浮窗/磁贴开始。
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val adapter by lazy {
        ConfigListAdapter(
            onClick = { item ->
                startActivity(ConfigEditorActivity.editIntent(this, item.id))
            },
            onLongClick = { item -> confirmDelete(item) },
        )
    }

    private var permissionBinder: PermissionCardBinder? = null
    private var tierBinder: TierBadgeBinder? = null

    override fun inflate() = ActivityMainBinding.inflate(layoutInflater)
    override fun layoutTag(): String = "Tikeno/Main"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T05 合规：首次启动先展示不可取消的合规弹窗（已确认则直接回调）
        com.tikeno.autoclicker.ui.guide.ComplianceDialog.showIfNeeded(this) {
            // 已确认合规，正常初始化
        }
        permissionBinder = PermissionCardBinder(binding.permissionCard)
        tierBinder = TierBadgeBinder(binding.tierBadge).apply {
            onReprobe = { runProbe() }
        }
        binding.configList.layoutManager = LinearLayoutManager(this)
        binding.configList.adapter = adapter
        binding.fabAdd.setOnClickListener {
            startActivity(ConfigEditorActivity.createIntent(this))
        }
        runProbe()
    }

    override fun onResume() {
        super.onResume()
        permissionBinder?.let { b ->
            val st = b.readState(this)
            b.render(this, st)
        }
        tierBinder?.renderCached()
        reloadConfigs()
    }

    // ------------------------------------------------------------------
    // 配置列表（IO 经 container.io()，回调回主线程）
    // ------------------------------------------------------------------

    private fun reloadConfigs() {
        val app = container ?: return
        app.io().execute {
            val all = app.configRepository().loadAll()
            app.mainHandler().post { renderConfigs(all) }
        }
    }

    private fun renderConfigs(all: List<ClickConfig>) {
        val items = all.map { c ->
            ConfigUiItem.from(c, actionSummary(c), loopSummary(c))
        }
        adapter.submitList(items)
        binding.tvEmpty.setVisible(items.isEmpty())
    }

    private fun actionSummary(c: ClickConfig): String {
        val n = c.sequence.actionCount()
        return getString(R.string.config_action_summary, n)
    }

    private fun loopSummary(c: ClickConfig): String = when (c.sequence.policy().kind) {
        1 -> getString(R.string.loop_fixed_count, c.sequence.policy().maxCount)
        2 -> getString(R.string.loop_fixed_duration, c.sequence.policy().maxDurationNs / 1_000_000_000L)
        else -> getString(R.string.loop_infinite)
    }

    private fun confirmDelete(item: ConfigUiItem) {
        ConfirmDialogs.confirm(
            this,
            getString(R.string.delete_config_title),
            getString(R.string.delete_config_msg, item.name),
        ) {
            val app = container ?: return@confirm
            app.io().execute {
                app.configRepository().delete(item.id)
                app.mainHandler().post { reloadConfigs() }
            }
        }
    }

    // ------------------------------------------------------------------
    // 档位探测（IO 线程）
    // ------------------------------------------------------------------

    private fun runProbe() {
        val app = container ?: return
        tierBinder?.render(TierUiState(com.tikeno.autoclicker.core.CapabilityProbe.cached().tier, "", probing = true))
        app.io().execute {
            val r = com.tikeno.autoclicker.core.CapabilityProbe.probe(applicationContext)
            app.mainHandler().post {
                tierBinder?.render(TierUiState(r.tier, r.detail, probing = false))
            }
        }
    }

    // ------------------------------------------------------------------
    // 菜单（底部导航：统计 / 设置）
    // ------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main_bottom, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_stats -> {
                startActivity(Intent(this, StatsActivity::class.java)); true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            R.id.action_foreground -> {
                ClickerForegroundService.start(this)
                toast(getString(R.string.fgs_started))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "Tikeno/Main"
    }
}
