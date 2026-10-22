package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Bounded production ledger contract: no game startup, synthetic clocks only. */
public final class CriticalFoundationContract {
    private static int checks;
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }

    public static void source(Class<?> type, String[] args) throws Exception {
        java.nio.file.Path origin = java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (args.length > 0 && !origin.toRealPath().equals(java.nio.file.Path.of(args[0]).toRealPath()))
            throw new AssertionError("Wrong production source for " + type.getName() + ": " + origin);
        System.out.println("SOURCE " + type.getName() + "=" + origin.toAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        source(CriticalFoundationChunk.class, args);
        source(CriticalWorldData.class, args);
        CriticalWorldData world = new CriticalWorldData();
        CriticalFoundationChunk chunk = world.foundation(-2, 7);
        check(chunk == world.foundation(-2, 7), "Two looms share the exact chunk journal");
        chunk.pour(400, 1);
        check(chunk.completedCells() == 0, "Unpaid work cannot advance");
        chunk.setFunded();
        chunk.pour(50, 1);
        chunk.pour(350, 1);
        check(chunk.cellProgress() == 50, "Two looms cannot advance one chunk in the same tick");
        NBTTagCompound saved = new NBTTagCompound();
        world.writeToNBT(saved);
        CriticalWorldData reloaded = new CriticalWorldData();
        reloaded.readFromNBT(saved);
        chunk = reloaded.foundation(-2, 7);
        check(chunk.funded() && chunk.cellProgress() == 50, "Reload preserves debit and exact progress");
        check(chunk.curingRemaining() == 1_440_000, "72,000 seconds survive world NBT");
        chunk.pour(350, 2);
        check(chunk.completedCells() == 1 && !chunk.funded(), "Completing one cell requests the next debit");
        check(chunk.cellProgress() == 0, "Next cell starts at zero");
        check(CriticalFoundationChunk.overclockWork(8, 7) == 0, "Insufficient voltage rejected");
        check(CriticalFoundationChunk.overclockWork(8, 8) == 1, "UHV baseline is one work per tick");
        check(CriticalFoundationChunk.overclockWork(8, 31) == 1, "Partial overclock voltage buys no fractional tier");
        check(CriticalFoundationChunk.overclockWork(8, 32) == 4, "Fourfold voltage buys fourfold speed");
        check(CriticalFoundationChunk.overclockWork(8, 128) == 16, "Two perfect overclocks compose");
        long tick = 3;
        for (int cell = 1; cell < 16; cell++) {
            chunk.setFunded();
            chunk.pour(400, tick++);
        }
        check(chunk.poured() && !chunk.complete(), "Pouring all sixteen cells still requires curing");
        int before = chunk.curingRemaining();
        chunk.cure(tick - 1);
        check(chunk.curingRemaining() == before, "Last pouring tick cannot also cure through another loom");
        chunk.cure(tick++);
        chunk.cure(tick - 1);
        check(chunk.curingRemaining() == before - 1, "Shared curing advances once per world tick");
        check(CriticalFoundationChunk.curingReduction(1_440_000, 16) == 144_000, "Sixteen singularities remove ten percent");
        check(CriticalFoundationChunk.curingReduction(1_440_000, 1) == 16_941, "One singularity rounds down whole ticks");
        check(CriticalFoundationChunk.curingReduction(1, Long.MAX_VALUE) == 0, "Fractional ticks cannot finish for free");
        check(CriticalFoundationChunk.curingReduction(1_440_000, Long.MAX_VALUE) == 287_999, "Huge counts avoid overflow and remain below twenty percent");
        check(CriticalFoundationChunk.curingReduction(1_440_000, 0) == 0, "Empty inputs do not shorten curing");
        int remaining = chunk.curingRemaining();
        chunk.accelerate(16);
        check(chunk.curingRemaining() == remaining - remaining / 10, "Reduction uses current remaining time");
        saved = new NBTTagCompound(); reloaded.writeToNBT(saved);
        world = new CriticalWorldData(); world.readFromNBT(saved);
        chunk = world.foundation(-2, 7);
        check(chunk.poured() && chunk.curingRemaining() == remaining - remaining / 10, "Curing and negative coordinates round-trip");
        world.release("unrelated-machine");
        check(world.foundation(-2, 7) == chunk, "Removing a machine never discards shared foundation work");
        chunk.lockFunding();
        remaining = chunk.curingRemaining(); chunk.cure(tick++); chunk.accelerate(16);
        check(chunk.curingRemaining() == remaining, "An uncertain debit locks every sharing loom");
        NBTTagCompound complete = new NBTTagCompound();
        complete.setInteger("cells", 16); complete.setInteger("curing", 0);
        chunk.read(complete);
        check(chunk.complete(), "A completed chunk remains reusable without payment");
        System.out.println("PASS CriticalFoundationContract checks=" + checks);
    }
}
