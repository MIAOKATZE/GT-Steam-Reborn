import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeTravel;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import ic2.api.item.ElectricItem;

/** Production recall + native players/NBT; discovery faults are injected before any energy or movement. */
public final class RecallDiscoveryFailureCheck {
    private static sun.misc.Unsafe unsafe;
    private static int checks, failures;
    private static FaultWorld world;

    public static final class Player extends EntityPlayerMP {
        String message;
        Player() { super(null, null, null, null); }
        @Override public void addChatMessage(IChatComponent chat) {
            message = ((ChatComponentTranslation) chat).getKey();
        }
    }
    public static final class Connection extends NetworkManager {
        Connection() { super(false); }
        @Override public boolean isChannelOpen() { return true; }
    }
    public static final class FaultChunks extends ChunkProviderServer {
        int loads, mode;
        FaultChunks() { super(null, null, null); }
        @Override public Chunk loadChunk(int x, int z) {
            loads++;
            if (mode == 1) throw new IllegalStateException("injected cold chunk generation failure");
            if (mode == 3) throw new AssertionError("injected fatal discovery error");
            return null;
        }
    }
    public static final class FaultWorld extends WorldServer {
        int mode, collisions;
        FaultWorld() { super(null, null, "fixture", 0, null, null); }
        @Override public long getTotalWorldTime() { return 100; }
        @Override public int getActualHeight() { return 256; }
        @Override public List getCollidingBoundingBoxes(Entity player, AxisAlignedBB box) {
            collisions++;
            if (mode == 2) throw new IllegalStateException("injected collision provider failure");
            return Collections.singletonList(box);
        }
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
    private static void check(boolean value, String message) {
        checks++;
        if (!value) { failures++; System.err.println("FAIL " + message); }
    }
    private static NBTTagCompound mark() {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("dimension", 0);
        n.setDouble("x", 10); n.setDouble("y", 64); n.setDouble("z", 10);
        n.setFloat("yaw", 10); n.setFloat("pitch", 20);
        return n;
    }
    private static void attempt(Player player, NBTTagCompound mark, int mode, String reason) {
        FaultChunks chunks = (FaultChunks) world.theChunkProviderServer;
        chunks.mode = mode; chunks.loads = 0; world.mode = mode; world.collisions = 0;
        ElectricItem.calls = 0; player.message = null;
        ItemStack stack = new ItemStack(new Item());
        NBTTagCompound tag = new NBTTagCompound(); tag.setTag("gtsrAnchor", mark); stack.setTagCompound(tag);
        NBTTagCompound before = (NBTTagCompound) tag.copy();
        boolean result = true, escaped = false;
        try { result = SpacetimeTravel.recall(player, stack); }
        catch (RuntimeException failure) { escaped = true; }
        check(!escaped && !result, reason + ": fails without escaping item callback");
        check(ElectricItem.calls == 0, reason + ": no energy simulation/debit");
        check(before.equals(stack.getTagCompound()), reason + ": item NBT unchanged");
        check(player.worldObj == world && player.posX == 10 && player.posY == 64 && player.posZ == 10,
            reason + ": no transfer or rollback attempted");
        check(("gtsr.anchor." + reason).equals(player.message), reason + ": reports the expected failure");
        if (reason.equals("unsafe") && mode == 1) check(chunks.loads == 0, "malformed mark cannot load a default chunk");
    }
    public static void main(String[] args) throws Exception {
        unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        world = (FaultWorld) unsafe.allocateInstance(FaultWorld.class);
        world.theChunkProviderServer = (FaultChunks) unsafe.allocateInstance(FaultChunks.class);
        DimensionManager.world = world;
        field(MinecraftServer.class, "mcServer").set(null, unsafe.allocateInstance(FakeServer.class));
        Player player = (Player) unsafe.allocateInstance(Player.class);
        player.worldObj = world; player.posX = 10; player.posY = 64; player.posZ = 10;
        put(player, Entity.class, "entityUniqueID", UUID.randomUUID());
        DataWatcher watcher = new DataWatcher(player); watcher.addObject(6, Float.valueOf(20));
        put(player, Entity.class, "dataWatcher", watcher);
        NetHandlerPlayServer handler = (NetHandlerPlayServer) unsafe.allocateInstance(NetHandlerPlayServer.class);
        handler.playerEntity = player; player.playerNetServerHandler = handler;
        put(handler, NetHandlerPlayServer.class, "netManager", new Connection());
        attempt(player, mark(), 1, "failed");
        attempt(player, mark(), 2, "failed");
        for (String key : new String[] { "dimension", "x", "y", "z", "yaw", "pitch" }) {
            NBTTagCompound missing = mark(); missing.removeTag(key);
            attempt(player, missing, 1, "unsafe");
            NBTTagCompound wrong = mark(); wrong.setString(key, "not-a-coordinate");
            attempt(player, wrong, 1, "unsafe");
        }
        attempt(player, mark(), 0, "unsafe");
        FaultChunks chunks = (FaultChunks) world.theChunkProviderServer; chunks.mode = 3;
        ItemStack stack = new ItemStack(new Item()); NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("gtsrAnchor", mark()); stack.setTagCompound(tag);
        boolean fatal = false;
        try { SpacetimeTravel.recall(player, stack); } catch (AssertionError expected) { fatal = true; }
        check(fatal, "fatal Errors are not swallowed as recoverable discovery faults");
        if (failures != 0) throw new AssertionError(failures + "/" + checks + " recall assertions failed");
        System.out.println("PASS RecallDiscoveryFailureCheck: " + checks + " assertions; native player/NBT, faulting discovery");
    }
}
