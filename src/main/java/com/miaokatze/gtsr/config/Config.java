package com.miaokatze.gtsr.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * 模组配置管理类
 * 负责读取和保存模组的配置文件 (config/gtsr/gtsr.cfg)
 */
public class Config {

    // GregTech 元机器实体 (MTE) ID 分配的偏移量。
    // 由 MetaTileEntityID.java 统一管理（新段 BASE=14700）；v1.11.34 起旧 ID 段已退役，偏移量仅作用于现行 ID 分配。
    public static int metaIdOffset = 0;

    // 是否为 GTNL 蒸汽机基类启用 GTSR 增强（过热蒸汽 4 倍加速 + 冷却舱室支持）。
    // 默认关闭。开启后 GTNL 蒸汽机将获得 GTSR 的过热蒸汽 4 倍消耗 4 倍速加速机制，
    // 并能使用 GTSR 冷却舱室。mixin 方法体运行时判断此值（远晚于配置读取），时序安全。
    public static boolean gtnlEnhancement = false;

    // 自然生成奇点频率（区块×区块，平均 N×N 区块生成 1 个，默认 48 = 每 chunk 1/2304）。
    public static int singularitySpawnFrequency = 48;

    // 自然生成的奇点是否具有破坏方块的能力（默认开；关闭后自然奇点不吸收/破坏方块）。
    public static boolean singularityDestroyBlocks = true;

    // 破碎维度奇点频率倍增分母（破碎分支频率 = singularitySpawnFrequency/N，默认 8 = 等效 8 倍密度；S6b 消费）。
    public static int shatteredSingularityMultiplier = 8;

    // 繁荣维度（prosperity-ruins）维度 ID（默认 78）。
    // 与其他 mod 冲突时该维度自动禁用（解析记 -1），不回退到空闲 ID。
    public static int prosperityDimId = 78;

    // 繁荣维度 ProviderType ID（默认 178）。被占用时维度自动禁用（解析记 -1）。
    public static int prosperityProviderId = 178;

    // 破碎维度（shattered-lands）维度 ID（默认 79）。
    // 与其他 mod 冲突时该维度自动禁用（解析记 -1），不回退到空闲 ID。
    public static int shatteredDimId = 79;

    // 破碎维度 ProviderType ID（默认 179）。被占用时维度自动禁用（解析记 -1）。
    public static int shatteredProviderId = 179;

    // 繁荣维度群系 ID 段起始（默认 180 = 四名群系的**首选** id 起点；P1 起逐群系独立配槽，
    // 首选槽被外部 mod 占用时向后顺延，允许非连续结果，硬上界见 GTSRBiomeBase.HARD_ID_MAX）。
    public static int prosperityBiomeIdStart = 180;

    // 破碎维度群系 ID 段起始（默认 190，口径同 prosperityBiomeIdStart：首选起点 + 逐群系顺延）。
    public static int shatteredBiomeIdStart = 190;

    // 群系配槽扫描上界（P1 新增；默认 254 = byte 平面硬上界，见 GTSRBiomeBase.HARD_ID_MAX）。
    // 只能**收紧**不能放宽：实际扫描上界 = min(本值, HARD_ID_MAX, biomeList 实际表长 - 1)。
    // 用途——整合包若想给自家群系预留高位段，把它调小即可让我方群系提前判"无槽"并显式降级。
    public static int biomeIdScanLimit = 254;

    // 繁荣维度残缺机器生成频率分母（平均 1/N chunk × 群系机器权重；默认 16，0 = 禁用；S4a 消费，
    // S-A5 密度上调 24→16 = plan §12 修订 7）。
    public static int prosperityMachineChance = 16;

    // 繁荣维度城外中型废墟（outpost）生成频率分母（平均 1/N chunk 生成 1 座；默认 64，0 = 禁用；
    // S-A5 新增消费 = plan §12 修订 7/8，与残缺机器同 chunk 互斥掷骰：先 outpost，命中跳过机器）。
    public static int prosperityOutpostChance = 64;

    // 繁荣维度古代城存在概率（每个 24×24 chunk cell 的存在掷骰百分比；默认 45，0 = 无城，100 = 全 cell 有城；S4b 消费）。
    public static int prosperityCityChance = 45;

    /**
     * planDimension 总开关持有者（plan 维度组键 planDimension.*，见 synchronizeConfiguration）。
     * 关闭后对应维度完全不注册（DimensionRegistrar 冲突检测第一道闸）。
     */
    public static final PlanDimensionHolder planDimension = new PlanDimensionHolder();

    public static final class PlanDimensionHolder {

        // 繁荣蒸汽时代遗迹维度总开关。默认开。关闭后维度不注册，相关生成与指令一并禁用。
        public boolean prosperityDimension = true;

        // 维度破碎之地维度总开关。默认开。关闭后维度不注册，相关生成与指令一并禁用。
        public boolean shatteredDimension = true;
    }

    /**
     * 同步配置文件
     * 从磁盘读取配置并更新静态变量，如果配置有变动则自动保存
     * 
     * @param configFile 配置文件对象
     */
    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        metaIdOffset = configuration.getInt(
            "metaIdOffset",
            Configuration.CATEGORY_GENERAL,
            metaIdOffset,
            -5000,
            5000,
            "应用于 MTE ID 基准值的偏移量 (用于预留 ID 区间)");

        gtnlEnhancement = configuration.getBoolean(
            "gtnlEnhancement",
            Configuration.CATEGORY_GENERAL,
            false,
            "是否为GTNL蒸汽机基类启用GTSR增强（过热蒸汽4倍加速+冷却舱室支持）。默认关闭。开启后GTNL蒸汽机将获得GTSR的过热蒸汽4倍消耗4倍速加速机制，并能使用GTSR冷却舱室。");

