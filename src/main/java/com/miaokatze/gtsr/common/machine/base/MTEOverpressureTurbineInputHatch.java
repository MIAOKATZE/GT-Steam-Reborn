package com.miaokatze.gtsr.common.machine.base;

import java.lang.reflect.Field;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.GTUtility;

/**
 * Overpressure Turbine Input Hatch - a specialized input hatch that only accepts steam fluids.
 * Uses the standard MTEBasicTank mFluid storage (no dual storage).
 */
public class MTEOverpressureTurbineInputHatch extends MTEHatchInput {

    private static final int CAPACITY = 2_000_000_000;
    private static final int DEFAULT_TEXTURE_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);

    public MTEOverpressureTurbineInputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
        setCustomCapacity(CAPACITY);
        setDefaultTextureIndex();
    }

    public MTEOverpressureTurbineInputHatch(String aName, int aTier, String[] aDescription,
        gregtech.api.interfaces.ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        setCustomCapacity(CAPACITY);
        setDefaultTextureIndex();
    }

    private void setDefaultTextureIndex() {
        try {
            Field texturePageField = gregtech.api.metatileentity.implementations.MTEHatch.class
                .getDeclaredField("texturePage");
            texturePageField.setAccessible(true);
            texturePageField.setInt(this, DEFAULT_TEXTURE_INDEX >> 7);

            Field textureIndexField = gregtech.api.metatileentity.implementations.MTEHatch.class
                .getDeclaredField("textureIndex");
            textureIndexField.setAccessible(true);
            textureIndexField.setInt(this, DEFAULT_TEXTURE_INDEX & 127);
        } catch (Exception ignored) {}
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEOverpressureTurbineInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        if (aFluid == null) return false;
        Fluid fluid = aFluid.getFluid();
        if (fluid == null) return false;
        String name = fluid.getName();
        if (name == null) return false;
        switch (name) {
            case "steam":
            case "ic2superheatedsteam":
            case "densesteam":
            case "densesuperheatedsteam":
            case "supercriticalsteam":
            case "densesupercriticalsteam":
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean doesEmptyContainers() {
        return true;
    }

    /**
     * Consume steam from this hatch. Used by the turbine array.
     */
    public void consumeSteam(int amount) {
        if (mFluid == null || amount <= 0) return;
        mFluid.amount -= amount;
        if (mFluid.amount <= 0) {
            mFluid = null;
        }
        getBaseMetaTileEntity().markDirty();
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.DARK_AQUA
                + StatCollector.translateToLocal("gtsr.tooltip.overpressure_turbine_input_hatch.info"),
            EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("gtsr.tooltip.overpressure_turbine_input_hatch.fluid_type"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + String.format("%,d", CAPACITY)
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"),
            EnumChatFormatting.GRAY
                + StatCollector.translateToLocal("gtsr.tooltip.overpressure_turbine_input_hatch.turbine_only"),
            EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.added_by")
                + " "
                + EnumChatFormatting.AQUA
                + "GT"
                + EnumChatFormatting.GREEN
                + "-"
                + EnumChatFormatting.GOLD
                + "Steam"
                + EnumChatFormatting.RED
                + "-"
                + EnumChatFormatting.BLUE
                + "Reborn" };
    }
}
