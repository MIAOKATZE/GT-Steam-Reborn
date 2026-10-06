package com.miaokatze.gtsr.common.weapons;

import java.util.concurrent.ArrayBlockingQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public final class WeaponNetwork {

    private static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_weapons");
    static final ArrayBlockingQueue<Pending> INPUT = new ArrayBlockingQueue<Pending>(256);
    private static volatile ClientSink sink;

    public interface ClientSink {

        void state(Snapshot state);

        void effect(Effect effect);
    }

    public static void setClientSink(ClientSink value) {
        sink = value;
    }

    public static void init() {
        NET.registerMessage(ControlHandler.class, Controls.class, 0, Side.SERVER);
        NET.registerMessage(StateHandler.class, StatePacket.class, 1, Side.CLIENT);
        NET.registerMessage(EffectHandler.class, EffectPacket.class, 2, Side.CLIENT);
    }

    public static void sendControls(int slot, boolean firing, boolean focusing, boolean reload) {
        Controls c = new Controls();
        c.slot = slot;
        c.firing = firing;
        c.focusing = focusing;
        c.reload = reload;
        NET.sendToServer(c);
    }

    public static void enqueueControls(EntityPlayerMP p, int slot, boolean firing, boolean focusing, boolean reload) {
        Controls c = new Controls();
        c.slot = slot;
        c.firing = firing;
        c.focusing = focusing;
        c.reload = reload;
        INPUT.offer(new Pending(p, c));
    }

    static void state(EntityPlayerMP p, Snapshot s) {
        StatePacket packet = new StatePacket();
        packet.s = s;
        NET.sendToAllAround(packet, new NetworkRegistry.TargetPoint(p.dimension, p.posX, p.posY, p.posZ, 96));
    }

    public static void effect(net.minecraft.world.World world, Effect e) {
        if (world.isRemote) return;
        EffectPacket packet = new EffectPacket();
        packet.e = e;
        NET.sendToAllAround(packet, new NetworkRegistry.TargetPoint(world.provider.dimensionId, e.x, e.y, e.z, 96));
    }

    static final class Pending {

        final EntityPlayerMP player;
        final Controls controls;

        Pending(EntityPlayerMP p, Controls c) {
            player = p;
            controls = c;
        }
    }

    public static class Controls implements IMessage {

        int slot;
        boolean firing, focusing, reload;

        public void fromBytes(ByteBuf b) {
            slot = b.readUnsignedByte();
            int flags = b.readUnsignedByte();
            firing = (flags & 1) != 0;
            focusing = (flags & 2) != 0;
            reload = (flags & 4) != 0;
        }

        public void toBytes(ByteBuf b) {
            b.writeByte(slot);
            b.writeByte((firing ? 1 : 0) | (focusing ? 2 : 0) | (reload ? 4 : 0));
        }
    }

    public static class ControlHandler implements IMessageHandler<Controls, IMessage> {

        public IMessage onMessage(Controls m, MessageContext c) {
            if (c.getServerHandler() != null) INPUT.offer(new Pending(c.getServerHandler().playerEntity, m));
            return null;
        }
    }

    public static class StatePacket implements IMessage {

        Snapshot s;

        public void toBytes(ByteBuf b) {
            b.writeInt(s.entityId);
            b.writeByte(s.kind);
            b.writeInt(s.magazine);
            b.writeInt(s.reserve);
            b.writeInt(s.reloadTicks);
            b.writeInt(s.reloadDuration);
            b.writeInt(s.shotSerial);
            b.writeFloat(s.heat);
            b.writeFloat(s.spin);
            b.writeBoolean(s.focusing);
            b.writeBoolean(s.overheated);
        }

        public void fromBytes(ByteBuf b) {
            s = new Snapshot();
            s.entityId = b.readInt();
            s.kind = b.readUnsignedByte();
            s.magazine = b.readInt();
            s.reserve = b.readInt();
            s.reloadTicks = b.readInt();
            s.reloadDuration = b.readInt();
            s.shotSerial = b.readInt();
            s.heat = b.readFloat();
            s.spin = b.readFloat();
            s.focusing = b.readBoolean();
            s.overheated = b.readBoolean();
        }
    }

    public static class StateHandler implements IMessageHandler<StatePacket, IMessage> {

        public IMessage onMessage(StatePacket m, MessageContext c) {
            ClientSink s = sink;
            if (s != null) s.state(m.s);
            return null;
        }
    }

    public static class EffectPacket implements IMessage {

        Effect e;

        public void toBytes(ByteBuf b) {
            b.writeInt(e.entityId);
            b.writeByte(e.kind);
            b.writeByte(e.type);
            b.writeDouble(e.x);
            b.writeDouble(e.y);
            b.writeDouble(e.z);
            b.writeFloat(e.yaw);
            b.writeFloat(e.pitch);
            b.writeInt(e.shotSerial);
        }

        public void fromBytes(ByteBuf b) {
            e = new Effect();
            e.entityId = b.readInt();
            e.kind = b.readUnsignedByte();
            e.type = b.readUnsignedByte();
            e.x = b.readDouble();
            e.y = b.readDouble();
            e.z = b.readDouble();
            e.yaw = b.readFloat();
            e.pitch = b.readFloat();
            e.shotSerial = b.readInt();
        }
    }

    public static class EffectHandler implements IMessageHandler<EffectPacket, IMessage> {

        public IMessage onMessage(EffectPacket m, MessageContext c) {
            ClientSink s = sink;
            if (s != null) s.effect(m.e);
            return null;
        }
    }
}
