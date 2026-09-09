// ============================================================================
// core/action_builder.cpp — TkActionFlat → TkStep[] 展开实现
// ============================================================================

#include "core/action_builder.h"

#include <string.h>

#include "core/constants.h"
#include "core/trajectory.h"
#include "platform/logging.h"

namespace tk {

namespace {

inline TkStep make_step(TkStepKind kind, int32_t slot, int32_t x, int32_t y,
                        int64_t delay_ns, int32_t arg, int32_t seq) {
    TkStep s;
    memset(&s, 0, sizeof(s));
    s.kind = static_cast<int32_t>(kind);
    s.slot = slot;
    s.x = x;
    s.y = y;
    s.delayNs = delay_ns;
    s.arg = arg;
    s.seq = seq;
    return s;
}

}  // namespace

void ActionBuilder::append_step(StepPool* pool, const TkStep& s, int32_t* seq) {
    TkStep copy = s;
    copy.seq = ++(*seq);
    pool->push(copy);
}

void ActionBuilder::append_wait(StepPool* pool, int64_t delay_ns, int32_t* seq) {
    if (delay_ns <= 0) return;
    append_step(pool, make_step(TkStepKind::kWait, 0, 0, 0, delay_ns, 0, 0), seq);
}

int64_t ActionBuilder::jittered_interval(const TkActionFlat& action, FastRng* rng,
                                         bool* clamped) {
    int64_t interval = action.intervalNs;
    // 频率边界钳制（PRD：1/小时 ~ 200/s；钳制记警告）
    if (interval > 0 && interval < kMinIntervalNs) {
        interval = kMinIntervalNs;
        if (clamped != nullptr) *clamped = true;
    }
    if (interval > kMaxIntervalNs) {
        interval = kMaxIntervalNs;
        if (clamped != nullptr) *clamped = true;
    }
    // 随机抖动：仅 flags bit1 且 jitterPct>0（默认关闭，PRD §模块二 P1；
    // 合规要求：仅用于避免固定周期异常，禁止"防检测"语义）
    if (rng != nullptr && (action.flags & 0x2) != 0 && action.jitterPct > 0.0f) {
        const float factor = rng->next_symmetric(action.jitterPct * 0.01f);
        interval += (int64_t)((float)interval * factor);
        if (interval < 0) interval = 0;
    }
    return interval;
}

int ActionBuilder::build(const TkActionFlat& action,
                         const TkPointFlat* points,
                         StepPool* pool,
                         FastRng* rng,
                         bool* clamped) {
    if (pool == nullptr || points == nullptr) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    const int32_t type = action.type;
    if (type < static_cast<int32_t>(TkActionType::kTap) ||
        type > static_cast<int32_t>(TkActionType::kCondition)) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    if (action.pointCount > kMaxPointsPerAction) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }

    // 首步起始序号（StepPool 内单调递增，Java 侧用于 ring 槽位校验）
    int32_t seq = pool->count();
    const int block_start_idx = pool->count();  // 本动作首步在池中的下标
    const int64_t hold_ns = (action.holdNs > 0)
        ? action.holdNs
        : (int64_t)action.durationMs * kNsPerMs;

    // 坐标 + 偏移（Java 已完成百分比/控件换算，此处仅叠加相对偏移）
    const auto px = [&points](int i) { return points[i].x + points[i].offsetX; };
    const auto py = [&points](int i) { return points[i].y + points[i].offsetY; };

