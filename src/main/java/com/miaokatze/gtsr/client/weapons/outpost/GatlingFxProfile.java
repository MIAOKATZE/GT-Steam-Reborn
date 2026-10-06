package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
public final class GatlingFxProfile {

    public static final float EJECT_SPREAD_DEG = 10.0F;
    public static final float FLASH_CORE_SCALE = 0.30F;
    public static final float FLASH_HALO_MAX = 1.2F;
    public static final float FLASH_HALO_MIN = 0.9F;
    public static final int FLASH_LIFE_MAX = 4;
    public static final int FLASH_LIFE_MIN = 2;
    public static final float FLASH_SCALE_JITTER_MAX = 1.2F;
    public static final float FLASH_SCALE_JITTER_MIN = 0.85F;
    public static final float MOTOR_OMEGA_MAX = 45.0F;
    public static final float MOTOR_RANGE = 24.0F;
    public static final float MOTOR_STOP_OMEGA = 0.5F;
    public static final float MUZZLESMOKE_ALPHA0 = 0.55F;
    public static final float MUZZLESMOKE_B = 0.73F;
    public static final float MUZZLESMOKE_BACKSUCK = 0.035F;
    public static final float MUZZLESMOKE_DRIFT = 0.02F;
    public static final float MUZZLESMOKE_G = 0.72F;
    public static final float MUZZLESMOKE_JITTER = 0.02F;
    public static final int MUZZLESMOKE_LIFE_MAX = 40;
    public static final int MUZZLESMOKE_LIFE_MIN = 24;
    public static final float MUZZLESMOKE_R = 0.72F;
    public static final float MUZZLESMOKE_RISE = 0.006F;
    public static final float MUZZLESMOKE_SCALE0 = 0.06F;
    public static final float MUZZLESMOKE_SCALE1 = 0.22F;
    public static final float MUZZLESMOKE_SPAWN_FORWARD = 0.06F;
    public static final float MUZZLESMOKE_SWELL_FACTOR = 1.3F;
    public static final float MUZZLESMOKE_SWELL_START = 0.72F;
    public static final int SMOKE_CAP = 600;
    public static final String SND_BULLET_WHIZZ = "gtsr:weapons.bullet_whizz";
    public static final String SND_MOTOR = "gtsr:weapons.gatling_motor";
    public static final float TRAIL_DRIFT = 0.01F;
    public static final float TRAIL_RISE = 0.004F;
    public static final float TRAIL_STREAK_ALPHA0 = 0.80F;
    public static final float TRAIL_STREAK_B = 0.52F;
    public static final float TRAIL_STREAK_G = 0.58F;
    public static final int TRAIL_STREAK_LIFE_TICKS = 12;
    public static final float TRAIL_STREAK_MAX_LENGTH = 4.0F;
    public static final float TRAIL_STREAK_R = 0.62F;
    public static final float TRAIL_STREAK_WIDTH0 = 0.225F;
    public static final float TRAIL_STREAK_WIDTH1 = 0.3375F;
    public static final float WHIZZ_PITCH_MAX = 1.3F;
    public static final float WHIZZ_PITCH_MIN = 0.9F;
    public static final double WHIZZ_TRIGGER_DIST = 6.0D;
    public static final float WHIZZ_VOL = 0.35F;

    public static float clamp01(float v) {
        return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float puffScaleWithSwell(float phase, float scale0, float scale1) {
        if (phase <= MUZZLESMOKE_SWELL_START) {
            return lerp(scale0, scale1, phase / MUZZLESMOKE_SWELL_START);
        }
        float swellT = (phase - MUZZLESMOKE_SWELL_START) / (1.0F - MUZZLESMOKE_SWELL_START);
        return lerp(scale1, scale1 * MUZZLESMOKE_SWELL_FACTOR, swellT);
    }

    public static float motorPitch(float omegaDegPerTick) {
        return 0.8F + 1.2F * clamp01(omegaDegPerTick / MOTOR_OMEGA_MAX);
    }

    public static float motorVolume(float omegaDegPerTick) {
        return 0.125F + 0.275F * clamp01(omegaDegPerTick / MOTOR_OMEGA_MAX);
    }
}
