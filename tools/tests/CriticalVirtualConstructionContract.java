package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.common.util.ForgeDirection;

/** Real State install/match/migration/NBT paths with counters on the sparse world's block and tile maps. */
public final class CriticalVirtualConstructionContract {
    private static Object invoke(CriticalMachineState state, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = CriticalMachineState.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(state, args);
    }

    private static final class Writes<T> extends HashMap<String, T> {
        int writes;
        @Override public T put(String key, T value) { writes++; return super.put(key, value); }
        @Override public T remove(Object key) { writes++; return super.remove(key); }
    }

    private static final class Player extends EntityPlayerMP {
        private Player() { super(null, null, null, null); }
        @Override public UUID getUniqueID() { return new UUID(0, 1); }
    }

    private static NBTTagCompound save(CriticalMachineState state) {
        NBTTagCompound saved = new NBTTagCompound();
        state.writeToNBT(saved);
        return saved;
    }

    private static void migrate(CriticalMachineState state) throws Exception {
        for (int pass = 0; pass < 1000 && (Integer) field(state, "virtualConstructionVersion") == 0; pass++)
            invoke(state, "migrateVirtualConstruction", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static void physicalChunkCoverage(CriticalMachineState state, SparseWorld world) throws Exception {
        List<ChunkCoordIntPair> foundations = state.getFoundationChunks();
        check(foundations.size() == 49 && state.getFoundationTotalCells() == 49 * CriticalFoundationChunk.CELLS,
            "Aligned current layout retains all 49 foundation chunk journals and 16 paid cells per chunk");
        List<ChunkCoordIntPair> physical = (List<ChunkCoordIntPair>) invoke(state, "physicalStructureChunks", new Class<?>[0]);
        Set<String> physicalKeys = new HashSet<>();
        for (ChunkCoordIntPair chunk : physical) physicalKeys.add(key(chunk.chunkXPos, 0, chunk.chunkZPos));
        ChunkCoordIntPair decorative = foundations.stream()
            .filter(chunk -> !physicalKeys.contains(key(chunk.chunkXPos, 0, chunk.chunkZPos))).findFirst().orElseThrow(java.util.NoSuchElementException::new);
        CriticalGeometry.Voxel port = geometry(state).stream()
            .filter(voxel -> CriticalStructures.isHatchPosition(state, voxel)).findFirst().orElseThrow(java.util.NoSuchElementException::new);
        int realX = state.blockWorldX(port) >> 4, realZ = state.blockWorldZ(port) >> 4;
        check(physicalKeys.contains(key(realX, 0, realZ)), "A real interface is included in the physical loading footprint");
        CriticalWorldData data = CriticalWorldData.get(world);
        for (ChunkCoordIntPair chunk : foundations) data.foundation(chunk.chunkXPos, chunk.chunkZPos);
        NBTTagCompound before = new NBTTagCompound(); data.writeToNBT(before);
        check(before.getTagList("foundations", 10).tagCount() == 49,
            "All original foundation economic journals exist independently of the smaller physical footprint");
        IChunkProvider original = world.chunks;
        int[] missing = { decorative.chunkXPos, decorative.chunkZPos };
        world.chunks = (IChunkProvider) Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),
            new Class<?>[] { IChunkProvider.class }, (proxy, method, arguments) -> method.getName().equals("chunkExists")
                ? world.loaded && !((Integer) arguments[0] == missing[0] && (Integer) arguments[1] == missing[1]) : null);
        try {
            check(state.areStructureChunksLoaded(), "An unloaded purely virtual outer chunk cannot block the physical structure");
            check((Boolean) invoke(state, "areaLoaded", new Class<?>[0]),
                "Virtual outer chunks do not retain the old stellar/clearance area loading gate");
            missing[0] = realX; missing[1] = realZ;
            check(!state.areStructureChunksLoaded(), "An unloaded real interface chunk still blocks structure loading");
            check(!(Boolean) invoke(state, "areaLoaded", new Class<?>[0]),
                "Stellar/clearance area loading still requires real interface chunks");
            NBTTagCompound after = new NBTTagCompound(); data.writeToNBT(after);
            check(after.equals(before) && state.getFoundationChunks().equals(foundations)
                && after.getTagList("foundations", 10).tagCount() == 49,
                "Changing physical loading readiness never truncates, reorders or advances foundation economic journals");
        } finally {
            world.chunks = original;
        }
    }

    private static void sourceOrigins(String[] args) throws Exception {
        if (args.length == 0) return;
        for (Class<?> type : new Class<?>[] { CriticalMachineState.class, CriticalConstructionLedger.class }) {
            java.nio.file.Path origin = java.nio.file.Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            check(origin.toRealPath().equals(java.nio.file.Paths.get(args[0]).toRealPath()),
                "Requested product source bound: " + type.getSimpleName());
            System.out.println("SOURCE " + type.getName() + "=" + origin.toAbsolutePath());
        }
    }

