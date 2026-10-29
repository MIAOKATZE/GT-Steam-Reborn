package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalInputBusME;

import appeng.me.GridAccessException;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * Exact funding of a single recipe's ME deficit through GT's native aware-input lifecycle.
 * The caller owns its physical inventory and must only debit it after SUCCESS.
 * COMMIT_FAILED is an uncertain, potentially partial AE debit: persist the requested deficit,
 * lock further funding, and do not create either escrow or a refund from these snapshots.
 */
public final class CriticalNativeMEFunding {

    public enum Result {
        SUCCESS,
        SHORTAGE,
        COMMIT_FAILED
    }

    private CriticalNativeMEFunding() {}

    public static final class ItemConsumption {

        public final Result result;
        public final long amount;

        public ItemConsumption(Result result, long amount) {
            this.result = result;
            this.amount = amount;
        }
    }

    /** Consume every matching native stocking view, once per backing ME inventory. */
    public static ItemConsumption consumeAllItem(MTEMultiBlockBase host, List<MTEHatchInputBus> inputBusses,
        ItemStack wanted) {
        List<MTEHatchInputBusME> opened = new ArrayList<>();
        Map<Object, ItemChoice> selected = new java.util.IdentityHashMap<>();
        long amount = 0;
        boolean settled = true;
        boolean longExtractionAttempted = false;
        try {
            for (MTEHatchInputBus bus : inputBusses) {
                if (!(bus instanceof MTEHatchInputBusME) || !bus.isValid()) continue;
                MTEHatchInputBusME me = (MTEHatchInputBusME) bus;
                if (!me.isAllowedToWork() || opened.contains(me)) continue;
                if (me instanceof MTEHatchCriticalInputBusME critical) {
                    for (int slot = 0; slot < MTEHatchInputBusME.SLOT_COUNT; slot++) {
                        CriticalEnergyPorts.LongItemSource source = critical.getLongInputSource(slot);
                        if (source == null || !sameItem(source.prototype(), wanted)) continue;
                        long available = source.available();
                        ItemChoice previous = selected.get(source.networkIdentity());
                        if (available > 0 && (previous == null || previous.amount < available))
                            selected.put(source.networkIdentity(), new ItemChoice(source, available));
                    }
                    continue;
                }
                Object inventory;
                try {
                    inventory = me.getProxy()
                        .getStorage()
                        .getItemInventory();
                } catch (GridAccessException unavailable) {
                    // A disconnected stocking view offers no inputs and has not requested a debit.
                    continue;
                }
                opened.add(me);
                me.startRecipeProcessing();
                for (int slot = 0; slot < MTEHatchInputBusME.SLOT_COUNT; slot++) {
                    ItemStack ref = me.getStackInSlot(slot);
                    if (ref == null || ref.stackSize <= 0 || !sameItem(ref, wanted)) continue;
                    ItemChoice previous = selected.get(inventory);
                    if (previous == null || previous.amount < ref.stackSize)
                        selected.put(inventory, new ItemChoice(ref));
                }
            }
            for (ItemChoice choice : selected.values()) {
                if (choice.source == null) {
                    amount = Math.addExact(amount, choice.ref.stackSize);
                    choice.ref.stackSize = 0;
                } else {
                    longExtractionAttempted = true;
                    long extracted = choice.source.extract(choice.amount, true);
                    if (extracted < 0 || extracted > choice.amount) {
                        settled = false;
                        break;
                    }
                    amount = Math.addExact(amount, extracted);
                    if (extracted != choice.amount) {
                        settled = false;
                        break;
                    }
                }
            }
        } catch (Exception failure) {
            settled = false;
        } finally {
            for (MTEHatchInputBusME me : opened) {
                try {
                    CheckRecipeResult result = me.endRecipeProcessing(host);
                    if (result == null || !result.wasSuccessful()) settled = false;
                } catch (RuntimeException failure) {
                    settled = false;
                }
            }
        }
        // Opening/closing an unchanged view cannot create an uncertain item extraction.
        return new ItemConsumption(
            settled || amount == 0 && !longExtractionAttempted ? Result.SUCCESS : Result.COMMIT_FAILED,
            amount);
    }

