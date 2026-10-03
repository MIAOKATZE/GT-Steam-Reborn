package com.miaokatze.gtsr.client.lore;

import java.util.ArrayDeque;
import java.util.Queue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Main-thread S2C titles, scoped to the receiving world and a bounded presentation queue. */
@SideOnly(Side.CLIENT)
public final class StructureTitleOverlay {

    private static final Queue<String> QUEUE = new ArrayDeque<>();
    private static WorldClient world;
    private static String current;
    private static long expires;
    private static boolean registered;

    private StructureTitleOverlay() {}

    public static void show(WorldClient receivingWorld, String key) {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new StructureTitleOverlay());
            registered = true;
        }
        if (world != receivingWorld) {
            QUEUE.clear();
            current = null;
            world = receivingWorld;
        }
        if (QUEUE.size() < 8) QUEUE.add(key);
    }

    @SubscribeEvent
    public void draw(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.theWorld != world || mc.thePlayer == null) {
            QUEUE.clear();
            current = null;
            world = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (current == null || now >= expires) {
            current = QUEUE.poll();
            expires = now + 3500;
        }
        if (current == null) return;
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        String title = StatCollector.translateToLocal(current);
        float scale = Math
            .min(2.5F, (resolution.getScaledWidth() - 24F) / Math.max(1, mc.fontRenderer.getStringWidth(title)));
        GL11.glPushMatrix();
        GL11.glTranslatef(resolution.getScaledWidth() / 2F, resolution.getScaledHeight() / 3F, 0);
        GL11.glScalef(scale, scale, 1);
        mc.fontRenderer.drawStringWithShadow(title, -mc.fontRenderer.getStringWidth(title) / 2, 0, 0xFFE7CF);
        GL11.glPopMatrix();
    }
}
