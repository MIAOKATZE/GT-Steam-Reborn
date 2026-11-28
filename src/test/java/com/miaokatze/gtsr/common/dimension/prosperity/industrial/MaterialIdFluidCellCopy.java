// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.gson.Gson;

/**
 * Offline, test-source-only legacy adapters. Uses MC's native NBT and region codecs, never a live Item registry.
 * Remove only after supported legacy worlds have been converted and their native reopen evidence retained.
 * These are deliberately narrow wire-layout adapters, not replacement GT/AE implementations. Unknown layouts stop.
 */
final class MaterialIdFluidCellCopy {

    private static final String GT_277_SHA = "88a0ac96a01e11fa04f738784722ecf27d4c72488973ff41eeb1399a167801e7";
    private static final String AE_1104_SHA = "2d21c3b4d2d7b6ac997c646bd6856061db3de1f9635baf525d9c5baa7a465196";
    private static final String IC2_828_SHA = "de1d4597972be036eccd1c3b37e9980c3c9d9cdb92f52df2bf470971873893f6";
    private static final String GT_ITEM = "gregtech:gt.metaitem.01";
    private static final String NATIVE_WATER = "native:ic2distilledwater";

    public static final class FluidRule {

        public String key, fluidName, targetRegistryName;
        public int oldMaterialId, numericItemId, targetNumericItemId, targetDamage;
    }

    static final class State {

        final Properties semantics;
        final Set<String> usedSemantics = new HashSet<>();
        final Map<String, Integer> sourceSnapshot;
        final Path sourceLevel;
        final Map<String, Long> carriers = new TreeMap<>();
        final Set<String> cellPaths = new HashSet<>();
        long nativeWaterPreserved;

        State(Properties semantics, Map<String, Integer> snapshot, Path sourceLevel) {
            this.semantics = semantics;
            this.sourceSnapshot = snapshot;
            this.sourceLevel = sourceLevel;
        }
    }

    private MaterialIdFluidCellCopy() {}

