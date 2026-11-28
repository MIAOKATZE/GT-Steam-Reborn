// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.critical;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.client.render.ClientRenderFrame;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.TileEntityCriticalRenderAnchor;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** One renderer per installation; immutable culled meshes shared by all installations of a model. */
@SideOnly(Side.CLIENT)
public final class CriticalControllerRenderer extends TileEntitySpecialRenderer
    implements IResourceManagerReloadListener {

    private static CriticalControllerRenderer instance;
    private final CriticalMeshCache nativeMeshes = new CriticalMeshCache();
    private final Map<TileEntityCriticalRenderAnchor, CriticalLightCache> lightCaches = new WeakHashMap<>();
    private final Map<TileEntityCriticalRenderAnchor, VisualClock> clocks = new WeakHashMap<>();
    private final Map<TileEntityCriticalRenderAnchor, CriticalContinuousMotion.FrameClock> loomClocks = new WeakHashMap<>();
    private final Map<TileEntityCriticalRenderAnchor, CriticalContinuousMotion.Context> motionClocks = new WeakHashMap<>();
    private final List<CriticalMeshCache.Mesh> nativeBlended = new ArrayList<>();
    private CriticalMachineState renderingController;
    private CriticalLightCache currentLight;
    private int anchorLight;
    private final DoubleBuffer matrix = BufferUtils.createDoubleBuffer(16);
    private final Map<TileEntityCriticalRenderAnchor, CriticalGroupVisibility> visibility = new WeakHashMap<>();
    private final Map<JsonObject, CachedPose> groupMatrices = new IdentityHashMap<>();
    private Set<String> activeGroups;
    private double constructionPhase;
    private double constructionSeconds;
    private String constructionMode = "idle";
    private double cameraX, cameraY, cameraZ;
    private static final double[] IDENTITY = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };

    public static void register() {
        if (instance != null) return;
        ClientRenderFrame.register();
        CriticalControllerRenderer renderer = new CriticalControllerRenderer();
        instance = renderer;
        CriticalFXBudget.register();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCriticalRenderAnchor.class, renderer);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(renderer);
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(renderer);
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void worldUnloaded(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (!event.world.isRemote) return;
        try {
            onResourceManagerReload(null);
        } finally {
            clocks.clear();
            loomClocks.clear();
            motionClocks.clear();
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        lightCaches.clear();
        visibility.clear();
        groupMatrices.clear();
        for (VisualClock clock : clocks.values()) {
            clock.signature = "";
            clock.motionFrame = Long.MIN_VALUE;
        }
        CriticalFX.clear();
        nativeMeshes.clear();
    }

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileEntityCriticalRenderAnchor anchor)) return;
        if (!worldAnchor(anchor)) return;
        CriticalMachineState controller = anchor.getVisualState();
        CriticalRenderMetrics.renderCalls++;
        try {
            renderAnchor(anchor, controller, x, y, z, partialTicks);
        } finally {
            activeGroups = null;
            renderingController = null;
            currentLight = null;
            CriticalAnimation.endFrame();
        }
    }

    static boolean worldAnchor(TileEntityCriticalRenderAnchor anchor) {
        return anchor.getWorldObj() != null && !anchor.isInvalid()
            && anchor.getWorldObj()
                .getTileEntity(anchor.xCoord, anchor.yCoord, anchor.zCoord) == anchor;
    }

    private void renderAnchor(TileEntityCriticalRenderAnchor anchor, CriticalMachineState controller, double x,
        double y, double z, float partialTicks) {
        TileEntity tile = anchor;
        boolean installation = !controller.getStructureId()
            .isEmpty() && controller.getState() != 0;
        if (!installation && !anchor.isLoomVisible()) return;
        boolean complete = controller.isStructureComplete();
        CriticalGroupVisibility checked = visibility.get(anchor);
        if (checked == null || !checked.matches(controller)) {
            checked = new CriticalGroupVisibility(controller);
            visibility.put(anchor, checked);
        }
        // Vanilla TESR dispatch supplies the controller's full installation AABB for frustum culling.
        if (anchor.getDistanceFrom(
            Minecraft.getMinecraft().thePlayer.posX,
            Minecraft.getMinecraft().thePlayer.posY,
            Minecraft.getMinecraft().thePlayer.posZ) > 512D * 512D) return;
        activeGroups = checked.update(controller);
        boolean geometryReady = controller.getState() != 2 || controller.isGeometryValidated();
        boolean loomReady = anchor.isLoomVisible();
        activeGroups = checked.readyGroups(activeGroups, geometryReady, loomReady);
        boolean hasBatch = controller.hasVisualBatch();
        double wallSeconds = ClientRenderFrame.seconds();
        boolean gamePaused = Minecraft.getMinecraft()
            .isGamePaused();
        VisualClock visualClock = clocks.computeIfAbsent(anchor, ignored -> new VisualClock());
        double seconds = visualClock.update(ClientRenderFrame.id(), wallSeconds, gamePaused || !geometryReady);
        constructionSeconds = loomClocks.computeIfAbsent(anchor, ignored -> new CriticalContinuousMotion.FrameClock())
            .update(ClientRenderFrame.id(), wallSeconds, gamePaused || !loomReady);
        double phase = controller.getVisualBatchPhase(partialTicks);
        cameraX = controller
            .localPointX(Minecraft.getMinecraft().thePlayer.posX, Minecraft.getMinecraft().thePlayer.posZ);
        cameraY = Minecraft.getMinecraft().thePlayer.posY - controller.getOriginY();
        cameraZ = controller
            .localPointZ(Minecraft.getMinecraft().thePlayer.posX, Minecraft.getMinecraft().thePlayer.posZ);
        float previousLightX = OpenGlHelper.lastBrightnessX, previousLightY = OpenGlHelper.lastBrightnessY;
        renderingController = controller;
        currentLight = lightCaches.computeIfAbsent(anchor, ignored -> new CriticalLightCache());
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(
                x + controller.getMeshOriginX() - tile.xCoord,
                y + controller.getOriginY() - tile.yCoord,
                z + controller.getMeshOriginZ() - tile.zCoord);
            // Every mesh, mechanical pose and FX point stays in the shared canonical scene frame.
            GL11.glRotated(controller.getStructureYawDegrees(), 0, 1, 0);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .01F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            anchorLight = currentLight.sample(
                controller.getWorldObj(),
                "anchor",
                new double[][] { { tile.xCoord, tile.yCoord + 1, tile.zCoord } },
                IDENTITY,
                0,
                0,
                0,
                0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, anchorLight & 65535, anchorLight >> 16);
            String slug = CriticalGeometry.visualMachineSlug(controller),
                loomSlug = CriticalGeometry.visualLoomSlug(controller),
                baseSlug = CriticalGeometry.visualBaseSlug(controller);
            CriticalGeometry.Model machineModel = CriticalGeometry.getModel(slug);
            boolean design = machineModel.meta != null && machineModel.meta.has("designFX");
            String machineMode = machineMode(controller, geometryReady, design, slug);
            CriticalContinuousMotion.Context motion = motionClocks
                .computeIfAbsent(anchor, ignored -> new CriticalContinuousMotion.Context());
            if (visualClock.motionFrame != ClientRenderFrame.id()) {
                visualClock.motionFrame = ClientRenderFrame.id();
                motion.beginFrame(seconds, visualClock.groups(slug, baseSlug, loomSlug), machineMode);
            }
            CriticalAnimation.beginFrame(motion);
            groupMatrices.clear();
            double buildPhase = Math.min(
                1,
                controller.getVisualConstructionTicks(partialTicks)
                    / Math.max(1D, controller.getConstructionDuration()));
            if (controller.getState() == 3)
                buildPhase = Math.min(1, controller.getCursor() / Math.max(1D, controller.getJobTotal()));
            constructionPhase = buildPhase;
            constructionMode = controller.getConstructionVisualMode();
            // Real cells remain in chunk rendering until every material pass of their rigid group
            // has an uploaded replacement. The shared first-upload budget cannot make a group vanish.
            java.util.Set<String> pending = new java.util.HashSet<>();
            prepareGroups(nativeMeshes.get(loomSlug), activeGroups, pending);
            if (installation) {
                prepareGroups(nativeMeshes.get(baseSlug), activeGroups, pending);
                prepareGroups(nativeMeshes.get(slug), activeGroups, pending);
                if (controller.hasPhysicalPlugins())
                    prepareGroups(nativeMeshes.getPlugins(controller), activeGroups, pending);
            }
            if (!pending.isEmpty()) {
                java.util.Set<String> uploaded = new java.util.HashSet<>(activeGroups);
                uploaded.removeAll(pending);
                activeGroups = java.util.Collections.unmodifiableSet(uploaded);
            }
            com.miaokatze.gtsr.common.critical.CriticalLoomProxyVisibility.publishVisibleGroups(anchor, activeGroups);
            if (loomReady) renderModel(loomSlug, buildPhase, constructionSeconds, constructionMode);
            if (installation) {
                renderModel(baseSlug, phase, seconds, design ? machineMode : hasBatch ? "build" : "idle");
                renderModel(slug, phase, seconds, machineMode);
            }
            if (installation && geometryReady && controller.hasInstalledPlugins())
                renderPlugins(controller, phase, seconds);
            if (installation && complete && controller.isGeometryValidated()) {
                double dx = cameraX - controller.getRegionSize() * .5,
                    dy = cameraY - controller.getRegionHeight() * .55, dz = cameraZ - controller.getRegionSize() * .5;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                int setting = Minecraft.getMinecraft().gameSettings.particleSetting;
                if (CriticalGeometry.isV2(controller)) CriticalFX.renderV2(
                    CriticalGeometry.getModel(slug),
                    CriticalGeometry.getModel(loomSlug),
                    CriticalGeometry.getModel(baseSlug),
                    phase,
                    seconds,
                    distanceSquared,
                    setting,
                    activeGroups,
                    slug,
                    loomSlug,
                    baseSlug,
                    machineMode);
                else CriticalFX.render(slug, CriticalGeometry.getModel(slug), phase, seconds, distanceSquared, setting);
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightX, previousLightY);
        }
    }

    static String machineMode(CriticalMachineState controller, boolean geometryReady, boolean design, String slug) {
        if (design) return geometryReady && controller.isWorking() ? "working" : "idle";
        if (!controller.hasVisualBatch()) return "idle";
        if (slug.endsWith("solar")) return geometryReady && controller.isWorking() ? "working" : "idle";
        return "build";
    }

    private void renderPlugins(CriticalMachineState controller, double phase, double seconds) {
        // V2 generic contacts are actual fixed blocks; chunk rendering owns them, never a duplicate TESR mesh.
        if (controller.hasPhysicalPlugins()) {
            List<CriticalMeshCache.Mesh> plugins = nativeMeshes.getPlugins(controller);
            if (!plugins.isEmpty()) renderNativeMeshes(plugins, phase, seconds, "idle");
            return;
        }
        String tier = controller.getTier()
            .name()
            .toLowerCase(Locale.ROOT);
        com.google.gson.JsonArray slots = CriticalGeometry.getModel("base_" + tier).meta.getAsJsonArray("pluginSlots");
        int[] counts = { controller.getParallelPlugins(), controller.getSpeedPlugins(),
            controller.getEfficiencyPlugins() };
        String[] kinds = { "parallel", "speed", "efficiency" };
        int slot = 0;
        for (int kind = 0; kind < kinds.length; kind++) for (int i = 0; i < counts[kind] && slot < 8; i++, slot++) {
            String model = "plugin_" + kinds[kind] + "_" + tier;
            com.google.gson.JsonArray location = slots.get(slot)
                .getAsJsonArray();
            double height = CriticalAnimation.number(CriticalGeometry.getModel(model).meta, "installOffsetY", 12);
            GL11.glPushMatrix();
            GL11.glTranslated(
                location.get(0)
                    .getAsDouble(),
                height,
                location.get(1)
                    .getAsDouble());
            renderModel(model, phase, seconds, controller.hasVisualBatch() ? "build" : "idle");
            GL11.glPopMatrix();
        }
    }

    private void renderModel(String slug, double phase, double seconds, String mode) {
        Set<String> groups = activeGroups;
        if (slug.startsWith("plugin_")) activeGroups = null;
        try {
            renderNativeModel(slug, phase, seconds, mode);
        } finally {
            activeGroups = groups;
        }
    }

    private void renderNativeModel(String slug, double phase, double seconds, String mode) {
        List<CriticalMeshCache.Mesh> model = nativeMeshes.get(slug);
        renderNativeMeshes(model, phase, seconds, mode);
    }

    static void prepareGroups(List<CriticalMeshCache.Mesh> model, Set<String> candidates, Set<String> pending) {
        for (CriticalMeshCache.Mesh mesh : model)
            if (candidates.contains(mesh.visibilityKey) && !mesh.prepare()) pending.add(mesh.visibilityKey);
    }

    private void renderNativeMeshes(List<CriticalMeshCache.Mesh> model, double phase, double seconds, String mode) {
        nativeBlended.clear();
        for (CriticalMeshCache.Mesh mesh : model)
            if (mesh.pass == 2 && (activeGroups == null || activeGroups.contains(mesh.visibilityKey)))
                nativeBlended.add(mesh);
        nativeBlended.sort(Comparator.comparingDouble((CriticalMeshCache.Mesh mesh) -> {
            double[] matrix = nativePose(mesh, phase, seconds, mode), p = mesh.center;
            double dx = matrix[0] * p[0] + matrix[4] * p[1] + matrix[8] * p[2] + matrix[12] - cameraX,
                dy = matrix[1] * p[0] + matrix[5] * p[1] + matrix[9] * p[2] + matrix[13] - cameraY,
                dz = matrix[2] * p[0] + matrix[6] * p[1] + matrix[10] * p[2] + matrix[14] - cameraZ;
            return dx * dx + dy * dy + dz * dz;
        })
            .reversed());
        bindTexture(TextureMap.locationBlocksTexture);
        for (int pass = 0; pass < 3; pass++) {
            GL11.glDepthMask(pass != 2);
            if (pass == 2) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);
            GL11.glAlphaFunc(GL11.GL_GREATER, pass == 1 ? .5F : .01F);
            for (CriticalMeshCache.Mesh mesh : pass == 2 ? nativeBlended : model) {
                if (mesh.pass != pass || activeGroups != null && !activeGroups.contains(mesh.visibilityKey)) continue;
                if (!mesh.prepare()) continue;
                GL11.glPushMatrix();
                try {
                    double[] transform = nativePose(mesh, phase, seconds, mode);
                    int light = currentLight.sample(
                        renderingController.getWorldObj(),
                        mesh.visibilityKey,
                        mesh.lightSamples,
                        transform,
                        renderingController.getMeshOriginX(),
                        renderingController.getOriginY(),
                        renderingController.getMeshOriginZ(),
                        renderingController.getStructureFacing(),
                        anchorLight);
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
                    if (transform != IDENTITY) {
                        matrix.clear();
                        matrix.put(transform)
                            .flip();
                        GL11.glMultMatrix(matrix);
                    }
                    mesh.albedo.draw();
                    CriticalRenderMetrics.displayListDraws++;
                    if (mesh.emission != null) {
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                        GL11.glEnable(GL11.GL_BLEND);
                        GL11.glDepthMask(false);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                        GL11.glPolygonOffset(-1, -1);
                        mesh.emission.draw();
                        CriticalRenderMetrics.displayListDraws++;
                        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        GL11.glDepthMask(pass != 2);
                        if (pass != 2) GL11.glDisable(GL11.GL_BLEND);
                        OpenGlHelper
                            .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
                    }
                } finally {
                    GL11.glPopMatrix();
                }
            }
        }
        GL11.glDepthMask(true);
    }

    private double[] nativePose(CriticalMeshCache.Mesh mesh, double phase, double seconds, String mode) {
        // V26 changes payment ownership only; transferred loom groups keep their construction clock and mode.
        boolean transferredLoom = mesh.visibilityKey.contains("base_t3:loom_");
        return pose(
            mesh.animation,
            transferredLoom ? constructionPhase : phase,
            transferredLoom ? constructionSeconds : seconds,
            transferredLoom ? constructionMode : mode);
    }

    private double[] pose(JsonObject animation, double phase, double seconds, String mode) {
        if (idleIdentity(animation, mode)) return IDENTITY;
        CachedPose cached = groupMatrices.get(animation);
        if (cached != null && cached.phase == phase && cached.seconds == seconds && cached.mode.equals(mode))
            return cached.matrix;
        double[] transformed = CriticalMechanicalPose.matrix(animation, phase, seconds, mode);
        groupMatrices.put(animation, new CachedPose(phase, seconds, mode, transformed));
        return transformed;
    }

    private static boolean idleIdentity(JsonObject animation, String mode) {
        return "idle".equals(mode) && !animation.has("idleMotion")
            && !animation.has("ambientRotation")
            && !animation.has("ambientTilt")
            && !animation.has("ambientOrbit")
            && !animation.has("continuousMotion")
            && !animation.has("levitationBob");
    }

    private static final class VisualClock extends CriticalContinuousMotion.FrameClock {

        long motionFrame = Long.MIN_VALUE;
        String signature = "";
        final List<JsonObject> continuousGroups = new ArrayList<>();

        List<JsonObject> groups(String machine, String base, String loom) {
            String key = machine + ':' + base + ':' + loom;
            if (!key.equals(signature)) {
                signature = key;
                continuousGroups.clear();
                for (String slug : new String[] { machine, base, loom })
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : CriticalGeometry
                        .getModel(slug).animations.entrySet()) if (entry.getValue()
                            .getAsJsonObject()
                            .has("continuousMotion"))
                            continuousGroups.add(
                                entry.getValue()
                                    .getAsJsonObject());
            }
            return continuousGroups;
        }
    }

    private static final class CachedPose {

        final double phase, seconds;
        final String mode;
        final double[] matrix;

        CachedPose(double phase, double seconds, String mode, double[] matrix) {
            this.phase = phase;
            this.seconds = seconds;
            this.mode = mode;
            this.matrix = matrix;
        }
    }

}
