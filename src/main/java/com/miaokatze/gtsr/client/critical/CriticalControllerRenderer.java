package com.miaokatze.gtsr.client.critical;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalGeometry.Voxel;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;
import com.miaokatze.gtsr.common.critical.TileEntityCriticalRenderAnchor;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** One renderer per installation; immutable culled meshes shared by all installations of a model. */
@SideOnly(Side.CLIENT)
public final class CriticalControllerRenderer extends TileEntitySpecialRenderer
    implements IResourceManagerReloadListener {

    private final Map<String, List<Mesh>> meshes = new HashMap<>();
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
    private static final int[][] NEIGHBOR = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    private static final double[][][] CORNERS = { { { 0, 0, 1 }, { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 } },
        { { 0, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 } }, { { 1, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 }, { 0, 1, 0 } },
        { { 0, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 } }, { { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 1 } },
        { { 1, 1, 1 }, { 1, 0, 1 }, { 1, 0, 0 }, { 1, 1, 0 } } };

    public static void register() {
        CriticalControllerRenderer renderer = new CriticalControllerRenderer();
        CriticalFXBudget.register();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCriticalRenderAnchor.class, renderer);
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(renderer);
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        for (List<Mesh> model : meshes.values()) for (Mesh mesh : model) GL11.glDeleteLists(mesh.list, 1);
        meshes.clear();
        nativeMeshes.clear();
        lightCaches.clear();
        visibility.clear();
        groupMatrices.clear();
        for (VisualClock clock : clocks.values()) {
            clock.signature = "";
            clock.motionFrame = Long.MIN_VALUE;
        }
        CriticalFX.clear();
    }

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileEntityCriticalRenderAnchor anchor)) return;
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
        com.miaokatze.gtsr.common.critical.CriticalLoomProxyVisibility.publishVisibleGroups(anchor, activeGroups);
        boolean hasBatch = controller.hasVisualBatch();
        double wallSeconds = System.nanoTime() / 1_000_000_000D;
        boolean gamePaused = Minecraft.getMinecraft()
            .isGamePaused();
        VisualClock visualClock = clocks.computeIfAbsent(anchor, ignored -> new VisualClock());
        double seconds = visualClock
            .update(CriticalRenderMetrics.frameStarts, wallSeconds, gamePaused || !geometryReady);
        constructionSeconds = loomClocks.computeIfAbsent(anchor, ignored -> new CriticalContinuousMotion.FrameClock())
            .update(CriticalRenderMetrics.frameStarts, wallSeconds, gamePaused || !loomReady);
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
            if (visualClock.motionFrame != CriticalRenderMetrics.frameStarts) {
                visualClock.motionFrame = CriticalRenderMetrics.frameStarts;
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
        if (slug.startsWith("v2_")) {
            renderNativeModel(slug, phase, seconds, mode);
            return;
        }
        List<Mesh> model = meshes.computeIfAbsent(slug, this::compile);
        List<Mesh> blended = new ArrayList<>();
        for (Mesh mesh : model) if (mesh.pass == 2 && visible(slug, mesh)) blended.add(mesh);
        blended.sort(
            Comparator.comparingDouble((Mesh mesh) -> distanceSquared(mesh, phase, seconds, mode))
                .reversed());
        for (int pass = 0; pass < 3; pass++) {
            GL11.glDepthMask(pass != 2);
            if (pass == 2) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);
            GL11.glAlphaFunc(GL11.GL_GREATER, pass == 1 ? .5F : .01F);
            for (Mesh mesh : pass == 2 ? blended : model) {
                if (mesh.pass != pass || !visible(slug, mesh)) continue;
                GL11.glPushMatrix();
                try {
                    affine(mesh.animation, phase, seconds, mode);
                    bindTexture(mesh.texture);
                    if (mesh.animated) animateTexture(true, seconds);
                    GL11.glCallList(mesh.list);
                    CriticalRenderMetrics.displayListDraws++;
                    if (mesh.animated) finishTexture();
                    if (mesh.emission != null) {
                        float oldX = OpenGlHelper.lastBrightnessX, oldY = OpenGlHelper.lastBrightnessY;
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                        GL11.glDepthMask(false);
                        GL11.glEnable(GL11.GL_BLEND);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                        GL11.glPolygonOffset(-1, -1);
                        GL11.glColor4f(mesh.strength, mesh.strength, mesh.strength, 1);
                        bindTexture(mesh.emission);
                        if (mesh.animated) animateTexture(true, seconds);
                        GL11.glCallList(mesh.list);
                        CriticalRenderMetrics.displayListDraws++;
                        if (mesh.animated) finishTexture();
                        GL11.glColor4f(1, 1, 1, 1);
                        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        GL11.glDepthMask(pass != 2);
                        if (pass != 2) GL11.glDisable(GL11.GL_BLEND);
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldX, oldY);
                    }
                } finally {
                    GL11.glPopMatrix();
                }
            }
        }
        GL11.glDepthMask(true);
    }

    private void renderNativeModel(String slug, double phase, double seconds, String mode) {
        List<CriticalMeshCache.Mesh> model = nativeMeshes.get(slug);
        renderNativeMeshes(model, phase, seconds, mode);
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
                GL11.glPushMatrix();
                try {
                    double[] transform = nativePose(mesh, phase, seconds, mode);
                    int light = currentLight.sample(
                        renderingController.getWorldObj(),
                        mesh.visibilityKey,
                        mesh.lightSamples,
                        IDENTITY,
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
                    GL11.glCallList(mesh.albedo);
                    CriticalRenderMetrics.displayListDraws++;
                    if (mesh.emission != 0) {
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                        GL11.glEnable(GL11.GL_BLEND);
                        GL11.glDepthMask(false);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                        GL11.glPolygonOffset(-1, -1);
                        GL11.glCallList(mesh.emission);
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

    private void animateTexture(boolean animated, double seconds) {
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        if (animated) GL11.glTranslated(0, (Math.floor(seconds * 20 / 10) % 8) / 8D, 0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private void finishTexture() {
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private boolean visible(String slug, Mesh mesh) {
        return activeGroups == null || slug.startsWith("plugin_") || activeGroups.contains(mesh.visibilityKey);
    }

    private double distanceSquared(Mesh mesh, double phase, double seconds, String mode) {
        double[] pose = pose(mesh.animation, phase, seconds, mode);
        double cx = mesh.center[0] + .5, cy = mesh.center[1] + .5, cz = mesh.center[2] + .5;
        double dx = pose[0] * cx + pose[4] * cy + pose[8] * cz + pose[12] - cameraX;
        double dy = pose[1] * cx + pose[5] * cy + pose[9] * cz + pose[13] - cameraY;
        double dz = pose[2] * cx + pose[6] * cy + pose[10] * cz + pose[14] - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private void affine(JsonObject animation, double phase, double seconds, String mode) {
        if (idleIdentity(animation, mode)) return;
        matrix.clear();
        matrix.put(pose(animation, phase, seconds, mode))
            .flip();
        GL11.glMultMatrix(matrix);
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

    private List<Mesh> compile(String slug) {
        CriticalRenderMetrics.meshCompiles++;
        CriticalGeometry.Model model = CriticalGeometry.getModel(slug);
        Map<String, List<Face>> batches = new LinkedHashMap<>();
        Map<Long, Voxel> occupied = new HashMap<>();
        for (Voxel voxel : model.voxels) occupied.put(voxel.positionKey(), voxel);
        for (Voxel voxel : model.voxels) {
            if (!model.animations.has(voxel.group) && !slug.startsWith("plugin_")) continue;
            for (int side = 0; side < 6; side++) {
                int[] d = NEIGHBOR[side];
                Voxel next = occupied.get(Voxel.positionKey(voxel.x + d[0], voxel.y + d[1], voxel.z + d[2]));
                // Cull only within the same rigid group; another group can move away.
                if (next != null && next.group.equals(voxel.group)
                    && ("opaque".equals(
                        CriticalMaterials.properties(next.material)
                            .get("alphaMode")
                            .getAsString())
                        || next.material.equals(voxel.material)))
                    continue;
                String key = voxel.group + ":" + voxel.material + ":" + (side < 2 ? "top" : "side");
                batches.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new Face(voxel, side));
            }
        }
        List<Mesh> result = new ArrayList<>();
        for (List<Face> faces : batches.values()) {
            Face first = faces.get(0);
            JsonObject properties = CriticalMaterials.properties(first.voxel.material);
            String id = properties.get("id")
                .getAsString();
            boolean animated = first.side >= 2 && properties.get("animated")
                .getAsBoolean();
            String suffix = first.side < 2 ? "__top" : "";
            int list = GL11.glGenLists(1);
            GL11.glNewList(list, GL11.GL_COMPILE);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            for (Face face : faces) {
                int[] normal = NEIGHBOR[face.side];
                t.setNormal(normal[0], normal[1], normal[2]);
                double shade = face.side == 0 ? .58 : face.side == 1 ? 1 : face.side < 4 ? .78 : .88;
                t.setColorOpaque_F((float) shade, (float) shade, (float) shade);
                for (int vertex = 0; vertex < 4; vertex++) {
                    double[] c = CORNERS[face.side][vertex];
                    t.addVertexWithUV(
                        face.voxel.x + c[0],
                        face.voxel.y + c[1],
                        face.voxel.z + c[2],
                        vertex >= 2 ? 1 : 0,
                        (vertex == 0 || vertex == 3 ? 0 : 1) / (animated ? 8D : 1D));
                }
            }
            t.draw();
            GL11.glEndList();
            float strength = properties.get("emissionStrength")
                .getAsFloat();
            result.add(
                new Mesh(
                    list,
                    model.animations.has(first.voxel.group) ? model.animations.getAsJsonObject(first.voxel.group)
                        : new JsonObject(),
                    texture(id + suffix),
                    strength > 0 && suffix.isEmpty() ? texture(id + "_emission") : null,
                    animated,
                    properties.get("alphaMode")
                        .getAsString(),
                    strength,
                    first.voxel.group,
                    slug + ":" + first.voxel.group,
                    meshCenter(faces)));
        }
        return result;
    }

    private static double[] meshCenter(List<Face> faces) {
        double[] center = new double[3];
        for (Face face : faces) {
            center[0] += face.voxel.x;
            center[1] += face.voxel.y;
            center[2] += face.voxel.z;
        }
        for (int axis = 0; axis < 3; axis++) center[axis] /= faces.size();
        return center;
    }

    private static ResourceLocation texture(String id) {
        return new ResourceLocation("gtsr", "textures/blocks/critical/" + id + ".png");
    }

    private static final class Face {

        final Voxel voxel;
        final int side;

        Face(Voxel voxel, int side) {
            this.voxel = voxel;
            this.side = side;
        }
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

    private static final class Mesh {

        final int list;
        final JsonObject animation;
        final ResourceLocation texture, emission;
        final boolean animated, transparent;
        final int pass;
        final String group, visibilityKey;
        final double[] center;
        final float strength;

        Mesh(int list, JsonObject animation, ResourceLocation texture, ResourceLocation emission, boolean animated,
            String alphaMode, float strength, String group, String visibilityKey, double[] center) {
            this.list = list;
            this.animation = animation;
            this.texture = texture;
            this.emission = emission;
            this.animated = animated;
            this.transparent = "blend".equals(alphaMode);
            this.pass = "blend".equals(alphaMode) ? 2 : "cutout".equals(alphaMode) ? 1 : 0;
            this.group = group;
            this.visibilityKey = visibilityKey;
            this.center = center;
            this.strength = strength;
        }
    }
}
