package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalEnergyPorts.FluidAmount;
import com.miaokatze.gtsr.common.items.IrradiatedDust;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.util.GTModHandler;
import gregtech.common.misc.WirelessNetworkManager;
import gtPlusPlus.core.material.MaterialMisc;
import gtnhintergalactic.config.IGConfig;

/** 400-tick energy batches, persisted independently from legacy recipe/tier/plugin arithmetic. */
public final class CriticalEnergyRuntime {

    public static final int CYCLE_TICKS = CriticalEnergyMath.CYCLE_TICKS;
    private static final ItemStack[] NO_ITEMS = new ItemStack[0];
    private static final FluidStack[] NO_FLUIDS = new FluidStack[0];

    private CriticalEnergyRuntime() {}

    public static FluidStack steam(int mode) {
        return switch (Math.floorMod(mode, 6)) {
            case 1 -> GTModHandler.getSuperHeatedSteam(1);
            case 2 -> FluidRegistry.getFluidStack("supercriticalsteam", 1);
            case 3 -> Materials.DenseSteam.getGas(1);
            case 4 -> Materials.DenseSuperheatedSteam.getGas(1);
            case 5 -> Materials.DenseSupercriticalSteam.getGas(1);
            default -> GTModHandler.getSteam(1);
        };
    }

    /** Mirrors the GT205 Dyson instance resolver without requiring a Dyson controller. */
    public static double dimensionFactor(World world) {
        if (!Mods.GalacticraftCore.isModLoaded()) return 1D;
        WorldProvider provider = world.provider;
        try {
            Class<?> orbit = Class.forName("micdoodle8.mods.galacticraft.api.world.IOrbitDimension");
            if (orbit.isInstance(provider)) return IGConfig.dysonSwarm.getPowerFactor(
                String.valueOf(
                    orbit.getMethod("getPlanetToOrbit")
                        .invoke(provider)));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot resolve loaded Galacticraft orbit provider", failure);
        }
        if (provider.getClass()
            .getName()
            .equals("me.eigenraven.personalspace.world.PersonalWorldProvider"))
            return IGConfig.dysonSwarm.getPowerFactor("PS");
        return IGConfig.dysonSwarm.getPowerFactor(String.valueOf(provider.dimensionId));
    }

    public static void tick(CriticalMachineState state, MTECriticalMachine host) {
        if (host == null) {
            state.fail("没有真实工作仓口");
            return;
        }
        boolean solar = state.getMachineKind() == CriticalMachineKind.SOLAR;
        if (solar && !CriticalWorldData.get(state.worldObj)
            .claimNight(state.worldObj, state.getStructureId())) {
            state.fail("本维度已有先启动的辐照锅炉");
            return;
        }
        NBTTagCompound data = state.energyRuntimeData();
        if (data.getBoolean("uncertain")) {
            state.fail("批次提交结果不确定，已锁定以保护原料和无线电量");
            return;
        }
        if (!data.getBoolean("funded")) {
            if (!(solar ? startSolar(state, host, data) : startTurbine(state, host, data))) return;
        }
        int progress = data.getInteger("progress");
        if (progress < CYCLE_TICKS) {
            data.setInteger("progress", ++progress);
            state.setRuntimeBatch(progress, CYCLE_TICKS, 0, "工作 " + progress + "/" + CYCLE_TICKS);
            state.markDirty();
            if (progress < CYCLE_TICKS) return;
        }
        List<FluidAmount> fluids = readFluids(data.getTagList("outputs", 10));
        ItemStack[] items = readItems(data.getTagList("items", 10));
        CriticalEnergyPorts.OutputPlan output = CriticalEnergyPorts.outputs(host, fluids, items);
        if (output == null) {
            state.fail("等待批次输出空间");
            return;
        }
        try {
            // Any thrown partial commit is quarantined, never automatically replayed.
            data.setBoolean("uncertain", true);
            state.markDirty();
            output.commit();
            BigInteger eu = number(data, "eu");
            if (eu.signum() > 0) {
                UUID owner = UUID.fromString(data.getString("owner"));
                WirelessNetworkManager.strongCheckOrAddUser(owner);
                if (!WirelessNetworkManager.addEUToGlobalEnergyMap(owner, eu))
                    throw new IllegalStateException("Wireless increment rejected");
            }
            data.setInteger("waterRemainder", data.getInteger("nextWaterRemainder"));
            data.setBoolean("funded", false);
            data.setBoolean("uncertain", false);
            data.setInteger("progress", 0);
            data.removeTag("outputs");
            data.removeTag("items");
            data.removeTag("inputs");
            data.setString("lastGeneratedEU", eu.toString());
            data.setString("eu", "0");
            state.setRuntimeBatch(0, 0, 0, "批次交付完成");
            state.markDirty();
        } catch (RuntimeException failure) {
            data.setString(
                "commitFailure",
                failure.getClass()
                    .getSimpleName());
            state.fail("批次提交结果不确定，已锁定以保护原料和无线电量");
            state.markDirty();
        }
    }

