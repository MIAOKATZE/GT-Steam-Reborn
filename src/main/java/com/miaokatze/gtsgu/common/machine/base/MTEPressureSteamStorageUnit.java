package com.miaokatze.gtsgu.common.machine.base;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

public class MTEPressureSteamStorageUnit extends MTESteamStorageUnit {

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    public MTEPressureSteamStorageUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, PRESSURE_CAPACITY);
    }

    public MTEPressureSteamStorageUnit(String aName, int capacity, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, capacity, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPressureSteamStorageUnit(mName, capacityPerUnit, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection sideDirection,
        ForgeDirection facingDirection, int colorIndex, boolean active, boolean redstoneLevel) {
        if (sideDirection == facingDirection) {
            return new ITexture[] { getCasingTexture(), TextureFactory.of(OVERLAY_PIPE) };
        }
        return new ITexture[] { getCasingTexture() };
    }

    private ITexture getCasingTexture() {
        return Textures.BlockIcons.getCasingTextureForId(CASING_INDEX);
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Pressure Steam Storage Unit", "Structural component for Steam Hub Array",
            "Capacity contribution: 16,000,000 L" };
    }
}
