package com.miaokatze.gtsr.common.dimension.shattered.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 破碎之地十字装饰方块（dim79 重做 S-B2，plan §12 修订 9：硬方块风装饰；独立 Block ID，
 * §12 修订 2）。双实例：石刺簇（ShatteredSpikeCluster）/ 灰烬棘丛（ShatteredAshThorn），
 * 由 ShatteredDecorPlacer 确定性散布，本类不感知群系。
 * <p>
 * 渲染 = 原版十字样式（vanilla BlockTallGrass 口径）：{@code getRenderType()=1}、
 * {@code isOpaqueCube()=false}、{@code renderAsNormalBlock()=false}、碰撞箱 null、
 * bounds 0.4/0.9。硬方块风取 Material.rock + 石声（非 plants）。<b>不继承 BlockBush</b>：
 * 其 canBlockStay 随机Tick 仅认原版土壤，在自研 corestone/top 方块上会随机掉落——只取十字
 * 渲染口径、去掉随机Tick 生存检查（落地于自然区 top 由放置层保证）。
 * <p>
 * 硬度档位（新方块集全部 ≥1000F 口径一致，可调常量登记切片报告）：hardness 1000F、
 * blast 1200、harvest pickaxe 3。贴图占位（gtsr:shattered_spike_cluster / shattered_ash_thorn，
 * artgen 轮次换像素不改注册名）。
 */
public class BlockShatteredCrossDecor extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段）。 */
    private final String texture;

    public BlockShatteredCrossDecor(String blockName, String texture) {
        super(Material.rock);
        setBlockName(blockName);
        this.texture = texture;
        setHardness(1000.0F);
        setResistance(1200.0F);
        setHarvestLevel("pickaxe", 3);
        setStepSound(soundTypeStone);
        final float spread = 0.4F;
        setBlockBounds(0.5F - spread, 0.0F, 0.5F - spread, 0.5F + spread, 0.9F, 0.5F + spread);
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
}