    private static boolean startTurbine(CriticalMachineState state, MTECriticalMachine host, NBTTagCompound data) {
        List<CriticalEnergyPorts.Input> snapshot = CriticalEnergyPorts.snapshot(host);
        List<CriticalEnergyPorts.Debit> debits = new ArrayList<>();
        BigInteger[] volumes = new BigInteger[6];
        for (int i = 0; i < 6; i++) {
            FluidStack type = steam(i);
            volumes[i] = CriticalEnergyPorts.total(snapshot, type);
            if (volumes[i].signum() > 0) debits.addAll(CriticalEnergyPorts.take(snapshot, type, volumes[i]));
        }
        CriticalEnergyMath.TurbineBatch batch = CriticalEnergyMath.turbine(volumes, data.getInteger("waterRemainder"));
        if (batch.eu.signum() == 0) {
            state.setRuntimeBatch(0, 0, 0, "没有完整可执行批次");
            return false;
        }
        List<FluidAmount> outputs = new ArrayList<>();
        if (batch.water.signum() > 0) outputs.add(new FluidAmount(GTModHandler.getDistilledWater(1), batch.water));
        if (CriticalEnergyPorts.outputs(host, outputs, NO_ITEMS) == null) {
            state.fail("批次输出空间不足，蒸汽已保留");
            return false;
        }
        UUID owner = WirelessNetworkManager.processInitialSettings(host.getBaseMetaTileEntity());
        if (owner == null) {
            state.fail("无线网络缺少控制器owner身份");
            return false;
        }
        data.setString("owner", owner.toString());
        return fund(state, data, debits, outputs, NO_ITEMS, batch.eu, batch.remainder);
    }

    private static boolean startSolar(CriticalMachineState state, MTECriticalMachine host, NBTTagCompound data) {
        List<CriticalEnergyPorts.Input> snapshot = CriticalEnergyPorts.snapshot(host);
        List<CriticalEnergyPorts.Debit> debits = new ArrayList<>();
        List<FluidAmount> outputs = new ArrayList<>();
        double factor = dimensionFactor(state.worldObj);
        int mode = Math.floorMod(data.getInteger("steamMode"), 6);
        FluidStack water = GTModHandler.getDistilledWater(1);
        BigInteger q = CriticalEnergyMath.logarithmic(
            CriticalEnergyPorts.total(snapshot, water),
            factor,
            CriticalEnergyMath.WATER_A,
            CriticalEnergyMath.WATER_B);
        if (mode >= 3) q = q.divide(BigInteger.valueOf(25))
            .multiply(BigInteger.valueOf(25));
        if (q.signum() > 0 && steam(mode) != null) {
            BigInteger steamAmount = mode < 3 ? q.multiply(BigInteger.valueOf(160))
                : q.divide(BigInteger.valueOf(25))
                    .multiply(BigInteger.valueOf(4));
            List<FluidAmount> candidate = new ArrayList<>(outputs);
            candidate.add(new FluidAmount(steam(mode), steamAmount));
            if (CriticalEnergyPorts.outputs(host, candidate, NO_ITEMS) != null) {
                outputs = candidate;
                debits.addAll(CriticalEnergyPorts.take(snapshot, water, q));
            }
        }
        FluidStack cold = MaterialMisc.SOLAR_SALT_COLD.getFluidStack(1);
        BigInteger salt = CriticalEnergyMath.logarithmic(
            CriticalEnergyPorts.total(snapshot, cold),
            factor,
            CriticalEnergyMath.SALT_A,
            CriticalEnergyMath.SALT_B);
        if (salt.signum() > 0) {
            List<FluidAmount> candidate = new ArrayList<>(outputs);
            candidate.add(new FluidAmount(MaterialMisc.SOLAR_SALT_HOT.getFluidStack(1), salt));
            if (CriticalEnergyPorts.outputs(host, candidate, NO_ITEMS) != null) {
                outputs = candidate;
                debits.addAll(CriticalEnergyPorts.take(snapshot, cold, salt));
            }
        }
        List<ItemStack> materialInputs = new ArrayList<>(), materialOutputs = new ArrayList<>();
        List<ItemStack> stock = new ArrayList<>();
        java.util.Collections.addAll(stock, host.criticalPhysicalItemsSnapshot());
        java.util.Collections.addAll(stock, host.criticalMEItemsSnapshot());
        int remaining = CriticalEnergyMath.IRRADIATION_PARALLEL;
        for (int i = 0; i < 2; i++) {
            ItemStack input = IrradiatedDust.source(i == 0);
            if (input == null) continue;
            long available = 0;
            for (ItemStack item : stock) if (same(item, input)) available += item.stackSize;
            int count = (int) Math.min(remaining, available);
            if (count <= 0) continue;
            ItemStack result = (i == 0 ? GTSRItemList.IrradiatedMoonDust : GTSRItemList.IrradiatedStoneDust).get(1);
            // GTUtility.copyAmount (used by ItemList.get) clamps counts to 64.
            result.stackSize = count;
            List<ItemStack> candidate = new ArrayList<>(materialOutputs);
            candidate.add(result);
            if (CriticalEnergyPorts.outputs(host, outputs, candidate.toArray(NO_ITEMS)) == null) continue;
            input.stackSize = count;
            materialInputs.add(input);
            materialOutputs = candidate;
            remaining -= count;
        }
        if (debits.isEmpty() && materialInputs.isEmpty()) {
            state.setRuntimeBatch(0, 0, 0, "没有完整可执行批次或输出空间不足");
            return false;
        }
        if (!CriticalEnergyPorts.canDebit(debits)) return state.fail("输入仓库存已变化，等待下一批快照");
        // Native item funding is bounded by the irradiation parallel, independently of fluid volume.
        if (!materialInputs.isEmpty() && !state.fundCriticalInputs(materialInputs.toArray(NO_ITEMS), NO_FLUIDS))
            return false;
        return fund(
            state,
            data,
            debits,
            outputs,
            materialOutputs.toArray(NO_ITEMS),
            BigInteger.ZERO,
            data.getInteger("waterRemainder"));
    }

