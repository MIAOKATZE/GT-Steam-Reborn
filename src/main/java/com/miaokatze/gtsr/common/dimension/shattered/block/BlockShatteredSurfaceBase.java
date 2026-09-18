package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 破碎之地群系表层 base 方块（dim79 重做 S-B2，plan §5 S-B2 + §12 修订 2/3：每群系独立
 * Block ID）。四实例：灰烬壤 / 渣木渣 / 琉璃化岩 / 焦油砾——表层 filler 段（1-2 格）消费，
 * filler 复用 base（"四群系各 top + base（filler 可复用 base）"任务口径；主体保持
 * shatteredCorestone，见 ChunkProviderShatteredGrounds 表层替换注释）。
 * <p>
 * 硬度档位（新方块集全部 ≥1000F，可调常量登记切片报告）：hardness 1000F、blast 1200、
 * harvest pickaxe 3。
 * <p>
 * 单 icon；贴图占位（gtsr:shattered_*_base，artgen 轮次换像素不改注册名/lang 键）。
 * 专用服字段剥离纪律：@SideOnly 字段不带字段级初始化器，registerBlockIcons 内赋值。
 */
public class BlockShatteredSurfaceBase extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String texture;

    public BlockShatteredSurfaceBase(String blockName, String texture) {
        super(Material.rock);
        setBlockName(blockName);
        this.texture = texture;
        setHardness(1000.0F);
        setResistance(1200.0F);
        setHarvestLevel("pickaxe", 3);
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