    static MaterialIdOfflineCopy.Context validated(MaterialIdOfflineCopy.Plan p, Path base) throws Exception {
        if (!"reviewed-offline-copy".equals(p.mode) || !Arrays.asList("5.09.54.265", "5.09.54.277")
            .contains(p.sourceGt)
            || !"5.09.54.277".equals(p.targetGt)
            || p.sourceGtsr == null
            || p.targetGtsr == null
            || !"reviewed-per-location".equals(p.sourceHistory)
            || p.targetFingerprint == null
            || !p.targetFingerprint.matches("[a-f0-9]{64}")
            || p.fluidRules == null
            || p.fluidRules.isEmpty()
            || p.rules != null && !p.rules.isEmpty())
            throw new IOException("v2 requires reviewed per-location history, .265/.277 -> .277 and fluid-only rules");
        MaterialIdOfflineCopy.evidence(base, p.gtArtifact, p.gtArtifactSha256);
        MaterialIdOfflineCopy.evidence(base, p.targetGtArtifact, p.targetGtArtifactSha256);
        MaterialIdOfflineCopy.evidence(base, p.sourceGtsrArtifact, p.sourceGtsrArtifactSha256);
        MaterialIdOfflineCopy.evidence(base, p.targetGtsrArtifact, p.targetGtsrArtifactSha256);
        MaterialIdOfflineCopy.evidence(base, p.aeArtifact, p.aeArtifactSha256);
        MaterialIdOfflineCopy.evidence(base, p.ic2Artifact, p.ic2ArtifactSha256);
        if (!GT_277_SHA.equals(p.targetGtArtifactSha256)
            || "5.09.54.277".equals(p.sourceGt) && !GT_277_SHA.equals(p.gtArtifactSha256)
            || !AE_1104_SHA.equals(p.aeArtifactSha256)
            || !IC2_828_SHA.equals(p.ic2ArtifactSha256))
            throw new IOException("Target GT .277 / AE rv3-beta-1104 / IC2 .828 artifact mismatch");
        Properties old = MaterialIdOfflineCopy
            .properties(MaterialIdOfflineCopy.evidence(base, p.oldMapping, p.oldMappingSha256));
        Properties fluids = MaterialIdOfflineCopy
            .properties(MaterialIdOfflineCopy.evidence(base, p.newMapping, p.newMappingSha256));
        Properties meanings = MaterialIdOfflineCopy
            .properties(MaterialIdOfflineCopy.evidence(base, p.semanticEvidence, p.semanticEvidenceSha256));
        Path sourceLevel = MaterialIdOfflineCopy.evidence(base, p.sourceLevelData, p.sourceLevelDataSha256);
        Path targetLevel = MaterialIdOfflineCopy.evidence(base, p.targetLevelData, p.targetLevelDataSha256);
        Map<String, Integer> source = snapshot(readLevel(sourceLevel)), target = snapshot(readLevel(targetLevel));
        // No registry edits. FML's entire namespaced block/item map must agree, not just the two transformed items.
        if (!source.equals(target))
            throw new IOException("Source/target FML ItemData differ; registry rebasing is unsupported");
        Map<Integer, String> numeric = new HashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) if (entry.getKey()
            .charAt(0) == '\u0002')
            numeric.put(
                entry.getValue(),
                entry.getKey()
                    .substring(1));
        Set<Integer> ids = new HashSet<>();
        Set<String> ruleKeys = new HashSet<>();
        for (FluidRule rule : p.fluidRules) {
            if (rule.key == null || !ruleKeys.add(rule.key)
                || !ids.add(rule.oldMaterialId)
                || rule.oldMaterialId < 0
                || rule.oldMaterialId >= 1000
                || !Integer.toString(rule.oldMaterialId)
                    .equals(old.getProperty(rule.key))
                || rule.fluidName == null
                || !rule.fluidName.matches("[a-z0-9_.-]+")
                || !rule.fluidName.equals(fluids.getProperty(rule.key))
                || !GT_ITEM.equals(numeric.get(rule.numericItemId))
                || rule.targetRegistryName == null
                || !rule.targetRegistryName.equals(numeric.get(rule.targetNumericItemId))
                || !"IC2:itemFluidCell".equals(rule.targetRegistryName)
                || rule.targetDamage != 0)
                throw new IOException("Unproven old material / target universal-cell identity: " + rule.key);
        }
        for (String location : meanings.stringPropertyNames()) {
            String meaning = meanings.getProperty(location);
            if (location.isEmpty() || !ruleKeys.contains(meaning) && !NATIVE_WATER.equals(meaning))
                throw new IOException("Unknown per-location semantic evidence: " + location);
        }
        MaterialIdOfflineCopy.Context context = new MaterialIdOfflineCopy.Context(p, numeric);
        context.fluids = new State(meanings, source, sourceLevel);
        return context;
    }

    private static NBTTagCompound readLevel(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        boolean gzip = bytes.length > 2 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139;
        return MaterialIdOfflineCopy.decode(bytes, gzip);
    }

    static Map<String, Integer> snapshot(NBTTagCompound root) throws IOException {
        if (!root.hasKey("FML", 10) || !root.getCompoundTag("FML")
            .hasKey("ItemData", 9))
            throw new IOException("Missing native FML.ItemData; a handwritten numeric registry is insufficient for v2");
        NBTTagList list = root.getCompoundTag("FML")
            .getTagList("ItemData", 10);
        if (list.tagCount() == 0 || list.tagCount() != ((NBTTagList) root.getCompoundTag("FML")
            .getTag("ItemData")).tagCount()) throw new IOException("Invalid FML.ItemData list");
        Map<String, Integer> result = new TreeMap<>();
        Set<String> numeric = new HashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound record = list.getCompoundTagAt(i);
            String key = record.getString("K");
            int value = record.getInteger("V");
            if (!record.hasKey("K", 8) || !record.hasKey("V", 3)
                || key.length() < 4
                || key.charAt(0) != '\u0001' && key.charAt(0) != '\u0002'
                || !key.substring(1)
                    .contains(":")
                || value < 0
                || value > 65535
                || result.put(key, value) != null
                || !numeric.add(key.charAt(0) + "/" + value))
                throw new IOException("Ambiguous or malformed FML.ItemData record " + i);
        }
        return result;
    }

    static void bindSource(Path source, MaterialIdOfflineCopy.Context context) throws IOException {
        Path actual = source.resolve("level.dat");
        if (!Files.isRegularFile(actual) || !actual.toRealPath()
            .equals(context.fluids.sourceLevel.toRealPath()))
            throw new IOException("sourceLevelData must name this detached source's level.dat");
        Path backup = source.resolve("level.dat_old");
        if (Files.exists(backup) && !snapshot(readLevel(backup)).equals(context.fluids.sourceSnapshot))
            throw new IOException(
                "level.dat_old contains a different FML registry; review separate historical evidence");
    }

    static void finish(MaterialIdOfflineCopy.Context context, Map<String, Object> report) throws IOException {
        if (!context.fluids.usedSemantics.equals(context.fluids.semantics.stringPropertyNames()))
            throw new IOException("Unused semantic evidence entries; source paths no longer match reviewed provenance");
        report.put("adapterRecords", context.fluids.carriers);
        report.put("nativeDistilledWaterPreserved", context.fluids.nativeWaterPreserved);
        report.put("semanticLocationsVerified", context.fluids.usedSemantics.size());
        report.put("registryRewrite", false);
        report.put("nativeGameReopenVerified", false);
        report.put("targetFingerprint", context.plan.targetFingerprint);
        report.put("adaptersVerified", true);
    }

    static void publishAdmission(Path target, Path reviewFile, MaterialIdOfflineCopy.Context context)
        throws IOException {
        // This is a provisional first-load approval candidate, not a declaration that the world has passed game tests.
        // It is written outside both worlds. Installing it in config is a separate, explicitly reviewed operator step.
        Map<String, Object> approval = new java.util.LinkedHashMap<>();
        approval.put("schema", 1);
        approval.put(
            "world",
            target.toAbsolutePath()
                .normalize()
                .toString());
        approval.put("mapping", context.plan.targetFingerprint);
        approval.put("markerSha256", MaterialIdOfflineCopy.hash(target.resolve(MaterialIdOfflineCopy.MARKER)));
        approval.put("levelSha256", MaterialIdOfflineCopy.hash(target.resolve("level.dat")));
        @SuppressWarnings("unchecked")
        Map<String, Object> review = new Gson()
            .fromJson(new String(Files.readAllBytes(reviewFile), StandardCharsets.UTF_8), Map.class);
        review.put("admissionApproval", approval);
        MaterialIdOfflineCopy.publishJson(reviewFile, review);
    }

    static void rewrite(NBTBase tag, String path, MaterialIdOfflineCopy.Context context, int depth) throws IOException {
        if (depth > 64 || ++context.nodes > 4_000_000)
            throw new IOException("NBT depth/node budget exceeded at " + path);
        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            if (list.func_150303_d() == 10) for (int i = 0; i < list.tagCount(); i++)
                rewrite(list.getCompoundTagAt(i), path + "[" + i + "]", context, depth + 1);
            else if (list.func_150303_d() == 9 || list.func_150303_d() == 7 || list.func_150303_d() == 11)
                throw new IOException("Unreviewed nested/opaque list at " + path);
            return;
        }
        if (!(tag instanceof NBTTagCompound)) return;
        NBTTagCompound compound = (NBTTagCompound) tag;
        if (compound.hasKey("StackType")) {
            String type = compound.getString("StackType");
            if (!compound.hasKey("StackType", 8) || !Arrays.asList("item", "fluid")
                .contains(type)) throw new IOException("Unknown AE StackType at " + path);
            if ("fluid".equals(type)) {
                validateFluidStack(compound, path);
                carrier(context, "AE generic fluid (preserved)");
                children(compound, path, context, depth, new HashSet<>(Arrays.asList("Count")));
                return;
            }
        }
        if (compound.hasKey("Damage") || compound.hasKey("Count") && compound.hasKey("id")) {
            item(compound, path, context);
        } else if (compound.hasKey("id")) {
            if (!compound.hasKey("id", 8)) throw new IOException("Unknown non-stack numeric ID at " + path);
            nativeCarrier(compound, path, context);
        } else {
            for (String key : compound.func_150296_c())
                if (key.matches("(?i)(storedItemId|itemId|itemDamage|itemMeta|packedItems|compressedItems)"))
                    throw new IOException("Unreviewed primitive item encoding at " + path + "." + key);
        }
        children(compound, path, context, depth, java.util.Collections.<String>emptySet());
    }

    private static void children(NBTTagCompound compound, String path, MaterialIdOfflineCopy.Context context, int depth,
        Set<String> skip) throws IOException {
        for (String key : new TreeSet<>(compound.func_150296_c())) {
            if (skip.contains(key)) continue;
            NBTBase child = compound.getTag(key);
            if (child.getId() == 7 || child.getId() == 11) {
                boolean section = path.matches(".*\\.Level\\.Sections\\[[0-9]+\\]");
                boolean structural = section && Arrays.asList("Blocks", "Data", "Add", "BlockLight", "SkyLight")
                    .contains(key) || path.endsWith(".Level")
                        && Arrays.asList("Biomes", "HeightMap")
                            .contains(key)
                    || path.endsWith(".FML") && key.equals("BlockedItemIds") && child.getId() == 11;
                if (!structural) throw new IOException("Unsupported opaque/external data at " + path + "." + key);
            } else rewrite(child, path + "." + key, context, depth + 1);
        }
        if (compound.hasKey("it") || hasSlot(compound)) {
            if (!context.fluids.cellPaths.contains(path))
                throw new IOException("AE-shaped data outside an audited AE cell at " + path);
            validateCell(compound, path, context);
        }
    }

    private static boolean hasSlot(NBTTagCompound compound) {
        for (String key : compound.func_150296_c()) if (key.matches("[#@][0-9]+")) return true;
        return false;
    }

    private static void item(NBTTagCompound stack, String path, MaterialIdOfflineCopy.Context context)
        throws IOException {
        // MC ItemStack: short id/Damage, byte Count. GTUtility additionally saves Count as int.
        // AEItemStack: same identity, Count=0 byte plus long Cnt/Req and byte Craft; never narrow these values.
        if (!stack.hasKey("id", 2) || !stack.hasKey("Damage", 2)
            || !stack.hasKey("Count", 1) && !stack.hasKey("Count", 3))
            throw new IOException("Unknown MC/GT/AE stack encoding at " + path);
        int id = stack.getShort("id") & 65535, damage = stack.getShort("Damage") & 65535;
        String name = context.registry.get(id);
        if (name == null) throw new IOException("Item absent from source FML ItemData at " + path);
        long quantity = quantity(stack, path);
        if (name.startsWith("appliedenergistics2:")) {
            boolean cell = name.matches("appliedenergistics2:item\\.ItemBasicStorageCell\\.(1|4|16|64)k");
            boolean pattern = name.equals("appliedenergistics2:item.ItemEncodedPattern");
            if (!cell && !pattern) throw new IOException("Unreviewed AE item/storage at " + path + ": " + name);
            if (stack.hasKey("tag", 10)) {
                NBTTagCompound data = stack.getCompoundTag("tag");
                if (cell) context.fluids.cellPaths.add(path + ".tag");
                else validatePattern(data, path + ".tag", context);
            }
        } else if (name.startsWith("ae2:") || name.startsWith("ae:"))
            throw new IOException("Unknown AE addon codec at " + path);
        FluidRule selected = null;
        for (FluidRule rule : context.plan.fluidRules)
            if (id == rule.numericItemId && damage == 30000 + rule.oldMaterialId) {
                if (selected != null) throw new IOException("Ambiguous fluid rule at " + path);
                selected = rule;
            }
        if (selected == null) {
            if (name.startsWith("gregtech:gt.metaitem.") && damage < 32000) {
                for (FluidRule rule : context.plan.fluidRules) if (damage % 1000 == rule.oldMaterialId)
                    throw new IOException("Unreviewed fluid material item/prefix at " + path);
            }
            return;
        }
        String meaning = context.fluids.semantics.getProperty(path);
        if (meaning == null) throw new IOException(
            "Missing authoritative per-location meaning at " + path
                + "; GT version and old cfg alone cannot disambiguate mixed 30705");
        context.fluids.usedSemantics.add(path);
        if (NATIVE_WATER.equals(meaning)) {
            if (selected.oldMaterialId != 705)
                throw new IOException("Native water evidence on non-705 cell at " + path);
            context.fluids.nativeWaterPreserved++;
            return;
        }
        if (!selected.key.equals(meaning)) throw new IOException("Semantic evidence disagrees with rule at " + path);
        if (stack.hasKey("tag") && !stack.hasKey("tag", 10)) throw new IOException("Non-compound item tag at " + path);
        NBTTagCompound custom = stack.hasKey("tag", 10) ? stack.getCompoundTag("tag") : new NBTTagCompound();
        if (custom.hasKey("Fluid")) throw new IOException("Existing Fluid key needs explicit review at " + path);
        NBTTagCompound before = (NBTTagCompound) stack.copy();
        NBTTagCompound fluid = new NBTTagCompound();
        fluid.setString("FluidName", selected.fluidName);
        fluid.setInteger("Amount", 1000);
        custom.setTag("Fluid", fluid);
        stack.setTag("tag", custom);
        stack.setShort("id", (short) selected.targetNumericItemId);
        stack.setShort("Damage", (short) selected.targetDamage);
        NBTTagCompound conserved = (NBTTagCompound) stack.copy();
        conserved.setShort("id", (short) id);
        conserved.setShort("Damage", (short) damage);
        conserved.getCompoundTag("tag")
            .removeTag("Fluid");
        if (!before.hasKey("tag")) conserved.removeTag("tag");
        if (!before.equals(conserved)) throw new IOException("Non-target NBT changed at " + path);
        try {
            long total = Math.addExact(context.quantities.getOrDefault(selected.key, 0L), quantity);
            Math.multiplyExact(total, 1000L);
            context.quantities.put(selected.key, total);
        } catch (ArithmeticException overflow) {
            throw new IOException("Quantity/fluid-volume report overflow at " + path, overflow);
        }
        context.operations.add(
            path + ": "
                + name
                + "/"
                + damage
                + " -> "
                + selected.targetRegistryName
                + "/"
                + selected.targetDamage
                + " Fluid="
                + selected.fluidName
                + " Amount=1000 Count="
                + quantity);
    }

    private static long quantity(NBTTagCompound stack, String path) throws IOException {
        if (stack.hasKey("Cnt") || stack.hasKey("Req") || stack.hasKey("Craft")) {
            if (!stack.hasKey("Cnt", 4) || !stack.hasKey("Req", 4)
                || !stack.hasKey("Craft", 1)
                || !stack.hasKey("Count", 1)
                || stack.getByte("Count") != 0
                || stack.getLong("Cnt") < 0
                || stack.getLong("Req") < 0) throw new IOException("Malformed AE long-count stack at " + path);
            return stack.getLong("Cnt");
        }
        long count = stack.hasKey("Count", 3) ? stack.getInteger("Count") : stack.getByte("Count");
        if (count <= 0) throw new IOException("Invalid native/GT Count at " + path);
        return count;
    }

    private static void validateFluidStack(NBTTagCompound fluid, String path) throws IOException {
        if (!fluid.hasKey("FluidName", 8) || fluid.getString("FluidName")
            .isEmpty()
            || !fluid.hasKey("Cnt", 4)
            || fluid.getLong("Cnt") < 0
            || !fluid.hasKey("Count", 1)
            || fluid.getByte("Count") != 0
            || !fluid.hasKey("Req", 4)
            || !fluid.hasKey("Craft", 1)
            || fluid.hasKey("id")
            || fluid.hasKey("Damage")) throw new IOException("Malformed AE fluid StackType at " + path);
    }

    private static void validatePattern(NBTTagCompound data, String path, MaterialIdOfflineCopy.Context context)
        throws IOException {
        for (String name : Arrays.asList("in", "out")) {
            if (!data.hasKey(name, 9)) throw new IOException("Missing AE pattern " + name + " at " + path);
            NBTTagList list = data.getTagList(name, 10);
            if (list.tagCount() != ((NBTTagList) data.getTag(name)).tagCount())
                throw new IOException("Malformed AE pattern list at " + path);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                // Empty slots are valid ordinary crafting patterns; generic ultimate patterns use StackType=item/fluid.
                if (entry.hasNoTags()) continue;
                if (entry.hasKey("StackType") && !Arrays.asList("item", "fluid")
                    .contains(entry.getString("StackType")))
                    throw new IOException("Unknown ultimate pattern StackType at " + path + "." + name + "[" + i + "]");
                if (!entry.hasKey("StackType", 8) || !entry.getString("StackType")
                    .equals("fluid"))
                    if (!entry.hasKey("id", 2) || !entry.hasKey("Damage", 2) || !entry.hasKey("Count", 1))
                        throw new IOException("Malformed pattern ItemStack at " + path);
            }
        }
        carrier(context, "AE ordinary/ultimate pattern");
    }

    private static void validateCell(NBTTagCompound data, String path, MaterialIdOfflineCopy.Context context)
        throws IOException {
        int types = data.hasKey("it") ? data.getShort("it") : 0;
        if (data.hasKey("it") && !data.hasKey("it", 2) || types < 0 || types > 63)
            throw new IOException("Invalid AE type count at " + path);
        long total = 0;
        for (int i = 0; i < types; i++) {
            if (!data.hasKey("#" + i, 10)) throw new IOException("Missing AE #N slot at " + path);
            NBTTagCompound slot = data.getCompoundTag("#" + i);
            long count = quantity(slot, path + ".#" + i);
            // Native CellInventory prefers @N>0, falling back to Cnt. Preserve both, including legacy absent @N.
            if (data.hasKey("@" + i)) {
                if (!data.hasKey("@" + i, 4) || data.getLong("@" + i) < 0)
                    throw new IOException("Malformed AE @N at " + path);
                if (data.getLong("@" + i) > 0 && data.getLong("@" + i) != count)
                    throw new IOException("AE @N/Cnt disagreement needs review at " + path);
            }
            try {
                total = Math.addExact(total, count);
            } catch (ArithmeticException overflow) {
                throw new IOException("AE total overflow at " + path, overflow);
            }
        }
        for (String key : data.func_150296_c()) if (key.matches("[#@][0-9]+")) {
            long index;
            try {
                index = Long.parseLong(key.substring(1));
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid AE slot index at " + path, invalid);
            }
            if (index >= types) throw new IOException("Unaccounted AE slot at " + path + "." + key);
        }
        if (types > 0 && (!data.hasKey("ic", 4) || data.getLong("ic") != total))
            throw new IOException("AE stored total disagrees with slots at " + path);
        carrier(context, "AE item cell #N/@N");
    }

    private static void nativeCarrier(NBTTagCompound data, String path, MaterialIdOfflineCopy.Context context)
        throws IOException {
        String id = data.getString("id");
        if ("GT_MetaTileEntity".equals(id)) {
            if (!data.hasKey("mID", 3) || !data.hasKey("Inventory", 9) && !data.hasKey("GTInventory", 9)
                && !data.hasKey("mOutputItemsLength", 3)) throw new IOException("Unreviewed GT delegate at " + path);
            Set<String> fields = new HashSet<>(
                Arrays.asList(
                    "id",
                    "x",
                    "y",
                    "z",
                    "mID",
                    "nbtVersion",
                    "Inventory",
                    "GTInventory",
                    "mOutputItemsLength",
                    "mOutputFluidsLength",
                    "mEUt",
                    "mProgresstime",
                    "mMaxProgresstime",
                    "mEfficiencyIncrease",
                    "mEfficiency",
                    "mPollution",
                    "mRuntime",
                    "mTotalRunTime",
                    "mLastWorkingTick",
                    "recipesDone",
                    "machineMode",
                    "mStoredSteam",
                    "mStoredEnergy",
                    "mWasShutdown",
                    "mLightValue",
                    "mOtherUpgrades",
                    "mFacing",
                    "mOwnerName",
                    "mOwnerUuid",
                    "mLockUpgrade",
                    "mMuffler",
                    "mActive",
                    "mWorks",
                    "mRedstone",
                    "mInputDisabled",
                    "mOutputDisabled",
                    "mWrench",
                    "mScrewdriver",
                    "mSoftMallet",
                    "mHardHammer",
                    "mSolderingTool",
                    "mCrowbar",
                    "lastParallel",
                    "powerPanelMaxParallel",
                    "alwaysMaxParallel",
                    "makePowerfailEvents",
                    "usesTurbine",
                    "canBeMuffled"));
            int outputs = data.getInteger("mOutputItemsLength");
            int fluids = data.getInteger("mOutputFluidsLength");
            if (outputs < 0 || outputs > 65536) throw new IOException("Invalid GT output length at " + path);
            if (fluids < 0 || fluids > 65536) throw new IOException("Invalid GT output fluid length at " + path);
            for (String field : data.func_150296_c()) {
                if (field.matches("mOutputItem[0-9]+") && Integer.parseInt(field.substring(11)) < outputs) {
                    if (!data.hasKey(field, 10)) throw new IOException("Invalid GT paid output at " + path);
                } else if (field.matches("mOutputFluids[0-9]+") && Integer.parseInt(field.substring(13)) < fluids) {
                    NBTTagCompound fluid = data.getCompoundTag(field);
                    if (!data.hasKey(field, 10) || !fluid.hasKey("FluidName", 8) || !fluid.hasKey("Amount", 3))
                        throw new IOException("Invalid GT paid fluid output at " + path);
                } else if (!fields.contains(field))
                    throw new IOException("Unreviewed GT machine/cover/delegate field at " + path + "." + field);
            }
            for (String inventory : Arrays.asList("Inventory", "GTInventory")) if (data.hasKey(inventory)) {
                if (!data.hasKey(inventory, 9)) throw new IOException("Invalid GT inventory at " + path);
                NBTTagList list = data.getTagList(inventory, 10);
                if (list.tagCount() != ((NBTTagList) data.getTag(inventory)).tagCount())
                    throw new IOException("Invalid GT inventory list at " + path);
                Set<Integer> slots = new HashSet<>();
                for (int i = 0; i < list.tagCount(); i++) {
                    NBTTagCompound item = list.getCompoundTagAt(i);
                    if (!item.hasKey("IntSlot", 3) || item.getInteger("IntSlot") < 0
                        || !slots.add(item.getInteger("IntSlot")))
                        throw new IOException("Invalid/duplicate GT IntSlot at " + path);
                }
            }
            carrier(context, "GT Inventory/IntSlot and paid mOutputItemN");
            return;
        }
        if (Arrays.asList("Chest", "Trap", "Dropper", "Furnace", "Hopper")
            .contains(id)) {
            Set<String> fields = new HashSet<>(
                Arrays.asList("id", "x", "y", "z", "Items", "CustomName", "BurnTime", "CookTime", "TransferCooldown"));
            if (!data.hasKey("Items", 9)) throw new IOException("Missing native inventory at " + path);
            for (String field : data.func_150296_c()) if (!fields.contains(field))
                throw new IOException("Unreviewed native carrier field at " + path + "." + field);
            carrier(context, "MC native inventory");
            return;
        }
        if (Arrays.asList("Item", "MinecartChest", "MinecartHopper")
            .contains(id)) {
            Set<String> fields = new HashSet<>(
                Arrays.asList(
                    "id",
                    "Pos",
                    "Motion",
                    "Rotation",
                    "FallDistance",
                    "Fire",
                    "Air",
                    "OnGround",
                    "Dimension",
                    "Invulnerable",
                    "PortalCooldown",
                    "UUIDMost",
                    "UUIDLeast",
                    "CustomName",
                    "CustomNameVisible",
                    "Item",
                    "Items",
                    "Age",
                    "Health",
                    "PickupDelay",
                    "Owner",
                    "Thrower",
                    "TransferCooldown",
                    "Enabled"));
            for (String field : data.func_150296_c()) if (!fields.contains(field))
                throw new IOException("Unreviewed native entity extension at " + path + "." + field);
            carrier(context, "MC item/inventory entity");
            return;
        }
        throw new IOException("Unknown external entity/tile/delegate at " + path + ": " + id);
    }

    private static void carrier(MaterialIdOfflineCopy.Context context, String kind) {
        context.fluids.carriers.merge(kind, 1L, Long::sum);
    }
}
