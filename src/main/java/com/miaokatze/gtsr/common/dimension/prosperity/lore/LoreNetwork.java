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
        NETWORK.registerMessage(TitleHandler.class, StructureTitle.class, 1, Side.CLIENT);
    }

    public static void send(EntityPlayerMP player, boolean open) {
        if (player.playerNetServerHandler != null) NETWORK.sendTo(new Snapshot(player, open), player);
    }

    public static final class Snapshot implements IMessage {

        public int dimension, progress, sceneEntries, sceneBattles;
        public UUID player;
        public boolean open, kingUnlocked, valid;

        public Snapshot() {}

        Snapshot(EntityPlayerMP p, boolean open) {
            dimension = p.dimension;
            player = p.getUniqueID();
            this.open = open;
            kingUnlocked = LoreRegistry.kingChapterUnlocked(p);
            progress = HistoryProgress.snapshot(p);
            sceneEntries = HistoryProgress.sceneEntries(p);
            sceneBattles = HistoryProgress.sceneBattles(p);
            valid = true;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = false;
            if (buf.readableBytes() != 34) return;
            dimension = buf.readInt();
            player = new UUID(buf.readLong(), buf.readLong());
            int openByte = buf.readUnsignedByte();
            int kingByte = buf.readUnsignedByte();
            progress = buf.readInt();
            sceneEntries = buf.readInt();
            sceneBattles = buf.readInt();
            if (openByte > 1 || kingByte > 1
                || (progress & ~HistoryProgress.VALID_MASK) != 0
                || (sceneEntries & ~HistoryProgress.VALID_MASK) != 0
                || (sceneBattles & ~HistoryProgress.VALID_MASK) != 0) return;
            open = openByte == 1;
            kingUnlocked = kingByte == 1;
            valid = true;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(dimension);
            buf.writeLong(player.getMostSignificantBits());
            buf.writeLong(player.getLeastSignificantBits());
            buf.writeBoolean(open);
            buf.writeBoolean(kingUnlocked);
            buf.writeInt(progress);
            buf.writeInt(sceneEntries);
            buf.writeInt(sceneBattles);
        }
    }

    public static void title(EntityPlayerMP player, String key) {
        if (player.playerNetServerHandler != null) NETWORK.sendTo(new StructureTitle(player, key), player);
    }

    public static final class StructureTitle implements IMessage {

        public int dimension;
        public UUID player;
        public String key = "";
        public boolean valid;

        public StructureTitle() {}

        StructureTitle(EntityPlayerMP p, String key) {
            dimension = p.dimension;
            player = p.getUniqueID();
            this.key = key;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(dimension);
            buf.writeLong(player.getMostSignificantBits());
            buf.writeLong(player.getLeastSignificantBits());
            cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, key);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = false;
            if (buf.readableBytes() < 22 || buf.readableBytes() > 180) return;
            dimension = buf.readInt();
            player = new UUID(buf.readLong(), buf.readLong());
            try {
                key = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
            } catch (RuntimeException malformed) {
                return;
            }
            boolean known = key.equals("lore.chapter.hanging_great_tree.title");
            for (String id : com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES)
                known |= key.equals("lore.entry.structures." + id + ".title");
            valid = !buf.isReadable() && key.length() <= 120 && known;
        }
    }

    public static final class TitleHandler implements IMessageHandler<StructureTitle, IMessage> {

        @Override
        public IMessage onMessage(StructureTitle message, MessageContext context) {
            if (message.valid) LoreClient.receiveTitle(message);
            return null;
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
