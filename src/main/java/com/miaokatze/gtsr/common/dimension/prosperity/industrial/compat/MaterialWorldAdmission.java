// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial.compat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdPreflight;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdRegistration;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdSchema;
import com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher;

/**
 * Read-only pre-load protection for legacy numeric materials. Never constructs a SaveHandler.
 * Receipts belong to config, not level.dat. Keep this layer separate from native fluid registration;
 * remove with the legacy numeric-cell/dust migration after their published compatibility lifetime.
 */
public final class MaterialWorldAdmission {

    private static Path pendingNewWorld;

    private MaterialWorldAdmission() {}

    public static synchronized void beforeLoad(MinecraftServer server) {
        pendingNewWorld = null;
        Path world = server.getFile((server.isDedicatedServer() ? "" : "saves/") + server.getFolderName())
            .toPath()
            .toAbsolutePath()
            .normalize();
        try {
            inspect(world, MaterialIdRegistration.startupIdentity(), MaterialIdRegistration.configDirectory());
            pendingNewWorld = world;
        } catch (IOException failure) {
            throw new IllegalStateException(
                "GTSR refused world admission before load: " + world
                    + ". "
                    + failure.getMessage()
                    + " Preserve the original cfg/world; restore its matching mapping or review an offline COPY.",
                failure);
        }
    }

    /** Returns true only for a new target; checks only the selected directory and metadata. */
    public static boolean inspect(Path world, MaterialIdSchema.Identity identity, Path config) throws IOException {
        rejectLinks(world);
        if (!Files.exists(world)) return true;
        if (!Files.isDirectory(world)) throw new IOException("World target is not a directory");
        try (java.util.stream.Stream<Path> entries = Files.list(world)) {
            if (!entries.findAny()
                .isPresent()) return true;
        }
        NBTTagCompound root = readLevel(world);
        NBTTagCompound data = root.getCompoundTag("Data");
        if (!data.hasKey("RandomSeed", 4) || !data.hasKey("LevelName", 8))
            throw new IOException("Missing world identity metadata");
        if (validReceipt(world, root, identity, config)) return false;
        if (validCopyApproval(world, identity.fingerprint, config)) return false;
        if (isNativeCreationShell(world, root)) return true;
        if (!identity.isGeneratedUnbound() && identity.conflictedFluidIds.isEmpty()) return false;
        throw new IOException(
            identity.isGeneratedUnbound()
                ? "Existing world has no matching mapping provenance; newly generated cfg is not old-world evidence"
                : "Legacy fluid-cell IDs conflict: " + identity.conflictedFluidIds
                    + "; a completed, provenance-checked offline copy is required");
    }

    /** Called once after admitted native startup; writes config only, never forces a world save. */
    public static synchronized void afterStarted() {
        Path world = pendingNewWorld;
        pendingNewWorld = null;
        if (world == null) return;
        try {
            // The first native save may happen later. Read the newly-created world's in-memory
            // identity without forcing a save; the next admission also verifies native FML metadata.
            net.minecraft.world.storage.WorldInfo info = MinecraftServer.getServer()
                .worldServerForDimension(0)
                .getWorldInfo();
            NBTTagCompound root = new NBTTagCompound(), data = new NBTTagCompound();
            data.setLong("RandomSeed", info.getSeed());
            data.setString("LevelName", info.getWorldName());
            root.setTag("Data", data);
            Properties receipt = receipt(world, root, MaterialIdRegistration.startupIdentity().fingerprint);
            Path output = receiptPath(world, MaterialIdRegistration.configDirectory());
            Files.createDirectories(output.getParent());
            if (Files.isSymbolicLink(output)) throw new IOException("Receipt symlink rejected");
            AtomicFilePublisher.publishAtomic(output, encode(receipt));
        } catch (IOException failure) {
            throw new IllegalStateException(
                "World mapping receipt could not be preserved; original world untouched",
                failure);
        }
    }

    public static synchronized void clear() {
        pendingNewWorld = null;
    }

