package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.miaokatze.gtsr.common.machine.MTESteamHubArray;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

public class MTESteamHubOutputHatch extends MTEHatchOutput {

    private static final int OUTPUT_PER_TICK = 100_000;

    public MTESteamHubArray mController;
    public boolean mOverflowOutput = false;

    public MTESteamHubOutputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1);
        this.mMode = 3;
    }

    public MTESteamHubOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        this.mMode = 3;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamHubOutputHatch(mName, mTier, mDescriptionArray, mTextures);
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

    private boolean isOverflowBlocked() {
        if (!mOverflowOutput) return false;
        if (mController == null || !mController.isFormed()) return false;
        long capacity = mController.getTotalCapacity();
        if (capacity <= 0) return false;
        return mController.getSteamStored() < (long) (capacity * 0.9);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (isOverflowBlocked()) return null;
        if (mController != null) {
            if (mController.isFormed()) {
                return mController.extractSteam(maxDrain, doDrain);
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
        if (!isSteamFluid(fluidStack)) return false;
        return super.canStoreFluid(fluidStack);
    }

    @Override
    public boolean acceptsFluidLock(String name) {
        return false;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (mController == null || !mController.isFormed()) return;
        if (isOverflowBlocked()) return;

        FluidStack stored = mController.getStoredFluidStack();
        if (stored == null) return;

        ForgeDirection hatchFront = aBaseMetaTileEntity.getFrontFacing();
        IFluidHandler adjacent = aBaseMetaTileEntity.getITankContainerAtSide(hatchFront);
        if (adjacent == null) return;

        int toPush = Math.min(OUTPUT_PER_TICK, stored.amount);
        FluidStack toExport = new FluidStack(stored.getFluid(), toPush);
        int pushed = adjacent.fill(hatchFront.getOpposite(), toExport, true);
        if (pushed > 0) {
            mController.extractSteam(pushed, true);
        }
    }

    public static boolean isSteamFluid(FluidStack aFluid) {
        return GTModHandler.isAnySteam(aFluid) || GTModHandler.isSuperHeatedSteam(aFluid);
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
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.steam_hub_output_hatch.info"),
            StatCollector.translateToLocal("gtsr.tooltip.steam_hub_output_hatch.fluid_type"),
            StatCollector.translateToLocal("gtsr.tooltip.steam_hub_output_hatch.output_rate"),
            StatCollector.translateToLocal("gtsr.tooltip.steam_hub_output_hatch.no_storage"),
            EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_output_screwdriver"),
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
