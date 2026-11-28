// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.fluid;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;

/** Native IC2 universal cells; each stack keeps its fluid identity in native Fluid NBT. */
public final class ProsperityFluidContainers {

    private ProsperityFluidContainers() {}

    public static ItemStack emptyCell(int count) {
        if (count <= 0) throw new IllegalArgumentException("Cell count must be positive");
        ItemStack cell = ItemList.Cell_Universal_Fluid.get(1);
        if (cell == null || cell.getItem() == null)
            throw new IllegalStateException("Native universal fluid cell is unavailable");
        return GTUtility.copyAmountUnsafe(count, cell);
    }

    public static ItemStack filledCell(FluidStack contents) {
        return filledCell(contents, 1);
    }

    /** Fill one container through GT's native API, then copy the requested stack count. */
    public static ItemStack filledCell(FluidStack contents, int count) {
        if (contents == null || contents.getFluid() == null || contents.amount != 1000)
            throw new IllegalArgumentException("A universal cell requires exactly 1000L");
        if (count <= 0) throw new IllegalArgumentException("Cell count must be positive");
        ItemStack filled = GTUtility.fillFluidContainer(contents.copy(), emptyCell(1), false, true);
        FluidStack actual = GTUtility.getFluidForFilledItem(filled, true);
        if (actual == null || actual.amount != 1000 || !actual.isFluidEqual(contents)) throw new IllegalStateException(
            "Native universal cell did not preserve fluid: " + contents.getFluid()
                .getName());
        ItemStack empty = GTUtility.getContainerForFilledItem(filled, true);
        if (empty == null || !ItemList.Cell_Universal_Fluid.isStackEqual(empty, false, true)
            || GTUtility.getFluidForFilledItem(empty, true) != null)
            throw new IllegalStateException("Native universal cell did not return its empty shell");
        return GTUtility.copyAmountUnsafe(count, filled);
    }
}
