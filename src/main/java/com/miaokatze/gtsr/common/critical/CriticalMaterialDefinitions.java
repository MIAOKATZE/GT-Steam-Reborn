package com.miaokatze.gtsr.common.critical;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** One immutable definition per registered material; geometry names are aliases, never extra identities. */
public final class CriticalMaterialDefinitions {

    private CriticalMaterialDefinitions() {}

    private static final class Loaded {

        private static final Catalog CATALOG = load();
    }

    public static Catalog catalog() {
        return Loaded.CATALOG;
    }

    public static Definition definition(String aliasOrId) {
        return catalog().definition(aliasOrId);
    }

    public static String canonicalId(String aliasOrId) {
        return definition(aliasOrId).id;
    }

    private static Catalog load() {
        String path = "/assets/gtsr/critical/materials.json";
        try (InputStream stream = CriticalMaterialDefinitions.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing critical asset " + path);
            return fromJson(
                new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot read critical asset " + path, ex);
        }
    }

    /** Also accepts the historical flat alias palette used by tools and saved fixtures. */
    public static Catalog fromJson(JsonObject source) {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        boolean normalized = source.has("schemaVersion");
        if (normalized && source.get("schemaVersion")
            .getAsInt() != 2) throw new IllegalArgumentException("Unsupported critical material schema");
        JsonObject rows = normalized ? source.getAsJsonObject("definitions") : source;
        for (Map.Entry<String, JsonElement> entry : rows.entrySet()) {
            JsonObject value = entry.getValue()
                .getAsJsonObject();
            String id = value.get("id")
                .getAsString();
            if (normalized && !id.equals(entry.getKey()))
                throw new IllegalArgumentException("Critical material definition key differs from id: " + id);
            Definition previous = definitions.get(id);
            if (previous == null) definitions.put(id, new Definition(value));
            else if (!previous.data.equals(value))
                throw new IllegalArgumentException("Conflicting critical material alias: " + entry.getKey());
            if (!normalized) aliases.put(entry.getKey(), id);
        }
        if (normalized) for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject("aliases")
            .entrySet())
            aliases.put(
                entry.getKey(),
                entry.getValue()
                    .getAsString());
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (!definitions.containsKey(entry.getValue()))
                throw new IllegalArgumentException("Unknown critical material target: " + entry.getKey());
            if (definitions.containsKey(entry.getKey()) && !entry.getKey()
                .equals(entry.getValue()))
                throw new IllegalArgumentException("Critical material alias shadows identity: " + entry.getKey());
        }
        return new Catalog(definitions, aliases);
    }

    /** Compatibility adapter for CriticalGeometry.readJson and historical Java fixtures. */
    public static JsonObject expandLegacyPalette(JsonObject source) {
        return source.has("schemaVersion") ? fromJson(source).legacyPalette() : source;
    }

    private static JsonObject copy(JsonObject source) {
        return new JsonParser().parse(source.toString())
            .getAsJsonObject();
    }

    public static final class Catalog {

        private final Map<String, Definition> definitions;
        private final Map<String, String> aliases;
        private final Map<String, JsonObject> legacyCanonical = new LinkedHashMap<>();
        private final JsonObject legacyPalette = new JsonObject();

        private Catalog(Map<String, Definition> definitions, Map<String, String> aliases) {
            this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
            this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
            for (Definition definition : definitions.values())
                legacyCanonical.put(definition.id, definition.properties());
            for (Map.Entry<String, String> entry : aliases.entrySet()) legacyPalette.add(
                entry.getKey(),
                definitions.get(entry.getValue())
                    .properties());
        }

        public Map<String, Definition> definitions() {
            return definitions;
        }

        public Map<String, String> aliases() {
            return aliases;
        }

        public Definition definition(String aliasOrId) {
            Definition result = definitions.get(aliases.getOrDefault(aliasOrId, aliasOrId));
            if (result == null) throw new IllegalArgumentException("Unknown critical material: " + aliasOrId);
            return result;
        }

        /** Cached legacy view: callers must treat it as read-only, just as the old palette API. */
        public JsonObject legacyPalette() {
            return legacyPalette;
        }

        public JsonObject legacyProperties(String aliasOrId) {
            JsonElement value = legacyPalette.get(aliasOrId);
            return value == null ? legacyCanonical.get(definition(aliasOrId).id) : value.getAsJsonObject();
        }
    }

    /** No mutable Gson objects escape this definition; the legacy API has a separate cached view. */
    public static final class Definition {

        public final String id, alphaMode;
        public final boolean transparent, animated, emissive;
        public final double emissionStrength;
        public final int lightValue, lightOpacity, frameCount, ticksPerFrame;
        public final List<String> tooltipKeys;
        private final JsonObject data;
        private final Map<String, SpritePair> sprites, ctmSprites;
        private final Map<String, FaceSprite> faceSprites;
        private final Map<String, String> ctmFaceGroups;
        private final Map<String, Integer> ctmMasks;

        private Definition(JsonObject source) {
            data = copy(source);
            id = source.get("id")
                .getAsString();
            alphaMode = source.get("alphaMode")
                .getAsString();
            transparent = !"opaque".equals(alphaMode);
            animated = source.get("animated")
                .getAsBoolean();
            emissionStrength = source.get("emissionStrength")
                .getAsDouble();
            emissive = emissionStrength > 0;
            lightValue = integer(source, "lightValue", 0);
            lightOpacity = integer(source, "lightOpacity", transparent ? 0 : 255);
            frameCount = integer(source, "frameCount", 1);
            ticksPerFrame = integer(source, "ticksPerFrame", 8);
            if (lightValue < 0 || lightValue > 15)
                throw new IllegalArgumentException("Invalid critical light level: " + id);
            List<String> keys = new ArrayList<>();
            if (source.has("tooltipKeys"))
                for (JsonElement key : source.getAsJsonArray("tooltipKeys")) keys.add(key.getAsString());
            tooltipKeys = Collections.unmodifiableList(keys);
            Map<String, SpritePair> pairs = new LinkedHashMap<>(), connections = new LinkedHashMap<>();
            Map<String, FaceSprite> faces = new LinkedHashMap<>();
            Map<String, String> groups = new LinkedHashMap<>();
            Map<String, Integer> masks = new LinkedHashMap<>();
            if (source.has("sprites")) for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject("sprites")
                .entrySet())
                pairs.put(
                    entry.getKey(),
                    new SpritePair(
                        entry.getValue()
                            .getAsJsonObject()));
            if (source.has("faceSprites"))
                for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject("faceSprites")
                    .entrySet()) {
                        JsonObject face = entry.getValue()
                            .getAsJsonObject();
                        faces.put(
                            entry.getKey(),
                            new FaceSprite(
                                face.get("material")
                                    .getAsString(),
                                face.get("group")
                                    .getAsString()));
                    }
            if (source.has("ctm")) {
                JsonObject ctm = source.getAsJsonObject("ctm");
                for (Map.Entry<String, JsonElement> entry : ctm.getAsJsonObject("faceLookup")
                    .entrySet())
                    groups.put(
                        entry.getKey(),
                        entry.getValue()
                            .getAsString());
                for (Map.Entry<String, JsonElement> entry : ctm.getAsJsonObject("faceAllowedMask")
                    .entrySet())
                    masks.put(
                        entry.getKey(),
                        entry.getValue()
                            .getAsInt());
                for (Map.Entry<String, JsonElement> group : ctm.getAsJsonObject("sprites")
                    .entrySet())
                    for (Map.Entry<String, JsonElement> mask : group.getValue()
                        .getAsJsonObject()
                        .entrySet())
                        connections.put(
                            group.getKey() + ':' + mask.getKey(),
                            new SpritePair(
                                mask.getValue()
                                    .getAsJsonObject()));
            }
            sprites = Collections.unmodifiableMap(pairs);
            ctmSprites = Collections.unmodifiableMap(connections);
            faceSprites = Collections.unmodifiableMap(faces);
            ctmFaceGroups = Collections.unmodifiableMap(groups);
            ctmMasks = Collections.unmodifiableMap(masks);
        }

        public JsonObject properties() {
            return copy(data);
        }

        public boolean hasSprites() {
            return !sprites.isEmpty();
        }

        public boolean hasFaceSprites() {
            return !faceSprites.isEmpty();
        }

        public boolean hasCtm() {
            return !ctmFaceGroups.isEmpty();
        }

        public Map<String, SpritePair> sprites() {
            return sprites;
        }

        public Map<String, SpritePair> ctmSprites() {
            return ctmSprites;
        }

        public SpritePair sprite(String group) {
            return sprites.get(group);
        }

        public SpritePair ctmSprite(String group, int mask) {
            return ctmSprites.get(group + ':' + mask);
        }

        public FaceSprite faceSprite(String face) {
            return faceSprites.get(face);
        }

        public String ctmFaceGroup(String face) {
            return ctmFaceGroups.get(face);
        }

        public int ctmAllowedMask(String face) {
            return ctmMasks.getOrDefault(face, 0);
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key)
            .getAsInt() : fallback;
    }

    public static final class SpritePair {

        public final String albedo, emission, frozenAlbedo, frozenEmission;

        private SpritePair(JsonObject source) {
            albedo = string(source, "albedo");
            emission = string(source, "emission");
            frozenAlbedo = string(source, "frozenAlbedo");
            frozenEmission = string(source, "frozenEmission");
        }

        public String texture(boolean emissive, boolean frozen) {
            return frozen ? emissive ? frozenEmission : frozenAlbedo : emissive ? emission : albedo;
        }

        private static String string(JsonObject source, String key) {
            JsonElement value = source.get(key);
            return value == null || value.isJsonNull() ? null : value.getAsString();
        }
    }

    public static final class FaceSprite {

        public final String material, group;

        private FaceSprite(String material, String group) {
            this.material = material;
            this.group = group;
        }
    }
}
