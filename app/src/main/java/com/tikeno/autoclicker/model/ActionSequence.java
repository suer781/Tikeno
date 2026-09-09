package com.tikeno.autoclicker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ActionSequence — 动作序列 + 循环策略（架构 §2.4 数据模型）。
 * flatten 的直接输入：动作按顺序写入 seqBuf（TkActionFlat[]）。
 */
public final class ActionSequence {

    private final List<ActionModel> actions = new ArrayList<>();
    private final LoopPolicy policy;

    public ActionSequence(LoopPolicy policy) {
        this.policy = (policy != null) ? policy : LoopPolicy.infinite();
    }

    public void addAction(ActionModel action) {
        if (action != null) {
            actions.add(action);
        }
    }

    public List<ActionModel> actionsView() {
        return Collections.unmodifiableList(actions);
    }

    public int actionCount() {
        return actions.size();
    }

    public LoopPolicy policy() {
        return policy;
    }
}
