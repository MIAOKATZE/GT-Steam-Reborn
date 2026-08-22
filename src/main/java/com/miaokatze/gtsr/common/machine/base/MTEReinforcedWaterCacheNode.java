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

/**
 * 耐压通用流体缓存节点：容量 8,000,000 L，基础交互速率 256,000 L/s（速率档位机制同基类）。
 * 机制/结构镜像 MTEReinforcedSteamCacheNode；接受任意流体（S5 通用流体口径）。
 * 绑定奇点消耗 = 0（对齐蓄水枢纽阵列通用流体节点家族现状）。
 */
public class MTEReinforcedWaterCacheNode extends MTEFilteredCacheNode {

    private static final int CAPACITY = 8_000_000;
    /** 自动排出速率（L/s，每 20t 一次）：SR-OPT-02 上提后同时作为枢纽基础传输速率单源。 */
    private static final int OUTPUT_RATE_PER_SEC = 256_000;

    public MTEReinforcedWaterCacheNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 3);
    }

    public MTEReinforcedWaterCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    protected int getBaseHubTransferRate() {
        return OUTPUT_RATE_PER_SEC;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEReinforcedWaterCacheNode(mName, mTier, mDescriptionArray, mTextures);
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
        // 通用流体口径：任意流体（罐内单一流体锁由父类罐机制保证）
        return fluid != null;
    }

    @Override
    protected Fluid getFamilyDefaultWindowFluid() {
        // 通用流体族绑定蓄水枢纽阵列：空罐默认窗=水（与蓄水枢纽阵列系奇点仓口径一致）
        return FluidRegistry.WATER;
    }

    @Override
    protected String getFluidTypeTooltipLangKey() {
        return "gtsr.tooltip.reinforced_water_cache_node.fluid_type";
    }

    @Override
    protected void addVariantTooltipLines(List<String> tooltip) {
        tooltip
            .add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.water_cache_node.bind_target"));
    }

}
