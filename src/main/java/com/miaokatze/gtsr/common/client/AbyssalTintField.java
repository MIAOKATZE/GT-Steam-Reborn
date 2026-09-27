package com.miaokatze.gtsr.common.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlockAbyssalFluid;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeSanzuRiver;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 深渊执念水色连续场（v1.20.50 P27 批次B-B2 C 片，plan §0 D3）：把
 * {@link BlockAbyssalFluid#colorMultiplier} 的"群系 instanceof 二值选色"换成压力场梯度——
 * {@code w = s01(clamp((sanzuBiomeShoreAt − lakeAt)/Δ_COLOR))}（湖内 w→1 取
 * {@link BlockAbyssalFluid#COLOR_SANZU}，水缘 w→0 取 {@link BlockAbyssalFluid#COLOR_NORMAL}），
 * 深湖心深色、浅滩浅色（附带深度观感）。类级 @SideOnly(CLIENT)：纯客户端渲染路径消费
 * （{@code BlockAbyssalFluid.colorMultiplier} 是 @SideOnly(CLIENT) 覆写，服务端永不加载本类）。
 * <p>
 * <b>求值纪律（plan §1-B2 禁改红线）</b>：只复算 {@code lakeAt} + {@code sanzuBiomeShoreAt}
 * （两参纯函数，L 片已 public 化）；<b>禁</b>复算 {@code isSanzuColumn} 全谓词（含 heightAt
 * 全地形链 µs 级 + 5×5 粗格侵蚀门递归，逐帧逐水方块不可承受）。1.7.10 客户端 join/respawn 包
 * 不含世界种子（{@code worldObj.getSeed()} 在客户端是占位值）⇒ 真种子经
 * {@code GTSRFXNet} id 2 S2C {@code SeedSyncMessage}（writeLong，登录 + 玩家切入 dim78 时发）
 * 同步到 {@link #applyWorldSeed}。
 * <p>
 * <b>缓存</b>：1:4 粗格槽表（{@code AMP_CELL_CACHE} 同构：ThreadLocal 直映射定长槽、
 * (seed,粗格) 全键精确比较、冲突即覆盖淘汰，splitmix 散列只决定淘汰分布不构成真值）缓存粗格
 * <b>w 终值</b>（代表点 = 粗格中心 (cx&lt;&lt;2)+2），块级双线性消 4 格阶梯。RVF 内部列级 memo
 * 亦为 ThreadLocal ⇒ 渲染线程与生成线程互不共享。
 * <p>
 * <b>退化路径（常驻 fallback 分支）</b>：种子未同步（holder 缺省 = 包未到 / 客户端不在
 * dim78 世界）⇒ 3×3 {@code getBiomeGenForCoords instanceof BiomeSanzuRiver} 占比 lerp
 * （{@code BlockProsperityTuft.colorMultiplier} 等仓内 4 先例同款，与原二值口径在群系平面
 * 内部逐点等价、边缘摊成 ~2-4 格窄过渡）。种子已同步但客户端在他维（桶倒执念水到主世界等）：
 * 同样走盒式 ⇒ 他维群系恒非 SanzuRiver ⇒ 恒 COLOR_NORMAL，与改造前口径一致。
 */
@SideOnly(Side.CLIENT)
public final class AbyssalTintField {

    /**
     * 过渡带压力半宽 Δ_color（缩样定档 0.010：median 带宽 22-30 格 / p90 44-90 格，3 seed ×
     * [-2560,2560]² 步距 2 读数，见 plan/tmp/p27-c-color-readings.md；候选 {0.006,0.010,0.015}
     * ≈ 12/20/30 格带的中档）。
     */
    private static final double DELTA_COLOR = 0.010D;

    /** 粗格边长移位（1:4：4×4 方块一格，与身份面 coarse 层同密度档）。 */
    private static final int CELL_SHIFT = 2;

    /**
     * 槽表容量（{@code ProsperityTerrainProfile.AMP_CELL_CACHE_CAP} 同口径：65536 格 =
     * 1024×1024 方块工作集，固定上限、冲突覆盖淘汰、重算值不变的纯记忆化）。
     */
    private static final int CACHE_CAP = 65536;

    private static final int CACHE_MASK = CACHE_CAP - 1;

    private static final ThreadLocal<CellDblSlot[]> CELL_CACHE = ThreadLocal.withInitial(AbyssalTintField::newSlots);

    /** 种子未同步哨兵（真实世界种子理论上可等于该值——届时仅表现为持续走盒式退化，无害）。 */
    private static final long SEED_UNSET = Long.MIN_VALUE;

    /** dim78 世界种子（服务端 SeedSyncMessage 落点；netty 线程写 / 渲染线程读，volatile 单长整）。 */
    private static volatile long worldSeed = SEED_UNSET;

    private AbyssalTintField() {}

    /**
     * {@code SeedSyncMessage}（GTSRFXNet id 2，S2C）客户端落点：纯 volatile 写、零 Minecraft
     * 引用 ⇒ netty 线程直写无需调度回主线程（AbsorbMessageHandler 的"调度回主线程"是粒子
     * 需要世界上下文；本处无此需求）。
     */
    public static void applyWorldSeed(long seed) {
        worldSeed = seed;
    }

    /**
     * {@link BlockAbyssalFluid#colorMultiplier} 唯一出口：场可用（种子已同步 ∧ 客户端当前世界
     * 是 dim78）⇒ 双线性粗格 w 场；否则退化 3×3 邻域盒式占比（见类注释退化路径段）。
     */
    public static int multiplierFor(IBlockAccess world, int x, int z) {
        final long seed = worldSeed;
        if (seed != SEED_UNSET && inProsperityWorld()) {
            return lerpColor(fieldWeightAt(seed, x, z));
        }
        return lerpColor(boxWeight(world, x, z));
    }

    /** 客户端当前世界是否 dim78（{@code WorldProviderProsperityRuins} 身份判定；渲染线程独占读）。 */
    private static boolean inProsperityWorld() {
        final World world = Minecraft.getMinecraft().theWorld;
        return world != null && world.provider instanceof WorldProviderProsperityRuins;
    }

    /**
     * (x,z) 的 w 场值：粗格代表点（格中心，(c&lt;&lt;2)+2）w 终值双线性插值。同一粗格四角各一次
     * 槽表查（命中 = 纯数组读 + 全键比较；未命中一次 RVF 两参求值后常驻）。
     */
    private static double fieldWeightAt(long seed, int x, int z) {
        final double scale = 1.0D / (double) (1 << CELL_SHIFT);
        final double gx = (x - 2) * scale;
        final double gz = (z - 2) * scale;
        final int cx = (int) Math.floor(gx);
        final int cz = (int) Math.floor(gz);
        final double tx = gx - cx;
        final double tz = gz - cz;
        final double w00 = cellWeight(seed, cx, cz);
        final double w10 = cellWeight(seed, cx + 1, cz);
        final double w01 = cellWeight(seed, cx, cz + 1);
        final double w11 = cellWeight(seed, cx + 1, cz + 1);
        final double top = w00 + (w10 - w00) * tx;
        final double bottom = w01 + (w11 - w01) * tx;
        return top + (bottom - top) * tz;
    }

    /**
     * 粗格 w 终值（记忆化槽表）：代表点 = 格中心，{@code t = clamp((shoreAt − lakeAt)/Δ)} 过
     * smoothstep（t²(3−2t)，{@code GTSRWorldgenHash} 同式私有实现故本地复刻，无第二真值——
     * 真值是 lakeAt/sanzuBiomeShoreAt 本身，w 只是消费阈值 {@link #DELTA_COLOR} 的派生量）。
     * 域外（含 NO_LAKE=1.0 哨兵 ⇒ d&lt;0）恒 0 ⇒ 恒 COLOR_NORMAL，与改造前"非 sanzu 域=普通档"一致。
     */
    private static double cellWeight(long seed, int cx, int cz) {
        final CellDblSlot[] slots = CELL_CACHE.get();
        final CellDblSlot slot = slots[slotIndex(seed, cx, cz)];
        if (slot.valid && slot.seed == seed && slot.cx == cx && slot.cz == cz) {
            return slot.val;
        }
        final int px = (cx << CELL_SHIFT) + 2;
        final int pz = (cz << CELL_SHIFT) + 2;
        double t = (GTSRVoronoiRiverField.sanzuBiomeShoreAt(seed, px, pz) - GTSRVoronoiRiverField.lakeAt(seed, px, pz))
            / DELTA_COLOR;
        if (t < 0.0D) {
            t = 0.0D;
        } else if (t > 1.0D) {
            t = 1.0D;
        }
        final double w = t * t * (3.0D - 2.0D * t);
        slot.seed = seed;
        slot.cx = cx;
        slot.cz = cz;
        slot.val = w;
        slot.valid = true;
        return w;
    }

    /**
     * 退化盒式：3×3 {@code getBiomeGenForCoords instanceof BiomeSanzuRiver} 计数占比
     * （{@code BlockProsperityTuft.colorMultiplier} 3×3 邻域均值 tint 同款；原二值口径 =
     * 本式在"整 3×3 同群系"列上的特例）。
     */
    private static double boxWeight(IBlockAccess world, int x, int z) {
        int sanzu = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (world.getBiomeGenForCoords(x + dx, z + dz) instanceof BiomeSanzuRiver) {
                    sanzu++;
                }
            }
        }
        return sanzu / 9.0D;
    }

    /** {@code lerp(COLOR_NORMAL, COLOR_SANZU, w)}（通道整型 + Math.round；端点单源在 BlockAbyssalFluid）。 */
    private static int lerpColor(double w) {
        final int nr = BlockAbyssalFluid.COLOR_NORMAL >> 16 & 255;
        final int ng = BlockAbyssalFluid.COLOR_NORMAL >> 8 & 255;
        final int nb = BlockAbyssalFluid.COLOR_NORMAL & 255;
        final int sr = BlockAbyssalFluid.COLOR_SANZU >> 16 & 255;
        final int sg = BlockAbyssalFluid.COLOR_SANZU >> 8 & 255;
        final int sb = BlockAbyssalFluid.COLOR_SANZU & 255;
        return (Math.round((float) (nr + (sr - nr) * w)) & 255) << 16
            | (Math.round((float) (ng + (sg - ng) * w)) & 255) << 8
            | Math.round((float) (nb + (sb - nb) * w)) & 255;
    }

    /**
     * (seed,粗格) → 槽下标（{@code GTSRVoronoiRiverField.slotIndex} 同式 splitmix 终混取高位；
     * 正确性只靠全键精确比较，散列仅决定淘汰分布）。
     */
    private static int slotIndex(long seed, int cx, int cz) {
        long k = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        k ^= k >>> 33;
        k *= 0xFF51AFD7ED558CCDL;
        k ^= k >>> 33;
        return (int) (k >>> 40) & CACHE_MASK;
    }

    private static CellDblSlot[] newSlots() {
        final CellDblSlot[] slots = new CellDblSlot[CACHE_CAP];
        for (int i = 0; i < CACHE_CAP; i++) {
            slots[i] = new CellDblSlot();
        }
        return slots;
    }

    /** 粗格双精度槽（{@code ProsperityTerrainProfile.CellDblSlot} 同构）。 */
    private static final class CellDblSlot {

        long seed;
        int cx;
        int cz;
        boolean valid;
        double val;
    }
}
