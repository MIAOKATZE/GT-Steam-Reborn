package com.miaokatze.gtsr.common.machine.base;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_BOTTOM;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_SIDE;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_TOP;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;

public class MTEWaterCacheNode extends MTEFilteredCacheNode {

    private static final int CAPACITY = 2_000_000;
    /** 每 tick 排出量：自动排出与枢纽基础传输速率均按 OUTPUT_PER_TICK*20（L/s）单源计算。 */
    private static final int OUTPUT_PER_TICK = 3_200;

    public MTEWaterCacheNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 3);
    }

    public MTEWaterCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    protected int getBaseHubTransferRate() {
        return OUTPUT_PER_TICK * 20;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWaterCacheNode(mName, mTier, mDescriptionArray, mTextures);
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
        // S5 放宽：通用流体缓存节点接受任意流体（罐内单一流体锁由父类罐机制保证）
        return fluid != null;
    }

    @Override
    protected Fluid getFamilyDefaultWindowFluid() {
        // 通用流体族绑定蓄水枢纽阵列：空罐默认窗=水（与蓄水枢纽阵列系奇点仓口径一致）
        return FluidRegistry.WATER;
    }

    @Override
    protected String getFluidTypeTooltipLangKey() {
        return "gtsr.tooltip.water_cache_node.fluid_type.water";
    }

    @Override
    protected void addVariantTooltipLines(List<String> tooltip) {
        tooltip
            .add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.water_cache_node.bind_target"));
    }

}
