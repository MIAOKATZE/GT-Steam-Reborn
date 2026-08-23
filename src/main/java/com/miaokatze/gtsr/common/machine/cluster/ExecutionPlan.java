package com.miaokatze.gtsr.common.machine.cluster;

import java.util.List;

/**
 * 集群全公式计算器（plan-prompt §6.1 逐字实现）：单物品耗时 / 有效并行 / 本链吞吐 / 本链蒸汽 / 集群总蒸汽。
 * <p>
 * 纯静态纯函数、零状态零副作用：GUI 公式演算折叠区与服务器运行期结算共用同一份代码，
 * 保证「GUI 推演 = 服务器执行」的一致性口径（§7 数据流约定）。全部数值常量取自 {@link ClusterParams}，
 * 本类零散硬编码禁止。
 * <p>
 * 增幅聚合入参 {@link BoosterState}（同包，增幅切片提供）只消费其聚合口径，本类不感知单个模块；
 * {@code null} 实例一律按零增益处理（速度 0 / 并行 0 / 节汽 0 / 惩罚乘积 1）。期望的聚合 API：
 * <ul>
 * <li>{@code double getSpeedBonus()} —— Σ速度增幅，小数口径（0.05 = +5%）；</li>
 * <li>{@code int getParallelBonus()} —— Σ并行增幅（点数，仅生效增幅模块）；</li>
 * <li>{@code double getSteamSaverBonus()} —— Σ节汽，小数口径（封顶在本式 min 处理）；</li>
 * <li>{@code double getPenaltyProduct()} —— 分类型惩罚乘子连乘积（缺流体模块增益与惩罚同步不计）。</li>
 * </ul>
 * 数值自查（与 §6.1 原式及 §9-M5 抽查表逐项一致）：
 * <ol>
 * <li>T1 单链 3 link（粉碎 8 + 锻造锤 2 + 简易洗 6）无增幅、每类 1 模块：
 * 耗时 = (8+2+6)×1.0÷1÷1 + 10 = 26 s；</li>
 * <li>T1 3 link 链蒸汽 = 8000×1.5²×1.0 = 18000 L/s；T1 4 link 链 = 8000×1.5³ = 27000 L/s；</li>
 * <li>T4（tier=3）单粉碎 link：8×0.4÷1÷1 + 0 = 3.2 s（物流时间 0s 即时完成）。</li>
 * </ol>
 */
public final class ExecutionPlan {

    /** 私有构造，禁止实例化纯静态计算器。 */
    private ExecutionPlan() {}

    /**
     * 单物品耗时（秒），§6.1 原式：
     *
     * <pre>
     * ( Σ_link base[link] × TIER_TIME_FACTOR[tier] ÷ max(1, 该 link 同类工作模块数) )
     *     ÷ (1 + Σ速度增幅)  +  LOGISTICS_TIME_SEC[tier]
     * </pre>
     *
     * <p>
     * 其中每个 link 项 = {@code link.getBaseSeconds() × TIER_TIME_FACTOR[tierIdx]
     * ÷ max(1, topology.countUnits(link.getRequiredUnitClass()))}——同类工作模块复数放置时
     * 对应 link 时间按数量均摊（§4.1「复数→对应 link 时间÷数量」）；速度增幅整体作用于工作段
     * （除法在物流时间之前），物流耗时不受速度增幅影响。
     *
     * <p>
     * 防御口径：
     * <ul>
     * <li>{@code tierIdx} 越界（&lt;0 或 ≥ {@link ClusterParams#TIER_COUNT}）→ 返 0（无效配置，调用方不应出现）；</li>
     * <li>{@code chain} 为 null 或空 → 返 {@code LOGISTICS_TIME_SEC[tierIdx]}（纯物流时间：无工作步骤）；</li>
     * <li>{@code topology} 为 null → 每类按 1 个模块计（等价 max(1, 0) 语义）；</li>
     * <li>{@code booster} 为 null → 速度增幅按 0；链内 null 元素跳过。</li>
     * </ul>
     *
     * @param chain    有序链（可含重复 link）
     * @param tierIdx  结构层级下标（0=青铜 … 3=钨钢）
     * @param topology 集群拓扑（按 link 所需工作单元类计数）
     * @param booster  增幅聚合快照，null 按零增益
     * @return 单物品耗时（秒）；tierIdx 越界时 0
     */
    public static double itemTimeSec(List<ChainLink> chain, int tierIdx, ClusterTopology topology,
        BoosterState booster) {
        if (tierIdx < 0 || tierIdx >= ClusterParams.TIER_COUNT) return 0;
        double speed = booster == null ? 0.0 : booster.getSpeedBonus();
        double work = 0.0;
        if (chain != null && !chain.isEmpty()) {
            double tierFactor = ClusterParams.TIER_TIME_FACTOR[tierIdx];
            for (ChainLink link : chain) {
                if (link == null) continue;
                int moduleCount = topology == null ? 0 : topology.countUnits(link.getRequiredUnitClass());
                work += link.getBaseSeconds() * tierFactor / Math.max(1, moduleCount);
            }
        }
        return work / (1.0 + speed) + ClusterParams.LOGISTICS_TIME_SEC[tierIdx];
    }

    /**
     * 有效并行数，§6.1 原式：{@code LOGISTICS_BASE_PARALLEL[tier] + Σ并行增幅（仅生效增幅）}。
     * <p>
     * 仅生效增幅计入聚合（缺流体模块由 {@link BoosterState} 侧剔除，本类不重复判定）。
     *
     * @param tierIdx 结构层级下标（越界按 0 处理，防御口径）
     * @param booster 增幅聚合快照，null 按零增益
     * @return 有效并行数（恒 ≥ 基并行值）
     */
    public static int effectiveParallel(int tierIdx, BoosterState booster) {
        int idx = tierIdx < 0 || tierIdx >= ClusterParams.TIER_COUNT ? 0 : tierIdx;
        int parallelBonus = booster == null ? 0 : booster.getParallelBonus();
        return ClusterParams.LOGISTICS_BASE_PARALLEL[idx] + parallelBonus;
    }

