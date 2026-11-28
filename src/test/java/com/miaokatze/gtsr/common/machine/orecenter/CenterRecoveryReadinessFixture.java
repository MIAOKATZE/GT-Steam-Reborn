// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import static com.miaokatze.gtsr.common.machine.orecenter.CenterRecoveryContracts.*;

import java.lang.ref.WeakReference;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.critical.CriticalStructureMonitor;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Frame;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Shape;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Deterministic recovery and reference-lifecycle checks; no GC timing or real chunk IO is involved. */
final class CenterRecoveryReadinessFixture {

    static void run() throws Exception {
        paidVersusNewAndOfflineDamage();
        newConstructionAndInstallation();
        knownModuleDamage();
        unloadAndDetach();
        result.put(
            "readinessLifecycle",
            "full-sweep debit gate, offline remote damage, persisted review obligations, weak scan queue, detach and world-unload claims preserved");
    }

    private static void newConstructionAndInstallation() throws Exception {
        SparseWorld world = world();
        IGregTechTileEntity te = tile(world, ForgeDirection.NORTH);
        CenterStructure structure = ready(te);
        set(structure, "formedSectors", 1);
        Frame frame = new Frame(te);
        Shape body = CenterStructure.optionalShape("tower-3"), wing = CenterStructure.optionalShape("sector-0");
        fill(world, frame, body);
        fill(world, frame, wing);
        java.lang.reflect.Method prime = CriticalStructureMonitor.class
            .getDeclaredMethod("primeConstruction", int.class);
        prime.setAccessible(true);
        CriticalStructureMonitor tower = (CriticalStructureMonitor) get(structure, "tower");
        CriticalStructureMonitor sector = ((CriticalStructureMonitor[]) get(structure, "sectors"))[0];
        prime.invoke(tower, body.count());
        prime.invoke(sector, wing.count());
        check(
            tower.getCompletedRounds() == 0 && sector.getCompletedRounds() == 0,
            "native paid construction has zero background monitor rounds");
        check(
            structure.isNewBatchReady(te),
            "just-verified construction does not wait 6000 ticks for a redundant monitor round");
        check(
            structure.install(te, 0, CenterModule.SPEED, 1),
            "real installation succeeds on the newly verified structure");
        check(!structure.isNewBatchReady(te), "an installed module still requires its original initial validation");
        Shape module = shape(0, CenterModule.SPEED, 1);
        int slices = 0;
        while (slices < 1000 && !structure.isNewBatchReady(te)) {
            scan(structure, te, 0, module);
            slices++;
        }
        check(
            structure.isNewBatchReady(te) && slices < 1000,
            "real module installation becomes eligible after its fast validation without the 6000-tick steady cadence");
        prime.invoke(moduleMonitor(structure, 0), module.count());
        check(
            moduleMonitor(structure, 0).getCompletedRounds() == 0 && structure.isNewBatchReady(te),
            "a voxel-verified module's zero-round native construction state also authorizes new work");
        CenterClaims.unload(world);
    }

    private static NBTTagCompound snapshot(SparseWorld world, IGregTechTileEntity te) throws Exception {
        CenterStructure structure = ready(te);
        set(structure, "serviceFront", true);
        set(structure, "formedSectors", 1);
        Frame frame = new Frame(te);
        Shape body = CenterStructure.optionalShape("tower-service-3");
        Shape wing = CenterStructure.optionalShape("sector-0");
        fill(world, frame, body);
        fill(world, frame, wing);
        java.lang.reflect.Method bind = CenterStructure.class.getDeclaredMethod("bindPortIdentity", Frame.class);
        bind.setAccessible(true);
        bind.invoke(structure, frame);
        set(structure, "portIndexReady", true);
        ((CriticalStructureMonitor[]) get(structure, "sectors"))[0].restoreValidation(wing.count());
        NBTTagCompound tag = new NBTTagCompound();
        structure.save(tag, te);
        return tag;
    }