    switch (static_cast<TkActionType>(type)) {
        case TkActionType::kTap: {
            if (action.pointCount < 1) return static_cast<int>(TkError::kErrInvalidArg);
            const int64_t press_ns = (hold_ns > 0) ? hold_ns : 60 * kNsPerMs;  // 默认 60ms
            append_step(pool, make_step(TkStepKind::kDown, 0, px(0), py(0), 0, 0, 0), &seq);
            append_step(pool, make_step(TkStepKind::kUp, 0, 0, 0, press_ns, 0, 0), &seq);
            append_step(pool, make_step(TkStepKind::kSync, 0, 0, 0, 0, 0, 0), &seq);
            break;
        }
        case TkActionType::kLongPress: {
            if (action.pointCount < 1) return static_cast<int>(TkError::kErrInvalidArg);
            const int64_t press_ns = (hold_ns > 0) ? hold_ns : 600 * kNsPerMs;
            append_step(pool, make_step(TkStepKind::kDown, 0, px(0), py(0), 0, 0, 0), &seq);
            append_step(pool, make_step(TkStepKind::kUp, 0, 0, 0, press_ns, 0, 0), &seq);
            append_step(pool, make_step(TkStepKind::kSync, 0, 0, 0, 0, 0, 0), &seq);
            break;
        }
        case TkActionType::kSwipe: {
            if (action.pointCount < 2) return static_cast<int>(TkError::kErrInvalidArg);
            const int64_t duration_ns = (hold_ns > 0) ? hold_ns : 300 * kNsPerMs;
            const int64_t step_ns = (action.sampleStepUs > 0)
                ? (int64_t)action.sampleStepUs * 1000
                : (int64_t)kDefaultSampleStepUs * 1000;
            append_step(pool, make_step(TkStepKind::kDown, 0, px(0), py(0), 0, 0, 0), &seq);
            TkTrajSample traj[kMaxTrajSamples];
            const int n = build_trajectory(static_cast<TkCurveType>(action.curveType),
                                           points[0], points[1],
                                           duration_ns, step_ns, traj, kMaxTrajSamples);
            for (int i = 0; i < n; ++i) {
                append_step(pool, make_step(TkStepKind::kMove, 0, traj[i].x, traj[i].y,
                                            traj[i].delay_ns, 0, 0), &seq);
            }
            append_step(pool, make_step(TkStepKind::kUp, 0, 0, 0, 0, 0, 0), &seq);
            append_step(pool, make_step(TkStepKind::kSync, 0, 0, 0, 0, 0, 0), &seq);
            break;
        }
        case TkActionType::kMultiTouch: {
            // 点语义：points 成对出现（起点, 终点）；落单点视为"原地点击"。
            // 多指按槽位顺序串行执行（每指独立 stroke + 槽位号，语义正确）。
            if (action.pointCount < 1) return static_cast<int>(TkError::kErrInvalidArg);
            const int64_t duration_ns = (hold_ns > 0) ? hold_ns : 200 * kNsPerMs;
            const int64_t step_ns = (action.sampleStepUs > 0)
                ? (int64_t)action.sampleStepUs * 1000
                : (int64_t)kDefaultSampleStepUs * 1000;
            int32_t slot = 0;
            for (int p = 0; p < action.pointCount && slot < kMaxSlots; p += 2, ++slot) {
                append_step(pool, make_step(TkStepKind::kDown, slot, px(p), py(p), 0, 0, 0), &seq);
                if (p + 1 < action.pointCount) {
                    TkTrajSample traj[kMaxTrajSamples];
                    const int n = build_trajectory(static_cast<TkCurveType>(action.curveType),
                                                   points[p], points[p + 1],
                                                   duration_ns, step_ns, traj, kMaxTrajSamples);
                    for (int i = 0; i < n; ++i) {
                        append_step(pool, make_step(TkStepKind::kMove, slot, traj[i].x, traj[i].y,
                                                    traj[i].delay_ns, 0, 0), &seq);
                    }
                } else {
                    // 落单点：保持 press_ns
                    const int64_t press_ns = (hold_ns > 0) ? hold_ns : 60 * kNsPerMs;
                    append_step(pool, make_step(TkStepKind::kUp, slot, 0, 0, press_ns, 0, 0), &seq);
                    continue;
                }
                append_step(pool, make_step(TkStepKind::kUp, slot, 0, 0, 0, 0, 0), &seq);
            }
            append_step(pool, make_step(TkStepKind::kSync, 0, 0, 0, 0, 0, 0), &seq);
            break;
        }
        case TkActionType::kWait: {
            // 等待动作：durationMs 为等待时长（拆到 kWait 步的 delayNs）
            const int64_t wait_ns = (int64_t)action.durationMs * kNsPerMs;
            append_step(pool, make_step(TkStepKind::kWait, 0, 0, 0, wait_ns, 0, 0), &seq);
            break;
        }
        case TkActionType::kGlobal: {
            append_step(pool, make_step(TkStepKind::kGlobal, 0, 0, 0, 0,
                                        action.globalAction, 0), &seq);
            break;
        }
        case TkActionType::kCondition: {
            // 条件动作（P1）：先发节点解析请求步，Java 侧按 missPolicy 决定
            // 跳过/等待/终止（本轮骨架：请求发出后正常继续）
            if ((action.flags & 0x4) != 0) {  // bit2 需节点解析
                append_step(pool, make_step(TkStepKind::kNodeResolveReq, 0, 0, 0, 0,
                                            action.missPolicy, 0), &seq);
            }
            if (action.pointCount >= 1) {
                const int64_t press_ns = (hold_ns > 0) ? hold_ns : 60 * kNsPerMs;
                append_step(pool, make_step(TkStepKind::kDown, 0, px(0), py(0), 0, 0, 0), &seq);
                append_step(pool, make_step(TkStepKind::kUp, 0, 0, 0, press_ns, 0, 0), &seq);
                append_step(pool, make_step(TkStepKind::kSync, 0, 0, 0, 0, 0, 0), &seq);
            }
            break;
        }
    }

    // 动作后间隔（repeat 的每次重复之间也插入间隔）：
    const int64_t interval = jittered_interval(action, rng, clamped);
    const int repeat = (action.repeat > 0) ? action.repeat : 1;
    if (repeat > kMaxRepeat) return static_cast<int>(TkError::kErrInvalidArg);

    // repeat > 1：将本动作已展开的步块复制 repeat-1 次，每次块后接间隔。
    const int block_len = pool->count() - block_start_idx;
    if (repeat > 1) {
        if (block_len <= 0) return static_cast<int>(TkError::kErrInvalidArg);
        for (int r = 1; r < repeat; ++r) {
            append_wait(pool, interval, &seq);
            for (int i = 0; i < block_len; ++i) {
                TkStep s = pool->data()[block_start_idx + i];
                s.seq = 0;  // append_step 重新编号
                append_step(pool, s, &seq);
            }
        }
    }
    append_wait(pool, interval, &seq);

    if (pool->count() >= pool->capacity()) {
        return static_cast<int>(TkError::kErrRingFull);  // 池满（容量保护）
    }
    return static_cast<int>(TkError::kOk);
}

int ActionBuilder::build_trajectory(TkCurveType curve,
                                    const TkPointFlat& from,
                                    const TkPointFlat& to,
                                    int64_t duration_ns,
                                    int64_t step_ns,
                                    TkTrajSample* out,
                                    int max_out) {
    return Trajectory::sample(curve, from, to, duration_ns, step_ns, out, max_out);
}

}  // namespace tk
