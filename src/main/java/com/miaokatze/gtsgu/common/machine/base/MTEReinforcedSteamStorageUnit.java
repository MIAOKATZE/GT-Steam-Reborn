package com.miaokatze.gtsgu.common.machine.base;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_STEEL_BOTTOM;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_STEEL_SIDE;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_STEEL_TOP;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE;

import net.minecraftforge.common.util.ForgeDirection;

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
        if (sideDirection == ForgeDirection.UP) {
            return new ITexture[] { TextureFactory.of(MACHINE_STEEL_TOP) };
        } else if (sideDirection == ForgeDirection.DOWN) {
            return new ITexture[] { TextureFactory.of(MACHINE_STEEL_BOTTOM) };
        } else if (sideDirection == facingDirection) {
            return new ITexture[] { TextureFactory.of(MACHINE_STEEL_SIDE), TextureFactory.of(OVERLAY_PIPE) };
        }
        return new ITexture[] { TextureFactory.of(MACHINE_STEEL_SIDE) };
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Reinforced Steam Storage Unit", "Structural component for Steam Hub Array",
            "Capacity contribution: 64,000,000 L" };
    }
}
