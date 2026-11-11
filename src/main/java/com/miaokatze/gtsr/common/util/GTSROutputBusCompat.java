// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

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
