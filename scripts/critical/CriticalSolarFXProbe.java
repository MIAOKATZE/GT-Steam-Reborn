package com.miaokatze.gtsr.client.critical;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Runs the production sampler against actual browser output, without a Minecraft or GL stub. */
public final class CriticalSolarFXProbe {

    private static double error;
    private static long particles;

    public static void main(String[] args) throws Exception {
        JsonArray cases = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        JsonObject[] metas = new JsonObject[3], animations = new JsonObject[3];
        String[] slugs = { "base_t3", "loom", "solar" };
        String prefix = args.length > 2 ? args[2] : "";
        for (int i = 0; i < 3; i++) {
            JsonObject model = JsonParser.parseString(Files.readString(
                Path.of(args[1], prefix + slugs[i] + ".geometry.json"))).getAsJsonObject();
            metas[i] = model.getAsJsonObject("meta");
            animations[i] = model.getAsJsonObject("animationGroups");
        }
        Map<String, Integer> nearCounts = new LinkedHashMap<>();
        for (JsonElement entry : cases) {
            JsonObject row = entry.getAsJsonObject();
            JsonArray expected = row.getAsJsonArray("expected");
            int[] cursor = { 0 };
            double phase = row.get("phase").getAsDouble(), seconds = row.get("seconds").getAsDouble();
            int lod = row.get("lod").getAsInt();
            int actual = CriticalSolarFX.sample(metas, animations, slugs, phase, seconds, lod, null,
                (family, index, center, color, alpha, glow, glowAlpha, size) -> {
                    if (cursor[0] >= expected.size()) throw new AssertionError("Extra particle in " + row);
                    JsonObject particle = expected.get(cursor[0]++).getAsJsonObject();
                    if (!family.equals(particle.get("family").getAsString()) || index != particle.get("index").getAsInt())
                        throw new AssertionError("Family/index mismatch " + particle + " actual=" + family + ':' + index);
                    checkVector(center, particle.getAsJsonArray("center"), family + ':' + index + " center");
                    checkVector(color, particle.getAsJsonArray("color"), "color");
                    checkVector(glow, particle.getAsJsonArray("glow"), "glow");
                    check(alpha, particle.get("alpha").getAsDouble(), "alpha");
                    check(glowAlpha, particle.get("glowAlpha").getAsDouble(), "glowAlpha");
                    check(size, particle.get("size").getAsDouble(), "size");
                    particles++;
                });
            if (actual != expected.size() || cursor[0] != expected.size())
                throw new AssertionError("Count mismatch phase=" + phase + " lod=" + lod + " expected="
                    + expected.size() + " actual=" + actual);
            if (row.get("setting").getAsInt() == 0 && (phase == 0 || phase == .5))
                nearCounts.put((phase == 0 ? "idle" : "work") + "_lod" + lod, actual);
        }
        int cachedModels = cacheSize("MODELS"), cachedPoses = cacheSize("POSES");
        int groupLimit = 0;
        for (JsonObject groups : animations) groupLimit += groups.size();
        if (cachedModels != 3 || cachedPoses > groupLimit)
            throw new AssertionError("Unbounded sampler cache: models=" + cachedModels + " poses=" + cachedPoses);
        if (nearCounts.get("work_lod1") > CriticalSolarFX.LOCAL_LIMIT)
            throw new AssertionError("Approved local FX budget exceeded");
        CriticalSolarFX.clear();
        if (cacheSize("MODELS") != 0 || cacheSize("POSES") != 0)
            throw new AssertionError("Resource reload did not clear sampler caches");
        String report = "{\"passed\":true,\"scenarios\":" + cases.size() + ",\"particles\":" + particles
            + ",\"maxAbsoluteError\":" + error + ",\"counts\":" + new com.google.gson.Gson().toJson(nearCounts)
            + ",\"cachedModels\":" + cachedModels + ",\"cachedPoses\":" + cachedPoses
            + ",\"poseCacheLimit\":" + groupLimit + ",\"reloadClearsCaches\":true}";
        Files.writeString(Path.of(args[0]).resolveSibling("solar-fx-report.json"), report);
        System.out.println("PASS " + cases.size() + " actual-preview Solar FX scenarios; " + particles
            + " particles; max error=" + error + "; " + nearCounts);
    }

    private static int cacheSize(String name) throws Exception {
        java.lang.reflect.Field field = CriticalSolarFX.class.getDeclaredField(name);
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).size();
    }

    private static void checkVector(double[] actual, JsonArray expected, String label) {
        for (int i = 0; i < 3; i++) check(actual[i], expected.get(i).getAsDouble(), label + " axis=" + i);
    }

    private static void check(double actual, double expected, String label) {
        double delta = Math.abs(actual - expected);
        if (!Double.isFinite(delta) || delta > 1e-8)
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        error = Math.max(error, delta);
    }
}
