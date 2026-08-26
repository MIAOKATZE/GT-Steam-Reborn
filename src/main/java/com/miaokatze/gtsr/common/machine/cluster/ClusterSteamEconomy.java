package com.miaokatze.gtsr.common.machine.cluster;

import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEHatch;

/**
 * 集群蒸汽+润滑液经济结算器：按 20 tick 周期对总控的流体输入仓做「蒸汽 + 润滑剂」原子结算，
 * 返回完整结算状态（{@link EconomySettleResult}，plan §3.6.2）。
 *
 * <p>
 * 流体源：总控 {@link MTESteamMineralLogisticsCluster#getClusterFluidInputHatches()} 返回的
 * 统一可枚举仓列表（E5：标准 mInputHatches + 主控侧耐压蒸汽输入仓；本类只经该方法取仓，不直接摸
 * 总控字段；ME 输入仓兼容由 {@link GTSRHatchFluidAccess} 统一承担——探测走 getTankInfo 模拟、
 * 实扣走 3 参 drain，见该类铁律）。
 *
 * <p>
 * 原子语义（{@code MTEAmmoniaPlant.consumeFuelAndSteam} 范式）：先跨仓合计探测两项是否同时
 * 足量（仅模拟），两者均足才统一实扣；<b>任一不足则两项均不扣（双零扣）</b>并按项置红标。
 * 蒸汽按 S8 种类口径择优：识别输入仓内实际蒸汽种类（{@link ClusterParams.SteamGrade} 六种，
 * divisor 降序、tier 门控），等效需求 ÷divisor 向上取整后以单一种类原子扣除，优先最高级；
 * 润滑剂取 {@code Materials.Lubricant.getFluid}；全部数值常量
 * 取自 {@link ClusterParams}，本类零散硬编码禁止。
 *
 * <p>
 * 完整状态口径（§3.6.2）：结算结果必须以 {@link EconomySettleResult} 整体返回/锁存——
 * {@code isSteamShortage()}/{@code isLubricantOk()} 等单点访问器仅作 GUI 读数保留，
 * <b>不得作为唯一门控</b>替代完整双流体结果。
 *
 * <p>
 * 刚满热无双扣（§3.6.2 修复 2）：{@link #settlePreheatFull} 在预热进度已能于本秒内抵达满热时置
 * {@code justReachedFullHeat=true}——该秒已按预热口径扣固定蒸汽+润滑，调用方（主控）不得再对同一秒
 * 追加 {@link #settleRunFull}；满热后每秒统一走一次 settleRun。
 *
 * <p>
 * 口径边界：多链蒸汽需求 = 每链独立加总的口径归 {@link ExecutionPlan}，本类只负责结算——
 * 调用方把加总后的 totalSteamLps 传入，本类不感知链结构。
 */
public final class ClusterSteamEconomy {

    /**
     * 完整结算状态（字段签名冻结）：一次 20t 原子结算的全部结果，供主控锁存、GUI 读数与
     * 断供降温判定共用同一份口径。
     */
    public static final class EconomySettleResult {

        /** 本秒结算是否成功（蒸汽且润滑均足额并已实扣；false = 双零扣）。 */
        public boolean ok;

        /** 蒸汽跨仓合计是否足额（false 项不扣，与润滑互为原子）。 */
        public boolean steamEnough;

        /** 润滑剂跨仓合计是否足额（false 项不扣，与蒸汽互为原子）。 */
        public boolean lubricantEnough;

        /** 仅预热结算有效：本次预热即满热前的最后一秒（本秒不得再叠加运行结算，无双扣）。 */
        public boolean justReachedFullHeat;

        /** 本次结算的蒸汽需求（L/s，向上取整后口径）。 */
        public int settledSteamLps;

        /** 本次结算的润滑剂需求（L/s，两段口径：CLUSTER_LUBRICANT_LPS[tier] + 物流工作段）。 */
        public int settledLubricantLps;
    }

    /** 上次结算蒸汽不足红标（true = 上次结算蒸汽跨仓合计不足，结构失效/停机时由 clearFlags 清除）。 */
    private boolean steamShortage = false;

