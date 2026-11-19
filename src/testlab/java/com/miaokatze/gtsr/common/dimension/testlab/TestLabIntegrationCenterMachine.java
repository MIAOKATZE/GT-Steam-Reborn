// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.SteamGrade;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraphPreview;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.machine.orecenter.CenterPresets;
import com.miaokatze.gtsr.common.machine.orecenter.CenterProcess;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;
import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** Natural world ticks with owned structure, assembled packs and ordinary GT hatches. */
final class TestLabIntegrationCenterMachine implements TestLabTask {

    static final String ID = "ore_integration_center.machine";
    private static final String PREFIX = "/assets/gtsr/orecenter/";
    private static final int ORE_COUNT = 8, STEAM_FILL = 4096;

    private final List<int[]> positions = new ArrayList<>();
    private final Map<Integer, Integer> hatchTexturesByTier = new LinkedHashMap<>();
    private List<IGregTechTileEntity> hatches;
    private int expectedCasingTier, checkHatchesAt;
    private int x, y, z, cursor, phase, pauseAt, steamAtPause, expected, outputCount;
    private long buildNanos, initialSequence;
    private IGregTechTileEntity controller, input, output;
    private MTEMineralIntegrationCenter center;
    private MTEHatchInput steam, ordinaryInput;
    private MTESteamCoolingHatch cooling;
    private long expectedWater;
    private ItemStack ore, product;
    private FluidStack withheld, coolingAtCheckpoint;
    private NBTTagCompound checkpoint;
    private ChunkCoordIntPair controllerChunk;
    private ForgeChunkManager.Ticket controllerTicket;
    private boolean reloadedController;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Ore center assembled one-sector batch, native buses and saved progress";
    }

    @Override
    public int timeoutTicks() {
        return 3600;
    }

    @Override
    public void start(WorldServer world, int originX, int originY, int originZ) throws IOException {
        require(world.provider instanceof TestLabWorldProvider, "Expected the owned Lab dimension");
        x = originX + 120;
        y = originY;
        z = originZ + 36;
        cursor = phase = 0;
        pauseAt = -1;
        controllerChunk = new ChunkCoordIntPair(x >> 4, z >> 4);
        controllerTicket = null;
        reloadedController = false;
        positions.clear();
        hatchTexturesByTier.clear();
        addOwnership("tower-service-0", 0, 0, 0, 0, false);
        addOwnership("outer-0", 0, 0, 0, 0, false);
        addOwnership("module-1-crusher-1", -46, 9, 49, 0, true);
        addOwnership("module-3-logistics-1", -76, 1, 66, 3, true);

        ore = GTOreDictUnificator.get(OrePrefixes.rawOre, Materials.Copper, ORE_COUNT);
        require(ore != null, "Missing GT copper");
        GTRecipe recipe = RecipeMaps.maceratorRecipes.findRecipeQuery()
            .items(ore.copy())
            .voltage(32L)
            .find();
        require(recipe != null && recipe.getOutputChance(0) == 10000, "Missing deterministic GT copper product");
        product = recipe.mOutputs[0].copy();
        expected = ORE_COUNT * product.stackSize;
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
        if (controller != null && elapsedTicks % 200 == 0) {
            GTSteamReborn.LOG.info(
                "Ore center machine phase={}, formed={}, shutdown={}, enabled={}, GT startup={}",
                phase,
                center.mMachine,
                controller.getLastShutDownReason()
                    .getKey(),
                controller.isAllowedToWork(),
                center.getmStartUpCheck());
        }
        if (phase == 0) {
            int end = Math.min(positions.size(), cursor + 1024);
            while (cursor < end) {
                int[] pos = positions.get(cursor++);
                TestLabFixtures.track(world, pos[0], pos[1], pos[2]);
            }
            if (cursor == positions.size()) {
                controller = TestLabFixtures.machine(
                    world,
                    x,
                    y,
                    z,
                    GTSRItemList.OreIntegrationCenter.get(1)
                        .getItemDamage());
                center = (MTEMineralIntegrationCenter) controller.getMetaTileEntity();
                controller.disableWorking();
                phase = 7;
            }
        } else if (phase == 7 && center.getmStartUpCheck() < 0) {
            require(
                !center.mMachine && (center.getErrorDisplayID() & 64) != 0,
                "An unbuilt controller did not report missing structure");
            center.stopMachine(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
            require(failedStructure(), "GT did not record the structure stop reason");
            long begin = System.nanoTime();
            center.construct(GTSRItemList.OreIntegrationCenter.get(1), false);
            buildNanos = System.nanoTime() - begin;
            input = place(world, 5, ItemList.Hatch_Input_Bus_LV);
            output = place(world, -5, ItemList.Hatch_Output_Bus_LV);
            ordinaryInput = (MTEHatchInput) place(world, 3, ItemList.Hatch_Input_IV).getMetaTileEntity();
            place(world, -3, ItemList.Hatch_Output_LV);
            phase = 8;
        } else if (phase == 8 && center.operationStatus()
            .getInteger("Sectors") != 0) {
                assertFormedWithFourPorts();
                require(
                    world.setBlock(x - 3, y, z, GregTechAPI.sBlockCasings3, 10, 3),
                    "Failed to replace the output hatch with its original casing");
                phase = 9;
            } else if (phase == 9 && !center.mMachine) {
                require(
                    !center.operationStatus()
                        .getBoolean("Formed") && (center.getErrorDisplayID() & 64) != 0,
                    "Removing a required fluid output did not update both GUIs");
                place(world, -3, ItemList.Hatch_Output_LV);
                phase = 10;
            } else if (phase == 10 && center.mMachine) {
                assertFormedWithFourPorts();
                steam = (MTEHatchInput) TestLabFixtures
                    .machine(
                        world,
                        x + 7,
                        y,
                        z,
                        GTSRItemList.SteamInputHatchGeneric.get(1)
                            .getItemDamage())
                    .getMetaTileEntity();
                cooling = (MTESteamCoolingHatch) TestLabFixtures
                    .machine(
                        world,
                        x - 7,
                        y,
                        z,
                        GTSRItemList.SteamCoolingHatch.get(1)
                            .getItemDamage())
                    .getMetaTileEntity();
                IGregTechTileEntity energy = place(world, -6, ItemList.Hatch_Energy_LV);
                hatches = Arrays.asList(
                    input,
                    output,
                    ordinaryInput.getBaseMetaTileEntity(),
                    (IGregTechTileEntity) world.getTileEntity(x - 3, y, z),
                    steam.getBaseMetaTileEntity(),
                    cooling.getBaseMetaTileEntity(),
                    energy);
                expectedCasingTier = 0;
                checkHatchesAt = elapsedTicks + 25;
                phase = 11;
            } else if (phase == 11 && elapsedTicks >= checkHatchesAt && formedAtTier(expectedCasingTier)) {
                checkHatchTextures(world, expectedCasingTier);
                ItemStack tower = GTSRItemList.OreIntegrationCenter
                    .get(expectedCasingTier == 3 ? 1 : expectedCasingTier + 2);
                center.construct(tower, false);
                if (expectedCasingTier == 3) phase = 12;
                else expectedCasingTier++;
            } else if (phase == 12 && formedAtTier(0)) {
                checkHatchTextures(world, 0);
                input.setInventorySlotContents(0, CenterModuleItems.stack(CenterModule.CRUSHER, 1));
                phase = 1;
            } else if (phase == 1 && center.installModuleFromBus(1)) {
                require(input.getStackInSlot(0) == null, "Crusher pack was not charged through real input bus");
                input.setInventorySlotContents(0, CenterModuleItems.stack(CenterModule.LOGISTICS, 1));
                phase = 2;
            } else if (phase == 2 && center.installModuleFromBus(3)) {
                require(input.getStackInSlot(0) == null, "Logistics pack was not charged through real input bus");
                CenterGraph graph = new CenterGraph(
                    7,
                    true,
                    Arrays.asList("rawOreCopper"),
                    Arrays.asList(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { -1, -1, -1 })))
                        .withBusPositions(new CenterGraph.BusPosition(-30, 64), new CenterGraph.BusPosition(390, 86));
                require(center.setGraphs(Arrays.asList(graph)), "Controller rejected valid copper graph");
                supply();
                var preview = new CenterGraphPreview(graph, ore, center.terminalState());
                preview.advance();
                require(
                    preview.products(0)
                        .stream()
                        .anyMatch(p -> p.port == 0 && p.canEnter(CenterProcess.WASHER)),
                    "The live washer map did not accept the crusher's principal copper product");
                verifyPresetTrace();
                NBTTagCompound legacy = new NBTTagCompound();
                center.saveNBTData(legacy);
                legacy.setBoolean("CenterDebugItems", true);
                center.loadNBTData(legacy);
                for (int i = 0; i < output.getSizeInventory(); i++)
                    output.setInventorySlotContents(i, new ItemStack(Blocks.cobblestone, 64));
                input.setInventorySlotContents(0, ore.copy());
                initialSequence = center.batchSequence();
                controller.enableWorking();
                phase = 3;
            } else if (phase == 3 && center.batchSequence() > initialSequence && center.batchProgress() >= 8) {
                require(input.getStackInSlot(0) == null, "Paid copper remains in real input bus");
                require(quantity(output, product) == 0, "Outputs appeared before recipe completion");
                checkpoint = new NBTTagCompound();
                center.saveNBTData(checkpoint);
                require(
                    center.getProgresstime() == center.batchProgress()
                        && center.maxProgresstime() == checkpoint.getInteger("CenterDuration")
                        && center.mOutputItems != null
                        && Arrays.stream(center.mOutputItems)
                            .filter(product::isItemEqual)
                            .mapToInt(s -> s.stackSize)
                            .sum() == expected,
                    "GT's recipe panel did not receive the paid batch progress and rolled item quantity");
                require(
                    !checkpoint.hasKey("mOutputItemsLength") && !checkpoint.hasKey("mMaxProgresstime"),
                    "GT UI mirrors became a second saved batch");
                FluidStack water = cooling.getFluid();
                coolingAtCheckpoint = water == null ? null : water.copy();
                require(
                    checkpoint.getInteger("CenterSteamKind") == SteamGrade.DENSE_STEAM.ordinal(),
                    "The ordinary input displaced the dedicated steam hatch");
                require(ordinaryInput.getFluid().amount == STEAM_FILL, "Ordinary input unexpectedly drained");
                steamAtPause = steam.getFillableStack().amount;
                withheld = steam.getFillableStack()
                    .copy();
                steam.setFillableStack(null);
                pauseAt = elapsedTicks;
                phase = 4;
            } else if (phase == 4 && elapsedTicks >= pauseAt + 8) {
                require(
                    center.batchProgress() == checkpoint.getInteger("CenterElapsed"),
                    "Discontinuous steam advanced progress");
                center.loadNBTData(checkpoint);
                CenterGraph restored = center.graphSnapshot()
                    .get(0);
                require(
                    restored.inputPosition != null && restored.inputPosition.x == -30
                        && restored.inputPosition.y == 64
                        && restored.outputPosition != null
                        && restored.outputPosition.x == 390
                        && restored.outputPosition.y == 86,
                    "Controller NBT lost graph bus positions");
                // Restore the paired tank too: cooling can drain its backlog during a stalled batch.
                cooling.setFillableStack(coolingAtCheckpoint);
                require(center.batchSequence() == initialSequence + 1, "NBT restore changed batch identity");
                require(
                    center.batchProgress() == checkpoint.getInteger("CenterElapsed"),
                    "NBT restore lost saved progress");
                steam.setFillableStack(withheld);
                pauseAt = elapsedTicks;
                controllerTicket = TestLabRuntime.INSTANCE.suspendTaskChunk(this, world, controllerChunk);
                world.theChunkProviderServer
                    .unloadChunksIfNotNearSpawn(controllerChunk.chunkXPos, controllerChunk.chunkZPos);
                phase = 15;
            } else if (phase == 15) {
                if (world.theChunkProviderServer.chunkExists(controllerChunk.chunkXPos, controllerChunk.chunkZPos)) {
                    require(elapsedTicks - pauseAt < 20, "Controller chunk never unloaded on the native provider tick");
                    return null;
                }
                require(center.batchProgress() == checkpoint.getInteger("CenterElapsed"),
                    "Controller ticked while its chunk was unloading");
                ForgeChunkManager.forceChunk(controllerTicket, controllerChunk);
                controllerTicket = null;
                TestLabFixtures.requireChunk(world, controllerChunk.chunkXPos, controllerChunk.chunkZPos);
                IGregTechTileEntity restoredController = tile(world, 0);
                require(restoredController != controller, "Chunk reload reused the original live controller");
                controller = restoredController;
                center = (MTEMineralIntegrationCenter) controller.getMetaTileEntity();
                input = tile(world, 5);
                output = tile(world, -5);
                steam = (MTEHatchInput) tile(world, 7).getMetaTileEntity();
                ordinaryInput = (MTEHatchInput) tile(world, 3).getMetaTileEntity();
                cooling = (MTESteamCoolingHatch) tile(world, -7).getMetaTileEntity();
                require(center.batchSequence() == initialSequence + 1
                    && center.batchProgress() == checkpoint.getInteger("CenterElapsed"),
                    "Native chunk read did not restore the paid batch");
                require(input.getStackInSlot(0) == null && quantity(output, product) == 0,
                    "Native chunk reload duplicated input or output");
                require(controller.isAllowedToWork(), "Reload changed the controller's work switch");
                reloadedController = true;
                pauseAt = elapsedTicks;
                phase = 5;
            } else if (phase == 5) {
                if (elapsedTicks >= pauseAt + 5) require(
                    center.operationStatus()
                        .getInteger("Sectors") == 1,
                    "Saved formation did not resume promptly with its footprint loaded");
                if (center.batchProgress() < checkpoint.getInteger("CenterDuration")) return null;
                require(quantity(output, product) == 0, "Full output bus accepted more products");
                NBTTagCompound after = new NBTTagCompound();
                center.saveNBTData(after);
                require(
                    after.getTagList("CenterItemOutputs", 10)
                        .tagCount() > 0,
                    "Full bus lost pending outputs");
                long paid = STEAM_FILL - steam.getFillableStack().amount;
                int density = SteamGrade.DENSE_STEAM.getDivisor();
                require(
                    paid == (checkpoint.getLong("CenterSteam") + density - 1) / density,
                    "Steam paid is not the frozen batch total");
                expectedWater = paid * density / 160;
                ItemStack nearlyFull = product.copy();
                nearlyFull.stackSize = nearlyFull.getMaxStackSize() - expected + 1;
                output.setInventorySlotContents(0, nearlyFull);
                pauseAt = elapsedTicks;
                phase = 14;
            } else if (phase == 14 && elapsedTicks >= pauseAt + 12) {
                require(
                    quantity(output, product) == product.getMaxStackSize() - expected + 1,
                    "Output transaction ejected a partial batch into the available space");
                require(center.batchSequence() == initialSequence + 1, "An unpaid or undelivered batch started again");
                for (int i = 0; i < output.getSizeInventory(); i++) output.setInventorySlotContents(i, null);
                phase = 6;
            } else if (phase == 6) {
                outputCount = quantity(output, product);
                require(outputCount == 0 || outputCount == expected, "The output bus received part of a batch");
                FluidStack water = cooling.getFluid();
                int waterAmount = water == null ? 0 : water.amount;
                require(
                    waterAmount <= expectedWater,
                    "Condensate was duplicated: " + waterAmount + "/" + expectedWater);
                if (outputCount == expected && waterAmount == expectedWater) {
                    require(
                        water == null || water.getFluid() == FluidRegistry.getFluid("ic2distilledwater"),
                        "Invalid condensate");
                    Map<String, Object> report = new LinkedHashMap<>();
                    report.put("hatchCasingTexturesByTowerTier", hatchTexturesByTier);
                    report.put("hatchesKeptDuringTowerRebuild", hatches.size());
                    report.put("restoredGraphBusPositions", true);
                    report.put("copperCrusherProductAcceptedByNativeWasher", true);
                    report.put("presetRawSamplesFollowFourProcessStagesWithoutStock", true);
                    report.put("fourRequiredPortsFormed", true);
                    report.put("nativeFormationAndTerminalAgreeAfterRemovalAndRestore", true);
                    report.put("clearedStaleStructureShutdownWithPowerSwitchOff", true);
                    report.put("ore", ORE_COUNT);
                    report.put("nativeOutput", expected);
                    report.put("actualOutput", outputCount);
                    report.put("legacyItemWaiverIgnored", true);
                    report.put("atomicOutputAfterPartialSpace", true);
                    report.put("savedFormationResumed", true);
                    report.put("nativeControllerChunkUnloadedAndReloaded", reloadedController);
                    report.put("nativeGtBatchDisplay", true);
                    report.put("steamAtCheckpoint", steamAtPause);
                    report.put("condensedDistilledWater", waterAmount);
                    report.put("footprintSamples", positions.size());
                    report.put("constructNanos", buildNanos);
                    report.put("restoreScope", "in-place checkpoint and native chunk reload into a new controller");
                    return new TestLabReport.Result(
                        TestLabReport.Status.PASS,
                        "Live assembly, real controller chunk reload and atomic output",
                        report);
                }
                require(outputCount <= expected, "Output was duplicated");
            }
        return null;
    }

    private boolean formedAtTier(int tier) {
        return center.mMachine && center.operationStatus()
            .getInteger("Tier") == tier;
    }

    private static void verifyPresetTrace() {
        var search = new CenterGraph.PreviewInputs("^crushedPurified(?:Copper|Lead)$");
        List<ItemStack> samples = new ArrayList<>();
        while (!search.advance(samples)) {
            // Advance the actual OreDictionary search under its normal per-call budget.
        }
        require(samples.size() == 2, "Purified copper and lead rule did not resolve two raw minerals");
        CenterGraph preset = CenterPresets.get("regular-MPMC")
            .graph("Preview fixture");
        for (ItemStack raw : samples) {
            require(
                Arrays.stream(CenterGraph.tags(raw))
                    .anyMatch(tag -> tag.equals("rawOreCopper") || tag.equals("rawOreLead")),
                "Preset mineral picker did not return a coarse ore");
            CenterGraphPreview trace = new CenterGraphPreview(preset, raw, new NBTTagCompound());
            for (int step = 0; step < 32; step++) trace.advance();
            require(
                trace.entryNode() == 0 && trace.products(0)
                    .stream()
                    .anyMatch(product -> product.port == 3),
                "Preset coarse ore did not reach the wash branch of the first crusher");
            require(
                trace.conditionalRecipe(1) && !trace.products(1)
                    .isEmpty()
                    && !trace.products(2)
                        .isEmpty()
                    && !trace.products(3)
                        .isEmpty(),
                "Preset trace stopped before washing, second grinding, and centrifuging");
        }
    }

    private void checkHatchTextures(WorldServer world, int tier) {
        int casing = switch (tier) {
            case 0 -> GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);
            case 1 -> GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
            case 2 -> GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 2);
            case 3 -> GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
            default -> throw new IllegalArgumentException("Unsupported fixture tier " + tier);
        };
        for (IGregTechTileEntity hatch : hatches) {
            require(
                world.getTileEntity(hatch.getXCoord(), hatch.getYCoord(), hatch.getZCoord()) == hatch,
                "Tower rebuild replaced the existing hatch at X offset " + (hatch.getXCoord() - x));
            NBTTagCompound texture = new NBTTagCompound();
            ((MTEHatch) hatch.getMetaTileEntity()).saveNBTData(texture);
            int actual = texture.getInteger("texturePage") << 7 | texture.getInteger("textureIndex");
            require(
                actual == casing,
                "Hatch at X offset " + (hatch.getXCoord() - x) + " had casing " + actual + ", expected " + casing);
        }
        hatchTexturesByTier.put(tier, casing);
    }

    private boolean failedStructure() {
        return ShutDownReasonRegistry.STRUCTURE_INCOMPLETE.getKey()
            .equals(
                controller.getLastShutDownReason()
                    .getKey());
    }

    private void assertFormedWithFourPorts() {
        NBTTagCompound state = center.operationStatus();
        require(
            center.mMachine && state.getBoolean("Formed") && (center.getErrorDisplayID() & 64) == 0,
            "GT and center did not agree that the four required ports are enough");
        require(state.getLong("Power") == 0, "Fixture must have no energy hatch at formation");
        require(
            !failedStructure() && !controller.isAllowedToWork(),
            "Recovery should clear the stale structure reason and preserve the off switch");
    }

    private IGregTechTileEntity place(WorldServer world, int offset, ItemList item) {
        return TestLabFixtures.machine(
            world,
            x + offset,
            y,
            z,
            item.get(1)
                .getItemDamage());
    }

    private IGregTechTileEntity tile(WorldServer world, int offset) {
        TileEntity restored = world.getTileEntity(x + offset, y, z);
        require(restored instanceof IGregTechTileEntity, "GT tile not restored at offset " + offset);
        return (IGregTechTileEntity) restored;
    }

    private void supply() {
        FluidStack dense = FluidRegistry.getFluidStack(SteamGrade.DENSE_STEAM.getFluidName(), STEAM_FILL);
        FluidStack higher = FluidRegistry.getFluidStack(SteamGrade.DENSE_SUPERHEATED_STEAM.getFluidName(), STEAM_FILL);
        require(
            dense != null && steam.fill(ForgeDirection.UNKNOWN, dense, true) == STEAM_FILL,
            "Dedicated hatch rejected dense steam");
        require(
            higher != null && ordinaryInput.fill(ForgeDirection.UNKNOWN, higher, true) == STEAM_FILL,
            "Ordinary input rejected dense superheated steam");
    }

    private void addOwnership(String key, int ox, int oy, int oz, int rotation, boolean module) throws IOException {
        InputStream asset = TestLabIntegrationCenterMachine.class.getResourceAsStream(PREFIX + key + ".gz");
        if (asset == null) throw new IOException("No packaged fixture geometry " + key);
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(asset))) {
            int palette = in.readUnsignedShort(), count = in.readInt();
            for (int i = 0; i < palette; i++) {
                byte[] entry = new byte[in.readUnsignedByte() + 1];
                in.readFully(entry);
            }
            for (int i = 0; i < count; i++) {
                int px = in.readShort(), py = in.readUnsignedByte(), pz = in.readShort();
                in.readUnsignedByte();
                int rx = rotation == 3 ? pz : px;
                int rz = rotation == 3 ? -px : pz;
                positions.add(new int[] { x + ox + rx, y + oy + py - (module ? 66 : 2), z + oz + rz + 19 });
            }
        }
    }

    private static int quantity(IGregTechTileEntity bus, ItemStack stack) {
        int sum = 0;
        for (int i = 0; i < bus.getSizeInventory(); i++) {
            ItemStack entry = bus.getStackInSlot(i);
            if (GTUtility.areStacksEqual(entry, stack, false)) sum += entry.stackSize;
        }
        return sum;
    }

    @Override
    public void stop(WorldServer world) {
        if (controller != null) controller.disableWorking();
        if (steam != null) steam.setFillableStack(null);
    }

    @Override
    public String debug(WorldServer world) {
        return "ore center machine phase=" + phase
            + " journal="
            + cursor
            + " progress="
            + (center == null ? 0 : center.batchProgress())
            + " output="
            + outputCount
            + " condensate="
            + (cooling == null ? 0 : cooling.getFluidAmount())
            + "/"
            + expectedWater;
    }

    private static void require(boolean condition, String text) {
        if (!condition) throw new IllegalStateException(text);
    }
}
