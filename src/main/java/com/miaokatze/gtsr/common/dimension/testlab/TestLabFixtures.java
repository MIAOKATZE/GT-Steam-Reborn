package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.critical.CriticalWorldData;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.main.GTSteamReborn;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** All writes are journalled for bounded, explicit regeneration. No normal world is accepted. */
public final class TestLabFixtures {

    static final UUID OWNER = UUID
        .nameUUIDFromBytes("gtsr-isolated-machine-lab".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final Set<Long> PLACED = new LinkedHashSet<>();
    private static final Deque<Long> CLEAR = new ArrayDeque<>();
    private static final Deque<Long> FREEZE = new ArrayDeque<>();
    private static final Deque<Long> VERIFY_CLEAR = new ArrayDeque<>();
    private static final Map<Long, Deque<Long>> RESET_GROUPS = new LinkedHashMap<>();
    private static final Map<Long, Deque<Long>> RESET_HOSTS = new LinkedHashMap<>();
    private static ForgeChunkManager.Ticket resetTicket;
    private static WorldServer resetWorld;
    private static ChunkCoordIntPair resetChunkPosition;
    private static Chunk resetChunk;
    private static boolean resetTicketForced;
    private static int resetCleared, resetVerified;
    private static Integer dimension;
    private static Journal journal;
    private static WorldServer journalWorld;
    private static boolean emptyChunkReported;

    private TestLabFixtures() {}

    static void attach(WorldServer world) {
        if (!(world.provider instanceof TestLabWorldProvider))
            throw new IllegalArgumentException("Not a GTSR test world");
        if (dimension != null && dimension != world.provider.dimensionId)
            throw new IllegalStateException("Lab dimension changed during a run");
        dimension = world.provider.dimensionId;
        if (journalWorld != world) {
            PLACED.clear();
            cancelReset();
            journalWorld = world;
            emptyChunkReported = false;
            journal = (Journal) world.perWorldStorage.loadData(Journal.class, "gtsr_testlab_fixtures");
            if (journal == null) {
                journal = new Journal();
                world.perWorldStorage.setData("gtsr_testlab_fixtures", journal);
            }
            PLACED.addAll(journal.positions);
        }
    }

    static void track(WorldServer world, int x, int y, int z) {
        attach(world);
        if (y < 0 || y >= 256) throw new IllegalArgumentException("Structure exceeds world height: " + y);
        long position = pack(x, y, z);
        if (PLACED.add(position)) {
            journal.positions.add(position);
            journal.markDirty();
        }
    }

    static IGregTechTileEntity machine(WorldServer world, int x, int y, int z, int id) {
        track(world, x, y, z);
        if (id < 0 || id >= GregTechAPI.METATILEENTITIES.length || GregTechAPI.METATILEENTITIES[id] == null)
            throw new IllegalArgumentException("Unregistered MTE " + id);
        IMetaTileEntity prototype = GregTechAPI.METATILEENTITIES[id];
        int baseType = prototype.getTileEntityBaseType();
        Chunk actualChunk = requireChunk(world, x >> 4, z >> 4);
        boolean changed = world.setBlock(x, y, z, GregTechAPI.sBlockMachines, baseType, 2);
        if (world.getBlock(x, y, z) != GregTechAPI.sBlockMachines || world.getBlockMetadata(x, y, z) != baseType)
            throw new IllegalStateException(
                "GT block placement rejected at " + x
                    + ","
                    + y
                    + ","
                    + z
                    + " id="
                    + id
                    + " changed="
                    + changed
                    + " expectedBaseType="
                    + baseType
                    + " actualBlock="
                    + net.minecraft.block.Block.blockRegistry.getNameForObject(world.getBlock(x, y, z))
                    + " actualMeta="
                    + world.getBlockMetadata(x, y, z)
                    + " chunk="
                    + chunkIdentity(actualChunk)
                    + " directBlock="
                    + net.minecraft.block.Block.blockRegistry.getNameForObject(actualChunk.getBlock(x & 15, y, z & 15))
                    + " gtBlockId="
                    + net.minecraft.block.Block.getIdFromBlock(GregTechAPI.sBlockMachines)
                    + " worldClass="
                    + world.getClass()
                        .getName()
                    + " isRemote="
                    + world.isRemote);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity)) throw new IllegalStateException(
            "Missing GT tile at " + x
                + ","
                + y
                + ","
                + z
                + " id="
                + id
                + " baseType="
                + baseType
                + " changed="
                + changed
                + " actualTile="
                + (tile == null ? "null"
                    : tile.getClass()
                        .getName()));
        IGregTechTileEntity gt = (IGregTechTileEntity) tile;
        gt.setInitialValuesAsNBT(null, (short) id);
        if (gt.getMetaTileID() != id || !prototype.getClass()
            .isInstance(gt.getMetaTileEntity()))
            throw new IllegalStateException(
                "GT MTE initialization mismatch at " + x
                    + ","
                    + y
                    + ","
                    + z
                    + " expectedId="
                    + id
                    + " actualId="
                    + gt.getMetaTileID()
                    + " expectedClass="
                    + prototype.getClass()
                        .getName()
                    + " actualClass="
                    + (gt.getMetaTileEntity() == null ? "null"
                        : gt.getMetaTileEntity()
                            .getClass()
                            .getName()));
        gt.setFrontFacing(ForgeDirection.NORTH);
        gt.setOwnerName("GTSR Test Lab");
        gt.setOwnerUuid(OWNER);
        gt.disableWorking();
        if (gt.getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEMultiBlockBase
            || gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode
            || gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.base.IHubCacheNode) {
            journal.hosts.add(pack(x, y, z));
            journal.markDirty();
        }
        return gt;
    }

    /** Tickets retain chunks; only the server provider's explicit load guarantees a real writable chunk. */
    static Chunk requireChunk(WorldServer world, int chunkX, int chunkZ) {
        attach(world);
        Chunk before = world.getChunkFromChunkCoords(chunkX, chunkZ);
        Chunk loaded = world.theChunkProviderServer.loadChunk(chunkX, chunkZ);
        Chunk visible = world.getChunkFromChunkCoords(chunkX, chunkZ);
        if (loaded == null || loaded instanceof EmptyChunk
            || loaded.xPosition != chunkX
            || loaded.zPosition != chunkZ
            || loaded.worldObj != world
            || visible != loaded
            || !world.theChunkProviderServer.chunkExists(chunkX, chunkZ))
            throw new IllegalStateException(
                "Lab chunk is not physically loaded: requested=" + chunkX
                    + ","
                    + chunkZ
                    + " loaded="
                    + chunkIdentity(loaded)
                    + " visible="
                    + chunkIdentity(visible)
                    + " loadChunkOnProvideRequest="
                    + world.theChunkProviderServer.loadChunkOnProvideRequest
                    + " worldClass="
                    + world.getClass()
                        .getName()
                    + " isRemote="
                    + world.isRemote);
        if (before instanceof EmptyChunk && !emptyChunkReported) {
            emptyChunkReported = true;
            com.miaokatze.gtsr.main.GTSteamReborn.LOG.info(
                "[GTSR-TESTLAB] explicit load replaced EmptyChunk: dim=" + world.provider.dimensionId
                    + " requested="
                    + chunkX
                    + ","
                    + chunkZ
                    + " before="
                    + chunkIdentity(before)
                    + " loaded="
                    + chunkIdentity(loaded)
                    + " loadChunkOnProvideRequest="
                    + world.theChunkProviderServer.loadChunkOnProvideRequest);
        }
        return loaded;
    }

    private static String chunkIdentity(Chunk chunk) {
        return chunk == null ? "null"
            : chunk.getClass()
                .getName() + "@"
                + Integer.toHexString(System.identityHashCode(chunk))
                + "("
                + chunk.xPosition
                + ","
                + chunk.zPosition
                + ")";
    }

    public static void reset(WorldServer world) {
        attach(world);
        clearResetQueues();
        TestLabSupply.clear(world);
        for (long p : journal.hosts) enqueueResetHost(p);
        for (long p : PLACED) enqueueResetPosition(p);
    }

    public static void reset(WorldServer world, int originX, int originZ) {
        attach(world);
        clearResetQueues();
        for (long p : journal.hosts) {
            int x = (int) (p >> 38), z = (int) (p << 26 >> 38);
            if (x >= originX && x < originX + 256 && z >= originZ && z < originZ + 256) enqueueResetHost(p);
        }
        for (long p : PLACED) {
            int x = (int) (p >> 38), z = (int) (p << 26 >> 38);
            if (x >= originX && x < originX + 256 && z >= originZ && z < originZ + 256) {
                enqueueResetPosition(p);
            }
        }
    }

    private static void clearResetQueues() {
        // A canceled/failed reset can restart from the retained authorization history.
        releaseResetTicket();
        FREEZE.clear();
        CLEAR.clear();
        VERIFY_CLEAR.clear();
        RESET_GROUPS.clear();
        RESET_HOSTS.clear();
    }

    public static void cancelReset() {
        clearResetQueues();
    }

    private static long resetChunkKey(long p) {
        return ChunkCoordIntPair.chunkXZ2Int((int) (p >> 38) >> 4, (int) (p << 26 >> 38) >> 4);
    }

    private static void enqueueResetHost(long p) {
        long key = resetChunkKey(p);
        // Retire controller chunks before body-only chunks. Once their pinned group is cleared,
        // no unloaded/reloaded controller can resume construction while later groups are retired.
        RESET_GROUPS.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        RESET_HOSTS.computeIfAbsent(key, ignored -> new ArrayDeque<>())
            .addLast(p);
    }

    private static void enqueueResetPosition(long p) {
        RESET_GROUPS.computeIfAbsent(resetChunkKey(p), ignored -> new ArrayDeque<>())
            .addLast(p);
    }

    public static boolean tickReset(WorldServer world) {
        try {
            return tickResetGroup(world);
        } catch (RuntimeException | LinkageError failure) {
            cancelReset();
            throw failure;
        }
    }

    private static boolean tickResetGroup(WorldServer world) {
        attach(world);
        if (resetChunk == null) {
            if (RESET_GROUPS.isEmpty()) return true;
            beginResetGroup(world);
        }
        Chunk visible = requireChunk(world, resetChunkPosition.chunkXPos, resetChunkPosition.chunkZPos);
        if (visible != resetChunk || !resetTicket.getChunkList()
            .contains(resetChunkPosition))
            throw new IllegalStateException("Lab reset lost its retained chunk: " + resetGroupDiagnostic(visible));
        Set<Long> loadedChunks = new java.util.HashSet<>();
        if (!FREEZE.isEmpty()) {
            for (int i = 0; i < 16 && !FREEZE.isEmpty(); i++) {
                freeze(world, FREEZE.peekFirst(), loadedChunks);
                FREEZE.removeFirst();
            }
            return false;
        }
        boolean clearing = !CLEAR.isEmpty();
        for (int i = 0; i < 2048 && !CLEAR.isEmpty(); i++) {
            long p = CLEAR.peekLast();
            int x = (int) (p >> 38), y = (int) (p & 255), z = (int) (p << 26 >> 38);
            requireChunkOnce(world, x, z, loadedChunks);
            // Avoid inventory drops / explosions from breaking a running controller.
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof IGregTechTileEntity) {
                IGregTechTileEntity gt = (IGregTechTileEntity) tile;
                gt.disableWorking();
            }
            world.removeTileEntity(x, y, z);
            net.minecraft.block.Block canonical = canonicalBlock(y);
            world.setBlock(x, y, z, canonical, 0, 2);
            if (world.getBlock(x, y, z) != canonical || world.getBlockMetadata(x, y, z) != 0)
                throw new IllegalStateException(
                    "Lab reset failed to restore physical block at " + x
                        + ","
                        + y
                        + ","
                        + z
                        + "; "
                        + resetGroupDiagnostic(visible));
            CLEAR.removeLast();
            resetCleared++;
            // Keep the authorized coordinate/host history. Chunk and WorldSavedData writes are
            // separate, so deleting a claim here could strand a physical block after interruption.
            // The durable union also retires old layouts when a later fixture uses fewer cells.
        }
        // Begin the read-only audit on a later server tick, after all writes have completed.
        if (clearing) return false;
        for (int i = 0; i < 2048 && !VERIFY_CLEAR.isEmpty(); i++) {
            long p = VERIFY_CLEAR.peekFirst();
            int x = (int) (p >> 38), y = (int) (p & 255), z = (int) (p << 26 >> 38);
            requireChunkOnce(world, x, z, loadedChunks);
            net.minecraft.block.Block canonical = canonicalBlock(y);
            if (world.getBlock(x, y, z) != canonical || world.getBlockMetadata(x, y, z) != 0
                || world.getTileEntity(x, y, z) != null)
                throw new IllegalStateException(
                    "Lab reset audit found a remaining block/tile at " + x
                        + ","
                        + y
                        + ","
                        + z
                        + " actual="
                        + net.minecraft.block.Block.blockRegistry.getNameForObject(world.getBlock(x, y, z))
                        + " metadata="
                        + world.getBlockMetadata(x, y, z)
                        + "; "
                        + resetGroupDiagnostic(visible));
            VERIFY_CLEAR.removeFirst();
            resetVerified++;
        }
        if (!VERIFY_CLEAR.isEmpty()) return false;
        GTSteamReborn.LOG.info("[GTSR-TESTLAB] reset_chunk phase=VERIFIED " + resetGroupDiagnostic(visible));
        releaseResetTicket();
        return RESET_GROUPS.isEmpty();
    }

    private static void beginResetGroup(WorldServer world) {
        Map.Entry<Long, Deque<Long>> group = RESET_GROUPS.entrySet()
            .iterator()
            .next();
        long key = group.getKey();
        resetWorld = world;
        resetChunkPosition = new ChunkCoordIntPair((int) key, (int) (key >> 32));
        Object mod = Loader.instance()
            .getIndexedModList()
            .get(GTSteamReborn.MODID)
            .getMod();
        resetTicket = ForgeChunkManager.requestTicket(mod, world, ForgeChunkManager.Type.NORMAL);
        if (resetTicket == null)
            throw new IllegalStateException("Forge has no ticket for the bounded lab reset window");
        resetTicket.getModData()
            .setString("OwnerType", "TestLabTransientReset");
        ForgeChunkManager.forceChunk(resetTicket, resetChunkPosition);
        resetTicketForced = resetTicket.getChunkList()
            .contains(resetChunkPosition);
        if (!resetTicketForced) throw new IllegalStateException("Forge rejected the lab reset chunk retention");
        resetChunk = requireChunk(world, resetChunkPosition.chunkXPos, resetChunkPosition.chunkZPos);
        Deque<Long> hosts = RESET_HOSTS.remove(key);
        if (hosts != null) FREEZE.addAll(hosts);
        CLEAR.addAll(group.getValue());
        VERIFY_CLEAR.addAll(group.getValue());
        RESET_GROUPS.remove(key);
        resetCleared = resetVerified = 0;
        GTSteamReborn.LOG.info(
            "[GTSR-TESTLAB] reset_chunk phase=RETAIN cells=" + CLEAR
                .size() + " hosts=" + FREEZE.size() + " " + resetGroupDiagnostic(resetChunk));
    }

    private static String resetGroupDiagnostic(Chunk visible) {
        return "expected=" + chunkIdentity(resetChunk)
            + " visible="
            + chunkIdentity(visible)
            + " forced="
            + resetTicketForced
            + " cleared="
            + resetCleared
            + " verified="
            + resetVerified;
    }

    private static void releaseResetTicket() {
        if (resetTicket != null) {
            // Forge may already have dropped its per-world ticket map in WorldEvent.Unload.
            if (!resetTicketForced || resetWorld != null && ForgeChunkManager.getPersistentChunksFor(resetWorld)
                .containsEntry(resetChunkPosition, resetTicket)) ForgeChunkManager.releaseTicket(resetTicket);
        }
        resetTicket = null;
        resetWorld = null;
        resetChunkPosition = null;
        resetChunk = null;
        resetTicketForced = false;
    }

    private static net.minecraft.block.Block canonicalBlock(int y) {
        return y == 0 ? Blocks.bedrock : y == 63 ? Blocks.stone : y == 64 ? Blocks.quartz_block : Blocks.air;
    }

    private static void requireChunkOnce(WorldServer world, int x, int z, Set<Long> loadedChunks) {
        long key = net.minecraft.world.ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4);
        if (loadedChunks.add(key)) requireChunk(world, x >> 4, z >> 4);
    }

    private static void freeze(WorldServer world, long p, Set<Long> loadedChunks) {
        int x = (int) (p >> 38), y = (int) (p & 255), z = (int) (p << 26 >> 38);
        requireChunkOnce(world, x, z, loadedChunks);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity)) return;
        IGregTechTileEntity gt = (IGregTechTileEntity) tile;
        gt.disableWorking();
        if (gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.base.IHubCacheNode) unbindNode(gt);
        if (gt.getMetaTileEntity() instanceof MTECriticalMachine) {
            com.miaokatze.gtsr.common.critical.CriticalMachineState state = ((MTECriticalMachine) gt
                .getMetaTileEntity()).getCriticalState();
            String id = state.getStructureId();
            FakePlayer player = FakePlayerFactory.get(world, new GameProfile(OWNER, "[GTSR Test Lab]"));
            player.capabilities.isCreativeMode = true;
            player.isDead = false;
            player.setPosition(state.xCoord + 0.5, state.yCoord, state.zCoord + 0.5);
            if (!state.setEnabled(player, false))
                throw new IllegalStateException("Cannot stop lab construction journal before reset: " + id);
            state.setChunkLoadingEnabled(false);
            state.invalidate();
            if (!id.isEmpty()) CriticalWorldData.get(world)
                .release(id);
        }
        if (gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode) {
            unbindNode(gt);
            ((com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode) gt.getMetaTileEntity()).releaseAllChunks();
        }
    }

    static void unbindNode(IGregTechTileEntity node) {
        NBTTagCompound saved = new NBTTagCompound();
        node.getMetaTileEntity()
            .saveNBTData(saved);
        if (saved.hasKey("gtsr.hubPos")) {
            saved.removeTag("gtsr.hubPos");
        }
        node.getMetaTileEntity()
            .loadNBTData(saved);
        node.disableWorking();
    }

    static String snapshot(IGregTechTileEntity tile) {
        if (tile == null) return "controller=null";
        NBTTagCompound nbt = new NBTTagCompound();
        ((TileEntity) tile).writeToNBT(nbt);
        return "pos=" + tile.getXCoord()
            + ","
            + tile.getYCoord()
            + ","
            + tile.getZCoord()
            + " dim="
            + tile.getWorld().provider.dimensionId
            + " meta="
            + tile.getMetaTileID()
            + " active="
            + tile.isActive()
            + " allowed="
            + tile.isAllowedToWork()
            + " nbt="
            + nbt;
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | y;
    }

    public static final class Journal extends WorldSavedData {

        // Monotonic authorization history, not an inventory of currently occupied blocks.
        // A successful reset leaves it intact so repeated and interrupted regeneration is idempotent.
        final Set<Long> positions = new LinkedHashSet<>();
        final Set<Long> hosts = new LinkedHashSet<>();

        public Journal() {
            super("gtsr_testlab_fixtures");
        }

        public Journal(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            positions.clear();
            NBTTagList list = nbt.getTagList("positions", 10);
            for (int i = 0; i < list.tagCount(); i++) positions.add(
                list.getCompoundTagAt(i)
                    .getLong("p"));
            hosts.clear();
            NBTTagList hostList = nbt.getTagList("hosts", 10);
            for (int i = 0; i < hostList.tagCount(); i++) hosts.add(
                hostList.getCompoundTagAt(i)
                    .getLong("p"));
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {
            NBTTagList list = new NBTTagList();
            for (long p : positions) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setLong("p", p);
                list.appendTag(entry);
            }
            nbt.setTag("positions", list);
            NBTTagList hostList = new NBTTagList();
            for (long p : hosts) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setLong("p", p);
                hostList.appendTag(entry);
            }
            nbt.setTag("hosts", hostList);
        }
    }
}
