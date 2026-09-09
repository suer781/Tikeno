// ============================================================================
// core/stats.cpp — 周期抖动统计实现
// ============================================================================

#include "core/stats.h"

#include <string.h>
#include <algorithm>

namespace tk {

Stats::Stats() {
    reset_samples();
}

void Stats::reset_samples() {
    memset(samples_, 0, sizeof(samples_));
    idx_ = 0;
    count_ = 0;
    sum_ = 0;
    max_ = 0;
    min_ = 0;
    p50_ = p95_ = p99_ = 0;
}

void Stats::record(int64_t error_ns) {
    samples_[idx_ % kStatsSamples] = error_ns;
    ++idx_;
    ++count_;
    sum_ += error_ns;
    if (count_ == 1 || error_ns > max_) max_ = error_ns;
    if (count_ == 1 || error_ns < min_) min_ = error_ns;
}

void Stats::compute_percentiles() {
    // 取最近至多 kStatsSamples 个样本（环形展开到 sorted_）
    int64_t n = count_;
    if (n > kStatsSamples) n = kStatsSamples;
    if (n <= 0) {
        p50_ = p95_ = p99_ = 0;
        return;
    }
    if (count_ <= kStatsSamples) {
        memcpy(sorted_, samples_, (size_t)n * sizeof(int64_t));
    } else {
        // 环形展开：最旧样本位于 idx_ % N，其后为最新
        const uint32_t start = idx_ % kStatsSamples;
        const int head_len = kStatsSamples - (int)start;
        memcpy(sorted_, samples_ + start, (size_t)head_len * sizeof(int64_t));
        memcpy(sorted_ + head_len, samples_, (size_t)start * sizeof(int64_t));
    }

    // std::nth_element：O(n) 原地选择，无堆分配（满足热路径零 malloc 的
    // 近似约束 —— writeTo 为 200ms 一次的控制面节拍，不在纳秒级热循环内）
    const auto pct_index = [n](int pct) -> int {
        // 上取整百分位索引：pct% 分位数
        int64_t idx = (int64_t)n * pct + 99;
        idx /= 100;
        if (idx < 1) idx = 1;
        if (idx > n) idx = n;
        return (int)idx - 1;
    };
    const int i50 = pct_index(50);
    const int i95 = pct_index(95);
    const int i99 = pct_index(99);

    std::nth_element(sorted_, sorted_ + i50, sorted_ + n);
    p50_ = sorted_[i50];
    if (i95 != i50) {
        std::nth_element(sorted_, sorted_ + i95, sorted_ + n);
        p95_ = sorted_[i95];
    } else {
        p95_ = p50_;
    }
    if (i99 != i95 && i99 != i50) {
        std::nth_element(sorted_, sorted_ + i99, sorted_ + n);
        p99_ = sorted_[i99];
    } else if (i99 == i50) {
        p99_ = p50_;
    } else {
        p99_ = p95_;
    }
}

void Stats::write_snapshot(void* buf, int64_t state, int64_t tier) {
    if (buf == nullptr) return;
    compute_percentiles();

    TkStatsSnapshot snap;
    memset(&snap, 0, sizeof(snap));
    snap.p50Ns = p50_;
    snap.p95Ns = p95_;
    snap.p99Ns = p99_;
    snap.meanNs = (count_ > 0) ? (sum_ / count_) : 0;
    snap.maxNs = max_;
    snap.minNs = min_;
    snap.missedTicks = missed_;
    snap.execCount = exec_count_;
    snap.sampleCount = count_;
    snap.state = state;
    snap.tier = tier;

    memcpy(buf, &snap, sizeof(snap));  // statsBuf ≥ 256B，快照 88B ✓
}

}  // namespace tk
