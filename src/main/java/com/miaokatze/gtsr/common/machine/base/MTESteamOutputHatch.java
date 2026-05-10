package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;
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
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

public class MTESteamOutputHatch extends MTEHatchOutput {

    private static final int CAPACITY = 128_000;
    private static final int OUTPUT_PER_TICK = 6_400;
    private static final int OUTPUT_PER_SECOND = 128_000;
    protected static final ITexture BRONZE_TEXTURE = Textures.BlockIcons
        .getCasingTextureForId(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10));
    protected static final ITexture STEEL_TEXTURE = Textures.BlockIcons
        .getCasingTextureForId(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0));

    public boolean isSteelTier = false;

    public MTESteamOutputHatch(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            6,
            new String[] { "Steam Output for Solar Array", "Capacity: " + CAPACITY + "L",
                "Output: " + OUTPUT_PER_SECOND + "L/s", "Fluid Type: Steam Only", "No External Input Allowed" },
            4);
    }

    public MTESteamOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture baseTexture = isSteelTier ? STEEL_TEXTURE : BRONZE_TEXTURE;
        if (side == facing) {
            ITexture[] overlays;
            if (GTMod.proxy.mRenderIndicatorsOnHatch) {
                overlays = new ITexture[] { TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
                    TextureFactory.of(Textures.BlockIcons.FLUID_OUT_SIGN) };
            } else {
                overlays = new ITexture[] { TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT) };
            }
            return mergeTextures(baseTexture, overlays);
        }
        return new ITexture[] { baseTexture };
    }

    private ITexture[] mergeTextures(ITexture background, ITexture[] overlays) {
        ITexture[] result = new ITexture[overlays.length + 1];
        result[0] = background;
        System.arraycopy(overlays, 0, result, 1, overlays.length);
        return result;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        return false;
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
        return super.fill(side, aFluid, doFill);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (getDrainableStack() == null || getDrainableStack().amount <= 0) return;

        ForgeDirection outputDirection = aBaseMetaTileEntity.getFrontFacing();
        IFluidHandler tTank = aBaseMetaTileEntity.getITankContainerAtSide(outputDirection);
        if (tTank != null) {
            FluidStack tDrained = drain(OUTPUT_PER_TICK, false);
            if (tDrained != null && tDrained.amount > 0) {
                int tFilledAmount = tTank.fill(outputDirection.getOpposite(), tDrained, false);
                if (tFilledAmount > 0) {
                    tTank.fill(outputDirection.getOpposite(), drain(tFilledAmount, true), true);
                }
            }
        }
    }

    @Override
    public boolean canStoreFluid(FluidStack aFluidToStore) {
        if (aFluidToStore == null) return false;
        return GTModHandler.isSteam(aFluidToStore);
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
