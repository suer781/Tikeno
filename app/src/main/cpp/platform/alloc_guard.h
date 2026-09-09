#pragma once
// ============================================================================
// platform/alloc_guard.h — Debug 模式热路径分配哨兵（架构 §2.3 #44 / §5.4）
// ----------------------------------------------------------------------------
// 【硬性要求】执行循环内零 malloc。Debug 构建下 TK_NO_ALLOC_SCOPE() 在作用域
// 入口快照 mallinfo().uordblks（bionic 自 API 16 提供，API 28 起标记 deprecated
// 但仍可用），作用域结束时对比：若堆用量增长说明发生了分配，立即 TK_LOGE 报警。
// Release（NDEBUG）下该宏展开为空，零开销。
// ============================================================================

#ifdef NDEBUG
// Release：空实现，编译期消除
#define TK_NO_ALLOC_SCOPE() ((void)0)
#else
#include <malloc.h>
#include "platform/logging.h"

namespace tk {
// RAII 哨兵：构造快照 / 析构校验
class AllocGuard {
 public:
    AllocGuard() : before_(mallinfo().uordblks) {}
    ~AllocGuard() {
        size_t after = mallinfo().uordblks;
        if (after > before_) {
            TK_LOGE("AllocGuard: 热路径发生堆分配！delta=%zu bytes", after - before_);
        }
    }
    AllocGuard(const AllocGuard&) = delete;
    AllocGuard& operator=(const AllocGuard&) = delete;

 private:
    size_t before_;
};
}  // namespace tk

#define TK_NO_ALLOC_SCOPE() tk::AllocGuard tk_alloc_guard_instance_
#endif  // NDEBUG
