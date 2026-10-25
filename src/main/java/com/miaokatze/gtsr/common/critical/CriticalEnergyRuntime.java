package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import gregtech.api.util.GTUtility;
import gregtech.common.misc.WirelessNetworkManager;
import gtPlusPlus.core.material.MaterialMisc;
import gtnhintergalactic.config.IGConfig;

/** 400-tick energy batches, persisted independently from legacy recipe/tier/plugin arithmetic. */
public final class CriticalEnergyRuntime {

    public static final int CYCLE_TICKS = CriticalEnergyMath.CYCLE_TICKS;
    public static final int CHEMICAL_COMPOUND_SCHEMA = 3;
    private static final int CHEMICAL_POLICY_LEGACY_V2 = 2;
    private static final int CHEMICAL_POLICY_COMPOUND_V3 = 3;
    private static final ItemStack[] NO_ITEMS = new ItemStack[0];
    private static final FluidStack[] NO_FLUIDS = new FluidStack[0];

    private CriticalEnergyRuntime() {}

    /** Only the steam turbine has an adaptive idle efficiency. Compound batches have no idle decay. */
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
            && (data.getInteger("chemicalSchema") > CHEMICAL_COMPOUND_SCHEMA
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
        if (schema > CHEMICAL_COMPOUND_SCHEMA || schema < 0) return state.fail("化学燃料账本版本未知，已冻结等待核验");
        if (GTSRHatchFluidAccess
            .hasUnsupportedWorkingFluidSources(host.getCriticalInputHatches(), host.getCriticalDualInputHatches()))
            return state.fail("不支持的化学流体输入实现，原料保留");
        // Funded v1 batches finish their frozen EU before migration. Residual v1 heat is
        // paid at its recorded base value to the recorded owner, never repriced.
        if (data.getTagList("chemicalCredits", 10)
            .tagCount() != 0 && !settleChemicalEscrow(state, host)) return false;
        // v2 pending payments froze prices, but not the catalogue peak. Bind their old
        // 256-recipe policy before upgrading the envelope; an idle v2 machine uses v3.
        if (data.hasKey("chemicalCompoundPayment")) compoundPaymentPolicy(data, schema);
        if (schema != CHEMICAL_COMPOUND_SCHEMA || data.hasKey("chemicalWindow")) {
            data.setInteger("chemicalSchema", CHEMICAL_COMPOUND_SCHEMA);
            data.removeTag("chemicalWindow");
            state.markDirty();
        }
        List<CriticalEnergyPorts.Input> snapshot = CriticalEnergyPorts.snapshot(host);
        UUID owner = WirelessNetworkManager.processInitialSettings(host.getBaseMetaTileEntity());
        if (owner == null) return state.fail("无线网络缺少控制器owner身份");
        if (!data.hasKey("chemicalCompoundPayment")) {
            if (!captureCompoundPayment(state, data, snapshot, owner)) return false;
        } else if (!owner.toString()
            .equals(data.getString("chemicalEscrowOwner"))) {
                return state.fail("化学已付燃料收款人变化，需先核验托管");
            }
        NBTTagList payment = data.getTagList("chemicalCompoundPayment", 10);
        validateCompoundPayment(payment);
        CriticalEnergyPorts.Allocation allocation = new CriticalEnergyPorts.Allocation(snapshot);
        List<CriticalEnergyPorts.Debit> debits = new ArrayList<>();
        Map<CriticalEnergyPorts.Debit, NBTTagCompound> paidEntries = new java.util.IdentityHashMap<>();
        for (int i = 0; i < payment.tagCount(); i++) {
            NBTTagCompound entry = payment.getCompoundTagAt(i);
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry);
            BigInteger missing = number(entry, "targetLitres").subtract(number(entry, "confirmedLitres"));
            if (CriticalEnergyPorts.total(snapshot, fluid)
                .compareTo(missing) < 0) return state.fail("全仓批次实收未齐；已付流体托管，等待剩余原料");
            for (CriticalEnergyPorts.Debit debit : allocation.take(fluid, missing)) {
                debits.add(debit);
                paidEntries.put(debit, entry);
            }
        }
        if (!CriticalEnergyPorts.canDebit(debits)) return state.fail("燃料库存已变化，等待重新收款");
        NBTTagList intent = new NBTTagList();
        Map<CriticalEnergyPorts.Debit, NBTTagCompound> receipts = new java.util.IdentityHashMap<>();
        for (CriticalEnergyPorts.Debit debit : debits) {
            NBTTagCompound entry = debit.source.fluid.writeToNBT(new NBTTagCompound());
            entry.setString("requestedLitres", Long.toString(debit.amount));
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
        CriticalEnergyPorts.DebitResult result = CriticalEnergyPorts.debitConfirmed(debits, (debit, litres) -> {
            NBTTagCompound paid = paidEntries.get(debit), receipt = receipts.get(debit);
            BigInteger actual = BigInteger.valueOf(litres);
            paid.setString(
                "confirmedLitres",
                number(paid, "confirmedLitres").add(actual)
                    .toString());
            receipt.setString(
                "confirmedLitres",
                number(receipt, "confirmedLitres").add(actual)
                    .toString());
            state.markDirty();
        });
        if (result.uncertain) return state.fail("化学燃料实扣结果不确定，已付明细与扣款意图保留待核验");
        data.removeTag("chemicalDebitIntent");
        data.setBoolean("uncertain", false);
        state.markDirty();
        if (!result.complete) return state.fail("全仓批次实收未齐；已付流体托管，等待剩余原料");
        return fundCompoundPayment(state, data, owner);
    }

