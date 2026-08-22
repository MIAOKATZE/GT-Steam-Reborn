package com.miaokatze.gtsr.common.machine.base;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_STEEL_BOTTOM;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_STEEL_SIDE;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_STEEL_TOP;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;

public class MTEReinforcedSteamCacheNode extends MTEFilteredCacheNode {

    private static final int CAPACITY = 64_000_000;
    /** 自动排出速率（L/s，每 20t 一次）：SR-OPT-02 上提后同时作为枢纽基础传输速率单源。 */
    private static final int OUTPUT_RATE_PER_SEC = 8_000_000;

    public MTEReinforcedSteamCacheNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 3);
    }

    public MTEReinforcedSteamCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    protected int getBaseHubTransferRate() {
        return OUTPUT_RATE_PER_SEC;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEReinforcedSteamCacheNode(mName, mTier, mDescriptionArray, mTextures);
    }

    /**
     * S4 容量基量：硬编码终值改覆写本方法，档位乘法统一在基类 getRealCapacity()
     * （getFluidCapacityLong 读同一算式，tooltip/getInfoData 容量读数自动跟随档位）。
     */
    @Override
    public int getBaseRealCapacity() {
        return CAPACITY;
    }

    @Override
    protected ITexture getTopTexture() {
        return TextureFactory.of(MACHINE_STEEL_TOP);
    }

    @Override
    protected ITexture getBottomTexture() {
        return TextureFactory.of(MACHINE_STEEL_BOTTOM);
    }

    @Override
    protected ITexture getSideTexture() {
        return TextureFactory.of(MACHINE_STEEL_SIDE);
    }

    @Override
    protected boolean isFluidAllowed(Fluid fluid) {
        if (fluid == null) return false;
        String name = fluid.getName();
        return "steam".equals(name) || "ic2superheatedsteam".equals(name);
    }

    @Override
    protected Fluid getFamilyDefaultWindowFluid() {
        return FluidRegistry.getFluid("steam");
    }

    @Override
    protected String getFluidTypeTooltipLangKey() {
        return "gtsr.tooltip.reinforced_steam_cache_node.fluid_type.superheated_steam";
    }

    @Override
    protected void addVariantTooltipLines(List<String> tooltip) {
        tooltip.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.singularity_cost"));
    }

}
