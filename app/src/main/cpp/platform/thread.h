#pragma once
// ============================================================================
// platform/thread.h — pthread 封装（架构 §2.3 #42 / §4.2 TkThread）
// ----------------------------------------------------------------------------
// bionic 无 pthread_timedjoin_np / pthread_tryjoin_np，
// 超时 join 用"完成标志 + 条件变量"实现：包装函数在原线程函数返回后广播，
// join_timed 带超时等待标志，随后 pthread_join（此时必然立即返回）。
// 架构 §7.3 规则 4：join 必须带超时，超时后 TK_LOGE 并继续（宁可泄漏一个
// 阻塞线程也不卡死启动流程）。禁止拷贝。
// ============================================================================

#include <pthread.h>
#include <stdint.h>

namespace tk {

class TkThread {
 public:
    typedef void* (*EntryFn)(void* arg);

    TkThread();
    ~TkThread();

    TkThread(const TkThread&) = delete;
    TkThread& operator=(const TkThread&) = delete;

    // 创建并启动线程；name 供 pthread_setname_np（≤15 字符）。
    // 返回 TK_OK / TK_ERR_THREAD_CREATE。
    int start(EntryFn fn, void* arg, const char* name);

    // 带超时的 join；超时返回 false（线程仍在运行，不阻塞调用方）。
    bool join_timed(int timeout_ms);

    bool running() const { return started_ && !done_; }
    bool started() const { return started_; }
    pthread_t tid() const { return tid_; }

 private:
    static void* trampoline(void* self);

    pthread_t tid_ = 0;
    EntryFn fn_ = nullptr;
    void* arg_ = nullptr;
    char name_[16] = {0};
    bool started_ = false;
    bool done_ = true;

    // 完成通知（bionic 无 timedjoin 的替代）
    pthread_mutex_t done_mu_;
    pthread_cond_t done_cv_;
};

}  // namespace tk
