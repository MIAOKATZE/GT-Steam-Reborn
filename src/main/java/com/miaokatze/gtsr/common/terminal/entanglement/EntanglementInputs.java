package com.miaokatze.gtsr.common.terminal.entanglement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/** Per-position, per-fluid limits. Probing never calls drain(false), including on GT594 ME hatches. */
public final class EntanglementInputs {

    public static final int KINDS = 6;
    private static final int MAX_LIMITS = 8192;
    private static final String[] NORMAL = { "steam", "ic2superheatedsteam", "supercriticalsteam", "pyrotheum",
        "cryotheum", "ic2uumatter" };
    private static final String[] DENSE = { "densesteam", "densesuperheatedsteam", "densesupercriticalsteam",
        "pyrotheum", "cryotheum", "ic2uumatter" };
    private final Map<String, Long> limits = new LinkedHashMap<>();
    private int revision;

    public static final class Input {

        public final MTEHatch hatch;
        public final int x, y, z, kind;
        public final long stored, limit;
        public final String key;
        private final FluidStack probe;

        Input(MTEHatch hatch, int kind, FluidStack probe, long stored, long limit) {
            this.hatch = hatch;
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            x = base.getXCoord();
            y = base.getYCoord();
            z = base.getZCoord();
            this.kind = kind;
            this.probe = probe;
            this.stored = Math.max(0, stored);
            this.limit = limit;
            key = key(x, y, z, kind);
        }

        public long requested() {
            return limit < 0 ? stored : Math.min(stored, limit);
        }
    }

    public static String key(int x, int y, int z, int kind) {
        return x + "," + y + "," + z + "," + kind;
    }

    public static FluidStack fluid(boolean critical, int kind) {
        if (kind < 0 || kind >= (critical ? KINDS : KINDS - 1)) return null;
        FluidStack fluid = FluidRegistry.getFluidStack((critical ? DENSE : NORMAL)[kind], 1);
        if (kind == 5 && fluid == null) fluid = FluidRegistry.getFluidStack("uumatter", 1);
        return fluid;
    }

    public static long stored(MTEHatch hatch, FluidStack probe) {
        if (probe == null) return 0;
        FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
        if (tanks == null) return 0;
        long amount = 0;
        for (FluidTankInfo tank : tanks) {
            if (tank == null || tank.fluid == null || !tank.fluid.isFluidEqual(probe)) continue;
            long present = Math.max(0, tank.fluid.amount);
            // Stocking ME slots can report the same network inventory more than once.
            amount = hatch instanceof MTEHatchInputME ? Math.max(amount, present) : add(amount, present);
        }
        return amount;
    }

    public List<Input> collect(List<MTEHatch> hatches, boolean critical) {
        List<Input> inputs = new ArrayList<>();
        for (MTEHatch hatch : hatches) {
            if (hatch == null || hatch.getBaseMetaTileEntity() == null) continue;
            for (int kind = 0; kind < (critical ? KINDS : KINDS - 1); kind++) {
                FluidStack probe = fluid(critical, kind);
                IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
                long limit = limits.getOrDefault(key(base.getXCoord(), base.getYCoord(), base.getZCoord(), kind), -1L);
                Input input = new Input(hatch, kind, probe, stored(hatch, probe), limit);
                if (input.stored > 0) inputs.add(input);
            }
        }
        inputs.sort(
            Comparator.comparingInt((Input input) -> input.x)
                .thenComparingInt(input -> input.y)
                .thenComparingInt(input -> input.z)
                .thenComparingInt(input -> input.kind));
        return inputs;
    }

    public static long add(long first, long second) {
        return second > Long.MAX_VALUE - first ? Long.MAX_VALUE : first + second;
    }

    public long drain(List<Input> inputs, int kind) {
        long total = 0;
        for (Input input : inputs) {
            if (input.kind != kind || input.probe == null) continue;
            long remaining = input.requested();
            while (remaining > 0) {
                FluidStack request = input.probe.copy();
                request.amount = (int) Math.min(Integer.MAX_VALUE, remaining);
                FluidStack got = input.hatch.drain(ForgeDirection.UNKNOWN, request, true);
                if (got == null || got.amount <= 0) break;
                int actual = (int) Math.min(remaining, got.amount);
                total = add(total, actual);
                remaining -= actual;
            }
        }
        return total;
    }

    public int revision() {
        return revision;
    }

    /** Call only on the server thread, after resolving all edited keys against current input hatches. */
    public boolean apply(Map<String, Long> changes, int expectedRevision) {
        return apply(changes, expectedRevision, false);
    }

    public boolean apply(Map<String, Long> changes, int expectedRevision, boolean reset) {
        if (expectedRevision != revision || changes.size() > 6) return false;
        int size = reset ? 0 : limits.size();
        for (Map.Entry<String, Long> change : changes.entrySet()) {
            if (change.getValue() < -1) return false;
            if (change.getValue() >= 0 && (reset || !limits.containsKey(change.getKey()))) size++;
            if (!reset && change.getValue() == -1 && limits.containsKey(change.getKey())) size--;
        }
        if (size > MAX_LIMITS) return false;
        if (reset) limits.clear();
        for (Map.Entry<String, Long> change : changes.entrySet()) {
            if (change.getValue() < 0) limits.remove(change.getKey());
            else limits.put(change.getKey(), change.getValue());
        }
        revision++;
        return true;
    }

    public boolean reset(int expectedRevision) {
        if (expectedRevision != revision) return false;
        limits.clear();
        revision++;
        return true;
    }

    public void save(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Long> entry : limits.entrySet()) {
            NBTTagCompound item = new NBTTagCompound();
            item.setString("input", entry.getKey());
            item.setLong("limit", entry.getValue());
            list.appendTag(item);
        }
        nbt.setTag("entanglementInputLimits", list);
    }

    public void load(NBTTagCompound nbt) {
        limits.clear();
        revision = 0;
        NBTTagList list = nbt.getTagList("entanglementInputLimits", 10);
        for (int i = 0; i < Math.min(MAX_LIMITS, list.tagCount()); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            String key = item.getString("input");
            if (key.matches("-?\\d{1,10},-?\\d{1,10},-?\\d{1,10},[0-5]") && item.hasKey("limit", 4)
                && item.getLong("limit") >= 0) limits.put(key, item.getLong("limit"));
        }
    }
}
