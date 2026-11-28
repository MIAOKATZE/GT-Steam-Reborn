// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.storage.RegionFile;

import com.google.gson.Gson;

/** Synthetic directories only. Never accepts a user world path or searches instances. */
public final class MaterialIdOfflineCopyContracts {

    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        java.lang.reflect.Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, MaterialIdOfflineCopyContracts.class.getName(), args)) return;
        Path scratch = Files.createTempDirectory("gtsr-material-copy-contract-");
        try {
            run(scratch);
            System.out.println(
                "PASS offline material copy: " + checks
                    + " checks; synthetic_only=true; AE_supported=false; world_ready=false");
        } finally {
            // Only the directory returned by createTempDirectory above is ever removed.
            try (java.util.stream.Stream<Path> paths = Files.walk(scratch)) {
                java.util.Iterator<Path> files = paths.sorted(java.util.Comparator.reverseOrder())
                    .iterator();
                while (files.hasNext()) Files.deleteIfExists(files.next());
            }
        }
    }

    private static void run(Path scratch) throws Exception {
        Path source = Files.createDirectory(scratch.resolve("synthetic-source"));
        Path target = scratch.resolve("synthetic-target");
        Path planPath = scratch.resolve("plan.json");
        Path review = scratch.resolve("review.json");
        Path oldMap = scratch.resolve("old.cfg"), newMap = scratch.resolve("new.cfg"),
            registry = scratch.resolve("registry.properties");
        int newId = 999;
        java.util.Set<Integer> nativeIds = new java.util.HashSet<>();
        for (int id : MaterialIdOfflineCopy.NATIVE_277_IDS) nativeIds.add(id);
        while (nativeIds.contains(newId)) newId--;
        Files.write(oldMap, "planned\\:native_acid_cut=705\n".getBytes(StandardCharsets.US_ASCII));
        Files.write(newMap, ("planned\\:native_acid_cut=" + newId + "\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(
            registry,
            "1000=gregtech\\:gt.metaitem.01\n1001=fixture\\:unrelated\n".getBytes(StandardCharsets.US_ASCII));
        MaterialIdOfflineCopy.Plan plan = new MaterialIdOfflineCopy.Plan();
        plan.schema = "gtsr.material-copy/v1";
        plan.mode = "reviewed-offline-copy";
        plan.sourceGt = plan.targetGt = "5.09.54.277";
        plan.sourceGtsr = "synthetic-source";
        plan.targetGtsr = "synthetic-target";
        Path syntheticSourceArtifact = scratch.resolve("synthetic-source-artifact.bin");
        Path syntheticTargetArtifact = scratch.resolve("synthetic-target-artifact.bin");
        Files.write(
            syntheticSourceArtifact,
            "synthetic GTSR source identity fixture".getBytes(StandardCharsets.US_ASCII));
        Files.write(
            syntheticTargetArtifact,
            "synthetic GTSR target identity fixture".getBytes(StandardCharsets.US_ASCII));
        plan.sourceGtsrArtifact = syntheticSourceArtifact.toString();
        plan.sourceGtsrArtifactSha256 = MaterialIdOfflineCopy.hash(syntheticSourceArtifact);
        plan.targetGtsrArtifact = syntheticTargetArtifact.toString();
        plan.targetGtsrArtifactSha256 = MaterialIdOfflineCopy.hash(syntheticTargetArtifact);
        plan.gtArtifact = Paths
            .get(
                ((java.net.JarURLConnection) gregtech.api.enums.Materials.class
                    .getResource("/gregtech/api/enums/Materials.class")
                    .openConnection()).getJarFileURL()
                        .toURI())
            .toString();
        plan.gtArtifactSha256 = MaterialIdOfflineCopy.hash(Paths.get(plan.gtArtifact));
        plan.oldMapping = oldMap.toString();
        plan.oldMappingSha256 = MaterialIdOfflineCopy.hash(oldMap);
        plan.newMapping = newMap.toString();
        plan.newMappingSha256 = MaterialIdOfflineCopy.hash(newMap);
        plan.registryEvidence = registry.toString();
        plan.registryEvidenceSha256 = MaterialIdOfflineCopy.hash(registry);
        plan.occupancyEvidence = Paths.get("tools/material-ids/gt-277-native-slots.properties")
            .toAbsolutePath()
            .toString();
        plan.occupancyEvidenceSha256 = MaterialIdOfflineCopy.hash(Paths.get(plan.occupancyEvidence));
        MaterialIdOfflineCopy.Rule dust = rule("dust", 2, newId), cell = rule("cell", 30, newId);
        plan.rules = Arrays.asList(dust, cell);
        Files.write(
            planPath,
            new Gson().toJson(plan)
                .getBytes(StandardCharsets.UTF_8));
        NBTTagCompound player = inventory();
        Path playerFile = source.resolve("player.dat");
        Files.write(playerFile, MaterialIdOfflineCopy.encode(player, true));
        Path raw = source.resolve("saved-output.nbt");
        Files.write(raw, MaterialIdOfflineCopy.encode(player, false));
        createRegion(source.resolve("region/r.0.0.mca"), player);
        createRegion(source.resolve("DIM-1/region/r.0.0.mca"), player);
        Map<String, String> original = MaterialIdOfflineCopy.inventory(source);
        MaterialIdOfflineCopy.main(cli("dry-run", source, target, planPath, review));
        check(!Files.exists(target), "dry-run creates no output directory");
        check(
            MaterialIdOfflineCopy.inventory(source)
                .equals(original),
            "dry-run leaves source byte-identical");
        MaterialIdOfflineCopy.main(cli("apply", source, target, planPath, review));
        check(
            MaterialIdOfflineCopy.inventory(source)
                .equals(original),
            "apply leaves all source dimensions/config records byte-identical");
        NBTTagCompound migrated = MaterialIdOfflineCopy.decode(Files.readAllBytes(target.resolve("player.dat")), true);
        NBTTagList items = migrated.getTagList("Inventory", 10);
        check(
            (items.getCompoundTagAt(0)
                .getShort("Damage") & 65535) == 2000 + newId,
            "exact GT dust prefix remapped");
        check(
            (items.getCompoundTagAt(1)
                .getShort("Damage") & 65535) == 30000 + newId,
            "exact GT cell prefix remapped");
        check(
            items.getCompoundTagAt(2)
                .equals(
                    player.getTagList("Inventory", 10)
                        .getCompoundTagAt(2)),
            "unrelated item with identical Damage is unchanged");
        check(
            items.getCompoundTagAt(3)
                .equals(
                    player.getTagList("Inventory", 10)
                        .getCompoundTagAt(3)),
            "GT custom item >=32000 is unchanged");
        NBTTagCompound restored = (NBTTagCompound) migrated.copy();
        restored.getTagList("Inventory", 10)
            .getCompoundTagAt(0)
            .setShort("Damage", (short) 2705);
        restored.getTagList("Inventory", 10)
            .getCompoundTagAt(1)
            .setShort("Damage", (short) 30705);
        check(restored.equals(player), "Count, custom NBT, unrelated items and every non-Damage field are conserved");
        for (String dimension : Arrays.asList("region/r.0.0.mca", "DIM-1/region/r.0.0.mca")) {
            RegionFile region = new RegionFile(
                target.resolve(dimension)
                    .toFile());
            try (DataInputStream input = region.getChunkDataInputStream(7, 11)) {
                NBTTagCompound chunk = CompressedStreamTools
                    .func_152456_a(input, new NBTSizeTracker(MaterialIdOfflineCopy.MAX_NBT));
                check(
                    chunk.getCompoundTag("Level")
                        .getCompoundTag("FixtureInventory")
                        .equals(migrated),
                    "unloaded native region inventory transformed in " + dimension);
            } finally {
                region.close();
            }
        }
        rejected(
            () -> MaterialIdOfflineCopy.main(cli("apply", source, target, planPath, review)),
            "repeat apply cannot double-transform output");
        rejected(
            () -> MaterialIdOfflineCopy.main(
                cli("dry-run", target, scratch.resolve("double"), planPath, scratch.resolve("double-review.json"))),
            "already converted source marker rejects second migration");
        // Resume recomputes each output from the immutable original, never from transformed output.
        Path resumeTarget = scratch.resolve("resume-target"), resumeReview = scratch.resolve("resume-review.json");
        MaterialIdOfflineCopy.main(cli("dry-run", source, resumeTarget, planPath, resumeReview));
        Files.createDirectory(resumeTarget);
        @SuppressWarnings("unchecked")
        Map<String, Object> interrupted = new Gson()
            .fromJson(new String(Files.readAllBytes(resumeReview), StandardCharsets.UTF_8), Map.class);
        interrupted.put("status", "INCOMPLETE");
        MaterialIdOfflineCopy.publishJson(resumeTarget.resolve(MaterialIdOfflineCopy.MARKER), interrupted);
        Files.write(resumeTarget.resolve("player.dat"), Files.readAllBytes(target.resolve("player.dat")));
        MaterialIdOfflineCopy.main(cli("resume", source, resumeTarget, planPath, resumeReview));
        check(
            MaterialIdOfflineCopy.decode(Files.readAllBytes(resumeTarget.resolve("player.dat")), true)
                .equals(migrated),
            "interrupted replay is idempotent from original input");
        MaterialIdOfflineCopy.Context context = new MaterialIdOfflineCopy.Context(plan, numericRegistry());
        NBTTagCompound opaque = new NBTTagCompound();
        opaque.setByteArray("AECompressedItems", MaterialIdOfflineCopy.encode(player, true));
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(opaque, "ae-fixture", context, 0),
            "nested/compressed AE bytes fail closed");
        NBTTagCompound ae = new NBTTagCompound();
        ae.setString("id", "appliedenergistics2:Drive");
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(ae, "ae-drive", context, 0),
            "unverified AE container fails closed even without bytes");
        NBTTagCompound primitiveContainer = new NBTTagCompound();
        primitiveContainer.setString("id", "SomeMod:CustomChest");
        primitiveContainer.setInteger("storedItemId", 1000);
        primitiveContainer.setInteger("meta", 705);
        primitiveContainer.setInteger("count", 64);
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(primitiveContainer, "unknown-primitive-container", context, 0),
            "unknown primitive-only container cannot bypass adapter validation");
        NBTTagCompound gtDelegate = new NBTTagCompound();
        gtDelegate.setString("id", "GT_MetaTileEntity");
        gtDelegate.setInteger("mID", 14858);
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(gtDelegate, "unknown-gt-delegate", context, 0),
            "GT delegates require a machine-specific adapter");
        NBTTagCompound chest = new NBTTagCompound();
        chest.setString("id", "Chest");
        chest.setInteger("x", 4);
        chest.setInteger("y", 64);
        chest.setInteger("z", 7);
        NBTTagList chestItems = new NBTTagList();
        chestItems.appendTag(stack(1000, 2705, 42));
        chest.setTag("Items", chestItems);
        MaterialIdOfflineCopy.rewrite(chest, "native-chest", context, 0);
        check(
            (chest.getTagList("Items", 10)
                .getCompoundTagAt(0)
                .getShort("Damage") & 65535) == 2000 + newId,
            "known native carrier transforms its standard ItemStack list");
        chest.setInteger("storedItemId", 1000);
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(chest, "extended-native-chest", context, 0),
            "primitive native-carrier extension also needs review");
        NBTTagCompound unsupportedPrefix = stack(1000, 1705, 8);
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(unsupportedPrefix, "unknown-prefix", context, 0),
            "unreviewed GT prefix with target material fails closed");
        rejected(() -> MaterialIdOfflineCopy.decode(new byte[] { 10, 0 }, false), "truncated NBT fails closed");
        rejected(() -> MaterialIdOfflineCopy.rewrite(player, "depth", context, 65), "depth limit fails closed");
        rejected(
            () -> MaterialIdOfflineCopy.rejectOverlap(source, source.resolve("nested-output")),
            "source/output overlap rejected");
        Path unsupported = source.resolve("unverified.bin");
        Files.write(unsupported, new byte[] { 1, 2, 3 });
        rejected(
            () -> MaterialIdOfflineCopy.main(
                cli("dry-run", source, scratch.resolve("bad-output"), planPath, scratch.resolve("bad-review.json"))),
            "unknown file encoding blocks entire dry-run");
        check(!Files.exists(scratch.resolve("bad-review.json")), "failed dry-run cannot authorize apply");
        Files.delete(unsupported);
        byte[] regionBytes = Files.readAllBytes(source.resolve("region/r.0.0.mca"));
        byte[] invalid = Arrays.copyOf(regionBytes, regionBytes.length);
        int location = java.nio.ByteBuffer.wrap(invalid)
            .getInt((7 + 11 * 32) * 4);
        invalid[(location >>> 8) * 4096 + 4] = 99;
        Path invalidRegion = scratch.resolve("invalid.mca");
        Files.write(invalidRegion, invalid);
        rejected(
            () -> MaterialIdOfflineCopy
                .region(invalidRegion, scratch.resolve("must-not-exist.mca"), "invalid.mca", context, true),
            "unknown region compression rejected");
        check(!Files.exists(scratch.resolve("must-not-exist.mca")), "rejected region never writes output");
    }

    private static Map<Integer, String> numericRegistry() {
        Map<Integer, String> registry = new HashMap<>();
        registry.put(1000, "gregtech:gt.metaitem.01");
        registry.put(1001, "fixture:unrelated");
        return registry;
    }

    private static MaterialIdOfflineCopy.Rule rule(String prefix, int index, int newId) {
        MaterialIdOfflineCopy.Rule rule = new MaterialIdOfflineCopy.Rule();
        rule.key = "planned:native_acid_cut";
        rule.registryName = "gregtech:gt.metaitem.01";
        rule.prefix = prefix;
        rule.numericItemId = 1000;
        rule.prefixIndex = index;
        rule.oldId = 705;
        rule.newId = newId;
        return rule;
    }

    private static NBTTagCompound stack(int id, int damage, int count) {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setShort("id", (short) id);
        stack.setShort("Damage", (short) damage);
        stack.setByte("Count", (byte) count);
        NBTTagCompound custom = new NBTTagCompound();
        custom.setString("display", "synthetic material evidence");
        stack.setTag("tag", custom);
        return stack;
    }

    private static NBTTagCompound inventory() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList items = new NBTTagList();
        items.appendTag(stack(1000, 2705, 64));
        items.appendTag(stack(1000, 30705, 13));
        items.appendTag(stack(1001, 2705, 23));
        items.appendTag(stack(1000, 32705, 1));
        root.setTag("Inventory", items);
        return root;
    }

    private static void createRegion(Path path, NBTTagCompound inventory) throws Exception {
        Files.createDirectories(path.getParent());
        RegionFile region = new RegionFile(path.toFile());
        try (DataOutputStream output = region.getChunkDataOutputStream(7, 11)) {
            NBTTagCompound root = new NBTTagCompound(), level = new NBTTagCompound();
            level.setTag("FixtureInventory", inventory.copy());
            root.setTag("Level", level);
            CompressedStreamTools.write(root, output);
        } finally {
            region.close();
        }
    }

    private static String[] cli(String mode, Path source, Path target, Path plan, Path review) {
        return new String[] { mode, source.toString(), target.toString(), plan.toString(), review.toString() };
    }

    private interface Operation {

        void run() throws Exception;
    }

    private static void rejected(Operation operation, String message) throws Exception {
        try {
            operation.run();
        } catch (java.io.IOException | IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
