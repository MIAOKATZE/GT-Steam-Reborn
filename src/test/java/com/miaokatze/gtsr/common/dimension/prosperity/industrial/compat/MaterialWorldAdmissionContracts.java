// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial.compat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdPreflight;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdSchema;

/** Synthetic metadata only; never opens a world, SaveHandler, client or dedicated server. */
public final class MaterialWorldAdmissionContracts {

    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gtsr-material-admission-");
        try {
            Path config = root.resolve("config"), world = root.resolve("world");
            MaterialIdSchema.Identity generated = identity(null, Collections.<Integer, String>emptyMap());
            check(MaterialWorldAdmission.inspect(world, generated, config), "new absent target auto-admitted");
            Files.createDirectory(world);
            check(MaterialWorldAdmission.inspect(world, generated, config), "empty new target auto-admitted");
            NBTTagCompound nbt = metadata("2.1.85");
            Path level = world.resolve("level.dat");
            try (java.io.OutputStream out = Files.newOutputStream(level)) {
                CompressedStreamTools.writeCompressed(nbt, out);
            }
            byte[] before = Files.readAllBytes(level);
            refused(
                () -> MaterialWorldAdmission.inspect(world, generated, config),
                "generated file cannot prove an old world's mapping");
            byte[] old = "dust=900\nfluid=705\n".getBytes(StandardCharsets.ISO_8859_1);
            MaterialIdSchema.Identity imported = identity(old, Collections.<Integer, String>emptyMap());
            check(
                !MaterialWorldAdmission.inspect(world, imported, config),
                "complete safe legacy mapping preserves admission");
            MaterialIdSchema.Identity collision = identity(old, Collections.singletonMap(705, "DistilledWater"));
            refused(
                () -> MaterialWorldAdmission.inspect(world, collision, config),
                "retired acid collision protects old world before load");
            check(Arrays.equals(before, Files.readAllBytes(level)), "all decisions preserve level bytes");
            check(!Files.exists(world.resolve("session.lock")), "preflight never constructs SaveHandler");
            copyApprovalContracts(world, config, collision, level);
            bindingContracts(world, config, generated, level);
            creationShellContracts(world, config, generated, level);
            Files.delete(level);
            Files.createDirectory(world.resolve("region"));
            refused(
                () -> MaterialWorldAdmission.inspect(world, generated, config),
                "missing metadata plus existing data cannot masquerade as new world");
            String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            java.util.Set<String> tokens = MaterialMappingHandshake.tokens(hash);
            check(
                tokens.size() == 4 && tokens.stream()
                    .allMatch(token -> token.length() == 20),
                "complete SHA fits four native channel names");
            HashSet<String> full = new HashSet<>(tokens);
            full.add("FML|HS");
            full.add("unrelated");
            check(MaterialMappingHandshake.matches(full, hash), "unrelated channels do not affect material proof");
            full.remove(
                tokens.iterator()
                    .next());
            check(!MaterialMappingHandshake.matches(full, hash), "incomplete hash refused");
            check(
                !MaterialMappingHandshake.matches(tokens, hash.substring(0, 63) + "0"),
                "last fingerprint bit matters");
            check(
                !MaterialMappingHandshake.matches(Collections.singleton("FML|HS"), hash),
                "legacy peer without proof refused");
            System.out
                .println("Material admission contracts: " + checks + " checks passed (synthetic metadata, no game)");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path file : paths.sorted(java.util.Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toList())) Files.delete(file);
            }
        }
    }

    private static void copyApprovalContracts(Path world, Path config, MaterialIdSchema.Identity identity, Path level)
        throws Exception {
        com.google.gson.JsonObject marker = new com.google.gson.JsonObject();
        marker.addProperty("status", "COPY_CONVERTED_REQUIRES_NATIVE_REOPEN");
        marker.addProperty("worldReady", false);
        marker.addProperty("targetFingerprint", identity.fingerprint);
        marker.addProperty("adaptersVerified", true);
        Path markerPath = world.resolve(".gtsr-material-copy.json");
        byte[] bytes = marker.toString()
            .getBytes(StandardCharsets.UTF_8);
        Files.write(markerPath, bytes);
        refused(
            () -> MaterialWorldAdmission.inspect(world, identity, config),
            "copy marker alone cannot admit an original world");
        com.google.gson.JsonObject approval = new com.google.gson.JsonObject();
        approval.addProperty("schema", 1);
        approval.addProperty(
            "world",
            world.toAbsolutePath()
                .normalize()
                .toString());
        approval.addProperty("mapping", identity.fingerprint);
        approval.addProperty("markerSha256", MaterialIdPreflight.sha256(bytes));
        approval.addProperty("levelSha256", MaterialIdPreflight.sha256(Files.readAllBytes(level)));
        Path accepted = MaterialWorldAdmission.copyApprovalPath(world, config);
        Files.createDirectories(accepted.getParent());
        Files.write(
            accepted,
            approval.toString()
                .getBytes(StandardCharsets.UTF_8));
        check(
            !MaterialWorldAdmission.inspect(world, identity, config),
            "reviewed completed copy may enter first native verification without worldReady claim");
        marker.addProperty("status", "INCOMPLETE");
        Files.write(
            markerPath,
            marker.toString()
                .getBytes(StandardCharsets.UTF_8));
        refused(
            () -> MaterialWorldAdmission.inspect(world, identity, config),
            "modified or incomplete copy receipt refused");
        Files.delete(accepted);
        Files.delete(markerPath);
    }

    private static void bindingContracts(Path world, Path config, MaterialIdSchema.Identity identity, Path level)
        throws Exception {
        java.util.Properties receipt = new java.util.Properties();
        receipt.setProperty("schema", "1");
        receipt.setProperty(
            "world",
            world.toAbsolutePath()
                .normalize()
                .toString());
        receipt.setProperty("seed", "123");
        receipt.setProperty("name", "fixture");
        receipt.setProperty("mapping", identity.fingerprint);
        Path file = MaterialWorldAdmission.receiptPath(world, config);
        Files.createDirectories(file.getParent());
        try (java.io.OutputStream out = Files.newOutputStream(file)) {
            receipt.store(out, "synthetic binding");
        }
        refused(
            () -> MaterialWorldAdmission.inspect(world, identity, config),
            "binding cannot reinterpret a pre-v2 world backup");
        try (java.io.OutputStream out = Files.newOutputStream(level)) {
            CompressedStreamTools.writeCompressed(metadata("v2.1.86"), out);
        }
        check(
            !MaterialWorldAdmission.inspect(world, identity, config),
            "new native world binding survives restart while origin remains unbound");
        receipt.setProperty("mapping", "wrong");
        try (java.io.OutputStream out = Files.newOutputStream(file)) {
            receipt.store(out, "wrong mapping");
        }
        refused(() -> MaterialWorldAdmission.inspect(world, identity, config), "binding cannot bypass mapping change");
        MaterialIdSchema.Identity anotherCompleteLegacy = identity(
            "dust=901\nfluid=706\n".getBytes(StandardCharsets.ISO_8859_1),
            Collections.<Integer, String>emptyMap());
        refused(
            () -> MaterialWorldAdmission.inspect(world, anotherCompleteLegacy, config),
            "a complete alternate legacy cfg cannot bypass an existing mismatched world binding");
        MaterialIdSchema.Identity originalLegacy = identity(
            "dust=900\nfluid=705\n".getBytes(StandardCharsets.ISO_8859_1),
            Collections.<Integer, String>emptyMap());
        receipt.setProperty("mapping", originalLegacy.fingerprint);
        try (java.io.OutputStream out = Files.newOutputStream(file)) {
            receipt.store(out, "matching original legacy");
        }
        try (java.io.OutputStream out = Files.newOutputStream(level)) {
            CompressedStreamTools.writeCompressed(metadata("2.1.85"), out);
        }
        check(
            !MaterialWorldAdmission.inspect(world, originalLegacy, config),
            "matching complete original mapping may retry after a crash before first native v2 save");
        Files.delete(file);
    }

    private static void creationShellContracts(Path world, Path config, MaterialIdSchema.Identity identity, Path level)
        throws Exception {
        net.minecraft.world.storage.WorldInfo nativeInfo = new net.minecraft.world.storage.WorldInfo(
            new net.minecraft.world.WorldSettings(
                123L,
                net.minecraft.world.WorldSettings.GameType.SURVIVAL,
                true,
                false,
                net.minecraft.world.WorldType.DEFAULT),
            "fixture");
        NBTTagCompound shell = metadata("v2.1.86");
        shell.setTag("Data", nativeInfo.getNBTTagCompound());
        try (java.io.OutputStream out = Files.newOutputStream(level)) {
            CompressedStreamTools.writeCompressed(shell, out);
        }
        Files.write(world.resolve("session.lock"), new byte[8]);
        Files.createDirectory(world.resolve("data"));
        check(
            MaterialWorldAdmission.inspect(world, identity, config),
            "native pre-written integrated-server creation shell auto-admits");
        Files.write(world.resolve("data/old-materials.dat"), new byte[] { 1 });
        refused(
            () -> MaterialWorldAdmission.inspect(world, identity, config),
            "nonempty data directory cannot masquerade as fresh creation");
        Files.delete(world.resolve("data/old-materials.dat"));
        shell.getCompoundTag("Data")
            .setTag("Player", new NBTTagCompound());
        try (java.io.OutputStream out = Files.newOutputStream(level)) {
            CompressedStreamTools.writeCompressed(shell, out);
        }
        refused(
            () -> MaterialWorldAdmission.inspect(world, identity, config),
            "embedded player prevents creation-shell admission");
        shell.getCompoundTag("Data")
            .removeTag("Player");
        shell.getCompoundTag("Data")
            .setBoolean("initialized", true);
        try (java.io.OutputStream out = Files.newOutputStream(level)) {
            CompressedStreamTools.writeCompressed(shell, out);
        }
        refused(
            () -> MaterialWorldAdmission.inspect(world, identity, config),
            "initialized tick-zero world is still an existing world");
        Files.delete(world.resolve("session.lock"));
        Files.delete(world.resolve("data"));
    }

    private static MaterialIdSchema.Identity identity(byte[] old, java.util.Map<Integer, String> slots) {
        MaterialIdSchema.Result value = MaterialIdSchema.inspect(
            null,
            old,
            Collections.singletonList(new MaterialIdPreflight.Definition("dust", "dust_name", false, false)),
            Collections.singletonList(new MaterialIdPreflight.Definition("fluid", "acid", false, false)),
            slots,
            Collections.<String, String>emptyMap());
        value.requireValid("admission fixture");
        return value.identity;
    }

    private static NBTTagCompound metadata(String version) {
        NBTTagCompound root = new NBTTagCompound(), data = new NBTTagCompound(), fml = new NBTTagCompound(),
            mod = new NBTTagCompound();
        data.setLong("RandomSeed", 123L);
        data.setString("LevelName", "fixture");
        root.setTag("Data", data);
        mod.setString("ModId", "gtsr");
        mod.setString("ModVersion", version);
        NBTTagList mods = new NBTTagList();
        mods.appendTag(mod);
        fml.setTag("ModList", mods);
        root.setTag("FML", fml);
        return root;
    }

    private interface Checked {

        void run() throws Exception;
    }

    private static void refused(Checked action, String label) throws Exception {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (java.io.IOException expected) {
            checks++;
        }
    }

    private static void check(boolean okay, String label) {
        if (!okay) throw new AssertionError(label);
        checks++;
    }
}
