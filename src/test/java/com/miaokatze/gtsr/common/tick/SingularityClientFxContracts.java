// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.tick;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.TileSpacetimeSingularity;
import com.miaokatze.gtsr.common.fx.GTSRBeamFX;
import com.miaokatze.gtsr.common.fx.GTSRFXVec;
import com.miaokatze.gtsr.common.fx.GTSRGlowFX;

/** Offline production-vector and owned-particle lifecycle checks; no Minecraft singleton, world or GL context. */
public final class SingularityClientFxContracts {

    private static int checks;

    public static void main(String[] args) throws Exception {
        vectors();
        lifecycle();
        System.out.println("PASS singularity client FX contracts: " + checks);
    }

    private static void vectors() {
        Random random = new Random(0x51A61A);
        GTSRFXVec scratch = new GTSRFXVec(0, 0, 0);
        for (int i = 0; i < 20_000; i++) {
            GTSRFXVec view = vector(random), segment = vector(random);
            if (i % 5 == 0) segment.z = 0;
            if (i % 7 == 0) segment = view.copy();
            if (i % 11 == 0) segment = new GTSRFXVec(0, 0, 0);
            float scale = random.nextFloat() * 4;
            GTSRFXVec old = GTSRFXVec.crossProduct(view, segment);
            if (old.lengthPow2() < 1.0E-6F) old = GTSRFXVec.getPerpendicular(segment);
            else old.normalize();
            old.multiply(scale);
            require(scratch.setCrossProduct(view, segment) == scratch, "cross product retains scratch identity");
            if (scratch.lengthPow2() < 1.0E-6F) scratch.setPerpendicular(segment);
            else scratch.normalize();
            scratch.multiply(scale);
            same(old, scratch);
            GTSRFXVec aliased = segment.copy();
            same(GTSRFXVec.crossProduct(segment, view), aliased.setCrossProduct(aliased, view));
            aliased = segment.copy();
            same(GTSRFXVec.getPerpendicular(segment), aliased.setPerpendicular(aliased));
        }
    }

    private static GTSRFXVec vector(Random random) {
        return new GTSRFXVec(
            random.nextFloat() * 200 - 100,
            random.nextFloat() * 200 - 100,
            random.nextFloat() * 200 - 100);
    }

    private static void same(GTSRFXVec expected, GTSRFXVec actual) {
        require(Float.floatToIntBits(expected.x) == Float.floatToIntBits(actual.x), "exact vector x");
        require(Float.floatToIntBits(expected.y) == Float.floatToIntBits(actual.y), "exact vector y");
        require(Float.floatToIntBits(expected.z) == Float.floatToIntBits(actual.z), "exact vector z");
    }

    @SuppressWarnings("unchecked")
    private static void lifecycle() throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) singleton.get(null);
        SingularityClientFXHandler handler = new SingularityClientFXHandler();
        TileRunawaySingularity natural = (TileRunawaySingularity) unsafe.allocateInstance(TileRunawaySingularity.class);
        TileRunawaySingularity other = (TileRunawaySingularity) unsafe.allocateInstance(TileSpacetimeSingularity.class);
        GTSRBeamFX[] nearBeams = { (GTSRBeamFX) unsafe.allocateInstance(GTSRBeamFX.class),
            (GTSRBeamFX) unsafe.allocateInstance(GTSRBeamFX.class) };
        GTSRBeamFX otherBeam = (GTSRBeamFX) unsafe.allocateInstance(GTSRBeamFX.class);
        GTSRGlowFX nearGlow = (GTSRGlowFX) unsafe.allocateInstance(GTSRGlowFX.class);
        GTSRGlowFX otherGlow = (GTSRGlowFX) unsafe.allocateInstance(GTSRGlowFX.class);
        Map<TileRunawaySingularity, GTSRBeamFX[]> beams = (Map<TileRunawaySingularity, GTSRBeamFX[]>) field(
            handler,
            "beams");
        Map<TileRunawaySingularity, GTSRGlowFX> glows = (Map<TileRunawaySingularity, GTSRGlowFX>) field(
            handler,
            "glows");
        Map<TileRunawaySingularity, Integer> cooldowns = (Map<TileRunawaySingularity, Integer>) field(
            handler,
            "arcCooldowns");
        beams.put(natural, nearBeams);
        beams.put(other, new GTSRBeamFX[] { otherBeam });
        glows.put(natural, nearGlow);
        glows.put(other, otherGlow);
        cooldowns.put(natural, 10);
        cooldowns.put(other, 7);
        handler.retireEffects(natural);
        require(nearBeams[0].isDead && nearBeams[1].isDead && nearGlow.isDead(), "retire marks all owned effects dead");
        require(
            !beams.containsKey(natural) && !glows.containsKey(natural) && !cooldowns.containsKey(natural),
            "retire removes owned references and cooldown");
        require(
            !otherBeam.isDead && !otherGlow.isDead() && cooldowns.get(other) == 7,
            "retire leaves another singularity's effects alone");
        handler.retireEffects(natural);
        require(!otherBeam.isDead, "retire is idempotent");
        ((Set<TileRunawaySingularity>) field(handler, "lastSeen")).add(other);
        ((Set<TileRunawaySingularity>) field(handler, "current")).add(other);
        handler.resetForWorld(null);
        require(otherBeam.isDead && otherGlow.isDead(), "disconnect retires remaining particles");
        require(beams.isEmpty() && glows.isEmpty() && cooldowns.isEmpty(), "disconnect releases effect ownership");
        require(
            ((Set<?>) field(handler, "lastSeen")).isEmpty() && ((Set<?>) field(handler, "current")).isEmpty(),
            "disconnect releases all tracked tiles");
        require(field(handler, "lastWorld") == null, "disconnect releases the old world");
        handler.resetForWorld(null);
        require(beams.isEmpty(), "world reset is idempotent");
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
