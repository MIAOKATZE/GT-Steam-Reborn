package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.minecraft.network.PacketBuffer;
import net.minecraft.world.ChunkCoordIntPair;

import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;

/** One immutable S2C sample: the map, stage cursor and clocks always refer to the same server tick. */
final class CriticalConstructionSnapshot {

    private static final int HEADER_SIZE = 21;
    private final int[] values;

    private CriticalConstructionSnapshot(int[] values) {
        this.values = values;
    }

    /** Client bootstrap only. This sample carries no authority and is replaced by S2C. */
    static CriticalConstructionSnapshot empty() {
        int[] values = new int[HEADER_SIZE];
        values[7] = values[8] = -1;
        return new CriticalConstructionSnapshot(values);
    }

    static GenericSyncValue<CriticalConstructionSnapshot, ?> register(PanelSyncManager manager,
        CriticalMachineState state) {
        GenericSyncValue<CriticalConstructionSnapshot, ?> sync = GenericSyncValue
            .builder(CriticalConstructionSnapshot.class)
            .getter(() -> manager.isClient() ? empty() : capture(state))
            .serializer((packet, sample) -> sample.write(packet))
            .deserializer(CriticalConstructionSnapshot::read)
            .equals((a, b) -> Arrays.equals(a.values, b.values))
            .copyImmutable()
            .build();
        manager.syncValue("critical.constructionView", sync);
        return sync;
    }

    static CriticalConstructionSnapshot capture(CriticalMachineState state) {
        List<ChunkCoordIntPair> chunks = state.getFoundationChunks();
        if (chunks.size() > 4096) throw new IllegalStateException("Construction preview exceeds 4096 chunks");
        int[] values = new int[HEADER_SIZE + chunks.size() * 3];
        values[0] = state.getFoundationPhase();
        values[1] = state.getFoundationCompletedCells();
        values[2] = state.getFoundationTotalCells();
        values[3] = state.getFoundationCellProgressTicks();
        values[4] = state.getFoundationCellDurationTicks();
        values[5] = state.getFoundationCuringRemainingTicks();
        values[6] = state.getFoundationCuringDurationTicks();
        values[7] = state.getFoundationActiveChunkIndex();
        values[8] = state.getFoundationActiveCellIndex();
        values[9] = state.getStage();
        values[10] = state.getJobProgress();
        values[11] = state.getJobTotal();
        values[12] = state.getStatusCode();
        values[13] = state.getConstructionElapsedTicks();
        values[14] = state.getConstructionDurationTicks();
        values[15] = state.isFoundationRequired() ? 1 : 0;
        values[16] = state.getReasonCode();
        values[17] = state.getTier()
            .ordinal();
        values[18] = state.isEnabled() ? 1 : 0;
        values[19] = state.isFoundationCellFunded() ? 1 : 0;
        values[20] = state.isLoomJournalOwner() ? 1 : 0;
        for (int i = 0; i < chunks.size(); i++) {
            ChunkCoordIntPair chunk = chunks.get(i);
            values[HEADER_SIZE + i * 3] = chunk.chunkXPos;
            values[HEADER_SIZE + i * 3 + 1] = chunk.chunkZPos;
            values[HEADER_SIZE + i * 3 + 2] = state.getFoundationChunkCompletedCells(i);
        }
        return new CriticalConstructionSnapshot(values);
    }

    void write(PacketBuffer packet) {
        packet.writeVarIntToBuffer(chunkCount());
        for (int value : values) packet.writeInt(value);
    }

    static CriticalConstructionSnapshot read(PacketBuffer packet) throws IOException {
        int count = packet.readVarIntFromBuffer();
        if (count < 0 || count > 4096) throw new IOException("Invalid construction preview chunk count: " + count);
        int[] values = new int[HEADER_SIZE + count * 3];
        for (int i = 0; i < values.length; i++) values[i] = packet.readInt();
        return new CriticalConstructionSnapshot(values);
    }

    boolean same(CriticalConstructionSnapshot other) {
        return other != null && Arrays.equals(values, other.values);
    }

    int phase() {
        return values[0];
    }

    int completedCells() {
        return values[1];
    }

    int totalCells() {
        return values[2];
    }

    int cellTicks() {
        return values[3];
    }

    int cellDuration() {
        return values[4];
    }

    int curingRemaining() {
        return values[5];
    }

    int curingDuration() {
        return values[6];
    }

    int activeChunk() {
        return values[7];
    }

    int activeCell() {
        return values[8];
    }

    int stage() {
        return values[9];
    }

    int placed() {
        return values[10];
    }

    int total() {
        return values[11];
    }

    int status() {
        return values[12];
    }

    int constructionTicks() {
        return values[13];
    }

    int constructionDuration() {
        return values[14];
    }

    boolean foundationRequired() {
        return values[15] != 0;
    }

    int reason() {
        return values[16];
    }

    int tier() {
        return values[17];
    }

    boolean enabled() {
        return values[18] != 0;
    }

    boolean cellFunded() {
        return values[19] != 0;
    }

    boolean loomOwner() {
        return values[20] != 0;
    }

    int chunkCount() {
        return (values.length - HEADER_SIZE) / 3;
    }

    int chunkX(int index) {
        return values[HEADER_SIZE + index * 3];
    }

    int chunkZ(int index) {
        return values[HEADER_SIZE + index * 3 + 1];
    }

    int chunkCells(int index) {
        return Math.max(0, Math.min(16, values[HEADER_SIZE + index * 3 + 2]));
    }

    boolean showsFoundation() {
        return status() == 0 || status() == 1 && foundationRequired() && (phase() == 1 || phase() == 2);
    }

    double progress() {
        if (status() == 2) return 1;
        if (showsFoundation()) {
            if (phase() == 1) return ratio(completedCells() + ratio(cellTicks(), cellDuration()), totalCells());
            if (phase() == 2) return 1 - ratio(curingRemaining(), curingDuration());
        }
        return ratio(placed(), total());
    }

    static double ratio(double value, double total) {
        return total <= 0 ? 0 : Math.max(0, Math.min(1, value / total));
    }
}