    private static void paidVersusNewAndOfflineDamage() throws Exception {
        SparseWorld world = world();
        IGregTechTileEntity te = tile(world, ForgeDirection.NORTH);
        NBTTagCompound tag = snapshot(world, te);
        CenterStructure structure = new CenterStructure();
        structure.load(tag);
        world.now++;
        structure.tick(te);
        check(structure.isExecutionReady(te), "cached paid work retains its ready-chunk grace");
        check(!structure.isNewBatchReady(te), "cached shape cannot authorize a new debit during the full sweep");
        for (int tick = 0; tick < 4000 && !structure.isNewBatchReady(te); tick++) {
            world.now++;
            structure.tick(te);
        }
        check(structure.isNewBatchReady(te), "all dependencies become eligible after their native full sweeps");
        Shape wing = CenterStructure.optionalShape("sector-0");
        Frame frame = new Frame(te);
        long remoteChunk = chunk(
            frame.x(wing.x[wing.count() - 1], wing.z[wing.count() - 1] + 19) >> 4,
            frame.z(wing.x[wing.count() - 1], wing.z[wing.count() - 1] + 19) >> 4);
        world.missing.add(remoteChunk);
        check(
            !structure.isExecutionReady(te) && !structure.isNewBatchReady(te),
            "current chunk readiness gates both paid execution and new debit");
        world.missing.clear();
        check(structure.isNewBatchReady(te), "a readiness query does not discard completed verification");
        // A block far beyond the marker is changed while the controller is offline.
        int damaged = wing.count() - 1;
        int x = frame.x(wing.x[damaged], wing.z[damaged] + 19);
        int y = frame.y + wing.y[damaged] - 2;
        int z = frame.z(wing.x[damaged], wing.z[damaged] + 19);
        world.cells.put(key(x, y, z), new Cell(Blocks.air, 0));
        CenterStructure broken = new CenterStructure();
        broken.load(tag);
        world.now++;
        broken.tick(te);
        check(!broken.isNewBatchReady(te), "offline non-marker damage cannot slip through cached formation");
        CriticalStructureMonitor monitor = ((CriticalStructureMonitor[]) get(broken, "sectors"))[0];
        for (int tick = 0; tick < 4000 && !monitor.hasKnownDamage(); tick++) {
            world.now++;
            broken.tick(te);
            check(!broken.isNewBatchReady(te), "no new debit before a remote dependency completes review");
        }
        check(monitor.hasKnownDamage(), "existing background sweep detects offline non-first-voxel damage");
        check(!broken.isExecutionReady(te), "known remote damage immediately suspends cached paid execution");
        // A marker loss revokes lastFormation immediately, but cannot erase the full-review obligation.
        int mx = frame.x(wing.x[0], wing.z[0] + 19), my = frame.y + wing.y[0] - 2;
        int mz = frame.z(wing.x[0], wing.z[0] + 19);
        world.cells.put(key(mx, my, mz), new Cell(Blocks.air, 0));
        CenterStructure missing = new CenterStructure();
        missing.load(tag);
        world.now++;
        missing.tick(te);
        check(((Integer) get(missing, "reviewSectors") & 1) != 0, "missing marker retains saved-sector review");
        NBTTagCompound again = new NBTTagCompound();
        missing.save(again, te);
        CenterStructure twice = new CenterStructure();
        twice.load(again);
        check(
            ((Integer) get(twice, "reviewSectors") & 1) != 0,
            "review survives a second reload after saved-sector revocation");
        check(!twice.isNewBatchReady(te), "second reload cannot bypass missing-sector review");
        // Finish the same monitor, then repair: only a complete clean round can restore the wing.
        world.cells.put(key(mx, my, mz), new Cell(wing.block(0), wing.metadata(0)));
        world.cells.put(key(x, y, z), new Cell(wing.block(damaged), wing.metadata(damaged)));
        for (int tick = 0; tick < 7000 && !twice.isNewBatchReady(te); tick++) {
            world.now++;
            twice.tick(te);
        }
        check(twice.isNewBatchReady(te), "repaired dependency recovers through the existing full sweep");
        check(world.unloadedReads == 0, "readiness fixture never reads an unloaded chunk");
    }

