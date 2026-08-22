package com.miaokatze.gtsr.common.machine.base;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.util.HubBindingUtil;
import com.miaokatze.gtsr.common.util.HubTeleportUtil;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 双枢纽（蒸汽/蓄水）钩子基类（O2-02/A04-H1）：上提两侧逐字镜像的无状态段——绑定缓存节点的
 * 注册/注销/解析与每 tick 节流、状态 UI 列表序列化与远程操作组、绑定调试、芯片判定、
 * 螺丝刀溢流输入切换与节点传输速率计算。
 *
 * 两侧本质差异（类型串族、存储锁表示、绑定列表 NBT 格式）经钩子 {@link #resolveNodeType(IHubCacheNode)}
 * 与子类留存段保留；网络同步 tag 字段（x/y/z/dim/type/name/fluid/stored/cap/rate/capPct/out/auto/modeLocked）
 * 与存档 NBT 键名一律逐字保留——基类化零行为变化（A04 施工图 4.1 镜像方法迁移表）。
 */
public abstract class MTEHubArrayBase<T extends MTEHubArrayBase<T>> extends MTEGTSRMultiBlockBase<T>
    implements IHubArray {

    /** 绑定节点解析失败后的重试间隔（tick）：两侧原独立常量现值同为 20，统一单源。 */
    protected static final int BOUND_NODE_RETRY_INTERVAL = 20;

    /** 非缓存节点（奇点仓等）的固定交互速率上限（L/tick）：两侧原独立常量现值同为 1,000,000，统一单源。 */
    protected static final int DEFAULT_NODE_TRANSFER_RATE = 1_000_000;

    /**
     * 绑定缓存节点记录（坐标 + 解析缓存）。
     * NBT 序列化两侧格式不同（Steam：NBTTagList 逐项；Water：count+nodeN），读写实现留子类。
     */
    protected static class BoundCacheNode {

        public final int x, y, z;
        public final int dimensionId;
        public final boolean isReinforced;
        public boolean isOutputMode;
        public transient IGregTechTileEntity cachedTile;
        public transient long lastLookupTick;
        public transient long nextLookupTick;
        public transient boolean lastLookupLoaded;

        public BoundCacheNode(int x, int y, int z, int dim, boolean reinforced, boolean outputMode) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimensionId = dim;
            this.isReinforced = reinforced;
            this.isOutputMode = outputMode;
        }

        /** 无 reinforced 维度的便捷构造（蓄水枢纽族：该字段恒 false 且不参与序列化）。 */
        public BoundCacheNode(int x, int y, int z, int dim, boolean outputMode) {
            this(x, y, z, dim, false, outputMode);
        }

        public void invalidateCache() {
            cachedTile = null;
            lastLookupTick = 0;
            nextLookupTick = 0;
            lastLookupLoaded = false;
        }
    }

    protected final ArrayList<BoundCacheNode> mBoundNodes = new ArrayList<>();
    public boolean mOverflowInput = false;
    public int mSetTier = -1;
    protected int mCasingTier = -1;
    protected int mPipeTier = -1;
    protected int mFrameTier = -1;
    /** 渲染状态同步去重 key（存储流体名），服务端 onPostTick 维护，变化才 issueTileUpdate。 */
    protected String mLastSyncKey = null;

    protected MTEHubArrayBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTEHubArrayBase(String aName) {
        super(aName);
    }

    /** 按实际节点类判定本族类型字符串（类型串族不同，两侧各持一份 instanceof 表）。 */
    protected abstract String resolveNodeType(IHubCacheNode node);

    /** 总容量公式（单元计数与芯片倍率族差异：字段留子类，读数经本钩子单点化）。 */
    public abstract long getTotalCapacity();

    public boolean isFormed() {
        return mMachine;
    }

    // region 绑定缓存节点注册/注销/模式同步（IHubArray）

    protected BoundCacheNode findBoundNode(int x, int y, int z, int dim) {
        for (BoundCacheNode node : mBoundNodes) {
            if (node.x == x && node.y == y && node.z == z && node.dimensionId == dim) {
                return node;
            }
        }
        return null;
    }

    @Override
    public void registerCacheNode(int x, int y, int z, int dim, boolean isOutputMode) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.isOutputMode = isOutputMode;
        } else {
            mBoundNodes.add(new BoundCacheNode(x, y, z, dim, false, isOutputMode));
        }
    }

    @Override
    public void unregisterCacheNode(int x, int y, int z, int dim) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.invalidateCache();
            mBoundNodes.remove(existing);
        }
    }

    @Override
    public void updateCacheNodeMode(int x, int y, int z, int dim, boolean isOutputMode) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.isOutputMode = isOutputMode;
        }
    }

    // endregion

    // region 绑定节点解析（每 tick 节流 / 行为触发即时重解析）

    protected IHubCacheNode resolveCacheNodeForAction(int x, int y, int z, int dim) {
        BoundCacheNode bound = findBoundNode(x, y, z, dim);
        if (bound == null) return null;
        bound.invalidateCache();
        return resolveCacheNode(bound, true);
    }

    /**
     * Resolves a bound node at most once per world tick. UI polling never loads chunks; explicit transfer/action
     * operations may load the target chunk. A temporarily unavailable world/chunk is not treated as an invalid node.
     */
    protected IHubCacheNode resolveCacheNode(BoundCacheNode bound, boolean loadChunk) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World hubWorld = base == null ? null : base.getWorld();
        long now = hubWorld == null ? 0L : hubWorld.getTotalWorldTime();
        if (bound.cachedTile != null && (bound.lastLookupTick == now || now < bound.nextLookupTick)) {
            IMetaTileEntity mte = bound.cachedTile.getMetaTileEntity();
            return mte instanceof IHubCacheNode node ? node : null;
        }
        if (!loadChunk && now < bound.nextLookupTick) return null;

        bound.lastLookupTick = now;
        bound.lastLookupLoaded = false;
        World world = DimensionManager.getWorld(bound.dimensionId);
        if (world == null) {
            bound.cachedTile = null;
            bound.nextLookupTick = now + BOUND_NODE_RETRY_INTERVAL;
            return null;
        }
        if (!world.blockExists(bound.x, 0, bound.z)) {
            if (!loadChunk || !HubTeleportUtil.ensureChunkLoaded(world, bound.x, bound.z)) {
                bound.cachedTile = null;
                bound.nextLookupTick = now + BOUND_NODE_RETRY_INTERVAL;
                return null;
            }
        }
        if (!world.blockExists(bound.x, bound.y, bound.z)) {
            bound.cachedTile = null;
            bound.nextLookupTick = now + BOUND_NODE_RETRY_INTERVAL;
            return null;
        }

        bound.lastLookupLoaded = true;
        TileEntity te = world.getTileEntity(bound.x, bound.y, bound.z);
        if (te instanceof IGregTechTileEntity gte && gte.getMetaTileEntity() instanceof IHubCacheNode node) {
            bound.cachedTile = gte;
            bound.nextLookupTick = now + BOUND_NODE_RETRY_INTERVAL;
            return node;
        }
        bound.cachedTile = null;
        bound.nextLookupTick = now + BOUND_NODE_RETRY_INTERVAL;
        return null;
    }

    // endregion

    // region 状态 UI 列表序列化与远程操作组（网络 tag 字段名逐字保留）

    /**
     * 序列化当前绑定缓存节点列表（供状态 UI 同步显示）。
     * 每项含：坐标/维度/类型(type)/自定义名(name)/流体名(fluid)/储量(stored,long)/容量(cap,long)/
     * 速率百分比(rate)/输出模式(out)。节点无法解析时数据回退为空/0，行仍显示（标记离线）。
     */
    public NBTTagList getCacheNodeListTag() {
        NBTTagList list = new NBTTagList();
        for (BoundCacheNode node : mBoundNodes) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", node.x);
            tag.setInteger("y", node.y);
            tag.setInteger("z", node.z);
            tag.setInteger("dim", node.dimensionId);
            IHubCacheNode cacheNode = resolveCacheNode(node, false);
            tag.setString("type", cacheNode != null ? resolveNodeType(cacheNode) : "");
            // 节点自定义名（无则为空串，客户端回退显示默认类型名；奇点仓恒空串）
            tag.setString("name", cacheNode != null ? cacheNode.getCustomName() : "");
            tag.setString("fluid", cacheNode != null ? cacheNode.getStoredFluidName() : "");
            // stored/cap 必须 long：强化/超压节点容量超出 int 范围
            tag.setLong("stored", cacheNode != null ? cacheNode.getStoredFluidAmount() : 0L);
            tag.setLong("cap", cacheNode != null ? cacheNode.getFluidCapacityLong() : 0L);
            // 速率百分比（奇点仓无速率档恒 100；GUI 侧 S4 再对仓隐藏/改容量按钮）
            tag.setInteger("rate", cacheNode != null ? cacheNode.getTransferRatePercent() : 0);
            // 容量档百分比（S4：缓存节点与接收仓生效；发送仓恒 100，GUI 容量按钮对其禁用）
            tag.setInteger("capPct", cacheNode != null ? cacheNode.getCapacityLimitPercent() : 100);
            tag.setBoolean("out", cacheNode != null ? cacheNode.isOutputMode() : node.isOutputMode);
            // 自动输出开关（与方向模式解耦）：节点离线时回退 false（奇点仓恒 false）
            tag.setBoolean("auto", cacheNode != null && cacheNode.isAutoOutput());
            tag.setBoolean("modeLocked", cacheNode != null && cacheNode.isOutputModeLocked());
            list.appendTag(tag);
        }
        return list;
    }

    /** 状态 UI 循环节点交互速率百分比（与手持芯片右击同一循环逻辑；奇点仓为 no-op）。 */
    public void cycleCacheNodeRateFromGui(int x, int y, int z, int dim) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        node.cycleTransferRatePercent();
    }

    /** 状态 UI 循环节点容量上限百分比（S4：缓存节点与接收仓；发送仓为 no-op，与空手 Shift 右击同逻辑）。 */
    public void cycleCacheNodeCapFromGui(int x, int y, int z, int dim) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        node.cycleCapacityLimitPercent();
    }

    /** 状态 UI 切换节点输出模式：写节点本体 + 同步枢纽侧绑定记录（IHubArray.updateCacheNodeMode）。 */
    public void setCacheNodeModeFromGui(int x, int y, int z, int dim, boolean output) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        // 模式锁定节点（奇点仓）：服务端整体拒改（节点与枢纽侧记录都不动，避免传输方向错位）
        if (node.isOutputModeLocked()) return;
        node.setOutputMode(output);
        updateCacheNodeMode(x, y, z, dim, output);
    }

    /** 状态 UI 切换节点自动输出开关：只写节点本体（与方向模式解耦，枢纽绑定记录无需同步）。 */
    public void setCacheNodeAutoFromGui(int x, int y, int z, int dim, boolean auto) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        node.setAutoOutput(auto);
    }

    /**
     * 状态 UI 重命名节点：名字在服务端做安全裁剪（剔 §/去首尾空白/≤24 字符），
     * 裁剪后为空表示清除自定义名（UI 回退默认类型名）。
     * 名字变化由列表每 tick 变化检测自动同步到枢纽状态 UI 客户端；
     * 节点方块自身（GUI 标题/Waila）另经 issueTileUpdate 触发 description packet 同步。
     */
    public void renameCacheNodeFromGui(int x, int y, int z, int dim, String name) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        node.setCustomName(MTERemoteWorkerNode.sanitizeCustomName(name));
        // 触发节点 TE 重同步（S35 description packet），客户端 MTE 拿到新自定义名以更新 GUI 标题
        if (node instanceof MetaTileEntity mte && mte.getBaseMetaTileEntity() != null) {
            mte.getBaseMetaTileEntity()
                .issueTileUpdate();
        }
    }

    /** Performs the same validated, one-singularity teleport used by the drilling hub status UI. */
    public void teleportPlayerToNodeFromGui(EntityPlayer player, int x, int y, int z, int dim) {
        if (player == null) return;
        if (!canUseStatusAction(player) || findBoundNode(x, y, z, dim) == null) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }

        World targetWorld = HubTeleportUtil.resolveTargetWorld(player, dim);
        if (targetWorld == null) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_dim"));
            return;
        }
        if (!HubTeleportUtil.ensureChunkLoaded(targetWorld, x, z)) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }

        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null || !acceptsNodeType(resolveNodeType(node))) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }

        int safeY = HubTeleportUtil.findSafeTeleportHeight(targetWorld, x, y, z);
        if (safeY < 0) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_unsafe"));
            return;
        }
        if (!HubTeleportUtil.teleportPlayer(player, targetWorld, dim, x, safeY, z)) {
            GTUtility
                .sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_no_singularity"));
        }
    }

    private boolean canUseStatusAction(EntityPlayer player) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (player == null || base == null || world == null || player.dimension != world.provider.dimensionId)
            return false;
        return base.canAccessData()
            && player.getDistanceSq(base.getXCoord() + 0.5D, base.getYCoord() + 0.5D, base.getZCoord() + 0.5D) <= 64.0D;
    }

    // endregion

    // region 芯片判定 / 绑定调试 / 螺丝刀 / 节点传输速率

    /** 普通或强化奇点芯片任一在位（绑定/传输门控）。 */
    protected boolean hasChipInstalled() {
        ItemStack stack = getControllerSlot();
        return stack != null && (GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true)
            || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(stack, true, true));
    }

    protected boolean hasHubChipInstalled() {
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true);
    }

    /** 强化奇点芯片（等级3 前置）：容量×20 与超压节点绑定门控共用。 */
    protected boolean hasReinforcedChipInstalled() {
        if (mSetTier < 3) return false;
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(stack, true, true);
    }

    /** 手持芯片右击的绑定列表调试输出（节点不足/无芯片提示 + 逐节点坐标与方向）。 */
    protected void sendBindingDebug(EntityPlayer aPlayer) {
        GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_title"));
        if (mBoundNodes.isEmpty()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_bindings"));
            return;
        }
        if (!hasChipInstalled()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_chip"));
        }
        for (BoundCacheNode node : mBoundNodes) {
            String mode = node.isOutputMode ? StatCollector.translateToLocal("gtsr.binding.debug_output")
                : StatCollector.translateToLocal("gtsr.binding.debug_input");
            String posInfo = StatCollector.translateToLocal("gtsr.binding.debug_node") + "("
                + node.x
                + ", "
                + node.y
                + ", "
                + node.z
                + ") DIM:"
                + node.dimensionId
                + " "
                + StatCollector.translateToLocal("gtsr.binding.debug_mode")
                + mode;
            GTUtility.sendChatToPlayer(aPlayer, posInfo);
        }
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        mOverflowInput = !mOverflowInput;
        if (aPlayer.worldObj.isRemote) return;
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_input") + ": "
                + (mOverflowInput ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")
                    : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")));
    }

    /**
     * 按节点交互速率百分比计算实际传输速率。
     * S1 类型拓宽：缓存节点=速率百分比实算；奇点仓=固定常量（getEffectiveHubTransferRate 默认实现）。
     */
    protected int getNodeTransferRate(IGregTechTileEntity gte) {
        IMetaTileEntity mte = gte.getMetaTileEntity();
        if (mte instanceof IHubCacheNode cacheNode) {
            return (int) Math.min(cacheNode.getEffectiveHubTransferRate(), Integer.MAX_VALUE);
        }
        return DEFAULT_NODE_TRANSFER_RATE;
    }

    // endregion

    // region 周期服务 tick / 跨节点传输 / 存档 NBT 骨架（A04-H3）

    /**
     * 服务端成形后每 tick 骨架：容量钳制 → 自动输出 → 存储流体名同步去重 → 周期传输分派。
     * 族差异经钩子保留（储量表量/自动输出/同步流体名/传输触发节奏——Steam 用持久化 mTickCounter
     * 计数、Water 用世界 aTick 取模，触发语义两侧原样保留）。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || !mMachine) return;

        long totalCapacity = getTotalCapacity();
        if (getStoredFluidAmount() > totalCapacity) {
            setStoredFluidAmount(totalCapacity);
        }

        autoOutputStored();

        // 存储流体名变化才发 description packet（首流锁定与抽干清空自然覆盖，量变化不发包，稳态零流量）
        String syncKey = storedFluidNameForSync();
        if (!syncKey.equals(mLastSyncKey)) {
            mLastSyncKey = syncKey;
            aBaseMetaTileEntity.issueTileUpdate();
        }

        onBoundTransferTick(aTick);
    }

    /** 当前存储量（族字段 mSteamStored/mWaterStored 留子类）。 */
    protected abstract long getStoredFluidAmount();

    /** 写当前存储量（容量钳制用）。 */
    protected abstract void setStoredFluidAmount(long amount);

    /** 自动输出族差异钩子（溢流输出 hatch 循环；水侧 toExport 经 createFluidTag）。 */
    protected abstract void autoOutputStored();

    /** 存储流体名同步 key（空串=无存储；蒸汽侧取 FluidStack 名、水侧取 String 名）。 */
    protected abstract String storedFluidNameForSync();

    /** 周期传输触发族差异钩子（Steam：mTickCounter 计数取模；Water：世界 aTick 取模）。 */
    protected abstract void onBoundTransferTick(long aTick);

    /**
     * 跨维度绑定传输模板：逐绑定节点解析（行为触发可加载目标区块）→ 类型过滤 → 无效节点清理 →
     * 芯片门控后按方向调用族传输钩子。流体锁表示差异（Steam FluidStack 锁 / Water String 锁）
     * 全部收敛在 transferOneNode 钩子内。
     */
    protected void transferWithBoundNodes() {
        boolean chipInstalled = hasChipInstalled();
        ArrayList<BoundCacheNode> invalidNodes = new ArrayList<>();

        for (BoundCacheNode node : mBoundNodes) {
            // 节点类型过滤对齐 resolveNodeType+acceptsNodeType，而非硬 instanceof
            IHubCacheNode cacheNode = resolveCacheNode(node, true);
            if (cacheNode == null) {
                if (node.lastLookupLoaded) invalidNodes.add(node);
                continue;
            }
            if (!acceptsNodeType(resolveNodeType(cacheNode)) || node.cachedTile == null) {
                invalidNodes.add(node);
                continue;
            }
            if (!chipInstalled) continue;
            transferOneNode(node, node.cachedTile, getNodeTransferRate(node.cachedTile));
        }

        mBoundNodes.removeAll(invalidNodes);
    }

    /** 单节点传输钩子：output 分支枢纽→节点（extract 族），input 分支节点→枢纽（receive 族）。 */
    protected abstract void transferOneNode(BoundCacheNode node, IGregTechTileEntity gte, int nodeRate);

    /**
     * 存档骨架：公共 tier/溢流开关字段与绑定列表键名两侧一致（mSetTier/mCasingTier/mPipeTier/
     * mFrameTier/mOverflowInput/mBoundNodes），族字段（储量/单元计数/存储流体）留子类写入；
     * 绑定列表两侧格式不同（Steam NBTTagList 逐项 / Water count+nodeN），经钩子保留。
     */
    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mPipeTier", mPipeTier);
        aNBT.setInteger("mFrameTier", mFrameTier);
        aNBT.setBoolean("mOverflowInput", mOverflowInput);
        if (!mBoundNodes.isEmpty()) {
            saveBoundNodes(aNBT);
        }
    }

    /** 绑定列表序列化钩子（mBoundNodes 键名固定，内部格式两侧保留）。 */
    protected abstract void saveBoundNodes(NBTTagCompound aNBT);

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
        mCasingTier = aNBT.getInteger("mCasingTier");
        mPipeTier = aNBT.getInteger("mPipeTier");
        mFrameTier = aNBT.getInteger("mFrameTier");
        mOverflowInput = aNBT.getBoolean("mOverflowInput");
        mBoundNodes.clear();
        if (aNBT.hasKey("mBoundNodes")) {
            loadBoundNodes(aNBT);
        }
    }

    /** 绑定列表反序列化钩子（旧档两种格式各自回归）。 */
    protected abstract void loadBoundNodes(NBTTagCompound aNBT);

    // endregion

    // region 绑定流（onRightclick 模板 + bindOne/bindWhole + 成本钩子，吸收 O2-11 双枢纽绑定流）

    /** 手持物类型识别表（本族缓存节点 + 奇点仓物品；无法识别返回 null 走默认右键）。 */
    protected abstract String resolveHeldType(ItemStack held);

    /** 打开缓存节点状态管理界面（Modern UI 2，独立 factory；两侧 factory 不同）。 */
    protected abstract void openHubStatusGui(EntityPlayer player);

    /** 本族奇点仓类型对（输入仓类型, 输出仓类型）：模式锁定与恒定 output 判定共用。 */
    protected abstract Pair<String, String> singularityCompartmentTypes();

    /** 该类型绑定是否需要强化奇点芯片（等级3）门控：两侧仅超压档为 true。 */
    protected boolean requiresReinforcedChipToBind(String type) {
        return false;
    }

    /** 该类型是否属于强化变体（写入 hubPos 的 reinforced 标记）：仅蒸汽族 reinforced_steam 为 true。 */
    protected boolean isReinforcedType(String type) {
        return false;
    }

    /**
     * 绑定奇点成本钩子（吸收 O2-11 三态）：基类默认 0（蓄水枢纽族恒 0，自动退化为仅打标记）；
     * 蒸汽枢纽覆写查表（reinforced_steam=1、overpressure_steam=8、奇点仓=1），
     * 钻井枢纽成本恒 1（未挂本基类，经 HubBindingUtil 同窗口部分吸收）。
     */
    protected int getBindSingularityCost(String type) {
        return 0;
    }

    /** 奇点仓类型（模式锁定，右键已绑定分支只解绑不翻转）。 */
    protected boolean isModeLockedType(String type) {
        Pair<String, String> compartmentTypes = singularityCompartmentTypes();
        return type.equals(compartmentTypes.getLeft()) || type.equals(compartmentTypes.getRight());
    }

    /**
     * 锁定类型绑定时的 item output 恒定值（反转语义：false=枢纽→节点/接收仓，true=节点→枢纽/发送仓；
     * 与节点 loadNBTData 强制归位值互补）。非锁定类型保持 false（现状）。
     */
    protected boolean getLockedItemOutput(String type) {
        return type.equals(singularityCompartmentTypes().getRight());
    }

    /** 绑定 hubPos 的族差异字段（默认无；蒸汽族覆写补写 reinforced 标记）。 */
    protected void writeBindExtras(NBTTagCompound hubTag, boolean isReinforced) {}

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        ItemStack held = aPlayer.getHeldItem();

        // 手持枢纽终端右击：打开缓存节点状态管理界面（Modern UI 2，独立 factory），
        // 不占用空手右键（空手仍打开主 GUI），与钻井枢纽的打开方式保持一致
        if (held != null && GTSRItemList.HubTerminal.isStackEqual(held, false, true)) {
            if (aBaseMetaTileEntity.isServerSide()) {
                openHubStatusGui(aPlayer);
            }
            return true;
        }

        if (held != null && (GTSRItemList.HubSingularityChip.isStackEqual(held, true, true)
            || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(held, true, true))) {
            if (aBaseMetaTileEntity.isServerSide()) {
                sendBindingDebug(aPlayer);
            }
            return true;
        }

        if (held == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        String type = resolveHeldType(held);

        if (type == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        if (!aBaseMetaTileEntity.isServerSide()) return true;

        if (requiresReinforcedChipToBind(type) && !hasReinforcedChipInstalled()) {
            GTUtility.sendChatToPlayer(
                aPlayer,
                StatCollector.translateToLocal("gtsr.binding.overpressure_no_reinforced_chip"));
            return true;
        }

        if (!hasChipInstalled()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.no_chip"));
            return true;
        }

        int myX = aBaseMetaTileEntity.getXCoord();
        int myY = aBaseMetaTileEntity.getYCoord();
        int myZ = aBaseMetaTileEntity.getZCoord();
        int myDim = aBaseMetaTileEntity.getWorld().provider.dimensionId;

        // 已绑定本枢纽的堆叠：无论普通/shift，优先走现有 output 翻转/解绑交互（完全保留现状逻辑）
        if (held.hasTagCompound() && held.getTagCompound()
            .hasKey("gtsr.hubPos")) {
            NBTTagCompound existing = held.getTagCompound()
                .getCompoundTag("gtsr.hubPos");
            int boundX = existing.getInteger("x");
            int boundY = existing.getInteger("y");
            int boundZ = existing.getInteger("z");
            int boundDim = existing.getInteger("dim");

            if (boundX == myX && boundY == myY && boundZ == myZ && boundDim == myDim) {
                // 奇点仓模式锁定：不提供 output 翻转，右击只解绑（沿用现解绑文案）
                if (isModeLockedType(type)) {
                    held.getTagCompound()
                        .removeTag("gtsr.hubPos");
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.cleared") + held.getDisplayName()
                            + StatCollector.translateToLocal("gtsr.binding.binding"));
                    return true;
                }
                boolean isOutput = existing.hasKey("output") && existing.getBoolean("output");

                if (!isOutput) {
                    existing.setBoolean("output", true);
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.bound_input") + held.getDisplayName()
                            + StatCollector.translateToLocal("gtsr.binding.mode_input"));
                } else {
                    held.getTagCompound()
                        .removeTag("gtsr.hubPos");
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.cleared") + held.getDisplayName()
                            + StatCollector.translateToLocal("gtsr.binding.binding"));
                }
                return true;
            }
        }

        // shift 右击：整个手持堆叠全部绑定（奇点消耗 = 单次成本 × 堆叠数量）；
        // 普通右击：拆出 1 个绑定（奇点按类型成本消耗一次），绑定物回背包，手持剩余保持未绑定
        boolean isReinforced = isReinforcedType(type);
        if (aPlayer.isSneaking()) {
            bindWholeHeld(aPlayer, held, type, isReinforced, myX, myY, myZ, myDim);
        } else {
            bindOneFromHeld(aPlayer, held, type, isReinforced, myX, myY, myZ, myDim);
        }
        return true;
    }

    /**
     * 普通右击：从手持堆叠拆出 1 个缓存节点绑定到本枢纽（无 singularity_consumed 标记则按类型成本
     * 消耗一次奇点：成本表见 getBindSingularityCost；0 成本族仅打标记），
     * 写 hubPos NBT 后放回玩家背包（背包无空位则落地），手持剩余 N-1 个保持未绑定。
     * 绑定他处的堆叠仅覆盖拆出的这 1 个。
     */
    protected void bindOneFromHeld(EntityPlayer aPlayer, ItemStack held, String type, boolean isReinforced, int myX,
        int myY, int myZ, int myDim) {
        // 先按手持标记状态决定是否消耗：无标记则按类型成本消耗一次（不足则报错不执行，保持手持原状）
        if (!held.hasTagCompound() || !held.getTagCompound()
            .hasKey("gtsr.singularity_consumed")) {
            int singularityCost = getBindSingularityCost(type);
            if (singularityCost > 0 && !HubBindingUtil.consumeSteamEntangledSingularities(aPlayer, singularityCost)) {
                GTUtility.sendChatToPlayer(
                    aPlayer,
                    StatCollector.translateToLocal("gtsr.binding.no_singularity") + " (" + singularityCost + ")");
                return;
            }
        }

        // 拆 1 个（copy + 减量，≤0 则清手持槽）
        ItemStack bound = held.copy();
        bound.stackSize = 1;
        held.stackSize--;
        if (held.stackSize <= 0) {
            aPlayer.inventory.mainInventory[aPlayer.inventory.currentItem] = null;
        }

        // 打标记（拆出物继承原 NBT，无标记则补；标记/消耗只作用于拆出物）
        if (!bound.hasTagCompound()) {
            bound.setTagCompound(new NBTTagCompound());
        }
        bound.getTagCompound()
            .setBoolean("gtsr.singularity_consumed", true);

        // 写 hubPos（覆盖绑定他处的旧 hubPos；机器侧 output 按语义直存，族差异字段经钩子补写）
        NBTTagCompound hubTag = HubBindingUtil.createHubPosTag(myX, myY, myZ, myDim, type, getLockedItemOutput(type));
        writeBindExtras(hubTag, isReinforced);
        bound.getTagCompound()
            .setTag("gtsr.hubPos", hubTag);

        GTUtility.addItemToPlayerInventory(aPlayer, bound);
        aPlayer.inventoryContainer.detectAndSendChanges();
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.binding.bound_output") + bound.getDisplayName()
                + StatCollector.translateToLocal("gtsr.binding.mode_output"));
    }

    /**
     * shift 右击：整个手持堆叠全部绑定到本枢纽，奇点消耗 = 单次成本 × 堆叠数量
     * （背包总量不足则报错不执行；0 成本族仅打标记）。绑定他处的堆叠覆盖整堆。
     */
    protected void bindWholeHeld(EntityPlayer aPlayer, ItemStack held, String type, boolean isReinforced, int myX,
        int myY, int myZ, int myDim) {
        // 无标记则按"单次成本 × 堆叠数量"消耗奇点并给整堆打标记
        if (!held.hasTagCompound() || !held.getTagCompound()
            .hasKey("gtsr.singularity_consumed")) {
            int singularityCost = getBindSingularityCost(type) * held.stackSize;
            if (singularityCost > 0 && !HubBindingUtil.consumeSteamEntangledSingularities(aPlayer, singularityCost)) {
                GTUtility.sendChatToPlayer(
                    aPlayer,
                    StatCollector.translateToLocal("gtsr.binding.no_singularity") + " (" + singularityCost + ")");
                return;
            }
            if (!held.hasTagCompound()) {
                held.setTagCompound(new NBTTagCompound());
            }
            held.getTagCompound()
                .setBoolean("gtsr.singularity_consumed", true);
        }

        // 整堆写 hubPos（覆盖绑定他处的旧 hubPos；机器侧 output 按语义直存，族差异字段经钩子补写）
        NBTTagCompound hubTag = HubBindingUtil.createHubPosTag(myX, myY, myZ, myDim, type, getLockedItemOutput(type));
        writeBindExtras(hubTag, isReinforced);
        held.getTagCompound()
            .setTag("gtsr.hubPos", hubTag);

        aPlayer.inventoryContainer.detectAndSendChanges();
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.binding.bound_output") + held.getDisplayName()
                + StatCollector.translateToLocal("gtsr.binding.mode_output"));
    }

    // endregion
}
