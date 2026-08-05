package com.miaokatze.gtsr.common.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.gui.MTEDenseStateManipulatorGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;

/** Tier 2 dense steam compressor/decompressor. */
public class MTEDenseStateManipulator extends MTESingularityMachineBase {

    private static final int SINGULARITY_DURATION_TICKS = 12000;
    private static final long DENSE_COMPRESSION_RATIO = 1000L;

    public static final int MODE_COMPRESS = 0;
    public static final int MODE_DECOMPRESS = 1;

    public int mMode = MODE_COMPRESS;
    public int mFuelTicks = 0;
    private double mAccum = 0.0d;
    private int mAccumGrade = -1;

    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;

    public MTEDenseStateManipulator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEDenseStateManipulator(String aName) {
        super(aName);
    }

    @Override
    protected String getTooltipKeyPrefix() {
        return "gtsr.tooltip.dense_state_manipulator.";
    }

    @Override
    public String getGuiKeyPrefix() {
        return "gtsr.gui.dense_state_manipulator.";
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEDenseStateManipulator(mName);
    }

    @Override
    protected int getRequiredTier() {
        return 2;
    }

    @Override
    protected double getHeatMax() {
        return 0.0d;
    }

    @Override
    protected long getHeatHalfPoint() {
        return 1L;
    }

    @Override
    protected boolean includeDenseSteam() {
        return false;
    }

    @Override
    protected ItemStack getAggregationOutput() {
        return null;
    }

    @Override
    protected boolean requiresOutputHatch() {
        return true;
    }

    @Override
    protected boolean requiresInputBus() {
        return true;
    }

    @Override
    public boolean isDenseStateManipulator() {
        return true;
    }

    @Override
    public int getModeForGui() {
        return mMode;
    }

    @Override
    public int getFuelTicksForGui() {
        return mFuelTicks;
    }

