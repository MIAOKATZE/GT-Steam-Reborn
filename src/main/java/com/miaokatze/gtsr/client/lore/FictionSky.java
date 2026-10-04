package com.miaokatze.gtsr.client.lore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.FictionNetwork;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** World-instance binding and a short heartbeat lease prevent a stale purple sky after disconnect. */
@SideOnly(Side.CLIENT)
public final class FictionSky {

    private static WorldClient bound;
    private static int phase;
    private static long serial = -1, expires;

    private FictionSky() {}

    public static void receive(final FictionNetwork.Sky message) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient receiving = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (receiving == null || receiving != mc.theWorld
                    || mc.thePlayer == null
                    || message.dimension != mc.thePlayer.dimension
                    || !message.player.equals(mc.thePlayer.getUniqueID())) return;
                if (bound != receiving) {
                    bound = receiving;
                    serial = -1;
                }
                if (message.serial < serial) return;
                phase = message.phase;
                serial = message.serial;
                expires = System.currentTimeMillis() + 3000;
            }
        });
    }

    public static Vec3 color(World world) {
        if (world != bound || phase == 0) return null;
        if (phase == 4 || System.currentTimeMillis() > expires) return Vec3.createVectorHelper(.35, .65, .95);
        return Vec3.createVectorHelper(.48, .18, .68);
    }
}
