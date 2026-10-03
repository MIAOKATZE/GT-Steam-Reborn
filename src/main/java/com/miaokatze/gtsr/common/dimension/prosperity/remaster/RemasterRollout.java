package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Contained rollout: three standard scenes; saved records and registered assets remain readable. */
public final class RemasterRollout {

    private static final List<String> ACTIVE = Collections
        .unmodifiableList(Arrays.asList("fallen_foundry", "subsided_factory", "forgotten_lake_court"));

    private RemasterRollout() {}

    public static List<String> activeIds() {
        return ACTIVE;
    }

    public static boolean isActive(String id) {
        return ACTIVE.contains(id);
    }

    public static int standardVariant(String id) {
        if (!isActive(id)) throw new IllegalArgumentException("Paused structure: " + id);
        return 0;
    }

    /** Client display hint only. Server authorization still requires the saved site and world state. */
    public static boolean allowsSavedId(String savedId) {
        if (savedId == null) return false;
        String[] parts = savedId.split(":", -1);
        if ((parts.length != 7 && parts.length != 8) || !"echo".equals(parts[0])
            || !"r7".equals(parts[1])
            || !isActive(parts[3])
            || !"0".equals(parts[4])) return false;
        boolean tree = "forgotten_lake_court".equals(parts[3]);
        if (tree ? parts.length != 8 || !"tree-overlay".equals(parts[7])
            : parts.length != 8 || !"compact-prefab".equals(parts[7])) return false;
        try {
            // Match the canonical decimal fields produced by RemasterSite.id(), not a loose prefix.
            return Long.toString(Long.parseLong(parts[2]))
                .equals(parts[2])
                && Integer.toString(Integer.parseInt(parts[5]))
                    .equals(parts[5])
                && Integer.toString(Integer.parseInt(parts[6]))
                    .equals(parts[6]);
        } catch (NumberFormatException malformed) {
            return false;
        }
    }

    /** Nonstandard saved variants are frozen, never moved, deleted or replayed as standard geometry. */
    public static boolean allowsGeneration(RemasterSite site) {
        return site != null && isActive(site.prefab)
            && site.variant == standardVariant(site.prefab)
            && ("forgotten_lake_court".equals(site.prefab) ? "tree-overlay".equals(site.layout)
                : "compact-prefab".equals(site.layout));
    }
}