    /**
     * 本链吞吐（矿/s），§6.1 口径：{@code effectiveParallel ÷ max(0.05, itemTimeSec)}。
     * <p>
     * 0.05 s 下限防止即时物流（tier=4 物流时间 0s）+空链组合出现除零；tierIdx 越界时直接返 0
     * （耗时侧越界已返 0，此处不拼装无意义的并行/耗时组合）。
     *
     * @param chain    有序链（可含重复 link）
     * @param tierIdx  结构层级下标
     * @param topology 集群拓扑
     * @param booster  增幅聚合快照，null 按零增益
     * @return 本链吞吐（矿/s）
     */
    public static double chainThroughputPerSec(List<ChainLink> chain, int tierIdx, ClusterTopology topology,
        BoosterState booster) {
        if (tierIdx < 0 || tierIdx >= ClusterParams.TIER_COUNT) return 0;
        double timeSec = itemTimeSec(chain, tierIdx, topology, booster);
        return effectiveParallel(tierIdx, booster) / Math.max(0.05, timeSec);
    }

    /**
     * 本链蒸汽（L/s），§6.1 原式：{@code BASE_CHAIN_STEAM_LPS × CHAIN_LENGTH_MULT^max(0, 链长-1)
     * × TIER_STEAM_MULT[tier]}。
     * <p>
     * 本式<b>不含</b>增幅惩罚乘子与节汽折扣——惩罚与节汽是集群级全局增幅项，只在
     * {@link #totalSteamLps} 处统一施加（分类型乘子连乘、节汽封顶 48%）。
     * <p>
     * 防御口径：chain 为 null/空 → 返 0（空链不可执行、不计蒸汽，与 totalSteamLps 的可执行口径一致）；
     * tierIdx 越界 → 返 0。
     *
     * @param chain   有序链（可含重复 link）
     * @param tierIdx 结构层级下标
     * @return 本链蒸汽消耗（L/s）
     */
    public static double chainSteamLps(List<ChainLink> chain, int tierIdx) {
        if (chain == null || chain.isEmpty()) return 0;
        if (tierIdx < 0 || tierIdx >= ClusterParams.TIER_COUNT) return 0;
        double lengthMult = Math.pow(ClusterParams.CHAIN_LENGTH_MULT, Math.max(0, chain.size() - 1));
        return ClusterParams.BASE_CHAIN_STEAM_LPS * lengthMult * ClusterParams.TIER_STEAM_MULT[tierIdx];
    }

    /**
     * 集群总蒸汽（L/s），§6.1 原式：
     *
     * <pre>
     * Σ_可执行物流单元( chainSteamLps(其链) )  ×  Π惩罚乘子  ×  (1 - min(STEAM_SAVER_CAP, Σ节汽))
     * </pre>
     *
     * <p>
     * 中文口径注记：<b>多链独立加总</b>（D9——每条可执行链各自按链长乘子计蒸汽后求和，不是链数直接乘基数）；
     * 惩罚为<b>分类型乘子连乘</b>（并行1.3/速度1.4/主产物2.0/副产物1.6/节汽1.1，按生效模块连乘，缺流体模块不计）；
     * <b>节汽封顶 48%</b>（Σ节汽超出 {@link ClusterParams#STEAM_SAVER_CAP} 的部分无效）。
     * 润滑液 10 L/s 为运行必需项但<b>不在本式</b>——由经济器（{@link ClusterSteamEconomy}）单列结算。
     *
     * <p>
     * 可执行口径 = 链非空且 {@code chain.isExecutable(topology)}（链有效性判定由 M4 切片在
     * {@link LogisticsChain} 上提供）；topology 为 null 时无从判定工作模块在场，全部链视为不可执行，返 0。
     * 防御口径：units 为 null/空 → 返 0；tierIdx 越界（&lt;0 或 ≥ TIER_COUNT）→ 返 0
     * （后者与逐链 chainSteamLps 越界返 0 等价，提前返回仅为口径显式化）。
     *
     * @param units    待结算的物流单元列表（通常为 topology.getLogisticsUnits()）
     * @param topology 集群拓扑（链可执行性判定）
     * @param tierIdx  结构层级下标
     * @param booster  增幅聚合快照，null 按零增益（惩罚乘积 1、节汽 0）
     * @return 集群总蒸汽消耗（L/s，不含润滑液）
     */
    public static double totalSteamLps(List<MTEBasicLogisticsUnit> units, ClusterTopology topology, int tierIdx,
        BoosterState booster) {
        if (units == null || units.isEmpty()) return 0;
        if (tierIdx < 0 || tierIdx >= ClusterParams.TIER_COUNT) return 0;
        if (topology == null) return 0;
        double chainSum = 0.0;
        for (MTEBasicLogisticsUnit unit : units) {
            if (unit == null) continue;
            LogisticsChain chain = unit.getChain();
            if (chain == null || chain.isEmpty()) continue;
            if (!chain.isExecutable(topology)) continue;
            chainSum += chainSteamLps(chain.getLinks(), tierIdx);
        }
        double penaltyProduct = booster == null ? 1.0 : booster.getPenaltyProduct();
        double steamSaver = booster == null ? 0.0 : booster.getSaverBonusEffective();
        return chainSum * penaltyProduct * (1.0 - Math.min(ClusterParams.STEAM_SAVER_CAP, steamSaver));
    }
}
