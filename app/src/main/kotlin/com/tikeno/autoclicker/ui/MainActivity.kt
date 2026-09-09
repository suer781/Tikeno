package com.tikeno.autoclicker.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tikeno.autoclicker.AppContainer
import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ActivityMainBinding
import com.tikeno.autoclicker.jni.NativeEngine
import com.tikeno.autoclicker.util.Logx

/**
 * MainActivity — 主界面骨架验证点（架构 §2.5 #97 / T01 验收）。
 *
 * 本轮：ViewBinding 装配 + 标题 "Tikeno" + native 版本串显示。
 * 完整主界面（权限卡片 / 配置列表 / 档位徽章 / 底部导航）随 T04 实现。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** AppContainer 入口（T04 BaseActivity 泛型封装后迁移） */
    val container: AppContainer?
        get() = (application as? com.tikeno.autoclicker.TikenoApp)?.container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.app_name)

        // 骨架验证点：native 版本串（JNI 注册与 loadLibrary 成功的可视证据）
        val buildInfo: String = try {
            NativeEngine.nativeGetBuildInfo() ?: "unknown"
        } catch (e: UnsatisfiedLinkError) {
            Logx.e("Tikeno/UI", "native 库加载失败", e)
            "native 未加载: ${e.message}"
        }
        findViewById<TextView>(R.id.tv_build_info).text = buildInfo
        Logx.i("Tikeno/UI", "MainActivity 启动，build=$buildInfo")
    }
}
