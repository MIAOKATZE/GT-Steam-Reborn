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
 * 数值自查（r6-S6 加权链路口径）：
 * <ol>
 * <li>tier0 单链「粉碎+熔炼」（每类 1 模块、unitTier=0、无增幅）：
 * T_粉碎=480t×1.0÷1=480t、T_熔炼=160t，ΣT=640t；
 * 链蒸汽 C=(2000×480+2000×160)÷640=2000 L/s；单物品工作耗时=(480+160)÷20=32 s（物流段另加）；</li>
 * <li>tier0 单链「锻造锤+粉碎」：C=(8000×160+2000×480)÷640=3500 L/s——时间加权使长步低耗的
 * 粉碎在均值中占主导；unitTier=1 时该值不变（4^u/2^u 同乘 T_i 后在 Σ(C·T)/Σ(T) 中约去）、
 * 但单物品工作耗时减半为 (160+480)÷2÷20=16 s；</li>
 * <li>集群运行总需求（主控组装）：8000 × FIXED_STEAM_TIER_MULT[集群 tier] + Σ可执行链加权值
 * ×Π惩罚乘子×(1-min(48%,Σ节汽))——节汽封顶只作用于加权链路段，不影响固定项。</li>
 * </ol>
 */
public final class ExecutionPlan {

    /** 私有构造，禁止实例化纯静态计算器。 */
    private ExecutionPlan() {}

    /**
     * 单物品耗时（秒），r6-S6 逐 link 口径：
     *
     * <pre>
     * ( Σ_link T_i ÷ 20 )  ÷ (1 + Σ速度增幅)  +  LOGISTICS_TIME_SEC[tier]
     * T_i = baseTicks[link] × TIER_TIME_FACTOR[tier] ÷ max(1, 该 link 同类工作模块数)
     *       ÷ 2^unitStructureTier
     * </pre>
     *
     * <p>
     * 每个 link 项 T_i 与 {@link #linkWeightTicks}（链蒸汽加权分母）完全同源：同类工作模块复数
     * 放置时对应 link 时间按数量均摊（§4.1），均摊只计 {@code isModuleEnabled()} 的模块（必修 c：
     * 未成型/断电的同类模块不参与均摊，防耗时 ÷N 虚快）；单元自身结构 tier 每升 1 级该 link
     * 时间 ÷2（{@code unitStructureTier} 为 -1/未成型时按 0）；速度增幅整体作用于工作段（除法在
     * 物流时间之前），物流耗时不受速度增幅影响。
     *
     * <p>
     * 防御口径：
     * <ul>
     * <li>{@code tierIdx} 越界（&lt;0 或 ≥ {@link ClusterParams#TIER_COUNT}）→ 返 0（无效配置，调用方不应出现）；</li>
     * <li>{@code chain} 为 null 或空 → 返 {@code LOGISTICS_TIME_SEC[tierIdx]}（纯物流时间：无工作步骤）；</li>
     * <li>{@code topology} 为 null → 每类按 1 个模块、unitTier 按 0 计；</li>
     * <li>{@code booster} 为 null → 速度增幅按 0；链内 null 元素跳过。</li>
     * </ul>
     *
     * @param chain    有序链（可含重复 link）
     * @param tierIdx  结构层级下标（0=青铜 … 3=钨钢）
     * @param topology 集群拓扑（按 link 所需工作单元类计数与取 tier，仅计 isModuleEnabled 的单元）
     * @param booster  增幅聚合快照，null 按零增益
     * @return 单物品耗时（秒）；tierIdx 越界时 0
     */
    public static double itemTimeSec(List<ChainLink> chain, int tierIdx, ClusterTopology topology,
        BoosterState booster) {
        if (tierIdx < 0 || tierIdx >= ClusterParams.TIER_COUNT) return 0;
        double speed = booster == null ? 0.0 : booster.getSpeedBonus();
        double workTicks = 0.0;
        if (chain != null && !chain.isEmpty()) {
            for (ChainLink link : chain) {
                if (link == null) continue;
                workTicks += linkWeightTicks(link, tierIdx, enabledUnitStats(topology, link.getRequiredUnitClass()));
            }
        }
        return workTicks / ChainLink.TICKS_PER_SECOND / (1.0 + speed) + ClusterParams.LOGISTICS_TIME_SEC[tierIdx];
    }

