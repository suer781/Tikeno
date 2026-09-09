// ============================================================================
// core/command_queue.cpp — 控制面命令队列实现
// ============================================================================

#include "core/command_queue.h"

namespace tk {

CommandQueue::CommandQueue() {
    pthread_mutex_init(&mu_, nullptr);
}

CommandQueue::~CommandQueue() {
    pthread_mutex_destroy(&mu_);
}

bool CommandQueue::try_push(const TkCommand& cmd) {
    pthread_mutex_lock(&mu_);
    const bool ok = ring_.try_push(cmd);
    pthread_mutex_unlock(&mu_);
    return ok;
}

bool CommandQueue::try_pop(TkCommand* out) {
    // 单消费者（调度线程），无锁
    return ring_.try_pop(out);
}

void CommandQueue::clear() {
    pthread_mutex_lock(&mu_);
    ring_.clear();
    pthread_mutex_unlock(&mu_);
}

}  // namespace tk
