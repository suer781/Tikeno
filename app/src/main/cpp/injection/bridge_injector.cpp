// ============================================================================
// injection/bridge_injector.cpp — L2/L3 桥接注入器实现
// ============================================================================

#include "injection/bridge_injector.h"

#include "core/types.h"
#include "platform/logging.h"

namespace tk {

BridgeInjector::BridgeInjector(SharedRingWriter* ring, int out_fd)
    : ring_(ring), out_fd_(out_fd) {}

int BridgeInjector::open() {
    if (ring_ == nullptr || !ring_->attached()) {
        TK_LOGE("BridgeInjector: outRing 未挂载");
        return static_cast<int>(TkError::kErrBufferNotAttached);
    }
    if (out_fd_ >= 0) {
        ring_->set_out_fd(out_fd_);
    }
    return static_cast<int>(TkError::kOk);
}

void BridgeInjector::close() {
    // 共享内存与 eventfd 由 Java 拥有，桥接器不关闭
    ring_ = nullptr;
    out_fd_ = -1;
}

int BridgeInjector::push_step(TkStepKind kind, int slot, int x, int y,
                              int64_t delay_ns, int arg) {
    if (ring_ == nullptr) return static_cast<int>(TkError::kErrBufferNotAttached);
    TkStep s;
    s.kind = static_cast<int32_t>(kind);
    s.slot = slot;
    s.x = x;
    s.y = y;
    s.delayNs = delay_ns;
    s.arg = arg;
    s.seq = static_cast<int32_t>(++seq_);
    if (!ring_->try_push(s)) {
        // 满：丢步（架构 §7.2 允许），累计在 ring.dropped_count()
        TK_LOGW("BridgeInjector: outRing 满，丢步（累计 %llu）",
                (unsigned long long)ring_->dropped_count());
        return static_cast<int>(TkError::kWarnRingFullDropped);
    }
    ++pending_;
    return static_cast<int>(TkError::kOk);
}

int BridgeInjector::emit_down(int slot, int x, int y) {
    return push_step(TkStepKind::kDown, slot, x, y, 0, 0);
}

int BridgeInjector::emit_move(int slot, int x, int y) {
    return push_step(TkStepKind::kMove, slot, x, y, 0, 0);
}

int BridgeInjector::emit_up(int slot) {
    return push_step(TkStepKind::kUp, slot, 0, 0, 0, 0);
}

int BridgeInjector::emit_sync() {
    return push_step(TkStepKind::kSync, 0, 0, 0, 0, 0);
}

int BridgeInjector::emit_wait(int64_t ns) {
    return push_step(TkStepKind::kWait, 0, 0, 0, ns, 0);
}

int BridgeInjector::emit_global(int action) {
    return push_step(TkStepKind::kGlobal, 0, 0, 0, 0, action);
}

int BridgeInjector::flush() {
    if (pending_ > 0 && ring_ != nullptr) {
        // 每 tick 仅一次 eventfd write（1~2μs），Java 侧被唤醒后批量 drain
        ring_->notify();
        pending_ = 0;
    }
    return static_cast<int>(TkError::kOk);
}

}  // namespace tk
