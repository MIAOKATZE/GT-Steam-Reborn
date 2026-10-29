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
import com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalInputME;
import com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalOutputBusME;
import com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalOutputME;

import appeng.me.GridAccessException;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputSlave;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/** Direct, identity-deduplicated real hatch IO; never uses controller fluid buffers. */
public final class CriticalEnergyPorts {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private CriticalEnergyPorts() {}

    /** Long quantities belong to the critical hatch; the controller never queries AE stock. */
    public interface LongFluidSource {

        Object networkIdentity();

        FluidStack prototype();

        long available();

        long extract(long wanted, boolean commit);
    }

    public interface LongItemSource {

        Object networkIdentity();

        ItemStack prototype();

        long available();

        long extract(long wanted, boolean commit);
    }

    public static List<Input> snapshot(MTECriticalMachine host) {
        List<Input> inputs = new ArrayList<>();
        Set<MTEHatchInput> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Object, Map<GTUtility.FluidId, Input>> networks = new IdentityHashMap<>();
        for (MTEHatchInput hatch : host.getCriticalInputHatches()) {
            if (!hatch.isValid() || !seen.add(hatch)) continue;
            if (hatch instanceof MTEHatchCriticalInputME critical) {
                for (int slot = 0; slot < MTEHatchInputME.SLOT_COUNT; slot++) {
                    LongFluidSource source = critical.getLongInputSource(slot);
                    if (source == null) continue;
                    FluidStack fluid = source.prototype();
                    long amount = source.available();
                    if (fluid != null && amount > 0)
                        addNetworkInput(networks, new Input(hatch, fluid, amount, source.networkIdentity(), source));
                }
            } else if (hatch instanceof MTEHatchInputME me) {
                if (!me.isAllowedToWork()) continue;
                try {
                    // The monitor is an identity token only. Quantities and debits use native int IO.
                    Object network = me.getProxy()
                        .getStorage()
                        .getFluidInventory();
                    for (FluidTankInfo tank : me.getTankInfo(ForgeDirection.UNKNOWN)) {
                        if (tank != null && tank.fluid != null && tank.fluid.amount > 0)
                            addNetworkInput(networks, new Input(hatch, tank.fluid, tank.fluid.amount, network, null));
                    }
                } catch (GridAccessException ignored) { /* Offline stocking views offer no inputs. */ }
            } else if (!(hatch instanceof IRecipeProcessingAwareHatch)) {
                Map<GTUtility.FluidId, Input> types = new LinkedHashMap<>();
                for (FluidTankInfo tank : hatch.getTankInfo(ForgeDirection.UNKNOWN)) {
                    if (tank == null || tank.fluid == null || tank.fluid.amount <= 0) continue;
                    GTUtility.FluidId key = GTUtility.FluidId.create(tank.fluid);
                    Input source = types.get(key);
                    if (source == null) types.put(key, new Input(hatch, tank.fluid, tank.fluid.amount, null, null));
                    else source.amount = Math.addExact(source.amount, tank.fluid.amount);
                }
                inputs.addAll(types.values());
            }
        }
        for (Map<GTUtility.FluidId, Input> network : networks.values()) inputs.addAll(network.values());
        // GT205 PatternSlot is already physically paid by pushPattern, not a virtual ME preview.
        Set<IDualInputInventory> inventories = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<FluidStack> stacks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IDualInputHatch dual : host.getCriticalDualInputHatches()) {
            MTEHatchCraftingInputME owner = null;
            if (dual != null && dual.getClass() == MTEHatchCraftingInputME.class)
                owner = (MTEHatchCraftingInputME) dual;
            else if (dual != null && dual.getClass() == MTEHatchCraftingInputSlave.class)
                owner = ((MTEHatchCraftingInputSlave) dual).getMaster();
            if (owner == null || owner.getClass() != MTEHatchCraftingInputME.class || !owner.isValid()) continue;
            for (java.util.Iterator<? extends IDualInputInventory> iterator = dual.inventories(); iterator.hasNext();) {
                IDualInputInventory inventory = iterator.next();
                if (!(inventory instanceof MTEHatchCraftingInputME.PatternSlot<?>) || !inventories.add(inventory))
                    continue;
                for (FluidStack stack : inventory.getFluidInputs())
                    if (stack != null && stack.amount > 0 && stacks.add(stack)) inputs.add(new Input(owner, stack));
            }
        }
        return inputs;
    }

    private static void addNetworkInput(Map<Object, Map<GTUtility.FluidId, Input>> networks, Input input) {
        if (input.network == null) return;
        Map<GTUtility.FluidId, Input> types = networks.computeIfAbsent(input.network, key -> new LinkedHashMap<>());
        GTUtility.FluidId id = GTUtility.FluidId.create(input.fluid);
        Input previous = types.get(id);
        // Several stocking hatches are views of the same stock, including mixed int/long views.
        if (previous == null || previous.amount < input.amount) types.put(id, input);
    }

    public static BigInteger total(List<Input> inputs, FluidStack fluid) {
        BigInteger total = BigInteger.ZERO;
        if (fluid != null) for (Input input : inputs)
            if (input.fluid.isFluidEqual(fluid)) total = total.add(BigInteger.valueOf(input.amount));
        return total;
    }

    /** Display quantities use the same deduplicated sources as payment, without narrowing AE long stacks. */
    public static List<FluidAmount> quantities(List<Input> inputs, boolean network) {
        Map<GTUtility.FluidId, FluidAmount> totals = new LinkedHashMap<>();
        for (Input input : inputs) {
            if ((input.network != null) != network) continue;
            GTUtility.FluidId key = GTUtility.FluidId.create(input.fluid);
            FluidAmount previous = totals.get(key);
            BigInteger amount = BigInteger.valueOf(input.amount);
            totals.put(key, new FluidAmount(input.fluid, previous == null ? amount : previous.amount.add(amount)));
        }
        return new ArrayList<>(totals.values());
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

    /** One allocation ledger per batch; repeated requests cannot spend the same snapshot twice. */
    public static final class Allocation {

        private final List<Input> inputs;
        private final Map<Input, Long> reserved = new IdentityHashMap<>();

        public Allocation(List<Input> inputs) {
            this.inputs = inputs;
        }

        public List<Debit> take(FluidStack fluid, BigInteger amount) {
            List<Debit> result = new ArrayList<>();
            for (Input input : inputs) {
                if (amount.signum() == 0) break;
                if (fluid == null || !input.fluid.isFluidEqual(fluid)) continue;
                long used = reserved.getOrDefault(input, 0L);
                long take = amount.min(BigInteger.valueOf(input.amount - used))
                    .longValueExact();
                if (take <= 0) continue;
                reserved.put(input, used + take);
                result.add(new Debit(input, take));
                amount = amount.subtract(BigInteger.valueOf(take));
            }
            if (amount.signum() != 0) throw new IllegalArgumentException("Snapshot allocation shortage");
            return result;
        }
    }

    public static final class DebitResult {

        public final boolean complete, uncertain;
        public final BigInteger confirmedLitres;

        private DebitResult(boolean complete, boolean uncertain, BigInteger confirmedLitres) {
            this.complete = complete;
            this.uncertain = uncertain;
            this.confirmedLitres = confirmedLitres;
        }
    }

    /**
     * The observer journals each confirmed extraction before proceeding to another source. A short return is
     * confirmed partial payment; a throwing/custom invalid source is uncertain and must never be auto-replayed.
     * AE and machine NBT still have separate save domains; this is not a cross-save atomic transaction.
     */
    public static DebitResult debitConfirmed(List<Debit> debits, java.util.function.BiConsumer<Debit, Long> confirmed) {
        BigInteger paid = BigInteger.ZERO;
        for (Debit debit : debits) {
            try {
                long left = debit.amount;
                while (left > 0) {
                    long request = debit.source.longSource != null ? left : Math.min(left, Integer.MAX_VALUE);
                    long actual = debit.source.extractAmount(request, true);
                    if (actual < 0 || actual > request) return new DebitResult(false, true, paid);
                    if (actual > 0) {
                        paid = paid.add(BigInteger.valueOf(actual));
                        confirmed.accept(debit, actual);
                        debit.source.markDirty();
                        left -= actual;
                    }
                    if (actual != request) return new DebitResult(false, false, paid);
                }
            } catch (RuntimeException failure) {
                return new DebitResult(false, true, paid);
            }
        }
        return new DebitResult(true, false, paid);
    }

    /** Returns false on a changed/custom hatch. Caller must lock an uncertain partial commit, never retry it. */
    public static boolean debit(List<Debit> debits) {
        if (!canDebit(debits)) return false;
        for (Debit debit : debits) if (!debit.source.extract(debit.amount, true)) return false;
        return true;
    }

    public static boolean canDebit(List<Debit> debits) {
        Map<Input, Long> totals = new IdentityHashMap<>();
        for (Debit debit : debits) totals.merge(debit.source, debit.amount, Math::addExact);
        for (Map.Entry<Input, Long> total : totals.entrySet()) if (!total.getKey()
            .extract(total.getValue(), false)) return false;
        return true;
    }

    public static final class Input {

        public final MTEHatchInput hatch;
        public final FluidStack fluid;
        public long amount;
        private final Object network;
        private final LongFluidSource longSource;
        private final MTEHatchCraftingInputME patternOwner;
        private final FluidStack patternStack;

        private Input(MTEHatchInput hatch, FluidStack fluid, long amount, Object network, LongFluidSource longSource) {
            this.hatch = hatch;
            this.fluid = fluid.copy();
            this.fluid.amount = 1;
            this.amount = amount;
            this.network = network;
            this.longSource = longSource;
            this.patternOwner = null;
            this.patternStack = null;
        }

        private Input(MTEHatchCraftingInputME owner, FluidStack stack) {
            this.hatch = null;
            this.fluid = stack.copy();
            this.fluid.amount = 1;
            this.amount = stack.amount;
            this.network = null;
            this.longSource = null;
            this.patternOwner = owner;
            this.patternStack = stack;
        }

        private void markDirty() {
            if (patternOwner != null) patternOwner.getBaseMetaTileEntity()
                .markDirty();
            else if (hatch != null) hatch.getBaseMetaTileEntity()
                .markDirty();
        }

        private boolean extract(long wanted, boolean commit) {
            if (!commit) return extractAmount(wanted, false) == wanted;
            long left = wanted;
            while (left > 0) {
                long actual = extractAmount(longSource != null ? left : Math.min(left, Integer.MAX_VALUE), true);
                if (actual <= 0) return false;
                left -= actual;
                markDirty();
            }
            return true;
        }

        private long extractAmount(long wanted, boolean commit) {
            if (patternOwner != null) {
                if (!patternOwner.isValid() || !patternStack.isFluidEqual(fluid)) return 0;
                int actual = (int) Math.min(wanted, Math.max(0, patternStack.amount));
                if (commit) patternStack.amount -= actual;
                return actual;
            }
            if (!hatch.isValid()) return 0;
            if (longSource != null) {
                long actual = longSource.extract(wanted, commit);
                if (actual < 0 || actual > wanted) throw new IllegalStateException("Invalid long hatch debit receipt");
                return actual;
            }
            if (!commit && !(hatch instanceof MTEHatchInputME)) {
                long present = 0;
                for (FluidTankInfo tank : hatch.getTankInfo(ForgeDirection.UNKNOWN))
                    if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(fluid))
                        present = Math.addExact(present, tank.fluid.amount);
                return Math.min(present, wanted);
            }
            FluidStack request = fluid.copy();
            request.amount = (int) Math.min(wanted, Integer.MAX_VALUE);
            FluidStack actual = hatch.drain(ForgeDirection.UNKNOWN, request, commit);
            if (actual == null || actual.amount == 0) return 0;
            if (!actual.isFluidEqual(request) || actual.amount < 0 || actual.amount > request.amount)
                throw new IllegalStateException("Invalid hatch debit receipt");
            return actual.amount;
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
        List<MTEHatchCriticalOutputME.Batch> meHatches = new ArrayList<>();
        for (MTEHatchOutput hatch : host.getCriticalOutputHatches()) {
            if (!hatch.isValid() || !seenHatches.add(hatch)) continue;
            if (hatch instanceof MTEHatchCriticalOutputME me) meHatches.add(me.beginLongOutput());
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
            for (MTEHatchCriticalOutputME.Batch batch : meHatches) {
                if (left.signum() == 0) break;
                long accepted = batch.insert(
                    output.fluid,
                    left.min(LONG_MAX)
                        .longValueExact());
                left = left.subtract(BigInteger.valueOf(accepted));
            }
            if (left.signum() != 0) return null;
        }
        Set<MTEHatchOutputBus> seenBusses = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IOutputBusTransaction> buses = new ArrayList<>();
        List<MTEHatchCriticalOutputBusME.Batch> meBuses = new ArrayList<>();
        for (MTEHatchOutputBus bus : host.getCriticalOutputBusses()) if (bus.isValid() && seenBusses.add(bus)) {
            if (bus instanceof MTEHatchCriticalOutputBusME me) {
                meBuses.add(me.beginLongOutput());
                continue;
            }
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
            for (MTEHatchCriticalOutputBusME.Batch batch : meBuses) {
                if (left.stackSize == 0) break;
                left.stackSize -= (int) batch.insert(item, left.stackSize);
            }
            if (left.stackSize != 0) return null;
        }
        plan.longItems.addAll(meBuses);
        plan.longFluids.addAll(meHatches);
        plan.fluids.addAll(transactions);
        plan.items.addAll(buses);
        return plan;
    }

    public static final class OutputPlan {

        private final List<IOutputHatchTransaction> fluids = new ArrayList<>();
        private final List<IOutputBusTransaction> items = new ArrayList<>();
        private final List<MTEHatchCriticalOutputME.Batch> longFluids = new ArrayList<>();
        private final List<MTEHatchCriticalOutputBusME.Batch> longItems = new ArrayList<>();

        public void commit() {
            for (MTEHatchCriticalOutputME.Batch batch : longFluids) batch.commit();
            for (MTEHatchCriticalOutputBusME.Batch batch : longItems) batch.commit();
            for (IOutputHatchTransaction transaction : fluids) transaction.commit();
            for (IOutputBusTransaction transaction : items) transaction.commit();
        }
    }

}
