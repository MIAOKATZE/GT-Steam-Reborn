package com.miaokatze.gtsr.common.machine.base;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.GTUtility;

public class MTEOverpressureTurbineInputHatch extends MTEHatchInput {

    private static final long CAPACITY = 2_000_000_000L;
    private static final int DEFAULT_TEXTURE_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);

    private long mSteamStored = 0;
    private FluidStack mStoredFluidType = null;

    public MTEOverpressureTurbineInputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
        setDefaultTextureIndex();
    }

    public MTEOverpressureTurbineInputHatch(String aName, int aTier, String[] aDescription,
        gregtech.api.interfaces.ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
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
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }

    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || aFluid.getFluid() == null) return 0;
        if (!isFluidInputAllowed(aFluid)) return 0;
        if (mStoredFluidType != null && !mStoredFluidType.isFluidEqual(aFluid)) return 0;
        long canAccept = CAPACITY - mSteamStored;
        int toAccept = (int) Math.min(aFluid.amount, canAccept);
        if (doFill && toAccept > 0) {
            if (mStoredFluidType == null) {
                mStoredFluidType = new FluidStack(aFluid.getFluid(), 0);
            }
            mSteamStored += toAccept;
        }
        return toAccept;
    }

    @Override
    public int fill(net.minecraftforge.common.util.ForgeDirection side, FluidStack aFluid, boolean doFill) {
        return fill(aFluid, doFill);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (mSteamStored <= 0 || mStoredFluidType == null) return null;
        int toDrain = (int) Math.min(maxDrain, mSteamStored);
        FluidStack result = new FluidStack(mStoredFluidType.getFluid(), toDrain);
        if (doDrain) {
            mSteamStored -= toDrain;
            if (mSteamStored <= 0) {
                mSteamStored = 0;
                mStoredFluidType = null;
            }
        }
        return result;
    }

    @Override
    public FluidStack drain(net.minecraftforge.common.util.ForgeDirection side, FluidStack aFluid, boolean doDrain) {
        if (aFluid == null || !aFluid.isFluidEqual(mStoredFluidType)) return null;
        return drain(aFluid.amount, doDrain);
    }

    @Override
    public FluidStack drain(net.minecraftforge.common.util.ForgeDirection side, int maxDrain, boolean doDrain) {
        return drain(maxDrain, doDrain);
    }

    @Override
    public int getCapacity() {
        return (int) Math.min(CAPACITY, Integer.MAX_VALUE);
    }

    @Override
    public FluidStack getFluid() {
        if (mStoredFluidType == null || mSteamStored <= 0) return null;
        int amount = (int) Math.min(mSteamStored, Integer.MAX_VALUE);
        return new FluidStack(mStoredFluidType.getFluid(), amount);
    }

    @Override
    public FluidStack getFillableStack() {
        return getFluid();
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
        return false;
    }

    public long getSteamStored() {
        return mSteamStored;
    }

    public FluidStack getStoredFluidType() {
        return mStoredFluidType;
    }

    public void consumeSteam(long amount) {
        mSteamStored -= amount;
        if (mSteamStored <= 0) {
            mSteamStored = 0;
            mStoredFluidType = null;
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mSteamStored", mSteamStored);
        if (mStoredFluidType != null) {
            NBTTagCompound fluidTag = new NBTTagCompound();
            mStoredFluidType.writeToNBT(fluidTag);
            aNBT.setTag("mStoredFluidType", fluidTag);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSteamStored = aNBT.getLong("mSteamStored");
        if (aNBT.hasKey("mStoredFluidType")) {
            mStoredFluidType = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mStoredFluidType"));
        }
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
