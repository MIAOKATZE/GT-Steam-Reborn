// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher;

/** Single-file publication; a failed precondition never invokes the publisher. */
final class MaterialIdFileStore {

    interface Publisher {

        void publish(Path target, byte[] contents) throws IOException;
    }

    private MaterialIdFileStore() {}

    static void publish(Path target, byte[] expected, byte[] replacement) throws IOException {
        publish(target, expected, replacement, AtomicFilePublisher::publishAtomic);
    }

    static void publish(Path target, byte[] expected, byte[] replacement, Publisher publisher) throws IOException {
        target = target.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path lockPath = target.resolveSibling(target.getFileName() + ".registration.lock");
        if (Files.isSymbolicLink(target) || Files.isSymbolicLink(lockPath))
            throw new IOException("Symbolic-link target/lock rejected");
        // Separate lock lets Windows replace the cfg atomically. All cooperating registrations use it.
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another registration owns the mapping lock");
            byte[] current = Files.exists(target) ? Files.readAllBytes(target) : null;
            if (!Arrays.equals(expected, current))
                throw new IOException("Mapping changed after preflight; current file preserved");
            publisher.publish(target, replacement);
            if (!Arrays.equals(Files.readAllBytes(target), replacement))
                throw new IOException("Published mapping verification failed");
        }
    }

    static void requireUnchanged(Path source, byte[] expected) throws IOException {
        if (Files.isSymbolicLink(source)) throw new IOException("Symbolic-link source rejected");
        byte[] current = Files.exists(source) ? Files.readAllBytes(source) : null;
        if (!Arrays.equals(expected, current)) throw new IOException("Mapping changed after preflight: " + source);
    }

    static void publishWithReadOnlySource(Path target, byte[] expected, byte[] replacement, Path source,
        byte[] sourceBytes) throws IOException {
        if (target.toAbsolutePath()
            .normalize()
            .equals(
                source.toAbsolutePath()
                    .normalize()))
            throw new IOException("V2 target must differ from read-only historical source");
        publish(target, expected, replacement, (path, contents) -> {
            requireUnchanged(source, sourceBytes);
            AtomicFilePublisher.publishAtomic(path, contents);
            requireUnchanged(source, sourceBytes);
        });
    }
}
