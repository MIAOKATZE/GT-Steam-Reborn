package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.*;
import java.util.*;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.*;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.*;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Candidate-bound real placement, hardness, dismantling and shared-ledger finalization. */
public final class CriticalConstructionOwnershipContract {
    private static Object invoke(CriticalMachineState state, String name, Class<?>[] types, Object... values) throws Exception {
        Method method = CriticalMachineState.class.getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(state, values);
    }
    private static Field member(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void dismantle(CriticalMachineState state) throws Exception {
        set(state, "blocksChangedThisTick", 0); set(state, "lightsChangedThisTick", 0); set(state, "blockBudgetStarted", 0L);
        invoke(state, "dismantle", new Class<?>[0]);
    }
    @SuppressWarnings("unchecked")
    private static void coverage(SparseWorld world, CriticalMachineState state) throws Exception {
        member(World.class, "provider").set(world, unsafe.allocateInstance(WorldProviderSurface.class));
        Host host = (Host) unsafe.allocateInstance(Host.class);
        host.setWorldObj(world); host.xCoord = state.xCoord; host.yCoord = state.yCoord; host.zCoord = state.zCoord;
        world.tiles.put(key(host.xCoord, host.yCoord, host.zCoord), host); state.bindHost(host);
        gregtech.api.enums.GTValues.enableChunkloaders = true;
        member(CriticalChunkLoading.class, "registered").setBoolean(null, true);
        member(CriticalChunkTicketManager.class, "mod").set(null, new Object());
        member(ForgeChunkManager.class, "overridesEnabled").setBoolean(null, false);
        member(ForgeChunkManager.class, "defaultMaxChunks").setInt(null, 128);
        Map<World, Multimap<String, Ticket>> tickets = (Map<World, Multimap<String, Ticket>>) member(ForgeChunkManager.class, "tickets").get(null);
        tickets.put(world, ArrayListMultimap.create());
        Map<World, ImmutableSetMultimap<ChunkCoordIntPair, Ticket>> forced = (Map<World, ImmutableSetMultimap<ChunkCoordIntPair, Ticket>>) member(ForgeChunkManager.class, "forcedChunks").get(null);
        forced.put(world, ImmutableSetMultimap.of());
        Constructor<Ticket> constructor = Ticket.class.getDeclaredConstructor(String.class, ForgeChunkManager.Type.class, World.class);
        constructor.setAccessible(true); Ticket ticket = constructor.newInstance("gtsr", ForgeChunkManager.Type.NORMAL, world);
        ticket.getModData().setInteger("OwnerX", host.xCoord); ticket.getModData().setInteger("OwnerY", host.yCoord); ticket.getModData().setInteger("OwnerZ", host.zCoord);
        tickets.get(world).put("gtsr", ticket);
        ((Map<net.minecraft.tileentity.TileEntity, Ticket>) member(CriticalChunkTicketManager.class, "REGISTERED").get(null)).put(host, ticket);
        for (ChunkCoordIntPair chunk : state.getFoundationChunks()) ForgeChunkManager.forceChunk(ticket, chunk);
        check(state.getChunkLoading().ensureAllStructureChunks(world, host, state.getFoundationChunks()), "Real Forge ticket covers the bounded fixture");
    }
    private static CriticalFoundationChunk foundation(CriticalWorldData data, int x, int remaining) {
        CriticalFoundationChunk chunk = data.foundation(x, 7);
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("cells", 16); tag.setInteger("curing", remaining); chunk.read(tag);
        return chunk;
    }
    public static void main(String[] args) throws Exception {
        CriticalFoundationContract.source(CriticalMaterials.class, args);
        CriticalFoundationContract.source(CriticalBlockProtection.class, args);
        unsafe = (sun.misc.Unsafe) member(sun.misc.Unsafe.class, "theUnsafe").get(null);
        materials(); SparseWorld world = world(); CriticalMachineState state = state(world, 31, ForgeDirection.NORTH);
        set(state, "owner", ""); coverage(world, state);
        CriticalWorldData data = CriticalWorldData.get(world);
        check(data.acquire(state.getStructureId(), "", state.getOriginX(), state.getOriginY(), state.getOriginZ(), state.getRegionSize(), state.getRegionHeight(), state.xCoord, state.yCoord, state.zCoord), "Fixture region acquired");
        check(data.bindTask(state.getStructureId(), "", state.getJobId(), state.getOriginX(), state.getOriginY(), state.getOriginZ()), "Fixture task bound");
        List<CriticalGeometry.Voxel> geometry = geometry(state);
        List<CriticalGeometry.Voxel> pair = new ArrayList<>();
        for (int index = geometry.size() - 1; index >= geometry.size() - 128 && pair.size() < 2; index--) {
            CriticalGeometry.Voxel voxel = geometry.get(index);
            if (!CriticalLoomStructure.isLoomVoxel(voxel) && !CriticalStructures.isController(voxel)) pair.add(voxel);
        }
        check(pair.size() == 2, "Real last dismantle window contains two paid material cells");
        CriticalGeometry.Voxel manual = pair.get(0), automatic = pair.get(1);
        put(state, manual, manual.block);
        set(state, "state", 1); set(state, "constructionCostSnapshot", true); set(state, "constructionRoundFunded", true);
        set(state, "debugIgnoreEnergy", true); set(state, "constructionTickBudget", CriticalConstructionProgress.STAGE_DURATION);
        invoke(state, "advanceConstruction", new Class<?>[] { List.class, boolean.class }, pair, false);
        BitSet placed = (BitSet) field(state, "placed");
        check(!placed.get(geometry.indexOf(manual)), "Actual construction does not adopt a preplaced matching block");
        check(placed.get(geometry.indexOf(automatic)), "Actual successful machine placement enters the persistent ledger");
        check(manual.block.getBlockHardness(world, state.blockWorldX(manual), state.blockWorldY(manual.y), state.blockWorldZ(manual)) == 5F, "Preplaced matching material retains ordinary hardness inside the job");
        check(automatic.block.getBlockHardness(world, state.blockWorldX(automatic), state.blockWorldY(automatic.y), state.blockWorldZ(automatic)) == -1F, "Actual machine placement has hardness minus one");
        CriticalGeometry.Voxel loom = geometry.stream().filter(CriticalLoomStructure::isLoomVoxel).filter(v -> !CriticalStructures.isController(v)).findFirst().orElseThrow();
        put(state, loom, CriticalLoomStructure.shell(loom));
        check(CriticalLoomStructure.shell(loom).getBlockHardness(world, state.blockWorldX(loom), state.blockWorldY(loom.y), state.blockWorldZ(loom)) == 5F, "Manual loom shell retains ordinary hardness");
        set(state, "foundationRequired", true);
        List<ChunkCoordIntPair> involved = Arrays.asList(new ChunkCoordIntPair(-2, 7), new ChunkCoordIntPair(-1, 7), new ChunkCoordIntPair(0, 7));
        set(state, "loomChunksCache", involved);
        CriticalFoundationChunk complete = foundation(data, -2, 0), curing = foundation(data, -1, 50);
        CriticalFoundationChunk prepaid = data.foundation(0, 7); prepaid.setFunded(); prepaid.pour(20, 1);
        CriticalFoundationChunk unrelated = data.foundation(99, 7); unrelated.setFunded(); unrelated.pour(40, 1);
        check(!state.startDismantle(null) && data.findFoundation(-1, 7) == curing, "Rejected dismantle never clears foundation work");
        Player player = (Player) unsafe.allocateInstance(Player.class); player.capabilities = new PlayerCapabilities();
        player.posX = state.xCoord + .5; player.posY = state.yCoord + .5; player.posZ = state.zCoord + .5;
        check(state.startDismantle(player), "Owner starts real dismantle transition");
        check(data.findFoundation(-1, 7) == curing && data.findFoundation(0, 7) == prepaid, "Accepted start retains curing and prepaid work until final success");
        player.allowed = false;
        check(!state.applyConstructionDebug(player, 0, 3, true), "Existing debug permission remains enforced during dismantling");
        player.allowed = true;
        check(state.resetConstructionDebug(player), "Owner can disable debug while dismantling");
        dismantle(state);
        check(state.getCursor() == 0 && data.findFoundation(-1, 7) == curing, "Unpowered ordinary dismantling pauses without clearing foundations");
        long cost = state.getTier().constructionVoltage(); set(state, "energy", cost);
        dismantle(state);
        check(state.getCursor() > 0 && state.getEnergy() == 0, "Ordinary dismantling keeps its original per-tick construction-voltage debit: cursor=" + state.getCursor() + " EU=" + state.getEnergy() + " status=" + state.getStatus() + " loading=" + state.getChunkLoading().getStatus());
        set(state, "cursor", 0);
        put(state, automatic, automatic.block);
        check(state.applyConstructionDebug(player, 0, 3, true), "Owner can enable existing energy exemption during dismantling");
        set(state, "state", 2);
        check(!(Boolean) invoke(state, "consumeEnergy", new Class<?>[] { long.class }, 1L), "Energy exemption does not make the shared production debit free in running state");
        set(state, "energy", 1L);
        check((Boolean) invoke(state, "consumeEnergy", new Class<?>[] { long.class }, 1L) && state.getEnergy() == 0, "Running-state debit still consumes exact energy with debug exemption configured");
        set(state, "state", 3);
        dismantle(state);
        check(state.getCursor() > 0 && state.getCursor() <= 128 && state.getEnergy() == 0, "Energy exemption advances at the original bounded dismantle speed");
        check(world.isAirBlock(state.blockWorldX(automatic), state.blockWorldY(automatic.y), state.blockWorldZ(automatic)) && !world.isAirBlock(state.blockWorldX(manual), state.blockWorldY(manual.y), state.blockWorldZ(manual)), "Dismantle removes actual machine placement and retains preplaced material");
        put(state, automatic, automatic.block);
        check(automatic.block.getBlockHardness(world, state.blockWorldX(automatic), state.blockWorldY(automatic.y), state.blockWorldZ(automatic)) == 5F, "Passed dismantle cursor no longer protects a replacement at an old paid coordinate");
        invoke(state, "reconcileDismantle", new Class<?>[0]);
        check(state.getCursor() < 128 && automatic.block.getBlockHardness(world, state.blockWorldX(automatic), state.blockWorldY(automatic.y), state.blockWorldZ(automatic)) == -1F, "Explicit reconciliation replays retained historical placement without discarding recovery ledger");
        dismantle(state);
        set(state, "cursor", geometry.size()); set(state, "scanCursor", geometry.size() - 128);
        set(state, "refundPrepared", true);
        Item item = new Item(); ItemStack[] inventory = (ItemStack[]) field(state, "inventory");
        for (int slot = 18; slot < 36; slot++) inventory[slot] = new ItemStack(item, 64);
        set(state, "pendingItems", new ItemStack[] { new ItemStack(item) });
        dismantle(state);
        check(state.getState() == 3 && (Boolean) field(state, "removalVerified") && data.findFoundation(-1, 7) == curing, "Retained manual block does not fail verification; blocked refund still keeps unfinished foundations");
        inventory[18] = null;
        dismantle(state);
        check(state.getState() == 0, "Actual finalization completes after refund delivery");
        check(data.findFoundation(-2, 7) == complete && complete.complete(), "Completed shared foundation remains reusable");
        check(data.findFoundation(-1, 7) == null && data.findFoundation(0, 7) == null, "Successful teardown discards only involved unfinished curing and prepaid journals");
        check(data.findFoundation(99, 7) == unrelated && unrelated.funded() && unrelated.cellProgress() == 40, "Unrelated prepaid chunk is unchanged");
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved); CriticalWorldData reload = new CriticalWorldData(); reload.readFromNBT(saved);
        check(reload.findFoundation(-1, 7) == null && reload.findFoundation(0, 7) == null && reload.findFoundation(-2, 7).complete(), "Foundation cleanup persists through real world NBT");
        System.out.println("PASS CriticalConstructionOwnershipContract checks=" + checks);
    }
    private static final class Host extends BaseMetaTileEntity { @Override public void markDirty() {} }
    private static final class Player extends EntityPlayerMP {
        boolean allowed;
        private Player() { super(null, null, null, null); }
        @Override public boolean canCommandSenderUseCommand(int level, String name) { return allowed; }
    }
}
