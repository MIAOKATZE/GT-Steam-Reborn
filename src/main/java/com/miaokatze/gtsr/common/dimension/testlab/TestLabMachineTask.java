package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.machine.MTECrustSteamBorer;
import com.miaokatze.gtsr.common.machine.MTESpacetimeCalibration;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** One independent minimum operation per physical controller and construction tier. */
final class TestLabMachineTask implements TestLabTask {

    private final MetaTileEntityID machineId;
    private final MetaTileEntityID targetId;
    private final int variant;
    private final List<IGregTechTileEntity> ports = new ArrayList<>();
    private final List<PortRequest> requests = new ArrayList<>();
    private final List<FluidStack> expectedFluids = new ArrayList<>();
    private final List<ItemStack> expectedItems = new ArrayList<>();
    private IGregTechTileEntity controller;
    private IGregTechTileEntity focusedPort;
    private TestLabUnderground deposit;
    private long initialDeposit;
    private MTEMultiBlockBase multi;
    private TestLabStructure structure;
    private GTRecipe recipe;
    private int phase, portCursor, startedProcessing, outputLocks;
    private boolean formed, itemConsumed, preheated, energyPrimed, energyConsumed;
    private boolean redstoneSawInitialLow, redstoneSawHigh, redstoneSawFinalLow;
    private int lastRedstoneSignal;
    private long inputItemCount;
    private String setup = "", expected = "", lastState = "";
    private String diagnosedProblem = "";

    TestLabMachineTask(MetaTileEntityID id, int variant) {
        this(id, id, variant);
    }

    private TestLabMachineTask(MetaTileEntityID targetId, MetaTileEntityID hostId, int variant) {
        this.targetId = targetId;
        this.machineId = hostId;
        this.variant = variant;
    }

    static TestLabTask hatchTask(MetaTileEntityID id) {
        switch (id) {
            case STEAM_INPUT_HATCH_GENERIC:
            case PRESSURE_STEAM_HATCH:
            case STEAM_COOLING_HATCH:
            case PRESSURE_STEAM_COOLING_HATCH:
                return new TestLabMachineTask(id, MetaTileEntityID.AIR_COMPRESSOR, 1);
            case STEAM_OUTPUT_HATCH_GENERIC:
            case PRESSURE_STEAM_OUTPUT_HATCH:
                return new TestLabMachineTask(id, MetaTileEntityID.GEAR_STEAM_COMPRESSOR, 1);
            case STEAM_OUTPUT_HATCH:
                return new TestLabMachineTask(id, MetaTileEntityID.LARGE_SOLAR_OVERPRESSURE_ARRAY, 1);
            case MEGA_AIR_INPUT_HATCH:
                return new TestLabMachineTask(id, MetaTileEntityID.ATMOSPHERIC_CENTRIFUGE, 1);
            case DISTILLED_WATER_HATCH:
                return new TestLabMachineTask(id, MetaTileEntityID.LARGE_GEOTHERMAL_STEAM_BOILER, 1);
            case OVERPRESSURE_TURBINE_INPUT_HATCH:
                return new TestLabMachineTask(id, MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY, 1);
            case REDSTONE_HATCH:
                return new TestLabMachineTask(id, MetaTileEntityID.LARGE_COKE_OVEN, 1);
            default:
                return null;
        }
    }

    @Override
    public String id() {
        return targetId.name()
            .toLowerCase(Locale.ROOT) + (variant == 1 ? "" : "_tier" + variant);
    }

    @Override
    public String displayName() {
        return targetId.name() + " [host=" + machineId + " construction=" + variant + "]";
    }

