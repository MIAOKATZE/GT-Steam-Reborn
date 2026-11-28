package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.SparseWorld;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.check;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.checks;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.field;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.geometry;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.key;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.materials;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.set;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.state;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.unsafe;
import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.world;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

/** Actual physical writes, collision, ownership, funding checkpoints and teardown with a sparse World. */
public final class CriticalPhysicalConstructionContract {
    private static final Item SHELL = new Item(), CONTROLLER = new Item();

    private static Object invoke(CriticalMachineState state, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = CriticalMachineState.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(state, args);
    }

    private static final class Writes<T> extends HashMap<String, T> {
        int attempts;
        boolean reject;
        @Override public T put(String key, T value) { attempts++; return reject ? get(key) : super.put(key, value); }
    }

    private static final class Player extends EntityPlayerMP {
        private Player() { super(null, null, null, null); }
        @Override public UUID getUniqueID() { return new UUID(0, 1); }
    }

    private static final class Host extends TileEntity {
        @Override public void markDirty() {}
    }

    private static NBTTagCompound save(CriticalMachineState state) {
        NBTTagCompound saved = new NBTTagCompound(); state.writeToNBT(saved); return saved;
    }

    private static CriticalConstructionEconomy manifest(String material, int count) {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("layout", "physical-current-layout-contract");
        n.setTag("controller", CriticalResourceNbt.writeItems(new ItemStack[] { new ItemStack(CONTROLLER) }));
        n.setTag("demand", CriticalResourceNbt.writeItems(new ItemStack[] { new ItemStack(SHELL, count), new ItemStack(CONTROLLER) }));
        NBTTagList rows = new NBTTagList(); NBTTagCompound row = new NBTTagCompound();
        row.setString("alias", material);
        row.setTag("item", CriticalResourceNbt.writeItems(new ItemStack[] { new ItemStack(SHELL) })); rows.appendTag(row);
        n.setTag("materials", rows); n.setTag("carriers", new NBTTagList());
        return CriticalConstructionEconomy.read(n);
    }

    private static long balance(CriticalConstructionLedger ledger, String field) {
        NBTTagCompound n = new NBTTagCompound(); ledger.write(n);
        NBTTagList rows = n.getTagList("constructionPhysicalBalances", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            if (CriticalResourceNbt.readItems(row.getTagList("item", 10), 1)[0].getItem() == SHELL) return row.getLong(field);
        }
        return 0;
    }

