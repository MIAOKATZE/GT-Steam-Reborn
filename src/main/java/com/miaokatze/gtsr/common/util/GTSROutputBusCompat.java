package com.miaokatze.gtsr.common.util;

import net.minecraft.item.ItemStack;

import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;

/**
 * v1.10.74：ME 输出总线 storePartial 兼容层。
 *
 * <p>
 * GT5U 的 ME 输出总线（MTEHatchOutputBusME）"空间"语义与 GT 普通输出总线不同：
 * 空间 = 本地 cache（默认 1600，插存储元件扩容），实放 addToCache 无容量门控（每 40 tick flush 网络，
 * GT5U 原生缓冲语义）；simulate 探测受 cache 空间门控——cache 满（网络未连接/网络满/flush 滞留）时
 * 返回 false 且不修改入参，导致 GTSR 的"探测→实放"两段式把 ME 总线误判为无空位（采矿节点 pending 卡死、
 * 聚合器矿石滞留）。
 *
 * <p>
 * 兼容语义：simulate 探测返回 false 但过滤放行（isFilteredToItem）时，视为全部可放（入参 stackSize 置 0）——
 * 探测与实放（进 cache 缓冲）一致；cell 分区白名单拒绝（过滤拒绝）保持拒绝；shouldCheck
 * （checkMode+cacheMode+cell）场景 cache 未满时 storePartial 原样返回，仅 cell 满才触发本特判，属合理放宽
 * （实放本就不丢）。非 simulate 与普通/压缩总线直通原语义。
 *
 * <p>
 * beta-1 差异（GT5U 5.09.52.594，v1.10.74 补充）：beta-1 的 MTEHatchOutputMEBase.storePartial 成功时
 * 返回 true 但不清零入参 stack.stackSize（beta-2 经 AEItemStack 回写 0）——GTSR「探测扣减量=可放量」
 * 约定（fits 计算）失效，探测恒判「无空位」。兼容层追加：simulate 成功但入参未扣减（stackSize > 0）时
 * 视为全部可放（置 0）；beta-2 成功时已回写 0，该分支不触发，两版统一处理。
 */
public final class GTSROutputBusCompat {

    private GTSROutputBusCompat() {}

    public static boolean storePartial(MTEHatchOutputBus bus, ItemStack stack, boolean simulate) {
        if (bus instanceof MTEHatchOutputBusME meBus && simulate) {
            boolean ok = meBus.storePartial(stack, true);
            if (!ok && meBus.isFilteredToItem(GTUtility.ItemId.createNoCopy(stack))) {
                // cache 满（网络未连接/网络满/flush 滞留）但过滤放行：视为全部可放（实放进 cache 缓冲）
                stack.stackSize = 0;
                return true;
            }
            if (ok && stack.stackSize > 0) {
                // beta-1（5.09.52.594）：成功但未扣减入参 → 视为全部可放；
                // beta-2（5.09.54.20）成功时已回写 stackSize=0，不触发
                stack.stackSize = 0;
            }
            return ok;
        }
        return bus.storePartial(stack, simulate);
    }
}
