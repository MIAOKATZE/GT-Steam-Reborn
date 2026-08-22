package com.miaokatze.gtsr.common.machine.base;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 超压通用流体缓存节点：容量 32,000,000 L，基础交互速率 2,000,000 L/s（速率档位机制同基类）。
 * 机制/结构镜像 MTEOverpressureSteamCacheNode；接受任意流体（S5 通用流体口径）。
 * 绑定奇点消耗 = 0（对齐蓄水枢纽阵列通用流体节点家族现状）；绑定需等级3蓄水枢纽阵列 + 强化奇点芯片（WHA 侧门控）。
 */
public class MTEOverpressureWaterCacheNode extends MTEFilteredCacheNode {

    private static final int CAPACITY = 32_000_000;
    /**
     * 枢纽基础传输速率（L/s）：SR-OPT-02 drain 模板上提后同时是自动排出速率
     * （原 OUTPUT_RATE_PER_SEC 与本常量同值 2_000_000，已合并单源）。
     */
    private static final int HUB_TRANSFER_RATE = 2_000_000;
    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);

    public MTEOverpressureWaterCacheNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 4);
    }

    public MTEOverpressureWaterCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    protected int getBaseHubTransferRate() {
        return HUB_TRANSFER_RATE;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEOverpressureWaterCacheNode(mName, mTier, mDescriptionArray, mTextures);
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
        return "gtsr.tooltip.overpressure_water_cache_node.fluid_type";
    }

    @Override
    protected void addVariantTooltipLines(List<String> tooltip) {
        tooltip.add(
            EnumChatFormatting.RED
                + StatCollector.translateToLocal("gtsr.tooltip.overpressure_water_cache_node.bind_requirement"));
        tooltip
            .add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.water_cache_node.bind_target"));
    }

}
