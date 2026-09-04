package com.miaokatze.gtsr.common.api.enums;

import com.miaokatze.gtsr.config.Config;

public enum MetaTileEntityID {

    // --- 单方块机器段（相对 0-49；0-3 已腾挪预留） ---
    STEAM_CACHE_NODE(6),
    REINFORCED_STEAM_CACHE_NODE(7),
    OVERPRESSURE_STEAM_CACHE_NODE(8),
    WATER_CACHE_NODE(9),
    // 0-3 预留；矿工/钻井节点位置保持不动
    SINGULARITY_MINER_NODE(4),
    SINGULARITY_DRILLING_NODE(5),
    REINFORCED_WATER_CACHE_NODE(10),
    OVERPRESSURE_WATER_CACHE_NODE(11),
    // 12-15 预留；奇点仓四件套使用新位置
    SINGULARITY_STEAM_COMPARTMENT(16),
    SINGULARITY_STEAM_OUTPUT_COMPARTMENT(17),
    SINGULARITY_FLUID_INPUT_COMPARTMENT(18),
    SINGULARITY_FLUID_OUTPUT_COMPARTMENT(19),

    // --- 多方块机器: 枢纽段 (相对 50-99) ---
    STEAM_HUB_ARRAY(50),
    WATER_HUB_ARRAY(51),
    SINGULARITY_DRILLING_HUB(52),

    // --- 多方块机器: 蒸汽基类段 (相对 100-149) ---
    STEAM_FLUID_DRILL(100),
    CRUST_STEAM_BORER(101),
    SINGULARITY_CRUST_STEAM_BORER(102),
    VEIN_STEAM_PYROLYZER(103),
    LARGE_STEAM_FURNACE(104),
    AIR_COMPRESSOR(105),
    ATMOSPHERIC_CENTRIFUGE(106),
    LARGE_GEOTHERMAL_STEAM_BOILER(107),
    LARGE_SOLAR_OVERPRESSURE_ARRAY(108),

    // --- 多方块机器: 工作机器段 (相对 150-199) ---
    MEGA_STEAM_TURBINE_ARRAY(150),
    KINETIC_PROCESSING_ARRAY(151),
    LARGE_COKE_OVEN(152),
    SIEMENS_MARTIN_FURNACE(153),
    AMMONIA_PLANT(154),
    GEAR_STEAM_COMPRESSOR(155),
    REINFORCED_BRICK_BLAST_FURNACE(156),

    // --- 多方块机器: 临界段 (相对 200-249) ---
    CRITICAL_SINGULARITY_COMPRESSOR(200),
    DENSE_STATE_MANIPULATOR(201),
    STEAM_SINGULARITY_ENTANGLER(202),

    // --- 仓室段 (相对 250-350) ---
    STEAM_INPUT_HATCH_GENERIC(250),
    STEAM_OUTPUT_HATCH_GENERIC(251),
    PRESSURE_STEAM_HATCH(252),
    PRESSURE_STEAM_OUTPUT_HATCH(253),
    STEAM_OUTPUT_HATCH(254),
    STEAM_COOLING_HATCH(255),
    PRESSURE_STEAM_COOLING_HATCH(256),
    // 巨型空气输入仓：仅允许空气/下界空气，容量 100,000,000 L
    MEGA_AIR_INPUT_HATCH(257),
    // 蒸馏水仓：蓄水仓同性质，生成蒸馏水，容量 2,000,000,000 L
    DISTILLED_WATER_HATCH(258),
    STEAM_HUB_INPUT_HATCH(259),
    STEAM_HUB_OUTPUT_HATCH(260),
    WATER_HUB_INPUT_HATCH(261),
    WATER_HUB_OUTPUT_HATCH(262),
    OVERPRESSURE_TURBINE_INPUT_HATCH(263),
    HUB_STORAGE_UNIT(264),
    REINFORCED_HUB_STORAGE_UNIT(265),
    OVERPRESSURE_HUB_STORAGE_UNIT(266),
    // 红石仓：任意多方块机器通用红石信号输出仓（全新 ID，无旧存档机器）
    REDSTONE_HATCH(267),

    // --- 集群段 (相对 351-365；蒸汽动力矿物处理物流工程集群：总控1+工作7+增幅5+物流1) ---
    // 全新 ID 段，无旧存档机器。
    CLUSTER_CONTROLLER(351),
    CLUSTER_UNIT_CRUSHER(352),
    CLUSTER_UNIT_ORE_WASHER(353),
    CLUSTER_UNIT_CENTRIFUGE(354),
    CLUSTER_UNIT_THERMOCENTRIFUGE(355),
    CLUSTER_UNIT_SIFTER(356),
    CLUSTER_UNIT_MAGNETIC_SEPARATOR(357),
    CLUSTER_UNIT_FURNACE(358),
    CLUSTER_BOOSTER_PARALLEL(359),
    CLUSTER_BOOSTER_SPEED(360),
    CLUSTER_BOOSTER_PRIMARY(361),
    CLUSTER_BOOSTER_SECONDARY(362),
    CLUSTER_BOOSTER_STEAM_SAVER(363),
    CLUSTER_UNIT_LOGISTICS(364),

    ;

    public final int ID;

    private static final int BASE_OLD = 14620;
    private static final int BASE = 14700;

    /**
     * 新段位规则（BASE = 14700）：
     * 单方块 0-49 / 枢纽 50-99 / 蒸汽基类 100-149 / 工作机器 150-199 / 临界 200-249 / 仓室 250-350。
     * 集群段 351-365：蒸汽动力矿物处理物流工程集群（总控 1 + 工作 7 + 增幅 5 + 物流 1，现用 351-364，365 预留）。
     */
    MetaTileEntityID(int relative) {
        this.ID = BASE + Config.metaIdOffset + relative;
    }

    /**
     * 旧 ID → 新 ID 物品映射骨架（历史锚点）：
     * v1.11.34 之前，本表承载"旧 ID 物品 → 新 ID"映射（旧段基准 14620），供占位转换器、
     * [OLD] 实机注册、Postea 物品迁移与工作台转换配方等迁移机制查询。
     * v1.11.34 起上述旧 ID 映射机制已整体移除，两表清空，仅保留数组结构与历史锚点
     * （旧段基准 14620），getMappedId 查询契约维持不变（空表恒返回 -1）。
     * 历史明细见 plan/sum/15_MetaV2迁移_sum.md 与 plan/sum/39_机器meta号段整理_sum.md。
     */
    public static final int[] LEGACY_TO_NEW_MAP = new int[51];
    public static final int[] PUBLISHED_REMAP = new int[4];

    /** 查询旧绝对 ID（含已发布段及迁移段）对应的新绝对 ID；无映射返回 -1。 */
    public static int getMappedId(int oldId) {
        int publishedIdx = oldId - BASE;
        if (publishedIdx >= 0 && publishedIdx < PUBLISHED_REMAP.length) {
            int publishedId = PUBLISHED_REMAP[publishedIdx];
            if (publishedId != 0) return publishedId;
        }
        int idx = oldId - BASE_OLD;
        if (idx < 0 || idx >= LEGACY_TO_NEW_MAP.length) return -1;
        int id = LEGACY_TO_NEW_MAP[idx];
        return id == 0 ? -1 : id;
    }
}
