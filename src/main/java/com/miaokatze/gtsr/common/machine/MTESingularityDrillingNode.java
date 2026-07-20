package com.miaokatze.gtsr.common.machine;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.common.UndergroundOil;

public class MTESingularityDrillingNode extends MTERemoteWorkerNode {

    private static final Block MINING_PIPE_TIP_BLOCK = GTUtility
        .getBlockFromStack(GTModHandler.getIC2Item("miningPipeTip", 0));
    private static final float[] EXTRACTION_COEFFICIENT = { 10.0f, 12.0f, 15.0f, 18.0f };
    private static final int[] CHUNK_RANGE = { 1, 2, 4, 8 }; // chunks per side: 1×1, 2×2, 4×4, 8×8
    private static final int EXTRACTION_INTERVAL = 8;
    private static final int[] SINGULARITY_COST = { 0, 16, 32, 64 };

    private static final int STATUS_OK = 0;
    private static final int STATUS_NO_PIPE = 1;
    private static final int STATUS_NO_HUB = 2;
    private static final int STATUS_HUB_OFF = 3;
    private static final int STATUS_SOFT_DISABLED = 4;
    private static final int STATUS_EXTRACTING = 5;
    private static final int STATUS_FLUID_DEPLETED = 6;

    private int mTipDepth = 0;
    private boolean mAtBedrock = false;
    private boolean mDisabled = false;
    private boolean mRetractDone = false;
    private boolean mHasStarted = false;
    private boolean mSoftDisabled = false;
    private int mStatus = STATUS_OK;
    private boolean mLastAllowedToWork = true;
    private boolean mForcedRetract = false;
    private int mCycleTimer = 0;
    private int mExtractionCounter = 0;

    private String mLastFluidName = "";
    private int mLastExtractedAmount = 0;
    private int mDisplayRate = 0;
    private Fluid mLockedFluid = null;
    private int mDrillTier = 0; // 0=基础, 1=强化I, 2=强化II, 3=强化III

    @Override
    public int getProgresstime() {
        if (mAtBedrock) {
            return mExtractionCounter;
        }
        return mCycleTimer;
    }

    @Override
    protected int maxProgresstimeInternal() {
        if (mAtBedrock) {
            return EXTRACTION_INTERVAL;
        }
        return WORK_CYCLE;
    }

    private FakePlayer mFakePlayer;

