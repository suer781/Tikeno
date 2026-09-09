package com.tikeno.autoclicker.model;

/**
 * PointModel — 单个坐标点（架构 §2.4 数据模型，对应 C++ TkPointFlat 的来源）。
 *
 * 坐标语义：
 *  - percent=false：x/y 为绝对像素（px）；
 *  - percent=true ：x/y 为千分比定点（×1000，如 0.5 → 500），
 *    由 SequenceFlattener 在 flatten 阶段按屏幕尺寸换算为 px（架构 §4.3）。
 */
public final class PointModel {

    public final int x;
    public final int y;
    public final boolean percent;

    public PointModel(int x, int y, boolean percent) {
        this.x = x;
        this.y = y;
        this.percent = percent;
    }

    /** 绝对像素坐标工厂 */
    public static PointModel px(int x, int y) {
        return new PointModel(x, y, false);
    }

    /** 千分比坐标工厂（x/y ∈ [0,1000]） */
    public static PointModel percent(int perMilleX, int perMilleY) {
        return new PointModel(perMilleX, perMilleY, true);
    }

    /** 换算为像素（percent=false 时原样返回） */
    public int toPxX(int screenW) {
        return percent ? (int) ((long) x * screenW / 1000L) : x;
    }

    public int toPxY(int screenH) {
        return percent ? (int) ((long) y * screenH / 1000L) : y;
    }

    @Override
    public String toString() {
        return (percent ? "pt(" : "px(") + x + "," + y + ")";
    }
}
