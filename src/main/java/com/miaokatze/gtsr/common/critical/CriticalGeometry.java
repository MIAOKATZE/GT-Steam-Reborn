package com.miaokatze.gtsr.common.critical;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Immutable canonical voxel data; no Minecraft client or GL dependency. */
public final class CriticalGeometry {

    private static final Map<String, Model> MODELS = new HashMap<>();
    private static final Map<String, List<Voxel>> STRUCTURES = new HashMap<>();

    private CriticalGeometry() {}

    public static JsonObject readJson(String relative) {
        String path = "/assets/gtsr/critical/" + relative;
        try (InputStream stream = CriticalGeometry.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing critical asset " + path);
            return new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot read critical asset " + path, ex);
        }
    }

    public static synchronized Model getModel(String slug) {
        Model cached = MODELS.get(slug);
        if (cached != null) return cached;
        JsonObject json = readJson("models/" + slug + ".geometry.json");
        JsonObject animations = json.getAsJsonObject("animationGroups");
        List<Voxel> voxels = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("blocks")) {
            JsonArray row = element.getAsJsonArray();
            int x = row.get(0)
                .getAsInt(),
                y = row.get(1)
                    .getAsInt(),
                z = row.get(2)
                    .getAsInt();
            String material = row.get(3)
                .getAsString(),
                group = row.get(4)
                    .getAsString();
            boolean dynamic = animations.has(group);
            int stage = slug.endsWith("loom") ? 0
                : slug.contains("base_") ? Math.min(3, y / 16 + 1) : slug.startsWith("v2_plugin_") ? 3 : 4;
            if (!slug.startsWith("v2_"))
                stage = slug.equals("loom") ? 0 : slug.startsWith("base_") ? Math.min(3, y / 12 + 1) : 4;
            voxels.add(new Voxel(x, y, z, CriticalMaterials.block(material, dynamic), stage, material, group, slug));
        }
        Model model = new Model(Collections.unmodifiableList(voxels), animations, json.getAsJsonObject("meta"));
        MODELS.put(slug, model);
        return model;
    }

    public static List<Voxel> getLoom() {
        return getModel("loom").voxels;
    }

    public static boolean isDynamic(String slug, String group) {
        return getModel(slug).animations.has(group);
    }

    public static boolean isV2(CriticalMachineState state) {
        return state.getGeometryVersion() == 2;
    }

    public static String visualLoomSlug(CriticalMachineState state) {
        return isV2(state) ? state.usesLegacyV2Geometry() ? "v2_legacy_loom"
            : state.getV2LayoutRevision() == 25 ? "v2_v25_loom"
                : state.getV2LayoutRevision() <= 27 ? "v2_v27_loom" : "v2_loom"
            : "loom";
    }

    public static String visualBaseSlug(CriticalMachineState state) {
        return isV2(state)
            ? state.usesLegacyV2Geometry() ? "v2_legacy_base_t3"
                : state.getV2LayoutRevision() == 25 ? "v2_v25_base_t3"
                    : state.getV2LayoutRevision() <= 27 ? "v2_v27_base_t3" : "v2_base_t3"
            : "base_" + state.getTier()
                .name()
                .toLowerCase(Locale.ROOT);
    }

    public static String visualMachineSlug(CriticalMachineState state) {
        return isV2(state) ? (state.getKind() == CriticalMachineKind.SOLAR ? "v2_solar" : "v2_sun")
            : state.getKind()
                .name()
                .toLowerCase(Locale.ROOT);
    }

    public static List<Voxel> getLoom(CriticalMachineState state) {
        return getModel(visualLoomSlug(state)).voxels;
    }

    /** Exact inverse sampling used by the frozen design's integer-world addon exporter. */
    public static List<Voxel> physicalPlugins(CriticalConfiguration config) {
        return physicalPlugins(config, "v2_base_t3");
    }

    public static List<Voxel> physicalPlugins(CriticalMachineState state) {
        return physicalPlugins(state.getConfiguration(), visualBaseSlug(state));
    }

    private static List<Voxel> physicalPlugins(CriticalConfiguration config, String baseSlug) {
        List<Voxel> result = new ArrayList<>();
        JsonArray mounts = getModel(baseSlug).meta.getAsJsonObject("attachmentPlacement")
            .getAsJsonArray("mounts");
        int[] counts = { config.parallel, config.speed, config.economy };
        String[] kinds = { "parallel", "speed", "efficiency" };
        int slot = 0;
        for (int kind = 0; kind < 3; kind++) for (int n = 0; n < counts[kind]; n++, slot++) {
            String slug = "v2_plugin_" + kinds[kind];
            Model model = getModel(slug);
            JsonObject mount = mounts.get(slot)
                .getAsJsonObject();
            JsonArray pos = mount.getAsJsonArray("position");
            int mx = pos.get(0)
                .getAsInt(),
                my = pos.get(1)
                    .getAsInt(),
                mz = pos.get(2)
                    .getAsInt();
            double angle = mount.get("yawRadians")
                .getAsDouble(), co = Math.cos(angle), si = Math.sin(angle);
            Map<Long, Voxel> cells = new HashMap<>();
            for (Voxel v : model.voxels) cells.put(v.positionKey(), v);
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE,
                minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (Voxel v : model.voxels) {
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
                minZ = Math.min(minZ, v.z);
                maxZ = Math.max(maxZ, v.z);
            }
            double lowX = Double.POSITIVE_INFINITY, highX = Double.NEGATIVE_INFINITY, lowZ = Double.POSITIVE_INFINITY,
                highZ = Double.NEGATIVE_INFINITY;
            for (double x : new double[] { minX - .5, maxX + .5 })
                for (double z : new double[] { minZ - .5, maxZ + .5 }) {
                    double dx = x * co + z * si, dz = -x * si + z * co;
                    lowX = Math.min(lowX, dx);
                    highX = Math.max(highX, dx);
                    lowZ = Math.min(lowZ, dz);
                    highZ = Math.max(highZ, dz);
                }
            for (int dx = (int) Math.ceil(lowX - 1e-8); dx < Math.ceil(highX - 1e-8); dx++)
                for (int dz = (int) Math.ceil(lowZ - 1e-8); dz < Math.ceil(highZ - 1e-8); dz++)
                    for (int dy = minY; dy <= maxY; dy++) {
                        int sx = (int) Math.floor(dx * co - dz * si + .5 + 1e-8);
                        int sz = (int) Math.floor(dx * si + dz * co + .5 + 1e-8);
                        Voxel source = cells.get(Voxel.positionKey(sx, dy, sz));
                        if (source != null) result.add(
                            new Voxel(
                                mx + dx,
                                my + dy,
                                mz + dz,
                                source.block,
                                3,
                                source.material,
                                "static_plugin",
                                slug));
                    }
        }
        return result;
    }

    public static synchronized List<Voxel> get(CriticalTier tier, CriticalMachineKind kind) {
        String base = "base_" + tier.name()
            .toLowerCase(Locale.ROOT);
        String machine = kind.name()
            .toLowerCase(Locale.ROOT);
        String key = base + ":" + machine;
        List<Voxel> cached = STRUCTURES.get(key);
        if (cached != null) return cached;
        List<Voxel> result = new ArrayList<>(getModel(base).voxels);
        result.addAll(getModel(machine).voxels);
        result.removeIf(v -> v.x == 40 && v.y == 0 && v.z == 32);
        result.sort(
            java.util.Comparator.comparingInt((Voxel v) -> v.stage)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.x)
                .thenComparingInt(v -> v.z));
        cached = Collections.unmodifiableList(result);
        STRUCTURES.put(key, cached);
        return cached;
    }

    public static final class Model {

        public final List<Voxel> voxels;
        public final JsonObject animations, meta;

        private Model(List<Voxel> voxels, JsonObject animations, JsonObject meta) {
            this.voxels = voxels;
            this.animations = animations;
            this.meta = meta;
        }
    }

    public static final class Voxel {

        public final int x, y, z, metadata = 0, stage;
        public final Block block;
        public final String material, group, model;

        private Voxel(int x, int y, int z, Block block, int stage, String material, String group, String model) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.stage = stage;
            this.material = material;
            this.group = group;
            this.model = model;
        }

        public long positionKey() {
            return positionKey(x, y, z);
        }

        public static long positionKey(int x, int y, int z) {
            return ((long) x & 0xfffff) << 40 | ((long) y & 0xfffff) << 20 | ((long) z & 0xfffff);
        }
    }
}
