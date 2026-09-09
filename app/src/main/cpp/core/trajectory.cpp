// ============================================================================
// core/trajectory.cpp — 滑动轨迹插值实现
// ----------------------------------------------------------------------------
// 数学约定：全 float；t ∈ [0,1] 归一化进度。
//   - 直线      ：线性插值
//   - 贝塞尔    ：二阶 B(t) = (1-t)²P0 + 2t(1-t)C + t²P1（控制点由 ActionBuilder
//                 按起终点连线的中垂线偏移 kBezierCtrlRatio 预计算）
//   - 缓入缓出  ：smoothstep t' = t·t·(3 - 2t) 后线性插值
//   - 人手模拟  ：smoothstep + 横向（垂直于主方向）正弦摆动，幅度
//                 kHumanWobbleRatio × 距离，种子取坐标哈希（确定性，非防检测开关）
// ============================================================================

#include "core/trajectory.h"

#include <math.h>
#include <string.h>

#include "core/constants.h"

namespace tk {

namespace {

// 起终点辅助：dx/dy 与长度（float）
inline void delta_of(const TkPointFlat& from, const TkPointFlat& to,
                     float* dx, float* dy, float* dist) {
    float ddx = (float)(to.x - from.x);
    float ddy = (float)(to.y - from.y);
    *dist = sqrtf(ddx * ddx + ddy * ddy);
    *dx = ddx;
    *dy = ddy;
}

// 各曲线位置函数（签名对齐 Trajectory::PosFn）
void pos_linear(float t, float x0, float y0, float x1, float y1,
                float /*ctrl_x*/, float /*ctrl_y*/, float* px, float* py) {
    *px = x0 + (x1 - x0) * t;
    *py = y0 + (y1 - y0) * t;
}

void pos_bezier(float t, float x0, float y0, float x1, float y1,
                float cx, float cy, float* px, float* py) {
    const float u = 1.0f - t;
    const float w0 = u * u;
    const float w1 = 2.0f * t * u;
    const float w2 = t * t;
    *px = w0 * x0 + w1 * cx + w2 * x1;
    *py = w0 * y0 + w1 * cy + w2 * y1;
}

inline float smoothstep(float t) {
    return t * t * (3.0f - 2.0f * t);
}

void pos_ease_in_out(float t, float x0, float y0, float x1, float y1,
                     float /*ctrl_x*/, float /*ctrl_y*/, float* px, float* py) {
    const float te = smoothstep(t);
    *px = x0 + (x1 - x0) * te;
    *py = y0 + (y1 - y0) * te;
}

void pos_human(float t, float x0, float y0, float x1, float y1,
               float /*ctrl_x*/, float /*ctrl_y*/, float* px, float* py) {
    const float te = smoothstep(t);
    float bx = x0 + (x1 - x0) * te;
    float by = y0 + (y1 - y0) * te;
    // 横向正弦摆动：峰值在行程中段，首尾为 0（sin(πt)）
    float dx = x1 - x0;
    float dy = y1 - y0;
    float dist = sqrtf(dx * dx + dy * dy);
    if (dist > 1.0f) {
        // 单位法向量（垂直于主方向）
        float nx = -dy / dist;
        float ny = dx / dist;
        float amp = dist * kHumanWobbleRatio * sinf((float)M_PI * t);
        bx += nx * amp;
        by += ny * amp;
    }
    *px = bx;
    *py = by;
}

}  // namespace

int Trajectory::sample_with(PosFn fn, const TkPointFlat& from, const TkPointFlat& to,
                            int64_t duration_ns, int64_t step_ns,
                            float ctrl_x, float ctrl_y,
                            TkTrajSample* out, int max_out) {
    if (out == nullptr || max_out <= 0 || duration_ns <= 0 || step_ns <= 0) return 0;

    // 采样数 = 总时长 / 步长，上限 kMaxTrajSamples 防越界放大
    int count = (int)(duration_ns / step_ns);
    if (count < 1) count = 1;                       // 至少输出终点
    if (count > kMaxTrajSamples) count = kMaxTrajSamples;

    const float x0 = (float)from.x, y0 = (float)from.y;
    const float x1 = (float)to.x,   y1 = (float)to.y;

    for (int i = 1; i <= count; ++i) {
        if (i - 1 >= max_out) return max_out;       // 容量保护（先于写入）
        const float t = (float)i / (float)count;    // 不含 t=0（起点即 DOWN 事件）
        float px = 0.f, py = 0.f;
        fn(t, x0, y0, x1, y1, ctrl_x, ctrl_y, &px, &py);
        TkTrajSample& s = out[i - 1];
        s.delay_ns = duration_ns / count;           // 等分时长（尾差 ≤ 1 步长，可接受）
        s.x = (int32_t)lroundf(px);
        s.y = (int32_t)lroundf(py);
    }
    return count;
}

int Trajectory::sample(TkCurveType curve, const TkPointFlat& from, const TkPointFlat& to,
                       int64_t duration_ns, int64_t step_ns,
                       TkTrajSample* out, int max_out) {
    switch (curve) {
        case TkCurveType::kBezier:
            return sample_bezier(from, to, duration_ns, step_ns, out, max_out);
        case TkCurveType::kEaseInOut:
            return sample_ease_in_out(from, to, duration_ns, step_ns, out, max_out);
        case TkCurveType::kHuman:
            return sample_human(from, to, duration_ns, step_ns, out, max_out);
        case TkCurveType::kLinear:
        default:
            return sample_linear(from, to, duration_ns, step_ns, out, max_out);
    }
}

int Trajectory::sample_linear(const TkPointFlat& from, const TkPointFlat& to,
                              int64_t duration_ns, int64_t step_ns,
                              TkTrajSample* out, int max_out) {
    return sample_with(pos_linear, from, to, duration_ns, step_ns, 0.f, 0.f, out, max_out);
}

int Trajectory::sample_bezier(const TkPointFlat& from, const TkPointFlat& to,
                              int64_t duration_ns, int64_t step_ns,
                              TkTrajSample* out, int max_out) {
    // 控制点：中点沿法线偏移 kBezierCtrlRatio × 距离（确定性弧线）
    float dx = 0.f, dy = 0.f, dist = 0.f;
    delta_of(from, to, &dx, &dy, &dist);
    const float mx = ((float)from.x + (float)to.x) * 0.5f;
    const float my = ((float)from.y + (float)to.y) * 0.5f;
    float cx = mx, cy = my;
    if (dist > 1.0f) {
        const float nx = -dy / dist;
        const float ny = dx / dist;
        const float amp = dist * kBezierCtrlRatio;
        cx = mx + nx * amp;
        cy = my + ny * amp;
    }
    return sample_with(pos_bezier, from, to, duration_ns, step_ns, cx, cy, out, max_out);
}

int Trajectory::sample_ease_in_out(const TkPointFlat& from, const TkPointFlat& to,
                                   int64_t duration_ns, int64_t step_ns,
                                   TkTrajSample* out, int max_out) {
    return sample_with(pos_ease_in_out, from, to, duration_ns, step_ns, 0.f, 0.f, out, max_out);
}

int Trajectory::sample_human(const TkPointFlat& from, const TkPointFlat& to,
                             int64_t duration_ns, int64_t step_ns,
                             TkTrajSample* out, int max_out) {
    // 种子取坐标哈希：同参数确定性复现（便于测试回归），无外部随机依赖
    uint64_t seed = ((uint64_t)(uint32_t)from.x << 32) ^ (uint32_t)to.y ^
                    ((uint64_t)(uint32_t)from.y << 16) ^ (uint32_t)to.x;
    FastRng rng(seed);
    // 摆动相位轻微抖动（±10% 周期），由 pos_human 的 sin(πt) 主导
    (void)rng.next_float();
    return sample_with(pos_human, from, to, duration_ns, step_ns, 0.f, 0.f, out, max_out);
}

}  // namespace tk
