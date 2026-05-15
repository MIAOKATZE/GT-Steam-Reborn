package com.miaokatze.gtsr.common.machine;

import java.lang.reflect.Field;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.common.blocks.BlockCasings1;

public class MTESteamInputBus extends MTEHatchInputBus {

    private static final int DEFAULT_TEXTURE_INDEX = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

    public MTESteamInputBus(int aID, String aName, String aNameRegional) {
        this(aID, aName, aNameRegional, 1);
    }

    public MTESteamInputBus(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            getSlots() + 1,
            new String[] { StatCollector.translateToLocal("gtsr.tooltip.steam_input_bus.name"),
                StatCollector.translateToLocal("gtsr.tooltip.steam_input_bus.info"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.steam_input_bus.restriction") });
        setDefaultTextureIndex();
    }

    public MTESteamInputBus(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, getSlots() + 1, aDescription, aTextures);
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

    public static int getSlots() {
        return 4;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamInputBus(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public int getCircuitSlot() {
        return getSlots();
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }
}
