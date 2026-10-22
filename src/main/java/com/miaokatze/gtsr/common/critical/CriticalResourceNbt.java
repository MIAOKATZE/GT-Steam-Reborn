package com.miaokatze.gtsr.common.critical;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

/** Shared sparse resource journals, including counts larger than vanilla ItemStack NBT can store. */
final class CriticalResourceNbt {

    private CriticalResourceNbt() {}

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
        int size = length;
        if (length < 0) {
            size = 0;
            for (int i = 0; i < list.tagCount(); i++) size = Math.max(
                size,
                list.getCompoundTagAt(i)
                    .getInteger("slot") + 1);
        }
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getInteger("slot");
            if (slot < 0 || slot >= size) continue;
            ItemStack item = ItemStack.loadItemStackFromNBT(t);
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
        int size = length;
        if (length < 0) {
            size = 0;
            for (int i = 0; i < list.tagCount(); i++) size = Math.max(
                size,
                list.getCompoundTagAt(i)
                    .getInteger("slot") + 1);
        }
        FluidStack[] fluids = new FluidStack[size];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getInteger("slot");
            if (slot >= 0 && slot < size) fluids[slot] = FluidStack.loadFluidStackFromNBT(t);
        }
        return fluids;
    }

}
