package com.miaokatze.gtsr.common.dimension.testlab;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;
import com.miaokatze.gtsr.compat.gtnl.GTNLMixinSelection;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;

/** One real canonical plant, finite cold observation, traced warm-save recovery and natural recipe ticks. */
final class TestLabAmmoniaNaturalTask implements TestLabTask {

    private enum Phase {
        BUILD,
        INSTALL,
        FORM,
        COLD,
        COLD_SHORT,
        START,
        RUN,
        ACTIVE_SHORT,
        RESUME,
        IDLE_AFTER
    }

    private final boolean pipeless;
    private final List<IGregTechTileEntity> ports = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final List<Map<String, Object>> samples = new ArrayList<>();
    private IGregTechTileEntity controller;
    private MTEAmmoniaPlant plant;
    private TestLabStructure structure;
    private MTEHatch gas, steam, nitrogen;
    private MTEHatchOutput primary, thermal;
    private Phase phase = Phase.BUILD;
    private int portCursor, phaseTick, checks, naturalPaidTicks, resumedTicks;
    private long gasSupplied, steamSupplied, steamRemoved, thermalRemoved, thermalPrefill;
    private long gasBefore, steamBefore, coldGas, coldSteam, pausedGas, pausePending;
    private int progressBefore, maxBefore, pauseProgress, pauseMax;
    private boolean observedRealFormation, pressureReleased, recipeStarted;

    TestLabAmmoniaNaturalTask(boolean pipeless) {
        this.pipeless = pipeless;
    }

    @Override
    public String id() {
        return "followup_ammonia_natural" + (pipeless ? "_gtnl" : "");
    }

    @Override
    public String displayName() {
        return id();
    }

    @Override
    public int timeoutTicks() {
        return 2600;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        TestLabFixtures.attach(world);
        controller = TestLabFixtures.machine(world, x + 80, y + 12, z + 48, MetaTileEntityID.AMMONIA_PLANT.ID);
        plant = (MTEAmmoniaPlant) controller.getMetaTileEntity();
        plant.setVoidingMode(gregtech.api.enums.VoidingMode.VOID_NONE);
        structure = new TestLabStructure(world, plant, new ItemStack(Items.stick, 1));
        evidence.put("pipelessRequested", pipeless);
        evidence.put("pipelessEnabled", GTNLMixinSelection.pipelessEnabled());
        evidence.put(
            "scope",
            "Canonical native StructureLib world construction and actual GT natural ticks; one warm NBT recovery fixture, no direct machine/progress writes or manual updateEntity calls");
        evidence.put(
            "maintenancePolicy",
            "Native ammonia getDefaultHasMaintenanceChecks=false; canonical structure has no maintenance slot, so no incompatible maintenance hatch is fabricated");
        evidence.put("samples", samples);
    }

    private void check(boolean condition, String label) {
        checks++;
        evidence.put(label, condition);
        if (!condition) throw new IllegalStateException(label);
    }

    private TestLabReport.Result result(TestLabReport.Status status, String detail) {
        evidence.put("checks", checks);
        evidence.put("phase", phase.name());
        evidence.put("observedRealFormation", observedRealFormation);
        evidence.put("naturalPaidTicks", naturalPaidTicks);
        evidence.put("gasSupplied", gasSupplied);
        evidence.put("steamSupplied", steamSupplied);
        evidence.put("steamFixtureRemoved", steamRemoved);
        evidence.put("thermalFixturePrefill", thermalPrefill);
        evidence.put("thermalRemoved", thermalRemoved);
        return new TestLabReport.Result(status, detail, evidence);
    }

