package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 灰烬枯树干（dim79 重做 S-B2，plan §12 修订 9：枯树类硬方块风装饰；独立 Block ID，§12 修订 2）。
 * 枯树造型（裸干无叶，2-5 高）由 ShatteredDecorPlacer 拼装，本类只供方块。
 * <p>
 * 硬度档位（新方块集全部 ≥1000F 口径一致，可调常量登记切片报告）：hardness 1000F、
 * blast 1200、harvest pickaxe 3；Material.rock + 石声（枯木石化风）。
 * <p>
 * 双 icon（side/top，vanilla BlockLog 同构）；贴图占位（gtsr:shattered_ash_log_side /
 * shattered_ash_log_top，artgen 轮次换像素不改注册名）。
 */
public class BlockShatteredAshLog extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon topIcon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String textureSide;
    private final String textureTop;

    public BlockShatteredAshLog(String blockName, String textureSide, String textureTop) {
        super(Material.rock);
        setBlockName(blockName);
        this.textureSide = textureSide;
        this.textureTop = textureTop;
        setHardness(1000.0F);
        setResistance(1200.0F);
        setHarvestLevel("pickaxe", 3);
        setStepSound(soundTypeStone);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.sideIcon = register.registerIcon(this.textureSide);
        this.topIcon = register.registerIcon(this.textureTop);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 0 || side == 1 ? this.topIcon : this.sideIcon;
    }
}
