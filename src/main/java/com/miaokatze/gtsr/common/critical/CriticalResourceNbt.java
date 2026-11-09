package com.miaokatze.gtsr.common.critical;

import java.util.BitSet;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

/** Shared sparse resource journals, including counts larger than vanilla ItemStack NBT can store. */
final class CriticalResourceNbt {

    // Read allocation budget, not a historical writer limit. Larger records require manual recovery.
    static final int MAX_JOURNAL_SLOTS = 65_536;

    private CriticalResourceNbt() {}

    static final class InvalidJournalException extends IllegalArgumentException {

        InvalidJournalException(String message) {
            super(message);
        }
    }

    static NBTTagList journal(NBTTagCompound n, String key) {
        if (n.hasKey(key) && !n.hasKey(key, 9))
            throw new InvalidJournalException("Resource journal is not a list: " + key);
        if (n.hasKey(key, 9)) {
            NBTTagList list = (NBTTagList) n.getTag(key);
            if (list.tagCount() > 0 && list.func_150303_d() != 10)
                throw new InvalidJournalException("Resource journal entries are not compounds: " + key);
            return list;
        }
        return new NBTTagList();
    }

    /** Validate the complete sparse index before allocating or decoding any resource array. */
    private static int readSize(NBTTagList list, int length) {
        int limit = length < 0 ? MAX_JOURNAL_SLOTS : length;
        int count = list.tagCount();
        if (count > limit) throw new InvalidJournalException("Too many resource journal entries: " + count);
        BitSet occupied = new BitSet();
        int size = length < 0 ? 0 : length;
        for (int i = 0; i < count; i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasKey("slot", 3)) throw new InvalidJournalException("Missing integer resource slot");
            int slot = entry.getInteger("slot");
            long end = (long) slot + 1;
            if (slot < 0 || end > limit)
                throw new InvalidJournalException("Resource slot outside journal budget: " + slot);
            if (occupied.get(slot)) throw new InvalidJournalException("Duplicate resource slot: " + slot);
            occupied.set(slot);
            if (length < 0) size = Math.max(size, (int) end);
        }
        return size;
    }

    static NBTTagList writeItems(ItemStack[] items) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < items.length; i++) if (items[i] != null) {
            NBTTagCompound t = new NBTTagCompound();
            items[i].writeToNBT(t);
            // Vanilla Count is a byte. Escrow and journal deliberately retain full counts.
            t.setInteger("fullCount", items[i].stackSize);
            t.setInteger("slot", i);
            list.appendTag(t);
        }
        return list;
    }

    static ItemStack[] readItems(NBTTagList list, int length) {
        int size = readSize(list, length);
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getInteger("slot");
            ItemStack item = ItemStack.loadItemStackFromNBT(t);
            if (item == null) throw new InvalidJournalException("Unknown resource item at slot " + slot);
            if (item != null) {
                item.stackSize = t.hasKey("fullCount") ? t.getInteger("fullCount") : item.stackSize;
                if (item.stackSize > 0) items[slot] = item;
            }
        }
        return items;
    }

    static NBTTagList writeFluids(FluidStack[] fluids) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < fluids.length; i++) if (fluids[i] != null) {
            NBTTagCompound t = new NBTTagCompound();
            fluids[i].writeToNBT(t);
            t.setInteger("slot", i);
            list.appendTag(t);
        }
        return list;
    }

    static FluidStack[] readFluids(NBTTagList list, int length) {
        int size = readSize(list, length);
        FluidStack[] fluids = new FluidStack[size];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getInteger("slot");
            fluids[slot] = FluidStack.loadFluidStackFromNBT(t);
            if (fluids[slot] == null) throw new InvalidJournalException("Unknown resource fluid at slot " + slot);
        }
        return fluids;
    }

}
