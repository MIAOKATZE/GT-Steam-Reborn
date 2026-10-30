package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtsr.client.lore.NavigatorClient;
import com.miaokatze.gtsr.common.commands.RuinLocateCommand;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.network.ServerPacketAuthority;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Inventory witness and bounded C2S query; world reads run only on the server tick thread. */
public final class NavigatorNetwork {

    public static final String RELIC_ID = "future_city_address_witness";
    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_navigator");
    private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static final Deque<LocateJob> ACTIVE = new ArrayDeque<>();
    private static final long TICK_BUDGET_NS = 10000000L, SESSION_TTL_NS = 55000000000L;
    private static final Map<EntityPlayerMP, QueryStamp> LAST_QUERY = new WeakHashMap<>();
    private static final Map<UUID, WeakReference<EntityItem>> PENDING_GIFTS = new HashMap<>();

    private NavigatorNetwork() {}

    public static void register() {
        NETWORK.registerMessage(QueryHandler.class, Query.class, 0, Side.SERVER);
        NETWORK.registerMessage(ResultHandler.class, Result.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new NavigatorNetwork());
    }

    public static void resetServerState() {
        synchronized (QUEUE) {
            QUEUE.clear();
            QUEUED.set(0);
        }
        ACTIVE.clear();
        LAST_QUERY.clear();
        PENDING_GIFTS.clear();
    }

