package com.miaokatze.gtsr.common.machine.base;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_BOTTOM;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_SIDE;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_TOP;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

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
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection sideDirection,
        ForgeDirection facingDirection, int colorIndex, boolean active, boolean redstoneLevel) {
        if (sideDirection == ForgeDirection.UP) {
            // 顶面三层：基材 + 流体窗（罐内蒸汽/枢纽默认）+ 绑定状态框架层（见 MTEFilteredCacheNode）
            return getTopFaceTextures(TextureFactory.of(MACHINE_BRONZE_TOP));
        } else if (sideDirection == ForgeDirection.DOWN) {
            return new ITexture[] { TextureFactory.of(MACHINE_BRONZE_BOTTOM) };
        } else if (sideDirection == facingDirection) {
            return new ITexture[] { TextureFactory.of(MACHINE_BRONZE_SIDE), TextureFactory.of(OVERLAY_PIPE) };
        } else {
            return new ITexture[] { TextureFactory.of(MACHINE_BRONZE_SIDE) };
        }
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
     * 只有装满「蒸汽」的流体容器才允许放入输入槽；空容器或非流体物品走父类逻辑。
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
        Fluid fluid = aFluid.getFluid();
        if (fluid == null) return false;
        return "steam".equals(fluid.getName());
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    @Override
    public String[] getInfoData() {
        String nameKey = "gt.blockmachines." + mName + ".name";
        if (mFluid == null) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal(nameKey) + EnumChatFormatting.RESET,
                StatCollector.translateToLocal("GT5U.infodata.digital_tank.stored_fluid"),
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("GT5U.infodata.digital_tank.stored_fluid.empty")
                    + EnumChatFormatting.RESET,
                EnumChatFormatting.GREEN + "0 L"
                    + EnumChatFormatting.RESET
                    + " "
                    + EnumChatFormatting.YELLOW
                    + formatNumber(getRealCapacity())
                    + " L"
                    + EnumChatFormatting.RESET };
        }
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal(nameKey) + EnumChatFormatting.RESET,
            StatCollector.translateToLocal("GT5U.infodata.digital_tank.stored_fluid"),
            EnumChatFormatting.GOLD + mFluid.getLocalizedName() + EnumChatFormatting.RESET,
            EnumChatFormatting.GREEN + formatNumber(mFluid.amount)
                + " L"
                + EnumChatFormatting.RESET
                + " "
                + EnumChatFormatting.YELLOW
                + formatNumber(getRealCapacity())
                + " L"
                + EnumChatFormatting.RESET };
    }
}
