package com.miaokatze.gtsr.common.machine.base;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_BOTTOM;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_SIDE;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_TOP;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;

public class MTEWaterCacheNode extends MTEFilteredCacheNode {

    private static final int CAPACITY = 128_000;
    private static final int OUTPUT_PER_TICK = 400;

    private static Textures.BlockIcons.CustomIcon TOP_OVERLAY;

    public MTEWaterCacheNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 3);
    }

    public MTEWaterCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        TOP_OVERLAY = new Textures.BlockIcons.CustomIcon("gtsr:MTEWaterCacheNode");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWaterCacheNode(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public int getRealCapacity() {
        return CAPACITY;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection sideDirection,
        ForgeDirection facingDirection, int colorIndex, boolean active, boolean redstoneLevel) {
        if (sideDirection == ForgeDirection.UP) {
            return new ITexture[] { TextureFactory.of(MACHINE_BRONZE_TOP), TextureFactory.of(TOP_OVERLAY) };
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
        if (fluid == null) return false;
        String name = fluid.getName();
        return "water".equals(name) || "ic2distilledwater".equals(name);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            if (mOutputFluid && getDrainableStack() != null && (aTick % 20 == 0)) {
                IFluidHandler tTank = aBaseMetaTileEntity.getITankContainerAtSide(aBaseMetaTileEntity.getFrontFacing());
                if (tTank != null) {
                    FluidStack tDrained = drain(OUTPUT_PER_TICK * 20, false);
                    if (tDrained != null) {
                        int tFilledAmount = tTank.fill(aBaseMetaTileEntity.getBackFacing(), tDrained, false);
                        if (tFilledAmount > 0)
                            tTank.fill(aBaseMetaTileEntity.getBackFacing(), drain(tFilledAmount, true), true);
                    }
                }
            }
        }
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return isWaterFluid(aFluid);
    }

    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || !isWaterFluid(aFluid)) return 0;
        return super.fill(aFluid, doFill);
    }

    @Override
    public int fill(ForgeDirection side, FluidStack aFluid, boolean doFill) {
        if (aFluid == null || !isWaterFluid(aFluid)) return 0;
        return super.fill(side, aFluid, doFill);
    }

    private static boolean isWaterFluid(FluidStack aFluid) {
        if (aFluid == null) return false;
        Fluid fluid = aFluid.getFluid();
        if (fluid == null) return false;
        String name = fluid.getName();
        return "water".equals(name) || "ic2distilledwater".equals(name);
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
                    + formatNumber(CAPACITY)
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
                + formatNumber(CAPACITY)
                + " L"
                + EnumChatFormatting.RESET };
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        super.addAdditionalTooltipInformation(stack, tooltip);
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.fluid_type")
                + EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.tooltip.water_cache_node.fluid_type.water"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.output_rate")
                + EnumChatFormatting.GREEN
                + String.format("%,d", OUTPUT_PER_TICK * 20)
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + String.format("%,d", getRealCapacity())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"));
        tooltip.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.singularity_cost"));
        tooltip
            .add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.cache_node_standalone"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.cache_node_hub_transfer"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.bind_hint"));
        tooltip.add(
            EnumChatFormatting.AQUA + "GT"
                + EnumChatFormatting.GREEN
                + "-"
                + EnumChatFormatting.GOLD
                + "Steam"
                + EnumChatFormatting.RED
                + "-"
                + EnumChatFormatting.BLUE
                + "Reborn");
    }
}
