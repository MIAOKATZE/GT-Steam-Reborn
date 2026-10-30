package com.miaokatze.gtsr.common.machine.base;

import java.lang.reflect.Field;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.IPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.util.GTModHandler;
import gregtech.common.blocks.BlockCasings2;

// O2-B03②：接入 api 契约接口（pushCoolingSteam 既有公有方法，冷却水侧继承父类 ICoolingHatch 实现），零行为变化
@IMetaTileEntity.SkipGenerateDescription
public class MTEPressureSteamCoolingHatch extends MTESteamCoolingHatch implements IPressureSteamCoolingHatch {

    private static final int PRESSURE_CAPACITY = 1_024_000;
    private static final int OUTPUT_PER_TICK = 51_200;
    private static final int DEFAULT_TEXTURE_INDEX = ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);

    public MTEPressureSteamCoolingHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 8);
        setPressureDefaultTextureIndex();
    }

    public MTEPressureSteamCoolingHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        setPressureDefaultTextureIndex();
    }

    private void setPressureDefaultTextureIndex() {
        try {
            Field texturePageField = MTEHatch.class.getDeclaredField("texturePage");
            texturePageField.setAccessible(true);
            texturePageField.setInt(this, DEFAULT_TEXTURE_INDEX >> 7);

            Field textureIndexField = MTEHatch.class.getDeclaredField("textureIndex");
            textureIndexField.setAccessible(true);
            textureIndexField.setInt(this, DEFAULT_TEXTURE_INDEX & 127);
        } catch (Exception ignored) {}
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPressureSteamCoolingHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public int getCapacity() {
        return PRESSURE_CAPACITY;
    }

    @Override
    public boolean canStoreFluid(FluidStack aFluidToStore) {
        if (aFluidToStore == null) return false;
        return GTModHandler.isSteam(aFluidToStore);
    }

    public int pushCoolingSteam(int superheatedSteamConsumed) {
        if (superheatedSteamConsumed <= 0) return 0;
        FluidStack steam = Materials.Steam.getGas(superheatedSteamConsumed);
        return this.fill(ForgeDirection.UNKNOWN, steam, true);
    }

    @Override
    protected int getOutputPerTick() {
        return OUTPUT_PER_TICK;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.pressure_steam_cooling_hatch.name"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + String.format("%,d", PRESSURE_CAPACITY)
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"),
            EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.pressure_steam_cooling_hatch.desc"),
            EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.output_rate")
                + EnumChatFormatting.GOLD
                + String.format("%,d", OUTPUT_PER_TICK * 20)
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s"),
            EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.no_external_input"),
            GTSRUtils.getAddedByLine() };
    }
}