    @Override
    public int timeoutTicks() {
        return machineId == MetaTileEntityID.SIEMENS_MARTIN_FURNACE ? 20000
            : machineId == MetaTileEntityID.VEIN_STEAM_PYROLYZER ? 12000 : 8000;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        TestLabFixtures.attach(world);
        phase = 0;
        controller = TestLabFixtures.machine(world, x + 80, y + 12, z + 48, machineId.ID);
        if (controller.getMetaTileEntity() instanceof MTEMultiBlockBase) {
            multi = (MTEMultiBlockBase) controller.getMetaTileEntity();
            ItemStack trigger = new ItemStack(net.minecraft.init.Items.stick, variant);
            structure = new TestLabStructure(world, multi, trigger);
        }
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        if (multi == null) return singleBlock(world, elapsedTicks);
        if (phase == 0) {
            if (!structure.buildTick()) return null;
            prepare(world);
            focusHatchRequests();
            phase = 1;
            return null;
        }
        if (phase == 1) {
            if (portCursor < requests.size()) {
                PortRequest request = requests.get(portCursor++);
                IGregTechTileEntity port = structure.install(request.meta);
                if (port == null) {
                    if (request.required) return result(
                        TestLabReport.Status.BLOCKED,
                        "structure=BLOCKED compatible hatch unavailable id=" + request.meta + " role=" + request.role);
                } else {
                    ports.add(port);
                    if (targetId != machineId && port.getMetaTileID() == targetId.ID) focusedPort = port;
                    if (port.getMetaTileEntity() instanceof MTEHatchOutput && outputLocks < expectedFluids.size()) {
                        ((MTEHatchOutput) port.getMetaTileEntity()).setFilter(expectedFluids.get(outputLocks++));
                    }
                    if (request.fluid != null) TestLabSupply.add(world, port, request.fluid);
                }
                return null;
            }
            formed = multi.checkStructure(true, controller);
            if (!formed) return result(
                TestLabReport.Status.FAIL,
                "structure=FAIL canonical checkStructure rejected; " + structure.describe()
                    + " "
                    + structure.diagnoseFailure());
            seedInputs();
            preheatFixture();
            if (targetId == MetaTileEntityID.REDSTONE_HATCH) {
                com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch hatch = (com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch) focusedPort
                    .getMetaTileEntity();
                hatch.setEntryKey("working");
                hatch.setThreshold(0.5);
                hatch.setInverted(false);
                hatch.setUpdateInterval(1);
                focusedPort.enableWorking();
                setup += " redstone=real_working_entry_threshold_0.5_six_strong_sides;";
            }
            inputItemCount = countInputItems();
            startedProcessing = elapsedTicks;
            controller.enableWorking();
            phase = 2;
            return null;
        }
        if (elapsedTicks - startedProcessing < 5) return null;
        sampleRedstone();
        itemConsumed |= countInputItems() < inputItemCount;
        boolean consumed = itemConsumed;
        for (IGregTechTileEntity port : ports) {
            consumed |= TestLabSupply.replenished(port) > 0;
            if (port.getMetaTileEntity() instanceof MTEHatchEnergy) {
                if (energyPrimed && port.getStoredEU() < port.getEUCapacity()) energyConsumed = true;
                ((BaseMetaTileEntity) port).setStoredEU(port.getEUCapacity());
                energyPrimed = true;
            }
        }
        consumed |= energyConsumed;
        String output = observedOutput();
        if (!output.isEmpty() && consumed && focusedPortUsed()) return result(
            diagnosedProblem.isEmpty() ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL,
            "structure=PASS production=PASS real_input_consumption=true diagnosedProblem=" + diagnosedProblem
                + " observed="
                + output
                + " expected="
                + expected
                + " redstoneSignalCycle="
                + (targetId == MetaTileEntityID.REDSTONE_HATCH ? "0_15_0" : "not_applicable")
                + " processingTicks="
                + (elapsedTicks - startedProcessing));
        if (!controller.isAllowedToWork() && elapsedTicks - startedProcessing > 120) return result(
            TestLabReport.Status.FAIL,
            "structure=" + (multi.mMachine ? "PASS" : "FAIL")
                + " production=FAIL machine stopped naturally; "
                + debug(world));
        lastState = "observed=" + output + " consumed=" + consumed + " focusedPortUsed=" + focusedPortUsed();
        return null;
    }

