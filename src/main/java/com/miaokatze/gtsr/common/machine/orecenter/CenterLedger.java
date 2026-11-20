package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import gregtech.api.util.GTUtility;

/** Bounded controller ledger. Quantities are recorded at debit, delivery and bonus generation respectively. */
final class CenterLedger {

    private static final int LIMIT = 2048;
    private final Map<GTUtility.ItemId, Entry> entries = new LinkedHashMap<>();
    private long revision;

    private static final class Entry {

        final ItemStack sample;
        final long[] totals;

        Entry(ItemStack sample, long[] totals) {
            this.sample = sample;
            this.totals = totals;
        }
    }

    void add(ItemStack stack, int column, int amount) {
        if (amount <= 0) return;
        GTUtility.ItemId id = GTUtility.ItemId.createNoCopy(stack);
        Entry entry = entries.get(id);
        if (entry == null) {
            if (entries.size() == LIMIT || stack.hasTagCompound() && stack.getTagCompound()
                .toString()
                .length() > 512) return;
            ItemStack sample = stack.copy();
            sample.stackSize = 1;
            entry = new Entry(sample, new long[3]);
            entries.put(GTUtility.ItemId.createNoCopy(sample), entry);
        }
        long added = Math.min(amount, Long.MAX_VALUE - entry.totals[column]);
        if (added > 0) {
            entry.totals[column] += added;
            revision++;
        }
    }

    long revision() {
        return revision;
    }

    NBTTagList save() {
        NBTTagList result = new NBTTagList();
        for (Entry entry : entries.values()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("Item", entry.sample.writeToNBT(new NBTTagCompound()));
            for (int i = 0; i < 3; i++) row.setLong("C" + i, entry.totals[i]);
            result.appendTag(row);
        }
        return result;
    }

    void load(NBTTagList rows) {
        clear();
        for (int i = 0; i < Math.min(LIMIT, rows.tagCount()); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            ItemStack item = ItemStack.loadItemStackFromNBT(row.getCompoundTag("Item"));
            if (item == null) continue;
            entries.put(
                GTUtility.ItemId.createNoCopy(item),
                new Entry(item, new long[] { row.getLong("C0"), row.getLong("C1"), row.getLong("C2") }));
        }
    }

    void clear() {
        entries.clear();
        revision++;
    }
}
