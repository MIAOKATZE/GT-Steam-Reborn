package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

public class MTEHatchPressureSteamInput extends MTEHatchCustomFluidBase {

    public MTEHatchPressureSteamInput(int aID, String aName, String aNameRegional, int aTier) {
        super(getSuperheatedSteamFluid(), 512000, aID, aName, aNameRegional, aTier);
    }

    public MTEHatchPressureSteamInput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(getSuperheatedSteamFluid(), 512000, aName, aTier, aDescription, aTextures);
    }

    private static Fluid getSuperheatedSteamFluid() {
        Fluid fluid = FluidRegistry.getFluid("ic2superheatedsteam");
        if (fluid != null) return fluid;
        return Materials.Steam.getGas(1)
            .getFluid();
    }

    private boolean isSteamType(String fluidName) {
        return "steam".equals(fluidName) || "ic2superheatedsteam".equals(fluidName);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchPressureSteamInput(mName, 0, mDescriptionArray, mTextures);
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        if (aFluid != null && aFluid.getFluid() != null) {
            return isSteamType(
                aFluid.getFluid()
                    .getName());
        }
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        if (side == aBaseMetaTileEntity.getFrontFacing() && aIndex == 0) {
            FluidStack fs = GTUtility.getFluidForFilledItem(aStack, true);
            if (fs != null && fs.getFluid() != null) {
                return isSteamType(
                    fs.getFluid()
                        .getName());
            }
        }
        return false;
    }

    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || aFluid.getFluid() == null || aFluid.amount <= 0) return 0;
        if (!isFluidInputAllowed(aFluid)) return 0;
        if (!canTankBeFilled()) return 0;

        FluidStack existing = getFluid();
        if (existing == null || existing.amount <= 0) {
            int toFill = Math.min(aFluid.amount, getCapacity());
            if (doFill) {
                setFluid(aFluid.copy());
                if (getFluid().amount > getCapacity()) {
                    getFluid().amount = getCapacity();
                }
                getBaseMetaTileEntity().markDirty();
            }
            return toFill;
        }

        if (!existing.isFluidEqual(aFluid)) return 0;

        int space = getCapacity() - existing.amount;
        if (space <= 0) return 0;

        int toFill = Math.min(aFluid.amount, space);
        if (doFill) {
            existing.amount += toFill;
            getBaseMetaTileEntity().markDirty();
        }
        return toFill;
    }

    private void setFluid(FluidStack aFluid) {
        mFluid = aFluid;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.pressure_steam_input_hatch.name"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + String.format("%,d", getCapacity())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.fluid_type")
                + EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_and_superheated"),
            EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_machine_only"),
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
