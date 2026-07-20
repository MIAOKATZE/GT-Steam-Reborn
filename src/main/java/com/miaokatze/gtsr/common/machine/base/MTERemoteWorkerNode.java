package com.miaokatze.gtsr.common.machine.base;

import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.HarvestTool;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.modularui.IAddUIWidgets;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.objects.GTChunkManager;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;

public abstract class MTERemoteWorkerNode extends MetaTileEntity implements IAddUIWidgets {

    private static Item sMiningPipeItem = null;

    protected static Item getMiningPipeItem() {
        if (sMiningPipeItem == null) {
            ItemStack pipe = GTModHandler.getIC2Item("miningPipe", 0);
            if (pipe != null) {
                sMiningPipeItem = pipe.getItem();
            }
        }
        return sMiningPipeItem;
    }

    protected static boolean isMiningPipe(ItemStack aStack) {
        if (aStack == null) return false;
        Item pipeItem = getMiningPipeItem();
        return pipeItem != null && aStack.getItem() == pipeItem;
    }

    protected int mHubX = 0;
    protected int mHubY = 0;
    protected int mHubZ = 0;
    protected int mHubDim = 0;
    protected String mHubType = "";
    protected boolean mIsOutputMode = true;
    protected boolean mRegistered = false;
    // 是否已绑定到枢纽（独立于 mHubDim，避免主世界 dim=0 被误判为未绑定）
    protected boolean mBound = false;

    protected boolean mIsWorking = false;
    protected int mWorkProgress = 0;
    protected static final int WORK_CYCLE = 20;

    // 区块加载申请标志：运行态即可，不写 NBT——世界重载后 Forge ticket 失效，
    // 标志随 MetaTileEntity 重建自然复位为 false，首个 postTick 会重新申请
    // （参照 GT5U MTEDrillerBase 的 mWorkChunkNeedsReload 模式）
    protected boolean mSelfChunkRequested = false;
    protected boolean mWorkChunksRequested = false;

    public MTERemoteWorkerNode(int aID, String aName, String aNameRegional, int aInvSlotCount) {
        super(aID, aName, aNameRegional, aInvSlotCount);
    }

    public MTERemoteWorkerNode(String aName, int aInvSlotCount) {
        super(aName, aInvSlotCount);
    }

    public abstract void doWork(IGregTechTileEntity aBaseMetaTileEntity);

    public abstract String getNodeType();

    public int getDrillTier() {
        return 0;
    }

    /**
     * Returns true if this node is actively working and should consume steam from the hub.
     * Drilling nodes always consume; miner nodes only consume when actively working.
     */
    public boolean isActivelyWorking() {
        return true;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing != ForgeDirection.UP && facing != ForgeDirection.DOWN && facing != ForgeDirection.UNKNOWN;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        if (isBound()) {
            NBTTagCompound hubTag = new NBTTagCompound();
            hubTag.setInteger("x", mHubX);
            hubTag.setInteger("y", mHubY);
            hubTag.setInteger("z", mHubZ);
            hubTag.setInteger("dim", mHubDim);
            hubTag.setString("type", mHubType);
            hubTag.setBoolean("output", mIsOutputMode);
            aNBT.setTag("gtsr.hubPos", hubTag);
        }
        aNBT.setBoolean("mIsWorking", mIsWorking);
        aNBT.setInteger("mWorkProgress", mWorkProgress);
    }

