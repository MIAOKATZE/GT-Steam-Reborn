package com.miaokatze.gtsr.common.util;

import net.minecraft.item.ItemStack;

import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/** Stable call site for the RC2 native output contract. */
public final class GTSROutputBusCompat {

    private GTSROutputBusCompat() {}

    public static boolean storePartial(MTEHatchOutputBus bus, ItemStack stack, boolean simulate) {
        return bus.storePartial(stack, simulate);
    }
}
