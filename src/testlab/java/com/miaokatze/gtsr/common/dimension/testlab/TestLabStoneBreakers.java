// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.EnumSet;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.loader.recipes.StoneBreakerRule;
import com.miaokatze.gtsr.loader.recipes.StoneSite;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.SingleRecipeCheck;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.common.GTDummyWorld;
import gregtech.common.tileentities.machines.basic.MTERockBreaker;
import gregtech.common.tileentities.machines.multi.MTEIndustrialRockBreaker;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEIndustrialRockBreakerLegacy;

/** Actual transformed machine entry points and registered recipes, with isolated dimension and adjacency fixtures. */
final class TestLabStoneBreakers implements TestLabTask {

    public String id() {
        return "rock_breaker_sites";
    }

    public String displayName() {
        return "Stone maker dimensions, voltage, catalysts and NEI";
    }

    public boolean requiresFixtureChunks() {
        return false;
    }

    public int timeoutTicks() {
        return 200;
    }

    public void start(WorldServer world, int x, int y, int z) {}

    public void stop(WorldServer world) {}

    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        GTDummyWorld fixture = new GTDummyWorld() {

            @Override
            public Block getBlock(int x, int y, int z) {
                if (x == 9) return Blocks.water;
                if (x == 7) return Blocks.lava;
                return Blocks.air;
            }
        };
        EnumSet<StoneSite> sites = EnumSet.noneOf(StoneSite.class);
        MTEIndustrialRockBreaker modern = new MTEIndustrialRockBreaker("stone_test");
        MTEIndustrialRockBreakerLegacy legacy = new MTEIndustrialRockBreakerLegacy("stone_test_legacy");
        ProcessingLogic[] industrial = { logic(modern, fixture), logic(legacy, fixture) };
        int checked = 0;
        for (GTRecipe recipe : RecipeMaps.multiblockRockBreakerRecipes.getAllRecipes()) {
            StoneBreakerRule rule = recipe.getMetadata(StoneBreakerRule.KEY);
            if (rule == null) continue;
            sites.add(rule.site);
            int tier = GTUtility.getTier(recipe.mEUt);
            int dimension = dimension(fixture, rule.site);
            boolean glowstone = recipe.mInputs[1].getItemDamage() == 6;
            boolean paid = rule.site == StoneSite.OUTSIDE_NETHER;
            require(
                recipe.mDuration == 16 && recipe.mEUt == rule.eut && recipe.mInputs[0].stackSize == (paid ? 1 : 0),
                recipe,
                "industrial definition");
            long displays = RecipeMaps.rockBreakerFakeRecipes.getAllRecipes()
                .stream()
                .filter(display -> {
                    StoneBreakerRule shown = display.getMetadata(StoneBreakerRule.KEY);
                    return shown != null && shown.site == rule.site
                        && display.mEUt == rule.eut
                        && display.mDuration == 16
                        && GTUtility.areStacksEqual(display.mOutputs[0], recipe.mOutputs[0])
                        && GTUtility.areStacksEqual(display.mInputs[0], recipe.mInputs[0])
                        && display.mInputs[0].stackSize == recipe.mInputs[0].stackSize
                        && GTUtility.areStacksEqual(display.mInputs[1], recipe.mInputs[1]);
                })
                .count();
            require(displays == 1, recipe, "single-block NEI");
            MTERockBreaker single = single(tier, fixture);
            ItemStack sample = GTUtility.copyAmount(1, recipe.mInputs[0]);
            single.mInventory[single.getInputSlot()] = sample;
            single.mInventory[single.getCircuitSlot()] = recipe.mInputs[1].copy();
            require(
                single.checkRecipe() == 2 && single.mEUt == rule.eut
                    && single.mMaxProgresstime == 16
                    && GTUtility.areStacksEqual(single.mOutputItems[0], recipe.mOutputs[0])
                    && sample.stackSize == (paid ? 0 : 1),
                recipe,
                "single-block start");
            if (tier > 1) {
                MTERockBreaker lower = single(tier - 1, fixture);
                lower.mInventory[lower.getCircuitSlot()] = recipe.mInputs[1].copy();
                lower.mInventory[lower.getInputSlot()] = GTUtility.copyAmount(1, recipe.mInputs[0]);
                require(lower.checkRecipe() != 2, recipe, "single-block voltage");
            }
            single.mInventory[single.getInputSlot()] = null;
            require(single.checkRecipe() != 2, recipe, "missing single-block sample");
            for (ProcessingLogic logic : industrial) {
                sample = GTUtility.copyAmount(1, recipe.mInputs[0]);
                logic.setAvailableVoltage(GTValues.V[tier])
                    .setInputItems(sample, recipe.mInputs[1].copy());
                require(
                    logic.process()
                        .wasSuccessful() && GTUtility.areStacksEqual(logic.getOutputItems()[0], recipe.mOutputs[0])
                        && sample.stackSize == (paid ? 0 : 1),
                    recipe,
                    "industrial start");
                logic.setInputItems(recipe.mInputs[1].copy());
                require(
                    !logic.process()
                        .wasSuccessful(),
                    recipe,
                    "missing industrial sample");
                if (tier > 1) {
                    logic.setAvailableVoltage(GTValues.V[tier - 1])
                        .setInputItems(GTUtility.copyAmount(1, recipe.mInputs[0]), recipe.mInputs[1].copy());
                    require(
                        !logic.process()
                            .wasSuccessful(),
                        recipe,
                        "industrial voltage at four amps");
                }
            }
            if (rule.site == StoneSite.MOON && recipe.mInputs[1].getItemDamage() == 8) {
                verifyLock(modern, industrial[0], fixture, recipe, tier);
                verifyLock(legacy, industrial[1], fixture, recipe, tier);
            }
            if (!glowstone) {
                fixture.provider.dimensionId = dimension == 0 ? -1 : 0;
                single.mInventory[single.getInputSlot()] = GTUtility.copyAmount(1, recipe.mInputs[0]);
                require(
                    single.checkRecipe() != 2 && single.mInventory[single.getInputSlot()].stackSize == 1,
                    recipe,
                    "single wrong world");
                for (ProcessingLogic logic : industrial) {
                    sample = GTUtility.copyAmount(1, recipe.mInputs[0]);
                    logic.setAvailableVoltage(GTValues.V[tier])
                        .setInputItems(sample, recipe.mInputs[1].copy());
                    require(
                        !logic.process()
                            .wasSuccessful() && sample.stackSize == 1,
                        recipe,
                        "industrial wrong world");
                }
            }
            checked++;
        }
        if (checked != 85 || sites.size() != StoneSite.values().length) throw new IllegalStateException(
            "Expected 83 materials + two glowstone routes, got " + checked + " recipes across " + sites);
        verifyGlowstoneReload(fixture);
        return new TestLabReport.Result(
            TestLabReport.Status.PASS,
            checked + " native single-block, modern/legacy industrial and NEI recipe pairs; "
                + sites.size()
                + " dimension rules; modern/legacy saved and legacy NC locks, batch costs and deferred world binding");
    }

    private static void verifyGlowstoneReload(GTDummyWorld fixture) throws Exception {
        GTRecipe free = glowstoneRecipe(StoneSite.NETHER);
        GTRecipe paid = glowstoneRecipe(StoneSite.OUTSIDE_NETHER);
        ItemStack[] unchanged = { GTUtility.copyAmount(1, free.mInputs[0]), GTUtility.getIntegratedCircuit(6) };
        // v2.1.50 used this same recipe shape and zero-cost ledger in every dimension.
        // Metadata is not serialized by SingleRecipeCheck, so this is also its old NC payload.
        NBTTagCompound oldLock = SingleRecipeCheck.builder(RecipeMaps.multiblockRockBreakerRecipes)
            .setBefore(unchanged, new FluidStack[0])
            .setAfter(unchanged, new FluidStack[0])
            .setRecipe(free)
            .build()
            .writeToNBT();
        for (boolean legacy : new boolean[] { false, true }) {
            for (GTRecipe recipe : new GTRecipe[] { free, paid }) {
                fixture.provider.dimensionId = recipe == free ? -1 : 0;
                MTEMultiBlockBase machine = industrial(legacy);
                ProcessingLogic processing = logic(machine, fixture);
                processing.setRecipeLocking(machine, true)
                    .setAvailableVoltage(GTValues.V[1]);
                verifyGlowstoneRun(processing, recipe, 1, "lock before save");
                require(machine.getSingleRecipeCheck() != null, recipe, "native glowstone lock");
                NBTTagCompound saved = machine.getSingleRecipeCheck()
                    .writeToNBT();
                verifyRestoredGlowstone(machine, processing, fixture, recipe, saved, "saved lock");
                verifyRestoredGlowstone(machine, processing, fixture, recipe, oldLock, "v2.1.50 NC lock");
            }
            // During chunk loading, the controller can read NBT before its tile has a world.
            fixture.provider.dimensionId = 0;
            MTEMultiBlockBase deferred = industrial(legacy);
            ProcessingLogic processing = logic(deferred, fixture);
            BaseMetaTileEntity base = (BaseMetaTileEntity) deferred.getBaseMetaTileEntity();
            base.setWorldObj(null);
            deferred.setSingleRecipeCheck(loadLock(deferred, oldLock));
            base.setWorldObj(fixture);
            require(
                deferred.getSingleRecipeCheck() != null && deferred.getSingleRecipeCheck()
                    .getRecipe() == paid,
                paid,
                "deferred world binding");
            processing.setRecipeLocking(deferred, true)
                .setAvailableVoltage(GTValues.V[1]);
            verifyGlowstoneRun(processing, paid, 4, "deferred paid batch");
        }
    }

    private static void verifyRestoredGlowstone(MTEMultiBlockBase machine, ProcessingLogic processing,
        GTDummyWorld fixture, GTRecipe recipe, NBTTagCompound saved, String step) throws Exception {
        int dimension = fixture.provider.dimensionId;
        machine.setSingleRecipeCheck(null);
        fixture.provider.dimensionId = dimension == 0 ? -1 : 0;
        // The shared recipe lookup used by NBT loading has empty fluid inputs. Warm that
        // exact query in the opposite world with the modern controller, which needs no fluids.
        ProcessingLogic peer = logic(new MTEIndustrialRockBreaker("stone_cache_peer"), fixture);
        peer.setInputFluids(new FluidStack[0])
            .setAvailableVoltage(GTValues.V[1]);
        verifyGlowstoneRun(
            peer,
            glowstoneRecipe(dimension == 0 ? StoneSite.NETHER : StoneSite.OUTSIDE_NETHER),
            1,
            step);
        fixture.provider.dimensionId = dimension;
        machine.setSingleRecipeCheck(loadLock(machine, saved));
        require(
            machine.getSingleRecipeCheck() != null && machine.getSingleRecipeCheck()
                .getRecipe() == recipe,
            recipe,
            step + " after opposite-world cache");
        processing.setRecipeLocking(machine, true)
            .setBatchSize(1);
        ItemStack sample = GTUtility.copyAmount(1, recipe.mInputs[0]);
        ItemStack circuit = GTUtility.getIntegratedCircuit(6);
        processing.setInputItems(circuit);
        require(
            !processing.process()
                .wasSuccessful(),
            recipe,
            step + " without sample");
        processing.setInputItems(GTUtility.copyAmount(0, sample), circuit);
        require(
            !processing.process()
                .wasSuccessful(),
            recipe,
            step + " with empty sample");
        processing.setInputItems(sample);
        require(
            !processing.process()
                .wasSuccessful() && sample.stackSize == 1,
            recipe,
            step + " without circuit");
        processing.setInputItems(sample, GTUtility.getIntegratedCircuit(7));
        require(
            !processing.process()
                .wasSuccessful() && sample.stackSize == 1,
            recipe,
            step + " wrong circuit");
        fixture.provider.dimensionId = dimension == 0 ? -1 : 0;
        processing.setInputItems(sample, circuit);
        require(
            !processing.process()
                .wasSuccessful() && sample.stackSize == 1,
            recipe,
            step + " wrong world");
        fixture.provider.dimensionId = dimension;
        verifyGlowstoneRun(processing, recipe, 1, step + " single cost");
        verifyGlowstoneRun(processing, recipe, 4, step + " batch cost");
        machine.setSingleRecipeCheck(null);
        processing.setRecipeLocking(machine, false)
            .setBatchSize(1);
    }

    private static void verifyGlowstoneRun(ProcessingLogic processing, GTRecipe recipe, int batch, String step) {
        ItemStack sample = GTUtility.copyAmount(16, recipe.mInputs[0]);
        ItemStack circuit = GTUtility.copyAmount(1, GTUtility.getIntegratedCircuit(6));
        processing.setBatchSize(batch)
            .setInputItems(sample, circuit);
        require(
            processing.process()
                .wasSuccessful(),
            recipe,
            step + " start");
        ItemStack output = processing.getOutputItems()[0];
        StoneBreakerRule rule = recipe.getMetadata(StoneBreakerRule.KEY);
        boolean paid = rule.site == StoneSite.OUTSIDE_NETHER;
        require(
            GTUtility.areStacksEqual(output, recipe.mOutputs[0]) && output.stackSize == batch
                && processing.getCurrentParallels() == batch
                && sample.stackSize == 16 - (paid ? batch : 0)
                && circuit.stackSize == 1,
            recipe,
            step + " output and dust ledger");
    }

    private static SingleRecipeCheck loadLock(MTEMultiBlockBase machine, NBTTagCompound saved) throws Exception {
        var load = MTEMultiBlockBase.class.getDeclaredMethod("loadSingleRecipeChecker", NBTTagCompound.class);
        load.setAccessible(true);
        return (SingleRecipeCheck) load.invoke(machine, saved.copy());
    }

    private static GTRecipe glowstoneRecipe(StoneSite site) {
        return RecipeMaps.multiblockRockBreakerRecipes.getAllRecipes()
            .stream()
            .filter(recipe -> {
                StoneBreakerRule rule = recipe.getMetadata(StoneBreakerRule.KEY);
                return rule != null && rule.site == site && recipe.mInputs[1].getItemDamage() == 6;
            })
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing glowstone recipe for " + site));
    }

    private static MTEMultiBlockBase industrial(boolean legacy) {
        return legacy ? new MTEIndustrialRockBreakerLegacy("stone_reload_legacy")
            : new MTEIndustrialRockBreaker("stone_reload");
    }

    private static void verifyLock(MTEMultiBlockBase machine, ProcessingLogic logic, GTDummyWorld fixture,
        GTRecipe recipe, int tier) {
        int moon = fixture.provider.dimensionId;
        ItemStack sample = GTUtility.copyAmount(1, recipe.mInputs[0]);
        ItemStack circuit = recipe.mInputs[1].copy();
        logic.setAvailableVoltage(GTValues.V[tier])
            .setRecipeLocking(machine, true)
            .setInputItems(sample, circuit);
        require(
            logic.process()
                .wasSuccessful() && machine.getSingleRecipeCheck() != null,
            recipe,
            "native recipe lock");
        logic.setInputItems(circuit);
        require(
            !logic.process()
                .wasSuccessful(),
            recipe,
            "locked without sample");
        logic.setInputItems(sample, GTUtility.getIntegratedCircuit(9));
        require(
            !logic.process()
                .wasSuccessful(),
            recipe,
            "locked wrong circuit");
        logic.setInputItems(sample, circuit);
        fixture.provider.dimensionId = 0;
        require(
            !logic.process()
                .wasSuccessful(),
            recipe,
            "locked wrong dimension");
        fixture.provider.dimensionId = moon;
        require(
            logic.process()
                .wasSuccessful() && sample.stackSize == 1,
            recipe,
            "locked recovery");
        machine.setSingleRecipeCheck(null);
        logic.setRecipeLocking(machine, false);
    }

    private static void require(boolean condition, GTRecipe recipe, String step) {
        if (!condition) throw new IllegalStateException(
            step + " failed for " + recipe.mOutputs[0] + " at " + recipe.mEUt + " EU/t");
    }

    private static int dimension(GTDummyWorld world, StoneSite site) {
        for (int id : DimensionManager.getStaticDimensionIDs()) {
            world.provider.dimensionId = id;
            if (site.allows(world)) return id;
        }
        throw new IllegalStateException("Missing registered dimension for " + site);
    }

    private static MTERockBreaker single(int tier, GTDummyWorld world) {
        int id = MetaTileEntityIDs.valueOf("ROCK_BREAKER_" + GTValues.VN[tier]).ID;
        BaseMetaTileEntity base = new BaseMetaTileEntity();
        base.setWorldObj(world);
        base.xCoord = 8;
        base.zCoord = 8;
        MTERockBreaker machine = (MTERockBreaker) GregTechAPI.METATILEENTITIES[id].newMetaEntity(base);
        machine.setBaseMetaTileEntity(base);
        return machine;
    }

    private static ProcessingLogic logic(MTEMultiBlockBase machine, GTDummyWorld world) throws Exception {
        BaseMetaTileEntity base = new BaseMetaTileEntity();
        base.setWorldObj(world);
        machine.setBaseMetaTileEntity(base);
        var create = machine.getClass()
            .getDeclaredMethod("createProcessingLogic");
        create.setAccessible(true);
        return ((ProcessingLogic) create.invoke(machine)).setMachine(machine)
            .setRecipeMap(RecipeMaps.multiblockRockBreakerRecipes)
            .setMaxParallelSupplier(() -> 1)
            .setAvailableAmperage(4)
            .setInputFluids(new FluidStack(FluidRegistry.WATER, 1000), new FluidStack(FluidRegistry.LAVA, 1000));
    }
}
