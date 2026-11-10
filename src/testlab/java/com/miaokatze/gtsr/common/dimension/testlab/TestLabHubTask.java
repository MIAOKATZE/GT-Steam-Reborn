package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.Locale;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.machine.base.IHubController;
import com.miaokatze.gtsr.common.machine.base.MTEHubArrayBase;
import com.mojang.authlib.GameProfile;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.FluidEjectionHelper;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** Real host/storage/port and item-bound node scenario; never tests a disconnected hub component as production. */
final class TestLabHubTask implements TestLabTask {

    private final MetaTileEntityID target;
    private final boolean water;
    private final int tier;
    private IGregTechTileEntity tile, input, output, node;
    private MTEHubArrayBase<?> machine;
    private IHubController hub;
    private TestLabStructure builder;
    private int phase, started;
    private long inserted, drained;
    private int originX, originY, originZ;
    private String nativeIssue = "";
    private String supplyPath = "hub_input";
    private String fluidModeRegression = "NOT_APPLICABLE";

    TestLabHubTask(MetaTileEntityID target) {
        this.target = target;
        water = target.name()
            .contains("WATER")
            || target.name()
                .contains("FLUID_");
        tier = target.name()
            .contains("OVERPRESSURE") ? 3
                : target.name()
                    .contains("REINFORCED") ? 2 : 1;
    }

    static boolean handles(MetaTileEntityID id) {
        String name = id.name();
        return id == MetaTileEntityID.STEAM_HUB_ARRAY || id == MetaTileEntityID.WATER_HUB_ARRAY
            || name.contains("CACHE_NODE")
            || name.contains("HUB_STORAGE_UNIT")
            || name.startsWith("SINGULARITY_STEAM_") && name.contains("COMPARTMENT")
            || name.startsWith("SINGULARITY_FLUID_")
            || name.startsWith("STEAM_HUB_")
            || name.startsWith("WATER_HUB_");
    }

    @Override
    public String id() {
        return target.name()
            .toLowerCase(Locale.ROOT);
    }

    @Override
    public String displayName() {
        return target.name() + " real hub integration";
    }

    @Override
    public int timeoutTicks() {
        return 1600;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        originX = x;
        originY = y;
        originZ = z;
        tile = TestLabFixtures.machine(
            world,
            x + 80,
            y + 12,
            z + 64,
            (water ? MetaTileEntityID.WATER_HUB_ARRAY : MetaTileEntityID.STEAM_HUB_ARRAY).ID);
        machine = (MTEHubArrayBase<?>) tile.getMetaTileEntity();
        hub = (IHubController) machine;
        builder = new TestLabStructure(world, machine, new ItemStack(net.minecraft.init.Items.stick, tier));
    }

    boolean supportsExisting() {
        return !isNodeTarget();
    }

