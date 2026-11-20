// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.BoosterType;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.SteamGrade;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;
import com.miaokatze.gtsr.common.machine.orecenter.CenterFixtureGeometry;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.machine.orecenter.CenterPresets;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.GTOreDictUnificator;

/** The shipped first preset runs on a formed six-sector controller with paid native inputs. */
final class TestLabCenterPresetLoad implements TestLabTask {

    static final String ID = "perf.ore_integration_center.preset_load";
    static final CenterModule[] MODULES = { CenterModule.PARALLEL, CenterModule.CRUSHER, CenterModule.WASHER,
        CenterModule.LOGISTICS, null, null, CenterModule.YIELD, CenterModule.CENTRIFUGE, CenterModule.SIFTER, null,
        null, null, CenterModule.SECONDARY, CenterModule.SMELTING, CenterModule.SEPARATOR, null, null, null,
        CenterModule.SPEED, CenterModule.THERMAL_CENTRIFUGE, CenterModule.ELECTROLYZER, null, null, null, null,
        CenterModule.DRYER, null, null, null, null, null, null, null, null, null, null };
    private static final BoosterType[] BOOSTS = { BoosterType.SPEED, BoosterType.PARALLEL, BoosterType.PRIMARY_OUTPUT,
        BoosterType.SECONDARY_OUTPUT };
    private static final int[] CHARGES = { 45, 36, 75, 30 };

    private static final class OreMix {

        static final Materials[] STOCK = { Materials.Copper, Materials.Iron, Materials.Tin, Materials.Lead,
            Materials.Gold, Materials.Nickel };
    }

