// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;
import com.miaokatze.gtsr.common.machine.recovery.GtsrStructureRecovery;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Two explicit runs separated by a complete JVM exit. PREPARED is deliberately BLOCKED, never PASS.
 * The journal is an observation of WorldEvent.Save, never an alternative machine persistence backend.
 * All reloads below come from the same real world; no loadNBTData, manual tile ticks or forced formation.
 */
final class TestLabRecoveryRestart implements TestLabTask {

    static final String PREFIX = "recovery.";
    private static final String JVM = UUID.randomUUID()
        .toString();
    private static final Set<TestLabRecoveryRestart> SAVE_OBSERVERS = new LinkedHashSet<>();
    private static final List<String> REPRESENTATIVES = Arrays.asList("center", "heavy", "mega", "critical_turbine");
    private final String key;
    private final boolean resume;
    private final boolean cleanupOnly;
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private TestLabTask fixture;
    private WorldServer world;
    private NBTTagCompound journal, previous;
    private IGregTechTileEntity controller;
    private MTEMultiBlockBase machine;
    private final List<IGregTechTileEntity> ports = new ArrayList<>();
    private final Set<String> checkedPorts = new LinkedHashSet<>();
    private ChunkCoordIntPair deferredChunk;
    private static final int DEFERRED_TICKS = 60;
    private boolean deferredReleased;
    private File file;
    private int originX, originZ, loadCursor, phase, since, frozenTicks;
    private boolean previousRecovering, prepared, cleaning;
    private long resumedEnergy;

    private TestLabRecoveryRestart(String key, boolean resume) {
        this(key, resume, false);
    }

    private TestLabRecoveryRestart(String key, boolean resume, boolean cleanupOnly) {
        this.key = key;
        this.resume = resume;
        this.cleanupOnly = cleanupOnly;
    }

    static List<TestLabTask> cases() {
        List<TestLabTask> result = new ArrayList<>();
        for (String key : REPRESENTATIVES) {
            result.add(new TestLabRecoveryRestart(key, false));
            result.add(new TestLabRecoveryRestart(key, true));
            result.add(new TestLabRecoveryRestart(key, false, true));
        }
        return result;
    }

    static TestLabRecoveryRestart select(String id) {
        for (TestLabTask task : cases()) if (task.id()
            .equals(id)) return (TestLabRecoveryRestart) task;
        throw new IllegalArgumentException("Unknown explicit JVM recovery case: " + id);
    }

    public String id() {
        return PREFIX + (cleanupOnly ? "cleanup." : resume ? "resume." : "prepare.") + key;
    }

    public String displayName() {
        return "Actual JVM stop/start recovery: " + key
            + (cleanupOnly ? " explicit cleanup only" : resume ? " resume" : " prepare (NOT PASS)");
    }

    public int timeoutTicks() {
        return key.startsWith("critical") ? 36000 : 16000;
    }

    public boolean requiresFixtureChunks() {
        return false;
    }

