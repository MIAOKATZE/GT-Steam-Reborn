package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣废岩（v1.20.39 G4 地底石化，plan §3.7）：dim78 地底 wholeBody 主体石——
 * {@code ChunkProviderProsperityRuins.baseBlockOf} 四群系统一返回本方块（整段同质，单一变体，
 * 第二变体无消费面不做），替代 S-A1 起的 BlockProsperityNaturalBase 壤土系主体。
 * <p>
 * 硬度档位参照仓内石类带 1.5-3.0F（取原版 stone 档 1.5F / 抗爆 10F / harvest pickaxe 0），
 * Material.rock；掉落自身（无 getItemDropped 覆写，与仓内 NaturalBase 一致风格）。
 * topBlock/filler 表层链与名册外回落 {@code Blocks.stone} 均不在本类职责内。
 * <p>
 * 单 icon；贴图 gtsr:prosperity_stone（32px，tools/artgen/dim7879/draw32_dim78.py 幂等再生；
 * 注册名不变换贴图，双端安全 String 字段）。专用服字段剥离纪律：@SideOnly 字段不带字段级初始化器，
 * registerBlockIcons 内赋值。
 */
public class BlockProsperityStone extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String texture;

    public BlockProsperityStone(String blockName, String texture) {
        super(Material.rock);
        setBlockName(blockName);
        this.texture = texture;
        setHardness(1.5F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 0);
        setStepSound(soundTypeStone);
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
}
