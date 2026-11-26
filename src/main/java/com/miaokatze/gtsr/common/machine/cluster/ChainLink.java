// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.cluster;

import java.util.Locale;

import com.miaokatze.gtsr.api.compat.GTVersionCompat;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;

/**
 * 矿石链式加工集群的链步定义枚举。
 * <p>
 * 每个链步绑定一段 GT 配方图、一种所需的集群工作单元类型与基础耗时/基础蒸汽消耗（r6 基础表）。
 * 内部时间基准统一为 tick（20t = 1s），避免亚秒级基础时间被秒制 int 截断；对外提供
 * {@link #getBaseSeconds()} 秒视图保持既有调用方兼容。工作单元类（MTEUnitXxx）由其他切片在本包内
 * 实现，此处仅做引用，不承担其构建逻辑。
 * <p>
 * 物流链步不设本枚举项（物流由总控物流单元直接承担）：其基础耗时见
 * {@link ClusterParams#LOGISTICS_TIME_SEC}，无蒸汽消耗。
 * <p>
 * SIMPLE_WASH（简易洗矿）使用 GTNH 2.9.0–2.9.2 共有的 RecipeMaps.simpleWasherRecipes，
 * 经现有静态门面取得；不探测独立 GT++ 类，也不把 API 链接失败转成“无配方”透传。
 */
public enum ChainLink {

    /** 破碎：矿石粗碎为碎矿。 */
    CRUSH(120, 2000),

    /** 锻造：矿物锤锻成形。 */
    HAMMER(4, 8000),

    /** 简易洗矿：轻量水洗，使用 GT5U 内置的 simpleWasherRecipes 配方图。 */
    SIMPLE_WASH(4, 200),

    /** 矿石清洗：洗去碎矿表面杂质。 */
    ORE_WASH(160, 200),

    /** 化学浸浴：用药剂分离矿物表面附着物。 */
    CHEM_BATH(240, 1000),

    /** 离心：按密度分离粉碎产物。 */
    CENTRIFUGE(160, 3000),

    /** 热离心：加热条件下进一步分离矿物；需要能源仓持续供电。 */
    THERMOCENTRIFUGE(120, 1000),

    /** 筛分：从粉碎产物中筛出稀有副产物。 */
    SIFTER(640, 1000),

    /** 磁力分离：以磁场提取含铁组分；需要能源仓持续供电。 */
    MAGNETIC_SEPARATOR(40, 100),

    /** 熔炼：将处理后的矿物熔为锭。 */
    FURNACE(40, 2000),

    /**
     * 熔炼（仅主产）：物品级动态主线（决策 b/b'，R9）——批开始沿链前缀逐步查配方推导「主线产物物品
     * 集合」（每步取配方槽 0 主输出链），集合内物品能熔就熔（含粉尘形态主产），集合外副产不熔、
     * 透传随批排出。只许枚举尾追（ordinal 序列化硬约束：插中段=旧存档静默错位）。
     */
    FURNACE_PRIMARY(40, 2000);

    /** tick 与秒换算基准（20t = 1s）。 */
    public static final int TICKS_PER_SECOND = 20;

    /** 简易洗矿配方图的首次读取缓存：读取前为 {@code null}，读取时直接引用公开字段。 */
    private static RecipeMap<?> simpleWasherMap;

    /** 保留既有首次读取标记；此处没有反射探测或异常回退。 */
    private static boolean simpleWasherProbed;

    /** 该链步无加速时的基础耗时（tick，20t = 1s）。 */
    private final int baseTicks;

    /** 该链步的基础蒸汽消耗（L/s，等效普通 Steam 口径）。 */
    private final int baseSteamLps;

    ChainLink(int baseTicks, int baseSteamLps) {
        this.baseTicks = baseTicks;
        this.baseSteamLps = baseSteamLps;
    }

    /**
     * @return 该链步无加速时的基础耗时（tick）
     */
    public int getBaseTicks() {
        return baseTicks;
    }

