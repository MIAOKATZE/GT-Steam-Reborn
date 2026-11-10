package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.miaokatze.miaokatzecore.api.testlab.LabCase;

/** Explicit capability/selection contract, shared by legacy and unified command catalogs. */
final class TestLabMetadata {

    private static final Set<String> REGRESSIONS = Collections.unmodifiableSet(
        new LinkedHashSet<>(
            Arrays.asList(
                "cluster_unit_furnace_output_split",
                "cluster_unit_ore_washer_water_bulk",
                "cluster_unit_ore_washer_residual_distilled_bulk",
                "cluster_unit_ore_washer_distilled_bulk")));
    private static final Set<String> PERFORMANCE = Collections.unmodifiableSet(
        new LinkedHashSet<>(
            Arrays.asList(
                "steam_input_hatch_generic",
                "air_compressor",
                "steam_cache_node",
                "critical_loom",
                "cluster_controller",
                "singularity_drilling_hub")));

    private TestLabMetadata() {}

    static LabCase describe(TestLabTask task) {
        Set<String> tags = new LinkedHashSet<>();
        boolean client = task.id()
            .startsWith("ui.client.");
        boolean followup = task instanceof TestLabFollowupTask || task instanceof TestLabAmmoniaNaturalTask
            || REGRESSIONS.contains(task.id());
        String group = task instanceof TestLabAmmoniaNaturalTask ? "ammonia-natural"
            : task instanceof TestLabFollowupTask ? "followup"
                : task instanceof TestLabHubTask ? "hub-storage"
                    : task instanceof TestLabClusterTask ? "cluster"
                        : task instanceof TestLabCriticalTask ? "critical-construction"
                            : task instanceof TestLabDrillingTask ? "mining"
                                : task.id()
                                    .startsWith("ui.") ? "ui-model-sync"
                                        : task.id()
                                            .startsWith("structure.") ? "structure" : "machine";
        tags.add("group:" + group);
        tags.add("world:isolated-persistent-lab");
        tags.add("mutation:owned-fixtures");
        tags.add(client ? "in-process-test-player" : "fake-actor");
        tags.add("input:no-os-input");
        if (followup) tags.add("followup");
        if (task instanceof TestLabAmmoniaNaturalTask) tags.add("natural");
        if (task instanceof TestLabStructureFailureTask) tags.add("negative");
        if (task.id()
            .startsWith("structure.")) tags.add("destructive");
        if (task.id()
            .startsWith("perf.")) tags.add("stress");
        if (client) tags.add("client");
        if (PERFORMANCE.contains(task.id())) tags.add("performance-representative");
        return new LabCase(
            task.id(),
            task.displayName(),
            client ? "client" : "server",
            tags,
            !followup && !client
                && !task.id()
                    .startsWith("ui.")
                && !tags.contains("negative")
                && !tags.contains("stress")
                && !tags.contains("destructive"),
            false);
    }

    static boolean defaultIncluded(TestLabTask task) {
        return describe(task).defaultSuite();
    }

    static boolean performanceRepresentative(TestLabTask task) {
        return defaultIncluded(task) && PERFORMANCE.contains(task.id());
    }
}
