package com.tikeno.autoclicker.core;

import org.json.JSONArray;
import org.json.JSONObject;

import com.tikeno.autoclicker.model.ActionModel;
import com.tikeno.autoclicker.model.ActionSequence;
import com.tikeno.autoclicker.model.ClickConfig;
import com.tikeno.autoclicker.model.LoopPolicy;
import com.tikeno.autoclicker.model.PointModel;

/**
 * ConfigCodec — ClickConfig ⇄ JSON（架构 §2.4.2 数据持久化编解码）。
 *
 * 使用 org.json（平台内置，零依赖）。格式版本 version=1：
 * { id, name, intervalNs, powerProfile, jitterPct, pauseOnScreenOff,
 *   policy:{kind,maxCount,maxDurationNs},
 *   actions:[{type,repeat,durationMs,intervalNs,holdNs,curveType,
 *             sampleStepUs,globalAction,jitterPct,
 *             points:[{x,y,percent}]}] }
 *
 * 解析容错：非法字段取默认值；解析失败抛 JSONException（调用方兜底）。
 */
public final class ConfigCodec {

    private static final int FORMAT_VERSION = 1;

    private ConfigCodec() {
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    public static JSONObject toJson(ClickConfig config) throws Exception {
        final JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("id", config.id);
        root.put("name", config.name);
        root.put("intervalNs", config.defaultIntervalNs);
        root.put("powerProfile", config.powerProfile);
        root.put("jitterPct", (double) config.jitterPct);
        root.put("pauseOnScreenOff", config.pauseOnScreenOff);

        final JSONObject policy = new JSONObject();
        policy.put("kind", config.sequence.policy().kind);
        policy.put("maxCount", config.sequence.policy().maxCount);
        policy.put("maxDurationNs", config.sequence.policy().maxDurationNs);
        root.put("policy", policy);

        final JSONArray actions = new JSONArray();
        for (ActionModel a : config.sequence.actionsView()) {
            final JSONObject ja = new JSONObject();
            ja.put("type", a.type);
            ja.put("repeat", a.repeat);
            ja.put("durationMs", a.durationMs);
            ja.put("intervalNs", a.intervalNs);
            ja.put("holdNs", a.holdNs);
            ja.put("curveType", a.curveType);
            ja.put("sampleStepUs", a.sampleStepUs);
            ja.put("globalAction", a.globalAction);
            ja.put("jitterPct", (double) a.jitterPct);
            final JSONArray pts = new JSONArray();
            for (PointModel p : a.pointsView()) {
                final JSONObject jp = new JSONObject();
                jp.put("x", p.x);
                jp.put("y", p.y);
                jp.put("percent", p.percent);
                pts.put(jp);
            }
            ja.put("points", pts);
            actions.put(ja);
        }
        root.put("actions", actions);
        return root;
    }

    public static String toJsonString(ClickConfig config) throws Exception {
        return toJson(config).toString();
    }

    // ------------------------------------------------------------------
    // 反序列化
    // ------------------------------------------------------------------

    public static ClickConfig fromJson(String json) throws Exception {
        final JSONObject root = new JSONObject(json);
        final int version = root.optInt("version", 1);
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("不支持的配置版本 " + version);
        }

        final JSONObject jPolicy = root.optJSONObject("policy");
        final LoopPolicy policy = (jPolicy != null)
                ? new LoopPolicy(jPolicy.optInt("kind", 0),
                        jPolicy.optInt("maxCount", 0),
                        jPolicy.optLong("maxDurationNs", 0L))
                : LoopPolicy.infinite();

        final ActionSequence sequence = new ActionSequence(policy);
        final JSONArray actions = root.optJSONArray("actions");
        if (actions != null) {
            for (int i = 0; i < actions.length(); i++) {
                final JSONObject ja = actions.getJSONObject(i);
                final ActionModel a = new ActionModel();
                a.type = ja.optInt("type", ActionModel.TYPE_TAP);
                a.repeat = Math.max(1, ja.optInt("repeat", 1));
                a.durationMs = ja.optInt("durationMs", 0);
                a.intervalNs = ja.optLong("intervalNs", 200_000_000L);
                a.holdNs = ja.optLong("holdNs", 0L);
                a.curveType = ja.optInt("curveType", 0);
                a.sampleStepUs = ja.optInt("sampleStepUs", 8000);
                a.globalAction = ja.optInt("globalAction", 0);
                a.jitterPct = (float) ja.optDouble("jitterPct", 0.0);
                final JSONArray pts = ja.optJSONArray("points");
                if (pts != null) {
                    for (int j = 0; j < pts.length(); j++) {
                        final JSONObject jp = pts.getJSONObject(j);
                        a.points.add(new PointModel(
                                jp.optInt("x", 0), jp.optInt("y", 0),
                                jp.optBoolean("percent", false)));
                    }
                }
                sequence.addAction(a);
            }
        }

        return new ClickConfig(
                root.optString("id", "cfg"),
                root.optString("name", "未命名"),
                sequence,
                root.optLong("intervalNs", 200_000_000L),
                root.optInt("powerProfile", 1),
                (float) root.optDouble("jitterPct", 0.0),
                root.optBoolean("pauseOnScreenOff", true));
    }
}
