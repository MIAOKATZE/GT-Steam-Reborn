// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.INetHandler;

import com.miaokatze.gtsr.client.network.ClientRecipientIdentity;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EncounterNetwork;
import com.miaokatze.gtsr.common.fx.GTSRGlowFX;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class EncounterSignals {

    private static final SceneBossCache SCENE = new SceneBossCache();
    private static WorldClient world;
    private static ClientRecipientIdentity.Audience audience;
    private static int progressTicks, guideTicks, remaining, entity = -1;
    private static double x, y, z;

    private EncounterSignals() {}

    public static void receive(final EncounterNetwork.Signal s, INetHandler source) {
        final ClientRecipientIdentity.Audience receivedAudience = ClientRecipientIdentity
            .capture(source, s.dimension, s.player);
        if (receivedAudience == null) return;
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient received = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (!receivedAudience.current()) return;
                if (world != received) clear();
                world = received;
                audience = receivedAudience;
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

    public static void receiveScene(final com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal s,
        INetHandler source) {
        final ClientRecipientIdentity.Audience receivedAudience = ClientRecipientIdentity
            .capture(source, s.dimension, s.player);
        if (receivedAudience == null) return;
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient received = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (!receivedAudience.current()) return;
                if (world != received) clear();
                world = received;
                audience = receivedAudience;
                SCENE.receive(received, mc.thePlayer.dimension, audience.player(), s);
            }
        });
    }

    public static com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal scene() {
        if (audience == null || !audience.current()) {
            clear();
            return null;
        }
        return SCENE.get(Minecraft.getMinecraft().theWorld);
    }

    public static boolean progressActive() {
        return audience != null && audience.current() && progressTicks > 0;
    }

    public static int remaining() {
        return audience != null && audience.current() ? remaining : 0;
    }

    public static void clear() {
        SCENE.clear();
        world = null;
        audience = null;
        progressTicks = guideTicks = 0;
        entity = -1;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (audience == null || !audience.current()) {
            clear();
            return;
        }
        SCENE.tick(mc.theWorld, mc.thePlayer.dimension, audience.player());
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
