#pragma once
// ============================================================================
// core/epoll_loop.h — epoll 事件循环封装（架构 §2.3 #23）
// ----------------------------------------------------------------------------
// 统一监听 timerFd + cmdEfd（命令唤醒）等 fd，单线程完成"定时 + 收令"。
// 【线程归属】Scheduler 调度线程独占，非线程安全。
// ============================================================================

#include <sys/epoll.h>
#include <stdint.h>

namespace tk {

class EpollLoop {
 public:
    static constexpr int kMaxEvents = 8;

    EpollLoop();
    ~EpollLoop();

    EpollLoop(const EpollLoop&) = delete;
    EpollLoop& operator=(const EpollLoop&) = delete;

    // 创建 epoll 实例（epoll_create1(CLOEXEC)）；返回 TK_OK / TK_ERR_TIMER_CREATE。
    int create();

    // 注册 fd（边缘触发由调用方通过 events 指定；默认水平触发 + 事件输入）。
    // 返回 TK_OK / TK_ERR_INVALID_ARG。
    int add_fd(int fd, uint32_t events);

    // 注销 fd。
    int remove_fd(int fd);

    // 等待一次事件；返回事件数（0=超时），负数=错误（EINTR 归一化为 0）。
    int wait_once(struct epoll_event* out_events, int max_events, int timeout_ms);

    int ep_fd() const { return ep_fd_; }
    bool valid() const { return ep_fd_ >= 0; }

 private:
    int ep_fd_ = -1;
};

}  // namespace tk
