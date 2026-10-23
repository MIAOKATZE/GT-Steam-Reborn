package com.miaokatze.gtsr.common.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;
import com.miaokatze.gtsr.common.items.HubTerminal;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** GTSR terminal records are server-owned player data; item NBT carries only a terminal identity. */
public final class MachineTerminalHistory {

    public static final int LIMIT = 32;
    private static final int TERMINAL_LIMIT = 8;
    private static final String HISTORY = "gtsr.machineTerminalHistory", ID = "gtsr.terminalId";

    public enum Kind {
        CRITICAL,
        STEAM_HUB,
        WATER_HUB,
        DRILLING_HUB,
        CACHE_NODE,
        CLUSTER,
        TCDS,
        AGGREGATOR,
        ENTANGLEMENT,
        WORKER
    }

    private MachineTerminalHistory() {}

    public static boolean held(EntityPlayer player) {
        return player != null && player.getHeldItem() != null
            && player.getHeldItem()
                .getItem() instanceof HubTerminal;
    }

    static String terminalId(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return "";
        ItemStack stack = recordingTerminal(player);
        if (stack == null) return "";
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(ID)) tag.setString(
            ID,
            UUID.randomUUID()
                .toString());
        return tag.getString(ID);
    }

    private static ItemStack recordingTerminal(EntityPlayer player) {
        if (held(player)) return player.getHeldItem();
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack != null && stack.getItem() instanceof HubTerminal) return stack;
        return null;
    }

    public static Kind kind(IGregTechTileEntity base) {
        if (base == null) return null;
        Object machine = base.getMetaTileEntity();
        if (machine instanceof MTECriticalMachine) return Kind.CRITICAL;
        if (machine instanceof MTESteamHubArray) return Kind.STEAM_HUB;
        if (machine instanceof MTEWaterHubArray) return Kind.WATER_HUB;
        if (machine instanceof MTESingularityDrillingHub) return Kind.DRILLING_HUB;
        if (machine instanceof IHubCacheNode) return Kind.CACHE_NODE;
        if (machine instanceof MTERemoteWorkerNode) return Kind.WORKER;
        if (machine instanceof MTESteamMineralLogisticsCluster) return Kind.CLUSTER;
        if (machine instanceof MTEThermoChemicalDenseSteamGenerator) return Kind.TCDS;
        if (machine instanceof MTECrustMatterAggregator) return Kind.AGGREGATOR;
        if (machine instanceof MTESingularityMachineBase singularity && singularity.hasEntanglementTerminal())
            return Kind.ENTANGLEMENT;
        return null;
    }

    static boolean enabled(Kind kind) {
        if (kind == null) return false;
        return switch (kind) {
            case CRITICAL, ENTANGLEMENT -> true;
            case STEAM_HUB, WATER_HUB -> Mui2TerminalRoutes.cacheHubTerminal;
            case DRILLING_HUB -> Mui2TerminalRoutes.singularityHubTerminal;
            case CACHE_NODE -> Mui2TerminalRoutes.cacheNodeEditor;
            case CLUSTER -> Mui2TerminalRoutes.clusterTerminal;
            case TCDS -> Mui2TerminalRoutes.tcdsTerminal;
            case AGGREGATOR -> Mui2TerminalRoutes.aggregatorConfig;
            case WORKER -> Mui2TerminalRoutes.remoteWorkerNodes;
        };
    }

    /** Called only after an authoritative factory actually installed a new container. */
    public static void recordOpened(EntityPlayer player, IGregTechTileEntity base, Object previousContainer) {
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer
            || recordingTerminal(player) == null
            || player.openContainer == previousContainer
            || !(player.openContainer instanceof ModularContainer container)
            || !(base instanceof MachineTerminalIdentity identity)
            || kind(base) == null
            || !(container.getGuiData() instanceof PosGuiData position)
            || position.getTileEntity() != base) return;
        Entry entry = new Entry(base, identity.gtsr$terminalTargetIdentity());
        List<Entry> records = read(player);
        records = com.miaokatze.miaokatzecore.api.collection.BoundedMru
            .moveToFront(records, entry, LIMIT, Entry::samePosition);
        write(player, records);
    }

    static List<Entry> read(EntityPlayer player) {
        String terminal = terminalId(player);
        List<Entry> records = new ArrayList<>();
        if (terminal.isEmpty()) return records;
        NBTTagList terminals = persisted(player).getTagList(HISTORY, 10);
        for (int i = 0; i < Math.min(TERMINAL_LIMIT, terminals.tagCount()); i++) {
            NBTTagCompound partition = terminals.getCompoundTagAt(i);
            if (!terminal.equals(partition.getString("terminal"))) continue;
            NBTTagList list = partition.getTagList("machines", 10);
            for (int j = 0; j < Math.min(LIMIT, list.tagCount()); j++) {
                Entry entry = Entry.read(list.getCompoundTagAt(j));
                if (entry != null) records.add(entry);
            }
            break;
        }
        return records;
    }

    static void delete(EntityPlayer player, Entry entry) {
        List<Entry> records = read(player);
        records.removeIf(old -> old.samePosition(entry) && old.targetId.equals(entry.targetId));
        write(player, records);
    }

    private static void write(EntityPlayer player, List<Entry> records) {
        String terminal = terminalId(player);
        NBTTagList old = persisted(player).getTagList(HISTORY, 10), next = new NBTTagList();
        NBTTagCompound current = new NBTTagCompound();
        current.setString("terminal", terminal);
        NBTTagList machines = new NBTTagList();
        records.forEach(entry -> machines.appendTag(entry.write()));
        current.setTag("machines", machines);
        next.appendTag(current);
        for (int i = 0; i < Math.min(TERMINAL_LIMIT, old.tagCount()) && next.tagCount() < TERMINAL_LIMIT; i++) {
            NBTTagCompound partition = old.getCompoundTagAt(i);
            if (!terminal.equals(partition.getString("terminal"))) next.appendTag(partition);
        }
        persisted(player).setTag(HISTORY, next);
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound entity = player.getEntityData();
        if (!entity.hasKey(EntityPlayer.PERSISTED_NBT_TAG))
            entity.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return entity.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** Loaded worlds/chunks only: this path never calls initDimension, provideChunk or getChunkFromChunkCoords. */
    static IGregTechTileEntity resolve(Entry entry) {
        World world = DimensionManager.getWorld(entry.dimension);
        if (world == null || !world.getChunkProvider()
            .chunkExists(entry.x >> 4, entry.z >> 4)) return null;
        return world.getTileEntity(entry.x, entry.y, entry.z) instanceof IGregTechTileEntity base ? base : null;
    }

    static String status(EntityPlayer player, Entry entry) {
        World world = DimensionManager.getWorld(entry.dimension);
        if (world == null || !world.getChunkProvider()
            .chunkExists(entry.x >> 4, entry.z >> 4)) return "offline";
        IGregTechTileEntity base = resolve(entry);
        if (base == null || base.isDead()
            || !(base instanceof MachineTerminalIdentity identity)
            || !entry.targetId.equals(identity.gtsr$terminalTargetIdentity())
            || base.getMetaTileID() != entry.metaId) return "replaced";
        if (kind(base) != entry.kind || !enabled(entry.kind)) return "unsupported";
        if (!entry.owner.equals(owner(base)) || !authorized(player, base)) return "denied";
        if (base.getMetaTileEntity() instanceof IHubCacheNode node && !node.isBoundToHub()) return "unbound";
        return "ready";
    }

    static String owner(IGregTechTileEntity base) {
        return base.getOwnerUuid() == null ? ""
            : base.getOwnerUuid()
                .toString();
    }

    static boolean authorized(EntityPlayer player, IGregTechTileEntity base) {
        return player instanceof EntityPlayerMP && !(player instanceof FakePlayer)
            && !player.isDead
            && base.canAccessData()
            && !base.isDead()
            && (base.getOwnerUuid() == null || base.getOwnerUuid()
                .equals(player.getUniqueID()))
            && base.getMetaTileEntity() != null
            && (base.getMetaTileEntity() instanceof MTECriticalMachine critical ? critical.getCriticalState()
                .permitsTerminalAccess(player)
                : base.getMetaTileEntity()
                    .isAccessAllowed(player));
    }

    /** A narrow exception for an already-open, server-created remote UI; never a global range override. */
    public static boolean hasRemoteAccess(EntityPlayer player, Object target) {
        return player != null && player.openContainer instanceof ModularContainer container
            && container.getGuiData() instanceof RemoteTerminalGuiData data
            && data.remote
            && data.getTileEntity() == target
            && data.canUseRemote(player);
    }

    public static boolean remote(PosGuiData data) {
        return data instanceof RemoteTerminalGuiData remote && remote.remote;
    }

    public static boolean canUseRemote(EntityPlayer player, PosGuiData data) {
        return data instanceof RemoteTerminalGuiData remote && remote.remote && remote.canUseRemote(player);
    }

    public static final class Entry {

        public final int dimension, x, y, z, metaId;
        public final Kind kind;
        public final String targetId, owner, name;

        private Entry(IGregTechTileEntity base, String identity) {
            this(
                base.getWorld().provider.dimensionId,
                base.getXCoord(),
                base.getYCoord(),
                base.getZCoord(),
                base.getMetaTileID(),
                kind(base),
                identity,
                owner(base),
                base.getMetaTileEntity()
                    .getLocalNameKey());
        }

        Entry(int dimension, int x, int y, int z, int metaId, Kind kind, String targetId, String owner, String name) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.metaId = metaId;
            this.kind = kind;
            this.targetId = targetId;
            this.owner = owner;
            this.name = name == null ? "" : name.substring(0, Math.min(96, name.length()));
        }

        boolean samePosition(Entry other) {
            return dimension == other.dimension && x == other.x && y == other.y && z == other.z;
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dim", dimension);
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);
            tag.setInteger("meta", metaId);
            tag.setString("kind", kind.name());
            tag.setString("id", targetId);
            tag.setString("owner", owner);
            tag.setString("name", name);
            return tag;
        }

        static Entry read(NBTTagCompound tag) {
            try {
                String id = tag.getString("id"), owner = tag.getString("owner");
                if (id.length() != 36 || owner.length() > 36) return null;
                return new Entry(
                    tag.getInteger("dim"),
                    tag.getInteger("x"),
                    tag.getInteger("y"),
                    tag.getInteger("z"),
                    tag.getInteger("meta"),
                    Kind.valueOf(tag.getString("kind")),
                    id,
                    owner,
                    tag.getString("name"));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    /** Kept exclusively in the server-created GuiData object; nothing deserialized from a packet creates a grant. */
    static final class Grant {

        final EntityPlayer player;
        final ItemStack terminal;
        final String terminalId;
        final int slot;
        final Entry entry;
        final IGregTechTileEntity base;
        final Object machine;

        Grant(EntityPlayer player, Entry entry, IGregTechTileEntity base) {
            this.player = player;
            this.entry = entry;
            this.base = base;
            this.machine = base.getMetaTileEntity();
            this.terminal = player.getHeldItem();
            this.terminalId = terminalId(player);
            this.slot = player.inventory.currentItem;
        }

        boolean valid(EntityPlayer player) {
            return player == this.player && player.inventory.currentItem == slot
                && player.getHeldItem() == terminal
                && held(player)
                && terminal.hasTagCompound()
                && terminalId.equals(
                    terminal.getTagCompound()
                        .getString(ID))
                && resolve(entry) == base
                && base.getMetaTileEntity() == machine
                && "ready".equals(status(player, entry));
        }
    }
}
