#pragma once
// ============================================================================
// core/stats.h — 周期抖动统计（架构 §2.3 #32 / §4.2）
// ----------------------------------------------------------------------------
// 定长环形采样（kStatsSamples=4096 个 int64 误差样本，误差 = 实际触发时刻 -
// 绝对 deadline，正值为晚触发），P50/P95/P99 用成员预分配 scratch 数组 +
// std::nth_element 计算（无堆分配）。快照写入共享 statsBuf（TkStatsSnapshot
// 88B 布局），Java StatsBuffer 直读，无 JNI、无锁。
// 【线程归属】Scheduler 调度线程单写；Java 侧只读快照。
// ============================================================================

#include <stdint.h>

#include "core/constants.h"
#include "core/types.h"

namespace tk {

class Stats {
 public:
    Stats();

    // 记录一个误差样本（纳秒，可正可负）
    void record(int64_t error_ns);

    void add_missed() { ++missed_; }
    void set_missed(int64_t n) { missed_ = n; }
    int64_t missed() const { return missed_; }
    int64_t sample_count() const { return count_; }
    int64_t exec_count() const { return exec_count_; }
    void inc_exec_count() { ++exec_count_; }

    // 把快照写入 statsBuf（88 字节 TkStatsSnapshot 布局；buf 为空则跳过）。
    // state / tier 由调用方传入（引擎当前值）。
    void write_snapshot(void* buf, int64_t state, int64_t tier);

    // 清零样本（重新开始统计）
    void reset_samples();

 private:
    void compute_percentiles();

    int64_t samples_[kStatsSamples];   // 定长环形样本
    int64_t sorted_[kStatsSamples];    // 百分位计算工作区（预分配，零堆分配）
    uint32_t idx_ = 0;                 // 环形写入游标
    int64_t count_ = 0;                // 累计样本数
    int64_t missed_ = 0;               // 漏拍计数
    int64_t exec_count_ = 0;           // 已执行步数
    // 运行累积量（均值/极值）
    int64_t sum_ = 0;
    int64_t max_ = 0;
    int64_t min_ = 0;
    // 百分位结果（compute_percentiles 填充）
    int64_t p50_ = 0;
    int64_t p95_ = 0;
    int64_t p99_ = 0;
};

}  // namespace tk
