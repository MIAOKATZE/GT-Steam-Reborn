package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 独碑岩（dim79 重做 S-B2，plan §5 S-B2 + §12 修订 2/3）：遗迹平台/地标结构块——
 * WorldGenShatteredRuins 3×3 平台消费（旧 shatteredBlackstone 平台随葬）。
 * <p>
 * 硬度档位（新方块集全部 ≥1000F，可调常量登记切片报告）：hardness 1200F（集合内最高档）、
 * blast 1200、harvest pickaxe 3。
 * <p>
 * 单 icon；贴图占位（gtsr:shattered_monolith，artgen 轮次换像素不改注册名/lang 键）。
 * 专用服字段剥离纪律：@SideOnly 字段不带字段级初始化器，registerBlockIcons 内赋值。
 */
public class BlockShatteredMonolith extends Block {

    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器，registerBlockIcons 内赋值。
    @SideOnly(Side.CLIENT)
    private IIcon monolithIcon;

    public BlockShatteredMonolith() {
        super(Material.rock);
        setBlockName("ShatteredMonolith");
        setHardness(1200.0F);
        setResistance(1200.0F);
        setHarvestLevel("pickaxe", 3);
        setStepSound(soundTypeStone);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.monolithIcon = register.registerIcon("gtsr:shattered_monolith");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.monolithIcon;
    }
}
