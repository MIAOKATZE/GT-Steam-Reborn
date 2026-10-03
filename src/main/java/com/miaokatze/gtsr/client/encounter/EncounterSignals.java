package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EncounterNetwork;
import com.miaokatze.gtsr.common.fx.GTSRGlowFX;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class EncounterSignals {

    private static final SceneBossCache SCENE = new SceneBossCache();
    private static WorldClient world;
    private static int progressTicks, guideTicks, remaining, entity = -1;
    private static double x, y, z;

    private EncounterSignals() {}

    public static void receive(final EncounterNetwork.Signal s) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient received = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (mc.theWorld == null || mc.theWorld != received
                    || mc.thePlayer == null
                    || mc.thePlayer.dimension != s.dimension
                    || !mc.thePlayer.getUniqueID()
                        .equals(s.player))
                    return;
                if (world != received) clear();
                world = received;
                if (s.kind == 0) {
                    progressTicks = s.active ? 60 : 0;
                    remaining = s.remaining;
                } else {
                    guideTicks = 60;
                    entity = s.entity;
                    x = s.x;
                    y = s.y;
                    z = s.z;
                }
            }
        });
    }

    public static void receiveScene(final com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal s) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient received = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (received == null || mc.theWorld != received || mc.thePlayer == null) return;
                if (world != received) clear();
                world = received;
                SCENE.receive(received, mc.thePlayer.dimension, mc.thePlayer.getUniqueID(), s);
            }
        });
    }

    public static com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal scene() {
        return SCENE.get(Minecraft.getMinecraft().theWorld);
    }

    public static boolean progressActive() {
        return progressTicks > 0;
    }

    public static int remaining() {
        return remaining;
    }

    public static void clear() {
        SCENE.clear();
        world = null;
        progressTicks = guideTicks = 0;
        entity = -1;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.theWorld != world || mc.thePlayer == null) {
            clear();
            return;
        }
        SCENE.tick(mc.theWorld, mc.thePlayer.dimension, mc.thePlayer.getUniqueID());
        if (progressTicks > 0) progressTicks--;
        if (guideTicks <= 0) return;
        Entity target = entity < 0 ? null : world.getEntityByID(entity);
        if (target != null) {
            if (target.isDead || target instanceof EntityLivingBase && ((EntityLivingBase) target).getHealth() <= 0) {
                guideTicks = 0;
                return;
            }
            x = target.posX;
            y = target.posY + target.height * .6;
            z = target.posZ;
        }
        if (--guideTicks % 4 != 0) return;
        double ox = mc.thePlayer.posX, oy = mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), oz = mc.thePlayer.posZ;
        double dx = x - ox, dy = y - oy, dz = z - oz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .01) return;
        double limit = Math.min(32, length);
        for (double d = .6; d <= limit; d += .6) {
            GTSRGlowFX.spawn(
                world,
                ox + dx * d / length,
                oy + dy * d / length,
                oz + dz * d / length,
                .10F,
                .72F,
                .90F,
                .36F,
                10);
        }
    }
}
