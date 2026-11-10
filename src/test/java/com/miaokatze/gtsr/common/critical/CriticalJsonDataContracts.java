package com.miaokatze.gtsr.common.critical;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import com.google.gson.JsonParser;

public final class CriticalJsonDataContracts {

    private static int checks;

    private interface Action {

        void run() throws Exception;
    }

    private static byte[] compile(String s) throws IOException {
        return CriticalJsonData.compile(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String read(byte[] b) throws IOException {
        return CriticalJsonData.read(new ByteArrayInputStream(b));
    }

    private static void reject(String name, Action action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            checks++;
            System.out.println("PASS reject " + name + ": " + expected.getMessage());
            return;
        }
        throw new AssertionError("Accepted " + name);
    }

    private static void same(String s) throws Exception {
        if (!s.equals(read(compile(s)))) throw new AssertionError("Lossy fixture: " + s);
        checks++;
    }

    private static byte[] payload(byte[] data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        CRC32 crc = new CRC32();
        crc.update(data);
        out.writeInt(0x47434a42);
        out.writeByte(1);
        out.writeInt(data.length);
        out.writeInt(1024);
        out.writeInt((int) crc.getValue());
        out.write(new byte[32]);
        out.write(data);
        return bytes.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Expected source resources and generated resources directories");
        same(
            "{\"unknown\":{\"duplicate\":1,\"duplicate\":2},\"numbers\":[-0,0,1.0,1e0,1E+09,-1.234567890123456789,9007199254740993,2147483648],\"escapes\":[\"\\u4e2d\\u6587\",\"中文\",\"\\/\\\\\\\"\\b\\f\\n"
                + "\\r"
                + "\\t\",\"\\uD800\"]}");
        same("[null,false,true,[],{},\"\"]");
        StringBuilder rows = new StringBuilder("[");
        for (int i = 0; i < 32; i++) {
            if (i > 0) rows.append(',');
            rows.append("[")
                .append(i % 7 - 3)
                .append(",\"stable\",null,true,false,1.0]");
        }
        rows.append(']');
        same(rows.toString());
        same("[-1073741824,1073741823,1073741824,-1073741825]");
        byte[] valid = compile("{\"x\":[1,2,3]}");
        reject("truncated header", () -> read(new byte[3]));
        byte[] version = valid.clone();
        version[4] = 127;
        reject("unknown version", () -> read(version));
        byte[] magic = valid.clone();
        magic[0] = 0;
        reject("wrong magic", () -> read(magic));
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        reject("truncated payload", () -> read(truncated));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        reject("trailing payload", () -> read(trailing));
        byte[] checksum = valid.clone();
        checksum[checksum.length - 1] ^= 1;
        reject("bad CRC", () -> read(checksum));
        byte[] negative = valid.clone();
        Arrays.fill(negative, 5, 9, (byte) 255);
        reject("negative length", () -> read(negative));
        byte[] enormous = valid.clone();
        enormous[5] = 127;
        reject("excessive payload", () -> read(enormous));
        byte[] expanded = valid.clone();
        expanded[9] = 127;
        reject("excessive expansion", () -> read(expanded));
        reject("invalid tag", () -> read(payload(new byte[] { 0, 127 })));
        reject("dictionary reference", () -> read(payload(new byte[] { 0, 5, 0 })));
        reject(
            "varint overflow",
            () -> read(payload(new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255, 127 })));
        reject("noncanonical varint", () -> read(payload(new byte[] { (byte) 128, 0 })));
        reject("container size", () -> read(payload(new byte[] { 0, 6, 127 })));
        reject("row dimensions", () -> read(payload(new byte[] { 0, 8, 127, 127 })));
        reject("object key type", () -> read(payload(new byte[] { 0, 7, 1, 0, 0 })));
        reject("dictionary token type", () -> read(payload(new byte[] { 1, 3, 34, 120, 34, 4, 0 })));
        byte[] shortExpansion = valid.clone();
        Arrays.fill(shortExpansion, 9, 13, (byte) 0);
        shortExpansion[12] = 1;
        reject("declared expansion bound", () -> read(shortExpansion));
        reject("nonASCII unicode escape", () -> compile("\"\\uＦＦＦＦ\""));
        reject(
            "invalid UTF8",
            () -> CriticalJsonData.compile(new ByteArrayInputStream(new byte[] { (byte) 192, (byte) 128 })));
        for (String invalid : new String[] { "[1,]", "{\"a\":1,}", "[01]", "[+1]", "[.1]", "[1.]", "[NaN]", "[true]x",
            "{1:2}", "\"\\q\"", "\"\\uXYZ0\"", "\"unclosed" })
            reject("invalid JSON " + invalid, () -> compile(invalid));
        StringBuilder depth = new StringBuilder();
        for (int i = 0; i < 130; i++) depth.append('[');
        depth.append('0');
        for (int i = 0; i < 130; i++) depth.append(']');
        reject("source depth", () -> compile(depth.toString()));
        byte[] deep = new byte[264];
        deep[0] = 0;
        for (int i = 0; i < 131; i++) {
            deep[1 + i * 2] = 6;
            deep[2 + i * 2] = 1;
        }
        deep[263] = 0;
        reject("compiled depth", () -> read(payload(deep)));
        byte[] deepRows = new byte[258];
        deepRows[0] = 0;
        for (int i = 0; i < 127; i++) {
            deepRows[1 + i * 2] = 6;
            deepRows[2 + i * 2] = 1;
        }
        deepRows[255] = 8;
        deepRows[256] = 16;
        deepRows[257] = 1;
        reject("row table depth", () -> read(payload(deepRows)));
        readerContracts(Paths.get(args[0]), Paths.get(args[1]));
        System.out.println("Contract checks: " + checks);
    }

