package com.miaokatze.gtsgu.common.machine.base;

import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;

public class MTESteamHubInputHatch extends MTEHatchInput {

    private static final int STEAM_HUB_CAPACITY = 2_000_000;

    public MTESteamHubInputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
    }

    public MTESteamHubInputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamHubInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public int getCapacity() {
        return STEAM_HUB_CAPACITY;
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        if (aFluid == null) return false;
        return MTESteamHubOutputHatch.isSteamFluid(aFluid);
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Steam Hub Array Input Hatch", "Accepts Steam/Superheated Steam only",
            "Capacity: 2,000,000 L" };
    }
}
