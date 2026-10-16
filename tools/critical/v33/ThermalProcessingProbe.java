package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/** Preserves the thermal diagnosis fixture verbatim; only adapts its CLI to a JSON receipt. */
public final class ThermalProcessingProbe {
    public static void main(String[] args) throws Exception {
        Class<?> fixture = Class.forName("SteamProbe");
        fixture.getMethod("main", String[].class).invoke(null, (Object) new String[] {"fixed"});
        Field checks = fixture.getDeclaredField("checks");
        checks.setAccessible(true);
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("checks", checks.getInt(null));
        result.addProperty("GT", ProbeSupport.source(gregtech.api.logic.ProcessingLogic.class));
        JsonObject sources = new JsonObject();
        for (String name : new String[] {"MTEReinforcedBrickBlastFurnace", "MTELargeCokeOven", "MTESiemensMartinFurnace"})
            sources.addProperty(name, ProbeSupport.source(Class.forName("com.miaokatze.gtsr.common.machine." + name)));
        result.add("productSources", sources);
        result.addProperty("boundary", "Real native power/ProcessingLogic/ParallelHelper/runMachine and input/output bus watcher events; Unsafe registry constructor bypass and FML platform gates; no full Forge server or real registered recipe-map lookup");
        Files.write(Paths.get(args[0]), new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes("UTF-8"));
    }
}
