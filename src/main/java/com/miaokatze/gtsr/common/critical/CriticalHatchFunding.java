package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch;

/** Server-thread funding from real GT inputs. Nothing is imported into a controller inventory. */
public final class CriticalHatchFunding {

    private CriticalHatchFunding() {}

    private static boolean isCircuitSlot(MTEHatchInputBus bus, int slot) {
        return slot == (bus instanceof gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug
            ? bus.getSizeInventory() - 1
            : bus.getCircuitSlot());
    }

    /** A single long-bounded transaction; excess stays in its source and a failed commit locks the journal. */
    public static CriticalNativeMEFunding.ItemConsumption consumeAllItem(MTEMultiBlockBase host,
        List<MTEHatchInputBus> busses, ItemStack wanted) {
        List<ItemDebit> debits = new ArrayList<>();
        Map<MTEHatchInputBus, Boolean> seen = new IdentityHashMap<>();
        for (MTEHatchInputBus bus : busses) {
            if (!bus.isValid() || bus instanceof IRecipeProcessingAwareHatch || seen.put(bus, true) != null) continue;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                if (isCircuitSlot(bus, slot)) continue;
                ItemStack ref = bus.getStackInSlot(slot);
                if (ref != null && ref.stackSize > 0 && same(ref, wanted))
                    debits.add(new ItemDebit(bus, slot, ref, ref.stackSize));
            }
        }
        CriticalNativeMEFunding.ItemConsumption network = CriticalNativeMEFunding.consumeAllItem(host, busses, wanted);
        if (network.result != CriticalNativeMEFunding.Result.SUCCESS) return network;
        long amount = network.amount;
        try {
            for (ItemDebit debit : debits) {
                // Native long inputs may already fill the receipt. Do not remove unrepresentable excess.
                int request = (int) Math.min(debit.amount, Long.MAX_VALUE - amount);
                if (request == 0) break;
                if (debit.bus instanceof gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug) {
                    amount += request;
                    continue;
                }
                if (debit.bus.getStackInSlot(debit.slot) != debit.ref || debit.ref.stackSize != debit.amount)
                    return new CriticalNativeMEFunding.ItemConsumption(
                        CriticalNativeMEFunding.Result.COMMIT_FAILED,
                        amount);
                ItemStack removed = debit.bus.decrStackSize(debit.slot, request);
                if (!same(removed, wanted) || removed.stackSize != request)
                    return new CriticalNativeMEFunding.ItemConsumption(
                        CriticalNativeMEFunding.Result.COMMIT_FAILED,
                        amount);
                amount += removed.stackSize;
                debit.bus.getBaseMetaTileEntity()
                    .markDirty();
            }
            host.updateSlots();
            return new CriticalNativeMEFunding.ItemConsumption(CriticalNativeMEFunding.Result.SUCCESS, amount);
        } catch (RuntimeException failure) {
            return new CriticalNativeMEFunding.ItemConsumption(CriticalNativeMEFunding.Result.COMMIT_FAILED, amount);
        }
    }

    public static ItemStack[] snapshotItems(List<MTEHatchInputBus> busses) {
        List<ItemStack> result = new ArrayList<>();
        Map<MTEHatchInputBus, Boolean> seen = new IdentityHashMap<>();
        for (MTEHatchInputBus bus : busses) {
            if (!bus.isValid() || bus instanceof IRecipeProcessingAwareHatch || seen.put(bus, true) != null) continue;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                if (isCircuitSlot(bus, slot)) continue;
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null && stack.stackSize > 0) result.add(stack.copy());
            }
        }
        return result.toArray(new ItemStack[0]);
    }

    public static FluidStack[] snapshotFluids(List<MTEHatchInput> hatches) {
        List<FluidStack> result = new ArrayList<>();
        Map<MTEHatchInput, Boolean> seen = new IdentityHashMap<>();
        for (MTEHatchInput hatch : hatches) {
            if (!hatch.isValid() || hatch instanceof IRecipeProcessingAwareHatch || seen.put(hatch, true) != null)
                continue;
            if (hatch instanceof gregtech.api.metatileentity.implementations.MTEHatchInputDebug debug) {
                for (FluidStack fluid : debug.getFluidList()) if (fluid != null) {
                    FluidStack copy = fluid.copy();
                    copy.amount = Integer.MAX_VALUE;
                    result.add(copy);
                }
                continue;
            }
            for (FluidTankInfo tank : hatch.getTankInfo(ForgeDirection.UNKNOWN)) {
                if (tank != null && tank.fluid != null && tank.fluid.amount > 0) result.add(tank.fluid.copy());
            }
        }
        return result.toArray(new FluidStack[0]);
    }

    public static CriticalNativeMEFunding.Result fund(MTEMultiBlockBase host, List<MTEHatchInputBus> busses,
        List<MTEHatchInput> hatches, ItemStack[] items, FluidStack[] fluids) {
        List<ItemDebit> itemDebits = new ArrayList<>();
        List<FluidDebit> fluidDebits = new ArrayList<>();
        List<ItemStack> missingItems = new ArrayList<>();
        List<FluidStack> missingFluids = new ArrayList<>();
        Map<ItemStack, Integer> reservedItems = new IdentityHashMap<>();
        Map<MTEHatchInputBus, Map<Integer, Integer>> debugReservations = new IdentityHashMap<>();
        Map<MTEHatchInput, List<FluidStack>> reservedFluids = new IdentityHashMap<>();
        for (ItemStack wanted : items) {
            if (wanted == null || wanted.stackSize == 0) continue;
            if (wanted.stackSize < 0) return CriticalNativeMEFunding.Result.SHORTAGE;
            int left = wanted.stackSize;
            Map<MTEHatchInputBus, Boolean> seen = new IdentityHashMap<>();
            for (MTEHatchInputBus bus : busses) {
                if (!bus.isValid() || bus instanceof IRecipeProcessingAwareHatch || seen.put(bus, true) != null)
                    continue;
                for (int slot = 0; slot < bus.getSizeInventory() && left > 0; slot++) {
                    if (isCircuitSlot(bus, slot)) continue;
                    ItemStack ref = bus.getStackInSlot(slot);
                    if (!same(ref, wanted)) continue;
                    boolean debug = bus instanceof gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug;
                    Map<Integer, Integer> slots = debugReservations
                        .computeIfAbsent(bus, ignored -> new java.util.HashMap<>());
                    int reserved = debug ? slots.getOrDefault(slot, 0) : reservedItems.getOrDefault(ref, 0);
                    int amount = Math.min(left, Math.max(0, ref.stackSize - reserved));
                    if (amount == 0) continue;
                    itemDebits.add(new ItemDebit(bus, slot, ref, amount));
                    reservedItems.put(ref, reserved + amount);
                    if (debug) slots.put(slot, reserved + amount);
                    left -= amount;
                }
            }
            if (left > 0) {
                ItemStack missing = wanted.copy();
                missing.stackSize = left;
                missingItems.add(missing);
            }
        }
        for (FluidStack wanted : fluids) {
            if (wanted == null || wanted.amount == 0) continue;
            if (wanted.amount < 0) return CriticalNativeMEFunding.Result.SHORTAGE;
            int left = wanted.amount;
            Map<MTEHatchInput, Boolean> seen = new IdentityHashMap<>();
            for (MTEHatchInput hatch : hatches) {
                if (left == 0) break;
                if (!hatch.isValid() || hatch instanceof IRecipeProcessingAwareHatch || seen.put(hatch, true) != null)
                    continue;
                FluidStack query = wanted.copy();
                query.amount = Integer.MAX_VALUE;
                FluidStack available = hatch.drain(ForgeDirection.UNKNOWN, query, false);
                if (available == null || !available.isFluidEqual(wanted)) continue;
                List<FluidStack> reservations = reservedFluids.computeIfAbsent(hatch, key -> new ArrayList<>());
                int reserved = 0;
                for (FluidStack reservation : reservations)
                    if (reservation.isFluidEqual(wanted)) reserved += reservation.amount;
                int amount = Math.min(left, Math.max(0, available.amount - reserved));
                if (amount == 0) continue;
                FluidStack debit = wanted.copy();
                debit.amount = amount;
                reservations.add(debit);
                fluidDebits.add(new FluidDebit(hatch, debit));
                left -= amount;
            }
            if (left > 0) {
                FluidStack missing = wanted.copy();
                missing.amount = left;
                missingFluids.add(missing);
            }
        }
        // No physical mutation before the entire ME deficit has successfully committed.
        CriticalNativeMEFunding.Result result = CriticalNativeMEFunding.fund(
            host,
            busses,
            hatches,
            missingItems.toArray(new ItemStack[0]),
            missingFluids.toArray(new FluidStack[0]));
        if (result != CriticalNativeMEFunding.Result.SUCCESS) return result;
        try {
            for (ItemDebit debit : itemDebits) {
                // Debug input buses deliberately expose fresh phantom copies, rather than stored references.
                if (debit.bus instanceof gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug) continue;
                if (debit.bus.getStackInSlot(debit.slot) != debit.ref || debit.ref.stackSize < debit.amount)
                    return CriticalNativeMEFunding.Result.COMMIT_FAILED;
                ItemStack removed = debit.bus.decrStackSize(debit.slot, debit.amount);
                if (!same(removed, debit.ref) || removed.stackSize != debit.amount)
                    return CriticalNativeMEFunding.Result.COMMIT_FAILED;
                debit.bus.getBaseMetaTileEntity()
                    .markDirty();
            }
            for (FluidDebit debit : fluidDebits) {
                FluidStack drained = debit.hatch.drain(ForgeDirection.UNKNOWN, debit.request, true);
                if (drained == null || !drained.isFluidEqual(debit.request) || drained.amount != debit.request.amount)
                    return CriticalNativeMEFunding.Result.COMMIT_FAILED;
                debit.hatch.getBaseMetaTileEntity()
                    .markDirty();
            }
            host.updateSlots();
            return CriticalNativeMEFunding.Result.SUCCESS;
        } catch (RuntimeException failure) {
            // Unknown custom hatch behavior can have partially committed. Never mint escrow/refunds from it.
            return CriticalNativeMEFunding.Result.COMMIT_FAILED;
        }
    }

    private static boolean same(ItemStack left, ItemStack right) {
        return left != null && right != null
            && left.getItem() == right.getItem()
            && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static final class ItemDebit {

        private final MTEHatchInputBus bus;
        private final int slot, amount;
        private final ItemStack ref;

        private ItemDebit(MTEHatchInputBus bus, int slot, ItemStack ref, int amount) {
            this.bus = bus;
            this.slot = slot;
            this.ref = ref;
            this.amount = amount;
        }
    }

    private static final class FluidDebit {

        private final MTEHatchInput hatch;
        private final FluidStack request;

        private FluidDebit(MTEHatchInput hatch, FluidStack request) {
            this.hatch = hatch;
            this.request = request;
        }
    }
}