    /**
     * 机器被破坏时，由 BaseMetaTileEntity.getDrops() 调用，用于把绑定数据写入掉落物的 NBT。
     * 默认实现（CommonMetaTileEntity.setItemNBT）为空，必须覆写才能让破坏后的物品保留 gtsr.hubPos 等绑定信息。
     * 字段语义与 saveNBTData 完全一致（output 无反转）。
     */
    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        super.setItemNBT(aNBT);
        if (isBound()) {
            NBTTagCompound hubTag = new NBTTagCompound();
            hubTag.setInteger("x", mHubX);
            hubTag.setInteger("y", mHubY);
            hubTag.setInteger("z", mHubZ);
            hubTag.setInteger("dim", mHubDim);
            hubTag.setString("type", mHubType);
            // 与 saveNBTData 一致：output 直接存储 mIsOutputMode，无反转
            hubTag.setBoolean("output", mIsOutputMode);
            aNBT.setTag("gtsr.hubPos", hubTag);
        }
        // 保留奇点消耗标记，避免玩家通过破坏→重新放置来重复利用蒸汽纠缠奇点
        aNBT.setBoolean("gtsr.singularity_consumed", true);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = aNBT.getCompoundTag("gtsr.hubPos");
            mHubX = hubTag.getInteger("x");
            mHubY = hubTag.getInteger("y");
            mHubZ = hubTag.getInteger("z");
            mHubDim = hubTag.getInteger("dim");
            mHubType = hubTag.getString("type");
            mIsOutputMode = hubTag.hasKey("output") && hubTag.getBoolean("output");
            // 已从 NBT 读取到绑定信息，标记为已绑定
            mBound = true;
        } else {
            mHubX = 0;
            mHubY = 0;
            mHubZ = 0;
            mHubDim = 0;
            mHubType = "";
            mIsOutputMode = true;
            mRegistered = false;
            // 无绑定信息，标记为未绑定
            mBound = false;
        }
        mIsWorking = aNBT.getBoolean("mIsWorking");
        mWorkProgress = aNBT.getInteger("mWorkProgress");
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        super.addAdditionalTooltipInformation(stack, tooltip);
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = stack.getTagCompound()
                .getCompoundTag("gtsr.hubPos");
            int hubX = hubTag.getInteger("x");
            int hubY = hubTag.getInteger("y");
            int hubZ = hubTag.getInteger("z");
            String hubType = hubTag.getString("type");
            tooltip.add(
                EnumChatFormatting.GREEN + translateToLocal(
                    "gtsr.binding.bound_to") + " " + hubType + " @ " + hubX + ", " + hubY + ", " + hubZ);
        } else {
            tooltip.add(EnumChatFormatting.YELLOW + translateToLocal("gtsr.tooltip.shared.node_unbound"));
        }
    }

    protected boolean isBound() {
        // 用 mBound 判断绑定状态，避免主世界 dim=0 被误判为未绑定
        return mBound;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (!mRegistered && isBound()) {
            mRegistered = registerWithHub(aBaseMetaTileEntity);
        }

        updateChunkLoading(aBaseMetaTileEntity);

        aBaseMetaTileEntity.setActive(mIsWorking);
        mIsWorking = false;
    }

    @Override
    public void onRemoval() {
        // 节点被移除时释放全部区块加载 ticket，避免残留强制加载
        releaseAllChunks();
        super.onRemoval();
    }

    /**
     * 区块加载维护（服务端每 tick 调用）：已绑定且允许工作时，按当前等级范围申请
     * 自身区块与工作区块；不再允许工作时释放全部 ticket。
     * 注意：子类若覆写 onPostTick 且不调用 super.onPostTick，需自行调用本方法。
     */
    protected void updateChunkLoading(IGregTechTileEntity aBaseMetaTileEntity) {
        if (isBound() && aBaseMetaTileEntity.isAllowedToWork()) {
            if (!mSelfChunkRequested) {
                requestSelfChunk();
            }
            if (!mWorkChunksRequested) {
                requestWorkChunks();
            }
        } else if (mSelfChunkRequested || mWorkChunksRequested) {
            releaseAllChunks();
        }
    }

    /**
     * 申请加载节点自身所在区块。GTChunkManager 中 chunk 参数传 null 即表示自身区块
     * （仅在 GT 配置 alwaysReloadChunkloaders 开启时实际生效，用于跨重启持久化）；
     * 一般情况下自身区块由 requestWorkChunks 的范围覆盖，本调用作为额外保险。
     */
    protected void requestSelfChunk() {
        GTChunkManager.requestChunkLoad((TileEntity) getBaseMetaTileEntity(), null);
        mSelfChunkRequested = true;
    }

    /**
     * 申请加载当前工作范围覆盖的全部区块，具体范围由 collectWorkChunks 钩子收集。
     */
    protected void requestWorkChunks() {
        List<ChunkCoordIntPair> chunks = new ArrayList<>();
        collectWorkChunks(chunks);
        TileEntity te = (TileEntity) getBaseMetaTileEntity();
        for (ChunkCoordIntPair chunk : chunks) {
            GTChunkManager.requestChunkLoad(te, chunk);
        }
        mWorkChunksRequested = true;
    }

    /**
     * 收集当前工作范围覆盖的区块坐标。默认实现：以节点所在区块为中心、
     * getWorkChunkRadius() 为半径的正方形区域。
     * 工作范围不符合「中心+半径」模型的子类（如钻井节点从所在区块向正方向 range×range）
     * 应覆写本方法而非 getWorkChunkRadius()。
     */
    protected void collectWorkChunks(List<ChunkCoordIntPair> out) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        int centerChunkX = te.getXCoord() >> 4;
        int centerChunkZ = te.getZCoord() >> 4;
        int chunkRadius = getWorkChunkRadius();
        for (int x = centerChunkX - chunkRadius; x <= centerChunkX + chunkRadius; x++) {
            for (int z = centerChunkZ - chunkRadius; z <= centerChunkZ + chunkRadius; z++) {
                out.add(new ChunkCoordIntPair(x, z));
            }
        }
    }

    /**
     * 当前工作范围对应的 chunk 半径（以节点所在区块为中心）。基类默认 0（仅自身区块），
     * 子类按各自等级对应的工作范围覆写。
     */
    protected int getWorkChunkRadius() {
        return 0;
    }

    /**
     * 释放本节点持有的全部区块加载 ticket，并复位申请标志，
     * 使下一 tick 的 updateChunkLoading 可按需重新申请。
     * public：枢纽快捷回收节点时需跨包调用（hub 在 machine 包，本类在 machine.base 包）。
     */
    public void releaseAllChunks() {
        GTChunkManager.releaseTicket((TileEntity) getBaseMetaTileEntity());
        mSelfChunkRequested = false;
        mWorkChunksRequested = false;
    }

    /**
     * 节点是否已完全停止（允许快捷回收的判定条件）。
     * 基类默认实现：不再允许工作即视为完全停止；
     * 有采矿管道的子类应覆写本方法，追加「管道全部收回」条件。
     */
    public boolean isFullyRetracted() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return base == null || !base.isAllowedToWork();
    }

    /**
     * 尝试升级节点（消耗玩家背包物品）。基类默认不支持升级，返回 false；
     * 支持升级的子类覆写本方法实现具体升级逻辑。
     */
    public boolean tryUpgrade(EntityPlayer player) {
        return false;
    }

    /**
     * 取出节点背包中全部采矿管道（枢纽快捷回收用），取出后对应槽位清空。
     * 返回的列表可能为空，但不会为 null。
     */
    public List<ItemStack> drainStoredMiningPipes() {
        List<ItemStack> pipes = new ArrayList<>();
        for (int i = 0; i < mInventory.length; i++) {
            ItemStack stack = mInventory[i];
            if (isMiningPipe(stack) && stack.stackSize > 0) {
                pipes.add(stack.copy());
                mInventory[i] = null;
            }
        }
        return pipes;
    }

    /**
     * 等级变化钩子（升级成功后调用）：释放旧范围 ticket 并复位标志，
     * 下一 tick 的 updateChunkLoading 会按新范围重新申请工作区块。
     */
    public void onTierChanged() {
        releaseAllChunks();
    }

    @Override
    public String[] getInfoData() {
        return new String[0];
    }

    protected boolean registerWithHub(IGregTechTileEntity aBaseMetaTileEntity) {
        World world = DimensionManager.getWorld(mHubDim);
        if (world == null || !world.blockExists(mHubX, mHubY, mHubZ)) return false;

        TileEntity te = world.getTileEntity(mHubX, mHubY, mHubZ);
        if (!(te instanceof IGregTechTileEntity gte)) return false;

        if (!(gte.getMetaTileEntity() instanceof IHubArray hub)) return false;

        if (!hub.acceptsNodeType(mHubType)) return false;

        hub.registerCacheNode(
            aBaseMetaTileEntity.getXCoord(),
            aBaseMetaTileEntity.getYCoord(),
            aBaseMetaTileEntity.getZCoord(),
            aBaseMetaTileEntity.getWorld().provider.dimensionId,
            mIsOutputMode);
        return true;
    }

    protected MTESingularityDrillingHub getBoundHub() {
        if (!isBound()) return null;
        World world = DimensionManager.getWorld(mHubDim);
        if (world == null || !world.blockExists(mHubX, mHubY, mHubZ)) return null;

        TileEntity te = world.getTileEntity(mHubX, mHubY, mHubZ);
        if (!(te instanceof IGregTechTileEntity gte)) return null;

        if (gte.getMetaTileEntity() instanceof MTESingularityDrillingHub hub) {
            return hub;
        }
        return null;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (aBaseMetaTileEntity.isServerSide()) {
            openGui(aPlayer);
        }
        return true;
    }

    @Override
    protected boolean useMui2() {
        return false;
    }

    @Override
    public int getGUIHeight() {
        return 182;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        final int slotCount = mInventory.length;
        builder.widget(new SlotWidget(inventoryHandler, 0).setPos(52, 24));
        for (int i = 1; i < slotCount; i++) {
            builder.widget(new SlotWidget(inventoryHandler, i).setPos(106 + (i - 1) * 18, 24));
        }
        addDisplayTexts(builder);
    }

    protected void addDisplayTexts(ModularWindow.Builder builder) {}

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        ITexture background = TextureFactory.of(GregTechAPI.sBlockCasings2, 0);
        if (side == facing) {
            return new ITexture[] { background, aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { background };
    }

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
    }

    @Override
    public boolean isElectric() {
        return false;
    }

    @Override
    public boolean isPneumatic() {
        return false;
    }

    @Override
    public boolean isSteampowered() {
        return false;
    }

    @Override
    public boolean isEnetInput() {
        return false;
    }

    @Override
    public boolean isEnetOutput() {
        return false;
    }

    @Override
    public long getMinimumStoredEU() {
        return 0;
    }

    @Override
    public long maxEUStore() {
        return 0;
    }

    @Override
    public long maxEUInput() {
        return 0;
    }

    @Override
    public long maxEUOutput() {
        return 0;
    }

    @Override
    public long maxAmperesIn() {
        return 0;
    }

    @Override
    public long maxAmperesOut() {
        return 0;
    }

    @Override
    public int getProgresstime() {
        return mWorkProgress;
    }

    @Override
    public int maxProgresstime() {
        return maxProgresstimeInternal();
    }

    /**
     * Subclasses override this to return their work cycle length.
     * Used by both Waila progress display and internal tracking.
     */
    protected int maxProgresstimeInternal() {
        return WORK_CYCLE;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gt.blockmachines." + mName + ".name") };
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return isInputSlot(aIndex) && isMiningPipe(aStack);
    }

    protected boolean isInputSlot(int aIndex) {
        return aIndex == 0;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return !isInputSlot(aIndex);
    }

    @Override
    public byte getTileEntityBaseType() {
        return HarvestTool.WrenchLevel0.toTileEntityBaseType();
    }

    protected ItemStack consumeMiningPipeFromInputs() {
        int inputCount = getInputSlotCount();
        for (int i = 0; i < inputCount; i++) {
            ItemStack stack = mInventory[i];
            if (isMiningPipe(stack) && stack.stackSize > 0) {
                stack.stackSize--;
                ItemStack result = stack.copy();
                result.stackSize = 1;
                if (stack.stackSize <= 0) {
                    mInventory[i] = null;
                }
                return result;
            }
        }
        return null;
    }

    protected int getInputSlotCount() {
        return 1;
    }
}
