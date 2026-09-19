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

    /**
     * 注册表条目。
     * <p>
     * <b>P7 扩形状（plan §5 P7「Entry 扩族/概率/重复上限字段（或外部参数表，二选一，推荐扩字段）」）</b>：
     * 在原 name/dimension/footprint/placer 之后追加四项——{@code family}（放置族，取
     * {@link PlacementGate} 的族常量）、{@code placementDenominator}（放置分母，0 = 跟随 Config 族键）、
     * {@code windowRepeatCap}（H-2 每 16×16 窗同模板重复上限，0 = 跟随 Config 族键）、
     * {@code allowsDamagedVariant}（是否允许损毁变体，P8 废墟族的 opt-in 损毁算子读它）。
     * <p>
     * <b>选"扩字段"而不是"外部参数表"的理由</b>（写死在此处，避免下一片再讨论）：
     * <ol>
     * <li>plan §2.1 横切 roster 行要求"结构名册 = 单一真值表"，另立一张 name→参数表等于把同一事实
     * 拆成两处登记，且必须再写一条"两表名集合相等"的断言来防漂移（P0 已经为名册/字符盘付过一次这个成本）；</li>
     * <li>条目本身已按 name 唯一，扩字段天然继承这个键，不需要第二张表的索引与生命周期；</li>
     * <li>注册点（{@code registerVariants}）就紧邻形状定义，参数与模板同处可读，改机型时不会漏改外部表。</li>
     * </ol>
     * <b>数字真值仍然只有一处</b>：三项数值字段允许 0 = "由 Config 族键决定"，本片 machine/outpost
     * 两族全部传 0（Config 是唯一出处）；只有当某模板需要偏离族级默认（P8 的按 variant 概率）时
     * 才在 roster 里写非 0 值，此时 roster 升为该模板的唯一出处，Config 只留族级默认与回退位。
     * 五参构造保留（{@code CityVariants} 的 26 个城内条目在本片不改，落 {@link PlacementGate#FAMILY_UNSCOPED}）。
     */
    public static final class Entry {

        public final String name;
        public final Dimension dimension;
        public final int footprintX;
        public final int footprintZ;
        public final Placer placer;
        /** 放置族（{@link PlacementGate} 的 {@code FAMILY_*} 常量；{@code FAMILY_UNSCOPED} = 未标注）。 */
        public final String family;
        /** 放置分母（1/N chunk）；0 = 跟随 Config 族键。 */
        public final int placementDenominator;
        /** H-2 每窗同模板重复上限；0 = 跟随 Config 族键。 */
        public final int windowRepeatCap;
        /** 是否允许损毁变体（P8 废墟族的 opt-in 损毁算子读；既有两族均为 false）。 */
        public final boolean allowsDamagedVariant;

        /** 五参构造（P7 前的既有形态）：族标注为未入册，三项数值跟随 Config。 */
        public Entry(String name, Dimension dimension, int footprintX, int footprintZ, Placer placer) {
            this(name, dimension, footprintX, footprintZ, placer, PlacementGate.FAMILY_UNSCOPED, 0, 0, false);
        }

        public Entry(String name, Dimension dimension, int footprintX, int footprintZ, Placer placer, String family,
            int placementDenominator, int windowRepeatCap, boolean allowsDamagedVariant) {
            this.name = name;
            this.dimension = dimension;
            this.footprintX = footprintX;
            this.footprintZ = footprintZ;
            this.placer = placer;
            this.family = family;
            this.placementDenominator = placementDenominator;
            this.windowRepeatCap = windowRepeatCap;
            this.allowsDamagedVariant = allowsDamagedVariant;
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
