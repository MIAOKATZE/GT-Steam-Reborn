package com.miaokatze.gtsr.common.api.enums;

import java.util.EnumSet;
import java.util.Set;

import com.miaokatze.gtsr.config.Config;

public enum MetaTileEntityID {

    // --- 单方块机器段 (相对 0-49) ---
    STEAM_CACHE_NODE(0, 1),
    REINFORCED_STEAM_CACHE_NODE(1, 2),
    OVERPRESSURE_STEAM_CACHE_NODE(2, 3),
    WATER_CACHE_NODE(3, 4),
    SINGULARITY_MINER_NODE(4, 28),
    SINGULARITY_DRILLING_NODE(5, 29),
    // 耐压/超压通用流体缓存节点：全新机器，OLD_ID 仅占用未用旧槽位避免覆盖 LEGACY_TO_NEW_MAP，
    // 无旧存档机器，不注册 LegacyConverter（同 REDSTONE_HATCH 模式）
    REINFORCED_WATER_CACHE_NODE(6, 46),
    OVERPRESSURE_WATER_CACHE_NODE(7, 47),
    // 奇点仓四件套（模式锁定的枢纽缓存仓）：全新机器，无旧存档机器，不注册 LegacyConverter。
    // 旧段 0-50 已无 4 连空位，OLD_ID 取段外 51-54：LEGACY_TO_NEW_MAP 静态块按 idx<51 越界跳过，
    // 不写入映射、不注册旧 ID，零副作用（比挤占 0/48/49 部分空位更不污染旧映射）
    SINGULARITY_STEAM_COMPARTMENT(8, 51),
    SINGULARITY_STEAM_OUTPUT_COMPARTMENT(9, 52),
    SINGULARITY_FLUID_INPUT_COMPARTMENT(10, 53),
    SINGULARITY_FLUID_OUTPUT_COMPARTMENT(11, 54),

    // --- 多方块机器: 枢纽段 (相对 50-99) ---
    STEAM_HUB_ARRAY(50, 6),
    WATER_HUB_ARRAY(51, 7),
    SINGULARITY_DRILLING_HUB(52, 27),

    // --- 多方块机器: 蒸汽基类段 (相对 100-149) ---
    STEAM_FLUID_DRILL(100, 19),
    CRUST_STEAM_BORER(101, 20),
    SINGULARITY_CRUST_STEAM_BORER(102, 21),
    VEIN_STEAM_PYROLYZER(103, 22),
    LARGE_STEAM_FURNACE(104, 23),
    AIR_COMPRESSOR(105, 24),
    ATMOSPHERIC_CENTRIFUGE(106, 25),
    LARGE_GEOTHERMAL_STEAM_BOILER(107, 16),
    LARGE_SOLAR_OVERPRESSURE_ARRAY(108, 15),

    // --- 多方块机器: 工作机器段 (相对 150-199) ---
    MEGA_STEAM_TURBINE_ARRAY(150, 17),
    KINETIC_PROCESSING_ARRAY(151, 26),
    LARGE_COKE_OVEN(152, 30),
    SIEMENS_MARTIN_FURNACE(153, 31),
    AMMONIA_PLANT(154, 32),
    GEAR_STEAM_COMPRESSOR(155, 33),
    REINFORCED_BRICK_BLAST_FURNACE(156, 50),

    // --- 多方块机器: 临界段 (相对 200-249) ---
    CRITICAL_SINGULARITY_COMPRESSOR(200, 43),
    DENSE_STATE_MANIPULATOR(201, 44),
    STEAM_SINGULARITY_ENTANGLER(202, 5),

