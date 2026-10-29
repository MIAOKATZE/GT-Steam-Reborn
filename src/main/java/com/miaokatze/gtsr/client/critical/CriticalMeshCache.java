package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalGeometry.Voxel;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;

/** Immutable V2 terrain-atlas lists: one compilation, same-rigid-group CTM, native sprite ticking. */
final class CriticalMeshCache {

    // Non-emissive moving bodies retain V37's dim colored relief without becoming fullbright materials.
    // Static structure blocks never enter this cache and keep their normal world-light response.
    private static final float BODY_EMISSION = .18F;
    private final Map<String, List<Mesh>> models = new HashMap<>();
    private static final double[][][] CORNERS = { { { 0, 0, 1 }, { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 } },
        { { 0, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 } }, { { 1, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 }, { 0, 1, 0 } },
        { { 0, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 } }, { { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 1 } },
        { { 1, 1, 1 }, { 1, 0, 1 }, { 1, 0, 0 }, { 1, 1, 0 } } };

    List<Mesh> get(String slug) {
        return models.computeIfAbsent(slug, this::compile);
    }

    List<Mesh> getPlugins(CriticalMachineState state) {
        String base = CriticalGeometry.visualBaseSlug(state);
        String key = base + ":plugins:"
            + state.getParallelPlugins()
            + ':'
            + state.getSpeedPlugins()
            + ':'
            + state.getEfficiencyPlugins();
        return models.computeIfAbsent(key, ignored -> {
            List<Voxel> voxels = CriticalGeometry.physicalPlugins(state);
            return compile(key, voxels, CriticalGeometry.getModel(base).animations, true);
        });
    }

    void clear() {
        for (List<Mesh> model : models.values()) for (Mesh mesh : model) {
            GL11.glDeleteLists(mesh.albedo, 1);
            if (mesh.emission != 0) GL11.glDeleteLists(mesh.emission, 1);
        }
        models.clear();
    }

    private List<Mesh> compile(String slug) {
        CriticalGeometry.Model model = CriticalGeometry.getModel(slug);
        return compile(slug, model.voxels, model.animations, false);
    }

    private List<Mesh> compile(String slug, List<Voxel> voxels, JsonObject animations, boolean plugins) {
        Map<Long, Voxel> occupied = new HashMap<>();
        for (Voxel voxel : voxels) occupied.put(voxel.positionKey(), voxel);
        Map<String, List<Face>> groups = new LinkedHashMap<>();
        for (Voxel voxel : voxels) {
            if (!CriticalGeometry.isDynamic(plugins ? voxel.model : slug, voxel.group)) continue;
            // Controller markers are real GT hosts. Hatch/bus materials are decorative MaterialBlocks;
            // their fixed interface group stays in chunk rendering, never in this moving mesh.
            if (voxel.material.equals("v2_gt_controller") || voxel.material.equals("v2_loom_machine_controller"))
                continue;
            JsonObject material = CriticalMaterials.properties(voxel.material);
            String mode = material.get("alphaMode")
                .getAsString();
            float emissionStrength = material.get("emissionStrength")
                .getAsFloat();
            for (int side = 0; side < 6; side++) {
                int[] n = CriticalMaterialTextures.NORMALS[side];
                Voxel neighbor = occupied.get(Voxel.positionKey(voxel.x + n[0], voxel.y + n[1], voxel.z + n[2]));
                if (neighbor != null && neighbor.group.equals(voxel.group)
                    && ("opaque".equals(
                        CriticalMaterials.properties(neighbor.material)
                            .get("alphaMode")
                            .getAsString())
                        || canonical(neighbor.material).equals(canonical(voxel.material))))
                    continue;
                int mask = mask(voxel, side, occupied, material);
                boolean frozen = animations.getAsJsonObject(voxel.group)
                    .has("materialFramePolicy");
                IIcon color = CriticalMaterialTextures.icon(voxel.material, side, mask, false, frozen);
                IIcon emission = emissionStrength > 0
                    ? CriticalMaterialTextures.icon(voxel.material, side, mask, true, frozen)
                    : null;
                groups.computeIfAbsent(voxel.group + ':' + mode, ignored -> new ArrayList<>())
                    .add(new Face(voxel, side, color, emission, emissionStrength));
            }
        }
        List<Mesh> result = new ArrayList<>();
        for (List<Face> faces : groups.values()) {
            Face first = faces.get(0);
            String mode = CriticalMaterials.properties(first.voxel.material)
                .get("alphaMode")
                .getAsString();
            int albedo = compileList(faces, false), emission = compileList(faces, true);
            result.add(
                new Mesh(
                    albedo,
                    emission,
                    animations.getAsJsonObject(first.voxel.group),
                    "blend".equals(mode) ? 2 : "cutout".equals(mode) ? 1 : 0,
                    (plugins ? first.voxel.model : slug) + ':' + first.voxel.group,
                    center(faces),
                    samples(faces)));
        }
        CriticalRenderMetrics.meshCompiles++;
        return result;
    }

