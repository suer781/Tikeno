#pragma once
// ============================================================================
// core/command_queue.h — 控制面命令队列（架构 §2.3 #26 / §7.2）
// ----------------------------------------------------------------------------
// 通道：Java（nativePostCommand）→ C++ 调度线程。
// 命令：STOP / PAUSE / RESUME / SET_PARAM / NODE_RESOLVED / RELOAD_SEQ。
// 【线程归属】多生产者（通知接收线程 / 磁贴线程 / UI 线程都可能发 STOP），
// 单消费者（调度线程）。push 侧用短临界区互斥保护（仅控制面，量级 ~1 次/秒）；
// pop 侧无锁（单消费者直接读）。push 成功后须 write(cmdEfd) 唤醒调度线程。
// ============================================================================

#include <stdint.h>
#include <pthread.h>

#include "core/constants.h"
#include "core/ring_buffer.h"
#include "core/types.h"

namespace tk {

class CommandQueue {
 public:
    static constexpr int kCapacity = kCommandQueueCapacity;  // 64 槽

    CommandQueue();
    ~CommandQueue();

    CommandQueue(const CommandQueue&) = delete;
    CommandQueue& operator=(const CommandQueue&) = delete;

    // 生产者（任意控制面线程）：入队；满则返回 false（Java 侧记 TK_WARN_CMD_DROPPED）。
    bool try_push(const TkCommand& cmd);

    // 消费者（仅调度线程）：取一条命令；空返回 false。
    bool try_pop(TkCommand* out);

    // 清空积压（停止后复位）。
    void clear();

 private:
    pthread_mutex_t mu_;
    SpscRing<TkCommand, kCapacity> ring_;
};

}  // namespace tk
