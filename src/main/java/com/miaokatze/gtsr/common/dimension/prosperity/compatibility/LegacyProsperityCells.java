// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.compatibility;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.MaterialBuilder;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/**
 * Temporary saved-cell compatibility. Remove only after supported historical worlds have migrated.
 * A safe alias still permits native old-empty-cell filling; new consumers always use universal cells.
 */
public final class LegacyProsperityCells {

    private static final Map<Materials, Integer> ALIASES = new LinkedHashMap<>();
    private static boolean containersRegistered;

    private LegacyProsperityCells() {}

    public static void enable(MaterialBuilder builder, int legacyId) {
        if (legacyId >= 0) builder.addCell();
    }

    public static void remember(Materials material, int legacyId) {
        if (legacyId < 0) return;
        if (legacyId >= 1000) throw new IllegalArgumentException("Invalid legacy cell ID");
        material.mMetaItemSubID = legacyId;
        ALIASES.put(material, legacyId);
    }

    /** Called in the existing init probe, after GT preInit has generated the saved GT cell items. */
    public static void registerContainers() {
        if (containersRegistered) return;
        for (Map.Entry<Materials, Integer> entry : ALIASES.entrySet()) {
            Materials material = entry.getKey();
            if (material.mMetaItemSubID != entry.getValue()
                || GregTechAPI.sGeneratedMaterials[entry.getValue()] != material)
                throw new IllegalStateException("Legacy cell identity changed: " + material.mName);
            ItemStack cell = GTOreDictUnificator.get(OrePrefixes.cell, material, 1L);
            FluidStack fluid = material.mGas != null ? material.getGas(1000) : material.getFluid(1000);
            if (cell == null || fluid == null)
                throw new IllegalStateException("Missing safe legacy cell: " + material.mName);
            FluidStack previous = GTUtility.getFluidForFilledItem(cell, true);
            if (previous == null) {
                if (!FluidContainerRegistry.registerFluidContainer(fluid, cell, ItemList.Cell_Empty.get(1)))
                    throw new IllegalStateException("Cannot register safe legacy cell: " + material.mName);
            } else if (previous.amount != 1000 || !previous.isFluidEqual(fluid)) {
                throw new IllegalStateException("Ambiguous legacy cell: " + material.mName);
            }
        }
        containersRegistered = true;
    }
}
