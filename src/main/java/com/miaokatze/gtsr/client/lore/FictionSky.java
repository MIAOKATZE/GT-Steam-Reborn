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
    private static boolean tinted;

    public FictionSky() {}

    public static boolean restored(World world) {
        return world == bound && phase == 4;
    }

    public static boolean purple(World world) {
        return world == bound && phase > 0 && phase < 4 && System.currentTimeMillis() <= expires;
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void grass(net.minecraftforge.event.terraingen.BiomeEvent.GetGrassColor event) {
        if (purple(Minecraft.getMinecraft().theWorld)) event.newColor = 0x8962B0;
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void foliage(net.minecraftforge.event.terraingen.BiomeEvent.GetFoliageColor event) {
        if (purple(Minecraft.getMinecraft().theWorld)) event.newColor = 0x79549D;
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void water(net.minecraftforge.event.terraingen.BiomeEvent.GetWaterColor event) {
        if (purple(Minecraft.getMinecraft().theWorld)) event.newColor = 0x8452AF;
    }

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
                boolean wasPurple = purple(receiving), wasRestored = restored(receiving);
                phase = message.phase;
                serial = message.serial;
                expires = System.currentTimeMillis() + 3000;
                boolean nowPurple = purple(receiving);
                if ((wasPurple != nowPurple || tinted != nowPurple || !wasRestored && phase == 4)
                    && mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
                tinted = nowPurple;
            }
        });
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void tick(cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean current = purple(mc.theWorld);
        if (tinted != current) {
            tinted = current;
            if (mc.theWorld != null && mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
        }
    }

    public static Vec3 color(World world) {
        return purple(world) ? Vec3.createVectorHelper(.48, .18, .68) : null;
    }
}
