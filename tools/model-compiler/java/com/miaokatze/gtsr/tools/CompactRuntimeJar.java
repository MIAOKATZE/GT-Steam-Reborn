package com.miaokatze.gtsr.tools;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Build-only container rewrite; every file keeps its path and exact uncompressed contents. */
public final class CompactRuntimeJar {

    private CompactRuntimeJar() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected runtime jar and owned build directory");
        Path archive = Paths.get(args[0]).toRealPath();
        Path ownedBuild = Paths.get(args[1]).toRealPath();
        if (!archive.startsWith(ownedBuild) || !archive.getFileName().toString().endsWith(".jar")) {
            throw new IOException("Archive must be a generated jar within the owned build directory");
        }
        long before = Files.size(archive);
        Path temporary = Files.createTempFile(archive.getParent(), ".compact-", ".tmp");
        Map<String, byte[]> expected = new LinkedHashMap<>();
        int directories = 0, retained = 0;
        try {
            try (ZipFile source = new ZipFile(archive.toFile());
                JarOutputStream output = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.setLevel(Deflater.BEST_COMPRESSION);
                Enumeration<? extends ZipEntry> entries = source.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        directories++;
                        // CommonProxy.DiagAssembly probes this directory URL; META-INF remains conventional.
                        if (!entry.getName().equals("META-INF/")
                            && !entry.getName().equals("assets/gtsr/textures/blocks/")) continue;
                        retained++;
                    }
                    ZipEntry copy = new ZipEntry(entry.getName());
                    copy.setTime(entry.getTime());
                    copy.setMethod(entry.getMethod());
                    copy.setSize(entry.getSize());
                    copy.setCrc(entry.getCrc());
                    if (entry.getMethod() == ZipEntry.STORED) copy.setCompressedSize(entry.getSize());
                    if (entry.getExtra() != null) copy.setExtra(entry.getExtra());
                    if (entry.getComment() != null) copy.setComment(entry.getComment());
                    output.putNextEntry(copy);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (InputStream input = source.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                            digest.update(buffer, 0, count);
                        }
                    }
                    output.closeEntry();
                    if (!entry.isDirectory() && expected.put(entry.getName(), digest.digest()) != null) {
                        throw new IOException("Duplicate archive file " + entry.getName());
                    }
                }
            }
            try (ZipFile verified = new ZipFile(temporary.toFile())) {
                int files = 0;
                Enumeration<? extends ZipEntry> entries = verified.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    files++;
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (InputStream input = verified.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                    }
                    if (!Arrays.equals(expected.get(entry.getName()), digest.digest())) {
                        throw new IOException("Archive rewrite changed file " + entry.getName());
                    }
                }
                if (files != expected.size()) throw new IOException("Archive rewrite changed file count");
            }
            try {
                Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println(
                "Verified runtime archive " + archive.getFileName() + ": files=" + expected.size() + " dirs="
                    + directories + "->" + retained + " bytes=" + before + "->" + Files.size(archive));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
