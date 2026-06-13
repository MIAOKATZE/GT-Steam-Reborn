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
    public int getCapacity() {
        if (mController != null && mController.isFormed()) {
            return (int) Math.min(mController.getTotalCapacity(), Integer.MAX_VALUE);
        }
        return 2_000_000;
    }

    @Override
    public boolean canStoreFluid(FluidStack fluidStack) {
        if (fluidStack == null) return false;
        if (!isWaterFluid(fluidStack)) return false;
        return super.canStoreFluid(fluidStack);
    }

    @Override
    public boolean acceptsFluidLock(Fluid fluid) {
        return false;
    }

    public static boolean isWaterFluid(FluidStack aFluid) {
        if (aFluid == null) return false;
        if (aFluid.getFluid() == null) return false;
        String fluidName = aFluid.getFluid()
            .getName();
        return "water".equals(fluidName) || "ic2distilledwater".equals(fluidName);
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
