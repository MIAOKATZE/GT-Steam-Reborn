// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.render;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IVertexBuffer;

/** Driver-level sharing, failure and release contracts; no OpenGL context required. */
public final class ClientMeshContracts {

    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final class Vao implements IVertexArrayObject {

        int draws, deletes, binds, unbinds;
        boolean failDraw, failDelete;

        public void bind() {
            binds++;
        }

        public void unbind() {
            unbinds++;
        }

        public void draw() {
            check(deletes == 0, "a released VAO is never submitted");
            draws++;
            if (failDraw) throw new IllegalStateException("draw failed");
        }

        public void draw(int first, int count) {
            draw();
        }

        public void draw(int mode, int first, int count) {
            draw();
        }

        public void delete() {
            check(++deletes == 1, "VAO deleted exactly once");
            if (failDelete) throw new IllegalStateException("delete failed");
        }

        public IVertexBuffer getVBO() {
            return null;
        }
    }

    public static void main(String[] args) {
        ClientMeshCache<String> cache = new ClientMeshCache<>();
        Vao vao = new Vao();
        ClientMeshCache<String>.Lease first = cache.acquire("model:material:pass", () -> new ClientMesh(vao));
        ClientMeshCache<String>.Lease second = cache
            .acquire("model:material:pass", () -> { throw new AssertionError("same model was uploaded twice"); });
        check(cache.contains("model:material:pass"), "shared model is indexed by local identity");
        first.draw();
        second.draw();
        first.close();
        first.close();
        check(vao.deletes == 0, "retiring one scene retains another scene's shared mesh");
        second.draw();
        second.close();
        check(vao.deletes == 1 && !cache.contains("model:material:pass"), "last owner releases shared buffers");

        Vao old = new Vao(), replacement = new Vao();
        ClientMeshCache<String>.Lease stale = cache.acquire("reload", () -> new ClientMesh(old));
        cache.clear();
        ClientMeshCache<String>.Lease current = cache.acquire("reload", () -> new ClientMesh(replacement));
        stale.draw();
        stale.close();
        current.draw();
        check(old.draws == 0 && replacement.deletes == 0, "old generation cannot draw or delete replacement");
        current.close();

        try {
            cache.acquire("failed", () -> { throw new IllegalArgumentException("upload"); });
            throw new AssertionError("failed allocation published");
        } catch (IllegalArgumentException expected) {
            check(!cache.contains("failed"), "failed capture never enters the shared index");
        }
        Vao broken = new Vao();
        broken.failDraw = true;
        ClientMesh mesh = new ClientMesh(broken);
        try {
            mesh.draw();
            throw new AssertionError("draw failure swallowed");
        } catch (IllegalStateException expected) {
            check(broken.binds == broken.unbinds, "failed draw restores VAO binding");
        }
        mesh.close();
        Vao failedDelete = new Vao(), other = new Vao();
        failedDelete.failDelete = true;
        cache.acquire("a", () -> new ClientMesh(failedDelete));
        cache.acquire("b", () -> new ClientMesh(other));
        try {
            cache.clear();
            throw new AssertionError("delete failure swallowed");
        } catch (IllegalStateException expected) {
            check(
                other.deletes == 1 && !cache.contains("a") && !cache.contains("b"),
                "cleanup removes ownership then attempts every buffer");
        }
        cache.clear();
        System.out.println("PASS shared VBO contracts: " + checks);
    }
}
