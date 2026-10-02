package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Authored local coordinates, explicit air, and chunk-local X runs. */
public final class RemasterPrefab {

    public final String id;
    public final int variant;
    public final JsonObject metadata;
    public final String[] palette;
    public final int[] min, max, extent, nominal;
    private final Map<Long, String> files = new HashMap<>();
    private static final Map<String, List<Run>> SLICE_CACHE = new LinkedHashMap<String, List<Run>>(64, .75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Run>> eldest) {
            return size() > 64;
        }
    };

    public static final class Run {

        public final int x, y, z, length, paletteIndex;

        Run(JsonArray row) {
            x = row.get(0)
                .getAsInt();
            y = row.get(1)
                .getAsInt();
            z = row.get(2)
                .getAsInt();
            length = row.get(3)
                .getAsInt();
            paletteIndex = row.get(4)
                .getAsInt();
        }
    }

    RemasterPrefab(JsonObject data) {
        id = data.get("id")
            .getAsString();
        variant = data.get("variant")
            .getAsInt();
        metadata = data.getAsJsonObject("metadata");
        min = ints(data.getAsJsonArray("min"));
        max = ints(data.getAsJsonArray("max"));
        extent = ints(data.getAsJsonArray("extent"));
        nominal = ints(data.getAsJsonArray("nominal"));
        JsonArray names = data.getAsJsonArray("palette");
        palette = new String[names.size()];
        for (int i = 0; i < names.size(); i++) palette[i] = names.get(i)
            .getAsString();
        for (JsonElement e : data.getAsJsonArray("slices")) {
            JsonObject s = e.getAsJsonObject();
            files.put(
                key(
                    s.get("x")
                        .getAsInt(),
                    s.get("z")
                        .getAsInt()),
                s.get("file")
                    .getAsString());
        }
    }

    private static int[] ints(JsonArray a) {
        int[] values = new int[a.size()];
        for (int i = 0; i < a.size(); i++) values[i] = a.get(i)
            .getAsInt();
        return values;
    }

    private static long key(int x, int z) {
        return (long) x << 32 ^ (z & 0xffffffffL);
    }

    /** Query one local 16x16 column. Returned lists are immutable; absent slices are empty. */
    public List<Run> slice(int localCx, int localCz) {
        String file = files.get(key(localCx, localCz));
        if (file == null) return Collections.emptyList();
        synchronized (SLICE_CACHE) {
            List<Run> runs = SLICE_CACHE.get(file);
            if (runs == null) {
                List<Run> loaded = new ArrayList<>();
                for (JsonElement row : RemasterCatalog.read(file, true)
                    .getAsJsonArray()) {
                    loaded.add(new Run(row.getAsJsonArray()));
                }
                runs = Collections.unmodifiableList(loaded);
                SLICE_CACHE.put(file, runs);
            }
            return runs;
        }
    }
}
