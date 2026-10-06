package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * client/effect 特效族<b>共享色板</b>（值对象）：烟团/闪光/冲击波/尾烟梯度的 RGB 常量
 * 唯一定义点（0..1 float 三元组），供特效类生成 SmokePuffEntity（client.render 包）时的
 * spawn 入参消费。
 * 色值风格锚定既有 GatlingFxProfile 暖灰口径（TRAIL_STREAK_R/G/B=0.62/0.58/0.52）。
 *
 * <p>
 * <b>只读约定</b>：公开数组为共享实例，消费方<b>只读禁改</b>（同
 * {@code GatlingFxProfile.objModelScale} 共享数组先例；每次调用零分配的写法不适用本类
 * ——插值走 {@link #lerpColor} 返回新数组）。
 *
 * <p>
 * <b>接线状态（第十二轮 A4 需求 1/3/4 起＝已有真实消费方）</b>：{@link #TRAIL_WHITE}/
 * {@link #TRAIL_WHITE_HOT}={@code MissileTrailEmitter} 白色档预设与 {@code ProjectileTrailTiers}
 * 的尾烟色梯度两端，{@link #SMOKE_BLAST_GREY}/{@link #FLASH_CORE}={@code MultiMuzzleFireFx}
 * 的重炮烟柱与白热火球，{@link #EM_ARC}={@code ProjectileTrailFx} 的电磁弧段烟（含
 * {@code ProjectileTrailTiers} 的常量转发）；{@code ExplosionEffectFactory}/
 * {@code MissileTrailEmitter}/{@code TurretObjRenderer} 亦读本类。
 * 本类 13 支公开常量中<b>仍零限定引用</b>的只有两支，且同因：{@link #FLASH_EDGE}（爆炸闪光
 * 边缘档）与 {@link #DEBRIS_SPARK}（破片配色）都随 {@code spawnExplosion} 族待接（该族至今
 * 零调用方）。{@link #WHITE_SMOKE_TEXTURE} 已于 v0.1.68 第十二轮由主代理批次绑定
 * （{@code SmokePuffEntity} 逐实例白档位 ＋ {@code RenderSmokePuff} 二选一绑定），<b>不再是</b>
 * 死常量。<b>Client-only</b>：仅客户端特效路径消费。
 *
 * @since v0.1.29
 */
@SideOnly(Side.CLIENT)
public final class EffectPalette {

    /** 闪光核心·白热（爆炸闪光初相粒子色） */
    public static final float[] FLASH_CORE = { 1.00F, 0.98F, 0.90F };

    /** 闪光边缘·橙白（闪光外围/次相粒子色） */
    public static final float[] FLASH_EDGE = { 1.00F, 0.80F, 0.45F };

    /** 爆炸烟·热灰（烟团初相，带灼烧余温观感） */
    public static final float[] SMOKE_HOT = { 0.42F, 0.40F, 0.38F };

    /** 爆炸烟·冷灰（烟团末相，冷却沉降观感） */
    public static final float[] SMOKE_COOL = { 0.28F, 0.28F, 0.30F };

    /**
     * 发射烟·中性浅灰（第十二轮 A4 需求 1 的重炮炮口烟柱档；比对侧
     * {@code GatlingFxProfile.MUZZLESMOKE_R/G/B=0.72} 略亮半档——身管越长、药量越大，
     * 炮口烟柱读越"厚"，但仍刻意低于 {@link #TRAIL_WHITE} 的 0.93 白档，
     * 使「重炮灰烟」与「火箭白烟」两族在同一画面里可分辨。
     */
    public static final float[] SMOKE_BLAST_GREY = { 0.78F, 0.78F, 0.80F };

    /** 导弹尾烟梯度·冷端（对齐既有曳光烟暖灰档 0.62/0.58/0.52） */
    public static final float[] TRAIL_COLD = { 0.62F, 0.58F, 0.52F };

    /** 导弹尾烟梯度·热端（尾焰灼烧观感，用于刚出喷口段） */
    public static final float[] TRAIL_HOT = { 1.00F, 0.75F, 0.45F };

    /** 破片火花·橙（保留档：当前破片走原版粒子通道不可调色，未来专用破片实体启用） */
    public static final float[] DEBRIS_SPARK = { 1.00F, 0.62F, 0.25F };

    /** 冲击波环·冷青白（世界空间环几何线色） */
    public static final float[] SHOCKWAVE = { 0.85F, 0.93F, 1.00F };

    // ------------------------------------------------------------------
    // 第十二轮 A4 需求 3/4 新增档：浓重<b>白</b>烟与电磁弧配色
    // （改前本包最白的档是 FLASH_CORE=1.00/0.98/0.90，是「白热闪光」而非「白烟」——
    // 它带暖黄底且在曳光暖灰档 TRAIL_COLD 之后没有第二支烟色，故火箭/导弹的浓重白烟
    // 在改前无处可取档，实测全仓唯一烟图 textures/fx/smoke_soft.png 亦为中性偏暖灰。）
    // ------------------------------------------------------------------

    /**
     * 浓重白烟·主档（0..1；<b>中性偏冷</b>白，R≈G≈B 且 B 略高半档——火箭尾烟离喷口一段
     * 的观感，刻意与 {@link #TRAIL_COLD}（暖灰 0.62/0.58/0.52，机炮曳光段烟）拉开色温差，
     * 使两族在混战画面里可分辨。消费方={@code ProjectileTrailTiers.NOZZLE_COLOR_COLD}。
     */
    public static final float[] TRAIL_WHITE = { 0.93F, 0.94F, 0.97F };

    /**
     * 浓重白烟·热端（刚出喷口段的白热，偏暖但<b>不加橙</b>——加橙即退回 {@link #TRAIL_HOT}
     * 的尾焰观感，会把「白烟柱」读成「火尾」。消费方={@code MissileTrailEmitter} 白色档预设。
     */
    public static final float[] TRAIL_WHITE_HOT = { 1.00F, 0.97F, 0.92F };

    /**
     * 电磁弧·主档（冷蓝白；与 {@code TurretObjRenderer} 蓄力螺旋的
     * {@code (0.45..0.87, 0.72..0.98, 1.0)} 蓝白系同族，取更亮的一档做「放电」而非「蓄力」）
     */
    public static final float[] EM_ARC = { 0.72F, 0.88F, 1.00F };

    /**
     * 白色烟图档（第十二轮 A4 需求 4；落位 {@code assets/gto/textures/fx/
     * smoke_white_soft.png}，32×32 RGBA、{@code sha256=861770c4…}，与 {@code smoke_soft.png}
     * 同尺寸的柔边圆斑，但实测 <b>RGB 峰值 246/247/250</b>（旧图 190 灰）＋
     * <b>alpha 峰值 233、alpha 均值 74.8</b>（旧图 221 / 26.6）＝更白也更浓）。
     * WHY 需要新图：{@code RenderSmokePuff} 以 {@code glColor4f(r,g,b,alpha)} 调制贴图，
     * 旧图 RGB=190 ⇒ 白色档的合成亮度硬上限 {@code 190/255=0.745}，无论 Java 侧把
     * {@link #TRAIL_WHITE} 调到多高都出不来「浓重白」——上限在贴图上，不在色档上。
     * <p>
     * 绑定状态（v0.1.68 第十二轮闭合）：A4 片落地本图时绑定面 {@code RenderSmokePuff} 与
     * {@code SmokePuffEntity} 都在该片写锁之外，故当时确实<b>零运行期消费者</b>；主代理批次随后
     * 补上逐实例档位（{@code SmokePuffEntity#setWhitePuff}＋{@code spawn}/{@code spawnStreak}
     * 的白档重载）与渲染端二选一绑定，白档消费方＝{@code MissileTrailEmitter.whiteRocketTrail()}
     * 与 {@code nozzleTail()}（F3 轨迹／F4 尾烟）＋{@code MultiMuzzleFireFx} 的 ROCKET 形态离架烟。
     * 本常量仍是路径的<b>单一真值</b>，渲染端只做 {@code ResourceLocation} 包装。
     */
    public static final String WHITE_SMOKE_TEXTURE = "textures/fx/smoke_white_soft.png";

    private EffectPalette() {}

    /**
     * 纯函数：RGB 线性插值（逐分量 lerp + 钳制 0..1）。
     *
     * @param a 冷端色（长度 3；null/越界按黑处理）
     * @param b 热端色（长度 3；null/越界按黑处理）
     * @param t 插值系数（0..1，越界钳制）
     * @return 新数组 {r,g,b}（不共享、可安全持有）
     */
    public static float[] lerpColor(float[] a, float[] b, float t) {
        float clamped = clamp01(t);
        return new float[] { lerpComponent(a, 0, b, 0, clamped), lerpComponent(a, 1, b, 1, clamped),
            lerpComponent(a, 2, b, 2, clamped) };
    }

    private static float lerpComponent(float[] a, int ia, float[] b, int ib, float t) {
        float va = (a != null && a.length > ia) ? a[ia] : 0.0F;
        float vb = (b != null && b.length > ib) ? b[ib] : 0.0F;
        float v = va + (vb - va) * t;
        return clamp01(v);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
    }
}
