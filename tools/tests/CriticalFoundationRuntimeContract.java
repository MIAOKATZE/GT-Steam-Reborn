package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;

/** Real State.tickFoundation with one shared real world ledger; no FML/game startup. */
public final class CriticalFoundationRuntimeContract {
    private static sun.misc.Unsafe unsafe;
    private static Method tick;
    private static int checks;
    private static final long UHV = gregtech.api.enums.GTValues.V[9];

    private static void check(boolean value, String text) {
        if (!value) throw new AssertionError(text);
        checks++;
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = CriticalMachineState.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static CriticalMachineState state(ClockWorld world) throws Exception {
        CriticalMachineState state = new CriticalMachineState();
        state.worldObj = world;
        state.setLoomController(true);
        field(state, "foundationRequired", true);
        field(state, "state", 1);
        field(state, "debugRequirements", 3);
        field(state, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(-2, 7)));
        return state;
    }

    private static void advance(CriticalMachineState state, ClockWorld world) throws Exception {
        world.time++;
        tick.invoke(state);
    }

    public static void main(String[] args) throws Exception {
        CriticalFoundationContract.source(CriticalMachineState.class, args);
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        unsafe = (sun.misc.Unsafe) access.get(null);
        tick = CriticalMachineState.class.getDeclaredMethod("tickFoundation");
        tick.setAccessible(true);
        ClockWorld world = (ClockWorld) unsafe.allocateInstance(ClockWorld.class);
        Field storage = World.class.getDeclaredField("perWorldStorage");
        storage.setAccessible(true);
        storage.set(world, new MapStorage(null));
        CriticalMachineState state = state(world);
        field(state, "energy", UHV * 50);
        for (int i = 0; i < 50; i++) advance(state, world);
        check(state.getFoundationCellProgressTicks() == 50 && state.getEnergy() == 0, "UHV baseline pays exactly fifty ticks");
        advance(state, world);
        check(state.getFoundationCellProgressTicks() == 50 && state.isFoundationCellFunded(), "Power loss preserves prepaid cell progress");
        NBTTagCompound saved = new NBTTagCompound();
        state.writeToNBT(saved);
        CriticalMachineState restored = state(world);
        restored.readFromNBT(saved);
        field(restored, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(-2, 7)));
        check(restored.isFoundationRequired() && restored.isFoundationCellFunded(), "Controller reload preserves foundation request and shared debit");
        field(restored, "energy", UHV * 350);
        for (int i = 0; i < 350; i++) advance(restored, world);
        check(restored.getFoundationCompletedCells() == 1 && restored.getEnergy() == 0, "Reload resumes remaining work without recharging fifty ticks");
        check(state.getFoundationCompletedCells() == 1, "Second loom sees first loom's completed cell immediately");
        field(restored, "debugSpeed", 3);
        for (int i = 1; i < 16; i++) {
            field(restored, "energy", UHV * 400);
            advance(restored, world);
        }
        check(restored.getFoundationPhase() == 2 && restored.getConstructionDurationTicks() == 1_440_000,
            "Sixteen cells enter fixed-duration curing phase=" + restored.getFoundationPhase()
                + " duration=" + restored.getConstructionDurationTicks() + " cells=" + restored.getFoundationCompletedCells());
        field(restored, "energy", UHV * 400);
        for (int i = 0; i < 400; i++) advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == 1_439_600, "Debug construction speed never accelerates curing");
        check(restored.getEnergy() == 0, "Curing spends exactly UHV one ampere per tick");
        Field cadence = CriticalMachineState.class.getDeclaredField("foundationCuringCheckTicks");
        cadence.setAccessible(true);
        check(cadence.getInt(restored) == 0, "The fourth hundred powered tick triggers and resets singularity inspection");
        int remaining = restored.getFoundationCuringRemainingTicks();
        advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == remaining && cadence.getInt(restored) == 0, "Unpowered curing neither advances nor triggers inspection");
        field(restored, "energy", UHV);
        advance(restored, world);
        field(state, "energy", UHV);
        tick.invoke(state);
        check(state.getEnergy() == UHV && state.getFoundationCuringRemainingTicks() == remaining - 1, "A second loom cannot double-cure a chunk or spend power in the same tick");
        saved = new NBTTagCompound();
        restored.writeToNBT(saved);
        CriticalMachineState legacy = state(world);
        saved.removeTag("foundationRequired");
        legacy.readFromNBT(saved);
        check(!legacy.isFoundationRequired(), "Old saved construction journals do not acquire a foundation requirement");
        System.out.println("PASS CriticalFoundationRuntimeContract checks=" + checks);
    }

    private static final class ClockWorld extends World {
        private long time;
        private ClockWorld() { super(null, "unused", (WorldProvider) null, null, null); }
        @Override public long getTotalWorldTime() { return time; }
        @Override public void markBlockForUpdate(int x, int y, int z) {}
        @Override protected IChunkProvider createChunkProvider() { throw new UnsupportedOperationException(); }
        @Override protected int func_152379_p() { throw new UnsupportedOperationException(); }
        @Override public Entity getEntityByID(int id) { throw new UnsupportedOperationException(); }
    }
}