    /** Fix the complete visible snapshot before extracting any source, including zero-heat diluents. */
    private static boolean captureCompoundPayment(CriticalMachineState state, NBTTagCompound data,
        List<CriticalEnergyPorts.Input> snapshot, UUID owner) {
        Map<GTUtility.FluidId, BigInteger> volumes = new LinkedHashMap<>();
        Map<GTUtility.FluidId, FluidStack> fluids = new LinkedHashMap<>();
        for (CriticalEnergyPorts.Input input : snapshot) {
            GTUtility.FluidId id = GTUtility.FluidId.create(input.fluid);
            volumes.merge(id, BigInteger.valueOf(input.amount), BigInteger::add);
            fluids.putIfAbsent(id, input.fluid);
        }
        List<CriticalGTRecipes.ChemicalFuel> fuels = CriticalGTRecipes.chemicalFuels();
        CriticalChemicalRecipes.Match match = CriticalChemicalRecipes.best(fuels, volumes);
        if (match == null || !match.evaluation.eligible) {
            state.setRuntimeBatch(0, 0, 0, "至少需要一种完整复合方案的全部流体，原料保留");
            return false;
        }
        Map<GTUtility.FluidId, CriticalGTRecipes.ChemicalFuel> definitions = new LinkedHashMap<>();
        for (CriticalGTRecipes.ChemicalFuel fuel : fuels)
            definitions.putIfAbsent(GTUtility.FluidId.create(fuel.fluid), fuel);
        NBTTagList payment = new NBTTagList();
        for (Map.Entry<GTUtility.FluidId, BigInteger> volume : volumes.entrySet()) {
            if (volume.getValue()
                .signum() <= 0) continue;
            NBTTagCompound entry = fluids.get(volume.getKey())
                .writeToNBT(new NBTTagCompound());
            CriticalGTRecipes.ChemicalFuel fuel = definitions.get(volume.getKey());
            entry.setString(
                "targetLitres",
                volume.getValue()
                    .toString());
            entry.setString("confirmedLitres", "0");
            entry.setString("euPerL", fuel == null ? "0" : fuel.euPerL.toString());
            entry.setInteger("spectrum", fuel == null ? -1 : fuel.spectrum);
            payment.appendTag(entry);
        }
        data.setTag("chemicalCompoundPayment", payment);
        data.setInteger("chemicalPaymentPolicy", CHEMICAL_POLICY_COMPOUND_V3);
        data.setString("chemicalEscrowOwner", owner.toString());
        data.setString(
            "chemicalPaymentId",
            UUID.randomUUID()
                .toString());
        state.markDirty();
        return true;
    }

