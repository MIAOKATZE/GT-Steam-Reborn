package com.miaokatze.gtsr.common.gui.mui2.inventory;

import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

/** Ordinary inventories only: validity/capacity queries must never temporarily clear real storage. */
public class PureInventorySlot extends ModularSlot {

    private Predicate<ItemStack> accepts = stack -> true;
    private boolean allowPut = true;
    private int limit = Integer.MAX_VALUE;

    public PureInventorySlot(IItemHandler handler, int index) {
        super(handler, index);
    }

    public PureInventorySlot limit(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Slot limit must be positive");
        this.limit = limit;
        return this;
    }

    @Override
    public PureInventorySlot filter(Predicate<ItemStack> filter) {
        this.accepts = filter == null ? stack -> true : filter;
        return this;
    }

    @Override
    public PureInventorySlot canPut(boolean allowPut) {
        this.allowPut = allowPut;
        super.canPut(allowPut);
        return this;
    }

    @Override
    public PureInventorySlot accessibility(boolean canPut, boolean canTake) {
        this.allowPut = canPut;
        super.accessibility(canPut, canTake);
        return this;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return allowPut && stack != null
            && stack.stackSize > 0
            && accepts.test(stack)
            && getItemHandler().isItemValid(getSlotIndex(), stack);
    }

    @Override
    public int getSlotStackLimit() {
        return Math.min(limit, getItemHandler().getSlotLimit(getSlotIndex()));
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return stack == null ? 0 : Math.min(getSlotStackLimit(), stack.getMaxStackSize());
    }
}
