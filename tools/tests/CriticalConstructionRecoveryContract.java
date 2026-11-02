package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
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
    private static boolean repairCatalogSeeded;
    private static java.util.Set<String> selectedScenarios = java.util.Collections.emptySet();
    private static void scenario(String name, Run run) throws Exception {
        if (!selectedScenarios.isEmpty() && !selectedScenarios.contains(name)) return;
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
        Fixture(CriticalMachineKind kind) throws Exception { this(kind, 31); }
        Fixture(CriticalMachineKind kind, int revision) throws Exception {
            world = world(); state = state(world, revision, ForgeDirection.NORTH);
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
            player = (Player) unsafe.allocateInstance(Player.class); player.capabilities = new PlayerCapabilities();
            player.posX = state.xCoord + .5; player.posY = state.yCoord + .5; player.posZ = state.zCoord + .5;
            player.worldObj = world; world.playerEntities.add(player);
            String owner = player.getUniqueID().toString(); set(state, "owner", owner);
            host.setOwnerUuid(player.getUniqueID());
            CriticalWorldData data = CriticalWorldData.get(world);
            check(data.acquire(state.getStructureId(), owner, state.getOriginX(), state.getOriginY(), state.getOriginZ(), state.getRegionSize(), state.getRegionHeight(), state.xCoord, state.yCoord, state.zCoord), "Acquire fixture region");
            check(data.bindTask(state.getStructureId(), owner, state.getJobId(), state.getOriginX(), state.getOriginY(), state.getOriginZ()), "Bind fixture journal");
            set(state, "state", 1); set(state, "foundationRequired", true); set(state, "debugIgnoreEnergy", true);
            set(state, "debugRequirements", 3);
        }
    }
    private static void ready(Fixture f) throws Exception {
        net.minecraft.tileentity.TileEntity host = f.state.getHost(); fill(f.state);
        f.world.tiles.put(key(host.xCoord, host.yCoord, host.zCoord), host);
        for (net.minecraft.tileentity.TileEntity tile : f.world.tiles.values())
            if (tile != host && tile instanceof BaseMetaTileEntity gt && gt.getMetaTileEntity() instanceof MTECriticalMachine) {
                MTECriticalMachine main = new MTECriticalMachine("recovery_fixture_main", f.state.getKind());
                member(MetaTileEntity.class, "mBaseMetaTileEntity").set(main, gt); gt.setMetaTileEntity(main);
                CriticalMachineState local = new CriticalMachineState(); local.setLoomOwnedMain(f.state);
                member(MTECriticalMachine.class, "state").set(main, local);
            }
        set(f.state, "state", 2); set(f.state, "structureValid", true);
        monitor(f.state).primeConstruction(geometry(f.state).size() + CriticalGeometry.optionalWorkPorts(f.state).size());
    }
    private static void repairCatalog() throws Exception {
        if (repairCatalogSeeded) return;
        net.minecraft.item.ItemStack turbine = com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalTurbine.get(1);
        java.lang.reflect.Method add = net.minecraft.item.Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        add.setAccessible(true);
        int id = 31010;
        for (String name : new String[] { "EQUIPMENT", "CONTROLLER", "SINGULARITY" })
            add.invoke(net.minecraft.item.Item.itemRegistry, id++, "gtsr:repair_" + name.toLowerCase(java.util.Locale.ROOT),
                member(CriticalConstructionEconomyContract.class, name).get(null));
        CriticalConstructionEconomyContract.seedSchedule(Collections.emptyMap());
        com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalTurbine.set(turbine);
        repairCatalogSeeded = true;
    }

    @SuppressWarnings("unchecked")
    private static CriticalConstructionEconomy frozenFixture(Fixture f, boolean installed) throws Exception {
        java.util.List<CriticalGeometry.Voxel> paid = new ArrayList<>(geometry(f.state));
        paid.removeIf(CriticalLoomStructure::isLoomVoxel);
        CriticalConstructionEconomy economy = CriticalConstructionEconomy.freeze(f.state.getConfiguration(), paid,
            CriticalLayoutDefinition.forState(f.state).cacheKey());
        CriticalConstructionLedger ledger = (CriticalConstructionLedger) field(f.state, "constructionLedger");
        ledger.freezeJob(economy);
        // This fixture begins with a genuinely paid/installed journal, rather than claiming manual lookalikes.
        if (installed) ledger.recordRoundPayment(economy.costs(paid, 0, paid.size()), new net.minecraftforge.fluids.FluidStack[0]);
        Map<CriticalGeometry.Voxel, Integer> indices = (Map<CriticalGeometry.Voxel, Integer>) field(f.state, "voxelIndices");
        BitSet placed = (BitSet) field(f.state, "placed");
        if (installed) for (CriticalGeometry.Voxel voxel : paid) {
            ledger.markPhysicalInstalled(voxel);
            placed.set(indices.get(voxel));
        }
        ledger.resetStage();
        set(f.state, "geometryCache", null); geometry(f.state);
        CriticalWorldData.get(f.world).updateProtection(f.state);
        return economy;
    }

    private static NBTTagCompound save(CriticalMachineState state) {
        NBTTagCompound saved = new NBTTagCompound(); state.writeToNBT(saved); return saved;
    }

    private static long balance(CriticalMachineState state, String column) {
        net.minecraft.nbt.NBTTagList rows = save(state).getTagList("constructionPhysicalBalances", 10);
        long total = 0;
        for (int i = 0; i < rows.tagCount(); i++) total += rows.getCompoundTagAt(i).getLong(column);
        return total;
    }

    private static void remove(Fixture f, CriticalGeometry.Voxel voxel) {
        int x = f.state.blockWorldX(voxel), y = f.state.blockWorldY(voxel.y), z = f.state.blockWorldZ(voxel);
        net.minecraft.block.Block block = f.world.getBlock(x, y, z); int metadata = f.world.getBlockMetadata(x, y, z);
        f.world.setBlockToAir(x, y, z);
        // SparseWorld replaces only world storage; execute the production post-replacement notification unchanged.
        CriticalBlockProtection.recordRemoval(f.world, x, y, z, block, metadata);
    }

    private static void update(Fixture f) { f.world.totalWorldTime++; f.state.updateEntity(); }
    public static void main(String[] args) throws Exception {
        if (args.length > 1) selectedScenarios = new java.util.HashSet<>(java.util.Arrays.asList(args[1].split(",")));
        Thread.currentThread().setName("Server thread");
        CriticalFoundationContract.source(CriticalMachineState.class, args);
        unsafe = (sun.misc.Unsafe) member(sun.misc.Unsafe.class, "theUnsafe").get(null);
        member(cpw.mods.fml.common.Loader.class, "instance").set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        member(cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side").set(null, cpw.mods.fml.relauncher.Side.SERVER);
        materials();
        CriticalRC2StateContract.seedCatalog();
        scenario("repair-scan-checkpoint", () -> {
            Fixture f = new Fixture(); ready(f);
            check(f.state.repairStructure(f.player), "Public repair starts a full missing-position scan");
            f.world.totalWorldTime++;
            f.state.updateEntity();
            NBTTagCompound checkpoint = new NBTTagCompound(); f.state.writeToNBT(checkpoint);
            int first = checkpoint.getInteger("repairScanCursor");
            check(f.state.getState() == 4 && checkpoint.getBoolean("repairScanPending") && first > 0 && first <= 1024,
                "One server tick performs one bounded repair scan window, not an entire structure; scanned=" + first);
            check(f.state.getRepairPhase() == 1 && f.state.getRepairScanProgress() == first
                && f.state.getRepairScanTotal() == geometry(f.state).size(), "Public scan sample reports the actual persisted work");
            String job = f.state.getJobId();
            f.state.readFromNBT(checkpoint);
            f.world.totalWorldTime++;
            f.state.updateEntity();
            NBTTagCompound resumed = new NBTTagCompound(); f.state.writeToNBT(resumed);
            int second = resumed.getInteger("repairScanCursor");
            check(second > first && second - first <= 1024 && f.state.getJobId().equals(job),
                "Reload continues the same bounded repair scan and job identity");
            f.world.loaded = false; f.world.totalWorldTime++;
            f.state.updateEntity();
            NBTTagCompound waiting = new NBTTagCompound(); f.state.writeToNBT(waiting);
            check(waiting.getInteger("repairScanCursor") == second && waiting.getBoolean("repairScanPending"),
                "Unloaded chunks pause the real scan without restarting or assuming missing positions");
            f.world.loaded = true;
        });
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
        scenario("stellar-refund-before-teardown", () -> {
            Fixture f = new Fixture(); set(f.state, "state", 2); set(f.state, "structureValid", true);
            NBTTagCompound thermal = f.state.getStellarRuntimeData();
            thermal.setInteger("schema", CriticalStellarRuntime.SCHEMA);
            thermal.setInteger("billingVersion", CriticalStellarPolicy.VERSION);
            thermal.setString("structure", f.state.getStructureId());
            thermal.setString("host", f.machine.criticalStellarPowerPort().identity());
            long capacity = f.state.energyCapacity(), paid = capacity + 12345L;
            thermal.setLong("ignitionPaid", paid);
            BaseMetaTileEntity host = (BaseMetaTileEntity) f.state.getHost();
            member(BaseMetaTileEntity.class, "mStoredEnergy").setLong(host, capacity);
            check(f.state.startDismantle(f.player), "Full-buffer ignition refund accepts a teardown reservation");
            check(f.state.getState() == 2 && CriticalStellarRuntime.hasAssets(thermal)
                && thermal.getLong("refund") == paid && thermal.getLong("ignitionPaid") == 0,
                "Paid ignition moves once into refund journal before any main structure is removed");
            // Synthetic receiver consumes the real GT buffer between commands. No output hatch is simulated.
            member(BaseMetaTileEntity.class, "mStoredEnergy").setLong(host, 0L);
            check(f.state.startDismantle(f.player), "Partially accepted refund keeps teardown reservation");
            check(f.state.getState() == 2 && thermal.getLong("refund") == 12345L
                && host.getStoredEU() == capacity, "Only actual local buffer acceptance reduces the refund");
            member(BaseMetaTileEntity.class, "mStoredEnergy").setLong(host, 0L);
            check(f.state.startDismantle(f.player), "Fully accepted local refund releases teardown");
            check(f.state.getState() == 3 && !CriticalStellarRuntime.hasAssets(thermal)
                && host.getStoredEU() == 12345L,
                "Common teardown gate reaches state three only after thermal assets are settled");
        });
        scenario("legacy-overcap-cancel-retains-real-input-refund", () -> {
            Fixture f = new Fixture(); ready(f);
            set(f.state, "batchId", "legacy-overcap");
            set(f.state, "batchEu", MTECriticalMachine.criticalEnergyTickCap() + 1);
            set(f.state, "cycleTicks", 17); set(f.state, "cycleDuration", 400);
            set(f.state, "escrow", new net.minecraft.item.ItemStack[0]);
            set(f.state, "fluidEscrow", new net.minecraftforge.fluids.FluidStack[] {
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 250) });
            set(f.state, "pendingItems", new net.minecraft.item.ItemStack[0]);
            set(f.state, "pendingFluids", new net.minecraftforge.fluids.FluidStack[] {
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.LAVA, 999) });
            check(f.state.cancelLegacyStellarBatch(f.player), "Owner deterministically cancels a never-delivered overcap batch");
            check(!f.state.cancelLegacyStellarBatch(f.player), "Already-converted refund cannot be cancelled a second time");
            check(f.state.startDismantle(f.player), "Cancelled old batch can reserve teardown without new ignition");
            for (int tick = 0; tick < 2; tick++) {
                f.state.updateEntity();
                net.minecraftforge.fluids.FluidStack[] pending = (net.minecraftforge.fluids.FluidStack[]) field(f.state, "pendingFluids");
                check(f.state.getState() == 2 && "legacy-overcap".equals(f.state.getBatchId())
                    && pending.length == 1 && pending[0].getFluid() == net.minecraftforge.fluids.FluidRegistry.WATER
                    && pending[0].amount == 250, "Blocked real output retains original raw input exactly, never old products");
                check((Long) field(f.state, "batchEu") == 0 && (Long) field(f.state, "batchGenerated") == 0
                    && !f.state.getStellarRuntimeData().getBoolean("hot"),
                    "Refund retry neither repays consumed EU nor starts a new stellar session");
            }
            NBTTagCompound saved = new NBTTagCompound(); f.state.writeToNBT(saved);
            check(saved.getCompoundTag("criticalBatchRecipe").getBoolean("cancelRefund")
                && saved.getString("batchId").equals("legacy-overcap"), "Raw-input refund identity survives world NBT");
        });
        scenario("repair-replays-frozen-stages", () -> {
            repairCatalog();
            Fixture f = new Fixture(CriticalMachineKind.TURBINE);
            CriticalWorldData data = CriticalWorldData.get(f.world); data.release(f.state.getStructureId());
            check(data.acquire(f.state.getStructureId(), f.player.getUniqueID().toString(), f.state.getOriginX(),
                f.state.getOriginY(), f.state.getOriginZ(), f.state.getRegionSize(), f.state.getRegionHeight(),
                f.state.xCoord, f.state.yCoord, f.state.zCoord), "Owner's real repair region acquired");
            check(data.bindTask(f.state.getStructureId(), f.player.getUniqueID().toString(), f.state.getJobId(),
                f.state.getOriginX(), f.state.getOriginY(), f.state.getOriginZ()), "Owner's real repair task bound");
            ready(f);
            CriticalConstructionEconomy economy = frozenFixture(f, true);
            java.util.List<CriticalGeometry.Voxel> voxels = geometry(f.state);
            CriticalGeometry.Voxel body = voxels.stream().filter(v -> economy.stage(v) == 5
                && !CriticalStructures.isController(v)).findFirst().orElseThrow();
            CriticalGeometry.Voxel main = voxels.stream().filter(v -> CriticalStructures.isController(v)
                && !CriticalLoomStructure.isLoomVoxel(v)).findFirst().orElseThrow();
            check(economy.stage(main) == 9, "The real frozen controller belongs to the last equipment installation row");
            f.world.setBlockToAir(f.state.blockWorldX(body), f.state.blockWorldY(body.y), f.state.blockWorldZ(body));
            f.world.setBlockToAir(f.state.blockWorldX(main), f.state.blockWorldY(main.y), f.state.blockWorldZ(main));
            check(f.state.repairStructure(f.player), "Missing main controller still accepts repair through its owning loom");
            String job = f.state.getJobId();
            for (int tick = 0; tick < 1000 && f.state.getRepairPhase() == 1; tick++) {
                f.world.totalWorldTime++; f.state.updateEntity();
            }
            check(f.state.getRepairPhase() == 2 && ((BitSet) field(f.state, "repairTargets")).cardinality() == 2,
                "Real scan schedules only the two absent paid positions");
            // The existing native-controller fixture only replaces World.setBlock's tile-creation boundary.
            BaseMetaTileEntity mainHost = (BaseMetaTileEntity) unsafe.allocateInstance(
                Class.forName(CriticalRC2StateContract.class.getName() + "$Host"));
            member(gregtech.api.metatileentity.BaseTileEntity.class, "mReturnedCoordinates")
                .set(mainHost, new net.minecraft.util.ChunkCoordinates());
            mainHost.setWorldObj(f.world); mainHost.xCoord = f.state.blockWorldX(main);
            mainHost.yCoord = f.state.blockWorldY(main.y); mainHost.zCoord = f.state.blockWorldZ(main);
            f.world.tiles.put(key(mainHost.xCoord, mainHost.yCoord, mainHost.zCoord), mainHost);
            set(f.state, "debugSpeed", 3);
            boolean bodyRestored = false, mainRestored = false, checkpointed = false;
            int previousStage = f.state.getStage();
            for (int tick = 0; tick < 500 && f.state.getState() == 4; tick++) {
                int workingStage = f.state.getStage();
                f.world.totalWorldTime++; f.state.updateEntity();
                check(f.state.getStage() >= previousStage && f.state.getJobId().equals(job),
                    "One repair never resets its original work order or task identity");
                previousStage = f.state.getStage();
                if (!bodyRestored && !f.world.isAirBlock(f.state.blockWorldX(body), f.state.blockWorldY(body.y), f.state.blockWorldZ(body))) {
                    check(workingStage == economy.stage(body) && f.world.isAirBlock(mainHost.xCoord, mainHost.yCoord, mainHost.zCoord),
                        "Body replacement executes its original stage before the controller installation");
                    bodyRestored = true;
                }
                if (!mainRestored && !f.world.isAirBlock(mainHost.xCoord, mainHost.yCoord, mainHost.zCoord)) {
                    check(bodyRestored && workingStage == 9 && mainHost.getMetaTileEntity() instanceof MTECriticalMachine restored
                        && restored.getLocalCriticalState().isLoomOwnedMainOf(f.state),
                        "Last equipment row creates a real GT controller linked back to the same loom journal");
                    mainRestored = true;
                }
                if (!checkpointed && f.state.getStage() == 5 && f.state.getConstructionElapsedTicks() > 0) {
                    NBTTagCompound saved = new NBTTagCompound(); f.state.writeToNBT(saved);
                    int elapsed = f.state.getConstructionElapsedTicks(); f.state.readFromNBT(saved);
                    check(f.state.getStage() == 5 && f.state.getConstructionElapsedTicks() == elapsed
                        && f.state.getJobId().equals(job), "Repair reload preserves its frozen row and paid work clock");
                    checkpointed = true;
                }
            }
            check(bodyRestored && mainRestored && checkpointed && f.state.getState() == 2
                && f.state.getRepairPhase() == 3 && !f.state.isGeometryValidated(),
                "Completed replacement enters a real final validation instead of trusting partial repair");
            for (int tick = 0; tick < 1000 && !f.state.isGeometryValidated(); tick++) {
                f.world.totalWorldTime++; f.state.updateEntity();
            }
            check(f.state.isGeometryValidated() && f.state.getRepairPhase() == 0 && !f.state.hasKnownStructureDamage(),
                "A complete clean validation leaves repair mode and restores the formed geometry");
        });
        scenario("repair-rejects-manual-lookalike", () -> {
            repairCatalog(); Fixture f = new Fixture(CriticalMachineKind.TURBINE, 33); ready(f);
            CriticalConstructionEconomy economy = frozenFixture(f, true);
            CriticalGeometry.Voxel body = geometry(f.state).stream().filter(v -> economy.stage(v) == 5
                && !CriticalStructures.isController(v)).findFirst().orElseThrow();
            check(f.state.requestDamageHighlight(f.player) && f.state.isGeometryValidated(),
                "Diagnostic request preserves the valid machine and production authorization");
            f.state.tickStructureMonitor();
            int diagnosticCursor = (Integer) field(monitor(f.state), "cursor");
            check(monitor(f.state).getLastChecks() <= 1024 && diagnosticCursor > 0
                && f.state.requestDamageHighlight(f.player) && (Integer) field(monitor(f.state), "cursor") == diagnosticCursor,
                "Repeated diagnostic requests retain the in-flight bounded scan cursor");
            long paid = balance(f.state, "paid"), destroyed = balance(f.state, "destroyed");
            int replacement = 0;
            for (net.minecraft.item.ItemStack item : economy.bill(body)) replacement += item.stackSize;
            f.world.setBlockToAir(f.state.blockWorldX(body), f.state.blockWorldY(body.y), f.state.blockWorldZ(body));
            long observedFingerprint = (Long) field(f.state, "jobFingerprint");
            check(!CriticalWorldData.get(f.world).isPlacementInvalidated(f.state.getStructureId(), observedFingerprint, body.positionKey()),
                "External direct-storage removal deliberately bypasses the native removal notification");
            for (int tick = 0; tick < CriticalStructureMonitor.ROUND_TICKS * 2 && !f.state.hasKnownStructureDamage(); tick++)
                f.state.tickStructureMonitor();
            put(f.state, body, body.block);
            check(!f.state.isGeometryValidated() && f.state.hasKnownStructureDamage(),
                "Observed hard removal remains invalid when an identical manual block is placed back");
            check(CriticalWorldData.get(f.world).isPlacementInvalidated(f.state.getStructureId(), observedFingerprint, body.positionKey()),
                "Real monitor evidence persists lost ownership even without any removal hook");
            check(balance(f.state, "paid") == paid && balance(f.state, "destroyed") == destroyed + replacement,
                "Lost original moves installed entitlement to destroyed exactly once without changing paid history");
            int reads = f.world.unloadedReads; f.world.loaded = false;
            CriticalMachineState.DamageHighlightSnapshot snapshot = f.state.getDamageHighlightSnapshot();
            f.world.loaded = true;
            check(snapshot.knownTotal == 1 && snapshot.positions.length == 3
                && snapshot.positions[0] == f.state.blockWorldX(body) && snapshot.positions[1] == f.state.blockWorldY(body.y)
                && snapshot.positions[2] == f.state.blockWorldZ(body) && reads == f.world.unloadedReads,
                "Cached highlight includes the occupied missing-identity cell without reading any world position");
            NBTTagCompound checkpoint = save(f.state), regions = new NBTTagCompound();
            CriticalWorldData.get(f.world).writeToNBT(regions); CriticalWorldData.get(f.world).readFromNBT(regions);
            f.state.readFromNBT(checkpoint); f.state.bindHost(f.state.getHost());
            check(f.state.getDamageHighlightSnapshot().knownTotal == 1 && balance(f.state, "destroyed") == destroyed + replacement,
                "Reload rebuilds persistent damage evidence without double-retiring the original entitlement");
            check(f.state.repairStructure(f.player), "Owner starts the real repair scan for the occupied invalidated cell");
            for (int tick = 0; tick < 1000 && !f.state.getStatus().contains("先清空"); tick++) update(f);
            check(f.state.getState() == 4 && f.state.getStatus().contains("先清空")
                && balance(f.state, "paid") == paid && !f.state.isConstructionRoundFunded(),
                "Identical manual occupation is rejected before installation payment and is never swallowed");
            remove(f, body);
            CriticalConstructionLedger ledger = (CriticalConstructionLedger) field(f.state, "constructionLedger");
            ledger.recordRoundPayment(economy.bill(body), new net.minecraftforge.fluids.FluidStack[0]);
            set(f.state, "debugRequirements", 2); set(f.state, "debugSpeed", 3);
            for (int tick = 0; tick < 1000 && f.state.getState() == 4; tick++) update(f);
            long fingerprint = (Long) field(f.state, "jobFingerprint");
            check(f.state.getState() == 2 && f.world.getBlock(f.state.blockWorldX(body), f.state.blockWorldY(body.y), f.state.blockWorldZ(body)) == body.block
                && !CriticalWorldData.get(f.world).isPlacementInvalidated(f.state.getStructureId(), fingerprint, body.positionKey()),
                "Cleared position is restored by actual owned placement before its lost-identity marker clears");
            check(balance(f.state, "paid") == paid + replacement && balance(f.state, "destroyed") == destroyed + replacement
                && balance(f.state, "escrow") == 0 && f.state.getDamageHighlightSnapshot().knownTotal == 0,
                "One replacement allocation installs once, retains the destroyed original, and clears only the repaired marker");
        });
        scenario("construction-rejects-never-placed-lookalike", () -> {
            repairCatalog(); Fixture f = new Fixture(CriticalMachineKind.TURBINE, 33); ready(f);
            CriticalConstructionEconomy economy = frozenFixture(f, false);
            java.util.List<CriticalGeometry.Voxel> voxels = geometry(f.state);
            CriticalGeometry.Voxel body = voxels.stream().filter(v -> economy.stage(v) == 9
                && !CriticalStructures.isController(v)).findFirst().orElseThrow();
            for (CriticalGeometry.Voxel voxel : voxels) if (!CriticalLoomStructure.isLoomVoxel(voxel))
                f.world.setBlockToAir(f.state.blockWorldX(voxel), f.state.blockWorldY(voxel.y), f.state.blockWorldZ(voxel));
            put(f.state, body, body.block);
            set(f.state, "state", 1); set(f.state, "foundationRequired", false); set(f.state, "stage", 9);
            set(f.state, "debugSpeed", 3);
            check(f.state.setEnabled(f.player, true), "Owner enables the real construction host switch");
            for (int tick = 0; tick < 10 && f.state.getReasonCode() != 6; tick++) update(f);
            check(f.state.getReasonCode() == 6 && !((BitSet) field(f.state, "placed")).get(voxels.indexOf(body))
                && balance(f.state, "paid") == 0 && f.state.getCursor() == 0,
                "Mid-construction manual lookalike cannot skip owned installation or trigger an installation debit: " + f.state.getStatus());
        });
        scenario("legacy-free-hull-repair", () -> {
            Fixture f = new Fixture(CriticalMachineKind.TURBINE); ready(f);
            java.util.List<CriticalGeometry.Voxel> hull = CriticalGeometry.supplyHull();
            java.util.Map<?, ?> indices = (java.util.Map<?, ?>) field(f.state, "protectionIndices");
            CriticalGeometry.Voxel support = hull.stream().filter(v -> !indices.containsKey(v.positionKey())).findFirst().orElseThrow();
            ((BitSet) field(f.state, "supplyHullPlaced")).set(hull.indexOf(support));
            set(f.state, "supplyHullComplete", true); put(f.state, support, support.block);
            CriticalWorldData.get(f.world).updateProtection(f.state);
            remove(f, support); put(f.state, support, support.block);
            check(f.state.repairStructure(f.player), "Legacy free supply hull accepts the same owning-loom repair flow");
            for (int tick = 0; tick < 1000 && !f.state.getStatus().contains("先清空"); tick++) update(f);
            check(f.state.getStatus().contains("先清空") && f.state.getDamageHighlightSnapshot().knownTotal == 1,
                "Legacy identical manual hull occupation remains invalid and is highlighted for clearing");
            remove(f, support);
            for (int tick = 0; tick < 1000 && f.state.getState() == 4; tick++) update(f);
            check(f.state.getState() == 2 && f.world.getBlock(f.state.blockWorldX(support), f.state.blockWorldY(support.y), f.state.blockWorldZ(support)) == support.block
                && f.state.getDamageHighlightSnapshot().knownTotal == 0 && save(f.state).getByteArray("placed").length == 0
                && !((CriticalConstructionLedger) field(f.state, "constructionLedger")).hasSpentItems(),
                "Existing free hull installer restores only the empty legacy casing without adding paid placements or salvage entitlement");
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
