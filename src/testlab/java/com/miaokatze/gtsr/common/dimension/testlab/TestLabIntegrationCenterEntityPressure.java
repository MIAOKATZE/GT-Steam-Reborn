// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.SteamGrade;
import com.miaokatze.gtsr.common.machine.orecenter.CenterFixtureGeometry;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.machine.orecenter.CenterProcess;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Places three controllers in the same forced chunk window and observes production through real buses. */
final class TestLabIntegrationCenterEntityPressure implements TestLabTask {

    static final String ID = "perf.ore_integration_center.entities";
    private static final int BASELINE_TICKS = 180, MEASURE_TICKS = 600;
    private static final int ORE_COUNT = 8, STEAM_TARGET = 4096;
    private static final int HEIGHT_STEP = 64, FIXTURE_CHUNK_SPAN = 208;
    private final Machine[] machines = new Machine[3];
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final IGregTechTileEntity[] observed = new IGregTechTileEntity[machines.length];
    private List<int[]> footprint;
    private TestLabCenterTickSamples samples;
    private int originX, originZ, activeCount, cursor;
    private ItemStack ore, product;
    private int expected;
    private boolean fixturesStopped, restoring;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Ore integration center simultaneous physical throughput and server tick pressure";
    }

    @Override
    public int timeoutTicks() {
        return 5500;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) {
        require(world.provider instanceof TestLabWorldProvider, "Expected the owned Lab dimension");
        footprint = CenterFixtureGeometry.oneSector();
        for (int[] offset : footprint) {
            require(120 + offset[0] >= 0 && 120 + offset[0] < FIXTURE_CHUNK_SPAN
                && 36 + offset[2] >= 0 && 36 + offset[2] < FIXTURE_CHUNK_SPAN,
                "Ore center footprint crosses its forced chunk window");
            require(offset[1] >= -2 && offset[1] < HEIGHT_STEP - 2, "Vertical ore center footprints overlap");
        }
        require(y + HEIGHT_STEP * machines.length - 3 < world.getHeight(), "Ore center exceeds dimension height");
        originX = x;
        originZ = z;
        Arrays.fill(observed, null);
        for (int i = 0; i < machines.length; i++) machines[i] = new Machine(x + 120, y + i * HEIGHT_STEP, z + 36);
        results.clear();
        activeCount = cursor = 0;
        fixturesStopped = restoring = false;
        ore = GTOreDictUnificator.get(OrePrefixes.rawOre, Materials.Copper, ORE_COUNT);
        require(ore != null, "Missing GT copper");
        GTRecipe recipe = RecipeMaps.maceratorRecipes.findRecipeQuery().items(ore.copy()).voltage(32L).find();
        require(recipe != null && recipe.getOutputChance(0) == 10000, "Missing deterministic GT copper product");
        product = recipe.mOutputs[0].copy();
        expected = ORE_COUNT * product.stackSize;
        samples = new TestLabCenterTickSamples(world, observed);
        samples.start();
        samples.beginWindow(0, BASELINE_TICKS);
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
        if (restoring) return TestLabFixtures.tickReset(world) ? restored(world) : null;
        if (samples.isRecording()) {
            serviceActiveMachines();
            if (samples.isComplete()) {
                List<Map<String, Object>> production = new ArrayList<>();
                for (int i = 0; i < activeCount; i++) production.add(machines[i].measured());
                results.add(samples.finishWindow(production));
                if (activeCount == machines.length) {
                    stopFixtures();
                    restoring = true;
                    TestLabFixtures.reset(world, originX, originZ);
                }
            }
            return null;
        }

        serviceActiveMachines();
        Machine pending = machines[activeCount];
        if (pending.controller == null) {
            prepareNext(world, pending);
        } else if (!pending.crusher && pending.center.installModuleFromBus(1)) {
            require(pending.input.getStackInSlot(0) == null, "Crusher pack was not paid by the native input bus");
            pending.crusher = true;
            pending.input.setInventorySlotContents(0, CenterModuleItems.stack(CenterModule.LOGISTICS, 1));
        } else if (pending.crusher && !pending.logistics && pending.center.installModuleFromBus(3)) {
            require(pending.input.getStackInSlot(0) == null, "Logistics pack was not paid by the native input bus");
            pending.logistics = true;
            require(pending.center.setGraphs(Arrays.asList(new CenterGraph(7, true, Arrays.asList("rawOreCopper"),
                Arrays.asList(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { -1, -1, -1 }))))),
                "Controller rejected valid copper graph");
            observed[activeCount] = pending.controller;
            pending.feed();
            pending.controller.enableWorking();
        } else if (pending.logistics) {
            pending.service();
            if (pending.completed > 0) {
                activeCount++;
                cursor = 0;
                for (int i = 0; i < activeCount; i++) machines[i].beginWindow();
                samples.beginWindow(activeCount, MEASURE_TICKS);
            }
        }
        return null;
    }

    private TestLabReport.Result restored(WorldServer world) {
        restoring = false;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("world", world.provider.dimensionId);
        detail.put("scope", "physical one-sector centers with native GT input, steam and output hatches");
        detail.put("recipeInput", "rawOreCopper");
        detail.put("rawOrePerBatch", ORE_COUNT);
        detail.put("nativePrimaryOutputPerBatch", expected);
        detail.put("footprintPositionsPerCenter", footprint.size());
        detail.put("windows", results);
        detail.put("fixtureRestore", "verified canonical terrain at journaled positions in this case's worksite");
        return new TestLabReport.Result(TestLabReport.Status.PASS,
            "Native copper production confirmed with one, two and three simultaneously scheduled centers", detail);
    }

    private void serviceActiveMachines() {
        for (int i = 0; i < activeCount; i++) machines[i].service();
    }

    private void prepareNext(WorldServer world, Machine machine) {
        int end = Math.min(footprint.size(), cursor + 2048);
        while (cursor < end) {
            int[] offset = footprint.get(cursor++);
            int x = machine.x + offset[0], y = machine.y + offset[1], z = machine.z + offset[2];
            checkTerrain(world, x, y, z);
            TestLabFixtures.track(world, x, y, z);
        }
        if (cursor != footprint.size()) return;
        checkTerrain(world, machine.x, machine.y, machine.z);
        for (int shift : new int[] { -5, -3, -1 }) checkTerrain(world, machine.x + shift, machine.y, machine.z + 1);
        machine.place(world);
    }

    private static void checkTerrain(WorldServer world, int x, int y, int z) {
        require(world.getChunkProvider().chunkExists(x >> 4, z >> 4), "Fixture coordinate has no owned loaded chunk");
        Block expected = y == 63 ? Blocks.stone : y == 64 ? Blocks.quartz_block : Blocks.air;
        if (world.getBlock(x, y, z) != expected || world.getTileEntity(x, y, z) != null)
            throw new IllegalStateException("Fixture footprint already occupied at " + x + "," + y + "," + z);
    }

    @Override
    public void stop(WorldServer world) {
        if (restoring) TestLabFixtures.cancelReset();
        restoring = false;
        stopFixtures();
    }

    private void stopFixtures() {
        if (samples != null) {
            samples.stop();
            samples = null;
        }
        if (fixturesStopped) return;
        for (Machine machine : machines) if (machine != null) machine.stop();
        fixturesStopped = true;
    }

    @Override
    public String performancePhase() {
        return restoring ? "orecenter.entities.restore" : samples != null && samples.isRecording()
            ? "orecenter.entities.measure" : "orecenter.entities.prepare";
    }

    @Override
    public String debug(WorldServer world) {
        List<String> states = new ArrayList<>();
        for (Machine machine : machines) if (machine != null && machine.center != null) {
            states.add("y=" + machine.y + ":sequence=" + machine.center.batchSequence() + ":progress="
                + machine.center.batchProgress() + ":enabled=" + machine.controller.isAllowedToWork()
                + ":completed=" + machine.completed + ":steam=" + (machine.steam == null ? 0 : machine.remainingSteam()));
        }
        return "centers=" + activeCount + " journal=" + cursor + " sampled=" + (samples == null ? 0 : samples.count())
            + " restoring=" + restoring + " machines=" + states;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private final class Machine {

        final int x, y, z;
        boolean crusher, logistics;
        IGregTechTileEntity controller, input, output;
        MTEMineralIntegrationCenter center;
        MTEHatchInput steam;
        int completed, startCompleted;
        long buildNanos, initialSequence, supplied, startConsumed;

        Machine(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void place(WorldServer world) {
            ItemStack centerStack = GTSRItemList.OreIntegrationCenter.get(1);
            controller = TestLabFixtures.machine(world, x, y, z, centerStack.getItemDamage());
            controller.disableWorking();
            center = (MTEMineralIntegrationCenter) controller.getMetaTileEntity();
            long begin = System.nanoTime();
            center.construct(centerStack, false);
            buildNanos = System.nanoTime() - begin;
            input = place(world, -5, ItemList.Hatch_Input_Bus_LV);
            output = place(world, -3, ItemList.Hatch_Output_Bus_LV);
            steam = (MTEHatchInput) place(world, -1, ItemList.Hatch_Input_IV).getMetaTileEntity();
            input.setInventorySlotContents(0, CenterModuleItems.stack(CenterModule.CRUSHER, 1));
        }

        private IGregTechTileEntity place(WorldServer world, int dx, ItemList item) {
            return TestLabFixtures.machine(world, x + dx, y, z + 1, item.get(1).getItemDamage());
        }

        void feed() {
            FluidStack dense = FluidRegistry.getFluidStack(SteamGrade.DENSE_STEAM.getFluidName(),
                STEAM_TARGET - remainingSteam());
            require(dense != null && steam.fill(ForgeDirection.UNKNOWN, dense, true) == dense.amount,
                "The native input hatch rejected dense steam");
            supplied += dense.amount;
            initialSequence = center.batchSequence();
            input.setInventorySlotContents(0, ore.copy());
        }

        void service() {
            require(controller.isAllowedToWork(), "Processing center disabled itself at Y=" + y);
            int actual = 0;
            for (int slot = 0; slot < output.getSizeInventory(); slot++) {
                ItemStack result = output.getStackInSlot(slot);
                if (GTUtility.areStacksEqual(result, product, false)) actual += result.stackSize;
            }
            if (actual == 0) return;
            require(actual <= expected, "The physical output bus contains a duplicated copper product");
            if (actual < expected) return;
            require(center.batchSequence() == initialSequence + 1 && input.getStackInSlot(0) == null,
                "Output appeared without exactly one paid native batch");
            completed++;
            clear(output);
            feed();
        }

        int remainingSteam() {
            FluidStack remaining = steam.getFillableStack();
            return remaining == null ? 0 : remaining.amount;
        }

        void beginWindow() {
            startCompleted = completed;
            startConsumed = supplied - remainingSteam();
        }

        Map<String, Object> measured() {
            int cycles = completed - startCompleted;
            long paid = supplied - remainingSteam() - startConsumed;
            require(cycles > 0 && paid > 0, "Physical center completed no paid batch in its measurement window at Y=" + y);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("controller", Arrays.asList(x, y, z));
            row.put("constructNanos", buildNanos);
            row.put("completedBatches", cycles);
            row.put("verifiedOutput", expected * cycles);
            row.put("denseSteamConsumed", paid);
            return row;
        }

        void stop() {
            if (controller != null) controller.disableWorking();
            if (steam != null) steam.setFillableStack(null);
            clear(input);
            clear(output);
            if (crusher) CenterFixtureGeometry.release(controller, 1);
            if (logistics) CenterFixtureGeometry.release(controller, 3);
        }

        private void clear(IGregTechTileEntity bus) {
            if (bus == null) return;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) bus.setInventorySlotContents(slot, null);
        }
    }
}
