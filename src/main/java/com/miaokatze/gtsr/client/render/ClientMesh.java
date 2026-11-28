// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.render;

import net.minecraft.client.renderer.Tessellator;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.miaokatze.miaokatzecore.client.preview.PreviewRenderScope;

/** Render-thread ownership of one immutable GTNHLib VBO/VAO. Capture never publishes a partial batch. */
public final class ClientMesh implements AutoCloseable {

    private static final ClientMeshCache<CaptureKey> CAPTURES = new ClientMeshCache<>();
    private IVertexArrayObject mesh;
    private ClientMeshCache<CaptureKey>.Lease captureLease;

    private static final class CaptureKey {

        final VertexFormat format;
        final int mode;
        final byte[] digest;

        CaptureKey(DirectTessellator tess) {
            format = tess.getVertexFormat();
            mode = tess.getDrawMode();
            try {
                java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
                java.nio.ByteBuffer vertices = tess.getWriteBuffer()
                    .duplicate();
                vertices.position(0);
                hash.update(vertices);
                digest = hash.digest();
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        @Override
        public int hashCode() {
            return 31 * (31 * System.identityHashCode(format) + mode) + java.util.Arrays.hashCode(digest);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CaptureKey key && key.format == format
                && key.mode == mode
                && java.util.Arrays.equals(digest, key.digest);
        }
    }

    ClientMesh(IVertexArrayObject mesh) {
        this.mesh = mesh;
    }

    private ClientMesh(ClientMeshCache<CaptureKey>.Lease captureLease) {
        this.captureLease = captureLease;
    }

    /** Infers the vertex format, retaining the native block renderer's light and colour attributes. */
    public static ClientMesh capture(Runnable commands) {
        return capture(null, commands);
    }

    public static ClientMesh capture(VertexFormat format, Runnable commands) {
        final PreviewRenderScope.ClientTessState snapshot;
        try {
            snapshot = new PreviewRenderScope.ClientTessState(Tessellator.instance);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot capture an active tessellator", failure);
        }
        DirectTessellator tess = format == null ? DirectTessellator.startCapturing()
            : DirectTessellator.startCapturing(format);
        ClientMesh uploaded = null;
        Throwable primary = null;
        try {
            commands.run();
            // Keep capture ownership until upload succeeds: GTNHLib's combined stop/upload leaves its
            // capture stack open if allocation throws. Explicitly stopping in finally handles both paths.
            // Native ISBRH output can include world-dependent colours/light. Content identity shares
            // equal local geometry while retaining those attributes, without baking world coordinates
            // into a cache key or assuming two differently lit installations have equal vertices.
            uploaded = tess.isEmpty() ? new ClientMesh((IVertexArrayObject) null)
                : new ClientMesh(
                    CAPTURES.acquire(
                        new CaptureKey(tess),
                        () -> new ClientMesh(tess.uploadToVBO(VertexBufferType.IMMUTABLE))));
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = null;
            try {
                DirectTessellator.stopCapturing();
            } catch (RuntimeException | Error failure) {
                cleanup = failure;
            }
            try {
                snapshot.restore();
            } catch (Throwable failure) {
                if (cleanup == null) cleanup = failure;
                else cleanup.addSuppressed(failure);
            }
            if (cleanup != null) {
                if (uploaded != null) try {
                    uploaded.close();
                } catch (RuntimeException | Error failure) {
                    cleanup.addSuppressed(failure);
                }
                if (primary != null) primary.addSuppressed(cleanup);
                else throw new IllegalStateException("Failed to restore mesh capture", cleanup);
            }
        }
        return uploaded;
    }

    public void draw() {
        if (captureLease != null) {
            captureLease.draw();
            return;
        }
        IVertexArrayObject owned = mesh;
        if (owned == null) return;
        owned.bind();
        try {
            owned.draw();
        } finally {
            owned.unbind();
        }
    }

    @Override
    public void close() {
        ClientMeshCache<CaptureKey>.Lease lease = captureLease;
        captureLease = null;
        if (lease != null) lease.close();
        IVertexArrayObject owned = mesh;
        mesh = null;
        if (owned != null) owned.delete();
    }

    public static void rollback(Iterable<ClientMesh> meshes, Throwable primary) {
        for (ClientMesh mesh : meshes) if (mesh != null) try {
            mesh.close();
        } catch (RuntimeException | Error failure) {
            primary.addSuppressed(failure);
        }
    }
}
