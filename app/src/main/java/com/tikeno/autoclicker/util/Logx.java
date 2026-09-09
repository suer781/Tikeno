package com.tikeno.autoclicker.util;

import com.tikeno.autoclicker.BuildConfig;

/**
 * Logx — 统一日志（架构 §2.4.7 #87 / §10.4）。
 * Release 只输出 W/E；tag 规范见架构 §10.4（Tikeno/App 等）。
 */
public final class Logx {

    private Logx() {
    }

    public static void d(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(tag, msg);
        }
    }

    public static void i(String tag, String msg) {
        android.util.Log.i(tag, msg);
    }

    public static void w(String tag, String msg) {
        android.util.Log.w(tag, msg);
    }

    public static void w(String tag, String msg, Throwable t) {
        android.util.Log.w(tag, msg, t);
    }

    public static void e(String tag, String msg) {
        android.util.Log.e(tag, msg);
    }

    public static void e(String tag, String msg, Throwable t) {
        android.util.Log.e(tag, msg, t);
    }
}
