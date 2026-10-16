package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitSelfPoweredProcessingUnit;
import com.miaokatze.gtsr.common.structure.GTSRStructureUtility;
import com.miaokatze.gtsr.main.GTSteamReborn;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTLanguageManager;

/**
 * Destructive probes run only in an empty, separate station. Native definitions supply geometry and native
 * construct/survivalConstruct/checkStructure supply behaviour. No machine formation flag or tick is fabricated.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class TestLabStructureContractTask implements TestLabTask {

    private static final int STATION_X = 16384, STATION_Y = 96, STATION_Z = 16384;
    private final MetaTileEntityID machineId;
    private final int casing, pipe, gear, height;
    private final boolean extended;
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<>();
    private final Set<ChunkCoordIntPair> chunks = new LinkedHashSet<>();
    private final List<Cell> cells = new ArrayList<>();
    private final List<Cell> owned = new ArrayList<>();
    private final List<Cell> spillGuard = new ArrayList<>();
    private final List<String> plannedPieces = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    /** Required contract work which could not execute; this prevents a PASS. */
    private final List<String> notRun = new ArrayList<>();
    /** Explicitly outside this case's requested coverage; retained without downgrading passed contracts. */
    private final List<String> optionalNotRun = new ArrayList<>();
    private final Map<IStructureElement, List<ItemStack>> candidates = new IdentityHashMap<>();
    private final Map<String, String> descriptionFallbacks = new LinkedHashMap<>();
    private IGregTechTileEntity tile;
    private MTEMultiBlockBase machine;
    private TestLabStructure model;
    private ItemStack trigger;
    private AutoPlaceEnvironment environment;
    private CountingSource source;
    private int phase, cursor, shellSlots, floor, preloadCursor;
    private boolean cleanupFailed;
    private boolean cleanupPrepared, spillGuardVerified;
    private int spillResidues;

    private TestLabStructureContractTask(MetaTileEntityID id, int casing, int pipe, int gear, int height,
        boolean extended) {
        machineId = id;
        this.casing = casing;
        this.pipe = pipe;
        this.gear = gear;
        this.height = height;
        this.extended = extended;
    }

    static List<TestLabTask> select(String requested) {
        List<TestLabTask> all = new ArrayList<>();
        for (MetaTileEntityID id : MetaTileEntityID.values()) {
            Object prototype = GregTechAPI.METATILEENTITIES[id.ID];
            if (!(prototype instanceof MTEMultiBlockBase) || prototype instanceof MTECriticalMachine) continue;
            all.add(new TestLabStructureContractTask(id, 1, 1, 1, 1, representative(id)));
        }
        for (MetaTileEntityID id : new MetaTileEntityID[] { MetaTileEntityID.KINETIC_PROCESSING_ARRAY,
            MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY }) {
            // One factor at a time: all casing/frame selections, independent pipe and gear channels.
            for (int tier = 2; tier <= 12; tier++) all.add(new TestLabStructureContractTask(id, tier, 1, 1, 1, false));
            all.add(new TestLabStructureContractTask(id, 1, 2, 1, 1, false));
            all.add(new TestLabStructureContractTask(id, 1, 3, 1, 1, false));
            all.add(new TestLabStructureContractTask(id, 1, 1, 2, 1, false));
        }
        // Turbine's height channel is a one-based layer selection; 5 creates the first extra four-layer stack.
        all.add(new TestLabStructureContractTask(MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY, 1, 1, 1, 4, false));
        all.add(new TestLabStructureContractTask(MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY, 12, 3, 2, 5, false));
        all.add(new TestLabStructureContractTask(MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY, 12, 3, 2, 17, false));
        for (MetaTileEntityID id : new MetaTileEntityID[] { MetaTileEntityID.STEAM_HUB_ARRAY,
            MetaTileEntityID.WATER_HUB_ARRAY }) {
            all.add(new TestLabStructureContractTask(id, 2, 2, 2, 1, false));
            all.add(new TestLabStructureContractTask(id, 3, 3, 3, 1, false));
            all.add(new TestLabStructureContractTask(id, 3, 3, 3, 2, false));
            all.add(new TestLabStructureContractTask(id, 3, 3, 3, 30, false));
        }
        List<TestLabTask> result = new ArrayList<>();
        for (TestLabTask task : all) if ("all".equals(requested) || task.id()
            .equals(requested)
            || task.id()
                .equals("structure." + requested)
            || ((TestLabStructureContractTask) task).machineId.name()
                .equalsIgnoreCase(requested))
            result.add(task);
        if (result.isEmpty()) throw new IllegalArgumentException("Unknown structure contract: " + requested);
        return result;
    }

    private static boolean representative(MetaTileEntityID id) {
        return id == MetaTileEntityID.AIR_COMPRESSOR || id == MetaTileEntityID.KINETIC_PROCESSING_ARRAY
            || id == MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY
            || id == MetaTileEntityID.STEAM_HUB_ARRAY
            || id == MetaTileEntityID.WATER_HUB_ARRAY;
    }

    @Override
    public String id() {
        return "structure." + machineId.name()
            .toLowerCase(Locale.ROOT) + ".c" + casing + "p" + pipe + "g" + gear + "h" + height;
    }

    @Override
    public String displayName() {
        return id() + " native structure contract";
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
    public String performancePhase() {
        return "structure.contract." + phase;
    }

    @Override
    public void start(WorldServer world, int ignoredX, int ignoredY, int ignoredZ) throws Exception {
        if (!(world.provider instanceof TestLabWorldProvider)) throw new IllegalArgumentException("Not testlab");
        // A lambda walker aborts at the first unloaded block. Load the bounded station before model iteration.
        phase = -1;
    }

    private void initializeStation(WorldServer world) throws Exception {
        if (!(world.provider instanceof TestLabWorldProvider)) throw new IllegalArgumentException("Not testlab");
        retain(world, STATION_X, STATION_Z);
        if (!world.isAirBlock(STATION_X, STATION_Y, STATION_Z))
            throw new IllegalStateException("FIXTURE_BLOCKED independent controller station occupied; never overwrite");
        owned.add(new Cell(null, STATION_X, STATION_Y, STATION_Z));
        tile = TestLabFixtures.machine(world, STATION_X, STATION_Y, STATION_Z, machineId.ID);
        machine = (MTEMultiBlockBase) tile.getMetaTileEntity();
        trigger = new ItemStack(Items.stick, 1);
        ChannelDataAccessor.setChannelData(trigger, "casing", casing);
        ChannelDataAccessor.setChannelData(trigger, "pipe", pipe);
        ChannelDataAccessor.setChannelData(trigger, "gear", gear);
        ChannelDataAccessor.setChannelData(trigger, "height", height);
        model = new TestLabStructure(world, machine, trigger);
        if (machine instanceof MTESteamHubArray || machine instanceof MTEWaterHubArray
            || machine instanceof MTEMegaSteamTurbineArray) configureVariablePieces();
        else plannedPieces.add("main");
        IStructureDefinition definition = (IStructureDefinition) machine.getClass()
            .getMethod("getStructureDefinition")
            .invoke(machine);
        int expectedVisits = 0;
        for (String piece : plannedPieces)
            for (IStructureElement element : (IStructureElement[]) definition.getStructureFor(piece))
                if (!element.isNavigating()) expectedVisits++;
        List<?> rawCells = (List<?>) field(model, "cells");
        evidence.put("definitionElementVisits", expectedVisits);
        evidence.put("enumeratedElementVisits", rawCells.size());
        if (rawCells.size() != expectedVisits) throw new IllegalStateException(
            "FIXTURE_BLOCKED incomplete native enumeration expected=" + expectedVisits + " actual=" + rawCells.size());
        Set<String> positions = new LinkedHashSet<>();
        for (Object raw : rawCells) {
            Cell cell = new Cell(
                (IStructureElement) field(raw, "element"),
                (int) field(raw, "x"),
                (int) field(raw, "y"),
                (int) field(raw, "z"));
            if (!world.blockExists(cell.x, cell.y, cell.z) || Math.abs((cell.x >> 4) - (STATION_X >> 4)) > 6
                || Math.abs((cell.z >> 4) - (STATION_Z >> 4)) > 6)
                throw new IllegalStateException("FIXTURE_BLOCKED native position outside loaded station " + cell.key());
            if (positions.add(cell.key())) cells.add(cell);
        }
        if (cells.size() < minimumShapeCells() || cells.size() > 16000)
            throw new IllegalStateException("FIXTURE_BLOCKED native shape sanity rejected cells=" + cells.size());
        source = new CountingSource();
        environment = AutoPlaceEnvironment.fromLegacy(
            source,
            FakePlayerFactory.get(world, new GameProfile(TestLabFixtures.OWNER, "[GTSR Structure Contract]")),
            msg -> {});
        evidence.put("origin", STATION_X + "," + STATION_Y + "," + STATION_Z);
        evidence.put("nativeCells", cells.size());
        evidence.put("channels", "casing=" + casing + ",pipe=" + pipe + ",gear=" + gear + ",height=" + height);
        evidence.put("legacyStationsTouched", false);
        evidence.put("loadedStationChunks", chunks.size());
        evidence.put("clientDescriptionRendering", "NOT_RUN: dedicated-server resource validation only");
        if (machineId == MetaTileEntityID.LARGE_SOLAR_OVERPRESSURE_ARRAY) declareSpillGuard();
    }

    private void declareSpillGuard() {
        int minX = STATION_X, maxX = STATION_X, minZ = STATION_Z, maxZ = STATION_Z, maxY = STATION_Y;
        for (Cell cell : cells) {
            minX = Math.min(minX, cell.x);
            maxX = Math.max(maxX, cell.x);
            minZ = Math.min(minZ, cell.z);
            maxZ = Math.max(maxZ, cell.z);
            maxY = Math.max(maxY, cell.y);
        }
        // Water can spread seven blocks sideways and descend to the lab's fixed y=64 platform.
        minX -= 8;
        maxX += 8;
        minZ -= 8;
        maxZ += 8;
        long volume = (long) (maxX - minX + 1) * (maxZ - minZ + 1) * (maxY - 64);
        if (volume <= 0 || volume > 100000)
            throw new IllegalStateException("FIXTURE_BLOCKED fluid guard exceeds bounded station volume: " + volume);
        for (int y = 65; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++)
            for (int x = minX; x <= maxX; x++) spillGuard.add(new Cell(null, x, y, z));
        evidence.put("declaredFluidGuard", minX + ",65," + minZ + ".." + maxX + ',' + maxY + ',' + maxZ);
        evidence.put("declaredFluidGuardCells", spillGuard.size());
    }

    private void configureVariablePieces() throws Exception {
        ((List<?>) field(model, "cells")).clear();
        ((Map<?, ?>) field(model, "elementGroups")).clear();
        ((List<?>) field(model, "elementOrder")).clear();
        if (machine instanceof MTEMegaSteamTurbineArray) {
            int extra = Math.min(4, Math.max(0, (height - 1) / 4));
            model.piece("base", 6, 5, 0);
            plannedPieces.add("base");
            for (int n = 0; n < extra; n++) {
                model.piece("stack", 6, 9 + n * 4, 0);
                plannedPieces.add("stack");
            }
            model.piece("cap", 6, 7 + extra * 4, 0);
            plannedPieces.add("cap");
            evidence.put("expectedExtraStacks", extra);
        } else {
            int a = (int) field(machine, "HORIZONTAL_OFF_SET");
            int b = (int) field(machine, "VERTICAL_OFF_SET");
            int c = (int) field(machine, "DEPTH_OFF_SET");
            boolean steam = machine instanceof MTESteamHubArray;
            int totalHeight = (int) method(machine, "getConstructTotalHeight", ItemStack.class)
                .invoke(machine, trigger);
            if (steam) {
                model.piece("cap", a, -1, c);
                plannedPieces.add("cap");
            }
            model.piece("base", a, b, c);
            plannedPieces.add("base");
            for (int n = 0; n < totalHeight - (steam ? 2 : 1); n++) {
                model.piece("stack", a, 1 + n, c);
                plannedPieces.add("stack");
            }
            evidence.put("expectedTotalHeight", totalHeight);
        }
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        try {
            if (phase == -1) {
                // Same 169-chunk bound as the ordinary lab, centred around this separate station.
                for (int budget = 0; budget < 16 && preloadCursor < 169; budget++, preloadCursor++)
                    retain(world, STATION_X + (preloadCursor % 13 - 6) * 16, STATION_Z + (preloadCursor / 13 - 6) * 16);
                if (preloadCursor < 169) return null;
                initializeStation(world);
                phase = 0;
            }
            if (phase == 0) {
                // Validate emptiness before claiming a coordinate. An occupied station is infrastructure BLOCKED.
                for (int budget = 0; budget < 128 && cursor < cells.size(); budget++, cursor++) {
                    Cell cell = cells.get(cursor);
                    retain(world, cell.x, cell.z);
                    if (cell.y < 0 || cell.y >= 256 || !world.isAirBlock(cell.x, cell.y, cell.z))
                        throw new IllegalStateException(
                            "FIXTURE_BLOCKED occupied/native-height position " + cell.key());
                    TestLabFixtures.track(world, cell.x, cell.y, cell.z);
                    owned.add(cell);
                }
                if (cursor < cells.size()) return null;
                phase = spillGuard.isEmpty() ? 1 : 7;
                cursor = 0;
            }
            if (phase == 7) {
                for (int budget = 0; budget < 4096 && cursor < spillGuard.size(); budget++, cursor++) {
                    Cell cell = spillGuard.get(cursor);
                    if (cell.x == STATION_X && cell.y == STATION_Y && cell.z == STATION_Z) continue;
                    if (!world.blockExists(cell.x, cell.y, cell.z) || !world.isAirBlock(cell.x, cell.y, cell.z)
                        || world.getTileEntity(cell.x, cell.y, cell.z) != null)
                        throw new IllegalStateException(
                            "FIXTURE_BLOCKED occupied declared fluid-guard position " + cell.key());
                }
                if (cursor < spillGuard.size()) return null;
                spillGuardVerified = true;
                evidence.put("fluidGuardBeforeConstruct", "PASS: entire declared volume verified empty");
                phase = 1;
            }
            if (phase == 1) {
                String before = fingerprint(world);
                ((IConstructable) machine).construct(trigger, true);
                require(before.equals(fingerprint(world)), "hintsOnly modified world");
                evidence.put("hintsOnlyEmptyWorldFingerprint", before);
                ((IConstructable) machine).construct(trigger, false);
                evidence.put("nativeConstructWorldFingerprint", fingerprint(world));
                phase = 2;
                cursor = 0;
                return null;
            }
            if (phase == 2) {
                for (int budget = 0; budget < 64 && cursor < cells.size(); budget++, cursor++) {
                    Cell cell = cells.get(cursor);
                    if (!candidates.containsKey(cell.element)) inspectCandidates(world, cell);
                    if (hasHatch(candidates.get(cell.element))) shellSlots++;
                }
                if (cursor < cells.size()) return null;
                evidence.put("explicitCandidateGroups", candidates.size());
                evidence.put("hatchReplaceablePositions", shellSlots);
                phase = 3;
                return null;
            }
            if (phase == 3) {
                installRequiredPorts(world);
                boolean formed = TestLabStructure.checkStructure(machine, tile);
                evidence.put("nativeCheckStructure", formed);
                if (!formed) {
                    if (representative(machineId))
                        require(false, "native constructed representative rejected: " + model.diagnoseFailure());
                    else notRun
                        .add("formation: native shape constructed; specialized port/topology fixture not supplied");
                }
                String before = fingerprint(world);
                ((IConstructable) machine).construct(trigger, true);
                require(before.equals(fingerprint(world)), "formed hintsOnly modified world");
                evidence.put("hintsOnlyPopulatedWorldFingerprint", before);
                if (formed && extended) survivalProbe(world);
                if (formed && machineId == MetaTileEntityID.AIR_COMPRESSOR && extended) redstoneProbe(world);
                if (formed && representative(machineId) && machineId != MetaTileEntityID.AIR_COMPRESSOR)
                    mixedTierProbe(world);
                if (formed && extended) {
                    floor = GTSRStructureUtility.minimumCasings(shellSlots);
                    evidence.put("expectedMinimumCasings", floor);
                    evidence.put("initialMechanicalCasings", casingCount());
                    phase = 4;
                    cursor = 0;
                } else phase = 5;
                return null;
            }
            if (phase == 4) {
                // Two cells per server tick; replacement search also has its own finite candidate budget.
                for (int budget = 0; budget < 2 && cursor < cells.size() && casingCount() > floor; budget++, cursor++) {
                    Cell cell = cells.get(cursor);
                    if (world.getTileEntity(cell.x, cell.y, cell.z) != null || world.isAirBlock(cell.x, cell.y, cell.z))
                        continue;
                    replaceCountedCasing(world, cell, true);
                }
                if (casingCount() > floor && cursor < cells.size()) return null;
                if (casingCount() != floor) notRun
                    .add("minimum boundary unreachable with available legal hatch types; actual=" + casingCount());
                else minimumBoundary(world);
                phase = 5;
                return null;
            }
            if (phase == 5) {
                if (representative(machineId)) checkTiers();
                if (!extended) optionalNotRun.add("survival/minimum-boundary: representative matrix only");
                if (!evidence.containsKey("redstone0to4and5")) {
                    evidence.put("redstone0to4and5", "NOT_RUN: air-compressor representative only");
                    optionalNotRun.add("redstone0to4and5: air-compressor representative only");
                }
                if (!evidence.containsKey("mixedTierCounterexample")) {
                    evidence.put("mixedTierCounterexample", "NOT_RUN: single-family representative matrix only");
                    optionalNotRun.add("mixed-tier: single-family representative matrix only");
                }
                evidence.put("production", "NOT_RUN: structure contract scope only");
                phase = 6;
                cursor = owned.size() - 1;
            }
        } catch (Exception | LinkageError problem) {
            String message = problem.getClass()
                .getSimpleName() + ": "
                + problem.getMessage();
            if (message.contains("FIXTURE_BLOCKED")) evidence.put("fixtureBlocked", message);
            else failures.add(message);
            phase = 6;
            cursor = owned.size() - 1;
        }
        if (phase == 6) {
            prepareCleanup(world);
            for (int budget = 0; budget < 128 && cursor >= 0; budget++, cursor--) {
                Cell cell = owned.get(cursor);
                clearOwned(world, cell);
            }
            if (cursor >= 0) return null;
            phase = spillGuardVerified ? 8 : 9;
            cursor = 0;
        }
        if (phase == 8) {
            for (int budget = 0; budget < 4096 && cursor < spillGuard.size(); budget++, cursor++) {
                Cell cell = spillGuard.get(cursor);
                if (world.isAirBlock(cell.x, cell.y, cell.z) && world.getTileEntity(cell.x, cell.y, cell.z) == null)
                    continue;
                cleanupFailed = true;
                spillResidues++;
                // Only observe the predeclared empty guard. Never erase an unowned block to manufacture PASS.
                if (spillResidues <= 8) evidence.put(
                    "fluidGuardResidue" + spillResidues,
                    cell.key() + " block="
                        + Block.blockRegistry.getNameForObject(world.getBlock(cell.x, cell.y, cell.z))
                        + " meta="
                        + world.getBlockMetadata(cell.x, cell.y, cell.z));
            }
            if (cursor < spillGuard.size()) return null;
            evidence
                .put("fluidGuardAfterCleanup", spillResidues == 0 ? "PASS: declared volume remains empty" : "BLOCKED");
            evidence.put("fluidGuardResidueCount", spillResidues);
            phase = 9;
        }
        if (phase == 9) {
            release();
            evidence.put("cleanup", cleanupFailed ? "BLOCKED" : "PASS");
            evidence.put("failures", failures);
            evidence.put("notRun", notRun);
            evidence.put("optionalNotRun", optionalNotRun);
            TestLabReport.Status status = cleanupFailed || evidence.containsKey("fixtureBlocked")
                ? TestLabReport.Status.BLOCKED
                : !failures.isEmpty() ? TestLabReport.Status.FAIL
                    : notRun.isEmpty() ? TestLabReport.Status.PASS : TestLabReport.Status.UNSUPPORTED;
            return new TestLabReport.Result(
                status,
                "structure_contract=" + status
                    + " case="
                    + id()
                    + " failures="
                    + failures.size()
                    + " requiredNotRun="
                    + notRun.size()
                    + " optionalNotRun="
                    + optionalNotRun.size(),
                evidence);
        }
        return null;
    }

    private void prepareCleanup(WorldServer world) {
        if (cleanupPrepared) return;
        cleanupPrepared = true;
        // Solar fills water from onPostTick even while disabled. Remove the controller first, then drain all
        // owned liquid cells in one bounded pass while the complete container walls and floor still exist.
        if (!owned.isEmpty()) clearOwned(world, owned.get(0));
        int drained = 0;
        for (Cell cell : owned) if (world.getBlock(cell.x, cell.y, cell.z)
            .getMaterial()
            .isLiquid()) {
                clearOwned(world, cell);
                drained++;
            }
        evidence.put("cleanupDrainedOwnedLiquidsBeforeWalls", drained);
    }

    private void clearOwned(WorldServer world, Cell cell) {
        try {
            world.setBlock(cell.x, cell.y, cell.z, Blocks.air, 0, 2);
            if (!world.isAirBlock(cell.x, cell.y, cell.z) || world.getTileEntity(cell.x, cell.y, cell.z) != null)
                cleanupFailed = true;
        } catch (RuntimeException | LinkageError cleanupProblem) {
            cleanupFailed = true;
            evidence.put(
                "cleanupProblem",
                cleanupProblem.getClass()
                    .getSimpleName() + ": "
                    + cleanupProblem.getMessage());
        }
    }

    private void inspectCandidates(WorldServer world, Cell cell) {
        IStructureElement.BlocksToPlace blocks = cell.element
            .getBlocksToPlace(machine, world, cell.x, cell.y, cell.z, trigger, environment);
        List<ItemStack> stacks = new ArrayList<>();
        if (blocks != null) {
            require(blocks.getStacks() != null, "predicate-only candidate group at " + cell.key());
            if (blocks.getStacks() != null) for (ItemStack stack : blocks.getStacks()) {
                require(stack != null && stack.getItem() != null, "invalid candidate at " + cell.key());
                if (stack != null && stack.getItem() != null) stacks.add(stack.copy());
            }
        }
        candidates.put(cell.element, stacks);
        if (hasHatch(stacks)) {
            List<String> descriptions = cell.element.getDescription(machine);
            require(
                descriptions != null && !descriptions.isEmpty(),
                "hatch candidates without description at " + cell.key());
            if (descriptions != null) for (String description : descriptions) validateDescription(description);
        }
    }

    private int minimumShapeCells() {
        switch (machineId) {
            case AIR_COMPRESSOR:
                return 23;
            case KINETIC_PROCESSING_ARRAY:
                return 93;
            case MEGA_STEAM_TURBINE_ARRAY:
                return 641 + 249 * Math.min(4, Math.max(0, (height - 1) / 4));
            case STEAM_HUB_ARRAY:
                return 70 + 25 * height;
            case WATER_HUB_ARRAY:
                return 25 + 13 * height;
            default:
                return 1;
        }
    }

    private void validateDescription(String description) {
        require(
            description != null && !description.trim()
                .isEmpty(),
            "empty hatch description");
        if (description == null || !description.matches("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+){2,}")) return;
        // Native descriptions may already be display text. Keys are checked against packaged UTF-8 resources,
        // because dedicated servers need not populate StatCollector with a client's selected language.
        String domain = description.startsWith("GTSR.") || description.startsWith("gtsr.") ? "gtsr" : "gregtech";
        if ("gregtech".equals(domain)) {
            validateNativeGtDescription(description);
            return;
        }
        for (String locale : new String[] { "en_US", "zh_CN" }) {
            try {
                Map<String, String> dictionary = DescriptionResources.dictionary(domain, locale);
                String value = dictionary.get(description);
                require(
                    value != null && !value.trim()
                        .isEmpty(),
                    "missing packaged description " + domain + '/' + locale + ": " + description);
            } catch (IOException missingResource) {
                String reason = "description resources unavailable: " + missingResource.getMessage();
                if (!notRun.contains(reason)) notRun.add(reason);
            }
        }
    }

    private void validateNativeGtDescription(String description) {
        try {
            String english = DescriptionResources.dictionary("gregtech", "en_US")
                .get(description);
            String source = "packaged en_US";
            if (!hasText(english)) {
                // GT183 GTLanguageManager.writeToLangFile injects the registered English text into this fallback
                // map before choosing a user-language/config translation. Read that actual registered value.
                if (GTLanguageManager.hasGTLocalizationKey(description)) {
                    Field fallback = GTLanguageManager.class.getDeclaredField("stringTranslateLanguageListFallBack");
                    fallback.setAccessible(true);
                    english = ((Map<String, String>) fallback.get(null)).get(description);
                    source = "GTLanguageManager registered English fallback";
                }
            }
            require(
                hasText(english) && !description.equals(english),
                "missing native GT English resource/registered fallback: " + description);
            if (!hasText(english) || description.equals(english)) return;
            String chinese = DescriptionResources.dictionary("gregtech", "zh_CN")
                .get(description);
            if (!hasText(chinese)) {
                descriptionFallbacks.put(description, "zh_CN absent; " + source + ": " + english);
                evidence.put("nativeGtDescriptionFallbacks", descriptionFallbacks);
            } else if (!"packaged en_US".equals(source)) {
                descriptionFallbacks.put(description, source + ": " + english + "; packaged zh_CN exists");
                evidence.put("nativeGtDescriptionFallbacks", descriptionFallbacks);
            }
        } catch (IOException | ReflectiveOperationException unavailable) {
            String reason = "native GT description evidence unavailable: " + unavailable.getMessage();
            if (!notRun.contains(reason)) notRun.add(reason);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }

    private static final class DescriptionResources {

        private static final Map<String, Map<String, String>> CACHE = new LinkedHashMap<>();

        static synchronized Map<String, String> dictionary(String domain, String locale) throws IOException {
            String path = "/assets/" + domain + "/lang/" + locale + ".lang";
            Map<String, String> cached = CACHE.get(path);
            if (cached != null) return cached;
            InputStream resource = TestLabStructureContractTask.class.getResourceAsStream(path);
            if (resource == null) throw new IOException(path);
            Map<String, String> result = new LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (line.startsWith("\uFEFF")) line = line.substring(1);
                    if (line.trim()
                        .isEmpty()
                        || line.trim()
                            .startsWith("#"))
                        continue;
                    int equals = line.indexOf('=');
                    if (equals > 0) result.put(
                        line.substring(0, equals)
                            .trim(),
                        line.substring(equals + 1));
                }
            }
            CACHE.put(path, result);
            return result;
        }
    }

    private void installRequiredPorts(WorldServer world) throws Exception {
        if (machineId == MetaTileEntityID.SINGULARITY_DRILLING_HUB) {
            // Same real metadata as TestLabDrillingTask, without its remote-node or deposit production fixture.
            int[] ids = { ItemList.Hatch_Input_HV.get(1)
                .getItemDamage(),
                ItemList.Hatch_Output_Bus_HV.get(1)
                    .getItemDamage(),
                ItemList.Hatch_Output_HV.get(1)
                    .getItemDamage() };
            for (int id : ids) if (model.install(id) == null)
                throw new IllegalStateException("required native drilling-hub hatch not installable id=" + id);
            evidence
                .put("drillingHubRequiredPorts", "HV fluid input, HV item output bus, HV fluid output; real metadata");
            return;
        }
        if (machine instanceof MTESteamHubArray || machine instanceof MTEWaterHubArray) {
            model.install(
                (casing == 3 ? MetaTileEntityID.OVERPRESSURE_HUB_STORAGE_UNIT
                    : casing == 2 ? MetaTileEntityID.REINFORCED_HUB_STORAGE_UNIT
                        : MetaTileEntityID.HUB_STORAGE_UNIT).ID);
            boolean water = machine instanceof MTEWaterHubArray;
            model.install((water ? MetaTileEntityID.WATER_HUB_INPUT_HATCH : MetaTileEntityID.STEAM_HUB_INPUT_HATCH).ID);
            model.install(
                (water ? MetaTileEntityID.WATER_HUB_OUTPUT_HATCH : MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH).ID);
            ((IInventory) tile).setInventorySlotContents(
                1,
                (casing == 3 ? GTSRItemList.ReinforcedHubSingularityChip : GTSRItemList.HubSingularityChip).get(1));
            return;
        }
        if (machineId.name()
            .startsWith("CLUSTER_")) {
            if (machineId == MetaTileEntityID.CLUSTER_CONTROLLER) model.install(
                (int) ItemList.Hatch_Input_LV.get(1)
                    .getItemDamage());
            if (machine instanceof MTEBasicLogisticsUnit) {
                model.install(
                    ItemList.Hatch_Input_Bus_LV.get(1)
                        .getItemDamage());
                model.install(
                    ItemList.Hatch_Output_Bus_LV.get(1)
                        .getItemDamage());
                model.install(
                    ItemList.Hatch_Input_LV.get(1)
                        .getItemDamage());
                model.install(
                    ItemList.Hatch_Output_LV.get(1)
                        .getItemDamage());
            } else if (machine instanceof MTEBasicAmplifierUnit) {
                model.install(
                    ItemList.Hatch_Input_LV.get(1)
                        .getItemDamage());
            } else if (machine instanceof MTEUnitSelfPoweredProcessingUnit) {
                boolean hasEnergy = false;
                for (Cell cell : cells) {
                    TileEntity existing = world.getTileEntity(cell.x, cell.y, cell.z);
                    if (existing instanceof IGregTechTileEntity && ((IGregTechTileEntity) existing)
                        .getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEHatchEnergy)
                        hasEnergy = true;
                }
                if (!hasEnergy) TestLabClusterLayout.energyPort(
                    world,
                    (MTEClusterUnitBase) machine,
                    ItemList.Hatch_Energy_IV.get(1)
                        .getItemDamage());
            }
            return;
        }
        // Reuse existing catalog's port requests, not its build/start/tick/thermal-fixture paths.
        TestLabMachineTask requestSource = new TestLabMachineTask(machineId, 1);
        setField(requestSource, "controller", tile);
        setField(requestSource, "multi", machine);
        setField(requestSource, "structure", model);
        method(requestSource, "prepare", WorldServer.class).invoke(requestSource, world);
        for (Object request : (List<?>) field(requestSource, "requests")) {
            int id = (int) field(request, "meta");
            IGregTechTileEntity found = model.install(id);
            if (found == null && (boolean) field(request, "required"))
                throw new IllegalStateException("required native hatch not installable id=" + id);
        }
    }

    private void survivalProbe(WorldServer world) throws Exception {
        if (!(machine instanceof ISurvivalConstructable)) {
            notRun.add("survivalConstruct unavailable");
            return;
        }
        List<Cell> holes = new ArrayList<>();
        for (Cell cell : cells) {
            if (world.getTileEntity(cell.x, cell.y, cell.z) != null || world.isAirBlock(cell.x, cell.y, cell.z))
                continue;
            Block block = world.getBlock(cell.x, cell.y, cell.z);
            ItemStack resource = new ItemStack(block, 1, world.getBlockMetadata(cell.x, cell.y, cell.z));
            if (resource.getItem() == null) continue;
            if (holes.size() < 2) {
                holes.add(cell);
                source.add(resource);
            }
            if (holes.size() == 2) break;
        }
        if (holes.size() != 2) {
            notRun.add("survival: fewer than two item-backed positions");
            return;
        }
        for (Cell cell : holes) world.setBlock(cell.x, cell.y, cell.z, Blocks.air, 0, 2);
        require(!TestLabStructure.checkStructure(machine, tile), "two missing structure blocks accepted");
        ISurvivalBuildEnvironment env = ISurvivalBuildEnvironment.create(source, environment.getActor());
        int first = ((ISurvivalConstructable) machine).survivalConstruct(trigger, 1, env);
        int present = 0;
        for (Cell cell : holes) if (!world.isAirBlock(cell.x, cell.y, cell.z)) present++;
        require(
            present == 1 && source.consumed == 1,
            "survival budget1 expected one block/one resource; present=" + present + " consumed=" + source.consumed);
        int second = ((ISurvivalConstructable) machine).survivalConstruct(trigger, 1, env);
        require(source.consumed == 2, "survival resume did not consume second resource: " + source.consumed);
        require(TestLabStructure.checkStructure(machine, tile), "survival resume did not restore native formation");
        evidence.put(
            "survivalBudgetResume",
            "two-missing-block repair only; not whole-machine survival build; first=" + first
                + ",second="
                + second
                + ",realResourcesConsumed="
                + source.consumed);
        int skips = 0;
        for (Cell cell : cells) if (world.getTileEntity(cell.x, cell.y, cell.z) instanceof IGregTechTileEntity) {
            String before = fingerprint(world);
            int consumed = source.consumed;
            IStructureElement.PlaceResult result = cell.element
                .survivalPlaceBlock(machine, world, cell.x, cell.y, cell.z, trigger, environment);
            require(
                result == IStructureElement.PlaceResult.SKIP && consumed == source.consumed
                    && before.equals(fingerprint(world)),
                "legal existing hatch did not SKIP without consumption/world changes at " + cell.key() + ": " + result);
            skips++;
        }
        evidence.put("legalExistingHatchSkipCount", skips);
    }

    private boolean replaceCountedCasing(WorldServer world, Cell cell, boolean preserveFormation) throws Exception {
        List<ItemStack> stacks = candidates.get(cell.element);
        if (stacks == null) return false;
        Block previous = world.getBlock(cell.x, cell.y, cell.z);
        int previousMeta = world.getBlockMetadata(cell.x, cell.y, cell.z);
        int count = casingCount();
        int attempts = 0;
        for (ItemStack stack : stacks) {
            int id = hatchId(stack);
            if (id < 0 || id == MetaTileEntityID.REDSTONE_HATCH.ID) continue;
            if (++attempts > 12) break;
            TestLabFixtures.machine(world, cell.x, cell.y, cell.z, id);
            boolean formed = TestLabStructure.checkStructure(machine, tile);
            if ((!preserveFormation || formed) && casingCount() == count - 1) return true;
            world.removeTileEntity(cell.x, cell.y, cell.z);
            world.setBlock(cell.x, cell.y, cell.z, previous, previousMeta, 2);
            TestLabStructure.checkStructure(machine, tile);
        }
        return false;
    }

    private void redstoneProbe(WorldServer world) throws Exception {
        List<Cell> replaced = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        List<Integer> metas = new ArrayList<>();
        int initial = casingCount();
        require(TestLabStructure.checkStructure(machine, tile), "redstone zero baseline rejected");
        for (Cell cell : cells) {
            if (!hasHatch(candidates.get(cell.element)) || world.getTileEntity(cell.x, cell.y, cell.z) != null
                || world.isAirBlock(cell.x, cell.y, cell.z)) continue;
            replaced.add(cell);
            blocks.add(world.getBlock(cell.x, cell.y, cell.z));
            metas.add(world.getBlockMetadata(cell.x, cell.y, cell.z));
            TestLabFixtures.machine(world, cell.x, cell.y, cell.z, MetaTileEntityID.REDSTONE_HATCH.ID);
            int n = replaced.size();
            boolean formed = TestLabStructure.checkStructure(machine, tile);
            require(formed == (n <= 4), "redstone count " + n + " formation=" + formed);
            if (n <= 4)
                require(casingCount() == initial - n, "redstone hatch incorrectly counted as mechanical casing");
            if (n == 5) break;
        }
        require(replaced.size() == 5, "redstone boundary needs five shell positions");
        for (int n = replaced.size() - 1; n >= 0; n--) {
            Cell cell = replaced.get(n);
            world.removeTileEntity(cell.x, cell.y, cell.z);
            world.setBlock(cell.x, cell.y, cell.z, blocks.get(n), metas.get(n), 2);
            if (n == 4) require(TestLabStructure.checkStructure(machine, tile), "redstone 5->4 restoration rejected");
        }
        require(
            TestLabStructure.checkStructure(machine, tile) && casingCount() == initial,
            "redstone fixture restoration failed");
        evidence.put(
            "redstone0to4and5",
            "native count0..4 PASS/count5 FAIL/5to4 PASS; each hatch excluded from casing count");
    }

    private void minimumBoundary(WorldServer world) throws Exception {
        require(TestLabStructure.checkStructure(machine, tile), "minimum casing count rejected");
        evidence.put("minimumCasingPASS", casingCount());
        for (Cell cell : cells) {
            if (world.getTileEntity(cell.x, cell.y, cell.z) != null || world.isAirBlock(cell.x, cell.y, cell.z))
                continue;
            Block block = world.getBlock(cell.x, cell.y, cell.z);
            int meta = world.getBlockMetadata(cell.x, cell.y, cell.z);
            if (!replaceCountedCasing(world, cell, false)) continue;
            require(!TestLabStructure.checkStructure(machine, tile), "minimum minus one casings incorrectly formed");
            evidence.put("minimumMinusOneFAIL", casingCount());
            world.removeTileEntity(cell.x, cell.y, cell.z);
            world.setBlock(cell.x, cell.y, cell.z, block, meta, 2);
            require(TestLabStructure.checkStructure(machine, tile), "casing restoration rejected");
            evidence.put("casingRestorationPASS", casingCount());
            return;
        }
        notRun.add("minimum-minus-one: no replaceable remaining casing");
    }

    private void checkTiers() throws Exception {
        Map<String, Integer> observed = new LinkedHashMap<>();
        for (String name : new String[] { "mCasingTier", "mFrameTier", "mPipeTier", "mGearTier", "mStackCount" }) {
            try {
                observed.put(name, (int) field(machine, name));
            } catch (NoSuchFieldException ignored) {}
        }
        evidence.put("nativeTiers", observed);
        if (observed.containsKey("mCasingTier"))
            require(observed.get("mCasingTier") == casing, "casing channel mismatch " + observed);
        if (observed.containsKey("mFrameTier"))
            require(observed.get("mFrameTier") == casing, "frame must share casing channel " + observed);
        if (observed.containsKey("mPipeTier"))
            require(observed.get("mPipeTier") == pipe, "pipe channel mismatch " + observed);
        if (observed.containsKey("mGearTier"))
            require(observed.get("mGearTier") == gear, "gear channel mismatch " + observed);
        if (machine instanceof MTEMegaSteamTurbineArray)
            require(observed.get("mStackCount") == (height - 1) / 4, "one-based turbine height mismatch " + observed);
    }

    private int casingCount() throws Exception {
        try {
            return (int) field(machine, "mMechanicalCasingCount");
        } catch (NoSuchFieldException ignored) {
            try {
                return (int) field(machine, "mCasingAmount");
            } catch (NoSuchFieldException missing) {
                return (int) field(machine, "mCasingCount");
            }
        }
    }

    private void mixedTierProbe(WorldServer world) throws Exception {
        int checked = 0;
        for (String channel : new String[] { "casing", "pipe", "gear" }) {
            ItemStack mixed = trigger.copy();
            int current = ChannelDataAccessor.getChannelData(mixed, channel);
            ChannelDataAccessor.setChannelData(mixed, channel, current == 1 ? 2 : 1);
            for (Cell cell : cells) {
                if (world.getTileEntity(cell.x, cell.y, cell.z) != null || world.isAirBlock(cell.x, cell.y, cell.z))
                    continue;
                Block block = world.getBlock(cell.x, cell.y, cell.z);
                int meta = world.getBlockMetadata(cell.x, cell.y, cell.z);
                IStructureElement.BlocksToPlace alternatives = cell.element
                    .getBlocksToPlace(machine, world, cell.x, cell.y, cell.z, mixed, environment);
                boolean different = false;
                if (alternatives != null && alternatives.getStacks() != null)
                    for (ItemStack stack : alternatives.getStacks()) if (stack != null && hatchId(stack) < 0
                        && stack.getItem() != null
                        && (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(block)
                            || stack.getItemDamage() != meta))
                        different = true;
                if (!different) continue;
                world.setBlock(cell.x, cell.y, cell.z, Blocks.air, 0, 2);
                cell.element.placeBlock(machine, world, cell.x, cell.y, cell.z, mixed);
                boolean changed = block != world.getBlock(cell.x, cell.y, cell.z)
                    || meta != world.getBlockMetadata(cell.x, cell.y, cell.z);
                if (changed) {
                    require(
                        !TestLabStructure.checkStructure(machine, tile),
                        "mixed " + channel + " tier unexpectedly formed");
                    checked++;
                }
                world.removeTileEntity(cell.x, cell.y, cell.z);
                world.setBlock(cell.x, cell.y, cell.z, block, meta, 2);
                require(TestLabStructure.checkStructure(machine, tile), "mixed tier restoration failed for " + channel);
                if (changed) break;
            }
        }
        evidence.put("mixedTierCounterexample", "nativeSingleBlockCounterexamples=" + checked);
        if (checked == 0) notRun.add("mixed-tier: no different native material selected");
    }

    private void require(boolean condition, String message) {
        if (!condition) failures.add(message);
    }

    private String fingerprint(WorldServer world) {
        long hash = 0xcbf29ce484222325L;
        for (Cell cell : owned) {
            hash = (hash ^ Block.getIdFromBlock(world.getBlock(cell.x, cell.y, cell.z))) * 0x100000001b3L;
            hash = (hash ^ world.getBlockMetadata(cell.x, cell.y, cell.z)) * 0x100000001b3L;
            TileEntity te = world.getTileEntity(cell.x, cell.y, cell.z);
            if (te != null) {
                NBTTagCompound nbt = new NBTTagCompound();
                te.writeToNBT(nbt);
                hash = (hash ^ nbt.hashCode()) * 0x100000001b3L;
            }
        }
        return Long.toHexString(hash);
    }

    private void retain(WorldServer world, int x, int z) {
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(x >> 4, z >> 4);
        if (!chunks.add(chunk)) return;
        ForgeChunkManager.Ticket ticket = tickets.isEmpty() ? null : tickets.get(tickets.size() - 1);
        if (ticket == null || ticket.getChunkListDepth() > 0 && ticket.getChunkList()
            .size() >= ticket.getChunkListDepth()) {
            Object mod = Loader.instance()
                .getIndexedModList()
                .get(GTSteamReborn.MODID)
                .getMod();
            ticket = ForgeChunkManager.requestTicket(mod, world, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) throw new IllegalStateException("FIXTURE_BLOCKED temporary chunk ticket unavailable");
            ticket.getModData()
                .setString("OwnerType", "TestLabTransient");
            tickets.add(ticket);
        }
        ForgeChunkManager.forceChunk(ticket, chunk);
        if (!ticket.getChunkList()
            .contains(chunk))
            throw new IllegalStateException(
                "FIXTURE_BLOCKED temporary ticket did not retain " + chunk.chunkXPos + ',' + chunk.chunkZPos);
        TestLabFixtures.requireChunk(world, chunk.chunkXPos, chunk.chunkZPos);
    }

    private void release() {
        for (ForgeChunkManager.Ticket ticket : tickets) TestLabRuntime.releaseOwnedTicket(ticket);
        tickets.clear();
        chunks.clear();
    }

    @Override
    public void stop(WorldServer world) {
        // Interrupted runs remain in the fixture journal for explicit regeneration. Never clear an unclaimed block.
        if (tile != null) tile.disableWorking();
        prepareCleanup(world);
        release();
    }

    @Override
    public String debug(WorldServer world) {
        return id() + " phase=" + phase + " cursor=" + cursor + " cells=" + cells.size() + " failures=" + failures;
    }

    private static boolean hasHatch(List<ItemStack> stacks) {
        if (stacks != null) for (ItemStack stack : stacks) if (hatchId(stack) >= 0) return true;
        return false;
    }

    private static int hatchId(ItemStack stack) {
        if (stack == null || stack.getItem() != net.minecraft.item.Item.getItemFromBlock(GregTechAPI.sBlockMachines))
            return -1;
        int id = stack.getItemDamage();
        return id >= 0 && id < GregTechAPI.METATILEENTITIES.length
            && GregTechAPI.METATILEENTITIES[id] != null
            && !(GregTechAPI.METATILEENTITIES[id] instanceof MTEMultiBlockBase) ? id : -1;
    }

    private static Field findField(Object owner, String name) throws NoSuchFieldException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object owner, String name) throws Exception {
        return findField(owner, name).get(owner);
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        findField(owner, name).set(owner, value);
    }

    private static Method method(Object owner, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }

    private static final class Cell {

        final IStructureElement element;
        final int x, y, z;

        Cell(IStructureElement element, int x, int y, int z) {
            this.element = element;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        String key() {
            return x + "," + y + "," + z;
        }
    }

    /** Finite item-backed source: simulation cannot debit, each committed extraction is recorded. */
    private static final class CountingSource implements IItemSource {

        final List<ItemStack> inventory = new ArrayList<>();
        int consumed;

        void add(ItemStack stack) {
            inventory.add(stack.copy());
        }

        @Override
        public Map<ItemStack, Integer> take(Predicate<ItemStack> predicate, boolean simulate, int count) {
            Map<ItemStack, Integer> taken = new LinkedHashMap<>();
            for (ItemStack stack : inventory) {
                if (count <= 0) break;
                if (stack.stackSize <= 0 || !predicate.test(stack)) continue;
                int amount = Math.min(count, stack.stackSize);
                ItemStack result = stack.copy();
                result.stackSize = amount;
                taken.put(result, amount);
                count -= amount;
                if (!simulate) {
                    stack.stackSize -= amount;
                    consumed += amount;
                }
            }
            return taken.isEmpty() ? Collections.emptyMap() : taken;
        }
    }
}
