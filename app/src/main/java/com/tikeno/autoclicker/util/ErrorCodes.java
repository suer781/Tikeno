package com.tikeno.autoclicker.util;

/**
 * ErrorCodes — 与 C++ TkError（cpp/core/types.h）一一对应的错误码常量
 * （架构 §2.4.3 #65 / §10.5）。任一侧修改必须同步另一侧（CI 一致性检查点）。
 */
public final class ErrorCodes {

    // —— 成功 ——
    public static final int TK_OK = 0;

    // —— 警告（非致命，1~999）——
    public static final int TK_WARN_RATE_CLAMPED = 1001;      // 频率超档位上限已钳制
    public static final int TK_WARN_MISSED_TICK = 1002;       // 发生漏拍
    public static final int TK_WARN_TIER_DOWNGRADED = 1003;   // 档位已降级
    public static final int TK_WARN_RING_FULL_DROPPED = 1004; // ring 满已丢步
    public static final int TK_WARN_CMD_DROPPED = 1005;       // 命令队列满已覆盖

    // —— C++ 核心（-1001 ~ -1099）——
    public static final int TK_ERR_BAD_HANDLE = -1001;          // 无效句柄
    public static final int TK_ERR_EMPTY_SEQ = -1002;           // 序列为空或越界
    public static final int TK_ERR_INVALID_ARG = -1003;         // 参数非法
    public static final int TK_ERR_BAD_STATE = -1004;           // 状态机不允许该操作
    public static final int TK_ERR_DEVICE_OPEN = -1005;         // 设备打开失败
    public static final int TK_ERR_WRITE = -1006;               // 写入失败
    public static final int TK_ERR_TIMER_CREATE = -1007;        // 定时器创建失败
    public static final int TK_ERR_THREAD_CREATE = -1008;       // 线程创建失败
    public static final int TK_ERR_RING_FULL = -1009;           // ring 已满
    public static final int TK_ERR_BUFFER_NOT_ATTACHED = -1010; // 缓冲未挂载

    // —— JNI 层（-2001 ~ -2099）——
    public static final int TK_ERR_ENV_GET_FAILED = -2001;      // JNIEnv 获取失败
    public static final int TK_ERR_NOT_DIRECT_BUFFER = -2002;   // 非直接缓冲
    public static final int TK_ERR_CLASS_NOT_FOUND = -2003;     // 类或字段未找到
    public static final int TK_ERR_LIB_NOT_LOADED = -2004;      // native 库未加载
    public static final int TK_ERR_FD_READ_FAILED = -2005;      // fd 读取失败

    // —— Java 注入层（-3001 ~ -3099）——
    public static final int TK_ERR_ACC_NOT_CONNECTED = -3001;    // 无障碍服务未连接
    public static final int TK_ERR_GESTURE_CANCELLED = -3002;    // 手势被系统取消
    public static final int TK_ERR_TIER_UNSUPPORTED = -3003;     // 当前档位不支持该动作
    public static final int TK_ERR_SHIZUKU_DENIED = -3004;       // Shizuku 未授权
    public static final int TK_ERR_PERMISSION_MISSING = -3005;   // 必要权限缺失
    public static final int TK_ERR_COORD_OUT_OF_RANGE = -3006;   // 坐标越界

    private ErrorCodes() {
    }

    /** 错误码 → 可读文案（UI 展示；strings.xml 中 err_&lt;abs(code)&gt; 可覆盖） */
    public static String toString(int code) {
        switch (code) {
            case TK_OK: return "成功";
            case TK_WARN_RATE_CLAMPED: return "频率超档位上限，已自动钳制";
            case TK_WARN_MISSED_TICK: return "发生漏拍";
            case TK_WARN_TIER_DOWNGRADED: return "注入档位已降级";
            case TK_WARN_RING_FULL_DROPPED: return "事件队列已满，发生丢步";
            case TK_WARN_CMD_DROPPED: return "命令队列已满，命令被覆盖";
            case TK_ERR_BAD_HANDLE: return "无效引擎句柄";
            case TK_ERR_EMPTY_SEQ: return "序列为空或越界";
            case TK_ERR_INVALID_ARG: return "参数非法";
            case TK_ERR_BAD_STATE: return "当前状态不允许该操作";
            case TK_ERR_DEVICE_OPEN: return "注入设备打开失败";
            case TK_ERR_WRITE: return "注入写入失败";
            case TK_ERR_TIMER_CREATE: return "定时器创建失败";
            case TK_ERR_THREAD_CREATE: return "线程创建失败";
            case TK_ERR_RING_FULL: return "事件环形缓冲已满";
            case TK_ERR_BUFFER_NOT_ATTACHED: return "共享缓冲未挂载";
            case TK_ERR_ENV_GET_FAILED: return "JNI 环境获取失败";
            case TK_ERR_NOT_DIRECT_BUFFER: return "缓冲必须为 DirectByteBuffer";
            case TK_ERR_CLASS_NOT_FOUND: return "JNI 类或字段未找到";
            case TK_ERR_LIB_NOT_LOADED: return "native 库未加载";
            case TK_ERR_FD_READ_FAILED: return "fd 读取失败";
            case TK_ERR_ACC_NOT_CONNECTED: return "无障碍服务未连接";
            case TK_ERR_GESTURE_CANCELLED: return "手势被系统取消";
            case TK_ERR_TIER_UNSUPPORTED: return "当前档位不支持该动作";
            case TK_ERR_SHIZUKU_DENIED: return "Shizuku 未授权";
            case TK_ERR_PERMISSION_MISSING: return "必要权限缺失";
            case TK_ERR_COORD_OUT_OF_RANGE: return "坐标越界";
            default: return "未知错误 " + code;
        }
    }
}
