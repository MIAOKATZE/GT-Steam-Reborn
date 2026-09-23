package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度重力沙/砾（v1.20.39 G8 重力沙砾，plan §3.9）：extends 原版 {@link BlockFalling}，
 * 消费方 = prosperityCoarseSand / prosperityRiverGravel 两实例（注册名/unlocalizedName 不变，
 * 世界数据兼容；旧区块中既有方块仅在受到邻块更新时结算塌落，属预期）。
 * prosperitySilicaSand 保持非重力（已拍板，不在本类消费面）。
 * <p>
 * 构造观感与 {@code BlockProsperityNaturalBase} 逐项对齐：Material.ground、hardness 0.5F、
 * soundTypeGravel；掉落自身（BlockFalling 默认 getItemDropped，不覆写）。
 * 生成时序<b>不</b>依赖任何 {@code fallInstantly} 窗口：该标志在本仓只在
 * {@code GTSRChunkProviderBase} 的两处被<b>显式置 {@code false}</b>、全仓无 {@code true} 置位点（S0b
 * 实测，P20 债⑤ 按符号改齐——旧注释写"沿用框架 populate 期置位/复位窗口 + 行号"，行号会随改动漂移，
 * 而"窗口"本身从未落地）。⇒ 重力结算走 vanilla 默认路径。
 * <p>
 * 单 icon；贴图名随实例传入（gtsr:prosperity_coarse_sand / gtsr:prosperity_river_gravel，
 * 既有 32px 贴图零改动）。专用服字段剥离纪律：@SideOnly 字段不带字段级初始化器，
 * registerBlockIcons 内赋值。
 */
public class BlockProsperityFalling extends BlockFalling {

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String texture;

    public BlockProsperityFalling(String blockName, String texture) {
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
