#pragma once
// ============================================================================
// core/fast_rng.h — xorshift128+ 快速随机数（header-only，架构 §2.3 #27）
// ----------------------------------------------------------------------------
// 用途：随机间隔抖动（默认关闭，仅 flags bit1 且 jitterPct>0 时启用）与
// 人手模拟曲线的确定性摆动。零分配、零系统调用、非密码学安全。
// 【线程归属】仅在控制面（loadSequence）使用，不进入热路径。
// ============================================================================

#include <stdint.h>

namespace tk {

class FastRng {
 public:
    explicit FastRng(uint64_t seed) {
        // 用 splitmix64 扩散种子，避免全零状态（xorshift128+ 全零会锁死）
        uint64_t s = seed ^ 0x9E3779B97F4A7C15ULL;
        s_ = split_mix_64(s);
        t_ = split_mix_64(s + 0x123456789ABCDEFULL);
        // 保证非零状态
        if (s_ == 0 && t_ == 0) s_ = 1;
    }

    // xorshift128+ 主生成（Thomas & Doty 2014）
    inline uint64_t next_u64() {
        uint64_t a = s_;
        uint64_t b = t_;
        s_ = b;
        a ^= a << 23;
        a ^= a >> 17;
        a ^= b ^ (b >> 26);
        t_ = a;
        return a + b;
    }

    // [0, 1) 均匀分布（取高 53 位，float 计算精度够用且无 double）
    inline float next_float() {
        return (float)(next_u64() >> 11) * (1.0f / 9007199254740992.0f);
    }

    // [-span, +span] 区间均匀采样
    inline float next_symmetric(float span) {
        return (next_float() * 2.0f - 1.0f) * span;
    }

 private:
    static inline uint64_t split_mix_64(uint64_t x) {
        x += 0x9E3779B97F4A7C15ULL;
        x = (x ^ (x >> 30)) * 0xBF58476D1CE4E5B9ULL;
        x = (x ^ (x >> 27)) * 0x94D049BB133111EBULL;
        return x ^ (x >> 31);
    }

    uint64_t s_ = 1;
    uint64_t t_ = 2;
};

}  // namespace tk