    private static final int STACK_SIZE = 64;
    private static final int STEAM_STOCK = 2_000_000;
    private static final int[][] SUPPLY_OFFSETS = { { 3, 0, 0 }, { -7, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };

    private final List<Map<String, Object>> results = new ArrayList<>();
    private final Map<String, Long> receipts = new LinkedHashMap<>();
    private final MTEHatchInput[] supplies = new MTEHatchInput[5];
    private final IGregTechTileEntity[] observed = new IGregTechTileEntity[1];
    private List<int[]> footprint;
    private IGregTechTileEntity controller, input, energy;
    private final List<IGregTechTileEntity> outputs = new ArrayList<>();
    private MTEMineralIntegrationCenter center;
    private MTEHatchInput steam;
    private CenterGraph graph;
    private int x, y, z, cursor, module, installedAt, phase, workload, waitSince, submitted;
    private long lastSequence, sampledEU, expectedEU, expectedSteam, initialEU;
    private final long[] reagents = new long[4];
    private final int[] initialTanks = new int[5];
    private final Map<String, Long> beginningInputs = new LinkedHashMap<>();
    private Map<String, Long> lastStock, previousDebits, previousDeliveries, previousBonuses;
    private final Map<String, Long> expectedOutputs = new LinkedHashMap<>();
    private final Map<String, Long> queuedOutput = new LinkedHashMap<>();
    private final List<Map<String, Long>> batchDebits = new ArrayList<>();
    private TestLabCenterTickSamples timings;
    private EntityPlayerMP actor;
    private boolean cameraRaised;
    private Path frames;
    private List<ChunkCoordIntPair> tcpChunks;
    private final List<Ticket> tcpTickets = new ArrayList<>();
    private Ticket controllerTicket, unusedChunkTicket;
    private ChunkCoordIntPair unusedChunk;
    private int reloadStarted, formationRecoveredIn = -1;
    private int profileBatches;

    private boolean profileFrames() {
        return actor != null && Boolean.getBoolean(CenterGraphTcpFixture.PREFIX + "profileFrames");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "First native preset, high parallel mixed and single ore";
    }

    @Override
    public int timeoutTicks() {
        return 7200;
    }

    @Override
    public int fixtureChunkWidth() {
        return 18;
    }

    @Override
    public void start(WorldServer world, int originX, int originY, int originZ) {
        require(world.provider instanceof TestLabWorldProvider, "Expected owned lab dimension");
        x = originX + 144;
        y = originY;
        z = originZ + 128;
        footprint = CenterFixtureGeometry.fullPreset(MODULES);
        unusedChunk = CenterFixtureGeometry.unusedChunkInSectorBounds(footprint, x, z);
        int span = fixtureChunkWidth() * 16;
        for (int[] p : footprint) {
            require(
                p[0] + x >= originX && p[0] + x < originX + span && p[2] + z >= originZ && p[2] + z < originZ + span,
                "Outside owned fixture chunks");
        }
        initialize(world);
    }

    /** The TCP host has already verified the owner marker of its disposable world. */
    void startTcp(WorldServer world, EntityPlayerMP connected, Path evidence) {
        actor = connected;
        frames = evidence;
        x = CenterGraphTcpFixture.X;
        y = CenterGraphTcpFixture.Y;
        z = CenterGraphTcpFixture.Z;
        Set<ChunkCoordIntPair> unique = new LinkedHashSet<>();
        footprint = CenterFixtureGeometry.fullPreset(MODULES);
        for (int[] p : footprint) unique.add(new ChunkCoordIntPair((x + p[0]) >> 4, (z + p[2]) >> 4));
        tcpChunks = new ArrayList<>(unique);
        actor.inventory.setInventorySlotContents(actor.inventory.currentItem, GTSRItemList.HubTerminal.get(1));
        initialize(world);
    }

    private void initialize(WorldServer world) {
        var preset = CenterPresets.all()
            .get(0);
        require(preset.id.equals("regular-MPMC") && preset.nodeCount() == 26, "First preset changed");
        graph = preset.graph(preset.name);
        timings = new TestLabCenterTickSamples(world, observed);
        timings.start();
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int tick) {
        if (actor != null && phase == 3) updateCamera(world);
        if (phase == 0) {
            if (!prepare(world)) return null;
            place(world);
            phase = 1;
        } else if (phase == 1 && center.mMachine
            && center.terminalState()
                .getInteger("Sectors") == 63) {
                    installNext(tick);
                } else
            if (phase == 2 && tick - installedAt >= 80) {
                require(center.setGraphs(Collections.singletonList(graph)), "Preset rejected by live controller");
                if (profileFrames()) {
                    int step = z - 48;
                    world.setBlock(x, y + 86, step, Blocks.stone);
                    actor.playerNetServerHandler.setPlayerLocation(x + .5, y + 87, step + .5, 180, 48);
                    CenterGraphTcpFixture.write(frames, "frame-profile-ready.json", Collections.emptyMap());
                    phase = 6;
                    return null;
                }
                stock(workload, tick);
                phase = 3;
            } else if (phase == 6) {
                updateCamera(world);
                if (Files.isRegularFile(frames.resolve("frame-profile-start.json"))) {
                    stock(0, tick);
                    phase = 7;
                }
            } else if (phase == 7) {
                collectDeliveries();
                boolean finishing = Files.isRegularFile(frames.resolve("frame-profile-finish.json"));
                if (center.batchSequence() != lastSequence) {
                    recordBatch(tick);
                    profileBatches++;
                    if (!finishing) restockProfile();
                }
                if (finishing && profileBatches > 1 && queuedOutput.isEmpty()) {
                    return new TestLabReport.Result(
                        TestLabReport.Status.PASS,
                        "Live work observed on TCP",
                        Collections.<String, Object>singletonMap("completedWorkloads", profileBatches));
                }
            } else if (phase == 3) {
                collectDeliveries();
                if (center.batchSequence() != lastSequence) {
                    recordBatch(tick);
                }
                if (inventory(input).isEmpty() && center.maxProgresstime() == 0) {
                    finishWorkload();
                    if (++workload == 2) {
                        Map<String, Object> detail = new LinkedHashMap<>();
                        detail.put("preset", "regular-MPMC");
                        detail.put("nodes", graph.nodes.size());
                        detail.put("cases", results);
                        if (formationRecoveredIn >= 0) {
                            detail.put("preloadedStructureReloadTicks", formationRecoveredIn);
                            detail.put("unusedChunkInsideSectorBoundsLeftUnloaded", true);
                        }
                        detail.put("ledgerDebits", ledger(0));
                        detail.put("ledgerDelivered", ledger(1));
                        detail.put("ledgerBonuses", ledger(2));
                        return new TestLabReport.Result(
                            TestLabReport.Status.PASS,
                            "Full preset exercised on real hatches",
                            detail);
                    }
                    if (actor == null) {
                        ChunkCoordIntPair chunk = new ChunkCoordIntPair(x >> 4, z >> 4);
                        controllerTicket = TestLabRuntime.INSTANCE.suspendTaskChunk(this, world, chunk);
                        unusedChunkTicket = TestLabRuntime.INSTANCE.suspendTaskChunk(this, world, unusedChunk);
                        world.theChunkProviderServer
                            .unloadChunksIfNotNearSpawn(unusedChunk.chunkXPos, unusedChunk.chunkZPos);
                        world.theChunkProviderServer.unloadChunksIfNotNearSpawn(chunk.chunkXPos, chunk.chunkZPos);
                        reloadStarted = tick;
                        phase = 4;
                        return null;
                    }
                    stock(workload, tick);
                }
                require(
                    tick - waitSince < 1000 || center.batchProgress() > 0,
                    "Accepted stock did not start or settle a batch; remaining=" + inventory(input));
            } else if (phase == 4) {
                if (world.theChunkProviderServer.chunkExists(x >> 4, z >> 4)
                    || world.theChunkProviderServer.chunkExists(unusedChunk.chunkXPos, unusedChunk.chunkZPos)) {
                    require(tick - reloadStarted < 20, "Native controller or empty corner did not unload");
                    return null;
                }
                restoreController(world);
                reloadStarted = tick;
                phase = 5;
            } else if (phase == 5) {
                require(tick - reloadStarted <= 100, "Preloaded six-sector structure failed to recover");
                if (!center.mMachine || center.terminalState()
                    .getInteger("Sectors") != 63) return null;
                require(
                    !world.theChunkProviderServer.chunkExists(unusedChunk.chunkXPos, unusedChunk.chunkZPos),
                    "Structure restoration loaded an unused corner");
                restoreUnusedChunk();
                formationRecoveredIn = tick - reloadStarted;
                require(
                    controller.isAllowedToWork() && center.batchSequence() == lastSequence,
                    "Native reload changed work switch or batch sequence");
                stock(workload, tick);
                phase = 3;
            }
        return null;
    }

    private boolean prepare(WorldServer world) {
        if (tcpChunks != null) {
            int end = Math.min(tcpChunks.size(), cursor + 8);
            while (cursor < end) {
                ChunkCoordIntPair coord = tcpChunks.get(cursor++);
                Ticket ticket = tcpTickets.isEmpty() ? null : tcpTickets.get(tcpTickets.size() - 1);
                if (ticket == null || ticket.getChunkList()
                    .size() >= ticket.getChunkListDepth()) {
                    Object owner = Loader.instance()
                        .getIndexedModList()
                        .get("gtsr")
                        .getMod();
                    ticket = ForgeChunkManager.requestTicket(owner, world, ForgeChunkManager.Type.NORMAL);
                    require(ticket != null && ticket.getChunkListDepth() > 0, "No ticket quota for owned TCP fixture");
                    ticket.getModData()
                        .setString("OwnerType", "TestLabTransient");
                    tcpTickets.add(ticket);
                }
                ForgeChunkManager.forceChunk(ticket, coord);
                world.theChunkProviderServer.loadChunk(coord.chunkXPos, coord.chunkZPos);
            }
            return cursor == tcpChunks.size();
        }
        int end = Math.min(footprint.size(), cursor + 2048);
        while (cursor < end) {
            int[] p = footprint.get(cursor++);
            TestLabFixtures.track(world, x + p[0], y + p[1], z + p[2]);
        }
        return cursor == footprint.size();
    }

    private void restockProfile() {
        for (int slot = 0; slot < 15; slot++) {
            if (input.getStackInSlot(slot) != null) continue;
            Materials mineral = OreMix.STOCK[slot % OreMix.STOCK.length];
            input.setInventorySlotContents(slot, GTOreDictUnificator.get(OrePrefixes.rawOre, mineral, STACK_SIZE));
        }
        lastStock = inventory(input);
        for (int i = 0; i < supplies.length; i++) {
            MTEHatchInput hatch = supplies[i];
            int missing = initialTanks[i] - volume(hatch);
            if (missing <= 0) continue;
            Fluid fluid = i == BOOSTS.length ? FluidRegistry.WATER
                : MTEBasicAmplifierUnit.resolveBoosterFluid(BOOSTS[i]);
            require(
                hatch.fill(ForgeDirection.UNKNOWN, new FluidStack(fluid, missing), true) == missing,
                "Visual fixture reagent refill failed");
        }
    }

    private void updateCamera(WorldServer world) {
        if (Files.isRegularFile(frames.resolve("preset-camera-home.json"))) {
            if (cameraRaised) actor.playerNetServerHandler.setPlayerLocation(x + .5, y, z - 2.5, 0, 0);
            cameraRaised = false;
        } else if (!cameraRaised && Files.isRegularFile(frames.resolve("preset-camera-crown.json"))) {
            // An observation step above the empty northern edge, within the controller's visual range.
            require(
                world.isAirBlock(x, y + 61, z + 6) && world.isAirBlock(x, y + 62, z + 6),
                "The crown observation step overlaps the structure");
            world.setBlock(x, y + 61, z + 6, Blocks.stone);
            actor.playerNetServerHandler.setPlayerLocation(x + .5, y + 62, z + 6.5, 0, 17);
            cameraRaised = true;
        }
    }

    private void place(WorldServer world) {
        if (actor != null) {
            world.setBlock(x, y - 1, z - 3, Blocks.stone);
            actor.playerNetServerHandler.setPlayerLocation(x + .5, y, z - 2.5, 0, 0);
        }
        ItemStack body = GTSRItemList.OreIntegrationCenter.get(4);
        controller = machine(world, x, y, z, body);
        require(controller.getFrontFacing() == ForgeDirection.NORTH, "Fixture controller is not facing north");
        controller.disableWorking();
        center = (MTEMineralIntegrationCenter) controller.getMetaTileEntity();
        observed[0] = controller;
        ChannelDataAccessor.setChannelData(body, "ore_center_sectors", 6);
        if (actor == null) center.construct(body, false);
        else CenterFixtureGeometry.constructTcpPreset(controller);
        input = port(world, 5, 0, 0, ItemList.Hatch_Input_Bus_UV.get(1));
        outputs.add(port(world, -5, 0, 0, ItemList.Hatch_Output_Bus_UV.get(1)));
        outputs.add(port(world, 1, 0, 0, ItemList.Hatch_Output_Bus_UV.get(1)));
        port(world, -3, 0, 0, ItemList.Hatch_Output_UV.get(1));
        energy = port(world, -6, 0, 0, ItemList.Hatch_Energy_UV.get(1));
        steam = (MTEHatchInput) port(world, 7, 0, 0, GTSRItemList.OverpressureTurbineInputHatch.get(1))
            .getMetaTileEntity();
        for (int i = 0; i < SUPPLY_OFFSETS.length; i++) {
            int[] p = SUPPLY_OFFSETS[i];
            supplies[i] = (MTEHatchInput) port(world, p[0], p[1], p[2], ItemList.Hatch_Input_UV.get(1))
                .getMetaTileEntity();
        }
    }

    private void restoreController(WorldServer world) {
        ForgeChunkManager.forceChunk(controllerTicket, new ChunkCoordIntPair(x >> 4, z >> 4));
        controllerTicket = null;
        TestLabFixtures.requireChunk(world, x >> 4, z >> 4);
        IGregTechTileEntity prior = controller;
        controller = at(world, 0, 0, 0);
        require(controller != prior, "Controller chunk reused the original live tile");
        center = (MTEMineralIntegrationCenter) controller.getMetaTileEntity();
        observed[0] = controller;
        input = at(world, 5, 0, 0);
        outputs.set(0, at(world, -5, 0, 0));
        outputs.set(1, at(world, 1, 0, 0));
        energy = at(world, -6, 0, 0);
        steam = (MTEHatchInput) at(world, 7, 0, 0).getMetaTileEntity();
        for (int i = 0; i < SUPPLY_OFFSETS.length; i++) {
            int[] p = SUPPLY_OFFSETS[i];
            supplies[i] = (MTEHatchInput) at(world, p[0], p[1], p[2]).getMetaTileEntity();
        }
    }

    private IGregTechTileEntity at(WorldServer world, int dx, int dy, int dz) {
        return (IGregTechTileEntity) world.getTileEntity(x + dx, y + dy, z + dz);
    }

    private void restoreUnusedChunk() {
        if (unusedChunkTicket == null) return;
        ForgeChunkManager.forceChunk(unusedChunkTicket, unusedChunk);
        unusedChunkTicket = null;
    }

    private IGregTechTileEntity port(WorldServer world, int a, int b, int c, ItemStack stack) {
        require(
            world.getBlock(x + a, y + b, z + c) != Blocks.air,
            "No casing at fixture port " + a + "," + b + "," + c);
        return machine(world, x + a, y + b, z + c, stack);
    }

    private IGregTechTileEntity machine(WorldServer world, int wx, int wy, int wz, ItemStack stack) {
        short id = (short) stack.getItemDamage();
        if (actor == null) return TestLabFixtures.machine(world, wx, wy, wz, id);
        world.setBlock(
            wx,
            wy,
            wz,
            GregTechAPI.sBlockMachines,
            GregTechAPI.METATILEENTITIES[id].getTileEntityBaseType(),
            3);
        var tile = world.getTileEntity(wx, wy, wz);
        require(tile instanceof IGregTechTileEntity, "Native GT tile missing");
        IGregTechTileEntity gt = (IGregTechTileEntity) tile;
        gt.setInitialValuesAsNBT(null, id);
        gt.setFrontFacing(ForgeDirection.NORTH);
        gt.setOwnerName(actor.getCommandSenderName());
        gt.setOwnerUuid(actor.getUniqueID());
        return gt;
    }

    private void installNext(int tick) {
        while (module < MODULES.length && MODULES[module] == null) module++;
        if (module < MODULES.length) {
            input.setInventorySlotContents(0, CenterModuleItems.stack(MODULES[module], 3));
            require(center.installModuleFromBus(module), "Cannot fit native module " + module + " " + MODULES[module]);
            require(input.getStackInSlot(0) == null, "Module package remained in the native bus");
            module++;
        } else {
            installedAt = tick;
            phase = 2;
        }
    }

    private void stock(int run, int tick) {
        center.getBaseMetaTileEntity()
            .disableWorking();
        receipts.clear();
        expectedOutputs.clear();
        queuedOutput.clear();
        batchDebits.clear();
        beginningInputs.clear();
        Arrays.fill(reagents, 0);
        expectedEU = expectedSteam = sampledEU = 0;
        lastSequence = center.batchSequence();
        for (int i = 0; i < supplies.length; i++) {
            MTEHatchInput hatch = supplies[i];
            hatch.setFillableStack(null);
            Fluid fluid = i == BOOSTS.length ? FluidRegistry.WATER
                : MTEBasicAmplifierUnit.resolveBoosterFluid(BOOSTS[i]);
            require(fluid != null, "Missing registered additive");
            int amount = i == BOOSTS.length ? hatch.getCapacity() : 200_000;
            require(
                hatch.fill(ForgeDirection.UNKNOWN, new FluidStack(fluid, amount), true) == amount,
                "Native hatch rejected " + fluid.getName());
            initialTanks[i] = volume(hatch);
        }
        steam.setFillableStack(null);
        Fluid fluid = SteamGrade.DENSE_SUPERCRITICAL_STEAM.resolveFluid();
        require(
            steam.fill(ForgeDirection.UNKNOWN, new FluidStack(fluid, STEAM_STOCK), true) == STEAM_STOCK,
            "Steam hatch rejected dense supercritical steam");
        initialEU = energy.getEUCapacity();
        energy.increaseStoredEnergyUnits(initialEU - energy.getStoredEU(), false);
        require(energy.getStoredEU() == initialEU, "Failed to stock native EU hatch");
        submitted = 0;
        for (int slot = 0; slot < 15; slot++) {
            Materials mineral = run == 0 ? OreMix.STOCK[slot % OreMix.STOCK.length] : Materials.Copper;
            ItemStack ore = GTOreDictUnificator.get(OrePrefixes.rawOre, mineral, STACK_SIZE);
            require(ore != null, "Missing raw ore " + mineral);
            input.setInventorySlotContents(slot, ore);
            beginningInputs.merge(key(ore), (long) ore.stackSize, Long::sum);
            submitted += ore.stackSize;
        }
        lastStock = inventory(input);
        require(lastStock.equals(beginningInputs), "Native input bus did not hold all offered ore");
        previousDebits = ledger(0);
        previousDeliveries = ledger(1);
        previousBonuses = ledger(2);
        timings.beginWindow(1, 400);
        waitSince = tick;
        controller.enableWorking();
    }

    private void recordBatch(int tick) {
        require(center.batchSequence() == lastSequence + 1, "Unexpected batch sequence");
        lastSequence++;
        waitSince = tick;
        NBTTagCompound batch = new NBTTagCompound();
        center.saveNBTData(batch);
        int density = SteamGrade.DENSE_SUPERCRITICAL_STEAM.getDivisor();
        require(
            batch.getInteger("CenterSteamKind") == SteamGrade.DENSE_SUPERCRITICAL_STEAM.ordinal(),
            "Batch selected the wrong steam class");
        expectedSteam += (batch.getLong("CenterSteam") + density - 1) / density;
        expectedEU += batch.getLong("CenterEnergy");
        int[] add = batch.getIntArray("CenterBoostFluids");
        require(add.length == reagents.length, "Missing batch boost costs");
        for (int i = 0; i < add.length; i++) reagents[i] += add[i];
        require(
            center.getProgresstime() == center.batchProgress() && center.maxProgresstime() > 0
                && center.mOutputItems != null
                && center.mOutputItems.length != 0,
            "Native GT progress and recipe outputs did not mirror the batch");
        Map<String, Long> remaining = inventory(input);
        Map<String, Long> debits = delta(lastStock, remaining);
        require(!debits.isEmpty(), "New batch had no physical item debit");
        batchDebits.add(debits);
        lastStock = remaining;
        require(queuedOutput.isEmpty(), "A batch started before the previous output was delivered");
        for (ItemStack item : center.mOutputItems) {
            queuedOutput.merge(key(item), (long) item.stackSize, Long::sum);
            expectedOutputs.merge(key(item), (long) item.stackSize, Long::sum);
        }
        if (frames != null && workload == 0 && batchDebits.size() == 1) {
            Map<String, Object> visible = new LinkedHashMap<>();
            visible.put("products", new LinkedHashMap<>(expectedOutputs));
            visible.put("duration", center.maxProgresstime());
            CenterGraphTcpFixture.write(frames, "preset-active.json", visible);
        }
    }

    private void collectDeliveries() {
        Map<String, Long> delivered = null;
        for (IGregTechTileEntity bus : outputs) for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
            ItemStack item = bus.getStackInSlot(slot);
            if (item == null) continue;
            if (delivered == null) delivered = new LinkedHashMap<>();
            delivered.merge(key(item), (long) item.stackSize, Long::sum);
            receipts.merge(key(item), (long) item.stackSize, Long::sum);
            bus.setInventorySlotContents(slot, null);
        }
        if (delivered != null) {
            require(queuedOutput.equals(delivered), "A physical output bus received only part of a batch");
            queuedOutput.clear();
        }
        long before = energy.getStoredEU();
        sampledEU += initialEU - before;
        if (before < initialEU) energy.increaseStoredEnergyUnits(initialEU - before, false);
    }

