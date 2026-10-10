package com.miaokatze.gtsr.common.dimension.prosperity.travel;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.altar.SpacetimeAltarNavigationSearch;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterData;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;

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

/** Session handshake carries no coordinates. Target selection belongs entirely to the server. */
public final class BeaconNavigationNetwork {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_beacon");
    private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static final Map<EntityPlayerMP, Session> SESSIONS = new WeakHashMap<>();

    private BeaconNavigationNetwork() {}

    public static void register() {
        NETWORK.registerMessage(HelloHandler.class, Hello.class, 0, Side.SERVER);
        NETWORK.registerMessage(TargetHandler.class, Target.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new BeaconNavigationNetwork());
    }

    public static void hello(long nonce, int dimension) {
        NETWORK.sendToServer(new Hello(nonce, dimension));
    }

    private static boolean carries(EntityPlayerMP player) {
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack != null && stack.stackSize > 0 && stack.getItem() instanceof SpacetimeAnchorBeacon) return true;
        return false;
    }

    private static boolean connected(EntityPlayerMP player) {
        return player != null && player.isEntityAlive()
            && player.playerNetServerHandler != null
            && player.playerNetServerHandler.netManager.isChannelOpen();
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SpacetimeAltarNavigationSearch.beginTick();
        for (int i = 0; i < 32; i++) {
            Pending pending = QUEUE.poll();
            if (pending == null) break;
            QUEUED.decrementAndGet();
            EntityPlayerMP player = pending.player;
            if (connected(player) && pending.hello.dimension == player.dimension) {
                Session session = SESSIONS.get(player);
                if (session == null || session.world != player.worldObj
                    || session.connection != player.playerNetServerHandler
                    || session.nonce != pending.hello.nonce) {
                    session = new Session(player, pending.hello.nonce);
                    SESSIONS.put(player, session);
                }
                // Ack even without a beacon. Repeated hello packets do not reset targets or cause index queries.
                if (session.lastHelloTick == Long.MIN_VALUE || player.ticksExisted - session.lastHelloTick >= 80) {
                    Target acknowledgement = new Target(session.nonce, player.dimension, null);
                    acknowledgement.acknowledgement = true;
                    NETWORK.sendTo(acknowledgement, player);
                    session.lastHelloTick = player.ticksExisted;
                }
            }
        }
        java.util.Iterator<Map.Entry<EntityPlayerMP, Session>> iterator = SESSIONS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityPlayerMP, Session> entry = iterator.next();
            EntityPlayerMP player = entry.getKey();
            Session session = entry.getValue();
            if (!connected(player) || player.worldObj != session.world
                || player.playerNetServerHandler != session.connection) {
                iterator.remove();
                continue;
            }
            if (!carries(player)) continue;
            if (player.dimension == 0)
                SpacetimeAltarNavigationSearch.keepAlive(player.worldObj, player.posX, player.posZ);
            if (player.ticksExisted % 40 != 0) continue;
            int[] nearest = nearest(player);
            NETWORK.sendTo(new Target(session.nonce, player.dimension, nearest), player);
        }
        SpacetimeAltarNavigationSearch.tick();
    }

    static int[] nearest(EntityPlayerMP player) {
        if (player.dimension == 0) {
            return SpacetimeAltarNavigationSearch.nearest(player.worldObj, player.posX, player.posZ);
        }
        if (!(player.worldObj.provider instanceof WorldProviderProsperityRuins)) return null;
        RemasterSite nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        // generatedOwners excludes merely planned/saved scenes and never loads chunks.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (RemasterData.GeneratedOwner owner : RemasterData.get(player.worldObj)
            .generatedOwners()) {
            RemasterSite site = owner.site;
            if (site.seed != player.worldObj.getSeed() || !seen.add(site.id())) continue;
            double dx = site.entryX() - player.posX, dz = site.entryZ() - player.posZ;
            double candidate = dx * dx + dz * dz;
            if (candidate < distance || (candidate == distance && nearest != null
                && site.id()
                    .compareTo(nearest.id()) < 0)) {
                nearest = site;
                distance = candidate;
            }
        }
        return nearest == null ? null : new int[] { nearest.entryX(), nearest.entryZ() };
    }

    private static final class Session {

        final World world;
        final net.minecraft.network.NetHandlerPlayServer connection;
        final long nonce;
        long lastHelloTick = Long.MIN_VALUE;

        Session(EntityPlayerMP player, long nonce) {
            world = player.worldObj;
            connection = player.playerNetServerHandler;
            this.nonce = nonce;
        }
    }

    private static final class Pending {

        final EntityPlayerMP player;
        final Hello hello;

        Pending(EntityPlayerMP player, Hello hello) {
            this.player = player;
            this.hello = hello;
        }
    }

    public static final class Hello implements IMessage {

        long nonce;
        int dimension;
        boolean valid;

        public Hello() {}

        Hello(long nonce, int dimension) {
            this.nonce = nonce;
            this.dimension = dimension;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeLong(nonce);
            buf.writeInt(dimension);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = false;
            if (buf.readableBytes() != 12) return;
            nonce = buf.readLong();
            dimension = buf.readInt();
            valid = nonce != 0;
        }
    }

    public static final class Target implements IMessage {

        public long nonce;
        public int dimension, x, z;
        public boolean found, valid, acknowledgement;

        public Target() {}

        Target(long nonce, int dimension, int[] position) {
            this.nonce = nonce;
            this.dimension = dimension;
            found = position != null;
            if (found) {
                x = position[0];
                z = position[1];
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeLong(nonce);
            buf.writeInt(dimension);
            buf.writeBoolean(found);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeBoolean(acknowledgement);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = false;
            if (buf.readableBytes() != 22) return;
            nonce = buf.readLong();
            dimension = buf.readInt();
            found = buf.readBoolean();
            x = buf.readInt();
            z = buf.readInt();
            acknowledgement = buf.readBoolean();
            valid = nonce != 0 && Math.abs((long) x) <= 30000000 && Math.abs((long) z) <= 30000000;
        }
    }

    public static final class HelloHandler implements IMessageHandler<Hello, IMessage> {

        @Override
        public IMessage onMessage(Hello hello, MessageContext context) {
            if (!hello.valid) return null;
            if (QUEUED.incrementAndGet() > 64) {
                QUEUED.decrementAndGet();
                return null;
            }
            QUEUE.add(new Pending(context.getServerHandler().playerEntity, hello));
            return null;
        }
    }

    public static final class TargetHandler implements IMessageHandler<Target, IMessage> {

        @Override
        public IMessage onMessage(Target target, MessageContext context) {
            if (target.valid) receive(target);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void receive(Target target) {
            com.miaokatze.gtsr.client.travel.BeaconNavigationClient.receive(target);
        }
    }
}
