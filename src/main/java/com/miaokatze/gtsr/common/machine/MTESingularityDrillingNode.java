package com.miaokatze.gtsr.common.machine;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

public class MTESingularityDrillingNode extends MTERemoteWorkerNode {

    private static final int FLUID_MULTIPLIER = 20;
    private static final int FLUID_BASE_AMOUNT = 50;

    private int mDepth = 0;
    private int mBaseY = -1;
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

        ItemStack pipeStack = mInventory[0];
        if (!isMiningPipe(pipeStack) || pipeStack.stackSize <= 0) {
            return;
        }

        Block pipeBlock = GTUtility.getBlockFromItem(pipeStack.getItem());
        if (pipeBlock == null || pipeBlock == Blocks.air) {
            return;
        }

        if (mBaseY < 0) {
            mBaseY = aBaseMetaTileEntity.getYCoord();
        }

        int drillY = mBaseY - 1 - mDepth;
        if (drillY <= 1) {
            mDepth = 0;
            drillY = mBaseY - 1;
        }

        World world = aBaseMetaTileEntity.getWorld();
        int x = aBaseMetaTileEntity.getXCoord();
        int z = aBaseMetaTileEntity.getZCoord();

        FluidStack water = FluidRegistry.getFluidStack("water", FLUID_BASE_AMOUNT * FLUID_MULTIPLIER);
        if (water != null) {
            hub.pushNodeFluidOutput(water);
        }

        GTUtility.setBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), x, drillY, z, pipeBlock, 0, false);

        pipeStack.stackSize--;
        if (pipeStack.stackSize <= 0) {
            mInventory[0] = null;
        }

        mDepth++;
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
        aNBT.setInteger("mDepth", mDepth);
        aNBT.setInteger("mBaseY", mBaseY);
    }

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mDepth = aNBT.getInteger("mDepth");
        mBaseY = aNBT.getInteger("mBaseY");
    }
}