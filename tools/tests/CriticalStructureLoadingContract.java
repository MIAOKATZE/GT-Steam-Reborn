package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

/** Uses preexisting APIs so the same regression can run against the released v2.0.17 jar. */
public final class CriticalStructureLoadingContract {
    private static void ticks(CriticalMachineState state, int ticks) throws Exception {
        for (int tick = 0; tick < ticks; tick++) {
            state.tickStructureMonitor();
            check(monitor(state).getLastChecks() <= 1024, "Recovery scan obeys its hard per-tick position budget");
        }
    }
    private static void stellarTick(CriticalMachineState state) throws Exception {
        Method method = CriticalMachineState.class.getDeclaredMethod("tickStellarSession");
        method.setAccessible(true); method.invoke(state);
    }

    private static NBTTagCompound save(CriticalMachineState state) {
        NBTTagCompound n = new NBTTagCompound(); state.writeToNBT(n); return n;
    }

    private static void monitorTransitions() throws Exception {
        CriticalStructureMonitor monitor = new CriticalStructureMonitor();
        monitor.primeConstruction(12000);
        monitor.tick(12000, index -> false);
        Method damage = CriticalStructureMonitor.class.getMethod("hasKnownDamage");
        Method status = CriticalStructureMonitor.class.getMethod("getStatus");
        Method unloaded = CriticalStructureMonitor.class.getMethod("pauseUnloaded");
        check((Boolean) damage.invoke(monitor), "Loaded mismatch records true damage");
        unloaded.invoke(monitor);
        check(status.invoke(monitor).toString().equals("UNLOADED") && (Boolean) damage.invoke(monitor),
            "Unloaded state never forgets a previously witnessed real mismatch");
        java.util.BitSet visited = new java.util.BitSet();
        boolean valid = false;
        for (int tick = 0; tick < 6000 && !valid; tick++) {
            valid = monitor.tick(12000, index -> { visited.set(index); return true; });
            check(valid == (visited.cardinality() == 12000), "Only the complete new clean sweep clears known damage");
        }
        check(valid && !(Boolean) damage.invoke(monitor), "Full clean revalidation recovers from damage plus unload");
        long rounds = monitor.getCompletedRounds();
        for (int tick = 0; tick < 5999; tick++) {
            check(monitor.tick(12000, index -> true), "Background check preserves a validated structure");
            check(monitor.getLastChecks() <= 128, "Background scan keeps its original small per-tick budget");
        }
        check(monitor.getCompletedRounds() == rounds, "Periodic cadence remains 6000 ticks");
        monitor.tick(12000, index -> true);
        check(monitor.getCompletedRounds() == rounds + 1, "Periodic round completes at exactly 6000 ticks");
        CriticalStructureMonitor huge = new CriticalStructureMonitor();
        for (int tick = 0; tick < 32; tick++) {
            check(!huge.tick(900000, index -> true) && huge.getLastChecks() <= 1024,
                "Huge recovery is bounded and cannot authorize a partial scan");
        }
    }

