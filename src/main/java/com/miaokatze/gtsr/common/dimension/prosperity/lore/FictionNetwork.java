package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtsr.client.lore.FictionSky;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Fixed-size server-to-client presentation only. No C2S path can start or finish the event. */
public final class FictionNetwork {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_fiction");

    private FictionNetwork() {}

    public static void register() {
        NETWORK.registerMessage(Handler.class, Sky.class, 0, Side.CLIENT);
    }

    public static void send(EntityPlayerMP player, int phase, long serial) {
        if (player.playerNetServerHandler != null) NETWORK.sendTo(new Sky(player, phase, serial), player);
    }

    public static final class Sky implements IMessage {

        public int dimension, phase;
        public UUID player;
        public long serial;
        public boolean valid;

        public Sky() {}

        Sky(EntityPlayerMP player, int phase, long serial) {
            this.dimension = player.dimension;
            this.player = player.getUniqueID();
            this.phase = phase;
            this.serial = serial;
            this.valid = phase >= 0 && phase <= 4;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(dimension);
            buf.writeLong(player.getMostSignificantBits());
            buf.writeLong(player.getLeastSignificantBits());
            buf.writeByte(phase);
            buf.writeLong(serial);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = false;
            if (buf.readableBytes() != 29) return;
            dimension = buf.readInt();
            player = new UUID(buf.readLong(), buf.readLong());
            phase = buf.readUnsignedByte();
            serial = buf.readLong();
            valid = phase <= 4 && serial >= 0;
        }
    }

    public static final class Handler implements IMessageHandler<Sky, IMessage> {

        @Override
        public IMessage onMessage(Sky message, MessageContext context) {
            if (message.valid) FictionSky.receive(message);
            return null;
        }
    }
}