    private void prepare(WorldServer world) throws Exception {
        switch (machineId) {
            case LARGE_COKE_OVEN:
            case REINFORCED_BRICK_BLAST_FURNACE:
            case LARGE_STEAM_FURNACE:
            case ATMOSPHERIC_CENTRIFUGE:
            case SIEMENS_MARTIN_FURNACE:
            case AMMONIA_PLANT:
                selectRecipe(multi.getRecipeMap());
                break;
            case KINETIC_PROCESSING_ARRAY:
                selectRecipe(RecipeMaps.furnaceRecipes);
                ((IInventory) controller).setInventorySlotContents(1, basicFurnace());
                break;
            default:
                break;
        }
        boolean primitive = machineId == MetaTileEntityID.LARGE_COKE_OVEN
            || machineId == MetaTileEntityID.REINFORCED_BRICK_BLAST_FURNACE;
        boolean steam = !primitive && machineId != MetaTileEntityID.LARGE_GEOTHERMAL_STEAM_BOILER
            && machineId != MetaTileEntityID.LARGE_SOLAR_OVERPRESSURE_ARRAY
            && machineId != MetaTileEntityID.TCDS_CONTROLLER
            && machineId != MetaTileEntityID.SPACETIME_CALIBRATION;
        if (steam) {
            FluidStack fuel = Materials.Steam.getGas(1);
            if (machineId == MetaTileEntityID.SIEMENS_MARTIN_FURNACE
                || machineId == MetaTileEntityID.KINETIC_PROCESSING_ARRAY
                || machineId == MetaTileEntityID.SINGULARITY_DRILLING_HUB) fuel = fluid("ic2superheatedsteam");
            if (machineId == MetaTileEntityID.VEIN_STEAM_PYROLYZER || targetId == MetaTileEntityID.PRESSURE_STEAM_HATCH
                || targetId == MetaTileEntityID.PRESSURE_STEAM_COOLING_HATCH) fuel = fluid("ic2superheatedsteam");
            if (machineId == MetaTileEntityID.CRITICAL_SINGULARITY_COMPRESSOR) fuel = fluid("densesupercriticalsteam");
            if (machineId == MetaTileEntityID.STEAM_SINGULARITY_ENTANGLER) fuel = fluid("supercriticalsteam");
            if (machineId == MetaTileEntityID.KINETIC_PROCESSING_ARRAY) {
                add(MetaTileEntityID.PRESSURE_STEAM_HATCH.ID, true, "required pressure steam input", fuel);
                add(nativeId(ItemList.Hatch_Energy_LV.get(1)), true, "LV energy within tier1 casing limit", null);
            } else addInput(fuel);
        }
        if (recipe != null) {
            if (recipe.mInputs.length > 0) busInput(true);
            if (recipe.mOutputs.length > 0) busOutput(true);
            for (FluidStack fluid : recipe.mFluidInputs) if (fluid != null) addInput(fluid);
            for (FluidStack output : recipe.mFluidOutputs) if (output != null) {
                output(true);
                expectedFluids.add(output.copy());
            }
            for (ItemStack output : recipe.mOutputs)
                if (output != null && output.stackSize > 0) expectedItems.add(output.copy());
            expected = "recipe:" + recipe.mDuration
                + "t/"
                + recipe.mEUt
                + "EU inputs="
                + java.util.Arrays.toString(recipe.mInputs);
        }
        switch (machineId) {
            case AIR_COMPRESSOR:
                output(true);
                expect(Materials.Air.getGas(1));
                break;
            case STEAM_FLUID_DRILL:
                output(true);
                expect(Materials.Water.getFluid(1));
                break;
            case CRUST_STEAM_BORER:
            case SINGULARITY_CRUST_STEAM_BORER:
                busOutput(true);
                expected = "ore item from real Overworld drop pool";
                break;
            case VEIN_STEAM_PYROLYZER:
                expected = "underground reservoir growth";
                break;
            case STEAM_SINGULARITY_ENTANGLER:
                busOutput(true);
                expectedItems.add(GTSRItemList.SteamEntangledSingularity.get(1));
                expected = "SteamEntangledSingularity";
                break;
            case CRITICAL_SINGULARITY_COMPRESSOR:
                busOutput(true);
                expectedItems.add(GTSRItemList.CriticalSteamEntangledSingularity.get(1));
                expected = "CriticalSteamEntangledSingularity";
                break;
            case DENSE_STATE_MANIPULATOR:
                busInput(true);
                busOutput(false);
                output(true);
                expect(fluid("densesteam"));
                break;
            case GEAR_STEAM_COMPRESSOR:
                output(true);
                expect(fluid("ic2superheatedsteam"));
                break;
            case LARGE_GEOTHERMAL_STEAM_BOILER:
                addInput(fluid("lava"));
                addInput(Materials.Water.getFluid(1));
                busOutput(false);
                output(true);
                expect(Materials.Steam.getGas(1));
                break;
            case LARGE_SOLAR_OVERPRESSURE_ARRAY:
                addInput(Materials.Water.getFluid(1));
                add(MetaTileEntityID.STEAM_OUTPUT_HATCH.ID, true, "steam output", null);
                expect(Materials.Steam.getGas(1));
                break;
            case TCDS_CONTROLLER:
                addInput(Materials.Methane.getGas(1));
                addInput(Materials.Air.getGas(1));
                addInput(fluid("ic2distilledwater"));
                add(nativeId(ItemList.Hatch_AutoMaintenance.get(1)), true, "automatic maintenance", null);
                add(nativeId(ItemList.Hatch_Muffler_HV.get(1)), true, "real muffler", null);
                output(true);
                expect(Materials.Steam.getGas(1));
                break;
            case MEGA_STEAM_TURBINE_ARRAY:
                add(nativeId(ItemList.Hatch_Dynamo_IV.get(1)), true, "real dynamo", null);
                output(false);
                expected = "EU in real dynamo";
                break;
            case AMMONIA_PLANT:
                addInput(Materials.Gas.getGas(1));
                busOutput(false);
                // The ammonia plant also outputs preheat steam, which must never count as ammonia production.
                output(false);
                break;
            case SIEMENS_MARTIN_FURNACE:
                addInput(Materials.Air.getGas(1));
                break;
            case SPACETIME_CALIBRATION:
                add(nativeId(ItemList.Hatch_Energy_LuV.get(1)), true, "LuV energy", null);
                add(nativeId(ItemList.Hatch_AutoMaintenance.get(1)), true, "automatic maintenance", null);
                expected = "portal activation";
                break;
            default:
                break;
        }
    }

