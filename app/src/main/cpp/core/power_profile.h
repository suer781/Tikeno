#pragma once
// ============================================================================
// core/power_profile.h — 三档功耗预设参数表（架构 §2.3 #33 / PRD P0）
// ----------------------------------------------------------------------------
// 精度优先：自旋 3ms，timerfd 后端，统计 200ms
// 均衡    ：自旋 2ms，timerfd 后端，统计 200ms（默认）
// 省电    ：自旋 0（永不自旋），timerfd 后端，统计 500ms
// ============================================================================

#include "core/types.h"

namespace tk {

struct TkPowerParams {
    int64_t spin_threshold_ns;   // 自旋阈值（0 = 永不自旋）
    TkTimerBackend backend;      // 首选定时器后端
    int64_t stats_period_ns;     // 统计快照写入周期
};

// 按功耗档取参数表；非法档位回退均衡档。
TkPowerParams power_params_for(int profile);

}  // namespace tk