    void resumeExisting(WorldServer world, IGregTechTileEntity existing,
        java.util.List<IGregTechTileEntity> existingTiles) {
        tile = existing;
        machine = (MTEHubArrayBase<?>) tile.getMetaTileEntity();
        hub = (IHubController) machine;
        if (!TestLabStructure.checkStructure(machine, tile))
            throw new IllegalStateException("Existing hub native structure rejected");
        int inputId = (water ? MetaTileEntityID.WATER_HUB_INPUT_HATCH : MetaTileEntityID.STEAM_HUB_INPUT_HATCH).ID;
        int outputId = (water ? MetaTileEntityID.WATER_HUB_OUTPUT_HATCH : MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH).ID;
        for (IGregTechTileEntity candidate : existingTiles) {
            if (candidate.getMetaTileID() == inputId) input = candidate;
            if (candidate.getMetaTileID() == outputId) output = candidate;
        }
        if (input == null || output == null) throw new IllegalStateException("Existing hub ports missing");
        FluidStack resource = water ? Materials.Water.getFluid(100000) : Materials.Steam.getGas(100000);
        inserted = ((MTEBasicTank) input.getMetaTileEntity()).fill(resource, true);
        FluidStack extracted = ((MTEBasicTank) output.getMetaTileEntity()).drain((int) inserted, true);
        drained = extracted == null ? 0 : extracted.amount;
        if (inserted != 100000 || drained != inserted || !resource.isFluidEqual(extracted))
            throw new IllegalStateException(
                "Existing hub storage/port roundtrip failed inserted=" + inserted + " drained=" + drained);
        tile.enableWorking();
        supplyPath = "existing_ports_only_no_rebuild";
        phase = 4;
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        if (phase == 0) {
            if (!builder.buildTick()) return null;
            phase = 1;
            return null;
        }
        if (phase == 1) {
            MetaTileEntityID storage = tier == 3 ? MetaTileEntityID.OVERPRESSURE_HUB_STORAGE_UNIT
                : tier == 2 ? MetaTileEntityID.REINFORCED_HUB_STORAGE_UNIT : MetaTileEntityID.HUB_STORAGE_UNIT;
            if (builder.install(storage.ID) == null) return result(
                TestLabReport.Status.FAIL,
                "structure=FAIL no storage position accepted by native element");
            phase = 2;
            return null;
        }
        if (phase == 2) {
            input = builder
                .install((water ? MetaTileEntityID.WATER_HUB_INPUT_HATCH : MetaTileEntityID.STEAM_HUB_INPUT_HATCH).ID);
            phase = 3;
            return null;
        }
        if (phase == 3) {
            output = builder.install(
                (water ? MetaTileEntityID.WATER_HUB_OUTPUT_HATCH : MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH).ID);
            if (input == null || output == null)
                return result(TestLabReport.Status.FAIL, "structure=FAIL real hub ports unavailable");
            ((IInventory) tile).setInventorySlotContents(
                1,
                (tier == 3 ? GTSRItemList.ReinforcedHubSingularityChip : GTSRItemList.HubSingularityChip).get(1));
            if (!TestLabStructure.checkStructure(machine, tile)) return result(
                TestLabReport.Status.FAIL,
                "structure=FAIL native hub check rejected " + builder.describe() + " " + builder.diagnoseFailure());
            // Probe actual input delegation into storage and actual output delegation out of the same storage.
            FluidStack resource = water ? Materials.Water.getFluid(100000) : Materials.Steam.getGas(100000);
            inserted = ((MTEBasicTank) input.getMetaTileEntity()).fill(resource, true);
            FluidStack extracted = ((MTEBasicTank) output.getMetaTileEntity()).drain((int) inserted, true);
            drained = extracted == null ? 0 : extracted.amount;
            if (inserted != 100000 || drained != inserted
                || !resource.isFluidEqual(extracted)
                || hub.getStoredFluidAmount() != 0)
                return result(
                    TestLabReport.Status.FAIL,
                    "structure=PASS function=FAIL storage/port roundtrip inserted=" + inserted
                        + " extracted="
                        + extracted
                        + " stored="
                        + hub.getStoredFluidAmount());
            if (isNodeTarget()) bindNode(world);
            tile.enableWorking();
            started = elapsedTicks;
            phase = 4;
            return null;
        }
        if (node == null) return result(
            TestLabReport.Status.PASS,
            "structure=PASS function=PASS real_input_storage_output_roundtrip=" + inserted
                + " capacity="
                + hub.getTotalCapacity()
                + " production=NOT_APPLICABLE");
        IHubCacheNode cache = (IHubCacheNode) node.getMetaTileEntity();
        if (elapsedTicks - started < 40) return null;
        boolean received = cache.isOutputMode();
        long nodeStored = cache.getStoredFluidAmount(), hubStored = hub.getStoredFluidAmount();
        if (received && nodeStored > 0 && hubStored < inserted || !received && nodeStored < inserted && hubStored > 0)
            return result(
                nativeIssue.isEmpty() ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL,
                "structure=PASS function=" + (nativeIssue.isEmpty() ? "PASS" : "FAIL secondary_locked_transfer=PASS")
                    + " binding=real_item_interaction natural_transfer=true mode="
                    + (received ? "hub_to_node" : "node_to_hub")
                    + " sourceInitial="
                    + inserted
                    + " hubStored="
                    + hubStored
                    + " nodeStored="
                    + nodeStored
                    + " supplyPath="
                    + supplyPath
                    + " nativeIssue="
                    + nativeIssue
                    + " fluidModeRegression="
                    + fluidModeRegression
                    + " production=NOT_APPLICABLE");
        return null;
    }

    private boolean isNodeTarget() {
        return target.name()
            .contains("CACHE_NODE")
            || target.name()
                .contains("COMPARTMENT");
    }