    public void start(WorldServer world, int ignoredX, int y, int ignoredZ) throws Exception {
        require(world.provider instanceof TestLabWorldProvider, "Owned lab world required");
        this.world = world;
        originX = TestLabRuntime.originX(PREFIX + "prepare." + key);
        originZ = TestLabRuntime.originZ(PREFIX + "prepare." + key);
        file = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtsr-testlab/recovery-" + key + ".dat");
        if (cleanupOnly) {
            require(file.isFile(), "No owned recovery journal to clean");
            try (FileInputStream in = new FileInputStream(file)) {
                journal = CompressedStreamTools.readCompressed(in);
            }
            require(
                journal.getInteger("schema") == 1 && key.equals(journal.getString("case")),
                "Cleanup journal identity mismatch");
            require(
                originX == journal.getInteger("originX") && originZ == journal.getInteger("originZ"),
                "Cleanup station identity changed");
            for (TestLabRecoveryRestart observer : new ArrayList<>(SAVE_OBSERVERS)) if (observer.file.equals(file)) {
                MinecraftForge.EVENT_BUS.unregister(observer);
                observer.prepared = false;
                for (ForgeChunkManager.Ticket ticket : observer.tickets) TestLabRuntime.releaseOwnedTicket(ticket);
                observer.tickets.clear();
                SAVE_OBSERVERS.remove(observer);
            }
            evidence.put("recoveryVerified", false);
            evidence.put("journal", file.getAbsolutePath());
            TestLabFixtures.reset(world, originX, originZ);
            cleaning = true;
            return;
        }
        if (resume) {
            require(file.isFile(), "No prepared disk journal; run prepare and fully stop the server first");
            try (FileInputStream in = new FileInputStream(file)) {
                journal = CompressedStreamTools.readCompressed(in);
            }
            require(
                journal.getInteger("schema") == 1 && key.equals(journal.getString("case")),
                "Journal identity mismatch");
            require(!JVM.equals(journal.getString("jvm")), "A new JVM is mandatory; hot reload is not a restart");
            require(
                "SAVED".equals(journal.getString("stage")),
                "No actual world-save observation; PREPARED alone cannot resume");
            require(
                originX == journal.getInteger("originX") && originZ == journal.getInteger("originZ"),
                "Station layout/catalog changed");
            // Acquire the controller first, before it can receive a natural tile tick in this JVM.
            NBTTagCompound pos = journal.getCompoundTag("controller");
            require(
                journal.hasKey("deferredBlock", 10),
                "Prepared journal has no real delayed structure-block witness");
            NBTTagCompound deferred = journal.getCompoundTag("deferredBlock");
            deferredChunk = new ChunkCoordIntPair(deferred.getInteger("x") >> 4, deferred.getInteger("z") >> 4);
            require(
                deferredChunk.chunkXPos != pos.getInteger("x") >> 4
                    || deferredChunk.chunkZPos != pos.getInteger("z") >> 4,
                "Delayed chunk contains the controller");
            require(
                ForgeChunkManager.getPersistentChunksFor(world)
                    .get(deferredChunk)
                    .isEmpty(),
                "Delayed structure chunk is already held by an external ticket; do not disturb its owner");
            requireDeferredUnloaded();
            retain(pos.getInteger("x") >> 4, pos.getInteger("z") >> 4);
            requireDeferredUnloaded();
            controller = tile(pos);
            machine = (MTEMultiBlockBase) controller.getMetaTileEntity();
            require(controller.getMetaTileID() == pos.getInteger("metaId"), "New world tile has a different MTE id");
            require(
                controller.isAllowedToWork() == journal.getBoolean("allowed"),
                "World restore changed the GT work switch");
            require(
                selectedState(machine).equals(journal.getCompoundTag("state")),
                "Actual new GT tile lost saved progress/heat/pending outputs");
            readSavedPorts(false);
            requireDeferredUnloaded();
            evidence.put("differentJvm", true);
            evidence.put("newTileReadFromWorld", true);
            evidence.put("actualSavedPortStocksReadBack", true);
            evidence.put(
                "savedState",
                journal.getCompoundTag("state")
                    .toString());
            previous = selectedState(machine);
            previousRecovering = GtsrStructureRecovery.isRecovering(machine);
            require(previousRecovering, "No native structure recovery on newly read controller");
            checkLegacyTickets();
        } else {
            require(
                !file.exists(),
                "Existing recovery journal retained; resume/inspect it before preparing another fixture");
            require(
                !"center".equals(key) || MTEMineralIntegrationCenter.REGISTRATION_ENABLED,
                "Center registration disabled");
            journal = new NBTTagCompound();
            journal.setInteger("schema", 1);
            journal.setInteger("dimension", world.provider.dimensionId);
            journal.setString("case", key);
            journal.setString("jvm", JVM);
            journal.setString("stage", "BUILDING");
            journal.setInteger("originX", originX);
            journal.setInteger("originZ", originZ);
            writeJournal();
        }
        evidence.put("journal", file.getAbsolutePath());
        evidence.put("stationOrigin", originX + ",65," + originZ);
        evidence.put(
            "scope",
            "center paid batch; heavy/mega native efficiency; critical turbine paid journal + external loading. Other machines NOT_RUN");
    }

