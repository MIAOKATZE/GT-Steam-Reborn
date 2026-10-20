package com.miaokatze.gtsr.common.util;

import net.minecraft.item.ItemStack;

import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;

/** RC2 output policy: retain native ME buffering when its cache, rather than its filter, is full. */
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
            return ok;
        }
        return bus.storePartial(stack, simulate);
    }
}
