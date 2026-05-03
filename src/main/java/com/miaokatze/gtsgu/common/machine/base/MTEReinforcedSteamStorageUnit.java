package com.miaokatze.gtsgu.common.machine.base;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;

public class MTEReinforcedSteamStorageUnit extends MTESteamStorageUnit {

    public MTEReinforcedSteamStorageUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, REINFORCED_CAPACITY);
    }

    public MTEReinforcedSteamStorageUnit(String aName, int capacity, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, capacity, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEReinforcedSteamStorageUnit(mName, capacityPerUnit, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection sideDirection,
        ForgeDirection facingDirection, int colorIndex, boolean active, boolean redstoneLevel) {
        return new ITexture[] { TextureFactory.of(Textures.BlockIcons.MACHINE_STEEL_SIDE) };
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Reinforced Steam Storage Unit", "Structural component for Steam Hub Array",
            "Capacity contribution: 64,000,000 L per unit" };
    }
}