    /** 上次结算润滑剂足额标记（false = 上次结算润滑剂跨仓合计不足）。 */
    private boolean lubricantOk = true;

    /** 上次结算的蒸汽需求（L/s，向上取整后口径）。 */
    private long lastSteamLps = 0;

    /** 上次结算的润滑剂需求（L/s，两段口径见 {@link #lubricantLpsFor}）。 */
    private long lastLubricantLps = ClusterParams.CLUSTER_LUBRICANT_LPS[0];

    /** 上次<b>成功</b>结算实际消耗的蒸汽种类（null = 尚未成功结算或零蒸汽需求；显示转化口径来源）。 */
    private ClusterParams.SteamGrade lastSteamGrade = null;

    /** 公共构造器：每台总控持有一个结算器实例，红标状态为实例私有，不做静态共享。 */
    public ClusterSteamEconomy() {}

    /**
     * 预热结算（完整状态口径）：预检并实扣固定蒸汽项（{@code FIXED_CLUSTER_STEAM_LPS ×
     * FIXED_STEAM_TIER_MULT[集群 tier]}，r6-S6 审查修正与运行期固定项同源）蒸汽 +
     * {@link ClusterParams#CLUSTER_LUBRICANT_LPS}[tier] 润滑，原子语义——两项同时足量才统一
     * 实扣，任一不足全程双零扣（ok=false）。
     *
     * <p>
     * 建议调用方（主控）时序：未满热每 20t 调本方法 → 以结果 ok 驱动
     * {@code preheat.tickServer(enabled, formed, ok)} → 若结果 {@code justReachedFullHeat=true}，
     * 本秒不得再调 {@link #settleRunFull}（预热扣已覆盖本秒），下一秒起转 settleRun。
     *
     * @param cluster 集群总控（流体输入仓来源）
     * @return 完整结算状态（含双项足额标记与刚满热标记）
     */
    public EconomySettleResult settlePreheatFull(MTESteamMineralLogisticsCluster cluster) {
        return settle(cluster, cluster.runFixedSteamLps(), true);
    }

    /**
     * 运行/保温结算（完整状态口径，r6-S6 新口径）：
     *
     * <pre>
     * 蒸汽需求 = max(0, fixedSteamLps) + max(0, weightedChainLps)   （L/s）
     * </pre>
     *
     * 固定项 {@code fixedSteamLps} = 主控按 {@code FIXED_CLUSTER_STEAM_LPS × FIXED_STEAM_TIER_MULT[集群 tier]}
     * 组装的新固定蒸汽项（保温兜底下限即该项本身：无可执行链时加权段为 0，需求回落到固定项），
     * 不再引用旧 2000/平铺 8000 下限；加权段由主控按 {@link ExecutionPlan} 聚合并传入。
     * 润滑两段口径见 {@link #lubricantLpsFor}；任一不足双零扣（ok=false）。
     * 满热后每秒统一执行本方法一次（§3.6.2 修复 3）。
     *
     * @param cluster          集群总控（流体输入仓来源）
     * @param fixedSteamLps    固定蒸汽项（L/s；主控组装的 FIXED_CLUSTER_STEAM_LPS × tier_mult 口径）
     * @param weightedChainLps 加权链路蒸汽段（L/s；调用方按 ExecutionPlan 聚合的 C，增幅已计）
     * @return 完整结算状态（justReachedFullHeat 恒 false——运行结算不存在预热叠加）
     */
    public EconomySettleResult settleRunFull(MTESteamMineralLogisticsCluster cluster, double fixedSteamLps,
        double weightedChainLps) {
        double demand = Math.max(0.0, fixedSteamLps) + Math.max(0.0, weightedChainLps);
        return settle(cluster, demand, false);
    }

    /**
     * // SHIM-E5 已删除（批2 E5）：旧 boolean 口径 settlePreheat/settleRun 过渡方法；
     * 完整状态统一走 {@link #settlePreheatFull} / {@link #settleRunFull}。
     */

