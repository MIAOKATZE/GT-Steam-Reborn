package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.common.util.UnitFormatUtil;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;

public class MTEWaterHubInputHatch extends MTEHatchInput {

    public MTEWaterHubArray mController;

    public MTEWaterHubInputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
    }

    public MTEWaterHubInputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWaterHubInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (mController != null) {
            if (mController.isFormed()) {
                return mController.receiveWater(aFluid, doFill);
            }
            mController = null;
        }
        return super.fill(aFluid, doFill);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        long stored = mController != null && mController.isFormed() ? mController.getWaterStored() : 0L;
        return new String[] { "gt.blockmachines." + mName + ".name",
            EnumChatFormatting.GREEN + UnitFormatUtil.format(stored)
                + " L"
                + EnumChatFormatting.RESET
                + " "
                + EnumChatFormatting.YELLOW
                + UnitFormatUtil.format(getCapacityLong())
                + " L"
                + EnumChatFormatting.RESET };
    }

    public long getCapacityLong() {
        if (mController != null && mController.isFormed()) {
            return Math.max(0L, mController.getTotalCapacity() - mController.getWaterStored());
        }
        return 2_000_000L;
    }

    @Override
    public int getCapacity() {
        return (int) Math.min(getCapacityLong(), Integer.MAX_VALUE);
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        // S5 放宽：接受任意流体（异种拒收由枢纽 mStoredFluidType 单一类型锁负责）
        return MTEWaterHubOutputHatch.isWaterFluid(aFluid);
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.water_hub_input_hatch.info"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.water_hub_input_hatch.fluid_type"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.water_hub_input_hatch.no_storage"),
            GTSRUtils.getAddedByLine() };
    }
}
