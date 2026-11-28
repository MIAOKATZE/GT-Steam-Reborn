// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import com.miaokatze.gtsr.common.dimension.prosperity.industrial.compat.LegacyMaterialIdImport;

/** Pure v2 split: dust remains active, historical fluid IDs are evidence and optional safe aliases. */
public final class MaterialIdSchema {

    private static final String META = "__gtsr.";
    public static final String GENERATED_UNBOUND = "generated-unbound";
    public static final String LEGACY_IMPORT = "legacy-import";

    private MaterialIdSchema() {}

    public static final class Identity {

        public final String origin, sourceSha256, fingerprint, catalogFingerprint;
        public final Map<String, Integer> activeDustIds, retiredFluidIds, legacyFluidAliases, conflictedFluidIds;

        private Identity(String origin, String sourceSha256, String catalogFingerprint, Map<String, Integer> dust,
            Map<String, Integer> retired, Map<String, Integer> aliases) {
            this.origin = origin;
            this.sourceSha256 = sourceSha256;
            this.catalogFingerprint = catalogFingerprint;
            activeDustIds = immutable(dust);
            retiredFluidIds = immutable(retired);
            legacyFluidAliases = immutable(aliases);
            Map<String, Integer> conflicts = new TreeMap<>(retired);
            conflicts.keySet()
                .removeAll(aliases.keySet());
            conflictedFluidIds = immutable(conflicts);
            fingerprint = MaterialIdPreflight.fingerprint(payload(this));
        }

        public boolean isGeneratedUnbound() {
            return GENERATED_UNBOUND.equals(origin);
        }
    }

    public static final class Result {

        public final Identity identity;
        public final List<String> errors;

        private Result(Identity identity, List<String> errors) {
            this.identity = identity;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        public void requireValid(String context) {
            if (!errors.isEmpty()) throw new IllegalStateException(
                "Material ID v2 preflight rejected [" + context
                    + "]:\n"
                    + String.join("\n", errors)
                    + "\nPreserve the original cfg/world. Restore matching dust mapping or diagnose a separate offline copy.");
        }
    }

