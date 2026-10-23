package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;

/** Real packet codec contract; no world, rendering or ledger mutation is needed. */
public final class CriticalBusinessSnapshotContract {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Constructor<CriticalConstructionSnapshot> mapConstructor = CriticalConstructionSnapshot.class
            .getDeclaredConstructor(int[].class);
        mapConstructor.setAccessible(true);
        int[] map = new int[24];
        map[9] = 3;
        map[10] = 17;
        map[11] = 23;
        map[12] = 3;
        map[17] = 1;
        map[21] = -123;
        map[22] = 456;
        map[23] = 7;
        CriticalConstructionSnapshot construction = mapConstructor.newInstance((Object) map);
        String exact = "12345678901234567890123456789012345678901234567890";
        String[] text = { "blocked", "next item", "next fluid", "missing item", "missing fluid", "physical",
            "output", exact, exact, exact, "visible unpaid" };
        String[][] identities = { { "S", "S1", "exact identity", exact, "17", "3", exact, "5", "20" } };
        String[][] runtime = { { "ui.runtime.batch_wireless", exact + " EU" } };
        int[] configuration = { 6, 2, 3, 1, 40, 400, 5, 1, 1 };
        Constructor<CriticalBusinessSnapshot> constructor = CriticalBusinessSnapshot.class.getDeclaredConstructor(
            CriticalConstructionSnapshot.class, String[].class, int.class, String[][].class, String[][].class, int[].class);
        constructor.setAccessible(true);
        CriticalBusinessSnapshot sample = constructor.newInstance(construction, text, 2, identities, runtime, configuration);
        Method write = CriticalBusinessSnapshot.class.getDeclaredMethod("write", PacketBuffer.class);
        Method read = CriticalBusinessSnapshot.class.getDeclaredMethod("read", PacketBuffer.class);
        write.setAccessible(true);
        read.setAccessible(true);
        CriticalBusinessSnapshot bootstrap = CriticalBusinessSnapshot.empty();
        check(bootstrap.identityCount() == 0 && bootstrap.runtimeCount() == 0
            && bootstrap.construction.chunkCount() == 0, "client bootstrap must not require a target world or tile");
        PacketBuffer bootstrapPacket = new PacketBuffer(Unpooled.buffer());
        write.invoke(bootstrap, bootstrapPacket);
        CriticalBusinessSnapshot bootstrapDecoded = (CriticalBusinessSnapshot) read.invoke(null, bootstrapPacket);
        check(bootstrapPacket.readableBytes() == 0 && bootstrapDecoded.energy().equals("0")
            && !bootstrapDecoded.canCancelLegacyStellarBatch(), "bootstrap must use the normal bounded S2C codec");
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        write.invoke(sample, packet);
        CriticalBusinessSnapshot decoded = (CriticalBusinessSnapshot) read.invoke(null, packet);
        check(packet.readableBytes() == 0, "one complete packet must decode without trailing fields");
        check(decoded.construction.same(construction), "map and dismantle cursor must survive the same sample");
        check(decoded.identity(0, 3).equals(exact), "large job quantities must remain exact decimal strings");
        check(decoded.energy().equals(exact), "large telemetry values must not pass through floating point");
        check(decoded.runtime(0, 1).equals(exact + " EU"), "runtime amounts must remain exact");
        check(decoded.inputs().equals("physical") && decoded.networkInputs().equals("visible unpaid"),
            "physical and unpaid network sources must remain separate fields");
        check(decoded.batchTicks() == 40 && decoded.batchDuration() == 400 && decoded.steamMode() == 5,
            "batch cursor and mode must be decoded from the same packet");
        check(decoded.configuration().parallel == 2 && decoded.configuration().speed == 3,
            "configuration must be from the same packet");
        check(decoded.configuration() == decoded.configuration(), "immutable display configuration is reused per sample");
        check(decoded.canCancelLegacyStellarBatch(), "legacy recovery action must belong to the same sample");
        PacketBuffer wrongVersion = new PacketBuffer(Unpooled.buffer());
        wrongVersion.writeVarIntToBuffer(99);
        rejects(read, wrongVersion, "unknown packet version must fail closed");
        PacketBuffer tooManyChunks = new PacketBuffer(Unpooled.buffer());
        tooManyChunks.writeVarIntToBuffer(1);
        tooManyChunks.writeVarIntToBuffer(4097);
        rejects(read, tooManyChunks, "chunk count must be bounded before allocation");
        PacketBuffer tooManyIdentities = new PacketBuffer(Unpooled.buffer());
        tooManyIdentities.writeVarIntToBuffer(1);
        construction.write(tooManyIdentities);
        for (String value : text) tooManyIdentities.writeStringToBuffer(value);
        tooManyIdentities.writeVarIntToBuffer(2);
        tooManyIdentities.writeVarIntToBuffer(65);
        rejects(read, tooManyIdentities, "identity count must be bounded before allocation");
        System.out.println("PASS CriticalBusinessSnapshotContract checks=" + checks);
    }

    private static void rejects(Method read, PacketBuffer packet, String message) throws Exception {
        try {
            read.invoke(null, packet);
            throw new AssertionError(message);
        } catch (InvocationTargetException rejected) {
            check(rejected.getCause() instanceof IOException, message);
        }
    }
}
