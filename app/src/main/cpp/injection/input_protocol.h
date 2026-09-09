#pragma once
// ============================================================================
// injection/input_protocol.h — Linux input 子系统常量与辅助（架构 §2.3 #40）
// ----------------------------------------------------------------------------
// header-only。struct input_event 来自 <linux/input.h>（NDK sysroot 提供）；
// uinput ioctl 码在部分 NDK 版本的 <linux/uinput.h> 中不齐全，此处自行定义，
// 数值与内核 include/uapi/linux/uinput.h 一致。
// 【线程归属】UinputInjector/EvdevInjector 调度线程内联使用。
// ============================================================================

#include <linux/input.h>
#include <stdint.h>
#include <string.h>

namespace tk {

// —— 多点触摸 Protocol B 关键码（ABS_MT_*）——
inline constexpr uint16_t kAbsMtSlot       = 0x2f;  // ABS_MT_SLOT
inline constexpr uint16_t kAbsMtTouchMajor = 0x30;  // ABS_MT_TOUCH_MAJOR
inline constexpr uint16_t kAbsMtPositionX  = 0x35;  // ABS_MT_POSITION_X
inline constexpr uint16_t kAbsMtPositionY  = 0x36;  // ABS_MT_POSITION_Y
inline constexpr uint16_t kAbsMtTrackingId = 0x39;  // ABS_MT_TRACKING_ID
inline constexpr uint16_t kAbsMtPressure   = 0x3a;  // ABS_MT_PRESSURE
inline constexpr uint16_t kBtnTouch        = 330;   // BTN_TOUCH

// —— uinput ioctl（与内核 uinput.h 一致的 _IOC 编码）——
inline constexpr unsigned long kUiSetEvbit  = 0x40045564;  // UI_SET_EVBIT
inline constexpr unsigned long kUiSetKeybit = 0x40045565;  // UI_SET_KEYBIT
inline constexpr unsigned long kUiSetRelbit = 0x40045566;  // UI_SET_RELBIT
inline constexpr unsigned long kUiSetAbsbit = 0x40045567;  // UI_SET_ABSBIT
inline constexpr unsigned long kUiSetPropbit= 0x4004556e;  // UI_SET_PROPBIT
inline constexpr unsigned long kUiDevCreate = 0x00005501;  // UI_DEV_CREATE
inline constexpr unsigned long kUiDevDestroy= 0x00005502;  // UI_DEV_DESTROY

// 设备属性：直接触摸屏（悬浮窗坐标语义必需，架构调研 §2.1.5）
inline constexpr uint16_t kInputPropDirect = 0x01;

// —— uinput uinput_user_dev（UI_DEV_CREATE 前需先 write 一次设备描述）——
struct UinputUserDev {
    char name[80];
    uint16_t id_bustype;
    uint16_t id_vendor;
    uint16_t id_product;
    uint16_t id_version;
    uint32_t ff_effects_max;
    int32_t absmax[64];
    int32_t absmin[64];
    int32_t absfuzz[64];
    int32_t absflat[64];
};
static_assert(sizeof(UinputUserDev) == 92 + 64 * 16, "UinputUserDev 布局自检（name80+id8+ff4+轴1024）");

// 构造一个 input_event（time 由内核回填/消费端忽略）
inline struct input_event make_event(uint16_t type, uint16_t code, int32_t value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    return ev;
}

// 写入单个事件（fd 必须已打开）；EV_KEY/EV_ABS 后必须跟 SYN_REPORT 才生效
inline int write_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    struct input_event ev = make_event(type, code, value);
    return (write(fd, &ev, sizeof(ev)) == sizeof(ev))
        ? static_cast<int>(TkError::kOk)
        : static_cast<int>(TkError::kErrWrite);
}

}  // namespace tk
