package com.miaokatze.gtsr.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;

import net.minecraftforge.client.model.obj.WavefrontObject;

import com.miaokatze.gtsr.client.weapons.ForgeWeaponModel;
import com.miaokatze.gtsr.client.weapons.WeaponModelData;

/** Build-only compiler; Forge itself parses and computes every source float and face normal. */
public final class WeaponModelCompiler {

    private WeaponModelCompiler() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IOException("Usage: WeaponModelCompiler <resources-root> <generated-output-root>");
        Path resources = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        if (output.startsWith(resources) || resources.startsWith(output))
            throw new IOException("Generated output and source resources must be separate trees");
        List<Path> sources;
        try (Stream<Path> files = Files.list(resources.resolve("assets/gtsr/models/weapons"))) {
            sources = files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".obj"))
                .sorted().collect(Collectors.toList());
        }
        if (sources.isEmpty()) throw new IOException("No weapon OBJ models found");
        List<Path> targets = new ArrayList<>();
        List<byte[]> binaries = new ArrayList<>();
        long sourceBytes = 0, compiledBytes = 0, sourceDeflate = 0, compiledDeflate = 0;
        for (Path source : sources) {
            byte[] original = Files.readAllBytes(source);
            WavefrontObject parsed = new WavefrontObject(source.toString(), new ByteArrayInputStream(original));
            WeaponModelData data = ForgeWeaponModel.capture(parsed, sha256(original));
            byte[] binary = encode(data);
            WeaponModelData decoded = WeaponModelData.readCompiled(new ByteArrayInputStream(binary));
            if (!Arrays.equals(binary, encode(decoded))) throw new IOException("Weapon codec roundtrip changed " + source);
            byte[] rehydrated = encode(ForgeWeaponModel.capture(new ForgeWeaponModel(decoded), sha256(original)));
            if (!Arrays.equals(binary, rehydrated)) throw new IOException("Forge face roundtrip changed " + source);
            Path target = output.resolve(resources.relativize(source).toString().replaceFirst("\\.obj$", ".weaponmesh"))
                .normalize();
            if (!target.startsWith(output)) throw new IOException("Generated weapon path escaped output tree");
            targets.add(target);
            binaries.add(binary);
            sourceBytes += original.length;
            compiledBytes += binary.length;
            sourceDeflate += deflatedBytes(original);
            compiledDeflate += deflatedBytes(binary);
            System.out.println("WEAPONMESH " + source.getFileName() + " source=" + original.length + " compiled="
                + binary.length + " groups=" + data.groupCount() + " faces=" + data.faceCount() + " roundtrip=PASS");
        }
        // Parse and verify every input before publishing this compiler's managed outputs.
        for (int i = 0; i < targets.size(); i++) {
            Path target = targets.get(i);
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "weapon-", ".tmp");
            try {
                Files.write(temporary, binaries.get(i));
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        Set<Path> expected = new HashSet<>(targets);
        try (Stream<Path> files = Files.walk(output)) {
            for (Path stale : files.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".weaponmesh")).collect(Collectors.toList())) {
                if (!expected.contains(stale.toAbsolutePath().normalize())) Files.delete(stale);
            }
        }
        System.out.println("WEAPONMESH PASS models=" + sources.size() + " sourceBytes=" + sourceBytes + " compiledBytes="
            + compiledBytes + " sourceDeflate6=" + sourceDeflate + " compiledDeflate6=" + compiledDeflate);
    }

    private static byte[] encode(WeaponModelData data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        data.writeCompiled(output);
        return output.toByteArray();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static int deflatedBytes(byte[] bytes) {
        Deflater deflater = new Deflater(6, true);
        try {
            deflater.setInput(bytes);
            deflater.finish();
            byte[] buffer = new byte[8192];
            int count = 0;
            while (!deflater.finished()) count += deflater.deflate(buffer);
            return count;
        } finally {
            deflater.end();
        }
    }
}
