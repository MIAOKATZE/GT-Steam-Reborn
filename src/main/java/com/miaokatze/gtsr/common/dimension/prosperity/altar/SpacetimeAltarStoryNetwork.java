package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** One fixed-size reading presentation and one one-use activation nonce; reading itself is server-only. */
public final class SpacetimeAltarStoryNetwork {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_altarstory");
    private static final Map<EntityPlayerMP, UUID> PENDING = new ConcurrentHashMap<>();

    private SpacetimeAltarStoryNetwork() {}

    public static void register() {
        NETWORK.registerMessage(ReadingHandler.class, Reading.class, 0, Side.CLIENT);
        NETWORK.registerMessage(ActivateHandler.class, Activate.class, 1, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new Drain());
    }

    public static void show(EntityPlayerMP player, TileSpacetimeAltarStory board, UUID nonce) {
        if (player.playerNetServerHandler != null) NETWORK.sendTo(new Reading(player, board, nonce), player);
    }

    public static void activate(UUID nonce) {
        NETWORK.sendToServer(new Activate(nonce));
    }

    public static final class Reading implements IMessage {

        public int dimension, x, y, z;
        public UUID player, nonce;
        public String instance = "";
        public boolean valid;

        public Reading() {}

        Reading(EntityPlayerMP player, TileSpacetimeAltarStory board, UUID nonce) {
            dimension = player.dimension;
            this.player = player.getUniqueID();
            this.nonce = nonce;
            x = board.xCoord;
            y = board.yCoord;
            z = board.zCoord;
            instance = board.instanceId();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(dimension);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeLong(player.getMostSignificantBits());
            buf.writeLong(player.getLeastSignificantBits());
            buf.writeLong(nonce.getMostSignificantBits());
            buf.writeLong(nonce.getLeastSignificantBits());
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, instance);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = false;
            if (buf.readableBytes() < 49 || buf.readableBytes() > 180) return;
            dimension = buf.readInt();
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            player = new UUID(buf.readLong(), buf.readLong());
            nonce = new UUID(buf.readLong(), buf.readLong());
            try {
                instance = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
            } catch (RuntimeException malformed) {
                return;
            }
            valid = dimension == 0 && y >= 0
                && y < 256
                && !instance.isEmpty()
                && instance.length() <= 120
                && !buf.isReadable();
        }
    }

    public static final class Activate implements IMessage {

        public UUID nonce;
        public boolean valid;

        public Activate() {}

        Activate(UUID nonce) {
            this.nonce = nonce;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeLong(nonce.getMostSignificantBits());
            buf.writeLong(nonce.getLeastSignificantBits());
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = buf.readableBytes() == 16;
            if (valid) nonce = new UUID(buf.readLong(), buf.readLong());
        }
    }

    public static final class ReadingHandler implements IMessageHandler<Reading, IMessage> {

        @Override
        public IMessage onMessage(Reading message, MessageContext context) {
            if (message.valid) receive(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void receive(Reading message) {
            com.miaokatze.gtsr.client.lore.GuiSpacetimeAltarStory.receive(message);
        }
    }

    public static final class ActivateHandler implements IMessageHandler<Activate, IMessage> {

        @Override
        public IMessage onMessage(Activate message, MessageContext context) {
            if (message.valid && context.getServerHandler() != null && PENDING.size() < 256)
                PENDING.put(context.getServerHandler().playerEntity, message.nonce);
            return null;
        }
    }

    public static final class Drain {

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            for (Map.Entry<EntityPlayerMP, UUID> row : PENDING.entrySet())
                if (PENDING.remove(row.getKey(), row.getValue()))
                    SpacetimeAltarStory.activateFromReading(row.getKey(), row.getValue());
        }
    }
}
