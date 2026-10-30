package com.miaokatze.gtsr.common.dimension.prosperity.travel;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.buffer.Unpooled;

/** Actual AnchorIntent queue/drain and MC player/stack classes, with fixture network and mark sinks. */
public final class AnchorIntentLifecycleCheck {
    private static sun.misc.Unsafe unsafe;
    private static int checks;
    private static int failures;
    private static int entityId;

    public static final class Player extends EntityPlayerMP {
        UUID id;
        ItemStack held;
        Player() { super(null, null, null, null); }
        @Override public UUID getUniqueID() { return id; }
        @Override public ItemStack getHeldItem() { return held; }
        @Override public void stopUsingItem() {}
    }

    public static final class Connection extends NetworkManager {
        Connection() { super(false); }
        @Override public boolean isChannelOpen() { return true; }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Object stat(String name) throws Exception { return field(AnchorIntent.class, name).get(null); }

    private static void reset() throws Exception {
        for (String name : new String[] { "WAITING", "USES", "LAST" }) ((Map<?, ?>) stat(name)).clear();
        ((ConcurrentLinkedQueue<?>) stat("QUEUE")).clear();
        ((AtomicInteger) stat("QUEUED")).set(0);
        SpacetimeTravel.class.getField("marks").setInt(null, 0);
    }

    private static int marks() throws Exception { return SpacetimeTravel.class.getField("marks").getInt(null); }

    private static Player player(UUID id, int tick, ItemStack held) throws Exception {
        Player p = (Player) unsafe.allocateInstance(Player.class);
        p.setEntityId(++entityId);
        p.id = id;
        p.ticksExisted = tick;
        p.held = held;
        NetHandlerPlayServer handler = (NetHandlerPlayServer) unsafe.allocateInstance(NetHandlerPlayServer.class);
        handler.playerEntity = p;
        Field manager = field(NetHandlerPlayServer.class, "netManager");
        unsafe.putObject(handler, unsafe.objectFieldOffset(manager), new Connection());
        p.playerNetServerHandler = handler;
        return p;
    }

    @SuppressWarnings("unchecked")
    private static void enqueue(Player p) throws Exception {
        Class<?> pending = Class.forName(AnchorIntent.class.getName() + "$Pending");
        java.lang.reflect.Constructor<?> ctor = pending.getDeclaredConstructor(NetHandlerPlayServer.class);
        ctor.setAccessible(true);
        ((ConcurrentLinkedQueue<Object>) stat("QUEUE")).add(ctor.newInstance(p.playerNetServerHandler));
        ((AtomicInteger) stat("QUEUED")).incrementAndGet();
    }

    private static void intent(Player p) throws Exception {
        enqueue(p);
        new AnchorIntent.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            failures++;
            System.err.println("FAIL " + message);
        }
    }

    public static void main(String[] args) throws Exception {
        unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        reset();
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ItemStack beacon = new ItemStack(new SpacetimeAnchorBeacon());
        Player old = player(id, 120000, beacon);
        AnchorIntent.used(old, beacon);
        intent(old);
        check(marks() == 1, "authorized same-session mark");
        old.ticksExisted++;
        AnchorIntent.used(old, beacon);
        intent(old);
        check(marks() == 1, "same-session five-tick limiter remains enforced");
        old.ticksExisted += 4;
        AnchorIntent.used(old, beacon);
        intent(old);
        check(marks() == 2, "same-session mark works after five ticks");
        Player replacement = player(id, 1, beacon);
        // Vanilla respawn deliberately reuses the old numeric entity ID (Entity.equals is ID-based).
        replacement.setEntityId(old.getEntityId());
        AnchorIntent.used(replacement, beacon);
        intent(replacement);
        check(marks() == 3, "respawn/reconnect must not inherit old entity tick cooldown");
        reset();
        old = player(id, 120000, beacon);
        replacement = player(id, 1, beacon);
        AnchorIntent.used(old, beacon);
        intent(replacement);
        check(marks() == 0, "old entity right-click must not authorize replacement entity");
        reset();
        Player fresh = player(id, 20, beacon);
        intent(fresh);
        check(marks() == 0, "packet alone cannot mark");
        AnchorIntent.used(fresh, beacon);
        fresh.ticksExisted += 6;
        intent(fresh);
        check(marks() == 0, "expired right-click cannot mark");
        AnchorIntent.Intent malformed = new AnchorIntent.Intent();
        malformed.fromBytes(Unpooled.wrappedBuffer(new byte[] { 1, 1 }));
        check(!malformed.valid, "trailing packet bytes rejected");
        reset(); old = player(id, 80, beacon); enqueue(old);
        replacement = player(id, 1, beacon);
        replacement.playerNetServerHandler = old.playerNetServerHandler;
        replacement.playerNetServerHandler.playerEntity = replacement;
        AnchorIntent.used(replacement, beacon);
        new AnchorIntent.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(marks() == 0, "old queued Alt intent cannot bind a respawned entity's new right-click");

        reset(); old = player(id, 80, beacon); intent(old);
        replacement = player(id, 1, beacon); replacement.playerNetServerHandler = old.playerNetServerHandler;
        replacement.playerNetServerHandler.playerEntity = replacement; AnchorIntent.used(replacement, beacon);
        new AnchorIntent.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(marks() == 0, "waiting Alt intent retains original entity identity across handler rebound");
        intent(replacement);check(marks() == 1, "retiring old waiting intent does not consume replacement entity authorization");

        reset(); fresh = player(id, 20, beacon); AnchorIntent.used(fresh, beacon); intent(fresh);
        enqueue(fresh); AnchorIntent.used(fresh, beacon);
        try {
            AnchorIntent.class.getMethod("resetServerState").invoke(null);
            check(((Map<?, ?>)stat("USES")).isEmpty() && ((Map<?, ?>)stat("LAST")).isEmpty()
                && ((Map<?, ?>)stat("WAITING")).isEmpty(), "stop clears authorization leases and waiting actors");
            check(((ConcurrentLinkedQueue<?>)stat("QUEUE")).isEmpty() && ((AtomicInteger)stat("QUEUED")).get()==0,
                "stop clears pending queue and quota together");
            AnchorIntent.class.getMethod("resetServerState").invoke(null);
            AnchorIntent.used(fresh, beacon); intent(fresh); check(marks()==2, "new authorization works after idempotent stop");
        } catch (NoSuchMethodException missing) { check(false, "anchor exposes stop cleanup hook"); }
        if (failures != 0) throw new AssertionError(failures + " lifecycle assertions failed");
        System.out.println("PASS AnchorIntentLifecycleCheck: " + checks + " assertions; fixture mark/network sinks, no game startup");
    }
}
