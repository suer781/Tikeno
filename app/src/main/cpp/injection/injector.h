#pragma once
// ============================================================================
// injection/injector.h — IInjector 注入抽象接口（架构 §2.3 #35 / §4.2）
// ----------------------------------------------------------------------------
// 四档注入器的统一抽象（L0 uinput / L1 evdev / L2-L3 桥接）。Scheduler 只
// 依赖本接口，不感知档位（架构分层不变式 4）；档位创建与降级在
// injector_factory 内完成。
// 【线程归属】Scheduler 调度线程独占调用（open/close 在控制面，emit* 在热路径，
// 全部无锁）。emit* 返回 TK_OK / TK_ERR_WRITE；不抛异常、不分配。
// ============================================================================

#include "core/types.h"

namespace tk {

class IInjector {
 public:
    virtual ~IInjector() = default;

    // 打开设备/建立桥接（控制面，start 前调用一次）。
    // 返回 TK_OK / TK_ERR_DEVICE_OPEN。
    virtual int open() = 0;

    // 关闭并释放 fd（幂等）。
    virtual void close() = 0;

    // 原子事件发射（热路径；slot 0..9）
    virtual int emit_down(int slot, int x, int y) = 0;
    virtual int emit_move(int slot, int x, int y) = 0;
    virtual int emit_up(int slot) = 0;

    // SYN_REPORT 同步帧：native 档写 EV_SYN；桥接档推送 kSync 步
    virtual int emit_sync() = 0;

    // 纯等待（native 档通常无需实现——等待由 Scheduler 管理；桥接档推 kWait 步）
    virtual int emit_wait(int64_t ns) = 0;

    // 全局动作（arg = TkGlobalAction）。native 档不支持：返回 0 并忽略（已记日志）。
    virtual int emit_global(int action) = 0;

    // 批次收尾：Scheduler 每 tick 调用一次。
    // 桥接档：一次 write(outEfd) 通知 Java（而非每步一次，省系统调用）。
    // native 档：no-op。
    virtual int flush() = 0;

    // 实际生效档位（可能与请求档位不同——工厂降级后）
    virtual TkInjectionTier tier() const = 0;
};

}  // namespace tk
