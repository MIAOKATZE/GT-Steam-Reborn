package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度自然区主体 base 方块（dim78 S-A1，plan §12 修订 4：自然区 stone 主体按群系替换为
 * 本族方块；每群系独立 Block ID，plan §12 修订 2）。四实例：锈草甸壤土 / 铜绿壤土 / 黄铜砂岩 /
 * 泥炭沼泥。filler 复用 base（planner 授权实现轮定夺，plan §12 修订 4 "filler 可复用 base"）。
 * <p>
 * 单 icon；贴图占位（gtsr:prosperity_*_base，后续 artgen 轮次换像素不改注册名/lang 键）。
 * 主体替换点在 ChunkProviderProsperityRuins.replaceBlocksForBiome（该切片证据：biomes 数组
 * 在 generateTerrain 之后才加载，主体替换无法前移）；城内 's' 键 = prosperitySurface meta5
 * 语义冻结不受影响。
 */
public class BlockProsperityNaturalBase extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String texture;

    public BlockProsperityNaturalBase(String blockName, String texture) {
        super(Material.ground);
        setBlockName(blockName);
        this.texture = texture;
        setHardness(0.5F);
        setStepSound(soundTypeGravel);
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
