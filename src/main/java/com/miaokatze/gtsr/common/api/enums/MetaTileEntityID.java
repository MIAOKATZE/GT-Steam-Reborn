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
    /** 耐压蒸汽存储单元 */
    PRESSURE_STEAM_STORAGE_UNIT(5),
    /** 加固蒸汽存储单元 */
    REINFORCED_STEAM_STORAGE_UNIT(6),

    // --- 多方块机器 (7+) ---
    /** 蒸汽枢纽阵列 */
    STEAM_HUB_ARRAY(7),
    /** 蒸汽流体钻井 */
    STEAM_FLUID_DRILL(8),
    /** 地壳蒸汽掘进机 */
    CRUST_STEAM_BORER(9),
    /** 虚空地壳蒸汽掘进机 */
    VOID_CRUST_STEAM_BORER(10),
    /** 耐压蒸汽仓 */
    PRESSURE_STEAM_HATCH(11),
    /** 地脉蒸汽热解机 */
    VEIN_STEAM_PYROLYZER(12),
    /** 大型蒸汽熔炉 */
    LARGE_STEAM_FURNACE(13),

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
