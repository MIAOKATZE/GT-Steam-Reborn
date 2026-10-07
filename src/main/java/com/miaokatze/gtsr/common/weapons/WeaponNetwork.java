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

    public static void sendControls(int slot, boolean firing, boolean focusing, boolean reload, boolean switchAmmo,
        float yaw, float pitch) {
        sendControls(slot, firing, focusing, reload, switchAmmo, false, yaw, pitch);
    }

    public static void sendControls(int slot, boolean firing, boolean focusing, boolean reload, boolean switchAmmo,
        boolean switchMode, float yaw, float pitch) {
        sendControls(slot, firing, focusing, reload, switchAmmo, switchMode, false, yaw, pitch);
    }

    public static void sendControls(int slot, boolean firing, boolean focusing, boolean reload, boolean switchAmmo,
        boolean switchMode, boolean cancelCharge, float yaw, float pitch) {
        Controls c = new Controls();
        c.yaw = yaw;
        c.pitch = pitch;
        c.switchAmmo = switchAmmo;
        c.switchMode = switchMode;
        c.cancelCharge = cancelCharge;
        c.slot = slot;
        c.firing = firing;
        c.focusing = focusing;
        c.reload = reload;
        NET.sendToServer(c);
    }

    public static void enqueueControls(EntityPlayerMP p, int slot, boolean firing, boolean focusing, boolean reload) {
        enqueueControls(p, slot, firing, focusing, reload, false);
    }

    public static void enqueueControls(EntityPlayerMP p, int slot, boolean firing, boolean focusing, boolean reload,
        boolean switchAmmo) {
        enqueueControls(p, slot, firing, focusing, reload, switchAmmo, p.rotationYaw, p.rotationPitch);
    }

    public static void enqueueControls(EntityPlayerMP p, int slot, boolean firing, boolean focusing, boolean reload,
        boolean switchAmmo, boolean switchMode) {
        enqueueControls(p, slot, firing, focusing, reload, switchAmmo, switchMode, p.rotationYaw, p.rotationPitch);
    }

    public static void enqueueControls(EntityPlayerMP p, int slot, boolean firing, boolean focusing, boolean reload,
        boolean switchAmmo, float yaw, float pitch) {
        enqueueControls(p, slot, firing, focusing, reload, switchAmmo, false, yaw, pitch);
    }

    public static void enqueueControls(EntityPlayerMP p, int slot, boolean firing, boolean focusing, boolean reload,
        boolean switchAmmo, boolean switchMode, float yaw, float pitch) {
        enqueueControls(p, slot, firing, focusing, reload, switchAmmo, switchMode, false, yaw, pitch);
    }

    public static void enqueueControls(EntityPlayerMP p, int slot, boolean firing, boolean focusing, boolean reload,
        boolean switchAmmo, boolean switchMode, boolean cancelCharge) {
        enqueueControls(
            p,
            slot,
            firing,
            focusing,
            reload,
            switchAmmo,
            switchMode,
            cancelCharge,
            p.rotationYaw,
            p.rotationPitch);
    }

    public static void enqueueControls(EntityPlayerMP p, int slot, boolean firing, boolean focusing, boolean reload,
        boolean switchAmmo, boolean switchMode, boolean cancelCharge, float yaw, float pitch) {
        Controls c = new Controls();
        c.yaw = yaw;
        c.pitch = pitch;
        c.slot = slot;
        c.firing = firing;
        c.focusing = focusing;
        c.reload = reload;
        c.switchAmmo = switchAmmo;
        c.switchMode = switchMode;
        c.cancelCharge = cancelCharge;
        enqueueControls(p, c);
    }

    /** Also used by isolated audits after the actual wire decoder. */
    public static void enqueueControls(EntityPlayerMP p, Controls controls) {
        INPUT.offer(new Pending(p, controls));
    }

    static void state(EntityPlayerMP p, Snapshot s) {
        StatePacket packet = new StatePacket();
        packet.s = s;
        NET.sendToDimension(packet, p.dimension);
    }

    public static void effect(net.minecraft.world.World world, Effect e) {
        if (world.isRemote) return;
        EffectPacket packet = new EffectPacket();
        packet.e = e;
        NET.sendToDimension(packet, world.provider.dimensionId);
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

        public int slot;
        public float yaw, pitch;
        public boolean firing, focusing, reload, switchAmmo, switchMode, cancelCharge;

        public void fromBytes(ByteBuf b) {
            slot = b.readUnsignedByte();
            int flags = b.readUnsignedByte();
            firing = (flags & 1) != 0;
            focusing = (flags & 2) != 0;
            reload = (flags & 4) != 0;
            switchAmmo = (flags & 8) != 0;
            switchMode = (flags & 16) != 0;
            cancelCharge = (flags & 32) != 0;
            yaw = b.readFloat();
            pitch = b.readFloat();
        }

        public void toBytes(ByteBuf b) {
            b.writeByte(slot);
            b.writeByte(
                (firing ? 1 : 0) | (focusing ? 2 : 0)
                    | (reload ? 4 : 0)
                    | (switchAmmo ? 8 : 0)
                    | (switchMode ? 16 : 0)
                    | (cancelCharge ? 32 : 0));
            b.writeFloat(yaw);
            b.writeFloat(pitch);
        }
    }

    public static class ControlHandler implements IMessageHandler<Controls, IMessage> {

        public IMessage onMessage(Controls m, MessageContext c) {
            if (c.getServerHandler() != null) enqueueControls(c.getServerHandler().playerEntity, m);
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
            b.writeInt(s.shotInterval);
            b.writeInt(s.shotCooldown);
            b.writeInt(s.shotAge);
            b.writeInt(s.ammoType);
            b.writeInt(s.remoteTicks);
            b.writeInt(s.mode);
            b.writeInt(s.chargeTicks);
            b.writeInt(s.chargeDuration);
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
            s.shotInterval = b.readInt();
            s.shotCooldown = b.readInt();
            s.shotAge = b.readInt();
            s.ammoType = b.readInt();
            s.remoteTicks = b.readInt();
            s.mode = b.readInt();
            s.chargeTicks = b.readInt();
            s.chargeDuration = b.readInt();
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
            b.writeDouble(e.ejectX);
            b.writeDouble(e.ejectY);
            b.writeDouble(e.ejectZ);
            b.writeFloat(e.yaw);
            b.writeFloat(e.pitch);
            b.writeInt(e.shotSerial);
            b.writeByte(e.mode);
        }

        public void fromBytes(ByteBuf b) {
            e = new Effect();
            e.entityId = b.readInt();
            e.kind = b.readUnsignedByte();
            e.type = b.readUnsignedByte();
            e.x = b.readDouble();
            e.y = b.readDouble();
            e.z = b.readDouble();
            e.ejectX = b.readDouble();
            e.ejectY = b.readDouble();
            e.ejectZ = b.readDouble();
            e.yaw = b.readFloat();
            e.pitch = b.readFloat();
            e.shotSerial = b.readInt();
            e.mode = b.readUnsignedByte();
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
