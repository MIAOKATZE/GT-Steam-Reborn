package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;

/** Reward difficulty is independent of the seal. Rolls are stable per world/site/node, never world.rand. */
public final class ChestTier {

    private static final int RARE_PERCENT = 10;

    private ChestTier() {}

    public static boolean bossReward(JsonObject node) {
        return "combat".equals(RemasterRuntime.string(node, "unlockMode", ""))
            && RemasterRuntime.string(node, "combatModule", "")
                .endsWith("-boss-group");
    }

    /** Legacy authoring flag retained for callers; it no longer grants non-boss tier V. */
    public static boolean rareEligible(JsonObject node) {
        String mode = RemasterRuntime.string(node, "unlockMode", "");
        return ("combat".equals(mode) || "puzzle".equals(mode)) && node.has("rareTier5")
            && node.get("rareTier5")
                .getAsBoolean();
    }

    public static int resolveTier(RemasterSite site, JsonObject node) {
        int fixed = clamp(RemasterRuntime.integer(node, "tier", 1));
        int lo = clamp(RemasterRuntime.integer(node, "tierMin", fixed));
        int hi = clamp(RemasterRuntime.integer(node, "tierMax", node.has("tierMin") ? lo : fixed));
        if (hi < lo) hi = lo;
        String mode = RemasterRuntime.string(node, "unlockMode", "");
        boolean conditional = "combat".equals(mode) || "puzzle".equals(mode) || "story".equals(mode);
        return resolve(
            hash(site, node, "tier"),
            hash(site, node, "special-tier"),
            lo,
            hi,
            bossReward(node),
            conditional);
    }

    public static int resolveNativeTier(TileEntitySealedChest tile, int fixed, boolean boss, boolean conditional) {
        String key = tile.getWorldObj()
            .getSeed() + ":"
            + tile.getEncounterId()
            + ":"
            + tile.getPlatformId()
            + ":"
            + tile.xCoord
            + ":"
            + tile.yCoord
            + ":"
            + tile.zCoord;
        fixed = clamp(fixed);
        return resolve(hash(key + ":tier"), hash(key + ":special-tier"), fixed, fixed, boss, conditional);
    }

    private static int resolve(long baseHash, long specialHash, int lo, int hi, boolean boss, boolean conditional) {
        // Mix the identity hash before using its high 53 bits; FNV's low-bit modulus biases rare rolls.
        int roll = (int) ((GTSRWorldgenHash.splitmix64(specialHash) >>> 11) / (double) GTSRWorldgenHash.UNIT_DIVISOR
            * 100);
        if (boss) return roll < RARE_PERCENT ? 5 : roll < RARE_PERCENT * 2 ? 4 : 3;
        if (conditional && hi >= 4 && roll < RARE_PERCENT) return 4;
        lo = Math.min(3, lo);
        hi = Math.min(3, hi);
        return lo + positive(baseHash, hi - lo + 1);
    }

    public static int clamp(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    private static int positive(long hash, int bound) {
        return (int) ((hash & Long.MAX_VALUE) % bound);
    }

    private static long hash(RemasterSite site, JsonObject node, String purpose) {
        String key = site.seed + ":" + site.id() + ":" + RemasterRuntime.string(node, "id", "") + ":" + purpose;
        return hash(key);
    }

    private static long hash(String key) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
