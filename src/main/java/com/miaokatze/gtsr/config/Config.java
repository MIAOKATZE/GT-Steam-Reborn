package com.miaokatze.gtsr.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * 模组配置管理类
 * 负责读取和保存模组的配置文件 (config/gtsr/gtsr.cfg)
 */
public class Config {

    // GregTech 元机器实体 (MTE) ID 分配的偏移量。
    // 注意：旧段 BASE=14620（OLD 锚点）与新段 BASE=14700 由 MetaTileEntityID.java 统一管理；metaIdOffset 同时作用于两段。
    public static int metaIdOffset = 0;

    // 是否为 GTNL 蒸汽机基类启用 GTSR 增强（过热蒸汽 4 倍加速 + 冷却舱室支持）。
    // 默认关闭。开启后 GTNL 蒸汽机将获得 GTSR 的过热蒸汽 4 倍消耗 4 倍速加速机制，
    // 并能使用 GTSR 冷却舱室。mixin 方法体运行时判断此值（远晚于配置读取），时序安全。
    public static boolean gtnlEnhancement = false;

    // 自然生成奇点频率（区块×区块，平均 N×N 区块生成 1 个，默认 48 = 每 chunk 1/2304）。
    public static int singularitySpawnFrequency = 48;

    // 自然生成的奇点是否具有破坏方块的能力（默认开；关闭后自然奇点不吸收/破坏方块）。
    public static boolean singularityDestroyBlocks = true;

    // 集群总控 [GTSR-JQ] 调试明细日志开关（默认关）。开启后输出结构扫描统计/模块发现断开/
    // 批执行与流体扣量摘要/增幅有效性摘要（均为边沿或每 20t 至多一条）；关闭时仅保留
    // 附录 C 的 INFO/WARN 边沿事件（开始预热/满热/供给翻转/成型破坏等）。
    public static boolean logisticsClusterDebug = false;

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
            singularityDestroyBlocks,
            "自然生成的奇点是否具有破坏方块的能力（默认开；关闭后自然奇点不吸收/破坏方块）");

        logisticsClusterDebug = configuration.getBoolean(
            "logisticsClusterDebug",
            Configuration.CATEGORY_GENERAL,
            false,
            "集群总控[GTSR-JQ]调试明细日志（结构扫描统计/模块发现断开/批执行与流体扣量摘要/增幅有效性摘要，边沿或每20t至多一条）。默认关。");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
