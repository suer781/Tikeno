// ============================================================================
// core/scheduler.cpp — 调度主循环实现
// ============================================================================

#include "core/scheduler.h"

#include <time.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>

#include "platform/alloc_guard.h"
#include "core/constants.h"
#include "core/power_profile.h"
#include "core/time_util.h"
#include "platform/logging.h"

namespace tk {

namespace {

// 自旋暂停指令（降低自旋功耗；ARM yield / x86 pause）
inline void cpu_relax() {
#if defined(__aarch64__)
    __builtin_arm_yield();
#elif defined(__x86_64__) || defined(__i386__)
    __builtin_ia32_pause();
#else
    // 其他架构：编译器屏障近似
    __asm__ __volatile__("" ::: "memory");
#endif
}

// 漏拍判定阈值：滞后超过该值记一次漏拍（PRD P95 ≤ ±2ms 量级下取 1ms）
inline constexpr int64_t kLateThresholdNs = 1000 * 1000;

}  // namespace

Scheduler::Scheduler(HighResTimer& timer, EpollLoop& epoll, CommandQueue& cmdq)
    : timer_(timer), epoll_(epoll), cmdq_(cmdq), cmd_fd_(-1) {
    for (int i = 0; i < kMaxSlots; ++i) pressed_[i] = false;
}

// ---------------------------------------------------------------------------
// 主循环
// ---------------------------------------------------------------------------
int Scheduler::run(IInjector* injector,
                   SequencePlayer* player,
                   Stats* stats,
                   std::atomic<int>* state,
                   int cmd_fd,
                   void* stats_buf,
                   const Params& params) {
    if (injector == nullptr || player == nullptr || stats == nullptr || state == nullptr) {
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    if (!player->loaded()) {
        return static_cast<int>(TkError::kErrEmptySeq);
    }

    TK_NO_ALLOC_SCOPE();  // Debug 分配哨兵：作用域内任何 malloc 即 TK_LOGE

    cmd_fd_ = cmd_fd;
    spin_threshold_ns_ = params.spin_threshold_ns;
    last_delay_ns_ = kDefaultSampleStepUs * 1000;  // 初始按默认步长门控自旋

    // epoll 注册：timerFd（若 timerfd 后端）+ cmdEfd
    epoll_.create();
    if (cmd_fd >= 0) {
        epoll_.add_fd(cmd_fd, EPOLLIN);
    }
    const bool use_timerfd = (params.backend == TkTimerBackend::kTimerFd) && timer_.is_timer_fd();
    if (use_timerfd) {
        epoll_.add_fd(timer_.fd(), EPOLLIN);
    }

    // 复制可变参数（SET_PARAM 在循环内更新此副本）
    Params p = params;

    // 重置按下槽位表
    for (int i = 0; i < kMaxSlots; ++i) pressed_[i] = false;

    // 首步立即执行（delay 语义为"本步之后等待"）
    player->reset(now_ns());
    int64_t next_deadline = now_ns();

    int last_error = static_cast<int>(TkError::kOk);
    int64_t last_stats_write = now_ns();

    // 初始快照
    stats->write_snapshot(stats_buf, state->load(std::memory_order_relaxed),
                          static_cast<int64_t>(p.tier));

    for (;;) {
        // 1) 非阻塞检查命令
        if (process_commands(state, &p)) {
            last_error = static_cast<int>(TkError::kOk);
            break;
        }

        // 2) 暂停态：阻塞等命令（不空转）
        if (state->load(std::memory_order_relaxed) ==
            static_cast<int>(TkEngineState::kPaused)) {
            wait_paused(state);
            if (state->load(std::memory_order_relaxed) ==
                static_cast<int>(TkEngineState::kStopping)) {
                last_error = static_cast<int>(TkError::kOk);
                break;
            }
            // 恢复：重新锚定 deadline 到现在（跳过暂停期间的时间债）
            next_deadline = now_ns();
        }

        // 3) 混合等待至 deadline
        {
            const WaitResult wr = wait_until_deadline(next_deadline, p, use_timerfd);
            if (wr == kWaitStop) {
                last_error = static_cast<int>(TkError::kOk);
                break;
            }
            if (wr == kWaitError) {
                last_error = static_cast<int>(TkError::kErrTimerCreate);
                break;
            }
        }

        // 4) 序列是否结束（含循环策略/时长终止）
        if (!player->has_next(now_ns())) {
            last_error = static_cast<int>(TkError::kOk);  // 正常完成
            break;
        }

        // 5) 漏拍判定：触发时刻晚于 deadline 超过阈值 → 记漏拍并重新锚定
        //    （步驱动模型：不丢步防手势断裂，仅重新锚定 + 计数）
        {
            const int64_t now = now_ns();
            if (now - next_deadline > kLateThresholdNs) {
                stats->add_missed();
                next_deadline = now;
            }
        }

        // 6) 取步并发射（热路径核心：无分配、无锁、每 tick 一次 flush）
        {
            const TkStep& step = player->current();
            int rc = static_cast<int>(TkError::kOk);
            switch (static_cast<TkStepKind>(step.kind)) {
                case TkStepKind::kDown:
                    rc = injector->emit_down(step.slot, step.x, step.y);
                    if (step.slot >= 0 && step.slot < kMaxSlots) pressed_[step.slot] = true;
                    break;
                case TkStepKind::kMove:
                    rc = injector->emit_move(step.slot, step.x, step.y);
                    break;
                case TkStepKind::kUp:
                    rc = injector->emit_up(step.slot);
                    if (step.slot >= 0 && step.slot < kMaxSlots) pressed_[step.slot] = false;
                    break;
                case TkStepKind::kSync:
                    rc = injector->emit_sync();
                    break;
                case TkStepKind::kWait:
                    rc = static_cast<int>(TkError::kOk);  // 纯等待：时间由 deadline 推进
                    break;
                case TkStepKind::kGlobal:
                    rc = injector->emit_global(step.arg);
                    break;
                case TkStepKind::kNodeResolveReq:
                    // 节点解析回填（T03 Java 侧经 nativeNotifyResolution）
                    rc = static_cast<int>(TkError::kOk);
                    break;
                default:
                    break;
            }
            injector->flush();  // 桥接档：一次 eventfd write；native 档 no-op

            // 7) 统计：误差样本 + 执行计数；周期性写共享快照
            const int64_t now_after = now_ns();
            stats->record(now_after - next_deadline);
            stats->inc_exec_count();

            // 8) 绝对时间推进（防漂移核心：deadline 累加，绝不用 now+delay）
            last_delay_ns_ = step.delayNs;
            next_deadline += step.delayNs;
            if (step.delayNs <= 0) {
                // 零延迟步（如 UP→SYNC）：不推进，防止落后被误判漏拍
                if (next_deadline < now_after) next_deadline = now_after;
            }

            if (now_after - last_stats_write >= p.stats_period_ns) {
                stats->write_snapshot(stats_buf, state->load(std::memory_order_relaxed),
                                      static_cast<int64_t>(p.tier));
                last_stats_write = now_after;
            }

            if (rc != static_cast<int>(TkError::kOk) &&
                rc != static_cast<int>(TkError::kWarnRingFullDropped)) {
                // 写入失败（设备被移除等）：终止本轮
                TK_LOGE("Scheduler: emit 失败 rc=%d，终止序列", rc);
                last_error = rc;
                break;
            }

            player->advance();
        }
    }

    // 停止语义（§6.3）：native 档补发 UP+SYNC，防下游残留 DOWN 状态
    flush_pressed_slots(injector);
    timer_.cancel();
    if (cmd_fd_ >= 0) {
        epoll_.remove_fd(cmd_fd_);
    }
    if (use_timerfd) {
        epoll_.remove_fd(timer_.fd());
    }

    // 最终统计快照（Java 侧经 statsBuf.state 直读确认完成）
    stats->write_snapshot(stats_buf, static_cast<int64_t>(TkEngineState::kIdle),
                          static_cast<int64_t>(p.tier));
    return last_error;
}

// ---------------------------------------------------------------------------
// 混合等待
// ---------------------------------------------------------------------------
Scheduler::WaitResult Scheduler::wait_until_deadline(int64_t deadline_ns,
                                                     const Params& params,
                                                     bool use_timerfd) {
    // 自旋门控（调研 §2.2.3 要点 5）：
    //   - 省电档永不自旋（threshold=0）
    //   - 仅当步间延迟 < 50ms 时允许自旋，长间隔一律睡眠
    const bool allow_spin =
        (spin_threshold_ns_ > 0) &&
        (last_delay_ns_ > 0) &&
        (last_delay_ns_ <= kSpinMaxPeriodNs);

    for (;;) {
        const int64_t now = now_ns();
        const int64_t remaining = deadline_ns - now;
        if (remaining <= 0) return kWaitOk;  // 已到点

        // 尾段补偿：剩余 ≤ 阈值时自旋磨掉内核唤醒抖动
        if (allow_spin && remaining <= spin_threshold_ns_) {
            spin_until(deadline_ns);
            return kWaitOk;
        }

        if (use_timerfd) {
            // 主后端：arm 绝对 deadline，epoll 等待（命令到达可立即唤醒）
            if (timer_.arm_absolute(deadline_ns) != static_cast<int>(TkError::kOk)) {
                return kWaitError;
            }
            struct epoll_event ev[EpollLoop::kMaxEvents];
            const int n = epoll_.wait_once(ev, EpollLoop::kMaxEvents, -1);
            if (n < 0) return kWaitError;
            for (int i = 0; i < n; ++i) {
                if (ev[i].data.fd == timer_.fd()) {
                    timer_.drain_expired();  // 到期：读出计数值解除可读态
                } else if (ev[i].data.fd == cmd_fd_) {
                    drain_cmd_signal();
                }
            }
            // 回顶：若剩余已小则走自旋，否则继续 epoll
        } else {
            // 回退后端：clock_nanosleep 分片睡眠 + 片间排空命令信号
            // （保证 STOP 响应 ≤ kNanosleepSliceNs = 20ms）
            const int64_t now2 = now_ns();
            int64_t slice_end = deadline_ns;
            if (slice_end - now2 > kNanosleepSliceNs) {
                slice_end = now2 + kNanosleepSliceNs;
            }
            struct timespec ts = ns_to_timespec(slice_end);
            clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &ts, nullptr);
            drain_cmd_signal();
        }
    }
}

void Scheduler::drain_cmd_signal() {
    // 排空 cmdEfd 信号字节（命令本体经 CommandQueue 由 process_commands 处理）
    if (cmd_fd_ < 0) return;
    uint64_t one = 0;
    while (read(cmd_fd_, &one, sizeof(one)) == sizeof(one)) {}
}

void Scheduler::spin_until(int64_t deadline_ns) {
    for (;;) {
        const int64_t now = now_ns();
        if (now >= deadline_ns) return;
        cpu_relax();
    }
}

// ---------------------------------------------------------------------------
// 命令处理
// ---------------------------------------------------------------------------
bool Scheduler::process_commands(std::atomic<int>* state, Params* params) {
    bool stop = false;
    TkCommand cmd;
    while (cmdq_.try_pop(&cmd)) {
        switch (static_cast<TkCommandId>(cmd.cmd)) {
            case TkCommandId::kStop:
                state->store(static_cast<int>(TkEngineState::kStopping),
                             std::memory_order_release);
                stop = true;
                break;
            case TkCommandId::kPause:
                state->store(static_cast<int>(TkEngineState::kPaused),
                             std::memory_order_release);
                break;
            case TkCommandId::kResume:
                if (state->load(std::memory_order_relaxed) ==
                    static_cast<int>(TkEngineState::kPaused)) {
                    state->store(static_cast<int>(TkEngineState::kRunning),
                                 std::memory_order_release);
                }
                break;
            case TkCommandId::kSetParam:
                if (params != nullptr) {
                    apply_set_param(static_cast<int32_t>(cmd.arg0), cmd.arg1, params);
                }
                break;
            case TkCommandId::kNodeResolved:
                // 节点解析结果回填（T03：Java 解析后把坐标写入后续步）
                break;
            case TkCommandId::kReloadSeq:
                // 重载序列：由 Engine 在停止态执行；运行期收到则忽略
                break;
            default:
                break;
        }
    }
    return stop;
}

void Scheduler::apply_set_param(int32_t param_id, int64_t value, Params* params) {
    switch (static_cast<TkParamId>(param_id)) {
        case TkParamId::kSpinThresholdNs:
            spin_threshold_ns_ = value;
            params->spin_threshold_ns = value;
            break;
        case TkParamId::kIntervalNs:
            pending_interval_ns_ = value;  // 下轮 loadSequence 生效
            break;
        case TkParamId::kPowerProfile:
            // 功耗档联动：取预设自旋阈值与统计周期（即刻生效）
            {
                const TkPowerParams pp = power_params_for(static_cast<int>(value));
                spin_threshold_ns_ = pp.spin_threshold_ns;
                params->spin_threshold_ns = pp.spin_threshold_ns;
                params->stats_period_ns = pp.stats_period_ns;
            }
            break;
        default:
            break;
    }
}

void Scheduler::wait_paused(std::atomic<int>* state) {
    // 暂停态阻塞：epoll 无限等待（仅 cmdEfd 有效事件；timerFd 已取消武装）
    struct epoll_event ev[EpollLoop::kMaxEvents];
    for (;;) {
        const int n = epoll_.wait_once(ev, EpollLoop::kMaxEvents, -1);
        if (n <= 0) continue;
        for (int i = 0; i < n; ++i) {
            if (ev[i].data.fd == cmd_fd_) {
                drain_cmd_signal();
            } else if (ev[i].data.fd == timer_.fd()) {
                timer_.drain_expired();
            }
        }
        process_commands(state, nullptr);
        if (state->load(std::memory_order_relaxed) !=
            static_cast<int>(TkEngineState::kPaused)) {
            return;  // RESUME 或 STOP
        }
    }
}

// ---------------------------------------------------------------------------
// 停止收尾
// ---------------------------------------------------------------------------
void Scheduler::flush_pressed_slots(IInjector* injector) {
    if (injector == nullptr) return;
    // 桥接档（L2/L3）：手势由 Java 侧以完整 GestureDescription 提交，
    // 在途手势自然完成，无需补发（§6.3 opt 段由 Java 处理）
    const bool native_tier =
        injector->tier() == TkInjectionTier::kL0Uinput ||
        injector->tier() == TkInjectionTier::kL1Evdev;
    if (!native_tier) return;

    bool any = false;
    for (int slot = 0; slot < kMaxSlots; ++slot) {
        if (pressed_[slot]) {
            injector->emit_up(slot);
            pressed_[slot] = false;
            any = true;
        }
    }
    if (any) {
        injector->emit_sync();
        injector->flush();
    }
}

}  // namespace tk
