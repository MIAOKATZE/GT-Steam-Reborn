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
 * 单方块无 meta；贴图（S7a 自有像素）：gtsr:shattered_blackstone；
 * 注册名（方块名/lang 键）保持不变。
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
        this.blackstoneIcon = register.registerIcon("gtsr:shattered_blackstone");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.blackstoneIcon;
    }
}