    /**
     * @return 该链步无加速时的基础耗时（秒，tick ÷ 20 向下取整的兼容视图）
     */
    public int getBaseSeconds() {
        return baseTicks / TICKS_PER_SECOND;
    }

    /**
     * @return 该链步无加速时的基础耗时（秒，tick ÷ 20 的 double 精确值——亚秒基础时间如 16t=0.8s
     *         不再被 int 除法截断；GUI 显示口径用，执行计时仍以 {@link #getBaseTicks()} 为准）
     */
    public double getBaseSecondsPrecise() {
        return baseTicks / (double) TICKS_PER_SECOND;
    }

    /**
     * @return 该链步的基础蒸汽消耗（L/s）；物流链步无蒸汽，不经本枚举表达
     */
    public int getBaseSteamLps() {
        return baseSteamLps;
    }

    /**
     * @return 本地化键，形如 {@code gtsr.gui.cluster.link.crush}（枚举名转小写）
     */
    public String getLangKey() {
        return "gtsr.gui.cluster.link." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * @return 是否需要能源仓持续供电；仅 {@code MAGNETIC_SEPARATOR} 与 {@code THERMOCENTRIFUGE}
     *         为 {@code true}（用户拍板：两者均需能源仓持续供电）
     */
    public boolean requiresContinuousPower() {
        return this == MAGNETIC_SEPARATOR || this == THERMOCENTRIFUGE;
    }

    /**
     * @return 该链步所需的集群工作单元类（同包，由其他切片实现，此处仅引用）
     */
    public Class<? extends MTEClusterUnitBase> getRequiredUnitClass() {
        switch (this) {
            case CRUSH:
            case HAMMER:
                return MTEUnitCrusher.class;
            case SIMPLE_WASH:
            case ORE_WASH:
            case CHEM_BATH:
                return MTEUnitOreWasher.class;
            case CENTRIFUGE:
                return MTEUnitCentrifuge.class;
            case THERMOCENTRIFUGE:
                return MTEUnitThermalCentrifuge.class;
            case SIFTER:
                return MTEUnitSifter.class;
            case MAGNETIC_SEPARATOR:
                return MTEUnitMagneticSeparator.class;
            case FURNACE:
            case FURNACE_PRIMARY:
                return MTEUnitFurnace.class;
            default:
                throw new AssertionError("未处理的链步: " + this);
        }
    }

    /**
     * @return 该链步绑定的 GT 配方图；{@code SIMPLE_WASH} 同样使用三版共有的公开配方图
     */
    public RecipeMap<?> getRecipeMap() {
        switch (this) {
            case CRUSH:
                return RecipeMaps.maceratorRecipes;
            case HAMMER:
                return RecipeMaps.hammerRecipes;
            case SIMPLE_WASH:
                return getSimpleWasherMap();
            case ORE_WASH:
                return RecipeMaps.oreWasherRecipes;
            case CHEM_BATH:
                return RecipeMaps.chemicalBathRecipes;
            case CENTRIFUGE:
                return RecipeMaps.centrifugeRecipes;
            case THERMOCENTRIFUGE:
                return RecipeMaps.thermalCentrifugeRecipes;
            case SIFTER:
                return RecipeMaps.sifterRecipes;
            case MAGNETIC_SEPARATOR:
                return RecipeMaps.electroMagneticSeparatorRecipes;
            case FURNACE:
            case FURNACE_PRIMARY:
                return RecipeMaps.furnaceRecipes;
            default:
                throw new AssertionError("未处理的链步: " + this);
        }
    }

    /**
     * 经 {@link GTVersionCompat#gppRecipeMap(String)} 直接读取三版共有的 simpleWasherRecipes。
     * 保留既有首次读取缓存；它不是反射探测，也不吞异常或为不支持的 API 提供 null 回退。
     */
    private static RecipeMap<?> getSimpleWasherMap() {
        if (!simpleWasherProbed) {
            simpleWasherProbed = true;
            simpleWasherMap = GTVersionCompat.gppRecipeMap("simpleWasherRecipes");
        }
        return simpleWasherMap;
    }
}
