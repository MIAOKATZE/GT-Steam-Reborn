package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.Random;

/** Authoritative tier rules shared by production timing, feedback and admission. */
public final class RemasterSpawnerContract {

    public static final int COOLDOWN = 12000;

    private RemasterSpawnerContract() {}

    public static int quota(int tier) {
        return tier == 3 ? 45 : tier == 2 ? 24 : 10;
    }

    public static int batch(int tier, int count) {
        return Math.max(0, Math.min(tier * 2, quota(tier) - count));
    }

    public static int delay(Random random) {
        return 200 + random.nextInt(601);
    }

    public static int runes(int tier, int count) {
        return Math.max(0, (int) Math.ceil((tier * 4 + 4) * (1 - (double) count / quota(tier))));
    }

    public static int experience(Random random) {
        return 15 + random.nextInt(15) + random.nextInt(15);
    }

    public static int[] buffs(int tier, Random random) {
        double chance = .2 + tier * .2;
        int[] pool = { 0, 1, 2, 3 }, selected = new int[3];
        int count = 0;
        while (count < 3 && random.nextDouble() < chance) {
            int slot = count + random.nextInt(pool.length - count);
            int swap = pool[count];
            pool[count] = pool[slot];
            pool[slot] = swap;
            selected[count] = pool[count];
            count++;
        }
        int[] result = new int[count];
        System.arraycopy(selected, 0, result, 0, count);
        return result;
    }
}
