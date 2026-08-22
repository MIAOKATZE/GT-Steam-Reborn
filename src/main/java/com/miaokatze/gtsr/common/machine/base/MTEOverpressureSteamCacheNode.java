package com.miaokatze.gtsr.common.machine.base;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

public class MTEOverpressureSteamCacheNode extends MTEFilteredCacheNode {

    private static final int CAPACITY = 256_000_000;
    /**
     * 枢纽基础传输速率（L/s）：SR-OPT-02 drain 模板上提后同时是自动排出速率
     * （原 OUTPUT_RATE_PER_SEC 与本常量同值 64_000_000，已合并单源）。
     */
    private static final int HUB_TRANSFER_RATE = 64_000_000;
    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);

    public MTEOverpressureSteamCacheNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 4);
    }

    public MTEOverpressureSteamCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    protected int getBaseHubTransferRate() {
        return HUB_TRANSFER_RATE;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEOverpressureSteamCacheNode(mName, mTier, mDescriptionArray, mTextures);
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
        return Textures.BlockIcons.getCasingTextureForId(CASING_INDEX);
    }

    @Override
    protected ITexture getBottomTexture() {
        return Textures.BlockIcons.getCasingTextureForId(CASING_INDEX);
    }

    @Override
    protected ITexture getSideTexture() {
        return Textures.BlockIcons.getCasingTextureForId(CASING_INDEX);
    }

    @Override
    protected boolean isFluidAllowed(Fluid fluid) {
        return MTESteamHubOutputHatch.isAnySteamFluidType(fluid) || GTModHandler.isAnySteam(new FluidStack(fluid, 1))
            || GTModHandler.isSuperHeatedSteam(new FluidStack(fluid, 1));
    }

    @Override
    protected Fluid getFamilyDefaultWindowFluid() {
        return FluidRegistry.getFluid("steam");
    }

    @Override
    protected String getFluidTypeTooltipLangKey() {
        return "gtsr.tooltip.overpressure_steam_cache_node.fluid_type";
    }

    @Override
    protected void addVariantTooltipLines(List<String> tooltip) {
        tooltip.add(
            EnumChatFormatting.RED
                + StatCollector.translateToLocal("gtsr.tooltip.overpressure_steam_cache_node.singularity_cost"));
        tooltip.add(
            EnumChatFormatting.GRAY
                + StatCollector.translateToLocal("gtsr.tooltip.overpressure_steam_cache_node.bind_requirement"));
    }

}
