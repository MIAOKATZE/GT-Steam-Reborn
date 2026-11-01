package com.miaokatze.gtsr.common.machinevisual;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.miaokatze.gtsr.common.network.ServerPacketAuthority;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/** Bounded subscribe/echo protocol. A pushed frame never creates a client installation. */
public final class MachineVisualNetwork {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_workvis");
    private static final long SESSION = new java.security.SecureRandom().nextLong();
    private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static final Map<EntityPlayerMP, Rate> RATES = new WeakHashMap<>();

    private MachineVisualNetwork() {}

    public static void register() {
        NETWORK.registerMessage(SubscribeHandler.class, Subscribe.class, 0, Side.SERVER);
        NETWORK.registerMessage(FrameHandler.class, Frame.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new MachineVisualNetwork());
    }

    static long session() {
        return SESSION;
    }

    public static void request(MachineVisualHost host, long nonce) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        Subscribe request = new Subscribe();
        request.dimension = tile.getWorld().provider.dimensionId;
        request.x = tile.getXCoord();
        request.y = tile.getYCoord();
        request.z = tile.getZCoord();
        request.nonce = nonce;
        NETWORK.sendToServer(request);
    }

    static void send(EntityPlayerMP player, MachineVisualSnapshot snapshot) {
        Frame message = new Frame();
        message.snapshot = snapshot;
        NETWORK.sendTo(message, player);
    }

    static boolean canObserve(EntityPlayerMP player, MachineVisualHost host) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        return ServerPacketAuthority.isCurrentPlayer(player) && player.worldObj == tile.getWorld()
            && tile.getWorld() instanceof WorldServer server
            && !((TileEntity) tile).isInvalid()
            && tile.getMetaTileEntity() == host
            && player.getDistanceSq(tile.getXCoord() + .5, tile.getYCoord() + .5, tile.getZCoord() + .5) <= 4096
            && server.getPlayerManager()
                .isPlayerWatchingChunk(player, tile.getXCoord() >> 4, tile.getZCoord() >> 4);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < 64; i++) {
            Pending pending;
            synchronized (QUEUE) {
                pending = QUEUE.poll();
                if (pending == null) break;
                QUEUED.decrementAndGet();
            }
            EntityPlayerMP player = pending.player;
            Subscribe r = pending.request;
            if (!ServerPacketAuthority.isCurrentPlayer(player, pending.connection) || player.dimension != r.dimension
                || !player.worldObj.blockExists(r.x, r.y, r.z)) continue;
            Rate rate = RATES.computeIfAbsent(player, ignored -> new Rate());
            if (player.ticksExisted < rate.tick || player.ticksExisted - rate.tick >= 20) {
                rate.tick = player.ticksExisted;
                rate.count = 0;
            }
            if (++rate.count > 64) continue;
            TileEntity found = player.worldObj.getTileEntity(r.x, r.y, r.z);
            if (found instanceof IGregTechTileEntity tile && tile.getMetaTileEntity() instanceof MachineVisualHost host
                && canObserve(player, host))
                host.visualState()
                    .subscribe(host, player, r.nonce);
        }
    }

    private static final class Rate {

        int tick, count;
    }

    private static final class Pending {

        final EntityPlayerMP player;
        final NetHandlerPlayServer connection;
        final Subscribe request;

        Pending(EntityPlayerMP player, Subscribe request) {
            this.player = player;
            this.connection = player.playerNetServerHandler;
            this.request = request;
        }
    }

    public static final class Subscribe implements IMessage {

        int dimension, x, y, z;
        long nonce;

        public Subscribe() {}

        @Override
        public void fromBytes(ByteBuf buffer) {
            if (buffer.readableBytes() != 24) throw new IllegalArgumentException("Machine subscription length");
            dimension = buffer.readInt();
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
            nonce = buffer.readLong();
            if (y < 0 || y >= 256) throw new IllegalArgumentException("Machine subscription height");
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(dimension)
                .writeInt(x)
                .writeInt(y)
                .writeInt(z)
                .writeLong(nonce);
        }
    }

    public static final class Frame implements IMessage {

        MachineVisualSnapshot snapshot;

        public Frame() {}

        @Override
        public void fromBytes(ByteBuf buffer) {
            snapshot = MachineVisualSnapshot.read(buffer);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            snapshot.write(buffer);
        }
    }

    public static final class SubscribeHandler implements IMessageHandler<Subscribe, IMessage> {

        @Override
        public IMessage onMessage(Subscribe message, MessageContext context) {
            synchronized (QUEUE) {
                if (QUEUED.get() >= 256) return null;
                QUEUE.add(new Pending(context.getServerHandler().playerEntity, message));
                QUEUED.incrementAndGet();
            }
            return null;
        }
    }

    public static final class FrameHandler implements IMessageHandler<Frame, IMessage> {

        @Override
        public IMessage onMessage(Frame message, MessageContext context) {
            GTSteamReborn.proxy.receiveMachineVisual(message.snapshot);
            return null;
        }
    }
}