    private static void originalLoadingCoverage(CriticalMachineState state, SparseWorld world) throws Exception {
        List<ChunkCoordIntPair> foundations = state.getFoundationChunks();
        check(foundations.size() == 49 && state.getFoundationTotalCells() == 49 * CriticalFoundationChunk.CELLS,
            "The physical layout retains its original 49 foundation journals and full cell bill");
        ChunkCoordIntPair outer = foundations.get(0);
        IChunkProvider original = world.chunks;
        world.chunks = (IChunkProvider) Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),
            new Class<?>[] { IChunkProvider.class }, (proxy, method, args) -> method.getName().equals("chunkExists")
                ? !((Integer) args[0] == outer.chunkXPos && (Integer) args[1] == outer.chunkZPos) : null);
        try {
            check(!state.areStructureChunksLoaded() && !(Boolean) invoke(state, "areaLoaded", new Class<?>[0]),
                "An unloaded outer structure chunk retains the original physical loading gate");
        } finally { world.chunks = original; }
        check(state.areStructureChunksLoaded(), "Restoring actual chunk availability restores the original loading gate");
    }

    private static void sourceOrigins(String[] args) throws Exception {
        if (args.length == 0) return;
        for (Class<?> type : new Class<?>[] { CriticalMachineState.class, CriticalConstructionLedger.class,
            CriticalRenderAnchors.class, CriticalBlockProtection.class }) {
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
        java.lang.reflect.Method register = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true); register.invoke(Item.itemRegistry, 31120, "gtsr:physical_contract_shell", SHELL);
        register.invoke(Item.itemRegistry, 31121, "gtsr:physical_contract_controller", CONTROLLER);
        materials();
        SparseWorld world = world();
        java.lang.reflect.Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(world, unsafe.allocateInstance(WorldProviderSurface.class));
        java.lang.reflect.Field ownerWorld = WorldProvider.class.getDeclaredField("worldObj"); ownerWorld.setAccessible(true);
        ownerWorld.set(world.provider, world);
        Writes<Block> blocks = new Writes<>(); world.blocks = blocks;
        CriticalMachineState state = state(world, 33, ForgeDirection.NORTH);
        Player player = (Player) unsafe.allocateInstance(Player.class); player.worldObj = world;
        world.playerEntities.add(player); set(state, "owner", player.getUniqueID().toString());
        Host host = new Host(); host.setWorldObj(world); host.xCoord = state.xCoord; host.yCoord = state.yCoord; host.zCoord = state.zCoord;
        world.tiles.put(key(host.xCoord, host.yCoord, host.zCoord), host); state.bindHost(host);
        CriticalWorldData data = CriticalWorldData.get(world);
        check(data.acquire(state.getStructureId(), player.getUniqueID().toString(), state.getOriginX(), state.getOriginY(),
            state.getOriginZ(), state.getRegionSize(), state.getRegionHeight(), state.xCoord, state.yCoord, state.zCoord), "Acquire original region");
        check(data.bindTask(state.getStructureId(), player.getUniqueID().toString(), state.getJobId(), state.getOriginX(),
            state.getOriginY(), state.getOriginZ()), "Bind physical journal");
        List<CriticalGeometry.Voxel> geometry = geometry(state);
        List<CriticalGeometry.Voxel> cells = new ArrayList<>();
        String material = null;
        for (CriticalGeometry.Voxel voxel : geometry) {
            if (CriticalLoomStructure.isLoomVoxel(voxel) || CriticalStructures.isController(voxel)
                || CriticalStructures.isHatchPosition(state, voxel) || CriticalLayoutDefinition.isSupplySupport(voxel)) continue;
            if (material == null) material = voxel.material;
            if (material.equals(voxel.material) && cells.size() < 10) cells.add(voxel);
        }
        check(cells.size() == 10, "Ten real automatic construction cells are selected from the authored layout");
        originalLoadingCoverage(state, world);
        CriticalConstructionLedger ledger = (CriticalConstructionLedger) field(state, "constructionLedger");
        ledger.freezeJob(manifest(material, 10));
        ledger.recordRoundPayment(new ItemStack[] { new ItemStack(SHELL, 10), new ItemStack(CONTROLLER) }, new FluidStack[0]);
        CriticalGeometry.Voxel cell = cells.get(0); int index = geometry.indexOf(cell);
        String target = key(state.blockWorldX(cell), state.blockWorldY(cell.y), state.blockWorldZ(cell));
        TileEntity foreign = new TileEntity(); blocks.put(target, cell.block); world.tiles.put(target, foreign);
        int attempts = blocks.attempts;
        check(!(Boolean) invoke(state, "placeOwnedBlock", new Class<?>[] { CriticalGeometry.Voxel.class }, cell)
            && blocks.attempts == attempts && world.tiles.get(target) == foreign && balance(ledger, "installed") == 0,
            "An occupied real target is retained without a world write, adoption or material installation");
        blocks.remove(target); world.tiles.remove(target);
        attempts = blocks.attempts; blocks.reject = true;
        check(!(Boolean) invoke(state, "placeOwnedBlock", new Class<?>[] { CriticalGeometry.Voxel.class }, cell),
            "A world which drops a write cannot report a successful physical installation");
        check(blocks.attempts == attempts + 1 && !((BitSet) field(state, "placed")).get(index)
            && balance(ledger, "installed") == 0 && balance(ledger, "escrow") == 10,
            "Failed actual placement preserves paid escrow and never claims installed material");
        NBTTagCompound paidFailure = save(state); state.readFromNBT(paidFailure); geometry = geometry(state);
        ledger = (CriticalConstructionLedger) field(state, "constructionLedger");
        check(ledger.funded() && balance(ledger, "paid") == 10 && balance(ledger, "escrow") == 10,
            "Reload preserves the paid retry boundary without a second debit");
        blocks.reject = false;
        for (CriticalGeometry.Voxel voxel : cells)
            check((Boolean) invoke(state, "placeOwnedBlock", new Class<?>[] { CriticalGeometry.Voxel.class }, voxel),
                "Each funded installation writes an actual matching block");
        check(balance(ledger, "installed") == 10 && balance(ledger, "escrow") == 0
            && cell.block.getCollisionBoundingBoxFromPool(world, state.blockWorldX(cell), state.blockWorldY(cell.y), state.blockWorldZ(cell)) != null,
            "Successful paid structure retains real collision and installed entitlement");
        state.markDirty();
        check(cell.block.getBlockHardness(world, state.blockWorldX(cell), state.blockWorldY(cell.y), state.blockWorldZ(cell)) == -1F,
            "Actual recorded structure retains its original owned-block protection");
        world.setBlockToAir(state.blockWorldX(cell), state.blockWorldY(cell.y), state.blockWorldZ(cell));
        state.onPlacementInvalidated(cell.positionKey());
        check(!(Boolean) invoke(state, "matches", new Class<?>[] { CriticalGeometry.Voxel.class }, cell)
            && balance(ledger, "destroyed") == 1 && balance(ledger, "installed") == 9,
            "A removed real cell invalidates the structure and its salvage entitlement once");
        state.onPlacementInvalidated(cell.positionKey());
        check(balance(ledger, "destroyed") == 1, "Repeated damage observations never consume the same installed allocation twice");
        ledger.completeRound(); ledger.recordRoundPayment(new ItemStack[] { new ItemStack(SHELL) }, new FluidStack[0]);
        check((Boolean) invoke(state, "placeOwnedBlock", new Class<?>[] { CriticalGeometry.Voxel.class }, cell)
            && balance(ledger, "paid") == 11 && balance(ledger, "installed") == 10 && balance(ledger, "destroyed") == 1,
            "Paid repair restores the real block while retaining the old loss history");
        NBTTagCompound repaired = save(state); state.readFromNBT(repaired); geometry(state);
        ledger = (CriticalConstructionLedger) field(state, "constructionLedger");
        check(balance(ledger, "paid") == 11 && balance(ledger, "installed") == 10 && balance(ledger, "destroyed") == 1
            && (Boolean) invoke(state, "matches", new Class<?>[] { CriticalGeometry.Voxel.class }, cell),
            "Reload retains repaired real ownership, exact payments and the original destroyed allocation");
        CriticalRenderAnchors.ensure(state, true);
        check(world.getBlock(CriticalRenderAnchors.getX(state), CriticalRenderAnchors.getY(state), CriticalRenderAnchors.getZ(state)) == CriticalRenderAnchors.BLOCK
            && world.getTileEntity(CriticalRenderAnchors.getX(state), CriticalRenderAnchors.getY(state), CriticalRenderAnchors.getZ(state)) instanceof TileEntityCriticalRenderAnchor,
            "The installation again owns its original single real rendering anchor");
        set(state, "debugIgnoreEnergy", true); set(state, "state", 3); set(state, "cursor", 0); set(state, "scanCursor", 0);
        // updateEntity resets these shared per-tick budgets before invoking dismantle in production.
        // This focused private-method fixture must establish the same boundary on each simulated tick.
        for (int tick = 0; tick < 20 && state.getState() == 3; tick++) {
            set(state, "blocksChangedThisTick", 0);
            set(state, "lightsChangedThisTick", 0);
            set(state, "blockBudgetStarted", 0L);
            invoke(state, "dismantle", new Class<?>[0]);
        }
        check(state.getState() == 0 && world.isAirBlock(state.blockWorldX(cell), state.blockWorldY(cell.y), state.blockWorldZ(cell)),
            "Actual dismantle removes only recorded real structure and completes settlement: state=" + state.getState()
                + " cursor=" + state.getCursor() + " status=" + state.getStatus());
        long returnedShells = 0;
        for (int slot = 18; slot < 36; slot++) {
            ItemStack output = state.getStackInSlot(slot);
            if (output != null && output.getItem() == SHELL) returnedShells += output.stackSize;
        }
        check(returnedShells == 1, "Ten surviving installed units refund one shell; the destroyed original is never refunded twice");
        NBTTagCompound settled = save(state);
        for (String field : new String[] { "virtualConstructionVersion", "virtualInstalled", "virtualSupplyHull", "virtualSupplyTerrace",
            "virtualMigrationCursor", "constructionInstallationVersion", "constructionVirtualInstalled" })
            check(!settled.hasKey(field), "Physical save contains no withdrawn installation field: " + field);
        System.out.println("PASS CriticalPhysicalConstructionContract checks=" + checks);
    }
}
