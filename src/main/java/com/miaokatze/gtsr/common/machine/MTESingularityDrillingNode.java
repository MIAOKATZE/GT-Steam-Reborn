package com.miaokatze.gtsr.common.machine;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import gregtech.api.enums.Textures;
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
    private static final float EXTRACTION_COEFFICIENT = 80.0f;
    private static final int EXTRACTION_INTERVAL = 16;

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

                int chunkX = x >> 4;
                int chunkZ = z >> 4;

                FluidStack extracted = UndergroundOil.undergroundOil(world, chunkX, chunkZ, EXTRACTION_COEFFICIENT);

                if (extracted == null || extracted.amount <= 0) {
                    mDisabled = true;
                    mStatus = STATUS_FLUID_DEPLETED;
                    return;
                }

                if (mLockedFluid == null) {
                    mLockedFluid = extracted.getFluid();
                }

                mLastExtractedAmount = extracted.amount;
                mDisplayRate = mLastExtractedAmount / 160;
                mLastFluidName = extracted.getLocalizedName();
                FluidStack toPush = extracted.copy();
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
                statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal(
                    "gtsr.node.status.extracting") + ": " + mLastFluidName + " " + mDisplayRate + " L/s";
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

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(new SlotWidget(inventoryHandler, 0).setPos(52, 24));
        builder.widget(new SlotWidget(inventoryHandler, 1).setPos(70, 24));
        builder.widget(new SlotWidget(inventoryHandler, 2).setPos(88, 24));
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
                    return EnumChatFormatting.GREEN + StatCollector.translateToLocal(
                        "gtsr.node.status.extracting") + ": " + mLastFluidName + " " + mDisplayRate + " L/s";
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

        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mStatus, val -> mStatus = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mTipDepth, val -> mTipDepth = val));
        builder.widget(new FakeSyncWidget.BooleanSyncer(() -> mAtBedrock, val -> mAtBedrock = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mLastExtractedAmount, val -> mLastExtractedAmount = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mDisplayRate, val -> mDisplayRate = val));
        builder.widget(new FakeSyncWidget.StringSyncer(() -> mLastFluidName, val -> mLastFluidName = val));
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
        if (aNBT.hasKey("mLockedFluid")) {
            mLockedFluid = net.minecraftforge.fluids.FluidRegistry.getFluid(aNBT.getString("mLockedFluid"));
        }
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (!aBaseMetaTileEntity.isClientSide() && aPlayer.getHeldItem() == null && mHubX != 0) {
            GTUtility.sendChatToPlayer(
                aPlayer,
                StatCollector.translateToLocal(
                    "gtsr.binding.bound_to") + " Hub @ " + mHubX + ", " + mHubY + ", " + mHubZ);
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }
}