    private static boolean validReceipt(Path world, NBTTagCompound root, MaterialIdSchema.Identity identity,
        Path config) throws IOException {
        Path file = receiptPath(world, config);
        if (!Files.exists(file)) return false;
        if (Files.isSymbolicLink(file)) throw new IOException("Receipt symlink rejected");
        Properties saved = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            saved.load(input);
        }
        if (!saved.equals(receipt(world, root, identity.fingerprint))) throw new IOException(
            "Existing world binding differs; refusing fallback to another legacy mapping or backup");
        // Before the first native save, identical mapping provenance can still use the original
        // safe legacy/copy admission. A mismatched binding must never fall through to that path.
        return hasCurrentGtsr(root);
    }

    /**
     * Minecraft.launchIntegratedServer writes a new WorldInfo before AboutToStart. Recognize only
     * that native pre-generation shell, never an initialized or partly missing existing world.
     * SaveHandler(false) creates session.lock and an empty data directory at that earlier stage.
     */
    private static boolean isNativeCreationShell(Path world, NBTTagCompound root) throws IOException {
        NBTTagCompound data = root.getCompoundTag("Data");
        if (!hasCurrentGtsr(root) || !data.hasKey("initialized", 1)
            || data.getBoolean("initialized")
            || !data.hasKey("Time", 4)
            || data.getLong("Time") != 0
            || !data.hasKey("DayTime", 4)
            || data.getLong("DayTime") != 0
            || !data.hasKey("SizeOnDisk", 4)
            || data.getLong("SizeOnDisk") != 0
            || data.hasKey("Player")) return false;
        if (!Files.isRegularFile(world.resolve("level.dat")) || !Files.isRegularFile(world.resolve("session.lock"))
            || Files.size(world.resolve("session.lock")) != 8) return false;
        try (java.util.stream.Stream<Path> entries = Files.list(world)) {
            for (Path entry : entries.collect(java.util.stream.Collectors.toList())) {
                if (Files.isSymbolicLink(entry)) return false;
                String name = entry.getFileName()
                    .toString();
                if ("level.dat".equals(name) || "session.lock".equals(name)) continue;
                if (!"data".equals(name) || !Files.isDirectory(entry)) return false;
                try (java.util.stream.Stream<Path> children = Files.list(entry)) {
                    if (children.findAny()
                        .isPresent()) return false;
                }
            }
        }
        return true;
    }

    private static Properties receipt(Path world, NBTTagCompound root, String fingerprint) {
        Properties value = new Properties();
        value.setProperty("schema", "1");
        value.setProperty(
            "world",
            world.toAbsolutePath()
                .normalize()
                .toString());
        value.setProperty(
            "seed",
            Long.toString(
                root.getCompoundTag("Data")
                    .getLong("RandomSeed")));
        value.setProperty(
            "name",
            root.getCompoundTag("Data")
                .getString("LevelName"));
        value.setProperty("mapping", fingerprint);
        return value;
    }

    public static Path receiptPath(Path world, Path config) {
        String key = MaterialIdPreflight.sha256(
            world.toAbsolutePath()
                .normalize()
                .toString()
                .getBytes(StandardCharsets.UTF_8));
        return config.resolve("material-world-bindings")
            .resolve(key + ".cfg");
    }

    public static Path copyApprovalPath(Path world, Path config) {
        String key = MaterialIdPreflight.sha256(
            world.toAbsolutePath()
                .normalize()
                .toString()
                .getBytes(StandardCharsets.UTF_8));
        return config.resolve("material-copy-approvals")
            .resolve(key + ".json");
    }

    /**
     * Explicit operator-reviewed copy admission, separate from a native reopen success claim.
     * The offline tool has already checked every output hash; startup only checks the reviewed
     * report and selected level metadata. It never recursively scans a running world.
     */
    private static boolean validCopyApproval(Path world, String fingerprint, Path config) throws IOException {
        Path approval = copyApprovalPath(world, config), marker = world.resolve(".gtsr-material-copy.json"),
            level = world.resolve("level.dat");
        if (!Files.exists(approval)) return false;
        if (Files.isSymbolicLink(approval) || Files.isSymbolicLink(marker) || Files.isSymbolicLink(level))
            throw new IOException("Unsafe offline-copy approval path");
        try {
            com.google.gson.JsonObject accepted = json(approval), copied = json(marker);
            if (accepted.has("admissionApproval")) accepted = accepted.getAsJsonObject("admissionApproval");
            if (accepted.get("schema")
                .getAsInt() != 1
                || !world.toAbsolutePath()
                    .normalize()
                    .toString()
                    .equals(
                        accepted.get("world")
                            .getAsString())
                || !fingerprint.equals(
                    accepted.get("mapping")
                        .getAsString())
                || !MaterialIdPreflight.sha256(Files.readAllBytes(marker))
                    .equals(
                        accepted.get("markerSha256")
                            .getAsString())
                || !MaterialIdPreflight.sha256(Files.readAllBytes(level))
                    .equals(
                        accepted.get("levelSha256")
                            .getAsString())
                || !fingerprint.equals(
                    copied.get("targetFingerprint")
                        .getAsString())
                || !"COPY_CONVERTED_REQUIRES_NATIVE_REOPEN".equals(
                    copied.get("status")
                        .getAsString())
                || !copied.get("adaptersVerified")
                    .getAsBoolean())
                throw new IOException("Offline copy approval does not match this completed copy and mapping");
            return true;
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed offline copy approval; original world preserved", malformed);
        }
    }

    private static com.google.gson.JsonObject json(Path path) throws IOException {
        try (java.io.Reader input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new com.google.gson.JsonParser().parse(input)
                .getAsJsonObject();
        }
    }

    private static byte[] encode(Properties properties) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        properties.store(bytes, "GTSR material world binding; config-side only");
        return bytes.toByteArray();
    }

    private static boolean hasCurrentGtsr(NBTTagCompound root) {
        NBTTagList mods = root.getCompoundTag("FML")
            .getTagList("ModList", 10);
        for (int index = 0; index < mods.tagCount(); index++) {
            NBTTagCompound mod = mods.getCompoundTagAt(index);
            if (!"gtsr".equals(mod.getString("ModId"))) continue;
            String version = mod.getString("ModVersion")
                .replaceFirst("^v", "");
            java.util.regex.Matcher match = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:$|[-+]).*")
                .matcher(version);
            if (!match.matches()) return false;
            try {
                int major = Integer.parseInt(match.group(1)), minor = Integer.parseInt(match.group(2)),
                    patch = Integer.parseInt(match.group(3));
                return major > 2 || major == 2 && (minor > 1 || minor == 1 && patch >= 86);
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        return false;
    }

    private static NBTTagCompound readLevel(Path world) throws IOException {
        Path primary = world.resolve("level.dat"), backup = world.resolve("level.dat_old");
        Path file = Files.isRegularFile(primary) ? primary : backup;
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file))
            throw new IOException("Missing/unsafe level metadata");
        try (InputStream input = Files.newInputStream(file)) {
            return CompressedStreamTools.readCompressed(input);
        }
    }

    private static void rejectLinks(Path path) throws IOException {
        for (Path part = path.toAbsolutePath(); part != null; part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IOException("Symbolic-link world path rejected");
    }
}
