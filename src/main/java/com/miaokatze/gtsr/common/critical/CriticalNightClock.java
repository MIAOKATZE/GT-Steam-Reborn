package com.miaokatze.gtsr.common.critical;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** Dimension-local time: never writes DerivedWorldInfo or the primary world's shared clock. */
public final class CriticalNightClock {

    private static final Map<Integer, State> SERVER = new ConcurrentHashMap<>();
    private static final Map<Integer, State> CLIENT = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<TimeMessage> INCOMING = new ConcurrentLinkedQueue<>();
    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_criticaltime");
    private static boolean registered;

    private CriticalNightClock() {}

    public static void register() {
        if (registered) return;
        registered = true;
        NETWORK.registerMessage(TimeHandler.class, TimeMessage.class, 0, Side.CLIENT);
        CriticalNightClock.Events events = new Events();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
    }

    private static State state(World world) {
        if (world == null || world.provider == null) return null;
        State state = (world.isRemote ? CLIENT : SERVER).get(world.provider.dimensionId);
        return state != null && state.world == world ? state : null;
    }

    public static Long time(World world) {
        State state = state(world);
        if (state == null || !state.active) return null;
        long elapsed = world.isRemote && state.advancing ? Math.max(0L, world.getTotalWorldTime() - state.anchor) : 0L;
        return state.time + elapsed;
    }

    public static boolean isNight(World world) {
        State state = state(world);
        return state != null && state.active && state.night;
    }

    public static void anchor(World world, CriticalWorldData data) {
        if (world.isRemote) return;
        State state = SERVER.computeIfAbsent(world.provider.dimensionId, ignored -> new State(world, data));
        if (state.world != world) {
            state = new State(world, data);
            SERVER.put(world.provider.dimensionId, state);
        }
        data.initializeLocalClock(world.getWorldTime());
        long midnight = Math.floorDiv(data.getLocalTime(), 24000L) * 24000L + 18000L;
        boolean changed = !state.active || !state.night;
        if (data.getLocalTime() != midnight) data.setLocalTime(midnight);
        state.active = true;
        state.night = true;
        state.advancing = false;
        state.time = midnight;
        if (changed) broadcast(state);
    }

    private static void broadcast(State state) {
        if (state.world.isRemote) return;
        NETWORK.sendToDimension(new TimeMessage(state), state.world.provider.dimensionId);
    }

    private static void send(EntityPlayerMP player) {
        World world = player.worldObj;
        State state = SERVER
            .computeIfAbsent(world.provider.dimensionId, ignored -> new State(world, CriticalWorldData.get(world)));
        NETWORK.sendTo(new TimeMessage(state), player);
    }

    public static void receiveClient(World world, TimeMessage message) {
        if (world == null || !world.isRemote || world.provider.dimensionId != message.dimension) return;
        State state = new State(world, null);
        state.active = message.active;
        state.night = message.night;
        state.advancing = message.advancing;
        state.time = message.time;
        state.anchor = world.getTotalWorldTime();
        CLIENT.put(message.dimension, state);
    }

    private static final class State {

        final World world;
        final CriticalWorldData data;
        boolean active, night, advancing;
        long time, anchor;

        State(World world, CriticalWorldData data) {
            this.world = world;
            this.data = data;
            if (data != null) {
                active = data.hasLocalClock();
                time = data.getLocalTime();
            }
        }
    }

    public static final class Events {

        @SubscribeEvent
        public void load(WorldEvent.Load event) {
            if (!event.world.isRemote) SERVER
                .put(event.world.provider.dimensionId, new State(event.world, CriticalWorldData.get(event.world)));
        }

        @SubscribeEvent
        public void unload(WorldEvent.Unload event) {
            if (event.world.isRemote) {
                CLIENT.clear();
                INCOMING.clear();
            } else {
                State state = SERVER.get(event.world.provider.dimensionId);
                if (state != null && state.world == event.world) SERVER.remove(event.world.provider.dimensionId);
            }
        }

        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
            State state = SERVER.computeIfAbsent(
                event.world.provider.dimensionId,
                ignored -> new State(event.world, CriticalWorldData.get(event.world)));
            if (!state.data.hasLocalClock()) return;
            boolean previousNight = state.night;
            state.active = true;
            state.night = state.data.validateNightOwner(event.world);
            state.advancing = !state.night && event.world.getGameRules()
                .getGameRuleBooleanValue("doDaylightCycle");
            long next = state.data.getLocalTime();
            if (state.night) next = Math.floorDiv(next, 24000L) * 24000L + 18000L;
            else if (state.advancing) next++;
            state.data.setLocalTime(next);
            state.time = next;
            if (previousNight != state.night || event.world.getTotalWorldTime() % 20 == 0) broadcast(state);
        }

        @SubscribeEvent
        public void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP player) send(player);
        }

        @SubscribeEvent
        public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.player instanceof EntityPlayerMP player) send(player);
        }

        @SubscribeEvent
        public void respawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.player instanceof EntityPlayerMP player) send(player);
        }

        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public void client(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END)
                com.miaokatze.gtsr.client.critical.CriticalNightClient.drain(INCOMING);
        }
    }

    public static final class TimeMessage implements IMessage {

        public int dimension;
        public boolean active, night, advancing;
        public long time;
        public transient Object clientConnection;

        public TimeMessage() {}

        private TimeMessage(State state) {
            dimension = state.world.provider.dimensionId;
            active = state.active;
            night = state.night;
            advancing = state.advancing;
            time = state.time;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            dimension = buffer.readInt();
            active = buffer.readBoolean();
            night = buffer.readBoolean();
            advancing = buffer.readBoolean();
            time = buffer.readLong();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(dimension);
            buffer.writeBoolean(active);
            buffer.writeBoolean(night);
            buffer.writeBoolean(advancing);
            buffer.writeLong(time);
        }
    }

    public static final class TimeHandler implements IMessageHandler<TimeMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(TimeMessage message, MessageContext context) {
            message.clientConnection = context.getClientHandler();
            INCOMING.removeIf(
                previous -> previous.dimension == message.dimension
                    && previous.clientConnection == message.clientConnection);
            INCOMING.add(message);
            return null;
        }
    }
}
