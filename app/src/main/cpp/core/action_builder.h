#pragma once
// ============================================================================
// core/action_builder.h — TkActionFlat → TkStep[] 展开（架构 §2.3 #29 / §4.2）
// ----------------------------------------------------------------------------
// 把"动作 + 坐标点"展开为原子 TkStep 序列（DOWN/MOVE/UP/SYNC/WAIT/GLOBAL），
// 写入调用方持有的 StepPool（定长池，控制面一次性预分配，运行期零分配）。
// delay 语义：每个 step 的 delayNs = 本步执行完到下一步之间的等待。
//   → 序列首个动作立即执行（首步 deadline = start），动作间隔作为
//     kWait 步尾随，随机抖动在此处烘焙（FastRng，控制面）。
// 【线程归属】仅控制面（engine.loadSequence）调用。
// ============================================================================

#include "core/constants.h"
#include "core/types.h"
#include "core/fast_rng.h"

namespace tk {

// 定长步池：TkStep × kStepPoolCapacity（512KB，随 engine 对象一次性分配）
class StepPool {
 public:
    StepPool() : count_(0) {}

    void reset() { count_ = 0; }
    bool push(const TkStep& s) {
        if (count_ >= kStepPoolCapacity) return false;
        buf_[count_++] = s;
        return true;
    }
    const TkStep* data() const { return buf_; }
    int count() const { return count_; }
    int capacity() const { return kStepPoolCapacity; }

 private:
    TkStep buf_[kStepPoolCapacity];
    int count_;
};

class ActionBuilder {
 public:
    // 展开一个动作为原子步并追加到 pool。
    //   points       ：该动作的 TkPointFlat 数组（紧跟在 seqBuf 的 TkActionFlat 之后）
    //   rng          ：抖动随机源（控制面）
    //   clamped(out) ：若间隔被钳制到 kMinIntervalNs 则置 true
    // 返回 TK_OK / TK_ERR_INVALID_ARG / TK_ERR_RING_FULL（池满，借用错误码）。
    static int build(const TkActionFlat& action,
                     const TkPointFlat* points,
                     StepPool* pool,
                     FastRng* rng,
                     bool* clamped);

    // 轨迹展开入口（类图 §4.2 ActionBuilder.buildTrajectory 对应）：
    // 把一次 stroke 的 MOVE 采样写入 out（调用方预分配），返回采样数。
    static int build_trajectory(TkCurveType curve,
                                const TkPointFlat& from,
                                const TkPointFlat& to,
                                int64_t duration_ns,
                                int64_t step_ns,
                                TkTrajSample* out,
                                int max_out);

 private:
    static void append_wait(StepPool* pool, int64_t delay_ns, int32_t* seq);
    static void append_step(StepPool* pool, const TkStep& s, int32_t* seq);

    // 动作间等待（含抖动）→ 一个 kWait 步
    static int64_t jittered_interval(const TkActionFlat& action, FastRng* rng, bool* clamped);
};

}  // namespace tk
