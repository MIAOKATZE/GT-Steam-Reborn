package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;

/** Real State.tickFoundation with one shared real world ledger; no FML/game startup. */
public final class CriticalFoundationRuntimeContract {
    private static sun.misc.Unsafe unsafe;
    private static Method tick;
    private static int checks;
    private static final long UHV = gregtech.api.enums.GTValues.V[9];

    private static void check(boolean value, String text) {
        if (!value) throw new AssertionError(text);
        checks++;
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = CriticalMachineState.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static CriticalMachineState state(ClockWorld world) throws Exception {
        CriticalMachineState state = new CriticalMachineState();
        state.worldObj = world;
        state.setLoomController(true);
        field(state, "foundationRequired", true);
        field(state, "state", 1);
        field(state, "debugRequirements", 3);
        field(state, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(-2, 7)));
        return state;
    }

    private static void advance(CriticalMachineState state, ClockWorld world) throws Exception {
        world.time++;
        tick.invoke(state);
    }

    public static void main(String[] args) throws Exception {
        CriticalFoundationContract.source(CriticalMachineState.class, args);
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        unsafe = (sun.misc.Unsafe) access.get(null);
        tick = CriticalMachineState.class.getDeclaredMethod("tickFoundation");
        tick.setAccessible(true);
        ClockWorld world = (ClockWorld) unsafe.allocateInstance(ClockWorld.class);
        Field storage = World.class.getDeclaredField("perWorldStorage");
        storage.setAccessible(true);
        storage.set(world, new MapStorage(null));
        chunkPayments(world, args);
        debugStages(world);
        CriticalMachineState state = state(world);
        field(state, "energy", UHV * 50);
        for (int i = 0; i < 50; i++) advance(state, world);
        check(state.getFoundationCellProgressTicks() == 50 && state.getEnergy() == 0, "UHV baseline pays exactly fifty ticks");
        advance(state, world);
        check(state.getFoundationCellProgressTicks() == 50 && state.isFoundationCellFunded(), "Power loss preserves prepaid cell progress");
        NBTTagCompound saved = new NBTTagCompound();
        state.writeToNBT(saved);
        CriticalMachineState restored = state(world);
        restored.readFromNBT(saved);
        field(restored, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(-2, 7)));
        check(restored.isFoundationRequired() && restored.isFoundationCellFunded(), "Controller reload preserves foundation request and shared debit");
        field(restored, "energy", UHV * 350);
        for (int i = 0; i < 350; i++) advance(restored, world);
        check(restored.getFoundationCompletedCells() == 1 && restored.getEnergy() == 0, "Reload resumes remaining work without recharging fifty ticks");
        check(state.getFoundationCompletedCells() == 1, "Second loom sees first loom's completed cell immediately");
        field(restored, "debugSpeed", 3);
        for (int i = 1; i < 16; i++) {
            field(restored, "energy", UHV * 400);
            advance(restored, world);
        }
        check(restored.getFoundationPhase() == 2 && restored.getConstructionDurationTicks() == 1_440_000,
            "Sixteen cells enter fixed-duration curing phase=" + restored.getFoundationPhase()
                + " duration=" + restored.getConstructionDurationTicks() + " cells=" + restored.getFoundationCompletedCells());
        field(restored, "debugSpeed", 0);
        field(restored, "energy", UHV * 400);
        for (int i = 0; i < 400; i++) advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == 1_439_600, "Normal speed preserves original static curing duration");
        check(restored.getEnergy() == 0, "Curing spends exactly UHV one ampere per tick");
        Field cadence = CriticalMachineState.class.getDeclaredField("foundationCuringCheckTicks");
        cadence.setAccessible(true);
        check(cadence.getInt(restored) == 0, "The fourth hundred powered tick triggers and resets singularity inspection");
        int remaining = restored.getFoundationCuringRemainingTicks();
        advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == remaining && cadence.getInt(restored) == 0, "Unpowered curing neither advances nor triggers inspection");
        field(restored, "energy", UHV);
        advance(restored, world);
        field(state, "energy", UHV);
        tick.invoke(state);
        check(state.getEnergy() == UHV && state.getFoundationCuringRemainingTicks() == remaining - 1, "A second loom cannot double-cure a chunk or spend power in the same tick");
        field(restored, "debugSpeed", 3);
        advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == remaining - 1, "Unpowered instant mode does not shorten curing");
        int fastRemaining = restored.getFoundationCuringRemainingTicks();
        field(restored, "energy", UHV * fastRemaining);
        for (int i = 0; i < 29; i++) advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() > 0, "Fast cure stays visible for twenty-nine loaded ticks");
        advance(restored, world);
        check(restored.getFoundationPhase() == 3 && restored.getEnergy() == 0,
            "Fast cure completes within thirty effective ticks and pays the exact remaining work");
        NBTTagCompound shortCure = new NBTTagCompound();
        shortCure.setInteger("cells", 16); shortCure.setInteger("curing", 3);
        CriticalWorldData.get(world).foundation(-2, 7).read(shortCure);
        field(restored, "energy", UHV * 3);
        advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == 0 && restored.getEnergy() == 0,
            "Fast mode never lengthens a shorter remaining cure or overcharges its energy");
        saved = new NBTTagCompound();
        restored.writeToNBT(saved);
        CriticalMachineState legacy = state(world);
        saved.removeTag("foundationRequired");
        legacy.readFromNBT(saved);
        check(!legacy.isFoundationRequired(), "Old saved construction journals do not acquire a foundation requirement");
        modernHeating(world);
        debugHeatingPayments(world);
        System.out.println("PASS CriticalFoundationRuntimeContract checks=" + checks);
    }

    private static PourState pourState(ClockWorld world, int chunkX) throws Exception {
        PourState state = new PourState(); state.worldObj = world; state.setLoomController(true);
        field(state, "foundationRequired", true); field(state, "foundationModern", true); field(state, "state", 1);
        field(state, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(chunkX, 7)));
        return state;
    }

    private static void chunkPayments(ClockWorld world, String[] args) throws Exception {
        CriticalFoundationContract.source(CriticalFoundationMaterials.class, args);
        Field loader = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
        loader.setAccessible(true); loader.set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        gregtech.api.GregTechAPI.sPreloadStarted = true;
        net.minecraft.item.Item part = new net.minecraft.item.Item();
        Class.forName("net.minecraft.init.Items");
        Field feather = net.minecraft.init.Items.class.getDeclaredField("feather");
        unsafe.putObject(unsafe.staticFieldBase(feather), unsafe.staticFieldOffset(feather), part);
        gregtech.api.enums.ItemList.Hull_MAX.set(new net.minecraft.item.ItemStack(part, 1, 1));
        gregtech.api.util.GTOreDictUnificator.getName2StackMap().put(
            gregtech.api.enums.OrePrefixes.stickLong.get(gregtech.api.enums.Materials.Neutronium).toString(),
            new net.minecraft.item.ItemStack(part, 1, 2));
        gregtech.api.util.GTOreDictUnificator.getName2StackMap().put(
            gregtech.api.enums.OrePrefixes.wireFine.get(gregtech.api.enums.Materials.TungstenSteel).toString(),
            new net.minecraft.item.ItemStack(part, 1, 3));
        Method registerBlock = net.minecraft.block.Block.blockRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        registerBlock.setAccessible(true);
        registerBlock.invoke(net.minecraft.block.Block.blockRegistry, 9, "minecraft:water",
            new net.minecraft.block.Block(net.minecraft.block.material.Material.water) {});
        registerBlock.invoke(net.minecraft.block.Block.blockRegistry, 11, "minecraft:lava",
            new net.minecraft.block.Block(net.minecraft.block.material.Material.lava) {});
        net.minecraftforge.fluids.Fluid concrete = new net.minecraftforge.fluids.Fluid("foundation_contract_concrete");
        net.minecraftforge.fluids.FluidRegistry.registerFluid(concrete);
        Field concreteField = CriticalFoundationMaterials.class.getDeclaredField("concrete");
        concreteField.setAccessible(true); concreteField.set(null, concrete);
        check(CriticalFoundationMaterials.items(true)[2].stackSize == 6400
            && CriticalFoundationMaterials.items(5)[2].stackSize == 8000,
            "New NEI sample uses 6400 wires while historical cell schedules retain 8000");
        PourState state = pourState(world, -10); field(state, "energy", UHV * 400);
        for (int i = 0; i < 50; i++) advance(state, world);
        check(state.payments == 1 && state.rods == 512 && state.hulls == 32 && state.wires == 6400
            && state.concrete == 1_000_000 && state.getFoundationCompletedCells() == 2,
            "A complete new chunk is funded once with 1ML concrete, 512 rods, 32 MAX hulls and 6400 fine wires");
        NBTTagCompound stateNbt = new NBTTagCompound(), worldNbt = new NBTTagCompound();
        state.writeToNBT(stateNbt); CriticalWorldData.get(world).writeToNBT(worldNbt);
        CriticalWorldData.get(world).readFromNBT(worldNbt);
        PourState restored = pourState(world, -10); restored.readFromNBT(stateNbt);
        field(restored, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(-10, 7)));
        for (int i = 0; i < 350; i++) advance(restored, world);
        check(restored.payments == 0 && restored.getEnergy() == 0 && restored.getFoundationCompletedCells() == 16
            && restored.getFoundationPhase() == 4,
            "Paid chunk plus controller/world NBT completes at 400 work without duplicate cost or changed heating");
        PourState sharing = pourState(world, -10);
        check(sharing.getFoundationCompletedCells() == 16, "Other machines reuse the same completed poured chunk");
        stateNbt.removeTag("foundationScheduleVersion");
        PourState oldJob = pourState(world, -11); oldJob.readFromNBT(stateNbt);
        field(oldJob, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(-11, 7)));
        field(oldJob, "energy", UHV); advance(oldJob, world);
        check(oldJob.wires == 6400 && CriticalWorldData.get(world).foundation(-11, 7).scheduleVersion() == 6,
            "Old modern work orders reaching an untouched chunk use the corrected whole-chunk bill");
        NBTTagCompound oldCell = new NBTTagCompound(); oldCell.setInteger("foundationVersion", 5);
        oldCell.setInteger("cells", 3); oldCell.setInteger("progress", 127); oldCell.setBoolean("funded", true);
        oldCell.setInteger("heating", 36_000); oldCell.setInteger("curing", 1_728_000);
        CriticalWorldData.get(world).foundation(-12, 7).read(oldCell);
        PourState oldShared = pourState(world, -12);
        check(oldShared.payments == 0 && oldShared.getFoundationCompletedCells() == 16,
            "Previously finished paid cells satisfy the corrected whole-chunk bill without further construction debits");
        oldCell.setInteger("cells", 0); CriticalWorldData.get(world).foundation(-13, 7).read(oldCell);
        oldShared = pourState(world, -13); field(oldShared, "energy", UHV * 273);
        for (int i = 0; i < 273; i++) advance(oldShared, world);
        check(oldShared.payments == 0 && oldShared.getFoundationCompletedCells() == 16 && oldShared.getEnergy() == 0,
            "The old first-cell prepayment covers all remaining whole-chunk work without rebilling");
    }

    private static final class PourState extends CriticalMachineState {
        int payments; long rods, hulls, wires, concrete;
        @Override public boolean fundCriticalInputs(net.minecraft.item.ItemStack[] items, net.minecraftforge.fluids.FluidStack[] fluids) {
            if (items.length != 3 || fluids.length != 1) throw new AssertionError("Unexpected pouring bill");
            payments++; rods += items[0].stackSize; hulls += items[1].stackSize; wires += items[2].stackSize;
            concrete += fluids[0].amount; return true;
        }
    }

    private static void debugStages(ClockWorld world) throws Exception {
        CriticalMachineState state = state(world);
        field(state, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(-4, 7)));
        field(state, "debugSpeed", 3);
        field(state, "debugIgnoreEnergy", true);
        field(state, "foundationModern", true);
        CriticalFoundationChunk chunk = CriticalWorldData.get(world).foundation(-4, 7);
        NBTTagCompound poured = new NBTTagCompound();
        poured.setInteger("foundationVersion", 5); poured.setInteger("cells", 16);
        poured.setInteger("heating", CriticalFoundationChunk.HEATING_DURATION);
        poured.setInteger("curing", CriticalFoundationChunk.CURING_DURATION);
        chunk.read(poured);
        for (int i = 0; i < 29; i++) advance(state, world);
        check(chunk.heatingRemaining() > 0, "Fast heating stays visible through 29 effective ticks");
        advance(state, world);
        check(chunk.heatingRemaining() == 0 && chunk.curingRemaining() == CriticalFoundationChunk.CURING_DURATION,
            "Fast heating finishes in thirty effective ticks without entering static cure in the same tick");
        for (int i = 0; i < 29; i++) advance(state, world);
        check(chunk.curingRemaining() > 0, "Fast static cure stays visible through 29 effective ticks");
        advance(state, world);
        check(chunk.complete(), "Fast static cure finishes in thirty effective ticks");
    }

    private static void modernHeating(ClockWorld world) throws Exception {
        Field loader = cpw.mods.fml.common.Loader.class.getDeclaredField("instance"); loader.setAccessible(true);
        loader.set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        java.lang.reflect.Method blocks = net.minecraft.block.Block.blockRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        blocks.setAccessible(true);
        blocks.invoke(net.minecraft.block.Block.blockRegistry, 9, "minecraft:water",
            new net.minecraft.block.Block(net.minecraft.block.material.Material.water) {});
        blocks.invoke(net.minecraft.block.Block.blockRegistry, 11, "minecraft:lava",
            new net.minecraft.block.Block(net.minecraft.block.material.Material.lava) {});
        net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("ic2superheatedsteam"));
        FundingState state = new FundingState(); state.worldObj = world; state.setLoomController(true);
        field(state, "foundationRequired", true); field(state, "foundationModern", true);
        field(state, "state", 1); field(state, "debugSpeed", 0);
        field(state, "loomChunksCache", Collections.singletonList(new ChunkCoordIntPair(-3, 7)));
        CriticalFoundationChunk chunk = CriticalWorldData.get(world).foundation(-3, 7);
        NBTTagCompound poured = new NBTTagCompound();
        poured.setInteger("foundationVersion", 5); poured.setInteger("cells", 16);
        poured.setInteger("curing", CriticalFoundationChunk.CURING_DURATION);
        poured.setInteger("heating", CriticalFoundationChunk.HEATING_DURATION);
        chunk.read(poured);
        field(state, "energy", UHV * 3); state.steam = 49_999;
        advance(state, world);
        check(state.getFoundationPhase() == 4 && chunk.heatingRemaining() == 36_000 && state.getEnergy() == UHV * 3,
            "Steam shortage neither advances heat curing nor spends EU");
        state.steam = 100_000;
        advance(state, world);
        check(chunk.heatingRemaining() == 35_999 && state.steam == 50_000 && state.debited == 50_000,
            "Normal heat curing: exactly 50,000 L buys exactly one loaded tick");
        tick.invoke(state);
        check(chunk.heatingRemaining() == 35_999 && state.debited == 50_000, "Shared world tick cannot double-debit heat steam");
        chunk.accelerate(Long.MAX_VALUE);
        check(chunk.curingRemaining() == 1_728_000 && chunk.heatingRemaining() == 35_999,
            "Singularities cannot accelerate or skip the heat phase");
        advance(state, world);
        check(chunk.heatingRemaining() == 35_998 && state.debited == 100_000, "Heat supply is one million L per twenty powered ticks");
        advance(state, world);
        check(chunk.heatingRemaining() == 35_998 && state.getEnergy() == UHV, "Interrupted supply preserves both exact clocks");
        chunk.fundHeating(); // Successful drain persisted before an energy/placement retry.
        NBTTagCompound paid = new NBTTagCompound(); chunk.write(paid);
        chunk.read(paid);
        check(state.getConstructionNextFluidCostsForGui().length == 0,
            "A persisted prepaid heat tick honestly shows no second steam payment due");
        advance(state, world);
        check(chunk.heatingRemaining() == 35_997 && state.debited == 100_000 && !chunk.heatingFunded(),
            "Reloaded paid heat tick resumes once without requesting steam again");
        paid.setInteger("heating", 1); paid.setBoolean("heatingFunded", false); chunk.read(paid);
        state.steam = 50_000; field(state, "energy", UHV * 2); field(state, "debugSpeed", 0);
        advance(state, world);
        check(state.getFoundationPhase() == 2 && chunk.heatingRemaining() == 0 && chunk.curingRemaining() == 1_728_000,
            "The final heated tick transitions to a full 24-hour static cure without double-progress");
        advance(state, world);
        check(chunk.curingRemaining() == 1_727_999 && state.debited == 150_000,
            "Static curing starts next tick and consumes no more heat steam");
        check((long) CriticalFoundationChunk.HEATING_DURATION * CriticalFoundationChunk.HEATING_STEAM_PER_TICK == 1_800_000_000L,
            "Thirty-minute heat total is exactly 1.8 billion L");
    }

    private static void debugHeatingPayments(ClockWorld world) throws Exception {
        FundingState state = new FundingState(); state.worldObj = world; state.setLoomController(true);
        field(state, "foundationRequired", true); field(state, "foundationModern", true);
        field(state, "state", 1); field(state, "debugSpeed", 3);
        field(state, "loomChunksCache", java.util.Arrays.asList(new ChunkCoordIntPair(-6, 7), new ChunkCoordIntPair(-7, 7)));
        CriticalFoundationChunk first = CriticalWorldData.get(world).foundation(-6, 7);
        CriticalFoundationChunk second = CriticalWorldData.get(world).foundation(-7, 7);
        NBTTagCompound poured = new NBTTagCompound();
        poured.setInteger("foundationVersion", 5); poured.setInteger("cells", 16);
        poured.setInteger("heating", CriticalFoundationChunk.HEATING_DURATION);
        poured.setInteger("curing", CriticalFoundationChunk.CURING_DURATION);
        first.read(poured); second.read(poured);
        long totalEU = UHV * CriticalFoundationChunk.HEATING_DURATION;
        field(state, "energy", totalEU); state.steam = 59_999_999;
        advance(state, world);
        check(first.heatingRemaining() == 36_000 && second.heatingRemaining() == 36_000
            && state.debited == 0 && state.getEnergy() == totalEU,
            "Two-chunk accelerated heating waits for its whole sixty-million-litre payment");
        state.steam = 1_800_000_000L;
        field(state, "energy", 0L);
        advance(state, world);
        check(first.heatingRemaining() == 36_000 && state.debited == 0,
            "Insufficient energy cannot prepay or advance accelerated heating");
        field(state, "energy", totalEU);
        advance(state, world);
        check(first.heatingRemaining() == 34_800 && second.heatingRemaining() == 34_800
            && state.debited == 60_000_000 && state.getEnergy() == totalEU - UHV * 1200,
            "One sixty-million-litre debit and one energy charge advance both chunks by 1200 work");
        tick.invoke(state);
        check(state.debited == 60_000_000, "Same tick cannot double-charge a multi-chunk phase");
        NBTTagCompound saved = new NBTTagCompound(); first.write(saved); first.read(saved);
        second.write(saved); second.read(saved);
        for (int i = 1; i < 30; i++) advance(state, world);
        check(first.heatingRemaining() == 0 && second.heatingRemaining() == 0
            && state.debited == 1_800_000_000L && state.steam == 0 && state.getEnergy() == 0,
            "Thirty accelerated ticks conserve 1.8 billion litres and baseline EU for two chunks across reload");
        // Old boolean receipts mean exactly one already-paid work unit, even with fastest speed selected.
        poured.setBoolean("heatingFunded", true); poured.removeTag("heatingPaidWork");
        first.read(poured); second.read(poured); state.debited = 0; state.steam = 0;
        field(state, "energy", UHV * 2); advance(state, world);
        check(first.heatingRemaining() == 35_999 && second.heatingRemaining() == 35_999 && state.debited == 0,
            "Old boolean prepaid heat survives reload as one shared paid work without another steam debit");
        first.fundHeating(1200); second.fundHeating(1200);
        first.write(saved); first.read(saved); second.write(saved); second.read(saved);
        field(state, "debugSpeed", 0); field(state, "energy", UHV); advance(state, world);
        check(first.heatingPaidWork() == 1199 && second.heatingPaidWork() == 1199 && state.debited == 0,
            "Changing speed after reload spends only one prepaid work and preserves its remaining 1199 units");
        for (int speed : new int[] {1, 2}) {
            first.read(poured); second.read(poured); first.heat(1, ++world.time); second.heat(1, world.time);
            field(state, "debugRequirements", 3); field(state, "debugSpeed", speed);
            field(state, "debugIgnoreEnergy", true); int before = first.heatingRemaining();
            advance(state, world);
            check(before - first.heatingRemaining() == (speed == 1 ? 2 : 10),
                "Heat phase applies every intermediate debug multiplier " + speed);
            poured.setInteger("heating", 0); first.read(poured); second.read(poured);
            before = first.curingRemaining(); advance(state, world);
            check(before - first.curingRemaining() == (speed == 1 ? 2 : 10),
                "Static phase applies every intermediate debug multiplier " + speed);
            poured.setInteger("heating", CriticalFoundationChunk.HEATING_DURATION);
        }
        poured.setInteger("heating", 0); first.read(poured); second.read(poured);
        field(state, "debugSpeed", 3); field(state, "debugIgnoreEnergy", false);
        field(state, "energy", UHV * 5); advance(state, world);
        check(first.curingRemaining() == CriticalFoundationChunk.CURING_DURATION - 5 && state.getEnergy() == 0,
            "Fast static curing uses the available paid work instead of deadlocking on an unattainable full debug budget");
    }

    private static final class FundingState extends CriticalMachineState {
        long steam, debited;
        @Override public boolean fundCriticalInputs(net.minecraft.item.ItemStack[] items, net.minecraftforge.fluids.FluidStack[] fluids) {
            if (items.length == 0 && fluids.length == 0) return true;
            if (items.length != 0 || fluids.length != 1 || fluids[0] == null || fluids[0].amount <= 0 || fluids[0].amount % 50_000 != 0)
                throw new AssertionError("Unexpected heat-phase payment request");
            if (steam < fluids[0].amount) return false;
            steam -= fluids[0].amount; debited += fluids[0].amount; return true;
        }
    }

    private static final class ClockWorld extends World {
        private long time;
        private ClockWorld() { super(null, "unused", (WorldProvider) null, null, null); }
        @Override public long getTotalWorldTime() { return time; }
        @Override public void markBlockForUpdate(int x, int y, int z) {}
        @Override protected IChunkProvider createChunkProvider() { throw new UnsupportedOperationException(); }
        @Override protected int func_152379_p() { throw new UnsupportedOperationException(); }
        @Override public Entity getEntityByID(int id) { throw new UnsupportedOperationException(); }
    }
}
