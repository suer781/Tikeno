package com.tikeno.autoclicker.ui.common

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * ViewBindingDelegate — `by viewBinding<T>()` 手写委托（架构 §2.5 #95，无三方库）。
 *
 * 用法（不继承 BaseActivity 的场景）：
 * ```
 * private val binding by viewBinding(ActivityMainBinding::inflate)
 * ```
 * 生命周期：首次 get 时 inflate + setContentView；Activity 销毁后访问抛
 * IllegalStateException（早失败，防泄漏窗口操作）。
 */
fun <VB : ViewBinding> AppCompatActivity.viewBinding(
    inflate: (LayoutInflater) -> VB,
): ReadOnlyProperty<AppCompatActivity, VB> =
    ViewBindingDelegate(inflate)

private class ViewBindingDelegate<VB : ViewBinding>(
    private val inflate: (LayoutInflater) -> VB,
) : ReadOnlyProperty<AppCompatActivity, VB> {

    private var binding: VB? = null

    override fun getValue(thisRef: AppCompatActivity, property: KProperty<*>): VB {
        val current = binding
        if (current != null && thisRef.window.isActive) {
            return current
        }
        if (current != null) {
            // 已绑定但窗口失效：视图随 Activity 销毁，禁止复用
            throw IllegalStateException("binding 已随上一次销毁失效，禁止跨实例缓存")
        }
        val created = inflate(thisRef.layoutInflater)
        thisRef.setContentView(created.root)
        binding = created
        return created
    }
}
