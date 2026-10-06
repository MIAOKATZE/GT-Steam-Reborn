package com.miaokatze.gtsr.common.dimension.prosperity.travel;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/** Bounded visual-only state. No entities, damage or terrain changes. */
public final class SpacetimeEffects {

    private static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_spacefx");

    public interface Receiver {

        void receive(Signal s);
    }

    public static Receiver receiver;

    private SpacetimeEffects() {}

    public static void init() {
        NET.registerMessage(Handler.class, Signal.class, 0, Side.CLIENT);
    }

    public static void portalState(IGregTechTileEntity tile, boolean active, double x, double y, double z,
        ForgeDirection front) {
        portalState(tile, active, x, y, z, front, 0);
    }

    public static void portalState(IGregTechTileEntity tile, boolean active, double x, double y, double z,
        ForgeDirection front, int preheatTicks) {
        if (tile.getWorld().isRemote || front == null || front == ForgeDirection.UNKNOWN) return;
        Signal s = new Signal();
        s.kind = active || preheatTicks > 0 ? 1 : 0;
        s.preheatTicks = Math.max(0, Math.min(600, preheatTicks));
        s.dimension = tile.getWorld().provider.dimensionId;
        s.keyX = tile.getXCoord();
        s.keyY = tile.getYCoord();
        s.keyZ = tile.getZCoord();
        s.x = x;
        s.y = y;
        s.z = z;
        s.front = front.ordinal();
        send((WorldServer) tile.getWorld(), s);
    }

    public static void burst(WorldServer world, double x, double y, double z) {
        Signal s = new Signal();
        s.kind = 2;
        s.dimension = world.provider.dimensionId;
        s.x = x;
        s.y = y;
        s.z = z;
        send(world, s);
    }

    private static void send(WorldServer world, Signal s) {
        for (Object object : world.playerEntities) if (object instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) object;
            if (p.playerNetServerHandler != null && p.getDistanceSq(s.x, s.y, s.z) <= 128 * 128) NET.sendTo(s, p);
        }
    }

    public static final class Signal implements IMessage {

        public int dimension, kind, keyX, keyY, keyZ, front, preheatTicks;
        public double x, y, z;
        public boolean valid;

        public void toBytes(ByteBuf b) {
            b.writeInt(dimension);
            b.writeByte(kind);
            b.writeInt(keyX);
            b.writeInt(keyY);
            b.writeInt(keyZ);
            b.writeByte(front);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeShort(preheatTicks);
        }

        public void fromBytes(ByteBuf b) {
            if (b.readableBytes() != 44) return;
            dimension = b.readInt();
            kind = b.readUnsignedByte();
            keyX = b.readInt();
            keyY = b.readInt();
            keyZ = b.readInt();
            front = b.readUnsignedByte();
            x = b.readDouble();
            y = b.readDouble();
            z = b.readDouble();
            preheatTicks = b.readUnsignedShort();
            valid = kind <= 2 && front < 6
                && preheatTicks <= 600
                && Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z)
                && Math.abs(x) <= 30000000
                && Math.abs(z) <= 30000000
                && Math.abs(y) <= 4096;
        }
    }

    public static final class Handler implements IMessageHandler<Signal, IMessage> {

        public IMessage onMessage(Signal s, MessageContext c) {
            if (s.valid && receiver != null) receiver.receive(s);
            return null;
        }
    }
}
