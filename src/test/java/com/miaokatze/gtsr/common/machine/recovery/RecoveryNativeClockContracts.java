// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.recovery;

import java.lang.reflect.Method;
import java.util.ArrayList;

import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitCrusher;

/** Persistence and owner-scope contract; native transformed tick behavior is covered by the runtime fixtures. */
public final class RecoveryNativeClockContracts {

    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, RecoveryNativeClockContracts.class.getName(), args)) return;
        Method setup = Class.forName("com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts")
            .getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        Machine machine = new Machine();
        check(GtsrStructureRecovery.applies(machine), "GTSR ancestor remains in scope for a fixture subclass");
        check(GtsrStructureRecovery.graceTicks(machine) == 200, "ordinary budget");
        check(GtsrStructureRecovery.graceTicks(new Heavy()) == 600, "large ancestor budget");
        check(GtsrStructureRecovery.graceTicks(new Mega()) == 2400, "mega ancestor budget");
        GtsrStructureRecovery.State state = machine.gtsr$getStructureRecovery();
        state.load(machine, new NBTTagCompound());
        check(
            machine.mStartUpCheck == 200 && machine.mUpdate == 1,
            "fresh reload uses native startup and event clocks");
        check(GtsrStructureRecovery.isRecovering(machine), "pending reload is protected");
        machine.mStartUpCheck = 37;
        machine.mUpdate = 17;
        NBTTagCompound saved = new NBTTagCompound();
        state.save(machine, saved);
        state.load(machine, saved);
        check(machine.mStartUpCheck == 37, "save and load never renew a pending budget");
        check(
            machine.mUpdate == 17,
            "short sessions preserve the native event phase rather than skipping startup forever");
        state.save(machine, saved);
        state.load(machine, saved);
        check(machine.mStartUpCheck == 37, "repeated save and load retain the same remaining native clock");
        machine.mStartUpCheck = -1;
        state.save(machine, saved);
        state.load(machine, saved);
        check(!GtsrStructureRecovery.isRecovering(machine), "exhaustion cannot be renewed by reopening the world");
        check(state.phase == GtsrStructureRecovery.State.EXPIRED, "exhaustion is explicit in NBT");
        state.phase = GtsrStructureRecovery.State.VERIFIED;
        state.active = false;
        state.save(machine, saved);
        state.load(machine, saved);
        check(machine.mStartUpCheck == 200, "a successfully verified machine gets a new reload budget");
        GtsrStructureRecovery.confirmedDamage(machine);
        check(
            !GtsrStructureRecovery.isRecovering(machine) && machine.mStartUpCheck < 0,
            "positive damage revokes protection immediately");
        state.save(machine, saved);
        state.load(machine, saved);
        check(
            state.phase == GtsrStructureRecovery.State.DAMAGED && !state.active,
            "damage cannot be hidden by reopening the world");
        GtsrStructureRecovery.markUnknown(machine);
        check(
            !state.active && state.phase == GtsrStructureRecovery.State.DAMAGED,
            "damage cannot open another unknown window");
        state.phase = GtsrStructureRecovery.State.EXPIRED;
        GtsrStructureRecovery.markUnknown(machine);
        check(
            !state.active && state.phase == GtsrStructureRecovery.State.EXPIRED,
            "expired recovery cannot renew on another unknown sample");
        check(
            GtsrStructureRecovery.isUnknownStructurePause(machine) && !GtsrStructureRecovery.isRecovering(machine),
            "unknown paid work remains paused after the independent heat protection expires");
        GtsrStructureRecovery.confirmedDamage(machine);
        check(
            !GtsrStructureRecovery.isUnknownStructurePause(machine),
            "confirmed damage cannot suppress native structural shutdown");
        state.phase = GtsrStructureRecovery.State.VERIFIED;
        state.active = false;
        machine.mStartUpCheck = -10;
        machine.mMachine = false;
        state.beginCheck();
        state.checkErrors = new ArrayList<>();
        state.beginPiece(new ArrayList<>(), false);
        state.unknownPiece(machine);
        check(state.finishPiece(), "an unknown auxiliary shape returns false locally");
        check(
            !state.unknown && !state.active && machine.mStartUpCheck == -10,
            "a discarded auxiliary error list cannot pause a verified machine");
        state.beginPiece(state.checkErrors, false);
        state.unknownPiece(machine);
        check(
            state.finishPiece() && state.unknown && state.active,
            "the formal piece opens recovery from a verified verdict");
        check(
            machine.mStartUpCheck == 200 && !machine.mMachine,
            "runtime recovery uses native startup without granting structure validity");
        machine.mStartUpCheck = 91;
        GtsrStructureRecovery.markUnknown(machine);
        check(machine.mStartUpCheck == 91, "repeated unknown samples cannot renew a runtime window");
        state.save(machine, saved);
        state.load(machine, saved);
        check(machine.mStartUpCheck == 91, "a runtime recovery window survives save and load without renewal");
        state.beginCheck();
        state.beginPiece(null, true);
        state.unknownPiece(machine);
        check(
            state.finishPiece() && state.unknown,
            "legacy pieces inside a complete transaction are explicitly formal");
        state.checking = false;
        state.unknown = false;
        state.beginPiece(null, true);
        state.unknownPiece(machine);
        check(state.finishPiece() && !state.unknown, "direct legacy probes have only a local unknown result");
        System.out.println("PASS native recovery persistence checks=" + checks);
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        checks++;
    }

    public static final class Machine extends MTEUnitCrusher implements IGtsrStructureRecovery {

        private final GtsrStructureRecovery.State recovery = new GtsrStructureRecovery.State();

        public Machine() {
            super("recovery_native_contract");
        }

        @Override
        public GtsrStructureRecovery.State gtsr$getStructureRecovery() {
            return recovery;
        }
    }

    private static final class Heavy extends MTEHeavySteamTurbine {

        private Heavy() {
            super("recovery_heavy_contract");
        }
    }

    private static final class Mega extends MTEMegaSteamTurbineArray {

        private Mega() {
            super("recovery_mega_contract");
        }
    }
}
