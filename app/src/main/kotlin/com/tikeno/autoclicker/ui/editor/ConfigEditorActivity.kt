package com.tikeno.autoclicker.ui.editor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.floatingactionbutton.FloatingActionButton

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ActivityConfigEditorBinding
import com.tikeno.autoclicker.model.ActionModel
import com.tikeno.autoclicker.model.ActionSequence
import com.tikeno.autoclicker.model.ClickConfig
import com.tikeno.autoclicker.model.LoopPolicy
import com.tikeno.autoclicker.model.PointModel
import com.tikeno.autoclicker.service.ClickerForegroundService
import com.tikeno.autoclicker.ui.common.BaseActivity
import com.tikeno.autoclicker.ui.common.ConfirmDialogs
import com.tikeno.autoclicker.ui.common.toast
import com.tikeno.autoclicker.ui.picker.PickerActivity
import com.tikeno.autoclicker.util.Logx
import java.util.UUID

/**
 * ConfigEditorActivity — 配置编辑（架构 §2.5 #101 / T04 最小可用路径）。
 *
 * 能力：名称、动作序列增删（点击/长按/滑动/多指/等待/全局）、循环策略、
 * 全局间隔与抖动。取点经 PickerActivity 回传坐标。
 * 保存 → ConfigRepository；"保存并启动" → controller.start + 前台服务。
 */
class ConfigEditorActivity : BaseActivity<ActivityConfigEditorBinding>() {

    companion object {
        const val EXTRA_CONFIG_ID = "config_id"

        fun createIntent(activity: Activity): Intent =
            Intent(activity, ConfigEditorActivity::class.java)

        fun editIntent(activity: Activity, configId: String): Intent =
            Intent(activity, ConfigEditorActivity::class.java)
                .putExtra(EXTRA_CONFIG_ID, configId)
    }

    private var configId: String = ""
    private var configName: String = ""
    private var defaultIntervalNs = 200_000_000L
    private var jitterPct = 0f
    private var loopKind = LoopPolicy.KIND_INFINITE
    private var loopMaxCount = 10
    private var loopMaxDurationNs = 60_000_000_000L

    private val actions = mutableListOf<ActionModel>()
    private var pendingPickActionIndex = -1   // 正在为第几个动作取点

