package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/** Pure local foundation levels; terrain outside authored rooms and streets is never graded. */
public final class RuinTerrainLayout {

    private RuinTerrainLayout() {}

    public static int ground(RuinSite s, int x, int z) {
        return ProsperityTerrainProfile.heightAt(s.seed, s.x + x, s.z + z) - s.y;
    }

    public static int level(RuinSite s, int x, int z) {
        // Local rooms occupy a terrace, instead of inheriting the entire site's center height.
        return ground(s, x, z) + 1;
    }

    public static int production(RuinSite s) {
        return s.kind == 1 ? -24 : level(s, s.width / 2, s.depth / 2);
    }
}
