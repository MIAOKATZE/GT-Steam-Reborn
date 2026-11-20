// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBusCompressed;
import gregtech.api.util.GTUtility;
import gregtech.common.inventory.AEInventory;

/** Plans a linear fill of empty, interchangeable native buses in one server tick. */
final class CenterBatchOutput {

    private static final class Fill {

        final MTEHatchOutputBus bus;
        final ItemStack[] inventory, staged;

        Fill(MTEHatchOutputBus bus, ItemStack[] inventory) {
            this.bus = bus;
            this.inventory = inventory;
            this.staged = new ItemStack[inventory.length];
        }

        void commit() {
            System.arraycopy(staged, 0, inventory, 0, staged.length);
            bus.markDirty();
        }
    }

    private static final class CompressedFill {

        final MTEHatchOutputBusCompressed bus;
        final AEInventory inventory;
        final IAEItemStack[] staged;
        int size;

        CompressedFill(MTEHatchOutputBusCompressed bus) {
            this.bus = bus;
            this.inventory = bus.getAEInventory();
            this.staged = new IAEItemStack[inventory.getSlots()];
        }

        void commit() {
            for (int slot = 0; slot < size; slot++) inventory.setStackInSlot(slot, staged[slot]);
            bus.markDirty();
        }
    }

    private CenterBatchOutput() {}

    static Runnable emptyBusses(List<IOutputBus> busses, List<ItemStack> outputs) {
        if (busses.isEmpty()) return null;
        if (busses.get(0)
            .getClass() == MTEHatchOutputBusCompressed.class) return emptyCompressedBusses(busses, outputs);
        for (IOutputBus output : busses) {
            if (output.getClass() != MTEHatchOutputBus.class) return null;
            MTEHatchOutputBus bus = (MTEHatchOutputBus) output;
            if (bus.isLocked()) return null;
            for (ItemStack item : bus.getRealInventory()) if (item != null) return null;
        }
        List<Fill> fills = new ArrayList<>();
        Fill fill = null;
        int nextBus = 0, slot = 0;
        for (ItemStack item : outputs) {
            int left = item.stackSize;
            while (left > 0) {
                if (fill == null || slot == fill.staged.length) {
                    if (nextBus == busses.size()) return null;
                    MTEHatchOutputBus bus = (MTEHatchOutputBus) busses.get(nextBus++);
                    ItemStack[] inventory = bus.getRealInventory();
                    if (inventory.length == 0) return null;
                    fill = new Fill(bus, inventory);
                    fills.add(fill);
                    slot = 0;
                }
                int limit = Math.min(fill.bus.getInventoryStackLimit(), fill.bus.getStackSizeLimit(-1, item));
                if (limit <= 0) return null;
                int count = Math.min(left, limit);
                fill.staged[slot++] = GTUtility.copyAmountUnsafe(count, item);
                left -= count;
            }
        }
        return () -> fills.forEach(Fill::commit);
    }

    private static Runnable emptyCompressedBusses(List<IOutputBus> busses, List<ItemStack> outputs) {
        List<CompressedFill> fills = new ArrayList<>(busses.size());
        for (IOutputBus output : busses) {
            if (output.getClass() != MTEHatchOutputBusCompressed.class) return null;
            MTEHatchOutputBusCompressed bus = (MTEHatchOutputBusCompressed) output;
            if (bus.isLocked()) return null;
            AEInventory inventory = bus.getAEInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (inventory.getAEStackInSlot(slot) != null) return null;
            }
            fills.add(new CompressedFill(bus));
        }
        int index = 0;
        for (ItemStack item : outputs) {
            IAEItemStack stack = AEItemStack.create(item);
            long left = stack.getStackSize();
            while (left > 0) {
                while (index < fills.size() && fills.get(index).size == fills.get(index).staged.length) index++;
                if (index == fills.size()) return null;
                CompressedFill fill = fills.get(index);
                long limit = fill.inventory.getAESlotLimit(fill.size, stack);
                if (limit <= 0) return null;
                long count = Math.min(left, limit);
                fill.staged[fill.size++] = stack.copy()
                    .setStackSize(count);
                left -= count;
            }
        }
        return () -> { for (CompressedFill fill : fills) if (fill.size > 0) fill.commit(); };
    }
}
