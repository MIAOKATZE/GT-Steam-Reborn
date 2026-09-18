package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度自然区草丛装饰方块（dim78 S-A1，plan §12 修订 5：地表装饰修复"光秃秃"；独立 Block ID，
 * plan §12 修订 2）。双实例：锈草丛（Steppe/Wastes 消费）/ 铜绿草丛（Forest/Swamp 消费），
 * 映射表在 ProsperityDecorPlacer（放置层），本类不感知群系。
 * <p>
 * 渲染 = 原版 tallgrass 十字样式参照（vanilla BlockTallGrass/BlockBush 口径）：
 * {@code getRenderType()=1} 十字渲染、{@code isOpaqueCube()=false}、{@code renderAsNormalBlock()=false}、
 * 碰撞箱 null、bounds 0.4/0.8（BlockTallGrass 构造器同值）。<b>不继承 BlockBush</b>：其
 * canBlockStay 随机Tick 仅认原版土壤（canSustainPlant），在自研 base 方块上会随机掉落——本维度
 * 无原版土，故只取十字渲染口径、去掉随机Tick 生存检查（装饰由放置层保证落地于自然区 top 方块）。
 * <p>
 * 草色 tint：{@link #colorMultiplier(IBlockAccess, int, int, int)} 3×3 邻域群系草色均值
 * （BlockProsperitySurface meta0 同款思路）——两实例 × 四群系草色 → 每群系草丛色调互异。
 * 贴图占位（gtsr:prosperity_tuft_*，artgen 轮次换像素不改注册名）。
 */
public class BlockProsperityTuft extends Block {

    /** 物品形态/准星取色占位（世界外无群系上下文；与 BiomeRustedSteppe.GRASS_COLOR 同值）。 */
    private static final int PLACEHOLDER_GRASS_COLOR = 0x8A7B4A;

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String texture;

    public BlockProsperityTuft(String blockName, String texture) {
        super(Material.plants);
        setBlockName(blockName);
        this.texture = texture;
        setHardness(0.0F);
        setStepSound(soundTypeGrass);
        final float spread = 0.4F;
        setBlockBounds(0.5F - spread, 0.0F, 0.5F - spread, 0.5F + spread, 0.8F, 0.5F + spread);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /** 十字渲染（vanilla BlockTallGrass 同款 renderType）。 */
    @Override
    public int getRenderType() {
        return 1;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
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

    /** 物品形态取色：固定锈草色占位（世界外无群系上下文）。 */
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

    /** 草色 tint 消费端：3×3 邻域群系草色均值（BlockProsperitySurface.colorMultiplier 同款）。 */
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