    private void finishWorkload() {
        require(!receipts.isEmpty(), "Finished without actual outputs");
        require(
            expectedOutputs.equals(receipts),
            "GT recipe list differs from output buses; expected=" + expectedOutputs + "; delivered=" + receipts);
        require(delta(ledger(0), previousDebits).equals(beginningInputs), "Ledger debit differs from actual input");
        require(delta(ledger(1), previousDeliveries).equals(receipts), "Ledger delivery differs from output buses");
        Map<String, Long> bonuses = delta(ledger(2), previousBonuses);
        require(!bonuses.isEmpty(), "Boosted batch reported no bonus products");
        if (workload == 0) require(
            batchDebits.stream()
                .anyMatch(batch -> batch.size() > 1),
            "No individual batch accepted a mix of ores");
        require(
            expectedEU > 0 && sampledEU == expectedEU,
            "Wrong native energy payment; expected=" + expectedEU + " actual=" + sampledEU);
        require(STEAM_STOCK - volume(steam) == expectedSteam, "Wrong native steam payment");
        for (int i = 0; i < BOOSTS.length; i++) require(
            initialTanks[i] - volume(supplies[i]) == reagents[i] && reagents[i] == (long) CHARGES[i] * submitted,
            "Wrong declared or actual booster charge " + BOOSTS[i]);
        int water = initialTanks[4] - volume(supplies[4]);
        require(water > 0, "The native wash route did not consume water");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mode", workload == 0 ? "mixed" : "single");
        detail.put("inputs", new LinkedHashMap<>(beginningInputs));
        detail.put("batchInputs", new ArrayList<>(batchDebits));
        detail.put("outputs", new LinkedHashMap<>(receipts));
        detail.put("bonuses", bonuses);
        detail.put("steamLitres", expectedSteam);
        detail.put("energyEU", expectedEU);
        detail.put("waterLitres", water);
        detail.put("boostLitres", reagents.clone());
        detail.put(
            "ledgerRows",
            center.terminalLedger()
                .tagCount());
        detail.put("timing", timings.finishWindow(Collections.singletonList(new LinkedHashMap<>())));
        results.add(detail);
    }

