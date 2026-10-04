package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Finite S2C geometry. Common classes never link to a client renderer. */
public final class CombatEffects {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_combatfx");

    public interface Receiver {

        void receive(Signal signal);
    }

    public static Receiver receiver;

    private CombatEffects() {}

    public static void register() {
        NETWORK.registerMessage(Handler.class, Signal.class, 0, Side.CLIENT);
    }

    public static void send(Entity source, int sequence, int stage, int duration, int palette,
        CombatGeometry geometry) {
        send(source, sequence, stage, duration, palette, geometry, 0, 0);
    }

    public static void send(Entity source, int sequence, int stage, int duration, int palette, CombatGeometry geometry,
        int skill, int direction) {
        for (Object o : source.worldObj.playerEntities) if (o instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getDistanceSqToEntity(source) > 128 * 128 || p.playerNetServerHandler == null) continue;
            NETWORK.sendTo(new Signal(p, source, sequence, stage, duration, palette, geometry, skill, direction), p);
        }
    }

    public static final class Signal implements IMessage {

        public int dimension, entity, sequence, stage, duration, palette, skill, direction;
        public UUID player, source;
        public CombatGeometry geometry;
        public boolean valid;

        public Signal() {}

        Signal(EntityPlayerMP p, Entity e, int sequence, int stage, int duration, int palette, CombatGeometry g) {
            this(p, e, sequence, stage, duration, palette, g, 0, 0);
        }

        Signal(EntityPlayerMP p, Entity e, int sequence, int stage, int duration, int palette, CombatGeometry g,
            int skill, int direction) {
            this.skill = skill;
            this.direction = direction;
            dimension = p.dimension;
            player = p.getUniqueID();
            source = e.getUniqueID();
            entity = e.getEntityId();
            this.sequence = sequence;
            this.stage = stage;
            this.duration = duration;
            this.palette = palette;
            geometry = g;
            valid = true;
        }

        public void toBytes(ByteBuf b) {
            b.writeInt(dimension);
            b.writeInt(entity);
            b.writeInt(sequence);
            b.writeByte(stage);
            b.writeShort(duration);
            b.writeByte(palette);
            b.writeLong(player.getMostSignificantBits());
            b.writeLong(player.getLeastSignificantBits());
            b.writeLong(source.getMostSignificantBits());
            b.writeLong(source.getLeastSignificantBits());
            b.writeByte(geometry.shape);
            b.writeDouble(geometry.x);
            b.writeDouble(geometry.y);
            b.writeDouble(geometry.z);
            b.writeDouble(geometry.tx);
            b.writeDouble(geometry.tz);
            b.writeDouble(geometry.radius);
            b.writeDouble(geometry.inner);
            b.writeByte(skill);
            b.writeByte(direction);
        }

        public void fromBytes(ByteBuf b) {
            valid = false;
            int length = b.readableBytes();
            if (length != 105 && length != 107) return;
            skill = direction = 0;
            dimension = b.readInt();
            entity = b.readInt();
            sequence = b.readInt();
            stage = b.readUnsignedByte();
            duration = b.readUnsignedShort();
            palette = b.readUnsignedByte();
            player = new UUID(b.readLong(), b.readLong());
            source = new UUID(b.readLong(), b.readLong());
            int shape = b.readUnsignedByte();
            geometry = new CombatGeometry(
                shape,
                b.readDouble(),
                b.readDouble(),
                b.readDouble(),
                b.readDouble(),
                b.readDouble(),
                b.readDouble(),
                b.readDouble());
            if (length == 107) {
                skill = b.readUnsignedByte();
                direction = b.readUnsignedByte();
            }
            double[] values = { geometry.x, geometry.y, geometry.z, geometry.tx, geometry.tz, geometry.radius,
                geometry.inner };
            for (double v : values) if (!Double.isFinite(v)) return;
            double spanX = geometry.tx - geometry.x, spanZ = geometry.tz - geometry.z;
            if ((shape == CombatGeometry.LINE || shape == CombatGeometry.CHAIN)
                && spanX * spanX + spanZ * spanZ > 128 * 128) return;
            valid = entity >= 0 && sequence >= 0
                && skill <= 5
                && direction <= 5
                && stage <= 2
                && duration <= 240
                && palette < 4
                && shape <= 5
                && geometry.radius >= 0
                && geometry.radius <= 64
                && geometry.inner >= 0
                && geometry.inner <= geometry.radius
                && Math.abs(geometry.x) <= 30000000
                && Math.abs(geometry.z) <= 30000000
                && Math.abs(geometry.tx) <= 30000000
                && Math.abs(geometry.tz) <= 30000000
                && Math.abs(geometry.y) <= 4096;
        }
    }

    public static final class Handler implements IMessageHandler<Signal, IMessage> {

        public IMessage onMessage(Signal s, MessageContext context) {
            if (s.valid && receiver != null) receiver.receive(s);
            return null;
        }
    }
}
