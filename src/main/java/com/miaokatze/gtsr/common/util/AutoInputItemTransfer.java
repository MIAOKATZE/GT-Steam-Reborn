// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.util;

import java.util.Arrays;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.ItemTransfer;
import com.gtnewhorizon.gtnhlib.item.StandardInventoryIterator;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.GTUtility;

/** Native source permissions and rollback, with GTSR's one attempted slot and target slot order. */
public final class AutoInputItemTransfer {

    private AutoInputItemTransfer() {}

    public static boolean pull(IInventory source, MTEHatchInputBus target, IGregTechTileEntity base,
        ForgeDirection front) {
        ForgeDirection sourceSide = front.getOpposite();
        int[] accessible = StandardInventoryIterator.getInventorySlotIndices(source, sourceSide)
            .clone();
        Arrays.sort(accessible);
        for (int slot : accessible) {
            if (slot < 0 || slot >= source.getSizeInventory()) continue;
            ItemStack available = source.getStackInSlot(slot);
            if (available == null || available.stackSize <= 0) continue;
            if (source instanceof ISidedInventory sided && !sided.canExtractItem(slot, available, sourceSide.ordinal()))
                continue;
            ItemTransfer transfer = new ItemTransfer();
            transfer.source(source, sourceSide);
            transfer.setSourceSlots(slot);
            transfer.sink(stack -> insertInSlotOrder(target, base, front, stack));
            transfer.setMaxItemsPerTransfer(Integer.MAX_VALUE);
            return transfer.transfer() > 0;
        }
        return false;
    }

    private static int insertInSlotOrder(MTEHatchInputBus target, IGregTechTileEntity base, ForgeDirection front,
        ImmutableItemStack stack) {
        ItemStack remaining = stack.toStack();
        for (int slot = 0; slot < target.getSizeInventory() && remaining.stackSize > 0; slot++) {
            if (!target.allowPutStack(base, slot, front, remaining)) continue;
            ItemStack existing = target.getStackInSlot(slot);
            if (existing != null && !GTUtility.areStacksEqual(existing, remaining)) continue;
            int limit = Math.min(
                target.getInventoryStackLimit(),
                existing == null ? remaining.getMaxStackSize() : existing.getMaxStackSize());
            int space = existing == null ? limit : limit - existing.stackSize;
            int moved = Math.min(remaining.stackSize, Math.max(0, space));
            if (moved == 0) continue;
            if (existing == null) {
                ItemStack inserted = remaining.copy();
                inserted.stackSize = moved;
                target.setInventorySlotContents(slot, inserted);
            } else {
                existing.stackSize += moved;
            }
            remaining.stackSize -= moved;
        }
        return remaining.stackSize;
    }
}
