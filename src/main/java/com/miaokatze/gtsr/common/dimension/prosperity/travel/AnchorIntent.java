package com.miaokatze.gtsr.common.dimension.prosperity.travel;

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
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private AnchorIntent() {}

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

    private static final class Pending {

        final NetHandlerPlayServer handler;
        final long receivedNanos;
        int firstTick;

        Pending(NetHandlerPlayServer h) {
            handler = h;
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
                if (QUEUED.incrementAndGet() > 128) {
                    QUEUED.decrementAndGet();
                    return null;
                }
                QUEUE.add(new Pending(c.getServerHandler()));
            }
            return null;
        }
    }

    public static final class Drain {

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Pending incoming;
            while ((incoming = QUEUE.poll()) != null) {
                EntityPlayerMP p = incoming.handler.playerEntity;
                if (p == null || p.playerNetServerHandler != incoming.handler
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
                EntityPlayerMP p = pending.handler.playerEntity;
                boolean connected = p != null && p.playerNetServerHandler == pending.handler
                    && pending.handler.netManager.isChannelOpen();
                Use use = USES.get(entry.getKey());
                if (connected && use == null
                    && p.ticksExisted - pending.firstTick < 5
                    && System.nanoTime() - pending.receivedNanos <= 500000000L) continue;
                it.remove();
                QUEUED.decrementAndGet();
                if (!connected) {
                    USES.remove(entry.getKey());
                    LAST.remove(entry.getKey());
                    continue;
                }
                ItemStack held = p.getHeldItem();
                if (use == null || System.nanoTime() - pending.receivedNanos > 500000000L
                    || p.ticksExisted - use.tick > 5
                    || held != use.stack
                    || held == null
                    || !(held.getItem() instanceof SpacetimeAnchorBeacon)
                    || !SpacetimeTravel.eligible(p)) continue;
                int last = LAST.getOrDefault(entry.getKey(), -100);
                if (p.ticksExisted - last < 5) continue;
                USES.remove(entry.getKey());
                LAST.put(entry.getKey(), p.ticksExisted);
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
