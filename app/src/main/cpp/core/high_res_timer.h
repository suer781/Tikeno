#pragma once
// ============================================================================
// core/high_res_timer.h — 双后端高精度定时器（架构 §2.3 #22 / §1.2 难点 2）
// ----------------------------------------------------------------------------
// 主后端 kTimerFd：timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK) +
//   timerfd_settime(TFD_TIMER_ABSTIME) —— 内核 hrtimer，与 clock_nanosleep 同源
//   同精度，额外收益是可挂进 epoll 与命令 fd 统一等待（命令到达立即唤醒）。
// 回退后端 kNanosleep：timerfd 创建失败时使用 clock_nanosleep(ABSTIME)，
//   以 kNanosleepSliceNs 分片睡眠、片间轮询命令，保证 STOP 响应 ≤ 分片时长。
// 【线程归属】由 Scheduler 调度线程独占使用，非线程安全（单线程语义）。
// ============================================================================

#include <stdint.h>

#include "core/types.h"

namespace tk {

class HighResTimer {
 public:
    HighResTimer();
    ~HighResTimer();

    HighResTimer(const HighResTimer&) = delete;
    HighResTimer& operator=(const HighResTimer&) = delete;

    // 创建后端。优先 kTimerFd；force_backend 可强制指定（配置传入）。
    // 返回 TK_OK 或 TK_ERR_TIMER_CREATE。
    int create(TkTimerBackend force_backend);

    // 设置下一次到期的绝对 deadline（纳秒，CLOCK_MONOTONIC 时基）。
    // kTimerFd：timerfd_settime(ABSTIME)；kNanosleep：仅记录 deadline。
    // 返回 TK_OK 或 TK_ERR_WRITE。
    int arm_absolute(int64_t deadline_ns);

    // kTimerFd 专用：取消已武装的定时（it_value 置零）。
    int cancel();

    // kTimerFd 专用：到期后需读出 8 字节计数值以解除可读态；返回 TK_OK/TK_ERR_FD_READ_FAILED。
    int drain_expired();

    // kNanosleep 后端专用：返回当前已武装的 deadline（供 Scheduler 分片睡眠）。
    int64_t armed_deadline_ns() const { return armed_deadline_ns_; }

    int backend() const { return static_cast<int>(backend_); }
    bool is_timer_fd() const { return backend_ == TkTimerBackend::kTimerFd; }
    int fd() const { return timer_fd_; }
    bool valid() const { return created_; }

 private:
    TkTimerBackend backend_ = TkTimerBackend::kTimerFd;
    int timer_fd_ = -1;
    int64_t armed_deadline_ns_ = 0;
    bool created_ = false;
};

}  // namespace tk
