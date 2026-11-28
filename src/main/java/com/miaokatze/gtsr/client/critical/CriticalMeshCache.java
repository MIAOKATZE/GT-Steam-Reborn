// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

import com.google.gson.JsonObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.miaokatze.gtsr.client.render.ClientMesh;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalGeometry.Voxel;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;

/** Immutable V2 terrain-atlas lists: one compilation, same-rigid-group CTM, native sprite ticking. */
final class CriticalMeshCache {

    private final Map<String, List<Mesh>> models = new HashMap<>();
    private final Map<CriticalMachineState, VirtualInstallation> installations = new java.util.IdentityHashMap<>();
    private final Map<CriticalMachineState, VirtualInstallation> physicalInstallations = new java.util.IdentityHashMap<>();
    private final Map<String, SharedVirtualModel> sharedVirtualModels = new HashMap<>();

    private static final class VirtualInstallation {

        final long revision;
        final Map<String, SharedVirtualModel> models = new HashMap<>();

        VirtualInstallation(long revision) {
            this.revision = revision;
        }
    }

    private static final class SharedVirtualModel {

        final String key;
        final List<Mesh> meshes;
        int references;

        SharedVirtualModel(String key, List<Mesh> meshes) {
            this.key = key;
            this.meshes = meshes;
        }
    }

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
            return compile(key, voxels, CriticalGeometry.getModel(base).animations, true, false);
        });
    }

    /** Only descriptor changes inspect installed cells. Stable frames retain local, shared rigid meshes. */
    List<Mesh> getVirtual(CriticalMachineState state, String slug) {
        VirtualInstallation installation = installations.get(state);
        if (installation == null || installation.revision != state.getVirtualVisualRevision()) {
            release(installations, state);
            installation = new VirtualInstallation(state.getVirtualVisualRevision());
            installations.put(state, installation);
        }
        SharedVirtualModel shared = installation.models.get(slug);
        if (shared == null) {
            List<Voxel> voxels = state.virtualVisualVoxels(slug);
            String key = fingerprint(slug, voxels);
            shared = sharedVirtualModels.get(key);
            if (shared == null) {
                boolean plugins = "plugins".equals(slug);
                String animationSlug = plugins ? CriticalGeometry.visualBaseSlug(state) : slug;
                JsonObject animations = "supply".equals(slug) ? new JsonObject()
                    : CriticalGeometry.getModel(animationSlug).animations;
                shared = new SharedVirtualModel(key, compile(slug, voxels, animations, plugins, true));
                sharedVirtualModels.put(key, shared);
            }
            shared.references++;
            installation.models.put(slug, shared);
        }
        return shared.meshes;
    }

    List<Mesh> getPhysical(CriticalMachineState state, String slug) {
        long stamp = (long) state.getVirtualConstructionVersion() << 56 | (long) state.getParallelPlugins() << 48
            | (long) state.getSpeedPlugins() << 40
            | (long) state.getEfficiencyPlugins() << 32;
        if (state.getVirtualConstructionVersion() == 0) stamp ^= state.getVirtualVisualRevision();
        VirtualInstallation installation = physicalInstallations.get(state);
        if (installation == null || installation.revision != stamp) {
            release(physicalInstallations, state);
            installation = new VirtualInstallation(stamp);
            physicalInstallations.put(state, installation);
        }
        SharedVirtualModel shared = installation.models.get(slug);
        if (shared == null) {
            boolean plugins = "plugins".equals(slug);
            boolean decorativePlugin = slug.startsWith("plugin_");
            List<Voxel> voxels = new ArrayList<>();
            for (Voxel voxel : plugins ? CriticalGeometry.physicalPlugins(state)
                : CriticalGeometry.getModel(slug).voxels)
                if ((decorativePlugin || CriticalGeometry.isDynamic(plugins ? voxel.model : slug, voxel.group))
                    && (decorativePlugin || com.miaokatze.gtsr.common.critical.CriticalLoomProxyVisibility
                        .isPhysicalProxy(state, voxel)))
                    voxels.add(voxel);
            String key = fingerprint(slug + ":physical", voxels);
            shared = sharedVirtualModels.get(key);
            if (shared == null) {
                JsonObject animations = CriticalGeometry
                    .getModel(plugins ? CriticalGeometry.visualBaseSlug(state) : slug).animations;
                shared = new SharedVirtualModel(key, compile(slug, voxels, animations, plugins, true));
                sharedVirtualModels.put(key, shared);
            }
            shared.references++;
            installation.models.put(slug, shared);
        }
        return shared.meshes;
    }

    void release(CriticalMachineState state) {
        RuntimeException cleanup = new IllegalStateException("Failed to release installation meshes");
        try {
            release(installations, state);
        } catch (RuntimeException | Error failure) {
            cleanup.addSuppressed(failure);
        }
        try {
            release(physicalInstallations, state);
        } catch (RuntimeException | Error failure) {
            cleanup.addSuppressed(failure);
        }
        if (cleanup.getSuppressed().length != 0) throw cleanup;
    }

    void releaseVirtual(CriticalMachineState state) {
        release(installations, state);
    }

    private void release(Map<CriticalMachineState, VirtualInstallation> owners, CriticalMachineState state) {
        VirtualInstallation installation = owners.remove(state);
        if (installation == null) return;
        List<ClientMesh> owned = new ArrayList<>();
        for (SharedVirtualModel shared : installation.models.values()) if (--shared.references == 0) {
            sharedVirtualModels.remove(shared.key, shared);
            collect(shared.meshes, owned);
        }
        close(owned);
    }

    private static String fingerprint(String slug, List<Voxel> voxels) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            for (Voxel voxel : voxels) {
                String cell = voxel.x + ","
                    + voxel.y
                    + ","
                    + voxel.z
                    + ","
                    + voxel.model
                    + ","
                    + voxel.group
                    + ","
                    + voxel.material
                    + ";";
                digest.update(cell.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            StringBuilder key = new StringBuilder(slug).append(':');
            for (byte value : digest.digest()) key.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    void clear() {
        List<ClientMesh> owned = new ArrayList<>();
        for (List<Mesh> model : models.values()) collect(model, owned);
        for (SharedVirtualModel model : sharedVirtualModels.values()) collect(model.meshes, owned);
        models.clear();
        installations.clear();
        physicalInstallations.clear();
        sharedVirtualModels.clear();
        close(owned);
    }

    private static void collect(List<Mesh> meshes, List<ClientMesh> owned) {
        for (Mesh mesh : meshes) {
            owned.add(mesh.albedo);
            if (mesh.emission != null) owned.add(mesh.emission);
        }
    }

    private static void close(List<ClientMesh> meshes) {
        RuntimeException failure = new IllegalStateException("Failed to release critical meshes");
        ClientMesh.rollback(meshes, failure);
        if (failure.getSuppressed().length != 0) throw failure;
    }

    private List<Mesh> compile(String slug) {
        CriticalGeometry.Model model = CriticalGeometry.getModel(slug);
        return compile(slug, model.voxels, model.animations, false, false);
    }

    private List<Mesh> compile(String slug, List<Voxel> voxels, JsonObject animations, boolean plugins,
        boolean virtual) {
        Map<Long, Voxel> occupied = new HashMap<>();
        for (Voxel voxel : voxels) occupied.put(voxel.positionKey(), voxel);
        Map<String, List<Face>> groups = new LinkedHashMap<>();
        for (Voxel voxel : voxels) {
            if (!virtual && !CriticalGeometry.isDynamic(plugins ? voxel.model : slug, voxel.group)) continue;
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
                boolean frozen = animations.has(voxel.group) && animations.getAsJsonObject(voxel.group)
                    .has("materialFramePolicy");
                boolean atlasMaterial = canonical(voxel.material).startsWith("v2_");
                IIcon color = atlasMaterial ? CriticalMaterialTextures.icon(voxel.material, side, mask, false, frozen)
                    : voxel.block.getIcon(side, voxel.metadata);
                IIcon emission = emissionStrength > 0
                    ? atlasMaterial ? CriticalMaterialTextures.icon(voxel.material, side, mask, true, frozen)
                        : side < 2 ? null
                            : net.minecraft.client.Minecraft.getMinecraft()
                                .getTextureMapBlocks()
                                .getAtlasSprite("gtsr:critical/" + canonical(voxel.material) + "_emission")
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
            result.add(
                new Mesh(
                    faces,
                    animations.has(first.voxel.group) ? animations.getAsJsonObject(first.voxel.group)
                        : new JsonObject(),
                    "blend".equals(mode) ? 2 : "cutout".equals(mode) ? 1 : 0,
                    (plugins ? first.voxel.model : slug) + ':' + first.voxel.group,
                    center(faces),
                    samples(faces)));
        }
        CriticalRenderMetrics.meshCompiles++;
        return result;
    }

    private static ClientMesh compileList(List<Face> faces, boolean emission) {
        if (emission) {
            boolean present = false;
            for (Face face : faces) if (face.emission != null) {
                present = true;
                break;
            }
            if (!present) return null;
        }
        return ClientMesh.capture(DefaultVertexFormat.POSITION_TEXTURE_COLOR_NORMAL, () -> {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            // Only the designed emission mask bypasses world light; moving albedo uses the same light as static blocks.
            for (Face face : faces) {
                IIcon icon = emission ? face.emission : face.color;
                if (icon == null) continue;
                int[] n = CriticalMaterialTextures.NORMALS[face.side], u = CriticalMaterialTextures.U[face.side],
                    v = CriticalMaterialTextures.V[face.side];
                tessellator.setNormal(n[0], n[1], n[2]);
                float shade = face.side == 0 ? .58F : face.side == 1 ? 1 : face.side < 4 ? .78F : .88F;
                if (emission) shade = face.emissionStrength;
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
        });
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

        ClientMesh albedo, emission;
        private List<Face> source;
        final int pass;
        final JsonObject animation;
        final String visibilityKey;
        final double[] center;
        final double[][] lightSamples;

        Mesh(List<Face> source, JsonObject animation, int pass, String visibilityKey, double[] center,
            double[][] lightSamples) {
            this.source = source;
            this.animation = animation;
            this.pass = pass;
            this.visibilityKey = visibilityKey;
            this.center = center;
            this.lightSamples = lightSamples;
        }

        boolean prepare() {
            if (source == null) return albedo != null;
            if (!com.miaokatze.gtsr.client.render.ClientRenderFrame.tryBuild()) return false;
            ClientMesh color = compileList(source, false);
            try {
                ClientMesh glow = compileList(source, true);
                albedo = color;
                emission = glow;
                source = null;
                return true;
            } catch (RuntimeException | Error failure) {
                try {
                    color.close();
                } catch (RuntimeException | Error cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
    }
}
