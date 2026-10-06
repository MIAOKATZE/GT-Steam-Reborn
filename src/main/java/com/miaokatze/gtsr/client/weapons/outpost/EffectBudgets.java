package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

import java.util.Arrays;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * client/effect 特效族<b>预算常量唯一落点</b>（对齐 {@code GatlingFxProfile#SMOKE_CAP}=600
 * 的预算思想：各通道独立活体上限 + 满员静默放弃，调用方无需处理）。特效专属观感档位仍留
 * 各特效类，<b>预算数值只改本文件</b>。
 *
 * <p>
 * <b>租约预算（{@link LeaseBudget}）</b>：粒子型特效（导弹尾烟/爆炸烟团）最终落为
 * SmokePuffEntity，由既有 SMOKE_CAP 全局门兜底；但通道自身需要独立上限防止挤占其他
 * 烟团消费方——采用<b>租约模型</b>：spawn 前 {@code acquire(nowTick, leaseTicks)} 按粒子
 * 寿命预扣 1 个名额，到期由环形桶<b>惰性</b>自动归还（不依赖实体 setDead 回调，纯计数、
 * 均摊 O(1)、零稳态分配）。{@code nowTick} 必须传客户端主线程单调时钟
 * {@code World#getTotalWorldTime()}——多发射器并发调用同一预算实例也正确（ drains 按
 * nowTick 推进，幂等）。租约长度=粒子寿命（SmokePuffEntity 到龄即死），账面误差为 0。
 *
 * <p>
 * <b>接线状态（第十二轮 A4 需求 1/4 起＝两通道已接线，第三条仍预置）</b>：
 * <ul>
 * <li>{@link #TRAIL}＝{@code MissileTrailEmitter}（经 {@code ProjectileTrailFx} 承载火箭/导弹
 * 白烟两族与长杆弹电磁弧）——<b>已有真实消费方</b>；</li>
 * <li>{@link #BLAST}＝{@code MultiMuzzleFireFx#spawnPuff}（重炮/火箭发射烟与火球，A4 新通道）
 * ——<b>已有真实消费方</b>；</li>
 * <li>{@link #EXPLOSION}＝{@code ExplosionEffectFactory} 的爆炸子效果——本批<b>仍零运行期
 * 消费者</b>：唯一 acquire 点在爆炸闪光/烟簇内，而组合入口 {@code spawnExplosion} 至今无调用方
 * （属命中/爆炸域，A4 需求 4 不越界接线）。注意 {@code ExplosionEffectFactory#registerBlastRing}
 * （A4 新入口，由 {@code MultiMuzzleFireFx} 挂炮口超压环）只登记环、<b>不</b>占本通道，
 * 故不得把「环已生效」读成「EXPLOSION 已接线」。</li>
 * </ul>
 * 三通道本体保持纯逻辑可单测（无 Minecraft 引用）。<b>Client-only</b>：仅设计于客户端渲染线程消费
 * （单线程假设，无锁）。换维/登出清态已由 {@code GatlingClientFxManager} 的换维分支调
 * {@link #resetAll()}（A4 需求 4 接线，对齐该处 SMOKE_CAP 归零纪律），否则残租约占满预算
 * （有界：≤cap，且随 maxLeaseTicks 推进自然排空）。
 *
 * @since v0.1.29
 */
@SideOnly(Side.CLIENT)
public final class EffectBudgets {

    // ------------------------------------------------------------------
    // 预算常量（集中落点；调预算只改这里）
    // ------------------------------------------------------------------

    /**
     * 导弹尾烟通道独立活体上限（粒）。预核算式（对照 SMOKE_CAP javadoc 口径）：单弹在飞
     * 租约峰值 = ceil(飞行 tick / intervalTicks) 粒且 ≤ lifeTicks/interval 倍稳态，按
     * interval=1t、飞行 100t、寿命 30t 计稳态 ≈ 30 粒/弹 → cap=400 可容 ≥8 弹并发满速
     * 拖尾，与 SMOKE_CAP=600 独立不互挤。
     * <p>
     * <b>round16 D-d 起弹道视觉读数走积分器，预算口径不变</b>：消费方（{@code ProjectileTrailFx}
     * 的白烟两族与电磁 beam）的弹体 pos/vel 采样源已切到 {@code ProjectileVisualTracker} 视觉态，
     * 但采样节律仍=弹体客户端每拍一次、租约/上限/退款机制与消费方调用面零改动——本通道账面
     * 不因数据源切换而重算。
     */
    public static final int TRAIL_CAP = 400;

    /** 尾烟租约最大寿命（tick）：超额 acquire 按此截断（环形桶槽位上界） */
    public static final int TRAIL_MAX_LEASE_TICKS = 128;

    /** 爆炸烟团/闪光通道独立活体上限（粒）：最重档 STRATEGIC 单次 ≤35 租约（32 烟+3 闪光），可容 ~5 次并发 */
    public static final int EXPLOSION_CAP = 200;

    /** 爆炸租约最大寿命（tick）：覆盖最大档烟团寿命带上界（140t）+ 裕量 */
    public static final int EXPLOSION_MAX_LEASE_TICKS = 160;

    /**
     * 炮口发射烟通道独立活体上限（粒；第十二轮 A4 需求 1/4 新增）。
     * <p>
     * <b>WHY 新通道而不是蹭 {@link #TRAIL} 或只靠全 mod 的 {@code SMOKE_CAP}</b>：重炮/火箭
     * 的炮口烟是「每条开火事件一小簇」的瞬态，TRAIL 是「每在飞弹体逐 tick」的长流——同一条
     * 通道时，HT-1 一轮 16 发 + WM-80 八管 + HQ-10 二十二发的齐射会把 TRAIL 账面瞬时吃满，
     * 反过来把在飞弹体的尾烟饿掉（两族观感权重明显不等）。
     * <b>预核算式</b>（口径同 {@code GatlingFxProfile#SMOKE_CAP} javadoc）：最重单机齐射
     * =HT-1 16 发 × 每发 2 粒 = <b>32</b> 租约，寿命带 ≤48t ⇒ 齐射瞬时峰值 32（round16 C-1
     * 把 HT-1 齐射节奏 12t→30t/发拉长 2.5 倍=480t 满夹窗，峰值不变、同拍堆积概率更低，
     * 账面维持不重算）；
     * 四台重炮同屏齐射 ≈128 ≤ 本 cap 240，且距 SMOKE_CAP=600 仍留 360 给现役曳光/停射余烟
     * （本通道的每一粒同时受 SMOKE_CAP 二级门约束，满员即 refund，见
     * {@code MultiMuzzleFireFx#spawnPuff}）。
     */
    public static final int BLAST_CAP = 240;

    /** 发射烟租约最大寿命（tick；盖住本通道最重档寿命带上界 + 裕量） */
    public static final int BLAST_MAX_LEASE_TICKS = 96;

    /** 冲击波环并发上限（环；超出挤掉最旧环——同一爆炸重复触发不无限堆积） */
    public static final int SHOCKWAVE_MAX_RINGS = 8;

    /** 镜头震动并发源上限（源；超出先清过期、仍满则替换最旧） */
    public static final int SHAKE_MAX_SOURCES = 32;

    /**
     * 屏幕模糊覆盖层 alpha 硬上限（0..1）：沿用 GTIT 渐白 overlay 封顶 0.8 的先例
     * （ascension-client-presentation §6.2：保留 HUD 文字可读性）。
     */
    public static final float SCREEN_BLUR_MAX_ALPHA = 0.8F;

    // ------------------------------------------------------------------
    // 预算实例（通道唯一）
    // ------------------------------------------------------------------

    /** 导弹尾烟通道预算（{@link MissileTrailEmitter} 消费） */
    public static final LeaseBudget TRAIL = new LeaseBudget(TRAIL_CAP, TRAIL_MAX_LEASE_TICKS);

    /** 爆炸烟团/闪光通道预算（{@link ExplosionEffectFactory} 消费） */
    public static final LeaseBudget EXPLOSION = new LeaseBudget(EXPLOSION_CAP, EXPLOSION_MAX_LEASE_TICKS);

    /**
     * 炮口发射烟通道预算（第十二轮 A4 需求 1；消费方={@code client/render/MultiMuzzleFireFx}
     * 的 flash/smoke-only 形态——重炮/火箭/能量塔的开火烟全走本通道，与 {@link #TRAIL}
     * （在飞尾烟）和 {@code SMOKE_CAP}（全 mod 兜底）三级独立）。
     */
    public static final LeaseBudget BLAST = new LeaseBudget(BLAST_CAP, BLAST_MAX_LEASE_TICKS);

    private EffectBudgets() {}

    /** 换维/登出兜底：三通道预算全清（接线点={@code GatlingClientFxManager} 换维分支，A4 需求 4） */
    public static void resetAll() {
        TRAIL.reset();
        EXPLOSION.reset();
        BLAST.reset();
    }

    /**
     * 租约预算桶（通道级活体计数 + 环形到期桶）。<b>非线程安全</b>：约定仅客户端主线程访问。
     *
     * <p>
     * 槽位语义：{@code acquire(now, L)} 在槽 {@code (now+L) % slots} 记 1；推进到
     * {@code now' = now+L} 时惰性 drain 该槽归还名额。{@code L} 钳制到 {@code [1, slots-1]}，
     * 保证槽位写入后至多 {@code slots-1} 个 tick 内被 drain，不会被同槽新写入抢先覆盖
     * （同槽累计计数，drain 一次性归还整槽）。
     */
    public static final class LeaseBudget {

        private final int cap;
        private final int[] expirySlots;
        private final int slots;
        private long lastDrainedTick;
        private boolean initialized;
        private int live;

        /** @param cap 活体上限（粒）；@param maxLeaseTicks 最大租约长度（tick，= 槽位数-1） */
        public LeaseBudget(int cap, int maxLeaseTicks) {
            this.cap = Math.max(1, cap);
            this.slots = Math.max(2, maxLeaseTicks + 1);
            this.expirySlots = new int[this.slots];
        }

        /**
         * 预扣 1 个租约（spawn 前调用）：满员返回 false（静默放弃，对齐 SMOKE_CAP 语义）。
         * 首次调用或 nowTick 回跳（换维后世界时钟重置）时全桶重置。
         *
         * @param nowTick    客户端主线程单调时钟（World#getTotalWorldTime()）
         * @param leaseTicks 租约长度（tick；= 粒子寿命，钳制 [1, maxLeaseTicks]）
         */
        public boolean acquire(long nowTick, int leaseTicks) {
            drain(nowTick);
            if (this.live >= this.cap) {
                return false;
            }
            int lease = Math.max(1, Math.min(leaseTicks, this.slots - 1));
            int slot = (int) ((nowTick + lease) % this.slots);
            this.expirySlots[slot]++;
            this.live++;
            return true;
        }

        /**
         * 精确退款（acquire 成功但底层 spawn 失败时调用，如 SMOKE_CAP 全局门满员）：
         * 撤销同参数 {@code acquire(nowTick, leaseTicks)} 刚记入的那 1 个租约（同槽计数 -1）。
         */
        public void refund(long nowTick, int leaseTicks) {
            int lease = Math.max(1, Math.min(leaseTicks, this.slots - 1));
            int slot = (int) ((nowTick + lease) % this.slots);
            if (this.expirySlots[slot] > 0) {
                this.expirySlots[slot]--;
                this.live = Math.max(0, this.live - 1);
            }
        }

        /** 当前活体租约数（监控/自证用） */
        public int live() {
            return this.live;
        }

        /** 预算上限（粒） */
        public int cap() {
            return this.cap;
        }

        /** 全清（换维/登出兜底；残留实体随 world 清理，账面归零即可） */
        public void reset() {
            Arrays.fill(this.expirySlots, 0);
            this.lastDrainedTick = 0;
            this.initialized = false;
            this.live = 0;
        }

        /**
         * 惰性 drain：按 nowTick 推进逐槽归还到期租约。delta ≥ slots 视为全过期一次清空
         * （长暂停/换维后不逐 tick 补扫）。
         */
        private void drain(long nowTick) {
            if (!this.initialized || nowTick < this.lastDrainedTick) {
                this.lastDrainedTick = nowTick;
                this.initialized = true;
                return; // 首次/时钟回跳：桶已是干净状态（reset 后或新实例）
            }
            long delta = nowTick - this.lastDrainedTick;
            if (delta == 0) {
                return; // 同 tick 并发 acquire：幂等
            }
            if (delta >= this.slots) {
                Arrays.fill(this.expirySlots, 0);
                this.live = 0;
            } else {
                for (long d = 1; d <= delta; d++) {
                    int slot = (int) ((this.lastDrainedTick + d) % this.slots);
                    this.live -= this.expirySlots[slot];
                    this.expirySlots[slot] = 0;
                }
                this.live = Math.max(0, this.live);
            }
            this.lastDrainedTick = nowTick;
        }
    }
}
