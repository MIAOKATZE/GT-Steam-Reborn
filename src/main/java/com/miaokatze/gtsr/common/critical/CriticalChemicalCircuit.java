package com.miaokatze.gtsr.common.critical;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug;
import gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

/** Read-only selector from bus circuit slots and physical inventory, excluding ME filters and network stock. */
public final class CriticalChemicalCircuit {

    public static final int MISSING = 0;
    public static final int INVALID = -1;
    public static final int CONFLICT = -2;

    private CriticalChemicalCircuit() {}

    public static int read(List<MTEHatchInputBus> busses) {
        int selected = MISSING;
        Set<MTEHatchInputBus> seen = Collections.newSetFromMap(new IdentityHashMap<MTEHatchInputBus, Boolean>());
        for (MTEHatchInputBus bus : busses) {
            if (bus == null || !bus.isValid() || !seen.add(bus) || bus instanceof MTEHatchInputBusDebug) continue;
            if (bus.getClass() == MTEHatchInputBusME.class) {
                // GT205's dedicated circuit slot is a zero-size config; the manual slot holds real items.
                // Read those two native slots directly, never slots[].config or virtual getStackInSlot.
                MTEHatchInputBusME me = (MTEHatchInputBusME) bus;
                selected = merge(selected, me.mInventory[me.getCircuitSlot()], true);
                if (selected < 0) return selected;
                selected = merge(selected, me.mInventory[me.getManualSlot()]);
            } else if (!(bus instanceof IRecipeProcessingAwareHatch)) {
                int circuitSlot = bus.getCircuitSlot();
                if (circuitSlot >= 0 && circuitSlot < bus.mInventory.length) {
                    selected = merge(selected, bus.mInventory[circuitSlot], true);
                    if (selected < 0) return selected;
                }
                for (int slot = 0; slot < bus.mInventory.length; slot++) {
                    if (slot == circuitSlot) continue;
                    selected = merge(selected, bus.mInventory[slot]);
                    if (selected < 0) return selected;
                }
            }
            if (selected < 0) return selected;
        }
        return selected;
    }

    private static int merge(int selected, ItemStack stack) {
        return merge(selected, stack, false);
    }

    private static int merge(int selected, ItemStack stack, boolean circuitSlot) {
        if (stack == null || stack.stackSize < 0
            || (!circuitSlot && stack.stackSize == 0)
            || stack.getItem() != ItemList.Circuit_Integrated.getItem()) return selected;
        int circuit = stack.getItemDamage();
        if (circuit < 2 || circuit > 9) return INVALID;
        return selected == MISSING || selected == circuit ? circuit : CONFLICT;
    }
}
