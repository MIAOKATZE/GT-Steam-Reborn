package com.miaokatze.gtsr.common.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.miaokatze.gtsr.common.debug.MachineDebugRegistry;
import com.miaokatze.gtsr.common.debug.WandClient;
import com.miaokatze.gtsr.common.gui.CriticalMachineTerminalFactory;
import com.miaokatze.gtsr.common.items.HubTerminal;
import com.miaokatze.gtsr.common.items.SingularityTuningWand;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/** Server tick owns every mutation. Vanilla controller activation is consumed separately. */
public final class WandNet {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_wand");
    private static final BoundedRequestQueue<Pending> PENDING = new BoundedRequestQueue<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int nextToken;

    public static void clearServerState() {
        PENDING.clear();
        SESSIONS.clear();
    }

    public static void init() {
        NETWORK.registerMessage(SelectHandler.class, PacketWandSelect.class, 0, Side.SERVER);
        NETWORK.registerMessage(TargetHandler.class, PacketTarget.class, 1, Side.SERVER);
        NETWORK.registerMessage(OpenHandler.class, PacketOpen.class, 2, Side.CLIENT);
        NETWORK.registerMessage(ProfileHandler.class, PacketProfile.class, 3, Side.SERVER);
        NETWORK.registerMessage(AirProfileHandler.class, PacketAirProfile.class, 4, Side.SERVER);
        NETWORK.registerMessage(TerminalTargetHandler.class, PacketTerminalTarget.class, 5, Side.SERVER);
        ServerDrain drain = new ServerDrain();
        FMLCommonHandler.instance()
            .bus()
            .register(drain);
        MinecraftForge.EVENT_BUS.register(drain);
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) initClient();
    }

    @SideOnly(Side.CLIENT)
    private static void initClient() {
        WandClient.init();
    }

    public static void sendSelectFromClient(int entry, int deltaIndex) {
        NETWORK.sendToServer(new PacketWandSelect(entry, deltaIndex));
    }

    public static void sendTarget(int x, int y, int z, int dimension, int metaId, int action, int slot) {
        PacketTarget message = new PacketTarget();
        message.x = x;
        message.y = y;
        message.z = z;
        message.dimension = dimension;
        message.metaId = metaId;
        message.action = action;
        message.slot = slot;
        NETWORK.sendToServer(message);
    }

    public static void sendProfile(int token, int[] values) {
        PacketProfile message = new PacketProfile();
        message.token = token;
        message.values = values.clone();
        NETWORK.sendToServer(message);
    }

    public static void sendTerminalTarget(int x, int y, int z, int dimension, int metaId, int slot) {
        PacketTerminalTarget message = new PacketTerminalTarget();
        message.x = x;
        message.y = y;
        message.z = z;
        message.dimension = dimension;
        message.metaId = metaId;
        message.slot = slot;
        NETWORK.sendToServer(message);
    }

    public static void sendAirProfile(String family, int[] values) {
        PacketAirProfile message = new PacketAirProfile();
        message.family = family;
        message.values = values.clone();
        NETWORK.sendToServer(message);
    }

    private static void enqueue(IMessage message, MessageContext context) {
        if (context.getServerHandler() == null) return;
        EntityPlayerMP player = context.getServerHandler().playerEntity;
        // Snapshot reference only; all validation and mutation happens in ServerTick.
        if (player != null) PENDING.offer(
            context.getServerHandler(),
            new Pending(player, context.getServerHandler(), player.getHeldItem(), player.inventory.currentItem, message));
    }

    public static class SelectHandler implements IMessageHandler<PacketWandSelect, IMessage> {

        public IMessage onMessage(PacketWandSelect message, MessageContext context) {
            enqueue(message, context);
            return null;
        }
    }

    public static class TargetHandler implements IMessageHandler<PacketTarget, IMessage> {

        public IMessage onMessage(PacketTarget message, MessageContext context) {
            enqueue(message, context);
            return null;
        }
    }

    public static class ProfileHandler implements IMessageHandler<PacketProfile, IMessage> {

        public IMessage onMessage(PacketProfile message, MessageContext context) {
            enqueue(message, context);
            return null;
        }
    }

    public static class OpenHandler implements IMessageHandler<PacketOpen, IMessage> {

        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketOpen message, MessageContext context) {
            WandClient.enqueue(message);
            return null;
        }
    }

    public static class AirProfileHandler implements IMessageHandler<PacketAirProfile, IMessage> {

        public IMessage onMessage(PacketAirProfile message, MessageContext context) {
            enqueue(message, context);
            return null;
        }
    }

    public static class TerminalTargetHandler implements IMessageHandler<PacketTerminalTarget, IMessage> {

        public IMessage onMessage(PacketTerminalTarget message, MessageContext context) {
            enqueue(message, context);
            return null;
        }
    }

    private static final class Pending {

        private final EntityPlayerMP player;
        private final NetHandlerPlayServer connection;
        private final ItemStack wand;
        private final int slot;
        private final IMessage message;

        private Pending(EntityPlayerMP player, NetHandlerPlayServer connection, ItemStack wand, int slot, IMessage message) {
            this.connection = connection;
            this.player = player;
            this.wand = wand;
            this.slot = slot;
            this.message = message;
        }

        private EntityPlayerMP player() {
            return player;
        }

        private ItemStack wand() {
            return wand;
        }

        private int slot() {
            return slot;
        }

        private IMessage message() {
            return message;
        }
    }

    private static final class Session {

        private final int token, slot, dimension, x, y, z;
        private final ItemStack wand;
        private final IGregTechTileEntity base;
        private final Object meta;
        private final long expiry;

        private Session(int token, ItemStack wand, int slot, int dimension, int x, int y, int z,
            IGregTechTileEntity base, Object meta, long expiry) {
            this.token = token;
            this.wand = wand;
            this.slot = slot;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.base = base;
            this.meta = meta;
            this.expiry = expiry;
        }

        private int token() {
            return token;
        }

        private ItemStack wand() {
            return wand;
        }

        private int slot() {
            return slot;
        }

        private int dimension() {
            return dimension;
        }

        private int x() {
            return x;
        }

        private int y() {
            return y;
        }

        private int z() {
            return z;
        }

        private IGregTechTileEntity base() {
            return base;
        }

        private Object meta() {
            return meta;
        }

        private long expiry() {
            return expiry;
        }
    }

    private static boolean usable(EntityPlayerMP player, IGregTechTileEntity base) {
        return base != null && base.canAccessData()
            && base.isUseableByPlayer(player)
            && base.getWorld() == player.worldObj
            && player.getDistanceSq(base.getXCoord() + 0.5, base.getYCoord() + 0.5, base.getZCoord() + 0.5) <= 64;
    }

    public static final class ServerDrain {

        @SubscribeEvent
        public void interact(PlayerInteractEvent event) {
            if (event.world.isRemote || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
            ItemStack held = event.entityPlayer.getHeldItem();
            if (held != null && held.getItem() instanceof SingularityTuningWand
                && event.world.getTileEntity(event.x, event.y, event.z) instanceof IGregTechTileEntity)
                event.setCanceled(true);
        }

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            SESSIONS.entrySet()
                .removeIf(
                    entry -> entry.getValue()
                        .expiry() < System.currentTimeMillis());
            Pending task;
            for (int n = 0; n < BoundedRequestQueue.MAX_PER_TICK && (task = PENDING.poll()) != null; n++) {
                EntityPlayerMP player = task.player();
                if (!ServerPacketAuthority.isCurrentPlayer(player, task.connection)) continue;
                ItemStack held = player.getHeldItem();
                if (held == null || held != task.wand() || player.inventory.currentItem != task.slot()) continue;
                if (task.message() instanceof PacketTerminalTarget target) {
                    if (!(held.getItem() instanceof HubTerminal) || target.dimension != player.dimension
                        || target.slot != player.inventory.currentItem
                        || !player.worldObj.blockExists(target.x, target.y, target.z)) continue;
                    if (player.worldObj.getTileEntity(target.x, target.y, target.z) instanceof IGregTechTileEntity base
                        && base.getMetaTileID() == target.metaId
                        && usable(player, base)
                        && base.getMetaTileEntity() instanceof MTECriticalMachine)
                        CriticalMachineTerminalFactory.open(player, base);
                    continue;
                }
                if (!(held.getItem() instanceof SingularityTuningWand)) continue;
                if (!player.capabilities.isCreativeMode && !player.canCommandSenderUseCommand(2, "gtsrdebug")) {
                    MachineDebugRegistry.feedback(player, "gtsr.wand.debug.denied");
                    continue;
                }
                if (task.message() instanceof PacketWandSelect select) {
                    if (select.entry < 0 || select.entry >= SingularityTuningWand.ENTRY_COUNT
                        || select.deltaIndex < 0
                        || select.deltaIndex >= SingularityTuningWand.DELTA_STEPS.length) continue;
                    SingularityTuningWand.setSelectedEntry(held, select.entry);
                    SingularityTuningWand.setSelectedDeltaIndex(held, select.deltaIndex);
                    // Legacy air selector updates the same singularity family profile.
                    if (held.hasTagCompound()) held.getTagCompound()
                        .getCompoundTag("gtsrDebugProfiles")
                        .removeTag("singularity");
                    player.inventoryContainer.detectAndSendChanges();
                } else if (task.message() instanceof PacketTarget target) {
                    handleTarget(player, held, target);
                } else if (task.message() instanceof PacketProfile profile) {
                    handleProfile(player, held, profile);
                } else if (task.message() instanceof PacketAirProfile profile) {
                    var adapter = MachineDebugRegistry.findFamily(profile.family);
                    // Air selection changes the held item's profile only; applying requires a validated target.
                    if (adapter != null && MachineDebugRegistry.writeProfile(held, adapter, null, profile.values))
                        player.inventoryContainer.detectAndSendChanges();
                }
            }
        }

        private void handleTarget(EntityPlayerMP player, ItemStack held, PacketTarget target) {
            if (target.action < 0 || target.action > 2
                || player.dimension != target.dimension
                || player.inventory.currentItem != target.slot
                || !player.worldObj.blockExists(target.x, target.y, target.z)
                || player.getDistanceSq(target.x + 0.5, target.y + 0.5, target.z + 0.5) > 64) return;
            if (!(player.worldObj.getTileEntity(target.x, target.y, target.z) instanceof IGregTechTileEntity base)
                || base.getMetaTileID() != target.metaId
                || !usable(player, base)) return;
            var machine = base.getMetaTileEntity();
            Session previous = SESSIONS.get(player.getUniqueID());
            if (target.action != 0 && previous != null
                && previous.dimension() == target.dimension
                && previous.x() == target.x
                && previous.y() == target.y
                && previous.z() == target.z
                && (previous.base() != base || previous.meta() != machine)) {
                SESSIONS.remove(player.getUniqueID());
                MachineDebugRegistry.feedback(player, "gtsr.wand.debug.denied");
                return;
            }
            var adapter = MachineDebugRegistry.find(machine);
            if (adapter == null) {
                MachineDebugRegistry.feedback(player, "gtsr.wand.debug.unsupported");
                return;
            }
            if (!adapter.canUse(machine, player)) {
                MachineDebugRegistry.feedback(player, "gtsr.wand.debug.denied");
                return;
            }
            int[] values = MachineDebugRegistry.readProfile(held, adapter, machine);
            if (target.action == 0) {
                int token = ++nextToken;
                SESSIONS.put(
                    player.getUniqueID(),
                    new Session(
                        token,
                        held,
                        target.slot,
                        target.dimension,
                        target.x,
                        target.y,
                        target.z,
                        base,
                        machine,
                        System.currentTimeMillis() + 300000));
                PacketOpen response = new PacketOpen();
                response.x = target.x;
                response.y = target.y;
                response.z = target.z;
                response.dimension = target.dimension;
                response.metaId = target.metaId;
                response.slot = target.slot;
                response.token = token;
                response.family = adapter.family();
                response.values = values;
                NETWORK.sendTo(response, player);
            } else adapter.apply(machine, player, values, target.action == 2);
        }

        private void handleProfile(EntityPlayerMP player, ItemStack held, PacketProfile message) {
            Session session = SESSIONS.get(player.getUniqueID());
            if (session == null || session.token() != message.token
                || session.wand() != held
                || session.slot() != player.inventory.currentItem
                || session.dimension() != player.dimension
                || !player.worldObj.blockExists(session.x(), session.y(), session.z())
                || player.worldObj.getTileEntity(session.x(), session.y(), session.z()) != session.base()
                || session.base()
                    .getMetaTileEntity() != session.meta()
                || !usable(player, session.base())) return;
            var adapter = MachineDebugRegistry.find(
                session.base()
                    .getMetaTileEntity());
            if (adapter == null || !adapter.canUse(
                session.base()
                    .getMetaTileEntity(),
                player)) return;
            if (MachineDebugRegistry.writeProfile(
                held,
                adapter,
                session.base()
                    .getMetaTileEntity(),
                message.values)) {
                // A validated target construction screen edits that controller's debug settings immediately.
                // Air profiles and singularity increment selection remain item-only settings.
                if ("construction".equals(adapter.family())) adapter.apply(
                    session.base()
                        .getMetaTileEntity(),
                    player,
                    message.values,
                    false);
                player.inventoryContainer.detectAndSendChanges();
            }
        }
    }

    public static class PacketTarget implements IMessage {

        public int x, y, z, dimension, metaId, action, slot;

        public void fromBytes(ByteBuf buffer) {
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
            dimension = buffer.readInt();
            metaId = buffer.readInt();
            action = buffer.readByte();
            slot = buffer.readByte();
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeInt(z);
            buffer.writeInt(dimension);
            buffer.writeInt(metaId);
            buffer.writeByte(action);
            buffer.writeByte(slot);
        }
    }

    public static class PacketOpen extends PacketTarget {

        public int token;
        public String family = "";
        public int[] values = new int[0];

        public void fromBytes(ByteBuf buffer) {
            super.fromBytes(buffer);
            token = buffer.readInt();
            family = ByteBufUtils.readUTF8String(buffer);
            values = readValues(buffer);
        }

        public void toBytes(ByteBuf buffer) {
            super.toBytes(buffer);
            buffer.writeInt(token);
            ByteBufUtils.writeUTF8String(buffer, family);
            writeValues(buffer, values);
        }
    }

    public static class PacketTerminalTarget extends PacketTarget {
    }

    public static class PacketProfile implements IMessage {

        public int token;
        public int[] values = new int[0];

        public void fromBytes(ByteBuf buffer) {
            token = buffer.readInt();
            values = readValues(buffer);
        }

        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(token);
            writeValues(buffer, values);
        }
    }

    public static class PacketAirProfile implements IMessage {

        public String family = "";
        public int[] values = new int[0];

        public void fromBytes(ByteBuf buffer) {
            family = ByteBufUtils.readUTF8String(buffer);
            values = readValues(buffer);
        }

        public void toBytes(ByteBuf buffer) {
            ByteBufUtils.writeUTF8String(buffer, family);
            writeValues(buffer, values);
        }
    }

    private static int[] readValues(ByteBuf buffer) {
        int length = buffer.readUnsignedByte();
        if (length > 16) throw new IllegalArgumentException("Too many wand options");
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = buffer.readUnsignedByte();
        return values;
    }

    private static void writeValues(ByteBuf buffer, int[] values) {
        buffer.writeByte(values.length);
        for (int value : values) buffer.writeByte(value);
    }
}
