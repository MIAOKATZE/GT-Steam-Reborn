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
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection sideDirection,
        ForgeDirection facingDirection, int colorIndex, boolean active, boolean redstoneLevel) {
        if (sideDirection == ForgeDirection.UP) {
            // 顶面三层：基材 + 流体窗（罐内蒸汽/枢纽默认）+ 绑定状态框架层（见 MTEFilteredCacheNode）
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

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return isSteamFluid(aFluid);
    }

    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || !isSteamFluid(aFluid)) return 0;
        return super.fill(aFluid, doFill);
    }

    @Override
    public int fill(ForgeDirection side, FluidStack aFluid, boolean doFill) {
        if (aFluid == null || !isSteamFluid(aFluid)) return 0;
        return super.fill(side, aFluid, doFill);
    }

    /**
     * 拦截 GUI 输入槽中的非目标流体单元。
     * 只有装满任意蒸汽类型（普通/过热/致密/超临界等）的流体容器才允许放入输入槽；空容器或非流体物品走父类逻辑。
     */
    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        if (aIndex == getInputSlot()) {
            FluidStack tFluid = GTUtility.getFluidForFilledItem(aStack, true);
            if (tFluid != null && tFluid.getFluid() != null) {
                return isSteamFluid(tFluid);
            }
        }
        return super.allowPutStack(aBaseMetaTileEntity, aIndex, side, aStack);
    }

    private static boolean isSteamFluid(FluidStack aFluid) {
        if (aFluid == null) return false;
        return MTESteamHubOutputHatch.isAnySteamFluid(aFluid);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }
}