    /** Read-only recipe selection view. These copies are never evidence of a committed debit. */
    public static ItemStack[] snapshotItems(List<MTEHatchInputBus> inputBusses) {
        List<ItemStack> result = new ArrayList<>();
        java.util.Set<MTEHatchInputBus> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (MTEHatchInputBus bus : inputBusses) {
            if (!(bus instanceof MTEHatchInputBusME me) || !bus.isValid() || !seen.add(bus) || !me.isAllowedToWork())
                continue;
            // Native display snapshots are int-sized. Refreshing them does not open a debit window.
            try {
                for (int slot = 0; slot < MTEHatchInputBusME.SLOT_COUNT; slot++) me.updateInformationSlot(slot);
                for (ItemStack stack : me.getItemsForHoloGlasses()) {
                    if (stack == null || stack.stackSize <= 0) continue;
                    boolean found = false;
                    for (ItemStack existing : result) if (sameItem(existing, stack)) {
                        existing.stackSize = Math.max(existing.stackSize, stack.stackSize);
                        found = true;
                        break;
                    }
                    if (!found) result.add(stack.copy());
                }
            } catch (GridAccessException unavailable) {
                // An offline stocking view offers no recipe-selection promise.
            }
        }
        return result.toArray(new ItemStack[result.size()]);
    }

    /** Uses the native hatch's non-processing SIMULATE query, including configured fluid tags. */
    public static FluidStack[] snapshotFluids(List<MTEHatchInput> inputHatches) {
        List<FluidStack> result = new ArrayList<>();
        for (MTEHatchInput hatch : inputHatches) {
            if (!(hatch instanceof MTEHatchInputME) || !hatch.isValid()) continue;
            MTEHatchInputME me = (MTEHatchInputME) hatch;
            if (!me.isAllowedToWork()) continue;
            for (FluidTankInfo tank : me.getTankInfo(ForgeDirection.UNKNOWN)) {
                if (tank == null || tank.fluid == null || tank.fluid.amount <= 0) continue;
                FluidStack stack = tank.fluid;
                boolean found = false;
                for (FluidStack existing : result) if (existing.isFluidEqual(stack)) {
                    existing.amount = Math.max(existing.amount, stack.amount);
                    found = true;
                    break;
                }
                if (!found) result.add(stack.copy());
            }
        }
        return result.toArray(new FluidStack[result.size()]);
    }

    /**
     * Only network-backed stocking slots participate. Never import the returned references into
     * machine storage. Duplicate exposure of one type across ME hatches is not additive, matching
     * GT's native input collector and preventing multiple views of one network from overbooking it.
     * No AE API, synthetic inventory setter, or dual-input cache is used.
     */
    public static Result fund(MTEMultiBlockBase host, List<MTEHatchInputBus> inputBusses,
        List<MTEHatchInput> inputHatches, ItemStack[] requiredItems, FluidStack[] requiredFluids) {
        List<ItemStack> items = normalizeItems(requiredItems);
        List<FluidStack> fluids = normalizeFluids(requiredFluids);
        if (items == null || fluids == null) return Result.SHORTAGE;
        if (items.isEmpty() && fluids.isEmpty()) return Result.SUCCESS;

        List<MTEHatchInputBusME> openedBusses = new ArrayList<>();
        List<MTEHatchInputME> openedHatches = new ArrayList<>();
        List<ItemStack> itemRefs = new ArrayList<>();
        List<FluidStack> fluidRefs = new ArrayList<>();
        List<ItemDebit> itemDebits = new ArrayList<>();
        List<FluidDebit> fluidDebits = new ArrayList<>();
        boolean reserved = false;
        boolean settled = true;
        try {
            if (!items.isEmpty()) for (MTEHatchInputBus bus : inputBusses) {
                if (!(bus instanceof MTEHatchInputBusME) || !bus.isValid()) continue;
                MTEHatchInputBusME me = (MTEHatchInputBusME) bus;
                if (!me.isAllowedToWork()) continue;
                if (openedBusses.contains(me)) continue;
                openedBusses.add(me);
                me.startRecipeProcessing();
                for (int slot = 0; slot < MTEHatchInputBusME.SLOT_COUNT; slot++) {
                    ItemStack ref = me.getStackInSlot(slot);
                    if (ref != null && ref.stackSize > 0) itemRefs.add(ref);
                }
            }
            if (!fluids.isEmpty()) for (MTEHatchInput hatch : inputHatches) {
                if (!(hatch instanceof MTEHatchInputME) || !hatch.isValid()) continue;
                MTEHatchInputME me = (MTEHatchInputME) hatch;
                if (!me.isAllowedToWork()) continue;
                if (openedHatches.contains(me)) continue;
                openedHatches.add(me);
                me.startRecipeProcessing();
                for (FluidStack ref : me.getStoredFluids()) {
                    if (ref != null && ref.amount > 0) fluidRefs.add(ref);
                }
            }
            // Plan every exact debit before changing any native snapshot.
            boolean sufficient = true;
            for (ItemStack wanted : items) {
                ItemStack best = null;
                for (ItemStack ref : itemRefs) {
                    if (sameItem(ref, wanted) && (best == null || ref.stackSize > best.stackSize)) best = ref;
                }
                if (best == null || best.stackSize < wanted.stackSize) {
                    sufficient = false;
                } else itemDebits.add(new ItemDebit(best, wanted.stackSize));
            }
            for (FluidStack wanted : fluids) {
                FluidStack best = null;
                for (FluidStack ref : fluidRefs) {
                    if (ref.isFluidEqual(wanted) && (best == null || ref.amount > best.amount)) best = ref;
                }
                if (best == null || best.amount < wanted.amount) {
                    sufficient = false;
                } else fluidDebits.add(new FluidDebit(best, wanted.amount));
            }
            if (sufficient) {
                for (ItemDebit debit : itemDebits) debit.ref.stackSize -= debit.amount;
                for (FluidDebit debit : fluidDebits) debit.ref.amount -= debit.amount;
                reserved = true;
            }
        } catch (RuntimeException failure) {
            // Before reservation nothing has been requested from AE; finally still closes windows.
            settled = false;
        } finally {
            // Native end may partially extract and then fail. Close each window exactly once and
            // preserve the failure; GT has no rollback or actual-extracted-amount result here.
            for (MTEHatchInputBusME me : openedBusses) {
                try {
                    CheckRecipeResult result = me.endRecipeProcessing(host);
                    if (result == null || !result.wasSuccessful()) settled = false;
                } catch (RuntimeException failure) {
                    settled = false;
                }
            }
            for (MTEHatchInputME me : openedHatches) {
                try {
                    CheckRecipeResult result = me.endRecipeProcessing(host);
                    if (result == null || !result.wasSuccessful()) settled = false;
                } catch (RuntimeException failure) {
                    settled = false;
                }
            }
        }
        if (!reserved) return Result.SHORTAGE;
        return settled ? Result.SUCCESS : Result.COMMIT_FAILED;
    }

