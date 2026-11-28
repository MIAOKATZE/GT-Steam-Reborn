// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.machinevisual.VirtualStructureFrame;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Server-described visual installations survive a missing client controller chunk without creating world tiles. */
@SideOnly(Side.CLIENT)
public final class VirtualStructureClient {

    private static final VirtualStructureClient INSTANCE = new VirtualStructureClient();
    private static final AtomicLong EPOCH = new AtomicLong();
    private static final int MAX_SCENES = 512;
    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private final Map<Long, long[]> versions = new LinkedHashMap<>();
    private World world;
    private Object connection;
    private long session;

    private VirtualStructureClient() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(manager -> INSTANCE.reload());
    }

    public static void receive(VirtualStructureFrame frame, Object connection) {
        Minecraft mc = Minecraft.getMinecraft();
        World receivedWorld = mc.theWorld;
        long epoch = EPOCH.get();
        mc.func_152344_a(() -> {
            if (epoch != EPOCH.get() || connection != mc.getNetHandler()
                || receivedWorld == null
                || receivedWorld != mc.theWorld
                || frame.dimension != receivedWorld.provider.dimensionId) return;
            INSTANCE.accept(frame, receivedWorld, connection);
        });
    }

    private void accept(VirtualStructureFrame frame, World nextWorld, Object nextConnection) {
        frame.validate();
        if (world != nextWorld || connection != nextConnection) {
            clear();
            world = nextWorld;
            connection = nextConnection;
            session = frame.session;
        }
        if (frame.session != session) return;
        long key = position(frame.x, frame.y, frame.z);
        if (!VirtualStructureFrame.newer(frame.session, frame.generation, frame.sequence, versions.get(key))) return;
        versions.put(key, new long[] { frame.session, frame.generation, frame.sequence });
        if (versions.size() > MAX_SCENES * 4) versions.remove(
            versions.keySet()
                .iterator()
                .next());
        Entry previous = entries.get(key);
        if (frame.kind == VirtualStructureFrame.REMOVED) {
            if (previous != null) previous.close();
            entries.remove(key);
            return;
        }
        if (previous != null && previous.frame.kind == frame.kind && previous.frame.generation == frame.generation) {
            previous.update(frame);
            return;
        }
        if (previous != null) previous.close();
        else if (entries.size() >= MAX_SCENES) {
            Long oldest = entries.keySet()
                .iterator()
                .next();
            entries.remove(oldest)
                .close();
        }
        entries.put(key, new Entry(frame));
    }

    private static long position(int x, int y, int z) {
        return ((x & 0x3ffffffL) << 38) | ((z & 0x3ffffffL) << 12) | (y & 0xfffL);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity eye = mc.renderViewEntity == null ? mc.thePlayer : mc.renderViewEntity;
        if (world == null || world != mc.theWorld || eye == null || entries.isEmpty()) return;
        double cx = eye.lastTickPosX + (eye.posX - eye.lastTickPosX) * event.partialTicks;
        double cy = eye.lastTickPosY + (eye.posY - eye.lastTickPosY) * event.partialTicks;
        double cz = eye.lastTickPosZ + (eye.posZ - eye.lastTickPosZ) * event.partialTicks;
        Frustrum camera = new Frustrum();
        camera.setPosition(cx, cy, cz);
        CenterModuleRenderer.beginFrame();
        float previousLightX = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX;
        float previousLightY = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .01F);
            GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableStandardItemLighting();
            for (int pass = 0; pass < 2; pass++) {
                GL11.glDepthMask(pass == 0);
                if (pass == 0) GL11.glDisable(GL11.GL_BLEND);
                else {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                }
                for (Entry entry : entries.values())
                    if (entry.center != null) entry.center.render(world, camera, cx, cy, cz, pass);
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDepthMask(true);
        } finally {
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit,
                previousLightX,
                previousLightY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) reset();
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        reset();
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        reset();
    }

    private static void reset() {
        EPOCH.incrementAndGet();
        Minecraft.getMinecraft()
            .func_152344_a(INSTANCE::clear);
    }

    private void clear() {
        for (Entry entry : entries.values()) entry.close();
        entries.clear();
        versions.clear();
        world = null;
        connection = null;
        CenterModuleRenderer.clearCache();
    }

    private void reload() {
        // Descriptors remain authoritative; only GPU leases are rebuilt after the block atlas is replaced.
        for (Entry entry : entries.values()) if (entry.center != null) {
            entry.center.close();
            entry.center = new CenterModuleRenderer(entry.frame);
        }
        CenterModuleRenderer.clearCache();
    }

    private final class Entry implements AutoCloseable {

        VirtualStructureFrame frame;
        CenterModuleRenderer center;

        Entry(VirtualStructureFrame frame) {
            update(frame);
        }

        void update(VirtualStructureFrame next) {
            boolean geometryChanged = frame == null || !frame.visual.equals(next.visual);
            frame = next;
            if (next.kind == VirtualStructureFrame.CENTER && geometryChanged) {
                if (center != null) center.close();
                center = new CenterModuleRenderer(next);
            }
        }

        @Override
        public void close() {
            if (center != null) center.close();

        }
    }
}
