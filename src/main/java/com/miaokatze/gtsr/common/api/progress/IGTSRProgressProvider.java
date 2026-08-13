package com.miaokatze.gtsr.common.api.progress;

import java.util.List;

/**
 * 进度显示提供者：机器（基类/mixin）实现后，GUI helper 可统一渲染进度行。
 * 词条注册由机器类通过基类/mixin 提供的具体 registerEntry(...) 便捷方法完成（本接口不声明注册方法，
 * 只声明读取四方法）。
 */
public interface IGTSRProgressProvider {

    /** 词条只读视图（注册顺序 = 显示顺序） */
    List<GTSRProgressEntry> getProgressEntries();

    /** 是否存在指定 internalKey 的词条 */
    boolean hasEntry(String internalKey);

    /** 词条值（优先同步缓存，否则实时值）；无此词条返回 NaN */
    double getEntryValue(String internalKey);

    /** 词条显示名 lang 键（红石仓下拉用）；无词条返回 null */
    String getDisplayKey(String internalKey);
}