    // --- 仓室段 (相对 250-350) ---
    STEAM_INPUT_HATCH_GENERIC(250, 34),
    STEAM_OUTPUT_HATCH_GENERIC(251, 35),
    PRESSURE_STEAM_HATCH(252, 36),
    PRESSURE_STEAM_OUTPUT_HATCH(253, 37),
    STEAM_OUTPUT_HATCH(254, 38),
    STEAM_COOLING_HATCH(255, 39),
    PRESSURE_STEAM_COOLING_HATCH(256, 40),
    // 巨型空气输入仓：仅允许空气/下界空气，容量 100,000,000 L
    MEGA_AIR_INPUT_HATCH(257, 41),
    // 蒸馏水仓：蓄水仓同性质，生成蒸馏水，容量 2,000,000,000 L
    DISTILLED_WATER_HATCH(258, 42),
    STEAM_HUB_INPUT_HATCH(259, 8),
    STEAM_HUB_OUTPUT_HATCH(260, 9),
    WATER_HUB_INPUT_HATCH(261, 10),
    WATER_HUB_OUTPUT_HATCH(262, 11),
    OVERPRESSURE_TURBINE_INPUT_HATCH(263, 18),
    HUB_STORAGE_UNIT(264, 12),
    REINFORCED_HUB_STORAGE_UNIT(265, 13),
    OVERPRESSURE_HUB_STORAGE_UNIT(266, 14),
    // 红石仓：任意多方块机器通用红石信号输出仓（新 ID，无旧机器转换需求；OLD_ID 用 45——
    // 43/44 已被临界奇点压缩机/稠态操纵器占用，避免覆盖 LEGACY_TO_NEW_MAP）
    REDSTONE_HATCH(267, 45),

    ;

    public final int ID;
    public final int OLD_ID;

    private static final int BASE_OLD = 14620;
    private static final int BASE = 14700;

    /**
     * 新段位规则（BASE = 14700）：
     * 单方块 0-49 / 枢纽 50-99 / 蒸汽基类 100-149 / 工作机器 150-199 / 临界 200-249 / 仓室 250-350。
     * 旧段相对 ID 仅用于旧 ID 占位注册（BASE_OLD = 14620 为迁移锚点），两者共享 Config.metaIdOffset。
     */
    MetaTileEntityID(int relative, int oldRelative) {
        this.ID = BASE + Config.metaIdOffset + relative;
        this.OLD_ID = BASE_OLD + Config.metaIdOffset + oldRelative;
    }

    /** 结构重置三机：旧 ID 注册 [OLD] 机器而非占位转换器（旧存档不转换，留一个大版本缓冲）。 */
    public static final Set<MetaTileEntityID> STRUCTURE_RESET = EnumSet
        .of(SINGULARITY_CRUST_STEAM_BORER, LARGE_SOLAR_OVERPRESSURE_ARRAY, STEAM_SINGULARITY_ENTANGLER);

    /**
     * 旧 ID → 新 ID 物品映射表（临时机制：供 Postea ItemStackReplacementManager 与后续迁移逻辑使用；
     * 下一大版本移除旧 ID 注册后，本表与 PosteaCompat 一并删除）。
     * index = 旧绝对ID - BASE_OLD，值 = 新绝对ID；0 表示无映射。结构重置三机（STRUCTURE_RESET）不映射。
     */
    public static final int[] LEGACY_TO_NEW_MAP = new int[51];

    static {
        for (MetaTileEntityID id : values()) {
            if (STRUCTURE_RESET.contains(id)) continue;
            int idx = id.OLD_ID - BASE_OLD;
            if (idx >= 0 && idx < LEGACY_TO_NEW_MAP.length) {
                LEGACY_TO_NEW_MAP[idx] = id.ID;
            }
        }
    }

    /** 查询旧绝对 ID 对应的新绝对 ID；无映射返回 -1。 */
    public static int getMappedId(int oldId) {
        int idx = oldId - BASE_OLD;
        if (idx < 0 || idx >= LEGACY_TO_NEW_MAP.length) return -1;
        int id = LEGACY_TO_NEW_MAP[idx];
        return id == 0 ? -1 : id;
    }
}
