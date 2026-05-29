package com.miaokatze.gtsr.common.machine.base;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;
import gregtech.common.blocks.BlockCasings1;

public class MTESteamOutputHatch extends MTEHatchOutput {

    private static final int CAPACITY = 128_000;
    private static final int OUTPUT_PER_TICK = 6_400;
    private static final int OUTPUT_PER_SECOND = 128_000;
    private static final int DEFAULT_TEXTURE_INDEX = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

    public MTESteamOutputHatch(int aID, String aName, String aNameRegional) {
        this(aID, aName, aNameRegional, 1);
    }

    public MTESteamOutputHatch(int aID, String aName, String aNameRegional, int aTier) {
        this(
            aID,
            aName,
            aNameRegional,
            aTier,
            new String[] { "Steam Output for Solar Array", "Capacity: " + CAPACITY + "L",
                "Output: " + OUTPUT_PER_SECOND + "L/s", "Fluid Type: Steam Only", "No External Input Allowed" },
            4);
    }

    public MTESteamOutputHatch(int aID, String aName, String aNameRegional, int aTier, String[] aDescription,
        int inventorySize) {
        super(aID, aName, aNameRegional, aTier, aDescription, inventorySize);
        setDefaultTextureIndex();
    }

    public MTESteamOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        setDefaultTextureIndex();
    }

    private void setDefaultTextureIndex() {
        try {
            Field texturePageField = MTEHatch.class.getDeclaredField("texturePage");
            texturePageField.setAccessible(true);
            texturePageField.setInt(this, 0);

            Field textureIndexField = MTEHatch.class.getDeclaredField("textureIndex");
            textureIndexField.setAccessible(true);
            textureIndexField.setInt(this, DEFAULT_TEXTURE_INDEX & 127);
        } catch (Exception ignored) {}
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamOutputHatch(mName, mTier, mDescriptionArray, mTextures);
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
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + String.format("%,d", CAPACITY)
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"),
            EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.output_rate")
                + EnumChatFormatting.GOLD
                + String.format("%,d", OUTPUT_PER_SECOND)
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.fluid_type")
                + EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_only"),
            EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.no_external_input"),
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