    private static boolean fund(CriticalMachineState state, NBTTagCompound data, List<CriticalEnergyPorts.Debit> debits,
        List<FluidAmount> outputs, ItemStack[] items, BigInteger eu, int remainder) {
        if (items.length == 0 && !CriticalEnergyPorts.canDebit(debits)) return state.fail("输入仓库存已变化，等待下一批快照");
        List<FluidAmount> inputs = new ArrayList<>();
        for (CriticalEnergyPorts.Debit debit : debits)
            inputs.add(new FluidAmount(debit.source.fluid, BigInteger.valueOf(debit.amount)));
        data.setTag("inputs", writeFluids(inputs));
        data.setTag("outputs", writeFluids(outputs));
        data.setTag("items", writeItems(items));
        data.setString("eu", eu.toString());
        data.setInteger("nextWaterRemainder", remainder);
        data.setBoolean("uncertain", true);
        state.markDirty();
        if (!CriticalEnergyPorts.debit(debits)) {
            state.fail("输入仓提交结果不确定，批次已锁定");
            return false;
        }
        data.setTag("outputs", writeFluids(outputs));
        data.setTag("items", writeItems(items));
        data.setString("eu", eu.toString());
        data.setInteger("nextWaterRemainder", remainder);
        data.setInteger("progress", 0);
        data.setBoolean("funded", true);
        data.setBoolean("uncertain", false);
        data.setString(
            "batchId",
            UUID.randomUUID()
                .toString());
        state.markDirty();
        return true;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static BigInteger number(NBTTagCompound data, String key) {
        return data.hasKey(key) ? new BigInteger(data.getString(key)) : BigInteger.ZERO;
    }

    private static NBTTagList writeFluids(List<FluidAmount> fluids) {
        NBTTagList list = new NBTTagList();
        for (FluidAmount fluid : fluids) {
            NBTTagCompound entry = fluid.fluid.writeToNBT(new NBTTagCompound());
            entry.setString("exactAmount", fluid.amount.toString());
            list.appendTag(entry);
        }
        return list;
    }

    private static List<FluidAmount> readFluids(NBTTagList list) {
        List<FluidAmount> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            result.add(
                new FluidAmount(
                    FluidStack.loadFluidStackFromNBT(entry),
                    new BigInteger(entry.getString("exactAmount"))));
        }
        return result;
    }

    private static NBTTagList writeItems(ItemStack[] items) {
        NBTTagList list = new NBTTagList();
        for (ItemStack item : items) {
            NBTTagCompound n = item.writeToNBT(new NBTTagCompound());
            n.setInteger("exactCount", item.stackSize);
            list.appendTag(n);
        }
        return list;
    }

    private static ItemStack[] readItems(NBTTagList list) {
        ItemStack[] result = new ItemStack[list.tagCount()];
        for (int i = 0; i < result.length; i++) {
            NBTTagCompound n = list.getCompoundTagAt(i);
            result[i] = ItemStack.loadItemStackFromNBT(n);
            result[i].stackSize = n.getInteger("exactCount");
        }
        return result;
    }
}
