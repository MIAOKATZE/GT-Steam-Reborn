package com.miaokatze.gtsr.common.critical;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** The highlight protocol has one S2C message and never accepts an unbounded coordinate allocation. */
public final class CriticalDamageHighlightPacketContract {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        CriticalDamageHighlights.Frame frame = new CriticalDamageHighlights.Frame();
        frame.dimension = -7;
        frame.session = 91;
        frame.knownTotal = 1300;
        frame.scanPending = true;
        frame.positions = new int[1024 * 3];
        for (int i = 0; i < frame.positions.length; i++) frame.positions[i] = i - 1400;
        ByteBuf wire = Unpooled.buffer();
        frame.toBytes(wire);
        check(wire.readableBytes() == 12307, "a maximum coordinate batch remains below 16 KiB");
        CriticalDamageHighlights.Frame decoded = new CriticalDamageHighlights.Frame();
        decoded.fromBytes(wire);
        check(wire.readableBytes() == 0 && decoded.dimension == -7 && decoded.session == 91
            && decoded.scanPending && decoded.knownTotal == 1300 && Arrays.equals(decoded.positions, frame.positions),
            "one complete S2C result retains coordinates, request identity, scan state and the known total");
        check(decoded.knownTotal > decoded.positions.length / 3,
            "the client can report explicit truncation without silently discarding the known total");
        rejects(header(1025, 1025), "oversized batch is rejected before allocating positions");
        rejects(header(1, 0).writeInt(1).writeInt(2).writeInt(3), "known total cannot be below the supplied batch");
        rejects(header(0, 0).writeByte(1), "trailing data cannot hide another coordinate batch");
        rejects(Unpooled.buffer().writeInt(0), "truncated metadata is rejected");
        frame.positions = new int[1];
        try {
            frame.toBytes(Unpooled.buffer());
            throw new AssertionError("incomplete XYZ tuple must fail");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        System.out.println("PASS CriticalDamageHighlightPacketContract checks=" + checks);
    }

    private static ByteBuf header(int count, int knownTotal) {
        return Unpooled.buffer().writeInt(0).writeLong(1).writeInt(knownTotal).writeBoolean(false).writeShort(count);
    }

    private static void rejects(ByteBuf wire, String message) {
        try {
            new CriticalDamageHighlights.Frame().fromBytes(wire);
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }
}
