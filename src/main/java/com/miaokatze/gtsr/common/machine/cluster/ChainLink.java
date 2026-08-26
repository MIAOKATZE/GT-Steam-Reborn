package com.miaokatze.gtsr.common.machine.cluster;

import java.util.Locale;

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
 * {@link ClusterParams#LOGISTICS_LINK_BASE_TICKS}（8s=160t），无蒸汽消耗。
 * <p>
 * SIMPLE_WASH（简易洗矿）依赖 GT++ 的 simpleWasherRecipes 配方图；GTSR 对 GT++ 无编译期依赖，
 * 故经懒反射探测（见 {@link #getRecipeMap()} 与 {@link #isSimpleWashAvailable()}），
 * GT++ 缺失时一次性探测失败即永久视为不可用，不抛错、不重试。
 */
public enum ChainLink {

    /** 破碎：矿石粗碎为碎矿。 */
    CRUSH(480, 2000),

    /** 锻造：矿物锤锻成形。 */
    HAMMER(160, 8000),

    /** 简易洗矿（GT++）：轻量水洗；依赖 GT++ simpleWasherRecipes，GT++ 缺失时该链步不可用。 */
    SIMPLE_WASH(160, 200),

    /** 矿石清洗：洗去碎矿表面杂质。 */
    ORE_WASH(640, 200),

    /** 化学浸浴：用药剂分离矿物表面附着物。 */
    CHEM_BATH(960, 1000),

    /** 离心：按密度分离粉碎产物。 */
    CENTRIFUGE(640, 3000),

    /** 热离心：加热条件下进一步分离矿物；需要能源仓持续供电。 */
    THERMOCENTRIFUGE(480, 1000),

    /** 筛分：从粉碎产物中筛出稀有副产物。 */
    SIFTER(2560, 1000),

    /** 磁力分离：以磁场提取含铁组分；需要能源仓持续供电。 */
    MAGNETIC_SEPARATOR(160, 100),

    /** 熔炼：将处理后的矿物熔为锭。 */
    FURNACE(160, 2000);

    /** tick 与秒换算基准（20t = 1s）。 */
    public static final int TICKS_PER_SECOND = 20;

    /** GT++ 简易洗矿配方图的懒缓存：探测成功前为 {@code null}，失败后永久为 {@code null}。 */
    private static RecipeMap<?> simpleWasherMap;

    /** GT++ 简易洗矿配方图是否已完成一次性探测（无论成败只探测一次，不重试）。 */
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
                return MTEUnitFurnace.class;
            default:
                throw new AssertionError("未处理的链步: " + this);
        }
    }

    /**
     * @return 该链步绑定的 GT 配方图；{@code SIMPLE_WASH} 经懒反射探测 GT++，缺失时返回 {@code null}
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
                return RecipeMaps.furnaceRecipes;
            default:
                throw new AssertionError("未处理的链步: " + this);
        }
    }

    /**
     * @return GT++ 简易洗矿链步当前是否可用（触发一次性探测并返回缓存值非 {@code null}）
     */
    public static boolean isSimpleWashAvailable() {
        return getSimpleWasherMap() != null;
    }

    /**
     * 探测并返回 GT++ 的简易洗矿机配方图。
     * <p>
     * 首次调用时反射读取 {@code gtPlusPlus.api.recipe.GTPPRecipeMaps} 的 static 字段
     * {@code simpleWasherRecipes}，值为 {@code gregtech.api.recipe.RecipeMap} 实例则缓存；
     * 任何 {@link Throwable}（类缺失、字段缺失、类型不符等）捕获后缓存 {@code null}（永久），不崩、不重试。
     *
     * @return GT++ 简易洗矿配方图；GT++ 不存在或字段不可用时为 {@code null}
     */
    private static RecipeMap<?> getSimpleWasherMap() {
        if (!simpleWasherProbed) {
            simpleWasherProbed = true;
            try {
                Class<?> gtppRecipeMaps = Class.forName("gtPlusPlus.api.recipe.GTPPRecipeMaps");
                Object candidate = gtppRecipeMaps.getField("simpleWasherRecipes")
                    .get(null);
                if (candidate instanceof RecipeMap<?>) {
                    simpleWasherMap = (RecipeMap<?>) candidate;
                }
            } catch (Throwable ignored) {
                // GT++ 缺失或字段不可用：simpleWasherMap 保持 null（永久），不崩、不重试。
            }
        }
        return simpleWasherMap;
    }
}
