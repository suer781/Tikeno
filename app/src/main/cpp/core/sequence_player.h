#pragma once
// ============================================================================
// core/sequence_player.h — 序列播放器（架构 §2.3 #30 / §4.2）
// ----------------------------------------------------------------------------
// 持有展开后的 TkStep 序列（来自 StepPool），按循环策略推进游标。
// 【线程归属】Scheduler 调度线程独占（reset 在 start 前、推进在循环内）。
// 循环策略（对应 Java model/LoopPolicy）：
//   0=无限  1=固定次数（序列完整跑完 max_count 遍）  2=固定时长  3=组合（先到先停）
// 时长策略由 has_next(now) 实时判定（start_ns 由 Scheduler 在 run 入口设置）。
// ============================================================================

#include "core/types.h"

namespace tk {

class SequencePlayer {
 public:
    SequencePlayer();

    // 装载展开好的步序列（不拷贝，指针指向 StepPool 存储）。
    void load(const TkStep* steps, int count, const TkLoopPolicy& policy);

    // 每轮运行开始调用：游标/计数复位并记录起始时间。
    void reset(int64_t start_ns);

    // 是否还有下一步（含循环策略与时长终止判定）。
    bool has_next(int64_t now_ns) const;

    // 当前步（has_next 为真时合法）。
    const TkStep& current() const;

    // 推进游标；序列尾则回绕并累加轮数。
    void advance();

    int64_t emitted() const { return emitted_; }
    int32_t loops_done() const { return loops_; }
    int count() const { return count_; }
    bool loaded() const { return steps_ != nullptr && count_ > 0; }

 private:
    bool count_exhausted() const;
    bool duration_exhausted(int64_t now_ns) const;

    const TkStep* steps_ = nullptr;
    int count_ = 0;
    int cursor_ = 0;
    TkLoopPolicy policy_{};
    int64_t start_ns_ = 0;
    int64_t emitted_ = 0;
    int32_t loops_ = 0;
};

}  // namespace tk
