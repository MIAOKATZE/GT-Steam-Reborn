// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.common.machinevisual;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Protocol and observer lifetime boundaries. No game or GL context is created. */
public final class VirtualStructureContracts {

    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static VirtualStructureFrame frame() {
        VirtualStructureFrame frame = new VirtualStructureFrame();
        frame.dimension = -7;
        frame.x = -100001;
        frame.y = 80;
        frame.z = 29999999;
        frame.kind = VirtualStructureFrame.CENTER;
        frame.session = Long.MIN_VALUE + 4;
        frame.generation = 51;
        frame.sequence = 7;
        frame.visual.setIntArray("types", new int[36]);
        int[] grades = new int[36];
        Arrays.fill(grades, 3);
        frame.visual.setIntArray("grades", grades);
        frame.visual.setInteger("facing", 5);
        return frame;
    }

    private static void rejected(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | IndexOutOfBoundsException expected) {
            check(true, message);
            return;
        }
        throw new AssertionError(message);
    }

    public static void main(String[] args) {
        VirtualStructureFrame source = frame();
        ByteBuf data = Unpooled.buffer();
        try {
            source.toBytes(data);
            VirtualStructureFrame decoded = new VirtualStructureFrame();
            decoded.fromBytes(data);
            check(
                decoded.dimension == source.dimension && decoded.x == source.x && decoded.z == source.z,
                "owner outside client controller range round-trips without tile lookup");
            check(
                decoded.visual.equals(source.visual) && decoded.session == source.session
                    && decoded.generation == 51
                    && decoded.sequence == 7,
                "complete installation snapshot round-trips");
            decoded.visual.setString("local", "changed");
            check(!source.visual.hasKey("local"), "decoded snapshot does not alias the sender");
        } finally {
            data.release();
        }
        rejected(() -> {
            VirtualStructureFrame f = frame();
            f.visual.setIntArray("types", new int[35]);
            f.validate();
        }, "partial layouts cannot become client installations");
        rejected(() -> {
            VirtualStructureFrame f = frame();
            int[] g = new int[36];
            g[4] = 4;
            f.visual.setIntArray("grades", g);
            f.validate();
        }, "unsupported grade rejected");
        rejected(() -> {
            VirtualStructureFrame f = frame();
            int[] t = new int[36];
            t[5] = 17;
            f.visual.setIntArray("types", t);
            f.validate();
        }, "unknown model rejected");
        rejected(() -> {
            VirtualStructureFrame f = frame();
            f.visual.setInteger("facing", 1);
            f.validate();
        }, "unsupported orientation rejected");
        rejected(() -> {
            VirtualStructureFrame f = frame();
            f.sequence = 0;
            f.validate();
        }, "sequence required");
        rejected(() -> {
            VirtualStructureFrame f = frame();
            f.x = Integer.MIN_VALUE;
            f.validate();
        }, "coordinate overflow rejected");
        VirtualStructureFrame empty = frame();
        int[] emptyTypes = new int[36];
        Arrays.fill(emptyTypes, -1);
        empty.visual.setIntArray("types", emptyTypes);
        empty.visual.setIntArray("grades", new int[36]);
        empty.validate();
        check(true, "the production controller uses -1 for empty or paused migration slots");
        int[] invalidGrades = new int[36];
        invalidGrades[0] = 1;
        empty.visual.setIntArray("grades", invalidGrades);
        rejected(empty::validate, "an installed package must have an actual module type");
        ByteBuf truncated = Unpooled.buffer(5)
            .writeInt(1)
            .writeByte(0);
        try {
            rejected(() -> new VirtualStructureFrame().fromBytes(truncated), "truncated frames rejected");
        } finally {
            truncated.release();
        }
        VirtualStructureFrame large = frame();
        byte[] random = new byte[70_000];
        new java.util.Random(175).nextBytes(random);
        large.visual.setByteArray("largeVisualMask", random);
        ByteBuf big = Unpooled.buffer();
        try {
            large.toBytes(big);
            check(big.readableBytes() > Short.MAX_VALUE, "fixture exceeds native short-tag packet boundary");
            VirtualStructureFrame decoded = new VirtualStructureFrame();
            decoded.fromBytes(big);
            check(
                Arrays.equals(decoded.visual.getByteArray("largeVisualMask"), random),
                "large descriptors use bounded native NBT with an explicit integer length");
        } finally {
            big.release();
        }
        VirtualStructureFrame bomb = frame();
        bomb.visual.setByteArray("expandedTooLarge", new byte[3 * 1024 * 1024]);
        ByteBuf compressed = Unpooled.buffer();
        try {
            rejected(() -> bomb.toBytes(compressed), "compressible payload cannot exceed reader memory budget");
        } finally {
            compressed.release();
        }
        long[] previous = { source.session, 51, 7 };
        check(!VirtualStructureFrame.newer(source.session, 51, 7, previous), "duplicate snapshot cannot replay");
        check(!VirtualStructureFrame.newer(source.session, 51, 6, previous), "late update cannot revive retired scene");
        check(!VirtualStructureFrame.newer(source.session, 50, 999, previous), "old owner generation rejected");
        check(VirtualStructureFrame.newer(source.session, 52, 1, previous), "new loaded owner replaces old generation");
        check(VirtualStructureFrame.newer(source.session, 51, 8, previous), "later valid update accepted");
        check(
            VirtualStructureNetwork.position(-1, 80, -1) != VirtualStructureNetwork.position(-1, 81, -1),
            "owners in same column remain distinct");
        check(
            VirtualStructureNetwork.chunk(-1, 0) != VirtualStructureNetwork.chunk(0, -1),
            "signed chunk coordinates remain distinct");
        System.out.println("PASS virtual structure protocol contracts: " + checks);
    }
}
