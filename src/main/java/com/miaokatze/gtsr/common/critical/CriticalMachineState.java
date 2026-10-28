package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.readFluids;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.readItems;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.writeFluids;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.writeItems;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.miaokatze.gtsr.common.critical.recipe.CriticalConstructionCosts;
import com.miaokatze.gtsr.common.critical.recipe.CriticalRecipes;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;

import gregtech.api.interfaces.tileentity.IEnergyConnected;

/** Live inputs stay in GT hatches; task escrow and pending delivery are saved in the controller NBT. */
public class CriticalMachineState implements ISidedInventory, IFluidHandler, IEnergyConnected {

    private TileEntity host;
    private boolean protectionDirty = true;
    private final Map<Long, Integer> protectionIndices = new java.util.HashMap<>();
    public net.minecraft.world.World worldObj;
    public int xCoord, yCoord, zCoord;

    public void bindHost(TileEntity tile) {
        if (host != tile || xCoord != tile.xCoord || yCoord != tile.yCoord || zCoord != tile.zCoord)
            loomChunksCache = loomValidationChunksCache = null;
        host = tile;
        worldObj = tile.getWorldObj();
        xCoord = tile.xCoord;
        yCoord = tile.yCoord;
        zCoord = tile.zCoord;
        // Old retained blueprints rewrote the legacy "owner" string as a name. Only empty copies may
        // recover identity from GT's independently maintained owner UUID and the matching legacy name.
        if (worldObj != null && !worldObj.isRemote
            && hasNoJournalAssets()
            && tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getOwnerUuid() != null
            && !owner.isEmpty()
            && owner.equals(gt.getOwnerName())) {
            owner = gt.getOwnerUuid()
                .toString();
            markDirty();
        }
        ForgeDirection frame = getStructureFacing();
        if (boundStructureFacing != frame) {
            loomChunksCache = loomValidationChunksCache = null;
            if (boundStructureFacing != ForgeDirection.UNKNOWN && !structureFacingLocked) {
                if (worldObj != null) CriticalRenderAnchors.remove(this, boundStructureFacing);
                clearPendingClearance();
            }
            boundStructureFacing = frame;
        }
        if (protectionDirty && worldObj != null && !worldObj.isRemote && !structureId.isEmpty()) markDirty();
    }

    public TileEntity getHost() {
        return host;
    }

    public net.minecraft.world.World getWorldObj() {
        return worldObj;
    }

    public void markDirty() {
        if (host != null) host.markDirty();
        if (protectionDirty && worldObj != null && !worldObj.isRemote && !structureId.isEmpty()) {
            data().updateProtection(this);
            protectionDirty = false;
        }
    }

    public static CriticalMachineState resolve(TileEntity tile) {
        if (tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine)
            return machine.getCriticalState();
        return null;
    }

    public long legacyEnergy() {
        return energy;
    }

    public void clearLegacyEnergy() {
        energy = 0;
    }

    private boolean consumeEnergy(long amount) {
        if (host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine) {
            if (machine.consumeCriticalEnergy(amount)) return true;
            return fail("GT仓口额定功率不足，缺电暂停");
        }
        if (energy < amount) return false;
        energy -= amount;
        return true;
    }

