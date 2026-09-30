package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtsr.client.lore.LoreClient;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** S2C-only fixed-size permissions. No client packet can award a chapter. */
public final class LoreNetwork {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_lore");

    private LoreNetwork() {}

    public static void register() {
        NETWORK.registerMessage(Handler.class, Snapshot.class, 0, Side.CLIENT);
    }

    public static void send(EntityPlayerMP player, boolean open) {
        if (player.playerNetServerHandler != null) NETWORK.sendTo(new Snapshot(player, open), player);
    }

    public static final class Snapshot implements IMessage {

        public int dimension;
        public UUID player;
        public boolean open, kingUnlocked, valid;

        public Snapshot() {}

        Snapshot(EntityPlayerMP p, boolean open) {
            dimension = p.dimension;
            player = p.getUniqueID();
            this.open = open;
            kingUnlocked = LoreRegistry.kingChapterUnlocked(p);
            valid = true;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            if (buf.readableBytes() != 22) return;
            dimension = buf.readInt();
            player = new UUID(buf.readLong(), buf.readLong());
            open = buf.readBoolean();
            kingUnlocked = buf.readBoolean();
            valid = true;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(dimension);
            buf.writeLong(player.getMostSignificantBits());
            buf.writeLong(player.getLeastSignificantBits());
            buf.writeBoolean(open);
            buf.writeBoolean(kingUnlocked);
        }
    }

    public static final class Handler implements IMessageHandler<Snapshot, IMessage> {

        @Override
        public IMessage onMessage(Snapshot message, MessageContext context) {
            if (message.valid) LoreClient.receive(message);
            return null;
        }
    }
}
