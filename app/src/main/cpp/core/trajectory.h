#pragma once
// ============================================================================
// core/trajectory.h — 滑动轨迹插值（架构 §2.3 #28 / PRD 模块二 P1）
// ----------------------------------------------------------------------------
// 四种曲线：直线 / 二阶贝塞尔 / ease-in-out（smoothstep）/ 人手模拟。
// 【零分配契约】sample_into() 输出到调用方预分配的 TkTrajSample 数组，
// 返回值仅为采样个数；全程 float 运算（架构 §10.3 禁 double）。
// 【线程归属】控制面（loadSequence 展开阶段），非热路径。
// ============================================================================

#include "core/types.h"
#include "core/fast_rng.h"

namespace tk {

class Trajectory {
 public:
    // 按曲线类型采样 (from → to) 的轨迹，duration_ns 总时长，step_ns 采样步长。
    // out 必须由调用方分配，max_out 为容量（≥1）。
    // 返回采样个数（含终点，≥1）；参数非法返回 0。
    static int sample(TkCurveType curve,
                      const TkPointFlat& from,
                      const TkPointFlat& to,
                      int64_t duration_ns,
                      int64_t step_ns,
                      TkTrajSample* out,
                      int max_out);

    // 便捷封装：各类曲线的独立入口（类图 §4.2 对应）。
    static int sample_linear(const TkPointFlat& from, const TkPointFlat& to,
                             int64_t duration_ns, int64_t step_ns,
                             TkTrajSample* out, int max_out);
    static int sample_bezier(const TkPointFlat& from, const TkPointFlat& to,
                             int64_t duration_ns, int64_t step_ns,
                             TkTrajSample* out, int max_out);
    static int sample_ease_in_out(const TkPointFlat& from, const TkPointFlat& to,
                                  int64_t duration_ns, int64_t step_ns,
                                  TkTrajSample* out, int max_out);
    static int sample_human(const TkPointFlat& from, const TkPointFlat& to,
                            int64_t duration_ns, int64_t step_ns,
                            TkTrajSample* out, int max_out);

 private:
    // 通用采样骨架：progress(t∈[0,1]) → 位置，由各曲线提供
    typedef void (*PosFn)(float t, float x0, float y0, float x1, float y1,
                          float ctrl_x, float ctrl_y, float* px, float* py);
    static int sample_with(PosFn fn, const TkPointFlat& from, const TkPointFlat& to,
                           int64_t duration_ns, int64_t step_ns,
                           float ctrl_x, float ctrl_y,
                           TkTrajSample* out, int max_out);
};

}  // namespace tk
