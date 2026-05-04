package com.miaokatze.gtsgu.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsgu.common.machine.MTESteamHubArray;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;

public class MTESteamHubOutputHatch extends MTEHatchOutput {

    public MTESteamHubArray mController;

    public MTESteamHubOutputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
        this.mMode = 3;
    }

    public MTESteamHubOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        this.mMode = 3;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamHubOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (mController != null) {
            if (mController.isFormed()) {
                return mController.extractSteam(maxDrain, doDrain);
            }
            mController = null;
        }
        return super.drain(maxDrain, doDrain);
    }

    @Override
    public FluidStack getFluid() {
        if (mController != null && mController.isFormed()) {
            return mController.getStoredFluidStack();
        }
        return super.getFluid();
    }

    @Override
    public int getCapacity() {
        if (mController != null && mController.isFormed()) {
            return (int) Math.min(mController.getTotalCapacity(), Integer.MAX_VALUE);
        }
        return 2_000_000;
    }

    @Override
    public boolean canStoreFluid(FluidStack fluidStack) {
        if (fluidStack == null) return false;
        if (!isSteamFluid(fluidStack)) return false;
        return super.canStoreFluid(fluidStack);
    }

    @Override
    public boolean acceptsFluidLock(String name) {
        return false;
    }

    public static boolean isSteamFluid(FluidStack aFluid) {
        return GTModHandler.isAnySteam(aFluid) || GTModHandler.isSuperHeatedSteam(aFluid);
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_output_hatch.info"),
            StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_output_hatch.fluid_type"),
            StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_output_hatch.output_rate"),
            StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_output_hatch.no_storage") };
    }
}
