// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.machinevisual;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.render.ClientDisplayList;

/** Native GT batches are sampled once per client tick; only the motion matrix changes between frames. */
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
    private final Map<Integer, Integer> groups = new HashMap<>();
    private long tick = Long.MIN_VALUE, visibility = Long.MIN_VALUE;
    private int pass, ambientOcclusion;
    private boolean disabled;

    MachineVisualMeshCache() {
        this(NATIVE);
    }

    MachineVisualMeshCache(Driver driver) {
        this.driver = driver;
    }

    void begin(long nextTick, long nextVisibility, int nextPass, int nextAmbientOcclusion) {
        if (tick == nextTick && visibility == nextVisibility
            && pass == nextPass
            && ambientOcclusion == nextAmbientOcclusion) return;
        clear();
        tick = nextTick;
        visibility = nextVisibility;
        pass = nextPass;
        ambientOcclusion = nextAmbientOcclusion;
    }

    /** Returns true for a replay. Compilation publishes only a successfully completed native batch. */
    boolean draw(int group, Runnable commands) {
        if (disabled) {
            commands.run();
            return false;
        }
        Integer list = groups.get(group);
        boolean replay = list != null;
        if (list == null) {
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
            groups.put(group, list);
        }
        driver.draw(list);
        return replay;
    }

    void invalidate(int group) {
        Integer list = groups.remove(group);
        if (list != null) driver.delete(list);
    }

    void clear() {
        // Remove ownership before deletion: a failed driver deletion must never replay a stale list.
        Integer[] owned = groups.values()
            .toArray(new Integer[0]);
        groups.clear();
        tick = Long.MIN_VALUE;
        RuntimeException primary = null;
        for (int list : owned) try {
            driver.delete(list);
        } catch (RuntimeException failure) {
            if (primary == null) primary = failure;
            else primary.addSuppressed(failure);
        }
        if (primary != null) throw primary;
    }
}
