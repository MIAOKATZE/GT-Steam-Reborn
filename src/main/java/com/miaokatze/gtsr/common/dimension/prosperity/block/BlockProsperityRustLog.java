package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度锈树干方块（dim78 S-A1，plan §12 修订 5：地表装饰修复"光秃秃"；独立 Block ID，
 * plan §12 修订 2）。普通立方块（不旋转轴向贴图，占位口径）；树造型由 ProsperityDecorPlacer
 * 确定性拼装（干柱 + 冠层），本类只承载材质/音效/硬度。
 * <p>
 * 贴图 side/top 两面分置（vanilla BlockLog 同构，简化不按轴向旋转）；占位
 * （gtsr:prosperity_rust_log*，artgen 轮次换像素不改注册名/lang 键）。
 */
public class BlockProsperityRustLog extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon topIcon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String textureSide;
    private final String textureTop;

    public BlockProsperityRustLog(String blockName, String textureSide, String textureTop) {
        super(Material.wood);
        setBlockName(blockName);
        this.textureSide = textureSide;
        this.textureTop = textureTop;
        setHardness(2.0F);
        setStepSound(soundTypeWood);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.sideIcon = register.registerIcon(this.textureSide);
        this.topIcon = register.registerIcon(this.textureTop);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 1 || side == 0 ? this.topIcon : this.sideIcon;
    }
}
