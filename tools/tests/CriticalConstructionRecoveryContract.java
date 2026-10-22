package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/** Exercises public controller commands and the real server update gate, never private work methods. */
public final class CriticalConstructionRecoveryContract {
    private static Field member(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static int failures;
    private static void scenario(String name, Run run) throws Exception {
        try { run.run(); System.out.println("PASS scenario=" + name); }
        catch (AssertionError failure) { failures++; System.out.println("FAIL scenario=" + name + " " + failure.getMessage()); }
    }
    private interface Run { void run() throws Exception; }
    private static final class Fixture {
        final SparseWorld world;
        final CriticalMachineState state;
        final Player player;
        final MTECriticalMachine machine;
        Fixture() throws Exception { this(CriticalMachineKind.SUN); }
        Fixture(CriticalMachineKind kind) throws Exception {
            world = world(); state = state(world, 31, ForgeDirection.NORTH);
            if (kind != CriticalMachineKind.SUN) {
                set(state, "config", new CriticalConfiguration(CriticalTier.T3, kind, 0, 0, 0));
                set(state, "geometryCache", null); geometry(state); set(state, "jobFingerprint", field(state, "modelFingerprint"));
            }
            set(state, "owner", ""); CriticalConstructionOwnershipContract.coverage(world, state);
            BaseMetaTileEntity host = (BaseMetaTileEntity) state.getHost();
            machine = new MTECriticalMachine("recovery_fixture", null);
            member(MTECriticalMachine.class, "state").set(machine, state);
            member(MetaTileEntity.class, "mBaseMetaTileEntity").set(machine, host); host.setMetaTileEntity(machine);
            member(BaseMetaTileEntity.class, "mFacing").set(host, state.getControllerFacing(true));
            for (CriticalGeometry.Voxel voxel : geometry(state))
                if (CriticalLoomStructure.isLoomVoxel(voxel) && !CriticalStructures.isController(voxel))
                    put(state, voxel, CriticalLoomStructure.shell(voxel));
            member(MTECriticalMachine.class, "loomStructureValid").setBoolean(machine, true);
            member(MTECriticalMachine.class, "loomGeometryValid").setBoolean(machine, true);
            member(MTECriticalMachine.class, "loomValidationTick").setLong(machine, 0);
            CriticalWorldData data = CriticalWorldData.get(world);
            check(data.acquire(state.getStructureId(), "", state.getOriginX(), state.getOriginY(), state.getOriginZ(), state.getRegionSize(), state.getRegionHeight(), state.xCoord, state.yCoord, state.zCoord), "Acquire fixture region");
            check(data.bindTask(state.getStructureId(), "", state.getJobId(), state.getOriginX(), state.getOriginY(), state.getOriginZ()), "Bind fixture journal");
            player = (Player) unsafe.allocateInstance(Player.class); player.capabilities = new PlayerCapabilities();
            player.posX = state.xCoord + .5; player.posY = state.yCoord + .5; player.posZ = state.zCoord + .5;
            set(state, "state", 1); set(state, "foundationRequired", true); set(state, "debugIgnoreEnergy", true);
            set(state, "debugRequirements", 3);
        }
    }
    private static void ready(Fixture f) throws Exception {
        net.minecraft.tileentity.TileEntity host = f.state.getHost(); fill(f.state);
        f.world.tiles.put(key(host.xCoord, host.yCoord, host.zCoord), host);
        for (net.minecraft.tileentity.TileEntity tile : f.world.tiles.values())
            if (tile != host && tile instanceof BaseMetaTileEntity gt && gt.getMetaTileEntity() instanceof MTECriticalMachine main) {
                CriticalMachineState local = new CriticalMachineState(); local.setLoomOwnedMain(f.state);
                member(MTECriticalMachine.class, "state").set(main, local);
            }
        set(f.state, "state", 2); set(f.state, "structureValid", true);
        monitor(f.state).primeConstruction(geometry(f.state).size() + CriticalGeometry.optionalWorkPorts(f.state).size());
    }
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Server thread");
        CriticalFoundationContract.source(CriticalMachineState.class, args);
        unsafe = (sun.misc.Unsafe) member(sun.misc.Unsafe.class, "theUnsafe").get(null);
        member(cpw.mods.fml.common.Loader.class, "instance").set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        member(cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side").set(null, cpw.mods.fml.relauncher.Side.SERVER);
        materials();
        scenario("force-stop-teardown", () -> {
            Fixture f = new Fixture();
            check(f.state.setEnabled(f.player, false), "Public force stop accepted during foundation work");
            check(f.state.startDismantle(f.player), "Public teardown accepted after force stop");
            check(f.state.isEnabled(), "Accepted dismantle must restore the actual GT host power switch");
            f.state.updateEntity();
            check(f.state.getState() == 0, "Foundation-only dismantle completes without scanning never-placed cells: " + f.state.getStatus());
        });
        scenario("zero-placement-teardown", () -> {
            Fixture f = new Fixture(); f.state.setEnabled(f.player, true);
            check(f.state.startDismantle(f.player), "Accept teardown"); f.state.updateEntity();
            check(f.state.getState() == 0, "No paid placements require no physical scan; cursor=" + f.state.getCursor());
        });
        scenario("repair-resumes-curing", () -> {
            Fixture f = new Fixture();
            for (net.minecraft.world.ChunkCoordIntPair p : f.state.getFoundationChunks()) {
                NBTTagCompound cure = new NBTTagCompound(); cure.setInteger("cells", 16); cure.setInteger("curing", 37);
                CriticalWorldData.get(f.world).foundation(p.chunkXPos, p.chunkZPos).read(cure);
            }
            f.state.setEnabled(f.player, false);
            NBTTagCompound saved = new NBTTagCompound(); f.state.writeToNBT(saved); f.state.readFromNBT(saved);
            check(f.state.repairStructure(f.player), "Owner repairs the saved paused journal");
            check(f.state.isEnabled(), "Repair must enable the GT switch without replacing the journal");
            f.state.updateEntity();
            check(f.state.getFoundationCuringRemainingTicks() == 36, "Public repair resumes the same curing countdown");
        });
        scenario("repair-can-teardown", () -> {
            Fixture f = new Fixture(); set(f.state, "state", 4);
            check(f.state.startDismantle(f.player), "Paid repair journal must permit teardown");
        });
        scenario("running-batch-drains-once", () -> {
            Fixture f = new Fixture();
            net.minecraft.tileentity.TileEntity host = f.state.getHost(); ready(f);
            set(f.state, "batchId", "paid-existing-batch"); set(f.state, "cycleTicks", 2); set(f.state, "cycleDuration", 2);
            set(f.state, "batchGenerated", 127L);
            f.state.setEnabled(f.player, false);
            check(f.state.startDismantle(f.player), "Running paid batch accepts a deferred teardown request");
            check(f.state.getState() == 2 && f.state.getBatchId().equals("paid-existing-batch"), "Teardown does not drop paid batch");
            NBTTagCompound saved = new NBTTagCompound(); f.state.writeToNBT(saved);
            check(saved.getBoolean("dismantleRequested"), "Teardown intent persists independently of batch journal");
            f.state.readFromNBT(saved); geometry(f.state);
            monitor(f.state).primeConstruction(geometry(f.state).size() + CriticalGeometry.optionalWorkPorts(f.state).size());
            f.state.updateEntity();
            check(f.state.getState() == 3 && f.state.getBatchId().isEmpty(), "Saved request drains existing batch and transitions without opening another: " + f.state.getStatus());
            check((Long) field(f.state, "pendingGenerated") == 0 && ((BaseMetaTileEntity) host).getStoredEU() == 127,
                "Paid legacy generated EU enters the actual GT buffer before teardown; pending=" + field(f.state, "pendingGenerated") + " stored=" + ((BaseMetaTileEntity) host).getStoredEU());
            f.state.updateEntity();
            check(f.state.getState() == 0 && f.state.canRemove(), "No invisible generated-EU journal locks a completed teardown");
        });
        scenario("native-funded-batch-drains-once", () -> {
            Fixture f = new Fixture(CriticalMachineKind.TURBINE); ready(f);
            NBTTagCompound batch = f.state.energyRuntimeData(); batch.setBoolean("funded", true);
            batch.setInteger("progress", CriticalEnergyRuntime.CYCLE_TICKS - 1); batch.setString("eu", "0");
            f.state.setEnabled(f.player, false);
            check(f.state.startDismantle(f.player), "Native funded batch accepts deferred teardown");
            check(batch.getBoolean("funded") && batch.getInteger("progress") == CriticalEnergyRuntime.CYCLE_TICKS - 1,
                "Request retains native paid checkpoint unchanged");
            f.state.updateEntity();
            check(!batch.getBoolean("funded") && f.state.getState() == 3, "Real energy runtime commits existing native batch once before teardown");
            f.state.updateEntity();
            check(f.state.getState() == 0 && !f.state.energyRuntimeData().getBoolean("funded"), "No new native batch opens after the request");
        });
        scenario("sparse-placement-and-restart", () -> {
            Fixture f = new Fixture(); f.state.setEnabled(f.player, true);
            java.util.List<CriticalGeometry.Voxel> voxels = geometry(f.state);
            CriticalGeometry.Voxel automatic = voxels.stream().filter(v -> !CriticalLoomStructure.isLoomVoxel(v) && !CriticalStructures.isController(v)).findFirst().orElseThrow();
            put(f.state, automatic, automatic.block); ((BitSet) field(f.state, "placed")).set(voxels.indexOf(automatic));
            check(f.state.startDismantle(f.player), "Sparse paid job accepts teardown");
            f.state.updateEntity();
            check(f.state.getState() == 0 && f.world.isAirBlock(f.state.blockWorldX(automatic), f.state.blockWorldY(automatic.y), f.state.blockWorldZ(automatic)), "One actual paid cell removes and finalizes without sweeping unrelated model cells");
            CriticalConstructionOwnershipContract.coverage(f.world, f.state);
            boolean started = false;
            for (int attempt = 0; attempt < 2000 && !started; attempt++) started = f.state.startConstruction(f.player);
            check(started && f.state.getState() == 1, "Public construction accepts the next job after sparse teardown: " + f.state.getStatus());
            f.state.updateEntity();
            check(f.state.getFoundationCellProgressTicks() == 1, "Next job actually pours through updateEntity after successful restart");
        });
        scenario("old-completed-teardown-energy", () -> {
            Fixture f = new Fixture(); set(f.state, "state", 0); set(f.state, "structureId", ""); set(f.state, "jobId", "");
            set(f.state, "pendingGenerated", 71L); f.state.setEnabled(f.player, false);
            long full = f.state.energyCapacity();
            member(BaseMetaTileEntity.class, "mStoredEnergy").setLong(f.state.getHost(), full);
            f.state.updateEntity();
            check((Long) field(f.state, "pendingGenerated") == 71 && !f.state.canRemove(), "Full GT store keeps old generated EU intact and visibly blocked");
            member(BaseMetaTileEntity.class, "mStoredEnergy").setLong(f.state.getHost(), 0L);
            f.state.updateEntity();
            check((Long) field(f.state, "pendingGenerated") == 0 && ((BaseMetaTileEntity) f.state.getHost()).getStoredEU() == 71,
                "Old state-zero journal recovers its exact EU even with GT power disabled");
            check(f.state.canRemove(), "Old completed teardown becomes removable after actual delivery");
        });
        if (failures != 0) throw new AssertionError("Recovery scenarios failed=" + failures);
        System.out.println("PASS CriticalConstructionRecoveryContract checks=" + checks);
    }
    private static final class Player extends EntityPlayerMP {
        private Player() { super(null, null, null, null); }
        @Override public boolean canCommandSenderUseCommand(int level, String name) { return true; }
        @Override public java.util.UUID getUniqueID() { return new java.util.UUID(0, 1); }
    }
}
