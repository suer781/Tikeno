// ============================================================================
// core/high_res_timer.cpp — 双后端高精度定时器实现
// ============================================================================

#include "core/high_res_timer.h"

#include <sys/timerfd.h>
#include <time.h>
#include <unistd.h>
#include <errno.h>

#include "core/constants.h"
#include "core/time_util.h"
#include "platform/logging.h"

namespace tk {

HighResTimer::HighResTimer() = default;

HighResTimer::~HighResTimer() {
    if (timer_fd_ >= 0) {
        close(timer_fd_);
        timer_fd_ = -1;
    }
}

int HighResTimer::create(TkTimerBackend force_backend) {
    if (force_backend == TkTimerBackend::kTimerFd) {
        // 主后端：非阻塞 + close-on-exec，ABSTIME 由 arm_absolute 设置
        timer_fd_ = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK | TFD_CLOEXEC);
        if (timer_fd_ >= 0) {
            backend_ = TkTimerBackend::kTimerFd;
            created_ = true;
            TK_LOGD("HighResTimer: 使用 timerfd 后端 fd=%d", timer_fd_);
            return static_cast<int>(TkError::kOk);
        }
        TK_LOGW("HighResTimer: timerfd_create 失败 errno=%d，回退 clock_nanosleep", errno);
    }
    // 回退后端：无需 fd，arm_absolute 只记录 deadline
    backend_ = TkTimerBackend::kNanosleep;
    created_ = true;
    TK_LOGD("HighResTimer: 使用 clock_nanosleep 后端");
    return static_cast<int>(TkError::kOk);
}

int HighResTimer::arm_absolute(int64_t deadline_ns) {
    if (!created_) return static_cast<int>(TkError::kErrTimerCreate);
    armed_deadline_ns_ = deadline_ns;
    if (backend_ != TkTimerBackend::kTimerFd) {
        return static_cast<int>(TkError::kOk);  // nanosleep 后端由 Scheduler 直接睡眠
    }
    struct itimerspec its;
    its.it_interval.tv_sec = 0;   // 单次触发（周期由 Scheduler 绝对推进）
    its.it_interval.tv_nsec = 0;
    its.it_value = ns_to_timespec(deadline_ns);
    if (timerfd_settime(timer_fd_, TFD_TIMER_ABSTIME, &its, nullptr) != 0) {
        TK_LOGE("HighResTimer: timerfd_settime 失败 errno=%d", errno);
        return static_cast<int>(TkError::kErrWrite);
    }
    return static_cast<int>(TkError::kOk);
}

int HighResTimer::cancel() {
    if (!created_ || backend_ != TkTimerBackend::kTimerFd) {
        armed_deadline_ns_ = 0;
        return static_cast<int>(TkError::kOk);
    }
    struct itimerspec its;
    its.it_interval.tv_sec = 0;
    its.it_interval.tv_nsec = 0;
    its.it_value.tv_sec = 0;    // 全零 = 取消
    its.it_value.tv_nsec = 0;
    if (timerfd_settime(timer_fd_, 0, &its, nullptr) != 0) {
        return static_cast<int>(TkError::kErrWrite);
    }
    armed_deadline_ns_ = 0;
    return static_cast<int>(TkError::kOk);
}

int HighResTimer::drain_expired() {
    if (timer_fd_ < 0) return static_cast<int>(TkError::kOk);
    uint64_t expirations = 0;
    ssize_t n = read(timer_fd_, &expirations, sizeof(expirations));
    if (n < 0) {
        if (errno == EAGAIN) return static_cast<int>(TkError::kOk);  // 非阻塞无数据
        return static_cast<int>(TkError::kErrFdReadFailed);
    }
    return static_cast<int>(TkError::kOk);
}

}  // namespace tk
