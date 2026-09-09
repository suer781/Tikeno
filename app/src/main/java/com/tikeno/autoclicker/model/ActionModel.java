package com.tikeno.autoclicker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ActionModel — 单个动作（架构 §2.4 数据模型，flatten 前的 Java 态）。
 *
 * 字段与 C++ TkActionFlat（cpp/core/types.h）一一对应：
 *   type / repeat / durationMs / intervalNs / holdNs / points / curveType /
 *   sampleStepUs / globalAction / missPolicy / jitterPct。
 *
 * 动作类型常量与 TkActionType（1..7）一致。
 */
public final class ActionModel {

    // —— 动作类型（与 C++ TkActionType 一致）——
    public static final int TYPE_TAP = 1;
    public static final int TYPE_LONG_PRESS = 2;
    public static final int TYPE_SWIPE = 3;
    public static final int TYPE_MULTI_TOUCH = 4;
    public static final int TYPE_WAIT = 5;
    public static final int TYPE_GLOBAL = 6;
    public static final int TYPE_CONDITION = 7;  // P1 预留，T03 不展开

    // —— 全局动作（与 C++ TkGlobalAction / AccessibilityService 常量一致）——
    public static final int GLOBAL_BACK = 1;
    public static final int GLOBAL_HOME = 2;
    public static final int GLOBAL_RECENTS = 3;
    public static final int GLOBAL_NOTIFICATIONS = 4;

    public int type = TYPE_TAP;
    public int repeat = 1;              // ≥1
    public int durationMs = 0;          // 长按/滑动/等待时长
    public long intervalNs = 200_000_000L;  // 动作后间隔（默认 200ms）
    public long holdNs = 0L;            // 按下保持（0=用 durationMs 推导）
    public final List<PointModel> points = new ArrayList<>();
    public int curveType = 0;           // 0=直线 1=贝塞尔 2=ease-in-out 3=人手
    public int sampleStepUs = 8000;     // 轨迹采样步长（微秒）
    public int globalAction = 0;        // TYPE_GLOBAL 时有效
    public float jitterPct = 0f;        // 0=关闭抖动
    public NodeSelector nodeSelector;   // 控件选择器（可空；T03 阶段解析由 NodeResolver 补齐）

    /** 点击工厂：单点，intervalNs 可覆盖默认间隔 */
    public static ActionModel tap(PointModel p, long intervalNs) {
        ActionModel a = new ActionModel();
        a.type = TYPE_TAP;
        a.points.add(p);
        a.intervalNs = intervalNs;
        return a;
    }

    /** 等待工厂 */
    public static ActionModel waitMs(int durationMs) {
        ActionModel a = new ActionModel();
        a.type = TYPE_WAIT;
        a.durationMs = durationMs;
        a.intervalNs = 0;   // 等待动作本身不再叠加间隔
        return a;
    }

    /** 全局动作工厂 */
    public static ActionModel global(int globalAction, long intervalNs) {
        ActionModel a = new ActionModel();
        a.type = TYPE_GLOBAL;
        a.globalAction = globalAction;
        a.intervalNs = intervalNs;
        return a;
    }

    /** 只读点列表视图 */
    public List<PointModel> pointsView() {
        return Collections.unmodifiableList(points);
    }

    /** 本动作跟随的点数（flatten 时写入 TkActionFlat.pointCount） */
    public int pointCount() {
        return points.size();
    }
}
