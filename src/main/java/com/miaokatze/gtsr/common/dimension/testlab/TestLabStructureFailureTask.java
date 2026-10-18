package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Real world negative contracts: a rejected optional piece must not supply required hatches. */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class TestLabStructureFailureTask implements TestLabTask {

    private final boolean cluster, validPort;
    private final int redstoneCount;
    private final String hub;
    private final List<Cell> base = new ArrayList<>(), extra = new ArrayList<>(), owned = new ArrayList<>();
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<>();
    private final Set<ChunkCoordIntPair> chunks = new LinkedHashSet<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private MTEMultiBlockBase machine;
    private IGregTechTileEntity tile, ghost;
    private TestLabStructure model;
    private final ItemStack trigger = new ItemStack(Items.stick, 1);
    private int phase, cursor, preload, station = 24576;
    private boolean blocked, cleaned, cleanupFailed;

    private TestLabStructureFailureTask(boolean cluster, boolean validPort) {
        this(cluster, validPort, 0);
    }

    private TestLabStructureFailureTask(boolean cluster, boolean validPort, int redstoneCount) {
        this.cluster = cluster;
        this.validPort = validPort;
        this.redstoneCount = redstoneCount;
        this.hub = null;
    }

    private TestLabStructureFailureTask(String hub, boolean validPort) {
        this(hub, validPort, 0);
    }

    private TestLabStructureFailureTask(String hub, boolean validPort, int redstoneCount) {
        this.cluster = false;
        this.validPort = validPort;
        this.redstoneCount = redstoneCount;
        this.hub = hub;
    }

    static List<TestLabTask> select(String requested) {
        List<TestLabTask> selected = new ArrayList<>();
        for (boolean cluster : new boolean[] { true, false }) for (boolean valid : new boolean[] { false, true }) {
            TestLabTask task = new TestLabStructureFailureTask(cluster, valid);
            if ("all".equals(requested) || "negative".equals(requested)
                || task.id()
                    .equals(requested)
                || task.id()
                    .equals("structure." + requested))
                selected.add(task);
        }
        for (int count : new int[] { 4, 5 }) {
            TestLabTask task = new TestLabStructureFailureTask(false, true, count);
            if ("all".equals(requested) || "negative".equals(requested)
                || task.id()
                    .equals(requested)
                || task.id()
                    .equals("structure." + requested))
                selected.add(task);
        }
        for (String hub : new String[] { "steam", "water" }) for (boolean valid : new boolean[] { false, true }) {
            TestLabTask task = new TestLabStructureFailureTask(hub, valid);
            if ("all".equals(requested) || "negative".equals(requested)
                || task.id()
                    .equals(requested)
                || task.id()
                    .equals("structure." + requested))
                selected.add(task);
        }
        for (String hub : new String[] { "steam", "water" }) for (int count : new int[] { 4, 5 }) {
            TestLabTask task = new TestLabStructureFailureTask(hub, true, count);
            if ("all".equals(requested) || "negative".equals(requested)
                || "hub-redstone".equals(requested)
                || task.id()
                    .equals(requested)
                || task.id()
                    .equals("structure." + requested))
                selected.add(task);
        }
        return selected;
    }

    @Override
    public String id() {
        if (redstoneCount > 0) return "structure.negative." + (hub == null ? "turbine" : hub + "_hub")
            + "_redstone.global"
            + redstoneCount;
        if (hub != null)
            return "structure.negative." + hub + "_hub_storage" + (validPort ? ".accepted_state" : ".ghost_only");
        return "structure.negative." + (cluster ? "cluster_input" : "turbine_dynamo")
            + (validPort ? ".accepted_state" : ".ghost_only");
    }

    @Override
    public String displayName() {
        return id();
    }

    @Override
    public int timeoutTicks() {
        return 2400;
    }

    @Override
    public boolean requiresFixtureChunks() {
        return false;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) {}

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsed) throws Exception {
        try {
            if (phase == 0) {
                for (int n = 0; n < 16 && preload < 169; n++, preload++)
                    retain(world, station + (preload % 13 - 6) * 16, 24576 + (preload / 13 - 6) * 16);
                if (preload < 169) return null;
                if (!world.isAirBlock(station, 120, 24576)) {
                    if (station >= 26112)
                        throw new IllegalStateException("FIXTURE_BLOCKED occupied controller stations");
                    release();
                    station += 512;
                    preload = 0;
                    return null;
                }
                tile = TestLabFixtures.machine(
                    world,
                    station,
                    120,
                    24576,
                    (hub != null
                        ? ("steam".equals(hub) ? MetaTileEntityID.STEAM_HUB_ARRAY : MetaTileEntityID.WATER_HUB_ARRAY)
                        : cluster ? MetaTileEntityID.CLUSTER_CONTROLLER
                            : MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY).ID);
                machine = (MTEMultiBlockBase) tile.getMetaTileEntity();
                owned.add(new Cell(null, station, 120, 24576, 0, 0, 0));
                for (String channel : new String[] { "casing", "pipe", "gear", "height", "tier" })
                    ChannelDataAccessor.setChannelData(trigger, channel, 1);
                model = new TestLabStructure(world, machine, trigger);
                if (hub != null) {
                    int a = (int) field(machine, "HORIZONTAL_OFF_SET");
                    int b = (int) field(machine, "VERTICAL_OFF_SET");
                    int c = (int) field(machine, "DEPTH_OFF_SET");
                    if ("steam".equals(hub)) piece(world, base, "cap", a, -1, c);
                    piece(world, base, "base", a, b, c);
                    piece(world, base, "stack", a, 1, c);
                    if (redstoneCount == 0) piece(world, extra, "stack", a, 2, c);
                } else if (cluster) {
                    piece(
                        world,
                        base,
                        "main",
                        ClusterStructureDef.mainOffsetA(),
                        ClusterStructureDef.mainOffsetB(),
                        ClusterStructureDef.mainOffsetC());
                    piece(
                        world,
                        extra,
                        "ext",
                        ClusterStructureDef.mainOffsetA(),
                        ClusterStructureDef.mainOffsetB(),
                        ClusterStructureDef.extOffsetC(0));
                } else {
                    piece(world, base, "base", 6, 5, 0);
                    piece(world, base, "cap", 6, redstoneCount > 0 ? 11 : 7, 0);
                    if (redstoneCount > 0) piece(world, base, "stack", 6, 9, 0);
                    List<Cell> stack = new ArrayList<>();
                    piece(world, stack, "stack", 6, 9, 0);
                    if (redstoneCount == 0) for (Cell c : stack) if (c.b == 0 && c.c == 0 && c.a <= 5) extra.add(c);
                    if (redstoneCount == 0 && extra.size() != 6)
                        throw new IllegalStateException("FIXTURE_BLOCKED incomplete native stack prefix");
                }
                Set<String> keys = new LinkedHashSet<>();
                for (Cell c : join(base, extra)) if (keys.add(c.key())) {
                    if (!world.isAirBlock(c.x, c.y, c.z) || world.getTileEntity(c.x, c.y, c.z) != null)
                        throw new IllegalStateException("FIXTURE_BLOCKED occupied planned position " + c.key());
                    owned.add(c);
                    TestLabFixtures.track(world, c.x, c.y, c.z);
                }
                evidence.put("station", station + ",120,24576");
                evidence.put("nativeBaseCells", base.size());
                evidence.put("nativeExtraCells", extra.size());
                phase = 1;
            }
            if (phase == 1) {
                for (int n = 0; n < 128 && cursor < base.size(); n++, cursor++) place(world, base.get(cursor));
                if (cursor < base.size()) return null;
                if (hub != null) {
                    if (validPort) need(model.install(MetaTileEntityID.HUB_STORAGE_UNIT.ID), "accepted hub storage");
                    need(
                        model.install(
                            ("steam".equals(hub) ? MetaTileEntityID.STEAM_HUB_INPUT_HATCH
                                : MetaTileEntityID.WATER_HUB_INPUT_HATCH).ID),
                        "hub input");
                    need(
                        model.install(
                            ("steam".equals(hub) ? MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH
                                : MetaTileEntityID.WATER_HUB_OUTPUT_HATCH).ID),
                        "hub output");
                    ((net.minecraft.inventory.IInventory) tile).setInventorySlotContents(
                        1,
                        com.miaokatze.gtsr.common.api.enums.GTSRItemList.HubSingularityChip.get(1));
                } else if (cluster) {
                    if (validPort) need(
                        model.install(
                            ItemList.Hatch_Input_LV.get(1)
                                .getItemDamage()),
                        "accepted input");
                } else {
                    need(
                        model.install(
                            ItemList.Hatch_Input_LV.get(1)
                                .getItemDamage()),
                        "accepted input");
                    need(
                        model.install(
                            ItemList.Hatch_Output_LV.get(1)
                                .getItemDamage()),
                        "accepted output");
                    if (validPort) need(
                        model.install(
                            ItemList.Hatch_Dynamo_LV.get(1)
                                .getItemDamage()),
                        "accepted dynamo");
                }
                boolean formed = TestLabStructure.checkStructure(machine, tile);
                evidence.put("baselineFormed", formed);
                require(formed == validPort, "baseline formation=" + formed + " expected=" + validPort);
                evidence.put("before", snapshot());
                cursor = 0;
                phase = 2;
                return null;
            }
            if (phase == 2 && redstoneCount > 0) {
                redstoneProbe(world);
                phase = 3;
                cursor = owned.size() - 1;
            }
            if (phase == 2) {
                for (int n = 0; n < 128 && cursor < extra.size(); n++, cursor++) {
                    Cell c = extra.get(cursor);
                    if (hub == null && !cluster && c.a >= 4) continue;
                    place(world, c);
                }
                if (cursor < extra.size()) return null;
                Cell port = hub != null ? at(extra, 2, 0, 2)
                    : at(extra, cluster ? 1 : 4, cluster ? 2 : 0, cluster ? 1 : 0);
                ghost = TestLabFixtures.machine(
                    world,
                    port.x,
                    port.y,
                    port.z,
                    hub != null ? MetaTileEntityID.HUB_STORAGE_UNIT.ID
                        : (cluster ? ItemList.Hatch_Input_LV : ItemList.Hatch_Dynamo_LV).get(1)
                            .getItemDamage());
                List<IGregTechTileEntity> ghostHubPorts = new ArrayList<>();
                // Steam STACK has storage slots, while its ordinary ports belong to BASE.
                if ("water".equals(hub)) {
                    ghostHubPorts.add(
                        installExtra(
                            world,
                            ("steam".equals(hub) ? MetaTileEntityID.STEAM_HUB_INPUT_HATCH
                                : MetaTileEntityID.WATER_HUB_INPUT_HATCH).ID));
                    ghostHubPorts.add(
                        installExtra(
                            world,
                            ("steam".equals(hub) ? MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH
                                : MetaTileEntityID.WATER_HUB_OUTPUT_HATCH).ID));
                }
                Cell hole = hub != null ? ("steam".equals(hub) ? at(extra, 5, 0, 7) : at(extra, 3, 0, 6))
                    : cluster ? at(extra, 27, 14, 7) : at(extra, 5, 0, 0);
                world.setBlock(hole.x, hole.y, hole.z, Blocks.air, 0, 2);
                boolean formed = TestLabStructure.checkStructure(machine, tile);
                Map<String, Object> after = snapshot();
                evidence.put("afterFailedOptionalPieceFormed", formed);
                evidence.put("after", after);
                evidence.put("ghostPosition", port.key());
                evidence.put("holePosition", hole.key());
                require(formed == validPort, "failed optional piece supplied a required hatch; formation=" + formed);
                require(
                    evidence.get("before")
                        .equals(after),
                    "failed optional piece changed accepted counts/lists/tiers");
                if (hub == null) {
                    Collection<?> ports = (Collection<?>) field(machine, cluster ? "mInputHatches" : "mDynamoHatches");
                    require(!ports.contains(ghost.getMetaTileEntity()), "rejected piece hatch remains registered");
                } else for (IGregTechTileEntity hatch : ghostHubPorts) {
                    Object controller = field(hatch.getMetaTileEntity(), "mController");
                    require(controller == null, "rejected hub layer port remains bound");
                }
                phase = 3;
                cursor = owned.size() - 1;
            }
        } catch (Exception | LinkageError problem) {
            String message = problem.getClass()
                .getSimpleName() + ": "
                + problem.getMessage();
            if (phase == 0 && message.contains("FIXTURE_BLOCKED occupied") && station < 26112) {
                for (Cell cell : owned) clear(world, cell);
                release();
                base.clear();
                extra.clear();
                owned.clear();
                station += 512;
                preload = 0;
                cursor = 0;
                tile = null;
                machine = null;
                evidence.put("stationRelocatedAfterOccupiedFootprint", station);
                return null;
            }
            if (message.contains("FIXTURE_BLOCKED")) {
                blocked = true;
                evidence.put("fixtureBlocked", message);
            } else failures.add(message);
            phase = 3;
            cursor = owned.size() - 1;
        }
        if (phase == 3) {
            if (tile != null && !cleaned) {
                clear(world, owned.get(0));
                cleaned = true;
            }
            for (int n = 0; n < 128 && cursor >= 0; n++, cursor--) clear(world, owned.get(cursor));
            if (cursor >= 0) return null;
            release();
            evidence.put("failures", failures);
            evidence.put("cleanup", cleanupFailed ? "BLOCKED" : "PASS");
            evidence.put("production", "NOT_RUN: structure negative contract only");
            TestLabReport.Status status = blocked || cleanupFailed ? TestLabReport.Status.BLOCKED
                : failures.isEmpty() ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL;
            return new TestLabReport.Result(status, id() + " failures=" + failures.size(), evidence);
        }
        return null;
    }

    private void piece(WorldServer world, List<Cell> destination, String name, int oa, int ob, int oc)
        throws Exception {
        IStructureDefinition definition = (IStructureDefinition) machine.getClass()
            .getMethod("getStructureDefinition")
            .invoke(machine);
        definition.iterate(
            name,
            world,
            ((MTEEnhancedMultiBlockBase) machine).getExtendedFacing(),
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            oa,
            ob,
            oc,
            (element, w, x, y, z, a, b, c) -> {
                if (x != tile.getXCoord() || y != tile.getYCoord() || z != tile.getZCoord())
                    destination.add(new Cell(element, x, y, z, a + oa, b + ob, c + oc, name));
                return true;
            });
        int expected = 0;
        for (IStructureElement element : (IStructureElement[]) definition.getStructureFor(name))
            if (!element.isNavigating()) expected++;
        // The controller marker is a navigation element and does not count as a visited structure cell.
        evidence.put(name + "DefinitionVisits", expected);
    }

    private void place(WorldServer world, Cell c) {
        if (!c.element.placeBlock(machine, world, c.x, c.y, c.z, trigger)
            && !c.element.check(machine, world, c.x, c.y, c.z))
            throw new IllegalStateException("FIXTURE_BLOCKED native placement failed " + c.key());
    }

    private IGregTechTileEntity installExtra(WorldServer world, int id) throws Exception {
        for (Cell c : extra) {
            if (world.isAirBlock(c.x, c.y, c.z) || world.getTileEntity(c.x, c.y, c.z) != null) continue;
            Block previous = world.getBlock(c.x, c.y, c.z);
            int meta = world.getBlockMetadata(c.x, c.y, c.z);
            IGregTechTileEntity hatch = TestLabFixtures.machine(world, c.x, c.y, c.z, id);
            if (c.element.check(machine, world, c.x, c.y, c.z)) return hatch;
            world.removeTileEntity(c.x, c.y, c.z);
            world.setBlock(c.x, c.y, c.z, previous, meta, 2);
        }
        throw new IllegalStateException("FIXTURE_BLOCKED no legal extra port " + id);
    }

    private void redstoneProbe(WorldServer world) throws Exception {
        List<IGregTechTileEntity> hatches = new ArrayList<>();
        List<String> pieces = new ArrayList<>();
        String[] sequence = hub == null ? new String[] { "base", "stack", "cap", "base", "stack" }
            : new String[] { "base", "stack", "base", "stack", "base" };
        for (String piece : sequence) {
            if (hatches.size() >= redstoneCount) break;
            Cell chosen = null;
            for (Cell c : base) {
                if (!piece.equals(c.piece) || world.isAirBlock(c.x, c.y, c.z)
                    || world.getTileEntity(c.x, c.y, c.z) != null) continue;
                IStructureElement.BlocksToPlace blocks = c.element
                    .getBlocksToPlace(machine, world, c.x, c.y, c.z, trigger, null);
                ItemStack inputCandidate = hub == null ? ItemList.Hatch_Input_LV.get(1)
                    : new ItemStack(
                        gregtech.api.GregTechAPI.sBlockMachines,
                        1,
                        ("steam".equals(hub) ? MetaTileEntityID.STEAM_HUB_INPUT_HATCH
                            : MetaTileEntityID.WATER_HUB_INPUT_HATCH).ID);
                ItemStack storageCandidate = new ItemStack(
                    gregtech.api.GregTechAPI.sBlockMachines,
                    1,
                    MetaTileEntityID.HUB_STORAGE_UNIT.ID);
                if (blocks != null && blocks.getPredicate() != null
                    && (blocks.getPredicate()
                        .test(inputCandidate)
                        || hub != null && blocks.getPredicate()
                            .test(storageCandidate))) {
                    chosen = c;
                    break;
                }
            }
            if (chosen == null) throw new IllegalStateException("FIXTURE_BLOCKED no redstone shell cell in " + piece);
            hatches
                .add(TestLabFixtures.machine(world, chosen.x, chosen.y, chosen.z, MetaTileEntityID.REDSTONE_HATCH.ID));
            pieces.add(piece + "@" + chosen.key());
        }
        boolean formed = TestLabStructure.checkStructure(machine, tile);
        evidence.put("redstonePieces", pieces);
        evidence.put("redstoneTotal", hatches.size());
        evidence.put("redstoneFormed", formed);
        evidence.put("redstoneAcceptedStackCount", field(machine, "mStackCount"));
        require(formed == (redstoneCount == 4), "global redstone " + redstoneCount + " formation=" + formed);
        List<Boolean> bound = new ArrayList<>();
        for (IGregTechTileEntity hatch : hatches) {
            boolean attached = ((com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch) hatch.getMetaTileEntity())
                .getController() == machine;
            bound.add(attached);
            require(attached == (redstoneCount == 4), "redstone binding differs from full structure verdict");
        }
        evidence.put("redstoneControllersBound", bound);
    }

    private Map<String, Object> snapshot() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : new String[] { "actualCasingCount", "mCasingAmount", "mStackCount", "mCasingTier",
            "mPipeTier", "mGearTier", "mFrameTier", "mFireboxTier", "mMechanicalCasingCount", "mPressureUnitCount",
            "mHubUnitCount", "mReinforcedUnitCount", "mOverpressureUnitCount", "mSteamInputHatches",
            "mSteamOutputHatches", "mWaterInputHatches", "mWaterOutputHatches", "mSingularitySteamCompartmentCount",
            "mSingularitySteamOutputCompartmentCount", "mSingularityFluidInputCompartmentCount",
            "mSingularityFluidOutputCompartmentCount", "mInputHatches", "mInputBusses", "mOutputHatches",
            "mOutputBusses", "mDynamoHatches", "mDualInputHatches", "pressureSteamHatches", "mSmartInputHatches",
            "mPressureSteamInputs", "eDynamoMulti" }) {
            try {
                Object value = field(machine, name);
                if (value instanceof Collection) {
                    List<String> members = new ArrayList<>();
                    for (Object member : (Collection<?>) value) members.add(identity(member));
                    result.put(name, members);
                } else result.put(name, value);
            } catch (NoSuchFieldException ignored) {}
        }
        if (hub != null) result.put(
            "capacity",
            machine.getClass()
                .getMethod("getTotalCapacity")
                .invoke(machine));
        return result;
    }

    private String identity(Object member) throws Exception {
        if (member instanceof gregtech.api.interfaces.metatileentity.IMetaTileEntity) {
            IGregTechTileEntity base = ((gregtech.api.interfaces.metatileentity.IMetaTileEntity) member)
                .getBaseMetaTileEntity();
            return member.getClass()
                .getName() + "@"
                + base.getXCoord()
                + ','
                + base.getYCoord()
                + ','
                + base.getZCoord();
        }
        return String.valueOf(member);
    }

    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static List<Cell> join(List<Cell> a, List<Cell> b) {
        List<Cell> r = new ArrayList<>(a);
        r.addAll(b);
        return r;
    }

    private static Cell at(List<Cell> cells, int a, int b, int c) {
        for (Cell cell : cells) if (cell.a == a && cell.b == b && cell.c == c) return cell;
        throw new IllegalStateException("FIXTURE_BLOCKED missing native ABC " + a + ',' + b + ',' + c);
    }

    private static void need(Object object, String name) {
        if (object == null) throw new IllegalStateException("FIXTURE_BLOCKED cannot install " + name);
    }

    private void require(boolean condition, String message) {
        if (!condition) failures.add(message);
    }

    private void clear(WorldServer world, Cell c) {
        try {
            world.setBlock(c.x, c.y, c.z, Blocks.air, 0, 2);
            if (!world.isAirBlock(c.x, c.y, c.z) || world.getTileEntity(c.x, c.y, c.z) != null) cleanupFailed = true;
        } catch (RuntimeException | LinkageError problem) {
            cleanupFailed = true;
            evidence.put("cleanupProblem", problem.toString());
        }
    }

    private void retain(WorldServer world, int x, int z) {
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(x >> 4, z >> 4);
        if (!chunks.add(chunk)) return;
        ForgeChunkManager.Ticket ticket = tickets.isEmpty() ? null : tickets.get(tickets.size() - 1);
        if (ticket == null || ticket.getChunkList()
            .size() >= ticket.getChunkListDepth()) {
            Object mod = Loader.instance()
                .getIndexedModList()
                .get(GTSteamReborn.MODID)
                .getMod();
            ticket = ForgeChunkManager.requestTicket(mod, world, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) throw new IllegalStateException("FIXTURE_BLOCKED no temporary chunk ticket");
            ticket.getModData()
                .setString("OwnerType", "TestLabTransient");
            tickets.add(ticket);
        }
        ForgeChunkManager.forceChunk(ticket, chunk);
        TestLabFixtures.requireChunk(world, chunk.chunkXPos, chunk.chunkZPos);
    }

    private void release() {
        for (ForgeChunkManager.Ticket ticket : tickets) TestLabRuntime.releaseOwnedTicket(ticket);
        tickets.clear();
        chunks.clear();
    }

    @Override
    public void stop(WorldServer world) {
        if (tile != null) tile.disableWorking();
        release();
    }

    @Override
    public String debug(WorldServer world) {
        return id() + " phase=" + phase + " cursor=" + cursor;
    }

    private static final class Cell {

        final IStructureElement element;
        final int x, y, z, a, b, c;
        final String piece;

        Cell(IStructureElement element, int x, int y, int z, int a, int b, int c) {
            this(element, x, y, z, a, b, c, "controller");
        }

        Cell(IStructureElement element, int x, int y, int z, int a, int b, int c, String piece) {
            this.piece = piece;
            this.element = element;
            this.x = x;
            this.y = y;
            this.z = z;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        String key() {
            return x + "," + y + "," + z;
        }
    }
}