    private val pickPointLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val x = result.data?.getIntExtra(PickerActivity.EXTRA_X, -1) ?: -1
            val y = result.data?.getIntExtra(PickerActivity.EXTRA_Y, -1) ?: -1
            if (x >= 0 && y >= 0 && pendingPickActionIndex in actions.indices) {
                actions[pendingPickActionIndex].points.clear()
                actions[pendingPickActionIndex].points.add(PointModel.px(x, y))
                refreshActions()
                toast(getString(R.string.point_picked, x, y))
            }
        }
        pendingPickActionIndex = -1
    }

    private lateinit var actionAdapter: ActionListAdapter

    override fun inflate() = ActivityConfigEditorBinding.inflate(layoutInflater)
    override fun layoutTag(): String = "Tikeno/Editor"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configId = intent.getStringExtra(EXTRA_CONFIG_ID) ?: ""
        loadIfEditing()

        actionAdapter = ActionListAdapter(
            onPick = { idx -> pickPointFor(idx) },
            onDelete = { idx ->
                ConfirmDialogs.confirm(this, getString(R.string.delete_action_title),
                    getString(R.string.delete_action_msg)) {
                    actions.removeAt(idx)
                    refreshActions()
                }
            },
        )
        binding.actionList.layoutManager = LinearLayoutManager(this)
        binding.actionList.adapter = actionAdapter

        binding.btnPickPoint.setOnClickListener { addTapActionWithPicker() }
        binding.btnAddAction.setOnClickListener { openActionSheet(-1) }
        binding.etConfigName.setText(configName)
        binding.etIntervalMs.setText((defaultIntervalNs / 1_000_000L).toString())
        binding.etJitterPct.setText(jitterPct.toString())

        LoopPolicyPanel.bind(
            kindGroup = binding.loopKindGroup,
            countInput = binding.etLoopCount,
            durationInput = binding.etLoopDurationSec,
            initialKind = loopKind,
            initialCount = loopMaxCount,
            initialDurationSec = (loopMaxDurationNs / 1_000_000_000L).toInt(),
        )

        binding.btnSave.setOnClickListener { save(start = false) }
        binding.btnSaveStart.setOnClickListener { save(start = true) }
    }

    // ------------------------------------------------------------------
    // 装载 / 渲染
    // ------------------------------------------------------------------

    private fun loadIfEditing() {
        if (configId.isEmpty()) {
            configId = UUID.randomUUID().toString()
            configName = getString(R.string.config_default_name)
            return
        }
        val app = container ?: return
        // 编辑装载同步执行（列表页刚写完文件即进入编辑是常见路径，
        // 单文件小数据，主线程读可接受；T05 可优化为异步装载）
        val cfg = app.configRepository().findById(configId)
        if (cfg != null) {
            configName = cfg.name
            defaultIntervalNs = cfg.defaultIntervalNs
            jitterPct = cfg.jitterPct
            loopKind = cfg.sequence.policy().kind
            loopMaxCount = cfg.sequence.policy().maxCount
            loopMaxDurationNs = cfg.sequence.policy().maxDurationNs
            actions.clear()
            actions.addAll(cfg.sequence.actionsView())
        }
    }

    private fun refreshActions() {
        actionAdapter.submit(actions.toList())
    }

    // ------------------------------------------------------------------
    // 取点
    // ------------------------------------------------------------------

    private fun addTapActionWithPicker() {
        actions.add(ActionModel.tap(PointModel.px(0, 0), defaultIntervalNs))
        pickPointFor(actions.size - 1)
    }

    private fun pickPointFor(actionIndex: Int) {
        pendingPickActionIndex = actionIndex
        pickPointLauncher.launch(Intent(this, PickerActivity::class.java))
    }

    // ------------------------------------------------------------------
    // 动作编辑弹层
    // ------------------------------------------------------------------

    private fun openActionSheet(editIndex: Int) {
        val existing = if (editIndex in actions.indices) actions[editIndex] else null
        ActionEditBottomSheet.show(supportFragmentManager, existing) { edited ->
            if (editIndex in actions.indices) {
                actions[editIndex] = edited
            } else {
                actions.add(edited)
            }
            refreshActions()
        }
    }

    // ------------------------------------------------------------------
    // 保存 / 启动
    // ------------------------------------------------------------------

    private fun buildConfig(): ClickConfig? {
        val name = binding.etConfigName.text.toString().trim()
            .ifEmpty { getString(R.string.config_default_name) }
        val intervalMs = binding.etIntervalMs.text.toString().toLongOrNull() ?: 200L
        val jitter = binding.etJitterPct.text.toString().toFloatOrNull() ?: 0f
        val policy = LoopPolicyPanel.collect(
            kindGroup = binding.loopKindGroup,
            countInput = binding.etLoopCount,
            durationInput = binding.etLoopDurationSec,
        )
        if (actions.isEmpty()) {
            toast(getString(R.string.err_empty_actions))
            return null
        }
        // 全局间隔回填：动作未单独设置（≤0）的覆盖为全局值
        for (a in actions) {
            if (a.intervalNs <= 0 && a.type != ActionModel.TYPE_WAIT) {
                a.intervalNs = intervalMs * 1_000_000L
            }
        }
        val seq = ActionSequence(policy)
        for (a in actions) {
            seq.addAction(a)
        }
        return ClickConfig(
            configId, name, seq,
            intervalMs * 1_000_000L,
            container?.prefs()?.powerProfile ?: 1,
            jitter,
            container?.prefs()?.isPauseOnScreenOff ?: true,
        )
    }

    private fun save(start: Boolean) {
        val config = buildConfig() ?: return
        val app = container ?: return
        app.io().execute {
            app.configRepository().save(config)
            app.mainHandler().post {
                toast(getString(R.string.config_saved))
                if (start) {
                    val rc = app.controller().start(config)
                    if (rc == 0) {
                        ClickerForegroundService.start(this)
                        toast(getString(R.string.task_started))
                    } else {
                        toast(com.tikeno.autoclicker.util.ErrorCodes.toString(rc))
                    }
                }
                finish()
            }
        }
    }
}
