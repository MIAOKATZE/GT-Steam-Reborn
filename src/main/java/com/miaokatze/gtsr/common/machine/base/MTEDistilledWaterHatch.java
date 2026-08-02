package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchFluidGenerator;

/**
 * 蒸馏水仓：性质几乎完全等同于 GT5U 蓄水仓（Reservoir Hatch）的流体生成仓，
 * 仅将生成流体改为蒸馏水，并使用淡蓝色调的水滴材质。蒸馏水不会引发钙化，
 * 可长期为太阳能阵列/地热锅炉等机器供给免结垢工作介质。
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTEDistilledWaterHatch extends MTEHatchFluidGenerator {

    private static final int GENERATED_AMOUNT = 2_000_000_000;

    private static IIconContainer OVERLAY_DISTILLED_WATER;

    public MTEDistilledWaterHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 4);
    }

    public MTEDistilledWaterHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_DISTILLED_WATER = Textures.BlockIcons.custom("gtsr:OVERLAY_DISTILLED_WATER");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEDistilledWaterHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getCustomTooltip() {
        return new String[] { StatCollector.translateToLocal("gtsr.tooltip.distilled_water_hatch.desc") };
    }

    @Override
    public Fluid getFluidToGenerate() {
        return GTModHandler.getDistilledWater(1)
            .getFluid();
    }

    @Override
    public int getAmountOfFluidToGenerate() {
        return GENERATED_AMOUNT;
    }

    @Override
    public int getMaxTickTime() {
        return 100;
    }

    @Override
    public int getCapacity() {
        return GENERATED_AMOUNT;
    }

    @Override
    public boolean doesHatchMeetConditionsToGenerate() {
        return true;
    }

    @Override
    public void generateParticles(World aWorld, String name) {}

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_DISTILLED_WATER) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_DISTILLED_WATER) };
    }

    @Override
    public synchronized String[] getDescription() {
        return new String[] {
            EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.distilled_water_hatch.desc"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + "2,000,000,000 "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.distilled_water_hatch.usage"),
            EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.added_by")
                + " "
                + EnumChatFormatting.AQUA
                + "GT"
                + EnumChatFormatting.GREEN
                + "-"
                + EnumChatFormatting.GOLD
                + "Steam"
                + EnumChatFormatting.RED
                + "-"
                + EnumChatFormatting.BLUE
                + "Reborn" };
    }
}
