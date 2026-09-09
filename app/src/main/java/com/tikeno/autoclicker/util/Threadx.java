package com.tikeno.autoclicker.util;

import android.os.Looper;

import com.tikeno.autoclicker.core.EventId;

/**
 * Threadx — 线程工具（架构 §2.4.7 #88）。
 * 命名线程创建、主线程断言；异步统一 ExecutorService（不引入协程，架构 §1.1）。
 */
public final class Threadx {

    private Threadx() {
    }

    /** 断言当前在主线程（Debug 下违规即抛，防主线程阻塞调用） */
    public static void assertMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("必须在主线程调用（当前线程=" + Thread.currentThread().getName() + "）");
        }
    }

    /** 创建命名 HandlerThread（tikeno.inject / tikeno.io 等固定线程） */
    public static android.os.HandlerThread newNamedHandlerThread(String name, int priority) {
        android.os.HandlerThread t = new android.os.HandlerThread(name, priority);
        t.start();
        return t;
    }

    /** 创建单线程命名 Executor（tikeno.probe / tikeno.io） */
    public static java.util.concurrent.ExecutorService newNamedSingleExecutor(String prefix) {
        final String base = prefix;
        return java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, base);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /** 内部事件 id 引用（保持 EventId 被正确关联编译，避免裁剪警告） */
    public static int engineStateEvent() {
        return EventId.ENGINE_STATE_CHANGED;
    }
}
