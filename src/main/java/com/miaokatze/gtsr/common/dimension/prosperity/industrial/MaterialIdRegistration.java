// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import gregtech.api.enums.Materials;

/** Durable startup reservation. Call before either facade family is constructed; never mutates Materials. */
public final class MaterialIdRegistration {

    private static Path legacyFile;
    private static volatile MaterialIdSchema.Identity startup;

    private MaterialIdRegistration() {}

    public static synchronized void configure(File originalFile) {
        Path requested = originalFile.toPath()
            .toAbsolutePath()
            .normalize();
        if (legacyFile != null && !legacyFile.equals(requested))
            throw new IllegalStateException("Material ID source already configured");
        legacyFile = requested;
    }

    public static synchronized void prepareEarlyRegistration() {
        if (startup != null) return;
        if (legacyFile == null) throw new IllegalStateException("Configure material IDs before GT materials init");
        Path target = legacyFile.resolveSibling("industrial-material-ids-v2.cfg");
        Map<Integer, String> occupied = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Materials material : Materials.getMaterialsMap()
            .values()) {
            names.put(material.mName, material.mName);
            if (material.mMetaItemSubID >= 0)
                occupied.merge(material.mMetaItemSubID, material.mName, (left, right) -> left + "," + right);
        }
        List<MaterialIdPreflight.Definition> dust = new ArrayList<>(), fluids = new ArrayList<>();
        for (IndustrialRecipeLedger.MaterialDefinition entry : IndustrialRecipeLedger.get().materials) {
            MaterialIdPreflight.Definition definition = new MaterialIdPreflight.Definition(
                entry.id,
                entry.name,
                entry.introducedVersion > 0,
                false);
            ("dust".equals(entry.phase) ? dust : fluids).add(definition);
        }
        fluids.addAll(GTSRProsperityAirMaterials.cellIdDefinitions());
        try {
            byte[] legacy = read(legacyFile), before = read(target);
            MaterialIdSchema.Result result = MaterialIdSchema.inspect(before, legacy, dust, fluids, occupied, names);
            ModContainer gregtech = Loader.instance()
                .getIndexedModList()
                .get("gregtech");
            String version = gregtech == null ? "missing" : gregtech.getVersion();
            result.requireValid("GT=" + version + ", cfg=" + target);
            byte[] replacement = MaterialIdSchema.encode(result, version);
            if (!Arrays.equals(before, replacement))
                MaterialIdFileStore.publishWithReadOnlySource(target, before, replacement, legacyFile, legacy);
            else {
                MaterialIdFileStore.requireUnchanged(target, before);
                MaterialIdFileStore.requireUnchanged(legacyFile, legacy);
            }
            // Identity becomes observable only after durable publication and source revalidation.
            startup = result.identity;
        } catch (IOException failure) {
            throw new IllegalStateException(
                "Cannot publish material ID v2; no material identities applied: " + target,
                failure);
        }
    }

    private static byte[] read(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic-link mapping rejected: " + path);
        return Files.exists(path) ? Files.readAllBytes(path) : null;
    }

    public static MaterialIdSchema.Identity startupIdentity() {
        if (startup == null) throw new IllegalStateException("Material ID startup preflight has not completed");
        return startup;
    }

    public static Path configDirectory() {
        if (legacyFile == null) throw new IllegalStateException("Material ID source not configured");
        return legacyFile.getParent();
    }

    public static int idFor(String key) {
        Integer id = startupIdentity().activeDustIds.get(key);
        if (id == null) throw new IllegalArgumentException("Not an active dust identity: " + key);
        return id;
    }

    public static int idFor(String namespace, String key) {
        return idFor(qualified(namespace, key));
    }

    public static int legacyFluidId(String namespace, String key) {
        Integer id = startupIdentity().legacyFluidAliases.get(qualified(namespace, key));
        return id == null ? -1 : id;
    }

    private static String qualified(String namespace, String key) {
        if (key.startsWith("planned:") || key.startsWith("air.")) return key;
        return "air".equals(namespace) ? "air." + key : namespace + ":" + key;
    }

    /** Defensive copy retained for callers that still consume Properties. */
    public static Properties snapshot() {
        Properties copy = new Properties();
        for (Map.Entry<String, Integer> entry : startupIdentity().activeDustIds.entrySet()) copy.setProperty(
            entry.getKey(),
            entry.getValue()
                .toString());
        return copy;
    }
}
