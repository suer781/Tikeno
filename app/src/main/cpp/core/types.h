#pragma once
// ============================================================================
// core/types.h — 跨语言扁平数据契约（架构 §4.3，与 Java 侧一一对应）
// ----------------------------------------------------------------------------
// 【字节布局契约】本文件所有结构体均为 packed 布局、小端序，
// 是 C++ ⇄ Java（DirectByteBuffer）之间的硬性 ABI：
//   - TkActionFlat : 64 字节 —— 一个动作的扁平传输态（Java ActionModel ↔）
//   - TkPointFlat  : 16 字节 —— 一个坐标点（px + 相对偏移）
//   - TkStep       : 32 字节 —— C++→Java 的原子注入事件（outRing 槽位单元）
//   - TkEngineConfigFlat : 64 字节 —— nativeCreate 的引擎配置入参
//   - TkStatsSnapshot    : 88 字节 —— statsBuf 共享内存布局（11 × int64）
// 任何字段增删必须同步架构文档 §4.3 与 Java 侧实现，禁止单独修改。
// ============================================================================

#include <stdint.h>
#include <stddef.h>

// ---------------------------------------------------------------------------
// 枚举定义（enum class : int32_t，值与 Java 侧常量一致）
// ---------------------------------------------------------------------------

// 动作类型（TkActionFlat.type）
enum class TkActionType : int32_t {
    kTap        = 1,  // 点击
    kLongPress  = 2,  // 长按
    kSwipe      = 3,  // 滑动
    kMultiTouch = 4,  // 多指手势（≤10 指）
    kWait       = 5,  // 等待
    kGlobal     = 6,  // 全局动作（返回/主页/最近任务/通知栏）
    kCondition  = 7,  // 条件判断（P1，预留）
};

// 原子事件类型（TkStep.kind）—— outRing 与 IInjector 共用
enum class TkStepKind : int32_t {
    kDown           = 0,  // 按下（slot 槽位）
    kMove           = 1,  // 移动
    kUp             = 2,  // 抬起
    kSync           = 3,  // SYN_REPORT 同步帧
    kWait           = 4,  // 纯等待（不注入，仅推进时间）
    kGlobal         = 5,  // 全局动作（arg = TkGlobalAction）
    kNodeResolveReq = 6,  // 节点解析请求（Java 回填见 nativeNotifyResolution）
};

// 坐标模式（TkActionFlat.coordMode）
enum class TkCoordMode : int32_t {
    kAbsPx   = 0,  // 绝对像素
    kPercent = 1,  // 百分比 ×1000 定点（Java 侧已在 flatten 阶段换算为 px）
};

// 滑动曲线类型（TkActionFlat.curveType）
enum class TkCurveType : int32_t {
    kLinear    = 0,  // 直线
    kBezier    = 1,  // 二阶贝塞尔
    kEaseInOut = 2,  // 缓入缓出（smoothstep）
    kHuman     = 3,  // 人手模拟（缓动 + 轻微横向摆动，确定性种子）
};

// 全局动作（TkActionFlat.globalAction，与 AccessibilityService 常量一致）
enum class TkGlobalAction : int32_t {
    kBack          = 1,
    kHome          = 2,
    kRecents       = 3,
    kNotifications = 4,
};

// 节点未命中降级策略（TkActionFlat.missPolicy）
enum class TkMissPolicy : int32_t {
    kSkip  = 0,  // 跳过该动作
    kWait  = 1,  // 等待后重试
    kAbort = 2,  // 终止序列
};

// 引擎状态机（与 Java EngineState 一致）
enum class TkEngineState : int32_t {
    kIdle      = 0,
    kPrepared  = 1,
    kRunning   = 2,
    kPaused    = 3,
    kStopping  = 4,
    kError     = 5,
};

// 注入档位（与 Java InjectionTier 一致）
enum class TkInjectionTier : int32_t {
    kL0Uinput        = 0,  // Root 增强：/dev/uinput 虚拟触摸设备
    kL1Evdev         = 1,  // Root 兼容：/dev/input/eventX 直写
    kL2Shell         = 2,  // Shell 档：C++ 写 outRing，Java(Shizuku) 注入
    kL3Accessibility = 3,  // 免 Root 保底：C++ 写 outRing，Java dispatchGesture
};

