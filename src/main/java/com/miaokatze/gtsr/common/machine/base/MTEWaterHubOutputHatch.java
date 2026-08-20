package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.common.util.UnitFormatUtil;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTUtility;

public class MTEWaterHubOutputHatch extends MTEHatchOutput {

    public MTEWaterHubArray mController;
    public boolean mOverflowOutput = false;

    public MTEWaterHubOutputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
        this.mMode = 3;
    }

    public MTEWaterHubOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        this.mMode = 3;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWaterHubOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        mOverflowOutput = !mOverflowOutput;
        if (aPlayer.worldObj.isRemote) return;
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_output") + ": "
                + (mOverflowOutput ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")
                    : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")));
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (mOverflowOutput && mController != null && mController.isFormed()) {
            long capacity = mController.getTotalCapacity();
            if (capacity > 0 && mController.getWaterStored() < (long) (capacity * 0.9)) return null;
        }
        if (mController != null) {
            if (mController.isFormed()) {
                return mController.extractWater(maxDrain, doDrain);
            }
            mController = null;
        }
        return super.drain(maxDrain, doDrain);
    }

    @Override
    public FluidStack getFluid() {
        if (mController != null && mController.isFormed()) {
            return mController.getStoredFluidStack();
        }
        return super.getFluid();
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
            return mController.getTotalCapacity();
        }
        return 2_000_000L;
    }

    @Override
    public int getCapacity() {
        return (int) Math.min(getCapacityLong(), Integer.MAX_VALUE);
    }

    @Override
    public boolean canStoreFluid(FluidStack fluidStack) {
        if (fluidStack == null) return false;
        // S5 放宽：任意流体（异种拒收由枢纽 mStoredFluidType 单一类型锁负责）
        return super.canStoreFluid(fluidStack);
    }

    @Override
    public boolean acceptsFluidLock(Fluid fluid) {
        return false;
    }

    /**
     * 通用流体判定（S5 放宽）：任意非空 FluidStack 均可；方法名保留避免破坏既有调用点。
     */
    public static boolean isWaterFluid(FluidStack aFluid) {
        return aFluid != null && aFluid.getFluid() != null;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("mOverflowOutput", mOverflowOutput);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mOverflowOutput = aNBT.getBoolean("mOverflowOutput");
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.water_hub_output_hatch.info"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.water_hub_output_hatch.fluid_type"),
            EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.water_hub_output_hatch.output_rate"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.water_hub_output_hatch.no_storage"),
            EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.tooltip.shared.screwdriver_rightclick_overflow"),
            EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_output_desc"),
            GTSRUtils.getAddedByLine() };
    }
}
