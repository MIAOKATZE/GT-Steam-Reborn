package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import com.google.gson.JsonObject;

/** Reward difficulty is independent of the seal. Rolls are stable per world/site/node, never world.rand. */
public final class ChestTier {

    private ChestTier() {}

    public static boolean bossReward(JsonObject node) {
        return "combat".equals(RemasterRuntime.string(node, "unlockMode", ""))
            && RemasterRuntime.string(node, "combatModule", "")
                .endsWith("-boss-group");
    }

    public static boolean rareEligible(JsonObject node) {
        String mode = RemasterRuntime.string(node, "unlockMode", "");
        return ("combat".equals(mode) || "puzzle".equals(mode)) && node.has("rareTier5")
            && node.get("rareTier5")
                .getAsBoolean();
    }

    public static int resolveTier(RemasterSite site, JsonObject node) {
        if (bossReward(node)) return 5;
        int fixed = clamp(RemasterRuntime.integer(node, "tier", 1));
        int lo = clamp(RemasterRuntime.integer(node, "tierMin", fixed));
        int hi = clamp(RemasterRuntime.integer(node, "tierMax", node.has("tierMin") ? lo : fixed));
        if (hi < lo) hi = lo;
        long hash = hash(site, node, "tier");
        boolean permitFive = rareEligible(node) && positive(hash(site, node, "rare-tier-five"), 1000) == 0;
        if (!permitFive) {
            lo = Math.min(4, lo);
            hi = Math.min(4, hi);
        }
        return lo + positive(hash, hi - lo + 1);
    }

    public static int clamp(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    private static int positive(long hash, int bound) {
        return (int) ((hash & Long.MAX_VALUE) % bound);
    }

    private static long hash(RemasterSite site, JsonObject node, String purpose) {
        String key = site.seed + ":" + site.id() + ":" + RemasterRuntime.string(node, "id", "") + ":" + purpose;
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
