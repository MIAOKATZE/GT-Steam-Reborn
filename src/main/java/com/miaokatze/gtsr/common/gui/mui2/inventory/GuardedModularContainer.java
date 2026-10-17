package com.miaokatze.gtsr.common.gui.mui2.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;

/** Rechecks the captured inventory identity on every click, including between server ticks. */
public class GuardedModularContainer extends ModularContainer {

    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (player != getPlayer() || !canInteractWith(player) || mode < 0 || mode > 6) return null;
        if (slotId >= inventorySlots.size()) return null;
        // Negative ids are valid for outside-click and drag phases, but not for a real slot lookup.
        if (slotId < 0 && mode != 0 && mode != 5) return null;
        return super.slotClick(slotId, button, mode, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (player != getPlayer() || !canInteractWith(player) || index < 0 || index >= inventorySlots.size())
            return null;
        return super.transferStackInSlot(player, index);
    }
}
