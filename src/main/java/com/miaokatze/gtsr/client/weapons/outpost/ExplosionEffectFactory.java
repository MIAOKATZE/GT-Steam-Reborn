package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import java.util.Random;

import net.minecraft.world.World;

public final class ExplosionEffectFactory {

    private static final Random RNG = new Random();
    public static final int FLASH_PUFFS = 3;
    public static final double FLASH_JITTER = .25;

    public static void spawnExplosion(World w, double x, double y, double z, boolean debris) {
        if (w == null || !w.isRemote) return;
        long now = w.getTotalWorldTime();
        ExplosionScale s = ExplosionScale.SMALL;
        spawnFlash(w, x, y, z, s, now);
        spawnSmokeCluster(w, x, y, z, s, now);
        if (debris) spawnDebris(w, x, y, z, s);
    }

    public enum ExplosionScale {

        /** 小口径（迫击炮/单弹头）：直径 ~2 格闪光、烟柱 ~4 格 */
        SMALL(1.8F, 4, 8, 0.8F, 2.2F, 40, 70, 6.0F, 12, 12, 0.35D, 1.2F, 8),
        /** 中口径（火箭/巡航导弹） */
        MEDIUM(2.6F, 5, 14, 1.2F, 3.2F, 50, 90, 10.0F, 16, 24, 0.45D, 2.0F, 10),
        /** 大口径（重弹/弹药库殉爆） */
        LARGE(3.6F, 6, 22, 1.6F, 4.2F, 60, 110, 16.0F, 20, 40, 0.55D, 3.0F, 14),
        /** 战略级（超重型弹头演出档） */
        STRATEGIC(5.0F, 8, 32, 2.2F, 5.5F, 80, 140, 24.0F, 26, 64, 0.70D, 4.5F, 18);

        /** 闪光粒径（格；出生即该尺寸，随寿命略放大） */
        public final float flashScaleBlocks;
        /** 闪光寿命（tick；快衰减档） */
        public final int flashLifeTicks;
        /** 烟团粒数 */
        public final int smokePuffCount;
        /** 烟团初粒径（格） */
        public final float smokeScale0Blocks;
        /** 烟团末粒径（格） */
        public final float smokeScale1Blocks;
        /** 烟团寿命随机带下界（tick） */
        public final int smokeLifeMinTicks;
        /** 烟团寿命随机带上界（tick） */
        public final int smokeLifeMaxTicks;
        /** 冲击波环最大半径（格） */
        public final float ringMaxRadiusBlocks;
        /** 冲击波环外扩时长（tick；走完即移除） */
        public final int ringDurationTicks;
        /** 破片粒子数 */
        public final int debrisCount;
        /** 破片初速（格/tick；随机方向球面） */
        public final double debrisSpeedBlocksPerTick;
        /** 镜头震动强度（deg；世界锚定源，随听者距离衰减） */
        public final float shakeIntensityDeg;
        /** 镜头震动时长（tick） */
        public final int shakeDurationTicks;

        ExplosionScale(float flashScaleBlocks, int flashLifeTicks, int smokePuffCount, float smokeScale0Blocks,
            float smokeScale1Blocks, int smokeLifeMinTicks, int smokeLifeMaxTicks, float ringMaxRadiusBlocks,
            int ringDurationTicks, int debrisCount, double debrisSpeedBlocksPerTick, float shakeIntensityDeg,
            int shakeDurationTicks) {
            this.flashScaleBlocks = flashScaleBlocks;
            this.flashLifeTicks = flashLifeTicks;
            this.smokePuffCount = smokePuffCount;
            this.smokeScale0Blocks = smokeScale0Blocks;
            this.smokeScale1Blocks = smokeScale1Blocks;
            this.smokeLifeMinTicks = smokeLifeMinTicks;
            this.smokeLifeMaxTicks = smokeLifeMaxTicks;
            this.ringMaxRadiusBlocks = ringMaxRadiusBlocks;
            this.ringDurationTicks = ringDurationTicks;
            this.debrisCount = debrisCount;
            this.debrisSpeedBlocksPerTick = debrisSpeedBlocksPerTick;
            this.shakeIntensityDeg = shakeIntensityDeg;
            this.shakeDurationTicks = shakeDurationTicks;
        }
    }

