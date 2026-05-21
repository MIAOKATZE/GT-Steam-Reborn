package com.miaokatze.gtsr.common.api.enums;

import com.miaokatze.gtsr.config.Config;

/**
 * 元机器实体 (MTE) ID 枚举
 * <p>
 * 模仿 NH-Utilities 的风格，为每个机器分配全局唯一的整数 ID。
 * 这种集中管理的方式可以有效避免与其他模组的机器 ID 发生冲突。
 * <p>
 * 最终 ID 计算公式：BASE (14600) + Config.metaIdOffset (配置偏移量) + relativeId (枚举内相对 ID)
 */
public enum MetaTileEntityID {

    // --- 单方块测试机器 (已禁用) ---
    // /** EV 等级测试发电机 (Tier 4) */
    // MTETEST_EV(0),
    // /** IV 等级测试发电机 (Tier 5) */
    // MTETEST_IV(1),
    // /** LuV 等级测试发电机 (Tier 6) */
    // MTETEST_LuV(2),

    // --- 多方块测试机器 (已禁用) ---
    // /** HV 等级测试多方块机器 (Tier 5) */
    // MTETEST_MULTIBLOCK_HV(10),

    // --- 基础单方块机器 (1+) ---
    /** 蒸汽缓存节点 (Tier 3 / HV) */
    STEAM_CACHE_NODE(1),
    /** 加固蒸汽缓存节点 (Tier 3 / HV) */
    REINFORCED_STEAM_CACHE_NODE(2),
    /** 蒸汽枢纽输出仓 */
    STEAM_HUB_OUTPUT_HATCH(3),
    /** 蒸汽枢纽输入仓 */
    STEAM_HUB_INPUT_HATCH(4),
    /** 枢纽存储单元 */
    HUB_STORAGE_UNIT(5),
    /** 加固枢纽存储单元 */
    REINFORCED_HUB_STORAGE_UNIT(6),

    // --- 多方块机器 (7+) ---
    /** 蒸汽枢纽阵列 */
    STEAM_HUB_ARRAY(7),
    /** 蒸汽流体钻井 */
    STEAM_FLUID_DRILL(8),
    /** 地壳蒸汽掘进机 */
    CRUST_STEAM_BORER(9),
    /** 奇点地壳蒸汽掘进机 */
    SINGULARITY_CRUST_STEAM_BORER(10),
    /** 耐压蒸汽仓 */
    PRESSURE_STEAM_HATCH(11),
    /** 地脉蒸汽热解机 */
    VEIN_STEAM_PYROLYZER(12),
    /** 大型蒸汽熔炉 */
    LARGE_STEAM_FURNACE(13),
    /** 蒸汽输出仓 */
    STEAM_OUTPUT_HATCH(14),
    /** 耐压蒸汽输出仓 */
    PRESSURE_STEAM_OUTPUT_HATCH(15),
    /** 大型太阳能超压阵列 (三等级自动检测) */
    LARGE_SOLAR_OVERPRESSURE_ARRAY(16),
    /** 大型地热蒸汽锅炉 (青铜/钢双等级) */
    LARGE_GEOTHERMAL_STEAM_BOILER(17),
    /** 蒸汽冷却仓 */
    STEAM_COOLING_HATCH(18),
    /** 耐压蒸汽冷却仓 */
    PRESSURE_STEAM_COOLING_HATCH(19),
    /** 蒸汽奇点压缩机 */
    STEAM_SINGULARITY_COMPRESSOR(20),
    /** 蓄水枢纽阵列 */
    WATER_HUB_ARRAY(21),
    /** 蓄水枢纽输出仓 */
    WATER_HUB_OUTPUT_HATCH(22),
    /** 蓄水枢纽输入仓 */
    WATER_HUB_INPUT_HATCH(23),
    /** 水缓存节点 */
    WATER_CACHE_NODE(24),
    /** 奇点钻井枢纽 */
    SINGULARITY_DRILLING_HUB(25),
    /** 奇点矿机节点 */
    SINGULARITY_MINER_NODE(26),
    /** 奇点钻井节点 */
    SINGULARITY_DRILLING_NODE(27),
    /** 蒸汽输入总线 (独立单方块，4+1槽) */
    STEAM_INPUT_BUS(28),
    /** 蒸汽输出总线 (独立单方块，4槽) */
    STEAM_OUTPUT_BUS(29),
    /** 大型焦炉 */
    LARGE_COKE_OVEN(30),
    /** 平炉（Siemens-Martin Furnace） */
    SIEMENS_MARTIN_FURNACE(31),
    /** 蒸汽输入仓 */
    STEAM_INPUT_HATCH(32),
    /** 输出仓(蒸汽) - 通用流体输出仓 */
    STEAM_OUTPUT_HATCH_GENERIC(33),
    /** 巨型蒸汽轮机机组 */
    MEGA_STEAM_TURBINE_ARRAY(34),
    /** 动力加工阵列 */
    KINETIC_PROCESSING_ARRAY(35),
    /** 制氨工厂 */
    AMMONIA_PLANT(36),
    /** 空气压缩机 */
    AIR_COMPRESSOR(37),
    /** 空气离心机 */
    ATMOSPHERIC_CENTRIFUGE(38),

    ;

    // 最终计算出的全局唯一 ID
    public final int ID;

    // ID 基准值，用于避免与其他模组的机器 ID 冲突
    private static final int BASE = 14620;

    /**
     * 构造函数：根据相对 ID 计算全局 ID
     * 
     * @param relativeId 枚举项在列表中的相对索引
     */
    MetaTileEntityID(int relativeId) {
        this.ID = BASE + Config.metaIdOffset + relativeId;
    }
}
