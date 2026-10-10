package com.miaokatze.gtsr.common.critical;

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

/** All input, escrow and delivery state is saved in one tile NBT, never across independent hatches. */
public class TileEntityCriticalController extends TileEntity implements ISidedInventory, IFluidHandler,
    IEnergyConnected, com.gtnewhorizon.structurelib.alignment.constructable.IConstructable {

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

    public long getEnergy() {
        return energy;
    }

    public CriticalConfiguration getConfiguration() {
        return config;
    }

    public long getEnergyStored() {
        return energy;
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
        if (status.contains("空间") || status.contains("容量")) return 5;
        if (status.contains("冲突") || status.contains("身份") || status.contains("锁")) return 6;
        if (!enabled) return 7;
        if (status.contains("先启动")) return 8;
        return 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isWorking() {
        if (worldObj != null && worldObj.isRemote) return clientWorking && structureValid && enabled;
        return state == 2 && structureValid
            && cycleDuration > 0
            && cycleTicks < cycleDuration
            && enabled
            && energy >= batchEu
            && getReasonCode() == 0;
    }

    public String getStructureId() {
        return structureId;
    }

    public double getVisualCycleTicks(float partialTicks) {
        double elapsed = worldObj != null && worldObj.isRemote && isWorking()
            ? Math.max(0L, worldObj.getTotalWorldTime() - clientCycleAnchor) + partialTicks
            : 0D;
        return Math.min(cycleDuration, Math.max(0D, cycleTicks + elapsed));
    }

    public int getCycleTicks() {
        return cycleTicks;
    }

    public int getCycleDuration() {
        return cycleDuration;
    }

    public int getOriginX() {
        return xCoord - 40;
    }

    public int getOriginY() {
        return yCoord;
    }

    public int getOriginZ() {
        return zCoord - 32;
    }

    public boolean canUse(EntityPlayer p) {
        return p != null && isUseableByPlayer(p)
            && (owner.isEmpty() || owner.equals(
                p.getUniqueID()
                    .toString()));
    }

    public boolean configure(EntityPlayer p, CriticalTier tier, CriticalMachineKind kind, int a, int b, int c) {
        if (worldObj.isRemote || !canUse(p) || state != 0) return false;
        try {
            CriticalConfiguration next = new CriticalConfiguration(tier, kind, a, b, c);
            long nextCapacity = next.tier.recipeVoltage() * 4096L;
            if (energy > nextCapacity || pendingGenerated > nextCapacity - energy) {
                return fail("储能容量不足，禁止降低等级");
            }
            config = next;
        } catch (IllegalArgumentException e) {
            return false;
        }
        owner = p.getUniqueID()
            .toString();
        geometryCache = null;
        changed();
        return true;
    }

    public boolean startConstruction(EntityPlayer p) {
        if (worldObj.isRemote || !canUse(p) || state != 0) return false;
        if (getOriginY() < 0 || getOriginY() + 161 > worldObj.getHeight()) return fail("施工高度超出世界");
        if (!areaLoaded()) return fail("80×80施工区未全部加载");
        for (CriticalGeometry.Voxel v : allVoxels()) {
            if (!worldObj.isAirBlock(wx(v), wy(v), wz(v)))
                return fail("目标区域存在方块: " + wx(v) + "," + wy(v) + "," + wz(v));
            if (!worldObj.canMineBlock(p, wx(v), wy(v), wz(v))) return fail("没有施工权限");
        }
        owner = p.getUniqueID()
            .toString();
        if (structureId.isEmpty()) structureId = UUID.randomUUID()
            .toString();
        if (!data().acquire(structureId, owner, getOriginX(), getOriginY(), getOriginZ())) return fail("施工区与已登记区域重叠");
        jobId = UUID.randomUUID()
            .toString();
        bindTask();
        recoverySeed = new Random().nextLong();
        jobFingerprint = modelFingerprint;
        state = 1;
        stage = 0;
        cursor = 0;
        stageFunded = false;
        refundPrepared = false;
        status = "施工中";
        changed();
        return true;
    }

    public boolean startDismantle(EntityPlayer p) {
        if (worldObj.isRemote || !canUse(p) || (state != 1 && state != 2)) return false;
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
        if (worldObj.isRemote || !canUse(p) || state == 0 || !areaLoaded()) return false;
        allVoxels();
        if (jobFingerprint != modelFingerprint) return fail("结构定义与任务快照不一致");
        if (!ownsTask() && !recoverTask(p)) return false;
        if (state == 3) return reconcileDismantle();
        if (state != 2) {
            status = "任务账本重核完成";
            changed();
            return true;
        }
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        repairTargets.clear();
        for (int i = 0; i < voxels.size(); i++) {
            CriticalGeometry.Voxel v = voxels.get(i);
            if (!matches(v)) {
                if (!worldObj.isAirBlock(wx(v), wy(v), wz(v)) || !worldObj.canMineBlock(p, wx(v), wy(v), wz(v))) {
                    repairTargets.clear();
                    return fail("修复冲突，禁止覆盖外来方块");
                }
                repairTargets.set(i);
            }
        }
        if (repairTargets.isEmpty()) {
            invalidateStructure();
            return true;
        }
        repairStages = null;
        state = 4;
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
        if (!canUse(player) || worldObj.getTileEntity(xCoord, yCoord, zCoord) != this || jobId.isEmpty()) return false;
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        for (int index = placed.nextSetBit(0); index >= 0; index = placed.nextSetBit(index + 1)) {
            if (index >= voxels.size()) return fail("任务位置账本越界");
            CriticalGeometry.Voxel voxel = voxels.get(index);
            boolean air = worldObj.isAirBlock(wx(voxel), wy(voxel), wz(voxel));
            if (state == 3 ? !air && !matches(voxel) : !matches(voxel) && !(air && (state == 2 || state == 4))) {
                return fail("任务账本重核发现方块冲突，禁止恢复");
            }
        }
        if (!data().acquire(structureId, owner, getOriginX(), getOriginY(), getOriginZ()) || !bindTask()) {
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
            if (!placed.get(index) || !matches(voxel)) return fail("拆除账本重核发现外来方块，禁止恢复");
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

    @Override
    public void onChunkUnload() {
        if (worldObj != null && !worldObj.isRemote) data().releaseNight(structureId);
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        if (worldObj != null && !worldObj.isRemote) data().releaseNight(structureId);
        super.invalidate();
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
            List<CriticalGeometry.Voxel> result = new ArrayList<>(CriticalGeometry.getLoom());
            result.addAll(CriticalGeometry.get(config.tier, config.kind));
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

    public void invalidateStructure() {
        structureValid = false;
        scanCursor = 0;
        validationClock = 0;
        removalVerified = false;
    }

    private boolean matches(CriticalGeometry.Voxel v) {
        return worldObj.getBlock(wx(v), wy(v), wz(v)) == v.block
            && worldObj.getBlockMetadata(wx(v), wy(v), wz(v)) == v.metadata;
    }

    private boolean areaLoaded() {
        for (int x = getOriginX() >> 4; x <= (getOriginX() + 79) >> 4; x++) {
            for (int z = getOriginZ() >> 4; z <= (getOriginZ() + 79) >> 4; z++) {
                if (!worldObj.getChunkProvider()
                    .chunkExists(x, z)) return false;
            }
        }
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || state == 0) return;
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
        if (!areaLoaded()) {
            structureValid = false;
            fail("区块未全部加载，暂停");
            return;
        }
        if (!enabled) {
            fail("已暂停");
            return;
        }
        boolean previouslyWorking = isWorking();
        if (state == 1) build();
        else if (state == 3) dismantle();
        else if (state == 4) repair();
        else if (state == 2) {
            if (validationClock-- <= 0) {
                List<CriticalGeometry.Voxel> geometry = allVoxels();
                int end = Math.min(geometry.size(), scanCursor + 512);
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

    private void repair() {
        List<CriticalGeometry.Voxel> voxels = allVoxels();
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
            invalidateStructure();
            changed();
            return;
        }
        List<CriticalGeometry.Voxel> current = repairStages.get(stage);
        if (current.isEmpty()) {
            stage++;
            cursor = 0;
            changed();
            return;
        }
        if (!repairFunded) {
            ItemStack[] costs = CriticalConstructionCosts.forStage(config, stage, current.size());
            if (!consumeItems(costs)) {
                fail("修复阶段缺少材料");
                return;
            }
            repairEscrow = copyItems(costs);
            repairFunded = true;
            changed();
        }
        long cost = config.tier.constructionVoltage();
        if (energy < cost) {
            fail("修复缺电");
            return;
        }
        energy -= cost;
        for (int i = 0; i < 128 && cursor < current.size(); i++) {
            CriticalGeometry.Voxel v = current.get(cursor);
            if (!matches(v)) {
                if (!worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                    fail("修复冲突");
                    return;
                }
                if (!worldObj.setBlock(wx(v), wy(v), wz(v), v.block, v.metadata, 2)) {
                    fail("修复放置失败");
                    return;
                }
            }
            placed.set(voxelIndices.get(v));
            cursor++;
        }
        status = "修复阶段 " + stage + " " + cursor + "/" + current.size();
        markDirty();
        if (cursor == current.size()) {
            for (ItemStack stack : repairEscrow) spent.add(stack.copy());
            repairEscrow = new ItemStack[0];
            repairFunded = false;
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
            status = "完工核验中";
            changed();
            return;
        }
        if (current.isEmpty()) {
            stage++;
            cursor = 0;
            changed();
            return;
        }
        if (!stageFunded) {
            ItemStack[] costs = CriticalConstructionCosts.forStage(config, stage, current.size());
            if (!consumeItems(costs)) {
                fail("施工阶段 " + stage + " 缺少材料");
                return;
            }
            escrow = copyItems(costs);
            stageFunded = true;
            changed();
        }
        long cost = config.tier.constructionVoltage();
        if (energy < cost) {
            fail("施工缺电");
            return;
        }
        energy -= cost;
        for (int i = 0; i < 128 && cursor < current.size(); i++) {
            CriticalGeometry.Voxel v = current.get(cursor);
            if (!matches(v)) {
                if (!worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                    fail("施工冲突，禁止覆盖方块");
                    return;
                }
                if (!worldObj.setBlock(wx(v), wy(v), wz(v), v.block, v.metadata, 2)) {
                    fail("方块放置失败");
                    return;
                }
            }
            placed.set(voxelIndices.get(v));
            cursor++;
        }
        status = "施工阶段 " + stage + " " + cursor + "/" + current.size();
        markDirty();
        if (cursor == current.size()) {
            for (ItemStack item : escrow) spent.add(item.copy());
            escrow = new ItemStack[0];
            stageFunded = false;
            cursor = 0;
            stage++;
            changed();
        }
    }

    private void dismantle() {
        List<CriticalGeometry.Voxel> voxels = allVoxels();
        long cost = config.tier.constructionVoltage();
        if (cursor < voxels.size()) {
            if (energy < cost) {
                fail("拆除缺电");
                return;
            }
            energy -= cost;
        }
        for (int i = 0; i < 128 && cursor < voxels.size(); i++) {
            CriticalGeometry.Voxel v = voxels.get(voxels.size() - 1 - cursor);
            if (placed.get(voxels.size() - 1 - cursor) && !worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
                if (!matches(v)) {
                    fail("拆除冲突，目标不是已登记结构方块");
                    return;
                }
                if (!worldObj.setBlockToAir(wx(v), wy(v), wz(v))) {
                    fail("拆除方块失败");
                    return;
                }
            }
            cursor++;
        }
        markDirty();
        status = "拆除 " + cursor + "/" + voxels.size();
        if (cursor < voxels.size()) return;
        if (!removalVerified) {
            int end = Math.min(voxels.size(), scanCursor + 512);
            for (; scanCursor < end; scanCursor++) {
                CriticalGeometry.Voxel v = voxels.get(scanCursor);
                if (!worldObj.isAirBlock(wx(v), wy(v), wz(v))) {
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
        data().release(structureId);
        state = 0;
        structureId = "";
        jobId = "";
        cursor = 0;
        stage = 0;
        refundPrepared = false;
        placed.clear();
        status = "拆除完成";
        changed();
    }

    private void runBatch() {
        if (config.kind == CriticalMachineKind.SOLAR) {
            if (!data().claimNight(worldObj, structureId)) {
                fail("本维度已有先启动的辐照锅炉");
                return;
            }
        }
        if (batchId.isEmpty()) {
            ItemStack[] inputs = new ItemStack[18];
            FluidStack[] fluids = new FluidStack[8];
            for (int i = 0; i < 18; i++) inputs[i] = inventory[i] == null ? null : inventory[i].copy();
            for (int i = 0; i < 8; i++) fluids[i] = tanks[i] == null ? null : tanks[i].copy();
            RecipeMatch match = CriticalRecipes.match(config, inputs, fluids);
            if (match == null) {
                status = "没有完整可执行批次";
                return;
            }
            if (match.generatedEu < 0 || match.generatedEu > capacity() - energy - pendingGenerated) {
                fail("发电批次容量不足，先释放储能");
                return;
            }
            if (!canDeliverItems(match.itemOutputs) || !canDeliverFluids(match.fluidOutputs)) {
                fail("批次输出空间不足");
                return;
            }
            if (!hasItems(match.itemInputs) || !hasFluids(match.fluidInputs)) {
                fail("输入快照已变化");
                return;
            }
            consumeItems(match.itemInputs);
            consumeFluids(match.fluidInputs);
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
            if (energy < batchEu) {
                fail("批次缺电，输入已保留在本机escrow");
                return;
            }
            energy -= batchEu;
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
        long transfer = Math.min(Math.max(0L, capacity() - energy), pendingGenerated);
        energy += transfer;
        pendingGenerated -= transfer;
        if (config.kind != CriticalMachineKind.BATTERY && config.kind != CriticalMachineKind.TURBINE
            && config.kind != CriticalMachineKind.SUN
            && config.kind != CriticalMachineKind.ACCELERATOR) return;
        long voltage = config.tier.recipeVoltage();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (side != ForgeDirection.DOWN || energy < voltage) continue;
            TileEntity target = worldObj
                .getTileEntity(xCoord + side.offsetX, yCoord + side.offsetY, zCoord + side.offsetZ);
            if (target instanceof IEnergyConnected receiver && receiver.inputEnergyFrom(side.getOpposite())) {
                if (!outputsEnergyTo(side)) continue;
                long amps = Math.min(16L, energy / voltage);
                long used = Math.max(0, Math.min(amps, receiver.injectEnergyUnits(side.getOpposite(), voltage, amps)));
                energy -= used * voltage;
            }
        }
    }

    @Override
    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        if (!inputEnergyFrom(side) || voltage <= 0 || amperage <= 0 || voltage > config.tier.recipeVoltage()) return 0;
        long reserved = pendingGenerated + (batchId.isEmpty() ? 0 : batchGenerated);
        long accepted = Math.min(amperage, Math.max(0, capacity() - energy - reserved) / voltage);
        energy += accepted * voltage;
        if (accepted > 0) markDirty();
        return accepted;
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side) {
        return side != ForgeDirection.DOWN;
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection side) {
        boolean producer = config.kind == CriticalMachineKind.BATTERY || config.kind == CriticalMachineKind.TURBINE
            || config.kind == CriticalMachineKind.SUN
            || config.kind == CriticalMachineKind.ACCELERATOR;
        return producer && side == ForgeDirection.DOWN && state == 2 && structureValid && enabled;
    }

    @Override
    public byte getColorization() {
        return -1;
    }

    @Override
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

    @Override
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

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null) return null;
        for (int i = state == 0 && from == ForgeDirection.DOWN ? 0 : 8; i < 16; i++) {
            if (tanks[i] != null && tanks[i].isFluidEqual(resource)) return drainTank(i, resource.amount, doDrain);
        }
        return null;
    }

    @Override
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

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return fluid != null;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        for (int i = state == 0 && from == ForgeDirection.DOWN ? 0 : 8; i < 16; i++) {
            if (tanks[i] != null && tanks[i].getFluid() == fluid) return true;
        }
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        FluidTankInfo[] result = new FluidTankInfo[16];
        for (int i = 0; i < 16; i++)
            result[i] = new FluidTankInfo(tanks[i] == null ? null : tanks[i].copy(), TANK_CAPACITY);
        return result;
    }

    @Override
    public int getSizeInventory() {
        return 36;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < 36 ? inventory[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null || amount <= 0) return null;
        ItemStack result = stack.splitStack(Math.min(amount, stack.stackSize));
        if (stack.stackSize == 0) inventory[slot] = null;
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 36) return;
        inventory[slot] = stack != null && stack.stackSize > 0 ? stack : null;
        if (stack != null) stack.stackSize = Math.min(stack.stackSize, Math.min(64, stack.getMaxStackSize()));
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "container.gtsr.critical";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return p != null && !p.isDead
            && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && (owner.isEmpty() || owner.equals(
                p.getUniqueID()
                    .toString()))
            && p.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) <= 64;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot >= 0 && slot < 18;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int[] slots = new int[18];
        for (int i = 0; i < 18; i++) slots[i] = side == 0 ? i + 18 : i;
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack item, int side) {
        return slot >= 0 && slot < 18;
    }

    @Override
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

    @Override
    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
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
        n.setBoolean("repairFunded", repairFunded);
        n.setTag("repairEscrow", writeItems(repairEscrow));
        n.setLong("jobFingerprint", jobFingerprint);
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
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
        repairFunded = n.getBoolean("repairFunded");
        repairEscrow = readItems(n.getTagList("repairEscrow", 10), -1);
        repairStages = null;
        jobFingerprint = n.getLong("jobFingerprint");
        geometryCache = null;
        scanCursor = 0;
        validationClock = 0;
        structureValid = false;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound n = new NBTTagCompound();
        n.setTag("config", config.write());
        n.setInteger("state", state);
        n.setString("status", status);
        n.setInteger("cycleTicks", cycleTicks);
        n.setInteger("cycleDuration", cycleDuration);
        n.setBoolean("valid", structureValid);
        n.setBoolean("enabled", enabled);
        n.setInteger("stage", stage);
        n.setInteger("cursor", cursor);
        n.setInteger("jobTotal", getJobTotal());
        n.setInteger("reason", getReasonCode());
        n.setBoolean("working", isWorking());
        n.setString("structureId", structureId);
        n.setString("batchId", batchId);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, n);
    }

    @Override
    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        NBTTagCompound n = packet.func_148857_g();
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

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(
            getOriginX(),
            getOriginY(),
            getOriginZ(),
            getOriginX() + 80,
            getOriginY() + 162,
            getOriginZ() + 80);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512D * 512D;
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        CriticalProjection.construct(this, trigger, hintsOnly);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public String[] getStructureDescription(ItemStack trigger) {
        return CriticalProjection.description();
    }
}
