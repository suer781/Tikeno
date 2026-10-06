package com.tikeno.autoclicker.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * InjectionTier / CapabilityProbe 纯逻辑单元测试。
 *
 * CapabilityProbe 的探测链（probe()/hasSu/isUinputWritable 等）依赖 Android
 * Context 与系统命令，纯 JVM 单元测试无法覆盖（需 Robolectric/真机），此处
 * 仅测试：
 *   - InjectionTier 枚举常量与 C++ TkInjectionTier 契约的对应
 *   - CapabilityProbe.cached() 的初始兜底值（不依赖 Android）
 */
public class InjectionTierTest {

    // —— InjectionTier 常量契约（与 cpp/core/types.h TkInjectionTier 对应）——

    @Test
    public void tierConstants_matchCppContract() {
        assertEquals(0, InjectionTier.L0_UINPUT.code());
        assertEquals(1, InjectionTier.L1_EVDEV.code());
        assertEquals(2, InjectionTier.L2_SHELL.code());
        assertEquals(3, InjectionTier.L3_ACCESSIBILITY.code());
    }

    @Test
    public void tierDisplayNames_nonEmpty() {
        assertFalse(InjectionTier.L0_UINPUT.displayName().isEmpty());
        assertFalse(InjectionTier.L1_EVDEV.displayName().isEmpty());
        assertFalse(InjectionTier.L2_SHELL.displayName().isEmpty());
        assertFalse(InjectionTier.L3_ACCESSIBILITY.displayName().isEmpty());
    }

    @Test
    public void tierMaxPerSecond_followsArchitecture() {
        assertEquals(200, InjectionTier.L0_UINPUT.maxPerSecond());
        assertEquals(200, InjectionTier.L1_EVDEV.maxPerSecond());
        assertEquals(60, InjectionTier.L2_SHELL.maxPerSecond());
        assertEquals(30, InjectionTier.L3_ACCESSIBILITY.maxPerSecond());
    }

    @Test
    public void fromCode_returnsExactTier() {
        assertSame(InjectionTier.L0_UINPUT, InjectionTier.fromCode(0));
        assertSame(InjectionTier.L1_EVDEV, InjectionTier.fromCode(1));
        assertSame(InjectionTier.L2_SHELL, InjectionTier.fromCode(2));
        assertSame(InjectionTier.L3_ACCESSIBILITY, InjectionTier.fromCode(3));
    }

    @Test
    public void fromCode_unknownFallsBackToAccessibility() {
        assertSame(InjectionTier.L3_ACCESSIBILITY, InjectionTier.fromCode(-1));
        assertSame(InjectionTier.L3_ACCESSIBILITY, InjectionTier.fromCode(99));
    }

    // —— CapabilityProbe.cached() 初始兜底（不触发探测）——

    @Test
    public void cached_initialValue_isAccessibilityFallback() {
        CapabilityProbe.Result r = CapabilityProbe.cached();
        assertNotNull(r);
        assertEquals(InjectionTier.L3_ACCESSIBILITY, r.tier);
        assertFalse(r.detail.isEmpty());
    }
}