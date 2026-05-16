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
import net.minecraftforge.fluids.FluidRegistry;
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

public class MTESingularityDrillingNode extends MTERemoteWorkerNode {

    private static final Block MINING_PIPE_TIP_BLOCK = GTUtility
        .getBlockFromStack(GTModHandler.getIC2Item("miningPipeTip", 0));
    private static final int FLUID_MULTIPLIER = 20;
    private static final int FLUID_BASE_AMOUNT = 50;

    private static final int STATUS_OK = 0;
    private static final int STATUS_NO_PIPE = 1;
    private static final int STATUS_NO_HUB = 2;
    private static final int STATUS_HUB_OFF = 3;
    private static final int STATUS_BEDROCK = 4;
    private static final int STATUS_SOFT_DISABLED = 5;

    private int mTipDepth = 0;
    private boolean mAtBedrock = false;
    private boolean mDisabled = false;
    private boolean mHasStarted = false;
    private boolean mSoftDisabled = false;
    private int mStatus = STATUS_OK;
    private boolean mLastAllowedToWork = true;
    private FakePlayer mFakePlayer;

    public MTESingularityDrillingNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
    }

    public MTESingularityDrillingNode(String aName) {
        super(aName, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESingularityDrillingNode(mName);
    }

    @Override
    public String getNodeType() {
        return "driller";
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
            mStatus = mSoftDisabled ? STATUS_SOFT_DISABLED : STATUS_BEDROCK;
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

        if (mAtBedrock) {
            FluidStack water = FluidRegistry.getFluidStack("water", FLUID_BASE_AMOUNT * FLUID_MULTIPLIER);
            if (water != null) {
                hub.pushNodeFluidOutput(water);
            }
            mStatus = STATUS_OK;
            mHasStarted = true;
            mIsWorking = true;
            mWorkProgress = (mWorkProgress + 20) % WORK_CYCLE;
            return;
        }

        int targetY = y + mTipDepth - 1;
        if (targetY <= 0) {
            mAtBedrock = true;
            return;
        }

        boolean isBedrock = GTUtility.getBlockHardnessAt(world, x, targetY, z) < 0;
        if (isBedrock) {
            mAtBedrock = true;
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
        mWorkProgress = (mWorkProgress + 20) % WORK_CYCLE;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        boolean allowedToWork = aBaseMetaTileEntity.isAllowedToWork();

        if (mLastAllowedToWork && !allowedToWork) {
            mSoftDisabled = true;
            mDisabled = true;
            mHasStarted = false;
            mStatus = STATUS_SOFT_DISABLED;
        } else if (!mLastAllowedToWork && allowedToWork && mSoftDisabled) {
            mSoftDisabled = false;
            mDisabled = false;
            mHasStarted = false;
            mStatus = STATUS_OK;
        }

        mLastAllowedToWork = allowedToWork;

        if (!mDisabled) {
            doWork(aBaseMetaTileEntity);
        }

        boolean shouldBeActive = mStatus == STATUS_OK && mHasStarted;
        if (shouldBeActive) {
            mIsWorking = true;
        }

        aBaseMetaTileEntity.setActive(mIsWorking);
        mIsWorking = false;
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
        addDisplayTexts(builder);
    }

    @Override
    protected void addDisplayTexts(ModularWindow.Builder builder) {
        builder.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> {
                        switch (mStatus) {
                            case STATUS_OK:
                                return EnumChatFormatting.GREEN
                                    + StatCollector.translateToLocal("gtsr.node.status.ok");
                            case STATUS_NO_PIPE:
                                return EnumChatFormatting.RED
                                    + StatCollector.translateToLocal("gtsr.node.status.no_pipe");
                            case STATUS_NO_HUB:
                                return EnumChatFormatting.RED
                                    + StatCollector.translateToLocal("gtsr.node.status.no_hub");
                            case STATUS_HUB_OFF:
                                return EnumChatFormatting.RED
                                    + StatCollector.translateToLocal("gtsr.node.status.hub_off");
                            case STATUS_BEDROCK:
                                return EnumChatFormatting.YELLOW
                                    + StatCollector.translateToLocal("gtsr.node.status.bedrock");
                            case STATUS_SOFT_DISABLED:
                                return EnumChatFormatting.YELLOW
                                    + StatCollector.translateToLocal("gtsr.node.status.soft_disabled");
                            default:
                                return EnumChatFormatting.GRAY
                                    + StatCollector.translateToLocal("gtsr.node.status.unknown");
                        }
                    })
                .setDefaultColor(0xFFFFFFFF)
                .setPos(10, 52));

        builder.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.depth")
                        + ": "
                        + mTipDepth)
                .setDefaultColor(0xFFFFFFFF)
                .setPos(10, 64));

        builder.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gtsr.node.driller_bedrock")
                        + ": "
                        + (mAtBedrock ? StatCollector.translateToLocal("gtsr.node.yes")
                            : StatCollector.translateToLocal("gtsr.node.no")))
                .setDefaultColor(0xFFFFFFFF)
                .setPos(10, 76));

        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mStatus, val -> mStatus = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mTipDepth, val -> mTipDepth = val));
        builder.widget(new FakeSyncWidget.BooleanSyncer(() -> mAtBedrock, val -> mAtBedrock = val));
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
