package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;

/** A full gearbox cube using the existing titanium gear pattern with tungstensteel's material tint. */
public final class BlockTungstenSteelGearbox extends Block {

    public BlockTungstenSteelGearbox() {
        super(Material.iron);
        setBlockName("TungstenSteelGearbox");
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("wrench", 2);
        setStepSound(soundTypeMetal);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {}

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return Textures.BlockIcons.MACHINE_CASING_GEARBOX_TITANIUM.getIcon();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int meta) {
        short[] rgba = Materials.TungstenSteel.mRGBa;
        return (rgba[0] & 255) << 16 | (rgba[1] & 255) << 8 | rgba[2] & 255;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return getRenderColor(0);
    }
}