    private void bindNode(WorldServer world) throws Exception {
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(TestLabFixtures.OWNER, "GTSR Test Lab"));
        player.setPosition(tile.getXCoord() + 0.5, tile.getYCoord(), tile.getZCoord() - 2);
        player.inventory.currentItem = 0;
        player.inventory.mainInventory[0] = GregTechAPI.METATILEENTITIES[target.ID].getStackForm(1);
        player.inventory.mainInventory[1] = GTSRItemList.SteamEntangledSingularity.get(16);
        if (!machine.tryHandleNodeBindClick(tile, player, true))
            throw new IllegalStateException("Host rejected node type");
        ItemStack bound = player.getHeldItem();
        if (bound == null || !bound.hasTagCompound()
            || !bound.getTagCompound()
                .hasKey("gtsr.hubPos"))
            throw new IllegalStateException("Real node binding failed");
        node = TestLabFixtures.machine(world, originX + 96, originY + 12, originZ + 64, target.ID);
        NBTTagCompound saved = new NBTTagCompound();
        node.getMetaTileEntity()
            .saveNBTData(saved);
        saved.setTag(
            "gtsr.hubPos",
            bound.getTagCompound()
                .getCompoundTag("gtsr.hubPos")
                .copy());
        saved.setBoolean("gtsr.singularity_consumed", true);
        node.getMetaTileEntity()
            .loadNBTData(saved);
        if (target == MetaTileEntityID.SINGULARITY_FLUID_OUTPUT_COMPARTMENT)
            verifyAnyFluidOutput((MTEHatchOutput) node.getMetaTileEntity());
        IHubCacheNode cache = (IHubCacheNode) node.getMetaTileEntity();
        cache.setAutoOutput(false);
        if (!cache.isOutputModeLocked()) cache.setOutputMode(true);
        node.enableWorking();
        FluidStack resource = water ? Materials.Water.getFluid((int) inserted) : Materials.Steam.getGas((int) inserted);
        if (cache.isOutputMode()) ((MTEBasicTank) input.getMetaTileEntity()).fill(resource, true);
        else {
            MTEHatchOutput hatch = (MTEHatchOutput) node.getMetaTileEntity();
            if (hatch.fill(ForgeDirection.NORTH, resource, false) != 0)
                throw new IllegalStateException("Sending compartment unexpectedly accepts external pipe input");
            // GT machines eject production through output transactions. External tank fill is deliberately
            // forbidden by the sending compartment and is not a legitimate way to seed this scene.
            int filled = ejectIntoOutput(hatch, resource);
            supplyPath = "real_GT_output_transaction";
            if (filled == 0 && target == MetaTileEntityID.SINGULARITY_FLUID_OUTPUT_COMPARTMENT) {
                nativeIssue = "default_unlocked_any_fluid_output_rejected_water_by_native_transaction; canStoreFluid="
                    + hatch.canStoreFluid(resource)
                    + "; outputsSteam="
                    + hatch.outputsSteam()
                    + "; outputsLiquids="
                    + hatch.outputsLiquids();
                if (!hatch.setFilter(resource))
                    throw new IllegalStateException("Native output fluid-lock configuration rejected water");
                filled = ejectIntoOutput(hatch, resource);
                supplyPath += "_secondary_real_water_lock_configuration";
            }
            if (filled != inserted) throw new IllegalStateException("Sending node rejected fixture fluid " + filled);
        }
        // Inventory is only a binding fixture; release it so later scenarios cannot consume stale singularities.
        player.inventory.mainInventory[0] = null;
        player.inventory.mainInventory[1] = null;
    }

    private void verifyAnyFluidOutput(MTEHatchOutput hatch) {
        NBTTagCompound legacy = new NBTTagCompound();
        hatch.saveNBTData(legacy);
        legacy.setByte("mMode", (byte) 3);
        hatch.loadNBTData(legacy);
        NBTTagCompound migrated = new NBTTagCompound();
        hatch.saveNBTData(migrated);
        if (migrated.getByte("mMode") != 0 || !hatch.isEmptyAndAcceptsAnyFluid())
            throw new IllegalStateException("Legacy steam-only default did not migrate to any-fluid mode");
        FluidStack waterProbe = Materials.Water.getFluid(1);
        FluidStack lavaProbe = new FluidStack(net.minecraftforge.fluids.FluidRegistry.LAVA, 1);
        FluidStack steamProbe = Materials.Steam.getGas(1);
        for (FluidStack probe : new FluidStack[] { waterProbe, lavaProbe, steamProbe }) {
            if (ejectIntoOutput(hatch, probe) != 1 || hatch.getFluid() == null
                || hatch.getFluid().amount != 1
                || !probe.isFluidEqual(hatch.getFluid()))
                throw new IllegalStateException("Any-fluid native output transaction failed for " + probe);
            FluidStack incompatible = probe.isFluidEqual(waterProbe) ? lavaProbe : waterProbe;
            if (ejectIntoOutput(hatch, incompatible) != 0 || hatch.getFluid().amount != 1)
                throw new IllegalStateException("Native output mixed incompatible fluids");
            FluidStack drainedProbe = hatch.drain(1, true);
            if (!probe.isFluidEqual(drainedProbe) || drainedProbe.amount != 1 || hatch.getFluidAmount() != 0)
                throw new IllegalStateException("Native output regression did not drain exact fixture amount");
        }
        if (!hatch.setFilter(waterProbe)) throw new IllegalStateException("Native water lock refused");
        NBTTagCompound locked = new NBTTagCompound();
        hatch.saveNBTData(locked);
        for (byte mode : new byte[] { 8, 9 }) {
            locked.setByte("mMode", mode);
            hatch.loadNBTData(locked);
            NBTTagCompound roundtrip = new NBTTagCompound();
            hatch.saveNBTData(roundtrip);
            hatch.loadNBTData(roundtrip);
            if (roundtrip.getByte("mMode") != mode || !hatch.isFluidLocked()
                || hatch.getLockedFluid() != waterProbe.getFluid())
                throw new IllegalStateException("Explicit fluid lock mode/name did not survive NBT: " + mode);
            if (ejectIntoOutput(hatch, lavaProbe) != 0 || ejectIntoOutput(hatch, steamProbe) != 0
                || hatch.getFluidAmount() != 0)
                throw new IllegalStateException("Explicit water lock accepted another fluid: " + mode);
            if (ejectIntoOutput(hatch, waterProbe) != 1)
                throw new IllegalStateException("Explicit water lock refused matching fluid: " + mode);
            hatch.drain(1, true);
        }
        if (!hatch.setFilter(null) || !hatch.isEmptyAndAcceptsAnyFluid())
            throw new IllegalStateException("Native fluid unlock did not restore any-fluid mode");
        fluidModeRegression = "PASS legacy_3_to_0; water_lava_steam_native_transactions; mixed_fluid_rejection;"
            + " locked_8_9_NBT_and_matching_only; native_unlock";
    }

    private static int ejectIntoOutput(MTEHatchOutput hatch, FluidStack resource) {
        FluidEjectionHelper ejection = new FluidEjectionHelper(java.util.Collections.singletonList(hatch), true);
        int filled = ejection.ejectStack(resource.copy());
        ejection.commit();
        return filled;
    }

    @Override
    public void stop(WorldServer world) {
        if (tile != null) tile.disableWorking();
        if (machine != null) machine.stopMachine(ShutDownReasonRegistry.NONE);
        if (node != null) {
            machine
                .unregisterCacheNode(node.getXCoord(), node.getYCoord(), node.getZCoord(), world.provider.dimensionId);
            ((IHubCacheNode) node.getMetaTileEntity()).setHubTransferRate(0);
            TestLabFixtures.unbindNode(node);
        }
    }

    @Override
    public String debug(WorldServer world) {
        return "phase=" + phase
            + " tier="
            + tier
            + " hubStored="
            + (hub == null ? -1 : hub.getStoredFluidAmount())
            + " supplyPath="
            + supplyPath
            + " nativeIssue="
            + nativeIssue
            + " "
            + TestLabFixtures.snapshot(tile)
            + " node{"
            + TestLabFixtures.snapshot(node)
            + "}";
    }

    private TestLabReport.Result result(TestLabReport.Status status, String detail) {
        return new TestLabReport.Result(
            status,
            detail + " controller=" + tile.getXCoord() + "," + tile.getYCoord() + "," + tile.getZCoord());
    }
}
