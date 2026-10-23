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

import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
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

    /** Only the steam turbine has an adaptive idle efficiency. Chemical windows have no idle decay. */
    public static void idleTick(CriticalMachineState state) {
        if (!adaptive(state)) return;
        NBTTagCompound data = state.energyRuntimeData();
        long now = state.worldObj.getTotalWorldTime();
        if (data.hasKey("lastIdleTick") && data.getLong("lastIdleTick") == now) return;
        data.setLong("lastIdleTick", now);
        if (data.hasKey("lastWorkTick") && data.getLong("lastWorkTick") == now - 1) {
            if (data.getInteger("idleTicks") != 0) {
                data.setInteger("idleTicks", 0);
                state.markDirty();
            }
            return;
        }
        int idle = data.getInteger("idleTicks") + 1;
        if (idle >= CYCLE_TICKS) {
            data.setInteger(
                "efficiency",
                CriticalEnergyMath.efficiency(
                    CriticalEnergyMath.efficiency(data.getInteger("efficiency")) - CriticalEnergyMath.EFFICIENCY_STEP));
            idle = 0;
        }
        data.setInteger("idleTicks", idle);
        state.markDirty();
    }

    private static boolean adaptive(CriticalMachineState state) {
        return state.getMachineKind() == CriticalMachineKind.TURBINE;
    }

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
        if (state.getMachineKind() == CriticalMachineKind.BATTERY
            && (data.getInteger("chemicalSchema") > CriticalChemicalMath.RULE_VERSION
                || data.getInteger("chemicalSchema") < 0)) {
            state.fail("化学燃料账本版本未知，已冻结等待核验");
            return;
        }
        if (data.getBoolean("uncertain")) {
            state.fail("批次提交结果不确定，已锁定以保护原料和无线电量");
            return;
        }
        if (!data.getBoolean("funded")) {
            if (GTSRHatchFluidAccess.hasUnsupportedDuals(host.getCriticalDualInputHatches())) {
                state.fail("不支持的工作流体输入实现；当前仅支持GT205原生样板仓与Slave");
                return;
            }
            boolean started = solar ? startSolar(state, host, data)
                : state.getMachineKind() == CriticalMachineKind.BATTERY ? startChemical(state, host, data)
                    : startTurbine(state, host, data);
            if (!started) return;
        }
        int progress = data.getInteger("progress");
        if (progress < CYCLE_TICKS) {
            data.setInteger("progress", ++progress);
            data.setLong("lastWorkTick", state.worldObj.getTotalWorldTime());
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
            data.setInteger("generationRemainder", data.getInteger("nextGenerationRemainder"));
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
        if (batch.rawHeat.signum() == 0) {
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
        if (!fundAdaptive(state, data, debits, outputs, batch.rawHeat, batch.remainder)) return false;
        for (int i = 0; i < 6; i++) data.setString("turbineVolume" + i, volumes[i].toString());
        data.setString("turbineRawHeat", batch.rawHeat.toString());
        data.setString("turbineWater", batch.water.toString());
        data.setInteger("turbineEfficiency", data.getInteger("efficiency"));
        state.markDirty();
        return true;
    }

    private static boolean startChemical(CriticalMachineState state, MTECriticalMachine host, NBTTagCompound data) {
        try {
            return startChemicalConfirmed(state, host, data);
        } catch (RuntimeException failure) {
            data.setBoolean("uncertain", true);
            data.setString(
                "commitFailure",
                failure.getClass()
                    .getSimpleName());
            state.markDirty();
            return state.fail("化学燃料账本或实扣结果异常，已锁定等待核验");
        }
    }

    private static boolean startChemicalConfirmed(CriticalMachineState state, MTECriticalMachine host,
        NBTTagCompound data) {
        int schema = data.getInteger("chemicalSchema");
        if (schema > CriticalChemicalMath.RULE_VERSION || schema < 0) return state.fail("化学燃料账本版本未知，已冻结等待核验");
        // A funded legacy batch never reaches here: tick() completes its locked old bill first.
        data.setInteger("chemicalSchema", CriticalChemicalMath.RULE_VERSION);
        int window = getChemicalWindow(data);
        List<CriticalGTRecipes.ChemicalFuel> fuels = CriticalGTRecipes.chemicalFuels();
        List<CriticalEnergyPorts.Input> snapshot = CriticalEnergyPorts.snapshot(host);
        BigInteger[] available = chemicalPaidHeat(data);
        for (CriticalGTRecipes.ChemicalFuel fuel : fuels) available[fuel.spectrum] = available[fuel.spectrum].add(
            CriticalEnergyPorts.total(snapshot, fuel.fluid)
                .multiply(fuel.euPerL));
        BigInteger[] target = CriticalChemicalMath.match(window, available, CriticalChemicalMath.cap(window));
        data.setString("chemicalBottleneck", chemicalBottleneck(window, available));
        if (CriticalChemicalMath.sum(target)
            .signum() == 0) {
            state.setRuntimeBatch(0, 0, 0, "所选化学工作窗缺少匹配燃料，余料保留");
            return false;
        }
        UUID owner = WirelessNetworkManager.processInitialSettings(host.getBaseMetaTileEntity());
        if (owner == null) return state.fail("无线网络缺少控制器owner身份");
        if (hasChemicalEscrow(data) && (!data.hasKey("chemicalEscrowOwner") || !owner.toString()
            .equals(data.getString("chemicalEscrowOwner")))) return state.fail("化学已付燃料收款人变化，需先核验托管");
        data.setString("owner", owner.toString());
        data.setString("chemicalEscrowOwner", owner.toString());
        BigInteger[] paidBefore = chemicalPaidHeat(data);
        BigInteger[] missing = new BigInteger[3];
        for (int i = 0; i < 3; i++) missing[i] = target[i].subtract(paidBefore[i])
            .max(BigInteger.ZERO);
        CriticalEnergyPorts.Allocation allocation = new CriticalEnergyPorts.Allocation(snapshot);
        List<CriticalEnergyPorts.Debit> debits = new ArrayList<>();
        java.util.Map<CriticalEnergyPorts.Debit, CriticalGTRecipes.ChemicalFuel> definitions = new java.util.IdentityHashMap<>();
        for (CriticalGTRecipes.ChemicalFuel fuel : fuels) {
            BigInteger amount = CriticalChemicalMath.litresForHeat(missing[fuel.spectrum], fuel.euPerL)
                .min(CriticalEnergyPorts.total(snapshot, fuel.fluid));
            if (amount.signum() == 0) continue;
            for (CriticalEnergyPorts.Debit debit : allocation.take(fuel.fluid, amount)) {
                debits.add(debit);
                definitions.put(debit, fuel);
            }
            missing[fuel.spectrum] = missing[fuel.spectrum].subtract(amount.multiply(fuel.euPerL))
                .max(BigInteger.ZERO);
        }
        if (!CriticalEnergyPorts.canDebit(debits)) return state.fail("燃料库存已变化，等待重新规划");
        NBTTagList intent = new NBTTagList();
        java.util.Map<CriticalEnergyPorts.Debit, NBTTagCompound> receipts = new java.util.IdentityHashMap<>();
        for (CriticalEnergyPorts.Debit debit : debits) {
            CriticalGTRecipes.ChemicalFuel fuel = definitions.get(debit);
            NBTTagCompound entry = debit.source.fluid.writeToNBT(new NBTTagCompound());
            entry.setString("requestedLitres", Long.toString(debit.amount));
            entry.setString("euPerL", fuel.euPerL.toString());
            entry.setInteger("spectrum", fuel.spectrum);
            entry.setString("confirmedLitres", "0");
            intent.appendTag(entry);
            receipts.put(debit, entry);
        }
        data.setTag("chemicalDebitIntent", intent);
        data.setString(
            "chemicalDebitId",
            UUID.randomUUID()
                .toString());
        data.setBoolean("uncertain", true);
        state.markDirty();
        BigInteger[] actualLitres = { BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO };
        CriticalEnergyPorts.DebitResult result = CriticalEnergyPorts.debitConfirmed(debits, (debit, litres) -> {
            CriticalGTRecipes.ChemicalFuel fuel = definitions.get(debit);
            addChemicalCredit(data, fuel, litres);
            NBTTagCompound receipt = receipts.get(debit);
            receipt.setString(
                "confirmedLitres",
                number(receipt, "confirmedLitres").add(BigInteger.valueOf(litres))
                    .toString());
            actualLitres[fuel.spectrum] = actualLitres[fuel.spectrum].add(BigInteger.valueOf(litres));
            state.markDirty();
        });
        for (int i = 0; i < 3; i++) data.setString("chemicalLitres" + i, actualLitres[i].toString());
        if (result.uncertain) return state.fail("化学燃料实扣结果不确定，已付明细与扣款意图保留待核验");
        data.removeTag("chemicalDebitIntent");
        data.setBoolean("uncertain", false);
        BigInteger[] matched = CriticalChemicalMath
            .match(window, chemicalPaidHeat(data), CriticalChemicalMath.sum(target));
        BigInteger heat = CriticalChemicalMath.sum(matched);
        if (heat.signum() == 0) {
            state.markDirty();
            return state.fail("实际已付燃料不足完整比例，已托管并等待补料");
        }
        consumeChemicalCredit(data, matched);
        data.setInteger("chemicalBatchWindow", window);
        data.setString("chemicalBatchHeat", heat.toString());
        data.setString("lastRawHeat", heat.toString());
        for (int i = 0; i < 3; i++) data.setString("chemicalHeat" + i, matched[i].toString());
        for (int i = 0; i < 3; i++) data.setString("chemicalLitres" + i, actualLitres[i].toString());
        data.setTag("outputs", new NBTTagList());
        data.setTag("items", new NBTTagList());
        data.setString(
            "eu",
            heat.multiply(BigInteger.valueOf(CriticalChemicalMath.multiplier(window)))
                .toString());
        data.setInteger("nextWaterRemainder", data.getInteger("waterRemainder"));
        data.setInteger("nextGenerationRemainder", data.getInteger("generationRemainder"));
        data.setInteger("progress", 0);
        data.setBoolean("funded", true);
        data.setString(
            "batchId",
            UUID.randomUUID()
                .toString());
        state.markDirty();
        return true;
    }

    private static String chemicalBottleneck(int window, BigInteger[] available) {
        BigInteger cap = CriticalChemicalMath.cap(window);
        if (window == 3) return CriticalChemicalMath.sum(available)
            .compareTo(cap) >= 0 ? "cap" : "stock";
        int[] weights = window == 0 ? new int[] { 1, 1, 0 }
            : window == 1 ? new int[] { 1, 1, 1 } : new int[] { 1, 1, 4 };
        BigInteger common = cap.divide(BigInteger.valueOf(window == 0 ? 2 : window == 1 ? 3 : 6));
        String limit = "cap";
        for (int i = 0; i < 3; i++) if (weights[i] > 0) {
            BigInteger branch = available[i].divide(BigInteger.valueOf(weights[i]));
            if (branch.compareTo(common) < 0) {
                common = branch;
                limit = new String[] { "D", "G", "R" }[i];
            }
        }
        return limit;
    }

    public static int getChemicalWindow(NBTTagCompound data) {
        return CriticalChemicalMath.window(data.getInteger("chemicalWindow"));
    }

    /** Caller owns player permission checks. Selection only changes future batches. */
    public static boolean cycleChemicalWindow(CriticalMachineState state) {
        NBTTagCompound data = state.energyRuntimeData();
        if (state.getMachineKind() != CriticalMachineKind.BATTERY || data.getBoolean("funded")
            || data.getBoolean("uncertain")) return false;
        data.setInteger("chemicalWindow", (getChemicalWindow(data) + 1) % CriticalChemicalMath.WINDOW_COUNT);
        state.markDirty();
        return true;
    }

    public static boolean hasChemicalEscrow(NBTTagCompound data) {
        return data.getTagList("chemicalCredits", 10)
            .tagCount() != 0;
    }

    /** Paid heat is valued at extraction time; fuel changes and window selection never reprice it. */
    public static BigInteger[] chemicalPaidHeat(NBTTagCompound data) {
        BigInteger[] heat = { BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO };
        NBTTagList credits = data.getTagList("chemicalCredits", 10);
        for (int i = 0; i < credits.tagCount(); i++) {
            NBTTagCompound credit = credits.getCompoundTagAt(i);
            int spectrum = credit.getInteger("spectrum");
            BigInteger remaining = number(credit, "remainingHeat");
            BigInteger price = number(credit, "euPerL"), litres = number(credit, "paidLitres");
            if (spectrum < 0 || spectrum > 2
                || remaining.signum() <= 0
                || price.signum() <= 0
                || litres.signum() <= 0
                || remaining.compareTo(price.multiply(litres)) > 0
                || credit.getInteger("ruleVersion") != CriticalChemicalMath.RULE_VERSION
                || FluidStack.loadFluidStackFromNBT(credit) == null)
                throw new IllegalStateException("Invalid chemical escrow");
            heat[spectrum] = heat[spectrum].add(remaining);
        }
        return heat;
    }

    private static void addChemicalCredit(NBTTagCompound data, CriticalGTRecipes.ChemicalFuel fuel, long litres) {
        NBTTagList credits = data.getTagList("chemicalCredits", 10);
        NBTTagCompound credit = null;
        for (int i = 0; i < credits.tagCount(); i++) {
            NBTTagCompound entry = credits.getCompoundTagAt(i);
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry);
            if (fluid != null && fluid.isFluidEqual(fuel.fluid)
                && entry.getInteger("spectrum") == fuel.spectrum
                && number(entry, "euPerL").equals(fuel.euPerL)) {
                credit = entry;
                break;
            }
        }
        if (credit == null) {
            credit = fuel.fluid.writeToNBT(new NBTTagCompound());
            credit.setInteger("spectrum", fuel.spectrum);
            credit.setInteger("ruleVersion", CriticalChemicalMath.RULE_VERSION);
            credit.setString("euPerL", fuel.euPerL.toString());
            credits.appendTag(credit);
        }
        BigInteger amount = BigInteger.valueOf(litres);
        credit.setString(
            "paidLitres",
            number(credit, "paidLitres").add(amount)
                .toString());
        credit.setString(
            "remainingHeat",
            number(credit, "remainingHeat").add(amount.multiply(fuel.euPerL))
                .toString());
        data.setTag("chemicalCredits", credits);
    }

    private static void consumeChemicalCredit(NBTTagCompound data, BigInteger[] matched) {
        BigInteger[] left = matched.clone();
        NBTTagList credits = data.getTagList("chemicalCredits", 10);
        NBTTagList remainder = new NBTTagList(), consumed = new NBTTagList();
        for (int i = 0; i < credits.tagCount(); i++) {
            NBTTagCompound credit = credits.getCompoundTagAt(i);
            int spectrum = credit.getInteger("spectrum");
            BigInteger heat = number(credit, "remainingHeat");
            BigInteger used = left[spectrum].min(heat);
            left[spectrum] = left[spectrum].subtract(used);
            if (used.signum() > 0) {
                NBTTagCompound receipt = (NBTTagCompound) credit.copy();
                receipt.setString("consumedHeat", used.toString());
                consumed.appendTag(receipt);
            }
            if (heat.compareTo(used) > 0) {
                credit.setString(
                    "remainingHeat",
                    heat.subtract(used)
                        .toString());
                remainder.appendTag(credit);
            }
        }
        if (CriticalChemicalMath.sum(left)
            .signum() != 0) throw new IllegalStateException("Chemical escrow shortage");
        data.setTag("chemicalCredits", remainder);
        data.setTag("chemicalBatchCredits", consumed);
    }

    /** Dismantle-only closeout: residual paid base heat gets 1x, never a window bonus or another fluid refund. */
    public static boolean settleChemicalEscrow(CriticalMachineState state, MTECriticalMachine host) {
        NBTTagCompound data = state.energyRuntimeData();
        if (data.getBoolean("funded") || data.getBoolean("uncertain")) return false;
        if (!hasChemicalEscrow(data)) return true;
        if (data.getInteger("chemicalSchema") != CriticalChemicalMath.RULE_VERSION)
            return state.fail("化学燃料账本版本未知，不能结清托管");
        try {
            BigInteger eu = CriticalChemicalMath.sum(chemicalPaidHeat(data));
            // Preserve the GT owner captured when these actual litres were bought, even if the host owner changed.
            UUID owner = UUID.fromString(data.getString("chemicalEscrowOwner"));
            data.setString(
                "chemicalCloseoutId",
                UUID.randomUUID()
                    .toString());
            data.setString("chemicalCloseoutEU", eu.toString());
            data.setBoolean("uncertain", true);
            state.markDirty();
            WirelessNetworkManager.strongCheckOrAddUser(owner);
            if (eu.signum() > 0 && !WirelessNetworkManager.addEUToGlobalEnergyMap(owner, eu))
                throw new IllegalStateException("Wireless chemical closeout rejected");
            data.removeTag("chemicalCredits");
            data.removeTag("chemicalEscrowOwner");
            data.setBoolean("uncertain", false);
            state.markDirty();
            return true;
        } catch (RuntimeException failure) {
            data.setBoolean("uncertain", true);
            data.setString(
                "commitFailure",
                failure.getClass()
                    .getSimpleName());
            state.markDirty();
            return state.fail("化学余热结清结果不确定，已锁定等待核验");
        }
    }

    private static boolean fundAdaptive(CriticalMachineState state, NBTTagCompound data,
        List<CriticalEnergyPorts.Debit> debits, List<FluidAmount> outputs, BigInteger heat, int waterRemainder) {
        int efficiency = CriticalEnergyMath
            .trendEfficiency(data.getInteger("efficiency"), number(data, "lastRawHeat"), heat);
        // Common unit is half-EU; keep even fractional efficiency EU across batches (denominator 200).
        BigInteger[] energy = heat.multiply(BigInteger.valueOf(efficiency))
            .add(BigInteger.valueOf(data.getInteger("generationRemainder")))
            .divideAndRemainder(BigInteger.valueOf(200));
        if (!fund(state, data, debits, outputs, NO_ITEMS, energy[0], waterRemainder)) return false;
        data.setInteger("nextGenerationRemainder", energy[1].intValue());
        data.setInteger("efficiency", efficiency);
        data.setInteger("idleTicks", 0);
        data.setString("lastRawHeat", heat.toString());
        state.markDirty();
        return true;
    }

    private static boolean startSolar(CriticalMachineState state, MTECriticalMachine host, NBTTagCompound data) {
        List<CriticalEnergyPorts.Input> snapshot = CriticalEnergyPorts.snapshot(host);
        List<CriticalEnergyPorts.Debit> debits = new ArrayList<>();
        List<FluidAmount> outputs = new ArrayList<>();
        double factor = dimensionFactor(state.worldObj);
        BigInteger paidWater = BigInteger.ZERO, paidSalt = BigInteger.ZERO;
        String waterStatus = "no_input", saltStatus = "no_input";
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
            waterStatus = "output_full";
            BigInteger steamAmount = mode < 3 ? q.multiply(BigInteger.valueOf(160))
                : q.divide(BigInteger.valueOf(25))
                    .multiply(BigInteger.valueOf(4));
            List<FluidAmount> candidate = new ArrayList<>(outputs);
            candidate.add(new FluidAmount(steam(mode), steamAmount));
            if (CriticalEnergyPorts.outputs(host, candidate, NO_ITEMS) != null) {
                outputs = candidate;
                debits.addAll(CriticalEnergyPorts.take(snapshot, water, q));
                paidWater = q;
                waterStatus = "paid";
            }
        }
        FluidStack cold = MaterialMisc.SOLAR_SALT_COLD.getFluidStack(1);
        BigInteger salt = CriticalEnergyMath.logarithmic(
            CriticalEnergyPorts.total(snapshot, cold),
            factor,
            CriticalEnergyMath.SALT_A,
            CriticalEnergyMath.SALT_B);
        if (salt.signum() > 0) {
            saltStatus = "output_full";
            List<FluidAmount> candidate = new ArrayList<>(outputs);
            candidate.add(new FluidAmount(MaterialMisc.SOLAR_SALT_HOT.getFluidStack(1), salt));
            if (CriticalEnergyPorts.outputs(host, candidate, NO_ITEMS) != null) {
                outputs = candidate;
                debits.addAll(CriticalEnergyPorts.take(snapshot, cold, salt));
                paidSalt = salt;
                saltStatus = "paid";
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
        boolean funded = fund(
            state,
            data,
            debits,
            outputs,
            materialOutputs.toArray(NO_ITEMS),
            BigInteger.ZERO,
            data.getInteger("waterRemainder"));
        if (!funded) return false;
        data.setDouble("solarFactor", factor);
        data.setString("solarWater", paidWater.toString());
        data.setString("solarSalt", paidSalt.toString());
        data.setString("solarWaterStatus", waterStatus);
        data.setString("solarSaltStatus", saltStatus);
        data.setBoolean("solarNightClaimed", true);
        data.setInteger("solarBatchSteamMode", mode);
        data.setInteger("solarMoonDust", 0);
        data.setInteger("solarStoneDust", 0);
        for (ItemStack item : materialInputs) {
            if (same(item, IrradiatedDust.source(true))) data.setInteger("solarMoonDust", item.stackSize);
            if (same(item, IrradiatedDust.source(false))) data.setInteger("solarStoneDust", item.stackSize);
        }
        state.markDirty();
        return true;
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