    /** @return 上次结算蒸汽不足（true = 蒸汽跨仓合计不足；仅 GUI 读数，不得作唯一门控）。 */
    public boolean isSteamShortage() {
        return steamShortage;
    }

    /** @return 上次结算润滑足额（false = 润滑剂跨仓合计不足；仅 GUI 读数，不得作唯一门控）。 */
    public boolean isLubricantOk() {
        return lubricantOk;
    }

    /** @return 上次结算蒸汽需求（L/s，等效普通蒸汽口径）。 */
    public long getLastSteamLps() {
        return lastSteamLps;
    }

    /** @return 上次结算润滑需求（L/s，两段口径见 {@link #lubricantLpsFor}）。 */
    public long getLastLubricantLps() {
        return lastLubricantLps;
    }

    /**
     * S8 显示转化接口：最近一次<b>成功</b>结算实际消耗的蒸汽种类（null = 尚未成功结算或零蒸汽
     * 需求，调用方按普通 Steam/divisor 1 口径处理）。供主控显示读数折算与 S10 性能面板使用。
     */
    public ClusterParams.SteamGrade getLastSteamGrade() {
        return lastSteamGrade;
    }

    /**
     * S8 显示转化：等效普通蒸汽量（L）→ 实际种类升数（÷divisor 向上取整，防 ÷1000/÷4000 后
     * 归零误判）；grade 为 null 按 divisor 1 原样计。
     *
     * @param equivalentLiters 等效普通蒸汽量（L）
     * @param grade            目标蒸汽种类（可 null）
     * @return 该种类的实际升数（≥0，向上取整）
     */
    public static long toGradeLiters(long equivalentLiters, ClusterParams.SteamGrade grade) {
        if (equivalentLiters <= 0L) return 0L;
        int divisor = grade == null ? 1 : grade.getDivisor();
        return (equivalentLiters + divisor - 1L) / divisor;
    }

    /** 结构失效/停机时清红标：两项复位为健康态，避免重检后残留旧短缺状态。 */
    public void clearFlags() {
        steamShortage = false;
        lubricantOk = true;
    }

    /**
     * 刚满热判定（§3.6.2 修复 2 的支持谓词）：预热进度已 ≥ 1 - 1/{@link ClusterParams#PREHEAT_SECONDS}
     * （本秒的 +1/30 增量足以封顶）时返回 true。已满热（不应再走预热结算）返回 false。
     */
    private static boolean isPreheatCompletingThisSecond(MTESteamMineralLogisticsCluster cluster) {
        ClusterPreheatController preheat = cluster == null ? null : cluster.getPreheat();
        if (preheat == null || preheat.isReady()) return false;
        double remaining = 1.0 / ClusterParams.PREHEAT_SECONDS;
        return preheat.getProgress() >= 1.0 - remaining - 1e-9;
    }

    /**
     * 润滑剂两段需求（L/s，r6-S6 接管 TODO(r6-S6) 临时接线）：
     * <ol>
     * <li><b>集群恒定段</b>：{@code CLUSTER_LUBRICANT_LPS[集群 tier]}——按总控结构 tier 取档，
     * tier 未定（-1）时回退青铜档；</li>
     * <li><b>物流工作段</b>：存在正在工作（{@link MTEBasicLogisticsUnit#isUnitRunning()} 处理窗口内）
     * 的物流单元时，加计 {@code LOGISTICS_UNIT_LUBRICANT_LPS[该单元 unitStructureTier]}
     * （多台工作时取最高结构 tier 计一段——D2/D3 同级强制下各单元 tier 恒一致；未成型 -1 不计入）。</li>
     * </ol>
     * 两段合并为单一润滑需求进入 {@link #settle} 原子结算（与蒸汽同时探测/实扣、双零扣语义不变）；
     * 预热期无批执行 → 物流单元不处于处理窗口，自然只有恒定段。
     */
    private static long lubricantLpsFor(MTESteamMineralLogisticsCluster cluster) {
        int tier = cluster == null ? -1 : cluster.getStructureTierIndex();
        if (tier < 0 || tier >= ClusterParams.TIER_COUNT) tier = 0;
        long lubricantLps = ClusterParams.CLUSTER_LUBRICANT_LPS[tier];
        int workingTier = workingLogisticsUnitTier(cluster);
        if (workingTier >= 0) {
            lubricantLps += ClusterParams.LOGISTICS_UNIT_LUBRICANT_LPS[Math
                .min(workingTier, ClusterParams.TIER_COUNT - 1)];
        }
        return lubricantLps;
    }

