package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import appeng.api.config.Actionable;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.GridAccessException;
import appeng.util.item.AEFluidStack;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Direct, identity-deduplicated real hatch IO; never uses controller fluid buffers. */
public final class CriticalEnergyPorts {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private CriticalEnergyPorts() {}

    public static List<Input> snapshot(MTECriticalMachine host) {
        List<Input> inputs = new ArrayList<>();
        Set<MTEHatchInput> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<IMEMonitor<IAEFluidStack>, Set<GTUtility.FluidId>> networks = new IdentityHashMap<>();
        for (MTEHatchInput hatch : host.getCriticalInputHatches()) {
            if (!hatch.isValid() || !seen.add(hatch)) continue;
            if (hatch instanceof MTEHatchInputME me) {
                if (!me.isAllowedToWork()) continue;
                try {
                    IMEMonitor<IAEFluidStack> monitor = me.getProxy()
                        .getStorage()
                        .getFluidInventory();
                    Set<GTUtility.FluidId> types = networks.computeIfAbsent(monitor, key -> new java.util.HashSet<>());
                    for (int i = 0; i < MTEHatchInputME.SLOT_COUNT; i++) {
                        FluidStack config = me.getSlotConfig(i);
                        if (config == null || !types.add(GTUtility.FluidId.create(config))) continue;
                        IAEFluidStack request = AEFluidStack.create(config);
                        request.setStackSize(Long.MAX_VALUE);
                        IAEFluidStack available = monitor.extractItems(
                            request,
                            Actionable.SIMULATE,
                            new MachineSource((appeng.api.networking.security.IActionHost) me.getBaseMetaTileEntity()));
                        if (available != null && available.getStackSize() > 0)
                            inputs.add(new Input(hatch, config, available.getStackSize(), monitor));
                    }
                } catch (GridAccessException ignored) { /* An offline network promises nothing. */ }
            } else if (!(hatch instanceof IRecipeProcessingAwareHatch)) {
                Map<GTUtility.FluidId, Input> types = new LinkedHashMap<>();
                for (FluidTankInfo tank : hatch.getTankInfo(ForgeDirection.UNKNOWN)) {
                    if (tank == null || tank.fluid == null || tank.fluid.amount <= 0) continue;
                    GTUtility.FluidId key = GTUtility.FluidId.create(tank.fluid);
                    Input source = types.get(key);
                    if (source == null) types.put(key, new Input(hatch, tank.fluid, tank.fluid.amount, null));
                    else source.amount = Math.addExact(source.amount, tank.fluid.amount);
                }
                inputs.addAll(types.values());
            }
        }
        return inputs;
    }

    public static BigInteger total(List<Input> inputs, FluidStack fluid) {
        BigInteger total = BigInteger.ZERO;
        if (fluid != null) for (Input input : inputs)
            if (input.fluid.isFluidEqual(fluid)) total = total.add(BigInteger.valueOf(input.amount));
        return total;
    }

    public static List<Debit> take(List<Input> inputs, FluidStack fluid, BigInteger amount) {
        List<Debit> result = new ArrayList<>();
        for (Input input : inputs) {
            if (amount.signum() == 0) break;
            if (fluid == null || !input.fluid.isFluidEqual(fluid)) continue;
            long take = amount.min(BigInteger.valueOf(input.amount))
                .longValueExact();
            if (take > 0) result.add(new Debit(input, take));
            amount = amount.subtract(BigInteger.valueOf(take));
        }
        if (amount.signum() != 0) throw new IllegalArgumentException("Snapshot shortage");
        return result;
    }

    /** Returns false on a changed/custom hatch. Caller must lock an uncertain partial commit, never retry it. */
    public static boolean debit(List<Debit> debits) {
        if (!canDebit(debits)) return false;
        for (Debit debit : debits) if (!debit.source.extract(debit.amount, true)) return false;
        return true;
    }

    public static boolean canDebit(List<Debit> debits) {
        for (Debit debit : debits) if (!debit.source.extract(debit.amount, false)) return false;
        return true;
    }

    public static final class Input {

