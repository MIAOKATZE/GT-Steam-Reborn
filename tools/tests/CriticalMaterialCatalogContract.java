package com.miaokatze.gtsr.common.critical;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Pure schema, identity and exposure contract. No Minecraft, GL, images or visual baseline. */
public final class CriticalMaterialCatalogContract {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejected(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        JsonObject document;
        try (InputStreamReader reader = new InputStreamReader(
            CriticalMaterialCatalogContract.class.getResourceAsStream("/assets/gtsr/critical/materials.json"),
            StandardCharsets.UTF_8)) {
            document = new JsonParser().parse(reader).getAsJsonObject();
        }
        CriticalMaterialDefinitions.Catalog catalog = CriticalMaterialDefinitions.fromJson(document);
        check(catalog.aliases().size() == 371, "371 legacy aliases");
        check(catalog.definitions().size() == 65, "65 registered identities");
        JsonObject flat = catalog.legacyPalette();
        CriticalMaterialDefinitions.Catalog historical = CriticalMaterialDefinitions.fromJson(flat);
        check(historical.aliases().equals(catalog.aliases()), "flat schema aliases round-trip");
        check(historical.legacyPalette().equals(flat), "flat schema full property shape round-trip");
        check(CriticalMaterialDefinitions.expandLegacyPalette(flat) == flat, "flat compatibility view remains cached");
        try {
            catalog.aliases().put("mutation", "alloy_frame");
            throw new AssertionError("mutable alias map");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
        try {
            catalog.definitions().clear();
            throw new AssertionError("mutable definition map");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
        for (Map.Entry<String, String> alias : catalog.aliases().entrySet()) {
            CriticalMaterialDefinitions.Definition definition = catalog.definition(alias.getKey());
            check(definition == catalog.definition(alias.getValue()), "aliases share the immutable definition");
            check(definition.properties().equals(flat.get(alias.getKey())), "legacy property shape is unchanged");
            check(catalog.legacyProperties(alias.getKey()) == catalog.legacyProperties(alias.getKey()), "cached legacy properties");
            JsonObject detached = definition.properties();
            detached.addProperty("id", "mutated");
            check(definition.properties().get("id").getAsString().equals(alias.getValue()), "defensive definition JSON");
        }
        JsonObject corrupt = new JsonParser().parse(document.toString()).getAsJsonObject();
        corrupt.getAsJsonObject("aliases").addProperty("bad", "missing");
        rejected(() -> CriticalMaterialDefinitions.fromJson(corrupt), "reject dangling alias");
        JsonObject shadow = new JsonParser().parse(document.toString()).getAsJsonObject();
        shadow.getAsJsonObject("aliases").addProperty("alloy_frame", "reinforced_panel");
        rejected(() -> CriticalMaterialDefinitions.fromJson(shadow), "reject identity shadow");
        JsonObject conflict = new JsonParser().parse(flat.toString()).getAsJsonObject();
        JsonObject conflictingAlias = catalog.definition("alloy_frame").properties();
        conflictingAlias.addProperty("animated", true);
        conflict.add("conflicting_alias", conflictingAlias);
        rejected(() -> CriticalMaterialDefinitions.fromJson(conflict), "reject divergent historical alias");
        CriticalMaterialCatalog exposure = CriticalMaterialCatalog.current();
        check(exposure.manualLoom().size() == 15, "15 manual loom supplies");
        check(exposure.automaticBody().size() == 21, "21 automatic body materials");
        check(exposure.currentPhysical().size() == 24, "24 physical current materials");
        int creative = 0, nei = 0, old = 0, frozen = 0, orphan = 0, marker = 0, shared = 0, bodyOnly = 0, loomOnly = 0;
        for (String id : catalog.definitions().keySet()) {
            if (exposure.visibleInCreative(id)) creative++;
            if (exposure.visibleInNei(id)) nei++;
            switch (exposure.usage(id)) {
                case LEGACY_V1: old++; break;
                case FROZEN_ONLY: frozen++; break;
                case ORPHAN: orphan++; break;
                case GT_CONTROLLER_MARKER: marker++; break;
                case SHARED_LOOM_AND_AUTOMATIC: shared++; break;
                case AUTOMATIC_BODY_ONLY: bodyOnly++; break;
                case MANUAL_LOOM_ONLY: loomOnly++; break;
            }
        }
        check(creative == 24 && nei == 24, "creative and NEI expose the same current materials");
        check(old == 35 && frozen == 2 && orphan == 2 && marker == 2, "compatibility groups remain registered");
        check(shared == 12 && bodyOnly == 9 && loomOnly == 3, "construction ownership partition");
        check(exposure.usage("v2_gt_controller") == CriticalMaterialCatalog.Usage.GT_CONTROLLER_MARKER,
            "main GT controller placeholder hidden");
        check(exposure.usage("v2_loom_machine_controller") == CriticalMaterialCatalog.Usage.GT_CONTROLLER_MARKER,
            "loom GT controller placeholder hidden");
        for (Map.Entry<String, CriticalMaterialDefinitions.Definition> entry : catalog.definitions().entrySet()) {
            CriticalMaterialDefinitions.Definition definition = entry.getValue();
            for (CriticalMaterialDefinitions.SpritePair sprite : definition.sprites().values())
                check(sprite.texture(false, false).equals(sprite.albedo), "typed sprite lookup");
            if (definition.hasCtm()) for (String face : new String[] { "ny", "py", "nz", "pz", "nx", "px" }) {
                String group = definition.ctmFaceGroup(face);
                for (int mask = 0; mask < 16; mask++)
                    check(definition.ctmSprite(group, mask & definition.ctmAllowedMask(face)) != null,
                        "typed CTM resolution covers allowed masks");
            }
        }
        for (Map.Entry<String, JsonElement> entry : document.getAsJsonObject("definitions").entrySet())
            check(catalog.definition(entry.getKey()).properties().equals(entry.getValue()), "canonical property payload preserved");
        System.out.println("PASS " + checks + " schema/identity/exposure checks; creative=24, NEI=24, definitions=65, aliases=371");
    }
}
