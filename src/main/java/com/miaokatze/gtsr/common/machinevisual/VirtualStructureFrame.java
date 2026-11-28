// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.common.machinevisual;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Read-only visual installation. Neither this packet nor its client consumer grants machine capabilities. */
public final class VirtualStructureFrame implements IMessage {

    public static final int REMOVED = 0, CENTER = 1;
    public static final int MAX_BYTES = 1024 * 1024;
    private static final int HEADER_BYTES = 48;
    public int dimension, x, y, z, kind;
    public long session, generation, sequence;
    public NBTTagCompound visual = new NBTTagCompound();

    public VirtualStructureFrame() {}

    VirtualStructureFrame(int dimension, int x, int y, int z, int kind, long generation, long sequence,
        NBTTagCompound visual) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.kind = kind;
        this.session = MachineVisualNetwork.session();
        this.generation = generation;
        this.sequence = sequence;
        this.visual = (NBTTagCompound) visual.copy();
    }

    public void validate() {
        if (kind < REMOVED || kind > CENTER
            || Math.abs((long) x) > 30_000_000L
            || Math.abs((long) z) > 30_000_000L
            || y < 0
            || y > 255
            || generation <= 0
            || sequence <= 0) throw new IllegalArgumentException("Invalid virtual structure identity");
        if (kind == CENTER) {
            int[] types = visual.getIntArray("types"), grades = visual.getIntArray("grades");
            int facing = visual.getInteger("facing");
            if (types.length != 36 || grades.length != 36 || facing < 2 || facing > 5)
                throw new IllegalArgumentException("Invalid virtual module layout");
            for (int i = 0; i < 36; i++)
                if (types[i] < -1 || types[i] >= 17 || grades[i] < 0 || grades[i] > 3 || grades[i] > 0 && types[i] < 0)
                    throw new IllegalArgumentException("Invalid virtual module entry");
        }
    }

    public static boolean newer(long session, long generation, long sequence, long[] previous) {
        return previous == null || previous[0] != session
            || generation > previous[1]
            || generation == previous[1] && sequence > previous[2];
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        if (buffer.readableBytes() <= HEADER_BYTES || buffer.readableBytes() > MAX_BYTES)
            throw new IllegalArgumentException("Virtual structure packet length");
        dimension = buffer.readInt();
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        kind = buffer.readInt();
        session = buffer.readLong();
        generation = buffer.readLong();
        sequence = buffer.readLong();
        int length = buffer.readInt();
        if (length <= 0 || length > MAX_BYTES - HEADER_BYTES || length != buffer.readableBytes())
            throw new IllegalArgumentException("Virtual structure payload length");
        byte[] compressed = new byte[length];
        buffer.readBytes(compressed);
        try {
            visual = CompressedStreamTools.func_152457_a(compressed, new NBTSizeTracker(2L * 1024 * 1024));
        } catch (java.io.IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Invalid virtual structure NBT", failure);
        }
        if (visual == null) throw new IllegalArgumentException("Virtual structure payload");
        validate();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        final byte[] compressed;
        try {
            compressed = CompressedStreamTools.compress(visual);
            // The writer obeys the same native expanded-NBT budget as the reader, including compressible arrays.
            CompressedStreamTools.func_152457_a(compressed, new NBTSizeTracker(2L * 1024 * 1024));
        } catch (java.io.IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Cannot encode virtual structure NBT", failure);
        }
        if (compressed.length > MAX_BYTES - HEADER_BYTES)
            throw new IllegalArgumentException("Virtual structure too large");
        buffer.writeInt(dimension)
            .writeInt(x)
            .writeInt(y)
            .writeInt(z)
            .writeInt(kind)
            .writeLong(session)
            .writeLong(generation)
            .writeLong(sequence);
        buffer.writeInt(compressed.length)
            .writeBytes(compressed);
    }
}