    private void focusHatchRequests() {
        if (targetId == machineId) return;
        if (targetId == MetaTileEntityID.STEAM_COOLING_HATCH
            || targetId == MetaTileEntityID.PRESSURE_STEAM_COOLING_HATCH
            || targetId == MetaTileEntityID.REDSTONE_HATCH) {
            add(targetId.ID, true, "focused attached hatch", null);
            return;
        }
        for (int i = 0; i < requests.size(); i++) {
            PortRequest request = requests.get(i);
            boolean replace;
            switch (targetId) {
                case DISTILLED_WATER_HATCH:
                    replace = request.fluid != null && request.fluid.isFluidEqual(Materials.Water.getFluid(1));
                    break;
                case MEGA_AIR_INPUT_HATCH:
                    replace = request.fluid != null && request.fluid.isFluidEqual(Materials.Air.getGas(1));
                    break;
                case STEAM_INPUT_HATCH_GENERIC:
                case PRESSURE_STEAM_HATCH:
                case OVERPRESSURE_TURBINE_INPUT_HATCH:
                    replace = request.fluid != null && (request.fluid.isFluidEqual(Materials.Steam.getGas(1))
                        || request.fluid.isFluidEqual(fluid("ic2superheatedsteam")));
                    break;
                default:
                    replace = request.fluid == null
                        && GregTechAPI.METATILEENTITIES[request.meta] instanceof MTEHatchOutput;
                    break;
            }
            if (!replace) continue;
            // The distilled-water hatch supplies itself through its own natural first-tick generation.
            requests.set(
                i,
                new PortRequest(
                    targetId.ID,
                    true,
                    "focused " + targetId,
                    targetId == MetaTileEntityID.DISTILLED_WATER_HATCH ? null : request.fluid));
            return;
        }
        throw new IllegalStateException("No matching host port request for " + targetId);
    }

