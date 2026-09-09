// ============================================================================
// injection/uinput_injector.cpp — L0 uinput 虚拟触摸设备实现
// ============================================================================

#include "injection/uinput_injector.h"

#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <string.h>

#include "core/constants.h"
#include "injection/input_protocol.h"
#include "platform/logging.h"

namespace tk {

UinputInjector::UinputInjector(int screen_w, int screen_h)
    : screen_w_(screen_w > 0 ? screen_w : 1080),
      screen_h_(screen_h > 0 ? screen_h : 2400) {
    for (int i = 0; i < kMaxSlots; ++i) tracking_ids_[i] = -1;
}

UinputInjector::~UinputInjector() {
    close();
}

int UinputInjector::open() {
    if (fd_ >= 0) return static_cast<int>(TkError::kOk);  // 幂等
    fd_ = ::open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd_ < 0) {
        // 部分厂商路径为 /dev/input/uinput
        fd_ = ::open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    }
    if (fd_ < 0) {
        TK_LOGW("UinputInjector: 打开 /dev/uinput 失败 errno=%d", errno);
        return static_cast<int>(TkError::kErrDeviceOpen);
    }
    const int rc = setup_device();
    if (rc != static_cast<int>(TkError::kOk)) {
        ::close(fd_);
        fd_ = -1;
        return rc;
    }
    TK_LOGI("UinputInjector: 虚拟触摸设备已创建（%dx%d, 10 指）", screen_w_, screen_h_);
    return static_cast<int>(TkError::kOk);
}

int UinputInjector::setup_device() {
    // 事件位：SYN / KEY / ABS
    if (ioctl(fd_, kUiSetEvbit, EV_SYN) < 0 ||
        ioctl(fd_, kUiSetEvbit, EV_KEY) < 0 ||
        ioctl(fd_, kUiSetEvbit, EV_ABS) < 0) {
        TK_LOGW("UinputInjector: UI_SET_EVBIT 失败 errno=%d", errno);
        return static_cast<int>(TkError::kErrDeviceOpen);
    }
    // BTN_TOUCH（触摸屏语义）
    if (ioctl(fd_, kUiSetKeybit, kBtnTouch) < 0) {
        return static_cast<int>(TkError::kErrDeviceOpen);
    }
    // INPUT_PROP_DIRECT：声明为直接触摸屏（调研 §2.1.5 要点）
    if (ioctl(fd_, kUiSetPropbit, kInputPropDirect) < 0) {
        TK_LOGW("UinputInjector: UI_SET_PROPBIT(INPUT_PROP_DIRECT) 失败 errno=%d", errno);
        return static_cast<int>(TkError::kErrDeviceOpen);
    }
    // 绝对轴：SLOT(0..9) / X / Y（范围非 0！） / TRACKING_ID / TOUCH_MAJOR / PRESSURE
    struct { uint16_t code; int32_t min_v; int32_t max_v; } axes[] = {
        { kAbsMtSlot,       0, kMaxSlots - 1 },
        { kAbsMtPositionX,  0, screen_w_ - 1 },
        { kAbsMtPositionY,  0, screen_h_ - 1 },
        { kAbsMtTrackingId, 0, 65535 },
        { kAbsMtTouchMajor, 0, 255 },
        { kAbsMtPressure,   0, 255 },
    };
    for (const auto& a : axes) {
        if (ioctl(fd_, kUiSetAbsbit, a.code) < 0) {
            TK_LOGW("UinputInjector: UI_SET_ABSBIT(%u) 失败", a.code);
            return static_cast<int>(TkError::kErrDeviceOpen);
        }
    }
    // 设备描述（uinput_user_dev：轴范围在此设置）
    UinputUserDev dev;
    memset(&dev, 0, sizeof(dev));
    snprintf(dev.name, sizeof(dev.name), "Tikeno Virtual Touch");
    dev.id_bustype = BUS_VIRTUAL;
    dev.id_vendor  = 0x544B;  // "TK"
    dev.id_product = 0x0001;
    dev.id_version = 1;
    for (const auto& a : axes) {
        dev.absmax[a.code] = a.max_v;
        dev.absmin[a.code] = a.min_v;
    }
    if (write(fd_, &dev, sizeof(dev)) != sizeof(dev)) {
        TK_LOGW("UinputInjector: 写设备描述失败 errno=%d", errno);
        return static_cast<int>(TkError::kErrDeviceOpen);
    }
    if (ioctl(fd_, kUiDevCreate, 0) < 0) {
        TK_LOGW("UinputInjector: UI_DEV_CREATE 失败 errno=%d", errno);
        return static_cast<int>(TkError::kErrDeviceOpen);
    }
    return static_cast<int>(TkError::kOk);
}

