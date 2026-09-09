// ============================================================================
// core/power_profile.cpp — 三档功耗预设参数表实现
// ============================================================================

#include "core/power_profile.h"

#include "core/constants.h"

namespace tk {

TkPowerParams power_params_for(int profile) {
    TkPowerParams p;
    switch (static_cast<TkPowerProfile>(profile)) {
        case TkPowerProfile::kAccuracy:
            p.spin_threshold_ns = kSpinAccuracyNs;
            p.backend = TkTimerBackend::kTimerFd;
            p.stats_period_ns = kDefaultStatsPeriodNs;
            break;
        case TkPowerProfile::kSaver:
            p.spin_threshold_ns = kSpinSaverNs;
            p.backend = TkTimerBackend::kTimerFd;
            p.stats_period_ns = 500 * kNsPerMs;
            break;
        case TkPowerProfile::kBalanced:
        default:
            p.spin_threshold_ns = kSpinBalancedNs;
            p.backend = TkTimerBackend::kTimerFd;
            p.stats_period_ns = kDefaultStatsPeriodNs;
            break;
    }
    return p;
}

}  // namespace tk
