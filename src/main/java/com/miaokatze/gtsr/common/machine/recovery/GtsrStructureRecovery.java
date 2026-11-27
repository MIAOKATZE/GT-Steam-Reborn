// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.recovery;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.structure.error.StructureError;

/** Reload protection uses GT's startup clock; it never substitutes a cached structure verdict. */
public final class GtsrStructureRecovery {

    private static final String KEY = "GtsrStructureRecovery";

    private GtsrStructureRecovery() {}

    public static boolean applies(Object machine) {
        if (machine == null) return false;
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName()
                .startsWith("com.miaokatze.gtsr.common.machine.")) return true;
        }
        return false;
    }

    public static int graceTicks(MTEMultiBlockBase machine) {
        int result = 200;
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getSimpleName();
            if (name.equals("MTECriticalMachine") || name.equals("MTEMineralIntegrationCenter")
                || name.equals("MTEMegaSteamTurbineArray")) return 2400;
            if (name.equals("MTEHeavySteamTurbine") || name.equals("MTESpacetimeCalibration")
                || name.equals("MTESteamSingularityEntangler")
                || name.equals("MTECriticalSingularityCompressor")
                || name.equals("MTEThermoChemicalDenseSteamGenerator")
                || name.equals("MTESingularityDrillingHub")
                || name.equals("MTESteamMineralLogisticsCluster")) result = 600;
        }
        return result;
    }

    public static State state(MTEMultiBlockBase machine) {
        return machine instanceof IGtsrStructureRecovery && applies(machine)
            ? ((IGtsrStructureRecovery) machine).gtsr$getStructureRecovery()
            : null;
    }

    public static boolean isRecovering(MTEMultiBlockBase machine) {
        State state = state(machine);
        return state != null && state.active && machine.mStartUpCheck >= 0;
    }

    /** Unknown chunk/interface availability suspends paid work even after the heat grace expires. */
    public static boolean isUnknownStructurePause(MTEMultiBlockBase machine) {
        State state = state(machine);
        return state != null && state.unknown && state.phase != State.DAMAGED;
    }

    /** Custom structure/resource checks call this before returning an inconclusive result. */
    public static void markUnknown(MTEMultiBlockBase machine) {
        State state = state(machine);
        if (state == null) return;
        state.unknown = true;
        if (!state.active && state.phase == State.VERIFIED) {
            state.active = true;
            state.phase = State.PENDING;
            machine.mStartUpCheck = graceTicks(machine);
            if (machine.mUpdate <= 0) machine.mUpdate = 20;
        }
    }

    /** Only positive evidence of damage may terminate reload protection early. */
    public static void confirmedDamage(MTEMultiBlockBase machine) {
        State state = state(machine);
        if (state != null) {
            state.active = false;
            state.phase = State.DAMAGED;
        }
        if (applies(machine)) machine.mStartUpCheck = -1;
    }

    public static final class State {

        public static final int PENDING = 1, VERIFIED = 2, EXPIRED = 3, DAMAGED = 4;
        public int phase;
        public boolean active;
        public boolean unknown;
        public boolean checking;
        public List<StructureError> checkErrors;
        private final Deque<Piece> pieces = new ArrayDeque<>();

        public void beginCheck() {
            unknown = false;
            checking = true;
            checkErrors = null;
            pieces.clear();
        }

        /** A discarded auxiliary error list must not invalidate the complete machine transaction. */
        public void beginPiece(List<StructureError> errors, boolean legacy) {
            pieces.push(new Piece(checking && (legacy || errors == checkErrors)));
        }

        public void unknownPiece(MTEMultiBlockBase machine) {
            Piece piece = pieces.peek();
            if (piece == null) return;
            piece.unknown = true;
            if (piece.formal) markUnknown(machine);
        }

        public boolean finishPiece() {
            Piece piece = pieces.poll();
            return piece != null && piece.unknown;
        }

        private static final class Piece {

            final boolean formal;
            boolean unknown;

            Piece(boolean formal) {
                this.formal = formal;
            }
        }

        public void load(MTEMultiBlockBase machine, NBTTagCompound nbt) {
            NBTTagCompound saved = nbt.getCompoundTag(KEY);
            int previous = saved.getInteger("Phase");
            phase = previous == EXPIRED || previous == DAMAGED ? previous : PENDING;
            active = phase == PENDING;
            machine.mStartUpCheck = !active ? -1
                : previous == PENDING || saved.getBoolean("Pending")
                    ? Math.max(0, Math.min(graceTicks(machine), saved.getInteger("Startup")))
                    : graceTicks(machine);
            machine.mUpdate = previous == PENDING && saved.hasKey("Update")
                ? Math.max(1, Math.min(20, saved.getInteger("Update")))
                : 1;
            unknown = false;
            checking = false;
            checkErrors = null;
            pieces.clear();
        }

        public void save(MTEMultiBlockBase machine, NBTTagCompound nbt) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setInteger("Phase", active && machine.mStartUpCheck < 0 ? EXPIRED : phase);
            saved.setInteger("Startup", Math.max(0, machine.mStartUpCheck));
            saved.setInteger("Update", Math.max(1, machine.mUpdate));
            nbt.setTag(KEY, saved);
        }
    }
}
