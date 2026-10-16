package com.miaokatze.gtsr.client.critical;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.critical.CriticalNightClock;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class CriticalNightClient {

    private CriticalNightClient() {}

    public static void drain(ConcurrentLinkedQueue<CriticalNightClock.TimeMessage> queue) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;
        CriticalNightClock.TimeMessage message;
        CriticalNightClock.TimeMessage latest = null;
        while ((message = queue.poll()) != null)
            if (message.dimension == world.provider.dimensionId && message.clientConnection == Minecraft.getMinecraft()
                .getNetHandler()) latest = message;
        if (latest != null) CriticalNightClock.receiveClient(world, latest);
    }
}
