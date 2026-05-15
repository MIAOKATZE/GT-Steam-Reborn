package com.miaokatze.gtsr.common.machine;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;

public class MTESingularityDrillingNode extends MTERemoteWorkerNode {

    private static final int FLUID_MULTIPLIER = 20;
    private static final int FLUID_BASE_AMOUNT = 50;

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

        FluidStack water = FluidRegistry.getFluidStack("water", FLUID_BASE_AMOUNT * FLUID_MULTIPLIER);
        if (water != null) {
            hub.pushNodeFluidOutput(water);
        }

        pipeStack.stackSize--;
        if (pipeStack.stackSize <= 0) {
            mInventory[0] = null;
        }

        mIsWorking = true;
        mWorkProgress = (mWorkProgress + 20) % WORK_CYCLE;
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_WATER_PUMP);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_WATER_PUMP_ACTIVE);
    }
}
