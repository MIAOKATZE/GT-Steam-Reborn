package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 碎裂泥土（dim1 S6a，04 §1.1 碎裂浮岛 fillerBlock）。
 * <p>
 * 单方块无 meta；贴图（S7a 自有像素）：gtsr:shattered_dirt；
 * 注册名（方块名/lang 键）保持不变。
 */
public class BlockShatteredDirt extends Block {

    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器，registerBlockIcons 内赋值。
    @SideOnly(Side.CLIENT)
    private IIcon dirtIcon;

    public BlockShatteredDirt() {
        super(Material.ground);
        setBlockName("ShatteredDirt");
        setHardness(0.5F);
        setStepSound(soundTypeGravel);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.dirtIcon = register.registerIcon("gtsr:shattered_dirt");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.dirtIcon;
    }
}
