package com.miaokatze.gtsr.common.gui.mui2.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.ItemHandlerHelper;
import com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PlayerSlotGroup;

/** Fixed inventory registration and ordinary, ordered stack transfers shared by the two P4 windows. */
public final class InventorySlots {

    private InventorySlots() {}

    /** Register after machine slots, retaining legacy main9..35 then hotbar0..8 container indices. */
    public static List<ModularSlot> registerPlayer(PanelSyncManager manager, EntityPlayer player) {
        PlayerMainInvWrapper handler = new PlayerMainInvWrapper(player.inventory);
        manager.registerSlotGroup(new PlayerSlotGroup(PlayerSlotGroup.NAME));
        List<ModularSlot> slots = new ArrayList<>(36);
        for (int n = 0; n < 36; n++) {
            int index = n < 27 ? n + 9 : n - 27;
            ModularSlot slot = new PureInventorySlot(handler, index).slotGroup(PlayerSlotGroup.NAME);
            slot.changeListener(
                (stack, amount, client, init) -> { if (!client && !init) player.inventory.markDirty(); });
            manager.itemSlot("player", index, slot);
            slots.add(slot);
        }
        return slots;
    }

    public static SlotGroupWidget playerWidgets() {
        return SlotGroupWidget.playerInventory(false);
    }

    public static List<ModularSlot> reverse(List<ModularSlot> slots) {
        List<ModularSlot> result = new ArrayList<>(slots);
        Collections.reverse(result);
        return result;
    }

    /** Returns a non-null remainder; source mutation belongs to ModularContainer.transferStackInSlot. */
    public static ItemStack moveInto(ItemStack offered, List<ModularSlot> targets) {
        ItemStack remainder = offered.copy();
        for (int pass = 0; pass < 2 && remainder.stackSize > 0; pass++) {
            for (ModularSlot target : targets) {
                if (remainder.stackSize < 1) break;
                if (!target.func_111238_b() || !target.isItemValid(remainder)) continue;
                ItemStack current = target.getStack();
                boolean empty = current == null || current.stackSize < 1;
                if ((pass == 0 && empty) || (pass == 1 && !empty)) continue;
                if (!empty && !ItemHandlerHelper.canItemStacksStack(current, remainder)) continue;
                int count = Math
                    .min(remainder.stackSize, target.getItemStackLimit(remainder) - (empty ? 0 : current.stackSize));
                if (count < 1) continue;
                ItemStack inserted = empty ? remainder.copy() : current.copy();
                inserted.stackSize = (empty ? 0 : current.stackSize) + count;
                target.putStack(inserted);
                remainder.stackSize -= count;
            }
        }
        return remainder;
    }
}
