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
        schemaV2(initialized.ids, definitions, slots);
        filePublication();
        System.out
            .println("PASS material ID preflight: " + checks + " checks; exact_gt_277=true; player_world_access=false");
    }

    private static void schemaV2(Properties historical, List<MaterialIdPreflight.Definition> definitions,
        Map<Integer, String> actualSlots) throws Exception {
        List<MaterialIdPreflight.Definition> dust = new ArrayList<>(), fluids = new ArrayList<>();
        Map<String, String> phases = new HashMap<>();
        for (IndustrialRecipeLedger.MaterialDefinition entry : IndustrialRecipeLedger.get().materials)
            phases.put(entry.id, entry.phase);
        for (MaterialIdPreflight.Definition definition : definitions)
            ("dust".equals(phases.get(definition.key)) ? dust : fluids).add(definition);
        check(dust.size() == 42 && fluids.size() == 63, "v2 catalog uses actual ledger 42 dust and 63 retired fluids");
        MaterialIdSchema.Result fresh = inspectV2(null, null, dust, fluids, actualSlots);
        check(
            fresh.errors.isEmpty() && fresh.identity.activeDustIds.size() == 42
                && fresh.identity.retiredFluidIds.isEmpty()
                && fresh.identity.legacyFluidAliases.isEmpty(),
            "automatic fresh reservation uses only 42 real dust identities without JVM flag");
        check(
            fresh.identity.isGeneratedUnbound() && fresh.identity.sourceSha256.isEmpty(),
            "fresh mapping explicitly has no old-world source claim");
        byte[] freshBytes = MaterialIdSchema.encode(fresh, "5.09.54.277");
        MaterialIdSchema.Result reloaded = inspectV2(freshBytes, null, dust, fluids, actualSlots);
        check(
            reloaded.errors.isEmpty() && reloaded.identity.isGeneratedUnbound()
                && reloaded.identity.fingerprint.equals(fresh.identity.fingerprint),
            "persisted automatic mapping remains unbound and idempotent on second startup");
        byte[] original = encodeLegacy(historical), preserved = original.clone();
        MaterialIdSchema.Result imported = inspectV2(null, original, dust, fluids, actualSlots);
        check(
            imported.errors.isEmpty() && imported.identity.legacyFluidAliases.size() == 63
                && MaterialIdSchema.LEGACY_IMPORT.equals(imported.identity.origin)
                && imported.identity.sourceSha256.equals(MaterialIdPreflight.sha256(original)),
            "complete legacy mapping preserves audited source and every actually free legacy alias");
        for (MaterialIdPreflight.Definition entry : dust) check(
            imported.identity.activeDustIds.get(entry.key)
                .toString()
                .equals(historical.getProperty(entry.key)),
            "v2 imports original dust ID " + entry.key);
        check(Arrays.equals(original, preserved), "v2 importer never changes old cfg bytes");
        Properties signed = copy(historical);
        signed.setProperty(dust.get(0).key, "+" + historical.getProperty(dust.get(0).key));
        MaterialIdSchema.Result signedImport = inspectV2(null, encodeLegacy(signed), dust, fluids, actualSlots);
        check(
            signedImport.errors.isEmpty()
                && signedImport.identity.activeDustIds.equals(imported.identity.activeDustIds),
            "historically legal signed ID syntax preserves the original numeric identity");
        Properties old705 = copy(historical);
        old705.setProperty("planned:native_acid_cut", "705");
        byte[] conflictSource = encodeLegacy(old705);
        MaterialIdSchema.Result conflict = inspectV2(null, conflictSource, dust, fluids, actualSlots);
        check(
            conflict.errors.isEmpty() && conflict.identity.retiredFluidIds.get("planned:native_acid_cut") == 705
                && conflict.identity.conflictedFluidIds.get("planned:native_acid_cut") == 705
                && !conflict.identity.legacyFluidAliases.containsKey("planned:native_acid_cut"),
            "real .277 DistilledWater 705 isolates historical acid alias without blocking pure fluid registration");
        check(
            actualSlots.get(705)
                .equals(Materials.DistilledWater.mName)
                && actualSlots.size() == MaterialIdOfflineCopy.NATIVE_277_IDS.length,
            "historical conflict never rewrites the actual native occupancy evidence");
        check(
            conflict.identity.activeDustIds.equals(imported.identity.activeDustIds),
            "retiring conflicted acid never reallocates any original dust ID");
        byte[] encodedConflict = MaterialIdSchema.encode(conflict, "5.09.54.277");
        MaterialIdSchema.Result conflictReload = inspectV2(encodedConflict, conflictSource, dust, fluids, actualSlots);
        check(
            conflictReload.errors.isEmpty() && conflictReload.identity.fingerprint.equals(conflict.identity.fingerprint)
                && conflictReload.identity.conflictedFluidIds.equals(conflict.identity.conflictedFluidIds),
            "retired conflicting evidence and safe alias fingerprint survive second startup");
        Properties badDust = copy(old705);
        badDust.setProperty(dust.get(0).key, "1");
        check(
            contains(inspectV2(null, encodeLegacy(badDust), dust, fluids, actualSlots).errors, "Occupied dust ID"),
            "retired-fluid exception never waives an actual Hydrogen dust conflict");
        Properties missing = copy(historical);
        missing.remove(dust.get(0).key);
        check(
            contains(inspectV2(null, encodeLegacy(missing), dust, fluids, actualSlots).errors, "Missing historical ID"),
            "partial original cfg never becomes guessed powder mapping");
        check(
            !inspectV2(new byte[0], null, dust, fluids, actualSlots).errors.isEmpty(),
            "present empty v2 is damaged configuration, not an automatic new install");
        check(
            !inspectV2(freshBytes, original, dust, fluids, actualSlots).errors.isEmpty(),
            "later discovered original cfg cannot silently replace an automatic unbound map");
        check(
            !inspectV2(encodedConflict, original, dust, fluids, actualSlots).errors.isEmpty(),
            "changed legacy source cannot silently override recorded mapping provenance");
        byte[] tamperedProvenance = new String(encodedConflict, StandardCharsets.ISO_8859_1)
            .replace("__gtsr.source-sha256=", "__gtsr.source-sha256=a")
            .getBytes(StandardCharsets.ISO_8859_1);
        check(
            contains(
                inspectV2(tamperedProvenance, null, dust, fluids, actualSlots).errors,
                "provenance record fingerprint mismatch"),
            "provenance has independent integrity even when original cfg is temporarily unavailable");
        byte[] tampered = new String(encodedConflict, StandardCharsets.ISO_8859_1)
            .replace("retired\\:planned\\:native_acid_cut=705", "retired\\:planned\\:native_acid_cut=706")
            .getBytes(StandardCharsets.ISO_8859_1);
        check(
            !Arrays.equals(tampered, encodedConflict) && contains(
                inspectV2(tampered, conflictSource, dust, fluids, actualSlots).errors,
                "fingerprint mismatch"),
            "retired evidence tampering is checked even though that slot is inactive");
        try {
            fresh.identity.activeDustIds.clear();
            throw new AssertionError("startup identity mutable");
        } catch (UnsupportedOperationException expected) {
            check(true, "world guard and handshake receive immutable startup identity");
        }
    }

    private static MaterialIdSchema.Result inspectV2(byte[] v2, byte[] original,
        List<MaterialIdPreflight.Definition> dust, List<MaterialIdPreflight.Definition> fluids,
        Map<Integer, String> actualSlots) {
        return MaterialIdSchema
            .inspect(v2, original, dust, fluids, actualSlots, Collections.<String, String>emptyMap());
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
            Path readOnly = scratch.resolve("original.cfg");
            Files.write(readOnly, before);
            try {
                try {
                    MaterialIdFileStore.publishWithReadOnlySource(target, after, before, readOnly, after);
                    throw new AssertionError("changed original cfg accepted");
                } catch (java.io.IOException expected) {
                    check(
                        Arrays.equals(after, Files.readAllBytes(target))
                            && Arrays.equals(before, Files.readAllBytes(readOnly)),
                        "source change refuses v2 publication and preserves both files");
                }
                MaterialIdFileStore.publishWithReadOnlySource(target, after, before, readOnly, before);
                check(
                    Arrays.equals(before, Files.readAllBytes(target))
                        && Arrays.equals(before, Files.readAllBytes(readOnly)),
                    "v2 publishes separately while original cfg stays byte-identical");
            } finally {
                Files.deleteIfExists(readOnly);
            }
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
