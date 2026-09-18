package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度锈树叶方块（dim78 S-A1，plan §12 修订 5：地表装饰修复"光秃秃"；独立 Block ID，
 * plan §12 修订 2）。普通立方块（不接原版树叶图形档/衰减逻辑——无随机Tick，冠层由
 * ProsperityDecorPlacer 确定性拼装后永久留存，"废都植被被岁月定格"观感口径）。
 * <p>
 * 叶色 tint：{@link #colorMultiplier(IBlockAccess, int, int, int)} 3×3 邻域群系草色均值
 * （BlockProsperitySurface meta0 同款思路）——同一冠层跨群系色调互异（森林铜绿/草原锈褐）。
 * 贴图占位（gtsr:prosperity_rust_leaves，artgen 轮次换像素不改注册名）。
 */
public class BlockProsperityRustLeaves extends Block {

    /** 物品形态/准星取色占位（世界外无群系上下文；与 BiomeGearworkForest.GRASS_COLOR 同值）。 */
    private static final int PLACEHOLDER_LEAVES_COLOR = 0x4E8A6E;

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String texture;

    public BlockProsperityRustLeaves(String blockName, String texture) {
        super(Material.leaves);
        setBlockName(blockName);
        this.texture = texture;
        setHardness(0.2F);
        setStepSound(soundTypeGrass);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.icon = register.registerIcon(this.texture);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.icon;
    }

    /** 物品形态取色：固定铜绿色占位（世界外无群系上下文）。 */
    @Override
    @SideOnly(Side.CLIENT)
    public int getBlockColor() {
        return PLACEHOLDER_LEAVES_COLOR;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int meta) {
        return getBlockColor();
    }

    /** 叶色 tint 消费端：3×3 邻域群系草色均值（BlockProsperitySurface.colorMultiplier 同款）。 */
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
