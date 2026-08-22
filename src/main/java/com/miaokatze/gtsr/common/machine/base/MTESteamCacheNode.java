package com.miaokatze.gtsr.common.machine.base;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_BOTTOM;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_SIDE;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_TOP;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;

public class MTESteamCacheNode extends MTEFilteredCacheNode {

    private static final int CAPACITY = 16_000_000;
    /** 自动排出速率（L/s，每 20t 一次）：SR-OPT-02 上提后同时作为枢纽基础传输速率单源。 */
    private static final int OUTPUT_RATE_PER_SEC = 2_000_000;

    public MTESteamCacheNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 3);
    }

    public MTESteamCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    protected int getBaseHubTransferRate() {
        return OUTPUT_RATE_PER_SEC;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamCacheNode(mName, mTier, mDescriptionArray, mTextures);
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
        return TextureFactory.of(MACHINE_BRONZE_TOP);
    }

    @Override
    protected ITexture getBottomTexture() {
        return TextureFactory.of(MACHINE_BRONZE_BOTTOM);
    }

    @Override
    protected ITexture getSideTexture() {
        return TextureFactory.of(MACHINE_BRONZE_SIDE);
    }

    @Override
    protected boolean isFluidAllowed(Fluid fluid) {
        return fluid != null && "steam".equals(fluid.getName());
    }

    @Override
    protected Fluid getFamilyDefaultWindowFluid() {
        return FluidRegistry.getFluid("steam");
    }

    @Override
    protected String getFluidTypeTooltipLangKey() {
        return "gtsr.tooltip.steam_cache_node.fluid_type.steam";
    }

}
