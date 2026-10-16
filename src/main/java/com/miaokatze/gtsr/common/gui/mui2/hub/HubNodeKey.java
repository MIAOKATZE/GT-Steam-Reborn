package com.miaokatze.gtsr.common.gui.mui2.hub;

import java.util.Objects;

/** Stable business identity; a sorted row index is never sent as a target. */
public final class HubNodeKey implements Comparable<HubNodeKey> {

    public final int x, y, z, dim;

    public HubNodeKey(int x, int y, int z, int dim) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
    }

    @Override
    public int compareTo(HubNodeKey other) {
        int c = Integer.compare(dim, other.dim);
        if (c == 0) c = Integer.compare(x, other.x);
        if (c == 0) c = Integer.compare(y, other.y);
        if (c == 0) c = Integer.compare(z, other.z);
        return c;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HubNodeKey other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, dim);
    }

    @Override
    public String toString() {
        return "[" + dim + "] " + x + ", " + y + ", " + z;
    }
}
