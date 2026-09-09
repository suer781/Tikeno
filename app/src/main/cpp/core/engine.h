#pragma once
// ============================================================================
// core/engine.h — TikenoEngine 引擎状态机（架构 §2.3 #34 / §4.2）
// ----------------------------------------------------------------------------
// 状态机：Idle → Prepared → Running ⇄ Paused → (Stopping) → Idle / Error
// 职责：配置装载、序列解析与展开（ActionBuilder → StepPool）、注入器创建
//（InjectorFactory）、调度线程生命周期（TkThread "tikeno.sched"）、
// 统计聚合（Stats → statsBuf）。
// 【线程归属】
//   - 控制面（create/attach/load/start/stop/...）：JNI 调用线程，互斥保护
//   - run_loop()：调度线程独占（Scheduler + Stats + IInjector emit*）
//   - 命令通道：Java → CommandQueue + cmdEfd → 调度线程
// 【资源所有权】共享缓冲与 eventfd 由 Java 持有，Engine 只缓存裸指针/int fd；
//   IInjector 由工厂创建、Engine 销毁。
// ============================================================================

#include <atomic>
#include <stdint.h>

#include "core/action_builder.h"
#include "core/command_queue.h"
#include "core/constants.h"
#include "core/epoll_loop.h"
#include "core/high_res_timer.h"
#include "core/scheduler.h"
#include "core/sequence_player.h"
#include "core/shared_ring.h"
#include "core/stats.h"
#include "core/types.h"
#include "platform/thread.h"

namespace tk {

class TikenoEngine {
 public:
    TikenoEngine();
    ~TikenoEngine();

    TikenoEngine(const TikenoEngine&) = delete;
    TikenoEngine& operator=(const TikenoEngine&) = delete;

    // —— 句柄校验（HandleTable 双保险，架构 §10.2.2）——
    uint32_t magic() const { return magic_.load(std::memory_order_relaxed); }

    // —— 控制面 ——
    // 装载配置（nativeCreate 传入的扁平配置）
    int create(const TkEngineConfigFlat& cfg);

    // 挂载共享缓冲（Java allocateDirect 分配，地址一次性缓存，运行期不变）
    int attach_sequence_buffer(void* base, size_t size);
    int attach_output_ring(void* base, size_t size);
    int attach_stats_buffer(void* base, size_t size);

    // 挂载 fd（cmdEfd：Java→C++ 命令唤醒；outEfd：C++→Java 事件通知）
    int attach_fds(int cmd_fd, int out_fd);

    // 解析 seqBuf 并展开为原子步（控制面；actionCount 为 Java 侧声明的数量）
    int load_sequence(int action_count);

    // 生命周期
    int start();
    int pause();
    int resume();
    int stop();

    // 命令投递（运行控制与参数热更新）
    int post_command(int32_t cmd, int64_t arg0, int64_t arg1);

    // 档位与参数（停止态生效）
    int set_injector_tier(int tier, const char* device_path);
    int set_power_profile(int profile);
    int set_spin_threshold_ns(int64_t ns);
    int set_interval_ns(int64_t ns);

    // —— 查询 ——
    int state() const { return state_.load(std::memory_order_relaxed); }
    int tier() const { return actual_tier_.load(std::memory_order_relaxed); }
    int64_t missed_ticks() const { return stats_.missed(); }
    const Stats* stats() const { return &stats_; }

    // 调度线程入口（TkThread trampoline 调用；勿在控制面线程直接调）
    void run_loop();

 private:
    // 控制面互斥（create/attach/load/start/stop/destroy 可从不同 JNI 线程到达）
    pthread_mutex_t mu_;

    std::atomic<uint32_t> magic_;   // kHandleMagic，destroy 时清零防野句柄
    std::atomic<int> state_;        // TkEngineState
    std::atomic<int> actual_tier_;  // 工厂降级后的实际档位

    // 配置（TkEngineConfigFlat 解析后的字段）
    int64_t default_interval_ns_ = 200 * 1000 * 1000;
    int64_t spin_threshold_ns_ = kDefaultSpinThresholdNs;
    int64_t stats_period_ns_ = kDefaultStatsPeriodNs;
    int32_t power_profile_ = static_cast<int32_t>(TkPowerProfile::kBalanced);
    int32_t backend_ = static_cast<int32_t>(TkTimerBackend::kTimerFd);
    int32_t requested_tier_ = static_cast<int32_t>(TkInjectionTier::kL3Accessibility);
    int32_t screen_w_ = 0;
    int32_t screen_h_ = 0;
    float jitter_pct_ = 0.0f;

    // 设备路径（L1 指定节点；本轮先保留字段，探测联调在 T03）
    char device_path_[128] = {0};

    // 共享缓冲（Java 拥有；此处仅裸指针缓存）
    void* seq_buf_ = nullptr;
    size_t seq_buf_size_ = 0;
    SharedRingWriter out_ring_;
    void* stats_buf_ = nullptr;

    // fd（Java 拥有；仅缓存 int）
    int cmd_fd_ = -1;
    int out_fd_ = -1;

    // 核心组件
    HighResTimer timer_;
    EpollLoop epoll_;
    CommandQueue cmdq_;
    Scheduler scheduler_;   // 引用 timer_/epoll_/cmdq_，须在其后声明
    StepPool step_pool_;
    SequencePlayer player_;
    Stats stats_;
    IInjector* injector_ = nullptr;

    // 调度线程
    TkThread worker_;
    std::atomic<bool> worker_running_{false};

    int stop_locked();       // 内部停止（已持锁）
    void close_injector();   // 释放注入器（幂等）
};

}  // namespace tk
