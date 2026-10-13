package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;
import com.google.gson.JsonElement;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** Real GTSR material matching, NBT, orphan guard and proxy publication; sparse detached world, no game startup. */
public final class CriticalLoomLifecycleContract {
    private static sun.misc.Unsafe unsafe;
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static void field(Object target, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    public static void main(String[] args) throws Exception {
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        unsafe = (sun.misc.Unsafe) access.get(null);
        java.lang.reflect.Method mapping = TileEntity.class.getDeclaredMethod("addMapping", Class.class, String.class);
        mapping.setAccessible(true); mapping.invoke(null, TileEntityCriticalRenderAnchor.class, "detached.anchor");
        materials();
        for (ForgeDirection frame : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.WEST }) {
            SparseWorld world = world();
            Host host = host(world, 83, 70, -93, frame, true);
            CriticalMachineState state = host.machine.getCriticalState();
            // Exercise the real shell ItemBlock placement seam, without simulating a GTMB inventory reservation.
            for (CriticalGeometry.Voxel voxel : CriticalLoomStructure.voxels(state)) {
                net.minecraft.item.ItemBlock item = new CriticalMaterials.MaterialItemBlock(CriticalLoomStructure.shell(voxel));
                check(item.placeBlockAt(new net.minecraft.item.ItemStack(item), null, world,
                    state.blockWorldX(voxel), state.blockWorldY(voxel.y), state.blockWorldZ(voxel), 1, .5F, .5F, .5F, 0),
                    "Real manual ItemBlock places captured shell palette " + frame);
            }
            check(CriticalLoomStructure.validateGeometry(host.machine), "Actual shell palette forms geometry " + frame);
            check(!CriticalLoomStructure.validateInterfaces(host.machine), "Shell palette does not waive real inputs/EU " + frame);
            CriticalRenderAnchors.ensure(state, true);
            TileEntityCriticalRenderAnchor anchor = (TileEntityCriticalRenderAnchor) world.getTileEntity(
                CriticalRenderAnchors.getX(state), CriticalRenderAnchors.getY(state), CriticalRenderAnchors.getZ(state));
            check(anchor != null && anchor.ownedBy(state), "Complete shell gains anchor without funding interfaces " + frame);
            check((state.getOriginX() & 15) != 0 || (state.getOriginZ() & 15) != 0, "Arbitrary unaligned placement " + frame);
            world.isRemote = true;
            anchor.readFromNBT(write(anchor));
            CriticalGeometry.Voxel moving = CriticalLoomStructure.voxels(state).stream()
                .filter(v -> "loom_hoop_low".equals(v.group)).findFirst().orElseThrow();
            int x = state.blockWorldX(moving), y = state.blockWorldY(moving.y), z = state.blockWorldZ(moving);
            Block shell = CriticalLoomStructure.shell(moving);
            check(!CriticalLoomProxyVisibility.animated(world, x, y, z, shell, 0), "Initially unchecked group keeps block visible");
            CriticalLoomProxyVisibility.publishVisibleGroups(anchor, Set.of("v2_loom:loom_hoop_low"));
            check(CriticalLoomProxyVisibility.animated(world, x, y, z, shell, 0), "Published drawn group hides proxy");
            int redraws = world.redraws;
            CriticalLoomProxyVisibility.publishVisibleGroups(anchor, Set.of("v2_loom:loom_hoop_low"));
            check(world.redraws == redraws, "Stable publication does not redraw chunks");
            CriticalLoomProxyVisibility.publishVisibleGroups(anchor, Set.of());
            check(!CriticalLoomProxyVisibility.animated(world, x, y, z, shell, 0), "Withdrawn incomplete group restores block");
            CriticalLoomProxyVisibility.publishVisibleGroups(anchor, Set.of("v2_loom:loom_hoop_low"));
            world.isRemote = false;
            CriticalRenderAnchors.remove(state);
            check(world.getTileEntity(anchor.xCoord, anchor.yCoord, anchor.zCoord) == null, "Owner removal clears anchor");
            world.isRemote = true;
            check(!CriticalLoomProxyVisibility.animated(world, x, y, z, shell, 0), "Removed anchor releases hiding index");
            check(world.redraws > 0, "Group publication/removal requests chunk redraw");
        }
        orphanCopy();
        geometryIndices(Path.of(args[1]));
        String report = "{\"status\":\"PASS\",\"checks\":" + checks
            + ",\"mode\":\"detached production Java; sparse World; no GTNH/FML startup or live GTMB click\"}";
        Files.writeString(Path.of(args[0]), report);
        System.out.println(report);
    }
    private static NBTTagCompound write(TileEntity tile) {
        NBTTagCompound tag = new NBTTagCompound(); tile.writeToNBT(tag); return tag;
    }
    @SuppressWarnings("unchecked")
    private static void geometryIndices(Path expectedPath) throws Exception {
        for (JsonElement entry : com.google.gson.JsonParser.parseString(Files.readString(expectedPath)).getAsJsonArray()) {
            com.google.gson.JsonObject expected = entry.getAsJsonObject();
            int revision = expected.get("revision").getAsInt();
            NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("geometryVersion", 2);
            tag.setInteger("v2LayoutRevision", revision); tag.setBoolean("loomController", revision != 24);
            tag.setBoolean("loomJournalOwner", revision != 24); tag.setInteger("state", 1);
            tag.setString("jobId", "frozen-job"); tag.setByteArray("placed", new byte[] { 1, 8, 64 });
            tag.setTag("config", new CriticalConfiguration(CriticalTier.T3,
                CriticalMachineKind.valueOf(expected.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)), 0, 0, 0).write());
            CriticalMachineState state = new CriticalMachineState(); state.readFromNBT(tag); state.worldObj = world();
            java.lang.reflect.Method all = CriticalMachineState.class.getDeclaredMethod("allVoxels"); all.setAccessible(true);
            java.util.List<CriticalGeometry.Voxel> voxels = (java.util.List<CriticalGeometry.Voxel>) all.invoke(state);
            Field fingerprint = CriticalMachineState.class.getDeclaredField("modelFingerprint"); fingerprint.setAccessible(true);
            check(fingerprint.getLong(state) == expected.get("fingerprint").getAsLong(), "Real job fingerprint matches released geometry revision " + revision);
            StringBuilder lines = new StringBuilder(); int manual = 0;
            for (CriticalGeometry.Voxel voxel : voxels) {
                lines.append(voxel.x).append(',').append(voxel.y).append(',').append(voxel.z).append(',')
                    .append(voxel.stage).append(',').append(voxel.material).append(',').append(voxel.group).append('\n');
                if (CriticalLoomStructure.isLoomVoxel(voxel)) manual++;
            }
            String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(lines.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            check(sha.equals(expected.get("indexSHA256").getAsString()), "Every real placement index matches reference revision " + revision);
            check(voxels.size() == expected.get("total").getAsInt() && manual == expected.get("manual").getAsInt(),
                "Paid/manual classification revision " + revision);
            check(state.getJobTotal() == manual, "Real initial job stage total revision " + revision);
            NBTTagCompound saved = new NBTTagCompound(); state.writeToNBT(saved);
            check(saved.getInteger("v2LayoutRevision") == revision && java.util.Arrays.equals(saved.getByteArray("placed"), new byte[] { 1, 8, 64 }),
                "Real job revision and placed bitset preserved " + revision);
        }
    }
    @SuppressWarnings("unchecked")
    private static void materials() throws Exception {
        Constructor<CriticalMaterials.MaterialBlock> constructor = CriticalMaterials.MaterialBlock.class.getDeclaredConstructor(
            String.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        for (Map.Entry<String, JsonElement> entry : CriticalGeometry.readJson("materials.json").entrySet()) {
            String id = entry.getValue().getAsJsonObject().get("id").getAsString();
            for (boolean proxy : new boolean[] { false, true }) for (boolean loom : new boolean[] { false, true }) {
                String name = loom ? proxy ? "LOOM_PROXY" : "LOOM_STATIC" : proxy ? "PROXY" : "STATIC";
                Field registry = CriticalMaterials.class.getDeclaredField(name); registry.setAccessible(true);
                Map<String, CriticalMaterials.MaterialBlock> blocks = (Map<String, CriticalMaterials.MaterialBlock>) registry.get(null);
                if (!blocks.containsKey(id)) blocks.put(id, constructor.newInstance(id, proxy, false, false, false, loom));
            }
        }
        CriticalGeometry.Voxel sample = CriticalGeometry.getModel("v2_loom").voxels.get(0);
        check(CriticalLoomStructure.shell(sample).getBlockHardness(null, 0, 0, 0) == 5F, "Manual shells have ordinary hardness");
        check(sample.block.getBlockHardness(null, 0, 0, 0) == 500F, "Paid blocks retain original hardness");
        CriticalMaterials.ConstructionOnlyItemBlock paid = new CriticalMaterials.ConstructionOnlyItemBlock(sample.block);
        check(!paid.placeBlockAt(new net.minecraft.item.ItemStack(paid), null, world(), 0, 0, 0, 1, .5F, .5F, .5F, 0),
            "Paid palette remains forbidden at the actual ItemBlock placement seam");
    }
    private static SparseWorld world() throws Exception {
        SparseWorld world = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        world.blocks = new HashMap<>(); world.tiles = new HashMap<>();
        world.chunks = (IChunkProvider) java.lang.reflect.Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),
            new Class<?>[] { IChunkProvider.class }, (proxy, method, args) -> method.getName().equals("chunkExists") ? world.loaded : null);
        world.loaded = true;
        return world;
    }
    private static Host host(SparseWorld world, int x, int y, int z, ForgeDirection front, boolean selector) throws Exception {
        Host tile = (Host) unsafe.allocateInstance(Host.class);
        tile.setWorldObj(world); tile.xCoord = x; tile.yCoord = y; tile.zCoord = z; tile.front = front;
        tile.machine = (MTECriticalMachine) unsafe.allocateInstance(MTECriticalMachine.class);
        CriticalMachineState state = new CriticalMachineState(); state.setLoomController(selector); state.presetKind(CriticalMachineKind.SOLAR);
        field(tile.machine, MTECriticalMachine.class, "state", state);
        field(tile.machine, MTECriticalMachine.class, "presetKind", selector ? null : CriticalMachineKind.SOLAR);
        field(tile.machine, MetaTileEntity.class, "mBaseMetaTileEntity", tile);
        tile.setMetaTileEntity(tile.machine);
        world.tiles.put(key(x, y, z), tile); state.bindHost(tile);
        return tile;
    }
    private static void orphanCopy() throws Exception {
        SparseWorld world = world(); Host owner = host(world, 56, 68, 47, ForgeDirection.NORTH, true);
        Host original = host(world, 56, 68, 35, ForgeDirection.SOUTH, false);
        original.machine.getLocalCriticalState().setLoomOwnedMain(owner.machine.getLocalCriticalState());
        // The exact conditional retainNBT copy seam: real journal write/read, retained flags and host identity.
        NBTTagCompound retained = new NBTTagCompound(); original.machine.getLocalCriticalState().writeToNBT(retained);
        Host copy = host(world, 200, 68, 200, ForgeDirection.SOUTH, false);
        copy.machine.getLocalCriticalState().readFromNBT((NBTTagCompound) retained.copy());
        EntityPlayerMP player = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
        java.util.UUID actor = java.util.UUID.fromString("5ade7201-a811-4b51-984e-098de47892f2");
        field(player, Entity.class, "entityUniqueID", actor);
        player.posX = 200.5; player.posY = 68.5; player.posZ = 200.5;
        check(copy.machine.getCriticalState() == copy.machine.getLocalCriticalState()
            && copy.machine.getLocalCriticalState().isLoomOwnedMain(), "Retained NBT reproduces unbound passive main");
        check(!copy.machine.getCriticalState().canUse(player), "Copied passive main remains barred from task actions");
        check(copy.machine.canRemoveOrphanedMain(player), "Empty authorized retained copy can be removed");
        world.loaded = false;
        check(!copy.machine.canRemoveOrphanedMain(player), "Unloaded owner neighborhood cannot establish orphan"); world.loaded = true;
        retained.setInteger("state", 1); retained.setString("jobId", "retained-job"); retained.setByteArray("placed", new byte[] { 1, 8 });
        copy.machine.getLocalCriticalState().readFromNBT(retained);
        check(!copy.machine.canRemoveOrphanedMain(player), "Retained active journal cannot be discarded");
        NBTTagCompound roundTrip = new NBTTagCompound(); copy.machine.getLocalCriticalState().writeToNBT(roundTrip);
        check(roundTrip.getString("jobId").equals("retained-job") && java.util.Arrays.equals(roundTrip.getByteArray("placed"), new byte[] { 1, 8 }),
            "Guard preserves retained job and construction bytes");
        player.posX = 56.5; player.posZ = 35.5;
        check(original.machine.getCriticalState() == owner.machine.getLocalCriticalState(), "Canonical main still resolves valid journal");
        check(!original.machine.canRemoveOrphanedMain(player), "Valid owner link cannot use orphan bypass");
        original.front = ForgeDirection.EAST;
        check(!original.machine.canRemoveOrphanedMain(player), "Turned main with valid owner identity cannot use bypass");
        retained.removeTag("jobId"); retained.setInteger("state", 0); retained.removeTag("placed");
        retained.removeTag("ownerUuid"); retained.setString("owner", "2183c3b2-85aa-4519-bf05-eb91cc0db94e");
        NBTTagCompound legacyCopied = forPlacement(retained, actor, "BlueprintActor");
        check(legacyCopied.getString("owner").equals("BlueprintActor"), "Actual GTMB rewrites legacy owner UUID as username");
        copy.ownerUuid = actor; copy.ownerName = "BlueprintActor";
        copy.machine.getLocalCriticalState().readFromNBT(legacyCopied);
        player.posX = 200.5; player.posZ = 200.5;
        check(copy.machine.canRemoveOrphanedMain(player), "Legacy empty copied name recovers only from matching GT UUID/name");
        NBTTagCompound recovered = new NBTTagCompound(); copy.machine.getLocalCriticalState().writeToNBT(recovered);
        check(recovered.getString("ownerUuid").equals(actor.toString()) && !recovered.hasKey("owner"), "New persistence uses the UUID semantic key");
        copy.ownerName = "DifferentName";
        copy.machine.getLocalCriticalState().readFromNBT(legacyCopied);
        check(!copy.machine.canRemoveOrphanedMain(player), "Mismatched GT owner name cannot authorize UUID recovery");
        copy.ownerName = "BlueprintActor";
        legacyCopied.setString("jobId", "protected-job");
        copy.machine.getLocalCriticalState().readFromNBT(legacyCopied);
        check(!copy.machine.canRemoveOrphanedMain(player), "Even idle copied job identity blocks legacy recovery/removal");
        retained.removeTag("owner"); retained.setString("ownerUuid", "2183c3b2-85aa-4519-bf05-eb91cc0db94e");
        NBTTagCompound newCopied = forPlacement(retained, actor, "BlueprintActor");
        check(newCopied.getString("ownerUuid").equals(actor.toString()), "Actual GTMB rewrites ownerUuid to actor UUID");
        copy.machine.getLocalCriticalState().readFromNBT(newCopied);
        check(copy.machine.canRemoveOrphanedMain(player), "New retained passive copy is removable by real actor UUID");
        Host selectorCopy = host(world, 400, 68, 400, ForgeDirection.NORTH, true);
        newCopied.setBoolean("loomOwnedMain", false); newCopied.setBoolean("loomController", true); newCopied.setBoolean("loomJournalOwner", true);
        selectorCopy.machine.getLocalCriticalState().readFromNBT(newCopied);
        player.posX = 400.5; player.posZ = 400.5;
        check(selectorCopy.machine.getCriticalState().canUse(player), "New retained empty selector can use its real actor UUID");
    }
    private static NBTTagCompound forPlacement(NBTTagCompound journal, java.util.UUID actor, String actorName) throws Exception {
        Class<?> type = Class.forName("com.miaokatze.gtmb.hologram.HologramBlueprintNbt");
        java.lang.reflect.Method encode = type.getDeclaredMethod("encodeCanonical", NBTTagCompound.class); encode.setAccessible(true);
        java.lang.reflect.Method place = type.getDeclaredMethod("forPlacement", String.class, java.util.UUID.class,
            String.class, int.class, int.class, int.class, boolean.class, boolean.class); place.setAccessible(true);
        NBTTagCompound tile = new NBTTagCompound(); tile.setTag("criticalState", journal);
        String wire = (String) encode.invoke(null, tile);
        return ((NBTTagCompound) place.invoke(null, wire, actor, actorName, 200, 68, 200, true, false)).getCompoundTag("criticalState");
    }
    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }
    private static final class Host extends BaseMetaTileEntity {
        MTECriticalMachine machine; ForgeDirection front; java.util.UUID ownerUuid; String ownerName = "";
        @Override public ForgeDirection getFrontFacing() { return front; }
        @Override public java.util.UUID getOwnerUuid() { return ownerUuid; }
        @Override public String getOwnerName() { return ownerName; }
        @Override public void markDirty() {}
    }
    private static final class SparseWorld extends World {
        Map<String, Block> blocks; Map<String, TileEntity> tiles; IChunkProvider chunks; boolean loaded; int redraws;
        private SparseWorld() { super(null, "unused", (WorldProvider) null, null, null); }
        @Override public Block getBlock(int x, int y, int z) { return blocks.get(key(x, y, z)); }
        @Override public int getBlockMetadata(int x, int y, int z) { return 0; }
        @Override public boolean isAirBlock(int x, int y, int z) { return !blocks.containsKey(key(x, y, z)); }
        @Override public TileEntity getTileEntity(int x, int y, int z) { return tiles.get(key(x, y, z)); }
        @Override public boolean setBlock(int x, int y, int z, Block block, int metadata, int flags) {
            blocks.put(key(x, y, z), block);
            if (block == CriticalRenderAnchors.BLOCK) {
                TileEntityCriticalRenderAnchor anchor = new TileEntityCriticalRenderAnchor();
                anchor.setWorldObj(this); anchor.xCoord = x; anchor.yCoord = y; anchor.zCoord = z;
                tiles.put(key(x, y, z), anchor);
            }
            return true;
        }
        @Override public boolean setBlockToAir(int x, int y, int z) {
            TileEntity old = tiles.remove(key(x, y, z)); if (old != null) old.invalidate();
            blocks.remove(key(x, y, z)); return true;
        }
        @Override public boolean blockExists(int x, int y, int z) { return loaded; }
        @Override public IChunkProvider getChunkProvider() { return chunks; }
        @Override public long getTotalWorldTime() { return 0; }
        @Override public void markBlockForUpdate(int x, int y, int z) {}
        @Override public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {}
        @Override public void func_147453_f(int x, int y, int z, Block block) {}
        @Override public void markBlockRangeForRenderUpdate(int x, int y, int z, int xx, int yy, int zz) { redraws++; }
        @Override protected IChunkProvider createChunkProvider() { throw new UnsupportedOperationException(); }
        @Override protected int func_152379_p() { throw new UnsupportedOperationException(); }
        @Override public Entity getEntityByID(int id) { throw new UnsupportedOperationException(); }
    }
}
