package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度锈变地表方块（dim1 S2，plan §1.2 S2 / 02 §1.1）：单方块 6 meta。
 * <ul>
 * <li>0 锈草（草原/森林地表顶层，顶面 tint 走群系草色）/ 1 锈土 / 2 黄铜沙 / 3 锈泥炭 / 4 锈黏 / 5 锈石铺面。</li>
 * <li>群系挂接：锈蚀草原/齿轮森林 top=0 filler=1；黄铜荒漠 top=2 filler=5；起雾沼泽 top=3 filler=4。</li>
 * <li>草色 tint：meta0 走 {@link #colorMultiplier(IBlockAccess, int, int, int)} 的 3×3 邻域群系草色均值
 * （GT5U toxiceverglades BlockDarkWorldPollutedDirt.java:46-62 样板）；其余 meta 恒白色不 tint。</li>
 * <li>贴图占位（S7a artgen 前临时口径）：全部原版路径（grass_top/grass_side/dirt/sand/clay/stonebrick），
 * 零外部依赖——BlockRunawaySingularity.java:130-137 教训（不引外部 mod jar 中可能缺失的贴图）；
 * 注册名（方块名/lang 键）S7a 换像素时保持不变。</li>
 * </ul>
 */
public class BlockProsperitySurface extends Block {

    public static final int META_RUST_GRASS = 0;
    public static final int META_RUST_DIRT = 1;
    public static final int META_BRASS_SAND = 2;
    public static final int META_RUST_PEAT = 3;
    public static final int META_RUST_CLAY = 4;
    public static final int META_RUST_STONE = 5;
    /** meta 总数（越界 meta 钳回本值域）。 */
    public static final int META_COUNT = 6;

    /** meta1..5 占位贴图（原版路径；meta0 三面分置不用本表）。 */
    private static final String[] PLACEHOLDER_ICONS = {
        // meta0 占位空缺（草三面：grass_top / grass_side / dirt）
        null, "minecraft:dirt", "minecraft:sand", "minecraft:dirt", "minecraft:clay", "minecraft:stonebrick" };

    /** meta0 世界外取色（物品栏/准星）：与 BiomeRustedSteppe.GRASS_COLOR 同值占位，S7a 艺术轮同调。 */
    private static final int PLACEHOLDER_GRASS_COLOR = 0x8A7B4A;

    @SideOnly(Side.CLIENT)
    private IIcon grassTopIcon;
    @SideOnly(Side.CLIENT)
    private IIcon grassSideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon grassBottomIcon;
    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器（构造器双端执行 → 服侧 NoSuchFieldError），
    // 数组在 registerBlockIcons（客户端专用方法）内赋值（vanilla BlockGrass 同款约定）。
    @SideOnly(Side.CLIENT)
    private IIcon[] metaIcons;

    public BlockProsperitySurface() {
        super(Material.ground);
        setBlockName("ProsperitySurface");
        setHardness(0.6F);
        setStepSound(soundTypeGravel);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.metaIcons = new IIcon[META_COUNT];
        this.grassTopIcon = register.registerIcon("minecraft:grass_top");
        this.grassSideIcon = register.registerIcon("minecraft:grass_side");
        this.grassBottomIcon = register.registerIcon("minecraft:dirt");
        for (int meta = META_RUST_DIRT; meta < META_COUNT; meta++) {
            this.metaIcons[meta] = register.registerIcon(PLACEHOLDER_ICONS[meta]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (meta == META_RUST_GRASS) {
            return side == 1 ? this.grassTopIcon : side == 0 ? this.grassBottomIcon : this.grassSideIcon;
        }
        return this.metaIcons[clampMeta(meta)];
    }

    /** 物品形态/准星取色：meta0 用固定锈草色（世界外无群系上下文），其余 meta 不 tint。 */
    @Override
    @SideOnly(Side.CLIENT)
    public int getBlockColor() {
        return PLACEHOLDER_GRASS_COLOR;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int meta) {
        return meta == META_RUST_GRASS ? getBlockColor() : 0xFFFFFF;
    }

    /**
     * 草色 tint 消费端（仅 meta0）：3×3 邻域群系草色均值（GT5U BlockDarkWorldPollutedDirt.java:46-62 原样思路）；
     * 非 meta0 返回白色（renderStandardBlock 全面包乘色，不 tint 沙土石 meta）。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        if (world.getBlockMetadata(x, y, z) != META_RUST_GRASS) {
            return 0xFFFFFF;
        }
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

    /** meta 钳位（0..META_COUNT-1）。 */
    public static int clampMeta(int meta) {
        return clampWithin(meta, META_COUNT);
    }

    /** 通用 meta 钳位（本包 meta 族方块共用）。 */
    static int clampWithin(int meta, int count) {
        return Math.max(0, Math.min(count - 1, meta));
    }
}
