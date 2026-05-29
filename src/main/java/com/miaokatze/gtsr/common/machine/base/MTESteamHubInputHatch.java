package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.MTESteamHubArray;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;

public class MTESteamHubInputHatch extends MTEHatchInput {

    public MTESteamHubArray mController;

    public MTESteamHubInputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
    }

    public MTESteamHubInputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamHubInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (mController != null) {
            if (mController.isFormed()) {
                return mController.receiveSteam(aFluid, doFill);
            }
            mController = null;
        }
        return super.fill(aFluid, doFill);
    }

    @Override
    public int getCapacity() {
        if (mController != null && mController.isFormed()) {
            long remaining = mController.getTotalCapacity() - mController.getSteamStored();
            return (int) Math.min(remaining, Integer.MAX_VALUE);
        }
        return 2_000_000;
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        if (aFluid == null) return false;
        return MTESteamHubOutputHatch.isSteamFluid(aFluid);
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.steam_hub_input_hatch.info"),
            StatCollector.translateToLocal("gtsr.tooltip.steam_hub_input_hatch.fluid_type"),
            StatCollector.translateToLocal("gtsr.tooltip.steam_hub_input_hatch.no_storage"),
            EnumChatFormatting.AQUA + "GT"
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
