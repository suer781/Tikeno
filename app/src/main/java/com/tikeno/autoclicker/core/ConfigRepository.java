package com.tikeno.autoclicker.core;

import android.content.Context;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.tikeno.autoclicker.model.ClickConfig;
import com.tikeno.autoclicker.util.Logx;

/**
 * ConfigRepository — 配置持久化（架构 §2.4.2，filesDir 单文件 JSON 数组）。
 *
 * 全部 IO 经 AppContainer.io() 单线程执行（调用方保证）；
 * 解析失败视为空库（日志告警，不抛出——配置损坏不应导致崩溃）。
 */
public final class ConfigRepository {

    private static final String TAG = "Tikeno/Repo";
    private static final String FILE_NAME = "configs.json";

    private final File file;

    public ConfigRepository(Context appContext) {
        this.file = new File(appContext.getFilesDir(), FILE_NAME);
    }

    /** 读取全部配置（IO 线程调用） */
    public List<ClickConfig> loadAll() {
        final List<ClickConfig> out = new ArrayList<>();
        if (!file.exists()) {
            return out;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            final byte[] buf = new byte[(int) file.length()];
            final int n = fis.read(buf);
            if (n <= 0) {
                return out;
            }
            final JSONArray arr = new JSONArray(new String(buf, 0, n, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                try {
                    out.add(ConfigCodec.fromJson(arr.getJSONObject(i).toString()));
                } catch (Exception e) {
                    Logx.w(TAG, "跳过损坏配置项 #" + i, e);
                }
            }
        } catch (Exception e) {
            Logx.w(TAG, "配置库读取失败，按空库处理", e);
        }
        return out;
    }

    /** 保存/更新（按 id 覆盖）。返回写入后的全量列表（IO 线程调用）。 */
    public List<ClickConfig> save(ClickConfig config) {
        final List<ClickConfig> all = loadAll();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(config.id)) {
                all.set(i, config);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(config);
        }
        writeAll(all);
        return all;
    }

    /** 删除（按 id）。返回写入后的全量列表（IO 线程调用）。 */
    public List<ClickConfig> delete(String id) {
        final List<ClickConfig> all = loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(id)) {
                all.remove(i);
                break;
            }
        }
        writeAll(all);
        return all;
    }

    /** 按 id 查找（IO 线程调用；未命中返回 null） */
    public ClickConfig findById(String id) {
        for (ClickConfig c : loadAll()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    private void writeAll(List<ClickConfig> configs) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            final JSONArray arr = new JSONArray();
            for (ClickConfig c : configs) {
                arr.put(ConfigCodec.toJson(c));
            }
            fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Logx.e(TAG, "配置库写入失败", e);
        }
    }
}
