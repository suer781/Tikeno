#pragma once
// ============================================================================
// injection/bridge_injector.h — L2/L3 桥接注入器（架构 §2.3 #39 / §1.2 难点 1）
// ----------------------------------------------------------------------------
// C++ 侧只做两件事（零 JNI、零 malloc）：
//   1. SharedRingWriter::try_push(step) —— 写共享内存（outRing）
//   2. flush() 时一次 write(outEfd) —— eventfd 唤醒 Java InjectionLooper
// Java 侧（tikeno.inject 线程）批量消费 ring 并执行 dispatchGesture / Shizuku。
// L0/L1 档完全不启用本类（C++ 直接写设备）。
// ============================================================================

#include "core/shared_ring.h"
#include "injection/injector.h"

namespace tk {

class BridgeInjector : public IInjector {
 public:
    // ring 必须已 attach（outRing）；out_fd 为 outEfd（-1 允许：仅推 ring 不通知）
    BridgeInjector(SharedRingWriter* ring, int out_fd);
    ~BridgeInjector() override = default;

    int open() override;
    void close() override;

    int emit_down(int slot, int x, int y) override;
    int emit_move(int slot, int x, int y) override;
    int emit_up(int slot) override;
    int emit_sync() override;
    int emit_wait(int64_t ns) override;
    int emit_global(int action) override;
    int flush() override;

    TkInjectionTier tier() const override { return TkInjectionTier::kL2Shell; }

    // 本批次实际推送的步数（flush 后归零）；丢步计数见 ring.dropped_count()
    uint32_t pending_count() const { return pending_; }

 private:
    int push_step(TkStepKind kind, int slot, int x, int y, int64_t delay_ns, int arg);

    SharedRingWriter* ring_;
    int out_fd_;
    uint32_t pending_ = 0;   // 本批已推未通知的步数
    uint32_t seq_ = 0;       // 步序号（Java 侧校验 ring 槽位有效性）
};

}  // namespace tk
