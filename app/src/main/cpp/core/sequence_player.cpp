// ============================================================================
// core/sequence_player.cpp — 序列播放器实现
// ============================================================================

#include "core/sequence_player.h"

#include <string.h>

namespace tk {

SequencePlayer::SequencePlayer() {
    policy_.kind = 0;
    policy_.max_count = 0;
    policy_.max_duration_ns = 0;
}

void SequencePlayer::load(const TkStep* steps, int count, const TkLoopPolicy& policy) {
    steps_ = steps;
    count_ = count;
    policy_ = policy;
    cursor_ = 0;
    emitted_ = 0;
    loops_ = 0;
    start_ns_ = 0;
}

void SequencePlayer::reset(int64_t start_ns) {
    cursor_ = 0;
    emitted_ = 0;
    loops_ = 0;
    start_ns_ = start_ns;
}

bool SequencePlayer::count_exhausted() const {
    // 固定次数（1）或组合（3）：完整遍数达到 max_count 即终止
    if (policy_.kind == 1 || policy_.kind == 3) {
        return policy_.max_count > 0 && loops_ >= policy_.max_count;
    }
    return false;
}

bool SequencePlayer::duration_exhausted(int64_t now_ns) const {
    // 固定时长（2）或组合（3）：运行时长达到上限即终止（0 = 不限时）
    if (policy_.kind == 2 || policy_.kind == 3) {
        if (policy_.max_duration_ns <= 0) return false;
        return (now_ns - start_ns_) >= policy_.max_duration_ns;
    }
    return false;
}

bool SequencePlayer::has_next(int64_t now_ns) const {
    if (count_ <= 0) return false;
    if (count_exhausted()) return false;
    if (duration_exhausted(now_ns)) return false;
    return true;
}

const TkStep& SequencePlayer::current() const {
    return steps_[cursor_];
}

void SequencePlayer::advance() {
    ++emitted_;
    ++cursor_;
    if (cursor_ >= count_) {
        cursor_ = 0;
        ++loops_;
    }
}

}  // namespace tk
