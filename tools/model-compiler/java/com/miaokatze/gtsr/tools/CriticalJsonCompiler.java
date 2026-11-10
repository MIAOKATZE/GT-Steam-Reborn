package com.miaokatze.gtsr.tools;

import com.miaokatze.gtsr.common.critical.CriticalJsonData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Build-time JSON compiler with an independent, exact lexical/order roundtrip comparison. */
public final class CriticalJsonCompiler {
    private CriticalJsonCompiler() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Expected source resources and generated resources directories");
        Path source = Paths.get(args[0]).resolve("assets/gtsr/critical/models");
        Path destination = Paths.get(args[1]).resolve("assets/gtsr/critical/models");
        List<Path> files;
        try (Stream<Path> paths = Files.list(source)) {
            files =
                    paths.filter(p -> p.toString().endsWith(".geometry.json"))
                            .sorted()
                            .collect(Collectors.toList());
        }
        if (files.isEmpty()) throw new IOException("No critical models: " + source);
        Files.createDirectories(destination);
        Set<String> expected = new HashSet<>();
        long originalBytes = 0, compiledBytes = 0;
        for (Path file : files) {
            byte[] encoded;
            try (InputStream in = Files.newInputStream(file)) {
                encoded = CriticalJsonData.compile(in);
            }
            String decoded = CriticalJsonData.read(new ByteArrayInputStream(encoded));
            byte[] original = Files.readAllBytes(file);
            if (!lexical(new String(original, StandardCharsets.UTF_8)).equals(decoded))
                throw new IOException("JSON tokens/order changed: " + file);
            String name = file.getFileName().toString().replace(".json", ".gcjb");
            Files.write(destination.resolve(name), encoded);
            expected.add(name);
            originalBytes += original.length;
            compiledBytes += encoded.length;
        }
        // Outputs are generated resources only; a removed source must not leave a stale runtime
        // model.
        try (Stream<Path> paths = Files.list(destination)) {
            for (Path old : paths.collect(Collectors.toList())) {
                String name = old.getFileName().toString();
                if (name.endsWith(".geometry.gcjb") && !expected.contains(name)) Files.delete(old);
            }
        }
        System.out.println(
                "Verified critical model lexical/order roundtrips: "
                        + files.size()
                        + "; source="
                        + originalBytes
                        + "; compiled="
                        + compiledBytes);
    }

    /** Independent comparison: removes only JSON whitespace outside quoted tokens. */
    private static String lexical(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean quoted = false, escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                result.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
                result.append(c);
            } else if (c != ' ' && c != '\r' && c != '\n' && c != '\t') result.append(c);
        }
        return result.toString();
    }
}
