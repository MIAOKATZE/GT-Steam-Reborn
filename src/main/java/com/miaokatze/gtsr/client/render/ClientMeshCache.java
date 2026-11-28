// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Render-thread shared geometry; keys describe local geometry/materials, never an installation position. */
public final class ClientMeshCache<K> implements AutoCloseable {

    private final Map<K, Shared> meshes = new HashMap<>();

    private static final class Shared {

        final ClientMesh mesh;
        int references;

        Shared(ClientMesh mesh) {
            this.mesh = mesh;
        }
    }

    public final class Lease implements AutoCloseable {

        private final K key;
        private Shared shared;

        private Lease(K key, Shared shared) {
            this.key = key;
            this.shared = shared;
            shared.references++;
        }

        public void draw() {
            if (shared != null) shared.mesh.draw();
        }

        @Override
        public void close() {
            Shared owned = shared;
            shared = null;
            if (owned != null && --owned.references == 0) {
                meshes.remove(key, owned);
                owned.mesh.close();
            }
        }
    }

    public Lease acquire(K key, Supplier<ClientMesh> compile) {
        Shared shared = meshes.get(key);
        if (shared == null) {
            shared = new Shared(java.util.Objects.requireNonNull(compile.get(), "mesh"));
            meshes.put(key, shared);
        }
        return new Lease(key, shared);
    }

    public boolean contains(K key) {
        return meshes.containsKey(key);
    }

    /** Invalidates live leases as well; their later close is harmless and cannot delete a replacement. */
    public void clear() {
        Shared[] owned = meshes.values()
            .toArray(new Shared[0]);
        meshes.clear();
        Throwable primary = null;
        for (Shared shared : owned) try {
            shared.mesh.close();
        } catch (RuntimeException | Error failure) {
            if (primary == null) primary = failure;
            else primary.addSuppressed(failure);
        }
        if (primary != null) throw new IllegalStateException("Failed to release shared meshes", primary);
    }

    @Override
    public void close() {
        clear();
    }
}
