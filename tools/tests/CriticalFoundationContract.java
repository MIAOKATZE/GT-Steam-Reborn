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
        chunkSchedule();
        CriticalWorldData world = new CriticalWorldData();
        CriticalFoundationChunk chunk = world.foundation(-2, 7);
        chunk.useLegacySchedule();
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

    private static void chunkSchedule() {
        CriticalFoundationChunk chunk = new CriticalFoundationChunk();
        check(chunk.scheduleVersion() == 7 && CriticalFoundationChunk.CELLS == 16 && chunk.cellDuration() == 40,
            "New chunks contain sixteen independently paid 4x4 cells at forty baseline ticks each");
        chunk.setFunded(); chunk.pour(17, 1);
        check(chunk.completedCells() == 0 && chunk.cellProgress() == 17 && chunk.funded(),
            "A partial new cell retains its own paid material receipt");
        NBTTagCompound saved = new NBTTagCompound(); chunk.write(saved);
        check(saved.getInteger("cells") == 0 && saved.getInteger("foundationVersion") == 7,
            "Version7 stores finished cells separately from current cell progress");
        CriticalFoundationChunk restored = new CriticalFoundationChunk(); restored.read(saved);
        NBTTagCompound copy = new NBTTagCompound(); restored.write(copy);
        check(copy.equals(saved) && restored.funded() && restored.cellProgress() == 17,
            "Paid partial-cell work survives NBT without another material debit");
        restored.pour(23, 2);
        check(restored.completedCells() == 1 && !restored.funded() && restored.cellProgress() == 0,
            "Exactly forty baseline work finishes one cell and requests its next payment");
        restored.pour(40, 3);
        check(restored.completedCells() == 1, "Unpaid next cells cannot advance");
        for (int cell = 1; cell < 16; cell++) { restored.setFunded(); restored.pour(40, cell + 3); }
        check(restored.poured() && restored.heatingRemaining() == 36_000 && restored.curingRemaining() == 1_728_000,
            "All sixteen new cells retain both original curing phases");
        for (int work : new int[] { 4, 16, 256 }) {
            chunk = new CriticalFoundationChunk();
            int total = 0, ticks = 0;
            while (!chunk.poured()) {
                if (!chunk.funded()) chunk.setFunded();
                int next = Math.min(work, chunk.cellDuration() - chunk.cellProgress());
                chunk.pour(next, ++ticks); total += next;
            }
            check(total == 640 && ticks == 16 * ((40 + work - 1) / work),
                "Perfect overclock preserves exactly 640 chunk work and clips each final cell tick");
        }
        saved = oldModern(0, 213, true); saved.setInteger("foundationVersion", 6);
        restored.read(saved); restored.write(copy);
        NBTTagCompound canonical = new NBTTagCompound(); chunk.read(copy); chunk.write(canonical);
        check(restored.scheduleVersion() == 6 && restored.cellDuration() == 400 && restored.completedCells() == 8
            && restored.cellProgress() == 213 && restored.funded() && copy.equals(canonical),
            "A paid v6 chunk preserves its exact full-chunk bill, 213 work and output entitlement");
        chunk = new CriticalFoundationChunk(); chunk.read(copy); chunk.pour(187, 1);
        check(chunk.poured() && !chunk.funded(), "Persisted v6 payment finishes its entire chunk without another bill");
        saved = oldModern(0, 0, false); saved.setInteger("foundationVersion", 6); restored.read(saved);
        check(restored.scheduleVersion() == 7 && restored.cellDuration() == 40 && !restored.funded(),
            "Untouched unpaid v6 chunks adopt the new per-cell schedule");
        saved = oldModern(0, 0, false); restored = new CriticalFoundationChunk(); restored.read(saved);
        check(restored.scheduleVersion() == 7 && restored.completedCells() == 0 && !restored.funded(),
            "An unpaid old modern chunk adopts the new per-cell bill");
        saved = oldModern(0, 127, true); restored.read(saved);
        check(restored.scheduleVersion() == 6 && restored.cellProgress() == 127 && restored.funded(),
            "The first paid partial old cell becomes prepaid whole-chunk progress without rebilling");
        copy = new NBTTagCompound(); restored.write(copy); chunk = new CriticalFoundationChunk(); chunk.read(copy);
        chunk.pour(273, 1);
        check(copy.getInteger("foundationVersion") == 6 && chunk.poured(),
            "Persisted migration finishes the remaining work once without acquiring a second bill");
        restored.read(oldModern(3, 127, true));
        check(restored.scheduleVersion() == 6 && restored.poured() && !restored.funded()
            && restored.heatingRemaining() == 36_000 && restored.curingRemaining() == 1_728_000,
            "One or more finished historical cells cover the full new chunk without charging remaining cells");
        saved = oldModern(16, 0, false); saved.setInteger("heating", 1234); saved.setInteger("curing", 987654);
        saved.setBoolean("heatingFunded", true); saved.setInteger("heatingPaidWork", 7); restored.read(saved);
        check(restored.scheduleVersion() == 6 && restored.poured() && restored.heatingRemaining() == 1234
            && restored.curingRemaining() == 987654 && restored.heatingPaidWork() == 7,
            "Already-poured migration preserves both curing clocks and all prepaid heating work");
        CriticalWorldData migratedWorld = new CriticalWorldData();
        net.minecraft.nbt.NBTTagList entries = new net.minecraft.nbt.NBTTagList();
        saved.setInteger("x", -2); saved.setInteger("z", 7); entries.appendTag(saved);
        NBTTagCompound worldRecord = new NBTTagCompound(); worldRecord.setTag("foundations", entries);
        migratedWorld.readFromNBT(worldRecord);
        check(migratedWorld.isDirty(), "Even already-poured migration marks world data dirty for durable version6 persistence");
        NBTTagCompound persistedWorld = new NBTTagCompound(); migratedWorld.writeToNBT(persistedWorld);
        check(persistedWorld.getTagList("foundations", 10).getCompoundTagAt(0).getInteger("foundationVersion") == 6,
            "The next world save persists the migrated economic unit exactly once");
        saved = oldModern(3, 127, true); saved.setBoolean("uncertain", true); restored.read(saved);
        restored.pour(400, 1); copy = new NBTTagCompound(); restored.write(copy);
        check(restored.scheduleVersion() == 5 && restored.completedCells() == 3 && restored.cellProgress() == 127
            && restored.funded() && restored.fundingUncertain() && copy.getInteger("foundationVersion") == 5,
            "Uncertain historical funding keeps original cell units, paid status and lock without migration");
        entries = new net.minecraft.nbt.NBTTagList(); entries.appendTag(saved);
        worldRecord.setTag("foundations", entries); migratedWorld = new CriticalWorldData(); migratedWorld.readFromNBT(worldRecord);
        check(!migratedWorld.isDirty(), "Uncertain legacy funding never receives migration credit or a clean version marker");
    }

    private static NBTTagCompound oldModern(int cells, int progress, boolean funded) {
        NBTTagCompound n = new NBTTagCompound(); n.setInteger("foundationVersion", 5);
        n.setInteger("cells", cells); n.setInteger("progress", progress); n.setBoolean("funded", funded);
        n.setInteger("heating", 36_000); n.setInteger("curing", 1_728_000); return n;
    }
}
