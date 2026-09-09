package com.tikeno.autoclicker.core;

/**
 * InjectionTier — 注入档位枚举（架构 §2.4.1 #49）。
 * 与 C++ TkInjectionTier（core/types.h）逐项对应。
 * 携带频率上限（PRD：免 Root 30/s 软上限提示，Root 档 200/s）与展示名。
 */
public enum InjectionTier {
    L0_UINPUT(0, "Root 增强 · uinput", 200),
    L1_EVDEV(1, "Root 兼容 · eventX", 200),
    L2_SHELL(2, "Shell 档 · Shizuku", 60),
    L3_ACCESSIBILITY(3, "免 Root · 无障碍", 30);

    private final int code;
    private final String displayName;
    private final int maxPerSecond;

    InjectionTier(int code, String displayName, int maxPerSecond) {
        this.code = code;
        this.displayName = displayName;
        this.maxPerSecond = maxPerSecond;
    }

    public int code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    /** 档位频率上限（超出 UI 提示钳制） */
    public int maxPerSecond() {
        return maxPerSecond;
    }

    public static InjectionTier fromCode(int code) {
        for (InjectionTier t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return L3_ACCESSIBILITY;
    }
}