    public long getBufferedEnergy() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt ? gt.getStoredEU() : energy;
    }

    public long energyCapacity() {
        return capacity();
    }

    public void setHostEnergy(long stored) {
        energy = Math.max(0, Math.min(capacity(), stored));
    }

    public void presetKind(CriticalMachineKind kind) {
        if (kind != null && state == 0)
            config = new CriticalConfiguration(config.tier, kind, config.parallel, config.speed, config.economy);
    }

    public static final int INPUT_SLOTS = 18;
    public static final int TANK_CAPACITY = 64_000_000;
    private final ItemStack[] inventory = new ItemStack[36];
    private final FluidStack[] tanks = new FluidStack[16];
    private CriticalConfiguration config = new CriticalConfiguration(
        CriticalTier.T1,
        CriticalMachineKind.SOLAR,
        0,
        0,
        0);
    private String owner = "", structureId = "", jobId = "", batchId = "";
    private String status = "待配置";
    private String runtimeBlockerKey = "";
    // IDLE, BUILD, READY, DISMANTLE. PAUSED is a reason, never a destructive state transition.
    private int state, stage, cursor, cycleTicks, cycleDuration;
    private long energy, batchEu, batchGenerated, pendingGenerated;
    private NBTTagCompound energyRuntime = new NBTTagCompound();
    private NBTTagCompound stellarRuntime = new NBTTagCompound(), batchRecipe = new NBTTagCompound();
    private String processingMode = "";

    public NBTTagCompound energyRuntimeData() {
        return energyRuntime;
    }

    public NBTTagCompound getStellarRuntimeData() {
        return stellarRuntime;
    }

    public NBTTagCompound getStellarRuntimeSnapshot() {
        return CriticalStellarRuntime.snapshot(stellarRuntime);
    }

    public boolean holdsNightForGui() {
        return worldObj != null && !worldObj.isRemote && !structureId.isEmpty() && data().holdsNight(structureId);
    }

    public NBTTagCompound getConstructionEconomySnapshot() {
        if (constructionLedger.isPhysicalJob() && (state == 1 || state == 4))
            constructionLedger.physicalNextItems(currentPaidStage());
        if (!constructionLedger.isPhysicalJob() && state == 0
            && isLoomJournalOwner()
            && getGeometryVersion() == 2
            && getV2LayoutRevision() >= 31
            && config.kind.isReleased()) {
            List<CriticalGeometry.Voxel> voxels = allVoxels();
            long now = worldObj == null ? 0L : worldObj.getTotalWorldTime();
            if (constructionPreview == null || now < constructionPreviewTick || now - constructionPreviewTick >= 200) {
                List<CriticalGeometry.Voxel> paid = new ArrayList<>();
                for (CriticalGeometry.Voxel voxel : voxels) if (!isUserBuilt(voxel)) paid.add(voxel);
                constructionPreview = new CriticalConstructionLedger();
                constructionPreview.freezeJob(
                    CriticalConstructionEconomy.freeze(
                        config,
                        paid,
                        CriticalLayoutDefinition.forState(this)
                            .cacheKey()));
                constructionPreviewTick = now;
            }
            return constructionPreview.guiSnapshot();
        }
        return constructionLedger.guiSnapshot();
    }

    public boolean selectStellarProductionPaused(EntityPlayer player) {
        if (worldObj.isRemote || !canUse(player) || config.kind != CriticalMachineKind.SUN) return false;
        stellarRuntime.setBoolean("productionPaused", !stellarRuntime.getBoolean("productionPaused"));
        changed();
        return true;
    }

    public boolean shutdownStellar(EntityPlayer player) {
        if (worldObj.isRemote || !canUse(player)
            || config.kind != CriticalMachineKind.SUN
            || !ownsTask() && CriticalStellarRuntime.hasAssets(stellarRuntime)) return false;
        if (gtMachine() != null)
            CriticalStellarRuntime.shutdown(stellarRuntime, gtMachine().criticalStellarPowerPort());
        return setProductionEnabled(player, false);
    }

    public void setRuntimeBatch(int progress, int duration, long eut, String message) {
        cycleTicks = Math.max(0, progress);
        cycleDuration = Math.max(0, duration);
        batchEu = Math.max(0, eut);
        status = message;
        runtimeBlockerKey = "";
        markDirty();
    }

    public int getSteamMode() {
        return Math.floorMod(energyRuntime.getInteger("steamMode"), 6);
    }

    public boolean selectSteamMode(EntityPlayer player) {
        if (!canUse(player) || config.kind != CriticalMachineKind.SOLAR) return false;
        energyRuntime.setInteger("steamMode", (getSteamMode() + 1) % 6);
        markDirty();
        return true;
    }

    public boolean getSolarSaltMode() {
        return energyRuntime.getBoolean("solarSaltMode");
    }

    public int getSolarRoute() {
        return energyRuntime.getBoolean("solarPlasmaMode") ? 2 : getSolarSaltMode() ? 1 : 0;
    }

    /** Changes the next batch only; the prepaid runtime retains its frozen output route. */
    public boolean selectSolarSaltMode(EntityPlayer player) {
        if (!canUse(player) || config.kind != CriticalMachineKind.SOLAR) return false;
        int route = (getSolarRoute() + 1) % 3;
        energyRuntime.setBoolean("solarSaltMode", route == 1);
        energyRuntime.setBoolean("solarPlasmaMode", route == 2);
        markDirty();
        return true;
    }

    public String getProcessingMode() {
        return processingMode;
    }

    public boolean selectProcessingMode(EntityPlayer player, String mode) {
        if (!canUse(player) || !batchId.isEmpty() || state != 0 && state != 2) return false;
        if (!CriticalProcessingModes.isAllowed(config.kind, mode)) return false;
        processingMode = mode;
        markDirty();
        return true;
    }

    private boolean enabled = true, stageFunded, refundPrepared, dismantleRequested;
    private ItemStack[] escrow = new ItemStack[0], pendingItems = new ItemStack[0];
    private FluidStack[] fluidEscrow = new FluidStack[0], pendingFluids = new FluidStack[0];
    private long recoverySeed;
    private boolean structureValid;
    private List<CriticalGeometry.Voxel> geometryCache;
    private List<CriticalGeometry.Voxel> hatchPortsCache;
    private final List<List<CriticalGeometry.Voxel>> stageCache = new ArrayList<>();
    private final Map<List<CriticalGeometry.Voxel>, List<CriticalGeometry.Voxel>> paidStageCache = new IdentityHashMap<>();
    private int scanCursor; // Dismantling/removal audit cursor; independent of the continuous monitor.
    private final CriticalStructureMonitor structureMonitor = new CriticalStructureMonitor();
    public static final int STRUCTURE_LOAD_GRACE_TICKS = 2400;
    private int structureLoadTicks;
    private long lastStructureLoadingTick = Long.MIN_VALUE;
    private List<CriticalGeometry.Voxel> integrityOptionalPorts;
    private final BitSet placed = new BitSet();
    private final Map<CriticalGeometry.Voxel, Integer> voxelIndices = new IdentityHashMap<>();
    private final BitSet repairTargets = new BitSet();
    private List<List<CriticalGeometry.Voxel>> repairStages;
    private ItemStack[] repairEscrow = new ItemStack[0];
    private boolean repairFunded;
    private boolean removalVerified;
    private long modelFingerprint, jobFingerprint;
    private int clientJobTotal, clientReason;
    private boolean clientWorking;
    private long clientCycleAnchor;
    private final CriticalChunkLoading chunkLoading = new CriticalChunkLoading();
    private List<net.minecraft.world.ChunkCoordIntPair> loomChunksCache;
    private List<net.minecraft.world.ChunkCoordIntPair> loomValidationChunksCache;
    private String pendingConstructionPlayer = "";
    private long pendingConstructionDeadline;
    private String visualHostId = UUID.randomUUID()
        .toString();
    private int geometryVersion;
    private int v2LayoutRevision = 31;
    private ForgeDirection structureFacing = ForgeDirection.NORTH, boundStructureFacing = ForgeDirection.UNKNOWN;
    private boolean structureFacingLocked;
    private boolean loomController, loomJournalOwner, loomOwnedMain, initialClearanceAccepted;
    private int clearanceCursor, blocksChangedThisTick, lightsChangedThisTick;
    private long blockBudgetStarted;

    public int getBlocksChangedThisTick() {
        return blocksChangedThisTick;
    }

    public int getLightsChangedThisTick() {
        return lightsChangedThisTick;
    }

    private List<CriticalGeometry.Voxel> clearanceTargets;
    private final java.util.Set<Long> clearanceTargetPositions = new java.util.HashSet<>();
    private final java.util.Set<Long> clearanceRechecks = new java.util.HashSet<>();

    public void setLoomController(boolean selector) {
        loomController = selector;
        loomJournalOwner = selector;
    }

    /** New installations keep their persistent job on the permanent loom, including after main removal. */
    public boolean isLoomJournalOwner() {
        return loomJournalOwner;
    }

    public boolean isLoomOwnedMain() {
        return loomOwnedMain;
    }

    public void setLoomOwnedMain(CriticalMachineState journalOwner) {
        loomOwnedMain = true;
        geometryVersion = journalOwner.getGeometryVersion();
        v2LayoutRevision = journalOwner.getV2LayoutRevision();
        structureFacing = journalOwner.getStructureFacing();
        structureFacingLocked = true;
        config = journalOwner.config;
        owner = journalOwner.owner;
        visualHostId = journalOwner.visualHostId;
        markDirty();
    }

    public boolean isLoomOwnedMainOf(CriticalMachineState journalOwner) {
        return loomOwnedMain && journalOwner.isLoomJournalOwner()
            && visualHostId.equals(journalOwner.visualHostId)
            && owner.equals(journalOwner.owner);
    }

    public boolean usesLegacyV2Geometry() {
        return getGeometryVersion() == 2 && v2LayoutRevision <= 30 && !loomJournalOwner && !loomOwnedMain;
    }

    /** A running journal keeps its exact voxel ordering across layout fixes. */
    public int getV2LayoutRevision() {
        return v2LayoutRevision;
    }

    private static int readV2LayoutRevision(NBTTagCompound n) {
        if (n.hasKey("v2LayoutRevision")) return n.getInteger("v2LayoutRevision");
        return n.getBoolean("loomOwnedMain") || n.getInteger("state") != 0
            || !n.getString("structureId")
                .isEmpty()
            || !n.getString("jobId")
                .isEmpty()
            || n.getLong("jobFingerprint") != 0L
            || n.getByteArray("placed").length != 0 ? 25 : 26;
    }

    private static boolean hasPersistentLayout(NBTTagCompound n) {
        return n.getBoolean("loomOwnedMain") || n.getInteger("state") != 0
            || !n.getString("structureId")
                .isEmpty()
            || !n.getString("jobId")
                .isEmpty()
            || n.getLong("jobFingerprint") != 0L
            || n.getByteArray("placed").length != 0
            || n.getInteger("stage") != 0
            || n.getInteger("cursor") != 0
            || n.getBoolean("stageFunded")
            || n.getBoolean("constructionRoundFunded")
            || n.getBoolean("constructionCostSnapshot")
            || n.getBoolean("repairFunded")
            || n.getByteArray("repairTargets").length != 0
            || n.getTagList("spent", 10)
                .tagCount() != 0
            || n.getTagList("escrow", 10)
                .tagCount() != 0
            || n.getTagList("fluidEscrow", 10)
                .tagCount() != 0
            || n.getTagList("repairEscrow", 10)
                .tagCount() != 0
            || n.getTagList("constructionPaidFluids", 10)
                .tagCount() != 0
            || n.getBoolean("nativeFundingUncertain")
            || n.getTagList("uncertainFundingItems", 10)
                .tagCount() != 0
            || n.getTagList("uncertainFundingFluids", 10)
                .tagCount() != 0;
    }

    /** Canonical loom-N/main-S frame. Existing commissioned layouts keep their original world axes. */
    public ForgeDirection getStructureFacing() {
        if (getGeometryVersion() != 2 || v2LayoutRevision <= 26) return ForgeDirection.NORTH;
        if (!structureFacingLocked && host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt) {
            ForgeDirection front = CriticalStructureAlignment.horizontal(gt.getFrontFacing());
            return loomController ? front : front.getOpposite();
        }
        return structureFacing;
    }

    public ForgeDirection getControllerFacing(boolean selector) {
        return selector ? getStructureFacing() : getStructureFacing().getOpposite();
    }

    public int blockWorldX(int x, int z) {
        return xCoord
            + CriticalStructureAlignment.rotateX(x - getControllerX(), z - getControllerZ(), getStructureFacing());
    }

    public int blockWorldY(int y) {
        return getOriginY() + y;
    }

    public int blockWorldZ(int x, int z) {
        return zCoord
            + CriticalStructureAlignment.rotateZ(x - getControllerX(), z - getControllerZ(), getStructureFacing());
    }

    public int blockWorldX(CriticalGeometry.Voxel voxel) {
        return blockWorldX(voxel.x, voxel.z);
    }

    public int blockWorldZ(CriticalGeometry.Voxel voxel) {
        return blockWorldZ(voxel.x, voxel.z);
    }

    public int localBlockX(int x, int z) {
        return getControllerX()
            + (int) CriticalStructureAlignment.unrotateX(x - xCoord, z - zCoord, getStructureFacing());
    }

    public int localBlockZ(int x, int z) {
        return getControllerZ()
            + (int) CriticalStructureAlignment.unrotateZ(x - xCoord, z - zCoord, getStructureFacing());
    }

    /** Continuous cube vertices rotate around block centers, rather than integer block corners. */
    public double getMeshOriginX() {
        return blockWorldX(0, 0) + .5 - CriticalStructureAlignment.rotateX(.5, .5, getStructureFacing());
    }

    public double getMeshOriginZ() {
        return blockWorldZ(0, 0) + .5 - CriticalStructureAlignment.rotateZ(.5, .5, getStructureFacing());
    }

    public double localPointX(double x, double z) {
        return CriticalStructureAlignment.unrotateX(x - getMeshOriginX(), z - getMeshOriginZ(), getStructureFacing());
    }

    public double localPointZ(double x, double z) {
        return CriticalStructureAlignment.unrotateZ(x - getMeshOriginX(), z - getMeshOriginZ(), getStructureFacing());
    }

    public double getStructureYawDegrees() {
        return CriticalStructureAlignment.yawDegrees(getStructureFacing());
    }

    public static boolean supportsV2Kind(CriticalMachineKind kind) {
        return kind != null;
    }

    public int getGeometryVersion() {
        return geometryVersion == 0 ? (supportsV2Kind(config.kind) ? 2 : 1) : geometryVersion;
    }

    public int getControllerX() {
        return CriticalLayoutDefinition.forState(this)
            .controllerX();
    }

    public int getControllerY() {
        return CriticalLayoutDefinition.forState(this)
            .controllerY();
    }

    public int getRegionSize() {
        return CriticalLayoutDefinition.forState(this).frameSize;
    }

    public int getRegionHeight() {
        return CriticalLayoutDefinition.forState(this).frameHeight;
    }

    public boolean isInitialClearanceAccepted() {
        return initialClearanceAccepted;
    }

    public boolean hasPhysicalPlugins() {
        return getGeometryVersion() == 2;
    }

    public boolean hasInstalledPlugins() {
        return state == 1 || state == 2 || state == 4;
    }

    public void recordClearanceChange(int x, int y, int z) {
        int rx = localBlockX(x, z), ry = y - getOriginY(), rz = localBlockZ(x, z);
        if (clearanceTargets == null || rx < 0
            || rx >= getRegionSize()
            || ry < 0
            || ry >= getRegionHeight()
            || rz < 0
            || rz >= getRegionSize()) return;
        long key = CriticalGeometry.Voxel.positionKey(rx, ry, rz);
        if (clearanceTargetPositions.contains(key)) clearanceRechecks.add(key);
    }

    public String getVisualHostId() {
        return visualHostId;
    }

    private final CriticalConstructionLedger constructionLedger = new CriticalConstructionLedger();
    private CriticalConstructionLedger constructionPreview;
    private long constructionPreviewTick;
    private int constructionTimeVersion = 1, debugRequirements, debugSpeed;
    private boolean debugIgnoreEnergy, constructionAdvancing, nativeFundingUncertain;
    private ItemStack[] uncertainFundingItems = new ItemStack[0];
    private FluidStack[] uncertainFundingFluids = new FluidStack[0];
    private int repairScanCursor, constructionTickBudget = 1, constructionLastAdvance;
    private int constructionBlockBudget = 128, constructionLightBudget = 16;
    private boolean repairScanPending, completionValidationPending;
    private boolean foundationRequired, foundationModern;
    private int foundationScheduleVersion = CriticalFoundationChunk.VERSION;
    private int foundationCuringCheckTicks;
    private int clientFoundationPhase, clientFoundationCellProgress, clientFoundationCuringRemaining,
        clientFoundationHeatingRemaining;
    private int[] clientFoundationCells = new int[0];
    private boolean clientFoundationCellFunded, clientFoundationHeatingFunded;

    public boolean isFoundationRequired() {
        return foundationRequired;
    }

    public boolean usesThreePartConstruction() {
        return constructionLedger.isPhysicalJob() && constructionLedger.economyVersion() >= 5;
    }

    public List<net.minecraft.world.ChunkCoordIntPair> getFoundationChunks() {
        return loomChunks();
    }

    private CriticalFoundationChunk foundation(int index) {
        net.minecraft.world.ChunkCoordIntPair chunk = loomChunks().get(index);
        CriticalFoundationChunk journal = data().findFoundation(chunk.chunkXPos, chunk.chunkZPos);
        if (journal == null) {
            journal = data().foundation(chunk.chunkXPos, chunk.chunkZPos);
            if (!foundationModern) journal.useLegacySchedule();
        }
        return journal;
    }

    private CriticalFoundationChunk findFoundation(int index) {
        net.minecraft.world.ChunkCoordIntPair chunk = loomChunks().get(index);
        return data().findFoundation(chunk.chunkXPos, chunk.chunkZPos);
    }

    public int getFoundationChunkCompletedCells(int index) {
        if (index < 0 || index >= loomChunks().size()) return 0;
        if (worldObj == null || worldObj.isRemote)
            return index < clientFoundationCells.length ? clientFoundationCells[index] : 0;
        CriticalFoundationChunk chunk = findFoundation(index);
        return chunk == null ? 0 : chunk.completedCells();
    }

    public int getFoundationCompletedCells() {
        int completed = 0;
        for (int i = 0; i < loomChunks().size(); i++) completed += getFoundationChunkCompletedCells(i);
        return completed;
    }

    public int getFoundationTotalCells() {
        return loomChunks().size() * CriticalFoundationChunk.CELLS;
    }

    public int getFoundationActiveChunkIndex() {
        for (int i = 0; i < loomChunks().size(); i++)
            if (getFoundationChunkCompletedCells(i) < CriticalFoundationChunk.CELLS) return i;
        return -1;
    }

    public int getFoundationActiveCellIndex() {
        int index = getFoundationActiveChunkIndex();
        return index < 0 ? -1 : getFoundationChunkCompletedCells(index);
    }

    public int getFoundationCellProgressTicks() {
        if (worldObj == null || worldObj.isRemote) return clientFoundationCellProgress;
        int index = getFoundationActiveChunkIndex();
        CriticalFoundationChunk chunk = index < 0 ? null : findFoundation(index);
        return chunk == null ? 0 : chunk.cellProgress();
    }

    public int getFoundationCellDurationTicks() {
        return getFoundationScheduleVersion() >= CriticalFoundationChunk.VERSION ? CriticalFoundationChunk.CELL_DURATION
            : CriticalFoundationChunk.LEGACY_CELL_DURATION;
    }

    public int getFoundationScheduleVersion() {
        if (worldObj == null || worldObj.isRemote) return foundationModern ? foundationScheduleVersion : 0;
        int active = getFoundationActiveChunkIndex();
        CriticalFoundationChunk chunk = active < 0 ? null : findFoundation(active);
        return chunk == null ? foundationModern ? foundationScheduleVersion : 0 : chunk.scheduleVersion();
    }

    public boolean isFoundationCellFunded() {
        if (worldObj == null || worldObj.isRemote) return clientFoundationCellFunded;
        int index = getFoundationActiveChunkIndex();
        CriticalFoundationChunk chunk = index < 0 ? null : findFoundation(index);
        return chunk != null && chunk.funded();
    }

    public int getFoundationCuringRemainingTicks() {
        if (worldObj == null || worldObj.isRemote) return clientFoundationCuringRemaining;
        int remaining = 0;
        for (int i = 0; i < loomChunks().size(); i++) {
            CriticalFoundationChunk chunk = findFoundation(i);
            remaining = Math
                .max(remaining, chunk == null ? getFoundationCuringDurationTicks() : chunk.curingRemaining());
        }
        return remaining;
    }

    public int getFoundationCuringDurationTicks() {
        return foundationModern ? CriticalFoundationChunk.CURING_DURATION
            : CriticalFoundationChunk.LEGACY_CURING_DURATION;
    }

    public int getFoundationHeatingRemainingTicks() {
        if (worldObj == null || worldObj.isRemote) return clientFoundationHeatingRemaining;
        int remaining = 0;
        for (int i = 0; i < loomChunks().size(); i++) {
            CriticalFoundationChunk chunk = findFoundation(i);
            remaining = Math.max(
                remaining,
                chunk == null ? (foundationModern ? CriticalFoundationChunk.HEATING_DURATION : 0)
                    : chunk.heatingRemaining());
        }
        return remaining;
    }

    public boolean isFoundationHeatingFunded() {
        if (worldObj == null || worldObj.isRemote) return clientFoundationHeatingFunded;
        boolean heating = false;
        for (int i = 0; i < loomChunks().size(); i++) {
            CriticalFoundationChunk chunk = findFoundation(i);
            if (chunk != null && chunk.heatingRemaining() > 0) {
                heating = true;
                if (!chunk.heatingFunded()) return false;
            }
        }
        return heating;
    }

    /** 0 legacy/no foundation, 1 pouring, 2 static curing, 3 complete, 4 steam heat curing. */
    public int getFoundationPhase() {
        if (!foundationRequired) return 0;
        if (worldObj == null || worldObj.isRemote) return clientFoundationPhase;
        if (getFoundationCompletedCells() < getFoundationTotalCells()) return 1;
        if (getFoundationHeatingRemainingTicks() > 0) return 4;
        return getFoundationCuringRemainingTicks() > 0 ? 2 : 3;
    }

    private void tickFoundation() {
        long tick = worldObj.getTotalWorldTime();
        long voltage = gregtech.api.enums.GTValues.V[9]; // UHV, one ampere.
        int active = getFoundationActiveChunkIndex();
        if (active >= 0) {
            CriticalFoundationChunk chunk = foundation(active);
            if (chunk.fundingUncertain()) {
                fail("ME扣料结果待人工核验，任务已锁定");
                return;
            }
            if (chunk.workedAt(tick)) return;
            com.miaokatze.gtsr.common.machine.MTECriticalMachine machine = gtMachine();
            long suppliedVoltage = machine == null ? voltage : machine.criticalRecipeVoltage();
            if (!debugIgnoreEnergy && suppliedVoltage < voltage) {
                fail("GT仓口额定功率不足，缺电暂停");
                return;
            }
            int work = Math.max(1, CriticalFoundationChunk.overclockWork(voltage, suppliedVoltage));
            work = debugSpeed == 3 ? chunk.cellDuration() : work * (debugSpeed == 2 ? 10 : debugSpeed == 1 ? 2 : 1);
            work = Math.min(work, chunk.cellDuration() - chunk.cellProgress());
            long eu = voltage * work;
            if (!debugIgnoreEnergy && !canConsumeConstructionEnergy(eu)) {
                fail("缺电暂停");
                return;
            }
            if (!chunk.funded()) {
                ItemStack[] items = ignoresConstructionItems() ? new ItemStack[0]
                    : CriticalFoundationMaterials.items(chunk.scheduleVersion());
                FluidStack[] fluids = ignoresConstructionFluids() ? new FluidStack[0]
                    : new FluidStack[] { CriticalFoundationMaterials
                        .fluid(CriticalFoundationMaterials.concreteAmount(chunk.scheduleVersion())) };
                if (!fundCriticalInputs(items, fluids)) {
                    if (nativeFundingUncertain) {
                        chunk.lockFunding();
                        data().markDirty();
                    }
                    return;
                }
                chunk.setFunded();
                data().markDirty();
            }
            if (!debugIgnoreEnergy && !consumeEnergy(eu)) return;
            chunk.pour(work, tick);
            data().markDirty();
            constructionAdvancing = true;
            status = "地基灌注中";
        } else if (getFoundationHeatingRemainingTicks() > 0) {
            List<CriticalFoundationChunk> heating = new ArrayList<>();
            boolean needsPayment = false;
            for (int i = 0; i < loomChunks().size(); i++) {
                CriticalFoundationChunk chunk = foundation(i);
                if (chunk.fundingUncertain()) {
                    fail("ME扣料结果待人工核验，任务已锁定");
                    return;
                }
                if (chunk.heatingRemaining() > 0 && !chunk.workedAt(tick)) {
                    heating.add(chunk);
                    needsPayment |= !chunk.heatingFunded();
                }
            }
            if (heating.isEmpty()) return;
            int work = foundationHeatingWork(heating);
            long eu = voltage * work;
            if (!debugIgnoreEnergy && gtMachine() != null && gtMachine().criticalRecipeVoltage() < voltage) {
                fail("GT仓口额定功率不足，缺电暂停");
                return;
            }
            if (!debugIgnoreEnergy && !canConsumeConstructionEnergy(eu)) {
                fail("缺电暂停");
                return;
            }
            if (needsPayment) {
                FluidStack[] steam = ignoresConstructionFluids() ? new FluidStack[0]
                    : new FluidStack[] { gregtech.api.util.GTModHandler
                        .getSuperHeatedSteam(CriticalFoundationChunk.HEATING_STEAM_PER_TICK * work) };
                if (!fundCriticalInputs(new ItemStack[0], steam)) {
                    if (nativeFundingUncertain) {
                        for (CriticalFoundationChunk chunk : heating) chunk.lockFunding();
                        data().markDirty();
                    }
                    return;
                }
                for (CriticalFoundationChunk chunk : heating) chunk.fundHeating(work);
                data().markDirty();
            }
            if (!debugIgnoreEnergy && !consumeEnergy(eu)) return;
            for (CriticalFoundationChunk chunk : heating) chunk.heat(work, tick);
            data().markDirty();
            constructionAdvancing = true;
            status = "地基热养护中";
        } else {
            List<CriticalFoundationChunk> curing = new ArrayList<>();
            for (int i = 0; i < loomChunks().size(); i++) {
                CriticalFoundationChunk chunk = foundation(i);
                if (chunk.fundingUncertain()) {
                    fail("ME扣料结果待人工核验，任务已锁定");
                    return;
                }
                if (!chunk.complete() && !chunk.workedAt(tick)) curing.add(chunk);
            }
            if (curing.isEmpty()) return;
            int work = foundationPoweredWork(
                Math.min(
                    getFoundationCuringRemainingTicks(),
                    CriticalConstructionDebugPolicy.work(1, debugSpeed, getFoundationCuringDurationTicks())));
            if (!debugIgnoreEnergy && gtMachine() != null && gtMachine().criticalRecipeVoltage() < voltage) {
                fail("GT仓口额定功率不足，缺电暂停");
                return;
            }
            if (!debugIgnoreEnergy && !consumeEnergy(voltage * work)) {
                fail("缺电暂停");
                return;
            }
            for (CriticalFoundationChunk chunk : curing) chunk.cure(work, tick);
            foundationCuringCheckTicks++;
            if (foundationCuringCheckTicks >= 400) {
                foundationCuringCheckTicks = 0;
                com.miaokatze.gtsr.common.machine.MTECriticalMachine machine = gtMachine();
                if (machine != null) {
                    CriticalNativeMEFunding.ItemConsumption result = machine.consumeAllCriticalInputItem(
                        com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity.get(1));
                    if (result.result != CriticalNativeMEFunding.Result.SUCCESS) {
                        nativeFundingUncertain = true;
                        ItemStack uncertain = com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity
                            .get(1);
                        uncertain.stackSize = (int) Math.min(Integer.MAX_VALUE, result.amount);
                        uncertainFundingItems = new ItemStack[] { uncertain };
                        for (CriticalFoundationChunk chunk : curing) chunk.lockFunding();
                        data().markDirty();
                        markDirty();
                        fail("ME扣料结果待人工核验，任务已锁定");
                        return;
                    }
                    for (CriticalFoundationChunk chunk : curing) chunk.accelerate(result.amount);
                }
            }
            data().markDirty();
            constructionAdvancing = true;
            status = "地基静态养护中";
        }
        markDirty();
    }

    private int foundationHeatingWork(List<CriticalFoundationChunk> heating) {
        int work = Math.min(
            getFoundationHeatingRemainingTicks(),
            CriticalConstructionDebugPolicy.work(1, debugSpeed, CriticalFoundationChunk.HEATING_DURATION));
        // A saved payment can belong to a slower setting or an interrupted earlier tick.
        for (CriticalFoundationChunk chunk : heating)
            if (chunk.heatingFunded()) work = Math.min(work, chunk.heatingPaidWork());
        return foundationPoweredWork(work);
    }

    private int foundationPoweredWork(int work) {
        if (debugIgnoreEnergy || work <= 1) return work;
        com.miaokatze.gtsr.common.machine.MTECriticalMachine machine = gtMachine();
        long available = machine == null ? energy : machine.criticalRecipePower();
        // Debug work can use real available power, but never raises the native per-tick ceiling.
        return (int) Math.min(work, Math.max(1L, available / gregtech.api.enums.GTValues.V[9]));
    }

    private int foundationHeatingSteamDue() {
        if (worldObj == null || worldObj.isRemote) return CriticalFoundationChunk.HEATING_STEAM_PER_TICK * Math.min(
            getFoundationHeatingRemainingTicks(),
            CriticalConstructionDebugPolicy.work(1, debugSpeed, CriticalFoundationChunk.HEATING_DURATION));
        List<CriticalFoundationChunk> heating = new ArrayList<>();
        for (int i = 0; i < loomChunks().size(); i++) {
            CriticalFoundationChunk chunk = findFoundation(i);
            if (chunk != null && chunk.heatingRemaining() > 0) heating.add(chunk);
        }
        return CriticalFoundationChunk.HEATING_STEAM_PER_TICK * foundationHeatingWork(heating);
    }

    public CriticalTier getTier() {
        return config.tier;
    }

    public CriticalMachineKind getKind() {
        return config.kind;
    }

    public CriticalMachineKind getMachineKind() {
        return config.kind;
    }

    public int getParallelPlugins() {
        return config.parallel;
    }

    public int getSpeedPlugins() {
        return config.speed;
    }

    public int getEfficiencyPlugins() {
        return config.economy;
    }

    public String getStatus() {
        return status;
    }

    public int getCursor() {
        return cursor;
    }

    public int getConstructionPlaced() {
        return cursor;
    }

    public boolean isStructureComplete() {
        return state == 2;
    }

    public boolean isStructureValidated() {
        return structureValid;
    }

    /** Geometry validity is independent of recipe activity, input availability and the soft mallet. */
    public boolean isGeometryValidated() {
        return state == 2 && structureValid;
    }

    /** Server-thread only. This method never requests a chunk or reads an unloaded position. */
    void tickStructureMonitor() {
        if (worldObj == null || worldObj.isRemote || state != 2) return;
        if (!areStructureChunksLoaded()) {
            boolean before = structureValid;
            structureMonitor.pauseUnloaded();
            structureValid = false;
            if (before) changed();
            return;
        }
        List<CriticalGeometry.Voxel> geometry = allVoxels();
        if (integrityOptionalPorts == null) integrityOptionalPorts = CriticalGeometry.optionalWorkPorts(this);
        boolean before = structureValid;
        structureValid = structureMonitor.tickSamples(geometry.size() + integrityOptionalPorts.size(), index -> {
            boolean optional = index >= geometry.size();
            CriticalGeometry.Voxel voxel = optional ? integrityOptionalPorts.get(index - geometry.size())
                : geometry.get(index);
            int x = wx(voxel), y = wy(voxel), z = wz(voxel);
            if (!worldObj.getChunkProvider()
                .chunkExists(x >> 4, z >> 4)) return CriticalStructureMonitor.Sample.UNLOADED;
            boolean matches = optional ? worldObj.isAirBlock(x, y, z) || isStandardHatch(voxel) : matches(voxel);
            return matches ? CriticalStructureMonitor.Sample.MATCH : CriticalStructureMonitor.Sample.BROKEN;
        });
        if (structureValid) completionValidationPending = false;
        if (before != structureValid) changed();
    }

    private void revokeGeometryValidation() {
        boolean before = structureValid;
        structureMonitor.invalidate();
        structureValid = false;
        if (before) changed();
    }

    public int getStage() {
        return stage;
    }

    public int getState() {
        return state;
    }

    public String getJobId() {
        return jobId;
    }

    public String getBatchId() {
        return batchId;
    }

    public long getBatchEuPerTick() {
        return batchEu;
    }

    public ItemStack[] getConstructionCostsForGui() {
        if (state != 0 && state != 1 && state != 4) return new ItemStack[0];
        if (state == 1 && foundationRequired && getFoundationPhase() != 3)
            return getFoundationPhase() == 1 ? activeFoundationItems() : new ItemStack[0];
        if (constructionLedger.isPhysicalJob()) {
            List<CriticalGeometry.Voxel> current = currentPaidStage();
            return constructionLedger.physicalJob()
                .costs(current, 0, current.size());
        }
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        int count = 0;
        int costStage = state == 0 ? 0 : stage;
        if ((state == 0 || state == 1) && costStage >= 0 && costStage < stageCache.size()) {
            for (CriticalGeometry.Voxel v : stageCache.get(costStage)) if (!isUserBuilt(v)) count++;
        } else if (state == 4) {
            for (int index = repairTargets.nextSetBit(0); index >= 0; index = repairTargets.nextSetBit(index + 1))
                if (index < voxels.size() && voxels.get(index).stage == stage && !isUserBuilt(voxels.get(index)))
                    count++;
        }
        return count == 0 ? new ItemStack[0] : CriticalConstructionCosts.forStage(config, costStage, count);
    }

    public long getEnergy() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
                ? machine.availableCriticalEnergy()
                : energy;
    }

    public CriticalConfiguration getConfiguration() {
        return config;
    }

    public long getEnergyStored() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
                ? machine.availableCriticalEnergy()
                : energy;
    }

    public long getEnergyCapacity() {
        return capacity();
    }

    public int getJobProgress() {
        return cursor;
    }

    public int getJobTotal() {
        if (worldObj == null) return 0;
        if (worldObj.isRemote) return clientJobTotal;
        if (state == 3) return allVoxels().size();
        if (constructionLedger.isPhysicalJob()) return currentPaidStage().size();
        if (state == 4 && repairStages != null && stage < repairStages.size()) return repairStages.get(stage)
            .size();
        allVoxels();
        return stage < stageCache.size() ? stageCache.get(stage)
            .size() : 0;
    }

    public int getBatchProgress() {
        return cycleTicks;
    }

    public int getBatchDuration() {
        return cycleDuration;
    }

    public int getStatusCode() {
        return state;
    }

    /** Stable presentation key, independent of the server language and persisted legacy status text. */
    public String getDisplayStatusKey() {
        int reason = getReasonCode();
        if (reason != 0) return "reason." + reason;
        if (state == 2) return isWorking() ? "ui.state.working" : "ui.state.ready";
        return "status." + state;
    }

    public static final String[] STATUS_KEYS = { "unbuilt", "building", "ready", "dismantling", "repairing" };

    public int getReasonCode() {
        if (worldObj != null && worldObj.isRemote) return clientReason;
        if (status.contains("缺电")) return 1;
        if (status.contains("缺少材料") || status.startsWith("本批") && (status.contains("不足") || status.contains("缺少")))
            return 2;
        if (status.contains("加载")) return 3;
        if (status.contains("缺损")) return 4;
        if (status.contains("禁止降低等级")) return 9;
        if (status.contains("空间") || status.contains("容量") || status.contains("锚点") || status.contains("施工高度"))
            return 5;
        if (status.contains("冲突") || status.contains("身份") || status.contains("锁")) return 6;
        if (!isEnabled()) return 7;
        if (status.contains("先启动")) return 8;
        return 0;
    }

    public boolean isEnabled() {
        return state == 2 ? isProductionEnabled() : isConstructionEnabled();
    }

    /** The fabrication loom's native switch controls construction, repair and dismantling only. */
    public boolean isConstructionEnabled() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt ? gt.isAllowedToWork()
            : enabled;
    }

    /** The linked machine's real GT switch is authoritative for completed-machine operation. */
    public boolean isProductionEnabled() {
        if (host == null || worldObj != null && worldObj.isRemote) return enabled;
        gregtech.api.interfaces.tileentity.IGregTechTileEntity controller = productionController();
        return controller != null && controller.isAllowedToWork();
    }

    private gregtech.api.interfaces.tileentity.IGregTechTileEntity productionController() {
        if (!(host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt)) return null;
        if (getGeometryVersion() != 2 || !loomController) return gt;
        int x = blockWorldX(56, 35), z = blockWorldZ(56, 35);
        if (!worldObj.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return null;
        if (worldObj.getTileEntity(x, yCoord, z) instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity main
            && main.getFrontFacing() == getControllerFacing(false)
            && main.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
            && !machine.isKindSelector()
            && machine.getPresetKind() == config.kind
            && machine.getCriticalState() == this) return main;
        return null;
    }

    /** Presentation only; production records its blocker without repeating recipe selection for every GUI read. */
    public String getRuntimeBlockerKey() {
        if (state != 2) return "";
        if (host != null && productionController() == null) return "runtime.main_unavailable";
        if (!isProductionEnabled()) return "runtime.power_off";
        if (!runtimeBlockerKey.isEmpty()) return runtimeBlockerKey;
        int reason = getReasonCode();
        if (reason != 0) return "reason." + reason;
        if (config.kind == CriticalMachineKind.SUN) {
            if (!CriticalStellarRuntime.supported(stellarRuntime) || stellarRuntime.getBoolean("uncertain")
                || stellarRuntime.getBoolean("identityMismatch")
                || stellarRuntime.getBoolean("itemCopy")) return "runtime.uncertain";
            if (status.equals("恒星尚未就绪，等待点火或持续燃料与电力")) return "runtime.sun.igniting";
            if (status.equals("加工已暂停，恒星持续消耗氦三与电力")) return "runtime.sun.production_paused";
            if (status.equals("没有完整可执行批次")) return "runtime.sun.waiting_recipe";
        }
        if (isWorking() || status.equals("建造完成") || status.equals("批次交付完成")) return "";
        return "runtime.blocked";
    }

    public String getRuntimeStatusKey() {
        if (state != 2) return state == 0 ? "runtime.not_built" : "status." + state;
        String blocker = getRuntimeBlockerKey();
        return !blocker.isEmpty() ? blocker : isWorking() ? "runtime.working" : "runtime.ready";
    }

    public boolean isWorking() {
        if (worldObj != null && worldObj.isRemote)
            return clientWorking && (state == 1 || state == 4 || structureValid) && isEnabled();
        if ((state == 1 || state == 4) && constructionTimeVersion > 0) return constructionAdvancing && isEnabled();
        if (config.kind == CriticalMachineKind.SUN && batchRecipe.getInteger("billingVersion") > 0)
            return state == 2 && structureValid
                && isEnabled()
                && cycleDuration > 0
                && cycleTicks < cycleDuration
                && worldObj != null
                && stellarRuntime.hasKey("processedTick")
                && stellarRuntime.getLong("processedTick") == worldObj.getTotalWorldTime();
        return state == 2 && structureValid
            && cycleDuration > 0
            && cycleTicks < cycleDuration
            && isEnabled()
            && getEnergy() >= batchEu
            && getReasonCode() == 0;
    }

    public String getStructureId() {
        return structureId;
    }

    public double getVisualConstructionTicks(float partialTicks) {
        return getVisualCycleTicks(partialTicks);
    }

    public int getConstructionDuration() {
        return getConstructionDurationTicks();
    }

    public boolean isConstructionWorking() {
        return (state == 1 || state == 4) && isWorking();
    }

    public String getConstructionVisualMode() {
        return state == 3 ? "destroy" : state == 1 || state == 4 ? "build" : "idle";
    }

    public double getVisualCycleTicks(float partialTicks) {
        double elapsed = worldObj != null && worldObj.isRemote && isWorking()
            ? Math.max(0L, worldObj.getTotalWorldTime() - clientCycleAnchor) + partialTicks
            : 0D;
        return state == 1 || state == 4
            ? Math.min(getConstructionDurationTicks(), getConstructionElapsedTicks() + elapsed)
            : Math.min(cycleDuration, Math.max(0D, cycleTicks + elapsed));
    }

    public int getCycleTicks() {
        return cycleTicks;
    }

    public int getCycleDuration() {
        return cycleDuration;
    }

    public int getOriginX() {
        int max = getRegionSize() - 1;
        return Math.min(blockWorldX(0, 0), blockWorldX(max, max));
    }

    public int getOriginY() {
        return yCoord - getControllerY();
    }

    public int getControllerZ() {
        return CriticalLayoutDefinition.forState(this)
            .controllerZ(loomController);
    }

    public int getOriginZ() {
        int max = getRegionSize() - 1;
        return Math.min(blockWorldZ(0, 0), blockWorldZ(max, max));
    }

    /** Ownership/loom contract independent of distance and any current GUI session. */
    public boolean permitsTerminalAccess(EntityPlayer p) {
        return !loomOwnedMain && p != null
            && !p.isDead
            && (owner.isEmpty() || owner.equals(
                p.getUniqueID()
                    .toString()));
    }

    public boolean canUse(EntityPlayer p) {
        return permitsTerminalAccess(p) && (isUseableByPlayer(p)
            || com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.hasRemoteAccess(p, host));
    }

    public boolean configure(EntityPlayer p, CriticalTier tier, CriticalMachineKind kind, int a, int b, int c) {
        if (worldObj.isRemote || !canUse(p) || state != 0) return false;
        if (getGeometryVersion() != 2 || getV2LayoutRevision() < 31 || !isLoomJournalOwner())
            return fail("新工程需要当前版拟态织机；旧工程仍可按原账完成或拆除");
        if (kind == null || !kind.isReleased()) return fail("该临界机型维护冻结，暂不可配置");
        if (host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
            && machine.getPresetKind() != null
            && kind != machine.getPresetKind()) return false;
        try {
            CriticalConfiguration next = new CriticalConfiguration(tier, kind, a, b, c);
            long nextCapacity = next.tier.recipeVoltage() * 4096L;
            if (getBufferedEnergy() > nextCapacity || pendingGenerated > nextCapacity - getBufferedEnergy()) {
                return fail("储能容量不足，禁止降低等级");
            }
            config = next;
            if (geometryVersion == 2 && !supportsV2Kind(kind)) geometryVersion = 1;
        } catch (IllegalArgumentException e) {
            return false;
        }
        owner = p.getUniqueID()
            .toString();
        geometryCache = null;
        hatchPortsCache = null;
        loomChunksCache = loomValidationChunksCache = null;
        clearanceTargets = null;
        clearanceCursor = 0;
        clearPendingClearance();
        changed();
        return true;
    }

    public boolean startConstruction(EntityPlayer p) {
        if (worldObj.isRemote || !canUse(p) || state != 0) return false;
        if (CriticalStellarRuntime.hasAssets(stellarRuntime) || CriticalEnergyRuntime.hasChemicalEscrow(energyRuntime))
            return fail("上一工程仍有已付资源待结清");
        if (!config.kind.isReleased()) return fail("该临界机型维护冻结，暂不可施工");
        // An explicit build request also resumes a controller stopped while its loom was incomplete.
        setEnabled(p, true);
        if (!ensureConstructionWindow(Collections.emptyList(), 0, 0)) {
            pendingConstructionPlayer = p.getUniqueID()
                .toString();
            pendingConstructionDeadline = worldObj.getTotalWorldTime() + 200;
            return fail("施工区块加载等待");
        }
        pendingConstructionPlayer = "";
        if (!(clearanceTargets == null ? forceLoomStructureCheck() : isLoomStructureValid())) {
            chunkLoading.release(host);
            return fail("必要织机结构缺损，请先手搭完整织机及仓口");
        }
        if (getOriginY() < 0 || getOriginY() + getRegionHeight() > worldObj.getHeight()) return fail("施工高度超出世界");
        int anchorX = CriticalRenderAnchors.getX(this), anchorY = CriticalRenderAnchors.getY(this),
            anchorZ = CriticalRenderAnchors.getZ(this);
        TileEntity visual = worldObj.getTileEntity(anchorX, anchorY, anchorZ);
        if (!worldObj.isAirBlock(anchorX, anchorY, anchorZ)
            && !(visual instanceof TileEntityCriticalRenderAnchor anchor && anchor.ownedBy(this)))
            return fail("织机前上方渲染锚点位置存在外来方块");
        if (!initialClearanceAccepted && !scanInitialClearance(p)) return false;
        List<CriticalGeometry.Voxel> paid = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : allVoxels()) if (!isUserBuilt(voxel)) paid.add(voxel);
        CriticalConstructionEconomy newEconomy;
        try {
            newEconomy = CriticalConstructionEconomy.freeze(
                config,
                paid,
                CriticalLayoutDefinition.forState(this)
                    .cacheKey());
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            return fail("施工物料目录不完整，尚未收费：" + unavailable.getMessage());
        }
        owner = p.getUniqueID()
            .toString();
        String acceptedId = structureId.isEmpty() ? UUID.randomUUID()
            .toString() : structureId;
        if (!data().acquire(
            acceptedId,
            owner,
            getOriginX(),
            getOriginY(),
            getOriginZ(),
            getRegionSize(),
            getRegionHeight(),
            xCoord,
            yCoord,
            zCoord)) return fail("施工区与已登记区域重叠");
        structureId = acceptedId;
        int plasmaHalfRemainder = stellarRuntime.getInteger("plasmaHalfRemainder");
        stellarRuntime = new NBTTagCompound();
        stellarRuntime.setInteger("plasmaHalfRemainder", plasmaHalfRemainder);
        initialClearanceAccepted = true;
        structureFacing = getStructureFacing();
        structureFacingLocked = true;
        CriticalRuntime.unregisterClearance(this);
        clearanceTargets = null;
        jobId = UUID.randomUUID()
            .toString();
        bindTask();
        recoverySeed = new Random().nextLong();
        jobFingerprint = modelFingerprint;
        constructionLedger.freezeJob(newEconomy);
        rebuildConstructionStages(geometryCache);
        state = 1;
        foundationRequired = isLoomJournalOwner();
        foundationModern = newEconomy != null && newEconomy.version() >= 5;
        foundationScheduleVersion = foundationModern ? CriticalFoundationChunk.VERSION : 0;
        foundationCuringCheckTicks = 0;
        constructionTimeVersion = 1;
        resetConstructionStage();
        stage = newEconomy != null && newEconomy.version() >= 5 ? 4 : 0;
        cursor = 0;
        stageFunded = false;
        refundPrepared = false;
        status = "施工中";
        changed();
        return true;
    }

    /** Sparse, windowed first-start scan; no payment or region registration exists during this phase. */
    private boolean scanInitialClearance(EntityPlayer player) {
        if (clearanceTargets == null) {
            clearanceTargets = new ArrayList<>();
            clearanceTargetPositions.clear();
            for (CriticalGeometry.Voxel v : allVoxels())
                if (getGeometryVersion() == 2 && isLoomJournalOwner() ? !CriticalLoomStructure.isLoomVoxel(v)
                    : !isUserBuilt(v)) {
                        clearanceTargets.add(v);
                        clearanceTargetPositions.add(v.positionKey());
                    }
            for (CriticalGeometry.Voxel port : CriticalGeometry.optionalWorkPorts(this)) {
                clearanceTargets.add(port);
                clearanceTargetPositions.add(port.positionKey());
            }
            clearanceCursor = 0;
            clearanceRechecks.clear();
            CriticalRuntime.registerClearance(this);
        }
        int end = loadedWindowEnd(clearanceTargets, clearanceCursor, clearanceTargets.size());
        if (end < 0) return waitForClearance(player, "等待检查区块 " + clearanceCursor + "/" + clearanceTargets.size());
        for (; clearanceCursor < end; clearanceCursor++) {
            CriticalGeometry.Voxel v = clearanceTargets.get(clearanceCursor);
            if (!worldObj.canMineBlock(player, wx(v), wy(v), wz(v))) return clearanceFailed("没有施工权限");
            if (!isClearForConstruction(v)) return clearanceFailed(
                "目标位置存在方块：" + worldObj.getBlock(wx(v), wy(v), wz(v))
                    .getLocalizedName() + "，世界(" + wx(v) + "," + wy(v) + "," + wz(v) + ")");
        }
        if (clearanceCursor < clearanceTargets.size())
            return waitForClearance(player, "首次施工占位检查 " + clearanceCursor + "/" + clearanceTargets.size());
        if (!areaLoaded()) return waitForClearance(player, "等待检查区块全部加载");
        if (!forceLoomStructureCheck()) return clearanceFailed("必要织机结构缺损，请先手搭完整织机及仓口");
        // Changes signaled during the scan must be read again in the accepting server tick.
        for (CriticalGeometry.Voxel v : clearanceTargets) if (clearanceRechecks.contains(v.positionKey())) {
            if (!isClearForConstruction(v))
                return clearanceFailed("目标位置存在方块，世界(" + wx(v) + "," + wy(v) + "," + wz(v) + ")");
            if (!worldObj.canMineBlock(player, wx(v), wy(v), wz(v))) return clearanceFailed("没有施工权限");
        }
        if (getGeometryVersion() == 2) {
            for (com.google.gson.JsonElement entry : CriticalGeometry
                .getModel(CriticalGeometry.visualLoomSlug(this)).meta.getAsJsonArray("operatingClearances")) {
                com.google.gson.JsonObject port = entry.getAsJsonObject();
                com.google.gson.JsonArray pos = port.getAsJsonArray("standing");
                for (int dy = 0; dy < port.get("headroomCells")
                    .getAsInt(); dy++) {
                    int lx = pos.get(0)
                        .getAsInt(),
                        lz = pos.get(2)
                            .getAsInt();
                    int x = blockWorldX(lx, lz), y = getOriginY() + pos.get(1)
                        .getAsInt() + dy, z = blockWorldZ(lx, lz);
                    if (!worldObj.isAirBlock(x, y, z))
                        return clearanceFailed("通道净空被占用，世界(" + x + "," + y + "," + z + ")");
                }
            }
        }
        java.util.Set<Long> targets = playerClearanceTargets();
        if (worldObj.isAirBlock(
            CriticalRenderAnchors.getX(this),
            CriticalRenderAnchors.getY(this),
            CriticalRenderAnchors.getZ(this)))
            targets.add(
                CriticalGeometry.Voxel.positionKey(
                    localBlockX(CriticalRenderAnchors.getX(this), CriticalRenderAnchors.getZ(this)),
                    CriticalRenderAnchors.getY(this) - getOriginY(),
                    localBlockZ(CriticalRenderAnchors.getX(this), CriticalRenderAnchors.getZ(this))));
        for (Object object : worldObj.playerEntities) if (object instanceof EntityPlayer p && p.boundingBox != null) {
            AxisAlignedBB box = p.boundingBox;
            for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++)
                for (int y = (int) Math.floor(box.minY); y < Math.ceil(box.maxY); y++)
                    for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                        int rx = localBlockX(x, z), ry = y - getOriginY(), rz = localBlockZ(x, z);
                        if (targets.contains(CriticalGeometry.Voxel.positionKey(rx, ry, rz)) && box.maxX > x
                            && box.minX < x + 1
                            && box.maxY > y
                            && box.minY < y + 1
                            && box.maxZ > z
                            && box.minZ < z + 1)
                            return clearanceFailed(
                                "玩家占用施工方块：相对(" + rx + "," + ry + "," + rz + ")，世界(" + x + "," + y + "," + z + ")");
                    }
        }
        pendingConstructionPlayer = "";
        return true;
    }

    /** Empty optional chambers are checked for foreign blocks but no paid block will occupy a player there. */
    private java.util.Set<Long> playerClearanceTargets() {
        java.util.Set<Long> targets = new java.util.HashSet<>(clearanceTargetPositions);
        for (CriticalGeometry.Voxel port : CriticalGeometry.optionalWorkPorts(this)) targets.remove(port.positionKey());
        return targets;
    }

    private boolean isClearForConstruction(CriticalGeometry.Voxel voxel) {
        return worldObj.isAirBlock(wx(voxel), wy(voxel), wz(voxel)) || getGeometryVersion() == 2 && loomJournalOwner
            && CriticalStructures.isPaidChamberPosition(voxel)
            && isStandardHatch(voxel);
    }

    private boolean waitForClearance(EntityPlayer player, String reason) {
        pendingConstructionPlayer = player.getUniqueID()
            .toString();
        pendingConstructionDeadline = worldObj.getTotalWorldTime() + 6000;
        return fail(reason);
    }

    private boolean clearanceFailed(String reason) {
        pendingConstructionPlayer = "";
        clearanceTargets = null;
        clearanceCursor = 0;
        clearanceRechecks.clear();
        clearanceTargetPositions.clear();
        CriticalRuntime.unregisterClearance(this);
        chunkLoading.release(host);
        return fail(reason);
    }

    public boolean startDismantle(EntityPlayer p) {
        if (worldObj.isRemote || !canUse(p) || nativeFundingUncertain || (state != 1 && state != 2 && state != 4))
            return false;
        allVoxels();
        if (jobFingerprint != modelFingerprint) return fail("结构定义与任务快照不一致");
        if (!ownsTask()) return fail("施工区任务身份不一致，请用修复按钮重核账本");
        if (energyRuntime.getBoolean("uncertain")) return fail("批次提交结果不确定，请先核验当前批次");
        if (stellarRuntime.getBoolean("uncertain")) return fail("恒星支付结果不确定，请先核验");
        if (canCancelLegacyStellarBatch()) cancelLegacyStellarBatch(p);
        if (hasActiveBatch() || pendingGenerated > 0) {
            if (!(state == 2 ? setProductionEnabled(p, true) : setEnabled(p, true))) return false;
            dismantleRequested = true;
            status = "已预约拆除，等待当前批次交付";
            changed();
            return true;
        }
        boolean started = beginDismantle();
        setEnabled(p, true);
        return started || dismantleRequested;
    }

    private boolean hasActiveBatch() {
        return !batchId.isEmpty() || energyRuntime.getBoolean("funded");
    }

    private boolean beginDismantle() {
        if (config.kind == CriticalMachineKind.SUN && gtMachine() != null) {
            CriticalStellarRuntime.shutdown(stellarRuntime, gtMachine().criticalStellarPowerPort());
            if (CriticalStellarRuntime.hasAssets(stellarRuntime)) {
                dismantleRequested = true;
                fail(stellarRuntime.getBoolean("uncertain") ? "恒星支付结果待核验" : "拆除已预约：先从工作仓发电口退回未用电量，请接收电能");
                changed();
                return false;
            }
        }
        if (state == 1 && stageFunded || state == 4 && repairFunded) {
            boolean repairing = state == 4;
            int total = repairing ? 0 : getJobTotal();
            if (repairing) {
                List<CriticalGeometry.Voxel> voxels = allVoxels();
                for (int index = repairTargets.nextSetBit(0); index >= 0
                    && index < voxels.size(); index = repairTargets.nextSetBit(index + 1))
                    if (voxels.get(index).stage == stage) total++;
            }
            List<ItemStack> unconsumed = new ArrayList<>();
            if (repairing) Collections.addAll(unconsumed, escrow);
            for (ItemStack stack : repairing ? repairEscrow : escrow) {
                int consumed = total == 0 ? 0 : (int) (((long) stack.stackSize * cursor + total - 1) / total);
                if (consumed > 0) {
                    ItemStack used = stack.copy();
                    used.stackSize = consumed;
                    constructionLedger.recordLegacyItem(used);
                }
                if (consumed < stack.stackSize) {
                    ItemStack left = stack.copy();
                    left.stackSize -= consumed;
                    unconsumed.add(left);
                }
            }
            escrow = unconsumed.toArray(new ItemStack[0]);
            stageFunded = false;
            repairFunded = false;
            repairEscrow = new ItemStack[0];
        }
        dismantleRequested = false;
        state = 3;
        invalidateStructure();
        CriticalRenderAnchors.ensure(this, isLoomGeometryValid());
        cursor = 0;
        jobId = UUID.randomUUID()
            .toString();
        bindTask();
        scanCursor = 0;
        removalVerified = false;
        data().releaseNight(structureId);
        status = "拆除中";
        changed();
        return true;
    }

    public boolean repairStructure(EntityPlayer p) {
        if (worldObj.isRemote || !canUse(p) || nativeFundingUncertain || state == 0) return false;
        if (!forceLoomStructureCheck()) return fail("必要织机结构缺损，请手动修复织机");
        allVoxels();
        if (jobFingerprint != modelFingerprint) return fail("结构定义与任务快照不一致");
        if (!ownsTask() && !recoverTask(p)) return false;
        if (!matchesOptionalWorkPorts()) return fail("工作仓口被外来方块占用，请手动维护仓室");
        if (state == 3) {
            if (!reconcileDismantle()) return false;
            return setEnabled(p, true);
        }
        if (state != 2) {
            setEnabled(p, true);
            status = "任务账本重核完成";
            changed();
            return true;
        }
        for (CriticalGeometry.Voxel voxel : allVoxels())
            if (!isUserBuilt(voxel) && !worldObj.canMineBlock(p, wx(voxel), wy(voxel), wz(voxel)))
                return fail("没有修复权限");
        repairTargets.clear();
        repairScanPending = true;
        repairScanCursor = 0;
        repairStages = null;
        state = 4;
        completionValidationPending = false;
        constructionTimeVersion = 1;
        resetConstructionStage();
        stage = 0;
        cursor = 0;
        repairFunded = false;
        jobId = UUID.randomUUID()
            .toString();
        bindTask();
        invalidateStructure();
        data().releaseNight(structureId);
        setEnabled(p, true);
        changed();
        return true;
    }

    public boolean setEnabled(EntityPlayer p, boolean value) {
        if (worldObj.isRemote || !canUse(p)) return false;
        enabled = value;
        if (host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt) {
            if (value) gt.enableWorking();
            else gt.disableWorking();
        }
        if (!value && state != 2) data().releaseNight(structureId);
        changed();
        return true;
    }

    public boolean setProductionEnabled(EntityPlayer p, boolean value) {
        if (worldObj.isRemote || !canUse(p)) return false;
        gregtech.api.interfaces.tileentity.IGregTechTileEntity controller = productionController();
        if (controller == null) return fail("主机器控制器缺失、朝向不符或区块未加载");
        if (value) controller.enableWorking();
        else {
            controller.disableWorking();
            data().releaseNight(structureId);
        }
        changed();
        return true;
    }

    public boolean failRuntime(String key, String reason) {
        fail(reason);
        runtimeBlockerKey = key;
        return false;
    }

    public boolean fail(String reason) {
        runtimeBlockerKey = "";
        if (!status.equals(reason)) {
            status = reason;
            changed();
        }
        return false;
    }

    private CriticalWorldData data() {
        return CriticalWorldData.get(worldObj);
    }

    private boolean ownsRegion() {
        return !structureId.isEmpty() && data().owns(structureId, getOriginX(), getOriginY(), getOriginZ());
    }

    private boolean ownsTask() {
        return !structureId.isEmpty()
            && data().ownsTask(structureId, owner, jobId, getOriginX(), getOriginY(), getOriginZ());
    }

    private boolean bindTask() {
        protectionDirty = true;
        return data().bindTask(structureId, owner, jobId, getOriginX(), getOriginY(), getOriginZ());
    }

    private boolean recoverTask(EntityPlayer player) {
        // Explicit owner action reconciles two save files. It never changes escrow or advances either cursor.
        if (!canUse(player) || worldObj.getTileEntity(xCoord, yCoord, zCoord) != host || jobId.isEmpty()) return false;
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        for (int index = placed.nextSetBit(0); index >= 0; index = placed.nextSetBit(index + 1)) {
            if (index >= voxels.size()) return fail("任务位置账本越界");
            CriticalGeometry.Voxel voxel = voxels.get(index);
            boolean air = worldObj.isAirBlock(wx(voxel), wy(voxel), wz(voxel));
            if (state == 3 ? !air && !matches(voxel) : !matches(voxel) && !(air && (state == 2 || state == 4))) {
                return fail("任务账本重核发现方块冲突，禁止恢复");
            }
        }
        if (!data().acquire(
            structureId,
            owner,
            getOriginX(),
            getOriginY(),
            getOriginZ(),
            getRegionSize(),
            getRegionHeight(),
            xCoord,
            yCoord,
            zCoord) || !bindTask()) {
            return fail("任务账本重核区域归属冲突");
        }
        invalidateStructure();
        changed();
        return true;
    }

    /** Replays only physically retained blocks from this job's persistent placement ledger. */
    private boolean reconcileDismantle() {
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        int rewind = cursor;
        for (int index = 0; index < voxels.size(); index++) {
            CriticalGeometry.Voxel voxel = voxels.get(index);
            if (isUserBuilt(voxel) || !placed.get(index)) continue;
            if (worldObj.isAirBlock(wx(voxel), wy(voxel), wz(voxel))) continue;
            if (!matches(voxel)) return fail("拆除账本重核发现外来方块，禁止恢复");
            rewind = Math.min(rewind, voxels.size() - 1 - index);
        }
        cursor = rewind;
        protectionDirty = true;
        scanCursor = 0;
        removalVerified = false;
        status = "拆除账本重核完成";
        changed();
        return true;
    }

    private void changed() {
        markDirty();
        CriticalRenderAnchors.ensure(this, isLoomGeometryValid());
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public boolean canRemove() {
        if (state != 0 || !structureId.isEmpty()
            || CriticalStellarRuntime.hasAssets(stellarRuntime)
            || CriticalEnergyRuntime.hasChemicalEscrow(energyRuntime)
            || energyRuntime.getBoolean("funded")
            || energyRuntime.getBoolean("uncertain")
            || !batchId.isEmpty()
            || escrow.length != 0
            || pendingItems.length != 0
            || pendingFluids.length != 0
            || pendingGenerated != 0) return false;
        for (ItemStack item : inventory) if (item != null) return false;
        for (FluidStack fluid : tanks) if (fluid != null) return false;
        return true;
    }

    /** A copied passive main has no journal to cancel. Only its authorized owner may remove an empty copy. */
    public boolean canRemoveOrphanedMain(EntityPlayer player) {
        return loomOwnedMain && hasNoJournalAssets()
            && player != null
            && isUseableByPlayer(player)
            && (owner.isEmpty() || owner.equals(
                player.getUniqueID()
                    .toString()));
    }

    private boolean hasNoJournalAssets() {
        return canRemove() && jobId.isEmpty()
            && jobFingerprint == 0L
            && placed.isEmpty()
            && !constructionLedger.hasSpentItems()
            && repairEscrow.length == 0
            && !nativeFundingUncertain;
    }

    public void onChunkUnload() {
        invalidateStructure();
        structureLoadTicks = 0;
        lastStructureLoadingTick = Long.MIN_VALUE;
        // Follow GT drillers: Forge persists/unloads tickets, then GT clears owners on server stop.
        // Releasing here runs after Forge's HIGHEST world-unload handler has removed its ticket map.
        if (worldObj != null && !worldObj.isRemote) {
            clearPendingClearance();
            data().releaseNight(structureId);
        }

    }

    public void invalidate() {
        invalidateStructure();
        clearPendingClearance();
        CriticalRenderAnchors.remove(this);
        chunkLoading.release(host);
        if (worldObj != null && !worldObj.isRemote) data().releaseNight(structureId);

    }

    private void clearPendingClearance() {
        if (worldObj != null && worldObj.isRemote) return;
        CriticalRuntime.unregisterClearance(this);
        pendingConstructionPlayer = "";
        clearanceTargets = null;
        clearanceCursor = 0;
        clearanceRechecks.clear();
        clearanceTargetPositions.clear();
    }

    private int wx(CriticalGeometry.Voxel v) {
        return blockWorldX(v);
    }

    private int wy(CriticalGeometry.Voxel v) {
        return getOriginY() + v.y;
    }

    private int wz(CriticalGeometry.Voxel v) {
        return blockWorldZ(v);
    }

    private List<CriticalGeometry.Voxel> allVoxels() {
        if (geometryCache == null) {
            List<CriticalGeometry.Voxel> result = CriticalStructures.voxels(this);
            geometryCache = Collections.unmodifiableList(result);
            stageCache.clear();
            paidStageCache.clear();
            constructionPreview = null;
            voxelIndices.clear();
            protectionIndices.clear();
            modelFingerprint = 0xcbf29ce484222325L;
            for (int index = 0; index < result.size(); index++) {
                CriticalGeometry.Voxel v = result.get(index);
                voxelIndices.put(v, index);
                if (!CriticalLoomStructure.isLoomVoxel(v)) protectionIndices.put(v.positionKey(), index);
                modelFingerprint = (modelFingerprint ^ v.positionKey()) * 0x100000001b3L;
                modelFingerprint = (modelFingerprint ^ v.material.hashCode() ^ v.group.hashCode() ^ v.stage)
                    * 0x100000001b3L;
            }
            rebuildConstructionStages(result);
        }
        return geometryCache;
    }

    private int constructionStage(CriticalGeometry.Voxel voxel) {
        if (isUserBuilt(voxel)) return 0;
        return constructionLedger.isPhysicalJob() ? constructionLedger.physicalJob()
            .stage(voxel) : voxel.stage;
    }

    private void rebuildConstructionStages(List<CriticalGeometry.Voxel> voxels) {
        stageCache.clear();
        paidStageCache.clear();
        for (CriticalGeometry.Voxel voxel : voxels) {
            int workStage = constructionStage(voxel);
            while (stageCache.size() <= workStage) stageCache.add(new ArrayList<>());
            stageCache.get(workStage)
                .add(voxel);
        }
    }

    /** GT casing is a fabrication host even while the commissioned structure is unfinished. */
    public boolean isFabricationHostValid() {
        if (host == null || worldObj == null || worldObj.getTileEntity(xCoord, yCoord, zCoord) != host) return false;
        if (!isLoomStructureValid()) return false;
        if (state == 0) return structureId.isEmpty() && jobId.isEmpty();
        allVoxels();
        return ownsTask() && jobFingerprint == modelFingerprint;
    }

    public void invalidateStructure() {
        structureValid = false;
        structureMonitor.reset();
        integrityOptionalPorts = null;
        removalVerified = false;
    }

    /** All physical alloy frames in the loom and target are shared GT hatch positions. */
    public List<CriticalGeometry.Voxel> getHatchPorts() {
        if (hatchPortsCache == null) {
            allVoxels();
            hatchPortsCache = CriticalCompiledLayout.forState(this)
                .hatchPorts();
        }
        return hatchPortsCache;
    }

    /** The manual loom funds fabrication; only the commissioned main's chamber row serves recipes. */
    public List<CriticalGeometry.Voxel> getResourceHatchPorts() {
        if (getGeometryVersion() != 2 || !loomJournalOwner) return getHatchPorts();
        List<CriticalGeometry.Voxel> result = new ArrayList<>();
        for (CriticalGeometry.Voxel port : getHatchPorts()) {
            if (state == 2 ? CriticalStructures.isPaidChamberPosition(port) : CriticalLoomStructure.isLoomVoxel(port))
                result.add(port);
            else if (config.kind == CriticalMachineKind.SUN && CriticalStructures.isPaidChamberPosition(port)
                && (stellarRuntime.getLong("refund") > 0 || dismantleRequested)
                && worldObj != null
                && worldObj.blockExists(wx(port), wy(port), wz(port))
                && worldObj.getTileEntity(
                    wx(port),
                    wy(port),
                    wz(port)) instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
                && gt.getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEHatchDynamo)
                result.add(port);
        }
        return result;
    }

    public boolean isStandardHatch(CriticalGeometry.Voxel v) {
        if (!CriticalStructures.isHatchPosition(this, v)) return false;
        if (worldObj == null || !worldObj.getChunkProvider()
            .chunkExists(wx(v) >> 4, wz(v) >> 4)) return false;
        TileEntity tile = worldObj.getTileEntity(wx(v), wy(v), wz(v));
        return CriticalLoomStructure.isLoomVoxel(v) ? isLoomHatchHost(tile) : isStandardHatchHost(tile);
    }

    public static boolean isStandardHatchHost(TileEntity tile) {
        return tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && isSupportedHatchMeta(gt.getMetaTileEntity());
    }

    public static boolean isLoomHatchHost(TileEntity tile) {
        return tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && isSupportedLoomHatchMeta(gt.getMetaTileEntity());
    }

    public static boolean isSupportedLoomHatchMeta(gregtech.api.interfaces.metatileentity.IMetaTileEntity mte) {
        return isSupportedHatchMeta(mte)
            && !(mte instanceof gregtech.api.metatileentity.implementations.MTEHatchDynamo);
    }

    /** Physical and native ME stock inputs are supported; dual crafting buffers are excluded. */
    public static boolean isSupportedHatchMeta(gregtech.api.interfaces.metatileentity.IMetaTileEntity mte) {
        if (mte instanceof gregtech.api.metatileentity.implementations.MTEHatchInputBus)
            return !(mte instanceof gregtech.common.tileentities.machines.IDualInputHatch);
        if (mte instanceof gregtech.api.metatileentity.implementations.MTEHatchInput) return true;
        return mte instanceof gregtech.api.metatileentity.implementations.MTEHatchDataAccess
            || mte instanceof gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing
            || mte instanceof gregtech.api.metatileentity.implementations.MTEHatchOutputBus
            || mte instanceof gregtech.api.metatileentity.implementations.MTEHatchOutput
            || isEnergyInputHatchMeta(mte)
            || mte instanceof gregtech.api.metatileentity.implementations.MTEHatchDynamo;
    }

    public static boolean isEnergyInputHatchMeta(gregtech.api.interfaces.metatileentity.IMetaTileEntity mte) {
        return mte instanceof gregtech.api.metatileentity.implementations.MTEHatchEnergy
            || mte instanceof tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti multi
                && (multi.getHatchType() == 1 || multi.getHatchType() == 2);
    }

    /** Minimal persistent claim snapshot: no inventories, escrow, energy or live tile references. */
    NBTTagCompound protectionSnapshot() {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("geometryVersion", getGeometryVersion());
        n.setInteger("v2LayoutRevision", v2LayoutRevision);
        n.setInteger("structureFacing", getStructureFacing().ordinal());
        n.setBoolean("loomController", loomController);
        n.setBoolean("loomJournalOwner", loomJournalOwner);
        n.setTag("config", config.write());
        n.setString("structureId", structureId);
        n.setInteger("state", state);
        n.setInteger("cursor", cursor);
        n.setLong("fingerprint", jobFingerprint);
        n.setByteArray("placed", placed.toByteArray());
        return n;
    }

    void readProtectionSnapshot(NBTTagCompound n) {
        readVisualData(n);
        jobFingerprint = n.getLong("fingerprint");
        placed.or(BitSet.valueOf(n.getByteArray("placed")));
    }

    /** Only a paid cell recorded by this journal is protected; real GT chambers remain maintainable. */
    boolean protectsPlayerBreak(int x, int y, int z) {
        List<CriticalGeometry.Voxel> geometry = allVoxels();
        if (jobFingerprint != modelFingerprint) return false;
        Integer index = protectionIndices
            .get(CriticalGeometry.Voxel.positionKey(localBlockX(x, z), y - getOriginY(), localBlockZ(x, z)));
        if (index == null || !placed.get(index)) return false;
        if (state == 3 && index >= geometry.size() - cursor) return false;
        CriticalGeometry.Voxel voxel = geometry.get(index);
        if (CriticalStructures.isController(voxel)) return matches(voxel);
        return !isStandardHatchHost(worldObj.getTileEntity(x, y, z)) && worldObj.getBlock(x, y, z) == voxel.block
            && worldObj.getBlockMetadata(x, y, z) == voxel.metadata;
    }

    public boolean matchesOptionalWorkPorts() {
        for (CriticalGeometry.Voxel port : CriticalGeometry.optionalWorkPorts(this))
            if (!worldObj.blockExists(wx(port), wy(port), wz(port))
                || !worldObj.isAirBlock(wx(port), wy(port), wz(port)) && !isStandardHatch(port)) return false;
        return true;
    }

    private boolean matches(CriticalGeometry.Voxel v) {
        if (CriticalStructures.isController(v))
            return new CriticalStructures.ControllerElement(v.z == 47, config.kind, getStructureFacing())
                .check(gtMachine(), worldObj, wx(v), wy(v), wz(v));
        if (isStandardHatch(v)) return true;
        if (CriticalLoomStructure.isLoomVoxel(v)) return CriticalLoomStructure.matchesVoxel(this, v);
        return worldObj.getBlock(wx(v), wy(v), wz(v)) == v.block
            && worldObj.getBlockMetadata(wx(v), wy(v), wz(v)) == v.metadata;
    }

    private List<net.minecraft.world.ChunkCoordIntPair> loomChunks() {
        if (loomChunksCache != null) return loomChunksCache;
        java.util.Map<Long, net.minecraft.world.ChunkCoordIntPair> chunks = new java.util.LinkedHashMap<>();
        int edge = getRegionSize() - 1;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : new int[] { 0, edge }) for (int z : new int[] { 0, edge }) {
            int wx = blockWorldX(x, z) >> 4, wz = blockWorldZ(x, z) >> 4;
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minZ = Math.min(minZ, wz);
            maxZ = Math.max(maxZ, wz);
        }
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) addStructureChunk(chunks, x, z);
        addStructureChunk(chunks, xCoord >> 4, zCoord >> 4);
        for (CriticalGeometry.Voxel voxel : allVoxels()) addStructureChunk(chunks, wx(voxel) >> 4, wz(voxel) >> 4);
        loomChunksCache = Collections.unmodifiableList(new ArrayList<>(chunks.values()));
        return loomChunksCache;
    }

    private static void addStructureChunk(java.util.Map<Long, net.minecraft.world.ChunkCoordIntPair> chunks, int x,
        int z) {
        chunks.put(((long) x << 32) ^ (z & 0xffffffffL), new net.minecraft.world.ChunkCoordIntPair(x, z));
    }

    private boolean ensureConstructionWindow(List<CriticalGeometry.Voxel> voxels, int start, int end) {
        return chunkLoading.ensureAllStructureChunks(worldObj, host, loomChunks());
    }

    private int loadedWindowEnd(List<CriticalGeometry.Voxel> voxels, int start, int end) {
        return ensureConstructionWindow(voxels, start, end) ? Math.min(end, start + constructionBlockBudget)
            : start == end ? end : -1;
    }

    /** Canonical coordinates for the native restart callback; never trust a saved arbitrary chunk list. */
    public List<net.minecraft.world.ChunkCoordIntPair> getChunkLoadingFootprint() {
        return loomChunks();
    }

    /** Presence checks only: never ask Minecraft to synchronously load an unknown structure position. */
    public boolean areLoomChunksLoaded() {
        if (worldObj == null) return false;
        if (loomValidationChunksCache == null) {
            java.util.Map<Long, net.minecraft.world.ChunkCoordIntPair> chunks = new java.util.LinkedHashMap<>();
            addStructureChunk(chunks, xCoord >> 4, zCoord >> 4);
            for (CriticalGeometry.Voxel voxel : CriticalLoomStructure.voxels(this))
                addStructureChunk(chunks, wx(voxel) >> 4, wz(voxel) >> 4);
            loomValidationChunksCache = new ArrayList<>(chunks.values());
        }
        for (net.minecraft.world.ChunkCoordIntPair chunk : loomValidationChunksCache) if (!worldObj.getChunkProvider()
            .chunkExists(chunk.chunkXPos, chunk.chunkZPos)) return false;
        return true;
    }

    private boolean areStructureChunksLoaded() {
        for (net.minecraft.world.ChunkCoordIntPair chunk : loomChunks()) if (!worldObj.getChunkProvider()
            .chunkExists(chunk.chunkXPos, chunk.chunkZPos)) return false;
        return true;
    }

    private boolean canRecoverLoomLoading() {
        if (structureLoadTicks >= STRUCTURE_LOAD_GRACE_TICKS || state == 0) return false;
        allVoxels();
        return ownsTask() && jobFingerprint == modelFingerprint;
    }

    public CriticalChunkLoading getChunkLoading() {
        return chunkLoading;
    }

    public boolean isChunkLoadingEnabled() {
        return chunkLoading.isEnabled();
    }

    public void setChunkLoadingEnabled(boolean value) {
        chunkLoading.setEnabled(value);
        markDirty();
    }

    public net.minecraft.world.ChunkCoordIntPair getActiveConstructionChunk() {
        net.minecraft.world.ChunkCoordIntPair chunk = chunkLoading.getActiveChunk();
        return chunk != null ? chunk : new net.minecraft.world.ChunkCoordIntPair(xCoord >> 4, zCoord >> 4);
    }

    private boolean areaLoaded() {
        for (int x = getOriginX() >> 4; x <= (getOriginX() + getRegionSize() - 1) >> 4; x++) {
            for (int z = getOriginZ() >> 4; z <= (getOriginZ() + getRegionSize() - 1) >> 4; z++) {
                if (!worldObj.getChunkProvider()
                    .chunkExists(x, z)) return false;
            }
        }
        return true;
    }

    private void tickStellarSession() {
        if (config.kind != CriticalMachineKind.SUN || gtMachine() == null) return;
        // A restart/full incremental scan has no formed verdict yet. Do not turn its saved star into
        // a shutdown/refund, and never carry a previous tick's payment authorization across the wait.
        if (state == 2 && (!areLoomChunksLoaded() || isLoomGeometryValid())
            && !structureValid
            && !structureMonitor.hasKnownDamage()) {
            stellarRuntime.setBoolean("tickAuthorized", false);
            stellarRuntime.setBoolean("sustainPaidThisTick", false);
            return;
        }
        CriticalStellarRuntime.PowerPort port = gtMachine().criticalStellarPowerPort();
        if (state == 0 || state == 3) {
            CriticalStellarRuntime.settleRefund(stellarRuntime, port);
            return;
        }
        allVoxels();
        CriticalStellarRuntime.tick(
            stellarRuntime,
            port,
            structureId,
            worldObj.getTotalWorldTime(),
            ownsTask() && !nativeFundingUncertain && jobFingerprint == modelFingerprint,
            areaLoaded(),
            isEnabled() && !(dismantleRequested && !hasActiveBatch()),
            state == 2 && structureValid && isLoomStructureValid(),
            !batchId.isEmpty() && batchRecipe.getInteger("billingVersion") == 0);
        if (!batchId.isEmpty() && batchRecipe.getInteger("billingVersion") == 1
            && CriticalStellarRuntime.supported(stellarRuntime)
            && !stellarRuntime.getBoolean("uncertain")) {
            int parallel = batchRecipe.getInteger("parallel");
            long nativeEu = batchRecipe.getLong("nativeEuPerTick");
            if (parallel < 1 || parallel > CriticalStellarPolicy.MAX_PARALLEL
                || !CriticalStellarPolicy.nativeCapability(
                    batchRecipe.getLong("fusionThreshold"),
                    nativeEu,
                    gregtech.api.enums.GTValues.V[10])
                || !batchRecipe.hasKey("nativeEuPerTick")) {
                stellarRuntime.setBoolean("uncertain", true);
            } else {
                batchEu = CriticalStellarPolicy.runEuPerTick(nativeEu, parallel);
                // Freeze the previous 95% policy and saved duration for this already-funded batch.
                batchRecipe.setInteger("billingVersion", 2);
            }
            markDirty();
        }
        if (batchRecipe.getBoolean("cancelRefund") && ownsTask() && !nativeFundingUncertain && areaLoaded()) runBatch();
    }

    public boolean canCancelLegacyStellarBatch() {
        return config.kind == CriticalMachineKind.SUN && !batchId.isEmpty()
            && batchRecipe.getInteger("billingVersion") == 0
            && !batchRecipe.getBoolean("cancelRefund")
            && !nativeFundingUncertain
            && !energyRuntime.getBoolean("uncertain")
            && cycleTicks < cycleDuration
            && batchEu > CriticalTier.T3.recipeVoltage() * 16L;
    }

    /** Only a provably unfinishable, never-delivered legacy batch can become a raw-input refund. */
    public boolean cancelLegacyStellarBatch(EntityPlayer player) {
        if (worldObj.isRemote || !canUse(player) || !ownsTask() || !canCancelLegacyStellarBatch()) return false;
        pendingItems = copyItems(escrow);
        pendingFluids = copyFluids(fluidEscrow);
        batchGenerated = batchEu = 0;
        cycleTicks = cycleDuration;
        batchRecipe.setBoolean("cancelRefund", true);
        status = "旧超限批次已取消，等待返还原料；已消耗电量不退";
        changed();
        return true;
    }

    public void updateEntity() {
        constructionAdvancing = false;
        blocksChangedThisTick = lightsChangedThisTick = 0;
        constructionBlockBudget = 128;
        constructionLightBudget = 16;
        blockBudgetStarted = 0;
        if (worldObj == null || worldObj.isRemote) return;
        CriticalEnergyRuntime.idleTick(this);
        // v2.0.5 could finish teardown with generated EU still in the legacy output journal.
        // Moving already-generated EU into the real GT buffer is safe even with power disabled.
        if (state == 0 && pendingGenerated > 0) {
            if (settleDismantleEnergy()) {
                status = "遗留发电交付完成";
                changed();
            }
        }
        boolean loomLoaded = areLoomChunksLoaded();
        boolean loomGeometry = loomLoaded && isLoomGeometryValid();
        // A saved, identity-checked journal may restore its footprint while its loom chunks arrive.
        // Spend one cumulative budget per load session, only on actual unready world ticks. A ready
        // interval neither consumes nor renews it, and repeated same-tick calls cannot spend it twice.
        long loadingTick = worldObj.getTotalWorldTime();
        if (!areStructureChunksLoaded() && loadingTick != lastStructureLoadingTick) {
            lastStructureLoadingTick = loadingTick;
            if (structureLoadTicks < STRUCTURE_LOAD_GRACE_TICKS) structureLoadTicks++;
        }
        if (loomGeometry || !loomLoaded && canRecoverLoomLoading())
            chunkLoading.ensureAllStructureChunks(worldObj, host, loomChunks());
        else {
            chunkLoading.release(host);
            if (loomLoaded) revokeGeometryValidation();
        }
        tickStructureMonitor();
        tickStellarSession();
        if (state == 0) {
            if (!pendingConstructionPlayer.isEmpty() && isEnabled()
                && worldObj.getTotalWorldTime() < pendingConstructionDeadline) {
                for (Object object : worldObj.playerEntities)
                    if (object instanceof EntityPlayer player && pendingConstructionPlayer.equals(
                        player.getUniqueID()
                            .toString())) {
                                startConstruction(player);
                                break;
                            }
                return;
            }
            pendingConstructionPlayer = "";
            CriticalRuntime.unregisterClearance(this);
            return;
        }
        if (!isEnabled()) {
            if (state == 2) data().releaseNight(structureId);
            fail("已暂停");
            return;
        }
        // Keep saved journals and dismantling available without advancing frozen machines.
        if ((state == 1 || state == 2 || state == 4) && !config.kind.isReleased()) {
            data().releaseNight(structureId);
            fail("该临界机型维护冻结，可保留或拆除已有结构");
            return;
        }
        if (nativeFundingUncertain) {
            fail("ME扣料结果待人工核验，任务已锁定");
            return;
        }
        if (!loomLoaded) {
            data().releaseNight(structureId);
            fail(structureLoadTicks >= STRUCTURE_LOAD_GRACE_TICKS ? "结构加载等待超时，保留工单等待区块恢复" : "结构加载等待，保留工单");
            return;
        }
        if (!isLoomStructureValid()) {
            if (!isLoomGeometryValid()) revokeGeometryValidation();
            data().releaseNight(structureId);
            fail("必要织机结构缺损，请手动修复织机");
            return;
        }
        if (!ensureConstructionWindow(Collections.emptyList(), 0, 0)) {
            fail("施工区块加载等待");
            return;
        }
        if (nativeFundingUncertain) {
            fail("ME扣料结果待人工核验，任务已锁定");
            return;
        }
        allVoxels();
        if (jobFingerprint != modelFingerprint) {
            fail("结构定义与任务快照不一致，禁止推进");
            return;
        }
        if (!ownsTask()) {
            revokeGeometryValidation();
            data().releaseNight(structureId);
            fail("区域锁或任务身份不一致，请用修复按钮重核账本");
            return;
        }
        if (!isEnabled()) {
            fail("已暂停");
            return;
        }
        boolean previouslyWorking = isWorking();
        if (dismantleRequested && !hasActiveBatch() && !energyRuntime.getBoolean("uncertain")) {
            if (!settleDismantleEnergy()) return;
            if (!beginDismantle()) return;
        }
        if (state == 1 && foundationRequired && getFoundationPhase() != 3) {
            tickFoundation();
            if (previouslyWorking != isWorking() || worldObj.getTotalWorldTime() % 20 == 0) changed();
            return;
        }
        if (state == 1 || state == 4) {
            int overclock = 1;
            if (isLoomJournalOwner() && constructionTimeVersion > 0 && !debugIgnoreEnergy) {
                overclock = CriticalFoundationChunk.overclockWork(
                    gregtech.api.enums.GTValues.V[9],
                    gtMachine() == null ? gregtech.api.enums.GTValues.V[9] : gtMachine().criticalRecipeVoltage());
                if (overclock == 0) {
                    fail("GT仓口额定功率不足，缺电暂停");
                    if (previouslyWorking) changed();
                    return;
                }
            }
            constructionTickBudget = CriticalConstructionDebugPolicy
                .work(overclock, debugSpeed, getConstructionDurationTicks());
            if (debugSpeed == 3) {
                // Headroom absorbs bursts where several paid steps unlock their physical carriers together.
                constructionBlockBudget = CriticalConstructionDebugPolicy.placementBudget(currentPaidStage().size());
                constructionLightBudget = constructionBlockBudget;
            }
            boolean anyAdvance = false;
            for (int steps = 0; steps < 8192 && constructionTickBudget > 0
                && blocksChangedThisTick < constructionBlockBudget
                && lightsChangedThisTick < constructionLightBudget
                && (state == 1 || state == 4); steps++) {
                int oldStage = stage, oldCursor = cursor, oldRepairScan = repairScanCursor;
                int oldBlockChanges = blocksChangedThisTick;
                constructionLastAdvance = 0;
                boolean legacyStep = constructionTimeVersion == 0;
                if (state == 1) build();
                else repair();
                if (legacyStep) break;
                anyAdvance |= constructionLastAdvance > 0 || blocksChangedThisTick > oldBlockChanges;
                constructionTickBudget -= constructionLastAdvance;
                // Keep each completed row visible; unused debug budget never spills into the next row.
                if (debugSpeed > 0 && oldStage != stage && anyAdvance) break;
                if (constructionLastAdvance == 0 && oldStage == stage
                    && oldCursor == cursor
                    && oldRepairScan == repairScanCursor) break;
                if (getReasonCode() != 0) break;
            }
            constructionAdvancing = anyAdvance;
        } else if (state == 3) dismantle();
        else if (state == 2) {
            if (!structureValid) {
                data().releaseNight(structureId);
                fail(structureMonitor.hasKnownDamage() ? "必要织机或机器结构缺损，暂停" : "结构加载复核中，保留工单并暂停运行");
                return;
            }
            emitEnergy();
            runBatch();
            if (dismantleRequested && !hasActiveBatch()
                && !energyRuntime.getBoolean("uncertain")
                && settleDismantleEnergy()) beginDismantle();
        }
        if (previouslyWorking != isWorking() || worldObj.getTotalWorldTime() % 20 == 0) changed();
    }

    public boolean isLoomStructureValid() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
            && machine.isLoomStructureValid();
    }

    public boolean isLoomGeometryValid() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
            && machine.isLoomGeometryValid();
    }

    private boolean forceLoomStructureCheck() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
            && machine.forceLoomStructureCheck();
    }

    private boolean isUserBuilt(CriticalGeometry.Voxel voxel) {
        if (constructionLedger.isPhysicalJob() && constructionLedger.economyVersion() >= 5
            && constructionLedger.physicalJob()
                .stage(voxel) == 0)
            return true;
        return CriticalLoomStructure.isLoomVoxel(voxel) || isStandardHatch(voxel);
    }

    public boolean applyConstructionDebug(EntityPlayer player, int requirements, int speed, boolean ignoreEnergy) {
        if (worldObj == null || worldObj.isRemote
            || !canUse(player)
            || !player.canCommandSenderUseCommand(2, "gtsrdebug") && !player.capabilities.isCreativeMode
            || requirements < 0
            || requirements > 3
            || speed < 0
            || speed > 3) return false;
        debugRequirements = requirements;
        debugSpeed = speed;
        debugIgnoreEnergy = ignoreEnergy;
        // GT's lifecycle must see the same resource-interface requirements as the journal immediately.
        forceLoomStructureCheck();
        changed();
        return true;
    }

    public boolean resetConstructionDebug(EntityPlayer player) {
        return applyConstructionDebug(player, 0, 0, false);
    }

    public int getConstructionDebugRequirements() {
        return debugRequirements;
    }

    public int getConstructionDebugSpeed() {
        return debugSpeed;
    }

    public boolean getConstructionDebugIgnoreEnergy() {
        return debugIgnoreEnergy;
    }

    public int getConstructionElapsedTicks() {
        if (worldObj != null && worldObj.isRemote) return cycleTicks;
        if (state == 1 && foundationRequired) {
            int phase = getFoundationPhase();
            if (phase == 1) return getFoundationCellProgressTicks();
            if (phase == 2) return getFoundationCuringDurationTicks() - getFoundationCuringRemainingTicks();
            if (phase == 4) return CriticalFoundationChunk.HEATING_DURATION - getFoundationHeatingRemainingTicks();
        }
        return constructionLedger.elapsed();
    }

    public int getConstructionDurationTicks() {
        if (worldObj != null && worldObj.isRemote && (state == 1 || state == 4)) return cycleDuration;
        if (state == 1 && foundationRequired && getFoundationPhase() == 4)
            return CriticalFoundationChunk.HEATING_DURATION;
        if (state == 1 && foundationRequired && getFoundationPhase() == 2) return getFoundationCuringDurationTicks();
        if (state == 1 && foundationRequired && getFoundationPhase() == 1) return getFoundationCellDurationTicks();
        return constructionLedger.economyVersion() >= 6
            ? CriticalConstructionProgress.STAGE_DURATION * getConstructionRoundTotal()
            : CriticalConstructionProgress.STAGE_DURATION;
    }

    private boolean ignoresConstructionItems() {
        return debugRequirements == 1 || debugRequirements == 3;
    }

    private boolean ignoresConstructionFluids() {
        return debugRequirements == 2 || debugRequirements == 3;
    }

    private void resetConstructionStage() {
        constructionLedger.resetStage();
    }

    public FluidStack[] getConstructionStageFluidCostsForGui() {
        return getConstructionFluidCostsForGui();
    }

    public FluidStack[] getConstructionFluidCostsForGui() {
        if (state == 1 && foundationRequired && getFoundationPhase() != 3) return getFoundationPhase() == 1
            ? new FluidStack[] { CriticalFoundationMaterials
                .fluid(CriticalFoundationMaterials.concreteAmount(getFoundationScheduleVersion())) }
            : getFoundationPhase() == 4
                ? new FluidStack[] { gregtech.api.util.GTModHandler.getSuperHeatedSteam(foundationHeatingSteamDue()) }
                : new FluidStack[0];
        if (constructionLedger.isPhysicalJob()) {
            if (state != 0 && state != 1 && state != 4) return new FluidStack[0];
            if (constructionLedger.hasStageSnapshot()) return constructionLedger.stageFluids();
            List<CriticalGeometry.Voxel> current = currentPaidStage();
            return constructionLedger.physicalJob()
                .fluidCosts(current, 0, current.size());
        }
        if (constructionTimeVersion == 0 && (stageFunded || repairFunded)) return new FluidStack[0];
        if (state != 0 && state != 1 && state != 4) return new FluidStack[0];
        if (constructionLedger.hasStageSnapshot()) return constructionLedger.stageFluids();
        allVoxels();
        int costStage = state == 0 ? 0 : stage;
        int count = 0;
        if ((state == 0 || state == 1) && costStage >= 0 && costStage < stageCache.size()) {
            for (CriticalGeometry.Voxel voxel : stageCache.get(costStage)) if (!isUserBuilt(voxel)) count++;
        } else if (state == 4) {
            List<CriticalGeometry.Voxel> voxels = allVoxels();
            for (int index = repairTargets.nextSetBit(0); index >= 0; index = repairTargets.nextSetBit(index + 1))
                if (index < voxels.size() && voxels.get(index).stage == stage && !isUserBuilt(voxels.get(index)))
                    count++;
        }
        return count == 0 ? new FluidStack[0] : CriticalConstructionCosts.forStageFluids(config, costStage, count);
    }

    public boolean isCompletionValidationPending() {
        return completionValidationPending;
    }

    public boolean isConstructionRoundFunded() {
        return constructionLedger.funded();
    }

    public int getConstructionRoundIndex() {
        return constructionLedger.round();
    }

    private boolean usesExactConstructionSteps() {
        return constructionLedger.economyVersion() >= 6
            && !(state == 1 && foundationRequired && getFoundationPhase() != 3);
    }

    public int getConstructionRoundTotal() {
        if (!usesExactConstructionSteps()) return CriticalConstructionLedger.roundCount(
            constructionLedger.hasStageSnapshot() ? constructionLedger.stageItems() : getConstructionCostsForGui());
        return constructionLedger.stageRoundCount(
            constructionLedger.hasStageSnapshot() ? constructionLedger.stageItems() : getConstructionCostsForGui(),
            constructionLedger.hasStageSnapshot() ? constructionLedger.stageFluids()
                : getConstructionFluidCostsForGui(),
            stage);
    }

    public int getConstructionRoundElapsedTicks() {
        return Math.max(
            0,
            getConstructionElapsedTicks() - (usesExactConstructionSteps()
                ? CriticalConstructionProgress.STAGE_DURATION * constructionLedger.round()
                : CriticalConstructionProgress.STAGE_DURATION * constructionLedger.round()
                    / getConstructionRoundTotal()));
    }

    public int getConstructionRoundDurationTicks() {
        if (usesExactConstructionSteps()) return CriticalConstructionProgress.STAGE_DURATION;
        int rounds = getConstructionRoundTotal();
        return CriticalConstructionProgress.STAGE_DURATION * (constructionLedger.round() + 1) / rounds
            - CriticalConstructionProgress.STAGE_DURATION * constructionLedger.round() / rounds;
    }

    public ItemStack[] getConstructionNextItemCostsForGui() {
        if (state != 0 && state != 1 && state != 4) return new ItemStack[0];
        if (ignoresConstructionItems()) return new ItemStack[0];
        if (state == 1 && foundationRequired && getFoundationPhase() != 3)
            return getFoundationPhase() == 1 && !isFoundationCellFunded() ? activeFoundationItems() : new ItemStack[0];
        if (constructionLedger.isPhysicalJob()) return constructionLedger.funded() ? new ItemStack[0]
            : constructionLedger.physicalNextItems(currentPaidStage());
        ItemStack[] costs = constructionLedger.hasStageSnapshot() ? constructionLedger.stageItems()
            : getConstructionCostsForGui();
        return constructionTimeVersion > 0 ? constructionLedger.nextItems(costs) : costs;
    }

    public FluidStack[] getConstructionNextFluidCostsForGui() {
        if (state != 0 && state != 1 && state != 4) return new FluidStack[0];
        if (ignoresConstructionFluids()) return new FluidStack[0];
        if (state == 1 && foundationRequired && getFoundationPhase() != 3)
            return getFoundationPhase() == 1 && !isFoundationCellFunded()
                ? new FluidStack[] { CriticalFoundationMaterials
                    .fluid(CriticalFoundationMaterials.concreteAmount(getFoundationScheduleVersion())) }
                : getFoundationPhase() == 4 && !isFoundationHeatingFunded()
                    ? new FluidStack[] {
                        gregtech.api.util.GTModHandler.getSuperHeatedSteam(foundationHeatingSteamDue()) }
                    : new FluidStack[0];
        if (constructionLedger.funded()) return new FluidStack[0];
        if (constructionTimeVersion == 0 && (stageFunded || repairFunded)) return new FluidStack[0];
        if (constructionLedger.hasStageSnapshot()) return constructionLedger.nextStageFluids();
        if (constructionLedger.economyVersion() >= 6) {
            FluidStack[] fluids = getConstructionFluidCostsForGui();
            for (FluidStack fluid : fluids) fluid.amount /= getConstructionRoundTotal();
            return fluids;
        }
        return constructionLedger.nextFluids(getConstructionFluidCostsForGui(), getConstructionCostsForGui());
    }

    private ItemStack[] activeFoundationItems() {
        int active = getFoundationActiveChunkIndex();
        CriticalFoundationChunk chunk = worldObj == null || worldObj.isRemote || active < 0 ? null
            : findFoundation(active);
        return CriticalFoundationMaterials
            .items(chunk == null ? foundationModern ? foundationScheduleVersion : 0 : chunk.scheduleVersion());
    }

    public ItemStack[] getConstructionMissingItemsForGui() {
        List<ItemStack> missing = new ArrayList<>();
        if (constructionLedger.funded()) return new ItemStack[0];
        ItemStack[] physicalItems = gtMachine() == null ? new ItemStack[0]
            : gtMachine().criticalPhysicalItemsSnapshot();
        ItemStack[] networkItems = gtMachine() == null ? new ItemStack[0] : gtMachine().criticalMEItemsSnapshot();
        for (ItemStack wanted : getConstructionNextItemCostsForGui()) {
            long available = 0;
            for (ItemStack physical : physicalItems) if (same(physical, wanted)) available += physical.stackSize;
            for (ItemStack network : networkItems) if (same(network, wanted)) available += network.stackSize;
            if (available < wanted.stackSize) {
                ItemStack item = wanted.copy();
                item.stackSize -= available;
                missing.add(item);
            }
        }
        return missing.toArray(new ItemStack[0]);
    }

    public FluidStack[] getConstructionMissingFluidsForGui() {
        List<FluidStack> missing = new ArrayList<>();
        if (constructionLedger.funded()) return new FluidStack[0];
        FluidStack[] physicalFluids = gtMachine() == null ? new FluidStack[0]
            : gtMachine().criticalPhysicalFluidsSnapshot();
        FluidStack[] networkFluids = gtMachine() == null ? new FluidStack[0] : gtMachine().criticalMEFluidsSnapshot();
        for (FluidStack wanted : getConstructionNextFluidCostsForGui()) {
            long available = 0;
            for (FluidStack physical : physicalFluids) if (physical.isFluidEqual(wanted)) available += physical.amount;
            for (FluidStack network : networkFluids) if (network.isFluidEqual(wanted)) available += network.amount;
            if (available < wanted.amount) {
                FluidStack fluid = wanted.copy();
                fluid.amount -= available;
                missing.add(fluid);
            }
        }
        return missing.toArray(new FluidStack[0]);
    }

    private boolean canConsumeConstructionEnergy(long amount) {
        com.miaokatze.gtsr.common.machine.MTECriticalMachine machine = gtMachine();
        return machine == null ? energy >= amount : machine.canConsumeCriticalEnergy(amount);
    }

    public boolean fundCriticalInputs(ItemStack[] items, FluidStack[] fluids) {
        if (nativeFundingUncertain) return fail("ME扣料结果待人工核验，任务已锁定");
        ItemStack[] missingItems = items;
        FluidStack[] missingFluids = fluids;
        if (missingItems.length > 0 || missingFluids.length > 0) {
            if (!(host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt)
                || !(gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine))
                return fail("缺少材料或流体，暂停");
            CriticalNativeMEFunding.Result result = machine.fundCriticalHatchInputs(missingItems, missingFluids);
            if (result == CriticalNativeMEFunding.Result.SHORTAGE) return fail("缺少材料或流体，暂停");
            if (result != CriticalNativeMEFunding.Result.SUCCESS) {
                nativeFundingUncertain = true;
                uncertainFundingItems = copyItems(missingItems);
                uncertainFundingFluids = copyFluids(missingFluids);
                markDirty();
                return fail("ME扣料结果待人工核验，任务已锁定");
            }
        }
        markDirty();
        return true;
    }

    private List<CriticalGeometry.Voxel> paidStage(List<CriticalGeometry.Voxel> current) {
        return paidStageCache.computeIfAbsent(current, voxels -> {
            List<CriticalGeometry.Voxel> result = new ArrayList<>();
            for (CriticalGeometry.Voxel voxel : voxels) if (!isUserBuilt(voxel)) result.add(voxel);
            return result;
        });
    }

    private List<CriticalGeometry.Voxel> currentPaidStage() {
        allVoxels();
        if (state == 4) {
            if (repairStages != null && stage < repairStages.size()) return paidStage(repairStages.get(stage));
            List<CriticalGeometry.Voxel> result = new ArrayList<>();
            List<CriticalGeometry.Voxel> voxels = allVoxels();
            for (int i = repairTargets.nextSetBit(0); i >= 0 && i < voxels.size(); i = repairTargets.nextSetBit(i + 1))
                if (constructionStage(voxels.get(i)) == stage && !isUserBuilt(voxels.get(i))) result.add(voxels.get(i));
            return result;
        }
        return stage >= 0 && stage < stageCache.size() ? paidStage(stageCache.get(stage)) : Collections.emptyList();
    }

    private void advanceConstruction(List<CriticalGeometry.Voxel> current, boolean repairing) {
        if (constructionLedger.isPhysicalJob()) current = paidStage(current);
        if (current.isEmpty()) {
            stage++;
            cursor = 0;
            resetConstructionStage();
            changed();
            return;
        }
        if (!constructionLedger.hasStageSnapshot()) {
            int count = 0;
            for (CriticalGeometry.Voxel voxel : current) if (!isUserBuilt(voxel)) count++;
            if (count == 0) {
                stage++;
                cursor = 0;
                resetConstructionStage();
                changed();
                return;
            }
            if (constructionLedger.isPhysicalJob()) constructionLedger.snapshotPhysicalStage(current);
            else constructionLedger.snapshotStage(
                CriticalConstructionCosts.forStage(config, stage, count),
                CriticalConstructionCosts.forStageFluids(config, stage, count));
        }
        CriticalConstructionLedger.Round round = constructionLedger.isPhysicalJob()
            ? constructionLedger.planPhysicalRound(constructionTickBudget, current)
            : constructionLedger.planRound(constructionTickBudget, current.size());
        int roundEnd = round.end, next = round.next, roundVoxelEnd = round.voxelEnd;
        if (constructionLedger.economyVersion() >= 6 && (roundVoxelEnd < cursor || roundVoxelEnd > current.size())) {
            nativeFundingUncertain = true;
            fail("施工已付边界异常，任务待人工核验");
            markDirty();
            return;
        }
        int end = loadedWindowEnd(current, cursor, roundVoxelEnd);
        if (end < 0) {
            fail("施工区块加载等待");
            return;
        }
        for (int index = cursor; index < end; index++) {
            CriticalGeometry.Voxel voxel = current.get(index);
            if (!isUserBuilt(voxel) && !matches(voxel) && !worldObj.isAirBlock(wx(voxel), wy(voxel), wz(voxel))) {
                fail("施工冲突，禁止覆盖方块");
                return;
            }
        }
        int advance = round.advance;
        long eu = (isLoomJournalOwner() ? gregtech.api.enums.GTValues.V[9] : config.tier.constructionVoltage())
            * advance;
        if (!constructionLedger.funded()) {
            ItemStack[] items = ignoresConstructionItems() ? new ItemStack[0] : round.items();
            FluidStack[] fluids = ignoresConstructionFluids() ? new FluidStack[0] : round.fluids();
            if (!debugIgnoreEnergy && !canConsumeConstructionEnergy(eu)) {
                fail("施工缺电，暂停");
                return;
            }
            if (!fundCriticalInputs(items, fluids)) return;
            constructionLedger.recordRoundPayment(items, fluids);
            markDirty();
        }
        if (advance > 0 && !debugIgnoreEnergy && (getEnergy() < eu || !consumeEnergy(eu))) {
            fail("施工缺电，暂停");
            return;
        }
        // Persist payment and elapsed together before any retryable placement. A failed setBlock cannot pay twice.
        constructionLedger.advanceElapsed(next);
        constructionLastAdvance = advance;
        markDirty();
        if (next < roundEnd) {
            constructionAdvancing = advance > 0;
            status = "施工回合计时 " + next + "/" + roundEnd;
            markDirty();
            return;
        }
        for (; cursor < end; cursor++) {
            CriticalGeometry.Voxel voxel = current.get(cursor);
            if (isUserBuilt(voxel)) continue;
            if (!matches(voxel) && !hasBlockBudget(voxel)) return;
            if (!matches(voxel) && !placeOwnedBlock(voxel)) {
                fail("施工放置失败，已付进度保留");
                return;
            }
        }
        constructionAdvancing = advance > 0;
        status = (repairing ? "修复阶段 " : "施工阶段 ") + stage + " " + next + "/" + constructionLedger.stageDuration();
        if (cursor < roundVoxelEnd) {
            markDirty();
            return;
        }
        constructionLedger.completeRound();
        if (next == constructionLedger.stageDuration() && cursor == current.size()) {
            stage++;
            cursor = 0;
            resetConstructionStage();
            changed();
        }
        markDirty();
    }

    private void repair() {
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        if (repairScanPending) {
            int end = loadedWindowEnd(voxels, repairScanCursor, voxels.size());
            if (end < 0) {
                fail("修复核验区块加载等待");
                return;
            }
            for (; repairScanCursor < end; repairScanCursor++) {
                CriticalGeometry.Voxel voxel = voxels.get(repairScanCursor);
                if (!matches(voxel)) {
                    if (isUserBuilt(voxel)) {
                        fail("手搭织机或仓口缺损，请手动修复");
                        return;
                    }
                    if (!worldObj.isAirBlock(wx(voxel), wy(voxel), wz(voxel))) {
                        fail("修复冲突，禁止覆盖外来方块");
                        return;
                    }
                    repairTargets.set(repairScanCursor);
                    constructionLedger.markPhysicalDamaged(voxel);
                }
            }
            markDirty();
            if (repairScanCursor < voxels.size()) {
                status = "修复核验中";
                return;
            }
            repairScanPending = false;
            repairStages = null;
        }
        if (repairStages == null) {
            repairStages = new ArrayList<>();
            for (int i = 0; i < stageCache.size(); i++) repairStages.add(new ArrayList<>());
            for (int index = repairTargets.nextSetBit(0); index >= 0; index = repairTargets.nextSetBit(index + 1)) {
                if (index >= voxels.size()) {
                    fail("修复账本与模型不一致");
                    return;
                }
                CriticalGeometry.Voxel v = voxels.get(index);
                repairStages.get(constructionStage(v))
                    .add(v);
            }
        }
        if (stage >= repairStages.size()) {
            repairTargets.clear();
            repairStages = null;
            state = 2;
            completionValidationPending = true;
            invalidateStructure();
            changed();
            return;
        }
        List<CriticalGeometry.Voxel> current = repairStages.get(stage);
        if (constructionTimeVersion == 0 && !repairFunded && cursor == 0) constructionTimeVersion = 1;
        if (constructionTimeVersion > 0) {
            advanceConstruction(current, true);
            return;
        }
        if (current.isEmpty()) {
            constructionTimeVersion = 1;
            resetConstructionStage();
            stage++;
            cursor = 0;
            changed();
            return;
        }
        if (!repairFunded) {
            ItemStack[] costs = CriticalConstructionCosts.forStage(
                config,
                stage,
                (int) current.stream()
                    .filter(v -> !isUserBuilt(v))
                    .count());
            if (!consumeItems(costs)) {
                fail("修复阶段缺少材料");
                return;
            }
            repairEscrow = copyItems(costs);
            repairFunded = true;
            changed();
        }
        long cost = config.tier.constructionVoltage();
        if (getEnergy() < cost) {
            fail("修复缺电");
            return;
        }
        if (!consumeEnergy(cost)) return;
        int loadedEnd = loadedWindowEnd(current, cursor, current.size());
        if (loadedEnd < 0) {
            fail("施工区块加载等待");
            return;
        }
        for (int i = 0; i < 128 && cursor < loadedEnd; i++) {
            CriticalGeometry.Voxel v = current.get(cursor);
            if (!isUserBuilt(v) && !matches(v)) {
                if (!worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                    fail("修复冲突");
                    return;
                }
                if (!hasBlockBudget(v)) return;
                if (!placeOwnedBlock(v)) {
                    fail("修复放置失败");
                    return;
                }
            }
            cursor++;
        }
        status = "修复阶段 " + stage + " " + cursor + "/" + current.size();
        markDirty();
        if (cursor == current.size()) {
            for (ItemStack stack : repairEscrow) constructionLedger.recordLegacyItem(stack);
            repairEscrow = new ItemStack[0];
            repairFunded = false;
            constructionTimeVersion = 1;
            resetConstructionStage();
            cursor = 0;
            stage++;
            changed();
        }
    }

    private void build() {
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        int maxStage = stageCache.size() - 1;
        List<CriticalGeometry.Voxel> current = stage <= maxStage ? stageCache.get(stage) : Collections.emptyList();
        if (stage > maxStage) {
            state = 2;
            invalidateStructure();
            // The paid placement journal and checked manual loom establish the initial result.
            // Repairs and disk reloads never enter this construction-only handoff.
            integrityOptionalPorts = CriticalGeometry.optionalWorkPorts(this);
            structureMonitor.primeConstruction(voxels.size() + integrityOptionalPorts.size());
            structureValid = true;
            completionValidationPending = false;
            status = "建造完成";
            changed();
            return;
        }
        if (current.isEmpty()) {
            constructionTimeVersion = 1;
            resetConstructionStage();
            stage++;
            cursor = 0;
            changed();
            return;
        }
        if (constructionTimeVersion == 0 && !stageFunded && cursor == 0) constructionTimeVersion = 1;
        if (constructionTimeVersion > 0) {
            advanceConstruction(current, false);
            return;
        }
        if (!stageFunded) {
            ItemStack[] costs = CriticalConstructionCosts.forStage(
                config,
                stage,
                (int) current.stream()
                    .filter(v -> !isUserBuilt(v))
                    .count());
            if (!consumeItems(costs)) {
                fail("施工阶段 " + stage + " 缺少材料");
                return;
            }
            escrow = copyItems(costs);
            stageFunded = true;
            changed();
        }
        long cost = config.tier.constructionVoltage();
        if (getEnergy() < cost) {
            fail("施工缺电");
            return;
        }
        if (!consumeEnergy(cost)) return;
        int loadedEnd = loadedWindowEnd(current, cursor, current.size());
        if (loadedEnd < 0) {
            fail("施工区块加载等待");
            return;
        }
        for (int i = 0; i < 128 && cursor < loadedEnd; i++) {
            CriticalGeometry.Voxel v = current.get(cursor);
            if (!isUserBuilt(v) && !matches(v)) {
                if (!worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                    fail("施工冲突，禁止覆盖方块");
                    return;
                }
                if (!hasBlockBudget(v)) return;
                if (!placeOwnedBlock(v)) {
                    fail("方块放置失败");
                    return;
                }
            }
            cursor++;
        }
        status = "施工阶段 " + stage + " " + cursor + "/" + current.size();
        markDirty();
        if (cursor == current.size()) {
            for (ItemStack item : escrow) constructionLedger.recordLegacyItem(item);
            escrow = new ItemStack[0];
            stageFunded = false;
            constructionTimeVersion = 1;
            resetConstructionStage();
            cursor = 0;
            stage++;
            changed();
        }
    }

    private boolean hasBlockBudget(CriticalGeometry.Voxel v) {
        return blocksChangedThisTick < constructionBlockBudget
            && (blockBudgetStarted == 0 || System.nanoTime() - blockBudgetStarted
                < (debugSpeed == 3 && (state == 1 || state == 4) ? 45_000_000L : 6_000_000L))
            && (lightsChangedThisTick < constructionLightBudget
                || v.block.getLightValue(worldObj, wx(v), wy(v), wz(v)) == 0);
    }

    private boolean placeOwnedBlock(CriticalGeometry.Voxel v) {
        if (!worldObj.isAirBlock(wx(v), wy(v), wz(v))) return false;
        EntityPlayer ownerPlayer = null;
        for (Object object : worldObj.playerEntities) if (object instanceof EntityPlayer p && owner.equals(
            p.getUniqueID()
                .toString()))
            ownerPlayer = p;
        if (ownerPlayer != null && !worldObj.canMineBlock(ownerPlayer, wx(v), wy(v), wz(v))) return false;
        if (blockBudgetStarted == 0) blockBudgetStarted = System.nanoTime();
        boolean placedBlock = CriticalStructures.isController(v)
            ? new CriticalStructures.ControllerElement(v.z == 47, config.kind, getStructureFacing())
                .placeController(gtMachine(), worldObj, wx(v), wy(v), wz(v))
            : worldObj.setBlock(wx(v), wy(v), wz(v), v.block, v.metadata, 2);
        if (!placedBlock) return false;
        constructionLedger.markPhysicalInstalled(v);
        placed.set(voxelIndices.get(v));
        protectionDirty = true;
        blocksChangedThisTick++;
        if (v.block.getLightValue(worldObj, wx(v), wy(v), wz(v)) > 0) lightsChangedThisTick++;
        return true;
    }

    private void dismantle() {
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        // The persisted placement journal is the ownership boundary. Never-placed cells need no
        // world reads, power or reverse scan (a foundation-only job has no machine cells at all).
        int next = placed.previousSetBit(voxels.size() - 1 - cursor);
        long cost = config.tier.constructionVoltage();
        if (next >= 0 && !debugIgnoreEnergy) {
            if (getEnergy() < cost) {
                fail("拆除缺电");
                return;
            }
            if (!consumeEnergy(cost)) return;
        }
        if (!ensureConstructionWindow(Collections.emptyList(), 0, 0)) {
            fail("拆除区块加载等待");
            return;
        }
        for (int i = 0; i < 128 && next >= 0; i++) {
            cursor = voxels.size() - 1 - next;
            CriticalGeometry.Voxel v = voxels.get(next);
            if (!isUserBuilt(v) && worldObj.isAirBlock(wx(v), wy(v), wz(v))) constructionLedger.markPhysicalDamaged(v);
            if (!isUserBuilt(v) && !worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                if (!matches(v)) {
                    fail("拆除冲突，目标不是已登记结构方块");
                    return;
                }
                if (!hasBlockBudget(v)) {
                    markDirty();
                    return;
                }
                boolean light = v.block.getLightValue(worldObj, wx(v), wy(v), wz(v)) > 0;
                if (blockBudgetStarted == 0) blockBudgetStarted = System.nanoTime();
                if (!worldObj.setBlockToAir(wx(v), wy(v), wz(v))) {
                    fail("拆除方块失败");
                    return;
                }
                blocksChangedThisTick++;
                if (light) lightsChangedThisTick++;
            }
            cursor++;
            protectionDirty = true;
            next = placed.previousSetBit(next - 1);
        }
        if (next < 0) cursor = voxels.size();
        markDirty();
        status = "拆除 " + cursor + "/" + voxels.size();
        if (cursor < voxels.size()) return;
        if (!removalVerified) {
            int index = placed.nextSetBit(scanCursor);
            for (int checked = 0; checked < 128 && index >= 0 && index < voxels.size(); checked++) {
                CriticalGeometry.Voxel v = voxels.get(index);
                if (!isUserBuilt(v) && !worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                    fail("区域残留核验失败");
                    return;
                }
                scanCursor = index + 1;
                index = placed.nextSetBit(scanCursor);
            }
            if (index >= 0 && index < voxels.size()) return;
            scanCursor = voxels.size();
            removalVerified = true;
        }
        if (!refundPrepared) {
            List<ItemStack> refund = new ArrayList<>();
            for (ItemStack stack : escrow) refund.add(stack.copy());
            for (int i = 0; i < INPUT_SLOTS; i++) if (inventory[i] != null) {
                refund.add(inventory[i].copy());
                inventory[i] = null;
            }
            List<FluidStack> fluidRefund = new ArrayList<>();
            for (int i = 0; i < 8; i++) if (tanks[i] != null) {
                fluidRefund.add(tanks[i].copy());
                tanks[i] = null;
            }
            for (ItemStack stack : constructionLedger.recoveryItems(recoverySeed)) refund.add(stack);
            pendingItems = refund.toArray(new ItemStack[0]);
            escrow = new ItemStack[0];
            constructionLedger.clearSpentItems();
            pendingFluids = fluidRefund.toArray(new FluidStack[0]);
            refundPrepared = true;
            changed();
        }
        flushItemJournal();
        if (pendingItems.length > 0 || !canDeliverFluids(pendingFluids)) {
            fail("拆除回收等待输出空间");
            return;
        }
        if (!settleDismantleEnergy()) return;
        if (config.kind == CriticalMachineKind.BATTERY
            && !CriticalEnergyRuntime.settleChemicalEscrow(this, gtMachine())) return;
        if (gtMachine() != null)
            CriticalStellarRuntime.settleRefund(stellarRuntime, gtMachine().criticalStellarPowerPort());
        if (CriticalStellarRuntime.hasAssets(stellarRuntime)) {
            fail("拆除等待恒星未用电量退回，请为退款提供输出空间");
            return;
        }
        deliverFluids(pendingFluids);
        pendingFluids = new FluidStack[0];
        CriticalRenderAnchors.remove(this);
        chunkLoading.release(host);
        if (foundationRequired) data().discardIncompleteFoundations(structureId, loomChunks());
        data().release(structureId);
        state = 0;
        invalidateStructure();
        initialClearanceAccepted = false;
        clearanceCursor = 0;
        clearanceTargets = null;
        clearanceRechecks.clear();
        constructionLedger.clearPaidFluids();
        constructionLedger.resetJob();
        paidStageCache.clear();
        completionValidationPending = false;
        structureId = "";
        jobId = "";
        cursor = 0;
        stage = 0;
        refundPrepared = false;
        dismantleRequested = false;
        placed.clear();
        repairTargets.clear();
        scanCursor = 0;
        removalVerified = false;
        jobFingerprint = 0;
        visualHostId = UUID.randomUUID()
            .toString();
        status = canRemove() ? "拆除完成" : "拆除完成，请取出控制器内回收物品与流体";
        changed();
    }

    private com.miaokatze.gtsr.common.machine.MTECriticalMachine gtMachine() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine ? machine
                : null;
    }

    private RecipeMatch matchCriticalHatchRecipe(ItemStack[] physicalItems, FluidStack[] physicalFluids) {
        com.miaokatze.gtsr.common.machine.MTECriticalMachine machine = gtMachine();
        if (machine == null) return null;
        ItemStack[] networkItems = machine.criticalMEItemsSnapshot();
        FluidStack[] networkFluids = machine.criticalMEFluidsSnapshot();
        for (RecipeMatch reference : CriticalRecipes.list(config))
            for (int multiplier = 1; multiplier <= config.parallelMultiplier(); multiplier++) {
                ItemStack[] items = copyItems(reference.itemInputs);
                FluidStack[] fluids = copyFluids(reference.fluidInputs);
                for (ItemStack item : items) item.stackSize = Math.multiplyExact(item.stackSize, multiplier);
                for (FluidStack fluid : fluids) fluid.amount = Math.multiplyExact(fluid.amount, multiplier);
                // Real GT inputs may contain unrelated or excess stock. Reserve only this batch's exact costs.
                ItemStack[] residualItems = copyItems(items);
                boolean compatible = true;
                for (ItemStack actual : physicalItems) if (actual != null) {
                    int left = actual.stackSize;
                    for (ItemStack remaining : residualItems) if (same(remaining, actual)) {
                        int used = Math.min(left, remaining.stackSize);
                        remaining.stackSize -= used;
                        left -= used;
                    }
                }
                FluidStack[] residualFluids = copyFluids(fluids);
                for (FluidStack actual : physicalFluids) if (actual != null) {
                    int left = actual.amount;
                    for (FluidStack remaining : residualFluids) if (remaining.isFluidEqual(actual)) {
                        int used = Math.min(left, remaining.amount);
                        remaining.amount -= used;
                        left -= used;
                    }
                }
                for (ItemStack remaining : residualItems) if (remaining.stackSize > 0) {
                    int available = 0;
                    for (ItemStack network : networkItems)
                        if (same(network, remaining)) available = Math.max(available, network.stackSize);
                    if (available < remaining.stackSize) {
                        compatible = false;
                        break;
                    }
                }
                for (FluidStack remaining : residualFluids) if (remaining.amount > 0) {
                    int available = 0;
                    for (FluidStack network : networkFluids)
                        if (network.isFluidEqual(remaining)) available = Math.max(available, network.amount);
                    if (available < remaining.amount) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) return CriticalRecipes.match(config, items, fluids);
            }
        return null;
    }

    private void runBatch() {
        boolean refund = batchRecipe.getBoolean("cancelRefund");
        boolean stellar = config.kind == CriticalMachineKind.SUN;
        if (stellar && (batchRecipe.getInteger("billingVersion") < 0
            || batchRecipe.getInteger("billingVersion") > CriticalStellarPolicy.VERSION)) {
            fail("恒星批次计费版本未知，保留原料等待核验");
            return;
        }
        if (stellar && !refund && (batchId.isEmpty() || batchRecipe.getInteger("billingVersion") > 0)) {
            if (!CriticalStellarRuntime.ready(stellarRuntime)) {
                fail(stellarRuntime.getBoolean("uncertain") ? "恒星支付结果待核验" : "恒星尚未就绪，等待点火或持续燃料与电力");
                return;
            }
            if (stellarRuntime.getBoolean("productionPaused") && !dismantleRequested) {
                fail("加工已暂停，恒星持续消耗氦三与电力");
                return;
            }
        }
        if (dismantleRequested && !hasActiveBatch()) return;
        // A pre-upgrade batch already paid its inputs. Settle its persisted outputs and energy
        // through the original journal before allowing the new energy backend to start a batch.
        if (batchId.isEmpty() && (config.kind == CriticalMachineKind.SOLAR || config.kind == CriticalMachineKind.TURBINE
            || config.kind == CriticalMachineKind.BATTERY)) {
            CriticalEnergyRuntime.tick(this, gtMachine());
            return;
        }
        if (config.kind == CriticalMachineKind.SOLAR && !data().claimNight(worldObj, structureId)) {
            fail("本维度已有先启动的辐照锅炉");
            return;
        }
        if (batchId.isEmpty()) {
            ItemStack[] inputs = gtMachine() == null ? new ItemStack[0] : gtMachine().criticalPhysicalItemsSnapshot();
            FluidStack[] fluids = gtMachine() == null ? new FluidStack[0]
                : gtMachine().criticalPhysicalFluidsSnapshot();
            RecipeMatch match;
            if (config.kind == CriticalMachineKind.SUN) match = CriticalGTRecipes.matchFusion(this, gtMachine());
            else if (config.kind == CriticalMachineKind.PROCESSING || config.kind == CriticalMachineKind.ASSEMBLY)
                match = CriticalGTRecipes.match(this, gtMachine());
            else {
                match = CriticalRecipes.match(config, inputs, fluids);
                if (match == null) match = matchCriticalHatchRecipe(inputs, fluids);
            }
            if (match == null) {
                status = "没有完整可执行批次";
                return;
            }
            if (match.generatedEu < 0 || match.generatedEu > capacity() - getBufferedEnergy() - pendingGenerated) {
                fail("发电批次容量不足，先释放储能");
                return;
            }
            if (!canDeliverBatch(match.itemOutputs, match.fluidOutputs)) {
                fail("批次输出空间不足");
                return;
            }
            if (!fundCriticalInputs(match.itemInputs, match.fluidInputs)) return;
            escrow = copyItems(match.itemInputs);
            fluidEscrow = copyFluids(match.fluidInputs);
            pendingItems = copyItems(match.itemOutputs);
            pendingFluids = copyFluids(match.fluidOutputs);
            batchEu = match.euPerTick;
            batchGenerated = match.generatedEu;
            batchRecipe = new NBTTagCompound();
            batchRecipe.setInteger("billingVersion", match.billingVersion);
            batchRecipe.setString("nativeFingerprint", match.nativeFingerprint);
            batchRecipe.setInteger("nativeDurationTicks", match.nativeDurationTicks);
            batchRecipe.setLong("nativeEuPerTick", match.nativeEuPerTick);
            batchRecipe.setLong("fusionThreshold", match.fusionThreshold);
            batchRecipe.setInteger("parallel", match.parallel);
            cycleTicks = 0;
            cycleDuration = match.durationTicks;
            batchId = UUID.randomUUID()
                .toString();
            status = "批次开工";
            changed();
        }
        if (cycleTicks < cycleDuration) {
            if (stellar && batchRecipe.getInteger("billingVersion") > 0) {
                if (gtMachine() == null || !CriticalStellarRuntime
                    .payProcessing(stellarRuntime, gtMachine().criticalStellarPowerPort(), batchId, batchEu)) {
                    fail("加工缺电，原料与已付进度保留；持续消耗独立结算");
                    return;
                }
            } else if (stellar && gtMachine() != null) {
                long alreadyPaid = batchRecipe.getLong("legacyTickPaid");
                CriticalEnergyDebit receipt = gtMachine().debitCriticalEnergy(Math.max(0, batchEu - alreadyPaid));
                batchRecipe.setLong("legacyTickPaid", alreadyPaid + receipt.confirmed);
                if (receipt.uncertain) {
                    nativeFundingUncertain = true;
                    fail("旧恒星批次扣电结果待核验");
                    markDirty();
                    return;
                }
                if (alreadyPaid + receipt.confirmed < batchEu) {
                    fail("旧恒星批次缺电，已付电量保留");
                    markDirty();
                    return;
                }
                batchRecipe.setLong("legacyTickPaid", 0);
            } else if (getEnergy() < batchEu || !consumeEnergy(batchEu)) {
                fail("批次缺电，输入已保留在本机escrow");
                return;
            }
            cycleTicks++;
            status = "工作 " + cycleTicks + "/" + cycleDuration;
            markDirty();
            if (cycleTicks < cycleDuration) return;
        }
        if (!canDeliverBatch(pendingItems, pendingFluids)) {
            fail("等待批次输出空间");
            return;
        }
        if (nativeProduction()) {
            CriticalEnergyPorts.OutputPlan output = nativeOutputs(pendingItems, pendingFluids);
            if (output == null) return;
            nativeFundingUncertain = true;
            markDirty();
            try {
                output.commit();
                nativeFundingUncertain = false;
            } catch (RuntimeException uncertain) {
                nativeFundingUncertain = true;
                fail("批次输出提交结果待人工核验，任务已锁定");
                markDirty();
                return;
            }
        } else {
            deliverItems(pendingItems);
            deliverFluids(pendingFluids);
        }
        pendingGenerated += batchGenerated;
        escrow = new ItemStack[0];
        fluidEscrow = new FluidStack[0];
        pendingItems = new ItemStack[0];
        pendingFluids = new FluidStack[0];
        batchId = "";
        batchRecipe = new NBTTagCompound();
        if (stellar && gtMachine() != null)
            CriticalStellarRuntime.finishBatch(stellarRuntime, gtMachine().criticalStellarPowerPort());
        batchGenerated = 0;
        cycleTicks = 0;
        cycleDuration = 0;
        status = "批次交付完成";
        changed();
    }

    private long capacity() {
        return CriticalTier.T3.recipeVoltage() * 4096L;
    }

    private void emitEnergy() {
        if (host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine) {
            pendingGenerated -= machine.emitCriticalEnergy(pendingGenerated);
        }
    }

    private boolean settleDismantleEnergy() {
        if (pendingGenerated <= 0) return true;
        emitEnergy();
        markDirty();
        return pendingGenerated == 0 || fail("拆除等待遗留发电交付，请腾出GT储能或输出空间");
    }

    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        if (host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity) return 0;
        if (!inputEnergyFrom(side) || voltage <= 0 || amperage <= 0 || voltage > config.tier.recipeVoltage()) return 0;
        long reserved = pendingGenerated + (batchId.isEmpty() ? 0 : batchGenerated);
        long accepted = Math.min(amperage, Math.max(0, capacity() - energy - reserved) / voltage);
        energy += accepted * voltage;
        if (accepted > 0) markDirty();
        return accepted;
    }

    public boolean inputEnergyFrom(ForgeDirection side) {
        return side != ForgeDirection.DOWN;
    }

    public boolean outputsEnergyTo(ForgeDirection side) {
        boolean producer = config.kind == CriticalMachineKind.BATTERY || config.kind == CriticalMachineKind.TURBINE
            || config.kind == CriticalMachineKind.SUN
            || config.kind == CriticalMachineKind.ACCELERATOR;
        return producer && side == ForgeDirection.DOWN && state == 2 && structureValid && isEnabled();
    }

    public byte getColorization() {
        return -1;
    }

    public byte setColorization(byte color) {
        return -1;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private boolean hasItems(ItemStack[] items) {
        ItemStack[] copy = copyItems(inventory);
        return removeItems(copy, items);
    }

    private boolean consumeItems(ItemStack[] items) {
        if (gtMachine() != null) return fundCriticalInputs(items, new FluidStack[0]);
        if (!hasItems(items)) return false;
        removeItems(inventory, items);
        markDirty();
        return true;
    }

    private static boolean removeItems(ItemStack[] target, ItemStack[] items) {
        for (ItemStack wanted : items) {
            if (wanted == null) continue;
            int remaining = wanted.stackSize;
            for (int i = 0; i < INPUT_SLOTS && remaining > 0; i++) if (same(target[i], wanted)) {
                int take = Math.min(remaining, target[i].stackSize);
                remaining -= take;
                target[i].stackSize -= take;
                if (target[i].stackSize == 0) target[i] = null;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private boolean nativeProduction() {
        return gtMachine() != null
            && (config.kind == CriticalMachineKind.PROCESSING || config.kind == CriticalMachineKind.ASSEMBLY
                || config.kind == CriticalMachineKind.SUN);
    }

    private CriticalEnergyPorts.OutputPlan nativeOutputs(ItemStack[] items, FluidStack[] fluids) {
        List<CriticalEnergyPorts.FluidAmount> amounts = new ArrayList<>();
        for (FluidStack fluid : fluids) if (fluid != null && fluid.amount > 0)
            amounts.add(new CriticalEnergyPorts.FluidAmount(fluid, java.math.BigInteger.valueOf(fluid.amount)));
        return CriticalEnergyPorts.outputs(gtMachine(), amounts, items);
    }

    public boolean canDeliverBatch(ItemStack[] items, FluidStack[] fluids) {
        return nativeProduction() ? nativeOutputs(items, fluids) != null
            : canDeliverItems(items) && canDeliverFluids(fluids);
    }

    public boolean canDeliverScaledBatch(ItemStack[] items, List<CriticalEnergyPorts.FluidAmount> fluids) {
        if (nativeProduction()) return CriticalEnergyPorts.outputs(gtMachine(), fluids, items) != null;
        List<FluidStack> copies = new ArrayList<>();
        for (CriticalEnergyPorts.FluidAmount amount : fluids) {
            if (amount.amount.compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0) return false;
            FluidStack copy = amount.fluid.copy();
            copy.amount = amount.amount.intValue();
            copies.add(copy);
        }
        return canDeliverBatch(items, copies.toArray(new FluidStack[0]));
    }

    public boolean canDeliverItems(ItemStack[] items) {
        return insertItems(copyItems(inventory), items);
    }

    public boolean deliverItems(ItemStack[] items) {
        if (!canDeliverItems(items)) return false;
        insertItems(inventory, items);
        markDirty();
        return true;
    }

    /** Recovery may exceed physical output capacity: deliver a persisted remainder as pipes drain slots. */
    private void flushItemJournal() {
        List<ItemStack> remainder = new ArrayList<>();
        for (ItemStack stack : pendingItems) {
            if (stack == null) continue;
            int left = stack.stackSize;
            for (int i = 18; i < 36 && left > 0; i++) if (same(inventory[i], stack)) {
                int add = Math.min(left, Math.min(64, stack.getMaxStackSize()) - inventory[i].stackSize);
                inventory[i].stackSize += add;
                left -= add;
            }
            for (int i = 18; i < 36 && left > 0; i++) if (inventory[i] == null) {
                inventory[i] = stack.copy();
                inventory[i].stackSize = Math.min(left, Math.min(64, stack.getMaxStackSize()));
                left -= inventory[i].stackSize;
            }
            if (left > 0) {
                ItemStack remaining = stack.copy();
                remaining.stackSize = left;
                remainder.add(remaining);
            }
        }
        pendingItems = remainder.toArray(new ItemStack[0]);
        markDirty();
    }

    private static boolean insertItems(ItemStack[] target, ItemStack[] items) {
        for (ItemStack stack : items) {
            if (stack == null) continue;
            int left = stack.stackSize;
            for (int i = 18; i < 36 && left > 0; i++) if (same(target[i], stack)) {
                int n = Math.min(left, Math.min(64, stack.getMaxStackSize()) - target[i].stackSize);
                target[i].stackSize += n;
                left -= n;
            }
            for (int i = 18; i < 36 && left > 0; i++) if (target[i] == null) {
                target[i] = stack.copy();
                target[i].stackSize = Math.min(left, Math.min(64, stack.getMaxStackSize()));
                left -= target[i].stackSize;
            }
            if (left > 0) return false;
        }
        return true;
    }

    private static ItemStack[] copyItems(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].copy();
        return result;
    }

    private static FluidStack[] copyFluids(FluidStack[] source) {
        FluidStack[] result = new FluidStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].copy();
        return result;
    }

    private boolean hasFluids(FluidStack[] source) {
        return removeFluids(copyFluids(tanks), source);
    }

    private void consumeFluids(FluidStack[] source) {
        removeFluids(tanks, source);
        markDirty();
    }

    private static boolean removeFluids(FluidStack[] target, FluidStack[] source) {
        for (FluidStack wanted : source) {
            if (wanted == null) continue;
            int remaining = wanted.amount;
            for (int i = 0; i < 8 && remaining > 0; i++) if (target[i] != null && target[i].isFluidEqual(wanted)) {
                int take = Math.min(remaining, target[i].amount);
                remaining -= take;
                target[i].amount -= take;
                if (target[i].amount == 0) target[i] = null;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    public boolean canDeliverFluids(FluidStack[] source) {
        return insertFluids(copyFluids(tanks), source);
    }

    public void deliverFluids(FluidStack[] source) {
        insertFluids(tanks, source);
        markDirty();
    }

    private static boolean insertFluids(FluidStack[] target, FluidStack[] source) {
        for (FluidStack stack : source) {
            if (stack == null) continue;
            int left = stack.amount;
            for (int i = 8; i < 16 && left > 0; i++) if (target[i] != null && target[i].isFluidEqual(stack)) {
                int add = Math.min(left, TANK_CAPACITY - target[i].amount);
                target[i].amount += add;
                left -= add;
            }
            for (int i = 8; i < 16 && left > 0; i++) if (target[i] == null) {
                target[i] = stack.copy();
                target[i].amount = Math.min(left, TANK_CAPACITY);
                left -= target[i].amount;
            }
            if (left > 0) return false;
        }
        return true;
    }

    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        for (int i = 0; i < 8; i++) if (tanks[i] != null && tanks[i].isFluidEqual(resource)) {
            int amount = Math.min(resource.amount, TANK_CAPACITY - tanks[i].amount);
            if (doFill) {
                tanks[i].amount += amount;
                markDirty();
            }
            return amount;
        }
        for (int i = 0; i < 8; i++) if (tanks[i] == null) {
            int amount = Math.min(resource.amount, TANK_CAPACITY);
            if (doFill) {
                tanks[i] = resource.copy();
                tanks[i].amount = amount;
                markDirty();
            }
            return amount;
        }
        return 0;
    }

    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null) return null;
        for (int i = state == 0 && from == ForgeDirection.DOWN ? 0 : 8; i < 16; i++) {
            if (tanks[i] != null && tanks[i].isFluidEqual(resource)) return drainTank(i, resource.amount, doDrain);
        }
        return null;
    }

    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        for (int i = state == 0 && from == ForgeDirection.DOWN ? 0 : 8; i < 16; i++) {
            if (tanks[i] != null) return drainTank(i, maxDrain, doDrain);
        }
        return null;
    }

    private FluidStack drainTank(int slot, int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) return null;
        FluidStack result = tanks[slot].copy();
        result.amount = Math.min(result.amount, maxDrain);
        if (doDrain) {
            tanks[slot].amount -= result.amount;
            if (tanks[slot].amount == 0) tanks[slot] = null;
            markDirty();
        }
        return result;
    }

    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return fluid != null;
    }

    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        for (int i = state == 0 && from == ForgeDirection.DOWN ? 0 : 8; i < 16; i++) {
            if (tanks[i] != null && tanks[i].getFluid() == fluid) return true;
        }
        return false;
    }

    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        FluidTankInfo[] result = new FluidTankInfo[16];
        for (int i = 0; i < 16; i++)
            result[i] = new FluidTankInfo(tanks[i] == null ? null : tanks[i].copy(), TANK_CAPACITY);
        return result;
    }

    public int getSizeInventory() {
        return 36;
    }

    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < 36 ? inventory[slot] : null;
    }

    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null || amount <= 0) return null;
        ItemStack result = stack.splitStack(Math.min(amount, stack.stackSize));
        if (stack.stackSize == 0) inventory[slot] = null;
        markDirty();
        return result;
    }

    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 36) return;
        inventory[slot] = stack != null && stack.stackSize > 0 ? stack : null;
        if (stack != null) stack.stackSize = Math.min(stack.stackSize, Math.min(64, stack.getMaxStackSize()));
        markDirty();
    }

    public String getInventoryName() {
        return "container.gtsr.critical";
    }

    public boolean hasCustomInventoryName() {
        return false;
    }

    public int getInventoryStackLimit() {
        return 64;
    }

    public boolean isUseableByPlayer(EntityPlayer p) {
        return p != null && !p.isDead
            && worldObj != null
            && worldObj.getTileEntity(xCoord, yCoord, zCoord) == host
            && (owner.isEmpty() || owner.equals(
                p.getUniqueID()
                    .toString()))
            && (p.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) <= 64 || isNearLinkedLoom(p));
    }

    private boolean isNearLinkedLoom(EntityPlayer p) {
        if (getGeometryVersion() != 2 || gtMachine() == null) return false;
        int x = blockWorldX(56, loomController ? 35 : 47), z = blockWorldZ(56, loomController ? 35 : 47);
        return worldObj.getChunkProvider()
            .chunkExists(x >> 4, z >> 4) && host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity main
            && main.getFrontFacing() == getControllerFacing(loomController)
            && worldObj
                .getTileEntity(x, yCoord, z) instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity tile
            && tile.getFrontFacing() == getControllerFacing(!loomController)
            && tile.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine other
            && other.isKindSelector() != loomController
            && other.getLocalCriticalState()
                .getGeometryVersion() == 2
            && other.getCriticalState() == this
            && p.getDistanceSq(x + .5, yCoord + .5, z + .5) <= 64;
    }

    public void openInventory() {}

    public void closeInventory() {}

    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot >= 0 && slot < 18;
    }

    public int[] getAccessibleSlotsFromSide(int side) {
        int[] slots = new int[18];
        for (int i = 0; i < 18; i++) slots[i] = side == 0 ? i + 18 : i;
        return slots;
    }

    public boolean canInsertItem(int slot, ItemStack item, int side) {
        return slot >= 0 && slot < 18;
    }

    public boolean canExtractItem(int slot, ItemStack item, int side) {
        return slot >= 18 && slot < 36;
    }

    public void writeToNBT(NBTTagCompound n) {
        n.setTag("criticalStellarRuntime", stellarRuntime);
        n.setTag("criticalBatchRecipe", batchRecipe);
        n.setBoolean("foundationRequired", foundationRequired);
        n.setBoolean("foundationModern", foundationModern);
        n.setInteger("foundationScheduleVersion", foundationScheduleVersion);
        n.setInteger("foundationCuringCheckTicks", foundationCuringCheckTicks);
        n.setBoolean("chunkLoadingEnabled", isChunkLoadingEnabled());
        n.setInteger("geometryVersion", getGeometryVersion());
        n.setInteger("v2LayoutRevision", v2LayoutRevision);
        n.setInteger("structureFacing", getStructureFacing().ordinal());
        n.setBoolean("structureFacingLocked", structureFacingLocked);
        n.setBoolean("loomController", loomController);
        n.setBoolean("loomJournalOwner", loomJournalOwner);
        n.setBoolean("loomOwnedMain", loomOwnedMain);
        n.setBoolean("initialClearanceAccepted", initialClearanceAccepted);

        n.setBoolean("nativeFundingUncertain", nativeFundingUncertain);
        n.setTag("uncertainFundingItems", writeItems(uncertainFundingItems));
        n.setTag("uncertainFundingFluids", writeFluids(uncertainFundingFluids));
        constructionLedger.write(n);
        n.setInteger("constructionTimeVersion", constructionTimeVersion);
        n.setInteger("constructionDebugRequirements", debugRequirements);
        n.setInteger("constructionDebugSpeed", debugSpeed);
        n.setBoolean("constructionDebugIgnoreEnergy", debugIgnoreEnergy);
        n.setString("visualHostId", visualHostId);
        n.setTag("config", config.write());
        n.setString("ownerUuid", owner);
        n.setString("structureId", structureId);
        n.setString("jobId", jobId);
        n.setString("batchId", batchId);
        n.setString("processingMode", processingMode);
        n.setTag("criticalEnergyRuntime", energyRuntime);
        n.setString("status", status);
        n.setInteger("state", state);
        n.setInteger("stage", stage);
        n.setInteger("cursor", cursor);
        n.setBoolean("enabled", enabled);
        n.setBoolean("stageFunded", stageFunded);
        n.setBoolean("refundPrepared", refundPrepared);
        n.setBoolean("dismantleRequested", dismantleRequested);
        n.setLong("energy", energy);
        n.setLong("seed", recoverySeed);
        n.setLong("batchEu", batchEu);
        n.setLong("batchGenerated", batchGenerated);
        n.setLong("pendingGenerated", pendingGenerated);
        n.setInteger("cycleTicks", cycleTicks);
        n.setInteger("cycleDuration", cycleDuration);
        n.setTag("inventory", writeItems(inventory));
        n.setTag("tanks", writeFluids(tanks));
        n.setTag("escrow", writeItems(escrow));
        n.setTag("fluidEscrow", writeFluids(fluidEscrow));
        n.setTag("pendingItems", writeItems(pendingItems));
        n.setTag("pendingFluids", writeFluids(pendingFluids));
        constructionLedger.writeSpent(n);
        n.setByteArray("placed", placed.toByteArray());
        n.setByteArray("repairTargets", repairTargets.toByteArray());
        n.setBoolean("completionValidationPending", completionValidationPending);
        n.setBoolean("repairScanPending", repairScanPending);
        n.setInteger("repairScanCursor", repairScanCursor);
        n.setBoolean("repairFunded", repairFunded);
        n.setTag("repairEscrow", writeItems(repairEscrow));
        n.setLong("jobFingerprint", jobFingerprint);
    }

    public void readFromNBT(NBTTagCompound n) {
        runtimeBlockerKey = "";
        stellarRuntime = n.getCompoundTag("criticalStellarRuntime");
        batchRecipe = n.getCompoundTag("criticalBatchRecipe");
        foundationRequired = n.getBoolean("foundationRequired");
        foundationModern = n.getBoolean("foundationModern");
        foundationScheduleVersion = foundationModern ? CriticalFoundationChunk.VERSION : 0;
        foundationCuringCheckTicks = Math.max(0, Math.min(399, n.getInteger("foundationCuringCheckTicks")));
        chunkLoading.setEnabled(!n.hasKey("chunkLoadingEnabled") || n.getBoolean("chunkLoadingEnabled"));
        protectionDirty = true;
        geometryVersion = n.hasKey("geometryVersion") ? n.getInteger("geometryVersion") : 1;
        v2LayoutRevision = readV2LayoutRevision(n);
        if (getGeometryVersion() == 2 && n.getBoolean("loomJournalOwner") && !hasPersistentLayout(n))
            v2LayoutRevision = Math.max(31, v2LayoutRevision);
        structureFacing = v2LayoutRevision <= 26 ? ForgeDirection.NORTH
            : CriticalStructureAlignment.horizontal(ForgeDirection.getOrientation(n.getInteger("structureFacing")));
        structureFacingLocked = v2LayoutRevision <= 26 || n.getBoolean("structureFacingLocked")
            || hasPersistentLayout(n);
        boundStructureFacing = ForgeDirection.UNKNOWN;
        // Missing flag denotes the existing main-owned layout; its geometry and placement indices stay frozen.
        loomJournalOwner = n.getBoolean("loomJournalOwner");
        loomOwnedMain = n.getBoolean("loomOwnedMain");
        if (n.hasKey("loomController")) loomController = n.getBoolean("loomController");
        initialClearanceAccepted = n.hasKey("initialClearanceAccepted") ? n.getBoolean("initialClearanceAccepted")
            : n.getInteger("state") != 0;
        geometryCache = null;
        hatchPortsCache = null;

        nativeFundingUncertain = n.getBoolean("nativeFundingUncertain");
        uncertainFundingItems = readItems(n.getTagList("uncertainFundingItems", 10), -1);
        uncertainFundingFluids = readFluids(n.getTagList("uncertainFundingFluids", 10), -1);
        constructionLedger.read(n);
        if (constructionLedger.hasIntegrityError()) nativeFundingUncertain = true;
        constructionTimeVersion = n.getInteger("constructionTimeVersion");
        debugRequirements = Math.max(0, Math.min(3, n.getInteger("constructionDebugRequirements")));
        debugSpeed = Math.max(0, Math.min(3, n.getInteger("constructionDebugSpeed")));
        debugIgnoreEnergy = n.getBoolean("constructionDebugIgnoreEnergy");
        if (n.hasKey("visualHostId")) visualHostId = n.getString("visualHostId");
        config = CriticalConfiguration.read(n.getCompoundTag("config"));
        owner = n.hasKey("ownerUuid", 8) ? n.getString("ownerUuid") : n.getString("owner");
        structureId = n.getString("structureId");
        jobId = n.getString("jobId");
        batchId = n.getString("batchId");
        processingMode = n.getString("processingMode");
        energyRuntime = n.getCompoundTag("criticalEnergyRuntime");
        status = n.getString("status");
        state = n.getInteger("state");
        stage = n.getInteger("stage");
        cursor = n.getInteger("cursor");
        enabled = !n.hasKey("enabled") || n.getBoolean("enabled");
        stageFunded = n.getBoolean("stageFunded");
        refundPrepared = n.getBoolean("refundPrepared");
        dismantleRequested = n.getBoolean("dismantleRequested") && (state == 2 || state == 4);
        energy = Math.max(0, Math.min(capacity(), n.getLong("energy")));
        recoverySeed = n.getLong("seed");
        batchEu = n.getLong("batchEu");
        batchGenerated = n.getLong("batchGenerated");
        pendingGenerated = n.getLong("pendingGenerated");
        cycleTicks = n.getInteger("cycleTicks");
        cycleDuration = n.getInteger("cycleDuration");
        System.arraycopy(readItems(n.getTagList("inventory", 10), 36), 0, inventory, 0, 36);
        System.arraycopy(readFluids(n.getTagList("tanks", 10), 16), 0, tanks, 0, 16);
        escrow = readItems(n.getTagList("escrow", 10), -1);
        fluidEscrow = readFluids(n.getTagList("fluidEscrow", 10), -1);
        pendingItems = readItems(n.getTagList("pendingItems", 10), -1);
        pendingFluids = readFluids(n.getTagList("pendingFluids", 10), -1);
        constructionLedger.readSpent(n);
        placed.clear();
        placed.or(BitSet.valueOf(n.getByteArray("placed")));
        repairTargets.clear();
        repairTargets.or(BitSet.valueOf(n.getByteArray("repairTargets")));
        completionValidationPending = n.getBoolean("completionValidationPending");
        repairScanPending = n.getBoolean("repairScanPending");
        repairScanCursor = Math.max(0, n.getInteger("repairScanCursor"));
        repairFunded = n.getBoolean("repairFunded");
        repairEscrow = readItems(n.getTagList("repairEscrow", 10), -1);
        repairStages = null;
        jobFingerprint = n.getLong("jobFingerprint");
        geometryCache = null;
        hatchPortsCache = null;
        loomChunksCache = loomValidationChunksCache = null;
        stageCache.clear();
        paidStageCache.clear();
        voxelIndices.clear();
        structureMonitor.reset();
        integrityOptionalPorts = null;
        structureValid = false;
        structureLoadTicks = 0;
        lastStructureLoadingTick = Long.MIN_VALUE;
    }

    public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, getVisualData());
    }

    /** Side-neutral visual snapshot for renderer anchors and description packets. */
    public NBTTagCompound getVisualData() {
        NBTTagCompound n = new NBTTagCompound();
        n.setBoolean("foundationRequired", foundationRequired);
        n.setBoolean("foundationModern", foundationModern);
        n.setInteger("foundationScheduleVersion", getFoundationScheduleVersion());
        n.setInteger("foundationPhase", getFoundationPhase());
        n.setInteger("foundationCellProgress", getFoundationCellProgressTicks());
        n.setBoolean("foundationCellFunded", isFoundationCellFunded());
        n.setInteger("foundationCuringRemaining", getFoundationCuringRemainingTicks());
        n.setInteger("foundationHeatingRemaining", getFoundationHeatingRemainingTicks());
        n.setBoolean("foundationHeatingFunded", isFoundationHeatingFunded());
        int[] foundationCells = new int[loomChunks().size()];
        for (int i = 0; i < foundationCells.length; i++) foundationCells[i] = getFoundationChunkCompletedCells(i);
        n.setIntArray("foundationCells", foundationCells);
        n.setInteger("geometryVersion", getGeometryVersion());
        n.setInteger("v2LayoutRevision", v2LayoutRevision);
        n.setInteger("structureFacing", getStructureFacing().ordinal());
        n.setBoolean("structureFacingLocked", structureFacingLocked);
        n.setBoolean("loomController", loomController);
        n.setBoolean("loomJournalOwner", loomJournalOwner);
        n.setBoolean("loomOwnedMain", loomOwnedMain);
        n.setString("visualHostId", visualHostId);
        n.setTag("config", config.write());
        n.setInteger("state", state);
        n.setString("status", status);
        n.setInteger("cycleTicks", state == 1 || state == 4 ? getConstructionElapsedTicks() : cycleTicks);
        n.setInteger("cycleDuration", state == 1 || state == 4 ? getConstructionDurationTicks() : cycleDuration);
        n.setBoolean("valid", structureValid);
        n.setBoolean("enabled", isEnabled());
        n.setInteger("stage", stage);
        n.setInteger("cursor", cursor);
        n.setInteger("jobTotal", getJobTotal());
        n.setInteger("reason", getReasonCode());
        n.setBoolean("working", isWorking());
        n.setInteger("constructionElapsed", getConstructionElapsedTicks());
        n.setInteger("constructionDuration", getConstructionDurationTicks());
        n.setInteger("constructionDebugSpeed", debugSpeed);
        n.setBoolean("constructionWorking", isConstructionWorking());
        n.setString("structureId", structureId);
        n.setString("batchId", batchId);
        n.setString("processingMode", processingMode);
        n.setTag("criticalEnergyRuntime", energyRuntime);
        n.setTag("protection", protectionSnapshot());
        return n;
    }

    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        readVisualData(packet.func_148857_g());
    }

    /** Reads only visual fields; never depends on client-only packet accessors. */
    public void readVisualData(NBTTagCompound n) {
        foundationRequired = n.getBoolean("foundationRequired");
        foundationModern = n.getBoolean("foundationModern");
        foundationScheduleVersion = n.hasKey("foundationScheduleVersion") ? n.getInteger("foundationScheduleVersion")
            : foundationModern ? 5 : 0;
        clientFoundationPhase = n.getInteger("foundationPhase");
        clientFoundationCellProgress = n.getInteger("foundationCellProgress");
        clientFoundationCellFunded = n.getBoolean("foundationCellFunded");
        clientFoundationCuringRemaining = n.getInteger("foundationCuringRemaining");
        clientFoundationHeatingRemaining = n.getInteger("foundationHeatingRemaining");
        clientFoundationHeatingFunded = n.getBoolean("foundationHeatingFunded");
        clientFoundationCells = n.getIntArray("foundationCells");
        debugSpeed = Math.max(0, Math.min(3, n.getInteger("constructionDebugSpeed")));
        geometryVersion = n.hasKey("geometryVersion") ? n.getInteger("geometryVersion") : 1;
        v2LayoutRevision = readV2LayoutRevision(n);
        structureFacing = v2LayoutRevision <= 26 ? ForgeDirection.NORTH
            : CriticalStructureAlignment.horizontal(ForgeDirection.getOrientation(n.getInteger("structureFacing")));
        structureFacingLocked = true;
        loomController = n.getBoolean("loomController");
        loomJournalOwner = n.getBoolean("loomJournalOwner");
        loomOwnedMain = n.getBoolean("loomOwnedMain");
        if (n.hasKey("visualHostId")) visualHostId = n.getString("visualHostId");
        config = CriticalConfiguration.read(n.getCompoundTag("config"));
        state = n.getInteger("state");
        status = n.getString("status");
        cycleTicks = n.getInteger("cycleTicks");
        cycleDuration = n.getInteger("cycleDuration");
        structureValid = n.getBoolean("valid");
        enabled = n.getBoolean("enabled");
        stage = n.getInteger("stage");
        cursor = n.getInteger("cursor");
        clientJobTotal = n.getInteger("jobTotal");
        clientReason = n.getInteger("reason");
        clientWorking = n.getBoolean("working") && clientReason == 0;
        structureId = n.getString("structureId");
        batchId = n.getString("batchId");
        processingMode = n.getString("processingMode");
        energyRuntime = n.getCompoundTag("criticalEnergyRuntime");
        clientCycleAnchor = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        if (worldObj != null && worldObj.isRemote) CriticalBlockProtection.readClientSnapshot(this, n);
    }

    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(
            getOriginX(),
            getOriginY(),
            getOriginZ(),
            getOriginX() + getRegionSize(),
            getOriginY() + getRegionHeight(),
            getOriginZ() + getRegionSize());
    }

    public double getMaxRenderDistanceSquared() {
        return 512D * 512D;
    }

}
