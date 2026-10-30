package com.miaokatze.gtsr.common.dimension.prosperity.travel;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;

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
import io.netty.buffer.ByteBuf;

/** One byte Alt intent; a vanilla right-click on the same stack must authorize it first. */
public final class AnchorIntent {

    private static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_anchor");
    private static final ConcurrentLinkedQueue<Pending> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static final Map<UUID, Pending> WAITING = new HashMap<>();
    private static final Map<UUID, Use> USES = new HashMap<>();
    // ticksExisted belongs to an entity lifetime, not a UUID: death/relogin creates a new clock.
    private static final Map<UUID, LastMark> LAST = new HashMap<>();

    private AnchorIntent() {}

    public static void resetServerState() {
        synchronized (QUEUE) {
            QUEUE.clear();
            QUEUED.set(0);
            WAITING.clear();
            USES.clear();
            LAST.clear();
        }
    }

    public static void init() {
        NET.registerMessage(Handler.class, Intent.class, 0, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new Drain());
    }

    public static void alt() {
        NET.sendToServer(new Intent());
    }

    public static void used(EntityPlayerMP p, ItemStack stack) {
        USES.put(p.getUniqueID(), new Use(stack, p));
    }

    private static final class Use {

        final ItemStack stack;
        final int tick;
        final EntityPlayerMP player;

        Use(ItemStack s, EntityPlayerMP p) {
            stack = s;
            tick = p.ticksExisted;
            player = p;
        }
    }

    private static final class LastMark {

        final WeakReference<EntityPlayerMP> player;
        final int tick;

        LastMark(EntityPlayerMP p) {
            player = new WeakReference<>(p);
            tick = p.ticksExisted;
        }
    }

    private static final class Pending {

        final NetHandlerPlayServer handler;
        final EntityPlayerMP player;
        final long receivedNanos;
        int firstTick;

        Pending(NetHandlerPlayServer h) {
            handler = h;
            player = h.playerEntity;
            receivedNanos = System.nanoTime();
        }
    }

    public static final class Intent implements IMessage {

        boolean valid;

        public void toBytes(ByteBuf b) {
            b.writeByte(1);
        }

        public void fromBytes(ByteBuf b) {
            valid = b.readableBytes() == 1 && b.readUnsignedByte() == 1;
        }
    }

    public static final class Handler implements IMessageHandler<Intent, IMessage> {

        public IMessage onMessage(Intent m, MessageContext c) {
            if (m.valid && c.getServerHandler() != null) {
                synchronized (QUEUE) {
                    if (QUEUED.get() >= 128) return null;
                    QUEUE.add(new Pending(c.getServerHandler()));
                    QUEUED.incrementAndGet();
                }
            }
            return null;
        }
    }

    public static final class Drain {

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            for (int drained = 0; drained < 128; drained++) {
                Pending incoming;
                synchronized (QUEUE) {
                    incoming = QUEUE.poll();
                    if (incoming == null) break;
                }
                EntityPlayerMP p = incoming.player;
                if (p == null || incoming.handler.playerEntity != p || p.playerNetServerHandler != incoming.handler
                    || !incoming.handler.netManager.isChannelOpen()
                    || System.nanoTime() - incoming.receivedNanos > 500000000L) {
                    QUEUED.decrementAndGet();
                    continue;
                }
                UUID id = p.getUniqueID();
                incoming.firstTick = p.ticksExisted;
                if (WAITING.containsKey(id)) {
                    QUEUED.decrementAndGet();
                    continue;
                }
                WAITING.put(id, incoming);
            }
            java.util.Iterator<Map.Entry<UUID, Pending>> it = WAITING.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Pending> entry = it.next();
                Pending pending = entry.getValue();
                EntityPlayerMP p = pending.player;
                boolean connected = p != null && pending.handler.playerEntity == p
                    && p.playerNetServerHandler == pending.handler && pending.handler.netManager.isChannelOpen();
                Use use = USES.get(entry.getKey());
                if (connected && use == null
                    && p.ticksExisted - pending.firstTick < 5
                    && System.nanoTime() - pending.receivedNanos <= 500000000L) continue;
                it.remove();
                QUEUED.decrementAndGet();
                if (!connected) {
                    if (use != null && use.player == p) USES.remove(entry.getKey());
                    LastMark last = LAST.get(entry.getKey());
                    if (last != null && last.player.get() == p) LAST.remove(entry.getKey());
                    continue;
                }
                ItemStack held = p.getHeldItem();
                if (use == null || use.player != p
                    || System.nanoTime() - pending.receivedNanos > 500000000L
                    || p.ticksExisted - use.tick < 0
                    || p.ticksExisted - use.tick > 5
                    || held != use.stack
                    || held == null
                    || !(held.getItem() instanceof SpacetimeAnchorBeacon)
                    || !SpacetimeTravel.eligible(p)) continue;
                LastMark last = LAST.get(entry.getKey());
                if (last != null && last.player.get() == p && p.ticksExisted - last.tick < 5) continue;
                USES.remove(entry.getKey());
                LAST.put(entry.getKey(), new LastMark(p));
                p.stopUsingItem();
                SpacetimeTravel.mark(p, held);
            }
            USES.entrySet()
                .removeIf(
                    e -> e.getValue().tick + 10 < e.getValue().player.ticksExisted
                        || !SpacetimeTravel.eligible(e.getValue().player));
            if (LAST.size() > 1024) LAST.clear();
        }
    }
}
