package com.tikeno.autoclicker.core;

/**
 * EngineState — 引擎状态枚举（架构 §2.4.1 #48）。
 * 与 C++ TkEngineState（core/types.h）逐项对应，勿单独修改。
 */
public enum EngineState {
    IDLE(0),       // 空闲
    PREPARED(1),   // 序列已装载
    RUNNING(2),    // 运行中
    PAUSED(3),     // 暂停
    STOPPING(4),   // 停止中
    ERROR(5);      // 错误

    private final int code;

    EngineState(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static EngineState fromCode(int code) {
        for (EngineState s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return ERROR;
    }
}
