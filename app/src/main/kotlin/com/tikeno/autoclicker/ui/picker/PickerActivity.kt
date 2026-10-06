package com.tikeno.autoclicker.ui.picker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent

import com.tikeno.autoclicker.R
import com.tikeno.autoclicker.databinding.ActivityPickerBinding
import com.tikeno.autoclicker.util.Logx

/**
 * PickerActivity — 取点器宿主（架构 §2.5 #106）。
 *
 * 全屏透明 Activity：根视图点击后以 rawX/rawY 作为屏幕坐标回传
 * （resultCode = RESULT_OK，Extra：EXTRA_X / EXTRA_Y），供
 * ConfigEditorActivity 写入动作点坐标。
 */
class PickerActivity : Activity() {

    private lateinit var binding: ActivityPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 用 OnTouchListener 捕获 rawX/rawY（屏幕绝对坐标）
        binding.root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                setResult(RESULT_OK, Intent().putExtra(EXTRA_X, x).putExtra(EXTRA_Y, y))
                finish()
            }
            true
        }

        binding.btnPickerCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        Logx.d(TAG, "onCreate")
    }

    companion object {
        private const val TAG = "Tikeno/Picker"

        const val EXTRA_X = "extra_x"
        const val EXTRA_Y = "extra_y"
    }
}