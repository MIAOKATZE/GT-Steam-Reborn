package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;

public class MTEReinforcedHubStorageUnit extends MTESteamStorageUnit {

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

    public MTEReinforcedHubStorageUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, REINFORCED_CAPACITY);
    }

    public MTEReinforcedHubStorageUnit(String aName, int capacity, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, capacity, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEReinforcedHubStorageUnit(mName, capacityPerUnit, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection sideDirection,
        ForgeDirection facingDirection, int colorIndex, boolean active, boolean redstoneLevel) {
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.reinforced_hub_storage_unit.info"),
            StatCollector.translateToLocal("gtsr.tooltip.reinforced_hub_storage_unit.capacity") };
    }
}
