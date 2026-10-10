package com.miaokatze.gtsr.client.critical;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.*;

/** Standalone probe: compile with CriticalAnimation.java and Gson; no Minecraft/OpenGL required. */
public final class CriticalAnimationProbe {
    public static void main(String[] args) throws Exception {
        JsonArray cases = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        Map<String, JsonObject> models = new HashMap<>();
        double error = 0;
        for (JsonElement element : cases) {
            JsonObject row = element.getAsJsonObject();
            String slug = row.get("model").getAsString();
            if (!models.containsKey(slug)) models.put(slug, JsonParser.parseString(Files.readString(
                Path.of(args[1], slug + ".geometry.json"))).getAsJsonObject());
            JsonObject group = models.get(slug).getAsJsonObject("animationGroups").getAsJsonObject(row.get("group").getAsString());
            double[] actual = CriticalAnimation.transform(CriticalAnimation.vector(row, "point", new double[3]), group,
                row.get("phase").getAsDouble(), row.get("seconds").getAsDouble(), row.get("mode").getAsString());
            double[] expected = CriticalAnimation.vector(row, "expected", new double[3]);
            for (int axis = 0; axis < 3; axis++) {
                double delta = Math.abs(actual[axis] - expected[axis]);
                if (!Double.isFinite(delta) || delta > 1e-8) throw new AssertionError(row + " axis=" + axis + " actual=" + actual[axis]);
                error = Math.max(error, delta);
            }
        }
        System.out.println("PASS " + cases.size() + " canonical transform cases; max error=" + error);
        Files.writeString(Path.of(args[0]).resolveSibling("animation-report.json"),
            "{\"passed\":true,\"cases\":" + cases.size() + ",\"maxAbsoluteError\":" + error + "}");
    }
}