    public static void main(String[] args) throws Exception {
        for (Class<?> type : new Class<?>[] { CriticalMachineState.class, CriticalStructureMonitor.class,
            MTECriticalMachine.class, CriticalStellarRuntime.class }) CriticalFoundationContract.source(type, args);
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true); unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        materials();
        for (CriticalMachineKind kind : new CriticalMachineKind[] { CriticalMachineKind.SOLAR, CriticalMachineKind.TURBINE,
            CriticalMachineKind.BATTERY, CriticalMachineKind.SUN }) {
            CriticalMachineState sized = state(world(), 31, ForgeDirection.NORTH);
            set(sized, "config", new CriticalConfiguration(CriticalTier.T3, kind, 0, 0, 0));
            set(sized, "geometryCache", null);
            int count = geometry(sized).size() + CriticalGeometry.optionalWorkPorts(sized).size();
            System.out.println("SIZE " + kind + "=" + count + " initial1024ticks=" + ((count + 1023) / 1024));
        }
        SparseWorld world = world();
        CriticalMachineState state = state(world, 31, ForgeDirection.NORTH);
        fill(state);
        MTECriticalMachine machine = CriticalV32Contract.attach(state);
        CriticalV32Contract.deep(machine.getBaseMetaTileEntity(), "mReturnedCoordinates")
            .set(machine.getBaseMetaTileEntity(), new net.minecraft.util.ChunkCoordinates());
        machine.getBaseMetaTileEntity().enableWorking();
        CriticalConstructionOwnershipContract.coverage(world, state);
        CriticalWorldData data = CriticalWorldData.get(world);
        check(data.acquire(state.getStructureId(), "owner", state.getOriginX(), state.getOriginY(), state.getOriginZ()),
            "Real region owner acquired");
        check(data.bindTask(state.getStructureId(), "owner", state.getJobId(), state.getOriginX(), state.getOriginY(), state.getOriginZ()),
            "Real saved work journal identity bound");
        check(machine.forceLoomStructureCheck(), "Complete manual loom passes native structure validation");
        NBTTagCompound star = (NBTTagCompound) field(state, "stellarRuntime");
        star.setInteger("schema", CriticalStellarRuntime.SCHEMA);
        star.setInteger("billingVersion", CriticalStellarPolicy.VERSION);
        star.setString("structure", state.getStructureId());
        star.setString("host", machine.criticalStellarPowerPort().identity());
        star.setBoolean("hot", true);
        star.setLong("ignitionPaid", 91);
        star.setLong("ignitionHelium3Paid", 23);
        star.setLong("processingPaid", 37);
        star.setBoolean("tickAuthorized", true);
        star.setBoolean("sustainPaidThisTick", true);
        set(state, "batchId", "funded-before-restart");
        ((NBTTagCompound) field(state, "batchRecipe")).setInteger("billingVersion", 2);
        set(state, "cycleTicks", 17); set(state, "cycleDuration", 400);
        set(state, "pendingGenerated", 33L);
        NBTTagCompound saved = save(state);
        CriticalMachineState beforeFirstTick = new CriticalMachineState();
        beforeFirstTick.readFromNBT(saved);
        check(beforeFirstTick.isLoomJournalOwner() && beforeFirstTick.isChunkLoadingEnabled()
            && beforeFirstTick.getWorldObj() == null,
            "Ordered Forge reload predicates recognize the saved journal before host binding or first tick");
        state.readFromNBT(saved);
        set(state, "jobFingerprint", field(state, "modelFingerprint"));
        check(!state.isStructureValidated(), "NBT formed cache never grants execution authority");
        Method owns = CriticalMachineState.class.getDeclaredMethod("ownsTask"); owns.setAccessible(true);
        Method loaded = CriticalMachineState.class.getDeclaredMethod("areaLoaded"); loaded.setAccessible(true);
        System.out.println("PRECHECK kind=" + state.getKind() + " state=" + state.getState() + " owns=" + owns.invoke(state)
            + " loaded=" + loaded.invoke(state) + " fingerprint=" + field(state, "jobFingerprint") + "/" + field(state, "modelFingerprint")
            + " host=" + state.getHost());
        star = (NBTTagCompound) field(state, "stellarRuntime");
        stellarTick(state);
        System.out.println("RESTART hot=" + star.getBoolean("hot") + " ignitionPaid=" + star.getLong("ignitionPaid")
            + " heliumPaid=" + star.getLong("ignitionHelium3Paid") + " processingPaid=" + star.getLong("processingPaid")
            + " refund=" + star.getLong("refund"));
        check(star.getBoolean("hot") && star.getLong("ignitionPaid") == 91 && star.getLong("processingPaid") == 37
            && star.getLong("ignitionHelium3Paid") == 23 && star.getLong("refund") == 0,
            "Reload pending must preserve the saved hot star, prepaid EU/He3 and avoid shutdown/refund");
        check(!star.getBoolean("tickAuthorized") && !star.getBoolean("sustainPaidThisTick"),
            "Pending validation cannot reuse previous-tick energy authorization");
        check(state.getBatchProgress() == 17 && state.getJobId().equals("paid-partial")
            && field(state, "pendingGenerated").equals(33L), "Pending scan preserves exact batch, order and pending energy");
        state.updateEntity();
        check(state.getReasonCode() == 3 && !state.isWorking() && state.getBatchProgress() == 17,
            "Real updateEntity reports loading and pauses funded work: " + state.getStatus());
        ticks(state, 1);
        world.loaded = false;
        for (int tick = 0; tick < 2401; tick++) state.tickStructureMonitor();
        stellarTick(state);
        check(!state.isStructureValidated() && star.getBoolean("hot") && star.getLong("refund") == 0,
            "Long chunk IO cannot become a true broken structure or refund the star");
        check(world.unloadedReads == 0, "Neither monitor nor pending star reads unloaded world blocks");
        world.loaded = true;
        Set<ChunkCoordIntPair> missing = new HashSet<>();
        CriticalGeometry.Voxel loom = CriticalLoomStructure.voxels(state).stream()
            .filter(v -> !CriticalStructures.isController(v)).filter(v -> (state.blockWorldX(v) >> 4) != (state.xCoord >> 4)
                || (state.blockWorldZ(v) >> 4) != (state.zCoord >> 4)).findFirst().orElseThrow();
        missing.add(new ChunkCoordIntPair(state.blockWorldX(loom) >> 4, state.blockWorldZ(loom) >> 4));
        IChunkProvider original = world.chunks;
        world.chunks = (IChunkProvider) Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(), new Class<?>[] { IChunkProvider.class },
            (proxy, method, values) -> method.getName().equals("chunkExists")
                ? !missing.contains(new ChunkCoordIntPair((Integer) values[0], (Integer) values[1])) : method.invoke(original, values));
        state.readFromNBT(save(state));
        star = (NBTTagCompound) field(state, "stellarRuntime");
        check(field(state, "structureLoadTicks").equals(0), "Only a fresh NBT load resets the cumulative waiting budget");
        check(!machine.forceLoomStructureCheck(), "Loom chunks still loading cannot form");
        world.totalWorldTime = 100;
        for (int repeat = 0; repeat < 20; repeat++) state.updateEntity();
        check(field(state, "structureLoadTicks").equals(1), "Repeated same-world-tick calls spend only one waiting tick");
        ChunkCoordIntPair waitingChunk = missing.iterator().next();
        missing.clear(); world.totalWorldTime = 10000; state.updateEntity();
        check(field(state, "structureLoadTicks").equals(1), "A long ready interval neither consumes nor renews loading grace");
        missing.add(waitingChunk); world.totalWorldTime = 20000; state.updateEntity();
        check(field(state, "structureLoadTicks").equals(2), "A later missing chunk spends one tick, excluding the ready interval");
        for (int tick = 0; tick < 2399; tick++) { world.totalWorldTime++; state.updateEntity(); }
        check(state.getStatus().contains("加载等待超时") && field(state, "structureLoadTicks").equals(2400),
            "Load recovery has a finite deadline that retries cannot extend");
        check(star.getBoolean("hot") && star.getLong("ignitionPaid") == 91 && state.getBatchProgress() == 17,
            "Expired loading grace still protects the saved assets and pauses paid work");
        missing.clear();
        check(machine.isLoomStructureValid(), "Real chunk readiness bypasses a cached pending loom result");
        ticks(state, 1);
        check(!state.isStructureValidated(), "Chunks restored after partial scan require a fresh full scan");
        int recoveryTicks = 1;
        while (!state.isStructureValidated() && recoveryTicks < 6000) { ticks(state, 1); recoveryTicks++; }
        check(state.isStructureValidated() && recoveryTicks < 6000, "Fresh full recovery scan finishes without waiting for periodic cadence");
        System.out.println("RECOVERY scanned=" + (geometry(state).size() + CriticalGeometry.optionalWorkPorts(state).size())
            + " ticks=" + recoveryTicks);
        CriticalGeometry.Voxel target = geometry(state).stream()
            .filter(v -> !CriticalLoomStructure.isLoomVoxel(v) && !CriticalStructures.isController(v)).findFirst().orElseThrow();
        world.blocks.remove(key(state.blockWorldX(target), state.blockWorldY(target.y), state.blockWorldZ(target)));
        ticks(state, 6000);
        check(!state.isStructureValidated(), "Actual loaded target damage still revokes authorization");
        stellarTick(state);
        check(!star.getBoolean("hot"), "True loaded target damage still shuts down the star");
        put(state, target, target.block);
        ticks(state, 6000);
        check(state.isStructureValidated(), "Manual repair recovers only through a wholly clean scan");
        world.blocks.remove(key(state.blockWorldX(loom), state.blockWorldY(loom.y), state.blockWorldZ(loom)));
        check(!machine.forceLoomStructureCheck(), "True loaded loom damage is never excused by loading grace");
        monitorTransitions();
        state.onChunkUnload();
        check(field(state, "structureLoadTicks").equals(0)
            && field(state, "lastStructureLoadingTick").equals(Long.MIN_VALUE), "Chunk unload starts a fresh future loading budget");
        System.out.println("PASS CriticalStructureLoadingContract checks=" + checks);
    }
}