void UinputInjector::close() {
    if (fd_ >= 0) {
        ioctl(fd_, kUiDevDestroy, 0);
        ::close(fd_);
        fd_ = -1;
    }
}

int UinputInjector::emit_down(int slot, int x, int y) {
    if (fd_ < 0) return static_cast<int>(TkError::kErrDeviceOpen);
    // Protocol B：切槽 → 新 tracking_id → 坐标 → 按压参数
    int rc = write_event(fd_, EV_ABS, kAbsMtSlot, slot);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    cur_slot_ = (uint16_t)slot;
    tracking_ids_[slot] = next_tracking_id_++;
    rc = write_event(fd_, EV_ABS, kAbsMtTrackingId, tracking_ids_[slot]);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_KEY, kBtnTouch, 1);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_ABS, kAbsMtPositionX, x);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_ABS, kAbsMtPositionY, y);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_ABS, kAbsMtTouchMajor, 8);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    return write_event(fd_, EV_ABS, kAbsMtPressure, 50);
}

int UinputInjector::emit_move(int slot, int x, int y) {
    if (fd_ < 0) return static_cast<int>(TkError::kErrDeviceOpen);
    if ((uint16_t)slot != cur_slot_) {
        int rc = write_event(fd_, EV_ABS, kAbsMtSlot, slot);
        if (rc != static_cast<int>(TkError::kOk)) return rc;
        cur_slot_ = (uint16_t)slot;
    }
    int rc = write_event(fd_, EV_ABS, kAbsMtPositionX, x);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    return write_event(fd_, EV_ABS, kAbsMtPositionY, y);
}

int UinputInjector::emit_up(int slot) {
    if (fd_ < 0) return static_cast<int>(TkError::kErrDeviceOpen);
    int rc = write_event(fd_, EV_ABS, kAbsMtSlot, slot);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    cur_slot_ = (uint16_t)slot;
    rc = write_event(fd_, EV_ABS, kAbsMtTrackingId, -1);  // -1 = 该指抬起
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    tracking_ids_[slot] = -1;
    // 全部指抬起后释放 BTN_TOUCH
    bool all_up = true;
    for (int i = 0; i < kMaxSlots; ++i) {
        if (tracking_ids_[i] >= 0) { all_up = false; break; }
    }
    if (all_up) {
        rc = write_event(fd_, EV_KEY, kBtnTouch, 0);
        if (rc != static_cast<int>(TkError::kOk)) return rc;
    }
    return static_cast<int>(TkError::kOk);
}

int UinputInjector::emit_sync() {
    if (fd_ < 0) return static_cast<int>(TkError::kErrDeviceOpen);
    return write_event(fd_, EV_SYN, SYN_REPORT, 0);
}

int UinputInjector::emit_wait(int64_t /*ns*/) {
    // 等待由 Scheduler 管理定时，native 档无需动作
    return static_cast<int>(TkError::kOk);
}

int UinputInjector::emit_global(int /*action*/) {
    // uinput 无法执行全局动作（HOME/BACK 等需系统权限）：忽略并告警。
    // 建议上层对 L0/L1 档屏蔽全局动作（调研 §2.1.5 能力边界）。
    TK_LOGW("UinputInjector: 档位不支持全局动作，已忽略");
    return static_cast<int>(TkError::kOk);
}

int UinputInjector::flush() {
    // 事件已在 emit_* 即时写入，无批处理缓冲
    return static_cast<int>(TkError::kOk);
}

}  // namespace tk
