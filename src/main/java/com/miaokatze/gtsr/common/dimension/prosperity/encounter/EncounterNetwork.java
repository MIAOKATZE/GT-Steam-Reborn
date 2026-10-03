package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtsr.client.encounter.EncounterSignals;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Fixed-size S2C signals; no client mutation route. */
public final class EncounterNetwork {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_encounter");

    private EncounterNetwork() {}

    public static void register() {
        NETWORK.registerMessage(Handler.class, Signal.class, 0, Side.CLIENT);
        NETWORK.registerMessage(SceneHandler.class, SceneBossSignal.class, 1, Side.CLIENT);
    }

    public static void scene(EntityPlayerMP p, SceneBossSignal s) {
        s.dimension = p.dimension;
        s.player = p.getUniqueID();
        if (p.playerNetServerHandler != null && s.sane()) NETWORK.sendTo(s, p);
    }

    public static final class SceneHandler implements IMessageHandler<SceneBossSignal, IMessage> {

        @Override
        public IMessage onMessage(SceneBossSignal s, MessageContext c) {
            if (s.valid) EncounterSignals.receiveScene(s);
            return null;
        }
    }

    public static void progress(EntityPlayerMP p, boolean active, int remaining) {
        send(p, new Signal(p, 0, active, remaining, -1, 0, 0, 0));
    }

    public static void guidance(EntityPlayerMP p, int entity, double x, double y, double z) {
        send(p, new Signal(p, 1, true, 0, entity, x, y, z));
    }

    private static void send(EntityPlayerMP p, Signal s) {
        if (p.playerNetServerHandler != null) NETWORK.sendTo(s, p);
    }

    public static final class Signal implements IMessage {

        public int dimension, kind, remaining, entity;
        public UUID player;
        public boolean active, valid;
        public double x, y, z;

        public Signal() {}

        Signal(EntityPlayerMP p, int k, boolean a, int r, int e, double x, double y, double z) {
            dimension = p.dimension;
            player = p.getUniqueID();
            kind = k;
            active = a;
            remaining = r;
            entity = e;
            this.x = x;
            this.y = y;
            this.z = z;
            valid = true;
        }

        @Override
        public void fromBytes(ByteBuf b) {
            valid = false;
            if (b.readableBytes() != 51) return;
            dimension = b.readInt();
            player = new UUID(b.readLong(), b.readLong());
            kind = b.readUnsignedByte();
            int flag = b.readUnsignedByte();
            remaining = b.readUnsignedByte();
            entity = b.readInt();
            x = b.readDouble();
            y = b.readDouble();
            z = b.readDouble();
            active = flag == 1;
            valid = kind <= 1 && flag <= 1
                && remaining <= 32
                && entity >= -1
                && Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z)
                && Math.abs(x) <= 30000000
                && Math.abs(z) <= 30000000
                && y >= -4096
                && y <= 4096;
        }

        @Override
        public void toBytes(ByteBuf b) {
            b.writeInt(dimension);
            b.writeLong(player.getMostSignificantBits());
            b.writeLong(player.getLeastSignificantBits());
            b.writeByte(kind);
            b.writeByte(active ? 1 : 0);
            b.writeByte(remaining);
            b.writeInt(entity);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
        }
    }

    public static final class Handler implements IMessageHandler<Signal, IMessage> {

        @Override
        public IMessage onMessage(Signal s, MessageContext c) {
            if (s.valid) EncounterSignals.receive(s);
            return null;
        }
    }
}