// 功耗档
enum class TkPowerProfile : int32_t {
    kAccuracy = 0,  // 精度优先：spin 3ms
    kBalanced = 1,  // 均衡：spin 2ms
    kSaver    = 2,  // 省电：永不自旋
};

// 定时器后端
enum class TkTimerBackend : int32_t {
    kTimerFd   = 0,  // timerfd_create(CLOCK_MONOTONIC) + TFD_TIMER_ABSTIME（主后端）
    kNanosleep = 1,  // clock_nanosleep(ABSTIME)（回退后端，分片轮询命令）
};

// 命令字（CommandQueue，Java→C++ 控制面）
enum class TkCommandId : int32_t {
    kStop         = 1,
    kPause        = 2,
    kResume       = 3,
    kSetParam     = 4,  // arg0 = 参数 id（TkParamId），arg1 = 值
    kNodeResolved = 5,  // arg0 = seq，arg1 = (y << 32) | x
    kReloadSeq    = 6,
};

// SetParam 的参数 id（TkCommand.arg0）
enum class TkParamId : int32_t {
    kIntervalNs      = 1,
    kSpinThresholdNs = 2,
    kPowerProfile    = 3,
};

// ---------------------------------------------------------------------------
// 错误码（与架构 §10.5 表格及 Java util/ErrorCodes.java 逐项对应）
// ---------------------------------------------------------------------------
enum class TkError : int32_t {
    // 成功
    kOk = 0,
    // —— 警告（非致命，1~999）——
    kWarnRateClamped     = 1001,  // 频率超档位上限，已钳制
    kWarnMissedTick      = 1002,  // 发生漏拍
    kWarnTierDowngraded  = 1003,  // 档位已降级
    kWarnRingFullDropped = 1004,  // ring 满已丢步
    kWarnCmdDropped      = 1005,  // 命令队列满已覆盖
    // —— C++ 核心（-1001 ~ -1099）——
    kErrBadHandle         = -1001,  // 无效句柄
    kErrEmptySeq          = -1002,  // 序列为空或越界
    kErrInvalidArg        = -1003,  // 参数非法
    kErrBadState          = -1004,  // 状态机不允许该操作
    kErrDeviceOpen        = -1005,  // 设备打开失败
    kErrWrite             = -1006,  // 写入失败
    kErrTimerCreate       = -1007,  // 定时器创建失败
    kErrThreadCreate      = -1008,  // 线程创建失败
    kErrRingFull          = -1009,  // ring 已满
    kErrBufferNotAttached = -1010,  // 缓冲未挂载
    // —— JNI 层（-2001 ~ -2099）——
    kErrEnvGetFailed    = -2001,  // JNIEnv 获取失败
    kErrNotDirectBuffer = -2002,  // 非直接缓冲
    kErrClassNotFound   = -2003,  // 类或字段未找到
    kErrLibNotLoaded    = -2004,  // native 库未加载
    kErrFdReadFailed    = -2005,  // fd 读取失败
    // —— Java 注入层（-3001 ~ -3099，仅文档约定，C++ 不产生）——
    kErrAccNotConnected   = -3001,
    kErrGestureCancelled  = -3002,
    kErrTierUnsupported   = -3003,
    kErrShizukuDenied     = -3004,
    kErrPermissionMissing = -3005,
    kErrCoordOutOfRange   = -3006,
};

// ---------------------------------------------------------------------------
// POD 结构体（packed，偏移以字节标注，必须与架构 §4.3 表格逐一一致）
// ---------------------------------------------------------------------------
#pragma pack(push, 1)

