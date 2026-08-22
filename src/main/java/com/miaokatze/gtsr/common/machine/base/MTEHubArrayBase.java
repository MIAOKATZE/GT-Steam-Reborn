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

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
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

    protected MTEHubArrayBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTEHubArrayBase(String aName) {
        super(aName);
    }

    /** 按实际节点类判定本族类型字符串（类型串族不同，两侧各持一份 instanceof 表）。 */
    protected abstract String resolveNodeType(IHubCacheNode node);

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
}
