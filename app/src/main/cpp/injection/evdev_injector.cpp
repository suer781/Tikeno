// ============================================================================
// injection/evdev_injector.cpp — L1 evdev 直写实现
// ============================================================================

#include "injection/evdev_injector.h"

#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>

#include "core/constants.h"
#include "injection/input_protocol.h"
#include "platform/logging.h"

namespace tk {

EvdevInjector::EvdevInjector() {
    for (int i = 0; i < kMaxSlots; ++i) pressed_[i] = false;
}

EvdevInjector::~EvdevInjector() {
    close();
}

int EvdevInjector::open() {
    if (fd_ >= 0) return static_cast<int>(TkError::kOk);  // 幂等
    const int rc = probe_device();
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    TK_LOGI("EvdevInjector: 已打开 %s", path_);
    return static_cast<int>(TkError::kOk);
}

int EvdevInjector::probe_device() {
    // 枚举 event0..15：以 O_WRONLY 试开（不可写即权限/SELinux 拦截，换下一个）。
    // 两轮：第一轮找 ABS_MT 能力可读的（名字含 touch），失败则取任意可写节点。
    static const char* kNameHints[] = { "touch", "mt", "goodix", "synaptics", "focaltech" };
    for (int pass = 0; pass < 2; ++pass) {
        for (int i = 0; i < 16; ++i) {
            char p[64];
            snprintf(p, sizeof(p), "/dev/input/event%d", i);
            int fd = ::open(p, O_WRONLY | O_NONBLOCK);
            if (fd < 0) continue;
            if (pass == 0) {
                // 粗过滤：读名字（需 O_RDONLY 权限可能失败，失败不淘汰）
                char name[128] = {0};
                char name_path[96];
                snprintf(name_path, sizeof(name_path),
                         "/sys/class/input/event%d/device/name", i);
                FILE* f = fopen(name_path, "r");
                if (f != nullptr) {
                    if (fgets(name, sizeof(name), f) == nullptr) name[0] = '\0';
                    fclose(f);
                }
                bool hit = false;
                for (const char* hint : kNameHints) {
                    if (strstr(name, hint) != nullptr) { hit = true; break; }
                }
                if (!hit) {
                    ::close(fd);
                    continue;
                }
            }
            snprintf(path_, sizeof(path_), "%s", p);
            fd_ = fd;
            return static_cast<int>(TkError::kOk);
        }
    }
    TK_LOGW("EvdevInjector: 未找到可写的 /dev/input/eventX（权限 660 / SELinux）");
    return static_cast<int>(TkError::kErrDeviceOpen);
}

void EvdevInjector::close() {
    if (fd_ >= 0) {
        // 安全收尾：确保 BTN_TOUCH 释放
        write_event(fd_, EV_KEY, kBtnTouch, 0);
        write_event(fd_, EV_SYN, SYN_REPORT, 0);
        ::close(fd_);
        fd_ = -1;
        path_[0] = '\0';
    }
}

int EvdevInjector::emit_down(int slot, int x, int y) {
    if (fd_ < 0) return static_cast<int>(TkError::kErrDeviceOpen);
    // 多指 Protocol B（目标设备需支持 ABS_MT_SLOT；单点设备用 slot 0 等效单指）
    int rc = write_event(fd_, EV_ABS, kAbsMtSlot, slot);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_ABS, kAbsMtTrackingId, slot + 1);  // ≥1 为有效接触
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_KEY, kBtnTouch, 1);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_ABS, kAbsMtPositionX, x);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_ABS, kAbsMtPositionY, y);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    pressed_[slot] = true;
    return static_cast<int>(TkError::kOk);
}

int EvdevInjector::emit_move(int slot, int x, int y) {
    if (fd_ < 0) return static_cast<int>(TkError::kErrDeviceOpen);
    int rc = write_event(fd_, EV_ABS, kAbsMtSlot, slot);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_ABS, kAbsMtPositionX, x);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    return write_event(fd_, EV_ABS, kAbsMtPositionY, y);
}

int EvdevInjector::emit_up(int slot) {
    if (fd_ < 0) return static_cast<int>(TkError::kErrDeviceOpen);
    int rc = write_event(fd_, EV_ABS, kAbsMtSlot, slot);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    rc = write_event(fd_, EV_ABS, kAbsMtTrackingId, -1);
    if (rc != static_cast<int>(TkError::kOk)) return rc;
    pressed_[slot] = false;
    bool all_up = true;
    for (int i = 0; i < kMaxSlots; ++i) {
        if (pressed_[i]) { all_up = false; break; }
    }
    if (all_up) {
        rc = write_event(fd_, EV_KEY, kBtnTouch, 0);
        if (rc != static_cast<int>(TkError::kOk)) return rc;
    }
    return static_cast<int>(TkError::kOk);
}

int EvdevInjector::emit_sync() {
    if (fd_ < 0) return static_cast<int>(TkError::kErrDeviceOpen);
    return write_event(fd_, EV_SYN, SYN_REPORT, 0);
}

int EvdevInjector::emit_wait(int64_t /*ns*/) {
    return static_cast<int>(TkError::kOk);  // 等待由 Scheduler 管理
}

int EvdevInjector::emit_global(int /*action*/) {
    TK_LOGW("EvdevInjector: 档位不支持全局动作，已忽略");
    return static_cast<int>(TkError::kOk);
}

int EvdevInjector::flush() {
    return static_cast<int>(TkError::kOk);  // 事件即时写入，无缓冲
}

}  // namespace tk
