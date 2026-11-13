// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.render.ClientDisplayList;

/** Native GT geometry is compiled on admission; only its motion matrix changes while the group is published. */
final class MachineVisualMeshCache {

    interface Driver {

        int compile(Runnable commands);

        void draw(int list);

        void delete(int list);
    }

    private static final Driver NATIVE = new Driver() {

        public int compile(Runnable commands) {
            return ClientDisplayList.compile(commands);
        }

        public void draw(int list) {
            GL11.glCallList(list);
        }

        public void delete(int list) {
            GL11.glDeleteLists(list, 1);
        }
    };

    private final Driver driver;
    private final Map<Long, Mesh> groups = new HashMap<>();
    private int pass, ambientOcclusion = Integer.MIN_VALUE;

    private static final class Mesh {

        final int list;
        final long revision;

        Mesh(int list, long revision) {
            this.list = list;
            this.revision = revision;
        }
    }

    private boolean disabled;

    MachineVisualMeshCache() {
        this(NATIVE);
    }

    MachineVisualMeshCache(Driver driver) {
        this.driver = driver;
    }

    void begin(int nextPass, int nextAmbientOcclusion) {
        if (ambientOcclusion != nextAmbientOcclusion) clear();
        pass = nextPass;
        ambientOcclusion = nextAmbientOcclusion;
    }

    /** Returns true for a replay. Compilation publishes only a successfully completed native batch. */
    boolean draw(int group, long revision, Runnable commands) {
        if (disabled) {
            commands.run();
            return false;
        }
        long key = ((long) pass << 32) | (group & 0xffffffffL);
        Mesh mesh = groups.get(key);
        if (mesh != null && mesh.revision != revision) {
            groups.remove(key);
            driver.delete(mesh.list);
            mesh = null;
        }
        boolean replay = mesh != null;
        if (mesh == null) {
            int list;
            boolean[] entered = { false };
            try {
                list = driver.compile(() -> {
                    entered[0] = true;
                    commands.run();
                });
            } catch (RuntimeException unavailable) {
                // Allocation/capture unavailable before any native block draw: retain immediate rendering.
                // A native renderer failure is propagated after ClientDisplayList restores its owned state.
                if (entered[0]) throw unavailable;
                disabled = true;
                commands.run();
                return false;
            }
            mesh = new Mesh(list, revision);
            groups.put(key, mesh);
        }
        driver.draw(mesh.list);
        return replay;
    }

    void invalidate(int group) {
        java.util.Iterator<Map.Entry<Long, Mesh>> iterator = groups.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Mesh> item = iterator.next();
            if ((int) (long) item.getKey() != group) continue;
            int list = item.getValue().list;
            iterator.remove();
            driver.delete(list);
        }
    }

    void clear() {
        // Remove ownership before deletion: a failed driver deletion must never replay a stale list.
        Mesh[] owned = groups.values()
            .toArray(new Mesh[0]);
        groups.clear();
        RuntimeException primary = null;
        for (Mesh mesh : owned) try {
            driver.delete(mesh.list);
        } catch (RuntimeException failure) {
            if (primary == null) primary = failure;
            else primary.addSuppressed(failure);
        }
        if (primary != null) throw primary;
    }
}
