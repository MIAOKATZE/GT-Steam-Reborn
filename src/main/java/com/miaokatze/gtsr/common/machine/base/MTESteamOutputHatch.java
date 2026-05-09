package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

public class MTESteamOutputHatch extends MTEHatchOutput {

    private static final int CAPACITY = 128_000;
    private static final int OUTPUT_PER_TICK = 6_400;
    private static final int OUTPUT_PER_SECOND = 128_000;
    private static final int BRONZE_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    public MTESteamOutputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 6);
        this.mMode = 0;
    }

    public MTESteamOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        this.mMode = 0;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (!isConnectedToParentMachine()) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(BRONZE_CASING_INDEX) };
        }
        return super.getTexture(aBaseMetaTileEntity, side, facing, colorIndex, aActive, redstoneLevel);
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    public boolean acceptsFluid(FluidStack aFluid) {
        return false;
    }

    @Override
    public int fill(ForgeDirection side, FluidStack aFluid, boolean doFill) {
        if (aFluid == null) return 0;
        if (!GTModHandler.isSteam(aFluid)) return 0;
        if (side != ForgeDirection.UNKNOWN && !isConnectedToParentMachine()) return 0;
        return super.fill(side, aFluid, doFill);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (getDrainableStack() == null || getDrainableStack().amount <= 0) return;

        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            IFluidHandler tTank = aBaseMetaTileEntity.getITankContainerAtSide(direction);
            if (tTank != null) {
                FluidStack tDrained = drain(OUTPUT_PER_TICK, false);
                if (tDrained != null && tDrained.amount > 0) {
                    int tFilledAmount = tTank.fill(direction.getOpposite(), tDrained, false);
                    if (tFilledAmount > 0) {
                        tTank.fill(direction.getOpposite(), drain(tFilledAmount, true), true);
                    }
                }
            }
        }
    }

    @Override
    public boolean canStoreFluid(FluidStack aFluidToStore) {
        if (aFluidToStore == null) return false;
        return GTModHandler.isSteam(aFluidToStore);
    }

    private boolean isConnectedToParentMachine() {
        return getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getMetaTileEntity() != null;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.steam_output_hatch.name"),
            EnumChatFormatting.AQUA + "Capacity: " + CAPACITY + " L",
            EnumChatFormatting.GREEN + "Output Rate: " + OUTPUT_PER_SECOND + " L/s",
            EnumChatFormatting.YELLOW + "Fluid Type: Steam Only",
            EnumChatFormatting.RED + "No External Input Allowed" };
    }
}
