// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.compatibility;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.postea.api.IDExtenderCompat;
import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.fluid.ProsperityFluidContainers;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialRecipeLedger;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialDusts;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/**
 * Optional historical v1/v2 cfg -> official Postea replacements, never material registration or world admission.
 * These files are read-only old inputs; v2 metadata/aliases never restore a configuration or provenance framework.
 * Postea 1.2.9 has no one-shot item gate: occupied GT slots (including native water705) must stay untouched.
 * Retire this adapter after the supported legacy-item compatibility lifetime; do not add world scanning here.
 */
public final class ProsperityPosteaMigration {

    static final String ORIGINAL = "gregtech:gt.metaitem.01";
    private static final String[] AIR = { "wastesigh", "thickgrease", "metalgrit", "umbralmire", "sanzu_residual_steam",
        "withered_breath" };
    private static final OrePrefixes[] DUSTS = { OrePrefixes.dustTiny, OrePrefixes.dustSmall, OrePrefixes.dust };
    private static boolean registered;

    private ProsperityPosteaMigration() {}

    /**
     * PostInit after native targets are ready. Read v1 if present, otherwise the sibling historical v2; never merge.
     */
    public static void register(Path optionalLegacyCfg) {
        if (registered) return;
        Path input = migrationInput(optionalLegacyCfg);
        if (!Files.exists(input, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Loader.isModLoaded("postea")) {
            GTSteamReborn.LOG.warn("[GTSR] Legacy prosperity items unchanged: Postea is unavailable");
            return;
        }
        try {
            Properties source = read(input);
            Set<String> expected = new HashSet<>(), dust = new HashSet<>(), fluids = new HashSet<>();
            for (IndustrialRecipeLedger.MaterialDefinition entry : IndustrialRecipeLedger.get().materials) {
                expected.add(entry.id);
                ("dust".equals(entry.phase) ? dust : fluids).add(entry.id);
            }
            for (String name : AIR) {
                expected.add("air." + name);
                fluids.add("air." + name);
            }
            if (!input.equals(optionalLegacyCfg)) source = extractV2(source, dust, fluids);
            Map<String, Integer> ids = mapping(source, expected);
            Map<Integer, ItemStack> replacements = new HashMap<>();
            Map<String, Integer> occupied = new LinkedHashMap<>();
            for (IndustrialRecipeLedger.MaterialDefinition entry : IndustrialRecipeLedger.get().materials) {
                Integer old = ids.get(entry.id);
                if (old == null) continue; // Older mappings need not contain materials added in later releases.
                if (GregTechAPI.sGeneratedMaterials[old] != null) {
                    occupied.put(entry.id, old);
                    continue;
                }
                if ("dust".equals(entry.phase)) {
                    // MetaGeneratedItem01 constructor: tiny=0, small=1, dust=2; cell=30.
                    for (int prefix = 0; prefix < DUSTS.length; prefix++) replacements
                        .put(prefix * 1000 + old, ProsperityIndustrialDusts.get(entry.id, DUSTS[prefix], 1));
                } else replacements.put(30000 + old, cell(ProsperityIndustrialMaterials.get(entry.id)));
            }
            Materials[] gases = { GTSRProsperityAirMaterials.WastesSigh, GTSRProsperityAirMaterials.ThickGrease,
                GTSRProsperityAirMaterials.MetalGrit, GTSRProsperityAirMaterials.UmbralMire,
                GTSRProsperityAirMaterials.SanzuResidualSteam, GTSRProsperityAirMaterials.WitheredBreath };
            for (int i = 0; i < AIR.length; i++) {
                Integer old = ids.get("air." + AIR[i]);
                if (old == null) continue;
                if (GregTechAPI.sGeneratedMaterials[old] != null) {
                    occupied.put("air." + AIR[i], old);
                    continue;
                }
                replacements.put(30000 + old, cell(gases[i]));
            }
            if (!replacements.isEmpty()) ItemStackReplacementManager
                .addTransformationHandler(ORIGINAL, (name, tag) -> transform(name, tag, replacements));
            registered = true;
            GTSteamReborn.LOG.info(
                "[GTSR] Optional Postea prosperity replacements=" + replacements.size()
                    + ", occupied legacy slots left unchanged="
                    + occupied.size());
            if (!occupied.isEmpty()) GTSteamReborn.LOG.warn(
                "[GTSR] Native/other-mod slots are not Postea migration rules; legacy items left unchanged: "
                    + occupied);
        } catch (Exception | LinkageError invalid) {
            GTSteamReborn.LOG.warn(
                "[GTSR] Optional legacy prosperity mapping ignored; new registration is unaffected: " + input
                    + ": "
                    + invalid.getMessage());
        }
    }

    private static ItemStack cell(Materials material) {
        return ProsperityFluidContainers
            .filledCell(material.mGas != null ? material.getGas(1000) : material.getFluid(1000));
    }

    static Path migrationInput(Path legacy) {
        return Files.exists(legacy, LinkOption.NOFOLLOW_LINKS) ? legacy
            : legacy.resolveSibling("industrial-material-ids-v2.cfg");
    }

    static Properties extractV2(Properties source, Set<String> dust, Set<String> fluids) {
        Properties extracted = new Properties();
        for (String key : dust)
            if (source.containsKey("active:" + key)) extracted.setProperty(key, source.getProperty("active:" + key));
        for (String key : fluids)
            if (source.containsKey("retired:" + key)) extracted.setProperty(key, source.getProperty("retired:" + key));
        return extracted;
    }

    static Properties read(Path file) throws Exception {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > 1024 * 1024)
            throw new IllegalArgumentException("Unsafe or oversized optional historical cfg");
        Properties result = new Properties() {

            @Override
            public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate historical key: " + key);
                return super.put(key, value);
            }
        };
        try (InputStream input = Files.newInputStream(file)) {
            result.load(input);
        }
        return result;
    }

    static Map<String, Integer> mapping(Properties source, Set<String> expected) {
        Set<String> keys = new HashSet<>(source.stringPropertyNames());
        keys.removeIf(key -> key.startsWith("__gtsr."));
        if (!expected.containsAll(keys)) throw new IllegalArgumentException("Historical mapping contains unknown keys");
        Map<String, Integer> ids = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        for (String key : keys) {
            int id = Integer.parseInt(source.getProperty(key));
            if (id < 0 || id >= 1000 || !used.add(id))
                throw new IllegalArgumentException("Invalid/duplicate historical ID: " + key);
            ids.put(key, id);
        }
        return ids;
    }

    static boolean transform(String original, NBTTagCompound stack, Map<Integer, ItemStack> replacements) {
        if (!ORIGINAL.equals(original) || !stack.hasKey("Damage", 2)) return false;
        int damage = stack.getShort("Damage") & 65535;
        ItemStack target = replacements.get(damage);
        if (target == null || GregTechAPI.sGeneratedMaterials[damage % 1000] != null) return false;
        int targetId = Item.getIdFromItem(target.getItem());
        if (targetId <= 0 || stack.hasKey("tag") && !stack.hasKey("tag", 10)) return false;
        NBTTagCompound merged = stack.getCompoundTag("tag");
        if (target.hasTagCompound()) {
            for (String key : target.getTagCompound()
                .func_150296_c())
                if (merged.hasKey(key) && !merged.getTag(key)
                    .equals(
                        target.getTagCompound()
                            .getTag(key)))
                    return false;
            merged = (NBTTagCompound) merged.copy();
            for (String key : target.getTagCompound()
                .func_150296_c())
                merged.setTag(
                    key,
                    target.getTagCompound()
                        .getTag(key)
                        .copy());
            stack.setTag("tag", merged);
        }
        // setItemStackID preserves a pre-existing idExt for small targets; remove that stale identity explicitly.
        stack.removeTag("idExt");
        IDExtenderCompat.setItemStackID(stack, targetId);
        stack.setShort("Damage", (short) target.getItemDamage());
        return true;
    }
}
