package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.Textures;
import gregtech.common.blocks.MaterialCasings;

/** A full gearbox cube using the native tungstensteel casing texture and machine-metal color. */
public final class BlockTungstenSteelGearbox extends Block {

    public BlockTungstenSteelGearbox() {
        super(MaterialCasings.INSTANCE);
        setBlockName("TungstenSteelGearbox");
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("wrench", 2);
        setStepSound(soundTypeMetal);
    }

    @Override
    public int getRenderType() {
        return GregTechAPI.sBlockCasings2.getRenderType();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {}

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return Textures.BlockIcons.MACHINE_CASING_GEARBOX_TUNGSTENSTEEL.getIcon();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int meta) {
        return Dyes.MACHINE_METAL.toInt();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return getRenderColor(0);
    }
}
