#pragma once
// ============================================================================
// core/ring_buffer.h — SPSC 无锁环形缓冲模板（header-only，架构 §2.3 #24）
// ----------------------------------------------------------------------------
// 【线程模型】单生产者 / 单消费者；head 与 tail 各自 alignas(64) 分离缓存行，
// 杜绝 false sharing。head 用 release store（发布数据），tail 用 acquire load
// （消费数据），内存序与 Linux kfifo 语义一致。
// 【使用限制】仅用于同进程控制面（CommandQueue）；跨进程共享内存的 outRing
// 使用 core/shared_ring.h（基于裸地址，Java 侧直接读写原子字段）。
// ============================================================================

#include <atomic>
#include <stdint.h>
#include <stddef.h>

namespace tk {

template <typename T, size_t kCapacity>
class SpscRing {
    static_assert((kCapacity & (kCapacity - 1)) == 0, "容量必须为 2 的幂（位运算取模）");

 public:
    SpscRing() : head_(0), tail_(0) {}

    // 生产者：入队成功返回 true；满则返回 false（不覆盖，调用方决定丢/等）
    bool try_push(const T& item) {
        const uint64_t h = head_.load(std::memory_order_relaxed);
        const uint64_t t = tail_.load(std::memory_order_acquire);
        if (h - t >= kCapacity) return false;  // 满
        buf_[h & (kCapacity - 1)] = item;
        head_.store(h + 1, std::memory_order_release);
        return true;
    }

    // 消费者：出队成功返回 true 并写入 out；空返回 false
    bool try_pop(T* out) {
        const uint64_t t = tail_.load(std::memory_order_relaxed);
        const uint64_t h = head_.load(std::memory_order_acquire);
        if (t >= h) return false;  // 空
        *out = buf_[t & (kCapacity - 1)];
        tail_.store(t + 1, std::memory_order_release);
        return true;
    }

    // 当前积压量（近似值，仅监控用）
    size_t size_approx() const {
        const uint64_t h = head_.load(std::memory_order_relaxed);
        const uint64_t t = tail_.load(std::memory_order_relaxed);
        return (size_t)(h - t);
    }

    void clear() {
        tail_.store(head_.load(std::memory_order_relaxed), std::memory_order_release);
    }

 private:
    // 缓存行分离：head（写侧）与 tail（读侧）不落同一缓存行
    alignas(64) std::atomic<uint64_t> head_;
    alignas(64) std::atomic<uint64_t> tail_;
    T buf_[kCapacity];
};

}  // namespace tk
