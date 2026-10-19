package com.miaokatze.gtsr.common.terminal.entanglement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bounded pages: the wire payload never grows with the complete hatch count. */
public final class EntanglementSnapshot {

    public static final int PAGE_SIZE = 6;
    public static final int MAX_BYTES = 2048;
    public boolean critical, formed, allowed, cycling, outputSpace;
    public double heat, overlimit, instability, tear;
    public int revision, page, totalRows;
    public final long[] amounts = new long[EntanglementInputs.KINDS];
    public final long[] storedAmounts = new long[EntanglementInputs.KINDS];
    public final List<Row> rows = new ArrayList<>();

    public static final class Row {

        public int x, y, z, kind;
        public long stored, limit;

        public String key() {
            return EntanglementInputs.key(x, y, z, kind);
        }

        public long requested(long override) {
            return override < 0 ? stored : Math.min(stored, override);
        }
    }

    public static EntanglementSnapshot assemble(boolean critical, boolean formed, boolean allowed, boolean cycling,
        boolean outputSpace, double heat, double overlimit, double instability, double tear, int revision, int page,
        List<EntanglementInputs.Input> inputs) {
        EntanglementSnapshot snapshot = new EntanglementSnapshot();
        snapshot.critical = critical;
        snapshot.formed = formed;
        snapshot.allowed = allowed;
        snapshot.cycling = cycling;
        snapshot.outputSpace = outputSpace;
        snapshot.heat = heat;
        snapshot.overlimit = overlimit;
        snapshot.instability = instability;
        snapshot.tear = critical ? tear : 0;
        snapshot.revision = revision;
        snapshot.totalRows = inputs.size();
        snapshot.page = Math.max(0, Math.min(page, snapshot.pages() - 1));
        for (int i = 0; i < inputs.size(); i++) {
            EntanglementInputs.Input input = inputs.get(i);
            snapshot.amounts[input.kind] = EntanglementInputs.add(snapshot.amounts[input.kind], input.requested());
            snapshot.storedAmounts[input.kind] = EntanglementInputs
                .add(snapshot.storedAmounts[input.kind], input.stored);
            if (i < snapshot.page * PAGE_SIZE || i >= (snapshot.page + 1) * PAGE_SIZE) continue;
            Row row = new Row();
            row.x = input.x;
            row.y = input.y;
            row.z = input.z;
            row.kind = input.kind;
            row.stored = input.stored;
            row.limit = input.limit;
            snapshot.rows.add(row);
        }
        return snapshot;
    }

    public int pages() {
        return Math.max(1, (totalRows + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public long[] requested(Map<String, Long> drafts) {
        return requested(drafts, false);
    }

    public long[] requested(Map<String, Long> drafts, boolean defaults) {
        long[] requested = (defaults ? storedAmounts : amounts).clone();
        for (Row row : rows) {
            Long override = drafts.get(row.key());
            if (override == null) continue;
            requested[row.kind] = EntanglementInputs.add(
                Math.max(0, requested[row.kind] - row.requested(defaults ? -1 : row.limit)),
                row.requested(override));
        }
        return requested;
    }

    public EntanglementMath.Forecast forecast(Map<String, Long> drafts) {
        return forecast(drafts, false);
    }

    public EntanglementMath.Forecast forecast(Map<String, Long> drafts, boolean defaults) {
        return EntanglementMath.predict(
            critical,
            formed,
            allowed,
            cycling,
            outputSpace,
            heat,
            overlimit,
            instability,
            tear,
            requested(drafts, defaults));
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeBoolean(critical);
            out.writeBoolean(formed);
            out.writeBoolean(allowed);
            out.writeBoolean(cycling);
            out.writeBoolean(outputSpace);
            out.writeDouble(heat);
            out.writeDouble(overlimit);
            out.writeDouble(instability);
            out.writeDouble(tear);
            out.writeInt(revision);
            out.writeInt(page);
            out.writeInt(totalRows);
            for (long amount : amounts) out.writeLong(amount);
            for (long amount : storedAmounts) out.writeLong(amount);
            out.writeByte(rows.size());
            for (Row row : rows) {
                out.writeInt(row.x);
                out.writeInt(row.y);
                out.writeInt(row.z);
                out.writeByte(row.kind);
                out.writeLong(row.stored);
                out.writeLong(row.limit);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static EntanglementSnapshot decode(byte[] payload) throws IOException {
        if (payload.length > MAX_BYTES) throw new IOException("Entanglement snapshot too large");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        EntanglementSnapshot snapshot = new EntanglementSnapshot();
        snapshot.critical = in.readBoolean();
        snapshot.formed = in.readBoolean();
        snapshot.allowed = in.readBoolean();
        snapshot.cycling = in.readBoolean();
        snapshot.outputSpace = in.readBoolean();
        snapshot.heat = in.readDouble();
        snapshot.overlimit = in.readDouble();
        snapshot.instability = in.readDouble();
        snapshot.tear = in.readDouble();
        for (double value : new double[] { snapshot.heat, snapshot.overlimit, snapshot.instability, snapshot.tear })
            if (!Double.isFinite(value) || value < 0) throw new IOException("Invalid entanglement state");
        snapshot.revision = in.readInt();
        snapshot.page = in.readInt();
        snapshot.totalRows = in.readInt();
        if (snapshot.totalRows < 0 || snapshot.totalRows > 100000
            || snapshot.page < 0
            || snapshot.page >= snapshot.pages()) throw new IOException("Invalid input page");
        for (int i = 0; i < snapshot.amounts.length; i++) {
            snapshot.amounts[i] = in.readLong();
            if (snapshot.amounts[i] < 0) throw new IOException("Invalid input amount");
        }
        for (int i = 0; i < snapshot.storedAmounts.length; i++) {
            snapshot.storedAmounts[i] = in.readLong();
            if (snapshot.storedAmounts[i] < 0) throw new IOException("Invalid stored amount");
        }
        int count = in.readUnsignedByte();
        if (count > PAGE_SIZE) throw new IOException("Too many input rows");
        for (int i = 0; i < count; i++) {
            Row row = new Row();
            row.x = in.readInt();
            row.y = in.readInt();
            row.z = in.readInt();
            row.kind = in.readUnsignedByte();
            row.stored = in.readLong();
            row.limit = in.readLong();
            if (row.kind >= (snapshot.critical ? 6 : 5) || row.stored < 0 || row.limit < -1)
                throw new IOException("Invalid input row");
            snapshot.rows.add(row);
        }
        if (in.available() != 0) throw new IOException("Trailing entanglement snapshot bytes");
        return snapshot;
    }
}
