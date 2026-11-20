// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.BoosterType;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.SteamGrade;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;
import com.miaokatze.gtsr.common.machine.orecenter.CenterFixtureGeometry;
import com.miaokatze.gtsr.common.machine.orecenter.CenterFixtureGeometry.SaturationLoad;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.machine.orecenter.CenterPresets;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;
import com.miaokatze.gtsr.main.GTSteamReborn;

import appeng.util.item.AEItemStack;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBusCompressed;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBusCompressed;

/** Saturate the first shipped graph on native physical hatches, with a full next batch already stocked. */
final class TestLabCenterSaturation implements TestLabTask {

    static final String ID = "perf.ore_integration_center.saturation";
    private static final int VARIETIES = 256, CYCLES = 6;
    private static final CenterModule[] MODULES = TestLabCenterPresetLoad.MODULES.clone();
    static {
        for (int sector = 0; sector < 6; sector++)
            MODULES[sector * 6] = (sector & 1) == 0 ? CenterModule.PARALLEL : CenterModule.SPEED;
    }

    private final IGregTechTileEntity[] observed = new IGregTechTileEntity[1];
    private final List<MTEHatchInputBusCompressed> inputs = new ArrayList<>();
    private final List<MTEHatchOutputBusCompressed> outputs = new ArrayList<>();
    private final List<IGregTechTileEntity> energy = new ArrayList<>();
    private final List<MTEHatchInput> water = new ArrayList<>(), speed = new ArrayList<>(),
        parallel = new ArrayList<>();
    private final List<MTEHatchOutput> fluidOutputs = new ArrayList<>();
    private final List<Map<String, Object>> batches = new ArrayList<>();
    private final Map<String, Long> delivered = new LinkedHashMap<>();
    private List<int[]> footprint, ports;
    private List<ItemStack> ores;
    private IGregTechTileEntity controller;
    private MTEHatchInput steam;
    private Fluid steamType, speedType, parallelType;
    private MTEMineralIntegrationCenter center;
    private CenterGraph graph;
    private TestLabCenterTickSamples timing;
    private SaturationLoad load;
    private int perCycle;
    private Map<String, Long> pending;
    private Map<String, Long> pendingFluid;
    private int x, y, z, cursor, port, phase, module, phaseSince, started, finished, warmEnd;
    private long lastSequence, realEnergy, expectedEnergy, expectedSteam, expectedSpeed, expectedParallel;
    private long realSteam, realSpeed, realParallel, realWater;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "256 distinct native ores with the first six-sector preset";
    }

    @Override
    public int timeoutTicks() {
        return 10000;
    }

    @Override
    public int fixtureChunkWidth() {
        return 18;
    }

    @Override
    public void start(WorldServer world, int originX, int originY, int originZ) {
        require(world.provider instanceof TestLabWorldProvider, "Expected owned lab dimension");
        x = originX + 144;
        y = originY;
        z = originZ + 128;
        footprint = CenterFixtureGeometry.fullPreset(MODULES);
        var preset = CenterPresets.all()
            .get(0);
        require(preset.id.equals("regular-MPMC") && preset.nodeCount() == 26, "First preset changed");
        graph = preset.graph(preset.name);
        timing = new TestLabCenterTickSamples(world, observed);
        timing.start();
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int tick) {
        if (phase == 0) {
            int end = Math.min(footprint.size(), cursor + 2048);
            while (cursor < end) {
                int[] p = footprint.get(cursor++);
                TestLabFixtures.track(world, x + p[0], y + p[1], z + p[2]);
            }
            if (cursor != footprint.size()) return null;
            place(world);
            phase = 1;
        } else if (phase == 1) {
            if (!center.mMachine || center.terminalState()
                .getInteger("Sectors") != 63) return null;
            while (module < MODULES.length && MODULES[module] == null) module++;
            if (module < MODULES.length) {
                inputs.get(0)
                    .setInventorySlotContents(0, CenterModuleItems.stack(MODULES[module], 3));
                require(center.installModuleFromBus(module++), "Cannot install saturation module");
            } else {
                phase = 2;
                phaseSince = tick;
            }
        } else if (phase == 2 && tick - phaseSince >= 120) {
            require(center.setGraphs(Collections.singletonList(graph)), "Preset rejected by live controller");
            ores = CenterFixtureGeometry.presetFeed(graph, VARIETIES, center.getMaxInputVoltage());
            steamType = SteamGrade.DENSE_SUPERCRITICAL_STEAM.resolveFluid();
            speedType = MTEBasicAmplifierUnit.resolveBoosterFluid(BoosterType.SPEED);
            parallelType = MTEBasicAmplifierUnit.resolveBoosterFluid(BoosterType.PARALLEL);
            load = CenterFixtureGeometry
                .calibrateSaturation(graph, ores, MODULES, center.getMaxInputVoltage(), center.getMaxInputEu());
            perCycle = load.oresPerMaterial * VARIETIES;
            expandPorts(world);
            GTSteamReborn.LOG.info(
                "Ore center pressure calibrated {} ores with {}/{} native operations; {} ports used",
                perCycle,
                load.executions,
                load.workLimit,
                port);
            phase = 3;
            phaseSince = tick;
        } else if (phase == 3 && tick - phaseSince >= 60) {
            replenish();
            realEnergy = realSteam = realSpeed = realParallel = realWater = 0;
            stock();
            require(center.supportsBatchMode() && !center.isBatchModeEnabled(), "Invalid native batch default");
            center.setBatchMode(true);
            lastSequence = center.batchSequence();
            controller.enableWorking();
            phase = 4;
            phaseSince = tick;
        } else if (phase == 4) {
            replenish();
            if (pending != null && (center.batchProgress() == 0 || center.batchProgress() >= center.maxProgresstime()))
                settle(tick);
            if (center.batchSequence() != lastSequence) beginBatch(tick);
            if (finished == CYCLES) return report(tick);
            require(tick - phaseSince < 1200, "Saturation stalled, phase=" + debug(world));
        }
        return null;
    }

    private void place(WorldServer world) {
        ItemStack stack = GTSRItemList.OreIntegrationCenter.get(4);
        controller = TestLabFixtures.machine(world, x, y, z, stack.getItemDamage());
        controller.setFrontFacing(ForgeDirection.NORTH);
        controller.disableWorking();
        observed[0] = controller;
        center = (MTEMineralIntegrationCenter) controller.getMetaTileEntity();
        ChannelDataAccessor.setChannelData(stack, "ore_center_sectors", 6);
        center.construct(stack, false);
        ports = CenterFixtureGeometry.towerPorts();
        for (int i = 0; i < 4; i++) energy.add(placePort(world, ItemList.Hatch_Energy_UXV.get(1)));
        steam = (MTEHatchInput) placePort(world, GTSRItemList.OverpressureTurbineInputHatch.get(1)).getMetaTileEntity();
        water.add(nativeFluid(world));
        inputs.add(nativeInputBus(world));
        outputs.add(nativeOutputBus(world));
        fluidOutputs.add(nativeFluidOutput(world));
    }

    private void expandPorts(WorldServer world) {
        Map<Fluid, Long> consumption = new LinkedHashMap<>();
        for (FluidStack spent : load.batch.consumedFluids)
            consumption.merge(spent.getFluid(), (long) spent.amount, Long::sum);
        for (var spent : consumption.entrySet()) {
            List<MTEHatchInput> bank = spent.getKey() == speedType ? speed
                : spent.getKey() == parallelType ? parallel : water;
            require(bank != water || spent.getKey() == FluidRegistry.WATER, "Unexpected calibration ingredient");
            long capacity = bank.stream()
                .mapToLong(MTEHatchInput::getCapacity)
                .sum();
            while (capacity < spent.getValue() * 1.06) {
                MTEHatchInput hatch = nativeFluid(world);
                bank.add(hatch);
                capacity += hatch.getCapacity();
            }
        }
        long slots = outputs.get(0)
            .getAEInventory()
            .getSlots();
        long needed = 0;
        for (ItemStack item : load.batch.outputs) {
            long limit = outputs.get(0)
                .getAEInventory()
                .getAESlotLimit(0, AEItemStack.create(item));
            require(limit > 0, "Compressed output has no capacity for " + key(item));
            needed += 1 + ((long) Math.ceil(item.stackSize * 1.08) - 1) / limit;
        }
        while (slots < needed + 24) {
            MTEHatchOutputBusCompressed next = nativeOutputBus(world);
            outputs.add(next);
            slots += next.getAEInventory()
                .getSlots();
        }
        int tanks = 1;
        int fluidCapacity = fluidOutputs.get(0)
            .getCapacity();
        for (FluidStack fluid : load.batch.outputFluids)
            tanks += (int) Math.ceil(fluid.amount * 1.12 / fluidCapacity) + 1;
        while (fluidOutputs.size() < tanks) fluidOutputs.add(nativeFluidOutput(world));

        int inputSlots = inputs.get(0)
            .getAEInventory()
            .getSlots();
        while (inputSlots < VARIETIES) {
            MTEHatchInputBusCompressed next = nativeInputBus(world);
            inputs.add(next);
            inputSlots += next.getAEInventory()
                .getSlots();
        }
    }

    private MTEHatchInputBusCompressed nativeInputBus(WorldServer world) {
        return (MTEHatchInputBusCompressed) placePort(world, ItemList.CompressedInputBusUXV.get(1)).getMetaTileEntity();
    }

    private MTEHatchOutputBusCompressed nativeOutputBus(WorldServer world) {
        return (MTEHatchOutputBusCompressed) placePort(world, ItemList.CompressedOutputBusUXV.get(1))
            .getMetaTileEntity();
    }

    private MTEHatchOutput nativeFluidOutput(WorldServer world) {
        return (MTEHatchOutput) placePort(world, ItemList.Hatch_Output_UXV.get(1)).getMetaTileEntity();
    }

    private MTEHatchInput nativeFluid(WorldServer world) {
        return (MTEHatchInput) placePort(world, ItemList.Hatch_Input_UXV.get(1)).getMetaTileEntity();
    }

    private IGregTechTileEntity placePort(WorldServer world, ItemStack stack) {
        require(port < ports.size(), "No legal tower ports left");
        int[] p = ports.get(port++);
        return TestLabFixtures.machine(world, x + p[0], y + p[1], z + p[2], stack.getItemDamage());
    }

    private void stock() {
        int placed = 0;
        for (MTEHatchInputBusCompressed bus : inputs) {
            var inventory = bus.getAEInventory();
            for (int slot = 0; slot < inventory.getSlots() && placed < VARIETIES; slot++) {
                var item = AEItemStack.create(ores.get(placed++))
                    .setStackSize(load.oresPerMaterial);
                require(
                    inventory.insertAEItem(slot, item, false, false) == null,
                    "Compressed input rejected test supply");
            }
        }
        require(placed == VARIETIES, "Input buses held only " + placed + " ore types");
    }

    private void beginBatch(int tick) {
        require(pending == null && center.batchSequence() == lastSequence + 1, "Overlapping or skipped batch");
        lastSequence++;
        started++;
        long residue = 0;
        Map<String, Long> residueByOre = new LinkedHashMap<>();
        for (MTEHatchInputBusCompressed bus : inputs) {
            var inventory = bus.getAEInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                var stack = inventory.getAEStackInSlot(slot);
                if (stack != null) {
                    residue += stack.getStackSize();
                    residueByOre.merge(key(stack.getItemStack()), stack.getStackSize(), Long::sum);
                }
            }
        }
        require(
            residue == 0,
            "Only " + (perCycle - residue)
                + " ores accepted in the proposed saturated batch; "
                + residueByOre.size()
                + " identities left e.g. "
                + residueByOre.entrySet()
                    .stream()
                    .limit(8)
                    .collect(java.util.stream.Collectors.toList())
                + "; pending fluid "
                + Arrays.toString(center.mOutputFluids));
        NBTTagCompound batch = new NBTTagCompound();
        center.saveNBTData(batch);
        require(batch.getBoolean("batchMode"), "Native batch setting did not persist in the controller journal");
        require(center.maxProgresstime() <= 128, "Combined recipe exceeded native Batch Mode's 128 tick window");
        require(
            batch.getInteger("CenterSteamKind") == SteamGrade.DENSE_SUPERCRITICAL_STEAM.ordinal(),
            "Wrong paid steam type");
        long density = SteamGrade.DENSE_SUPERCRITICAL_STEAM.getDivisor();
        expectedEnergy += batch.getLong("CenterEnergy");
        expectedSteam += (batch.getLong("CenterSteam") + density - 1) / density;
        int[] additives = batch.getIntArray("CenterBoostFluids");
        require(additives[0] == perCycle * 135 && additives[1] == perCycle * 108, "Booster levels were not fully paid");
        expectedSpeed += additives[0];
        expectedParallel += additives[1];
        pending = new LinkedHashMap<>();
        require(center.mOutputItems != null, "Paid batch had no expected items");
        for (ItemStack item : center.mOutputItems) pending.merge(key(item), (long) item.stackSize, Long::sum);
        pendingFluid = new LinkedHashMap<>();
        if (center.mOutputFluids != null) for (FluidStack fluid : center.mOutputFluids) pendingFluid.merge(
            fluid.getFluid()
                .getName(),
            (long) fluid.amount,
            Long::sum);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("acceptedTick", tick);
        info.put("recipeTicks", center.maxProgresstime());
        info.put("rawOre", perCycle);
        batches.add(info);
        GTSteamReborn.LOG.info(
            "Ore center saturated batch {} accepted at tick {} for {} ticks",
            started,
            tick,
            center.maxProgresstime());
        if (started < CYCLES) stock();
        phaseSince = tick;
    }

    private void settle(int tick) {
        Map<String, Long> items = new LinkedHashMap<>();
        for (MTEHatchOutputBusCompressed bus : outputs) {
            var inventory = bus.getAEInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                var item = inventory.getAEStackInSlot(slot);
                if (item == null) continue;
                var extracted = inventory.extractAEItem(slot, item.getStackSize(), false, true);
                items.merge(key(extracted.getItemStack()), extracted.getStackSize(), Long::sum);
            }
        }
        if (items.isEmpty()) return;
        require(items.equals(pending), "Incomplete physical batch output");
        Map<String, Long> fluids = new LinkedHashMap<>();
        for (MTEHatchOutput hatch : fluidOutputs) {
            FluidStack stack = hatch.getFluid();
            if (stack == null) continue;
            fluids.merge(
                stack.getFluid()
                    .getName(),
                (long) stack.amount,
                Long::sum);
            hatch.setFillableStack(null);
        }
        require(fluids.equals(pendingFluid), "Incomplete physical fluid output");
        items.forEach((key, amount) -> delivered.merge(key, amount, Long::sum));
        pending = null;
        batches.get(finished++)
            .put("deliveredTick", tick);
        if (finished == 1) {
            warmEnd = tick;
            timing.beginWindow(1, timeoutTicks());
        }
        phaseSince = tick;
    }

    private void replenish() {
        for (IGregTechTileEntity hatch : energy) {
            long charge = hatch.getEUCapacity() - hatch.getStoredEU();
            if (charge > 0)
                require(hatch.increaseStoredEnergyUnits(charge, false), "Native energy hatch did not refill");
            realEnergy += charge;
        }
        realSteam += fill(steam, steamType);
        for (MTEHatchInput hatch : speed) realSpeed += fill(hatch, speedType);
        for (MTEHatchInput hatch : parallel) realParallel += fill(hatch, parallelType);
        for (MTEHatchInput hatch : water) realWater += fill(hatch, FluidRegistry.WATER);
    }

    private static int fill(MTEHatchInput tank, Fluid fluid) {
        FluidStack stock = tank.getFluid();
        int need = tank.getCapacity() - (stock == null ? 0 : stock.amount);
        if (need > 0) require(
            tank.fill(ForgeDirection.UNKNOWN, new FluidStack(fluid, need), true) == need,
            "Native ingredient tank did not refill: " + fluid.getName());
        return need;
    }

    private TestLabReport.Result report(int tick) {
        require(
            realEnergy == expectedEnergy && realSteam == expectedSteam && realWater > 0,
            "Native EU, steam or water receipt is inconsistent");
        require(realSpeed == expectedSpeed && realParallel == expectedParallel, "Boost stock does not match receipts");
        Map<String, Long> debits = new LinkedHashMap<>(), output = new LinkedHashMap<>();
        NBTTagList rows = center.terminalLedger();
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            String item = key(ItemStack.loadItemStackFromNBT(row.getCompoundTag("Item")));
            if (row.getLong("C0") != 0) debits.put(item, row.getLong("C0"));
            if (row.getLong("C1") != 0) output.put(item, row.getLong("C1"));
        }
        require(
            debits.size() == VARIETIES && debits.values()
                .stream()
                .allMatch(count -> count == CYCLES * (long) load.oresPerMaterial),
            "Ledger lost a mixed input identity or quantity: " + debits.size());
        require(delivered.equals(output), "Ledger delivery differs from physical stock");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("preset", "regular-MPMC");
        details.put("oreIdentities", debits.size());
        details.put(
            "oreMaterials",
            ores.stream()
                .map(CenterFixtureGeometry::oreMaterial)
                .distinct()
                .count());
        details.put("oreTotal", perCycle * CYCLES);
        details.put("orePerMaterialPerBatch", load.oresPerMaterial);
        details.put("calibrationWork", load.executions);
        details.put("workLimit", load.workLimit);
        details.put("firstRejectedOrePerMaterial", load.rejectedOresPerMaterial);
        details.put("inputItemBusses", inputs.size());
        details.put("outputItemBusses", outputs.size());
        details.put("physicalHatches", port);
        details.put("inputEuPerTick", center.getMaxInputEu());
        details.put("nativeBatchMode", center.isBatchModeEnabled());
        details.put("sustainedOrePerTick", perCycle * (CYCLES - 1.0) / (tick - warmEnd));
        details.put("multipleOfLegacyUpperBound", perCycle * (CYCLES - 1.0) * 20 / (tick - warmEnd) / 4096);
        details.put("batches", batches);
        details.put("spent", Arrays.asList(realEnergy, realSteam, realWater, realSpeed, realParallel));
        details.put("timing", timing.finishWindow(Collections.singletonList(new LinkedHashMap<>())));
        boolean reachedTarget = ((Number) details.get("multipleOfLegacyUpperBound")).doubleValue() >= 200;
        return new TestLabReport.Result(
            reachedTarget ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL,
            reachedTarget ? "Native mixed-ore throughput reached 200x the legacy upper bound"
                : "Native mixed-ore throughput below 200x the legacy upper bound",
            details);
    }

    private static String key(ItemStack item) {
        return Item.itemRegistry.getNameForObject(item.getItem()) + "/" + item.getItemDamage();
    }

    @Override
    public void stop(WorldServer world) {
        if (timing != null) timing.stop();
        if (controller == null) return;
        controller.disableWorking();
        for (int i = 0; i < module; i++) if (MODULES[i] != null) CenterFixtureGeometry.release(controller, i);
    }

    @Override
    public String debug(WorldServer world) {
        return "phase=" + phase
            + " module="
            + module
            + " started="
            + started
            + " delivered="
            + finished
            + " progress="
            + (center == null ? 0 : center.batchProgress())
            + " formed="
            + (center != null && center.mMachine)
            + " power="
            + (center == null ? 0 : center.getMaxInputEu())
            + " enabled="
            + (controller != null && controller.isAllowedToWork())
            + " batch="
            + (center != null && center.isBatchModeEnabled());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