    /**
     * 单个链步的有效耗时权重 T_i（tick）——{@link #itemTimeSec} 的逐 link 时间项与链蒸汽加权
     * 分母共用的唯一实现（同源一致口径）：{@code baseTicks × TIER_TIME_FACTOR[tierIdx]
     * ÷ max(1, 同类已启用工作模块数) ÷ 2^unitStructureTier}。
     *
     * @param link     链步（非 null）
     * @param tierIdx  集群结构层级下标（已由调用方验证有效）
     * @param unitStat {@link #enabledUnitStats} 统计结果（非 null）
     * @return 该链步的有效耗时权重（tick，恒 &gt; 0）
     */
    private static double linkWeightTicks(ChainLink link, int tierIdx, int[] unitStat) {
        double t = link.getBaseTicks() * ClusterParams.TIER_TIME_FACTOR[tierIdx] / Math.max(1, unitStat[0]);
        return t / Math.pow(2.0, unitStat[1]);
    }

    /**
     * 同类已启用工作单元统计（必修 c 口径沿用：仅计 {@code isModuleEnabled()} 的单元——未成型/
     * 断电的同类模块照计会把耗时 ÷N 虚快）。
     *
     * @param topology 集群拓扑；null 计 0（等价 max(1, 0)、unitTier 按 0 语义）
     * @param type     link 所需工作单元类型（instanceof 语义，含子类）
     * @return [0]=该类型且已启用的单元数；[1]=首个已启用单元的 unitStructureTier（无单元或 -1 时为 0，
     *         幂次回退青铜档）
     */
    private static int[] enabledUnitStats(ClusterTopology topology, Class<? extends MTEClusterUnitBase> type) {
        int count = 0;
        int tier = 0;
        if (topology != null) {
            for (MTEClusterUnitBase unit : topology.getUnits()) {
                if (type.isInstance(unit) && unit.isModuleEnabled()) {
                    if (count == 0) tier = Math.max(0, unit.getUnitStructureTier());
                    count++;
                }
            }
        }
        return new int[] { count, tier };
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
     * 本链加权蒸汽（L/s）——主控组装「固定项 + C」的单链口径（§3.6.2 冻结名，r6-S6 加权重做）：
     *
     * <pre>
     * C_chain = Σ_link(C_i × T_i) ÷ Σ_link(T_i)
     * C_i = baseSteamLps[link] × 4^unitStructureTier（模块每升 1 级消耗 ×4；-1/未成型按 0）
     * T_i = linkWeightTicks（与 itemTimeSec 逐 link 时间同源；物流段无蒸汽、不参与本式）
     * </pre>
     *
     * 不含固定项（{@code FIXED_CLUSTER_STEAM_LPS × FIXED_STEAM_TIER_MULT[tier]} 由主控另加）与
     * 集群级增幅惩罚/节汽（后者在 {@link #totalSteamLps} 统一施加、只作用于本加权段）。
     *
     * @param chain    有序链（可含重复 link；null/空返 0——空链不可执行不计蒸汽）
     * @param tierIdx  结构层级下标
     * @param topology 集群拓扑（逐 link 模块计数与单元 tier 来源）；null 按无模块口径防御计算
     * @return 本链加权蒸汽消耗（L/s）
     */
    public static double computeChainSteam(List<ChainLink> chain, int tierIdx, ClusterTopology topology) {
        return chainSteamLps(chain, tierIdx, topology);
    }

    /**
     * 集群聚合蒸汽 C（L/s）——主控组装「固定项 + C」的聚合口径（§3.6.2 冻结名）：
     *
     * <pre>
     * C = Σ 可执行物流单元( chainSteamLps(其链) )
     *     × Π 生效增幅蒸汽惩罚 × (1 - min(48%, Σ生效节汽))
     * </pre>
     *
     * 与 {@link #totalSteamLps} 同式同防御口径；主控以 {@code ceil(固定项 + C)} 组装运行蒸汽需求
     * 传入 {@code ClusterSteamEconomy.settleRunFull}（固定项 = FIXED_CLUSTER_STEAM_LPS ×
     * FIXED_STEAM_TIER_MULT[tier]，由主控组装；节汽封顶只作用于本加权段）。
     *
     * @param units    待结算的物流单元列表（通常为 topology.getLogisticsUnits()）
     * @param topology 集群拓扑（链可执行性判定）
     * @param tierIdx  结构层级下标
     * @param booster  增幅聚合快照，null 按零增益（惩罚乘积 1、节汽 0）
     * @return 聚合蒸汽需求 C（L/s，不含固定项与润滑）
     */
    public static double computeAggregateSteamC(List<MTEBasicLogisticsUnit> units, ClusterTopology topology,
        int tierIdx, BoosterState booster) {
        return totalSteamLps(units, topology, tierIdx, booster);
    }

    /**
     * 本链加权蒸汽（L/s），r6-S6 原式：{@code Σ(C_i×T_i) ÷ Σ(T_i)}
     * （C_i/T_i 定义见 {@link #computeChainSteam}）。
     * <p>
     * 本式<b>不含</b>增幅惩罚乘子与节汽折扣——惩罚与节汽是集群级全局增幅项，只在
     * {@link #totalSteamLps} 处统一施加于加权段（分类型乘子连乘、节汽封顶 48%）。
     * <p>
     * 防御口径：chain 为 null/空 → 返 0（空链不可执行、不计蒸汽，与 totalSteamLps 的可执行口径一致）；
     * tierIdx 越界 → 返 0；Σ(T_i)=0（防御，基础表恒正不触发）→ 返 0。
     *
     * @param chain    有序链（可含重复 link）
     * @param tierIdx  结构层级下标
     * @param topology 集群拓扑（逐 link 模块计数与单元 tier 来源）
     * @return 本链加权蒸汽消耗（L/s）
     */
    public static double chainSteamLps(List<ChainLink> chain, int tierIdx, ClusterTopology topology) {
        if (chain == null || chain.isEmpty()) return 0;
        if (tierIdx < 0 || tierIdx >= ClusterParams.TIER_COUNT) return 0;
        double weightedSum = 0.0;
        double weightSum = 0.0;
        for (ChainLink link : chain) {
            if (link == null) continue;
            int[] stat = enabledUnitStats(topology, link.getRequiredUnitClass());
            double t = linkWeightTicks(link, tierIdx, stat);
            weightedSum += link.getBaseSteamLps() * Math.pow(4.0, stat[1]) * t;
            weightSum += t;
        }
        return weightSum <= 0.0 ? 0.0 : weightedSum / weightSum;
    }

    /**
     * 集群总蒸汽加权段 C（L/s），r6-S6 口径：
     *
     * <pre>
     * Σ_可执行物流单元( chainSteamLps(其链) )  ×  Π惩罚乘子  ×  (1 - min(STEAM_SAVER_CAP, Σ节汽))
     * </pre>
     *
     * <p>
     * 中文口径注记：<b>多链独立加总</b>（每条可执行链各自按加权式计蒸汽后求和）；惩罚为<b>分类型
     * 乘子连乘</b>（并行1.3/速度1.4/主产物2.0/副产物1.6/节汽1.1，按生效模块连乘，缺流体模块不计）；
     * <b>节汽封顶 48%</b>（Σ节汽超出 {@link ClusterParams#STEAM_SAVER_CAP} 的部分无效）。
     * 惩罚与节汽<b>只作用于本加权段</b>——固定蒸汽项（{@code FIXED_CLUSTER_STEAM_LPS ×
     * FIXED_STEAM_TIER_MULT[tier]}）由主控另加、不受其影响。润滑液为运行必需项但<b>不在本式</b>——
     * 由经济器（{@link ClusterSteamEconomy}）单列结算。
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
     * @return 集群总蒸汽加权段消耗（L/s，不含固定项与润滑液）
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
            chainSum += chainSteamLps(chain.getLinks(), tierIdx, topology);
        }
        double penaltyProduct = booster == null ? 1.0 : booster.getPenaltyProduct();
        double steamSaver = booster == null ? 0.0 : booster.getSaverBonusEffective();
        return chainSum * penaltyProduct * (1.0 - Math.min(ClusterParams.STEAM_SAVER_CAP, steamSaver));
    }
}