    public MTESingularityDrillingNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 3);
    }

    public MTESingularityDrillingNode(String aName) {
        super(aName, 3);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESingularityDrillingNode(mName);
    }

    @Override
    public String getNodeType() {
        return "driller";
    }

    @Override
    public int getDrillTier() {
        return mDrillTier;
    }

    @Override
    public boolean isActivelyWorking() {
        return !mDisabled && mHasStarted;
    }

    @Override
    protected void collectWorkChunks(List<ChunkCoordIntPair> out) {
        // 钻井抽取循环为 for dx/dz in 0..range-1：以节点所在区块为起点向正方向 range×range，
        // 与基类「中心+半径」模型不同，故覆写收集钩子按正方向区块申请，与抽取范围严格一致
        int baseChunkX = getBaseMetaTileEntity().getXCoord() >> 4;
        int baseChunkZ = getBaseMetaTileEntity().getZCoord() >> 4;
        int range = CHUNK_RANGE[mDrillTier];
        for (int dx = 0; dx < range; dx++) {
            for (int dz = 0; dz < range; dz++) {
                out.add(new ChunkCoordIntPair(baseChunkX + dx, baseChunkZ + dz));
            }
        }
    }

    private float getExtractionCoefficient() {
        return EXTRACTION_COEFFICIENT[mDrillTier];
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.drilling_node.desc") };
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.drilling_node.extraction")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.tooltip.drilling_node.extraction_base"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.drilling_node.chunk_range")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.tooltip.drilling_node.chunk_range_base"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.work_cycle")
                + EnumChatFormatting.GREEN
                + StatCollector.translateToLocal("gtsr.tooltip.shared.8s"));
        tooltip.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.drilling_node.steam_cost"));
        tooltip.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.singularity_cost"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.node_bind_hint"));
        tooltip.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.drilling_node.upgrade_title"));
        tooltip
            .add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.drilling_node.upgrade_desc"));
        tooltip.add(
            EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.added_by")
                + " "
                + EnumChatFormatting.AQUA
                + "GT"
                + EnumChatFormatting.GREEN
                + "-"
                + EnumChatFormatting.GOLD
                + "Steam"
                + EnumChatFormatting.RED
                + "-"
                + EnumChatFormatting.BLUE
                + "Reborn");
    }

    @Override
    public boolean isInputSlot(int aIndex) {
        return aIndex < 3;
    }

    @Override
    protected int getInputSlotCount() {
        return 3;
    }

    private FakePlayer getFakePlayer(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mFakePlayer == null) {
            mFakePlayer = GTUtility.getFakePlayer(aBaseMetaTileEntity);
        }
        if (mFakePlayer != null) {
            mFakePlayer.setWorld(aBaseMetaTileEntity.getWorld());
            mFakePlayer.setPosition(
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord());
        }
        return mFakePlayer;
    }

    @Override
    public void doWork(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mDisabled) {
            mStatus = mSoftDisabled ? STATUS_SOFT_DISABLED : STATUS_FLUID_DEPLETED;
            return;
        }

        MTESingularityDrillingHub hub = getBoundHub();
        if (hub == null) {
            mStatus = STATUS_NO_HUB;
            return;
        }

        if (!hub.isMachineRunning()) {
            mStatus = STATUS_HUB_OFF;
            return;
        }

        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        World world = aBaseMetaTileEntity.getWorld();

        int targetY = y + mTipDepth - 1;

        if (targetY < 0) {
            mAtBedrock = true;
        } else {
            Block belowBlock = world.getBlock(x, targetY, z);
            if (belowBlock == Blocks.bedrock || GTUtility.getBlockHardnessAt(world, x, targetY, z) < 0) {
                mAtBedrock = true;
            }
        }

        if (mAtBedrock) {
            mExtractionCounter++;
            mStatus = STATUS_EXTRACTING;
            mHasStarted = true;

            if (mExtractionCounter >= EXTRACTION_INTERVAL) {
                mExtractionCounter = 0;

                int baseChunkX = x >> 4;
                int baseChunkZ = z >> 4;
                int range = CHUNK_RANGE[mDrillTier];

                int totalExtracted = 0;
                Fluid extractedFluid = null;

                for (int dx = 0; dx < range; dx++) {
                    for (int dz = 0; dz < range; dz++) {
                        FluidStack extracted = UndergroundOil
                            .undergroundOil(world, baseChunkX + dx, baseChunkZ + dz, getExtractionCoefficient());
                        if (extracted != null && extracted.amount > 0) {
                            if (mLockedFluid == null) {
                                mLockedFluid = extracted.getFluid();
                            }
                            if (extracted.getFluid() == mLockedFluid) {
                                totalExtracted += extracted.amount;
                                if (extractedFluid == null) {
                                    extractedFluid = extracted.getFluid();
                                }
                            }
                        }
                    }
                }

                if (totalExtracted <= 0) {
                    mDisabled = true;
                    mStatus = STATUS_FLUID_DEPLETED;
                    return;
                }

                mLastExtractedAmount = totalExtracted;
                mDisplayRate = mLastExtractedAmount;
                if (extractedFluid != null) {
                    mLastFluidName = new FluidStack(extractedFluid, 0).getLocalizedName();
                }
                FluidStack toPush = new FluidStack(mLockedFluid, totalExtracted);
                hub.pushNodeFluidOutput(toPush);
            }

            mIsWorking = true;
            return;
        }

        ItemStack consumed = consumeMiningPipeFromInputs();
        if (consumed == null) {
            mStatus = STATUS_NO_PIPE;
            return;
        }

        if (mTipDepth < 0) {
            int prevTipY = y + mTipDepth;
            if (world.getBlock(x, prevTipY, z) == MINING_PIPE_TIP_BLOCK) {
                Block pipeBlock = GTUtility.getBlockFromItem(consumed.getItem());
                if (pipeBlock != null && pipeBlock != Blocks.air) {
                    world.setBlock(x, prevTipY, z, pipeBlock);
                }
            }
        }

        GTUtility
            .setBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), x, targetY, z, MINING_PIPE_TIP_BLOCK, 0, false);

        mTipDepth--;

        mStatus = STATUS_OK;
        mHasStarted = true;
        mIsWorking = true;
    }

    private boolean retractOnePipe(IGregTechTileEntity aBaseMetaTileEntity) {
        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        World world = aBaseMetaTileEntity.getWorld();

        if (mTipDepth >= 0) {
            mTipDepth = 0;
            return false;
        }

        int tipY = y + mTipDepth;
        Block blockThere = world.getBlock(x, tipY, z);
        ItemStack pipeItem = GTModHandler.getIC2Item("miningPipe", 0);
        Block miningPipeBlock = pipeItem != null ? GTUtility.getBlockFromItem(pipeItem.getItem()) : null;

        if (blockThere == MINING_PIPE_TIP_BLOCK || blockThere == miningPipeBlock) {
            world.setBlockToAir(x, tipY, z);
            mTipDepth++;
            if (!mForcedRetract) {
                ItemStack pipeStack = GTModHandler.getIC2Item("miningPipe", 1);
                if (pipeStack != null) {
                    for (int i = 0; i < 3; i++) {
                        ItemStack slot = mInventory[i];
                        if (slot == null) {
                            mInventory[i] = pipeStack.copy();
                            break;
                        }
                        if (isMiningPipe(slot) && slot.stackSize < slot.getMaxStackSize()) {
                            slot.stackSize++;
                            break;
                        }
                    }
                }
            }
            return true;
        }

        mTipDepth++;
        if (mTipDepth >= 0) {
            mTipDepth = 0;
            return false;
        }
        return retractOnePipe(aBaseMetaTileEntity);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (!mRegistered && isBound()) {
            mRegistered = registerWithHub(aBaseMetaTileEntity);
        }

        // 本类未调用 super.onPostTick（避免基类 setActive/mIsWorking 逻辑与本类状态机冲突），
        // 故显式调用基类区块加载维护，按当前等级范围申请/释放工作区块
        updateChunkLoading(aBaseMetaTileEntity);

        boolean currentlyAllowed = aBaseMetaTileEntity.isAllowedToWork();
        if (currentlyAllowed && !mLastAllowedToWork) {
            mSoftDisabled = false;
            mDisabled = false;
            mRetractDone = false;
            mForcedRetract = false;
            mAtBedrock = false;
            mTipDepth = 0;
            mCycleTimer = 0;
            mExtractionCounter = 0;
            mHasStarted = false;
            mLastFluidName = "";
            mLastExtractedAmount = 0;
            mDisplayRate = 0;
            mLockedFluid = null;
        } else if (!currentlyAllowed && mLastAllowedToWork) {
            mSoftDisabled = true;
            mDisabled = true;
            mForcedRetract = true;
        }
        mLastAllowedToWork = currentlyAllowed;

        if (mDisabled && !mRetractDone) {
            if (!retractOnePipe(aBaseMetaTileEntity)) {
                mRetractDone = true;
            }
        } else if (!mDisabled) {
            mCycleTimer++;
            mWorkProgress = mAtBedrock ? mExtractionCounter : mCycleTimer;
            if (mCycleTimer >= WORK_CYCLE) {
                mCycleTimer = 0;
                mIsWorking = true;
                doWork(aBaseMetaTileEntity);
            }
        }

        if (!mDisabled) {
            mIsWorking = mIsWorking || (mCycleTimer > 0 && mCycleTimer < WORK_CYCLE);
        }

        boolean shouldBeActive = (mStatus == STATUS_OK || mStatus == STATUS_EXTRACTING) && mHasStarted;
        aBaseMetaTileEntity.setActive(shouldBeActive);
        if (!shouldBeActive) {
            mIsWorking = false;
        }
    }

    @Override
    public String[] getInfoData() {
        String statusText;
        switch (mStatus) {
            case STATUS_OK:
                statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.node.status.ok");
                break;
            case STATUS_NO_PIPE:
                statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.no_pipe");
                break;
            case STATUS_NO_HUB:
                statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.no_hub");
                break;
            case STATUS_HUB_OFF:
                statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.hub_off");
                break;
            case STATUS_SOFT_DISABLED:
                statusText = EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.node.status.soft_disabled");
                break;
            case STATUS_EXTRACTING:
                statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.node.status.extracting")
                    + ": "
                    + mLastFluidName
                    + " "
                    + mDisplayRate
                    + " "
                    + StatCollector.translateToLocal("gtsr.gui.drilling_node.output_per_8s");
                break;
            case STATUS_FLUID_DEPLETED:
                statusText = EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.node.status.fluid_depleted");
                break;
            default:
                statusText = EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.node.status.unknown");
                break;
        }

        String depthText = EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.depth")
            + ": "
            + mTipDepth;

        String bedrockText = EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.driller_bedrock")
            + ": "
            + (mAtBedrock ? StatCollector.translateToLocal("gtsr.node.yes")
                : StatCollector.translateToLocal("gtsr.node.no"));

        return new String[] { statusText, depthText, bedrockText };
    }

    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTESingularityDrillingNode_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTESingularityDrillingNode_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(OVERLAY_OFF);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(OVERLAY_ON);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(new SlotWidget(inventoryHandler, 0).setPos(52, 24));
        builder.widget(new SlotWidget(inventoryHandler, 1).setPos(70, 24));
        builder.widget(new SlotWidget(inventoryHandler, 2).setPos(88, 24));
        // 升级按钮：点击后从玩家背包消耗对应等级油气钻井物品与奇点进行升级
        builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 0 && !widget.isClient()) {
                tryUpgrade(buildContext.getPlayer());
            }
        })
            .setPlayClickSound(true)
            .setBackground(GTUITextures.BUTTON_STANDARD, GTUITextures.OVERLAY_BUTTON_ARROW_GREEN_UP)
            .dynamicTooltip(this::getUpgradeTooltip)
            .setTooltipShowUpDelay(TOOLTIP_DELAY)
            .setPos(150, 24)
            .setSize(18, 18));
        addDisplayTexts(builder);
    }

    @Override
    protected void addDisplayTexts(ModularWindow.Builder builder) {
        builder.widget(new TextWidget().setStringSupplier(() -> {
            switch (mStatus) {
                case STATUS_OK:
                    return EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.node.status.ok");
                case STATUS_NO_PIPE:
                    return EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.no_pipe");
                case STATUS_NO_HUB:
                    return EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.no_hub");
                case STATUS_HUB_OFF:
                    return EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.hub_off");
                case STATUS_SOFT_DISABLED:
                    return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.node.status.soft_disabled");
                case STATUS_EXTRACTING:
                    return EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.node.status.extracting")
                        + ": "
                        + mLastFluidName
                        + " "
                        + mDisplayRate
                        + " "
                        + StatCollector.translateToLocal("gtsr.gui.drilling_node.output_per_8s");
                case STATUS_FLUID_DEPLETED:
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.node.status.fluid_depleted");
                default:
                    return EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.node.status.unknown");
            }
        })
            .setDefaultColor(0xFFFFFFFF)
            .setPos(10, 52));

        builder.widget(
            new TextWidget().setStringSupplier(
                () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.depth") + ": " + mTipDepth)
                .setDefaultColor(0xFFFFFFFF)
                .setPos(10, 64));

        builder.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.driller_bedrock")
                        + ": "
                        + (mAtBedrock ? StatCollector.translateToLocal("gtsr.node.yes")
                            : StatCollector.translateToLocal("gtsr.node.no")))
                .setDefaultColor(0xFFFFFFFF)
                .setPos(10, 76));

        builder
            .widget(
                new TextWidget()
                    .setStringSupplier(
                        () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.drilling_node.tier")
                            + " "
                            + EnumChatFormatting.AQUA
                            + (mDrillTier == 0 ? StatCollector.translateToLocal("gtsr.gui.drilling_node.base")
                                : StatCollector.translateToLocal("gtsr.gui.drilling_node.enhanced")
                                    + toRoman(mDrillTier)))
                    .setDefaultColor(0xFFFFFFFF)
                    .setPos(10, 88));

        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mStatus, val -> mStatus = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mTipDepth, val -> mTipDepth = val));
        builder.widget(new FakeSyncWidget.BooleanSyncer(() -> mAtBedrock, val -> mAtBedrock = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mLastExtractedAmount, val -> mLastExtractedAmount = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mDisplayRate, val -> mDisplayRate = val));
        builder.widget(new FakeSyncWidget.StringSyncer(() -> mLastFluidName, val -> mLastFluidName = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mDrillTier, val -> mDrillTier = val));
    }

    @Override
    public void saveNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mTipDepth", mTipDepth);
        aNBT.setBoolean("mAtBedrock", mAtBedrock);
        aNBT.setBoolean("mDisabled", mDisabled);
        aNBT.setBoolean("mHasStarted", mHasStarted);
        aNBT.setBoolean("mSoftDisabled", mSoftDisabled);
        aNBT.setInteger("mStatus", mStatus);
        aNBT.setBoolean("mRetractDone", mRetractDone);
        aNBT.setBoolean("mForcedRetract", mForcedRetract);
        aNBT.setInteger("mCycleTimer", mCycleTimer);
        aNBT.setInteger("mExtractionCounter", mExtractionCounter);
        aNBT.setInteger("mDisplayRate", mDisplayRate);
        aNBT.setString("mLastFluidName", mLastFluidName);
        aNBT.setInteger("mLastExtractedAmount", mLastExtractedAmount);
        aNBT.setInteger("mDrillTier", mDrillTier);
        if (mLockedFluid != null) {
            aNBT.setString("mLockedFluid", mLockedFluid.getName());
        }
    }

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mTipDepth = aNBT.getInteger("mTipDepth");
        mAtBedrock = aNBT.getBoolean("mAtBedrock");
        if (aNBT.hasKey("mDisabled")) {
            mDisabled = aNBT.getBoolean("mDisabled");
        }
        if (aNBT.hasKey("mHasStarted")) {
            mHasStarted = aNBT.getBoolean("mHasStarted");
        }
        if (aNBT.hasKey("mSoftDisabled")) {
            mSoftDisabled = aNBT.getBoolean("mSoftDisabled");
        }
        if (aNBT.hasKey("mStatus")) {
            mStatus = aNBT.getInteger("mStatus");
        }
        if (aNBT.hasKey("mRetractDone")) {
            mRetractDone = aNBT.getBoolean("mRetractDone");
        }
        if (aNBT.hasKey("mForcedRetract")) {
            mForcedRetract = aNBT.getBoolean("mForcedRetract");
        }
        if (aNBT.hasKey("mCycleTimer")) {
            mCycleTimer = aNBT.getInteger("mCycleTimer");
        }
        if (aNBT.hasKey("mExtractionCounter")) {
            mExtractionCounter = aNBT.getInteger("mExtractionCounter");
        }
        if (aNBT.hasKey("mDisplayRate")) {
            mDisplayRate = aNBT.getInteger("mDisplayRate");
        }
        if (aNBT.hasKey("mLastFluidName")) {
            mLastFluidName = aNBT.getString("mLastFluidName");
        }
        if (aNBT.hasKey("mLastExtractedAmount")) {
            mLastExtractedAmount = aNBT.getInteger("mLastExtractedAmount");
        }
        if (aNBT.hasKey("mDrillTier")) {
            mDrillTier = aNBT.getInteger("mDrillTier");
        }
        // Legacy: migrate old mEnhanced boolean
        if (aNBT.hasKey("mEnhanced") && aNBT.getBoolean("mEnhanced")) {
            mDrillTier = Math.max(mDrillTier, 1);
        }
        if (aNBT.hasKey("mLockedFluid")) {
            mLockedFluid = net.minecraftforge.fluids.FluidRegistry.getFluid(aNBT.getString("mLockedFluid"));
        }
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (!aBaseMetaTileEntity.isServerSide()) return true;

        ItemStack held = aPlayer.getHeldItem();

        // Default: show binding info
        if (held == null && mHubX != 0) {
            GTUtility.sendChatToPlayer(
                aPlayer,
                StatCollector.translateToLocal(
                    "gtsr.binding.bound_to") + " Hub @ " + mHubX + ", " + mHubY + ", " + mHubZ);
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    /**
     * 节点是否已完全停止：已禁止工作且钻探管道全部收回（mTipDepth==0，含从未下管）。
     * 仅完全停止的节点允许从枢纽状态 UI 快捷回收。
     */
    @Override
    public boolean isFullyRetracted() {
        return super.isFullyRetracted() && mTipDepth == 0;
    }

    /**
     * 尝试升级节点：从玩家背包查找并消耗对应等级的油气钻井物品与蒸汽纠缠奇点。
     * 升级成功返回 true 并发送聊天提示；失败时发送原因提示并返回 false。
     * public：枢纽状态 UI 的远程升级按钮通过基类引用多态调用。
     */
    @Override
    public boolean tryUpgrade(EntityPlayer player) {
        // 已满级
        if (mDrillTier >= 3) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.gui.drilling_node.max_tier"));
            return false;
        }
        int targetTier = mDrillTier + 1;
        // 从背包查找目标等级对应的油气钻井物品（升级需逐级进行，目标等级固定为当前+1）
        int drillSlot = findDrillItemSlot(player, targetTier);
        if (drillSlot < 0) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.gui.drilling_node.need_drill"));
            return false;
        }
        int cost = SINGULARITY_COST[targetTier];
        if (!consumeSingularityItems(player, cost)) {
            GTUtility.sendChatToPlayer(
                player,
                StatCollector.translateToLocal("gtsr.gui.drilling_node.need_singularity") + " " + cost);
            return false;
        }
        ItemStack drillStack = player.inventory.mainInventory[drillSlot];
        drillStack.stackSize--;
        if (drillStack.stackSize <= 0) player.inventory.mainInventory[drillSlot] = null;
        mDrillTier = targetTier;
        player.inventoryContainer.detectAndSendChanges();
        GTUtility.sendChatToPlayer(
            player,
            StatCollector.translateToLocal("gtsr.gui.drilling_node.applied_enhanced") + " " + toRoman(targetTier));
        // 等级已变化：释放旧范围区块加载 ticket，下一 tick 按新范围重新申请
        onTierChanged();
        return true;
    }

    /**
     * 在玩家背包中查找目标等级对应的油气钻井物品，返回槽位下标，未找到返回 -1。
     */
    private int findDrillItemSlot(EntityPlayer player, int targetTier) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack == null) continue;
            boolean match = switch (targetTier) {
                case 1 -> ItemList.OilDrill2.isStackEqual(stack, false, true);
                case 2 -> ItemList.OilDrill3.isStackEqual(stack, false, true);
                case 3 -> ItemList.OilDrill4.isStackEqual(stack, false, true);
                default -> false;
            };
            if (match) return i;
        }
        return -1;
    }

    /**
     * 获取目标等级对应的油气钻井物品（用于升级按钮 tooltip 展示）。
     */
    private ItemStack getDrillItemForTier(int tier) {
        return switch (tier) {
            case 1 -> ItemList.OilDrill2.get(1);
            case 2 -> ItemList.OilDrill3.get(1);
            case 3 -> ItemList.OilDrill4.get(1);
            default -> null;
        };
    }

    /**
     * 升级按钮的动态 tooltip：说明下一级所需油气钻井物品与奇点数量。
     */
    private List<String> getUpgradeTooltip() {
        List<String> tips = new ArrayList<>();
        tips.add(
            EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.title")
                + EnumChatFormatting.RESET);
        if (mDrillTier >= 3) {
            tips.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.max"));
            return tips;
        }
        int next = mDrillTier + 1;
        tips.add(
            EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.next")
                + " "
                + StatCollector.translateToLocal("gtsr.gui.drilling_node.enhanced")
                + toRoman(next));
        ItemStack drillStack = getDrillItemForTier(next);
        if (drillStack != null) {
            tips.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.drill")
                    + " "
                    + drillStack.getDisplayName());
        }
        tips.add(
            EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.singularity")
                + " "
                + SINGULARITY_COST[next]);
        return tips;
    }

    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(num);
        };
    }

    private boolean consumeSingularityItems(EntityPlayer player, int count) {
        int found = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(stack, false, true)) {
                found += stack.stackSize;
            }
        }
        if (found < count) return false;

        int remaining = count;
        for (int i = 0; i < player.inventory.mainInventory.length && remaining > 0; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(stack, false, true)) {
                int toConsume = Math.min(remaining, stack.stackSize);
                stack.stackSize -= toConsume;
                remaining -= toConsume;
                if (stack.stackSize <= 0) {
                    player.inventory.mainInventory[i] = null;
                }
            }
        }
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }
}
