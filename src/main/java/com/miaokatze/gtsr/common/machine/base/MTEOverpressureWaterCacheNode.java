package com.miaokatze.gtsr.common.machine.base;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
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
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection sideDirection,
        ForgeDirection facingDirection, int colorIndex, boolean active, boolean redstoneLevel) {
        if (sideDirection == ForgeDirection.UP) {
            // 顶面三层：基材（Casings8:6，与超压蒸汽节点同款） + 流体窗 + 绑定状态框架层
            return getTopFaceTextures(Textures.BlockIcons.getCasingTextureForId(CASING_INDEX));
        } else if (sideDirection == ForgeDirection.DOWN) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
        } else if (sideDirection == facingDirection) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                TextureFactory.of(OVERLAY_PIPE) };
        } else {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
        }
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

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return isAnyFluid(aFluid);
    }

    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || !isAnyFluid(aFluid)) return 0;
        return super.fill(aFluid, doFill);
    }

    @Override
    public int fill(ForgeDirection side, FluidStack aFluid, boolean doFill) {
        if (aFluid == null || !isAnyFluid(aFluid)) return 0;
        return super.fill(side, aFluid, doFill);
    }

    /**
     * 拦截 GUI 输入槽中的空容器：任意流体容器都允许放入；空容器或非流体物品走父类逻辑。
     */
    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        if (aIndex == getInputSlot()) {
            FluidStack tFluid = GTUtility.getFluidForFilledItem(aStack, true);
            if (tFluid != null && tFluid.getFluid() != null) {
                return true;
            }
        }
        return super.allowPutStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    private static boolean isAnyFluid(FluidStack aFluid) {
        return aFluid != null && aFluid.getFluid() != null;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }
}
