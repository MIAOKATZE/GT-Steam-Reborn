package com.miaokatze.gtsr.common.machine;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public class MTESingularityMinerNode extends MTERemoteWorkerNode {

    private int mDepth = 0;
    private int mBaseY = -1;

    public MTESingularityMinerNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 5);
    }

    public MTESingularityMinerNode(String aName) {
        super(aName, 5);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESingularityMinerNode(mName);
    }

    @Override
    public String getNodeType() {
        return "miner";
    }

    @Override
    public void doWork(IGregTechTileEntity aBaseMetaTileEntity) {
        MTESingularityDrillingHub hub = getBoundHub();
        if (hub == null) {
            mIsWorking = false;
            return;
        }

        ItemStack pipeStack = mInventory[0];
        if (pipeStack == null || pipeStack.stackSize <= 0) {
            mIsWorking = false;
            return;
        }

        if (mBaseY < 0) {
            mBaseY = aBaseMetaTileEntity.getYCoord();
        }

        int mineY = mBaseY - 1 - mDepth;
        if (mineY <= 1) {
            mDepth = 0;
            mineY = mBaseY - 1;
        }

        World world = aBaseMetaTileEntity.getWorld();
        int x = aBaseMetaTileEntity.getXCoord();
        int z = aBaseMetaTileEntity.getZCoord();

        Block block = world.getBlock(x, mineY, z);
        int meta = world.getBlockMetadata(x, mineY, z);

        ItemStack oreDrop = null;
        if (block != null && block != Blocks.air && block != Blocks.bedrock) {
            oreDrop = new ItemStack(block, 1, meta);
        } else if (block == Blocks.bedrock) {
            mDepth = 0;
            mIsWorking = true;
            mWorkProgress = 0;
            return;
        }

        if (oreDrop != null && oreDrop.getItem() != null) {
            hub.pushNodeItemOutput(oreDrop);
        }

        pipeStack.stackSize--;
        if (pipeStack.stackSize <= 0) {
            mInventory[0] = null;
        }

        mDepth++;
        mIsWorking = true;
        mWorkProgress = (mWorkProgress + 20) % WORK_CYCLE;
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
