package com.miaokatze.gtsr.common.dimension.prosperity.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度旧栖晴晕（Old Roost Glow）——巨树冠下 / 湖上光源方块（v1.20.43 P22-B S2，p21 §4.1/§5 语义定案）。
 * <p>
 * 行为面四个硬值全部取 p21 §5 登记口径，不新造数值：
 * <ul>
 * <li><b>光 14</b> = {@code setLightLevel(0.9375F)}——torch 注册行同源字面（{@code Block.setLightLevel}
 * 体为 {@code (int)(15*value)}，0.9375×15 = 14.0625 → 14）。<b>禁「十四除十五」式浮点侥幸写法</b>：
 * 该商的 15 倍乘回是 14.0000001… 级浮点侥幸而不是定义——其完整被禁 token 见 p21 §5，
 * 本文件与 P17BlockRosterCheck lumen 档的反向钉共同封死；</li>
 * <li><b>硬度 0.0F</b> = {@code Blocks.torch} 注册行字面（Block.java:293）⇒ 点挖即碎；</li>
 * <li><b>无碰撞箱</b>：{@link #getCollisionBoundingBoxFromPool} 恒返 null（可穿身）；</li>
 * <li><b>十字渲染</b> {@code getRenderType()=1}：四件套口径照 {@link BlockProsperityTuft} 同族
 * （isOpaqueCube / renderAsNormalBlock / getRenderType / 碰撞箱），bounds 取 vanilla torch 同值（0.4/0.6）。</li>
 * </ul>
 * 材质 = {@code Material.circuits}（{@code BlockTorch} 构造同料，非植物；两料皆非 replaceable，
 * circuits 才是 torch 先例，p21 §4.1）。
 * <p>
 * <b>不继承 BlockBush</b>：其 canBlockStay 随机 Tick 仅认原版土壤（canSustainPlant），在本维度自研
 * base 上会随机掉落消失（P17 已证）；本类零覆写随机 Tick。
 * <b>零 tint</b>：金色直接画进像素（{@code prosperity_roost_glow.png} = 32×192 竖条 6 帧 +
 * 同名 mcmeta {@code animation.frametime=5}，奇点族帧带同口径），故本类<b>不</b>覆写
 * {@code colorMultiplier} / {@code getBlockColor}——父类默认白乘色即贴图原样，且避开
 * {@code BlockProsperityTuft.colorMultiplier} 那条客户端 {@code getBiomeGenForCoords} 红线（p21 §4.1）。
 * <p>
 * 消费方 = S4 旧栖晴晕两趟（树冠下 {@code SALT_LUMEN} 独立盐 / 湖上更低密度，密度只钉比值）；
 * 本片（S2）只注册与出资产，零生成接线（p21 §7-S2 独占写面）。meta 恒 0，独立 Block ID，
 * ItemBlock 三全零；不进 SurfaceGate 名册（DIM78_SIZE 恒 5）。
 */
public class BlockProsperityRoostGlow extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    /** 贴图名（gtsr 域内；注册名不变换贴图，双端安全 String 字段，同 Tuft/Log 纪律）。 */
    private final String texture;

    public BlockProsperityRoostGlow(String blockName, String texture) {
        super(Material.circuits);
        setBlockName(blockName);
        this.texture = texture;
        setHardness(0.0F);
        setLightLevel(0.9375F);
        setStepSound(soundTypeWood);
        // 视觉占位 = vanilla torch 同值（碰撞箱另由 getter 恒 null 保证，与此无关）。
        setBlockBounds(0.4F, 0.0F, 0.4F, 0.6F, 0.6F, 0.6F);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /** 十字渲染（与 Tuft 花/草同 renderType=1 档；帧带由 mcmeta 驱动跃动）。 */
    @Override
    public int getRenderType() {
        return 1;
    }

    /** 无碰撞箱：可穿身（p21 §5 行为断言主判据；本文件唯一 return null）。 */
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
