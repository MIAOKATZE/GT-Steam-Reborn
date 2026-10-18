package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.ProbeSupport.*;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** One focused jar-only release contract. Native GT classes; seeded recipes, not a complete Forge server. */
public final class Release46Probe {
    static BigInteger n(long value) { return BigInteger.valueOf(value); }
    static void tick(CriticalMachineState s, MTECriticalMachine h) {
        SparseWorld w = (SparseWorld) s.worldObj;
        w.setWorldTime(w.getTotalWorldTime() + 1);
        CriticalEnergyRuntime.tick(s, h);
    }
    static void cycle(CriticalMachineState s, MTECriticalMachine h) {
        for (int i = 0; i < 400; i++) tick(s, h);
    }
    static CriticalMachineState restore(CriticalMachineState s) {
        NBTTagCompound saved = new NBTTagCompound(); s.writeToNBT(saved);
        CriticalMachineState r = new CriticalMachineState(); r.readFromNBT(saved); return r;
    }
    static void math() {
        check(EnumSet.copyOf(Arrays.asList(CriticalMachineKind.releasedValues())).equals(
            EnumSet.of(CriticalMachineKind.SUN, CriticalMachineKind.SOLAR, CriticalMachineKind.BATTERY,
                CriticalMachineKind.TURBINE)), "exactly four released kinds; historical ordinals retained");
        for (int i = 0; i < 6; i++) {
            long[] volumes = new long[6]; volumes[i] = 2;
            long expected = new long[] {1, 3, 5, 1000, 3000, 5000}[i];
            check(CriticalEnergyMath.turbine(volumes, 0).eu.equals(n(expected)),
                "steam grade " + i + " mega-turbine overlimit EU/L, dense x1000");
        }
        check(CriticalEnergyMath.trendEfficiency(1000, n(100), n(110)) == 1000
            && CriticalEnergyMath.trendEfficiency(1000, n(100), n(90)) == 1000,
            "exact +/-10 percent leaves efficiency unchanged");
        check(CriticalEnergyMath.trendEfficiency(1000, n(100), n(111)) == 1010
            && CriticalEnergyMath.trendEfficiency(1000, n(100), n(89)) == 990,
            "strict exterior thresholds step by ten percentage points");
        check(CriticalEnergyMath.trendEfficiency(1995, n(100), n(111)) == 2000
            && CriticalEnergyMath.trendEfficiency(100, n(100), n(89)) == 100,
            "adaptive efficiency caps at 100..2000 percent");
    }
    static void maps() throws Exception {
        MTECriticalMachine loom = host(state(CriticalMachineKind.SUN)); set(loom, "presetKind", null);
        check(loom.getRecipeMap() == null && loom.getAvailableRecipeMaps().isEmpty(),
            "loom exposes no production recipe map or GT catalyst map");
        for (CriticalMachineKind kind : CriticalMachineKind.values()) {
            MTECriticalMachine h = host(state(kind));
            check(h.getAvailableRecipeMaps().size() == (kind == CriticalMachineKind.SOLAR ? 2 : kind.isReleased() ? 1 : 0),
                "native catalyst-map exposure follows released kind " + kind.key);
        }
    }
    static void turbine() throws Exception {
        CriticalMachineState s = state(CriticalMachineKind.TURBINE);
        MTECriticalMachine h = EnergyProbe.hostOwned(s);
        MTEHatchOutput out = EnergyProbe.output(); add(h, "mOutputHatches", out);
        long[] volumes = {159, 1, 160, 1, 2, 3};
        for (int i = 0; i < 6; i++) {
            FluidStack steam = CriticalEnergyRuntime.steam(i); steam.amount = (int) volumes[i];
            MTEHatchInput in = EnergyProbe.input(steam); add(h, "mInputHatches", in);
            if (i == 0) add(h, "mInputHatches", in);
        }
        WirelessNetworkManager.setUserEU(EnergyProbe.OWNER, BigInteger.ZERO);
        tick(s, h);
        check(h.criticalPhysicalFluidsSnapshot().length == 0, "six native steam hatches debit once despite duplicate reference");
        for (int i = 1; i < 199; i++) tick(s, h);
        String locked = s.energyRuntimeData().getString("eu"); s.energyRuntimeData().setInteger("efficiency", 200);
        for (int i = 0; i < 401; i++) {
            s.worldObj.setWorldTime(s.worldObj.getTotalWorldTime() + 1); CriticalEnergyRuntime.idleTick(s);
        }
        check(s.energyRuntimeData().getInteger("efficiency") == 190
            && s.energyRuntimeData().getString("eu").equals(locked),
            "paused paid batch decays future efficiency without changing locked batch EU");
        CriticalMachineState r = restore(s); MTECriticalMachine rh = EnergyProbe.hostOwned(r);
        add(rh, "mOutputHatches", out);
        for (int i = 0; i < 200; i++) tick(r, rh);
        check(out.mFluid == null && r.energyRuntimeData().getInteger("progress") == 399,
            "paid NBT batch resumes to tick399 without early native output");
        tick(r, rh);
        check(out.mFluid.amount == 39 && r.energyRuntimeData().getInteger("waterRemainder") == 80,
            "400tick settlement condenses exact 39 water with 80/160 carry");
        check(WirelessNetworkManager.getUserEU(EnergyProbe.LEADER).equals(n(11481)),
            "six steam grades credit exact native team wireless EU at initial100 percent");
        tick(r, rh);
        check(WirelessNetworkManager.getUserEU(EnergyProbe.LEADER).equals(n(11481)),
            "settled paid journal cannot replay on empty next tick");
        r.energyRuntimeData().setInteger("efficiency", 200);
        r.energyRuntimeData().setString("lastRawHeat", "1");
        add(rh, "mInputHatches", EnergyProbe.input(CriticalEnergyRuntime.steam(0)));
        cycle(r, rh);
        check(r.energyRuntimeData().getString("lastGeneratedEU").equals("1"),
            "1L ordinary steam at200 percent yields1EU before any premature floor");
        r.energyRuntimeData().setInteger("efficiency", 100);
        add(rh, "mInputHatches", EnergyProbe.input(CriticalEnergyRuntime.steam(0)));
        cycle(r, rh);
        check(r.energyRuntimeData().getString("lastGeneratedEU").equals("0")
            && r.energyRuntimeData().getInteger("generationRemainder") == 100,
            "first half-EU batch retains100/200 generation remainder");
        CriticalMachineState rr = restore(r); MTECriticalMachine rrh = EnergyProbe.hostOwned(rr);
        add(rrh, "mOutputHatches", out);
        add(rrh, "mInputHatches", EnergyProbe.input(CriticalEnergyRuntime.steam(0))); cycle(rr, rrh);
        check(rr.energyRuntimeData().getString("lastGeneratedEU").equals("1")
            && rr.energyRuntimeData().getInteger("generationRemainder") == 0,
            "reloaded second half-EU batch releases1EU without losing fraction");
        FluidStack next = CriticalEnergyRuntime.steam(0); next.amount = 77;
        add(rrh, "mInputHatches", EnergyProbe.input(next)); cycle(rr, rrh);
        check(out.mFluid.amount == 40 && rr.energyRuntimeData().getInteger("waterRemainder") == 0,
            "persisted80 plus1+1+1+77 normal steam condenses exactly one further water");
    }
    static GTRecipe fuel(Fluid f, int value) {
        return new GTRecipe(false, null, null, null, null, null, null, null,
            new FluidStack[]{new FluidStack(f, 1)}, null, 1, 0, value);
    }
    static void chemical() throws Exception {
        RecipeMap<?>[] maps = {RecipeMaps.dieselFuels, RecipeMaps.gasTurbineFuels, RecipeMaps.rocketFuels};
        Fluid[] fluids = new Fluid[3];
        for (int i = 0; i < maps.length; i++) {
            fluids[i] = EnergyProbe.fluid("release46.fuel." + i);
            GTRecipe nativeFuel = fuel(fluids[i], 100 + i);
            if (i == 1) {
                Item full = CoreProbe.item(31970, "release46.gas.full");
                Item empty = CoreProbe.item(31971, "release46.gas.empty");
                FluidContainerRegistry.registerFluidContainer(new FluidStack(fluids[i], 1000),
                    new ItemStack(full), new ItemStack(empty));
                nativeFuel.mInputs = new ItemStack[]{new ItemStack(full)};
                nativeFuel.mFluidInputs = new FluidStack[0];
            }
            check(maps[i].addRecipe(nativeFuel, false, false, false) != null,
                "seed dedicated liquid recipe in native UCFE fuel map " + maps[i].unlocalizedName);
        }
        List<CriticalGTRecipes.ChemicalFuel> decoded = CriticalGTRecipes.chemicalFuels();
        for (int i = 0; i < fluids.length; i++) {
            BigInteger expected = n((100 + i) * (i == 2 ? 3 : 1)); boolean found = false;
            for (CriticalGTRecipes.ChemicalFuel fuel : decoded)
                if (fuel.fluid.getFluid() == fluids[i]) found = fuel.euPerL.equals(expected);
            check(found, "native fuel map " + i + " energy decoding including rocket x3");
        }
        CriticalMachineState s = state(CriticalMachineKind.BATTERY); MTECriticalMachine h = EnergyProbe.hostOwned(s);
        for (Fluid f : fluids) add(h, "mInputHatches", EnergyProbe.input(new FluidStack(f, 2)));
        WirelessNetworkManager.setUserEU(EnergyProbe.OWNER, BigInteger.ZERO); cycle(s, h);
        check(h.criticalPhysicalFluidsSnapshot().length == 0
            && WirelessNetworkManager.getUserEU(EnergyProbe.LEADER).equals(n(1014)),
            "battery consumes all three native fuel-map liquids without promoter and credits exact1014EU");
    }
    static void idle() throws Exception {
        CriticalMachineState s = state(CriticalMachineKind.BATTERY); EnergyProbe.hostOwned(s);
        s.energyRuntimeData().setInteger("efficiency", 200);
        SparseWorld w = (SparseWorld) s.worldObj;
        for (int i = 1; i <= 399; i++) { w.setWorldTime(i); CriticalEnergyRuntime.idleTick(s); }
        check(s.energyRuntimeData().getInteger("efficiency") == 200, "399 loaded idle ticks do not decay efficiency");
        CriticalEnergyRuntime.idleTick(s);
        check(s.energyRuntimeData().getInteger("idleTicks") == 399, "same world tick is idempotent");
        CriticalMachineState r = restore(s); EnergyProbe.hostOwned(r); SparseWorld rw = (SparseWorld) r.worldObj;
        rw.setWorldTime(399); CriticalEnergyRuntime.idleTick(r);
        check(r.energyRuntimeData().getInteger("efficiency") == 200,
            "reload at same tick does not repeat idle accounting");
        rw.setWorldTime(1000000); CriticalEnergyRuntime.idleTick(r);
        check(r.energyRuntimeData().getInteger("efficiency") == 190
            && r.energyRuntimeData().getInteger("idleTicks") == 0,
            "unloaded time gap adds only one callback,400th loaded idle tick decays exactly10");
    }
    static void fusion() throws Exception {
        Fluid a = EnergyProbe.fluid("release46.fusion.a"), b = EnergyProbe.fluid("release46.fusion.b");
        Fluid product = EnergyProbe.fluid("release46.fusion.product");
        GTRecipe recipe = new GTRecipe(false, null, null, null, null, null, null, null,
            new FluidStack[]{new FluidStack(a, 1), new FluidStack(b, 1)},
            new FluidStack[]{new FluidStack(product, 3)}, 9999, 128, Integer.MAX_VALUE);
        check(RecipeMaps.fusionRecipes.addRecipe(recipe, false, false, false) != null,
            "seed native fusion recipe with maximal startup cost and non400t duration");
        CriticalMachineState s = state(CriticalMachineKind.SUN); MTECriticalMachine h = host(s);
        add(h, "mInputHatches", EnergyProbe.input(new FluidStack(a, 9000)));
        add(h, "mInputHatches", EnergyProbe.input(new FluidStack(b, 9000)));
        MTEHatchOutput out = EnergyProbe.output(); add(h, "mOutputHatches", out);
        CriticalMachineState empty = state(CriticalMachineKind.SUN);
        check(CriticalGTRecipes.matchFusion(empty, host(empty)) == null,
            "SUN insufficient native inputs yields no match");
        out.mFluid = new FluidStack(EnergyProbe.fluid("release46.blocked"), 1);
        check(CriticalGTRecipes.matchFusion(s, h) == null
            && h.criticalPhysicalFluidsSnapshot()[0].amount == 9000,
            "SUN incompatible output capacity rejects native batch without consuming input");
        out.mFluid = null;
        RecipeMatch m = CriticalGTRecipes.matchFusion(s, h);
        check(m != null && m.durationTicks == 400 && m.euPerTick == 128L * 8196
            && m.fluidInputs[0].amount == 8196 && m.fluidOutputs[0].amount == 3 * 8196,
            "SUN native ParallelHelper enforces8196,400ticks,raw EUt xparallel,native fluid output");
        check(h.criticalRecipePower() == 0 && h.criticalPhysicalFluidsSnapshot()[0].amount == 9000,
            "SUN matching ignores startup/tier power gate and never predebits live fluid snapshot");
        set(h.getBaseMetaTileEntity(), "mStoredEnergy", 1000000000L);
        for (int i = 0; i < 400; i++) {
            set(h, "energySpentThisTick", 0L); ((Map<?, ?>) get(h, "hatchEnergySpent")).clear(); call(s, "runBatch");
        }
        check(out.mFluid != null && out.mFluid.getFluid() == product && out.mFluid.amount == 24588,
            "SUN runBatch delivers actual native output hatch after400ticks");
        check(h.getBaseMetaTileEntity().getStoredEU() == 1000000000L - 128L * 8196 * 400,
            "SUN actual controller energy debit equals original EUt x8196 x400 with no discount");
        check(s.getBatchId().isEmpty() && h.criticalPhysicalFluidsSnapshot()[0].amount == 804,
            "SUN batch pays8196 input exactly once and clears paid journal");
    }
    public static void main(String[] args) throws Exception {
        bootstrap(); EnergyProbe.fluids();
        GlobalEnergyWorldSavedData.INSTANCE = new GlobalEnergyWorldSavedData();
        SpaceProjectManager.checkOrCreateTeam(EnergyProbe.OWNER);
        SpaceProjectManager.checkOrCreateTeam(EnergyProbe.LEADER);
        SpaceProjectManager.putInTeam(EnergyProbe.OWNER, EnergyProbe.LEADER);
        math(); maps(); turbine(); chemical(); idle(); fusion();
        JsonObject report = new JsonObject(); report.addProperty("status", "PASS"); report.addProperty("checks", checks);
        report.addProperty("GT", source(GTRecipe.class)); report.addProperty("product", source(CriticalEnergyRuntime.class));
        report.addProperty("boundary", "Real GT205 recipe maps/ParallelHelper/hatches/wireless; seeded dedicated recipes and sparse world; no complete modpack registration, Forge server, client, NEI UI or TPS test");
        Files.write(Paths.get(args[0]), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes("UTF-8"));
    }
}
