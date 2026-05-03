package com.miaokatze.gtsgu.common.machine.base;

import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;

public class MTESteamHubOutputHatch extends MTEHatchOutput {

    private static final int STEAM_HUB_CAPACITY = 2_000_000;

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
    public int getCapacity() {
        return STEAM_HUB_CAPACITY;
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
        return new String[] { "Steam Hub Array Output Hatch", "Outputs Steam/Superheated Steam only",
            "Capacity: 2,000,000 L" };
    }
}
