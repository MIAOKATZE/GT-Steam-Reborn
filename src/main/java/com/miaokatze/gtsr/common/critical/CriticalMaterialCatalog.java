package com.miaokatze.gtsr.common.critical;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Exposure describes construction ownership, never a promise of a crafting recipe. */
public final class CriticalMaterialCatalog {

    public enum Usage {
        MANUAL_LOOM_ONLY,
        SHARED_LOOM_AND_AUTOMATIC,
        AUTOMATIC_BODY_ONLY,
        LEGACY_V1,
        FROZEN_ONLY,
        ORPHAN,
        GT_CONTROLLER_MARKER
    }

    private static final class Loaded {

        private static final CriticalMaterialCatalog CATALOG = new CriticalMaterialCatalog();
    }

    private final Set<String> manualLoom, automaticBody, currentPhysical, controllerMarkers;
    private final Map<String, Usage> usage;

    private CriticalMaterialCatalog() {
        CriticalMaterialDefinitions.Catalog definitions = CriticalMaterialDefinitions.catalog();
        Set<String> loom = modelMaterials("v2_loom", definitions);
        Set<String> body = new LinkedHashSet<>();
        body.addAll(modelMaterials("v2_base_t3", definitions));
        for (CriticalMachineKind kind : CriticalMachineKind.releasedValues())
            body.addAll(modelMaterials("v2_" + kind.key, definitions));
        // Default r31 layout: T3 with eight parallel addons; copies do not add material identities.
        body.addAll(modelMaterials("v2_plugin_parallel", definitions));
        Set<String> markers = new LinkedHashSet<>();
        markers.add("v2_gt_controller");
        markers.add("v2_loom_machine_controller");
        loom.removeAll(markers);
        body.removeAll(markers);
        manualLoom = Collections.unmodifiableSet(loom);
        automaticBody = Collections.unmodifiableSet(body);
        Set<String> physical = new LinkedHashSet<>(loom);
        physical.addAll(body);
        currentPhysical = Collections.unmodifiableSet(physical);
        controllerMarkers = Collections.unmodifiableSet(markers);
        Map<String, Usage> classes = new LinkedHashMap<>();
        for (String id : definitions.definitions()
            .keySet()) {
            Usage group = markers.contains(id) ? Usage.GT_CONTROLLER_MARKER
                : loom.contains(id) ? body.contains(id) ? Usage.SHARED_LOOM_AND_AUTOMATIC : Usage.MANUAL_LOOM_ONLY
                    : body.contains(id) ? Usage.AUTOMATIC_BODY_ONLY
                        : !id.startsWith("v2_") ? Usage.LEGACY_V1
                            : "v2_hazard_stripe".equals(id) || "v2_plugin_socket".equals(id) ? Usage.ORPHAN
                                : Usage.FROZEN_ONLY;
            classes.put(id, group);
        }
        usage = Collections.unmodifiableMap(classes);
    }

    public static CriticalMaterialCatalog current() {
        return Loaded.CATALOG;
    }

    private static Set<String> modelMaterials(String slug, CriticalMaterialDefinitions.Catalog definitions) {
        JsonObject model = CriticalGeometry.readJson("models/" + slug + ".geometry.json");
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement element : model.getAsJsonArray("blocks")) {
            JsonArray row = element.getAsJsonArray();
            result.add(
                definitions.definition(
                    row.get(3)
                        .getAsString()).id);
        }
        return result;
    }

    public Usage usage(String aliasOrId) {
        return usage.get(CriticalMaterialDefinitions.canonicalId(aliasOrId));
    }

    /** All current physical blocks remain obtainable during development, including automatic-only parts. */
    public boolean visibleInCreative(String aliasOrId) {
        return currentPhysical.contains(CriticalMaterialDefinitions.canonicalId(aliasOrId));
    }

    /** NEI lists the player's manual loom supply list; paid construction is a separate acquisition path. */
    public boolean visibleInNei(String aliasOrId) {
        return manualLoom.contains(CriticalMaterialDefinitions.canonicalId(aliasOrId));
    }

    public Set<String> manualLoom() {
        return manualLoom;
    }

    public Set<String> automaticBody() {
        return automaticBody;
    }

    public Set<String> currentPhysical() {
        return currentPhysical;
    }

    public Set<String> controllerMarkers() {
        return controllerMarkers;
    }

    public Map<String, Usage> usages() {
        return usage;
    }
}
