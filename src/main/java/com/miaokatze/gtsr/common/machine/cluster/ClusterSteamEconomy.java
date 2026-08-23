package com.miaokatze.gtsr.common.machine.cluster;

import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEHatchInput;

/**
 * 集群蒸汽+润滑液经济结算器：按 20 tick 周期对总控的流体输入仓做「蒸汽 + 润滑剂」原子结算。
 *
 * <p>
 * 流体源：总控 {@link MTESteamMineralLogisticsCluster#getClusterFluidInputHatches()} 返回的
 * mInputHatches live 视图（本类只经该方法取仓，不直接摸总控字段；ME 输入仓兼容由
 * {@link GTSRHatchFluidAccess} 统一承担——探测走 getTankInfo 模拟、实扣走 3 参 drain，见该类铁律）。
 *
 * <p>
 * 原子语义（{@code MTEAmmoniaPlant.consumeFuelAndSteam} 范式）：先跨仓合计探测两项是否同时
 * 足量（仅模拟），两者均足才统一实扣；任一不足则一分不扣并按项置红标。蒸汽取
 * {@code Materials.Steam.getGas}，润滑剂取 {@code Materials.Lubricant.getFluid}；全部数值常量
 * 取自 {@link ClusterParams}，本类零散硬编码禁止。
 *
 * <p>
 * 口径边界：多链蒸汽需求 = 每链独立加总的口径归 ExecutionPlan（M5 批），本类只负责结算——
 * 调用方把加总后的 totalSteamLps 传入，本类不感知链结构。
 */
public final class ClusterSteamEconomy {

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
     * 运行结算（每 20 tick 一次，调用方 aTick%20==0）：探测蒸汽 totalSteamLps 与润滑液
     * {@link ClusterParams#LUBRICANT_LPS} 是否同时足量，足→两者原子实扣返回 true；
     * 不足→一分不扣，置红标，返回 false。totalSteamLps 为调用方（ExecutionPlan，M5 批）
     * 按每链独立加总口径算出的总需求，向上取整为整升后结算。
     *
     * @param cluster       集群总控（流体输入仓来源）
     * @param totalSteamLps 本周期蒸汽总需求（L/s）
     * @return true = 两项均已足额实扣；false = 任一不足，全程零扣
     */
    public boolean settleRun(MTESteamMineralLogisticsCluster cluster, double totalSteamLps) {
        return settle(cluster, totalSteamLps);
    }

    /**
     * 预热结算：蒸汽需求取 {@link ClusterParams#PREHEAT_STEAM_LPS}，润滑剂与运行结算同源，
     * 同原子语义——两项同时足量才统一实扣，任一不足全程零扣并置红标。
     *
     * @param cluster 集群总控（流体输入仓来源）
     * @return true = 两项均已足额实扣；false = 任一不足，全程零扣
     */
    public boolean settlePreheat(MTESteamMineralLogisticsCluster cluster) {
        return settle(cluster, ClusterParams.PREHEAT_STEAM_LPS);
    }

    /** @return 上次结算蒸汽不足（true = 蒸汽跨仓合计不足；结构失效/停机时应 clearFlags）。 */
    public boolean isSteamShortage() {
        return steamShortage;
    }

    /** @return 上次结算润滑足额（false = 润滑剂跨仓合计不足）。 */
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
     * 原子结算核心：先 hasEnoughAcross 跨仓合计探测蒸汽与润滑剂两项（仅模拟不消耗），
     * 均足量再统一 depleteFluidAcross 实扣；任一不足→全程零扣并按项置红标（蒸汽不足置
     * steamShortage，润滑剂不足拉低 lubricantOk），结算成功则两项红标复位为健康态。
     *
     * @param cluster       集群总控
     * @param totalSteamLps 蒸汽总需求（L/s，向上取整为整升）
     * @return true = 两项均已足额实扣；false = 任一不足或仓源不可达，全程零扣
     */
    private boolean settle(MTESteamMineralLogisticsCluster cluster, double totalSteamLps) {
        long steamLps = Math.max(0L, (long) Math.ceil(totalSteamLps));
        long lubricantLps = ClusterParams.LUBRICANT_LPS;
        lastSteamLps = steamLps;
        lastLubricantLps = lubricantLps;

        List<MTEHatchInput> hatches = cluster == null ? null : cluster.getClusterFluidInputHatches();
        if (hatches == null) {
            // 仓源不可达视同双项不足：零扣 + 置红标
            steamShortage = true;
            lubricantOk = false;
            return false;
        }

        FluidStack steam = Materials.Steam.getGas((int) Math.min(steamLps, Integer.MAX_VALUE));
        FluidStack lubricant = Materials.Lubricant.getFluid((int) Math.min(lubricantLps, Integer.MAX_VALUE));
        boolean steamEnough = GTSRHatchFluidAccess.hasEnoughAcross(hatches, steam);
        boolean lubricantEnough = GTSRHatchFluidAccess.hasEnoughAcross(hatches, lubricant);
        if (!steamEnough || !lubricantEnough) {
            // 原子失败：两项都未实扣（探测仅模拟），按项置红标
            steamShortage = !steamEnough;
            lubricantOk = lubricantEnough;
            return false;
        }
        GTSRHatchFluidAccess.depleteFluidAcross(hatches, steam);
        GTSRHatchFluidAccess.depleteFluidAcross(hatches, lubricant);
        steamShortage = false;
        lubricantOk = true;
        return true;
    }
}
