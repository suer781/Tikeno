// ============================================================================
// platform/thread.cpp — pthread 封装实现
// ============================================================================

#include "platform/thread.h"

#include <string.h>
#include <time.h>

#include "core/constants.h"
#include "core/time_util.h"
#include "core/types.h"
#include "platform/logging.h"

namespace tk {

TkThread::TkThread() {
    pthread_mutex_init(&done_mu_, nullptr);
    // 条件变量绑定 CLOCK_MONOTONIC（与 ms_to_rel_timespec 的时基一致；
    // bionic 默认为 CLOCK_REALTIME，NTP 跳变会破坏超时语义）
    pthread_condattr_t attr;
    pthread_condattr_init(&attr);
    pthread_condattr_setclock(&attr, CLOCK_MONOTONIC);
    pthread_cond_init(&done_cv_, &attr);
    pthread_condattr_destroy(&attr);
}

TkThread::~TkThread() {
    // 析构不 join（Engine 显式 join_timed）；仅防泄漏提示
    if (started_ && !done_) {
        TK_LOGW("TkThread(%s): 析构时线程仍在运行（Engine 应先 join_timed）", name_);
    }
    pthread_cond_destroy(&done_cv_);
    pthread_mutex_destroy(&done_mu_);
}

void* TkThread::trampoline(void* self) {
    TkThread* t = static_cast<TkThread*>(self);
    void* ret = t->fn_(t->arg_);
    // 通知完成（join_timed 依赖）
    pthread_mutex_lock(&t->done_mu_);
    t->done_ = true;
    pthread_cond_broadcast(&t->done_cv_);
    pthread_mutex_unlock(&t->done_mu_);
    return ret;
}

int TkThread::start(EntryFn fn, void* arg, const char* name) {
    if (fn == nullptr || started_) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    fn_ = fn;
    arg_ = arg;
    if (name != nullptr) {
        strncpy(name_, name, sizeof(name_) - 1);
        name_[sizeof(name_) - 1] = '\0';
    }
    done_ = false;
    const int rc = pthread_create(&tid_, nullptr, trampoline, this);
    if (rc != 0) {
        done_ = true;
        TK_LOGE("TkThread::start(%s) 失败 rc=%d", name_, rc);
        return static_cast<int>(TkError::kErrThreadCreate);
    }
    started_ = true;
    // 命名失败不影响运行（长度/权限限制均可能）
    if (name_[0] != '\0') {
        pthread_setname_np(tid_, name_);
    }
    return static_cast<int>(TkError::kOk);
}

bool TkThread::join_timed(int timeout_ms) {
    if (!started_ || done_) {
        if (started_) {
            pthread_join(tid_, nullptr);
        }
        return true;
    }
    // 带超时等待完成标志
    pthread_mutex_lock(&done_mu_);
    if (!done_) {
        struct timespec ts = ms_to_rel_timespec(timeout_ms);
        pthread_cond_timedwait(&done_cv_, &done_mu_, &ts);
    }
    const bool finished = done_;
    pthread_mutex_unlock(&done_mu_);

    if (finished) {
        pthread_join(tid_, nullptr);  // 线程已结束，立即返回
        return true;
    }
    TK_LOGE("TkThread::join_timed(%s) 超时 %dms，线程仍在运行（按架构 §7.3 放弃等待）",
            name_, timeout_ms);
    return false;
}

}  // namespace tk
