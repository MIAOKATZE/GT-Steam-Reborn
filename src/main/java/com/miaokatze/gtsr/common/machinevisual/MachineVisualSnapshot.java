package com.miaokatze.gtsr.common.machinevisual;

import io.netty.buffer.ByteBuf;

/** Frozen full frame; deliberately contains no recipe, inventory, output or business NBT. */
public final class MachineVisualSnapshot {

    public static final int BYTES = 100;
    public final int dimension, x, y, z, registryId, orientation, flags, progress, duration;
    public final long session, nonce, generation, revision, sequence, sampledTick, workSequence, lastAdvanceTick;

    MachineVisualSnapshot(int dimension, int x, int y, int z, int registryId, int orientation, int flags, int progress,
        int duration, long session, long nonce, long generation, long revision, long sequence, long sampledTick,
        long workSequence, long lastAdvanceTick) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.registryId = registryId;
        this.orientation = orientation;
        this.flags = flags;
        this.progress = progress;
        this.duration = duration;
        this.session = session;
        this.nonce = nonce;
        this.generation = generation;
        this.revision = revision;
        this.sequence = sequence;
        this.sampledTick = sampledTick;
        this.workSequence = workSequence;
        this.lastAdvanceTick = lastAdvanceTick;
    }

    MachineVisualSnapshot withNonce(long nonce) {
        return new MachineVisualSnapshot(
            dimension,
            x,
            y,
            z,
            registryId,
            orientation,
            flags,
            progress,
            duration,
            session,
            nonce,
            generation,
            revision,
            sequence,
            sampledTick,
            workSequence,
            lastAdvanceTick);
    }

    public boolean formed() {
        return (flags & 1) != 0;
    }

    public boolean motionShape() {
        return (flags & 2) != 0;
    }

    public boolean working() {
        return (flags & 4) != 0;
    }

    public boolean stopIntent() {
        return (flags & 8) != 0;
    }

    public void write(ByteBuf buffer) {
        buffer.writeInt(dimension)
            .writeInt(x)
            .writeInt(y)
            .writeInt(z)
            .writeInt(registryId)
            .writeInt(orientation)
            .writeInt(flags)
            .writeInt(progress)
            .writeInt(duration)
            .writeLong(session)
            .writeLong(nonce)
            .writeLong(generation)
            .writeLong(revision)
            .writeLong(sequence)
            .writeLong(sampledTick)
            .writeLong(workSequence)
            .writeLong(lastAdvanceTick);
    }

    public static MachineVisualSnapshot read(ByteBuf buffer) {
        if (buffer.readableBytes() != BYTES) throw new IllegalArgumentException("Machine visual frame length");
        MachineVisualSnapshot frame = new MachineVisualSnapshot(
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong(),
            buffer.readLong());
        if (frame.orientation < 0 || frame.orientation >= 96
            || (frame.flags & ~15) != 0
            || frame.progress < 0
            || frame.duration < 0
            || frame.progress > frame.duration
            || frame.sequence < 0
            || frame.generation <= 0
            || frame.workSequence < 0
            || frame.y < 0
            || frame.y >= 256) throw new IllegalArgumentException("Machine visual frame range");
        return frame;
    }
}
