package com.miaokatze.gtsr.common.gui.mui2.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;

/** Local draft; network snapshots never replace edited content. */
public final class ClusterDraftState {

    public final List<Integer> ordinals = new ArrayList<>();
    public int peak;
    public boolean dirty;
    public int revision;
    public long generation;
    public boolean invalidTarget;

    public void follow(List<Integer> chain, int peak, long generation) {
        if (dirty) {
            if (this.generation != generation) invalidTarget = true;
            return;
        }
        ordinals.clear();
        ordinals.addAll(chain);
        this.peak = peak;
        this.generation = generation;
        invalidTarget = false;
    }

    private void edit() {
        dirty = true;
        revision++;
    }

    public void append(int ordinal) {
        if (ordinal < 0 || ordinal >= ChainLink.values().length || ordinals.size() >= 16) return;
        edit();
        ordinals.add(ordinal);
    }

    public void move(int index, int direction) {
        int target = index + direction;
        if (index < 0 || index >= ordinals.size() || target < 0 || target >= ordinals.size()) return;
        edit();
        Collections.swap(ordinals, index, target);
    }

    public void remove(int index) {
        if (index < 0 || index >= ordinals.size()) return;
        edit();
        ordinals.remove(index);
        peak = Math.max(0, Math.min(peak, ordinals.size() - 1));
    }

    public void peak(int index) {
        if (index < 0 || index >= ordinals.size()) return;
        edit();
        peak = index;
    }

    public void clear() {
        edit();
        ordinals.clear();
        peak = 0;
    }

    public boolean valid() {
        if (invalidTarget || ordinals.isEmpty() || ordinals.size() > 16) return false;
        int[] values = ordinals.stream()
            .mapToInt(Integer::intValue)
            .toArray();
        return LogisticsChain.fromOrdinalArray(values)
            .isValidStructure();
    }
}