    static boolean spawnFlash(World world, double x, double y, double z, ExplosionScale scale, long nowTick) {
        boolean any = false;
        for (int i = 0; i < FLASH_PUFFS; i++) {
            float[] color = (i % 2 == 0) ? EffectPalette.FLASH_CORE : EffectPalette.FLASH_EDGE;
            int life = Math.max(1, scale.flashLifeTicks - i); // 外围粒略短命，衰减更锐
            if (!EffectBudgets.EXPLOSION.acquire(nowTick, life)) {
                break; // 通道预算满员：静默放弃余粒
            }
            double jx = x + (RNG.nextDouble() * 2.0D - 1.0D) * FLASH_JITTER;
            double jy = y + (RNG.nextDouble() * 2.0D - 1.0D) * FLASH_JITTER;
            double jz = z + (RNG.nextDouble() * 2.0D - 1.0D) * FLASH_JITTER;
            boolean spawned = SmokePuffEntity.spawn(
                world,
                jx,
                jy,
                jz,
                0.0D,
                0.005D,
                0.0D,
                scale.flashScaleBlocks,
                scale.flashScaleBlocks * 1.15F,
                0.95F,
                life,
                color[0],
                color[1],
                color[2]);
            if (spawned) {
                any = true;
            } else {
                EffectBudgets.EXPLOSION.refund(nowTick, life); // SMOKE_CAP 全局门满员：退租约
            }
        }
        return any;
    }

    static boolean spawnSmokeCluster(World world, double x, double y, double z, ExplosionScale scale, long nowTick) {
        boolean any = false;
        for (int i = 0; i < scale.smokePuffCount; i++) {
            int life = scale.smokeLifeMinTicks
                + RNG.nextInt(Math.max(1, scale.smokeLifeMaxTicks - scale.smokeLifeMinTicks + 1));
            if (!EffectBudgets.EXPLOSION.acquire(nowTick, life)) {
                break;
            }
            double dx = RNG.nextDouble() * 2.0D - 1.0D;
            double dy = RNG.nextDouble() * 2.0D - 1.0D;
            double dz = RNG.nextDouble() * 2.0D - 1.0D;
            double speed = 0.05D + RNG.nextDouble() * 0.15D; // 格/tick 低速滚涌
            float sizeT = RNG.nextFloat();
            float scale0 = lerp(scale.smokeScale0Blocks, scale.smokeScale1Blocks, sizeT * 0.5F);
            float scale1 = lerp(scale.smokeScale0Blocks, scale.smokeScale1Blocks, 0.5F + sizeT * 0.5F);
            float[] color = EffectPalette.lerpColor(EffectPalette.SMOKE_HOT, EffectPalette.SMOKE_COOL, RNG.nextFloat());
            boolean spawned = SmokePuffEntity.spawn(
                world,
                x + dx * 0.5D,
                y + Math.abs(dy) * 0.5D,
                z + dz * 0.5D,
                dx * speed,
                Math.abs(dy) * speed + 0.02D, // 上抛偏置：热烟升腾
                dz * speed,
                scale0,
                scale1,
                0.55F + RNG.nextFloat() * 0.25F,
                life,
                color[0],
                color[1],
                color[2]);
            if (spawned) {
                any = true;
            } else {
                EffectBudgets.EXPLOSION.refund(nowTick, life);
            }
        }
        return any;
    }

    static boolean spawnDebris(World world, double x, double y, double z, ExplosionScale scale) {
        for (int i = 0; i < scale.debrisCount; i++) {
            double dx = RNG.nextDouble() * 2.0D - 1.0D;
            double dy = RNG.nextDouble(); // 偏上抛
            double dz = RNG.nextDouble() * 2.0D - 1.0D;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-6D) {
                continue;
            }
            double speed = scale.debrisSpeedBlocksPerTick * (0.5D + RNG.nextDouble());
            double vx = dx / len * speed;
            double vy = dy / len * speed;
            double vz = dz / len * speed;
            if (RNG.nextFloat() < 0.7F) {
                world.spawnParticle("crit", x, y, z, vx, vy, vz);
            } else {
                world.spawnParticle("smoke", x, y, z, vx * 0.3D, vy * 0.3D, vz * 0.3D);
            }
        }
        return scale.debrisCount > 0;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