    @Override
    protected int getCasingTextureIndex() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
    }

    @Override
    protected int getHatchCasingTextureIndex() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        updateHatchTextures();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTESingularityDrillingHub_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTESingularityDrillingHub_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        int casingIndex = getCasingTextureIndex();
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                TextureFactory.of(aActive ? OVERLAY_ON : OVERLAY_OFF) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (mMode == MODE_DECOMPRESS) return processDecompressionCycle();
        mMode = MODE_COMPRESS;
        return processCompressionCycle();
    }

    private CheckRecipeResult processCompressionCycle() {
        if (mFuelTicks <= 0) {
            if (!consumeSingularityFromInputBuses(1)) return CheckRecipeResultRegistry.NO_RECIPE;
            mFuelTicks = SINGULARITY_DURATION_TICKS;
        }

        if (mAccum < 1.0d) {
            int grade = findHighestGrade(false);
            if (grade < 0) return CheckRecipeResultRegistry.NO_RECIPE;
            long amount = sumGrade(grade, false);
            if (amount <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
            drainGrade(grade, false);
            if (grade != mAccumGrade) {
                mAccum = 0.0d;
                mAccumGrade = grade;
            }
            mAccum += (double) amount / DENSE_COMPRESSION_RATIO;
        }

        long output = (long) Math.floor(mAccum);
        if (output <= 0) {
            startCycle();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        if (mAccumGrade < 0) {
            mAccum = 0.0d;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        FluidStack dense = FluidRegistry
            .getFluidStack(DENSE_FLUID_NAMES[mAccumGrade], (int) Math.min(output, Integer.MAX_VALUE));
        if (dense == null || dense.amount <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
        mAccum -= fillOutput(dense);
        startCycle();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private CheckRecipeResult processDecompressionCycle() {
        if (mFuelTicks <= 0) {
            if (!consumeSingularityFromInputBuses(1)) return CheckRecipeResultRegistry.NO_RECIPE;
            mFuelTicks = SINGULARITY_DURATION_TICKS;
        }

        if (mAccum < 1.0d) {
            int grade = findHighestDenseGrade();
            if (grade < 0) return CheckRecipeResultRegistry.NO_RECIPE;
            long amount = sumDenseGrade(grade);
            if (amount <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
            drainDenseGrade(grade);
            if (grade != mAccumGrade) {
                mAccum = 0.0d;
                mAccumGrade = grade;
            }
            mAccum += (double) amount * DENSE_COMPRESSION_RATIO;
        }

        long output = (long) Math.floor(mAccum);
        if (output <= 0) {
            startCycle();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        if (mAccumGrade < 0) {
            mAccum = 0.0d;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        FluidStack steam = FluidRegistry
            .getFluidStack(NORMAL_FLUID_NAMES[mAccumGrade], (int) Math.min(output, Integer.MAX_VALUE));
        if (steam == null || steam.amount <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
        mAccum -= fillOutput(steam);
        startCycle();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private int findHighestDenseGrade() {
        for (int grade = 2; grade >= 0; grade--) {
            if (probeGrade(grade, false, true)) return grade;
        }
        return -1;
    }

    private long sumDenseGrade(int grade) {
        long amount = 0;
        FluidStack request = FluidRegistry.getFluidStack(DENSE_FLUID_NAMES[grade], 1);
        if (request == null) return 0;
        for (gregtech.api.metatileentity.implementations.MTEHatch hatch : getSteamInputHatches()) {
            FluidStack full = request.copy();
            full.amount = MAX_DRAIN_PER_CYCLE;
            FluidStack result = hatch.drain(ForgeDirection.UNKNOWN, full, false);
            if (result != null && result.amount > 0) amount += result.amount;
        }
        return amount;
    }

    private void drainDenseGrade(int grade) {
        FluidStack request = FluidRegistry.getFluidStack(DENSE_FLUID_NAMES[grade], 1);
        if (request == null) return;
        for (gregtech.api.metatileentity.implementations.MTEHatch hatch : getSteamInputHatches()) {
            // 按需量实扣：先探测本仓实际可得量（cap 到 MAX_DRAIN_PER_CYCLE），再按探测结果实扣。
            // 修复：原实现以 MAX_VALUE 实扣，对 ME 输入仓会一次拉取整个网络该流体库存。
            FluidStack full = request.copy();
            full.amount = MAX_DRAIN_PER_CYCLE;
            FluidStack available = hatch.drain(ForgeDirection.UNKNOWN, full, false);
            if (available != null && available.amount > 0) {
                FluidStack toDrain = available.copy();
                toDrain.amount = Math.min(available.amount, MAX_DRAIN_PER_CYCLE);
                hatch.drain(ForgeDirection.UNKNOWN, toDrain, true);
            }
        }
    }

    @Override
    protected boolean shouldDecayHeat() {
        return false;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer.worldObj.isRemote) return;
        if (mTier < 2) {
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.compressor_mode.require_tier2");
            return;
        }
        mMode = mMode == MODE_COMPRESS ? MODE_DECOMPRESS : MODE_COMPRESS;
        mFuelTicks = 0;
        mAccum = 0.0d;
        mAccumGrade = -1;
        String key = mMode == MODE_COMPRESS ? "gtsr.chat.compressor_mode.on.compress"
            : "gtsr.chat.compressor_mode.on.decompress";
        GTUtility.sendChatTrans(aPlayer, key);
        getBaseMetaTileEntity().markDirty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || aTick % CYCLE_LENGTH != 0L) return;
        mFuelTicks -= CYCLE_LENGTH;
        if (mFuelTicks <= 0) {
            mFuelTicks = consumeSingularityFromInputBuses(1) ? SINGULARITY_DURATION_TICKS : 0;
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mMode", mMode);
        aNBT.setInteger("mFuelTicks", mFuelTicks);
        aNBT.setDouble("mAccum", mAccum);
        aNBT.setInteger("mAccumGrade", mAccumGrade);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mMode = aNBT.getInteger("mMode") == MODE_DECOMPRESS ? MODE_DECOMPRESS : MODE_COMPRESS;
        mFuelTicks = aNBT.getInteger("mFuelTicks");
        mAccum = aNBT.getDouble("mAccum");
        mAccumGrade = aNBT.getInteger("mAccumGrade");
    }

    @Override
    public String[] getInfoData() {
        String tooltipKeyPrefix = getTooltipKeyPrefix();
        String guiKeyPrefix = getGuiKeyPrefix();
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal(tooltipKeyPrefix + "type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        String fuelValue = mFuelTicks > 0 ? String.format("%ds", mFuelTicks / CYCLE_LENGTH)
            : StatCollector.translateToLocal(guiKeyPrefix + "fuel_no_fuel");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "fuel_time")
                + EnumChatFormatting.RED
                + fuelValue
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "tier")
                + EnumChatFormatting.GOLD
                + mTier
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTEDenseStateManipulatorGui(this);
    }
}
