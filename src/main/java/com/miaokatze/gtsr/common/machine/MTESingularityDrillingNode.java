package com.miaokatze.gtsr.common.machine;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

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

    private int mTipDepth = 0;
    private boolean mAtBedrock = false;
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
        MTESingularityDrillingHub hub = getBoundHub();
        if (hub == null) {
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
        mIsWorking = true;
        mWorkProgress = (mWorkProgress + 20) % WORK_CYCLE;
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
    public void saveNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mTipDepth", mTipDepth);
        aNBT.setBoolean("mAtBedrock", mAtBedrock);
    }

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mTipDepth = aNBT.getInteger("mTipDepth");
        mAtBedrock = aNBT.getBoolean("mAtBedrock");
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
