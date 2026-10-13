package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.nbt.NBTTagList;
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
    public net.minecraft.world.World worldObj;
    public int xCoord, yCoord, zCoord;

    public void bindHost(TileEntity tile) {
        if (host != tile || xCoord != tile.xCoord || yCoord != tile.yCoord || zCoord != tile.zCoord)
            loomChunksCache = null;
        host = tile;
        worldObj = tile.getWorldObj();
        xCoord = tile.xCoord;
        yCoord = tile.yCoord;
        zCoord = tile.zCoord;
    }

    public TileEntity getHost() {
        return host;
    }

    public net.minecraft.world.World getWorldObj() {
        return worldObj;
    }

    public void markDirty() {
        if (host != null) host.markDirty();
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
    // IDLE, BUILD, READY, DISMANTLE. PAUSED is a reason, never a destructive state transition.
    private int state, stage, cursor, cycleTicks, cycleDuration;
    private long energy, batchEu, batchGenerated, pendingGenerated;
    private boolean enabled = true, stageFunded, refundPrepared;
    private ItemStack[] escrow = new ItemStack[0], pendingItems = new ItemStack[0];
    private FluidStack[] fluidEscrow = new FluidStack[0], pendingFluids = new FluidStack[0];
    private final List<ItemStack> spent = new ArrayList<>();
    private long recoverySeed;
    private int validationClock;
    private boolean structureValid;
    private List<CriticalGeometry.Voxel> geometryCache;
    private List<CriticalGeometry.Voxel> hatchPortsCache;
    private final List<List<CriticalGeometry.Voxel>> stageCache = new ArrayList<>();
    private int scanCursor;
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
    private String pendingConstructionPlayer = "";
    private long pendingConstructionDeadline;
    private String visualHostId = UUID.randomUUID()
        .toString();
    private int geometryVersion;
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
        return getGeometryVersion() == 2 && !loomJournalOwner && !loomOwnedMain;
    }

    public static boolean supportsV2Kind(CriticalMachineKind kind) {
        return kind == CriticalMachineKind.SUN || kind == CriticalMachineKind.SOLAR;
    }

    public int getGeometryVersion() {
        return geometryVersion == 0 ? (supportsV2Kind(config.kind) ? 2 : 1) : geometryVersion;
    }

    public int getControllerX() {
        return getGeometryVersion() == 2 ? 56 : 40;
    }

    public int getControllerY() {
        return getGeometryVersion() == 2 ? 4 : 0;
    }

    public int getRegionSize() {
        return getGeometryVersion() == 2 ? 112 : 80;
    }

    public int getRegionHeight() {
        return getGeometryVersion() == 2 ? 180 : 162;
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
        int rx = x - getOriginX(), ry = y - getOriginY(), rz = z - getOriginZ();
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

    private final CriticalConstructionProgress constructionProgress = new CriticalConstructionProgress();
    private int constructionTimeVersion = 1, debugRequirements, debugSpeed;
    private boolean debugIgnoreEnergy, constructionAdvancing, constructionCostSnapshot, constructionRoundFunded,
        nativeFundingUncertain;
    private ItemStack[] uncertainFundingItems = new ItemStack[0];
    private FluidStack[] uncertainFundingFluids = new FluidStack[0];
    private int constructionRound, repairScanCursor, constructionTickBudget = 1, constructionLastAdvance;
    private boolean repairScanPending, completionValidationPending;
    private ItemStack[] constructionStageCosts = new ItemStack[0];
    private FluidStack[] constructionStageFluidCosts = new FluidStack[0], constructionPaidFluids = new FluidStack[0];

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

    public static final String[] STATUS_KEYS = { "unbuilt", "building", "ready", "dismantling", "repairing" };

    public int getReasonCode() {
        if (worldObj != null && worldObj.isRemote) return clientReason;
        if (status.contains("缺电")) return 1;
        if (status.contains("缺少材料")) return 2;
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
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt ? gt.isAllowedToWork()
            : enabled;
    }

    public boolean isWorking() {
        if (worldObj != null && worldObj.isRemote)
            return clientWorking && (state == 1 || state == 4 || structureValid) && isEnabled();
        if ((state == 1 || state == 4) && constructionTimeVersion > 0) return constructionAdvancing && isEnabled();
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
        return xCoord - getControllerX();
    }

    public int getOriginY() {
        return yCoord - getControllerY();
    }

    public int getControllerZ() {
        return getGeometryVersion() == 2 ? (loomController ? 47 : 35) : CriticalStructures.CONTROLLER_Z;
    }

    public int getOriginZ() {
        return zCoord - getControllerZ();
    }

    public boolean canUse(EntityPlayer p) {
        return !loomOwnedMain && p != null
            && isUseableByPlayer(p)
            && (owner.isEmpty() || owner.equals(
                p.getUniqueID()
                    .toString()));
    }

    public boolean configure(EntityPlayer p, CriticalTier tier, CriticalMachineKind kind, int a, int b, int c) {
        if (worldObj.isRemote || !canUse(p) || state != 0) return false;
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
        loomChunksCache = null;
        clearanceTargets = null;
        clearanceCursor = 0;
        clearPendingClearance();
        changed();
        return true;
    }

    public boolean startConstruction(EntityPlayer p) {
        if (worldObj.isRemote || !canUse(p) || state != 0) return false;
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
        if (getGeometryVersion() == 2 && ((getOriginX() & 15) != 0 || (getOriginZ() & 15) != 0))
            return fail("施工区域原点必须按区块对齐");
        int anchorX = CriticalRenderAnchors.getX(this), anchorY = CriticalRenderAnchors.getY(this),
            anchorZ = CriticalRenderAnchors.getZ(this);
        TileEntity visual = worldObj.getTileEntity(anchorX, anchorY, anchorZ);
        if (!worldObj.isAirBlock(anchorX, anchorY, anchorZ)
            && !(visual instanceof TileEntityCriticalRenderAnchor anchor && anchor.ownedBy(this)))
            return fail("织机前上方渲染锚点位置存在外来方块");
        if (!initialClearanceAccepted && !scanInitialClearance(p)) return false;
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
        initialClearanceAccepted = true;
        CriticalRuntime.unregisterClearance(this);
        clearanceTargets = null;
        jobId = UUID.randomUUID()
            .toString();
        bindTask();
        recoverySeed = new Random().nextLong();
        jobFingerprint = modelFingerprint;
        state = 1;
        constructionTimeVersion = 1;
        resetConstructionStage();
        stage = 0;
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
                    int x = getOriginX() + pos.get(0)
                        .getAsInt(), y = getOriginY()
                            + pos.get(1)
                                .getAsInt()
                            + dy,
                        z = getOriginZ() + pos.get(2)
                            .getAsInt();
                    if (!worldObj.isAirBlock(x, y, z))
                        return clearanceFailed("通道净空被占用，世界(" + x + "," + y + "," + z + ")");
                }
            }
        }
        java.util.Set<Long> targets = new java.util.HashSet<>(clearanceTargetPositions);
        if (worldObj.isAirBlock(
            CriticalRenderAnchors.getX(this),
            CriticalRenderAnchors.getY(this),
            CriticalRenderAnchors.getZ(this)))
            targets.add(
                CriticalGeometry.Voxel.positionKey(
                    CriticalRenderAnchors.getX(this) - getOriginX(),
                    CriticalRenderAnchors.getY(this) - getOriginY(),
                    CriticalRenderAnchors.getZ(this) - getOriginZ()));
        for (Object object : worldObj.playerEntities) if (object instanceof EntityPlayer p && p.boundingBox != null) {
            AxisAlignedBB box = p.boundingBox;
            for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++)
                for (int y = (int) Math.floor(box.minY); y < Math.ceil(box.maxY); y++)
                    for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                        int rx = x - getOriginX(), ry = y - getOriginY(), rz = z - getOriginZ();
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
        if (worldObj.isRemote || !canUse(p) || nativeFundingUncertain || (state != 1 && state != 2)) return false;
        allVoxels();
        if (jobFingerprint != modelFingerprint) return fail("结构定义与任务快照不一致");
        if (!ownsTask()) return fail("施工区任务身份不一致，请用修复按钮重核账本");
        if (!batchId.isEmpty()) return fail("先完成或恢复当前批次");
        if (state == 1 && stageFunded) {
            int total = getJobTotal();
            List<ItemStack> unconsumed = new ArrayList<>();
            for (ItemStack stack : escrow) {
                int consumed = total == 0 ? 0 : (int) (((long) stack.stackSize * cursor + total - 1) / total);
                if (consumed > 0) {
                    ItemStack used = stack.copy();
                    used.stackSize = consumed;
                    spent.add(used);
                }
                if (consumed < stack.stackSize) {
                    ItemStack left = stack.copy();
                    left.stackSize -= consumed;
                    unconsumed.add(left);
                }
            }
            escrow = unconsumed.toArray(new ItemStack[0]);
            stageFunded = false;
        }
        state = 3;
        invalidateStructure();
        CriticalRenderAnchors.ensure(this, isLoomStructureValid());
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
        if (state == 3) return reconcileDismantle();
        if (state != 2) {
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
        if (!value) data().releaseNight(structureId);
        changed();
        return true;
    }

    private boolean fail(String reason) {
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
            if (worldObj.isAirBlock(wx(voxel), wy(voxel), wz(voxel))) continue;
            if (!isUserBuilt(voxel) && (!placed.get(index) || !matches(voxel))) return fail("拆除账本重核发现外来方块，禁止恢复");
            rewind = Math.min(rewind, voxels.size() - 1 - index);
        }
        cursor = rewind;
        scanCursor = 0;
        removalVerified = false;
        status = "拆除账本重核完成";
        changed();
        return true;
    }

    private void changed() {
        markDirty();
        CriticalRenderAnchors.ensure(this, isLoomStructureValid());
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public boolean canRemove() {
        if (state != 0 || !structureId.isEmpty()
            || !batchId.isEmpty()
            || escrow.length != 0
            || pendingItems.length != 0
            || pendingFluids.length != 0
            || pendingGenerated != 0) return false;
        for (ItemStack item : inventory) if (item != null) return false;
        for (FluidStack fluid : tanks) if (fluid != null) return false;
        return true;
    }

    public void onChunkUnload() {
        // Follow GT drillers: Forge persists/unloads tickets, then GT clears owners on server stop.
        // Releasing here runs after Forge's HIGHEST world-unload handler has removed its ticket map.
        if (worldObj != null && !worldObj.isRemote) {
            clearPendingClearance();
            data().releaseNight(structureId);
        }

    }

    public void invalidate() {
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
        return getOriginX() + v.x;
    }

    private int wy(CriticalGeometry.Voxel v) {
        return getOriginY() + v.y;
    }

    private int wz(CriticalGeometry.Voxel v) {
        return getOriginZ() + v.z;
    }

    private List<CriticalGeometry.Voxel> allVoxels() {
        if (geometryCache == null) {
            List<CriticalGeometry.Voxel> result = CriticalStructures.voxels(this);
            geometryCache = Collections.unmodifiableList(result);
            stageCache.clear();
            voxelIndices.clear();
            modelFingerprint = 0xcbf29ce484222325L;
            for (int index = 0; index < result.size(); index++) {
                CriticalGeometry.Voxel v = result.get(index);
                voxelIndices.put(v, index);
                modelFingerprint = (modelFingerprint ^ v.positionKey()) * 0x100000001b3L;
                modelFingerprint = (modelFingerprint ^ v.material.hashCode() ^ v.group.hashCode() ^ v.stage)
                    * 0x100000001b3L;
                while (stageCache.size() <= v.stage) stageCache.add(new ArrayList<>());
                stageCache.get(v.stage)
                    .add(v);
            }
        }
        return geometryCache;
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
        scanCursor = 0;
        validationClock = 0;
        removalVerified = false;
    }

    /** All physical alloy frames in the loom and target are shared GT hatch positions. */
    public List<CriticalGeometry.Voxel> getHatchPorts() {
        if (hatchPortsCache == null) {
            java.util.Map<Long, CriticalGeometry.Voxel> ports = new java.util.LinkedHashMap<>();
            for (CriticalGeometry.Voxel port : allVoxels())
                if (CriticalStructures.isHatchPosition(port)) ports.put(port.positionKey(), port);
            hatchPortsCache = new ArrayList<>(ports.values());
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
        }
        return result;
    }

    public boolean isStandardHatch(CriticalGeometry.Voxel v) {
        if (!CriticalStructures.isHatchPosition(v)) return false;
        if (worldObj == null || !worldObj.getChunkProvider()
            .chunkExists(wx(v) >> 4, wz(v) >> 4)) return false;
        TileEntity tile = worldObj.getTileEntity(wx(v), wy(v), wz(v));
        return isStandardHatchHost(tile);
    }

    public static boolean isStandardHatchHost(TileEntity tile) {
        return tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && isSupportedHatchMeta(gt.getMetaTileEntity());
    }

    /** Physical and native ME stock inputs are supported; dual crafting buffers are excluded. */
    public static boolean isSupportedHatchMeta(gregtech.api.interfaces.metatileentity.IMetaTileEntity mte) {
        if (mte instanceof gregtech.api.metatileentity.implementations.MTEHatchInputBus)
            return !(mte instanceof gregtech.common.tileentities.machines.IDualInputHatch);
        if (mte instanceof gregtech.api.metatileentity.implementations.MTEHatchInput) return true;
        return mte instanceof gregtech.api.metatileentity.implementations.MTEHatchOutputBus
            || mte instanceof gregtech.api.metatileentity.implementations.MTEHatchOutput
            || isEnergyInputHatchMeta(mte)
            || mte instanceof gregtech.api.metatileentity.implementations.MTEHatchDynamo;
    }

    public static boolean isEnergyInputHatchMeta(gregtech.api.interfaces.metatileentity.IMetaTileEntity mte) {
        return mte instanceof gregtech.api.metatileentity.implementations.MTEHatchEnergy
            || mte instanceof tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti multi && multi.getHatchType() == 1;
    }

    private boolean matches(CriticalGeometry.Voxel v) {
        if (CriticalStructures.isController(v)) return new CriticalStructures.ControllerElement(v.z == 47, config.kind)
            .check(gtMachine(), worldObj, wx(v), wy(v), wz(v));
        if (isStandardHatch(v)) return true;
        if (CriticalLoomStructure.isLoomVoxel(v)) return CriticalLoomStructure
            .matchesVoxel(worldObj, getOriginX(), getOriginY(), getOriginZ(), v, config.kind);
        return worldObj.getBlock(wx(v), wy(v), wz(v)) == v.block
            && worldObj.getBlockMetadata(wx(v), wy(v), wz(v)) == v.metadata;
    }

    private List<net.minecraft.world.ChunkCoordIntPair> loomChunks() {
        if (loomChunksCache != null) return new ArrayList<>(loomChunksCache);
        java.util.Map<Long, net.minecraft.world.ChunkCoordIntPair> chunks = new java.util.LinkedHashMap<>();
        net.minecraft.world.ChunkCoordIntPair controller = new net.minecraft.world.ChunkCoordIntPair(
            xCoord >> 4,
            zCoord >> 4);
        chunks.put(((long) controller.chunkXPos << 32) ^ (controller.chunkZPos & 0xffffffffL), controller);
        for (CriticalGeometry.Voxel voxel : getGeometryVersion() == 2 ? Collections.<CriticalGeometry.Voxel>emptyList()
            : CriticalLoomStructure.voxels()) {
            int x = wx(voxel) >> 4, z = wz(voxel) >> 4;
            chunks.put(((long) x << 32) ^ (z & 0xffffffffL), new net.minecraft.world.ChunkCoordIntPair(x, z));
        }
        loomChunksCache = new ArrayList<>(chunks.values());
        return new ArrayList<>(loomChunksCache);
    }

    private boolean ensureConstructionWindow(List<CriticalGeometry.Voxel> voxels, int start, int end) {
        List<net.minecraft.world.ChunkCoordIntPair> chunks = loomChunks();
        for (int index = start; index < end; index++) {
            CriticalGeometry.Voxel voxel = voxels.get(index);
            net.minecraft.world.ChunkCoordIntPair chunk = new net.minecraft.world.ChunkCoordIntPair(
                wx(voxel) >> 4,
                wz(voxel) >> 4);
            if (!chunks.contains(chunk)) chunks.add(chunk);
        }
        return chunkLoading.ensure(worldObj, host, chunks);
    }

    private int loadedWindowEnd(List<CriticalGeometry.Voxel> voxels, int start, int end) {
        List<net.minecraft.world.ChunkCoordIntPair> chunks = loomChunks();
        int limit = Math.min(end, start + 128), index = start;
        for (; index < limit; index++) {
            CriticalGeometry.Voxel voxel = voxels.get(index);
            net.minecraft.world.ChunkCoordIntPair chunk = new net.minecraft.world.ChunkCoordIntPair(
                wx(voxel) >> 4,
                wz(voxel) >> 4);
            if (!chunks.contains(chunk)) {
                if (chunks.size() >= chunkLoading.budget()) break;
                chunks.add(chunk);
            }
        }
        return index > start && chunkLoading.ensure(worldObj, host, chunks) ? index : start == end ? end : -1;
    }

    public net.minecraft.world.ChunkCoordIntPair getActiveConstructionChunk() {
        return chunkLoading.getActiveChunk();
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

    public void updateEntity() {
        constructionAdvancing = false;
        blocksChangedThisTick = lightsChangedThisTick = 0;
        blockBudgetStarted = 0;
        if (worldObj == null || worldObj.isRemote) return;
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
            chunkLoading.release(host);
            return;
        }
        if (!isEnabled()) {
            chunkLoading.release(host);
            fail("已暂停");
            return;
        }
        if (nativeFundingUncertain) {
            chunkLoading.release(host);
            fail("ME扣料结果待人工核验，任务已锁定");
            return;
        }
        if ((state != 2 || completionValidationPending) && chunkLoading.currentSet()
            .isEmpty() && !ensureConstructionWindow(Collections.emptyList(), 0, 0)) {
            fail("施工区块加载等待");
            return;
        }
        if (nativeFundingUncertain) {
            fail("ME扣料结果待人工核验，任务已锁定");
            return;
        }
        if (!isLoomStructureValid()) {
            chunkLoading.release(host);
            invalidateStructure();
            data().releaseNight(structureId);
            fail("必要织机结构缺损，请手动修复织机");
            return;
        }
        allVoxels();
        if (jobFingerprint != modelFingerprint) {
            fail("结构定义与任务快照不一致，禁止推进");
            return;
        }
        if (!ownsTask()) {
            structureValid = false;
            data().releaseNight(structureId);
            fail("区域锁或任务身份不一致，请用修复按钮重核账本");
            return;
        }
        if (state == 2 && !completionValidationPending && !areaLoaded()) {
            structureValid = false;
            fail("区块未全部加载，暂停");
            return;
        }
        if (!isEnabled()) {
            chunkLoading.release(host);
            fail("已暂停");
            return;
        }
        boolean previouslyWorking = isWorking();
        if (state == 1 || state == 4) {
            constructionTickBudget = debugSpeed == 3 ? Integer.MAX_VALUE
                : debugSpeed == 2 ? 10 : debugSpeed == 1 ? 2 : 1;
            boolean anyAdvance = false;
            for (int steps = 0; steps < 8192 && constructionTickBudget > 0
                && blocksChangedThisTick < 128
                && lightsChangedThisTick < 16
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
                if (constructionLastAdvance == 0 && oldStage == stage
                    && oldCursor == cursor
                    && oldRepairScan == repairScanCursor) break;
                if (getReasonCode() != 0) break;
            }
            constructionAdvancing = anyAdvance;
        } else if (state == 3) dismantle();
        else if (state == 2) {
            if (validationClock-- <= 0) {
                List<CriticalGeometry.Voxel> geometry = allVoxels();
                int end = completionValidationPending ? loadedWindowEnd(geometry, scanCursor, geometry.size())
                    : Math.min(geometry.size(), scanCursor + 512);
                if (end < 0) {
                    fail("完工核验区块加载等待");
                    return;
                }
                for (; scanCursor < end; scanCursor++) if (!matches(geometry.get(scanCursor))) {
                    invalidateStructure();
                    data().releaseNight(structureId);
                    fail("必要织机或机器结构缺损，暂停");
                    return;
                }
                if (scanCursor == geometry.size()) {
                    structureValid = true;
                    scanCursor = 0;
                    validationClock = 100;
                    completionValidationPending = false;
                    chunkLoading.release(host);
                }
            }
            if (!structureValid) {
                data().releaseNight(structureId);
                fail("必要织机或机器结构缺损，暂停");
                return;
            }
            emitEnergy();
            runBatch();
        }
        if (previouslyWorking != isWorking() || worldObj.getTotalWorldTime() % 20 == 0) changed();
    }

    public boolean isLoomStructureValid() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
            && machine.isLoomStructureValid();
    }

    private boolean forceLoomStructureCheck() {
        return host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine
            && machine.forceLoomStructureCheck();
    }

    private boolean isUserBuilt(CriticalGeometry.Voxel voxel) {
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
        return worldObj != null && worldObj.isRemote ? cycleTicks : constructionProgress.elapsed();
    }

    public int getConstructionDurationTicks() {
        return CriticalConstructionProgress.STAGE_DURATION;
    }

    private boolean ignoresConstructionItems() {
        return debugRequirements == 1 || debugRequirements == 3;
    }

    private boolean ignoresConstructionFluids() {
        return debugRequirements == 2 || debugRequirements == 3;
    }

    private void resetConstructionStage() {
        constructionProgress.reset();
        constructionRound = 0;
        constructionRoundFunded = false;
        constructionCostSnapshot = false;
        constructionStageCosts = new ItemStack[0];
        constructionStageFluidCosts = new FluidStack[0];
    }

    private ItemStack[] constructionItemIncrement(ItemStack[] totals, int next) {
        List<ItemStack> costs = new ArrayList<>();
        for (ItemStack total : totals) if (total != null) {
            int delta = CriticalConstructionProgress.cumulative(total.stackSize, next)
                - CriticalConstructionProgress.cumulative(total.stackSize, constructionProgress.elapsed());
            if (delta > 0) {
                ItemStack item = total.copy();
                item.stackSize = delta;
                costs.add(item);
            }
        }
        return costs.toArray(new ItemStack[0]);
    }

    private FluidStack[] constructionFluidIncrement() {
        List<FluidStack> costs = new ArrayList<>();
        int rounds = constructionRoundCount(constructionStageCosts);
        for (FluidStack total : constructionStageFluidCosts) if (total != null) {
            int delta = (int) ((long) total.amount * (constructionRound + 1) / rounds
                - (long) total.amount * constructionRound / rounds);
            if (delta > 0) {
                FluidStack fluid = total.copy();
                fluid.amount = delta;
                costs.add(fluid);
            }
        }
        return costs.toArray(new FluidStack[0]);
    }

    public FluidStack[] getConstructionStageFluidCostsForGui() {
        return getConstructionFluidCostsForGui();
    }

    public FluidStack[] getConstructionFluidCostsForGui() {
        if (constructionTimeVersion == 0 && (stageFunded || repairFunded)) return new FluidStack[0];
        if (state != 0 && state != 1 && state != 4) return new FluidStack[0];
        if (constructionCostSnapshot) return copyFluids(constructionStageFluidCosts);
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

    private int constructionRoundCount(ItemStack[] costs) {
        long count = 0;
        for (ItemStack item : costs) if (item != null) count += item.stackSize;
        return (int) Math.max(1L, Math.min(200L, count));
    }

    private ItemStack[] constructionRoundItems(ItemStack[] totals) {
        long units = 0;
        for (ItemStack item : totals) if (item != null) units += item.stackSize;
        int rounds = constructionRoundCount(totals);
        long start = units * constructionRound / rounds, end = units * (constructionRound + 1) / rounds, offset = 0;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : totals) if (item != null) {
            long overlap = Math.max(0, Math.min(end, offset + item.stackSize) - Math.max(start, offset));
            if (overlap > 0) {
                ItemStack cost = item.copy();
                cost.stackSize = (int) overlap;
                items.add(cost);
            }
            offset += item.stackSize;
        }
        return items.toArray(new ItemStack[0]);
    }

    public boolean isCompletionValidationPending() {
        return completionValidationPending;
    }

    public boolean isConstructionRoundFunded() {
        return constructionRoundFunded;
    }

    public int getConstructionRoundIndex() {
        return constructionRound;
    }

    public int getConstructionRoundTotal() {
        return constructionRoundCount(constructionCostSnapshot ? constructionStageCosts : getConstructionCostsForGui());
    }

    public int getConstructionRoundElapsedTicks() {
        return Math.max(
            0,
            getConstructionElapsedTicks()
                - CriticalConstructionProgress.STAGE_DURATION * constructionRound / getConstructionRoundTotal());
    }

    public int getConstructionRoundDurationTicks() {
        int rounds = getConstructionRoundTotal();
        return CriticalConstructionProgress.STAGE_DURATION * (constructionRound + 1) / rounds
            - CriticalConstructionProgress.STAGE_DURATION * constructionRound / rounds;
    }

    public ItemStack[] getConstructionNextItemCostsForGui() {
        if (ignoresConstructionItems()) return new ItemStack[0];
        ItemStack[] costs = constructionCostSnapshot ? constructionStageCosts : getConstructionCostsForGui();
        return constructionTimeVersion > 0 ? constructionRoundItems(costs) : costs;
    }

    public FluidStack[] getConstructionNextFluidCostsForGui() {
        if (ignoresConstructionFluids()) return new FluidStack[0];
        if (constructionTimeVersion == 0 && (stageFunded || repairFunded)) return new FluidStack[0];
        if (constructionCostSnapshot) return constructionFluidIncrement();
        FluidStack[] totals = getConstructionFluidCostsForGui();
        int rounds = constructionRoundCount(getConstructionCostsForGui());
        List<FluidStack> costs = new ArrayList<>();
        for (FluidStack total : totals) {
            int delta = (int) ((long) total.amount * (constructionRound + 1) / rounds
                - (long) total.amount * constructionRound / rounds);
            if (delta > 0) {
                FluidStack fluid = total.copy();
                fluid.amount = delta;
                costs.add(fluid);
            }
        }
        return costs.toArray(new FluidStack[0]);
    }

    public ItemStack[] getConstructionMissingItemsForGui() {
        List<ItemStack> missing = new ArrayList<>();
        if (constructionRoundFunded) return new ItemStack[0];
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
        if (constructionRoundFunded) return new FluidStack[0];
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

    private boolean fundCriticalInputs(ItemStack[] items, FluidStack[] fluids) {
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

    private void recordConstructionItems(ItemStack[] paid) {
        for (ItemStack item : paid) {
            boolean merged = false;
            for (ItemStack existing : spent)
                if (same(existing, item) && existing.stackSize <= Integer.MAX_VALUE - item.stackSize) {
                    existing.stackSize += item.stackSize;
                    merged = true;
                    break;
                }
            if (!merged) spent.add(item.copy());
        }
    }

    private void recordConstructionFluids(FluidStack[] paid) {
        for (FluidStack fluid : paid) {
            boolean merged = false;
            for (FluidStack existing : constructionPaidFluids)
                if (existing.isFluidEqual(fluid) && existing.amount <= Integer.MAX_VALUE - fluid.amount) {
                    existing.amount += fluid.amount;
                    merged = true;
                    break;
                }
            if (!merged) {
                FluidStack[] next = Arrays.copyOf(constructionPaidFluids, constructionPaidFluids.length + 1);
                next[next.length - 1] = fluid.copy();
                constructionPaidFluids = next;
            }
        }
    }

    private void advanceConstruction(List<CriticalGeometry.Voxel> current, boolean repairing) {
        if (current.isEmpty()) {
            stage++;
            cursor = 0;
            resetConstructionStage();
            changed();
            return;
        }
        if (!constructionCostSnapshot) {
            int count = 0;
            for (CriticalGeometry.Voxel voxel : current) if (!isUserBuilt(voxel)) count++;
            if (count == 0) {
                stage++;
                cursor = 0;
                resetConstructionStage();
                changed();
                return;
            }
            constructionStageCosts = CriticalConstructionCosts.forStage(config, stage, count);
            constructionStageFluidCosts = CriticalConstructionCosts.forStageFluids(config, stage, count);
            constructionCostSnapshot = true;
        }
        int rounds = constructionRoundCount(constructionStageCosts);
        int roundEnd = CriticalConstructionProgress.STAGE_DURATION * (constructionRound + 1) / rounds;
        int next = (int) Math.min(roundEnd, (long) constructionProgress.elapsed() + constructionTickBudget);
        int roundVoxelEnd = (int) ((long) current.size() * roundEnd / CriticalConstructionProgress.STAGE_DURATION);
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
        int advance = next - constructionProgress.elapsed();
        long eu = config.tier.constructionVoltage() * advance;
        if (!constructionRoundFunded) {
            ItemStack[] items = ignoresConstructionItems() ? new ItemStack[0]
                : constructionRoundItems(constructionStageCosts);
            FluidStack[] fluids = ignoresConstructionFluids() ? new FluidStack[0] : constructionFluidIncrement();
            if (!debugIgnoreEnergy && !canConsumeConstructionEnergy(eu)) {
                fail("施工缺电，暂停");
                return;
            }
            if (!fundCriticalInputs(items, fluids)) return;
            recordConstructionItems(items);
            recordConstructionFluids(fluids);
            constructionRoundFunded = true;
            markDirty();
        }
        if (advance > 0 && !debugIgnoreEnergy && (getEnergy() < eu || !consumeEnergy(eu))) {
            fail("施工缺电，暂停");
            return;
        }
        // Persist payment and elapsed together before any retryable placement. A failed setBlock cannot pay twice.
        constructionProgress.advance(next);
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
            placed.set(voxelIndices.get(voxel));
        }
        constructionAdvancing = advance > 0;
        status = (repairing ? "修复阶段 " : "施工阶段 ") + stage
            + " "
            + next
            + "/"
            + CriticalConstructionProgress.STAGE_DURATION;
        if (cursor < roundVoxelEnd) {
            markDirty();
            return;
        }
        constructionRoundFunded = false;
        constructionRound++;
        if (next == CriticalConstructionProgress.STAGE_DURATION && cursor == current.size()) {
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
                repairStages.get(v.stage)
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
            if (!isUserBuilt(v)) placed.set(voxelIndices.get(v));
            cursor++;
        }
        status = "修复阶段 " + stage + " " + cursor + "/" + current.size();
        markDirty();
        if (cursor == current.size()) {
            for (ItemStack stack : repairEscrow) spent.add(stack.copy());
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
            completionValidationPending = true;
            invalidateStructure();
            status = "完工核验中";
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
            if (!isUserBuilt(v)) placed.set(voxelIndices.get(v));
            cursor++;
        }
        status = "施工阶段 " + stage + " " + cursor + "/" + current.size();
        markDirty();
        if (cursor == current.size()) {
            for (ItemStack item : escrow) spent.add(item.copy());
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
        return blocksChangedThisTick < 128
            && (blockBudgetStarted == 0 || System.nanoTime() - blockBudgetStarted < 6_000_000L)
            && (lightsChangedThisTick < 16 || v.block.getLightValue(worldObj, wx(v), wy(v), wz(v)) == 0);
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
            ? new CriticalStructures.ControllerElement(v.z == 47, config.kind)
                .placeController(gtMachine(), worldObj, wx(v), wy(v), wz(v))
            : worldObj.setBlock(wx(v), wy(v), wz(v), v.block, v.metadata, 2);
        if (!placedBlock) return false;
        blocksChangedThisTick++;
        if (v.block.getLightValue(worldObj, wx(v), wy(v), wz(v)) > 0) lightsChangedThisTick++;
        return true;
    }

    private void dismantle() {
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        long cost = config.tier.constructionVoltage();
        if (cursor < voxels.size()) {
            if (getEnergy() < cost) {
                fail("拆除缺电");
                return;
            }
            if (!consumeEnergy(cost)) return;
        }
        List<CriticalGeometry.Voxel> reversed = new ArrayList<>(voxels);
        Collections.reverse(reversed);
        int loadedEnd = loadedWindowEnd(reversed, cursor, reversed.size());
        if (loadedEnd < 0) {
            fail("拆除区块加载等待");
            return;
        }
        for (int i = 0; i < 128 && cursor < loadedEnd; i++) {
            CriticalGeometry.Voxel v = voxels.get(voxels.size() - 1 - cursor);
            if (!isUserBuilt(v) && placed.get(voxels.size() - 1 - cursor)
                && !worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                if (!matches(v)) {
                    fail("拆除冲突，目标不是已登记结构方块");
                    return;
                }
                if (!hasBlockBudget(v)) return;
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
        }
        markDirty();
        status = "拆除 " + cursor + "/" + voxels.size();
        if (cursor < voxels.size()) return;
        if (!removalVerified) {
            int end = loadedWindowEnd(voxels, scanCursor, voxels.size());
            if (end < 0) {
                fail("区域残留区块加载等待");
                return;
            }
            for (; scanCursor < end; scanCursor++) {
                CriticalGeometry.Voxel v = voxels.get(scanCursor);
                if (!isUserBuilt(v) && !worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                    fail("区域残留核验失败");
                    return;
                }
            }
            if (scanCursor < voxels.size()) return;
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
            Random random = new Random(recoverySeed);
            for (ItemStack stack : spent) {
                int count = 0;
                for (int i = 0; i < stack.stackSize; i++) if (random.nextInt(10) == 0) count++;
                if (count > 0) {
                    ItemStack r = stack.copy();
                    r.stackSize = count;
                    refund.add(r);
                }
            }
            pendingItems = refund.toArray(new ItemStack[0]);
            escrow = new ItemStack[0];
            spent.clear();
            pendingFluids = fluidRefund.toArray(new FluidStack[0]);
            refundPrepared = true;
            changed();
        }
        flushItemJournal();
        if (pendingItems.length > 0 || !canDeliverFluids(pendingFluids)) {
            fail("拆除回收等待输出空间");
            return;
        }
        deliverFluids(pendingFluids);
        pendingFluids = new FluidStack[0];
        CriticalRenderAnchors.remove(this);
        chunkLoading.release(host);
        data().release(structureId);
        state = 0;
        invalidateStructure();
        initialClearanceAccepted = false;
        clearanceCursor = 0;
        clearanceTargets = null;
        clearanceRechecks.clear();
        constructionPaidFluids = new FluidStack[0];
        completionValidationPending = false;
        structureId = "";
        jobId = "";
        cursor = 0;
        stage = 0;
        refundPrepared = false;
        placed.clear();
        repairTargets.clear();
        scanCursor = 0;
        removalVerified = false;
        jobFingerprint = 0;
        visualHostId = UUID.randomUUID()
            .toString();
        status = "拆除完成";
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
        if (config.kind == CriticalMachineKind.SOLAR) {
            if (!data().claimNight(worldObj, structureId)) {
                fail("本维度已有先启动的辐照锅炉");
                return;
            }
        }
        if (batchId.isEmpty()) {
            ItemStack[] inputs = gtMachine() == null ? new ItemStack[0] : gtMachine().criticalPhysicalItemsSnapshot();
            FluidStack[] fluids = gtMachine() == null ? new FluidStack[0]
                : gtMachine().criticalPhysicalFluidsSnapshot();
            RecipeMatch match = CriticalRecipes.match(config, inputs, fluids);
            if (match == null) match = matchCriticalHatchRecipe(inputs, fluids);
            if (match == null) {
                status = "没有完整可执行批次";
                return;
            }
            if (match.generatedEu < 0 || match.generatedEu > capacity() - getBufferedEnergy() - pendingGenerated) {
                fail("发电批次容量不足，先释放储能");
                return;
            }
            if (!canDeliverItems(match.itemOutputs) || !canDeliverFluids(match.fluidOutputs)) {
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
            cycleTicks = 0;
            cycleDuration = match.durationTicks;
            batchId = UUID.randomUUID()
                .toString();
            status = "批次开工";
            changed();
        }
        if (cycleTicks < cycleDuration) {
            if (getEnergy() < batchEu) {
                fail("批次缺电，输入已保留在本机escrow");
                return;
            }
            if (!consumeEnergy(batchEu)) return;
            cycleTicks++;
            status = "工作 " + cycleTicks + "/" + cycleDuration;
            markDirty();
            return;
        }
        if (!canDeliverItems(pendingItems) || !canDeliverFluids(pendingFluids)) {
            fail("等待批次输出空间");
            return;
        }
        deliverItems(pendingItems);
        deliverFluids(pendingFluids);
        pendingGenerated += batchGenerated;
        escrow = new ItemStack[0];
        fluidEscrow = new FluidStack[0];
        pendingItems = new ItemStack[0];
        pendingFluids = new FluidStack[0];
        batchId = "";
        batchGenerated = 0;
        cycleTicks = 0;
        cycleDuration = 0;
        status = "批次交付完成";
        changed();
    }

    private long capacity() {
        return config.tier.recipeVoltage() * 4096L;
    }

    private void emitEnergy() {
        if (host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine) {
            pendingGenerated -= machine.emitCriticalEnergy(pendingGenerated);
        }
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

    private boolean canDeliverItems(ItemStack[] items) {
        return insertItems(copyItems(inventory), items);
    }

    private boolean deliverItems(ItemStack[] items) {
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

    private boolean canDeliverFluids(FluidStack[] source) {
        return insertFluids(copyFluids(tanks), source);
    }

    private void deliverFluids(FluidStack[] source) {
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
        int z = zCoord + (loomController ? -12 : 12);
        return worldObj.getChunkProvider()
            .chunkExists(xCoord >> 4, z >> 4)
            && host instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity main
            && main.getFrontFacing() == (loomController ? ForgeDirection.NORTH : ForgeDirection.SOUTH)
            && worldObj
                .getTileEntity(xCoord, yCoord, z) instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity tile
            && tile.getFrontFacing() == (loomController ? ForgeDirection.SOUTH : ForgeDirection.NORTH)
            && tile.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine other
            && other.isKindSelector() != loomController
            && other.getLocalCriticalState()
                .getGeometryVersion() == 2
            && other.getCriticalState() == this
            && p.getDistanceSq(xCoord + .5, yCoord + .5, z + .5) <= 64;
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

    private static NBTTagList writeItems(ItemStack[] items) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < items.length; i++) if (items[i] != null) {
            NBTTagCompound t = new NBTTagCompound();
            items[i].writeToNBT(t);
            // Vanilla Count is a byte. Escrow and journal deliberately retain full counts.
            t.setInteger("fullCount", items[i].stackSize);
            t.setInteger("slot", i);
            list.appendTag(t);
        }
        return list;
    }

    private static ItemStack[] readItems(NBTTagList list, int length) {
        int size = length;
        if (length < 0) {
            size = 0;
            for (int i = 0; i < list.tagCount(); i++) size = Math.max(
                size,
                list.getCompoundTagAt(i)
                    .getInteger("slot") + 1);
        }
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getInteger("slot");
            if (slot < 0 || slot >= size) continue;
            ItemStack item = ItemStack.loadItemStackFromNBT(t);
            if (item != null) {
                item.stackSize = t.hasKey("fullCount") ? t.getInteger("fullCount") : item.stackSize;
                if (item.stackSize > 0) items[slot] = item;
            }
        }
        return items;
    }

    private static NBTTagList writeFluids(FluidStack[] fluids) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < fluids.length; i++) if (fluids[i] != null) {
            NBTTagCompound t = new NBTTagCompound();
            fluids[i].writeToNBT(t);
            t.setInteger("slot", i);
            list.appendTag(t);
        }
        return list;
    }

    private static FluidStack[] readFluids(NBTTagList list, int length) {
        int size = length;
        if (length < 0) {
            size = 0;
            for (int i = 0; i < list.tagCount(); i++) size = Math.max(
                size,
                list.getCompoundTagAt(i)
                    .getInteger("slot") + 1);
        }
        FluidStack[] fluids = new FluidStack[size];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            int slot = t.getInteger("slot");
            if (slot >= 0 && slot < size) fluids[slot] = FluidStack.loadFluidStackFromNBT(t);
        }
        return fluids;
    }

    public void writeToNBT(NBTTagCompound n) {
        n.setInteger("geometryVersion", getGeometryVersion());
        n.setBoolean("loomController", loomController);
        n.setBoolean("loomJournalOwner", loomJournalOwner);
        n.setBoolean("loomOwnedMain", loomOwnedMain);
        n.setBoolean("initialClearanceAccepted", initialClearanceAccepted);

        n.setBoolean("nativeFundingUncertain", nativeFundingUncertain);
        n.setTag("uncertainFundingItems", writeItems(uncertainFundingItems));
        n.setTag("uncertainFundingFluids", writeFluids(uncertainFundingFluids));
        n.setBoolean("constructionRoundFunded", constructionRoundFunded);
        n.setInteger("constructionRound", constructionRound);
        n.setInteger("constructionTimeVersion", constructionTimeVersion);
        constructionProgress.write(n);
        n.setBoolean("constructionCostSnapshot", constructionCostSnapshot);
        n.setTag("constructionStageCosts", writeItems(constructionStageCosts));
        n.setTag("constructionStageFluidCosts", writeFluids(constructionStageFluidCosts));
        n.setTag("constructionPaidFluids", writeFluids(constructionPaidFluids));
        n.setInteger("constructionDebugRequirements", debugRequirements);
        n.setInteger("constructionDebugSpeed", debugSpeed);
        n.setBoolean("constructionDebugIgnoreEnergy", debugIgnoreEnergy);
        n.setString("visualHostId", visualHostId);
        n.setTag("config", config.write());
        n.setString("owner", owner);
        n.setString("structureId", structureId);
        n.setString("jobId", jobId);
        n.setString("batchId", batchId);
        n.setString("status", status);
        n.setInteger("state", state);
        n.setInteger("stage", stage);
        n.setInteger("cursor", cursor);
        n.setBoolean("enabled", enabled);
        n.setBoolean("stageFunded", stageFunded);
        n.setBoolean("refundPrepared", refundPrepared);
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
        n.setTag("spent", writeItems(spent.toArray(new ItemStack[0])));
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
        geometryVersion = n.hasKey("geometryVersion") ? n.getInteger("geometryVersion") : 1;
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
        constructionRoundFunded = n.getBoolean("constructionRoundFunded");
        constructionRound = Math.max(0, n.getInteger("constructionRound"));
        constructionTimeVersion = n.getInteger("constructionTimeVersion");
        constructionProgress.read(n);
        constructionCostSnapshot = n.getBoolean("constructionCostSnapshot");
        constructionStageCosts = readItems(n.getTagList("constructionStageCosts", 10), -1);
        constructionStageFluidCosts = readFluids(n.getTagList("constructionStageFluidCosts", 10), -1);
        constructionPaidFluids = readFluids(n.getTagList("constructionPaidFluids", 10), -1);
        debugRequirements = Math.max(0, Math.min(3, n.getInteger("constructionDebugRequirements")));
        debugSpeed = Math.max(0, Math.min(3, n.getInteger("constructionDebugSpeed")));
        debugIgnoreEnergy = n.getBoolean("constructionDebugIgnoreEnergy");
        if (n.hasKey("visualHostId")) visualHostId = n.getString("visualHostId");
        config = CriticalConfiguration.read(n.getCompoundTag("config"));
        owner = n.getString("owner");
        structureId = n.getString("structureId");
        jobId = n.getString("jobId");
        batchId = n.getString("batchId");
        status = n.getString("status");
        state = n.getInteger("state");
        stage = n.getInteger("stage");
        cursor = n.getInteger("cursor");
        enabled = !n.hasKey("enabled") || n.getBoolean("enabled");
        stageFunded = n.getBoolean("stageFunded");
        refundPrepared = n.getBoolean("refundPrepared");
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
        spent.clear();
        for (ItemStack item : readItems(n.getTagList("spent", 10), -1)) if (item != null) spent.add(item);
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
        loomChunksCache = null;
        stageCache.clear();
        voxelIndices.clear();
        scanCursor = 0;
        validationClock = 0;
        structureValid = false;
    }

    public Packet getDescriptionPacket() {
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, getVisualData());
    }

    /** Side-neutral visual snapshot for renderer anchors and description packets. */
    public NBTTagCompound getVisualData() {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("geometryVersion", getGeometryVersion());
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
        n.setBoolean("constructionWorking", isConstructionWorking());
        n.setString("structureId", structureId);
        n.setString("batchId", batchId);
        return n;
    }

    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        readVisualData(packet.func_148857_g());
    }

    /** Reads only visual fields; never depends on client-only packet accessors. */
    public void readVisualData(NBTTagCompound n) {
        geometryVersion = n.hasKey("geometryVersion") ? n.getInteger("geometryVersion") : 1;
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
        clientCycleAnchor = worldObj == null ? 0 : worldObj.getTotalWorldTime();
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
