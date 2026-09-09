// ============================================================================
// injection/injector_factory.cpp — 注入器工厂实现
// ============================================================================

#include "injection/injector_factory.h"

#include <string.h>

#include "injection/bridge_injector.h"
#include "injection/evdev_injector.h"
#include "injection/uinput_injector.h"
#include "platform/logging.h"

namespace tk {

InjectorFactory::Result InjectorFactory::create(TkInjectionTier requested_tier,
                                                SharedRingWriter* ring,
                                                int out_fd,
                                                int screen_w,
                                                int screen_h) {
    Result result;

    switch (requested_tier) {
        case TkInjectionTier::kL0Uinput: {
            // L0：uinput 失败 → 降级 L1
            UinputInjector* uinput = new UinputInjector(screen_w, screen_h);
            if (uinput->open() == static_cast<int>(TkError::kOk)) {
                result.injector = uinput;
                result.actual_tier = TkInjectionTier::kL0Uinput;
                result.error = static_cast<int>(TkError::kOk);
                return result;
            }
            result.error = static_cast<int>(TkError::kErrDeviceOpen);
            delete uinput;
            TK_LOGW("InjectorFactory: L0 失败，降级 L1（evdev）");
            result.downgraded = true;
            // fallthrough → L1
            [[fallthrough]];
        }
        case TkInjectionTier::kL1Evdev: {
            EvdevInjector* evdev = new EvdevInjector();
            if (evdev->open() == static_cast<int>(TkError::kOk)) {
                result.injector = evdev;
                result.actual_tier = TkInjectionTier::kL1Evdev;
                if (!result.downgraded) result.error = static_cast<int>(TkError::kOk);
                return result;
            }
            result.error = static_cast<int>(TkError::kErrDeviceOpen);
            delete evdev;
            TK_LOGW("InjectorFactory: L1 失败，降级桥接（L2/L3）");
            result.downgraded = true;
            // fallthrough → 桥接
            [[fallthrough]];
        }
        case TkInjectionTier::kL2Shell:
        case TkInjectionTier::kL3Accessibility:
        default: {
            BridgeInjector* bridge = new BridgeInjector(ring, out_fd);
            const int rc = bridge->open();
            if (rc == static_cast<int>(TkError::kOk)) {
                result.injector = bridge;
                result.actual_tier = requested_tier;  // 桥接档按请求返回（Java 侧细分）
                if (!result.downgraded) result.error = static_cast<int>(TkError::kOk);
                return result;
            }
            result.error = rc;
            delete bridge;
            result.injector = nullptr;
            result.actual_tier = TkInjectionTier::kL3Accessibility;
            return result;
        }
    }
}

}  // namespace tk
