package com.miaokatze.gtsr.client.weapons;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.client.model.IModelCustomLoader;
import net.minecraftforge.client.model.ModelFormatException;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.WavefrontObject;

/** Runs with actual patched Minecraft/Forge classes, replacing only the Tessellator sink for event recording. */
public final class WeaponModelContracts {

    private static int checks;

    private interface Action {

        void run() throws Exception;
    }

    private WeaponModelContracts() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IOException("Usage: WeaponModelContracts <resources-root>");
        System.out.println("WEAPON CONTRACT java=" + System.getProperty("java.version"));
        for (Class<?> type : Arrays.asList(WavefrontObject.class, Vec3.class, MathHelper.class, Tessellator.class))
            System.out.println(
                "WEAPON ORACLE " + type.getName()
                    + "="
                    + type.getProtectionDomain()
                        .getCodeSource());
        RecordingTessellator recorder = new RecordingTessellator();
        Tessellator original = replaceTessellator(recorder);
        try {
            Path models = Paths.get(args[0])
                .resolve("assets/gtsr/models/weapons");
            List<Path> sources;
            try (Stream<Path> paths = Files.list(models)) {
                sources = paths.filter(
                    p -> p.toString()
                        .endsWith(".obj"))
                    .sorted()
                    .collect(Collectors.toList());
            }
            check(sources.size() == 9, "all nine shipped weapon/heat/projectile/casing models");
            int comparisons = 0;
            check(
                ForgeWeaponModel.class.getMethod("renderAll")
                    .getDeclaringClass() == WavefrontObject.class,
                "renderAll remains inherited for existing Forge mixins");
            for (Path source : sources) {
                byte[] sourceBytes = Files.readAllBytes(source);
                WavefrontObject legacy = parse(sourceBytes);
                WeaponModelData data = ForgeWeaponModel.capture(legacy, sha256(sourceBytes));
                byte[] binary = encode(data);
                WeaponModelData decoded = WeaponModelData.readCompiled(new ByteArrayInputStream(binary));
                check(Arrays.equals(binary, encode(decoded)), "deterministic codec " + source);
                check(Arrays.equals(sha256(sourceBytes), decoded.sourceSha256()), "source hash " + source);
                ForgeWeaponModel restored = new ForgeWeaponModel(decoded);
                check(
                    restored.vertices.size() == decoded.positions.length
                        && restored.textureCoordinates.size() == decoded.texCoords.length
                        && restored.vertexNormals.isEmpty()
                        && restored.groupObjects.size() == decoded.groupCount(),
                    "no public seed geometry remains " + source);
                int calls = compare(legacy, restored, recorder);
                comparisons += calls;
                System.out.println(
                    "WEAPON DRAW " + source.getFileName()
                        + " comparisons="
                        + calls
                        + " allSha256="
                        + hex(sha256(record(legacy::renderAll, recorder))));
            }
            check(comparisons == 696, "696 shipped model draw calls");
            fixtures(recorder);
            loading(recorder);
            defenses();
            System.out.println(
                "WEAPON CONTRACT PASS models=" + sources
                    .size() + " shippedDrawComparisons=" + comparisons + " checks=" + checks);
        } finally {
            replaceTessellator(original);
        }
    }

    private static int compare(WavefrontObject legacy, WavefrontObject compiled, RecordingTessellator recorder)
        throws Exception {
        check(Arrays.equals(record(legacy::renderAll, recorder), record(compiled::renderAll, recorder)), "renderAll");
        int comparisons = 1;
        List<String[]> names = new ArrayList<>();
        names.add(new String[] { "missing" });
        names.add(new String[] { "body", "BARREL", "body" });
        for (GroupObject group : legacy.groupObjects) names.add(new String[] { group.name.toUpperCase(Locale.ROOT) });
        for (String[] selected : names) {
            check(
                Arrays.equals(
                    record(() -> legacy.renderOnly(selected), recorder),
                    record(() -> compiled.renderOnly(selected), recorder)),
                "renderOnly " + Arrays.toString(selected));
            check(
                Arrays.equals(
                    record(() -> legacy.renderPart(selected[0]), recorder),
                    record(() -> compiled.renderPart(selected[0]), recorder)),
                "renderPart " + selected[0]);
            check(
                Arrays.equals(
                    record(() -> legacy.renderAllExcept(selected), recorder),
                    record(() -> compiled.renderAllExcept(selected), recorder)),
                "renderAllExcept " + Arrays.toString(selected));
            comparisons += 3;
        }
        return comparisons;
    }

    private static void fixtures(RecordingTessellator recorder) throws Exception {
        String base = "v -0.0 0.00000001 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\n"
            + "vt 0.00000001 0.0 0.5\nvt 1.0 0.0 0.0\nvt 1.0 1.0 0.0\nvt 0.0 1.0 0.0\n"
            + "vn 0 1 0\ns 1\nmtllib ignored.mtl\nusemtl ignored\n";
        for (String faces : Arrays.asList(
            "o body\nf 1/1/1 2/2/1 3/3/1\ng body\nf 1//1 3//1 4//1\ng empty\n",
            "f 1 2 3 4\ng quads\nf 1/1 2/2 3/3 4/4\n",
            "g triangles\nf 1/1 2/2 3/3\ng quads\nf 1/1 2/2 3/3 4/4\n")) {
            byte[] source = (base + faces).getBytes(StandardCharsets.UTF_8);
            WavefrontObject legacy = parse(source);
            ForgeWeaponModel restored = new ForgeWeaponModel(
                WeaponModelData
                    .readCompiled(new ByteArrayInputStream(encode(ForgeWeaponModel.capture(legacy, sha256(source))))));
            compare(legacy, restored, recorder);
        }
    }

    private static void loading(RecordingTessellator recorder) throws Exception {
        ResourceLocation path = new ResourceLocation("gtsr", "models/weapons/fixture.obj");
        byte[] source = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes(StandardCharsets.US_ASCII);
        byte[] binary = encode(ForgeWeaponModel.capture(parse(source), sha256(source)));
        int[] opens = { 0 };
        IModelCustom override = WeaponModelLoader.load(path, location -> {
            opens[0]++;
            check(location.equals(path), "OBJ checked first");
            return new ByteArrayInputStream(source);
        });
        check(opens[0] == 1 && override.getClass() == WavefrontObject.class, "present OBJ uses original Forge parser");
        IModelCustom bundled = WeaponModelLoader.load(path, location -> {
            if (location.equals(path)) throw new FileNotFoundException();
            check(
                location.getResourcePath()
                    .equals("models/weapons/fixture.weaponmesh"),
                "compiled path");
            return new ByteArrayInputStream(binary);
        });
        check(bundled instanceof ForgeWeaponModel, "missing OBJ selects compiled model");
        Field sourceName = WavefrontObject.class.getDeclaredField("fileName");
        sourceName.setAccessible(true);
        check(
            sourceName.get(bundled)
                .equals(path.toString()),
            "compiled model retains original source identity");
        check(
            Arrays.equals(record(override::renderAll, recorder), record(bundled::renderAll, recorder)),
            "loader draw stream");
        opens[0] = 0;
        modelRejected("bad OBJ does not fall back", () -> WeaponModelLoader.load(path, location -> {
            opens[0]++;
            return new ByteArrayInputStream("v broken\n".getBytes(StandardCharsets.US_ASCII));
        }));
        check(opens[0] == 1, "bad override stops after OBJ");
        opens[0] = 0;
        modelRejected("OBJ IO error does not fall back", () -> WeaponModelLoader.load(path, location -> {
            opens[0]++;
            throw new IOException("resource pack failure");
        }));
        check(opens[0] == 1, "nonmissing IO stops after OBJ");
        registeredLoader(path, override);
        // No static model cache in the loader: a later resource generation gets its own Forge model.
        IModelCustom nextGeneration = WeaponModelLoader.load(path, location -> new ByteArrayInputStream(source));
        check(nextGeneration != override, "loader does not retain a stale resource generation");
    }

    @SuppressWarnings("unchecked")
    private static void registeredLoader(ResourceLocation path, IModelCustom expected) throws Exception {
        Field handlersField = AdvancedModelLoader.class.getDeclaredField("instances");
        handlersField.setAccessible(true);
        Map<String, IModelCustomLoader> handlers = (Map<String, IModelCustomLoader>) handlersField.get(null);
        IModelCustomLoader previous = handlers.get("obj");
        boolean[] closed = { false };
        ByteArrayInputStream probe = new ByteArrayInputStream(new byte[0]) {

            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        try {
            AdvancedModelLoader.registerModelHandler(new IModelCustomLoader() {

                public String getType() {
                    return "contract";
                }

                public String[] getSuffixes() {
                    return new String[] { "obj" };
                }

                public IModelCustom loadInstance(ResourceLocation resource) {
                    check(resource.equals(path) && closed[0], "probe closes before registered OBJ handler");
                    return expected;
                }
            });
            check(
                WeaponModelLoader.load(path, resource -> probe, AdvancedModelLoader::loadModel) == expected,
                "raw OBJ delegates to the actual Forge handler registry");
            AdvancedModelLoader.registerModelHandler(new IModelCustomLoader() {

                public String getType() {
                    return "broken contract";
                }

                public String[] getSuffixes() {
                    return new String[] { "obj" };
                }

                public IModelCustom loadInstance(ResourceLocation resource) {
                    throw new ModelFormatException("registered handler failure");
                }
            });
            modelRejected(
                "registered handler failure does not fall back",
                () -> WeaponModelLoader
                    .load(path, resource -> new ByteArrayInputStream(new byte[0]), AdvancedModelLoader::loadModel));
        } finally {
            if (previous == null) handlers.remove("obj");
            else handlers.put("obj", previous);
        }
    }

    private static void defenses() throws Exception {
        byte[] source = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes(StandardCharsets.US_ASCII);
        byte[] binary = encode(ForgeWeaponModel.capture(parse(source), sha256(source)));
        rejected("truncated header", () -> decode(Arrays.copyOf(binary, 20)));
        rejected("truncated payload", () -> decode(Arrays.copyOf(binary, binary.length - 1)));
        rejected("trailing bytes", () -> decode(Arrays.copyOf(binary, binary.length + 1)));
        byte[] badMagic = binary.clone();
        badMagic[0] ^= 1;
        rejected("bad magic", () -> decode(badMagic));
        byte[] badVersion = binary.clone();
        badVersion[7]++;
        rejected("bad version", () -> decode(badVersion));
        byte[] badHash = binary.clone();
        badHash[8] ^= 1;
        rejected("source hash corruption", () -> decode(badHash));
        byte[] badPayload = binary.clone();
        badPayload[badPayload.length - 1] ^= 1;
        rejected("payload corruption", () -> decode(badPayload));
        byte[] oversized = binary.clone();
        Arrays.fill(oversized, 40, 44, (byte) 127);
        rejected("oversized payload", () -> decode(oversized));
        rejected("noncanonical varint", () -> decode(withPayload(new byte[] { (byte) 128, 0 })));
        rejected(
            "oversized table count",
            () -> decode(withPayload(new byte[] { (byte) 255, (byte) 255, (byte) 255, 127 })));
        rejected(
            "invalid varint",
            () -> decode(withPayload(new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255, 15 })));
        rejected("no groups", () -> decode(withPayload(new byte[] { 0, 0, 0, 0 })));
        byte[] invalidRef = Arrays.copyOfRange(binary, 76, binary.length);
        invalidRef[invalidRef.length - 1] = 127;
        rejected("face reference outside table", () -> decode(withPayload(invalidRef)));
    }

    private static byte[] withPayload(byte[] payload) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        byte[] source = new byte[32];
        out.writeInt(0x4754574D);
        out.writeInt(1);
        out.write(source);
        out.writeInt(payload.length);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(source);
        out.write(digest.digest(payload));
        out.write(payload);
        return buffer.toByteArray();
    }

    private static WeaponModelData decode(byte[] bytes) throws IOException {
        return WeaponModelData.readCompiled(new ByteArrayInputStream(bytes));
    }

    private static WavefrontObject parse(byte[] bytes) {
        return new WavefrontObject("weapon contract", new ByteArrayInputStream(bytes));
    }

    private static byte[] encode(WeaponModelData data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        data.writeCompiled(output);
        return output.toByteArray();
    }

    private static byte[] record(Runnable render, RecordingTessellator recorder) {
        recorder.reset();
        render.run();
        return recorder.bytes.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void rejected(String message, Action action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted " + message);
    }

    private static void modelRejected(String message, Action action) throws Exception {
        try {
            action.run();
        } catch (ModelFormatException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted " + message);
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value & 255));
        return result.toString();
    }

    private static Tessellator replaceTessellator(Tessellator replacement) throws Exception {
        // Test-only sink injection avoids an OpenGL context while executing unmodified Forge render APIs.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Field field = Tessellator.class.getDeclaredField("instance");
        Object base = unsafeClass.getMethod("staticFieldBase", Field.class)
            .invoke(unsafe, field);
        long offset = (Long) unsafeClass.getMethod("staticFieldOffset", Field.class)
            .invoke(unsafe, field);
        Method put = unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class);
        Tessellator old = Tessellator.instance;
        put.invoke(unsafe, base, offset, replacement);
        return old;
    }

    private static final class RecordingTessellator extends Tessellator {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(bytes);

        void reset() {
            bytes.reset();
        }

        @Override
        public void startDrawing(int mode) {
            event(1, mode);
        }

        @Override
        public int draw() {
            event(2);
            return 0;
        }

        @Override
        public void setNormal(float x, float y, float z) {
            event(3, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z));
        }

        @Override
        public void addVertex(double x, double y, double z) {
            event(4, Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(y), Double.doubleToRawLongBits(z));
        }

        @Override
        public void addVertexWithUV(double x, double y, double z, double u, double v) {
            event(
                5,
                Double.doubleToRawLongBits(x),
                Double.doubleToRawLongBits(y),
                Double.doubleToRawLongBits(z),
                Double.doubleToRawLongBits(u),
                Double.doubleToRawLongBits(v));
        }

        private void event(long... values) {
            try {
                out.writeInt(values.length);
                for (long value : values) out.writeLong(value);
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }
}