    /**
     * 正在工作的物流单元最高结构 tier（润滑工作段取档来源）：仅计 {@code isModuleEnabled()} 且
     * {@code isUnitRunning()}（成型+连接+物理电源开+处理窗口内）的物流单元；无则返 -1。
     */
    private static int workingLogisticsUnitTier(MTESteamMineralLogisticsCluster cluster) {
        if (cluster == null) return -1;
        int best = -1;
        for (MTEBasicLogisticsUnit unit : cluster.getTopology()
            .getLogisticsUnits()) {
            if (unit == null || !unit.isModuleEnabled() || !unit.isWorkInProgress()) continue;
            best = Math.max(best, unit.getUnitStructureTier());
        }
        return best;
    }

    /**
     * 原子结算核心（S8 蒸汽种类口径）：蒸汽需求为<b>等效普通蒸汽</b> L/s，实际按仓内可用的
     * 最高级（divisor 最大）被接受种类折算扣除——
     * <ol>
     * <li>候选种类按 divisor 降序遍历，仅取 {@link ClusterParams.SteamGrade#isAcceptedBy} 对集群
     * tier 接受的种类（未接受种类不消耗也不扣——钢级只装普通蒸汽时按断供处理，见下）；</li>
     * <li>每种折算需求 = {@link #toGradeLiters}（÷divisor 向上取整，防 ÷1000/÷4000 归零误判），
     * 经 hasEnoughAcross 跨仓模拟探测，首个足额种类即选定（多种并存时优先最高级、次按可用量
     * 择优降档；不做跨种类混合扣，保持单流体原子语义）；</li>
     * <li>无可用种类或润滑不足 → 全程双零扣并按项置红标；零需求（steamLps=0）视蒸汽恒足。</li>
     * </ol>
     * 结算成功后锁存 {@link #lastSteamGrade} 供显示转化（终端读数 ÷ 实际种类 divisor）。
     *
     * @param cluster        集群总控
     * @param steamDemandLps 等效蒸汽需求（L/s，内部向上取整为整升）
     * @param preheat        true = 预热口径（评估 justReachedFullHeat）；false = 运行/保温口径
     * @return 完整结算状态（永非 null）
     */
    private EconomySettleResult settle(MTESteamMineralLogisticsCluster cluster, double steamDemandLps,
        boolean preheat) {
        EconomySettleResult result = new EconomySettleResult();
        long steamLps = Math.max(0L, (long) Math.ceil(steamDemandLps));
        long lubricantLps = lubricantLpsFor(cluster);
        lastSteamLps = steamLps;
        lastLubricantLps = lubricantLps;
        result.settledSteamLps = (int) Math.min(steamLps, Integer.MAX_VALUE);
        result.settledLubricantLps = (int) Math.min(lubricantLps, Integer.MAX_VALUE);
        result.justReachedFullHeat = preheat && isPreheatCompletingThisSecond(cluster);

        List<? extends MTEHatch> hatches = cluster == null ? null : cluster.getClusterFluidInputHatches();
        if (hatches == null) {
            // 仓源不可达视同双项不足：双零扣 + 置红标
            steamShortage = true;
            lubricantOk = false;
            result.steamEnough = false;
            result.lubricantEnough = false;
            result.ok = false;
            return result;
        }

        // S8：蒸汽种类择优（tier 门控 + divisor 降序 + 折算 ceil），null = 无可扣种类
        GradeSelection selection = selectSteamGrade(hatches, steamLps, clusterTierOf(cluster));
        FluidStack lubricant = Materials.Lubricant.getFluid((int) Math.min(lubricantLps, Integer.MAX_VALUE));
        result.steamEnough = steamLps <= 0L || selection != null;
        result.lubricantEnough = GTSRHatchFluidAccess.hasEnoughAcross(hatches, lubricant);
        steamShortage = !result.steamEnough;
        lubricantOk = result.lubricantEnough;
        if (!result.steamEnough || !result.lubricantEnough) {
            // 原子失败：两项都未实扣（探测仅模拟），按项置红标
            result.ok = false;
            return result;
        }
        if (selection != null) {
            GTSRHatchFluidAccess.depleteFluidAcross(hatches, selection.toStack());
            lastSteamGrade = selection.grade;
        }
        GTSRHatchFluidAccess.depleteFluidAcross(hatches, lubricant);
        result.ok = true;
        return result;
    }

