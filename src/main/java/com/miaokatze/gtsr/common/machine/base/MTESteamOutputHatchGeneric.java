package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;

public class MTESteamOutputHatchGeneric extends MTEHatchOutput {

    private static final int CAPACITY = 8_000;
    private static final int OUTPUT_PER_TICK = 400;
    private static final int OUTPUT_PER_SECOND = 8_000;

    public MTESteamOutputHatchGeneric(int aID, String aName, String aNameRegional) {
        this(aID, aName, aNameRegional, 1);
    }

    public MTESteamOutputHatchGeneric(int aID, String aName, String aNameRegional, int aTier) {
        this(
            aID,
            aName,
            aNameRegional,
            aTier,
            new String[] { "Output Hatch for Multiblocks", "Capacity: " + CAPACITY + "L",
                "Output: " + OUTPUT_PER_SECOND + "L/s", "Fluid Type: Any Fluid", "For Steam Multiblock Machines" },
            4);
    }

    public MTESteamOutputHatchGeneric(int aID, String aName, String aNameRegional, int aTier, String[] aDescription,
        int inventorySize) {
        super(aID, aName, aNameRegional, aTier, aDescription, inventorySize);
    }

    public MTESteamOutputHatchGeneric(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamOutputHatchGeneric(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
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
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.steam_output_hatch_generic.name"),
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
                + StatCollector.translateToLocal("gtsr.tooltip.shared.any_fluid"),
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
