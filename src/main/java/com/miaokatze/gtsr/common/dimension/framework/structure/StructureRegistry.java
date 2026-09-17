package com.miaokatze.gtsr.common.dimension.framework.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 结构变体注册表（dim1 S4a，plan D1 / §1.2 S4a）：name → 条目（name/dimension 标记/placer 回调）。
 * <p>
 * <ul>
 * <li>dimension 标记（{@link Dimension}）：PROSPERITY/SHATTERED/ANY，跨维度隔离（破碎之地不生成
 * 繁荣古代城，plan §3.4）；S5 /gtsr structure 补全同源 {@link #names()}；</li>
 * <li>placer 回调面向 {@link BlockSink}（本类零 Minecraft 依赖，离线驱动可注册纯预览条目）；</li>
 * <li>注册时机由游戏侧显式调用（如 ProsperityWorldGenerator 构造时登记机型变体）——本类不自持静态
 * 初始化，保证离线驱动加载本类不触发任何游戏类；</li>
 * <li>同名重复注册覆盖并告警容忍（幂等注册友好）；names() 排序返回（Tab 补全稳定序）。</li>
 * </ul>
 */
public final class StructureRegistry {

    /** 结构归属维度标记。 */
    public enum Dimension {
        PROSPERITY,
        SHATTERED,
        ANY
    }

    /** 放置回调：把结构以 (originX, originY, originZ) 为原点写进 sink（确定性由 seed 派生）。 */
    public interface Placer {

        void place(BlockSink sink, int originX, int originY, int originZ, long seed);
    }

    /** 注册表条目（plan S4a：name/dimension/placer；footprint 供指令回执与城市求交）。 */
    public static final class Entry {

        public final String name;
        public final Dimension dimension;
        public final int footprintX;
        public final int footprintZ;
        public final Placer placer;

        public Entry(String name, Dimension dimension, int footprintX, int footprintZ, Placer placer) {
            this.name = name;
            this.dimension = dimension;
            this.footprintX = footprintX;
            this.footprintZ = footprintZ;
            this.placer = placer;
        }
    }

    private static final Map<String, Entry> ENTRIES = new TreeMap<>();

    private StructureRegistry() {}

    /** 注册条目（同名覆盖）。 */
    public static void register(Entry entry) {
        ENTRIES.put(entry.name, entry);
    }

    public static Entry get(String name) {
        return ENTRIES.get(name);
    }

    public static boolean contains(String name) {
        return ENTRIES.containsKey(name);
    }

    /** 全部条目（只读）。 */
    public static List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
    }

    /** 排序名单（S5 /gtsr structure Tab 补全同源）。 */
    public static List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES.keySet()));
    }
}