    public static boolean holdsCompass(EntityPlayer player) {
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack != null && stack.stackSize > 0 && stack.getItem() == LoreRegistry.RELICS.get(RELIC_ID))
                return true;
        return false;
    }

    public static boolean giveCompass(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer
            || player.worldObj.isRemote
            || !(player.worldObj.provider instanceof WorldProviderProsperityRuins)
            || !player.isEntityAlive()
            || holdsCompass(player)) return false;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        WeakReference<EntityItem> reference = PENDING_GIFTS.get(mp.getUniqueID());
        EntityItem pending = reference == null ? null : reference.get();
        if (pending != null && !pending.isDead
            && pending.worldObj == player.worldObj
            && pending.getEntityItem() != null
            && pending.getEntityItem().stackSize > 0) return false;
        ItemStack gift = new ItemStack(LoreRegistry.RELICS.get(RELIC_ID));
        if (!player.inventory.addItemStackToInventory(gift)) {
            EntityItem drop = new EntityItem(player.worldObj, player.posX, player.posY + .3, player.posZ, gift);
            drop.delayBeforeCanPickup = 10;
            drop.func_145797_a(player.getCommandSenderName());
            drop.func_145799_b(player.getCommandSenderName());
            if (!player.worldObj.spawnEntityInWorld(drop) || drop.isDead) return false;
            PENDING_GIFTS.put(mp.getUniqueID(), new WeakReference<>(drop));
        }
        HistoryProgress.recordGrantedRelic(player, RELIC_ID);
        mp.inventoryContainer.detectAndSendChanges();
        LoreNetwork.send(mp, false);
        return true;
    }

    public static void open(EntityPlayerMP player) {
        if (eligible(player)) NETWORK.sendTo(new Result(player, -1, 0, 0, 0), player);
        else player.addChatMessage(new ChatComponentTranslation("gtsr.navigator.dimension"));
    }

    private static boolean eligible(EntityPlayerMP player) {
        return ServerPacketAuthority.isCurrentPlayer(player) && !(player instanceof FakePlayer)
            && player.isEntityAlive()
            && !player.worldObj.isRemote
            && player.worldObj instanceof WorldServer
            && player.worldObj.provider instanceof WorldProviderProsperityRuins
            && holdsCompass(player);
    }

    public static void query(int index, int serial) {
        NETWORK.sendToServer(new Query(index, serial));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Dropped items belong to the world, not this transient inventory witness cache.
        PENDING_GIFTS.values().removeIf(reference -> {
            EntityItem item = reference.get();
            return item == null || item.isDead;
        });
        // Netty only enqueues; admission and all world access belong to this thread.
        for (int count = 0; count < 4; count++) {
            Pending pending;
            synchronized (QUEUE) {
                pending = QUEUE.poll();
                if (pending == null) break;
                QUEUED.decrementAndGet();
            }
            if (System.nanoTime() - pending.created <= SESSION_TTL_NS
                && ServerPacketAuthority.isCurrentPlayer(pending.player, pending.connection))
                execute(pending.player, pending.query);
        }
        long deadline = System.nanoTime() + TICK_BUDGET_NS;
        for (int steps = 0; steps < 1024 && !ACTIVE.isEmpty() && System.nanoTime() < deadline; steps++) {
            LocateJob job = ACTIVE.removeFirst();
            if (!job.valid()) continue;
            if (System.nanoTime() - job.created > SESSION_TTL_NS) {
                job.expired.run();
                continue;
            }
            job.lookup.step();
            if (job.lookup.done()) job.complete.accept(job.lookup.result());
            else ACTIVE.addLast(job);
        }
    }

    /** Main-thread admission shared with locate/tplocate; one session per player and four globally. */
    public static boolean submitLocate(EntityPlayerMP player, RuinLocateCommand.Lookup lookup,
        Consumer<RuinLocateCommand.Location> complete) {
        return submitLocate(
            player,
            lookup,
            complete,
            () -> player.addChatMessage(new ChatComponentTranslation("gtsr.navigator.timeout")),
            false);
    }

    private static boolean submitLocate(EntityPlayerMP player, RuinLocateCommand.Lookup lookup,
        Consumer<RuinLocateCommand.Location> complete, Runnable expired, boolean compass) {
        if (!ServerPacketAuthority.isCurrentPlayer(player) || !player.isEntityAlive() || ACTIVE.size() >= 4)
            return false;
        for (LocateJob job : ACTIVE) if (job.player == player) return false;
        ACTIVE.addLast(new LocateJob(player, lookup, complete, expired, compass));
        return true;
    }

    private static final class LocateJob {

        final EntityPlayerMP player;
        final net.minecraft.world.World world;
        final net.minecraft.network.NetHandlerPlayServer connection;
        final RuinLocateCommand.Lookup lookup;
        final Consumer<RuinLocateCommand.Location> complete;
        final Runnable expired;
        final boolean compass;
        final long created = System.nanoTime();

        LocateJob(EntityPlayerMP player, RuinLocateCommand.Lookup lookup, Consumer<RuinLocateCommand.Location> complete,
            Runnable expired, boolean compass) {
            this.player = player;
            world = player.worldObj;
            connection = player.playerNetServerHandler;
            this.lookup = lookup;
            this.complete = complete;
            this.expired = expired;
            this.compass = compass;
        }

        boolean valid() {
            return ServerPacketAuthority.isCurrentPlayer(player, connection) && player.isEntityAlive()
                && player.worldObj == world
                && !world.isRemote
                && (!compass || eligible(player));
        }
    }

    static void execute(EntityPlayerMP player, Query query) {
        if (!eligible(player) || !query.valid || player.playerNetServerHandler == null) return;
        List<String> ids = RuinLocateCommand.names();
        if (query.index < 0 || query.index >= ids.size()) return;
        long now = System.nanoTime();
        QueryStamp last = LAST_QUERY.get(player);
        if (last != null && last.player.get() == player
            && last.connection.get() == player.playerNetServerHandler && now - last.time < 1000000000L) {
            NETWORK.sendTo(new Result(player, query.index, query.serial, 3, 0), player);
            return;
        }
        LAST_QUERY.remove(player);
        LAST_QUERY.put(player, new QueryStamp(player, now));
        WorldServer world = (WorldServer) player.worldObj;
        int ox = MathHelper.floor_double(player.posX), oz = MathHelper.floor_double(player.posZ);
        String id = ids.get(query.index);
        if (!submitLocate(
            player,
            RuinLocateCommand.beginNearest(world.getSeed(), id, ox, oz, world),
            nearest -> respond(player, query, id, ox, oz, nearest),
            () -> NETWORK.sendTo(new Result(player, query.index, query.serial, 4, 0), player),
            true)) NETWORK.sendTo(new Result(player, query.index, query.serial, 3, 0), player);
    }

    private static void respond(EntityPlayerMP player, Query query, String id, int ox, int oz,
        RuinLocateCommand.Location nearest) {
        Result result = new Result(
            player,
            query.index,
            query.serial,
            nearest == null ? 2 : 1,
            nearest == null ? 0 : (int) Math.round(Math.hypot(nearest.x - (double) ox, nearest.z - (double) oz)));
        if (nearest != null) {
            result.x = nearest.x;
            result.z = nearest.z;
        }
        NETWORK.sendTo(result, player);
        if (nearest != null) player.addChatMessage(
            new ChatComponentTranslation(
                "gtsr.navigator.found",
                new ChatComponentTranslation(
                    "forgotten_lake_court".equals(id) ? "lore.chapter.hanging_great_tree.title"
                        : "lore.entry.structures." + id + ".title"),
                nearest.x,
                nearest.z,
                result.distance));
    }

    private static final class QueryStamp {

        final WeakReference<EntityPlayerMP> player;
        final WeakReference<NetHandlerPlayServer> connection;
        final long time;

        QueryStamp(EntityPlayerMP player, long time) {
            this.player = new WeakReference<>(player);
            connection = new WeakReference<>(player.playerNetServerHandler);
            this.time = time;
        }
    }

    private static final class Pending {

        final EntityPlayerMP player;
        final NetHandlerPlayServer connection;
        final Query query;
        final long created = System.nanoTime();

        Pending(NetHandlerPlayServer connection, Query query) {
            this.connection = connection;
            this.player = connection.playerEntity;
            this.query = query;
        }
    }

    public static final class Query implements IMessage {

        public int index, serial;
        public boolean valid;

        public Query() {}

        Query(int index, int serial) {
            this.index = index;
            this.serial = serial;
            valid = true;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(index);
            buf.writeInt(serial);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = false;
            if (buf.readableBytes() != 8) return;
            index = buf.readInt();
            serial = buf.readInt();
            valid = index >= 0 && index < 64 && serial > 0;
        }
    }

    public static final class Result implements IMessage {

        public UUID player;
        public int dimension, index, serial, status, x, z, distance;
        public boolean valid;

        public Result() {}

        Result(EntityPlayerMP player, int index, int serial, int status, int distance) {
            this.player = player.getUniqueID();
            dimension = player.dimension;
            this.index = index;
            this.serial = serial;
            this.status = status;
            this.distance = distance;
            valid = true;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeLong(player.getMostSignificantBits());
            buf.writeLong(player.getLeastSignificantBits());
            buf.writeInt(dimension);
            buf.writeInt(index);
            buf.writeInt(serial);
            buf.writeInt(status);
            buf.writeInt(x);
            buf.writeInt(z);
            buf.writeInt(distance);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            valid = false;
            if (buf.readableBytes() != 44) return;
            player = new UUID(buf.readLong(), buf.readLong());
            dimension = buf.readInt();
            index = buf.readInt();
            serial = buf.readInt();
            status = buf.readInt();
            x = buf.readInt();
            z = buf.readInt();
            distance = buf.readInt();
            valid = (index == -1 && status == 0 && serial == 0)
                || (index >= 0 && index < 64 && serial > 0 && status >= 1 && status <= 4 && distance >= 0);
        }
    }

    public static final class QueryHandler implements IMessageHandler<Query, IMessage> {

        @Override
        public IMessage onMessage(Query query, MessageContext context) {
            if (!query.valid || context.getServerHandler() == null) return null;
            synchronized (QUEUE) {
                if (QUEUED.get() >= 64) return null;
                QUEUE.add(new Pending(context.getServerHandler(), query));
                QUEUED.incrementAndGet();
            }
            return null;
        }
    }

    public static final class ResultHandler implements IMessageHandler<Result, IMessage> {

        @Override
        public IMessage onMessage(Result result, MessageContext context) {
            if (result.valid) NavigatorClient.receive(result);
            return null;
        }
    }
}
