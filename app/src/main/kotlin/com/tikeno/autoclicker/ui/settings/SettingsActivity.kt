package com.tikeno.autoclicker.ui.settings

import android.os.Bundle

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.tikeno.autoclicker.BuildConfig
import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ActivitySettingsBinding
import com.tikeno.autoclicker.ui.common.viewBinding

/**
 * SettingsActivity — 设置页（架构 §2.5 #103）。
 *
 * 能力：注入功耗档位（性能/均衡/省电）、熄屏自动暂停开关、
 * 合规声明弹窗、版本号展示。功耗档位与暂停开关直接读写
 * AppContainer.prefs()（SharedPreferences 封装，主线程 apply 提交）。
 */
class SettingsActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = (application as? com.tikeno.autoclicker.TikenoApp)?.container?.prefs()
            ?: return

        // 1. 功耗档位：0=性能 1=均衡 2=省电（默认 1）
        binding.rgPowerProfile.check(
            when (prefs.powerProfile) {
                0 -> R.id.rb_power_performance
                2 -> R.id.rb_power_saver
                else -> R.id.rb_power_balanced
            }
        )
        binding.rgPowerProfile.setOnCheckedChangeListener { _, checkedId ->
            val profile = when (checkedId) {
                R.id.rb_power_performance -> 0
                R.id.rb_power_saver -> 2
                else -> 1
            }
            prefs.setPowerProfile(profile)
        }

        // 2. 熄屏自动暂停（默认开启）
        binding.swPauseOnScreenOff.isChecked = prefs.isPauseOnScreenOff
        binding.swPauseOnScreenOff.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPauseOnScreenOff(isChecked)
        }

        // 3. 合规声明弹窗
        binding.btnCompliance.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.compliance_title)
                .setMessage(R.string.compliance_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
        }

        // 4. 版本号
        binding.tvVersion.text = getString(R.string.settings_version) + " " + BuildConfig.VERSION_NAME
    }
}