    private static int compileList(List<Face> faces, boolean emission) {
        int list = GL11.glGenLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // Block light emission does not make the albedo fullbright: only the designed mask bypasses world light.
        // Keep the existing body/mask batch; ordinary moving bodies retain V37's weak colored relief.
        for (int layer = 0; layer < (emission ? 2 : 1); layer++) for (Face face : faces) {
            IIcon icon = layer == 1 ? face.emission : face.color;
            if (icon == null) continue;
            int[] n = CriticalMaterialTextures.NORMALS[face.side], u = CriticalMaterialTextures.U[face.side],
                v = CriticalMaterialTextures.V[face.side];
            tessellator.setNormal(n[0], n[1], n[2]);
            float shade = face.side == 0 ? .58F : face.side == 1 ? 1 : face.side < 4 ? .78F : .88F;
            if (emission)
                shade = layer == 1 ? face.emissionStrength : shade * (face.emissionStrength > 0 ? 0 : BODY_EMISSION);
            tessellator.setColorOpaque_F(shade, shade, shade);
            for (double[] corner : CORNERS[face.side]) {
                double du = .5, dv = .5;
                for (int axis = 0; axis < 3; axis++) {
                    du += (corner[axis] - .5) * u[axis];
                    dv += (corner[axis] - .5) * v[axis];
                }
                tessellator.addVertexWithUV(
                    face.voxel.x + corner[0],
                    face.voxel.y + corner[1],
                    face.voxel.z + corner[2],
                    icon.getInterpolatedU(du * 16),
                    icon.getInterpolatedV(dv * 16));
            }
        }
        tessellator.draw();
        GL11.glEndList();
        return list;
    }

    static int mask(Voxel voxel, int side, Map<Long, Voxel> occupied, JsonObject properties) {
        if (!properties.has("ctm")) return 0;
        JsonObject rule = properties.getAsJsonObject("ctm");
        int allowed = rule.getAsJsonObject("faceAllowedMask")
            .get(CriticalMaterialTextures.FACE_NAMES[side])
            .getAsInt();
        int[] u = CriticalMaterialTextures.U[side], v = CriticalMaterialTextures.V[side],
            n = CriticalMaterialTextures.NORMALS[side];
        int[][] directions = { { -v[0], -v[1], -v[2] }, u, v, { -u[0], -u[1], -u[2] } };
        int mask = 0;
        for (int edge = 0; edge < 4; edge++) {
            int[] d = directions[edge];
            int x = voxel.x + d[0], y = voxel.y + d[1], z = voxel.z + d[2];
            Voxel neighbor = occupied.get(Voxel.positionKey(x, y, z));
            if (neighbor != null && neighbor.group.equals(voxel.group)
                && canonical(neighbor.material).equals(canonical(voxel.material))
                && !occupied.containsKey(Voxel.positionKey(x + n[0], y + n[1], z + n[2]))) mask |= 1 << edge;
        }
        return mask & allowed;
    }

    private static String canonical(String material) {
        return CriticalMaterials.properties(material)
            .get("id")
            .getAsString();
    }

    private static double[] center(List<Face> faces) {
        double[] center = new double[3];
        for (Face face : faces) {
            center[0] += face.voxel.x + .5;
            center[1] += face.voxel.y + .5;
            center[2] += face.voxel.z + .5;
        }
        for (int axis = 0; axis < 3; axis++) center[axis] /= faces.size();
        return center;
    }

    private static double[][] samples(List<Face> faces) {
        double[][] samples = new double[Math.min(4, faces.size())][3];
        for (int i = 0; i < samples.length; i++) {
            Face face = faces.get(i * faces.size() / samples.length);
            int[] n = CriticalMaterialTextures.NORMALS[face.side];
            samples[i] = new double[] { face.voxel.x + .5 + n[0], face.voxel.y + .5 + n[1], face.voxel.z + .5 + n[2] };
        }
        return samples;
    }

    private static final class Face {

        final Voxel voxel;
        final int side;
        final IIcon color, emission;
        final float emissionStrength;

        Face(Voxel voxel, int side, IIcon color, IIcon emission, float emissionStrength) {
            this.voxel = voxel;
            this.side = side;
            this.color = color;
            this.emission = emission;
            this.emissionStrength = emissionStrength;
        }
    }

    static final class Mesh {

        final int albedo, emission, pass;
        final JsonObject animation;
        final String visibilityKey;
        final double[] center;
        final double[][] lightSamples;

        Mesh(int albedo, int emission, JsonObject animation, int pass, String visibilityKey, double[] center,
            double[][] lightSamples) {
            this.albedo = albedo;
            this.emission = emission;
            this.animation = animation;
            this.pass = pass;
            this.visibilityKey = visibilityKey;
            this.center = center;
            this.lightSamples = lightSamples;
        }
    }
}