    private static void knownModuleDamage() throws Exception {
        SparseWorld world = world();
        IGregTechTileEntity te = tile(world, ForgeDirection.NORTH);
        NBTTagCompound saved = snapshot(world, te);
        CenterStructure structure = new CenterStructure();
        structure.load(saved);
        world.now++;
        structure.tick(te);
        check(structure.install(te, 0, CenterModule.SPEED, 1), "fixture installs a real claimed module");
        CriticalStructureMonitor monitor = moduleMonitor(structure, 0);
        monitor.restoreValidation(shape(0, CenterModule.SPEED, 1).count());
        structure.invalidateModule(0);
        check(monitor.hasKnownDamage(), "module damage is recorded by the native monitor");
        check(
            !structure.isExecutionReady(te) && !structure.isNewBatchReady(te),
            "known module damage cannot use cached paid/new authorization while review is pending");
        NBTTagCompound again = new NBTTagCompound();
        structure.save(again, te);
        CenterStructure loaded = new CenterStructure();
        loaded.load(again);
        check(((Long) get(loaded, "reviewModules") & 1L) != 0, "known damaged-module review survives NBT reload");
        check(loaded.moduleBroken(0, CenterModule.SPEED, 1), "known module damage survives NBT reload");
        CenterClaims.unload(world);
    }

    private static void unloadAndDetach() throws Exception {
        SparseWorld world = world(), otherWorld = world();
        IGregTechTileEntity te = tile(world, ForgeDirection.NORTH);
        CenterStructure first = ready(te), second = ready(te);
        CenterClaims claims = CenterClaims.get(world), other = CenterClaims.get(otherWorld);
        Frame frame = new Frame(te);
        Shape module = shape(0, CenterModule.SPEED, 1);
        claims.claim(frame, 0, "module-0-speed-1", module);
        claims.attach(frame, 0, first);
        claims.track(frame, first);
        claims.countScan(2_000_000L);
        check(claims.allowsScan(first, 10), "first tick starts with fresh global scan budget");
        claims.countScan(2_000_000L);
        check(!claims.allowsScan(first, 10), "exhausted budget queues first controller");
        check(!claims.allowsScan(second, 10), "same-world peer waits fairly behind first");
        Map<?, ?> waiters = (Map<?, ?>) get(claims, "waitingScans");
        check(
            waiters.keySet()
                .iterator()
                .next() instanceof WeakReference,
            "waiting queue cannot root a structure/world through its weak-key world cache");
        NBTTagCompound before = new NBTTagCompound();
        claims.writeToNBT(before);
        set(first, "footprintWorld", world);
        first.detach(te);
        check(get(first, "footprintWorld") == null, "controller detach releases cached World");
        check(waiters.size() == 1, "detach removes only the departing controller's queue entry");
        check(claims.allowsScan(second, 11), "remaining center retains its next fair scan slice");
        world.now = 12;
        first.tick(te);
        boolean trackedAgain = false;
        for (Object reference : ((Map<?, ?>) get(claims, "centers")).values())
            trackedAgain |= ((WeakReference<?>) reference).get() == first;
        check(trackedAgain, "same controller object reattaches its event subscription without a facing change");
        first.detach(te);
        NBTTagCompound detached = new NBTTagCompound();
        claims.writeToNBT(detached);
        check(before.equals(detached), "detach preserves exact persistent generated-voxel claims");
        for (Object claim : ((Map<?, ?>) get(claims, "owners")).values())
            check(get(claim, "source") == null, "detach clears only the claim's disposable controller source");
        claims.track(frame, second);
        set(second, "footprintWorld", world);
        claims.countScan(2_000_000L);
        check(!claims.allowsScan(second, 12), "world unload begins with a denied scan queued");
        CenterClaims.unload(world);
        check(
            waiters.isEmpty() && ((Map<?, ?>) get(claims, "centers")).isEmpty(),
            "world unload clears queue and controller index");
        check(get(second, "footprintWorld") == null, "world unload releases queued controller's cached World");
        for (Object claim : ((Map<?, ?>) get(claims, "owners")).values())
            check(get(claim, "source") == null, "world unload clears claim runtime sources");
        java.lang.reflect.Field worlds = CenterClaims.class.getDeclaredField("WORLDS");
        worlds.setAccessible(true);
        check(
            !((Map<?, ?>) worlds.get(null)).containsKey(world),
            "unloaded world is removed from the weak-key runtime cache");
        NBTTagCompound after = new NBTTagCompound();
        claims.writeToNBT(after);
        check(before.equals(after), "world unload leaves unsaved persistent claims intact");
        check(CenterClaims.get(world) == claims, "reload uses the same WorldSavedData with claims retained");
        check(CenterClaims.get(otherWorld) == other, "world unload does not disturb another world's runtime cache");
        CenterClaims.unload(world);
        CenterClaims.unload(otherWorld);
    }
}
