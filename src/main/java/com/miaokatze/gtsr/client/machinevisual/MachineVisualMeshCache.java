// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

import java.util.HashMap;
import java.util.Map;

import com.miaokatze.gtsr.client.render.ClientMesh;

/** Native GT geometry is compiled on admission; only its motion matrix changes while the group is published. */
final class MachineVisualMeshCache {

    interface Driver {

        Object compile(Runnable commands);

        void draw(Object list);

        void delete(Object list);
    }

    private static final Driver NATIVE = new Driver() {

        public Object compile(Runnable commands) {
            return ClientMesh.capture(commands);
        }

        public void draw(Object list) {
            ((ClientMesh) list).draw();
        }

        public void delete(Object list) {
            ((ClientMesh) list).close();
        }
    };

    private final Driver driver;
    private final Map<Long, Mesh> groups = new HashMap<>();
    private int pass, ambientOcclusion = Integer.MIN_VALUE;

    private static final class Mesh {

        final Object list;
        final long revision;

        Mesh(Object list, long revision) {
            this.list = list;
            this.revision = revision;
        }
    }

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
        long key = ((long) pass << 32) | (group & 0xffffffffL);
        Mesh mesh = groups.get(key);
        if (mesh != null && mesh.revision != revision) {
            groups.remove(key);
            driver.delete(mesh.list);
            mesh = null;
        }
        boolean replay = mesh != null;
        if (mesh == null) {
            if (driver == NATIVE && !com.miaokatze.gtsr.client.render.ClientRenderFrame.tryBuild()) return false;
            Object list = driver.compile(commands);
            mesh = new Mesh(list, revision);
            groups.put(key, mesh);
        }
        driver.draw(mesh.list);
        return replay;
    }

    void invalidate(int group) {
        java.util.List<Object> owned = new java.util.ArrayList<>();
        java.util.Iterator<Map.Entry<Long, Mesh>> iterator = groups.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Mesh> item = iterator.next();
            if ((int) (long) item.getKey() != group) continue;
            Object list = item.getValue().list;
            iterator.remove();
            owned.add(list);
        }
        RuntimeException primary = null;
        for (Object list : owned) try {
            driver.delete(list);
        } catch (RuntimeException failure) {
            if (primary == null) primary = failure;
            else primary.addSuppressed(failure);
        }
        if (primary != null) throw primary;
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
