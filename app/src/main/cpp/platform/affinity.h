#pragma once
// ============================================================================
// platform/affinity.h — CPU 亲和性与实时调度（架构 §2.3 #41 / §1.2 难点 2）
// ----------------------------------------------------------------------------
// - pin_to_big_core：解析 /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq
//   找出最大频率核心集合，sched_setaffinity 绑定（降低跨核迁移抖动）。
// - try_sched_fifo：SCHED_FIFO(60)（架构 §10.3 / §1.2：仅 Root 档启用，
//   失败静默回退 SCHED_OTHER，不视为错误）。
// - set_thread_name：pthread_setname_np 封装。
// ============================================================================

#include <pthread.h>

namespace tk {

// 绑定到最大频率核心（大核）；探测失败返回 false（保持默认调度）。
bool pin_to_big_core(pthread_t tid);

// 尝试提升为 SCHED_FIFO(priority)；成功 true，失败 false（静默降级）。
bool try_sched_fifo(pthread_t tid, int priority);

// 设置线程名（≤15 字符；失败忽略）。
void set_thread_name(pthread_t tid, const char* name);

}  // namespace tk
