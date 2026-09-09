#pragma once
// ============================================================================
// injection/evdev_injector.h — L1 Root 兼容：/dev/input/eventX 直写
// （架构 §2.3 #38 / 调研 §2.1.3）
// ----------------------------------------------------------------------------
// 直接 write() struct input_event 到触摸屏设备节点，绕过 system_server。
// 门槛：节点 660 权限 + SELinux（open 失败的原因已在日志中体现，
// 由 CapabilityProbe 预探测）。设备探测：优先选名字含 touch/mt 的节点，
// 否则取第一个可 O_WRONLY 打开的 eventX。
// 【线程归属】open/close 控制面；emit* 调度线程热路径。
// ============================================================================

#include <cstdio>

#include "core/constants.h"
#include "injection/injector.h"

namespace tk {

class EvdevInjector : public IInjector {
 public:
    EvdevInjector();
    ~EvdevInjector() override;

    int open() override;
    void close() override;

    int emit_down(int slot, int x, int y) override;
    int emit_move(int slot, int x, int y) override;
    int emit_up(int slot) override;
    int emit_sync() override;
    int emit_wait(int64_t ns) override;
    int emit_global(int action) override;
    int flush() override;

    TkInjectionTier tier() const override { return TkInjectionTier::kL1Evdev; }

    const char* device_path() const { return path_; }

 private:
    int probe_device();  // 枚举 /dev/input/event0..15，选可写节点

    int fd_ = -1;
    char path_[64] = {0};
    bool pressed_[kMaxSlots];
};

}  // namespace tk
