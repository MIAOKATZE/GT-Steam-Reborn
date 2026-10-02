package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Small immutable catalog; large metadata and voxel slices are loaded on demand. */
public final class RemasterCatalog {

    private static final Map<String, JsonObject> DESCRIPTORS = new LinkedHashMap<>();
    private static final List<String> IDS = new ArrayList<>();
    private static final JsonObject CONFIG;
    private static final Map<String, RemasterPrefab> CACHE = new LinkedHashMap<String, RemasterPrefab>(16, .75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RemasterPrefab> eldest) {
            return size() > 8;
        }
    };

    static {
        JsonObject catalog = read("catalog.json", false).getAsJsonObject();
        CONFIG = catalog;
        for (JsonElement e : catalog.getAsJsonArray("structures")) {
            IDS.add(
                e.getAsJsonObject()
                    .get("id")
                    .getAsString());
        }
        for (JsonElement e : catalog.getAsJsonArray("prefabs")) {
            JsonObject d = e.getAsJsonObject();
            DESCRIPTORS.put(
                key(
                    d.get("id")
                        .getAsString(),
                    d.get("variant")
                        .getAsInt()),
                d);
        }
    }

    private RemasterCatalog() {}

    private static String key(String id, int variant) {
        return id + "-v" + variant;
    }

    public static List<String> ids() {
        return Collections.unmodifiableList(IDS);
    }

    /** Authored static story/puzzle declarations. Callers must treat returned data as read-only. */
    public static JsonObject config() {
        return CONFIG;
    }

    public static int variants(String id) {
        int n = 0;
        while (DESCRIPTORS.containsKey(key(id, n))) n++;
        return n;
    }

    public static JsonObject descriptor(String id, int variant) {
        JsonObject d = DESCRIPTORS.get(key(id, variant));
        if (d == null) throw new IllegalArgumentException("Unknown remaster prefab: " + key(id, variant));
        return d;
    }

    public static synchronized RemasterPrefab get(String id, int variant) {
        String key = key(id, variant);
        RemasterPrefab p = CACHE.get(key);
        if (p == null) {
            p = new RemasterPrefab(
                read(
                    descriptor(id, variant).get("file")
                        .getAsString(),
                    true).getAsJsonObject());
            CACHE.put(key, p);
        }
        return p;
    }

    static JsonElement read(String path, boolean compressed) {
        String resource = "/assets/gtsr/remaster/" + path;
        InputStream stream = RemasterCatalog.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing remaster asset " + resource);
        try (Reader reader = new InputStreamReader(
            compressed ? new GZIPInputStream(stream) : stream,
            StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid remaster asset " + resource, e);
        }
    }
}
