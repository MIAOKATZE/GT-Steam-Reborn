package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

public class MTEPressureSteamOutputHatch extends MTESteamOutputHatch {

    private static final int PRESSURE_CAPACITY = 1_024_000;
    private static final int PRESSURE_OUTPUT_PER_TICK = 51_200;
    private static final int PRESSURE_OUTPUT_PER_SECOND = 1_024_000;
    private static final ITexture STEEL_TEXTURE = Textures.BlockIcons
        .getCasingTextureForId(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0));

    public MTEPressureSteamOutputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEPressureSteamOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPressureSteamOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        if (GTMod.proxy.mRenderIndicatorsOnHatch) {
            return new ITexture[] { STEEL_TEXTURE, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(Textures.BlockIcons.FLUID_OUT_SIGN) };
        }
        return new ITexture[] { STEEL_TEXTURE, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        if (GTMod.proxy.mRenderIndicatorsOnHatch) {
            return new ITexture[] { STEEL_TEXTURE, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
                TextureFactory.of(Textures.BlockIcons.FLUID_OUT_SIGN) };
        }
        return new ITexture[] { STEEL_TEXTURE, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT) };
    }

    @Override
    public int getCapacity() {
        return PRESSURE_CAPACITY;
    }

    @Override
    public int fill(ForgeDirection side, FluidStack aFluid, boolean doFill) {
        if (aFluid == null) return 0;
        if (!GTModHandler.isAnySteam(aFluid)) return 0;
        return super.fill(side, aFluid, doFill);
    }

    @Override
    public boolean canStoreFluid(FluidStack aFluidToStore) {
        if (aFluidToStore == null) return false;
        return GTModHandler.isAnySteam(aFluidToStore);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (getDrainableStack() == null || getDrainableStack().amount <= 0) return;

        int remainingOutput = PRESSURE_OUTPUT_PER_TICK;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (remainingOutput <= 0) break;
            IFluidHandler tTank = aBaseMetaTileEntity.getITankContainerAtSide(direction);
            if (tTank != null) {
                FluidStack tDrained = drain(remainingOutput, false);
                if (tDrained != null && tDrained.amount > 0) {
                    int tFilledAmount = tTank.fill(direction.getOpposite(), tDrained, false);
                    if (tFilledAmount > 0) {
                        tTank.fill(direction.getOpposite(), drain(tFilledAmount, true), true);
                        remainingOutput -= tFilledAmount;
                    }
                }
            }
        }
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.pressure_steam_output_hatch.name"),
            EnumChatFormatting.AQUA + "Capacity: " + PRESSURE_CAPACITY + " L",
            EnumChatFormatting.GREEN + "Output Rate: " + PRESSURE_OUTPUT_PER_SECOND + " L/s",
            EnumChatFormatting.YELLOW + "Fluid Type: Steam & Superheated Steam",
            EnumChatFormatting.RED + "No External Input Allowed" };
    }
}
