package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 碎裂草方块（dim1 S6a，04 §1.1 碎裂浮岛 topBlock）。
 * <p>
 * 单方块无 meta；草色 tint 走 {@link #colorMultiplier(IBlockAccess, int, int, int)} 的 3×3
 * 邻域群系草色均值（BlockProsperitySurface meta0 同款，GT5U BlockDarkWorldPollutedDirt 样板）。
 * 贴图（S7a 自有像素）：gtsr:shattered_grass / shattered_grass_side / shattered_dirt 三面分置；
 * 注册名（方块名/lang 键）保持不变。
 */
public class BlockShatteredGrass extends Block {

    /** 物品形态/准星取色占位（与 BiomeShatteredIsles.GRASS_COLOR 同值，S7a 艺术轮同调）。 */
    private static final int PLACEHOLDER_GRASS_COLOR = 0x6B5E7E;

    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器（构造器双端执行 → 服侧 NoSuchFieldError），
    // 在 registerBlockIcons（客户端专用方法）内赋值（vanilla BlockGrass 同款约定）。
    @SideOnly(Side.CLIENT)
    private IIcon grassTopIcon;
    @SideOnly(Side.CLIENT)
    private IIcon grassSideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon grassBottomIcon;

    public BlockShatteredGrass() {
        super(Material.grass);
        setBlockName("ShatteredGrass");
        setHardness(0.6F);
        setStepSound(soundTypeGrass);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.grassTopIcon = register.registerIcon("gtsr:shattered_grass");
        this.grassSideIcon = register.registerIcon("gtsr:shattered_grass_side");
        this.grassBottomIcon = register.registerIcon("gtsr:shattered_dirt");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 1 ? this.grassTopIcon : side == 0 ? this.grassBottomIcon : this.grassSideIcon;
    }

    /** 物品形态取色：固定灰紫占位（世界外无群系上下文）。 */
    @Override
    @SideOnly(Side.CLIENT)
    public int getBlockColor() {
        return PLACEHOLDER_GRASS_COLOR;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int meta) {
        return getBlockColor();
    }

    /** 草色 tint 消费端：3×3 邻域群系草色均值（BlockProsperitySurface.colorMultiplier 同款思路）。 */
    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                final int color = world.getBiomeGenForCoords(x + dx, z + dz)
                    .getBiomeGrassColor(x + dx, y, z + dz);
                red += (color >> 16) & 255;
                green += (color >> 8) & 255;
                blue += color & 255;
            }
        }
        return (red / 9 & 255) << 16 | (green / 9 & 255) << 8 | blue / 9 & 255;
    }
}
