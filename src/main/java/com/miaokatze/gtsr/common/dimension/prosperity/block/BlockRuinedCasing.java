package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 残缺外壳方块（dim1 S2，plan §1.2 S2 / §3）：单方块 3 meta，GT 机制壳的废墟拟态，
 * 供 S4a/S4b 结构与古代城放置（无 TileEntity，'C' 核心位由 S4a 以 meta1 积碳壳占位）。
 * <ul>
 * <li>0 锈蚀外壳 / 1 积碳外壳 / 2 碎瓷外壳。</li>
 * <li>贴图占位（S7a artgen 前临时口径）：原版路径（iron_block/coal_block/quartz_block_side）；
 * 注册名不变。</li>
 * </ul>
 */
public class BlockRuinedCasing extends Block {

    public static final int META_RUSTED = 0;
    public static final int META_SOOTED = 1;
    public static final int META_PORCELAIN = 2;
    /** meta 总数。 */
    public static final int META_COUNT = 3;

    /** 占位贴图（原版路径）。 */
    private static final String[] PLACEHOLDER_ICONS = { "minecraft:iron_block", "minecraft:coal_block",
        "minecraft:quartz_block_side" };

    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器（构造器双端执行 → 服侧 NoSuchFieldError），
    // 数组在 registerBlockIcons（客户端专用方法）内赋值（vanilla BlockGrass 同款约定）。
    @SideOnly(Side.CLIENT)
    private IIcon[] metaIcons;

    public BlockRuinedCasing() {
        super(Material.iron);
        setBlockName("RuinedCasing");
        setHardness(3.0F);
        setResistance(8.0F);
        setStepSound(soundTypeMetal);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.metaIcons = new IIcon[META_COUNT];
        for (int meta = 0; meta < META_COUNT; meta++) {
            this.metaIcons[meta] = register.registerIcon(PLACEHOLDER_ICONS[meta]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.metaIcons[BlockProsperitySurface.clampWithin(meta, META_COUNT)];
    }
}