// 一个动作的扁平传输态 —— 64 字节
// 线程归属：Java（SequenceFlattener）写 seqBuf → C++（engine.loadSequence，控制面）读
struct TkActionFlat {
    int32_t type;          // 偏移  0：TkActionType
    int32_t flags;         // 偏移  4：bit0 百分比坐标 / bit1 启用抖动 / bit2 需节点解析
    int32_t repeat;        // 偏移  8：该动作重复次数（≥1）
    int32_t durationMs;    // 偏移 12：长按/滑动时长
    int64_t intervalNs;    // 偏移 16：动作后间隔（纳秒）
    int64_t holdNs;        // 偏移 24：按下保持（纳秒）
    int32_t pointCount;    // 偏移 32：跟随的 TkPointFlat 数量
    int32_t curveType;     // 偏移 36：TkCurveType
    int32_t sampleStepUs;  // 偏移 40：轨迹采样步长（微秒，默认 8000）
    int32_t coordMode;     // 偏移 44：TkCoordMode
    int32_t globalAction;  // 偏移 48：TkGlobalAction
    int32_t missPolicy;    // 偏移 52：TkMissPolicy
    float   jitterPct;     // 偏移 56：随机间隔百分比（0=关闭）
    int32_t reserved;      // 偏移 60：补齐 64B 对齐
};
static_assert(sizeof(TkActionFlat) == 64, "TkActionFlat 必须为 64 字节");
static_assert(offsetof(TkActionFlat, type)         ==  0, "偏移契约 0");
static_assert(offsetof(TkActionFlat, flags)        ==  4, "偏移契约 4");
static_assert(offsetof(TkActionFlat, repeat)       ==  8, "偏移契约 8");
static_assert(offsetof(TkActionFlat, durationMs)   == 12, "偏移契约 12");
static_assert(offsetof(TkActionFlat, intervalNs)   == 16, "偏移契约 16");
static_assert(offsetof(TkActionFlat, holdNs)       == 24, "偏移契约 24");
static_assert(offsetof(TkActionFlat, pointCount)   == 32, "偏移契约 32");
static_assert(offsetof(TkActionFlat, curveType)    == 36, "偏移契约 36");
static_assert(offsetof(TkActionFlat, sampleStepUs) == 40, "偏移契约 40");
static_assert(offsetof(TkActionFlat, coordMode)    == 44, "偏移契约 44");
static_assert(offsetof(TkActionFlat, globalAction) == 48, "偏移契约 48");
static_assert(offsetof(TkActionFlat, missPolicy)   == 52, "偏移契约 52");
static_assert(offsetof(TkActionFlat, jitterPct)    == 56, "偏移契约 56");
static_assert(offsetof(TkActionFlat, reserved)     == 60, "偏移契约 60");

// 一个坐标点 —— 16 字节（px；百分比与控件 resolve 由 Java 侧完成）
struct TkPointFlat {
    int32_t x;         // 偏移 0：目标 X（px）
    int32_t y;         // 偏移 4：目标 Y（px）
    int32_t offsetX;   // 偏移 8：相对偏移 dx
    int32_t offsetY;   // 偏移 12：相对偏移 dy
};
static_assert(sizeof(TkPointFlat) == 16, "TkPointFlat 必须为 16 字节");

// C++→Java 原子注入事件 —— 32 字节（outRing 的槽位单元）
// 线程归属：C++ 调度线程单写 → Java InjectionLooper 单读（SPSC 共享内存）
// 注：架构 §4.3 字段表列出 "pad 补齐 32B"，但 kind/slot/x/y/delayNs/arg/seq
// 合计已为 32B；按 32B 契约与 outRing 容量（64 + 4096×32B）计算，本结构
// 不含额外 pad 字段（偏差已记录于交付说明）。
struct TkStep {
    int32_t kind;     // 偏移  0：TkStepKind
    int32_t slot;     // 偏移  4：多指槽位 0..9
    int32_t x;        // 偏移  8：px 坐标 X
    int32_t y;        // 偏移 12：px 坐标 Y
    int64_t delayNs;  // 偏移 16：相对上一步的等待（绝对时间由 Scheduler 管理）
    int32_t arg;      // 偏移 24：global action / 条件参数
    int32_t seq;      // 偏移 28：单调递增序号，Java 侧校验 ring 槽位有效性
};
static_assert(sizeof(TkStep) == 32, "TkStep 必须为 32 字节");
static_assert(offsetof(TkStep, kind)    ==  0, "偏移契约 0");
static_assert(offsetof(TkStep, slot)    ==  4, "偏移契约 4");
static_assert(offsetof(TkStep, x)       ==  8, "偏移契约 8");
static_assert(offsetof(TkStep, y)       == 12, "偏移契约 12");
static_assert(offsetof(TkStep, delayNs) == 16, "偏移契约 16");
static_assert(offsetof(TkStep, arg)     == 24, "偏移契约 24");
static_assert(offsetof(TkStep, seq)     == 28, "偏移契约 28");