    /**
     * Reload only the real entry class; every critical resource must come from this fixture URL.
     */
    private static final class ResourceLoader extends URLClassLoader {

        private static final String GEOMETRY = "com.miaokatze.gtsr.common.critical.CriticalGeometry";

        ResourceLoader(Path root) throws Exception {
            super(
                new URL[] { root.toUri()
                    .toURL() },
                CriticalJsonDataContracts.class.getClassLoader());
        }

        @Override
        public URL getResource(String name) {
            return name.startsWith("assets/gtsr/critical/") ? findResource(name) : super.getResource(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(GEOMETRY)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    String resource = name.replace('.', '/') + ".class";
                    try (InputStream input = getParent().getResourceAsStream(resource)) {
                        if (input == null) throw new ClassNotFoundException(name);
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
                        byte[] code = bytes.toByteArray();
                        found = defineClass(name, code, 0, code.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) resolveClass(found);
                return found;
            }
        }

        Object readJson(String relative) throws Exception {
            Class<?> geometry = loadClass(GEOMETRY);
            return geometry.getMethod("readJson", String.class)
                .invoke(null, relative);
        }
    }

    private static void readerRejected(ResourceLoader loader, String name, String relative, String cause)
        throws Exception {
        try {
            loader.readJson(relative);
        } catch (InvocationTargetException expected) {
            if (!expected.getCause()
                .getClass()
                .getName()
                .equals(cause)) throw new AssertionError(name, expected);
            checks++;
            return;
        }
        throw new AssertionError("Reader accepted " + name);
    }

    private static void readerContracts(Path sourceRoot, Path compiledRoot) throws Exception {
        Path source = sourceRoot.resolve("assets/gtsr/critical/models");
        int count = 0;
        try (ResourceLoader loader = new ResourceLoader(compiledRoot); Stream<Path> paths = Files.list(source)) {
            for (Path file : paths.sorted()
                .toArray(Path[]::new)) {
                if (!file.toString()
                    .endsWith(".geometry.json")) continue;
                String relative = "models/" + file.getFileName();
                if (loader.getResource("assets/gtsr/critical/" + relative) != null)
                    throw new AssertionError("JSON leaked into binary-only fixture");
                String expected = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                    .toString();
                if (!expected.equals(
                    loader.readJson(relative)
                        .toString()))
                    throw new AssertionError("Entry point changed full JSON " + relative);
                checks++;
                count++;
            }
        }
        if (count < 51) throw new AssertionError("Expected all 51 released models, got " + count);
        Path fixture = Files.createTempDirectory("gtsr-critical-json-contract-");
        Path models = fixture.resolve("assets/gtsr/critical/models");
        Files.createDirectories(models);
        Path json = models.resolve("contract.geometry.json"), binary = models.resolve("contract.geometry.gcjb");
        try (ResourceLoader loader = new ResourceLoader(fixture)) {
            Files.write(json, "{\"selected\":\"json\"}".getBytes(StandardCharsets.UTF_8));
            Files.write(binary, new byte[] { 1, 2, 3 });
            if (!"{\"selected\":\"json\"}".equals(
                loader.readJson("models/contract.geometry.json")
                    .toString()))
                throw new AssertionError("Original JSON did not win");
            checks++;
            Files.write(json, "{\"broken\":".getBytes(StandardCharsets.UTF_8));
            Files.write(binary, compile("{\"selected\":\"binary\"}"));
            readerRejected(
                loader,
                "bad JSON must not fall back",
                "models/contract.geometry.json",
                "com.google.gson.JsonSyntaxException");
            Files.delete(json);
            if (!"{\"selected\":\"binary\"}".equals(
                loader.readJson("models/contract.geometry.json")
                    .toString()))
                throw new AssertionError("Missing JSON did not fall back");
            checks++;
            Files.write(binary, new byte[] { 1, 2, 3 });
            readerRejected(loader, "bad binary", "models/contract.geometry.json", "java.lang.IllegalStateException");
            Path other = fixture.resolve("assets/gtsr/critical/other.geometry.gcjb");
            Files.write(other, compile("{}"));
            readerRejected(loader, "non-model path", "other.geometry.json", "java.lang.IllegalStateException");
            readerRejected(loader, "both missing", "models/missing.geometry.json", "java.lang.IllegalStateException");
        } finally {
            try (Stream<Path> paths = Files.walk(fixture)) {
                for (Path p : paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new)) Files.delete(p);
            }
        }
        System.out.println("Actual CriticalGeometry.readJson binary-only models: " + count);
    }
}
