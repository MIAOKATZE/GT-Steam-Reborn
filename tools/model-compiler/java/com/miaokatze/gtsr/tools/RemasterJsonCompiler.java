package com.miaokatze.gtsr.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import com.miaokatze.gtsr.common.critical.CriticalJsonData;

/** Compiles each authored gzip independently and requires an exact UTF-8 byte roundtrip. */
public final class RemasterJsonCompiler {

    private RemasterJsonCompiler() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Expected source resources and generated resources directories");
        Path source = Paths.get(args[0]).resolve("assets/gtsr/remaster/prefabs");
        Path destination = Paths.get(args[1]).resolve("assets/gtsr/remaster/prefabs");
        if (destination.toAbsolutePath().normalize().startsWith(source.toAbsolutePath().normalize()))
            throw new IOException("Generated resources must be separate from authored resources");
        List<Path> files;
        try (Stream<Path> paths = Files.walk(source)) {
            files = paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json.gz"))
                .sorted()
                .collect(Collectors.toList());
        }
        if (files.isEmpty()) throw new IOException("No remaster prefabs: " + source);
        Set<Path> expected = new HashSet<>();
        long originalBytes = 0, jsonBytes = 0, compiledBytes = 0;
        for (Path file : files) {
            byte[] original;
            try (InputStream compressed = Files.newInputStream(file);
                InputStream input = new GZIPInputStream(compressed);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if ((long) bytes.size() + n > CriticalJsonData.MAX_BYTES)
                        throw new IOException("Remaster JSON size limit: " + file);
                    bytes.write(buffer, 0, n);
                }
                original = bytes.toByteArray();
            }
            byte[] encoded = CriticalJsonData.compile(new ByteArrayInputStream(original));
            byte[] decoded = CriticalJsonData.read(new ByteArrayInputStream(encoded)).getBytes(StandardCharsets.UTF_8);
            // This independent byte comparison includes field/row order, exact numbers and escapes.
            if (!Arrays.equals(original, decoded)) throw new IOException("Remaster JSON bytes changed: " + file);
            String relative = source.relativize(file).toString();
            Path output = destination.resolve(relative.substring(0, relative.length() - ".json.gz".length()) + ".gcjb");
            Files.createDirectories(output.getParent());
            Files.write(output, encoded);
            expected.add(output);
            originalBytes += Files.size(file);
            jsonBytes += original.length;
            compiledBytes += encoded.length;
        }
        // Only remove stale derived files inside this compiler's own generated output tree.
        try (Stream<Path> paths = Files.walk(destination)) {
            for (Path old : paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".gcjb"))
                .collect(Collectors.toList())) {
                if (!expected.contains(old)) Files.delete(old);
            }
        }
        System.out.println(
            "Verified remaster exact UTF-8 byte roundtrips: " + files.size() + "; gzip=" + originalBytes
                + "; json=" + jsonBytes + "; compiled=" + compiledBytes);
    }
}
