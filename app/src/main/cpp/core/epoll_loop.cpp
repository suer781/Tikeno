// ============================================================================
// core/epoll_loop.cpp — epoll 事件循环封装实现
// ============================================================================

#include "core/epoll_loop.h"

#include <sys/epoll.h>
#include <unistd.h>
#include <errno.h>

#include "core/types.h"
#include "platform/logging.h"

namespace tk {

EpollLoop::EpollLoop() = default;

EpollLoop::~EpollLoop() {
    if (ep_fd_ >= 0) {
        close(ep_fd_);
        ep_fd_ = -1;
    }
}

int EpollLoop::create() {
    if (ep_fd_ >= 0) return static_cast<int>(TkError::kOk);  // 幂等
    ep_fd_ = epoll_create1(EPOLL_CLOEXEC);
    if (ep_fd_ < 0) {
        TK_LOGE("EpollLoop: epoll_create1 失败 errno=%d", errno);
        return static_cast<int>(TkError::kErrTimerCreate);
    }
    return static_cast<int>(TkError::kOk);
}

int EpollLoop::add_fd(int fd, uint32_t events) {
    if (ep_fd_ < 0 || fd < 0) return static_cast<int>(TkError::kErrInvalidArg);
    struct epoll_event ev;
    ev.events = events;
    ev.data.fd = fd;
    if (epoll_ctl(ep_fd_, EPOLL_CTL_ADD, fd, &ev) != 0) {
        // EEXIST 视为幂等成功
        if (errno != EEXIST) {
            TK_LOGE("EpollLoop: EPOLL_CTL_ADD fd=%d 失败 errno=%d", fd, errno);
            return static_cast<int>(TkError::kErrInvalidArg);
        }
    }
    return static_cast<int>(TkError::kOk);
}

int EpollLoop::remove_fd(int fd) {
    if (ep_fd_ < 0 || fd < 0) return static_cast<int>(TkError::kErrInvalidArg);
    if (epoll_ctl(ep_fd_, EPOLL_CTL_DEL, fd, nullptr) != 0 && errno != ENOENT) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    return static_cast<int>(TkError::kOk);
}

int EpollLoop::wait_once(struct epoll_event* out_events, int max_events, int timeout_ms) {
    if (ep_fd_ < 0) return -1;
    int n = epoll_wait(ep_fd_, out_events, max_events, timeout_ms);
    if (n < 0 && errno == EINTR) return 0;  // 信号打断归一化为超时语义
    return n;
}

}  // namespace tk
