// ============================================================================
// core/shared_ring.cpp — outRing 共享内存 SPSC 写端实现
// ============================================================================

#include "core/shared_ring.h"

#include <unistd.h>
#include <errno.h>
#include <string.h>

#include "core/constants.h"
#include "platform/logging.h"

namespace tk {

SharedRingWriter::SharedRingWriter() = default;

int SharedRingWriter::attach(void* base, size_t size_bytes) {
    if (base == nullptr) return static_cast<int>(TkError::kErrInvalidArg);
    if (size_bytes < kOutRingHeaderBytes + kOutRingCapacity * kOutRingStepBytes) {
        TK_LOGE("SharedRingWriter: 缓冲过小 %zu < %zu", size_bytes,
                kOutRingHeaderBytes + kOutRingCapacity * kOutRingStepBytes);
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    auto* head = head_at(base);
    auto* tail = tail_at(base);
    auto* cap  = reinterpret_cast<uint64_t*>(static_cast<char*>(base) + kOffCapacity);
    auto* mg   = reinterpret_cast<uint64_t*>(static_cast<char*>(base) + kOffMagic);

    if (mg != nullptr && *mg != kRingMagic) {
        // 首次绑定：由 C++ 侧初始化 header（Java 侧分配后清零）
        if (*mg == 0) {
            head->store(0, std::memory_order_relaxed);
            tail->store(0, std::memory_order_relaxed);
            *cap = kOutRingCapacity;
            *mg = kRingMagic;
            TK_LOGD("SharedRingWriter: 初始化 outRing header（capacity=%llu）",
                    (unsigned long long)kOutRingCapacity);
        } else {
            TK_LOGE("SharedRingWriter: magic 不匹配 0x%llx", (unsigned long long)*mg);
            return static_cast<int>(TkError::kErrInvalidArg);
        }
    }
    if (*cap != kOutRingCapacity) {
        TK_LOGE("SharedRingWriter: capacity 不一致 %llu", (unsigned long long)*cap);
        return static_cast<int>(TkError::kErrInvalidArg);
    }
    base_ = base;
    size_bytes_ = size_bytes;
    return static_cast<int>(TkError::kOk);
}

bool SharedRingWriter::try_push(const TkStep& step) {
    if (base_ == nullptr) return false;
    auto* head = head_at(base_);
    auto* tail = tail_at(base_);
    const uint64_t h = head->load(std::memory_order_relaxed);
    const uint64_t t = tail->load(std::memory_order_acquire);
    if (h - t >= kOutRingCapacity) {
        ++dropped_count_;  // 满：丢步并计数（架构 §7.2），不阻塞调度线程
        return false;
    }
    void* slot = static_cast<char*>(base_) + kOffSlots +
                 (h & (kOutRingCapacity - 1)) * kOutRingStepBytes;
    memcpy(slot, &step, kOutRingStepBytes);
    head->store(h + 1, std::memory_order_release);
    return true;
}

int SharedRingWriter::notify() {
    if (out_efd_ < 0) return static_cast<int>(TkError::kOk);  // 未挂载 outEfd（L0/L1 档）
    uint64_t one = 1;
    ssize_t n = write(out_efd_, &one, sizeof(one));
    if (n != sizeof(one)) {
        if (errno == EAGAIN) return static_cast<int>(TkError::kOk);  // 计数器已非零，等效
        return static_cast<int>(TkError::kErrWrite);
    }
    return static_cast<int>(TkError::kOk);
}

}  // namespace tk