    public static Result inspect(byte[] v2, byte[] legacy, List<MaterialIdPreflight.Definition> dust,
        List<MaterialIdPreflight.Definition> fluids, Map<Integer, String> occupied, Map<String, String> occupiedNames) {
        List<String> errors = new ArrayList<>();
        List<MaterialIdPreflight.Definition> all = new ArrayList<>(dust);
        all.addAll(fluids);
        Map<String, MaterialIdPreflight.Definition> definitions = new TreeMap<>();
        Properties catalog = new Properties();
        Set<String> names = new HashSet<>();
        for (MaterialIdPreflight.Definition definition : all) {
            if (definitions.put(definition.key, definition) != null)
                errors.add("Duplicate definition: " + definition.key);
            if (!names.add(definition.name)) errors.add("Duplicate material name: " + definition.name);
            if (occupiedNames.containsKey(definition.name)) errors
                .add("Occupied material name: " + definition.key + " owner=" + occupiedNames.get(definition.name));
            catalog.setProperty(definition.key, (dust.contains(definition) ? "dust:" : "fluid:") + definition.name);
        }
        String catalogHash = MaterialIdPreflight.fingerprint(catalog);
        Map<String, Integer> active = new TreeMap<>(), retired = new TreeMap<>(), aliases = new TreeMap<>();
        String origin = GENERATED_UNBOUND, sourceHash = "";
        if (v2 != null) {
            Properties saved = MaterialIdPreflight.parse(v2, errors);
            origin = saved.getProperty(META + "origin", "");
            sourceHash = saved.getProperty(META + "source-sha256", "");
            if (!"2".equals(saved.getProperty(META + "schema"))) errors.add("Unsupported v2 schema");
            if (!recordFingerprint(saved).equals(saved.getProperty(META + "record-sha256")))
                errors.add("V2 provenance record fingerprint mismatch");
            if (saved.getProperty(META + "gt-version", "")
                .isEmpty()) errors.add("Missing GT provenance version");
            if (!catalogHash.equals(saved.getProperty(META + "catalog-sha256")))
                errors.add("Material catalog mismatch");
            if (!GENERATED_UNBOUND.equals(origin) && !LEGACY_IMPORT.equals(origin)) errors.add("Unknown v2 origin");
            if (GENERATED_UNBOUND.equals(origin) && !sourceHash.isEmpty())
                errors.add("Unbound mapping has source claim");
            if (LEGACY_IMPORT.equals(origin) && !sourceHash.matches("[a-f0-9]{64}"))
                errors.add("Missing legacy source hash");
            for (String key : saved.stringPropertyNames()) {
                if (key.startsWith("active:")) readId(saved, key, "active:", active, errors);
                else if (key.startsWith("retired:")) readId(saved, key, "retired:", retired, errors);
                else if (key.startsWith("alias:")) readId(saved, key, "alias:", aliases, errors);
                else if (!key.equals(META + "schema") && !key.equals(META + "origin")
                    && !key.equals(META + "source-sha256")
                    && !key.equals(META + "catalog-sha256")
                    && !key.equals(META + "mapping-sha256")
                    && !key.equals(META + "gt-version")
                    && !key.equals(META + "record-sha256")) errors.add("Unknown v2 property: " + key);
            }
            Identity persisted = new Identity(origin, sourceHash, catalogHash, active, retired, aliases);
            if (!persisted.fingerprint.equals(saved.getProperty(META + "mapping-sha256")))
                errors.add("V2 mapping fingerprint mismatch");
            if (legacy != null
                && (!LEGACY_IMPORT.equals(origin) || !sourceHash.equals(MaterialIdPreflight.sha256(legacy))))
                errors.add("Original legacy mapping differs from recorded source; recover matching configuration");
            for (Map.Entry<String, Integer> alias : aliases.entrySet()) if (!alias.getValue()
                .equals(retired.get(alias.getKey()))) errors.add("Alias has no matching retired ID: " + alias.getKey());
            if (GENERATED_UNBOUND.equals(origin) && !retired.isEmpty())
                errors.add("Unbound mapping contains historical aliases");
        } else if (legacy != null) {
            // Occupied fluid IDs are evidence, never a request to overwrite the native registry.
            Properties original = LegacyMaterialIdImport.inspect(legacy, all, errors);
            for (MaterialIdPreflight.Definition definition : all) {
                if (original.containsKey(definition.key))
                    readId(original, definition.key, "", dust.contains(definition) ? active : retired, errors);
            }
            origin = LEGACY_IMPORT;
            sourceHash = MaterialIdPreflight.sha256(legacy);
        }
        Set<String> dustKeys = keys(dust), fluidKeys = keys(fluids);
        for (String key : active.keySet()) if (!dustKeys.contains(key)) errors.add("Non-dust active ID: " + key);
        for (String key : retired.keySet())
            if (!fluidKeys.contains(key)) errors.add("Unknown retired fluid ID: " + key);
        if (LEGACY_IMPORT.equals(origin)) for (String key : fluidKeys)
            if (!retired.containsKey(key)) errors.add("Missing retired fluid evidence: " + key);
        Map<Integer, String> reserved = new HashMap<>();
        for (Map.Entry<String, Integer> entry : active.entrySet()) {
            String previous = reserved.put(entry.getValue(), entry.getKey());
            if (previous != null) errors.add("Duplicate dust ID: " + previous + " and " + entry.getKey());
            if (occupied.containsKey(entry.getValue())) errors.add(
                "Occupied dust ID: " + entry
                    .getKey() + "=" + entry.getValue() + " owner=" + occupied.get(entry.getValue()));
        }
        for (Map.Entry<String, Integer> entry : retired.entrySet()) {
            String previous = reserved.putIfAbsent(entry.getValue(), entry.getKey());
            if (previous != null) errors.add("Duplicate historical ID: " + previous + " and " + entry.getKey());
        }
        // Allocate only a truly absent configuration, never repair a partial imported or v2 dust table.
        for (MaterialIdPreflight.Definition definition : dust) {
            if (active.containsKey(definition.key)) continue;
            if (v2 != null || legacy != null) errors.add("Missing persistent dust ID: " + definition.key);
            else if (errors.isEmpty()) {
                int id = definition.ascending ? 0 : 999, step = definition.ascending ? 1 : -1;
                while (id >= 0 && id < 1000 && (occupied.containsKey(id) || reserved.containsKey(id))) id += step;
                if (id < 0 || id >= 1000) errors.add("No free material slot for: " + definition.key);
                else {
                    active.put(definition.key, id);
                    reserved.put(id, definition.key);
                }
            }
        }
        aliases.clear();
        for (Map.Entry<String, Integer> entry : retired.entrySet())
            if (!occupied.containsKey(entry.getValue())) aliases.put(entry.getKey(), entry.getValue());
        return new Result(new Identity(origin, sourceHash, catalogHash, active, retired, aliases), errors);
    }