    /** 集群 tier 解析（门控用）：与 {@link #lubricantLpsFor} 同防御口径——未定/越界回退青铜（全收）。 */
    private static ClusterParams.ClusterTier clusterTierOf(MTESteamMineralLogisticsCluster cluster) {
        int idx = cluster == null ? -1 : cluster.getStructureTierIndex();
        return ClusterParams.ClusterTier.get(idx);
    }

    /**
     * 单一蒸汽种类的选定结果：{@code grade × liters}（liters 为该种类实际升数，已 ÷divisor 向上
     * 取整且 ≤ Integer.MAX_VALUE，可直接构造 FluidStack）。
     */
    private static final class GradeSelection {

        final ClusterParams.SteamGrade grade;
        final long liters;

        GradeSelection(ClusterParams.SteamGrade grade, long liters) {
            this.grade = grade;
            this.liters = liters;
        }

        FluidStack toStack() {
            return new FluidStack(grade.resolveFluid(), (int) Math.min(liters, Integer.MAX_VALUE));
        }
    }

    /**
     * S8 蒸汽种类择优（仅模拟探测，不实扣）：候选按 divisor 降序（致密超临界 → 致密过热 →
     * 致密 → 超临界 → 过热 → 普通），逐一种类过滤 tier 门控与流体注册可得性，折算需求
     * （{@link #toGradeLiters}）跨仓探测首个足额者即返回；无可行种类返回 null（双零扣断供路径）。
     *
     * @param hatches  流体输入仓列表（非 null）
     * @param steamLps 等效普通蒸汽需求（L/s，≥0）
     * @param tier     集群层级（门控）
     * @return 选定种类与实际升数；零需求或无可行种类返回 null
     */
    private static GradeSelection selectSteamGrade(List<? extends MTEHatch> hatches, long steamLps,
        ClusterParams.ClusterTier tier) {
        if (steamLps <= 0L) return null;
        for (ClusterParams.SteamGrade grade : SteamGradePriority.ORDER) {
            if (!grade.isAcceptedBy(tier)) continue;
            net.minecraftforge.fluids.Fluid fluid = grade.resolveFluid();
            if (fluid == null) continue;
            long needLiters = toGradeLiters(steamLps, grade);
            FluidStack want = new FluidStack(fluid, (int) Math.min(needLiters, Integer.MAX_VALUE));
            if (GTSRHatchFluidAccess.hasEnoughAcross(hatches, want)) {
                return new GradeSelection(grade, needLiters);
            }
        }
        return null;
    }

    /** 蒸汽种类遍历顺序（divisor 降序，S8 择优专用）：优先消耗仓内能量密度最高的种类。 */
    private static final class SteamGradePriority {

        static final ClusterParams.SteamGrade[] ORDER = { ClusterParams.SteamGrade.DENSE_SUPERCRITICAL_STEAM,
            ClusterParams.SteamGrade.DENSE_SUPERHEATED_STEAM, ClusterParams.SteamGrade.DENSE_STEAM,
            ClusterParams.SteamGrade.SUPERCRITICAL_STEAM, ClusterParams.SteamGrade.SUPERHEATED_STEAM,
            ClusterParams.SteamGrade.STEAM };
    }
}
