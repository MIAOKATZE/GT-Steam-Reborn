// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.common.api.enums.GTSRHatchElement;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.compat.gtnl.GTNLMixinSelection;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;
import com.mojang.authlib.GameProfile;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Explicit bounded live-game probes. These do not claim formed-machine recipe or crash-atomicity coverage. */
public final class TestLabFollowupTask implements TestLabTask {

    private final String mode;
    private final List<IGregTechTileEntity> owned = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private int x, y, z, checks;
    private CriticalMachineState critical;
    private NBTTagCompound criticalBefore;

    public TestLabFollowupTask(String mode) {
        this.mode = mode;
    }

    @Override
    public String id() {
        return "followup_" + mode;
    }

    @Override
    public String displayName() {
        return id();
    }

    @Override
    public int timeoutTicks() {
        return 100;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private void check(boolean value, String label) {
        evidence.put(label, value);
        if (!value) throw new IllegalStateException(label);
        checks++;
    }

    private IGregTechTileEntity place(WorldServer world, int id) {
        int index = owned.size();
        IGregTechTileEntity tile = TestLabFixtures.machine(world, x + index % 8 * 2, y, z + index / 8 * 2, id);
        owned.add(tile);
        return tile;
    }

    private Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method m = type.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m.invoke(target, args);
            } catch (NoSuchMethodException missing) {}
        }
        throw new NoSuchMethodException(name);
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int ticks) throws Exception {
        if (mode.equals("critical_recovery")) critical(world);
        else if (mode.equals("ammonia_payment")) ammonia(world);
        else gtnl(world);
        evidence.put("checks", checks);
        evidence.put(
            "scope",
            "Real registered tiles/native fluid IO/Forge event delivery; explicit fixture state, no formed-machine production or disk-crash claim");
        return new TestLabReport.Result(TestLabReport.Status.PASS, id() + " checks=" + checks, evidence);
    }

    private void critical(WorldServer world) {
        IGregTechTileEntity tile = place(world, MetaTileEntityID.CRITICAL_LOOM.ID);
        critical = ((MTECriticalMachine) tile.getMetaTileEntity()).getCriticalState();
        criticalBefore = new NBTTagCompound();
        critical.writeToNBT(criticalBefore);
        critical.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 7));
        NBTTagCompound valid = new NBTTagCompound();
        critical.writeToNBT(valid);
        critical.readFromNBT(valid);
        check(critical.getStackInSlot(0).stackSize == 7, "live_controller_nbt_restores_resources");
        NBTTagCompound bad = (NBTTagCompound) valid.copy();
        bad.getCompoundTag("criticalEnergyRuntime")
            .setString("eu", "broken-exact-amount");
        critical.readFromNBT(bad);
        check(!critical.canRemove() && critical.getStackInSlot(0) == null, "bad_runtime_locks_resources_and_removal");
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(TestLabFixtures.OWNER, "GTSR Test Lab"));
        player.setPosition(tile.getXCoord() + .5, tile.getYCoord() + .5, tile.getZCoord() + .5);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            world,
            GregTechAPI.sBlockMachines,
            world.getBlockMetadata(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()),
            player);
        MinecraftForge.EVENT_BUS.post(event);
        check(
            event.isCanceled() && world.getTileEntity(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()) == tile,
            "real_forge_break_event_rejects_quarantined_controller");
        NBTTagCompound saved = new NBTTagCompound();
        critical.writeToNBT(saved);
        check(
            saved.getCompoundTag("resourceNbtQuarantine")
                .equals(bad),
            "saved_quarantine_preserves_original_record");
        critical.readFromNBT(valid);
        check(
            critical.decrStackSize(0, 7).stackSize == 7 && critical.decrStackSize(0, 7) == null,
            "explicit_good_record_recovers_inventory_once");
    }

    private int ordinaryInputId() {
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++) if (GregTechAPI.METATILEENTITIES[i] != null
            && GregTechAPI.METATILEENTITIES[i].getClass() == MTEHatchInput.class) return i;
        throw new IllegalStateException("No native input hatch registered");
    }

    private void ammonia(WorldServer world) throws Exception {
        MTEAmmoniaPlant plant = (MTEAmmoniaPlant) place(world, MetaTileEntityID.AMMONIA_PLANT.ID).getMetaTileEntity();
        IGregTechTileEntity gasTile = place(world, ordinaryInputId()), steamTile = place(world, ordinaryInputId());
        MTEHatchInput gas = (MTEHatchInput) gasTile.getMetaTileEntity(),
            steam = (MTEHatchInput) steamTile.getMetaTileEntity();
        plant.addInputHatchToMachineList(gasTile, 0);
        plant.addInputHatchToMachineList(steamTile, 0);
        gas.fill(ForgeDirection.UNKNOWN, gregtech.api.enums.Materials.Gas.getGas(20), true);
        steam.fill(ForgeDirection.UNKNOWN, gregtech.api.enums.Materials.Steam.getGas(10), true);
        check(
            !(Boolean) invoke(plant, "consumeFuelAndSteam", new Class<?>[] { int.class, int.class }, 10, 20)
                && gas.getFluidAmount() == 20
                && steam.getFluidAmount() == 10,
            "both_prepare_before_any_live_debit");
        steam.fill(ForgeDirection.UNKNOWN, gregtech.api.enums.Materials.Steam.getGas(10), true);
        check(
            (Boolean) invoke(plant, "consumeFuelAndSteam", new Class<?>[] { int.class, int.class }, 10, 20)
                && gas.getFluidAmount() == 10
                && steam.getFluidAmount() == 0,
            "live_hatches_pay_one_confirmed_pair");
        NBTTagCompound record = new NBTTagCompound();
        plant.saveNBTData(record);
        check(record.getLong("gtsrAmmoniaPendingSuperheatedSteam") == 20, "backpressured_side_output_is_owned_in_nbt");
        NBTTagCompound item = new NBTTagCompound();
        plant.setItemNBT(item);
        check(item.getLong("gtsrAmmoniaPendingSuperheatedSteam") == 20, "controller_item_retains_owned_side_output");
        plant.loadNBTData(record);
        check(
            plant.thermalGasCredit() == 0 && plant.thermalSteamCredit() == 0,
            "paid_pair_is_not_recreated_by_live_reload");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void gtnl(WorldServer world) throws Exception {
        boolean enabled = GTNLMixinSelection.pipelessEnabled();
        evidence.put("pipelessEnabled", enabled);
        if (!enabled) {
            check(PipelessSteamSupport.count(new Object()) == 0, "disabled_or_absent_count_is_zero");
            check(
                PipelessSteamSupport.candidates(java.util.Collections.emptyList(), true)
                    .isEmpty(),
                "disabled_or_absent_has_no_optional_candidates");
            return;
        }
        int wireless = -1;
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++)
            if (GregTechAPI.METATILEENTITIES[i] instanceof MTEHatch hatch && hatch.mTier == 0
                && PipelessSteamSupport.hatchClass()
                    .isInstance(hatch)) {
                        wireless = i;
                        break;
                    }
        check(wireless >= 0, "actual_tier_zero_gtnl_registry_entry");
        MetaTileEntityID[] ids = { MetaTileEntityID.GEAR_STEAM_COMPRESSOR, MetaTileEntityID.KINETIC_PROCESSING_ARRAY,
            MetaTileEntityID.SIEMENS_MARTIN_FURNACE, MetaTileEntityID.AMMONIA_PLANT,
            MetaTileEntityID.CLUSTER_CONTROLLER, MetaTileEntityID.CRITICAL_SINGULARITY_COMPRESSOR,
            MetaTileEntityID.DENSE_STATE_MANIPULATOR, MetaTileEntityID.STEAM_SINGULARITY_ENTANGLER,
            MetaTileEntityID.SINGULARITY_CRUST_STEAM_BORER, MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY,
            MetaTileEntityID.AIR_COMPRESSOR, MetaTileEntityID.ATMOSPHERIC_CENTRIFUGE,
            MetaTileEntityID.LARGE_STEAM_FURNACE, MetaTileEntityID.VEIN_STEAM_PYROLYZER,
            MetaTileEntityID.CRUST_STEAM_BORER, MetaTileEntityID.STEAM_FLUID_DRILL,
            MetaTileEntityID.SINGULARITY_DRILLING_HUB };
        for (MetaTileEntityID id : ids) {
            IGregTechTileEntity tile = place(world, id.ID), source = place(world, wireless);
            Object machine = tile.getMetaTileEntity();
            boolean accepted;
            if (machine instanceof MTESingularityMachineBase singularity)
                accepted = singularity.addSteamInputToMachineList(source, 0);
            else if (machine instanceof MTEGTSRMultiBlockBase base)
                accepted = base.addInputHatchToMachineList(source, 0);
            else if (machine instanceof com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray)
                accepted = (Boolean) invoke(
                    machine,
                    "addPressureSteamToMachineList",
                    new Class<?>[] { IGregTechTileEntity.class, int.class },
                    source,
                    0);
            else accepted = GTSRHatchElement.SteamInputHatch.adder()
                .apply((MTEMultiBlockBase) machine, source, (short) 0);
            check(accepted && PipelessSteamSupport.count(machine) == 1, "source_registration_" + id.name());
        }
        MTEGearSteamCompressor gear = (MTEGearSteamCompressor) owned.get(0)
            .getMetaTileEntity();
        MTEHatch source = (MTEHatch) owned.get(1)
            .getMetaTileEntity();
        source.fill(ForgeDirection.UNKNOWN, FluidRegistry.getFluidStack("steam", 200), true);
        IGregTechTileEntity pressureTile = place(world, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID);
        MTEHatchPressureSteamInput pressure = (MTEHatchPressureSteamInput) pressureTile.getMetaTileEntity();
        pressure.fill(ForgeDirection.UNKNOWN, FluidRegistry.getFluidStack("steam", 100), true);
        invoke(
            gear,
            "addPressureSteamToMachineList",
            new Class<?>[] { IGregTechTileEntity.class, int.class },
            pressureTile,
            0);
        check(
            (Boolean) invoke(gear, "depleteSteam", new Class<?>[] { long.class }, 250L)
                && pressure.getFluidAmount() == 0
                && source.drain(Integer.MAX_VALUE, false).amount == 50,
            "native_pressure_paid_first_optional_only_remaining");
        Object other = owned.get(2)
            .getMetaTileEntity();
        ((IAdditionalSteamSourceHolder) gear).gtsr$getAdditionalSteamSources()
            .clear();
        check(PipelessSteamSupport.count(other) == 1, "each_controller_has_its_own_source_collection");
        gear.clearHatches();
        check(PipelessSteamSupport.count(gear) == 0, "native_hatch_lifecycle_clears_optional_sources");
    }

    @Override
    public void stop(WorldServer world) {
        if (critical != null && criticalBefore != null) critical.readFromNBT(criticalBefore);
        for (IGregTechTileEntity tile : owned) tile.disableWorking();
    }
}
