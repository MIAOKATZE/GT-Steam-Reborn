package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.GTUtility;

/**
 * 巨型空气输入仓
 * <p>
 * 仿照 GT5U 的液态空气仓（{@code MTECompressedFluidHatch}）实现，但仅允许输入「空气」与「下界空气」。
 * 容量为 100,000,000 L，用于为耗气量巨大的多方块机器（如空气离心机）提供大量空气供给。
 * <p>
 * 材质继承自 {@link MTEHatchInput} 的标准管道覆盖层（与液态空气仓一致），不重写 getTexturesActive/Inactive。
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTEMegaAirInputHatch extends MTEHatchInput {

    // 巨型仓室容量：1亿 L
    private static final int CAPACITY = 100_000_000;

    public MTEMegaAirInputHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 0);
    }

    public MTEMegaAirInputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    /**
     * 仅允许输入「空气」与「下界空气」。
     */
    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        if (aFluid == null || aFluid.getFluid() == null) return false;
        return GTUtility.areFluidsEqual(aFluid, Materials.Air.getFluid(1))
            || GTUtility.areFluidsEqual(aFluid, Materials.NetherAir.getFluid(1));
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEMegaAirInputHatch(this.mName, this.mTier, this.mDescriptionArray, this.mTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.mega_air_input_hatch.desc"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + String.format("%,d", getCapacity())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.fluid_type")
                + EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.tooltip.mega_air_input_hatch.fluid_type"),
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
