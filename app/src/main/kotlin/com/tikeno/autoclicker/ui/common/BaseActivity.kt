package com.tikeno.autoclicker.ui.common

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

import com.tikeno.autoclicker.AppContainer
import com.tikeno.autoclicker.TikenoApp
import com.tikeno.autoclicker.util.Logx

/**
 * BaseActivity — ViewBinding 泛型基类（架构 §2.5 #92）。
 *
 * 子类提供 [inflate] 方法（binding 类的静态 inflate 引用）与 [layoutTag]
 * （日志用）；[container] 为主线程安全的应用容器入口。
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null

    /** 仅在 onCreate/onResume 间访问；onDestroy 后为 null */
    val binding: VB
        get() = _binding ?: throw IllegalStateException("binding 未初始化或已销毁")

    /** 应用容器（可空：App 尚未完成初始化的极端场景） */
    val container: AppContainer?
        get() = (application as? TikenoApp)?.container

    /** 子类提供 binding 的静态 inflate 引用，如 ActivityMainBinding::inflate */
    abstract fun inflate(): VB

    /** 子类日志 tag */
    protected open fun layoutTag(): String = "Tikeno/UI"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflate()
        setContentView(binding.root)
        Logx.d(layoutTag(), "onCreate $localClassName")
    }

    override fun onDestroy() {
        Logx.d(layoutTag(), "onDestroy $localClassName")
        _binding = null
        super.onDestroy()
    }
}