    public TestLabReport.Result tick(WorldServer world, int tick) throws Exception {
        if (cleaning) {
            if (!TestLabFixtures.tickReset(world)) return null;
            journal.setString("stage", "CLEANED");
            writeJournal();
            Files.move(
                file.toPath(),
                new File(file.getParentFile(), "recovery-" + key + "-completed-" + journal.getString("jvm") + ".dat")
                    .toPath());
            evidence.put("ownedFixtureCleanup", true);
            evidence.put("recoveryFrozenTicks", frozenTicks);
            return new TestLabReport.Result(
                TestLabReport.Status.PASS,
                cleanupOnly ? "Explicit owned fixture cleanup only; recovery NOT_RUN"
                    : "Real JVM world recovery, native recheck, output, bad-block rejection and cleanup",
                evidence);
        }
        if (resume && machine != null && phase == 0) observeFreeze();
        if (resume && phase == 0 && !deferredReleased) {
            if (tick <= DEFERRED_TICKS) {
                requireDeferredUnloaded();
                require(
                    GtsrStructureRecovery.isRecovering(machine) && !machine.mMachine,
                    "Native recovery ended or formed while a real required structure chunk was still unloaded");
                return null;
            }
            // Bring back actual saved blocks through the native provider. This does not write a production field.
            retain(deferredChunk.chunkXPos, deferredChunk.chunkZPos);
            readSavedPorts(true);
            NBTTagCompound witness = journal.getCompoundTag("deferredBlock");
            int wx = witness.getInteger("x"), wy = witness.getInteger("y"), wz = witness.getInteger("z");
            require(
                witness.getString("block")
                    .equals(net.minecraft.block.Block.blockRegistry.getNameForObject(world.getBlock(wx, wy, wz)))
                    && world.getBlockMetadata(wx, wy, wz) == witness.getInteger("metadata"),
                "Delayed saved structure block changed on disk");
            evidence.put("realStructureChunkWithheldTicks", DEFERRED_TICKS);
            evidence.put("delayedStructureBlock", witness.toString());
            evidence.put("externalTicketDidNotPreloadDelayedChunk", true);
            deferredReleased = true;
        }
        if (loadCursor < 169) {
            for (int n = 0; n < 8 && loadCursor < 169; n++, loadCursor++)
                retain((originX >> 4) + loadCursor % 13, (originZ >> 4) + loadCursor / 13);
            return null;
        }
        if (!resume) return prepare(tick);
        if (phase == 0) {
            NBTTagList savedPorts = journal.getTagList("ports", 10);
            if (ports.isEmpty())
                for (int i = 0; i < savedPorts.tagCount(); i++) ports.add(tile(savedPorts.getCompoundTagAt(i)));
            if (GtsrStructureRecovery.isRecovering(machine)) return null;
            require(frozenTicks > 0, "Recovery window was never observed under natural world ticks");
            require(machine.mMachine, "Native structure recheck rejected intact saved fixture");
            evidence.put("nativeStructureRevalidated", true);
            checkLegacyTickets();
            if ("center".equals(key)) {
                IGregTechTileEntity output = tile(journal.getCompoundTag("output"));
                for (int i = 0; i < output.getSizeInventory(); i++) output.setInventorySlotContents(i, null);
                // Only the real buses/tanks are replenished. Saved controller state is never loaded by this probe.
                for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchInput) {
                    FluidStack fluid = ((MTEHatchInput) port.getMetaTileEntity()).getFluid();
                    if (fluid != null) TestLabSupply.add(world, port, fluid);
                }
            } else if (!key.startsWith("critical")) {
                for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchInput) {
                    FluidStack fluid = ((MTEHatchInput) port.getMetaTileEntity()).getFluid();
                    if (fluid != null) TestLabSupply.add(world, port, fluid);
                }
            }
            for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchDynamo)
                ((gregtech.api.metatileentity.BaseMetaTileEntity) port).setStoredEU(0);
            resumedEnergy = dynamoEnergy();
            phase = 1;
            since = tick;
            return null;
        }
        if (phase == 1) {
            require(controller.isAllowedToWork(), "Native recovery switched the intact fixture off");
            boolean delivered;
            if ("center".equals(key)) {
                MTEMineralIntegrationCenter center = (MTEMineralIntegrationCenter) machine;
                require(center.batchSequence() == journal.getLong("sequence"), "Paid batch was restarted/duplicated");
                IGregTechTileEntity output = tile(journal.getCompoundTag("output"));
                net.minecraft.item.ItemStack product = net.minecraft.item.ItemStack
                    .loadItemStackFromNBT(journal.getCompoundTag("product"));
                int count = 0;
                for (int i = 0; i < output.getSizeInventory(); i++) {
                    net.minecraft.item.ItemStack stack = output.getStackInSlot(i);
                    if (stack != null && stack.isItemEqual(product)) count += stack.stackSize;
                }
                require(count == 0 || count == journal.getInteger("expected"), "Partial/duplicate paid output");
                delivered = count == journal.getInteger("expected");
                evidence.put("actualPaidOutput", count);
            } else if (machine instanceof MTECriticalMachine) {
                var state = ((MTECriticalMachine) machine).getCriticalState();
                delivered = state.getBatchId()
                    .isEmpty()
                    && !state.runtimeSnapshot()
                        .energy()
                        .getBoolean("funded");
                if (delivered) {
                    MTEHatchOutput output = (MTEHatchOutput) tile(journal.getCompoundTag("output")).getMetaTileEntity();
                    require(
                        output.getFluidAmount() == journal.getLong("expectedFluid"),
                        "Paid critical fluid output mismatch");
                    evidence.put("actualPaidFluidOutput", output.getFluidAmount());
                }
                checkLegacyTickets();
            } else delivered = dynamoEnergy() > resumedEnergy;
            if (!delivered) {
                require(tick - since < 1800, "Saved paid batch/real dynamo did not resume");
                return null;
            }
            evidence.put("realOutputAfterRecovery", true);
            // Break an actual required world block only after the intact recovery has delivered.
            NBTTagCompound broken = journal.getCompoundTag("output");
            require(
                world.setBlockToAir(broken.getInteger("x"), broken.getInteger("y"), broken.getInteger("z")),
                "Required hatch removal failed");
            phase = 2;
            since = tick;
            return null;
        }
        if (phase == 2) {
            // Wait for GT's natural native structure lifecycle, never invoke checkStructure/updateEntity here.
            if (machine.mMachine || GtsrStructureRecovery.isRecovering(machine)) {
                require(tick - since < 4000, "Real missing required hatch was never rejected");
                return null;
            }
            evidence.put("actualBrokenWorldBlockRejected", true);
            TestLabSupply.clear(world);
            TestLabFixtures.reset(world, originX, originZ);
            cleaning = true;
        }
        return null;
    }

    private TestLabReport.Result prepare(int tick) throws Exception {
        if (fixture == null) {
            fixture = switch (key) {
                case "center" -> new TestLabIntegrationCenterMachine();
                case "heavy" -> new TestLabMachineTask(MetaTileEntityID.HEAVY_STEAM_TURBINE, 3);
                case "mega" -> new TestLabMachineTask(MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY, 1);
                case "critical_turbine" -> new TestLabCriticalTask("turbine");
                default -> throw new IllegalStateException(key);
            };
            fixture.start(world, originX, 65, originZ);
        }
        // Observe existing test fixture fields only. Production state is read through its native APIs/NBT.
        controller = (IGregTechTileEntity) field(fixture, key.startsWith("critical") ? "tile" : "controller");
        if (controller != null) machine = (MTEMultiBlockBase) controller.getMetaTileEntity();
        boolean ready = false;
        if (machine instanceof MTEMineralIntegrationCenter) {
            MTEMineralIntegrationCenter center = (MTEMineralIntegrationCenter) machine;
            ready = ((Integer) field(fixture, "phase")) == 3 && center.batchProgress() >= 8;
        } else if (machine instanceof MTECriticalMachine) {
            var state = ((MTECriticalMachine) machine).getCriticalState();
            ready = "RUN".equals(String.valueOf(field(fixture, "phase"))) && state.getBatchProgress() >= 8
                && state.runtimeSnapshot()
                    .energy()
                    .getBoolean("funded");
        } else if (machine != null)
            ready = ((Integer) field(fixture, "phase")) == 2 && machine.mEfficiency > 0 && dynamoFromFixture() > 0;
        if (!ready) {
            // Once the ordinary fixture has installed legitimate ports and inputs, allow only natural ticks.
            // Its longer business probe may deliberately cut fuel/reload chunks and is a separate scenario.
            if (fixture instanceof TestLabMachineTask && ((Integer) field(fixture, "phase")) == 2) return null;
            TestLabReport.Result result = fixture.tick(world, tick);
            require(
                result == null,
                "Fixture ended before restart checkpoint: " + (result == null ? "" : result.detail));
            return null;
        }
        journal.setTag("controller", position(controller));
        journal.setInteger("controllerX", controller.getXCoord());
        journal.setInteger("controllerY", controller.getYCoord());
        journal.setInteger("controllerZ", controller.getZCoord());
        if ("center".equals(key)) {
            require(
                ((IGregTechTileEntity) field(fixture, "input")).getStackInSlot(0) == null,
                "Restart checkpoint was not paid through the actual input bus");
            for (String name : new String[] { "input", "output", "ordinaryInput", "steam", "cooling" }) {
                Object entry = field(fixture, name);
                ports.add(
                    entry instanceof IGregTechTileEntity ? (IGregTechTileEntity) entry
                        : ((gregtech.api.interfaces.metatileentity.IMetaTileEntity) entry).getBaseMetaTileEntity());
            }
            journal.setTag("output", position((IGregTechTileEntity) field(fixture, "output")));
            NBTTagCompound product = new NBTTagCompound();
            ((net.minecraft.item.ItemStack) field(fixture, "product")).writeToNBT(product);
            journal.setTag("product", product);
            journal.setInteger("expected", (Integer) field(fixture, "expected"));
            journal.setLong("sequence", ((MTEMineralIntegrationCenter) machine).batchSequence());
        } else if (key.startsWith("critical")) {
            MTEHatchInput input = (MTEHatchInput) field(fixture, "fluidInput");
            MTEHatchOutput output = (MTEHatchOutput) field(fixture, "fluidOutput");
            ports.add(input.getBaseMetaTileEntity());
            ports.add(output.getBaseMetaTileEntity());
            journal.setTag("output", position(output.getBaseMetaTileEntity()));
            journal.setLong("expectedFluid", (Long) field(fixture, "expectedFluid"));
        } else {
            ports.addAll(fixturePorts());
            for (IGregTechTileEntity port : ports)
                if (port.getMetaTileEntity() instanceof MTEHatchOutput) journal.setTag("output", position(port));
        }
        NBTTagList portList = new NBTTagList();
        for (IGregTechTileEntity port : ports) portList.appendTag(position(port));
        journal.setTag("ports", portList);
        journal.setTag("deferredBlock", selectDeferredBlock());
        journal.setString("stage", "PREPARED");
        prepared = true;
        MinecraftForge.EVENT_BUS.register(this);
        SAVE_OBSERVERS.add(this);
        writeJournal();
        evidence.put("prepared", true);
        evidence.put("fullJvmStopRequired", true);
        evidence.put("externalFixtureTicketsRetainedUntilJvmShutdown", true);
        evidence.put("nextCase", PREFIX + "resume." + key);
        return new TestLabReport.Result(
            TestLabReport.Status.BLOCKED,
            "PREPARED_NOT_PASS: stop this JVM normally, then run the resume case on the same fixed world",
            evidence);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSave(WorldEvent.Save event) {
        if (!prepared || event.world != world) return;
        try {
            if (key.startsWith("critical")) {
                // Inject at save time: the live 20-tick retirement sweep must not turn this into a fake
                // restart test before Forge serializes forcedchunks.dat. The driver checks that disk file.
                for (ForgeChunkManager.Ticket old : new LinkedHashSet<>(
                    ForgeChunkManager.getPersistentChunksFor(world)
                        .values())) {
                    NBTTagCompound tag = old.getModData();
                    if (GTSteamReborn.MODID.equals(old.getModId()) && !old.isPlayerTicket()
                        && "CriticalLoom".equals(tag.getString("OwnerType"))
                        && tag.getInteger("OwnerX") == controller.getXCoord()
                        && tag.getInteger("OwnerY") == controller.getYCoord()
                        && tag.getInteger("OwnerZ") == controller.getZCoord()) ForgeChunkManager.releaseTicket(old);
                }
                ForgeChunkManager.Ticket legacy = newTicket();
                legacy.getModData()
                    .setString("OwnerType", "CriticalLoom");
                legacy.getModData()
                    .setInteger("OwnerX", controller.getXCoord());
                legacy.getModData()
                    .setInteger("OwnerY", controller.getYCoord());
                legacy.getModData()
                    .setInteger("OwnerZ", controller.getZCoord());
                ForgeChunkManager.forceChunk(
                    legacy,
                    new ChunkCoordIntPair(controller.getXCoord() >> 4, controller.getZCoord() >> 4));
                journal.setBoolean("legacyTicketInjected", true);
            }
            journal.setTag("state", selectedState(machine));
            journal.setBoolean("allowed", controller.isAllowedToWork());
            NBTTagList savedPorts = new NBTTagList();
            for (IGregTechTileEntity port : ports) {
                NBTTagCompound pos = position(port);
                pos.setTag("stock", stock(port));
                savedPorts.appendTag(pos);
            }
            journal.setTag("ports", savedPorts);
            journal.setLong("savedWorldTick", world.getTotalWorldTime());
            NBTTagCompound actualTile = new NBTTagCompound();
            ((TileEntity) controller).writeToNBT(actualTile);
            journal.setTag("actualSavedTile", actualTile);
            journal.setString("stage", "SAVED");
            writeJournal();
        } catch (Exception failure) {
            throw new IllegalStateException(
                "Recovery observation could not be saved; prepared is not valid evidence",
                failure);
        }
    }

    private void observeFreeze() {
        boolean recovering = GtsrStructureRecovery.isRecovering(machine);
        NBTTagCompound current = selectedState(machine);
        if (recovering && previousRecovering) {
            require(
                previous.equals(current),
                "Progress/heat/efficiency/paid outputs changed inside native recovery window");
            require(
                controller.isAllowedToWork() == journal.getBoolean("allowed"),
                "Recovery changed the saved work switch");
            frozenTicks++;
        }
        previous = current;
        previousRecovering = recovering;
    }

    private void requireDeferredUnloaded() {
        require(
            !world.theChunkProviderServer.chunkExists(deferredChunk.chunkXPos, deferredChunk.chunkZPos),
            "Delayed structure chunk was loaded early by another actor/provider; no frozen-state claim is possible");
    }

    private void readSavedPorts(boolean includeDeferred) {
        NBTTagList savedPorts = journal.getTagList("ports", 10);
        for (int i = 0; i < savedPorts.tagCount(); i++) {
            NBTTagCompound pos = savedPorts.getCompoundTagAt(i);
            int cx = pos.getInteger("x") >> 4, cz = pos.getInteger("z") >> 4;
            if (!includeDeferred && cx == deferredChunk.chunkXPos && cz == deferredChunk.chunkZPos) continue;
            String identity = pos.getInteger("x") + "," + pos.getInteger("y") + "," + pos.getInteger("z");
            if (checkedPorts.contains(identity)) continue;
            retain(cx, cz);
            IGregTechTileEntity port = tile(pos);
            require(port.getMetaTileID() == pos.getInteger("metaId"), "Saved port MTE identity changed");
            require(
                stock(port).equals(pos.getCompoundTag("stock")),
                "World read changed paid input/output inventory or tank contents");
            ports.add(port);
            checkedPorts.add(identity);
        }
    }

    /** Choose an actual non-air cell from the fixture's live native structure geometry, never an invented block. */
    private NBTTagCompound selectDeferredBlock() throws Exception {
        // Prefer a body-only chunk, so unrelated hatch natural ticks cannot affect the delayed stock readback.
        for (boolean allowPortChunk : new boolean[] { false, true }) {
            if (fixture instanceof TestLabIntegrationCenterMachine) {
                for (Object entry : (List<?>) field(fixture, "positions")) {
                    int[] pos = (int[]) entry;
                    NBTTagCompound witness = deferredWitness(pos[0], pos[1], pos[2], allowPortChunk);
                    if (witness != null) return witness;
                }
            } else if (fixture instanceof TestLabCriticalTask) {
                var state = ((MTECriticalMachine) machine).getCriticalState();
                for (Object entry : (List<?>) field(fixture, "tracked")) {
                    CriticalGeometry.Voxel voxel = (CriticalGeometry.Voxel) entry;
                    NBTTagCompound witness = deferredWitness(
                        state.blockWorldX(voxel),
                        state.blockWorldY(voxel.y),
                        state.blockWorldZ(voxel),
                        allowPortChunk);
                    if (witness != null) return witness;
                }
            } else {
                Object structure = field(fixture, "structure");
                for (Object cell : (List<?>) field(structure, "cells")) {
                    NBTTagCompound witness = deferredWitness(
                        (Integer) field(cell, "x"),
                        (Integer) field(cell, "y"),
                        (Integer) field(cell, "z"),
                        allowPortChunk);
                    if (witness != null) return witness;
                }
            }
        }
        throw new IllegalStateException("Fixture has no required saved body block outside its controller chunk");
    }

    private NBTTagCompound deferredWitness(int x, int y, int z, boolean allowPortChunk) {
        int cx = x >> 4, cz = z >> 4;
        if (cx == controller.getXCoord() >> 4 && cz == controller.getZCoord() >> 4 || world.isAirBlock(x, y, z))
            return null;
        if (!allowPortChunk) for (IGregTechTileEntity port : ports)
            if (cx == port.getXCoord() >> 4 && cz == port.getZCoord() >> 4) return null;
        NBTTagCompound witness = new NBTTagCompound();
        witness.setInteger("x", x);
        witness.setInteger("y", y);
        witness.setInteger("z", z);
        witness.setString(
            "block",
            String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(world.getBlock(x, y, z))));
        witness.setInteger("metadata", world.getBlockMetadata(x, y, z));
        return witness;
    }

    private static NBTTagCompound selectedState(MTEMultiBlockBase machine) {
        NBTTagCompound nbt = new NBTTagCompound(), selected = new NBTTagCompound();
        machine.saveNBTData(nbt);
        for (String key : Arrays.asList(
            "mProgresstime",
            "mMaxProgresstime",
            "mEfficiency",
            "turbineEfficiency",
            "workingTicks",
            "idleTicks",
            "fullFlowTicks",
            "energyCredit",
            "paidSteam",
            "pendingWater",
            "pendingSteam",
            "excessWater",
            "mHeat",
            "CenterElapsed",
            "CenterDuration",
            "CenterSteam",
            "CenterCoolingSteam",
            "CenterEnergy",
            "CenterBoostFluids",
            "CenterBatchModules",
            "CenterBatchRounds",
            "CenterSteamKind",
            "CenterSequence",
            "CenterItemOutputs",
            "CenterFluidOutputs"))
            if (nbt.hasKey(key)) selected.setTag(
                key,
                nbt.getTag(key)
                    .copy());
        if (nbt.hasKey("criticalState")) {
            NBTTagCompound state = nbt.getCompoundTag("criticalState"), paid = new NBTTagCompound();
            for (String key : Arrays.asList(
                "batchId",
                "cycleTicks",
                "cycleDuration",
                "criticalEnergyRuntime",
                "criticalBatchRecipe",
                "batchEu",
                "batchGenerated",
                "pendingGenerated",
                "pendingItems",
                "pendingFluids",
                "inventory",
                "tanks",
                "escrow",
                "fluidEscrow",
                "enabled",
                "ownerUuid",
                "structureId"))
                if (state.hasKey(key)) paid.setTag(
                    key,
                    state.getTag(key)
                        .copy());
            selected.setTag("criticalPaidJournal", paid);
        }
        return selected;
    }

    private void checkLegacyTickets() {
        if (!key.startsWith("critical")) return;
        Set<ForgeChunkManager.Ticket> unique = new LinkedHashSet<>(
            ForgeChunkManager.getPersistentChunksFor(world)
                .values());
        for (ForgeChunkManager.Ticket ticket : unique) {
            NBTTagCompound nbt = ticket.getModData();
            require(
                !("CriticalLoom".equals(nbt.getString("OwnerType"))
                    && nbt.getInteger("OwnerX") == controller.getXCoord()
                    && nbt.getInteger("OwnerY") == controller.getYCoord()
                    && nbt.getInteger("OwnerZ") == controller.getZCoord()),
                "Obsolete CriticalLoom ticket survived restart/reappeared");
        }
        evidence.put("obsoleteCriticalTicketRejected", journal.getBoolean("legacyTicketInjected"));
        evidence.put("criticalWorksWithExternalTickets", true);
    }

    private long dynamoEnergy() {
        long energy = 0;
        for (IGregTechTileEntity port : ports)
            if (port.getMetaTileEntity() instanceof MTEHatchDynamo) energy += port.getStoredEU();
        return energy;
    }

    private long dynamoFromFixture() throws Exception {
        long energy = 0;
        for (IGregTechTileEntity port : fixturePorts())
            if (port.getMetaTileEntity() instanceof MTEHatchDynamo) energy += port.getStoredEU();
        return energy;
    }

    @SuppressWarnings("unchecked")
    private List<IGregTechTileEntity> fixturePorts() throws Exception {
        return (List<IGregTechTileEntity>) field(fixture, "ports");
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private ForgeChunkManager.Ticket newTicket() {
        Object mod = Loader.instance()
            .getIndexedModList()
            .get(GTSteamReborn.MODID)
            .getMod();
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(mod, world, ForgeChunkManager.Type.NORMAL);
        require(ticket != null, "No external lab chunk ticket available");
        return ticket;
    }

    private void retain(int x, int z) {
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(x, z);
        for (ForgeChunkManager.Ticket ticket : tickets) if (ticket.getChunkList()
            .contains(chunk)) return;
        ForgeChunkManager.Ticket ticket = tickets.isEmpty() ? null : tickets.get(tickets.size() - 1);
        if (ticket == null || ticket.getChunkListDepth() > 0 && ticket.getChunkList()
            .size() >= ticket.getChunkListDepth()) {
            ticket = newTicket();
            ticket.getModData()
                .setString("OwnerType", "TestLabTransient");
            tickets.add(ticket);
        }
        ForgeChunkManager.forceChunk(ticket, chunk);
        require(
            ticket.getChunkList()
                .contains(chunk),
            "External lab ticket failed to retain chunk");
        TestLabFixtures.requireChunk(world, x, z);
    }

    private IGregTechTileEntity tile(NBTTagCompound pos) {
        TestLabFixtures.requireChunk(world, pos.getInteger("x") >> 4, pos.getInteger("z") >> 4);
        TileEntity tile = world.getTileEntity(pos.getInteger("x"), pos.getInteger("y"), pos.getInteger("z"));
        require(tile instanceof IGregTechTileEntity, "Saved fixture GT tile is missing");
        return (IGregTechTileEntity) tile;
    }

    private static NBTTagCompound position(IGregTechTileEntity tile) {
        NBTTagCompound pos = new NBTTagCompound();
        pos.setInteger("x", tile.getXCoord());
        pos.setInteger("y", tile.getYCoord());
        pos.setInteger("z", tile.getZCoord());
        pos.setInteger("metaId", tile.getMetaTileID());
        return pos;
    }

    private static NBTTagCompound stock(IGregTechTileEntity tile) {
        NBTTagCompound result = new NBTTagCompound();
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < tile.getSizeInventory(); slot++) {
            net.minecraft.item.ItemStack stack = tile.getStackInSlot(slot);
            if (stack == null) continue;
            NBTTagCompound item = stack.writeToNBT(new NBTTagCompound());
            item.setInteger("slot", slot);
            items.appendTag(item);
        }
        result.setTag("items", items);
        if (tile.getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEBasicTank) {
            FluidStack fluid = ((gregtech.api.metatileentity.implementations.MTEBasicTank) tile.getMetaTileEntity())
                .getFluid();
            if (fluid != null) result.setTag("fluid", fluid.writeToNBT(new NBTTagCompound()));
        }
        return result;
    }

    private void writeJournal() throws Exception {
        Files.createDirectories(
            file.getParentFile()
                .toPath());
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            CompressedStreamTools.writeCompressed(journal, out);
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void stop(WorldServer world) {
        // Preparation intentionally retains the real paid machine and its work switch until the JVM saves it.
        // A failed resume keeps its journal/fixtures for inspection, never deleting paid evidence automatically.
        // Keep the prepared live tiles pinned through the final save. Forge discards these explicitly
        // transient external fixture tickets at JVM shutdown/load; the driver requires complete process exit.
        if (prepared) return;
        if (fixture != null) fixture.stop(world);
        for (ForgeChunkManager.Ticket ticket : tickets) TestLabRuntime.releaseOwnedTicket(ticket);
        tickets.clear();
        if (cleaning) TestLabFixtures.cancelReset();
    }

    public String debug(WorldServer world) {
        return "restart=" + key
            + " phase="
            + phase
            + " loaded="
            + loadCursor
            + "/169 frozen="
            + frozenTicks
            + " startup="
            + (machine == null ? "none" : machine.getmStartUpCheck())
            + " prepared="
            + prepared;
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
