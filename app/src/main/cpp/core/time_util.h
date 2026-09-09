#pragma once
// ============================================================================
// core/time_util.h — 高精度时间工具（header-only，always_inline，架构 §2.3 #21）
// ----------------------------------------------------------------------------
// 【线程归属】Scheduler 调度线程热路径调用；clock_gettime 走 vDSO，
// 无真实系统调用开销。全文件零堆分配。
// ============================================================================

#include <time.h>
#include <stdint.h>

#include "core/constants.h"

namespace tk {

// 当前时间（CLOCK_MONOTONIC，不受 NTP 调整影响），单位纳秒
static inline __attribute__((always_inline)) int64_t now_ns() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * kNsPerSec + ts.tv_nsec;
}

// 绝对 deadline + 增量 → 新绝对 deadline（防溢出）
static inline __attribute__((always_inline)) int64_t deadline_add(int64_t deadline_ns,
                                                                  int64_t delta_ns) {
    return deadline_ns + delta_ns;
}

// 纳秒 → timespec（供 timerfd_settime / clock_nanosleep 的 ABSTIME 入参）
static inline __attribute__((always_inline)) struct timespec ns_to_timespec(int64_t ns) {
    struct timespec ts;
    ts.tv_sec  = (time_t)(ns / kNsPerSec);
    ts.tv_nsec = (long)(ns % kNsPerSec);
    // ABSTIME 允许 tv_nsec 为"未来"值，无需规范化负值（调用方保证 deadline > 0）
    return ts;
}

// 毫秒超时 → timespec（相对时间，供 pthread_cond_timedwait 用 CLOCK_MONOTONIC）
static inline __attribute__((always_inline)) struct timespec ms_to_rel_timespec(int64_t ms) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    ts.tv_sec  += (time_t)(ms / 1000);
    ts.tv_nsec += (long)((ms % 1000) * kNsPerMs);
    if (ts.tv_nsec >= (long)kNsPerSec) {
        ts.tv_sec  += 1;
        ts.tv_nsec -= (long)kNsPerSec;
    }
    return ts;
}

// 跳步补拍计算：落后 now - next 有多少个完整周期（架构 §2.2.3 关键实现 1）
// 返回需要跳过的步数（≥0）；period_ns ≤ 0 时返回 0 防除零。
static inline __attribute__((always_inline)) int64_t catch_up_steps(int64_t now_ns_v,
                                                                    int64_t next_ns,
                                                                    int64_t period_ns) {
    if (period_ns <= 0 || now_ns_v <= next_ns) return 0;
    int64_t lag = now_ns_v - next_ns;
    return lag / period_ns;
}

}  // namespace tk
