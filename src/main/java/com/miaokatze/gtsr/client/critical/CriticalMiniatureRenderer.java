package com.miaokatze.gtsr.client.critical;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import com.miaokatze.gtsr.common.critical.CriticalPreviewDescriptor;
import com.miaokatze.gtsr.main.GTSteamReborn;
import com.miaokatze.miaokatzecore.client.preview.PreviewRenderScope.RenderState;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Passive GUI adapter with a private color/depth target and Core's complete caller-state restoration. */
@SideOnly(Side.CLIENT)
public final class CriticalMiniatureRenderer implements IResourceManagerReloadListener {

    private static final int TARGET_SIZE = 512, FRAMEBUFFER = 0x8d40, RENDERBUFFER = 0x8d41;
    private static final Map<String, CriticalPreviewScene> SCENES = new LinkedHashMap<>(4, .75F, true);
    private static final Map<String, Boolean> FAILED_KEYS = new LinkedHashMap<>(8, .75F, true);
    private static final Field DRAWING = ReflectionHelper.findField(Tessellator.class, "isDrawing", "field_78415_z");
    private static final FloatBuffer LIGHT = BufferUtils.createFloatBuffer(4);
    private static boolean listening, targetFailed, hadScreen;
    private static int framebuffer, texture, depth, failures, compiles;

    private CriticalMiniatureRenderer() {}

    /** False means preparing, unsupported or failed: the caller keeps its readable 2D fallback. No input is taken. */
    public static boolean draw(CriticalPreviewDescriptor descriptor, int x, int y, int width, int height,
        double seconds, boolean reducedMotion) {
        if (descriptor == null || width <= 0 || height <= 0) return false;
        if (!OpenGlHelper.isFramebufferEnabled()) {
            if (!SCENES.isEmpty() || framebuffer != 0) clear();
            return false;
        }
        try {
            if (DRAWING.getBoolean(Tessellator.instance)) return false;
        } catch (IllegalAccessException unavailable) {
            return false;
        }
        listen();
        String key = descriptor.geometryKey();
        if (targetFailed || FAILED_KEYS.containsKey(key)) return false;
        RenderState state = null;
        CriticalPreviewScene scene = null;
        try {
            state = new RenderState();
            state.push();
            state.useProgram(0);
            prepareGuiTextures();
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            scene = SCENES.get(descriptor.geometryKey());
            if (scene == null) {
                while (SCENES.size() >= 4) {
                    Iterator<CriticalPreviewScene> oldest = SCENES.values()
                        .iterator();
                    oldest.next()
                        .close();
                    oldest.remove();
                }
                scene = new CriticalPreviewScene(descriptor);
                SCENES.put(descriptor.geometryKey(), scene);
                compiles++;
            }
            scene.prepare();
            if (!scene.ready || scene.failed) return false;
            Minecraft mc = Minecraft.getMinecraft();
            int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
            int pixelsX = Math.min(TARGET_SIZE, Math.max(1, width * scale));
            int pixelsY = Math.min(TARGET_SIZE, Math.max(1, height * scale));
            RenderState offscreen = new RenderState();
            try {
                offscreen.push();
                ensureTarget();
                OpenGlHelper.func_153171_g(FRAMEBUFFER, framebuffer);
                GL11.glViewport(0, 0, pixelsX, pixelsY);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glColorMask(true, true, true, true);
                GL11.glDepthMask(true);
                GL11.glClearColor(0, 0, 0, 0);
                GL11.glClearDepth(1);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glDepthRange(0, 1);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glAlphaFunc(GL11.GL_GREATER, .03F);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glBindTexture(
                    GL11.GL_TEXTURE_2D,
                    mc.getTextureManager()
                        .getTexture(TextureMap.locationBlocksTexture)
                        .getGlTextureId());
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glLoadIdentity();
                double radius = scene.radius(), aspect = (double) width / height;
                double halfY = Math.max(
                    (scene.projectedHigh[1] - scene.projectedLow[1]) * .5,
                    (scene.projectedHigh[0] - scene.projectedLow[0]) * .5 / aspect) * 1.035 + .25;
                double halfX = halfY * aspect;
                double centerX = (scene.projectedLow[0] + scene.projectedHigh[0]) * .5;
                double centerY = (scene.projectedLow[1] + scene.projectedHigh[1]) * .5;
                GL11.glOrtho(
                    centerX - halfX,
                    centerX + halfX,
                    centerY - halfY,
                    centerY + halfY,
                    -radius * 4,
                    radius * 4);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glLoadIdentity();
                lighting();
                GL11.glRotated(CriticalMiniatureModel.PITCH, 1, 0, 0);
                GL11.glRotated(CriticalMiniatureModel.YAW, 0, 1, 0);
                scene.draw(descriptor, seconds, reducedMotion);
            } finally {
                offscreen.restore();
            }
            // The parent projection, modelview, FBO, viewport and scissor are now active again.
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glEnable(GL11.GL_BLEND);
            // Offscreen color is premultiplied by its alpha while drawing translucent construction groups.
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            double u = (double) pixelsX / TARGET_SIZE, v = (double) pixelsY / TARGET_SIZE;
            GL11.glBegin(GL11.GL_QUADS);
            try {
                GL11.glTexCoord2d(0, v);
                GL11.glVertex3d(x, y, 0);
                GL11.glTexCoord2d(0, 0);
                GL11.glVertex3d(x, y + height, 0);
                GL11.glTexCoord2d(u, 0);
                GL11.glVertex3d(x + width, y + height, 0);
                GL11.glTexCoord2d(u, v);
                GL11.glVertex3d(x + width, y, 0);
            } finally {
                GL11.glEnd();
            }
            return true;
        } catch (RuntimeException failure) {
            failures++;
            if (scene != null) scene.fail();
            if (FAILED_KEYS.size() >= 8) FAILED_KEYS.remove(
                FAILED_KEYS.keySet()
                    .iterator()
                    .next());
            FAILED_KEYS.put(key, true);
            GTSteamReborn.LOG.warn("Critical miniature fell back to schematic", failure);
            return false;
        } finally {
            if (state != null) state.restore();
        }
    }

