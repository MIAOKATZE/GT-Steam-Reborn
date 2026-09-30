package com.miaokatze.gtsr.common.dimension.prosperity.architecture;

import net.minecraft.block.BlockLog;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Zenith heartwood retains vanilla axis metadata: upright 0, X 4, Z 8, bark 12. */
public final class BlockZenithLog extends BlockLog {

    private final String sideTexture;
    private final String endTexture;
    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;
    @SideOnly(Side.CLIENT)
    private IIcon endIcon;

    public BlockZenithLog(String name, String side, String end) {
        setBlockName(name);
        setHardness(3F);
        setStepSound(soundTypeWood);
        sideTexture = side;
        endTexture = end;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        sideIcon = register.registerIcon(sideTexture);
        endIcon = register.registerIcon(endTexture);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected IIcon getSideIcon(int meta) {
        return sideIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected IIcon getTopIcon(int meta) {
        return endIcon;
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }
}
