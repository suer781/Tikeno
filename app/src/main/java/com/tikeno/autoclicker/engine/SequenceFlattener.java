package com.tikeno.autoclicker.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.tikeno.autoclicker.model.ActionModel;
import com.tikeno.autoclicker.model.ActionSequence;
import com.tikeno.autoclicker.model.PointModel;

/**
 * SequenceFlattener — ActionSequence → TkActionFlat 扁平字节（架构 §2.4.4 #73）。
 *
 * 写入复用的 seqBuf（DirectByteBuffer，零拷贝交给 C++）：
 *   前 8 字节 = actionCount(int32) + schemaVersion(int32)；
 *   其后每个动作 = 64B TkActionFlat + 16B × pointCount 的 TkPointFlat[]。
 *
 * 职责：百分比→px 换算（架构 §4.3：Java 侧完成换算，C++ 只收 px）。
 * 节点解析（TkActionFlat.flags bit2）在 T03 阶段暂不置位
 * （NodeResolver 随后续轮次接入，见交付说明偏差记录）。
 */
public final class SequenceFlattener {

    private SequenceFlattener() {
    }

    // TkActionFlat 字段偏移（与 cpp/core/types.h 一致，仅注释自检用）
    // 64B: type@0 flags@4 repeat@8 durationMs@12 intervalNs@16 holdNs@24
    //      pointCount@32 curveType@36 sampleStepUs@40 coordMode@44
    //      globalAction@48 missPolicy@52 jitterPct@56 reserved@60

    /**
     * 展开序列到 seqBuf。
     *
     * @return 0 成功；-1003 参数非法（空序列/超容量）
     */
    public static int flatten(ActionSequence sequence, int screenW, int screenH,
                              ByteBuffer seqBuf) {
        if (sequence == null || seqBuf == null || !seqBuf.isDirect()) {
            return -1003;
        }
        final int count = sequence.actionCount();
        if (count <= 0 || count > 256 /* kMaxActions */) {
            return -1003;
        }
        // 容量校验：8B 头 + count×(64 + 20×16) ≤ 256KB
        final int worst = 8 + count * (64 + 20 * 16);
        if (seqBuf.capacity() < worst) {
            return -1003;
        }

        final ByteBuffer b = seqBuf.order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, count);
        b.putInt(4, 1);   // kConfigSchemaVersion（与 C++ constants.h 一致）

        int off = 8;
        for (ActionModel a : sequence.actionsView()) {
            final int pc = a.pointCount();
            b.putInt(off, a.type);                  // +0  type
            b.putInt(off + 4, flagsOf(a));          // +4  flags（bit1 抖动）
            b.putInt(off + 8, Math.max(1, a.repeat));
            b.putInt(off + 12, a.durationMs);
            b.putLong(off + 16, a.intervalNs);
            b.putLong(off + 24, a.holdNs);
            b.putInt(off + 32, pc);
            b.putInt(off + 36, a.curveType);
            b.putInt(off + 40, a.sampleStepUs);
            b.putInt(off + 44, 0 /* coordMode：已换算为绝对 px */);
            b.putInt(off + 48, a.globalAction);
            b.putInt(off + 52, 0 /* missPolicy：跳过（T03 默认）*/);
            b.putFloat(off + 56, a.jitterPct);
            b.putInt(off + 60, 0);                  // reserved
            off += 64;

            for (int i = 0; i < pc; i++) {
                final PointModel p = a.pointsView().get(i);
                b.putInt(off, p.toPxX(screenW));      // +0 x（percent→px 已完成）
                b.putInt(off + 4, p.toPxY(screenH));  // +4 y
                b.putInt(off + 8, 0);                 // +8 offsetX
                b.putInt(off + 12, 0);                // +12 offsetY
                off += 16;
            }
        }
        return 0;
    }

    private static int flagsOf(ActionModel a) {
        int flags = 0;
        if (a.jitterPct > 0f) {
            flags |= 0x2;   // bit1 启用抖动
        }
        // bit0（百分比坐标）与 bit2（需节点解析）在 T03 均不置位：
        // 坐标已换算为 px；节点解析待 NodeResolver 接入后由 flatten 调用方控制。
        return flags;
    }
}
