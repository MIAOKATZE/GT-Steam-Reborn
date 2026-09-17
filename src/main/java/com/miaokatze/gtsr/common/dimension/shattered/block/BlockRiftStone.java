package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 裂隙石（dim1 S6a，04 §1.1 深裂谷 topBlock/fillerBlock 通体）。
 * <p>
 * 单方块无 meta；贴图占位（S7a artgen 前临时口径）：原版 stone 路径；
 * 注册名（方块名/lang 键）S7a 换像素时保持不变。
 */
public class BlockRiftStone extends Block {

    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器，registerBlockIcons 内赋值。
    @SideOnly(Side.CLIENT)
    private IIcon stoneIcon;

    public BlockRiftStone() {
        super(Material.rock);
        setBlockName("RiftStone");
        setHardness(1.5F);
        setStepSound(soundTypeStone);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.stoneIcon = register.registerIcon("minecraft:stone");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.stoneIcon;
    }
}