        singularitySpawnFrequency = configuration.getInt(
            "singularitySpawnFrequency",
            Configuration.CATEGORY_GENERAL,
            singularitySpawnFrequency,
            1,
            10000,
            "自然生成奇点频率（区块×区块，平均 N×N 区块生成 1 个，默认 48 = 每 chunk 1/2304）");

        singularityDestroyBlocks = configuration.getBoolean(
            "singularityDestroyBlocks",
            Configuration.CATEGORY_GENERAL,
            true,
            "自然生成的奇点是否具有破坏方块的能力（默认开；关闭后自然奇点不吸收/破坏方块）");

        shatteredSingularityMultiplier = configuration.getInt(
            "shatteredSingularityMultiplier",
            Configuration.CATEGORY_GENERAL,
            shatteredSingularityMultiplier,
            1,
            64,
            "破碎维度奇点频率倍增分母（破碎分支频率 = singularitySpawnFrequency/N，默认 8 = 等效 8 倍密度；范围 1-64）");

        planDimension.prosperityDimension = configuration.getBoolean(
            "planDimension.prosperityDimension",
            Configuration.CATEGORY_GENERAL,
            true,
            "繁荣蒸汽时代遗迹维度（prosperity-ruins）总开关。默认开。关闭后维度不注册，相关生成与指令一并禁用。");

        planDimension.shatteredDimension = configuration.getBoolean(
            "planDimension.shatteredDimension",
            Configuration.CATEGORY_GENERAL,
            true,
            "维度破碎之地（shattered-lands）总开关。默认开。关闭后维度不注册，相关生成与指令一并禁用。");

        prosperityDimId = configuration.getInt(
            "prosperityDimId",
            Configuration.CATEGORY_GENERAL,
            prosperityDimId,
            0,
            1000,
            "繁荣维度 ID（默认 78）。与其他 mod 冲突时维度自动禁用（记 -1），不回退到空闲 ID");

        prosperityProviderId = configuration.getInt(
            "prosperityProviderId",
            Configuration.CATEGORY_GENERAL,
            prosperityProviderId,
            0,
            1000,
            "繁荣维度 ProviderType ID（默认 178）。被占用时维度自动禁用（记 -1）");

        shatteredDimId = configuration.getInt(
            "shatteredDimId",
            Configuration.CATEGORY_GENERAL,
            shatteredDimId,
            0,
            1000,
            "破碎维度 ID（默认 79）。与其他 mod 冲突时维度自动禁用（记 -1），不回退到空闲 ID");

        shatteredProviderId = configuration.getInt(
            "shatteredProviderId",
            Configuration.CATEGORY_GENERAL,
            shatteredProviderId,
            0,
            1000,
            "破碎维度 ProviderType ID（默认 179）。被占用时维度自动禁用（记 -1）");

        prosperityBiomeIdStart = configuration.getInt(
            "prosperityBiomeIdStart",
            Configuration.CATEGORY_GENERAL,
            prosperityBiomeIdStart,
            0,
            GTSRBiomeBase.HARD_ID_MAX,
            "繁荣维度群系 ID 首选起点（默认 180）。P1 起逐群系独立配槽：首选槽被占则向后顺延，允许非连续；"
                + "可用上界 " + GTSRBiomeBase.HARD_ID_MAX + "（byte 平面 + 255 懒回填哨兵，见 GTSRBiomeBase.HARD_ID_MAX）");

        shatteredBiomeIdStart = configuration.getInt(
            "shatteredBiomeIdStart",
            Configuration.CATEGORY_GENERAL,
            shatteredBiomeIdStart,
            0,
            GTSRBiomeBase.HARD_ID_MAX,
            "破碎维度群系 ID 首选起点（默认 190）。配槽口径同 prosperityBiomeIdStart（首选 + 顺延，允许非连续）；"
                + "可用上界 " + GTSRBiomeBase.HARD_ID_MAX);

        biomeIdScanLimit = configuration.getInt(
            "biomeIdScanLimit",
            Configuration.CATEGORY_GENERAL,
            biomeIdScanLimit,
            1,
            GTSRBiomeBase.HARD_ID_MAX,
            "群系配槽扫描上界（默认 " + GTSRBiomeBase.HARD_ID_MAX
                + " = byte 平面硬上界，只能收紧不能放宽）。整合包要预留高位段时调小它，"
                + "我方群系会提前判无槽并在日志显式降级 degraded=SHORT/EMPTY");

        prosperityMachineChance = configuration.getInt(
            "prosperityMachineChance",
            Configuration.CATEGORY_GENERAL,
            prosperityMachineChance,
            0,
            1000,
            "繁荣维度残缺机器生成频率分母（平均 1/N 区块 × 群系机器权重，默认 16 = 约 4-8% 每区块；0 = 禁用）");

        prosperityOutpostChance = configuration.getInt(
            "prosperityOutpostChance",
            Configuration.CATEGORY_GENERAL,
            prosperityOutpostChance,
            0,
            1000,
            "繁荣维度城外中型废墟（outpost）生成频率分母（平均 1/N 区块生成 1 座，默认 64；0 = 禁用；与残缺机器同区块互斥：outpost 命中则跳过机器）");

        prosperityCityChance = configuration.getInt(
            "prosperityCityChance",
            Configuration.CATEGORY_GENERAL,
            prosperityCityChance,
            0,
            100,
            "繁荣维度古代城存在概率（每个 24×24 区块 cell 的存在掷骰百分比，默认 45 = 平均每 500-600 格一座；0 = 无城）");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
