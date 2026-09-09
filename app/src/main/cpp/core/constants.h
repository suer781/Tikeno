#pragma once
// ============================================================================
// core/constants.h — 全局常量（架构 §2.3 #20）
// 所有跨文件共享的数值常量集中于此，禁止散落魔法值。
// ============================================================================

#include <stdint.h>
#include <stddef.h>

namespace tk {

// —— 句柄与 magic ——
inline constexpr uint32_t kHandleMagic   = 0x544B454EU;  // "TKEN"
inline constexpr uint64_t kRingMagic     = 0x544B454E4F555452ULL; // "TKENOUTR"

// —— 共享缓冲容量（架构 §4.3 共享缓冲布局表）——
inline constexpr size_t kSeqBufBytes     = 256 * 1024;  // seqBuf：256 KB
inline constexpr size_t kOutRingBytes    = 256 * 1024;  // outRing：256 KB
inline constexpr size_t kStatsBufBytes   = 256;         // statsBuf：256 B

// outRing 布局：前 64B header（head/tail/capacity/magic 各 8B）+ 4096 × TkStepPadded(32B)
inline constexpr size_t kOutRingHeaderBytes = 64;
inline constexpr size_t kOutRingStepBytes   = 32;
inline constexpr uint64_t kOutRingCapacity  = 4096;  // 64 + 4096*32 = 131136 ≤ 256KB ✓
static_assert(kOutRingHeaderBytes + kOutRingCapacity * kOutRingStepBytes <= kSeqBufBytes,
              "outRing 必须装得进 256KB");

// seqBuf 布局：前 8B = actionCount(int32) + schemaVersion(int32)
inline constexpr int32_t kConfigSchemaVersion = 1;

// —— 统计 ——
inline constexpr int kStatsSamples = 4096;  // 定长环形样本数（P50/P95/P99）

// —— 定时 / 自旋（架构 §2.2.3）——
inline constexpr int64_t kNsPerMs   = 1000 * 1000;
inline constexpr int64_t kNsPerSec  = 1000 * 1000 * 1000;
inline constexpr int64_t kUsPerSec  = 1000 * 1000;

// 三档自旋阈值：精度 3ms / 均衡 2ms / 省电 0（永不自旋）
inline constexpr int64_t kSpinAccuracyNs = 3 * kNsPerMs;
inline constexpr int64_t kSpinBalancedNs = 2 * kNsPerMs;
inline constexpr int64_t kSpinSaverNs    = 0;
inline constexpr int64_t kDefaultSpinThresholdNs = kSpinBalancedNs;

// 仅当周期 < 50ms 时允许自旋（长间隔一律睡眠，防烧电）
inline constexpr int64_t kSpinMaxPeriodNs = 50 * kNsPerMs;

// nanosleep 回退后端的睡眠分片（保证 STOP 命令响应 ≤ 分片时长）
inline constexpr int64_t kNanosleepSliceNs = 20 * kNsPerMs;

// —— 频率边界（PRD：1 次/小时 ~ 200 次/秒）——
inline constexpr int64_t kMinIntervalNs = 5 * kNsPerMs;                 // 200 次/秒
inline constexpr int64_t kMaxIntervalNs = (int64_t)3600 * kNsPerSec;    // 1 次/小时

// 统计快照默认写入周期
inline constexpr int64_t kDefaultStatsPeriodNs = 200 * kNsPerMs;

// —— 序列容量 ——
inline constexpr int kMaxActions      = 256;     // 序列最大动作数
inline constexpr int kMaxPointsPerAction = 20;   // 单动作最大点数（多指 ≤10 指×2）
inline constexpr int kMaxSlots        = 10;      // 多指槽位上限（AOSP MAX_STROKE_COUNT）
inline constexpr int kStepPoolCapacity = 16384;  // StepPool 定长步池（32B×16384=512KB）
inline constexpr int kMaxTrajSamples  = 1024;    // 单次轨迹最大采样数（防越界放大）
inline constexpr int kMaxRepeat       = 10000;   // 单动作最大重复次数

// —— 轨迹默认 ——
inline constexpr int32_t kDefaultSampleStepUs = 8000;  // 默认采样步长 8ms

// —— 命令队列 ——
inline constexpr int kCommandQueueCapacity = 64;

// —— SCHED_FIFO ——
inline constexpr int kSchedFifoPriority = 60;  // 避开 90+ 的系统关键线程

// —— 人手模拟曲线的横向摆动幅度（相对直线距离的比例，确定性，非随机化开关）——
inline constexpr float kHumanWobbleRatio = 0.06f;
// 贝塞尔控制点偏移比例（垂直于起终点连线）
inline constexpr float kBezierCtrlRatio  = 0.12f;

// —— 版本（nativeGetBuildInfo 返回）——
#ifndef TK_VERSION
#define TK_VERSION "0.1.0"
#endif

}  // namespace tk
