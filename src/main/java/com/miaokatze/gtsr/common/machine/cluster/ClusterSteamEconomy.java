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
 * 蒸汽取 {@code Materials.Steam.getGas}，润滑剂取 {@code Materials.Lubricant.getFluid}；全部数值常量
 * 取自 {@link ClusterParams}，本类零散硬编码禁止。
 *
 * <p>
 * 完整状态口径（§3.6.2）：结算结果必须以 {@link EconomySettleResult} 整体返回/锁存——
 * {@code isSteamShortage()}/{@code isLubricantOk()} 等单点访问器仅作 GUI 读数保留，
 * <b>不得作为唯一门控</b>替代完整双流体结果。
 *
 * <p>
 * 刚满热无双扣（§3.6.2 修复 2）：{@link #settlePreheatFull} 在预热进度已能于本秒内抵达满热时置
 * {@code justReachedFullHeat=true}——该秒已按预热口径扣 2000+10，调用方（主控）不得再对同一秒
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

        /** 本次结算的润滑剂需求（L/s，恒 {@link ClusterParams#LUBRICANT_LPS}）。 */
        public int settledLubricantLps;
    }

    /** 上次结算蒸汽不足红标（true = 上次结算蒸汽跨仓合计不足，结构失效/停机时由 clearFlags 清除）。 */
    private boolean steamShortage = false;

    /** 上次结算润滑剂足额标记（false = 上次结算润滑剂跨仓合计不足）。 */
    private boolean lubricantOk = true;

    /** 上次结算的蒸汽需求（L/s，向上取整后口径）。 */
    private long lastSteamLps = 0;

    /** 上次结算的润滑剂需求（L/s，恒为 {@link ClusterParams#LUBRICANT_LPS}）。 */
    private long lastLubricantLps = ClusterParams.LUBRICANT_LPS;

    /** 公共构造器：每台总控持有一个结算器实例，红标状态为实例私有，不做静态共享。 */
    public ClusterSteamEconomy() {}

    /**
     * 预热结算（完整状态口径）：预检并实扣 {@link ClusterParams#PREHEAT_STEAM_LPS}（2000 L/s）
     * 蒸汽 + {@link ClusterParams#LUBRICANT_LPS}（10 L/s）润滑，原子语义——两项同时足量才统一
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
        return settle(cluster, ClusterParams.PREHEAT_STEAM_LPS, true);
    }

    /**
     * 运行/保温结算（完整状态口径）：蒸汽需求 = {@code ceil(max(2000, totalSteamLps))} L/s
     * （保温下限 2000，运行 = 2000 + C，C 由调用方按 {@link ExecutionPlan} 聚合并传入），
     * 润滑恒 {@link ClusterParams#LUBRICANT_LPS}（10 L/s）；任一不足双零扣（ok=false）。
     * 满热后每秒统一执行本方法一次（§3.6.2 修复 3）。
     *
     * @param cluster       集群总控（流体输入仓来源）
     * @param totalSteamLps 本周期蒸汽总需求（L/s；调用方组装的 2000+C 口径，本方法兜底下限 2000）
     * @return 完整结算状态（justReachedFullHeat 恒 false——运行结算不存在预热叠加）
     */
    public EconomySettleResult settleRunFull(MTESteamMineralLogisticsCluster cluster, double totalSteamLps) {
        double demand = Math.max((double) ClusterParams.PREHEAT_STEAM_LPS, totalSteamLps);
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

    /** @return 上次结算蒸汽需求（L/s）。 */
    public long getLastSteamLps() {
        return lastSteamLps;
    }

    /** @return 上次结算润滑需求（L/s，常量 {@link ClusterParams#LUBRICANT_LPS}）。 */
    public long getLastLubricantLps() {
        return lastLubricantLps;
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
     * 原子结算核心：先 hasEnoughAcross 跨仓合计探测蒸汽与润滑剂两项（仅模拟不消耗），
     * 均足量再统一 depleteFluidAcross 实扣；任一不足→全程双零扣并按项置红标（蒸汽不足置
     * steamShortage，润滑剂不足拉低 lubricantOk），结算成功则两项红标复位为健康态。
     *
     * @param cluster        集群总控
     * @param steamDemandLps 蒸汽需求（L/s，内部向上取整为整升；settleRun 侧已兜底保温下限）
     * @param preheat        true = 预热口径（评估 justReachedFullHeat）；false = 运行/保温口径
     * @return 完整结算状态（永非 null）
     */
    private EconomySettleResult settle(MTESteamMineralLogisticsCluster cluster, double steamDemandLps,
        boolean preheat) {
        EconomySettleResult result = new EconomySettleResult();
        long steamLps = Math.max(0L, (long) Math.ceil(steamDemandLps));
        long lubricantLps = ClusterParams.LUBRICANT_LPS;
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

        FluidStack steam = Materials.Steam.getGas((int) Math.min(steamLps, Integer.MAX_VALUE));
        FluidStack lubricant = Materials.Lubricant.getFluid((int) Math.min(lubricantLps, Integer.MAX_VALUE));
        result.steamEnough = GTSRHatchFluidAccess.hasEnoughAcross(hatches, steam);
        result.lubricantEnough = GTSRHatchFluidAccess.hasEnoughAcross(hatches, lubricant);
        steamShortage = !result.steamEnough;
        lubricantOk = result.lubricantEnough;
        if (!result.steamEnough || !result.lubricantEnough) {
            // 原子失败：两项都未实扣（探测仅模拟），按项置红标
            result.ok = false;
            return result;
        }
        GTSRHatchFluidAccess.depleteFluidAcross(hatches, steam);
        GTSRHatchFluidAccess.depleteFluidAcross(hatches, lubricant);
        result.ok = true;
        return result;
    }
}
