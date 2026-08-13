package com.miaokatze.gtsr.common.api.progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gregtech.api.util.GTLog;

/**
 * 进度条容器（组合用，非基类）：机器组合一个实例并注册词条，
 * 注册顺序 = 终端显示顺序。词条只声明「显示口径」，本容器统一提供查询与客户端同步缓存。
 */
public class GTSRProgressBar {

    /** 词条列表（有序，注册顺序 = 显示顺序） */
    private final List<GTSRProgressEntry> entries = new ArrayList<>();
    /** 客户端同步缓存：客户端收到同步后写入，查询优先返回缓存值（见 {@link #getEntryValue}） */
    private final Map<String, Double> syncedCache = new HashMap<>();

    /** 注册词条；同 internalKey 重复注册 → 后者覆盖并记警告日志 */
    public void registerEntry(GTSRProgressEntry entry) {
        if (entry == null) return;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i)
                .getInternalKey()
                .equals(entry.getInternalKey())) {
                GTLog.err.println(
                    "Warning: GTSRProgressBar duplicate internalKey '" + entry.getInternalKey()
                        + "', the latter entry overrides the former.");
                entries.set(i, entry);
                return;
            }
        }
        entries.add(entry);
    }

    /** 词条只读视图（注册顺序 = 显示顺序） */
    public List<GTSRProgressEntry> getProgressEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean hasEntry(String internalKey) {
        return findEntry(internalKey) != null;
    }

    /**
     * 词条值：优先返回 syncedCache 中的值（客户端收到同步后显示缓存），
     * 否则返回词条 valueSupplier 的实时值；无此词条返回 NaN。
     */
    public double getEntryValue(String internalKey) {
        Double cached = syncedCache.get(internalKey);
        if (cached != null) return cached;
        GTSRProgressEntry entry = findEntry(internalKey);
        return entry != null ? entry.getValue() : Double.NaN;
    }

    /** 写同步缓存（客户端收到同步时调用；红石仓/将来使用） */
    public void cacheEntryValue(String internalKey, double value) {
        syncedCache.put(internalKey, value);
    }

    /** 词条显示名 lang 键（红石仓下拉用）；无词条返回 null */
    public String getDisplayKey(String internalKey) {
        GTSRProgressEntry entry = findEntry(internalKey);
        return entry != null ? entry.getDisplayKey() : null;
    }

    private GTSRProgressEntry findEntry(String internalKey) {
        if (internalKey == null) return null;
        for (GTSRProgressEntry entry : entries) {
            if (internalKey.equals(entry.getInternalKey())) return entry;
        }
        return null;
    }
}
