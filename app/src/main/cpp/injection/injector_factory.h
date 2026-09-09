#pragma once
// ============================================================================
// injection/injector_factory.h — 注入器工厂（架构 §2.3 #36 / 调研 §2.1.6）
// ----------------------------------------------------------------------------
// 按 TkInjectionTier 创建具体注入器；L0/L1 打开失败时按 L0→L1→桥接 静默
// 降级（架构分层不变式 + 调研 §2.1.6"探测成功即升级，失败静默降级"）。
// 实际生效档位经 out_tier 返回（可能与请求不同），上层据此更新 UI 徽章。
// ============================================================================

#include "core/shared_ring.h"
#include "injection/injector.h"

namespace tk {

class InjectorFactory {
 public:
    struct Result {
        IInjector* injector = nullptr;   // 所有权归调用方（delete 释放）
        TkInjectionTier actual_tier = TkInjectionTier::kL3Accessibility;
        int error = 0;                   // TK_OK 或降级前的最后错误
        bool downgraded = false;         // 是否发生降级
    };

    // 创建注入器。
    //   requested_tier : 期望档位（L0/L1 为 native 直写；L2/L3 统一为桥接）
    //   device_path    : L1 可指定 eventX 路径（null/空则自动探测）
    //   ring / out_fd  : 桥接所需（outRing 已 attach + outEfd）
    //   screen_w/h     : uinput 轴范围
    // 注：device_path 由 Engine 在控制面保存，EvdevInjector 内部先探测默认
    // 节点；指定路径的精细化注入在 T03 档位探测联调时启用。
    static Result create(TkInjectionTier requested_tier,
                         SharedRingWriter* ring,
                         int out_fd,
                         int screen_w,
                         int screen_h);
};

}  // namespace tk
