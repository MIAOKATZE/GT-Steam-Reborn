package com.miaokatze.gtsr.common.dimension.prosperity.travel;

/** One navigation destination per player session; discovery uncertainty never retires it. */
public final class BeaconTargetLock {

    public interface Lookup {

        int[] nearest();

        /** Zero means confirmed unavailable; negative means unknown, positive means usable. */
        int status(int x, int z);
    }

    private int[] target;
    private boolean unavailable;

    public int[] update(Lookup lookup) {
        unavailable = false;
        if (target != null && lookup.status(target[0], target[1]) == 0) {
            target = null;
            unavailable = true;
        }
        if (target == null) {
            int[] candidate = lookup.nearest();
            if (candidate != null && lookup.status(candidate[0], candidate[1]) != 0)
                target = new int[] { candidate[0], candidate[1] };
        }
        return target == null ? null : new int[] { target[0], target[1] };
    }

    public boolean unavailable() {
        return unavailable;
    }
}
