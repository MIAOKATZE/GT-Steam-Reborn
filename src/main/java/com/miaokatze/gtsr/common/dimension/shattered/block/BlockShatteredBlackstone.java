package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 碎裂黑石（dim1 S6a，04 §1.1 奇点荒原 topBlock/fillerBlock + §1.6 裂隙层 y&lt;20 基底）。
 * <p>
 * 单方块无 meta；贴图占位（S7a artgen 前临时口径）：原版 coal_block 路径（1.7.10 无黑石原版贴图，
 * 取暗色近邻）；注册名（方块名/lang 键）S7a 换像素时保持不变。
 */
public class BlockShatteredBlackstone extends Block {

    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器，registerBlockIcons 内赋值。
    @SideOnly(Side.CLIENT)
    private IIcon blackstoneIcon;

    public BlockShatteredBlackstone() {
        super(Material.rock);
        setBlockName("ShatteredBlackstone");
        setHardness(2.0F);
        setStepSound(soundTypeStone);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.blackstoneIcon = register.registerIcon("minecraft:coal_block");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.blackstoneIcon;
    }
}
