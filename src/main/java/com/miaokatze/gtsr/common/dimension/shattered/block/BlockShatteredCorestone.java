package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 碎核岩（dim79 重做 S-B2，plan §5 S-B2 + §12 修订 2/3）：地形主体石——
 * ChunkProviderShatteredGrounds.generateTerrain 基岩之上实心填充至 heightAt，表层替换后
 * 即暴露在崖壁/谷地的主体方块。
 * <p>
 * 硬度档位（plan §12 修订 3：新方块集全部 ≥1000F，含表层族；分层值 = 可调常量，登记切片报告）：
 * hardness 1000F、blast 1200、harvest pickaxe 3（下界合金镐级）。旧 shatteredGrass/Dirt/
 * riftStone/shatteredBlackstone 四方块删除，注册名不保留（无 retrogen，存量 chunk≈0）。
 * <p>
 * 单 icon；贴图占位（gtsr:shattered_corestone，后续 artgen 轮次换像素不改注册名/lang 键）。
 * 专用服字段剥离纪律：@SideOnly 字段不带字段级初始化器，registerBlockIcons 内赋值。
 */
public class BlockShatteredCorestone extends Block {

    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器，registerBlockIcons 内赋值。
    @SideOnly(Side.CLIENT)
    private IIcon coreIcon;

    public BlockShatteredCorestone() {
        super(Material.rock);
        setBlockName("ShatteredCorestone");
        setHardness(1000.0F);
        setResistance(1200.0F);
        setHarvestLevel("pickaxe", 3);
        setStepSound(soundTypeStone);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.coreIcon = register.registerIcon("gtsr:shattered_corestone");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.coreIcon;
    }
}