    private Map<String, Long> ledger(int column) {
        NBTTagList rows = center.terminalLedger();
        Map<String, Long> values = new LinkedHashMap<>();
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            long amount = row.getLong("C" + column);
            if (amount > 0) values.put(key(ItemStack.loadItemStackFromNBT(row.getCompoundTag("Item"))), amount);
        }
        return values;
    }

    private static Map<String, Long> delta(Map<String, Long> current, Map<String, Long> before) {
        Map<String, Long> difference = new LinkedHashMap<>();
        current.forEach((item, amount) -> {
            long changed = amount - before.getOrDefault(item, 0L);
            if (changed > 0) difference.put(item, changed);
        });
        return difference;
    }

    private static Map<String, Long> inventory(IGregTechTileEntity bus) {
        Map<String, Long> items = new LinkedHashMap<>();
        for (int i = 0; i < bus.getSizeInventory(); i++) {
            ItemStack item = bus.getStackInSlot(i);
            if (item != null && item.stackSize > 0) items.merge(key(item), (long) item.stackSize, Long::sum);
        }
        return items;
    }

    static String key(ItemStack item) {
        return item.getUnlocalizedName() + "/" + item.getItemDamage();
    }

    private static int volume(MTEHatchInput hatch) {
        FluidStack contents = hatch.getFluid();
        return contents == null ? 0 : contents.amount;
    }

    @Override
    public void stop(WorldServer world) {
        if (timings != null) timings.stop();
        for (Ticket ticket : tcpTickets) TestLabRuntime.releaseOwnedTicket(ticket);
        tcpTickets.clear();
        restoreUnusedChunk();
        if (controller == null) return;
        if (controllerTicket != null) restoreController(world);
        controller.disableWorking();
        for (int i = 0; i < module; i++) if (MODULES[i] != null) CenterFixtureGeometry.release(controller, i);
    }

    @Override
    public String debug(WorldServer world) {
        return "preset load phase=" + phase
            + " module="
            + module
            + " workloads="
            + workload
            + " progress="
            + (center == null ? 0 : center.batchProgress());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
