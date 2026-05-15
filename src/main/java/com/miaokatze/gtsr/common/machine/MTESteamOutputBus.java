package com.miaokatze.gtsr.common.machine;

import java.lang.reflect.Field;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.common.blocks.BlockCasings1;

public class MTESteamOutputBus extends MTEHatchOutputBus {

    private static final int DEFAULT_TEXTURE_INDEX = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

    public MTESteamOutputBus(int aID, String aName, String aNameRegional) {
        this(aID, aName, aNameRegional, 1);
    }

    public MTESteamOutputBus(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            new String[] { StatCollector.translateToLocal("gtsr.tooltip.steam_output_bus.name"),
                StatCollector.translateToLocal("gtsr.tooltip.steam_output_bus.info"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.steam_output_bus.restriction") },
            4);
        setDefaultTextureIndex();
    }

    public MTESteamOutputBus(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 4, aDescription, aTextures);
        setDefaultTextureIndex();
    }

    private void setDefaultTextureIndex() {
        try {
            Field texturePageField = MTEHatch.class.getDeclaredField("texturePage");
            texturePageField.setAccessible(true);
            texturePageField.setInt(this, 0);

            Field textureIndexField = MTEHatch.class.getDeclaredField("textureIndex");
            textureIndexField.setAccessible(true);
            textureIndexField.setInt(this, DEFAULT_TEXTURE_INDEX & 127);
        } catch (Exception ignored) {}
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamOutputBus(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean pushOutputInventory() {
        return false;
    }
}
