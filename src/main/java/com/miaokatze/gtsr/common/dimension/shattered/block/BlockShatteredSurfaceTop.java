package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 破碎之地群系表层 top 方块（dim79 重做 S-B2，plan §5 S-B2 + §12 修订 2/3：每群系独立
 * Block ID，不做 meta 扩展）。四实例：灰烬草毡（Ashen Prairie）/ 渣木草皮（Slagwood Grove）/
 * 琉璃沙（Vitreous Waste）/ 焦油壳（Tar Basin），四群系读感互异。
 * <p>
 * 硬方块风：Material.rock + 石声（非草系 Material.grass），硬度档位（新方块集全部 ≥1000F，
 * 可调常量登记切片报告）hardness 1000F、blast 1200、harvest pickaxe 3。
 * <p>
 * 三面分置（top/side/bottom，vanilla BlockGrass 同构；bottom 复用同群系 base 贴图——
 * BlockProsperityNaturalTop 同款）；贴图占位（gtsr:shattered_*_top 家族，artgen 轮次换像素
 * 不改注册名/lang 键）。不做 colorMultiplier tint：独立方块自身差异化。
 * <p>
 * 专用服字段剥离纪律：@SideOnly IIcon 字段不带字段级初始化器（构造器双端执行 → 服侧
 * NoSuchFieldError），在 registerBlockIcons 内赋值；贴图名为 String 常量（双端安全）。
 */
public class BlockShatteredSurfaceTop extends Block {

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

    public BlockShatteredSurfaceTop(String blockName, String textureTop, String textureSide, String textureBottom) {
        super(Material.rock);
        setBlockName(blockName);
        this.textureTop = textureTop;
        this.textureSide = textureSide;
        this.textureBottom = textureBottom;
        setHardness(1000.0F);
        setResistance(1200.0F);
        setHarvestLevel("pickaxe", 3);
        setStepSound(soundTypeStone);
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
