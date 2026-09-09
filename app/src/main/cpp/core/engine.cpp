// ============================================================================
// core/engine.cpp — TikenoEngine 引擎状态机实现
// ============================================================================

#include "core/engine.h"

#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/eventfd.h>
#include <unistd.h>

#include "core/constants.h"
#include "core/power_profile.h"
#include "core/time_util.h"
#include "injection/injector_factory.h"
#include "platform/affinity.h"
#include "platform/logging.h"

namespace tk {

TikenoEngine::TikenoEngine()
    : scheduler_(timer_, epoll_, cmdq_) {
    pthread_mutex_init(&mu_, nullptr);
    magic_.store(kHandleMagic, std::memory_order_relaxed);
    state_.store(static_cast<int>(TkEngineState::kIdle), std::memory_order_relaxed);
    actual_tier_.store(static_cast<int>(TkInjectionTier::kL3Accessibility),
                       std::memory_order_relaxed);
}

TikenoEngine::~TikenoEngine() {
    pthread_mutex_lock(&mu_);
    stop_locked();
    close_injector();
    magic_.store(0, std::memory_order_relaxed);  // 先失效句柄再销毁
    pthread_mutex_unlock(&mu_);
    pthread_mutex_destroy(&mu_);
}

// ---------------------------------------------------------------------------
// 配置与缓冲
// ---------------------------------------------------------------------------
int TikenoEngine::create(const TkEngineConfigFlat& cfg) {
    pthread_mutex_lock(&mu_);
    if (cfg.schemaVersion != kConfigSchemaVersion) {
        pthread_mutex_unlock(&mu_);
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    power_profile_ = cfg.powerProfile;
    default_interval_ns_ = cfg.defaultIntervalNs;
    if (default_interval_ns_ < kMinIntervalNs) default_interval_ns_ = kMinIntervalNs;
    if (default_interval_ns_ > kMaxIntervalNs) default_interval_ns_ = kMaxIntervalNs;
    spin_threshold_ns_ = (cfg.spinThresholdNs >= 0)
        ? cfg.spinThresholdNs : kDefaultSpinThresholdNs;
    requested_tier_ = cfg.tier;
    backend_ = cfg.timerBackend;
    stats_period_ns_ = (cfg.statsPeriodNs > 0) ? cfg.statsPeriodNs : kDefaultStatsPeriodNs;
    screen_w_ = cfg.screenW;
    screen_h_ = cfg.screenH;
    jitter_pct_ = cfg.jitterPct;

    // 功耗档预设校准（显式配置优先，未配置则取档位表）
    if (spin_threshold_ns_ == 0 && cfg.powerProfile >= 0) {
        const TkPowerParams pp = power_params_for(power_profile_);
        spin_threshold_ns_ = pp.spin_threshold_ns;
        backend_ = static_cast<int32_t>(pp.backend);
        stats_period_ns_ = pp.stats_period_ns;
    }
    pthread_mutex_unlock(&mu_);
    return static_cast<int>(TkError::kOk);
}

int TikenoEngine::attach_sequence_buffer(void* base, size_t size) {
    if (base == nullptr || size < 8) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    pthread_mutex_lock(&mu_);
    seq_buf_ = base;
    seq_buf_size_ = size;
    pthread_mutex_unlock(&mu_);
    return static_cast<int>(TkError::kOk);
}

int TikenoEngine::attach_output_ring(void* base, size_t size) {
    const int rc = out_ring_.attach(base, size);
    return rc;
}

int TikenoEngine::attach_stats_buffer(void* base, size_t size) {
    if (base == nullptr || size < sizeof(TkStatsSnapshot)) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    pthread_mutex_lock(&mu_);
    stats_buf_ = base;
    pthread_mutex_unlock(&mu_);
    return static_cast<int>(TkError::kOk);
}

int TikenoEngine::attach_fds(int cmd_fd, int out_fd) {
    pthread_mutex_lock(&mu_);
    cmd_fd_ = cmd_fd;
    out_fd_ = out_fd;
    // Java 公开 API（android.system.Os）未暴露 eventfd，Java 传入的是
    // Os.pipe() 的单端 fd。两点适配（T03 JNI 桥补全）：
    //   1) 传入 fd 一律设 O_NONBLOCK——scheduler 的排空循环
    //      `while(read(fd,&u64,8)==8){}` 依赖非阻塞语义退出；
    //   2) 引擎内部自唤醒改用自建 eventfd（可读可写、非阻塞），
    //      因为 pipe 单端无法同时满足"引擎写 + 调度线程读"。
    if (cmd_fd >= 0) {
        const int fl = fcntl(cmd_fd, F_GETFL, 0);
        if (fl >= 0) (void)fcntl(cmd_fd, F_SETFL, fl | O_NONBLOCK);
    }
    if (out_fd >= 0) {
        const int fl = fcntl(out_fd, F_GETFL, 0);
        if (fl >= 0) (void)fcntl(out_fd, F_SETFL, fl | O_NONBLOCK);
        out_ring_.set_out_fd(out_fd);
    }
    if (wake_fd_ >= 0) {
        close(wake_fd_);
        wake_fd_ = -1;
    }
    wake_fd_ = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    if (wake_fd_ < 0) {
        TK_LOGE("attach_fds: wake eventfd 创建失败 errno=%d", errno);
        wake_fd_ = -1;
    }
    pthread_mutex_unlock(&mu_);
    return static_cast<int>(TkError::kOk);
}

// ---------------------------------------------------------------------------
// 序列装载
// ---------------------------------------------------------------------------
int TikenoEngine::load_sequence(int action_count) {
    pthread_mutex_lock(&mu_);
    if (state_.load(std::memory_order_relaxed) ==
        static_cast<int>(TkEngineState::kRunning)) {
        pthread_mutex_unlock(&mu_);
        return static_cast<int>(TkError::kErrBadState);
    }
    if (seq_buf_ == nullptr) {
        pthread_mutex_unlock(&mu_);
        return static_cast<int>(TkError::kErrBufferNotAttached);
    }

    // seqBuf 布局：[0]=actionCount(int32) [4]=schemaVersion(int32)，其后顺序排列
    // TkActionFlat(64B) + 跟随的 TkPointFlat[](16B×pointCount)（架构 §4.3）
    const auto* header = static_cast<const uint8_t*>(seq_buf_);
    int32_t buf_action_count = 0;
    int32_t schema_version = 0;
    memcpy(&buf_action_count, header, sizeof(int32_t));
    memcpy(&schema_version, header + 4, sizeof(int32_t));
    if (schema_version != kConfigSchemaVersion) {
        pthread_mutex_unlock(&mu_);
        TK_LOGE("load_sequence: schema=%d 不支持", schema_version);
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    // Java 传入数量与缓冲头数量二选一为 0 时取另一个（防御双 0）
    int32_t count = (action_count > 0) ? action_count : buf_action_count;
    if (count <= 0 || count > kMaxActions) {
        pthread_mutex_unlock(&mu_);
        return static_cast<int>(TkError::kErrEmptySeq);
    }

    // 解析并展开（控制面：唯一允许分配重置的阶段）
    step_pool_.reset();
    FastRng rng(0x54494B454E4FULL);  // "TIKEN" 种子（抖动用，确定性）
    size_t cursor = 8;
    int warn = static_cast<int>(TkError::kOk);
    for (int32_t i = 0; i < count; ++i) {
        // 动作头
        if (cursor + sizeof(TkActionFlat) > seq_buf_size_) {
            pthread_mutex_unlock(&mu_);
            return static_cast<int>(TkError::kErrEmptySeq);
        }
        TkActionFlat action;
        memcpy(&action, static_cast<const uint8_t*>(seq_buf_) + cursor, sizeof(TkActionFlat));
        cursor += sizeof(TkActionFlat);

        // 跟随点数组
        if (action.pointCount < 0 || action.pointCount > kMaxPointsPerAction ||
            cursor + (size_t)action.pointCount * sizeof(TkPointFlat) > seq_buf_size_) {
            pthread_mutex_unlock(&mu_);
            return static_cast<int>(TkError::kErrEmptySeq);
        }
        const TkPointFlat* points = (action.pointCount > 0)
            ? reinterpret_cast<const TkPointFlat*>(
                  static_cast<const uint8_t*>(seq_buf_) + cursor)
            : nullptr;
        cursor += (size_t)action.pointCount * sizeof(TkPointFlat);

        bool clamped = false;
        const int rc = ActionBuilder::build(action, points, &step_pool_, &rng, &clamped);
        if (rc != static_cast<int>(TkError::kOk) &&
            rc != static_cast<int>(TkError::kErrRingFull)) {
            pthread_mutex_unlock(&mu_);
            return rc;
        }
        if (rc == static_cast<int>(TkError::kErrRingFull) ||
            clamped) {
            warn = (rc == static_cast<int>(TkError::kErrRingFull))
                ? rc : static_cast<int>(TkError::kWarnRateClamped);
        }
    }

    if (step_pool_.count() <= 0) {
        pthread_mutex_unlock(&mu_);
        return static_cast<int>(TkError::kErrEmptySeq);
    }

    // 循环策略：骨架轮次默认无限循环（kind=0）；策略扁平化字段随 T03
    // SequenceFlattener 扩展 seqBuf 头部传入（偏差已记录）
    TkLoopPolicy policy;
    policy.kind = 0;
    policy.max_count = 0;
    policy.max_duration_ns = 0;
    player_.load(step_pool_.data(), step_pool_.count(), policy);

    state_.store(static_cast<int>(TkEngineState::kPrepared), std::memory_order_relaxed);
    TK_LOGI("load_sequence: %d 动作 → %d 原子步", (int)count, step_pool_.count());
    pthread_mutex_unlock(&mu_);
    return warn;
}

// ---------------------------------------------------------------------------
// 生命周期
// ---------------------------------------------------------------------------
int TikenoEngine::start() {
    pthread_mutex_lock(&mu_);
    const int st = state_.load(std::memory_order_relaxed);
    if (st != static_cast<int>(TkEngineState::kPrepared)) {
        pthread_mutex_unlock(&mu_);
        return static_cast<int>(TkError::kErrBadState);
    }
    if (worker_running_.load(std::memory_order_relaxed)) {
        pthread_mutex_unlock(&mu_);
        return static_cast<int>(TkError::kErrBadState);
    }

    // 创建注入器（若未创建或档位变更）；失败自动降级（工厂内完成）
    if (injector_ == nullptr) {
        InjectorFactory::Result r = InjectorFactory::create(
            static_cast<TkInjectionTier>(requested_tier_),
            &out_ring_, out_fd_, screen_w_, screen_h_);
        if (r.injector == nullptr) {
            pthread_mutex_unlock(&mu_);
            return r.error != 0 ? r.error : static_cast<int>(TkError::kErrDeviceOpen);
        }
        injector_ = r.injector;
        actual_tier_.store(static_cast<int>(r.actual_tier), std::memory_order_relaxed);
        if (r.downgraded) {
            TK_LOGW("Engine: 注入器降级至档位 %d", (int)r.actual_tier);
        }
    }

    // 定时器后端创建
    if (!timer_.valid()) {
        const int rc = timer_.create(static_cast<TkTimerBackend>(backend_));
        if (rc != static_cast<int>(TkError::kOk)) {
            pthread_mutex_unlock(&mu_);
            return rc;
        }
    }

    state_.store(static_cast<int>(TkEngineState::kRunning), std::memory_order_relaxed);
    worker_running_.store(true, std::memory_order_relaxed);
    const int rc = worker_.start(
        [](void* self) -> void* {
            static_cast<TikenoEngine*>(self)->run_loop();
            return nullptr;
        },
        this, "tikeno.sched");
    if (rc != static_cast<int>(TkError::kOk)) {
        worker_running_.store(false, std::memory_order_relaxed);
        state_.store(static_cast<int>(TkEngineState::kError), std::memory_order_relaxed);
        pthread_mutex_unlock(&mu_);
        return rc;
    }
    pthread_mutex_unlock(&mu_);
    return static_cast<int>(TkError::kOk);
}

int TikenoEngine::pause() {
    return post_command(static_cast<int32_t>(TkCommandId::kPause), 0, 0);
}

int TikenoEngine::resume() {
    return post_command(static_cast<int32_t>(TkCommandId::kResume), 0, 0);
}

int TikenoEngine::stop() {
    pthread_mutex_lock(&mu_);
    const int rc = stop_locked();
    pthread_mutex_unlock(&mu_);
    return rc;
}

int TikenoEngine::stop_locked() {
    const int st = state_.load(std::memory_order_relaxed);
    if (!worker_running_.load(std::memory_order_relaxed)) {
        // 未在运行：直接归位
        if (st == static_cast<int>(TkEngineState::kRunning) ||
            st == static_cast<int>(TkEngineState::kPaused) ||
            st == static_cast<int>(TkEngineState::kStopping)) {
            state_.store(static_cast<int>(TkEngineState::kIdle), std::memory_order_relaxed);
        }
        return static_cast<int>(TkError::kOk);
    }
    // 投递 STOP 并唤醒调度线程（epoll 立即返回，§6.3 目标 ≤5ms）
    TkCommand cmd;
    memset(&cmd, 0, sizeof(cmd));
    cmd.cmd = static_cast<int32_t>(TkCommandId::kStop);
    cmdq_.try_push(cmd);
    if (wake_fd_ >= 0) {
        uint64_t one = 1;
        ssize_t n = write(wake_fd_, &one, sizeof(one));
        (void)n;
    }
    // 带超时回收线程（架构 §7.3 规则 4：超时记日志继续，不卡死控制面）
    worker_.join_timed(200);
    worker_running_.store(false, std::memory_order_relaxed);
    state_.store(static_cast<int>(TkEngineState::kIdle), std::memory_order_relaxed);
    return static_cast<int>(TkError::kOk);
}

// ---------------------------------------------------------------------------
// 命令与参数
// ---------------------------------------------------------------------------
int TikenoEngine::post_command(int32_t cmd, int64_t arg0, int64_t arg1) {
    TkCommand c;
    memset(&c, 0, sizeof(c));
    c.cmd = cmd;
    c.arg0 = arg0;
    c.arg1 = arg1;
    if (!cmdq_.try_push(c)) {
        return static_cast<int>(TkError::kWarnCmdDropped);  // 队列满已覆盖（§10.5）
    }
    if (wake_fd_ >= 0) {
        uint64_t one = 1;
        ssize_t n = write(wake_fd_, &one, sizeof(one));
        (void)n;
    }
    return static_cast<int>(TkError::kOk);
}

int TikenoEngine::set_injector_tier(int tier, const char* device_path) {
    pthread_mutex_lock(&mu_);
    if (worker_running_.load(std::memory_order_relaxed)) {
        pthread_mutex_unlock(&mu_);
        return static_cast<int>(TkError::kErrBadState);  // 运行中不可换档
    }
    requested_tier_ = tier;
    if (device_path != nullptr && device_path[0] != '\0') {
        strncpy(device_path_, device_path, sizeof(device_path_) - 1);
        device_path_[sizeof(device_path_) - 1] = '\0';
    }
    close_injector();  // 下次 start 重新创建
    pthread_mutex_unlock(&mu_);
    return static_cast<int>(TkError::kOk);
}

int TikenoEngine::set_power_profile(int profile) {
    return post_command(static_cast<int32_t>(TkCommandId::kSetParam),
                        static_cast<int32_t>(TkParamId::kPowerProfile), profile);
}

int TikenoEngine::set_spin_threshold_ns(int64_t ns) {
    return post_command(static_cast<int32_t>(TkCommandId::kSetParam),
                        static_cast<int32_t>(TkParamId::kSpinThresholdNs), ns);
}

int TikenoEngine::set_interval_ns(int64_t ns) {
    if (ns < kMinIntervalNs || ns > kMaxIntervalNs) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    return post_command(static_cast<int32_t>(TkCommandId::kSetParam),
                        static_cast<int32_t>(TkParamId::kIntervalNs), ns);
}

// ---------------------------------------------------------------------------
// 调度线程体
// ---------------------------------------------------------------------------
void TikenoEngine::run_loop() {
    // 线程调优（架构 §1.2 难点 2）：绑大核；native 档尝试 SCHED_FIFO(60)，
    // 失败静默回退（非 Root / 无 CAP_SYS_NICE 属预期）
    pthread_t self = pthread_self();
    pin_to_big_core(self);
    const int cur_tier = actual_tier_.load(std::memory_order_relaxed);
    if (cur_tier == static_cast<int>(TkInjectionTier::kL0Uinput) ||
        cur_tier == static_cast<int>(TkInjectionTier::kL1Evdev)) {
        try_sched_fifo(self, kSchedFifoPriority);
    }

    // 组装调度参数（运行期只读，SET_PARAM 由 Scheduler 内部更新副本）
    Scheduler::Params params;
    params.spin_threshold_ns = spin_threshold_ns_;
    params.stats_period_ns = stats_period_ns_;
    params.backend = static_cast<TkTimerBackend>(backend_);
    params.tier = static_cast<TkInjectionTier>(cur_tier);
    params.max_duration_ns = 0;

    // 传给调度器的唤醒 fd：自唤醒 eventfd（引擎内部命令）；
    // Java 唤醒（cmd_fd_，pipe 读端）作为附加监听传入
    const int rc = scheduler_.run(injector_, &player_, &stats_, &state_,
                                  wake_fd_, cmd_fd_, stats_buf_, params);
    if (rc != static_cast<int>(TkError::kOk)) {
        TK_LOGE("run_loop: 调度循环异常退出 rc=%d", rc);
        state_.store(static_cast<int>(TkEngineState::kError), std::memory_order_relaxed);
    }
}

void TikenoEngine::close_injector() {
    if (injector_ != nullptr) {
        injector_->close();
        delete injector_;
        injector_ = nullptr;
    }
}

}  // namespace tk
