package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.util.StatCollector;
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
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.reinforced_steam_storage_unit.info"),
            StatCollector.translateToLocal("gtsr.tooltip.reinforced_steam_storage_unit.capacity") };
    }
}
