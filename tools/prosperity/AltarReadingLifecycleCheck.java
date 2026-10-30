import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import com.miaokatze.gtsr.common.dimension.prosperity.altar.*;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlocks;

/** Actual player equality/connection and production reading leases; explicit world/payment fixtures. */
public final class AltarReadingLifecycleCheck {
    private static sun.misc.Unsafe unsafe;
    private static int checks, failures, entityIds;
    private static FixtureWorld world;
    private static TileSpacetimeAltarStory board;
    private static TileSpacetimeAltar core;
    private static FakeServer server;

    public static final class Player extends EntityPlayerMP {
        Player() { super(null, null, null, null); }
        @Override public boolean isEntityAlive() { return !isDead; }
        @Override public void addChatMessage(IChatComponent message) {}
    }
    public static final class Handler extends NetHandlerPlayServer {
        Handler() { super(null, null, null); }
        @Override public void sendPacket(net.minecraft.network.Packet packet) {}
    }
    public static final class Connection extends NetworkManager {
        public boolean open = true;
        Connection() { super(false); }
        @Override public boolean isChannelOpen() { return open; }
    }
    public static final class Chunks extends ChunkProviderServer {
        Chunks() { super(null, null, null); }
        @Override public boolean chunkExists(int x, int z) { return true; }
    }
    public static final class FixtureWorld extends WorldServer {
        IChunkProvider chunks;
        long time = 10;
        FixtureWorld() { super(null, null, "fixture", 0, null, null); }
        @Override protected IChunkProvider createChunkProvider() { return chunks; }
        @Override protected int getRenderDistanceChunks() { return 1; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public IChunkProvider getChunkProvider() { return chunks; }
        @Override public long getTotalWorldTime() { return time; }
        @Override public TileEntity getTileEntity(int x, int y, int z) {
            return x == core.xCoord && y == core.yCoord && z == core.zCoord ? core
                : x == board.xCoord && y == board.yCoord && z == board.zCoord ? board : null;
        }
        @Override public Block getBlock(int x, int y, int z) {
            return getTileEntity(x, y, z) == core ? SpacetimeAltarBlocks.core : RemasterBlocks.get("board");
        }
    }
    public static final class Configuration extends ServerConfigurationManager {
        Configuration() { super(null); }
    }
    public static final class FakeServer extends MinecraftServer {
        FakeServer() { super(null, null); }
        @Override public WorldServer worldServerForDimension(int dimension) { return world; }
        @Override protected boolean startServer() { return false; }
        @Override public boolean canStructuresSpawn() { return false; }
        @Override public WorldSettings.GameType getGameType() { return null; }
        @Override public EnumDifficulty getDifficulty() { return null; }
        @Override public boolean isHardcore() { return false; }
        @Override public int getOpPermissionLevel() { return 0; }
        @Override public boolean isDedicatedServer() { return false; }
        @Override public boolean func_152363_m() { return false; }
        @Override public boolean isCommandBlockEnabled() { return false; }
        @Override public String shareToLAN(WorldSettings.GameType type, boolean cheats) { return null; }
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void put(Object owner, Class<?> type, String name, Object value) throws Exception {
        unsafe.putObject(owner, unsafe.objectFieldOffset(field(type, name)), value);
    }
    @SuppressWarnings("unchecked")
    private static Map<EntityPlayerMP, ?> sessions() throws Exception {
        return (Map<EntityPlayerMP, ?>) field(SpacetimeAltarStory.class, "SESSIONS").get(null);
    }
    private static Player player() throws Exception {
        Player p = (Player) unsafe.allocateInstance(Player.class);
        p.setEntityId(++entityIds);
        p.worldObj = world;
        p.posX = 5.5; p.posY = 67.5; p.posZ = 4.5;
        NetHandlerPlayServer h = (NetHandlerPlayServer) unsafe.allocateInstance(Handler.class);
        h.playerEntity = p;
        put(h, NetHandlerPlayServer.class, "netManager", new Connection());
        p.playerNetServerHandler = h;
        server.getConfigurationManager().playerEntityList.add(p);
        return p;
    }
    private static UUID read(Player player) throws Exception {
        check(SpacetimeAltarStory.read(player, board), "live exact board read admitted");
        Object session = sessions().get(player);
        return (UUID) field(session.getClass(), "nonce").get(session);
    }
    private static void reset() throws Exception {
        sessions().clear(); core.activations = 0;
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) { failures++; System.err.println("FAIL " + message); }
    }
    public static void main(String[] args) throws Exception {
        unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        world = (FixtureWorld) unsafe.allocateInstance(FixtureWorld.class);
        server = (FakeServer) unsafe.allocateInstance(FakeServer.class);
        field(MinecraftServer.class, "mcServer").set(null, server);
        Configuration configuration = (Configuration) unsafe.allocateInstance(Configuration.class);
        put(configuration, ServerConfigurationManager.class, "playerEntityList", new ArrayList<>());
        server.setConfigManager(configuration);
        put(world, World.class, "provider", new WorldProviderSurface());
        world.chunks = (Chunks) unsafe.allocateInstance(Chunks.class);
        core = new TileSpacetimeAltar(); core.xCoord = 7; core.yCoord = 72; core.zCoord = 7; core.setWorldObj(world);
        board = new TileSpacetimeAltarStory(); board.xCoord = 5; board.yCoord = 67; board.zCoord = 4; board.setWorldObj(world);
        Player old = player();
        UUID nonce = read(old);
        SpacetimeAltarStory.activateFromReading(old, nonce);
        check(core.activations == 1, "same live entity consumes its reading lease");
        SpacetimeAltarStory.activateFromReading(old, nonce);
        check(core.activations == 1, "nonce remains one-use");

        reset(); old = player(); nonce = read(old);
        Player replacement = player(); replacement.setEntityId(old.getEntityId());
        check(old.equals(replacement), "native Entity equality reproduces respawn numeric-ID alias");
        SpacetimeAltarStory.activateFromReading(replacement, nonce);
        check(core.activations == 0, "replacement entity cannot inherit the old reading lease");

        reset(); old = player(); nonce = read(old);
        ((Connection) old.playerNetServerHandler.netManager).open = false;
        SpacetimeAltarStory.activateFromReading(old, nonce);
        check(core.activations == 0, "disconnected queued activation cannot spend saved-away inventory");

        reset(); old = player(); nonce = read(old);
        NetHandlerPlayServer captured = old.playerNetServerHandler;
        old.playerNetServerHandler = player().playerNetServerHandler;
        old.playerNetServerHandler.playerEntity = old;
        SpacetimeAltarStory.activateFromReading(old, nonce);
        check(core.activations == 0, "replacement connection cannot inherit an old lease");
        old.playerNetServerHandler = captured;

        reset(); old = player(); read(old);
        replacement = player(); replacement.setEntityId(old.getEntityId());
        UUID freshNonce = read(replacement);
        check(sessions().keySet().iterator().next() == replacement, "new read owns the actual weak key, not old equal entity");
        SpacetimeAltarStory.activateFromReading(old, freshNonce);
        check(core.activations == 0, "old entity cannot consume replacement's new lease");
        SpacetimeAltarStory.activateFromReading(replacement, freshNonce);
        check(core.activations == 1, "stale activation does not retire the replacement's valid lease");

        reset(); old = player(); nonce = read(old);
        old.playerNetServerHandler.playerEntity = player();
        SpacetimeAltarStory.activateFromReading(old, nonce);
        check(core.activations == 0, "native reassigned handler cannot act for its old player");

        reset(); old = player();
        server.getConfigurationManager().playerEntityList.clear();
        check(!SpacetimeAltarStory.read(old, board), "removed online-list player cannot obtain a new lease");
        check(sessions().isEmpty(), "offline read stores no session");

        reset(); old = player(); nonce = read(old);
        world.time = 1201;
        SpacetimeAltarStory.activateFromReading(old, nonce);
        check(core.activations == 0, "expired same-session lease remains rejected");
        world.time = 0;

        reset(); old = player(); nonce = read(old);
        SpacetimeAltarStory.activateFromReading(old, UUID.randomUUID());
        SpacetimeAltarStory.activateFromReading(old, nonce);
        check(core.activations == 0, "wrong nonce consumes only its own one-use lease");
        if (failures != 0) throw new AssertionError(failures + "/" + checks + " altar lifecycle assertions failed");
        System.out.println("PASS AltarReadingLifecycleCheck: " + checks + " assertions; native players, fixture world/payment");
    }
}
