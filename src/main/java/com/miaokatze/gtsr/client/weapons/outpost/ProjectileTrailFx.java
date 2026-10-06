package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public final class ProjectileTrailFx {

    private static final Map<Integer, Long> WHIZZ_PLAYED = new HashMap<>();

    public static void reset() {
        WHIZZ_PLAYED.clear();
    }

    public static void tick(Entity proj) {
        World world = proj.worldObj;
        double segX = proj.posX - proj.prevPosX, segY = proj.posY - proj.prevPosY, segZ = proj.posZ - proj.prevPosZ;
        double segLen = Math.sqrt(segX * segX + segY * segY + segZ * segZ);
        if (segLen < 1e-4) return;
        // === TRACER：曳光段烟（以下逐语句为改前原样，仅键门由集合改为本档） ===
        double inv = 1.0D / segLen;
        double driftX = (world.rand.nextDouble() - 0.5D) * 2.0D * GatlingFxProfile.TRAIL_DRIFT;
        double driftZ = (world.rand.nextDouble() - 0.5D) * 2.0D * GatlingFxProfile.TRAIL_DRIFT;
        SmokePuffEntity.spawnStreak(
            world,
            (proj.prevPosX + proj.posX) * 0.5D,
            (proj.prevPosY + proj.posY) * 0.5D,
            (proj.prevPosZ + proj.posZ) * 0.5D,
            (float) (segX * inv),
            (float) (segY * inv),
            (float) (segZ * inv),
            (float) Math.min(segLen, GatlingFxProfile.TRAIL_STREAK_MAX_LENGTH),
            driftX,
            GatlingFxProfile.TRAIL_RISE,
            driftZ,
            GatlingFxProfile.TRAIL_STREAK_WIDTH0,
            GatlingFxProfile.TRAIL_STREAK_WIDTH1,
            GatlingFxProfile.TRAIL_STREAK_ALPHA0,
            GatlingFxProfile.TRAIL_STREAK_LIFE_TICKS,
            GatlingFxProfile.TRAIL_STREAK_R,
            GatlingFxProfile.TRAIL_STREAK_G,
            GatlingFxProfile.TRAIL_STREAK_B);
        playWhizzOnceNearPlayer(proj, world);
    }

    private static void playWhizzOnceNearPlayer(Entity slug, World world) {
        try {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return; // thePlayer 空守卫（标题屏/重生成窗口）
            }
            double dx = player.posX - slug.posX;
            double dy = player.posY - slug.posY;
            double dz = player.posZ - slug.posZ;
            if (dx * dx + dy * dy + dz * dz
                >= GatlingFxProfile.WHIZZ_TRIGGER_DIST * GatlingFxProfile.WHIZZ_TRIGGER_DIST) {
                return;
            }
            long now = world.getTotalWorldTime();
            Integer id = Integer.valueOf(slug.getEntityId());
            if (WHIZZ_PLAYED.containsKey(id)) {
                return; // 每实体一次
            }
            if (WHIZZ_PLAYED.size() >= 256) WHIZZ_PLAYED.clear();
            WHIZZ_PLAYED.put(id, now);
            world.playSound(
                slug.posX,
                slug.posY,
                slug.posZ,
                GatlingFxProfile.SND_BULLET_WHIZZ,
                GatlingFxProfile.WHIZZ_VOL,
                GatlingFxProfile.WHIZZ_PITCH_MIN
                    + world.rand.nextFloat() * (GatlingFxProfile.WHIZZ_PITCH_MAX - GatlingFxProfile.WHIZZ_PITCH_MIN),
                false);
        } catch (Throwable t) {
            /* Sound failure must not stop trails. */ // 破空声失败不拖垮轨迹（独立捕获，轨迹已 spawn 完毕）
        }
    }
}