    private static void lighting() {
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        for (int light = GL11.GL_LIGHT1; light <= GL11.GL_LIGHT7; light++) GL11.glDisable(light);
        GL11.glEnable(GL11.GL_NORMALIZE);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        light(GL11.GL_POSITION, -.4F, .8F, 1, 0);
        light(GL11.GL_DIFFUSE, .8F, .8F, .8F, 1);
        light(GL11.GL_AMBIENT, .35F, .35F, .35F, 1);
    }

    private static void light(int property, float x, float y, float z, float w) {
        LIGHT.clear();
        LIGHT.put(x)
            .put(y)
            .put(z)
            .put(w)
            .flip();
        GL11.glLight(GL11.GL_LIGHT0, property, LIGHT);
    }

    private static void ensureTarget() {
        if (framebuffer != 0) return;
        try {
            framebuffer = OpenGlHelper.func_153165_e();
            texture = GL11.glGenTextures();
            depth = OpenGlHelper.func_153185_f();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                TARGET_SIZE,
                TARGET_SIZE,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
            OpenGlHelper.func_153171_g(FRAMEBUFFER, framebuffer);
            OpenGlHelper.func_153188_a(FRAMEBUFFER, 0x8ce0, GL11.GL_TEXTURE_2D, texture, 0);
            OpenGlHelper.func_153176_h(RENDERBUFFER, depth);
            OpenGlHelper.func_153186_a(RENDERBUFFER, 0x81a6, TARGET_SIZE, TARGET_SIZE);
            OpenGlHelper.func_153190_b(FRAMEBUFFER, 0x8d00, RENDERBUFFER, depth);
            if (framebuffer == 0 || texture == 0 || depth == 0 || OpenGlHelper.func_153167_i(FRAMEBUFFER) != 0x8cd5)
                throw new IllegalStateException("Incomplete miniature framebuffer");
        } catch (RuntimeException failure) {
            deleteTarget();
            targetFailed = true;
            throw failure;
        }
    }

    private static void prepareGuiTextures() {
        // This scene only uses Minecraft's two 2D units. Disabling unrelated legacy targets (notably
        // CUBE_MAP) falls through Angelica's core-profile backend and raises GL_INVALID_ENUM.
        for (int unit : new int[] { OpenGlHelper.defaultTexUnit, OpenGlHelper.lightmapTexUnit }) {
            OpenGlHelper.setActiveTexture(unit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glLoadIdentity();
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static void listen() {
        if (listening) return;
        listening = true;
        CriticalMiniatureRenderer listener = new CriticalMiniatureRenderer();
        MinecraftForge.EVENT_BUS.register(listener);
        FMLCommonHandler.instance()
            .bus()
            .register(listener);
        IResourceManager manager = Minecraft.getMinecraft()
            .getResourceManager();
        if (manager instanceof IReloadableResourceManager)
            ((IReloadableResourceManager) manager).registerReloadListener(listener);
    }

    /** Call on the client render thread; no world objects or asynchronous GL deletion are retained. */
    public static void clear() {
        for (CriticalPreviewScene scene : SCENES.values()) scene.close();
        SCENES.clear();
        FAILED_KEYS.clear();
        deleteTarget();
        targetFailed = false;
    }

    private static void deleteTarget() {
        if (depth != 0) OpenGlHelper.func_153184_g(depth);
        if (framebuffer != 0) OpenGlHelper.func_153174_h(framebuffer);
        if (texture != 0) GL11.glDeleteTextures(texture);
        depth = framebuffer = texture = 0;
    }

    public static String diagnostics() {
        int ready = 0, lists = 0, faces = 0, failed = FAILED_KEYS.size();
        double motion = 0;
        for (CriticalPreviewScene scene : SCENES.values()) {
            if (scene.ready && !scene.failed) ready++;
            if (scene.failed) failed++;
            lists += scene.listCount();
            faces += scene.faceCount();
            motion += scene.motionSeconds();
        }
        return "scenes=" + SCENES.size()
            + ",ready="
            + ready
            + ",compiles="
            + compiles
            + ",failures="
            + failures
            + ",failed="
            + failed
            + ",lists="
            + lists
            + ",faces="
            + faces
            + ",motionSeconds="
            + motion
            + ",targetPixels="
            + (framebuffer == 0 ? 0 : TARGET_SIZE * TARGET_SIZE);
    }

    public static String diagnostics(CriticalPreviewDescriptor descriptor) {
        CriticalPreviewScene scene = descriptor == null ? null : SCENES.get(descriptor.geometryKey());
        return scene == null ? "ready=0,lists=0,faces=0"
            : "ready=" + (scene.ready && !scene.failed ? 1 : 0)
                + ",lists="
                + scene.listCount()
                + ",faces="
                + scene.faceCount()
                + ",motionSeconds="
                + scene.motionSeconds();
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        clear();
    }

    @SubscribeEvent
    public void stitched(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() == 0) clear();
    }

    @SubscribeEvent
    public void worldUnloaded(WorldEvent.Unload event) {
        if (event.world.isRemote) clear();
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean screen = Minecraft.getMinecraft().currentScreen != null;
        if (hadScreen && !screen) clear();
        hadScreen = screen;
    }
}
