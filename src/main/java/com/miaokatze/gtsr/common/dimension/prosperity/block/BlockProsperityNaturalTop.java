package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度自然区表层 top 方块（dim78 S-A1，plan §12 修订 2/4：每群系独立 Block ID，不做 meta 扩展；
 * 既有 BlockProsperitySurface meta0-5 城内语义冻结不动）。四实例：锈草甸（Steppe）/ 铜绿（Forest）/
 * 黄铜丘沙（Wastes）/ 泥炭（Swamp），四群系读感互异。
 * <p>
 * 三面分置（top/side/bottom，vanilla BlockGrass 同构）；贴图占位（gtsr:prosperity_*_top 家族，
 * 后续 artgen 轮次换像素不改注册名/lang 键——BlockProsperitySurface 同款纪律）。不做 colorMultiplier
 * tint：独立方块自身差异化，避免占位贴图与群系草色相乘发暗（tint 消费移至草丛/叶）。
 * <p>
 * 专用服字段剥离纪律：@SideOnly IIcon 字段不带字段级初始化器（构造器双端执行 → 服侧
 * NoSuchFieldError），在 registerBlockIcons（客户端专用方法）内赋值；贴图名为 String 常量
 * （双端安全），vanilla BlockGrass 同款约定。
 */
public class BlockProsperityNaturalTop extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon topIcon;
    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon bottomIcon;

    /** 三面贴图名（gtsr 域内；注册名不变换贴图）。 */
    private final String textureTop;
    private final String textureSide;
    private final String textureBottom;

    public BlockProsperityNaturalTop(String blockName, String textureTop, String textureSide, String textureBottom) {
        super(Material.grass);
        setBlockName(blockName);
        this.textureTop = textureTop;
        this.textureSide = textureSide;
        this.textureBottom = textureBottom;
        setHardness(0.6F);
        setStepSound(soundTypeGrass);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.topIcon = register.registerIcon(this.textureTop);
        this.sideIcon = register.registerIcon(this.textureSide);
        this.bottomIcon = register.registerIcon(this.textureBottom);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 1 ? this.topIcon : side == 0 ? this.bottomIcon : this.sideIcon;
    }
}
