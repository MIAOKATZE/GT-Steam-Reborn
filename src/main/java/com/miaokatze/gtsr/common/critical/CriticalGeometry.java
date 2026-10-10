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
            int stage = slug.equals("loom") ? 0 : slug.startsWith("base_") ? Math.min(3, y / 12 + 1) : 4;
            voxels.add(new Voxel(x, y, z, CriticalMaterials.block(material, dynamic), stage, material, group, slug));
        }
        Model model = new Model(Collections.unmodifiableList(voxels), animations, json.getAsJsonObject("meta"));
        MODELS.put(slug, model);
        return model;
    }

    public static List<Voxel> getLoom() {
        return getModel("loom").voxels;
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
