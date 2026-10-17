package com.miaokatze.gtsr.common.gui.mui2.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;

/** All real slots are registered by ItemSlotSH; only the legacy transfer policy is customized. */
public final class AggregatorConfigModularContainer extends GuardedModularContainer {

    public static final int MACHINE_SLOTS = 25;
    public static final int TOTAL_SLOTS = 61;

    @Override
    protected ItemStack transferItem(ModularSlot fromSlot, ItemStack offered) {
        int source = inventorySlots.indexOf(fromSlot);
        List<ModularSlot> targets = new ArrayList<>();
        if (source >= 0 && source < MACHINE_SLOTS) {
            for (int n = TOTAL_SLOTS - 1; n >= MACHINE_SLOTS; n--) targets.add(getModularSlot(n));
        } else if (source >= MACHINE_SLOTS && MTECrustMatterAggregator.isDimensionDisplayItem(offered)) {
            for (int n = 1; n < MACHINE_SLOTS; n++) targets.add(getModularSlot(n));
            targets.add(getModularSlot(0));
        }
        return InventorySlots.moveInto(offered, targets);
    }
}
