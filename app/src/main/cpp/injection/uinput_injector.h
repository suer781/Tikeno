#pragma once
// ============================================================================
// injection/uinput_injector.h — L0 Root 增强：/dev/uinput 虚拟触摸设备
// （架构 §2.3 #37 / 调研 §2.1.5）
// ----------------------------------------------------------------------------
// 创建虚拟触摸屏（Protocol B 多指）：必须 UI_SET_PROPBIT(INPUT_PROP_DIRECT)
// 且 ABS_X/ABS_Y 轴范围非 0（否则系统识别失败），ABS_MT_SLOT max=9（10 指）。
// 【线程归属】open/close 控制面；emit*/flush 调度线程热路径，纯 write() 系统调用。
// ============================================================================

#include <cstdio>

#include "core/constants.h"
#include "injection/injector.h"

namespace tk {

class UinputInjector : public IInjector {
 public:
    UinputInjector(int screen_w, int screen_h);
    ~UinputInjector() override;

    int open() override;
    void close() override;

    int emit_down(int slot, int x, int y) override;
    int emit_move(int slot, int x, int y) override;
    int emit_up(int slot) override;
    int emit_sync() override;
    int emit_wait(int64_t ns) override;
    int emit_global(int action) override;
    int flush() override;

    TkInjectionTier tier() const override { return TkInjectionTier::kL0Uinput; }

 private:
    int setup_device();  // UI_SET_* + uinput_user_dev + UI_DEV_CREATE

    int fd_ = -1;
    int screen_w_;
    int screen_h_;
    int32_t tracking_ids_[kMaxSlots];   // 每槽位当前 tracking_id（-1=抬起）
    uint16_t cur_slot_ = 0;             // Protocol B 当前槽位
    int32_t next_tracking_id_ = 1;
};

}  // namespace tk
