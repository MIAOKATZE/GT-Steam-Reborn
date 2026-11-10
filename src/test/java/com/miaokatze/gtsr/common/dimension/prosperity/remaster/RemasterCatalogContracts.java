package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.critical.CriticalJsonData;

/** Exercises the real catalog and prefab classes without Minecraft or FML. */
public final class RemasterCatalogContracts {

    private static final String BASE = "assets/gtsr/remaster/";
    private static final String PACKAGE = "com.miaokatze.gtsr.common.dimension.prosperity.remaster.";
    private static int checks, declarations, knownMismatches;

    private interface Action {

        void run() throws Exception;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void same(Object expected, Object actual, String message) {
        check(
            String.valueOf(expected)
                .equals(String.valueOf(actual)),
            message);
    }

    private static byte[] bytes(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static String original(Path source, String relative) throws IOException {
        return new String(
            bytes(new GZIPInputStream(Files.newInputStream(source.resolve(BASE + relative)))),
            StandardCharsets.UTF_8);
    }

    private static JsonObject object(String json) {
        return new JsonParser().parse(json)
            .getAsJsonObject();
    }

    private static void write(Path path, String json, boolean gzip) throws IOException {
        Files.createDirectories(path.getParent());
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        if (gzip) {
            try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
                out.write(raw);
            }
        } else Files.write(path, CriticalJsonData.compile(new ByteArrayInputStream(raw)));
    }

    /** Every remaster resource comes exclusively from these fixture URLs, never the parent. */
    private static final class Resources extends URLClassLoader {

        final Map<String, Integer> requests = new LinkedHashMap<>();

        Resources(Path... roots) throws Exception {
            super(urls(roots), RemasterCatalogContracts.class.getClassLoader());
        }

        private static URL[] urls(Path[] roots) throws Exception {
            URL[] urls = new URL[roots.length];
            for (int i = 0; i < roots.length; i++) urls[i] = roots[i].toUri()
                .toURL();
            return urls;
        }

