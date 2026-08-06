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
    STEAM_SINGULARITY_COMPRESSOR(202, 5),

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
        .of(SINGULARITY_CRUST_STEAM_BORER, LARGE_SOLAR_OVERPRESSURE_ARRAY, STEAM_SINGULARITY_COMPRESSOR);
}