    private static List<ItemStack> normalizeItems(ItemStack[] source) {
        List<ItemStack> result = new ArrayList<>();
        if (source == null) return result;
        for (ItemStack stack : source) {
            if (stack == null || stack.stackSize == 0) continue;
            if (stack.stackSize < 0) return null;
            ItemStack combined = null;
            for (ItemStack existing : result) if (sameItem(existing, stack)) {
                combined = existing;
                break;
            }
            if (combined == null) result.add(stack.copy());
            else {
                long amount = (long) combined.stackSize + stack.stackSize;
                if (amount > Integer.MAX_VALUE) return null;
                combined.stackSize = (int) amount;
            }
        }
        return result;
    }

    private static List<FluidStack> normalizeFluids(FluidStack[] source) {
        List<FluidStack> result = new ArrayList<>();
        if (source == null) return result;
        for (FluidStack stack : source) {
            if (stack == null || stack.amount == 0) continue;
            if (stack.amount < 0) return null;
            FluidStack combined = null;
            for (FluidStack existing : result) if (existing.isFluidEqual(stack)) {
                combined = existing;
                break;
            }
            if (combined == null) result.add(stack.copy());
            else {
                long amount = (long) combined.amount + stack.amount;
                if (amount > Integer.MAX_VALUE) return null;
                combined.amount = (int) amount;
            }
        }
        return result;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return left != null && right != null
            && left.getItem() == right.getItem()
            && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static final class ItemChoice {

        final ItemStack ref;
        final CriticalEnergyPorts.LongItemSource source;
        final long amount;

        ItemChoice(ItemStack ref) {
            this.ref = ref;
            this.source = null;
            this.amount = ref.stackSize;
        }

        ItemChoice(CriticalEnergyPorts.LongItemSource source, long amount) {
            this.ref = null;
            this.source = source;
            this.amount = amount;
        }
    }

    private static final class ItemDebit {

        private final ItemStack ref;
        private final int amount;

        private ItemDebit(ItemStack ref, int amount) {
            this.ref = ref;
            this.amount = amount;
        }
    }

    private static final class FluidDebit {

        private final FluidStack ref;
        private final int amount;

        private FluidDebit(FluidStack ref, int amount) {
            this.ref = ref;
            this.amount = amount;
        }
    }
}
