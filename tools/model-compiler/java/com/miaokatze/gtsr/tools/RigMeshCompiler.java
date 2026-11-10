package com.miaokatze.gtsr.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;

import com.miaokatze.gtsr.client.encounter.RigMeshData;

/** Build-only compiler. Original OBJ files stay in the source tree and sources artifact. */
public final class RigMeshCompiler {

    private RigMeshCompiler() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IOException("Usage: RigMeshCompiler <resources-root> <generated-output-root>");
        Path resources = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        if (output.startsWith(resources) || resources.startsWith(output))
            throw new IOException("Generated output and source resources must be separate trees");
        validatePixelStyle(resources);
        Path liminal = resources.resolve("assets/gtsr/liminal");
        List<Path> sources = new ArrayList<>();
        Set<String> active = new HashSet<>();
        for (String line : Files.readAllLines(liminal.resolve("active-models.txt"), StandardCharsets.UTF_8)) {
            String code = line.split("#", 2)[0].trim();
            if (code.isEmpty()) continue;
            if (!code.matches("[a-z0-9_-]+") || !active.add(code))
                throw new IOException("Invalid or duplicate active encounter model: " + code);
            Path source = liminal.resolve(code).resolve("models").resolve(code + ".obj");
            if (!Files.isRegularFile(source)) throw new IOException("Missing active encounter model: " + source);
            sources.add(source);
        }
        sources.sort(Path::compareTo);
        if (sources.isEmpty()) throw new IOException("No encounter OBJ models found in " + liminal);
        List<Compiled> compiled = new ArrayList<>();
        Set<Path> expected = new HashSet<>();
        long sourceBytes = 0, binaryBytes = 0, sourceDeflate = 0, binaryDeflate = 0, primitiveBytes = 0;
        long triangles = 0;
        for (Path source : sources) {
            Path relative = liminal.relativize(source);
            String code = relative.getName(0).toString();
            if (relative.getNameCount() != 3 || !code.matches("[a-z0-9_-]+")
                || !"models".equals(relative.getName(1).toString())
                || !(code + ".obj").equals(relative.getName(2).toString()))
                throw new IOException("Unexpected encounter model path: " + relative);
            RigMeshData original;
            try (InputStream input = Files.newInputStream(source)) {
                original = RigMeshData.readObj(input);
            }
            byte[] binary = serialize(original);
            RigMeshData decoded = RigMeshData.readCompiled(new ByteArrayInputStream(binary));
            compare(original, decoded, source);
            if (!Arrays.equals(binary, serialize(decoded)))
                throw new IOException("Nondeterministic polygon roundtrip: " + source);
            Path target = output.resolve(resources.relativize(source).toString().replaceFirst("\\.obj$", ".rigmesh"))
                .normalize();
            if (!target.startsWith(output)) throw new IOException("Generated path escaped output tree");
            compiled.add(new Compiled(target, binary));
            expected.add(target);
            sourceBytes += Files.size(source);
            binaryBytes += binary.length;
            byte[] sourceData = Files.readAllBytes(source);
            sourceDeflate += deflatedBytes(sourceData);
            binaryDeflate += deflatedBytes(binary);
            primitiveBytes += decoded.retainedPrimitiveBytes();
            triangles += decoded.triangles();
            System.out.println("RIGMESH " + code + " sourceSha256=" + hex(original.sourceSha256()) + " source="
                + sourceData.length + " compiled=" + binary.length + " triangles=" + decoded.triangles()
                + " roundtrip=PASS");
        }
        // Validate the entire set before publishing anything. Only this compiler's managed files are removed.
        Files.createDirectories(output);
        for (Compiled item : compiled) publish(item);
        try (Stream<Path> stream = Files.walk(output)) {
            for (Path stale : stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".rigmesh")).collect(Collectors.toList())) {
                Path absolute = stale.toAbsolutePath().normalize();
                if (!absolute.startsWith(output)) throw new IOException("Stale path escaped generated output");
                if (!expected.contains(absolute)) Files.delete(absolute);
            }
        }
        System.out.println("RIGMESH PASS models=" + compiled.size() + " triangles=" + triangles + " sourceBytes="
            + sourceBytes + " compiledBytes=" + binaryBytes + " sourceDeflate6=" + sourceDeflate + " compiledDeflate6="
            + binaryDeflate + " retainedPrimitiveBytes=" + primitiveBytes);
    }

    /** Keep authoring tools from silently reintroducing 32px assets after the style migration. */
    private static void validatePixelStyle(Path resources) throws IOException {
        Path textures = resources.resolve("assets/gtsr/textures");
        int checked = 0;
        for (String folder : new String[] { "blocks", "items/lore" }) {
            Path directory = textures.resolve(folder);
            if (!Files.isDirectory(directory)) throw new IOException("Missing texture directory: " + directory);
            try (Stream<Path> stream = Files.walk(directory)) {
                for (Path png : stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".png")).collect(Collectors.toList())) {
                    require16PixelFrames(png);
                    checked++;
                }
            }
        }
        for (String file : new String[] { "spacetime_anchor_beacon.png", "spacetime_anchor_beacon_needle.png",
            "AbyssalObsessionBucket.png" }) {
            require16PixelFrames(textures.resolve("items").resolve(file));
            checked++;
        }
        System.out.println("PIXEL_STYLE PASS images=" + checked + " frameSize=16");
    }

    private static void require16PixelFrames(Path png) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(png))) {
            if (input.readLong() != 0x89504e470d0a1a0aL || input.readInt() != 13
                || input.readInt() != 0x49484452) throw new IOException("Invalid PNG header: " + png);
            int width = input.readInt(), height = input.readInt();
            if (width != 16 || height <= 0 || height % 16 != 0)
                throw new IOException("Expected 16x16 texture frames, found " + width + "x" + height + ": " + png);
        }
    }

    private static void publish(Compiled item) throws IOException {
        Files.createDirectories(item.path.getParent());
        Path temp = Files.createTempFile(item.path.getParent(), ".rigmesh-", ".tmp");
        try {
            Files.write(temp, item.bytes);
            try {
                Files.move(temp, item.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, item.path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] serialize(RigMeshData mesh) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mesh.writeCompiled(output);
        return output.toByteArray();
    }

    private static void compare(RigMeshData original, RigMeshData decoded, Path source) throws IOException {
        if (original.triangles() != decoded.triangles()
            || !Arrays.equals(original.sourceSha256(), decoded.sourceSha256()))
            throw new IOException("Mesh metadata roundtrip mismatch: " + source);
        compareBits(original.positions(), decoded.positions(), source);
        compareBits(original.texCoords(), decoded.texCoords(), source);
        Iterator<Map.Entry<String, int[]>> left = original.groups().entrySet().iterator();
        Iterator<Map.Entry<String, int[]>> right = decoded.groups().entrySet().iterator();
        while (left.hasNext() && right.hasNext()) {
            Map.Entry<String, int[]> a = left.next(), b = right.next();
            if (!a.getKey().equals(b.getKey()) || !Arrays.equals(a.getValue(), b.getValue()))
                throw new IOException("Mesh group/triangle order mismatch: " + source);
        }
        if (left.hasNext() || right.hasNext()) throw new IOException("Mesh group count mismatch: " + source);
    }

    private static void compareBits(double[] expected, double[] actual, Path source) throws IOException {
        if (expected.length != actual.length) throw new IOException("Coordinate count mismatch: " + source);
        for (int i = 0; i < expected.length; i++)
            if (Double.doubleToRawLongBits(expected[i]) != Double.doubleToRawLongBits(actual[i]))
                throw new IOException("Coordinate precision mismatch at " + i + ": " + source);
    }

    private static long deflatedBytes(byte[] bytes) {
        Deflater deflater = new Deflater(6, true);
        try {
            deflater.setInput(bytes);
            deflater.finish();
            byte[] buffer = new byte[8192];
            long count = 0;
            while (!deflater.finished()) count += deflater.deflate(buffer);
            return count;
        } finally {
            deflater.end();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    private static final class Compiled {
        final Path path;
        final byte[] bytes;

        Compiled(Path path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }
}
