#pragma once
// ============================================================================
// core/shared_ring.h — outRing 共享内存 SPSC 写端（架构 §2.3 #25 / §1.2 难点 1）
// ----------------------------------------------------------------------------
// 【布局契约】（架构 §4.3，Java 侧 OutputRing 逐字节对应）
//   offset 0  : head   (uint64, 自增游标，C++ 写端独占)   alignas 缓存行 0
//   offset 8  : tail   (uint64, 自增游标，Java 读端独占)   同一缓存行内但不同 8B
//   offset 16 : capacity (uint64, = kOutRingCapacity)
//   offset 24 : magic   (uint64, kRingMagic)
//   offset 64 : 槽位数组，每槽 32B（TkStep），共 4096 槽
// 内存序：head 发布（release store），tail 消费（acquire load）。
// 【线程归属】C++ 调度线程独占写；Java InjectionLooper 通过
//   MessageQueue.addOnFileDescriptorEventListener(outFd) 被唤醒后读。
// ============================================================================

#include <atomic>
#include <stdint.h>

#include "core/constants.h"
#include "core/types.h"

namespace tk {

class SharedRingWriter {
 public:
    SharedRingWriter();

    // 绑定外部缓冲（DirectByteBuffer 裸地址）。校验 magic 与容量，
    // 首次绑定时初始化 head/tail/capacity/magic；重复绑定校验一致性。
    // 返回 TK_OK / TK_ERR_INVALID_ARG。
    int attach(void* base, size_t size_bytes);

    // 绑定 outEfd（eventfd），notify() 写入；L0/L1 档不调用（notify 变为 no-op）。
    void set_out_fd(int fd) { out_efd_ = fd; }

    // 非阻塞入队一步。满则返回 false（调用方计丢步，架构 §7.2 允许高频丢步）。
    bool try_push(const TkStep& step);

    // 写 outEfd（eventfd）通知 Java 侧 —— 全链路唯一的跨线程系统调用（1~2μs）。
    // 返回 TK_OK / TK_ERR_WRITE。
    int notify();

    bool attached() const { return base_ != nullptr; }
    uint64_t dropped_count() const { return dropped_count_; }

 private:
    static constexpr size_t kOffHead     = 0;
    static constexpr size_t kOffTail     = 8;
    static constexpr size_t kOffCapacity = 16;
    static constexpr size_t kOffMagic    = 24;
    static constexpr size_t kOffSlots    = 64;

    // 共享内存中的原子字段直接以 volatile 指针访问（JNI 侧无 std::atomic 对象布局保证）
    static std::atomic<uint64_t>* head_at(void* base) {
        return reinterpret_cast<std::atomic<uint64_t>*>(static_cast<char*>(base) + kOffHead);
    }
    static std::atomic<uint64_t>* tail_at(void* base) {
        return reinterpret_cast<std::atomic<uint64_t>*>(static_cast<char*>(base) + kOffTail);
    }

    void* base_ = nullptr;
    size_t size_bytes_ = 0;
    int out_efd_ = -1;
    uint64_t dropped_count_ = 0;
};

}  // namespace tk
