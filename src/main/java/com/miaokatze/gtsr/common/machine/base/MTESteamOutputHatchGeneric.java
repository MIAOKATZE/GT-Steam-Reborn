package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;

@IMetaTileEntity.SkipGenerateDescription
public class MTESteamOutputHatchGeneric extends MTEHatchOutput {

    private static final int CAPACITY = 16_000;
    private static final int OUTPUT_PER_TICK = 800;
    private static final int OUTPUT_PER_SECOND = 16_000;

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

    protected int getOutputPerTick() {
        return OUTPUT_PER_TICK;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        // GT205's output-hatch tick owns an unbounded push and an inventory notification;
        // its ancestors have no tick work. Replace only the push, notifying with the real tank
        // visible (temporarily hiding mFluid around super would mislead native watchers).
        if (!aBaseMetaTileEntity.isServerSide()) return;
        try {
            if (!aBaseMetaTileEntity.isAllowedToWork()) return;
            ForgeDirection outputDirection = aBaseMetaTileEntity.getFrontFacing();
            IFluidHandler tank = aBaseMetaTileEntity.getITankContainerAtSide(outputDirection);
            if (tank == null) return;
            FluidStack outgoing = drain(getOutputPerTick(), false);
            if (outgoing == null || outgoing.amount <= 0) return;
            int offered = outgoing.amount;
            int accepted = tank.fill(outputDirection.getOpposite(), outgoing.copy(), true);
            if (accepted > 0) drain(Math.min(offered, accepted), true);
        } finally {
            detectInventoryChange();
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
            GTSRUtils.getAddedByLine() };
    }
}