    private boolean focusedPortUsed() {
        if (targetId == machineId) return true;
        if (focusedPort == null) return false;
        if (targetId == MetaTileEntityID.REDSTONE_HATCH)
            return ((com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch) focusedPort.getMetaTileEntity())
                .getController() == multi && redstoneSawInitialLow && redstoneSawHigh && redstoneSawFinalLow;
        if (targetId == MetaTileEntityID.DISTILLED_WATER_HATCH) {
            MTEBasicTank hatch = (MTEBasicTank) focusedPort.getMetaTileEntity();
            FluidStack stack = hatch.getFluid();
            return stack != null && stack.amount > 0 && stack.amount < hatch.getCapacity();
        }
        if (focusedPort.getMetaTileEntity() instanceof MTEHatchOutput) {
            FluidStack stack = ((MTEHatchOutput) focusedPort.getMetaTileEntity()).getFluid();
            return stack != null && stack.amount > 0;
        }
        return TestLabSupply.replenished(focusedPort) > 0;
    }

    private void sampleRedstone() {
        if (targetId != MetaTileEntityID.REDSTONE_HATCH || focusedPort == null) return;
        com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch hatch = (com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch) focusedPort
            .getMetaTileEntity();
        if (hatch.getController() != multi) return;
        boolean allLow = true, allHigh = true;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            int actual = focusedPort.getStrongOutputRedstoneSignal(side);
            lastRedstoneSignal = actual;
            allLow &= actual == 0;
            allHigh &= actual == 15;
        }
        double working = hatch.readValue("working");
        if (working == 0 && allLow) {
            if (redstoneSawHigh) redstoneSawFinalLow = true;
            else redstoneSawInitialLow = true;
        }
        if (working == 1 && allHigh && redstoneSawInitialLow) redstoneSawHigh = true;
    }

    private void selectRecipe(RecipeMap<?> map) {
        if (map == null) return;
        if (map == RecipeMaps.furnaceRecipes) {
            // FurnaceBackend is a NonGTBackend: its recipes are generated from the live smelting registry
            // during lookup, so getAllRecipes() cannot enumerate the normal furnace workload.
            for (net.minecraft.block.Block block : new net.minecraft.block.Block[] {
                net.minecraft.init.Blocks.cobblestone, net.minecraft.init.Blocks.sand,
                net.minecraft.init.Blocks.iron_ore }) {
                GTRecipe found = map.findRecipeQuery()
                    .items(new ItemStack(block))
                    .voltage(128)
                    .find();
                if (found != null) {
                    recipe = found;
                    setup += " recipe_source=real_dynamic_FurnaceBackend_lookup;";
                    return;
                }
            }
            throw new IllegalStateException(
                "Real furnace backend returned no recipe for lab's ordinary smelting inputs");
        }
        List<GTRecipe> candidates = new ArrayList<>();
        for (GTRecipe candidate : map.getAllRecipes()) {
            if (candidate.mDuration <= 0 || candidate.mDuration > 100000
                || candidate.mEUt > 128
                || candidate.mSpecialItems != null) continue;
            if (machineId == MetaTileEntityID.ATMOSPHERIC_CENTRIFUGE && (candidate.mFluidInputs.length != 1
                || !candidate.mFluidInputs[0].isFluidEqual(Materials.Air.getGas(1))
                || candidate.mDuration > 6000)) continue;
            boolean deterministic = candidate.mFluidOutputs.length > 0;
            for (int i = 0; i < candidate.mOutputs.length; i++)
                if (candidate.mOutputs[i] != null && candidate.getOutputChance(i) == 10000) deterministic = true;
            if (deterministic) candidates.add(candidate);
        }
        candidates.sort(
            Comparator.comparingLong(
                (GTRecipe r) -> (long) r.mEUt * 100000 + r.mDuration * 10L + r.mInputs.length + r.mFluidInputs.length)
                .thenComparing(r -> java.util.Arrays.toString(r.mInputs) + java.util.Arrays.toString(r.mFluidInputs)));
        if (!candidates.isEmpty()) recipe = candidates.get(0);
    }

    private void seedInputs() throws Exception {
        for (IGregTechTileEntity port : ports) {
            if (!(port.getMetaTileEntity() instanceof MTEHatchMaintenance)
                || !((MTEHatchMaintenance) port.getMetaTileEntity()).mAuto) continue;
            ItemStack[] costs = MTEHatchMaintenance.getAutoMaintenanceInputs();
            IInventory inventory = (IInventory) port;
            if (costs.length > inventory.getSizeInventory())
                throw new IllegalStateException("Auto-maintenance supplies exceed actual hatch inventory");
            for (int slot = 0; slot < costs.length; slot++)
                if (costs[slot] != null) inventory.setInventorySlotContents(slot, costs[slot].copy());
            setup += " maintenance=real_autorepair_consumables;";
        }
        if (recipe != null) {
            IGregTechTileEntity bus = firstPort(MTEHatchInputBus.class);
            if (recipe.mInputs.length > 0 && bus == null)
                throw new IllegalStateException("No real input bus for selected recipe");
            if (bus != null) {
                IInventory inventory = (IInventory) bus;
                int slot = 0;
                for (ItemStack input : recipe.mInputs) if (input != null) {
                    if (slot >= inventory.getSizeInventory())
                        throw new IllegalStateException("Recipe inputs exceed bus capacity");
                    ItemStack copy = input.copy();
                    copy.stackSize = Math.max(1, copy.stackSize);
                    inventory.setInventorySlotContents(slot++, copy);
                }
            }
        }
        if (machineId == MetaTileEntityID.DENSE_STATE_MANIPULATOR) {
            IGregTechTileEntity bus = firstPort(MTEHatchInputBus.class);
            if (bus != null)
                ((IInventory) bus).setInventorySlotContents(0, GTSRItemList.CriticalSteamEntangledSingularity.get(1));
        }
        if (machineId == MetaTileEntityID.CRUST_STEAM_BORER) {
            // This machine only accepts dimensions 0/-1. Preserve that observation and isolate the pool fixture to this
            // instance.
            setup += " lab_dimension_gate=REJECTED; environment=instance_Overworld_pool_fixture;";
            setField(multi, "mCurrentDimId", 0);
            setField(multi, "canMineInCurrentDim", true);
            java.lang.reflect.Method method = MTECrustSteamBorer.class.getDeclaredMethod("calculateDropMap");
            method.setAccessible(true);
            method.invoke(multi);
        }
        if (machineId == MetaTileEntityID.VEIN_STEAM_PYROLYZER) {
            deposit = new TestLabUnderground(
                (WorldServer) controller.getWorld(),
                controller.getXCoord() >> 4,
                controller.getZCoord() >> 4);
            initialDeposit = deposit.amount();
            setup += " resource=real_GT_underground_deposit_in_lab_chunk;";
        }
        if (machineId == MetaTileEntityID.SINGULARITY_CRUST_STEAM_BORER) {
            ItemStack dimension = dimensionItem();
            MTECrustMatterAggregator aggregator = (MTECrustMatterAggregator) multi;
            if (dimension != null && MTECrustMatterAggregator.isDimensionDisplayItem(dimension)) {
                aggregator.getPluginSlotInventory()
                    .setInventorySlotContents(0, dimension);
                setup += " resource=real_Overworld_dimension_display_item;";
            } else {
                if (dimension != null)
                    diagnosedProblem = "native_dimension_display_gate_rejected_registered_item class="
                        + dimension.getItem()
                            .getClass()
                            .getName();
                aggregator.mCurrentDimId = 0;
                setup += " lab_native_dimension_gate=REJECTED; environment=instance_Overworld_pool_fixture; dimension_item="
                    + (dimension == null ? "optional_plugin_unavailable" : "native_gate_rejected")
                    + ";";
            }
            aggregator.forceRefreshPool();
        }
    }

    private void preheatFixture() {
        String key = null;
        double value = 1;
        switch (machineId) {
            case SIEMENS_MARTIN_FURNACE:
                key = "mFurnaceTemperature";
                value = 1.5;
                break;
            case LARGE_GEOTHERMAL_STEAM_BOILER:
                key = "mHeat";
                break;
            case STEAM_SINGULARITY_ENTANGLER:
            case CRITICAL_SINGULARITY_COMPRESSOR:
                key = "mHeat";
                value = 0.99;
                break;
            case AMMONIA_PLANT:
                key = "mHeatLevel";
                value = 10000;
                break;
            default:
                break;
        }
        if (key == null) return;
        NBTTagCompound saved = new NBTTagCompound();
        multi.saveNBTData(saved);
        if (machineId == MetaTileEntityID.AMMONIA_PLANT) saved.setInteger(key, (int) value);
        else saved.setDouble(key, value);
        multi.loadNBTData(saved);
        preheated = true;
    }

    private String observedOutput() throws Exception {
        if (deposit != null && deposit.amount() > initialDeposit)
            return "underground_GT_internal_growth=" + (deposit.amount() - initialDeposit);
        if (multi instanceof MTESpacetimeCalibration && ((MTESpacetimeCalibration) multi).isPortalActive())
            return "portalActive=true after real 600 tick preheat";
        for (IGregTechTileEntity port : ports) {
            IMetaTileEntity mte = port.getMetaTileEntity();
            if (mte instanceof MTEHatchDynamo && port.getStoredEU() > 0) return "dynamoEU=" + port.getStoredEU();
            if (mte instanceof MTEHatchOutput) {
                FluidStack found = ((MTEHatchOutput) mte).getFluid();
                if (found != null && found.amount > 0)
                    for (FluidStack wanted : expectedFluids) if (found.isFluidEqual(wanted)) return found.getFluid()
                        .getName() + "="
                        + found.amount;
            }
            if (mte instanceof MTEHatchOutputBus) {
                IInventory inventory = (IInventory) port;
                for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                    ItemStack found = inventory.getStackInSlot(slot);
                    if (found == null || found.stackSize < 1) continue;
                    if (expectedItems.isEmpty() && expected.startsWith("ore item")) return found.toString();
                    for (ItemStack wanted : expectedItems) if (found.isItemEqual(wanted)) return found.toString();
                }
            }
        }
        return "";
    }

    private TestLabReport.Result singleBlock(WorldServer world, int elapsedTicks) {
        if (elapsedTicks < 5) return null;
        IMetaTileEntity mte = controller.getMetaTileEntity();
        if (mte instanceof MTEBasicTank) {
            MTEBasicTank tank = (MTEBasicTank) mte;
            FluidStack[] samples = { Materials.Steam.getGas(1000), fluidOrNull("ic2superheatedsteam", 1000),
                Materials.Water.getFluid(1000), Materials.Air.getGas(1000) };
            for (FluidStack sample : samples) {
                if (sample == null) continue;
                int filled = tank.fill(ForgeDirection.UNKNOWN, sample, true);
                if (filled <= 0) continue;
                FluidStack drained = tank.drain(ForgeDirection.UNKNOWN, new FluidStack(sample, filled), true);
                if (drained != null && drained.isFluidEqual(sample) && drained.amount == filled) return result(
                    TestLabReport.Status.PASS,
                    "structure=NOT_APPLICABLE function=PASS real_fill_drain_roundtrip=" + filled
                        + " "
                        + sample.getFluid()
                            .getName()
                        + "; production=NOT_APPLICABLE");
                return result(
                    TestLabReport.Status.FAIL,
                    "structure=NOT_APPLICABLE function=FAIL fill=" + filled + " drain=" + drained);
            }
        }
        return result(
            TestLabReport.Status.BLOCKED,
            "structure=NOT_APPLICABLE production=NOT_TESTED dependent module requires its dedicated host scenario; controller placed only");
    }

    @Override
    public void stop(WorldServer world) {
        for (IGregTechTileEntity port : ports) {
            TestLabSupply.remove(port);
            port.disableWorking();
        }
        if (controller != null) {
            controller.disableWorking();
            if (multi != null) multi.stopMachine(ShutDownReasonRegistry.NONE);
        }
        if (deposit != null) deposit.restore();
    }

    @Override
    public String debug(WorldServer world) {
        StringBuilder debug = new StringBuilder("diagnosedProblem=").append(diagnosedProblem)
            .append(" redstoneCycle=")
            .append(redstoneSawInitialLow)
            .append('/')
            .append(redstoneSawHigh)
            .append('/')
            .append(redstoneSawFinalLow)
            .append(" lastStrongRedstone=")
            .append(lastRedstoneSignal)
            .append(" phase=")
            .append(phase)
            .append(" structure=")
            .append(formed)
            .append(" setup=")
            .append(setup)
            .append(" preheated=")
            .append(preheated)
            .append(" expected=")
            .append(expected)
            .append(" last=")
            .append(lastState)
            .append(" supply=")
            .append(TestLabSupply.describe(world))
            .append(' ')
            .append(TestLabFixtures.snapshot(controller));
        for (IGregTechTileEntity port : ports) debug.append(" port{")
            .append(TestLabFixtures.snapshot(port))
            .append('}');
        return debug.toString();
    }

    private TestLabReport.Result result(TestLabReport.Status status, String detail) {
        return new TestLabReport.Result(
            status,
            detail + " setup="
                + setup
                + " preheated="
                + preheated
                + " target="
                + targetId
                + " focusedPort="
                + (focusedPort == null ? "none" : focusedPort.getMetaTileID())
                + " "
                + (controller == null ? ""
                    : "controller=" + controller
                        .getXCoord() + "," + controller.getYCoord() + "," + controller.getZCoord()));
    }

    private void addInput(FluidStack fluid) {
        add(
            nativeId(ItemList.Hatch_Input_HV.get(1)),
            true,
            "input " + fluid.getFluid()
                .getName(),
            fluid);
    }

    private void output(boolean required) {
        add(nativeId(ItemList.Hatch_Output_HV.get(1)), required, "fluid output", null);
    }

    private void busInput(boolean required) {
        add(nativeId(ItemList.Hatch_Input_Bus_HV.get(1)), required, "item input", null);
    }

    private void busOutput(boolean required) {
        add(nativeId(ItemList.Hatch_Output_Bus_HV.get(1)), required, "item output", null);
    }

    private void add(int meta, boolean required, String role, FluidStack fluid) {
        requests.add(new PortRequest(meta, required, role, fluid));
    }

    private void expect(FluidStack output) {
        expectedFluids.add(output);
        expected = output.getFluid()
            .getName();
    }

    private static int nativeId(ItemStack stack) {
        return stack.getItemDamage();
    }

    private IGregTechTileEntity firstPort(Class<?> type) {
        for (IGregTechTileEntity port : ports) if (type.isInstance(port.getMetaTileEntity())) return port;
        return null;
    }

    private long countInputItems() {
        long count = 0;
        for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchInputBus) {
            IInventory inventory = (IInventory) port;
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack != null) count += stack.stackSize;
            }
        }
        return count;
    }

    private static FluidStack fluid(String name) {
        FluidStack stack = fluidOrNull(name, 1);
        if (stack == null) throw new IllegalStateException("Missing registered fluid " + name);
        return stack;
    }

    private static FluidStack fluidOrNull(String name, int amount) {
        return FluidRegistry.getFluidStack(name, amount);
    }

    private static ItemStack basicFurnace() throws Exception {
        for (IMetaTileEntity mte : GregTechAPI.METATILEENTITIES) if (mte != null && mte.getClass()
            .getSimpleName()
            .contains("BasicMachine")) try {
                Object map = mte.getClass()
                    .getMethod("getRecipeMap")
                    .invoke(mte);
                if (map == RecipeMaps.furnaceRecipes && TestLabStructure.readInt(mte, "mTier") == 1)
                    return mte.getStackForm(1);
            } catch (NoSuchMethodException ignored) {}
        throw new IllegalStateException("No registered LV furnace for kinetic array");
    }

    private static ItemStack dimensionItem() throws Exception {
        for (String name : new String[] { "gtneioreplugin.plugin.item.ItemDimensionDisplay",
            "pers.gwyog.gtneioreplugin.plugin.item.ItemDimensionDisplay" }) try {
                Class<?> display = Class.forName(name);
                for (String method : new String[] { "getItem", "getDimensionStack" }) try {
                    return (ItemStack) display.getMethod(method, String.class)
                        .invoke(null, "Ow");
                } catch (NoSuchMethodException ignored) {}
            } catch (ClassNotFoundException ignored) {}
        return null;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
            return;
        } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }

    private static final class PortRequest {

        final int meta;
        final boolean required;
        final String role;
        final FluidStack fluid;

        PortRequest(int meta, boolean required, String role, FluidStack fluid) {
            this.meta = meta;
            this.required = required;
            this.role = role;
            this.fluid = fluid;
        }
    }
}
