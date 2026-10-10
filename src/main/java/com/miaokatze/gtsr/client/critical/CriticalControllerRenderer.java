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
import com.miaokatze.gtsr.common.critical.CriticalMaterials;
import com.miaokatze.gtsr.common.critical.TileEntityCriticalController;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** One renderer per installation; immutable culled meshes shared by all installations of a model. */
@SideOnly(Side.CLIENT)
public final class CriticalControllerRenderer extends TileEntitySpecialRenderer
    implements IResourceManagerReloadListener {

    private final Map<String, List<Mesh>> meshes = new HashMap<>();
    private final DoubleBuffer matrix = BufferUtils.createDoubleBuffer(16);
    private final Map<TileEntityCriticalController, CriticalGroupVisibility> visibility = new WeakHashMap<>();
    private final Map<JsonObject, double[]> groupMatrices = new IdentityHashMap<>();
    private Set<String> activeGroups;
    private double posePhase = Double.NaN, poseSeconds = Double.NaN;
    private String poseMode = "";
    private double cameraX, cameraY, cameraZ;
    private static final int[][] NEIGHBOR = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    private static final double[][][] CORNERS = { { { 0, 0, 1 }, { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 } },
        { { 0, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 } }, { { 1, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 }, { 0, 1, 0 } },
        { { 0, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 } }, { { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 1 } },
        { { 1, 1, 1 }, { 1, 0, 1 }, { 1, 0, 0 }, { 1, 1, 0 } } };

    public static void register() {
        CriticalControllerRenderer renderer = new CriticalControllerRenderer();
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCriticalController.class, renderer);
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(renderer);
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        for (List<Mesh> model : meshes.values()) for (Mesh mesh : model) GL11.glDeleteLists(mesh.list, 1);
        meshes.clear();
        visibility.clear();
        groupMatrices.clear();
    }

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileEntityCriticalController controller)) return;
        if (controller.getStructureId()
            .isEmpty() || controller.getState() == 0) return;
        boolean complete = controller.isStructureComplete();
        CriticalGroupVisibility checked = visibility.get(controller);
        if (checked == null || !checked.matches(controller)) {
            checked = new CriticalGroupVisibility(controller);
            visibility.put(controller, checked);
        }
        // Vanilla TESR dispatch supplies the controller's full installation AABB for frustum culling.
        if (controller.getDistanceFrom(
            Minecraft.getMinecraft().thePlayer.posX,
            Minecraft.getMinecraft().thePlayer.posY,
            Minecraft.getMinecraft().thePlayer.posZ) > 512D * 512D) return;
        activeGroups = checked.update(controller);
        double seconds = (controller.getWorldObj()
            .getTotalWorldTime() + partialTicks) / 20D;
        boolean hasBatch = !controller.getBatchId()
            .isEmpty();
        double phase = hasBatch
            ? Math.min(1, controller.getVisualCycleTicks(partialTicks) / Math.max(1D, controller.getCycleDuration()))
            : 0;
        cameraX = Minecraft.getMinecraft().thePlayer.posX - controller.getOriginX();
        cameraY = Minecraft.getMinecraft().thePlayer.posY - controller.getOriginY();
        cameraZ = Minecraft.getMinecraft().thePlayer.posZ - controller.getOriginZ();
        float previousLightX = OpenGlHelper.lastBrightnessX, previousLightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(
                x + controller.getOriginX() - tile.xCoord,
                y + controller.getOriginY() - tile.yCoord,
                z + controller.getOriginZ() - tile.zCoord);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .01F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            int light = controller.getWorldObj()
                .getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord + 1, tile.zCoord, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >> 16);
            String slug = controller.getMachineKind()
                .name()
                .toLowerCase(Locale.ROOT);
            double buildPhase = complete ? 0
                : controller.getJobProgress() / (double) Math.max(1, controller.getJobTotal());
            String constructionMode = controller.getState() == 3 ? "destroy"
                : controller.getState() == 1 || controller.getState() == 4 ? "build" : "idle";
            renderModel("loom", buildPhase, seconds, constructionMode);
            renderModel(slug, phase, seconds, hasBatch ? "build" : "idle");
            if (controller.getState() >= 2) renderPlugins(controller, phase, seconds);
            if (complete && controller.isStructureValidated()) {
                CriticalFX.render(slug, CriticalGeometry.getModel(slug), phase, seconds);
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightX, previousLightY);
            activeGroups = null;
        }
    }

    private void renderPlugins(TileEntityCriticalController controller, double phase, double seconds) {
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
            renderModel(
                model,
                phase,
                seconds,
                controller.getBatchId()
                    .isEmpty() ? "idle" : "build");
            GL11.glPopMatrix();
        }
    }

    private void renderModel(String slug, double phase, double seconds, String mode) {
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
                    animateTexture(mesh.animated, seconds);
                    GL11.glCallList(mesh.list);
                    finishTexture();
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
                        animateTexture(mesh.animated, seconds);
                        GL11.glCallList(mesh.list);
                        finishTexture();
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
        return activeGroups == null || slug.startsWith("plugin_") || activeGroups.contains(slug + ":" + mesh.group);
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
        matrix.clear();
        matrix.put(pose(animation, phase, seconds, mode))
            .flip();
        GL11.glMultMatrix(matrix);
    }

    private double[] pose(JsonObject animation, double phase, double seconds, String mode) {
        if (phase != posePhase || seconds != poseSeconds || !mode.equals(poseMode)) {
            groupMatrices.clear();
            posePhase = phase;
            poseSeconds = seconds;
            poseMode = mode;
        }
        double[] cached = groupMatrices.get(animation);
        if (cached != null) return cached;
        double[] origin = CriticalAnimation.transform(new double[3], animation, phase, seconds, mode);
        double[][] cols = new double[3][];
        for (int i = 0; i < 3; i++) {
            double[] unit = new double[3];
            unit[i] = 1;
            cols[i] = CriticalAnimation.transform(unit, animation, phase, seconds, mode);
            for (int j = 0; j < 3; j++) cols[i][j] -= origin[j];
        }
        // JSON positions are cube centers; world block coordinates are cube corners.
        matrix.clear();
        for (int i = 0; i < 3; i++) matrix.put(cols[i])
            .put(0);
        matrix.put(origin[0] + .5 - .5 * (cols[0][0] + cols[1][0] + cols[2][0]));
        matrix.put(origin[1] + .5 - .5 * (cols[0][1] + cols[1][1] + cols[2][1]));
        matrix.put(origin[2] + .5 - .5 * (cols[0][2] + cols[1][2] + cols[2][2]))
            .put(1)
            .flip();
        cached = new double[16];
        matrix.get(cached);
        matrix.rewind();
        groupMatrices.put(animation, cached);
        return cached;
    }

    private List<Mesh> compile(String slug) {
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

    private static final class Mesh {

        final int list;
        final JsonObject animation;
        final ResourceLocation texture, emission;
        final boolean animated, transparent;
        final int pass;
        final String group;
        final double[] center;
        final float strength;

        Mesh(int list, JsonObject animation, ResourceLocation texture, ResourceLocation emission, boolean animated,
            String alphaMode, float strength, String group, double[] center) {
            this.list = list;
            this.animation = animation;
            this.texture = texture;
            this.emission = emission;
            this.animated = animated;
            this.transparent = "blend".equals(alphaMode);
            this.pass = "blend".equals(alphaMode) ? 2 : "cutout".equals(alphaMode) ? 1 : 0;
            this.group = group;
            this.center = center;
            this.strength = strength;
        }
    }
}