    private void sample(int tick, String label) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tick", tick);
        row.put("label", label);
        row.put("formed", plant.mMachine);
        row.put("gas", amount(gas));
        row.put("steam", amount(steam));
        row.put("nitrogen", amount(nitrogen));
        row.put("heat", plant.mHeatLevel);
        row.put("progress", plant.getProgresstime());
        row.put("duration", plant.maxProgresstime());
        row.put("ammonia", amount(primary));
        row.put("thermal", amount(thermal));
        row.put("pendingThermal", pending());
        row.put("gasCredit", plant.thermalGasCredit());
        row.put("steamCredit", plant.thermalSteamCredit());
        samples.add(row);
    }

    private static long amount(MTEHatch hatch) {
        if (hatch == null) return 0;
        FluidStack probe = hatch.drain(Integer.MAX_VALUE, false);
        return probe == null ? 0 : probe.amount;
    }

    private long pending() {
        NBTTagCompound saved = new NBTTagCompound();
        plant.saveNBTData(saved);
        return saved.getLong("gtsrAmmoniaPendingSuperheatedSteam");
    }

    private int fill(MTEHatch hatch, FluidStack identity, int amount) {
        FluidStack input = identity.copy();
        input.amount = amount;
        return hatch.fill(ForgeDirection.UNKNOWN, input, true);
    }

    private void topUp() {
        gasSupplied += fill(gas, Materials.Gas.getGas(1), (int) Math.max(0, 20_000 - amount(gas)));
        steamSupplied += fill(steam, Materials.Steam.getGas(1), (int) Math.max(0, 40_000 - amount(steam)));
    }

    private void baseline() {
        gasBefore = amount(gas);
        steamBefore = amount(steam);
        progressBefore = plant.getProgresstime();
        maxBefore = plant.maxProgresstime();
    }

    private void thermalConservation() {
        long consumedSteam = steamSupplied - steamRemoved - amount(steam) - plant.thermalSteamCredit();
        check(
            amount(thermal) + thermalRemoved + pending() - thermalPrefill == consumedSteam,
            "thermal_side_balance_equals_actually_paid_steam");
    }

    private int sourceId() {
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++)
            if (GregTechAPI.METATILEENTITIES[i] instanceof MTEHatch hatch && hatch.mTier == 0
                && PipelessSteamSupport.hatchClass()
                    .isInstance(hatch))
                return i;
        throw new IllegalStateException("Pinned GTNL tier-zero wireless steam hatch is not registered");
    }

    private boolean install(WorldServer world) throws Exception {
        int id;
        switch (portCursor) {
            case 0:
                id = pipeless ? sourceId()
                    : ItemList.Hatch_Input_HV.get(1)
                        .getItemDamage();
                break;
            case 1:
            case 2:
                id = ItemList.Hatch_Input_HV.get(1)
                    .getItemDamage();
                break;
            case 3:
                id = ItemList.Hatch_Output_HV.get(1)
                    .getItemDamage();
                break;
            case 4:
                id = ItemList.Hatch_Output_Bus_HV.get(1)
                    .getItemDamage();
                break;
            default:
                id = MetaTileEntityID.PRESSURE_STEAM_OUTPUT_HATCH.ID;
        }
        IGregTechTileEntity tile = structure.install(id);
        check(tile != null, "canonical_port_accepted_" + portCursor);
        ports.add(tile);
        if (portCursor == 0) {
            steam = (MTEHatch) tile.getMetaTileEntity();
            if (pipeless) {
                UUID actor = UUID.nameUUIDFromBytes(
                    (id() + ":"
                        + controller.getXCoord()
                        + ":"
                        + controller.getZCoord()
                        + ":"
                        + world.getTotalWorldTime()).getBytes(StandardCharsets.UTF_8));
                tile.setOwnerUuid(actor);
                tile.setOwnerName("GTSR isolated ammonia supply");
                evidence.put("wirelessActor", actor.toString());
            }
        } else if (portCursor == 1) gas = (MTEHatch) tile.getMetaTileEntity();
        else if (portCursor == 2) nitrogen = (MTEHatch) tile.getMetaTileEntity();
        else if (portCursor == 3) {
            primary = (MTEHatchOutput) tile.getMetaTileEntity();
            primary.setFilter(Materials.Ammonia.getGas(1));
        } else if (portCursor == 5) thermal = (MTEHatchOutput) tile.getMetaTileEntity();
        return ++portCursor == 6;
    }

    private void warmSaveFixture() {
        NBTTagCompound before = new NBTTagCompound();
        plant.saveNBTData(before);
        NBTTagCompound fixture = (NBTTagCompound) before.copy();
        fixture.setInteger("mHeatLevel", 10000);
        fixture.setInteger("gtsrHeatRemainder", 0);
        plant.loadNBTData(fixture);
        NBTTagCompound after = new NBTTagCompound();
        plant.saveNBTData(after);
        Map<String, Object> warm = new LinkedHashMap<>();
        warm.put("scope", "Warm machine save-recovery fixture after real cold observations; not full natural preheat");
        warm.put("beforeHeat", before.getInteger("mHeatLevel"));
        warm.put("targetHeat", 10000);
        warm.put("afterHeat", after.getInteger("mHeatLevel"));
        warm.put("beforeNBT", before.toString());
        warm.put("afterNBT", after.toString());
        evidence.put("warmFixture", warm);
        before.removeTag("mHeatLevel");
        before.removeTag("gtsrHeatRemainder");
        after.removeTag("mHeatLevel");
        after.removeTag("gtsrHeatRemainder");
        check(before.equals(after) && plant.mHeatLevel == 10000, "warm_load_only_changes_declared_heat_fields");
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int ticks) throws Exception {
        try {
            if (pipeless && !GTNLMixinSelection.pipelessEnabled()) return result(
                TestLabReport.Status.UNSUPPORTED,
                "This explicit GTNL natural case requires pinned GTNL and pipeless=true");
            switch (phase) {
                case BUILD:
                    if (structure.buildTick()) phase = Phase.INSTALL;
                    break;
                case INSTALL:
                    if (install(world)) {
                        phase = Phase.FORM;
                        phaseTick = ticks;
                    }
                    break;
                case FORM:
                    if (!TestLabStructure.checkStructure(plant, controller)) return result(
                        TestLabReport.Status.FAIL,
                        "Canonical ammonia structure rejected: " + structure.describe()
                            + " "
                            + structure.diagnoseFailure());
                    check(plant.mMachine, "native_checkStructure_confirms_real_formation");
                    observedRealFormation = true;
                    check(!plant.hasMaintenanceChecks, "native_no_maintenance_policy_preserved");
                    if (pipeless)
                        check(PipelessSteamSupport.count(plant) == 1, "wireless_is_counted_by_real_structure_checker");
                    thermalPrefill = fill(
                        thermal,
                        net.minecraftforge.fluids.FluidRegistry.getFluidStack("ic2superheatedsteam", 1),
                        thermal.getCapacity());
                    check(thermalPrefill == thermal.getCapacity(), "thermal_backpressure_fixture_fills_real_output");
                    gasSupplied += fill(gas, Materials.Gas.getGas(1), 200);
                    steamSupplied += fill(steam, Materials.Steam.getGas(1), 8000);
                    check(
                        amount(gas) == 200 && amount(steam) == 8000,
                        "native_recipe_filters_accept_real_thermal_inputs");
                    controller.enableWorking();
                    for (IGregTechTileEntity port : ports) port.enableWorking();
                    coldGas = amount(gas);
                    coldSteam = amount(steam);
                    phase = Phase.COLD;
                    phaseTick = ticks;
                    baseline();
                    sample(ticks, "cold_start");
                    break;
                case COLD:
                    check(plant.mMachine, "real_formation_remains_valid");
                    check(
                        plant.maxProgresstime() == 0 && amount(primary) == 0,
                        "cold_plant_cannot_start_ammonia_without_heat_or_recipe_inputs");
                    check(
                        steamBefore - amount(steam) == 40 * (gasBefore - amount(gas)),
                        "cold_natural_tick_pays_both_thermal_resources");
                    thermalConservation();
                    if (amount(gas) == 0 && amount(steam) == 0) {
                        check(
                            plant.mHeatLevel == 10 && coldGas == 200 && coldSteam == 8000,
                            "twenty_actual_cold_payments_raise_ten_heat");
                        sample(ticks, "cold_twenty_paid_ticks");
                        gasSupplied += fill(gas, Materials.Gas.getGas(1), 20);
                        pausedGas = amount(gas);
                        pausePending = pending();
                        phase = Phase.COLD_SHORT;
                        phaseTick = ticks;
                    } else if (ticks - phaseTick > 600) return result(
                        TestLabReport.Status.FAIL,
                        "No bounded natural cold payment completion: " + debug(world));
                    baseline();
                    break;
                case COLD_SHORT:
                    check(
                        amount(gas) == pausedGas && amount(steam) == 0 && plant.maxProgresstime() == 0,
                        "missing_steam_does_not_spend_gas_or_start_recipe");
                    check(pending() == pausePending, "missing_pair_does_not_create_thermal_output");
                    if (ticks - phaseTick >= 20) {
                        sample(ticks, "cold_missing_pair_window");
                        warmSaveFixture();
                        check(
                            TestLabStructure.checkStructure(plant, controller),
                            "warm_recovery_rechecks_canonical_structure");
                        topUp();
                        check(
                            fill(nitrogen, Materials.Nitrogen.getGas(1), 1000) == 1000,
                            "one_native_recipe_nitrogen_filled");
                        phase = Phase.START;
                        phaseTick = ticks;
                        baseline();
                    }
                    break;
                case START:
                    thermalConservation();
                    if (plant.maxProgresstime() > 0) {
                        check(
                            amount(nitrogen) == 0 && plant.maxProgresstime() == 1280,
                            "native_recipe_consumes_one_batch_and_freezes_duration");
                        recipeStarted = true;
                        phase = Phase.RUN;
                        sample(ticks, "natural_recipe_started");
                    } else if (ticks - phaseTick > 200) return result(
                        TestLabReport.Status.FAIL,
                        "Warm restored plant did not start a real recipe: " + debug(world));
                    topUp();
                    baseline();
                    break;
                case RUN:
                case RESUME:
                    check(plant.mMachine && controller.isAllowedToWork(), "natural_active_batch_is_not_aborted");
                    thermalConservation();
                    if (maxBefore > 0 && plant.maxProgresstime() > 0) {
                        check(plant.getProgresstime() == progressBefore + 1, "one_natural_tick_advances_once");
                        check(
                            gasBefore - amount(gas) == 10 && steamBefore - amount(steam) == 1400,
                            "active_progress_requires_same_tick_full_gas_and_steam_pair");
                        naturalPaidTicks++;
                    }
                    if (phase == Phase.RUN && naturalPaidTicks >= 1) {
                        FluidStack removed = steam.drain(Integer.MAX_VALUE, true);
                        steamRemoved += removed == null ? 0 : removed.amount;
                        pauseProgress = plant.getProgresstime();
                        pauseMax = plant.maxProgresstime();
                        pausedGas = amount(gas);
                        pausePending = pending();
                        phase = Phase.ACTIVE_SHORT;
                        phaseTick = ticks;
                        sample(ticks, "active_steam_removed_fixture");
                        baseline();
                        break;
                    }
                    if (phase == Phase.RESUME) {
                        resumedTicks++;
                        if (!pressureReleased && resumedTicks >= 32) {
                            check(
                                pending() > pausePending && amount(primary) == 0,
                                "thermal_backpressure_does_not_use_primary_ammonia_port");
                            FluidStack removed = thermal.drain(Integer.MAX_VALUE, true);
                            thermalRemoved += removed == null ? 0 : removed.amount;
                            pressureReleased = true;
                            sample(ticks, "thermal_output_unblocked");
                        }
                    }
                    if (recipeStarted && plant.maxProgresstime() == 0 && amount(primary) >= 1000) {
                        check(
                            amount(primary) == 1000 && primary.getFluid()
                                .isFluidEqual(Materials.Ammonia.getGas(1)),
                            "one_natural_batch_delivers_exact_ammonia");
                        check(
                            gasBefore - amount(gas) == 10 && steamBefore - amount(steam) == 1400,
                            "completion_tick_does_not_repeat_idle_payment");
                        naturalPaidTicks++;
                        check(
                            amount(nitrogen) == 0 && naturalPaidTicks == 1280,
                            "no_second_batch_or_synthetic_progress");
                        check(
                            pressureReleased && amount(thermal) > 0,
                            "backpressured_thermal_output_eventually_reaches_real_side_port");
                        sample(ticks, "natural_recipe_output");
                        phase = Phase.IDLE_AFTER;
                        phaseTick = ticks;
                        topUp();
                        baseline();
                        break;
                    }
                    if (ticks % 40 == 0) sample(ticks, "natural_running");
                    topUp();
                    baseline();
                    break;
                case IDLE_AFTER:
                    check(
                        plant.maxProgresstime() == 0 && amount(primary) == 1000,
                        "no_unfunded_second_recipe_after_completion");
                    check(
                        gasBefore - amount(gas) == 10 && steamBefore - amount(steam) == 600,
                        "subsequent_idle_ticks_still_pay_exactly_one_maintenance_pair");
                    thermalConservation();
                    if (ticks - phaseTick >= 5) {
                        sample(ticks, "five_natural_idle_maintenance_ticks");
                        return result(
                            TestLabReport.Status.PASS,
                            "Canonical plant natural cold/shortage/pause/recovery/start/run/completion/idle/backpressure and one ammonia recipe passed");
                    }
                    topUp();
                    baseline();
                    break;
                case ACTIVE_SHORT:
                    check(
                        plant.mMachine && plant.getProgresstime() == pauseProgress
                            && plant.maxProgresstime() == pauseMax,
                        "unpaid_active_batch_keeps_native_progress_and_outputs_reserved");
                    check(
                        amount(gas) == pausedGas && amount(steam) == 0 && pending() == pausePending,
                        "unpaid_active_batch_does_not_burn_gas_or_emit_side_steam");
                    if (ticks - phaseTick >= 20) {
                        sample(ticks, "active_twenty_tick_pause");
                        topUp();
                        phase = Phase.RESUME;
                        baseline();
                    }
                    break;
            }
            return null;
        } catch (RuntimeException failure) {
            sample(ticks, "failure");
            evidence.put("failure", failure.toString());
            return result(TestLabReport.Status.FAIL, failure.getMessage());
        }
    }

    @Override
    public String debug(WorldServer world) {
        return id() + " phase="
            + phase
            + " formed="
            + (plant != null && plant.mMachine)
            + " progress="
            + (plant == null ? -1 : plant.getProgresstime())
            + "/"
            + (plant == null ? -1 : plant.maxProgresstime())
            + " gas="
            + amount(gas)
            + " steam="
            + amount(steam)
            + " primary="
            + amount(primary);
    }

    @Override
    public void stop(WorldServer world) {
        if (controller != null) controller.disableWorking();
        for (IGregTechTileEntity port : ports) port.disableWorking();
    }
}
