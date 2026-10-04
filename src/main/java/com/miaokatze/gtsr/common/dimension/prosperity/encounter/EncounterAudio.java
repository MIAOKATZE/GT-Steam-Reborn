package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Server-authoritative finite sound cues; names are mirrored by sounds.json. */
public final class EncounterAudio {

    public static final String KING_CHANT = "gtsr:king.chant";
    public static final String KING_WARNING = "gtsr:king.warning";
    public static final String KING_PHASE = "gtsr:king.phase";
    public static final String KING_CRUSH = "gtsr:king.crush";
    public static final String KING_PULL = "gtsr:king.pull";
    public static final String KING_SILENCE = "gtsr:king.silence";
    public static final String KING_DOMAIN = "gtsr:king.domain";
    public static final String KING_METEOR = "gtsr:king.meteor";
    public static final String KING_IMPACT = "gtsr:king.impact";
    public static final String SPAWNER_SPAWN = "gtsr:spawner.spawn";
    public static final String SPAWNER_UNLOCK = "gtsr:spawner.unlock";
    public static final String SPAWNER_RECHARGE = "gtsr:spawner.recharge";
    public static final String PROJECTILE_EXPLODE = "gtsr:echo.projectile.explode";

    private EncounterAudio() {}

    public static String mob(String profile, String event) {
        return "gtsr:entity." + profile + "." + event;
    }

    public static float pitch(boolean enraged) {
        return enraged ? 1.18F : 1F;
    }

    public static void at(Entity entity, String key, float volume, float pitch) {
        at(entity.worldObj, entity.posX, entity.posY, entity.posZ, key, volume, pitch);
    }

    public static void at(World world, double x, double y, double z, String key, float volume, float pitch) {
        if (world != null && !world.isRemote) {
            world.playSoundEffect(x, y, z, key, volume, pitch);
        }
    }
}
