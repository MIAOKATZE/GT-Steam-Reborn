package com.miaokatze.gtsr.common.dimension.prosperity.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.fx.GTSRGlowFX;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度旧栖晴晕（Old Roost Glow）——巨树冠下 / 湖上光源方块（v1.20.43 P22-B S2 注册；
 * v1.20.44 P24-A2 按用户裁决改为「不可见方块本体 + 纯光点光效」，形态对齐失控奇点）。
 * <p>
 * <b>形态 = 奇点同款三件套</b>（只读证据 plan/tmp/ultra/singularity-glow.md §1.4）：
 * <ol>
 * <li><b>不可见载体方块</b>：{@link #registerBlockIcons} 绑 vanilla 1.7.10 自带全透明贴图
 * {@code minecraft:redstone_dust_cross_overlay}（16×16 alpha 全 0，与 {@code BlockRunawaySingularity}
 * 逐字节同款），方块本体上屏不可见；自有 32×192 竖条帧带贴图与同名 mcmeta 已退役
 * （本类零自有 PNG/mcmeta，避免死资产）；</li>
 * <li><b>光 14</b> = {@code setLightLevel(0.9375F)}——torch 注册行同源字面（{@code Block.setLightLevel}
 * 体为 {@code (int)(15*value)}，0.9375×15 = 14.0625 → 14）。<b>禁「十四除十五」式浮点侥幸写法</b>：
 * 该商的 15 倍乘回是 14.0000001… 级浮点侥幸而不是定义——其完整被禁 token 见 p21 §5，
 * 本文件与 P17BlockRosterCheck lumen 档的反向钉共同封死；</li>
 * <li><b>纯光点光效</b>：{@link #randomDisplayTick} 在方块附近确定性撒暖金辉光（见下「光效路径取舍」）；</li>
 * </ol>
 * <b>硬度 0.0F</b> = {@code Blocks.torch} 注册行字面（Block.java:293）⇒ 点挖即碎；
 * <b>无碰撞箱</b>：{@link #getCollisionBoundingBoxFromPool} 恒返 null（可穿身）；
 * <b>十字渲染</b> {@code getRenderType()=1}：四件套口径照 {@link BlockProsperityTuft} 同族
 * （isOpaqueCube / renderAsNormalBlock / getRenderType / 碰撞箱），bounds 取 vanilla torch 同值（0.4/0.6）——
 * 因贴图全透明，十字面不可见，仅保留既有渲染契约（P17 四件套断言不放松）。
 * <p>
 * 材质 = {@code Material.circuits}（{@code BlockTorch} 构造同料，非植物；两料皆非 replaceable，
 * circuits 才是 torch 先例，p21 §4.1）。
 * <b>不继承 BlockBush</b>：其 canBlockStay 随机 Tick 仅认原版土壤（canSustainPlant），在本维度自研
 * base 上会随机掉落消失（P17 已证）；本类零覆写随机 Tick（tickRate/updateTick 皆无）。
 * <b>零 tint</b>：暖金色写进粒子颜色，不覆写 {@code colorMultiplier} / {@code getBlockColor}
 * ——父类默认白乘色即贴图原样（全透明），避开 {@code BlockProsperityTuft.colorMultiplier} 那条客户端
 * {@code getBiomeGenForCoords} 红线（p21 §4.1）。
 * <p>
 * <b>光效路径取舍（randomDisplayTick 优先，按任务包优先级 2）</b>：1.7.10 客户端每 tick 走
 * {@code Minecraft.runTick() → "animateTick" → WorldClient.doVoidFogParticles}，在玩家周围
 * ±15 格箱内抽 1000 个位置、对非空气方块回调 {@code Block.randomDisplayTick}
 * （原版火把/传送门/附魔台同一条 animateTick 通道；Minecraft.java:2135-2140、
 * WorldClient.java:323-346）。<b>选它的依据</b>：该钩子零新增基础设施——无需 TileEntity、
 * 无需 client tick handler、无需维护光点位置表，方块位置由世界自身提供，正是「静态光源」的最简载体
 * （与奇点需 TE 承载业务参数不同，本方块无任何运行期参数）。<b>代价（如实申报）</b>：spawn 域 =
 * 玩家 ±15 格，更远处的光点只贡献 lightValue 光照、不出现光点 sprite（原版火把同样只在近处冒焰）；
 * 粒子渲染距离另由 {@link GTSRGlowFX} 自带的 64 格裁剪兜底。若日后要求「百格外的光点可见」，
 * 须改仿 {@code SingularityClientFXHandler} 的 ClientTickEvent 处理器 + 独立光点位置表
 * （本仓零 TESR/TE 方块先例下须自建 chunk 扫描索引），本片不预先引入。
 * <p>
 * <b>密度控制</b>：animateTick 箱内单格每 tick 被抽中概率 ≈ 1000/31³ ≈ 3.4%；本类再叠 1/2 门限
 * ⇒ 单光点平均每 ≈60 tick 生成 1 枚寿命 60 tick 的辉光（同时可见 ≈1 枚），慢速闪烁而非高频刷屏；
 * 偏移由世界 Random 派生（无裸 rand 自造随机源），与 S4 生成侧位置/数量零耦合。
 * <p>
 * 消费方 = S4 旧栖晴晕两趟（树冠下 {@code SALT_LUMEN} 独立盐 / 湖上更低密度，密度只钉比值）；
 * 本片不改生成侧（populate 位置与数量零改动）。meta 恒 0，独立 Block ID，
 * ItemBlock 三全零；不进 SurfaceGate 名册（DIM78_SIZE 恒 5）。
 */
public class BlockProsperityRoostGlow extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public BlockProsperityRoostGlow(String blockName) {
        super(Material.circuits);
        setBlockName(blockName);
        setHardness(0.0F);
        setLightLevel(0.9375F);
        setStepSound(soundTypeWood);
        // 视觉占位 = vanilla torch 同值（碰撞箱另由 getter 恒 null 保证，与此无关；贴图全透明故不可见）。
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

    /** 十字渲染（与 Tuft 花/草同 renderType=1 档；贴图全透明 ⇒ 面不可见，契约字面保留）。 */
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
        // 与 BlockRunawaySingularity 同款：vanilla 1.7.10 自带全透明方块贴图（16×16 alpha 全 0），
        // 零自有资产、材质包同名覆盖亦全透明 ⇒ 方块本体上屏不可见，只贡献 lightValue=14 的光照。
        this.icon = register.registerIcon("minecraft:redstone_dust_cross_overlay");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.icon;
    }

    /**
     * 纯光点光效（vanilla animateTick 通道，见类 javadoc「光效路径取舍」）：1/2 门限 + 小幅偏移
     * 生成 1 枚暖金短寿命辉光（{@link GTSRGlowFX} 走 additive billboard 粒子管道，非 TESR/非贴图动画）。
     * 只读 world 提供的 Random 与坐标，无状态、无分配表、服务端不执行（@SideOnly 方法由 FML 剥离）。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        if (rand.nextInt(2) != 0) {
            return;
        }
        double px = (double) x + 0.5D + ((double) rand.nextFloat() - 0.5D) * 0.4D;
        double py = (double) y + 0.5D + ((double) rand.nextFloat() - 0.5D) * 0.4D;
        double pz = (double) z + 0.5D + ((double) rand.nextFloat() - 0.5D) * 0.4D;
        GTSRGlowFX.spawn(world, px, py, pz, 0.45F, 1.0F, 0.86F, 0.5F, 60);
    }
}
