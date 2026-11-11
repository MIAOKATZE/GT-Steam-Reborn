// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.critical;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.critical.CriticalDamageHighlights;
import com.miaokatze.miaokatzecore.client.render.GlScope;
import com.miaokatze.miaokatzecore.client.render.ScreenLineStream;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Critical-specific world markers. Core owns only the reusable line submission, never their lifetime or selection. */
@SideOnly(Side.CLIENT)
public final class CriticalDamageHighlightClient {

    private static final CriticalDamageHighlightClient INSTANCE = new CriticalDamageHighlightClient();
    private static final AtomicLong EPOCH = new AtomicLong();
    private World world;
    private Object activeConnection;
    private long latestSession;
    private int remaining, knownTotal;
    private boolean scanPending;
    private int[] positions = new int[0];
    private ScreenLineStream lines;
    private Tessellator fallback;

    private CriticalDamageHighlightClient() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    /** Netty entry: retain the real connection and world; all state and GL writes stay on the client thread. */
    public static void receive(CriticalDamageHighlights.Frame frame, Object connection) {
        Minecraft mc = Minecraft.getMinecraft();
        World receivedWorld = mc.theWorld;
        long epoch = EPOCH.get();
        mc.func_152344_a(() -> {
            if (epoch != EPOCH.get() || connection != mc.getNetHandler()
                || receivedWorld == null
                || receivedWorld != mc.theWorld
                || receivedWorld.provider.dimensionId != frame.dimension) return;
            INSTANCE.accept(receivedWorld, connection, frame);
        });
    }

    private void accept(World receivedWorld, Object connection, CriticalDamageHighlights.Frame frame) {
        if (connection != activeConnection) {
            activeConnection = connection;
            latestSession = 0;
        }
        if (frame.session < latestSession || frame.session == latestSession && remaining <= 0) return;
        if (frame.session != latestSession) {
            latestSession = frame.session;
            remaining = CriticalDamageHighlights.DURATION_TICKS;
            Minecraft.getMinecraft().thePlayer
                .addChatMessage(new ChatComponentTranslation("gtsr.critical.ui.highlight.started"));
        }
        world = receivedWorld;
        positions = frame.positions;
        knownTotal = frame.knownTotal;
        scanPending = frame.scanPending;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (world != null && world != Minecraft.getMinecraft().theWorld) clear(false);
        if (remaining > 0 && --remaining == 0) clear(false);
        if (remaining == 0) closeLines();
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) clearScheduled(false);
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clearScheduled(true);
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        clearScheduled(true);
    }

    private static void clearScheduled(boolean connectionChanged) {
        long epoch = EPOCH.incrementAndGet();
        Minecraft.getMinecraft()
            .func_152344_a(() -> { if (epoch == EPOCH.get()) INSTANCE.clear(connectionChanged); });
    }

    private void clear(boolean connectionChanged) {
        world = null;
        positions = new int[0];
        remaining = knownTotal = 0;
        scanPending = false;
        if (connectionChanged) {
            activeConnection = null;
            latestSession = 0;
        }
        closeLines();
    }

    /** A deferred clear may run during teardown; never delete a VAO without the client's live GL context. */
    private void closeLines() {
        if (lines != null && Display.isCreated()) {
            lines.close();
            lines = null;
        }
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Text event) {
        if (remaining <= 0 || world != Minecraft.getMinecraft().theWorld) return;
        event.left.add(
            EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                "gtsr.critical.ui.highlight.counts",
                positions.length / 3,
                knownTotal,
                (remaining + 19) / 20));
        String scan = StatCollector
            .translateToLocal("gtsr.critical.ui.highlight." + (scanPending ? "scanning" : "scanned"));
        event.left.add(
            knownTotal > positions.length / 3 ? scan + " · "
                + EnumChatFormatting.RED
                + StatCollector.translateToLocal("gtsr.critical.ui.highlight.truncated") : scan);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (remaining <= 0 || positions.length == 0 || world != Minecraft.getMinecraft().theWorld) return;
        try (GlScope ignored = new GlScope()) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(2F);
            if (lines == null) lines = new ScreenLineStream();
            if (lines.begin(Tessellator.instance)) {
                try {
                    lines.color(1F, .32F, .08F, .95F);
                    boxes(lines, null);
                } finally {
                    lines.end();
                }
            } else {
                // A rejected Core pass submitted nothing. This dedicated vanilla tessellator draws every same edge
                // without touching a possibly foreign, captured or already-open global Tessellator.
                if (fallback == null) fallback = new Tessellator();
                fallback.startDrawing(GL11.GL_LINES);
                fallback.setColorRGBA_F(1F, .32F, .08F, .95F);
                boxes(null, fallback);
                fallback.draw();
            }
        }
    }

    private void boxes(ScreenLineStream stream, Tessellator nativeLines) {
        for (int i = 0; i < positions.length; i += 3) {
            double x = positions[i] - RenderManager.renderPosX - .002;
            double y = positions[i + 1] - RenderManager.renderPosY - .002;
            double z = positions[i + 2] - RenderManager.renderPosZ - .002;
            double x1 = x + 1.004, y1 = y + 1.004, z1 = z + 1.004;
            line(stream, nativeLines, x, y, z, x1, y, z);
            line(stream, nativeLines, x1, y, z, x1, y, z1);
            line(stream, nativeLines, x1, y, z1, x, y, z1);
            line(stream, nativeLines, x, y, z1, x, y, z);
            line(stream, nativeLines, x, y1, z, x1, y1, z);
            line(stream, nativeLines, x1, y1, z, x1, y1, z1);
            line(stream, nativeLines, x1, y1, z1, x, y1, z1);
            line(stream, nativeLines, x, y1, z1, x, y1, z);
            line(stream, nativeLines, x, y, z, x, y1, z);
            line(stream, nativeLines, x1, y, z, x1, y1, z);
            line(stream, nativeLines, x1, y, z1, x1, y1, z1);
            line(stream, nativeLines, x, y, z1, x, y1, z1);
        }
    }

    private static void line(ScreenLineStream stream, Tessellator nativeLines, double x, double y, double z, double x1,
        double y1, double z1) {
        if (stream != null) stream.line(x, y, z, x1, y1, z1);
        else {
            nativeLines.addVertex(x, y, z);
            nativeLines.addVertex(x1, y1, z1);
        }
    }
}
