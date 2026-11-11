// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
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
import com.google.gson.stream.JsonReader;
import com.miaokatze.gtsr.common.critical.CriticalJsonData;

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

    private static final Map<String, JsonObject> ENTRANCES = new LinkedHashMap<String, JsonObject>(128, .75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, JsonObject> e) {
            return size() > 128;
        }
    };

    /** Reads only entrance metadata; voxel slices never become prefab runs during a lookup. */
    public static synchronized JsonObject entrance(String id, int variant) {
        String key = key(id, variant);
        if (ENTRANCES.containsKey(key)) return ENTRANCES.get(key);
        String path = descriptor(id, variant).get("file")
            .getAsString();
        JsonObject entry = null;
        try (JsonReader reader = new JsonReader(openReader(path, true))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if ("metadata".equals(reader.nextName())) {
                    JsonObject metadata = new JsonParser().parse(reader)
                        .getAsJsonObject();
                    entry = metadata.getAsJsonObject("surfaceEntrance");
                    if (entry == null) entry = metadata.getAsJsonObject("productionSurfaceEntrance");
                    break;
                }
                reader.skipValue();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Invalid remaster entrance " + path, e);
        }
        ENTRANCES.put(key, entry);
        return entry;
    }

    static JsonElement read(String path, boolean compressed) {
        try (Reader reader = openReader(path, compressed)) {
            return new JsonParser().parse(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid remaster asset " + path, e);
        }
    }

    /** Originals win; a malformed original must never be hidden by a compiled fallback. */
    private static Reader openReader(String path, boolean compressed) throws IOException {
        String resource = "/assets/gtsr/remaster/" + path;
        InputStream stream = RemasterCatalog.class.getResourceAsStream(resource);
        if (stream != null) {
            try {
                return new InputStreamReader(compressed ? new GZIPInputStream(stream) : stream, StandardCharsets.UTF_8);
            } catch (IOException e) {
                try {
                    stream.close();
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }
        }
        if (compressed && path.endsWith(".json.gz")) {
            String binary = resource.substring(0, resource.length() - ".json.gz".length()) + ".gcjb";
            InputStream compiled = RemasterCatalog.class.getResourceAsStream(binary);
            if (compiled != null) {
                try (InputStream input = compiled) {
                    return new StringReader(CriticalJsonData.read(input));
                } catch (IOException e) {
                    throw new IOException("Invalid compiled remaster asset " + binary, e);
                }
            }
        }
        throw new IOException("Missing remaster asset " + resource);
    }
}
