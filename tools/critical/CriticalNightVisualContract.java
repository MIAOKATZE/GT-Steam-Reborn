package com.miaokatze.gtsr.client.critical;

/** Deterministic local transition and lightmap contract; no game process, clocks, or GL context required. */
public final class CriticalNightVisualContract {
    private static int checks;
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        checks++;
    }
    private static void near(float value, float expected, String message) {
        check(Math.abs(value - expected) < .00001F, message + ": " + value);
    }
    public static void main(String[] args) {
        CriticalNightClient.Transition fade = new CriticalNightClient.Transition();
        Object world = new Object(), connection = new Object();
        long start = 10_000_000_000L, second = 1_000_000_000L;
        near(fade.update(world, connection, false, start), 0F, "natural initial sky");
        near(fade.update(world, connection, true, start), 0F, "ON begins at existing brightness");
        near(fade.sample(start + 2 * second), .104F, "ON remains transitional after two seconds");
        near(fade.sample(start + 5 * second), .5F, "ON midpoint at five seconds");
        near(fade.sample(start + 10 * second), 1F, "ON completes in ten seconds");
        near(fade.update(world, connection, true, start + 15 * second), 1F, "repeated authoritative packet does not restart fade");
        near(fade.update(world, connection, false, start + 15 * second), 1F, "OFF begins continuously");
        near(fade.sample(start + 17 * second), .896F, "OFF remains transitional after two seconds");
        near(fade.sample(start + 20 * second), .5F, "OFF midpoint at five seconds");
        near(fade.sample(start + 25 * second), 0F, "OFF returns exactly after ten seconds");
        fade.update(world, connection, true, start + 30 * second);
        near(fade.update(world, connection, false, start + 35 * second), .5F, "mid-transition reversal is continuous");
        near(fade.sample(start + 40 * second), .25F, "reversal fades from sampled brightness");
        near(fade.update(new Object(), connection, false, start + 40 * second), 0F, "new World identity rejects old fade");
        near(fade.update(world, new Object(), true, start + 45 * second), 0F, "new connection rejects old fade");
        fade.reset();
        near(fade.sample(start + 100 * second), 0F, "disconnect reset restores original sky");

        CriticalNightClient.NaturalClock clock = new CriticalNightClient.NaturalClock();
        clock.update(world, connection, false, false, 6000, 100, true);
        clock.update(world, connection, false, true, 18000, 101, true);
        check(clock.sample(101) == 6001, "queued ON prevents native S03 locked-time packet replacing natural anchor");
        clock.update(world, connection, true, false, 18000, 120, true);
        check(clock.sample(120) == 6020, "ON visual baseline predicts natural daytime despite actual client WorldInfo midnight");
        clock.update(world, connection, false, false, 18000, 130, true);
        check(clock.sample(130) == 6030, "OFF retains natural prediction until real time resync");
        clock.update(world, connection, false, false, 6060, 160, true);
        check(clock.sample(160) == 6060, "OFF native natural-time packet restores normal clock exactly");
        clock.update(world, connection, true, false, 18000, 170, false);
        check(clock.sample(1000) == 6070, "doDaylightCycle=false freezes prediction continuously");
        clock.update(new Object(), connection, false, false, 9000, 0, true);
        check(clock.sample(1) == 9001, "replacement World cannot inherit natural clock");
        clock.update(world, new Object(), false, false, 4000, 100, false);
        check(clock.sample(200) == 4000, "replacement connection uses its own stopped clock");

        int[] midnight = new int[256], natural = new int[256];
        for (int sky = 0; sky < 16; sky++) for (int block = 0; block < 16; block++) {
            int i = sky * 16 + block;
            midnight[i] = 0xff000000 | sky * 12 << 16 | block * 13 << 8 | sky * 7 + block;
            natural[i] = 0xff000000 | sky * 15 << 16 | block * 13 << 8 | sky * 9 + block;
        }
        int[] dim = midnight.clone();
        CriticalNightClient.blendNightLight(dim, null, 1F);
        for (int sky = 0; sky < 16; sky++) for (int block = 0; block < 16; block++) {
            int i = sky * 16 + block;
            check(dim[i] == midnight[Math.max(0, sky - 1) * 16 + block], "normal midnight shifted one sky row, same block column");
            check((dim[i] >> 8 & 255) == block * 13, "block light retained");
        }
        int[] halfway = midnight.clone();
        CriticalNightClient.blendNightLight(halfway, natural, .5F);
        for (int i = 0; i < 256; i++) check(halfway[i] == CriticalNightClient.blendColor(natural[i], dim[i], .5F), "midpoint palette interpolation");
        int[] off = midnight.clone();
        CriticalNightClient.blendNightLight(off, natural, 0F);
        check(java.util.Arrays.equals(off, natural), "OFF endpoint matches natural/custom palette exactly");
        check(dim[15 * 16] == midnight[14 * 16] && dim[15 * 16] != midnight[0], "open sky is one step below midnight, not sky-zero darkness");
        System.out.println("PASS CriticalNightVisualContract checks=" + checks);
    }
}
