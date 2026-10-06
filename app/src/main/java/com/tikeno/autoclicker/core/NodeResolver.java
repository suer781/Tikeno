package com.tikeno.autoclicker.core;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.tikeno.autoclicker.model.NodeSelector;
import com.tikeno.autoclicker.service.ClickerAccessibilityService;
import com.tikeno.autoclicker.util.Logx;

/**
 * NodeResolver — 基于 AccessibilityNodeInfo 的控件查找与中心坐标解析
 * （架构 §2.4.3 #62）。
 *
 * 前置条件：无障碍服务已连接 且 窗口内容读取已开启
 * （ClickerAccessibilityService.setNodeContentEnabled(true)，用完关闭）。
 * 未就绪时 resolve 返回 null（调用方按 fallbackPoint/跳过策略降级）。
 */
public final class NodeResolver {

    private static final String TAG = "Tikeno/Node";

    /** 解析结果：控件中心像素坐标（屏幕系） */
    public static final class ResolvedPoint {
        public final int x;
        public final int y;

        ResolvedPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private NodeResolver() {
    }

    /** 是否具备解析条件（服务连接 + 内容读取开启） */
    public static boolean isReady() {
        final ClickerAccessibilityService svc = ClickerAccessibilityService.peek();
        if (svc == null || svc.getRootInActiveWindow() == null) {
            return false;
        }
        return (svc.getServiceInfo() != null)
                && (svc.getServiceInfo().flags
                        & android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) != 0;
    }

    /**
     * 按选择器查找控件并解析中心坐标。
     *
     * @return 命中坐标；未命中/未就绪返回 null
     */
    public static ResolvedPoint resolve(NodeSelector selector) {
        if (selector == null || !isReady()) {
            return null;
        }
        final ClickerAccessibilityService svc = ClickerAccessibilityService.peek();
        final AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) {
            return null;
        }
        java.util.List<AccessibilityNodeInfo> hits = null;
        try {
            switch (selector.matchType) {
                case NodeSelector.MATCH_TEXT:
                    hits = root.findAccessibilityNodeInfosByText(selector.value);
                    break;
                case NodeSelector.MATCH_TEXT_CONTAINS:
                    hits = root.findAccessibilityNodeInfosByText(selector.value);
                    break;
                case NodeSelector.MATCH_ID:
                    // viewId 需带包名前缀（由调用方保证），精确匹配接口
                    hits = root.findAccessibilityNodeInfosByViewId(selector.value);
                    break;
                case NodeSelector.MATCH_DESCRIPTION:
                    hits = findByDescription(root, selector.value);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Logx.w(TAG, "节点查找异常", e);
        }
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        final Rect rect = new Rect();
        for (AccessibilityNodeInfo node : hits) {
            if (node != null && node.isVisibleToUser()) {
                node.getBoundsInScreen(rect);
                return new ResolvedPoint(rect.centerX(), rect.centerY());
            }
        }
        return null;
    }

    /** contentDescription 遍历匹配（平台无直接接口，深度优先） */
    private static java.util.List<AccessibilityNodeInfo> findByDescription(
            AccessibilityNodeInfo root, String desc) {
        final java.util.List<AccessibilityNodeInfo> out = new java.util.ArrayList<>();
        collectByDescription(root, desc, out, 0);
        return out;
    }

    private static void collectByDescription(AccessibilityNodeInfo node, String desc,
                                             java.util.List<AccessibilityNodeInfo> out,
                                             int depth) {
        if (node == null || depth > 20 || out.size() >= 8) {
            return;   // 深度/结果数上限，防大页面卡顿
        }
        if (desc.equals(String.valueOf(node.getContentDescription()))) {
            out.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectByDescription(node.getChild(i), desc, out, depth + 1);
        }
    }
}
