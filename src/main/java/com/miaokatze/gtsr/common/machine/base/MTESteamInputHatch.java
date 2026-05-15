package com.miaokatze.gtsr.common.machine.base;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.common.blocks.BlockCasings1;

public class MTESteamInputHatch extends MTEHatchInput {

    private static final int CAPACITY = 128_000;
    private static final int DEFAULT_TEXTURE_INDEX = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

    public MTESteamInputHatch(int aID, String aName, String aNameRegional) {
        this(aID, aName, aNameRegional, 1);
    }

    public MTESteamInputHatch(int aID, String aName, String aNameRegional, int aTier) {
        this(
            aID,
            aName,
            aNameRegional,
            aTier,
            new String[] { "Steam Input for Multiblocks", "Capacity: " + CAPACITY + "L", "Fluid Type: Any Fluid",
                "For Steam Multiblock Machines" },
            4);
    }

    public MTESteamInputHatch(int aID, String aName, String aNameRegional, int aTier, String[] aDescription,
        int inventorySize) {
        super(aID, inventorySize, aName, aNameRegional, aTier, aDescription);
        setDefaultTextureIndex();
    }

    public MTESteamInputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, 4, aTier, aDescription, aTextures);
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
        return new MTESteamInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        return false;
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.steam_input_hatch.name"),
            EnumChatFormatting.AQUA + "Capacity: " + CAPACITY + " L",
            EnumChatFormatting.GREEN + "Fluid Type: Any Fluid",
            EnumChatFormatting.RED + "For Steam Multiblock Machines" };
    }
}
