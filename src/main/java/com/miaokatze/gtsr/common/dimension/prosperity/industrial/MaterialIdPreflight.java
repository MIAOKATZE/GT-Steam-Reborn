// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Pure, side-effect-free registration preflight. Never edits Materials or persistent input. */
public final class MaterialIdPreflight {

    private static final String META = "__gtsr.";
    private static final String PROVENANCE_COMMENT = "# gtsr-provenance ";

    private MaterialIdPreflight() {}

    public static final class Definition {

        public final String key, name;
        public final boolean addition, ascending;

        public Definition(String key, String name, boolean addition, boolean ascending) {
            this.key = key;
            this.name = name;
            this.addition = addition;
            this.ascending = ascending;
        }
    }

    public static final class Result {

        public final Properties ids;
        public final List<String> errors;
        public final String origin;

        private Result(Properties ids, List<String> errors, String origin) {
            this.ids = ids;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
            this.origin = origin;
        }

        public void requireValid(String context) {
            if (!errors.isEmpty()) throw new IllegalStateException(
                "Material ID preflight rejected [" + context
                    + "]:\n"
                    + String.join("\n", errors)
                    + "\nKeep the original config and world. Restore their matching mapping; use tools/material-ids for offline diagnosis.");
        }
    }

    public static Result inspect(byte[] source, boolean explicitNewInstance, List<Definition> definitions,
        Map<Integer, String> liveSlots, Map<String, String> liveNames) {
        final List<String> errors = new ArrayList<>();
        Properties loaded = new Properties() {

            @Override
            public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) errors.add("Duplicate property key: " + key);
                return super.put(key, value);
            }
        };
        if (source != null) {
            loadSource(loaded, source, errors);
        } else if (!explicitNewInstance) {
            errors.add(
                "Mapping missing; absence does not prove a new instance. Restore the original cfg. Only a confirmed new instance may use -Dgtsr.materialIds.initializeNewInstance=true.");
        }
        Properties ids = new Properties();
        Map<Integer, String> reserved = new HashMap<>();
        for (String key : new java.util.TreeSet<>(loaded.stringPropertyNames())) {
            if (key.startsWith(META)) continue;
            String value = loaded.getProperty(key);
            ids.setProperty(key, value);
            try {
                if (!value.matches("[+-]?[0-9]+")) throw new NumberFormatException();
                int id = Integer.parseInt(value);
                if (id < 0 || id >= 1000) errors.add("Out-of-range saved ID: " + key + "=" + value);
                else {
                    String previous = reserved.putIfAbsent(id, key);
                    if (previous != null) errors.add("Duplicate saved ID: " + previous + " and " + key + "=" + id);
                    if (liveSlots.containsKey(id))
                        errors.add("Occupied saved ID: " + key + "=" + id + " owner=" + liveSlots.get(id));
                }
            } catch (NumberFormatException invalid) {
                errors.add("Non-numeric saved ID: " + key + "=" + value);
            }
        }
        boolean recorded = loaded.containsKey(META + "schema");
        String origin = source == null ? "explicit-new-instance"
            : recorded ? loaded.getProperty(META + "origin") : "legacy-import";
        if (recorded) {
            if (!"1".equals(loaded.getProperty(META + "schema"))) errors.add("Unsupported provenance schema");
            if (!fingerprint(ids).equals(loaded.getProperty(META + "mapping-sha256")))
                errors.add("Mapping provenance fingerprint mismatch");
            String knownKeys = loaded.getProperty(META + "keys", "");
            if (knownKeys.isEmpty() || origin == null || loaded.getProperty(META + "gt-version") == null)
                errors.add("Incomplete provenance record");
            for (String key : knownKeys.split(","))
                if (!ids.containsKey(key)) errors.add("Missing previously recorded key: " + key);
        } else {
            for (String key : loaded.stringPropertyNames())
                if (key.startsWith(META)) errors.add("Partial provenance record: " + key);
        }
        Map<String, Definition> names = new LinkedHashMap<>();
        Map<String, Definition> keys = new LinkedHashMap<>();
        for (Definition definition : definitions) {
            if (keys.put(definition.key, definition) != null) errors.add("Duplicate definition key: " + definition.key);
            if (names.put(definition.name, definition) != null)
                errors.add("Duplicate definition name: " + definition.name);
            if (liveNames.containsKey(definition.name)) errors.add(
                "Occupied material name: " + definition.key
                    + " name="
                    + definition.name
                    + " owner="
                    + liveNames.get(definition.name));
            // Legacy imports cannot prove that a missing historical addition was never allocated.
            if (source != null && !ids.containsKey(definition.key) && (!recorded || !definition.addition)) errors.add(
                "Missing persistent ID: " + definition.key
                    + "; legacy partial mappings require explicit source recovery");
        }
        // Do not allocate anything when the complete read-only validation has failed.
        if (errors.isEmpty()) {
            for (Definition definition : definitions) {
                if (ids.containsKey(definition.key)) continue;
                int id = definition.ascending ? 0 : 999;
                int step = definition.ascending ? 1 : -1;
                while (id >= 0 && id < 1000 && (reserved.containsKey(id) || liveSlots.containsKey(id))) id += step;
                if (id < 0 || id >= 1000) errors.add("No free material slot for: " + definition.key);
                else {
                    ids.setProperty(definition.key, Integer.toString(id));
                    reserved.put(id, definition.key);
                }
            }
        }
        return new Result(ids, errors, origin);
    }

    /** Read each logical Properties record separately so malformed escapes cannot hide later conflicts. */
    private static void loadSource(Properties loaded, byte[] source, List<String> errors) {
        StringBuilder record = new StringBuilder();
        boolean provenance = false;
        int lineNumber = 0, startLine = 0;
        for (String line : new String(source, StandardCharsets.ISO_8859_1).split("\\r?\\n", -1)) {
            lineNumber++;
            if (record.length() == 0) {
                startLine = lineNumber;
                provenance = line.startsWith(PROVENANCE_COMMENT);
                if (provenance) line = line.substring(PROVENANCE_COMMENT.length());
                else if (line.trim()
                    .startsWith("#")
                    || line.trim()
                        .startsWith("!"))
                    continue;
            }
            record.append(line)
                .append('\n');
            int slashes = 0;
            for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) slashes++;
            if ((slashes & 1) != 0) continue;
            final Map<String, String> parsed = new LinkedHashMap<>();
            Properties one = new Properties() {

                @Override
                public synchronized Object put(Object key, Object value) {
                    parsed.put((String) key, (String) value);
                    return super.put(key, value);
                }
            };
            try {
                one.load(
                    new ByteArrayInputStream(
                        record.toString()
                            .getBytes(StandardCharsets.ISO_8859_1)));
                for (Map.Entry<String, String> entry : parsed.entrySet()) {
                    if (provenance != entry.getKey()
                        .startsWith(META)) {
                        errors
                            .add("Misplaced provenance/material property at line " + startLine + ": " + entry.getKey());
                    } else loaded.setProperty(entry.getKey(), entry.getValue());
                }
            } catch (IOException | IllegalArgumentException invalid) {
                errors.add("Malformed mapping at line " + startLine + ": " + invalid.getMessage());
            }
            record.setLength(0);
        }
        if (record.length() != 0) errors.add("Incomplete continued property at line " + startLine);
    }

    public static byte[] encode(Result result, String gtVersion) {
        result.requireValid("encode");
        TreeMap<String, String> entries = entries(result.ids);
        entries.put(META + "schema", "1");
        entries.put(META + "origin", result.origin);
        entries.put(META + "gt-version", gtVersion);
        entries.put(META + "mapping-sha256", fingerprint(result.ids));
        entries.put(META + "keys", String.join(",", new java.util.TreeSet<>(result.ids.stringPropertyNames())));
        StringBuilder text = new StringBuilder(
            "# Persistent GT IDs. Preserve with the world. Never reallocate saved IDs.\n");
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            // All emitted identities are ledger keys; reject escapes rather than emitting ambiguous Properties.
            if (!entry.getKey()
                .matches("[A-Za-z0-9_.:-]+")
                || !entry.getValue()
                    .matches("[A-Za-z0-9_.,:+-]+"))
                throw new IllegalStateException("Unencodable mapping identity: " + entry.getKey());
            if (entry.getKey()
                .startsWith(META)) text.append(PROVENANCE_COMMENT);
            text.append(
                entry.getKey()
                    .replace(":", "\\:"))
                .append('=')
                .append(entry.getValue())
                .append('\n');
        }
        return text.toString()
            .getBytes(StandardCharsets.ISO_8859_1);
    }

    private static TreeMap<String, String> entries(Properties ids) {
        TreeMap<String, String> entries = new TreeMap<>();
        for (String key : ids.stringPropertyNames()) entries.put(key, ids.getProperty(key));
        return entries;
    }

    public static String fingerprint(Properties ids) {
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> entry : entries(ids).entrySet()) canonical.append(entry.getKey())
            .append('=')
            .append(entry.getValue())
            .append('\n');
        return sha256(
            canonical.toString()
                .getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256")
                .digest(bytes)) hex.append(String.format("%02x", value & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