        public final MTEHatchInput hatch;
        public final FluidStack fluid;
        public long amount;
        private final IMEMonitor<IAEFluidStack> monitor;

        private Input(MTEHatchInput hatch, FluidStack fluid, long amount, IMEMonitor<IAEFluidStack> monitor) {
            this.hatch = hatch;
            this.fluid = fluid.copy();
            this.fluid.amount = 1;
            this.amount = amount;
            this.monitor = monitor;
        }

        private boolean extract(long wanted, boolean commit) {
            if (!hatch.isValid()) return false;
            if (monitor != null) {
                IAEFluidStack request = AEFluidStack.create(fluid);
                request.setStackSize(wanted);
                IAEFluidStack actual = monitor.extractItems(
                    request,
                    commit ? Actionable.MODULATE : Actionable.SIMULATE,
                    new MachineSource((appeng.api.networking.security.IActionHost) hatch.getBaseMetaTileEntity()));
                return actual != null && actual.getStackSize() == wanted;
            }
            if (!commit) {
                long present = 0;
                for (FluidTankInfo tank : hatch.getTankInfo(ForgeDirection.UNKNOWN))
                    if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(fluid))
                        present = Math.addExact(present, tank.fluid.amount);
                return present >= wanted;
            }
            long left = wanted;
            while (left > 0) {
                FluidStack request = fluid.copy();
                request.amount = (int) Math.min(left, Integer.MAX_VALUE);
                FluidStack actual = hatch.drain(ForgeDirection.UNKNOWN, request, commit);
                if (actual == null || !actual.isFluidEqual(request)
                    || actual.amount <= 0
                    || actual.amount > request.amount) return false;
                left -= actual.amount;
            }
            hatch.getBaseMetaTileEntity()
                .markDirty();
            return true;
        }
    }

    public static final class Debit {

        public final Input source;
        public final long amount;

        private Debit(Input source, long amount) {
            this.source = source;
            this.amount = amount;
        }
    }

    public static final class FluidAmount {

        public final FluidStack fluid;
        public final BigInteger amount;

        public FluidAmount(FluidStack fluid, BigInteger amount) {
            if (fluid == null || amount.signum() < 0) throw new IllegalArgumentException("Invalid fluid output");
            this.fluid = fluid.copy();
            this.fluid.amount = 1;
            this.amount = amount;
        }
    }

    public static OutputPlan outputs(MTECriticalMachine host, List<FluidAmount> fluids, ItemStack[] items) {
        OutputPlan plan = new OutputPlan();
        Map<GTUtility.FluidId, FluidAmount> merged = new LinkedHashMap<>();
        for (FluidAmount output : fluids) {
            if (output.amount.signum() == 0) continue;
            GTUtility.FluidId key = GTUtility.FluidId.create(output.fluid);
            FluidAmount previous = merged.get(key);
            merged.put(
                key,
                previous == null ? output : new FluidAmount(output.fluid, previous.amount.add(output.amount)));
        }
        Set<MTEHatchOutput> seenHatches = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IOutputHatchTransaction> transactions = new ArrayList<>();
        List<MTEHatchOutputME> meHatches = new ArrayList<>();
        for (MTEHatchOutput hatch : host.getCriticalOutputHatches()) {
            if (!hatch.isValid() || !seenHatches.add(hatch)) continue;
            // Multi-fluid batches need the native transaction's shared cell-capacity reservation.
            if (hatch instanceof MTEHatchOutputME me && merged.size() == 1) meHatches.add(me);
            else {
                IOutputHatchTransaction transaction = hatch.createTransaction();
                if (transaction instanceof IOutputTransaction.IRecipeCheckAware aware) aware.setRecipeCheck(true);
                transactions.add(transaction);
            }
        }
        for (FluidAmount output : merged.values()) {
            BigInteger left = output.amount;
            GTUtility.FluidId id = GTUtility.FluidId.create(output.fluid);
            for (IOutputHatchTransaction transaction : transactions) {
                while (left.signum() > 0 && transaction.hasAvailableSpace()) {
                    FluidStack part = output.fluid.copy();
                    part.amount = left.min(INT_MAX)
                        .intValue();
                    int before = part.amount;
                    transaction.storePartial(id, part, 1, 1);
                    int inserted = before - part.amount;
                    if (inserted <= 0) break;
                    left = left.subtract(BigInteger.valueOf(inserted));
                }
                if (left.signum() == 0) break;
            }
            // Long AE stacks avoid an O(volume / INT_MAX) loop for genuinely huge network batches.
            Set<IMEMonitor<IAEFluidStack>> networks = Collections.newSetFromMap(new IdentityHashMap<>());
            for (MTEHatchOutputME me : meHatches) {
                if (left.signum() == 0) break;
                if (!me.canStoreFluid(output.fluid)) continue;
                try {
                    IMEMonitor<IAEFluidStack> monitor = me.getProxy()
                        .getStorage()
                        .getFluidInventory();
                    if (!networks.add(monitor)) continue;
                    IAEFluidStack request = AEFluidStack.create(output.fluid);
                    request.setStackSize(
                        left.min(LONG_MAX)
                            .longValueExact());
                    IAEFluidStack rejected = monitor.injectItems(
                        request,
                        Actionable.SIMULATE,
                        new MachineSource((appeng.api.networking.security.IActionHost) me.getBaseMetaTileEntity()));
                    long accepted = request.getStackSize() - (rejected == null ? 0 : rejected.getStackSize());
                    if (accepted > 0) {
                        IAEFluidStack stored = request.copy();
                        stored.setStackSize(accepted);
                        plan.networks.add(new NetworkOutput(monitor, stored, me));
                        left = left.subtract(BigInteger.valueOf(accepted));
                    }
                } catch (GridAccessException ignored) { /* Offline is blocked. */ }
            }
            if (left.signum() != 0) return null;
        }
        Set<MTEHatchOutputBus> seenBusses = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IOutputBusTransaction> buses = new ArrayList<>();
        for (MTEHatchOutputBus bus : host.getCriticalOutputBusses()) if (bus.isValid() && seenBusses.add(bus)) {
            IOutputBusTransaction transaction = bus.createTransaction();
            if (transaction instanceof IOutputTransaction.IRecipeCheckAware aware) aware.setRecipeCheck(true);
            buses.add(transaction);
        }
        for (ItemStack item : items) {
            ItemStack left = item.copy();
            GTUtility.ItemId id = GTUtility.ItemId.create(item);
            for (IOutputBusTransaction bus : buses) {
                while (left.stackSize > 0 && bus.hasAvailableSpace()) {
                    int before = left.stackSize;
                    bus.storePartial(id, left, 1, 1);
                    if (before == left.stackSize) break;
                }
                if (left.stackSize == 0) break;
            }
            if (left.stackSize != 0) return null;
        }
        plan.fluids.addAll(transactions);
        plan.items.addAll(buses);
        return plan;
    }

    public static final class OutputPlan {

        private final List<IOutputHatchTransaction> fluids = new ArrayList<>();
        private final List<IOutputBusTransaction> items = new ArrayList<>();
        private final List<NetworkOutput> networks = new ArrayList<>();

        public void commit() {
            for (NetworkOutput network : networks) {
                IAEFluidStack rejected = network.monitor.injectItems(
                    network.stack,
                    Actionable.MODULATE,
                    new MachineSource(
                        (appeng.api.networking.security.IActionHost) network.hatch.getBaseMetaTileEntity()));
                if (rejected != null && rejected.getStackSize() > 0)
                    throw new IllegalStateException("Output network changed during batch commit");
            }
            for (IOutputHatchTransaction transaction : fluids) transaction.commit();
            for (IOutputBusTransaction transaction : items) transaction.commit();
        }
    }

    private static final class NetworkOutput {

        final IMEMonitor<IAEFluidStack> monitor;
        final IAEFluidStack stack;
        final MTEHatchOutputME hatch;

        NetworkOutput(IMEMonitor<IAEFluidStack> monitor, IAEFluidStack stack, MTEHatchOutputME hatch) {
            this.monitor = monitor;
            this.stack = stack;
            this.hatch = hatch;
        }
    }
}
