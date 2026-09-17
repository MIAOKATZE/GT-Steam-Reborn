package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 残骸散点方块（dim1 S2，plan §1.2 S2 / 02 §3）：单方块 4 meta，供 S4a 结构体系放置。
 * <ul>
 * <li>0 轨枕 / 1 管道 / 2 铆接板 / 3 烟囱残段（本轮全部为整方块占位，异形/半砖不在裁剪范围内）。</li>
 * <li>贴图（S7a 自有像素）：gtsr:ruin_debris_{sleeper,pipe,rivet_plate,chimney} 与 meta 0..3 一一对应，
 * 零外部依赖；注册名不变。</li>
 * </ul>
 */
public class BlockRuinDebris extends Block {

    public static final int META_SLEEPER = 0;
    public static final int META_PIPE = 1;
    public static final int META_RIVET_PLATE = 2;
    public static final int META_CHIMNEY = 3;
    /** meta 总数。 */
    public static final int META_COUNT = 4;

    /** 贴图（gtsr 自有贴图，下标=meta）。 */
    private static final String[] PLACEHOLDER_ICONS = { "gtsr:ruin_debris_sleeper", "gtsr:ruin_debris_pipe",
        "gtsr:ruin_debris_rivet_plate", "gtsr:ruin_debris_chimney" };

    // 专用服字段剥离纪律：@SideOnly 字段不得带字段级初始化器（构造器双端执行 → 服侧 NoSuchFieldError），
    // 数组在 registerBlockIcons（客户端专用方法）内赋值（vanilla BlockGrass 同款约定）。
    @SideOnly(Side.CLIENT)
    private IIcon[] metaIcons;

    public BlockRuinDebris() {
        super(Material.rock);
        setBlockName("RuinDebris");
        setHardness(2.0F);
        setResistance(6.0F);
        setStepSound(soundTypeStone);
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