    public static void main(String[] args) throws Exception {
        sourceOrigins(args);
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true); unsafe = (sun.misc.Unsafe) access.get(null);
        materials();
        SparseWorld world = world();
        java.lang.reflect.Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(world, unsafe.allocateInstance(WorldProviderSurface.class));
        java.lang.reflect.Field ownerWorld = WorldProvider.class.getDeclaredField("worldObj"); ownerWorld.setAccessible(true);
        ownerWorld.set(world.provider, world);
        Writes<Block> blocks = new Writes<>(); Writes<TileEntity> tiles = new Writes<>();
        world.blocks = blocks; world.tiles = tiles;
        CriticalMachineState state = state(world, 33, ForgeDirection.NORTH);
        set(state, "virtualConstructionVersion", 1);
        Player player = (Player) unsafe.allocateInstance(Player.class); player.worldObj = world;
        world.playerEntities.add(player); set(state, "owner", player.getUniqueID().toString());
        CriticalWorldData data = CriticalWorldData.get(world);
        check(data.acquire(state.getStructureId(), player.getUniqueID().toString(), state.getOriginX(), state.getOriginY(),
            state.getOriginZ(), state.getRegionSize(), state.getRegionHeight(), state.xCoord, state.yCoord, state.zCoord),
            "Fixture acquired original region");
        check(data.bindTask(state.getStructureId(), player.getUniqueID().toString(), state.getJobId(), state.getOriginX(),
            state.getOriginY(), state.getOriginZ()), "Fixture bound original job");
        data.updateProtection(state);
        List<CriticalGeometry.Voxel> geometry = geometry(state);
        CriticalGeometry.Voxel cell = geometry.stream().filter(state::isVirtualConstructionVoxel).findFirst().orElseThrow(java.util.NoSuchElementException::new);
        check(!CriticalLoomStructure.isLoomVoxel(cell) && !CriticalStructures.isHatchPosition(state, cell)
            && !CriticalStructures.isController(cell), "Virtual roles exclude real loom, controllers and hatches");
        check(geometry.stream().filter(v -> CriticalLoomStructure.isLoomVoxel(v) || CriticalStructures.isController(v)
            || CriticalStructures.isHatchPosition(state, v) || CriticalLayoutDefinition.isSupplySupport(v))
            .noneMatch(state::isVirtualConstructionVoxel), "Every real interface, support and manual loom stays physical");
        check(!CriticalLoomProxyVisibility.isPhysicalProxy(state, cell),
            "An installed virtual role never enters the physical loom proxy index");
        CriticalGeometry.Voxel realLoom = geometry.stream().filter(CriticalLoomStructure::isLoomVoxel).findFirst().orElseThrow(java.util.NoSuchElementException::new);
        check(CriticalLoomProxyVisibility.isPhysicalProxy(state, realLoom),
            "Real manual loom geometry retains its native physical proxy eligibility");
        physicalChunkCoverage(state, world);
        String position = key(state.blockWorldX(cell), state.blockWorldY(cell.y), state.blockWorldZ(cell));
        blocks.put(position, AIR); TileEntity foreign = new TileEntity(); tiles.put(position, foreign);
        int writes = blocks.writes + tiles.writes;
        check((Boolean) invoke(state, "placeOwnedBlock", new Class<?>[] { CriticalGeometry.Voxel.class }, cell),
            "Direct virtual installation succeeds with foreign decoration-space contents");
        check(blocks.writes + tiles.writes == writes && blocks.get(position) == AIR && tiles.get(position) == foreign,
            "Actual virtual installation performs zero block/TileEntity writes and preserves foreign contents");
        check((Boolean) invoke(state, "matches", new Class<?>[] { CriticalGeometry.Voxel.class }, cell),
            "Virtual completion uses the logical installed mask");
        check(!((BitSet) field(state, "placed")).get(geometry.indexOf(cell)),
            "Logical work never changes the historic physical ownership mask");
        long revision = state.getVirtualVisualRevision();
        NBTTagCompound saved = save(state);
        CriticalMachineState restored = new CriticalMachineState(); restored.readFromNBT(saved); restored.worldObj = world;
        check(restored.isVirtualVoxelInstalled(cell) && restored.getVirtualVisualRevision() == revision,
            "NBT restores logical installation independently of world contents");
        check(restored.virtualVisualVoxels(cell.model).stream().anyMatch(v -> v.positionKey() == cell.positionKey()),
            "Renderer receives the installed static or moving cell after reload");
        check(blocks.writes + tiles.writes == writes, "Save and reload never restore decorative blocks");

        // Upgrade owns only one recorded cell; every other template position is outside its removable journal.
        set(state, "virtualConstructionVersion", 0); set(state, "virtualMigrationCursor", geometry.indexOf(cell));
        ((BitSet) field(state, "virtualInstalled")).clear();
        ((BitSet) field(state, "placed")).set(geometry.indexOf(cell));
        world.loaded = false;
        invoke(state, "migrateVirtualConstruction", new Class<?>[0]);
        check((Integer) field(state, "virtualMigrationCursor") == geometry.indexOf(cell)
            && world.unloadedReads == 0, "Unloaded migration waits without reading or advancing its persisted cursor");
        world.loaded = true;
        invoke(state, "migrateVirtualConstruction", new Class<?>[0]);
        check(tiles.get(position) == foreign && ((BitSet) field(state, "placed")).get(geometry.indexOf(cell)),
            "Migration preserves foreign TileEntity and its unresolved old ownership evidence");
        tiles.remove(position); blocks.remove(position);
        migrate(state);
        check((Integer) field(state, "virtualConstructionVersion") == 1 && !state.isVirtualVoxelInstalled(cell),
            "Missing old decoration is retired without inventing a paid installation");
        set(state, "virtualConstructionVersion", 0); set(state, "virtualMigrationCursor", geometry.indexOf(cell));
        ((BitSet) field(state, "placed")).set(geometry.indexOf(cell)); blocks.put(position, cell.block);
        int before = blocks.writes;
        migrate(state);
        check(!blocks.containsKey(position) && state.isVirtualVoxelInstalled(cell) && blocks.writes == before + 1,
            "Exact generated block without TileEntity is removed once and converted to logical visibility");
        before = blocks.writes;
        migrate(state);
        check(blocks.writes == before, "Completed conversion does not replay world cleanup");
        System.out.println("PASS CriticalVirtualConstructionContract checks=" + checks);
    }
}
