package com.miaokatze.gtsr.client.lore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Properties;
import java.util.Queue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Main-thread S2C titles, scoped to the receiving world and a bounded presentation queue. */
@SideOnly(Side.CLIENT)
public final class StructureTitleOverlay {

    private static final Queue<String> QUEUE = new ArrayDeque<>();
    private static WorldClient world;
    private static String current;
    private static long expires;
    private static long appears;
    private static EntryMusic music;
    private static final Properties INTRODUCTIONS = new Properties();
    private static boolean registered;

    static {
        try (InputStream in = StructureTitleOverlay.class
            .getResourceAsStream("/assets/gtsr/lore/scene-introductions.properties")) {
            if (in != null) INTRODUCTIONS.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException failed) {
            throw new IllegalStateException("Cannot load scene introductions", failed);
        }
    }

    private StructureTitleOverlay() {}

    public static void show(WorldClient receivingWorld, String key) {
        if (!registered) {
            StructureTitleOverlay owner = new StructureTitleOverlay();
            MinecraftForge.EVENT_BUS.register(owner);
            FMLCommonHandler.instance()
                .bus()
                .register(owner);
            registered = true;
        }
        if (world != receivingWorld) {
            reset();
            world = receivingWorld;
        }
        if (QUEUE.size() < 8) QUEUE.add(key);
    }

    private static void reset() {
        QUEUE.clear();
        current = null;
        if (music != null) {
            music.stop();
            Minecraft.getMinecraft()
                .getSoundHandler()
                .stopSound(music);
            music = null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.theWorld != world || mc.thePlayer == null) {
            reset();
            world = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (current != null && now < expires) return;
        current = QUEUE.poll();
        if (current == null) return;
        if (music != null) mc.getSoundHandler()
            .stopSound(music);
        music = new EntryMusic();
        mc.getSoundHandler()
            .playSound(music);
        appears = now + 450;
        expires = appears + 3500;
    }

    private static final class EntryMusic extends MovingSound {

        EntryMusic() {
            super(new ResourceLocation("gtsr", "scene.entry"));
            volume = 0.8F;
            repeat = false;
            field_147666_i = ISound.AttenuationType.NONE;
        }

        @Override
        public void update() {}

        void stop() {
            donePlaying = true;
        }
    }

    @SubscribeEvent
    public void draw(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.theWorld != world || mc.thePlayer == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (current == null || now < appears || now >= expires) return;
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        String title = StatCollector.translateToLocal(current);
        float scale = Math
            .min(2.5F, (resolution.getScaledWidth() - 24F) / Math.max(1, mc.fontRenderer.getStringWidth(title)));
        GL11.glPushMatrix();
        GL11.glTranslatef(resolution.getScaledWidth() / 2F, resolution.getScaledHeight() / 2F - 12, 0);
        GL11.glScalef(scale, scale, 1);
        mc.fontRenderer.drawStringWithShadow(title, -mc.fontRenderer.getStringWidth(title) / 2, 0, 0xFFE7CF);
        GL11.glPopMatrix();
        String introduction = INTRODUCTIONS.getProperty(current, "旧日的回声仍在此地");
        mc.fontRenderer.drawStringWithShadow(
            introduction,
            (resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(introduction)) / 2,
            resolution.getScaledHeight() / 2 + 17,
            0xD8C8B5);
    }
}
