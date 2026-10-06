package com.tikeno.autoclicker.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ErrorCodes 单元测试。
 *
 * 测试目标：
 *  - 验证 TK_OK 与全部错误码常量值（成功/警告/C++ 核心/JNI 层/注入层）；
 *  - 验证 toString(int) 对每个已知码返回非空且不包含"未知"的中文文案；
 *  - 验证未知码返回"未知错误"文案；
 *  - 验证具体映射（如 1001 返回包含"钳制"的文案）。
 *
 * 纯 JVM 可运行（无 Android 依赖）。
 */
public class ErrorCodesTest {

    @Test
    public void constants_okAndWarnings() {
        assertEquals(0, ErrorCodes.TK_OK);

        assertEquals(1001, ErrorCodes.TK_WARN_RATE_CLAMPED);
        assertEquals(1002, ErrorCodes.TK_WARN_MISSED_TICK);
        assertEquals(1003, ErrorCodes.TK_WARN_TIER_DOWNGRADED);
        assertEquals(1004, ErrorCodes.TK_WARN_RING_FULL_DROPPED);
        assertEquals(1005, ErrorCodes.TK_WARN_CMD_DROPPED);
    }

    @Test
    public void constants_coreErrors() {
        assertEquals(-1001, ErrorCodes.TK_ERR_BAD_HANDLE);
        assertEquals(-1002, ErrorCodes.TK_ERR_EMPTY_SEQ);
        assertEquals(-1003, ErrorCodes.TK_ERR_INVALID_ARG);
        assertEquals(-1004, ErrorCodes.TK_ERR_BAD_STATE);
        assertEquals(-1005, ErrorCodes.TK_ERR_DEVICE_OPEN);
        assertEquals(-1006, ErrorCodes.TK_ERR_WRITE);
        assertEquals(-1007, ErrorCodes.TK_ERR_TIMER_CREATE);
        assertEquals(-1008, ErrorCodes.TK_ERR_THREAD_CREATE);
        assertEquals(-1009, ErrorCodes.TK_ERR_RING_FULL);
        assertEquals(-1010, ErrorCodes.TK_ERR_BUFFER_NOT_ATTACHED);
    }

    @Test
    public void constants_jniErrors() {
        assertEquals(-2001, ErrorCodes.TK_ERR_ENV_GET_FAILED);
        assertEquals(-2002, ErrorCodes.TK_ERR_NOT_DIRECT_BUFFER);
        assertEquals(-2003, ErrorCodes.TK_ERR_CLASS_NOT_FOUND);
        assertEquals(-2004, ErrorCodes.TK_ERR_LIB_NOT_LOADED);
        assertEquals(-2005, ErrorCodes.TK_ERR_FD_READ_FAILED);
    }

    @Test
    public void constants_injectionErrors() {
        assertEquals(-3001, ErrorCodes.TK_ERR_ACC_NOT_CONNECTED);
        assertEquals(-3002, ErrorCodes.TK_ERR_GESTURE_CANCELLED);
        assertEquals(-3003, ErrorCodes.TK_ERR_TIER_UNSUPPORTED);
        assertEquals(-3004, ErrorCodes.TK_ERR_SHIZUKU_DENIED);
        assertEquals(-3005, ErrorCodes.TK_ERR_PERMISSION_MISSING);
        assertEquals(-3006, ErrorCodes.TK_ERR_COORD_OUT_OF_RANGE);
    }

    @Test
    public void toString_okContainsSuccess() {
        assertEquals("成功", ErrorCodes.toString(ErrorCodes.TK_OK));
        assertTrue(ErrorCodes.toString(ErrorCodes.TK_OK).contains("成功"));
    }

    @Test
    public void toString_rateClampedMapsToClampWording() {
        String text = ErrorCodes.toString(ErrorCodes.TK_WARN_RATE_CLAMPED);
        assertTrue(text.contains("钳制"));
    }

    @Test
    public void toString_knownCodesAreNonEmptyAndNotUnknown() {
        int[] knownCodes = {
                ErrorCodes.TK_OK,
                ErrorCodes.TK_WARN_RATE_CLAMPED,
                ErrorCodes.TK_WARN_MISSED_TICK,
                ErrorCodes.TK_WARN_TIER_DOWNGRADED,
                ErrorCodes.TK_WARN_RING_FULL_DROPPED,
                ErrorCodes.TK_WARN_CMD_DROPPED,
                ErrorCodes.TK_ERR_BAD_HANDLE,
                ErrorCodes.TK_ERR_EMPTY_SEQ,
                ErrorCodes.TK_ERR_INVALID_ARG,
                ErrorCodes.TK_ERR_BAD_STATE,
                ErrorCodes.TK_ERR_DEVICE_OPEN,
                ErrorCodes.TK_ERR_WRITE,
                ErrorCodes.TK_ERR_TIMER_CREATE,
                ErrorCodes.TK_ERR_THREAD_CREATE,
                ErrorCodes.TK_ERR_RING_FULL,
                ErrorCodes.TK_ERR_BUFFER_NOT_ATTACHED,
                ErrorCodes.TK_ERR_ENV_GET_FAILED,
                ErrorCodes.TK_ERR_NOT_DIRECT_BUFFER,
                ErrorCodes.TK_ERR_CLASS_NOT_FOUND,
                ErrorCodes.TK_ERR_LIB_NOT_LOADED,
                ErrorCodes.TK_ERR_FD_READ_FAILED,
                ErrorCodes.TK_ERR_ACC_NOT_CONNECTED,
                ErrorCodes.TK_ERR_GESTURE_CANCELLED,
                ErrorCodes.TK_ERR_TIER_UNSUPPORTED,
                ErrorCodes.TK_ERR_SHIZUKU_DENIED,
                ErrorCodes.TK_ERR_PERMISSION_MISSING,
                ErrorCodes.TK_ERR_COORD_OUT_OF_RANGE
        };

        for (int code : knownCodes) {
            String text = ErrorCodes.toString(code);
            assertNotNull("code=" + code + " 返回 null", text);
            assertFalse("code=" + code + " 文案为空", text.isEmpty());
            assertFalse("code=" + code + " 文案不应包含'未知'", text.contains("未知"));
        }
    }

    @Test
    public void toString_unknownCodeContainsUnknownError() {
        String text = ErrorCodes.toString(-9999);
        assertTrue(text.contains("未知错误"));
    }
}