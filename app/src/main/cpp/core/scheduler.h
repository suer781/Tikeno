#pragma once
// ============================================================================
// core/scheduler.h — 调度主循环（架构 §2.3 #31 / §1.2 难点 2 / §7 线程模型）
// ----------------------------------------------------------------------------
// 【线程归属】独占运行于 "tikeno.sched" pthread（T2）。
// 核心机制（调研 §2.2.3 / §6.2 时序图 ②）：
//   1. 绝对时间推进：next_deadline += step.delayNs，绝不用 now + delay（防漂移）
//   2. 混合等待：剩余 > spinThreshold → epoll(timerFd|cmdEfd) 阻塞（可被命令
//      立即唤醒，紧急停止 ≪100ms）；剩余 ≤ spinThreshold（且周期 < 50ms）→
//      clock_gettime 自旋磨掉内核唤醒抖动
//   3. 漏拍统计：滞后 > 1ms 记 missed 并重新锚定 deadline（步驱动模型，
//      不丢步防手势断裂，与纯周期模型的跳步补拍语义见 catch_up_steps）
//   4. 命令即时响应：STOP/PAUSE/RESUME/SET_PARAM 经 CommandQueue + cmdEfd
//   5. 停止语义（§6.3）：当前 stroke 补发 UP+SYNC 后退出，防触摸卡死
// 【零开销契约】循环内零 malloc / 零 JNI 回调 / 零锁（Debug 由 AllocGuard 校验）
// ============================================================================

#include <atomic>
#include <stdint.h>

#include "core/command_queue.h"
#include "core/constants.h"
#include "core/epoll_loop.h"
#include "core/high_res_timer.h"
#include "core/sequence_player.h"
#include "core/stats.h"
#include "core/types.h"
#include "injection/injector.h"

namespace tk {

class Scheduler {
 public:
    struct Params {
        int64_t spin_threshold_ns = kDefaultSpinThresholdNs;
        int64_t stats_period_ns = kDefaultStatsPeriodNs;
        int64_t max_duration_ns = 0;   // 循环时长上限（0=不限时，来自循环策略）
        TkTimerBackend backend = TkTimerBackend::kTimerFd;
        TkInjectionTier tier = TkInjectionTier::kL3Accessibility;
    };

    // timer/epoll/cmdq 生命周期归 Engine，Scheduler 持引用
    Scheduler(HighResTimer& timer, EpollLoop& epoll, CommandQueue& cmdq);

    // 运行主循环（阻塞直至 序列完成 / STOP / 出错）。
    //   injector      ：实际生效的注入器
    //   player        ：已 load 的序列播放器
    //   stats         ：统计器（写快照到 stats_buf）
    //   state         ：引擎状态原子（共享内存快照里的 state 字段也读它）
    //   cmd_fd        ：自唤醒 eventfd（引擎内部命令；可读可写、非阻塞）
    //   java_wake_fd  ：Java→C++ 单向唤醒（pipe 读端，非阻塞；无则传 -1）
    //   stats_buf     ：共享统计缓冲（可为 nullptr）
    // 返回 TK_OK（正常完成/停止）或错误码。
    int run(IInjector* injector,
            SequencePlayer* player,
            Stats* stats,
            std::atomic<int>* state,
            int cmd_fd,
            int java_wake_fd,
            void* stats_buf,
            const Params& params);

 private:
    enum WaitResult { kWaitOk = 0, kWaitStop = 1, kWaitError = 2 };

    // 混合等待直到 deadline；期间处理命令（返回 kWaitStop 表示收到 STOP）。
    // use_timerfd 决定 epoll 等待还是 nanosleep 分片。
    WaitResult wait_until_deadline(int64_t deadline_ns, const Params& params,
                                   bool use_timerfd);

    // 排空 cmdEfd 信号字节（命令本体经 CommandQueue）
    void drain_cmd_signal();

    // 排空 Java 唤醒信号（pipe 读端；信号本体无语义，仅唤醒）
    void drain_java_wake();

    // 非阻塞排空 CommandQueue 并执行命令；返回是否收到 STOP
    bool process_commands(std::atomic<int>* state, Params* params);

    // SET_PARAM 命令分发
    void apply_set_param(int32_t param_id, int64_t value, Params* params);

    // 阻塞等待直到任一命令到达（暂停态）
    void wait_paused(std::atomic<int>* state);

    // 自旋到 deadline（尾段补偿）
    void spin_until(int64_t deadline_ns);

    // 停止收尾：补发所有按下槽位的 UP + SYNC（防触摸卡死，§6.3）
    void flush_pressed_slots(IInjector* injector);

    HighResTimer& timer_;
    EpollLoop& epoll_;
    CommandQueue& cmdq_;

    int cmd_fd_ = -1;        // 自唤醒 eventfd（引擎内部命令）
    int java_wake_fd_ = -1;  // Java→C++ 单向唤醒（pipe 读端；-1=无）

    // 运行期可变参数（SET_PARAM 更新副本）
    int64_t spin_threshold_ns_ = kDefaultSpinThresholdNs;
    int64_t pending_interval_ns_ = 0;  // kIntervalNs 参数（下一轮 loadSequence 生效）
    int64_t last_delay_ns_ = 0;        // 最近一步的 delayNs（自旋门控用）

    // 按下槽位表（native 档用于停止补发）
    bool pressed_[kMaxSlots];
};

}  // namespace tk