// 引擎配置扁平入参 —— 64 字节（Java nativeCreate 时写入 ByteBuffer）
// 布局为本轮固化的契约（架构 §4.3 未展开，字段与 Java 侧 TkEngineConfigFlat 对齐）：
struct TkEngineConfigFlat {
    int32_t schemaVersion;     // 偏移  0：固定 kConfigSchemaVersion = 1
    int32_t powerProfile;      // 偏移  4：TkPowerProfile
    int64_t defaultIntervalNs; // 偏移  8：默认动作间隔（周期基准）
    int64_t spinThresholdNs;   // 偏移 16：自旋阈值（0=禁用自旋）
    int32_t tier;              // 偏移 24：TkInjectionTier
    int32_t timerBackend;      // 偏移 28：TkTimerBackend
    int64_t statsPeriodNs;     // 偏移 32：统计快照写入周期（默认 200ms）
    int32_t screenW;           // 偏移 40：屏幕宽（px，uinput 轴范围用）
    int32_t screenH;           // 偏移 44：屏幕高（px）
    float   jitterPct;         // 偏移 48：全局随机间隔百分比（0=关闭）
    int32_t reserved0;         // 偏移 52
    int64_t reserved1;         // 偏移 56：补齐 64B
};
static_assert(sizeof(TkEngineConfigFlat) == 64, "TkEngineConfigFlat 必须为 64 字节");

// 统计快照 —— statsBuf 共享内存布局（11 × int64 = 88B，缓冲 256B）
// 线程归属：C++ 调度线程单写 → Java StatsBuffer 直读（无 JNI、无锁）
struct TkStatsSnapshot {
    int64_t p50Ns;        // 偏移  0
    int64_t p95Ns;        // 偏移  8
    int64_t p99Ns;        // 偏移 16
    int64_t meanNs;       // 偏移 24
    int64_t maxNs;        // 偏移 32
    int64_t minNs;        // 偏移 40
    int64_t missedTicks;  // 偏移 48：漏拍计数
    int64_t execCount;    // 偏移 56：已执行步数
    int64_t sampleCount;  // 偏移 64：误差样本数
    int64_t state;        // 偏移 72：TkEngineState
    int64_t tier;         // 偏移 80：TkInjectionTier
};
static_assert(sizeof(TkStatsSnapshot) == 88, "TkStatsSnapshot 必须为 88 字节（11×8B）");

// 命令单元 —— 32 字节（CommandQueue 槽位；仅控制面，非热路径）
struct TkCommand {
    int32_t cmd;    // 偏移 0：TkCommandId
    int32_t pad0;   // 偏移 4：对齐
    int64_t arg0;   // 偏移 8：命令参数 0
    int64_t arg1;   // 偏移 16：命令参数 1
    int64_t pad1;   // 偏移 24：补齐 32B
};
static_assert(sizeof(TkCommand) == 32, "TkCommand 必须为 32 字节");

#pragma pack(pop)

// 轨迹采样点（ActionBuilder → Trajectory 的中间产物，栈上传递，零分配）
struct TkTrajSample {
    int64_t delay_ns;  // 距上一个采样点的等待
    int32_t x;         // 采样点 X
    int32_t y;         // 采样点 Y
};

// 序列循环策略（对应 Java model/LoopPolicy）
struct TkLoopPolicy {
    int32_t kind;              // 0=无限 1=固定次数 2=固定时长 3=组合（先到先停）
    int32_t max_count;         // 固定次数上限
    int64_t max_duration_ns;   // 固定时长上限
};
