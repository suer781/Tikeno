#pragma once
// ============================================================================
// jni/handle_table.h — 引擎句柄表（架构 §2.3 #18 / §10.2.2）
// ----------------------------------------------------------------------------
// jlong handle → TikenoEngine* 映射（std::unordered_map + 读写锁，仅控制面）。
// 防野句柄双保险：
//   1. 表内不存在 → nullptr
//   2. 表内存在但对象 magic != "TKEN"（已析构/内存踩踏）→ nullptr
// 非法句柄一律返回 TK_ERR_BAD_HANDLE，绝不解引用。
// ============================================================================

#include <jni.h>
#include <pthread.h>
#include <unordered_map>

#include "core/constants.h"
#include "core/engine.h"
#include "core/types.h"

namespace tk {
namespace jni {

class HandleTable {
 public:
    static HandleTable& instance() {
        static HandleTable table;
        return table;
    }

    // 注册引擎，返回句柄（对象指针即句柄；安全性由 magic 校验兜底）
    jlong put(TikenoEngine* engine) {
        pthread_rwlock_wrlock(&mu_);
        const jlong handle = reinterpret_cast<jlong>(engine);
        map_[handle] = engine;
        pthread_rwlock_unlock(&mu_);
        return handle;
    }

    // 解析句柄 → 引擎指针；非法返回 nullptr（调用方回 TK_ERR_BAD_HANDLE）
    TikenoEngine* get(jlong handle) {
        if (handle == 0) return nullptr;
        pthread_rwlock_rdlock(&mu_);
        auto it = map_.find(handle);
        TikenoEngine* engine = (it != map_.end()) ? it->second : nullptr;
        pthread_rwlock_unlock(&mu_);
        // 双保险：magic 校验（防"指针已析构但句柄仍被 Java 持有"的窗口期）
        if (engine != nullptr &&
            engine->magic() != kHandleMagic) {
            return nullptr;
        }
        return engine;
    }

    // 注销（destroy 时调用；幂等）
    void remove(jlong handle) {
        pthread_rwlock_wrlock(&mu_);
        map_.erase(handle);
        pthread_rwlock_unlock(&mu_);
    }

 private:
    HandleTable() { pthread_rwlock_init(&mu_, nullptr); }
    ~HandleTable() { pthread_rwlock_destroy(&mu_); }
    HandleTable(const HandleTable&) = delete;
    HandleTable& operator=(const HandleTable&) = delete;

    pthread_rwlock_t mu_;
    std::unordered_map<jlong, TikenoEngine*> map_;
};

}  // namespace jni
}  // namespace tk
