// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial.compat;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdPreflight;

/**
 * Read-only adapter for the historical 105-ID cfg. Remove only when support for importing that format ends;
 * never use this adapter as an allocator or as permission to reinterpret a conflicting saved cell.
 */
public final class LegacyMaterialIdImport {

    private LegacyMaterialIdImport() {}

    public static Properties inspect(byte[] source, List<MaterialIdPreflight.Definition> definitions,
        List<String> errors) {
        MaterialIdPreflight.Result validation = MaterialIdPreflight.inspect(
            source,
            false,
            definitions,
            Collections.<Integer, String>emptyMap(),
            Collections.<String, String>emptyMap());
        errors.addAll(validation.errors);
        Properties original = MaterialIdPreflight.parse(source, new java.util.ArrayList<String>());
        Set<String> known = new HashSet<>();
        for (MaterialIdPreflight.Definition definition : definitions) {
            known.add(definition.key);
            // v1 supports allocating genuinely new additions; an import cannot prove missing historical entries.
            if (!original.containsKey(definition.key)) errors.add("Missing historical ID: " + definition.key);
        }
        for (String key : validation.ids.stringPropertyNames())
            if (!known.contains(key)) errors.add("Unknown historical material ID: " + key);
        return original;
    }
}
