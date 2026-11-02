package com.miaokatze.gtsr.common.critical;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.tileentity.TileEntity;

import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
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

/** One short-lived, server-authorized diagnostic per viewer. Clients never submit highlight coordinates. */
public final class CriticalDamageHighlights {

    public static final int DURATION_TICKS = 600;
    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_critdamage");
    private static final Map<EntityPlayerMP, Session> SESSIONS = new HashMap<>();
    private static long sequence;

    private CriticalDamageHighlights() {}

    public static void register() {
        NETWORK.registerMessage(Handler.class, Frame.class, 0, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new CriticalDamageHighlights());
    }

    public static void onServerStopped() {
        SESSIONS.clear();
    }

    /** Called only by the terminal's existing guarded server action. */
    public static boolean request(EntityPlayer viewer, MTECriticalMachine machine) {
        if (!(viewer instanceof EntityPlayerMP player) || machine == null || !machine.isKindSelector()) return false;
        Session session = new Session(player, machine, ++sequence);
        if (!current(player, session) || !session.state.requestDamageHighlight(player)) return false;
        SESSIONS.put(player, session);
        send(player, session);
        return true;
    }

    private static boolean current(EntityPlayerMP player, Session session) {
        if (!ServerPacketAuthority.isCurrentPlayer(player, session.connection) || player.dimension != session.dimension
            || session.state.worldObj != player.worldObj
            || !session.structureId.equals(session.state.getStructureId())
            || session.machine.getCriticalState() != session.state
            || !session.state.canUse(player)) return false;
        IGregTechTileEntity tile = session.host;
        return tile != null && session.machine.getBaseMetaTileEntity() == tile
            && tile.getWorld() == player.worldObj
            && !((TileEntity) tile).isInvalid()
            && tile.getMetaTileEntity() == session.machine
            && player.worldObj.blockExists(tile.getXCoord(), tile.getYCoord(), tile.getZCoord())
            && player.worldObj.getTileEntity(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()) == tile;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<EntityPlayerMP, Session>> entries = SESSIONS.entrySet()
            .iterator();
        while (entries.hasNext()) {
            Map.Entry<EntityPlayerMP, Session> entry = entries.next();
            EntityPlayerMP player = entry.getKey();
            Session session = entry.getValue();
            if (!current(player, session) || player.worldObj.getTotalWorldTime() >= session.expires) {
                entries.remove();
                continue;
            }
            if (player.worldObj.getTotalWorldTime() >= session.nextSample) send(player, session);
        }
    }

    private static void send(EntityPlayerMP player, Session session) {
        CriticalMachineState.DamageHighlightSnapshot snapshot = session.state.getDamageHighlightSnapshot();
        session.nextSample = player.worldObj.getTotalWorldTime() + 5;
        if (session.last != null && session.last.knownTotal == snapshot.knownTotal
            && session.last.scanPending == snapshot.scanPending
            && Arrays.equals(session.last.positions, snapshot.positions)) return;
        session.last = snapshot;
        NETWORK.sendTo(new Frame(session.dimension, session.id, snapshot), player);
    }

    private static final class Session {

        final MTECriticalMachine machine;
        final CriticalMachineState state;
        final IGregTechTileEntity host;
        final NetHandlerPlayServer connection;
        final String structureId;
        final int dimension;
        final long id, expires;
        long nextSample;
        CriticalMachineState.DamageHighlightSnapshot last;

        Session(EntityPlayerMP player, MTECriticalMachine machine, long id) {
            this.machine = machine;
            state = machine.getCriticalState();
            host = machine.getBaseMetaTileEntity();
            connection = player.playerNetServerHandler;
            structureId = state.getStructureId();
            dimension = player.dimension;
            this.id = id;
            expires = player.worldObj.getTotalWorldTime() + DURATION_TICKS;
        }
    }

    public static final class Frame implements IMessage {

        public int dimension, knownTotal;
        public long session;
        public boolean scanPending;
        public int[] positions = new int[0];

        public Frame() {}

        private Frame(int dimension, long session, CriticalMachineState.DamageHighlightSnapshot snapshot) {
            this.dimension = dimension;
            this.session = session;
            knownTotal = snapshot.knownTotal;
            scanPending = snapshot.scanPending;
            positions = snapshot.positions;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            if (buffer.readableBytes() < 19) throw new IllegalArgumentException("Truncated Critical damage frame");
            dimension = buffer.readInt();
            session = buffer.readLong();
            knownTotal = buffer.readInt();
            scanPending = buffer.readBoolean();
            int count = buffer.readUnsignedShort();
            if (count > CriticalMachineState.MAX_DAMAGE_HIGHLIGHT_POSITIONS || knownTotal < count
                || buffer.readableBytes() != count * 12)
                throw new IllegalArgumentException("Critical damage frame size");
            positions = new int[count * 3];
            for (int i = 0; i < positions.length; i++) positions[i] = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            int count = positions.length / 3;
            if (positions.length % 3 != 0 || count > CriticalMachineState.MAX_DAMAGE_HIGHLIGHT_POSITIONS
                || knownTotal < count) throw new IllegalArgumentException("Critical damage frame size");
            buffer.writeInt(dimension)
                .writeLong(session)
                .writeInt(knownTotal)
                .writeBoolean(scanPending)
                .writeShort(count);
            for (int position : positions) buffer.writeInt(position);
        }
    }

    public static final class Handler implements IMessageHandler<Frame, IMessage> {

        @Override
        public IMessage onMessage(Frame message, MessageContext context) {
            GTSteamReborn.proxy.receiveCriticalDamageHighlights(message, context.getClientHandler());
            return null;
        }
    }
}