    public static byte[] encode(Result result, String gtVersion) {
        result.requireValid("encode");
        Properties values = payload(result.identity);
        values.setProperty(META + "schema", "2");
        values.setProperty(META + "origin", result.identity.origin);
        values.setProperty(META + "source-sha256", result.identity.sourceSha256);
        values.setProperty(META + "gt-version", gtVersion);
        values.setProperty(META + "mapping-sha256", result.identity.fingerprint);
        values.setProperty(META + "record-sha256", recordFingerprint(values));
        StringBuilder out = new StringBuilder(
            "# GTSR v2: active dust plus read-only legacy fluid evidence. Preserve with the world.\n");
        for (String key : new java.util.TreeSet<>(values.stringPropertyNames())) {
            String value = values.getProperty(key);
            if (!key.matches("[A-Za-z0-9_.:-]+") || !value.matches("[A-Za-z0-9_.,:+-]*"))
                throw new IllegalStateException("Unencodable v2 identity: " + key);
            if (key.startsWith(META)) out.append("# gtsr-provenance ");
            out.append(key.replace(":", "\\:"))
                .append('=')
                .append(value)
                .append('\n');
        }
        return out.toString()
            .getBytes(StandardCharsets.ISO_8859_1);
    }

    private static Properties payload(Identity identity) {
        Properties values = new Properties();
        values.setProperty(META + "schema", "2");
        values.setProperty(META + "catalog-sha256", identity.catalogFingerprint);
        add(values, "active:", identity.activeDustIds);
        add(values, "retired:", identity.retiredFluidIds);
        add(values, "alias:", identity.legacyFluidAliases);
        return values;
    }

    /** Provenance integrity is separate from the network mapping identity. Neither is an authentication claim. */
    private static String recordFingerprint(Properties saved) {
        Properties copy = new Properties();
        copy.putAll(saved);
        copy.remove(META + "record-sha256");
        return MaterialIdPreflight.fingerprint(copy);
    }

    private static void add(Properties values, String prefix, Map<String, Integer> ids) {
        for (Map.Entry<String, Integer> entry : ids.entrySet()) values.setProperty(
            prefix + entry.getKey(),
            entry.getValue()
                .toString());
    }

    private static Set<String> keys(List<MaterialIdPreflight.Definition> definitions) {
        Set<String> keys = new HashSet<>();
        for (MaterialIdPreflight.Definition definition : definitions) keys.add(definition.key);
        return keys;
    }

    private static void readId(Properties values, String key, String prefix, Map<String, Integer> target,
        List<String> errors) {
        try {
            String value = values.getProperty(key);
            if (!value.matches("[+-]?[0-9]+")) throw new NumberFormatException();
            int id = Integer.parseInt(value);
            if (id < 0 || id >= 1000) errors.add("Out-of-range saved ID: " + key + "=" + value);
            else target.put(key.substring(prefix.length()), id);
        } catch (NumberFormatException invalid) {
            errors.add("Non-numeric saved ID: " + key);
        }
    }

    private static Map<String, Integer> immutable(Map<String, Integer> source) {
        return Collections.unmodifiableMap(new TreeMap<>(source));
    }
}
