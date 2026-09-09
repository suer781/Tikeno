package com.tikeno.autoclicker.model;

/**
 * NodeSelector — 控件选择器（架构 §2.4.3 #62 的数据载体）。
 *
 * T03 阶段仅作为数据结构（flatten 时记录"需节点解析"标记）；
 * 基于 AccessibilityNodeInfo 的实际解析由 NodeResolver 在后续轮次补齐。
 * T03 内若 nodeSelector 非空且解析不可用，按 fallbackPoint 降级为坐标点击。
 */
public final class NodeSelector {

    /** 匹配方式常量 */
    public static final int MATCH_TEXT = 0;        // 精确文本
    public static final int MATCH_TEXT_CONTAINS = 1;
    public static final int MATCH_ID = 2;          // viewIdResourceName
    public static final int MATCH_DESCRIPTION = 3; // contentDescription

    public final int matchType;
    public final String value;
    /** 解析失败/不可用时的兜底坐标（px），可空 */
    public final PointModel fallbackPoint;

    public NodeSelector(int matchType, String value, PointModel fallbackPoint) {
        this.matchType = matchType;
        this.value = value;
        this.fallbackPoint = fallbackPoint;
    }

    public static NodeSelector byText(String text, PointModel fallback) {
        return new NodeSelector(MATCH_TEXT, text, fallback);
    }
}