    private static void validateCompoundPayment(NBTTagList payment) {
        if (payment.tagCount() == 0) throw new IllegalStateException("Empty compound payment");
        java.util.Set<GTUtility.FluidId> seen = new java.util.HashSet<>();
        for (int i = 0; i < payment.tagCount(); i++) {
            NBTTagCompound entry = payment.getCompoundTagAt(i);
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry);
            BigInteger target = number(entry, "targetLitres"), paid = number(entry, "confirmedLitres");
            BigInteger price = number(entry, "euPerL");
            int spectrum = entry.getInteger("spectrum");
            if (fluid == null || !seen.add(GTUtility.FluidId.create(fluid))
                || target.signum() <= 0
                || paid.signum() < 0
                || paid.compareTo(target) > 0
                || price.signum() < 0
                || (price.signum() == 0 ? spectrum != -1 : spectrum < 0 || spectrum > 2))
                throw new IllegalStateException("Invalid compound payment");
        }
    }

    /** Persisted catalogue policy belongs to this manifest, never to the machine's idle epoch. */
    private static int compoundPaymentPolicy(NBTTagCompound data, int schema) {
        if (!data.hasKey("chemicalPaymentPolicy")) {
            if (schema != CHEMICAL_POLICY_LEGACY_V2) throw new IllegalStateException("Missing compound payment policy");
            data.setInteger("chemicalPaymentPolicy", CHEMICAL_POLICY_LEGACY_V2);
        }
        int policy = data.getInteger("chemicalPaymentPolicy");
        if (policy != CHEMICAL_POLICY_LEGACY_V2 && policy != CHEMICAL_POLICY_COMPOUND_V3)
            throw new IllegalStateException("Unknown compound payment policy");
        if (schema < CHEMICAL_POLICY_LEGACY_V2
            || (schema == CHEMICAL_POLICY_LEGACY_V2 && policy != CHEMICAL_POLICY_LEGACY_V2))
            throw new IllegalStateException("Compound payment policy does not match schema");
        return policy;
    }

    private static boolean fundCompoundPayment(CriticalMachineState state, NBTTagCompound data, UUID owner) {
        NBTTagList payment = data.getTagList("chemicalCompoundPayment", 10);
        validateCompoundPayment(payment);
        int policy = compoundPaymentPolicy(data, data.getInteger("chemicalSchema"));
        List<CriticalGTRecipes.ChemicalFuel> fuels = new ArrayList<>();
        Map<GTUtility.FluidId, BigInteger> paid = new LinkedHashMap<>();
        for (int i = 0; i < payment.tagCount(); i++) {
            NBTTagCompound entry = payment.getCompoundTagAt(i);
            BigInteger litres = number(entry, "confirmedLitres");
            if (!litres.equals(number(entry, "targetLitres")))
                throw new IllegalStateException("Compound batch not fully paid");
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry);
            paid.put(GTUtility.FluidId.create(fluid), litres);
            BigInteger price = number(entry, "euPerL");
            if (price.signum() > 0)
                fuels.add(new CriticalGTRecipes.ChemicalFuel(fluid, price, entry.getInteger("spectrum")));
        }
        CriticalChemicalRecipes.Match match = policy == CHEMICAL_POLICY_LEGACY_V2
            ? CriticalChemicalRecipes.bestLegacyV2(fuels, paid)
            : CriticalChemicalRecipes.best(fuels, paid);
        if (match == null || !match.evaluation.eligible) return state.fail("实付组成缺少完整复合方案，已付原料托管");
        CriticalChemicalMath.Evaluation evaluation = match.evaluation;
        data.setString("owner", owner.toString());
        data.setString("chemicalRecipe", match.recipe.id);
        data.setInteger("chemicalBatchRule", policy);
        data.setInteger("chemicalEfficiencyHundredths", evaluation.efficiencyHundredths());
        data.setString("chemicalBatchHeat", match.rawHeat.toString());
        data.setString("chemicalBatchLitres", match.totalLitres.toString());
        data.setString("lastRawHeat", match.rawHeat.toString());
        data.setTag("chemicalBatchCredits", payment.copy());
        data.removeTag("chemicalCompoundPayment");
        data.removeTag("chemicalPaymentPolicy");
        data.removeTag("chemicalEscrowOwner");
        data.setTag("outputs", new NBTTagList());
        data.setTag("items", new NBTTagList());
        // Keep the exact rational efficiency through settlement, flooring only this final EU.
        data.setString("eu", evaluation.generatedEU.toString());
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

    public static boolean hasChemicalEscrow(NBTTagCompound data) {
        return data.getTagList("chemicalCredits", 10)
            .tagCount() != 0 || data.hasKey("chemicalCompoundPayment");
    }

    /** Legacy paid heat is valued at extraction time; migration never reprices it. */
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

    /** Legacy migration or dismantling: paid base heat gets exactly 1x, without another fluid refund. */
    public static boolean settleChemicalEscrow(CriticalMachineState state, MTECriticalMachine host) {
        NBTTagCompound data = state.energyRuntimeData();
        if (data.getBoolean("funded") || data.getBoolean("uncertain")) return false;
        if (!hasChemicalEscrow(data)) return true;
        int schema = data.getInteger("chemicalSchema");
        if (schema < 0 || schema > CHEMICAL_COMPOUND_SCHEMA) return state.fail("化学燃料账本版本未知，不能结清托管");
        try {
            BigInteger eu = CriticalChemicalMath.sum(chemicalPaidHeat(data));
            if (data.hasKey("chemicalCompoundPayment")) {
                compoundPaymentPolicy(data, schema);
                NBTTagList payment = data.getTagList("chemicalCompoundPayment", 10);
                validateCompoundPayment(payment);
                for (int i = 0; i < payment.tagCount(); i++) {
                    NBTTagCompound entry = payment.getCompoundTagAt(i);
                    eu = eu.add(number(entry, "confirmedLitres").multiply(number(entry, "euPerL")));
                }
            }
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
            data.removeTag("chemicalCompoundPayment");
            data.removeTag("chemicalPaymentPolicy");
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
