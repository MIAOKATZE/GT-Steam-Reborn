// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import gregtech.api.enums.Materials;

/** Pure planner contracts against the actual .277 ID table; no Materials registry is cleared or overwritten. */
public final class MaterialIdPreflightContracts {

    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        java.lang.reflect.Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, MaterialIdPreflightContracts.class.getName(), args)) return;
        Class<?> fixtures = Class.forName("com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts");
        java.lang.reflect.Method setup = fixtures.getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        Path gtJar = java.nio.file.Paths.get(
            ((java.net.JarURLConnection) Materials.class.getResource("/gregtech/api/enums/Materials.class")
                .openConnection()).getJarFileURL()
                    .toURI());
        check(
            MaterialIdPreflight.sha256(Files.readAllBytes(gtJar))
                .equals("88a0ac96a01e11fa04f738784722ecf27d4c72488973ff41eeb1399a167801e7"),
            "exact .277 artifact fingerprint");
        java.lang.reflect.Constructor<?> constructor = Class.forName("gregtech.api.enums.MaterialsIDMap")
            .getDeclaredConstructor();
        constructor.setAccessible(true);
        Map<?, ?> actual = (Map<?, ?>) constructor.newInstance();
        Map<Integer, String> slots = new HashMap<>();
        for (Map.Entry<?, ?> entry : actual.entrySet())
            slots.put((Integer) entry.getKey(), ((Materials) entry.getValue()).mName);
        check(
            actual.get(705) == Materials.DistilledWater && Materials.DistilledWater.mName.equals(slots.get(705)),
            "native .277 slot 705 belongs to actual DistilledWater identity/mName");
        java.util.Set<Integer> audited = new java.util.HashSet<>();
        for (int id : MaterialIdOfflineCopy.NATIVE_277_IDS) audited.add(id);
        check(audited.equals(slots.keySet()), "offline tool native occupancy evidence exactly covers all .277 slots");
        List<MaterialIdPreflight.Definition> definitions = new ArrayList<>();
        for (IndustrialRecipeLedger.MaterialDefinition definition : IndustrialRecipeLedger.get().materials)
            definitions.add(
                new MaterialIdPreflight.Definition(
                    definition.id,
                    definition.name,
                    definition.introducedVersion > 0,
                    false));
        for (String name : Arrays
            .asList("wastesigh", "thickgrease", "metalgrit", "umbralmire", "sanzu_residual_steam", "withered_breath"))
            definitions.add(new MaterialIdPreflight.Definition("air." + name, name, true, true));
        check(definitions.size() == 105, "all 99 industrial plus six raw-gas identities checked together");
        MaterialIdPreflight.Result initialized = inspect(null, true, definitions, slots);
        check(initialized.errors.isEmpty(), "explicit new instance allocation against actual .277 occupied slots");
        byte[] recorded = MaterialIdPreflight.encode(initialized, "5.09.54.277");
        MaterialIdPreflight.Result restored = inspect(recorded, false, definitions, slots);
        check(
            restored.errors.isEmpty() && restored.ids.equals(initialized.ids),
            "normal 105-entry mapping keeps every old number");
        byte[] legacy = encodeLegacy(initialized.ids);
        check(
            inspect(legacy, false, definitions, slots).ids.equals(initialized.ids),
            "complete legacy mapping preserves numbers");
        check(!inspect(null, false, definitions, slots).errors.isEmpty(), "missing cfg cannot imply new instance");
        Properties conflict = copy(initialized.ids);
        conflict.setProperty("planned:native_acid_cut", "705");
        String anotherKey = definitions.get(0).key;
        conflict.setProperty(anotherKey, "1");
        List<String> conflicts = inspect(encodeLegacy(conflict), false, definitions, slots).errors;
        check(
            contains(conflicts, "705 owner=" + Materials.DistilledWater.mName)
                && contains(conflicts, "1 owner=" + Materials.Hydrogen.mName),
            "collect simultaneous conflicts, not just first 705");
        check(
            contains(
                inspect(
                    (new String(legacy, StandardCharsets.ISO_8859_1) + "\nplanned\\:native_acid_cut=705\n")
                        .getBytes(StandardCharsets.ISO_8859_1),
                    false,
                    definitions,
                    slots).errors,
                "Duplicate property key"),
            "duplicate Properties keys are rejected before last-value collapse");
        Properties damaged = copy(initialized.ids);
        damaged.setProperty(definitions.get(0).key, "oops");
        damaged.setProperty(definitions.get(1).key, "1000");
        damaged.setProperty(definitions.get(2).key, damaged.getProperty(definitions.get(3).key));
        damaged.remove(definitions.get(4).key);
        List<String> damage = inspect(encodeLegacy(damaged), false, definitions, slots).errors;
        check(
            contains(damage, "Non-numeric") && contains(damage, "Out-of-range")
                && contains(damage, "Duplicate saved")
                && contains(damage, "Missing persistent"),
            "report malformed, duplicate, missing and range errors together");
        byte[] malformedThenConflict = ("broken=" + "\\"
            + "uZZZZ\n"
            + new String(encodeLegacy(conflict), StandardCharsets.ISO_8859_1)).getBytes(StandardCharsets.ISO_8859_1);
        List<String> remaining = inspect(malformedThenConflict, false, definitions, slots).errors;
        check(
            contains(remaining, "Malformed mapping")
                && contains(remaining, "705 owner=" + Materials.DistilledWater.mName),
            "malformed escape cannot hide later numeric conflicts");
        byte[] tampered = new String(recorded, StandardCharsets.ISO_8859_1).replace("air.wastesigh=", "air.wastesigh=9")
            .getBytes(StandardCharsets.ISO_8859_1);
        check(
            contains(inspect(tampered, false, definitions, slots).errors, "fingerprint mismatch"),
            "provenance fingerprint rejects modified mapping");
        List<MaterialIdPreflight.Definition> added = new ArrayList<>(definitions);
        added.add(new MaterialIdPreflight.Definition("future:added", "future_added", true, false));
        MaterialIdPreflight.Result addition = inspect(recorded, false, added, slots);
        check(
            addition.errors.isEmpty() && addition.ids.size() == 106,
            "recorded old catalog permits explicitly new definition only");
        for (String key : initialized.ids.stringPropertyNames()) check(
            initialized.ids.getProperty(key)
                .equals(addition.ids.getProperty(key)),
            "addition preserves " + key);
        Map<Integer, String> full = new HashMap<>();
        for (int i = 0; i < 1000; i++) full.put(i, "fixture-occupied-" + i);
        check(
            contains(inspect(null, true, definitions, full).errors, "No free"),
            "slot exhaustion never applies partial candidate allocations");
        Map<String, String> names = Collections.singletonMap(definitions.get(0).name, "foreign-provider");
        check(
            contains(
                MaterialIdPreflight.inspect(legacy, false, definitions, slots, names).errors,
                "Occupied material name"),
            "foreign material name collision");
        byte[] preserved = Arrays.copyOf(legacy, legacy.length);
        inspect(legacy, false, definitions, full);
        check(Arrays.equals(legacy, preserved), "failed preflight leaves original byte input unchanged");
        Properties oldReader = new Properties();
        oldReader.load(new java.io.ByteArrayInputStream(recorded));
        check(
            oldReader.size() == 105,
            "provenance is comment-only; old Properties reader sees numeric identities only");
        for (String key : oldReader.stringPropertyNames()) Integer.parseInt(oldReader.getProperty(key));
        filePublication();
        System.out
            .println("PASS material ID preflight: " + checks + " checks; exact_gt_277=true; player_world_access=false");
    }

    private static void filePublication() throws Exception {
        Path scratch = Files.createTempDirectory("gtsr-material-publish-contract-");
        Path target = scratch.resolve("industrial-material-ids.cfg");
        byte[] before = "saved=601\n".getBytes(StandardCharsets.US_ASCII);
        byte[] after = "saved=601\nadded=602\n".getBytes(StandardCharsets.US_ASCII);
        Files.write(target, before);
        try {
            try {
                MaterialIdFileStore.publish(
                    target,
                    before,
                    after,
                    (path, bytes) -> { throw new java.io.IOException("fixture disk failure"); });
                throw new AssertionError("injected write failure accepted");
            } catch (java.io.IOException expected) {
                check(
                    Arrays.equals(before, Files.readAllBytes(target)),
                    "publication failure preserves original bytes");
            }
            try {
                MaterialIdFileStore.publish(
                    target,
                    after,
                    after,
                    (path, bytes) -> { throw new AssertionError("publisher invoked after stale preflight"); });
                throw new AssertionError("changed target accepted");
            } catch (java.io.IOException expected) {
                check(
                    Arrays.equals(before, Files.readAllBytes(target)),
                    "target change rejects before publisher and preserves current bytes");
            }
            MaterialIdFileStore.publish(target, before, after);
            check(Arrays.equals(after, Files.readAllBytes(target)), "Core atomic publisher round-trip");
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(scratch.resolve("industrial-material-ids.cfg.registration.lock"));
            Files.deleteIfExists(scratch);
        }
    }

    private static MaterialIdPreflight.Result inspect(byte[] source, boolean fresh,
        List<MaterialIdPreflight.Definition> definitions, Map<Integer, String> slots) {
        return MaterialIdPreflight.inspect(source, fresh, definitions, slots, Collections.<String, String>emptyMap());
    }

    private static boolean contains(List<String> errors, String text) {
        for (String error : errors) if (error.contains(text)) return true;
        return false;
    }

    private static byte[] encodeLegacy(Properties source) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        source.store(output, "fixture only");
        return output.toByteArray();
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
