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
        field(restored, "debugSpeed", 2);
        field(restored, "energy", UHV * 400);
        for (int i = 0; i < 400; i++) advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == 1_439_600, "Non-instant debug speed never accelerates curing");
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
        field(restored, "energy", UHV * 100);
        advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == 99, "Instant mode caps remaining curing at five seconds and advances one tick");
        for (int i = 0; i < 98; i++) advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == 1, "Repeated instant ticks count down rather than reset the cap");
        advance(restored, world);
        check(restored.getFoundationPhase() == 3 && restored.getEnergy() == 0, "Capped curing finishes naturally after one hundred powered ticks");
        NBTTagCompound shortCure = new NBTTagCompound();
        shortCure.setInteger("cells", 16); shortCure.setInteger("curing", 3);
        CriticalWorldData.get(world).foundation(-2, 7).read(shortCure);
        field(restored, "energy", UHV);
        advance(restored, world);
        check(restored.getFoundationCuringRemainingTicks() == 2, "Instant mode never lengthens a shorter remaining cure");
        saved = new NBTTagCompound();
        restored.writeToNBT(saved);
        CriticalMachineState legacy = state(world);
        saved.removeTag("foundationRequired");
        legacy.readFromNBT(saved);
        check(!legacy.isFoundationRequired(), "Old saved construction journals do not acquire a foundation requirement");
        modernHeating(world);
        System.out.println("PASS CriticalFoundationRuntimeContract checks=" + checks);
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
        field(state, "state", 1); field(state, "debugSpeed", 3);
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
            "Instant debug cannot bypass heat curing: exactly 50,000 L buys exactly one loaded tick");
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

    private static final class FundingState extends CriticalMachineState {
        long steam, debited;
        @Override public boolean fundCriticalInputs(net.minecraft.item.ItemStack[] items, net.minecraftforge.fluids.FluidStack[] fluids) {
            if (items.length != 0 || fluids.length != 1 || fluids[0] == null || fluids[0].amount != 50_000)
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
