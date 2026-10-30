package com.miaokatze.gtsr.common.terminal;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.DimensionManager;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.items.HubTerminal;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Detached production authorization and NBT codec. No simulated GUI success or live Mixin startup claim. */
public final class MachineTerminalRemoteContract {
    private static sun.misc.Unsafe unsafe;
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void field(Object object, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static void source(Class<?> type, Path expected) throws Exception {
        Path actual = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!Files.isSameFile(actual, expected))
            throw new AssertionError("Unexpected product CodeSource for " + type.getName() + ": " + actual);
        System.out.println("CODESOURCE " + type.getName() + " " + actual);
    }

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Server thread");
        if (args.length != 0 && args.length != 2) throw new IllegalArgumentException("Expected candidate-dev.jar core-dev.jar");
        if (args.length == 2) {
            Path candidate = Path.of(args[0]).toRealPath();
            source(MachineTerminalHistory.class, candidate);
            source(RemoteTerminalFactory.class, candidate);
            source(RemoteTerminalGuiData.class, candidate);
            source(MachineTerminalIdentity.State.class, candidate);
            source(CriticalMachineState.class, candidate);
            source(com.miaokatze.miaokatzecore.api.collection.BoundedMru.class, Path.of(args[1]).toRealPath());
        }
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        unsafe = (sun.misc.Unsafe) access.get(null);
        field(null, cpw.mods.fml.common.Loader.class, "instance", unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        Object side = Proxy.newProxyInstance(cpw.mods.fml.common.IFMLSidedHandler.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.IFMLSidedHandler.class }, (proxy, method, values) ->
                method.getName().equals("getSide") ? cpw.mods.fml.relauncher.Side.SERVER : null);
        field(cpw.mods.fml.common.FMLCommonHandler.instance(), cpw.mods.fml.common.FMLCommonHandler.class, "sidedDelegate", side);
        identityCodec();
        SparseWorld world = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        field(world, net.minecraft.world.World.class, "provider", new WorldProviderSurface());
        world.provider.dimensionId = -704208;
        world.loaded = true;
        world.chunks = (IChunkProvider) Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),
            new Class<?>[] { IChunkProvider.class }, (proxy, method, values) -> {
                if (method.getName().equals("chunkExists")) return world.loaded;
                throw new AssertionError("Unexpected chunk load/query: " + method.getName());
            });
        Field worlds = DimensionManager.class.getDeclaredField("worlds"); worlds.setAccessible(true);
        @SuppressWarnings("unchecked") Hashtable<Integer, WorldServer> registry = (Hashtable<Integer, WorldServer>) worlds.get(null);
        registry.put(world.provider.dimensionId, world);
        try {
            EntityPlayerMP player = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
            field(player, Entity.class, "entityUniqueID", UUID.fromString("562e48d9-e220-4f1f-a963-3712d46a7407"));
            player.worldObj = world; player.dimension = 0; player.posX = 100000; player.posY = 70; player.posZ = 100000;
            player.inventory = new InventoryPlayer(player);
            connect(player, world);
            HubTerminal item = (HubTerminal) unsafe.allocateInstance(HubTerminal.class);
            Object delegate = Proxy.newProxyInstance(cpw.mods.fml.common.registry.RegistryDelegate.class.getClassLoader(),
                new Class<?>[] { cpw.mods.fml.common.registry.RegistryDelegate.class }, (proxy, method, values) ->
                    method.getName().equals("get") ? item : null);
            field(item, net.minecraft.item.Item.class, "delegate", delegate);
            ItemStack terminal = new ItemStack(item);
            player.inventory.mainInventory[0] = terminal;
            Target original = target(world, player.getUniqueID()); world.tile = original;
            MachineTerminalHistory.Entry entry = new MachineTerminalHistory.Entry(world.provider.dimensionId, 12, 70, -18,
                original.getMetaTileID(), MachineTerminalHistory.Kind.CRITICAL, original.gtsr$terminalTargetIdentity(),
                original.owner.toString(), "fixture");
            check("ready".equals(MachineTerminalHistory.status(player, entry)), "Critical owner may open remotely without a pre-existing grant");
            MachineTerminalHistory.Grant grant = new MachineTerminalHistory.Grant(player, entry, original);
            RemoteTerminalGuiData data = new RemoteTerminalGuiData(player, entry.x, entry.y, entry.z,
                entry.dimension, entry.kind, grant);
            ModularContainer container = (ModularContainer) unsafe.allocateInstance(ModularContainer.class);
            field(container, ModularContainer.class, "guiData", data); player.openContainer = container;
            check(data.canUseRemote(player), "Server-issued grant binds real remote target: held=" + MachineTerminalHistory.held(player)
                + " current=" + (player.getHeldItem() == grant.terminal) + " slot=" + player.inventory.currentItem
                + " id=" + grant.terminalId + " actual=" + (terminal.hasTagCompound() ? terminal.getTagCompound().getString("gtsr.terminalId") : "no tag")
                + " resolved=" + (MachineTerminalHistory.resolve(entry) == original) + " machine=" + (original.getMetaTileEntity() == grant.machine)
                + " status=" + MachineTerminalHistory.status(player, entry));
            check(MachineTerminalHistory.hasRemoteAccess(player, original), "Only active target receives the range exception");
            check(original.machine.getCriticalState().canUse(player), "Real Critical canUse permits distant owner without recursive access calls");
            player.inventory.currentItem = 1;
            check(!data.canUseRemote(player), "Switching hotbar slot immediately invalidates authorization");
            player.inventory.currentItem = 0;
            player.inventory.mainInventory[0] = terminal.copy();
            check(!data.canUseRemote(player), "A copied stack with matching terminal NBT does not inherit an existing grant");
            player.inventory.mainInventory[0] = terminal;
            original.owner = UUID.randomUUID();
            check("denied".equals(MachineTerminalHistory.status(player, entry)) && !data.canUseRemote(player),
                "Changing target owner invalidates grant and exposes denied status");
            original.owner = player.getUniqueID();
            original.machine.getCriticalState().setLoomOwnedMain(new CriticalMachineState());
            check("denied".equals(MachineTerminalHistory.status(player, entry)), "Passive loom-owned controller retains its access restriction");
            world.tile = target(world, player.getUniqueID());
            check(world.tile.getClass() == original.getClass() && ((Target) world.tile).getMetaTileID() == original.getMetaTileID(),
                "Replacement fixture has identical tile class and meta id");
            check("replaced".equals(MachineTerminalHistory.status(player, entry)) && !data.canUseRemote(player),
                "Same-meta replacement never inherits old persistent identity or grant");
            world.loaded = false; int reads = world.tileReads;
            check(MachineTerminalHistory.resolve(entry) == null && "offline".equals(MachineTerminalHistory.status(player, entry)),
                "Unloaded chunks remain offline");
            check(world.tileReads == reads, "Offline lookup never calls getTileEntity or a chunk load method");
            registry.remove(world.provider.dimensionId);
            check("offline".equals(MachineTerminalHistory.status(player, entry)), "Unloaded dimensions remain offline without initialization");
            RemoteTerminalGuiData untrusted = new RemoteTerminalGuiData(player, entry.x, entry.y, entry.z,
                entry.dimension, entry.kind, null);
            check(untrusted.getTileEntity() == null && !untrusted.canUseRemote(player), "Deserialized coordinates alone cannot resolve or authorize a server tile");
            player.openContainer = null;
            check(!MachineTerminalHistory.hasRemoteAccess(player, original), "Closing a remote container revokes the range exception");
        } finally { registry.remove(world.provider.dimensionId); }
        System.out.println("PASS MachineTerminalRemoteContract checks=" + checks + " (detached models; no live Mixin/UI assertion)");
    }

    /** Grant validity now includes the issuing live connection, not merely item/player identity. */
    private static void connect(EntityPlayerMP player, WorldServer world) throws Exception {
        AuthorityServer server = (AuthorityServer) unsafe.allocateInstance(AuthorityServer.class);
        server.world = world;
        field(null, net.minecraft.server.MinecraftServer.class, "mcServer", server);
        AuthorityConfiguration configuration = (AuthorityConfiguration) unsafe.allocateInstance(AuthorityConfiguration.class);
        field(configuration, net.minecraft.server.management.ServerConfigurationManager.class,
            "playerEntityList", new java.util.ArrayList<>());
        server.setConfigManager(configuration);
        configuration.playerEntityList.add(player);
        net.minecraft.network.NetHandlerPlayServer connection = (net.minecraft.network.NetHandlerPlayServer)
            unsafe.allocateInstance(net.minecraft.network.NetHandlerPlayServer.class);
        connection.playerEntity = player;
        player.playerNetServerHandler = connection;
        net.minecraft.network.NetworkManager network = new net.minecraft.network.NetworkManager(false);
        field(network, net.minecraft.network.NetworkManager.class, "channel",
            new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter()));
        field(connection, net.minecraft.network.NetHandlerPlayServer.class, "netManager", network);
    }

    private static final class AuthorityConfiguration extends net.minecraft.server.management.ServerConfigurationManager {
        private AuthorityConfiguration() { super(null); }
    }

    private static final class AuthorityServer extends net.minecraft.server.MinecraftServer {
        WorldServer world;
        private AuthorityServer() { super(null, null); }
        @Override public WorldServer worldServerForDimension(int dimension) { return world; }
        @Override protected boolean startServer() { return false; }
        @Override public boolean canStructuresSpawn() { return false; }
        @Override public net.minecraft.world.WorldSettings.GameType getGameType() { return null; }
        @Override public net.minecraft.world.EnumDifficulty getDifficulty() { return null; }
        @Override public boolean isHardcore() { return false; }
        @Override public int getOpPermissionLevel() { return 0; }
        @Override public boolean isDedicatedServer() { return false; }
        @Override public boolean func_152363_m() { return false; }
        @Override public boolean isCommandBlockEnabled() { return false; }
        @Override public String shareToLAN(net.minecraft.world.WorldSettings.GameType type, boolean cheats) { return null; }
    }

    private static void identityCodec() {
        MachineTerminalIdentity.State first = new MachineTerminalIdentity.State();
        String id = first.identity(); NBTTagCompound saved = new NBTTagCompound(); first.write(saved);
        NBTTagCompound disk = (NBTTagCompound) saved.copy();
        MachineTerminalIdentity.State loaded = new MachineTerminalIdentity.State(); loaded.read(disk);
        check(loaded.identity().equals(id), "Exact production world-NBT write/read preserves target UUID across reload");
        check(!new MachineTerminalIdentity.State().identity().equals(id), "A replacement tile gets a new identity even with identical meta");
        NBTTagCompound invalid = new NBTTagCompound(); invalid.setString("gtsr.terminalTargetId", "invalid");
        loaded.read(invalid); check(!loaded.identity().equals(id), "Corrupt world identity never silently preserves a previous tile's grant");
        MachineTerminalHistory.Entry entry = new MachineTerminalHistory.Entry(7, 8, 9, -10, 11,
            MachineTerminalHistory.Kind.WORKER, id, "", "test");
        MachineTerminalHistory.Entry copy = MachineTerminalHistory.Entry.read(entry.write());
        check(copy != null && copy.targetId.equals(id) && copy.samePosition(entry), "Real history NBT retains dimension, position and target identity");
    }

    private static Target target(SparseWorld world, UUID owner) throws Exception {
        Target tile = (Target) unsafe.allocateInstance(Target.class);
        tile.setWorldObj(world); tile.xCoord = 12; tile.yCoord = 70; tile.zCoord = -18; tile.owner = owner;
        tile.identity = new MachineTerminalIdentity.State();
        tile.machine = (MTECriticalMachine) unsafe.allocateInstance(MTECriticalMachine.class);
        CriticalMachineState state = new CriticalMachineState();
        field(state, CriticalMachineState.class, "geometryVersion", 1);
        field(tile.machine, MTECriticalMachine.class, "state", state);
        field(tile.machine, gregtech.api.metatileentity.MetaTileEntity.class, "mBaseMetaTileEntity", tile);
        tile.setMetaTileEntity(tile.machine);
        state.bindHost(tile);
        return tile;
    }

    private static final class Target extends BaseMetaTileEntity implements MachineTerminalIdentity {
        MTECriticalMachine machine; UUID owner; MachineTerminalIdentity.State identity;
        @Override public int getMetaTileID() { return 31800; }
        @Override public UUID getOwnerUuid() { return owner; }
        @Override public void markDirty() {}
        @Override public String gtsr$terminalTargetIdentity() { return identity.identity(); }
    }

    private static final class SparseWorld extends WorldServer {
        TileEntity tile; IChunkProvider chunks; boolean loaded; int tileReads;
        private SparseWorld() { super(null, null, "unused", 0, null, null); }
        @Override public TileEntity getTileEntity(int x, int y, int z) { tileReads++; return tile; }
        @Override public IChunkProvider getChunkProvider() { return chunks; }
        @Override public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {}
    }
}