        @Override
        public URL getResource(String name) {
            if (!name.startsWith(BASE)) return super.getResource(name);
            requests.put(name, requests.containsKey(name) ? requests.get(name) + 1 : 1);
            return findResource(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PACKAGE + "RemasterCatalog") && !name.startsWith(PACKAGE + "RemasterPrefab"))
                return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] code = bytes(input);
                        found = defineClass(name, code, 0, code.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) resolveClass(found);
                return found;
            }
        }

        Object call(String name, Class<?>[] types, Object... args) throws Exception {
            Method method = loadClass(PACKAGE + "RemasterCatalog").getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        }

        Object prefab(String id, int variant) throws Exception {
            return call("get", new Class<?>[] { String.class, int.class }, id, variant);
        }

        JsonObject entrance(String id, int variant) throws Exception {
            return (JsonObject) call("entrance", new Class<?>[] { String.class, int.class }, id, variant);
        }

        JsonElement read(String path, boolean compressed) throws Exception {
            return (JsonElement) call("read", new Class<?>[] { String.class, boolean.class }, path, compressed);
        }

        @SuppressWarnings("unchecked")
        Map<Object, Object> cache(String owner, String name) throws Exception {
            Field field = loadClass(PACKAGE + owner).getDeclaredField(name);
            field.setAccessible(true);
            return (Map<Object, Object>) field.get(null);
        }

        int reads(String relative) {
            Integer count = requests.get(BASE + relative);
            return count == null ? 0 : count;
        }
    }

    private static String compiled(String relative) {
        return relative.substring(0, relative.length() - ".json.gz".length()) + ".gcjb";
    }

    private static Object field(Object instance, String name) throws Exception {
        return instance.getClass()
            .getField(name)
            .get(instance);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> slice(Object prefab, int x, int z) throws Exception {
        return (List<Object>) prefab.getClass()
            .getMethod("slice", int.class, int.class)
            .invoke(prefab, x, z);
    }

    private static void rejected(String label, Action action) throws Exception {
        try {
            action.run();
        } catch (InvocationTargetException e) {
            check(e.getCause() instanceof IllegalStateException, label + " must explicitly fail: " + e.getCause());
            check(
                e.getCause()
                    .getCause() != null,
                label + " lost failure cause");
            return;
        }
        throw new AssertionError("Accepted " + label);
    }

    private static void declaration(String relative, String raw, JsonObject descriptor) throws Exception {
        StringBuilder sha = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256")
            .digest(raw.getBytes(StandardCharsets.UTF_8))) sha.append(String.format("%02x", b & 255));
        if (sha.toString()
            .equals(
                descriptor.get("sha256")
                    .getAsString()))
            declarations++;
        else {
            same("prefabs/fallen_foundry-v0.json.gz", relative, "Unexpected authored SHA mismatch");
            knownMismatches++;
        }
    }

    private static void binaryOnly(Path source, Path generated, Path fixture) throws Exception {
        Path catalog = source.resolve(BASE + "catalog.json");
        Files.createDirectories(fixture.resolve(BASE));
        Files.copy(catalog, fixture.resolve(BASE + "catalog.json"));
        JsonObject config = object(new String(Files.readAllBytes(catalog), StandardCharsets.UTF_8));
        JsonArray descriptors = config.getAsJsonArray("prefabs");
        Map<String, JsonObject> roots = new LinkedHashMap<>();
        try (Resources loader = new Resources(generated, fixture)) {
            same(config, loader.call("config", new Class<?>[0]), "Catalog fields/order changed");
            check(
                loader.cache("RemasterCatalog", "CACHE")
                    .isEmpty(),
                "Catalog eagerly loads prefabs");
            check(
                loader.cache("RemasterPrefab", "SLICE_CACHE")
                    .isEmpty(),
                "Catalog eagerly loads slices");
            for (JsonElement value : descriptors) {
                JsonObject descriptor = value.getAsJsonObject();
                String id = descriptor.get("id")
                    .getAsString(),
                    path = descriptor.get("file")
                        .getAsString();
                int variant = descriptor.get("variant")
                    .getAsInt();
                check(loader.findResource(BASE + path) == null, "Original gzip leaked into binary fixture " + path);
                String raw = original(source, path);
                JsonObject expected = object(raw);
                roots.put(path, expected);
                declaration(path, raw, descriptor);
                same(expected, loader.read(path, true), "Root fields/order changed " + path);
                same(
                    descriptor,
                    loader.call("descriptor", new Class<?>[] { String.class, int.class }, id, variant),
                    "Descriptor changed");
                JsonObject metadata = expected.getAsJsonObject("metadata");
                JsonObject entrance = metadata.getAsJsonObject("surfaceEntrance");
                if (entrance == null) entrance = metadata.getAsJsonObject("productionSurfaceEntrance");
                same(entrance, loader.entrance(id, variant), "Entrance streaming entry changed " + path);
                Object prefab = loader.prefab(id, variant);
                same(
                    expected.get("id")
                        .getAsString(),
                    field(prefab, "id"),
                    "Prefab id changed");
                same(
                    expected.get("variant")
                        .getAsInt(),
                    field(prefab, "variant"),
                    "Prefab variant changed");
                same(metadata, field(prefab, "metadata"), "Prefab metadata/source fields changed");
                for (String name : new String[] { "min", "max", "extent", "nominal" }) {
                    int[] actual = (int[]) field(prefab, name);
                    JsonArray values = expected.getAsJsonArray(name);
                    check(actual.length == values.size(), "Prefab bounds length changed");
                    for (int i = 0; i < actual.length; i++) same(
                        values.get(i)
                            .getAsInt(),
                        actual[i],
                        "Prefab bounds changed");
                }
                String[] palette = (String[]) field(prefab, "palette");
                JsonArray names = expected.getAsJsonArray("palette");
                check(palette.length == names.size(), "Palette length changed");
                for (int i = 0; i < palette.length; i++) same(
                    names.get(i)
                        .getAsString(),
                    palette[i],
                    "Palette order/value changed");
                check(loader.prefab(id, variant) == prefab, "Prefab cache missed retained entry");
                check(
                    loader.cache("RemasterCatalog", "CACHE")
                        .size() <= 8,
                    "Prefab LRU exceeded 8");
                check(
                    loader.cache("RemasterPrefab", "SLICE_CACHE")
                        .isEmpty(),
                    "Root/entrance eagerly loaded slice runs");
            }
            check(roots.size() == 64, "Expected 64 prefab roots");
            check(
                loader.cache("RemasterCatalog", "CACHE")
                    .size() == 8,
                "Prefab LRU did not evict");
            JsonObject firstDescriptor = descriptors.get(0)
                .getAsJsonObject();
            String firstRoot = firstDescriptor.get("file")
                .getAsString();
            int rootReads = loader.reads(compiled(firstRoot));
            loader.prefab(
                firstDescriptor.get("id")
                    .getAsString(),
                firstDescriptor.get("variant")
                    .getAsInt());
            check(loader.reads(compiled(firstRoot)) == rootReads + 1, "Evicted prefab did not reload lazily");
            for (JsonObject root : roots.values()) {
                for (JsonElement value : root.getAsJsonArray("slices")) {
                    String path = value.getAsJsonObject()
                        .get("file")
                        .getAsString();
                    check(loader.reads(compiled(path)) == 0, "Root/entrance opened slice " + path);
                }
            }
            int slices = 0;
            String firstSlice = null;
            Object firstPrefab = null;
            int firstX = 0, firstZ = 0;
            for (JsonElement value : descriptors) {
                JsonObject descriptor = value.getAsJsonObject();
                Object prefab = loader.prefab(
                    descriptor.get("id")
                        .getAsString(),
                    descriptor.get("variant")
                        .getAsInt());
                JsonObject root = roots.get(
                    descriptor.get("file")
                        .getAsString());
                check(slice(prefab, Integer.MIN_VALUE, Integer.MAX_VALUE).isEmpty(), "Absent slice must be empty");
                for (JsonElement item : root.getAsJsonArray("slices")) {
                    JsonObject entry = item.getAsJsonObject();
                    String path = entry.get("file")
                        .getAsString();
                    check(loader.findResource(BASE + path) == null, "Original slice leaked into binary fixture");
                    int x = entry.get("x")
                        .getAsInt(),
                        z = entry.get("z")
                            .getAsInt();
                    String raw = original(source, path);
                    declaration(path, raw, entry);
                    JsonArray rows = new JsonParser().parse(raw)
                        .getAsJsonArray();
                    same(rows, loader.read(path, true), "Slice JSON fields/order/value changed " + path);
                    List<Object> runs = slice(prefab, x, z);
                    check(runs.size() == rows.size(), "Run count changed " + path);
                    for (int i = 0; i < rows.size(); i++) {
                        JsonArray row = rows.get(i)
                            .getAsJsonArray();
                        String[] fields = { "x", "y", "z", "length", "paletteIndex" };
                        for (int column = 0; column < fields.length; column++) same(
                            row.get(column)
                                .getAsInt(),
                            field(runs.get(i), fields[column]),
                            "Run order/value changed " + path);
                    }
                    check(slice(prefab, x, z) == runs, "Slice cache missed retained entry");
                    try {
                        runs.add(null);
                        throw new AssertionError("Slice runs became mutable");
                    } catch (UnsupportedOperationException expected) {
                        checks++;
                    }
                    check(
                        loader.cache("RemasterPrefab", "SLICE_CACHE")
                            .size() <= 64,
                        "Slice LRU exceeded 64");
                    if (firstSlice == null) {
                        firstSlice = path;
                        firstPrefab = prefab;
                        firstX = x;
                        firstZ = z;
                    }
                    slices++;
                }
            }
            check(slices == 740, "Expected all 740 slices");
            check(
                loader.cache("RemasterPrefab", "SLICE_CACHE")
                    .size() == 64,
                "Slice LRU did not evict");
            int before = loader.reads(compiled(firstSlice));
            slice(firstPrefab, firstX, firstZ);
            check(loader.reads(compiled(firstSlice)) == before + 1, "Evicted slice did not reload lazily");
            System.out
                .println("Actual binary-only catalog/get/entrance/slice: roots=" + roots.size() + "; slices=" + slices);
        }
    }

    private static void failures(Path fixture) throws Exception {
        Path base = fixture.resolve(BASE);
        Files.createDirectories(base.resolve("prefabs"));
        Files.write(
            base.resolve("catalog.json"),
            ("{\"structures\":[],\"prefabs\":[{\"id\":\"contract\",\"variant\":0,"
                + "\"file\":\"prefabs/contract.json.gz\"}]}").getBytes(StandardCharsets.UTF_8));
        Path gzip = base.resolve("prefabs/contract.json.gz"), binary = base.resolve("prefabs/contract.gcjb");
        String source = "{\"selected\":\"original\",\"metadata\":{\"surfaceEntrance\":{\"value\":1}}}";
        String fallback = "{\"selected\":\"compiled\",\"metadata\":{\"productionSurfaceEntrance\":{\"value\":2}}}";
        try (Resources loader = new Resources(fixture)) {
            write(gzip, source, true);
            Files.write(binary, new byte[] { 1, 2, 3 });
            same(object(source), loader.read("prefabs/contract.json.gz", true), "Original gzip must win");
            same("{\"value\":1}", loader.entrance("contract", 0), "Entrance must prefer original gzip");
            check(loader.reads("prefabs/contract.gcjb") == 0, "Original lookup opened fallback");
            write(binary, fallback, false);
            Files.write(gzip, new byte[] { 1, 2, 3 });
            rejected("bad original gzip must not fall back", () -> loader.read("prefabs/contract.json.gz", true));
            loader.cache("RemasterCatalog", "ENTRANCES")
                .clear();
            rejected("entrance bad original gzip", () -> loader.entrance("contract", 0));
            write(gzip, "{\"broken\":", true);
            rejected("malformed original JSON must not fall back", () -> loader.read("prefabs/contract.json.gz", true));
            Files.delete(gzip);
            same(object(fallback), loader.read("prefabs/contract.json.gz", true), "Missing gzip must use binary");
            same("{\"value\":2}", loader.entrance("contract", 0), "Entrance must use binary Reader");
            rejected(
                "uncompressed read must not use gzip fallback",
                () -> loader.read("prefabs/contract.json.gz", false));
            Files.write(binary, new byte[] { 1, 2, 3 });
            rejected("bad compiled header", () -> loader.read("prefabs/contract.json.gz", true));
            loader.cache("RemasterCatalog", "ENTRANCES")
                .clear();
            rejected("entrance bad compiled header", () -> loader.entrance("contract", 0));
            write(binary, fallback, false);
            byte[] corrupt = Files.readAllBytes(binary);
            corrupt[corrupt.length - 1] ^= 1;
            Files.write(binary, corrupt);
            rejected("bad compiled checksum", () -> loader.read("prefabs/contract.json.gz", true));
            Files.delete(binary);
            rejected("both resources missing", () -> loader.read("prefabs/contract.json.gz", true));
            rejected("entrance both resources missing", () -> loader.entrance("contract", 0));
            same(
                "{\"structures\":[],\"prefabs\":[{\"id\":\"contract\",\"variant\":0,\"file\":\"prefabs/contract.json.gz\"}]}",
                loader.read("catalog.json", false),
                "Uncompressed catalog read changed");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
            "Expected source resources, generated resources and owned fixture parent");
        Path parent = Paths.get(args[2]);
        Files.createDirectories(parent);
        Path owned = Files.createTempDirectory(parent, "remaster-owned-");
        try {
            binaryOnly(Paths.get(args[0]), Paths.get(args[1]), owned.resolve("catalog"));
            failures(owned.resolve("failures"));
            check(declarations == 803 && knownMismatches == 1, "Authored declaration baseline changed");
            System.out.println(
                "Preserved authored SHA declarations: matches=" + declarations
                    + "; known fallen_foundry mismatch="
                    + knownMismatches);
            System.out.println("Remaster contract checks: " + checks);
        } finally {
            try (Stream<Path> paths = Files.walk(owned)) {
                for (Path path : paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new)) Files.delete(path);
            }
        }
    }
}
