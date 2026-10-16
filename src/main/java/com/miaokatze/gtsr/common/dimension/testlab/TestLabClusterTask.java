package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterItemForms;
import com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitSelfPoweredProcessingUnit;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/**
 * Each registered cluster ID gets its own reproducible real-world production fixture. StructureLib supplies all
 * geometry, actual structure scans bind the units, and ordinary server ticks provide heat and recipe progress.
 * A missing suitable recipe is UNSUPPORTED after formation, never a production PASS.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class TestLabClusterTask implements TestLabTask {

    private final MetaTileEntityID requested;
    private final ChainLink link;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Spec> specs = new ArrayList<>();
    private final List<IGregTechTileEntity> feeds = new ArrayList<>();
    private MTESteamMineralLogisticsCluster cluster;
    private MTEBasicLogisticsUnit logistics;
    private MTEBasicAmplifierUnit booster;
    private MTEHatchInputBus inputBus;
    private MTEHatchOutputBus outputBus;
    private MTEHatchInput boosterHatch;
    private int boosterInitial;
    private TestLabStructure building;
    private Node current;
    private int specIndex;
    private String phase = "NEW";
    private Iterator<GTRecipe> recipes;
    private ItemStack input;
    private ItemStack expected;
    private FluidStack recipeFluid;
    private IGregTechTileEntity logisticsFluid;
    private IGregTechTileEntity steamFeed;
    private IGregTechTileEntity lubricantFeed;
    private IGregTechTileEntity energyFeed;
    private long initialEnergy;
    private final List<IGregTechTileEntity> energyFeeds = new ArrayList<>();
    private final List<java.util.Map<String, Object>> energyFixtureActions = new ArrayList<>();
    private final java.util.Map<String, Long> initialEnergyByPort = new java.util.LinkedHashMap<>();
    private boolean recipeHit;
    private boolean sawRunning;
    private boolean sawColdInputPreserved;
    private int productionStart;
    private int scannedRecipes;
    private String formation = "pending";
    private boolean existingLayout;
    private boolean existingWarm;
    private double loadedHeatPercent;
    private boolean loadedPreheatReady;
    private long initialProcessed;
    private int initialOutputCount;
    private boolean thermalFixtureIssued;
    private int directThermalWrites;
    private final java.util.Map<String, Object> thermalFixture = new java.util.LinkedHashMap<>();
    private final List<String> beforeThermalFixtureSnapshots = new ArrayList<>();

    public TestLabClusterTask(MetaTileEntityID requested) {
        if (!requested.name()
            .startsWith("CLUSTER_")) throw new IllegalArgumentException("Not a cluster ID: " + requested);
        this.requested = requested;
        link = linkFor(requested);
    }

    @Override
    public String id() {
        return requested.name()
            .toLowerCase(Locale.ROOT);
    }

    @Override
    public String displayName() {
        return requested + " 原生结构、绑定、自然预热与真实配方产出";
    }

    @Override
    public int timeoutTicks() {
        return 4800;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        nodes.clear();
        specs.clear();
        feeds.clear();
        specIndex = 0;
        scannedRecipes = 0;
        recipeHit = false;
        sawRunning = false;
        sawColdInputPreserved = false;
        input = null;
        expected = null;
        recipeFluid = null;
        booster = null;
        boosterHatch = null;
        logistics = null;
        logisticsFluid = null;
        steamFeed = null;
        lubricantFeed = null;
        recipes = null;
        inputBus = null;
        outputBus = null;
        energyFeed = null;
        initialEnergy = 0L;
        energyFeeds.clear();
        energyFixtureActions.clear();
        initialEnergyByPort.clear();
        formation = "pending";
        // Runtime reserves positive x/z chunks from this station's origin.
        IGregTechTileEntity tile = TestLabFixtures
            .machine(world, x + 48, y + 16, z + 16, MetaTileEntityID.CLUSTER_CONTROLLER.ID);
        cluster = (MTESteamMineralLogisticsCluster) tile.getMetaTileEntity();
        current = new Node(MetaTileEntityID.CLUSTER_CONTROLLER, tile);
        nodes.add(current);
        building = new TestLabStructure(world, current.machine, trigger());
        if (link != ChainLink.FURNACE) building.piece(
            ClusterStructureDef.PIECE_EXT,
            ClusterStructureDef.extOffsetA(0),
            ClusterStructureDef.extOffsetB(),
            ClusterStructureDef.extOffsetC(0));
        MetaTileEntityID processing = isWorking(requested) ? requested
            : link == ChainLink.CRUSH ? MetaTileEntityID.CLUSTER_UNIT_CRUSHER : MetaTileEntityID.CLUSTER_UNIT_FURNACE;
        specs.add(new Spec(processing, 'F', 0));
        if (link != ChainLink.FURNACE) specs.add(new Spec(MetaTileEntityID.CLUSTER_UNIT_FURNACE, 'F', 1));
        specs.add(new Spec(MetaTileEntityID.CLUSTER_UNIT_LOGISTICS, 'G', 0));
        if (requested.name()
            .startsWith("CLUSTER_BOOSTER_")) specs.add(new Spec(requested, 'H', 0));
        phase = "BUILD";
    }

    /** Resume the actual saved topology; no pad, unit, casing or port is installed. */
    void resumeExisting(WorldServer world, IGregTechTileEntity existing, List<IGregTechTileEntity> existingTiles) {
        cluster = (MTESteamMineralLogisticsCluster) existing.getMetaTileEntity();
        existingLayout = true;
        loadedHeatPercent = cluster.getPreheatProgress();
        loadedPreheatReady = cluster.isPreheatReady();
        // A loaded 99.95%-warm controller can reach full heat before the probe's first sample.
        // It has no <95% cold-gate observation window; classify that check as NOT_RUN instead of inventing a PASS.
        existingWarm = loadedHeatPercent >= 95D;
        initialProcessed = cluster.getTotalProcessedOre();
        for (IGregTechTileEntity tile : existingTiles) {
            if (!(tile.getMetaTileEntity() instanceof MTEMultiBlockBase)) continue;
            MetaTileEntityID registered = null;
            for (MetaTileEntityID id : MetaTileEntityID.values()) if (id.ID == tile.getMetaTileID()) registered = id;
            if (registered == null)
                throw new IllegalStateException("Unknown saved cluster controller " + tile.getMetaTileID());
            Node node = new Node(registered, tile);
            nodes.add(node);
            if (node.machine instanceof MTEBasicLogisticsUnit) {
                logistics = (MTEBasicLogisticsUnit) node.machine;
                if (logistics.mInputBusses.isEmpty() || logistics.mOutputBusses.isEmpty()
                    || logistics.mInputHatches.isEmpty())
                    throw new IllegalStateException("Saved logistics ports missing");
                inputBus = logistics.mInputBusses.get(0);
                outputBus = logistics.mOutputBusses.get(0);
                logisticsFluid = logistics.mInputHatches.get(0)
                    .getBaseMetaTileEntity();
            } else if (node.machine instanceof MTEBasicAmplifierUnit) {
                booster = (MTEBasicAmplifierUnit) node.machine;
                if (booster.mInputHatches.isEmpty()) throw new IllegalStateException("Saved amplifier input missing");
                boosterHatch = booster.mInputHatches.get(0);
                boosterHatch.fill(
                    ForgeDirection.UNKNOWN,
                    new FluidStack(booster.getBoosterFluidForAccess(), Integer.MAX_VALUE),
                    true);
                boosterInitial = boosterHatch.getFluidAmount();
            } else if (node.machine instanceof MTEUnitSelfPoweredProcessingUnit) {
                if (node.machine.mEnergyHatches.isEmpty())
                    throw new IllegalStateException("Saved powered-unit energy port missing");
                energyFeed = node.machine.mEnergyHatches.get(0)
                    .getBaseMetaTileEntity();
                primeEnergyWithoutOverflow(node.machine, energyFeed);
            }
        }
        if (logistics == null || cluster.mInputHatches.size() < 2)
            throw new IllegalStateException("Saved cluster supply/logistics topology incomplete");
        for (MTEHatchInput hatch : cluster.mInputHatches) {
            FluidStack fluid = hatch.getFluid();
            if (fluid != null && fluid.isFluidEqual(Materials.Steam.getGas(1L)))
                steamFeed = hatch.getBaseMetaTileEntity();
            if (fluid != null && fluid.isFluidEqual(Materials.Lubricant.getFluid(1L)))
                lubricantFeed = hatch.getBaseMetaTileEntity();
        }
        if (steamFeed == null || lubricantFeed == null) throw new IllegalStateException(
            "Saved cluster steam/lubricant identity unavailable; refusing speculative remapping");
        supply(world, steamFeed, Materials.Steam.getGas(1L));
        supply(world, lubricantFeed, Materials.Lubricant.getFluid(1L));
        formation = "PASS existing_controllers=" + nodes.size();
        recipes = link.getRecipeMap()
            .getAllRecipes()
            .iterator();
        phase = "RECIPE";
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        if ("BUILD".equals(phase)) {
            if (!building.buildTick()) return null;
            current.geometry = building.describe();
            configurePorts(world, current);
            if (current.machine instanceof MTEClusterUnitBase
                && !TestLabStructure.checkStructure(current.machine, current.tile))
                return result(
                    TestLabReport.Status.FAIL,
                    "单元原生checkStructure未成型：" + current.id + "; " + building.diagnoseFailure());
            if (specIndex < specs.size()) {
                Spec spec = specs.get(specIndex++);
                IGregTechTileEntity tile = TestLabClusterLayout.mount(world, cluster, spec.id, spec.pad, spec.segment);
                current = new Node(spec.id, tile);
                nodes.add(current);
                building = new TestLabStructure(world, current.machine, trigger());
                return null;
            }
            if (!TestLabStructure.checkStructure(cluster, cluster.getBaseMetaTileEntity()))
                return result(TestLabReport.Status.FAIL, "总控原生checkStructure未成型");
            for (Node node : nodes) if (node.machine instanceof MTEClusterUnitBase) {
                MTEClusterUnitBase unit = (MTEClusterUnitBase) node.machine;
                if (!unit.isUnitStructureFormed() || unit.getCluster() != cluster
                    || !unit.isTierValidForConnection()
                    || !cluster.getTopology()
                        .getUnits()
                        .contains(unit))
                    return result(TestLabReport.Status.FAIL, "成型/双向绑定/tier不一致：" + node.id);
            }
            formation = "PASS controllers=" + nodes.size()
                + " segments="
                + cluster.getTopology()
                    .getSegmentCount();
            LogisticsChain chain = new LogisticsChain();
            chain.append(link);
            if (link != ChainLink.FURNACE) chain.append(ChainLink.FURNACE);
            logistics.setChain(chain);
            if (!chain.isValidStructure()) return result(TestLabReport.Status.FAIL, "测试链不满足原生FSM终态契约");
            recipes = link.getRecipeMap()
                .getAllRecipes()
                .iterator();
            phase = "RECIPE";
            return null;
        }
        if ("RECIPE".equals(phase)) {
            // Canonical furnace fixture is fixed; remaining maps are searched with a bounded per-tick budget.
            if (link == ChainLink.FURNACE) {
                ItemStack iron = GTOreDictUnificator.get(OrePrefixes.dust, Materials.Iron, 1L);
                if (iron != null) selectRecipe(
                    link.getRecipeMap()
                        .findRecipeQuery()
                        .items(iron)
                        .find());
            } else for (int budget = 0; budget < 128 && recipes.hasNext() && input == null; budget++) {
                scannedRecipes++;
                selectRecipe(recipes.next());
            }
            if (input == null) {
                if (link == ChainLink.FURNACE || !recipes.hasNext())
                    return result(TestLabReport.Status.UNSUPPORTED, "成型与绑定通过；当前配方图无适合确定产物的单输入测试配方；未验证生产");
                return null;
            }
            if (recipeFluid != null) supply(world, logisticsFluid, recipeFluid);
            for (Node node : nodes) node.tile.enableWorking();
            cluster.setMachineEnabled(true);
            // Feed before natural preheat, explicitly checking that the low-heat gate does not consume it.
            captureThermalBaseline();
            beginEnergyObservation();
            initialOutputCount = count(outputBus, expected);
            inputBus.setInventorySlotContents(0, input.copy());
            productionStart = elapsedTicks;
            phase = "RUN";
            return null;
        }
        if ("RUN".equals(phase)) {
            if (!cluster.isClusterStructureFormed()) return result(TestLabReport.Status.FAIL, "运行期间总控失去成型");
            for (Node node : nodes) if (node.machine instanceof MTEClusterUnitBase) {
                MTEClusterUnitBase unit = (MTEClusterUnitBase) node.machine;
                if (!unit.isUnitStructureFormed() || unit.getCluster() != cluster)
                    return result(TestLabReport.Status.FAIL, "运行期间单元成型/连接丢失：" + node.id);
            }
            if (!cluster.isPreheatReady() && cluster.getPreheatProgress() < 95D && elapsedTicks > productionStart + 2) {
                if (count(inputBus, input) != input.stackSize || cluster.getTotalProcessedOre() != initialProcessed
                    || count(outputBus, expected) != 0) return result(TestLabReport.Status.FAIL, "低温门控失败：预热期间吞料/输出/记账");
                sawColdInputPreserved = true;
                if (!thermalFixture.containsKey("coldGateNaturalCheck")) {
                    java.util.Map<String, Object> check = new java.util.LinkedHashMap<>();
                    check.put("elapsedTicksAfterInputSeed", elapsedTicks - productionStart);
                    check.put("heatPercentObserved", cluster.getPreheatProgress());
                    check.put("inputRemaining", count(inputBus, input));
                    check.put("expectedInputRemaining", input.stackSize);
                    check.put("processedDelta", cluster.getTotalProcessedOre() - initialProcessed);
                    check.put("outputObserved", count(outputBus, expected));
                    thermalFixture.put("coldGateNaturalCheck", check);
                }
            }
            if (!thermalFixtureIssued && elapsedTicks > productionStart + 2
                && (sawColdInputPreserved || existingLayout && existingWarm)) acceleratePreheat();
            sawRunning |= logistics.isWorkInProgress();
            recipeHit |= lastBatchLinks().contains(link);
            if (count(outputBus, expected) - initialOutputCount >= 1) {
                if (!cluster.isPreheatReady() || !(sawColdInputPreserved || existingLayout && existingWarm)
                    || !recipeHit
                    || !sawRunning
                    || count(inputBus, input) != 0
                    || cluster.getTotalProcessedOre() - initialProcessed < 1
                    || logistics.hasPendingOutputs())
                    return result(TestLabReport.Status.FAIL, "已见产物但预热/真实配方命中/运行进度/扣料/排空证据不完整");
                if (TestLabSupply.replenished(steamFeed) <= 0L || TestLabSupply.replenished(lubricantFeed) <= 0L)
                    return result(TestLabReport.Status.FAIL, "已见产物但未观测到真实蒸汽/润滑消耗");
                if (!energyFeeds.isEmpty() && totalUncappedEnergy() >= initialEnergy)
                    return result(TestLabReport.Status.FAIL, "需持续供电链步未观察到能源仓真实EU扣除");
                if (booster != null && (booster.isLastBatchPrecheckFailed()
                    || boosterInitial - boosterHatch.getFluidAmount() != booster.batchCost(input.stackSize)))
                    return result(TestLabReport.Status.FAIL, "增幅液未按原生批单价真实扣除");
                return result(
                    TestLabReport.Status.PASS,
                    "formation=" + formation
                        + " binding=PASS coldGate="
                        + coldGateStatus()
                        + " preheat=traceable_fixture_acceleration directThermalWrites="
                        + directThermalWrites
                        + " recipeHit="
                        + link
                        + " steamConsumed="
                        + TestLabSupply.replenished(steamFeed)
                        + " lubricantConsumed="
                        + TestLabSupply.replenished(lubricantFeed)
                        + " inputConsumed="
                        + input.stackSize
                        + " observedOutput="
                        + count(outputBus, expected)
                        + " output="
                        + expected.getDisplayName()
                        + " pending=empty"
                        + (booster == null ? "" : " boosterPaid=" + (boosterInitial - boosterHatch.getFluidAmount()))
                        + "; 仅验证本配方与基础档，不代表所有链/档位/概率增益/断供回滚已覆盖");
            }
            if (elapsedTicks - productionStart > 2400)
                return result(TestLabReport.Status.FAIL, "可追溯预热fixture后的自然配方生产超时（不强写成型/进度）");
        }
        return null;
    }

    private void configurePorts(WorldServer world, Node node) throws Exception {
        if (node.machine == cluster) {
            steamFeed = requiredPort(ItemList.Hatch_Input_HV.get(1L));
            lubricantFeed = requiredPort(ItemList.Hatch_Input_HV.get(1L));
            supply(world, steamFeed, Materials.Steam.getGas(1L));
            supply(world, lubricantFeed, Materials.Lubricant.getFluid(1L));
        } else if (node.machine instanceof MTEBasicLogisticsUnit) {
            logistics = (MTEBasicLogisticsUnit) node.machine;
            inputBus = (MTEHatchInputBus) requiredPort(ItemList.Hatch_Input_Bus_HV.get(1L)).getMetaTileEntity();
            outputBus = (MTEHatchOutputBus) requiredPort(ItemList.Hatch_Output_Bus_HV.get(1L)).getMetaTileEntity();
            logisticsFluid = requiredPort(ItemList.Hatch_Input_HV.get(1L));
            requiredPort(ItemList.Hatch_Output_HV.get(1L));
        } else if (node.machine instanceof MTEBasicAmplifierUnit) {
            booster = (MTEBasicAmplifierUnit) node.machine;
            boosterHatch = (MTEHatchInput) requiredPort(ItemList.Hatch_Input_HV.get(1L)).getMetaTileEntity();
            if (booster.getBoosterFluidForAccess() == null)
                throw new IllegalStateException("Missing registered booster fluid " + requested);
            boosterHatch.fill(
                ForgeDirection.UNKNOWN,
                new FluidStack(booster.getBoosterFluidForAccess(), Integer.MAX_VALUE),
                true);
            boosterInitial = boosterHatch.getFluidAmount();
            // One finite full hatch deliberately makes actual batch payment observable; it is not replenished.
        } else if (node.machine instanceof MTEUnitSelfPoweredProcessingUnit) {
            ItemStack energyHatch = ItemList.Hatch_Energy_IV.get(1L);
            if (energyHatch == null) throw new IllegalStateException("Missing registered IV fixture energy hatch");
            energyFeed = TestLabClusterLayout
                .energyPort(world, (MTEClusterUnitBase) node.machine, energyHatch.getItemDamage());
            primeEnergyWithoutOverflow(node.machine, energyFeed);
        }
    }

    /** GT's capped getStoredEU() conceals excess raw EU: add only the capacity gap and observe the real stored pool. */
    private void primeEnergyWithoutOverflow(MTEMultiBlockBase poweredUnit, IGregTechTileEntity selectedPort) {
        for (gregtech.api.metatileentity.implementations.MTEHatchEnergy hatch : poweredUnit.mEnergyHatches) {
            IGregTechTileEntity port = hatch.getBaseMetaTileEntity();
            if (!energyFeeds.contains(port)) energyFeeds.add(port);
        }
        if (!energyFeeds.contains(selectedPort)) energyFeeds.add(selectedPort);
        long before = uncappedEnergy(selectedPort);
        long target = Math.min(1_000_000L, selectedPort.getEUCapacity());
        long requested = Math.max(0L, target - before);
        if (requested > 0) selectedPort.increaseStoredEnergyUnits(requested, false);
        java.util.Map<String, Object> action = new java.util.LinkedHashMap<>();
        action.put("position", energyPosition(selectedPort));
        action.put("capacity", selectedPort.getEUCapacity());
        action.put("uncappedBeforeFixture", before);
        action.put("requestedTarget", target);
        action.put("requestedAddedEu", requested);
        action.put("uncappedAfterFixture", uncappedEnergy(selectedPort));
        action.put("actualAddedEu", uncappedEnergy(selectedPort) - before);
        action.put("cappedAfterFixture", selectedPort.getStoredEU());
        action
            .put("scope", "Isolated test stock; preserve existing over-capacity stock, never refill during production");
        energyFixtureActions.add(action);
    }

    private void beginEnergyObservation() {
        initialEnergy = totalUncappedEnergy();
        initialEnergyByPort.clear();
        for (IGregTechTileEntity port : energyFeeds)
            initialEnergyByPort.put(energyPosition(port), uncappedEnergy(port));
    }

    private static long uncappedEnergy(IGregTechTileEntity port) {
        if (!(port instanceof BaseMetaTileEntity)) throw new IllegalStateException("Expected real GT energy tile");
        return ((BaseMetaTileEntity) port).getStoredEUuncapped();
    }

    private long totalUncappedEnergy() {
        long result = 0L;
        for (IGregTechTileEntity port : energyFeeds) result = Math.addExact(result, uncappedEnergy(port));
        return result;
    }

    private static String energyPosition(IGregTechTileEntity port) {
        return port.getXCoord() + "," + port.getYCoord() + "," + port.getZCoord();
    }

    private java.util.Map<String, Object> energyEvidence() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put(
            "measurement",
            "sum of actual energy hatches BaseMetaTileEntity.getStoredEUuncapped(); capped getter is diagnostic only");
        result.put("fixtureActions", new ArrayList<>(energyFixtureActions));
        result.put("uncappedBaselineTotal", initialEnergy);
        result.put("uncappedCurrentTotal", totalUncappedEnergy());
        result.put("actualEuConsumed", initialEnergy - totalUncappedEnergy());
        List<java.util.Map<String, Object>> ports = new ArrayList<>();
        for (IGregTechTileEntity port : energyFeeds) {
            java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
            state.put("position", energyPosition(port));
            state.put("uncappedBaseline", initialEnergyByPort.get(energyPosition(port)));
            state.put("uncappedCurrent", uncappedEnergy(port));
            state.put("cappedCurrent", port.getStoredEU());
            state.put("capacity", port.getEUCapacity());
            ports.add(state);
        }
        result.put("actualEnergyHatches", ports);
        return result;
    }

    private IGregTechTileEntity requiredPort(ItemStack stack) throws Exception {
        if (stack == null) throw new IllegalStateException("Missing registered GT fixture port");
        IGregTechTileEntity hatch = building.install(stack.getItemDamage());
        if (hatch == null) throw new IllegalStateException(
            "Native structure accepts no required port " + stack.getDisplayName() + " on " + current.id);
        hatch.enableWorking();
        return hatch;
    }

    private void supply(WorldServer world, IGregTechTileEntity tile, FluidStack fluid) {
        if (fluid == null) throw new IllegalStateException("Missing registered fixture supply fluid");
        TestLabSupply.add(world, tile, fluid);
        feeds.add(tile);
    }

    private void selectRecipe(GTRecipe candidate) {
        if (candidate == null || candidate.mInputs == null
            || candidate.mInputs.length != 1
            || candidate.mInputs[0] == null
            || candidate.mInputs[0].stackSize != 1
            || candidate.mOutputs == null
            || candidate.mOutputs.length == 0
            || candidate.mFluidInputs != null && candidate.mFluidInputs.length > 1) return;
        ItemStack selectedInput = candidate.mInputs[0].copy();
        if (!accepts(ClusterItemForms.classify(selectedInput))) return;
        FluidStack fluid = candidate.mFluidInputs == null || candidate.mFluidInputs.length == 0 ? null
            : candidate.mFluidInputs[0];
        if (fluid != null && link != ChainLink.ORE_WASH) return;
        // The real executor uses water for ORE_WASH. Never feed a different map-only fluid signal.
        if (link == ChainLink.ORE_WASH) fluid = Materials.Water.getFluid(Integer.MAX_VALUE);
        GTRecipe resolved = fluid == null ? link.getRecipeMap()
            .findRecipeQuery()
            .items(selectedInput)
            .find()
            : link.getRecipeMap()
                .findRecipeQuery()
                .items(selectedInput)
                .fluids(fluid)
                .find();
        if (resolved == null || resolved.mInputs.length != 1 || resolved.mInputs[0].stackSize != 1) return;
        for (int i = 0; i < resolved.mOutputs.length; i++) {
            if (resolved.getOutputChance(i) < 10000 || link == ChainLink.CRUSH && i > 0) continue;
            ItemStack product = resolved.getOutput(i);
            if (product == null) continue;
            if (link != ChainLink.FURNACE && ClusterItemForms.classify(product) != ClusterItemForms.OreForm.OTHER) {
                GTRecipe smelt = ChainLink.FURNACE.getRecipeMap()
                    .findRecipeQuery()
                    .items(product.copy())
                    .find();
                if (smelt != null && smelt.mOutputs.length > 0 && smelt.getOutputChance(0) >= 10000)
                    product = smelt.getOutput(0);
            }
            if (product == null || GTUtility.areStacksEqual(selectedInput, product)) continue;
            input = selectedInput;
            expected = product.copy();
            expected.stackSize = 1;
            recipeFluid = fluid;
            return;
        }
    }

    private boolean accepts(ClusterItemForms.OreForm form) {
        switch (link) {
            case CRUSH:
                return form == ClusterItemForms.OreForm.ORE;
            case ORE_WASH:
                return form == ClusterItemForms.OreForm.CRUSHED;
            case CENTRIFUGE:
                return form == ClusterItemForms.OreForm.DUST_IMPURE || form == ClusterItemForms.OreForm.DUST_PURE;
            case THERMOCENTRIFUGE:
                return form == ClusterItemForms.OreForm.CRUSHED || form == ClusterItemForms.OreForm.CRUSHED_PURIFIED;
            case SIFTER:
                return form == ClusterItemForms.OreForm.CRUSHED_PURIFIED;
            case MAGNETIC_SEPARATOR:
                return form == ClusterItemForms.OreForm.DUST_PURE;
            case FURNACE:
                return form != ClusterItemForms.OreForm.OTHER;
            default:
                return false;
        }
    }

    private Set<ChainLink> lastBatchLinks() throws Exception {
        // Observation only. The machine intentionally keeps this execution evidence package-private.
        Method method = MTESteamMineralLogisticsCluster.class.getDeclaredMethod("getLastBatchLinks");
        method.setAccessible(true);
        return (Set<ChainLink>) method.invoke(cluster);
    }

    private static int count(gregtech.api.interfaces.metatileentity.IMetaTileEntity inventory, ItemStack template) {
        int result = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null && GTUtility.areStacksEqual(stack, template)) result += stack.stackSize;
        }
        return result;
    }

    private TestLabReport.Result result(TestLabReport.Status status, String detail) {
        java.util.Map<String, Object> evidence = new java.util.LinkedHashMap<>();
        evidence.put("thermalFixture", thermalFixtureEvidence());
        evidence.put("freshProductionObservations", freshProductionObservations());
        if (!energyFeeds.isEmpty()) evidence.put("realEnergyConsumption", energyEvidence());
        return new TestLabReport.Result(status, detail + "; " + debug(null), evidence);
    }

    private void captureThermalBaseline() {
        if (!beforeThermalFixtureSnapshots.isEmpty()) return;
        java.util.Set<IGregTechTileEntity> observed = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<IGregTechTileEntity, Boolean>());
        for (Node node : nodes) observed.add(node.tile);
        observed.addAll(feeds);
        if (inputBus != null) observed.add(inputBus.getBaseMetaTileEntity());
        if (outputBus != null) observed.add(outputBus.getBaseMetaTileEntity());
        if (boosterHatch != null) observed.add(boosterHatch.getBaseMetaTileEntity());
        if (logisticsFluid != null) observed.add(logisticsFluid);
        if (energyFeed != null) observed.add(energyFeed);
        for (IGregTechTileEntity tile : observed) beforeThermalFixtureSnapshots.add(TestLabFixtures.snapshot(tile));
    }

    private void acceleratePreheat() {
        thermalFixtureIssued = true;
        double before = cluster.getPreheat()
            .getProgress();
        if (before < 1D) {
            cluster.getPreheat()
                .setProgress(1D);
            directThermalWrites++;
        }
        thermalFixture.put(
            "scope",
            "FUNCTION_TEST_PREHEAT_ACCELERATION; natural full preheat and thermal save compatibility are not established");
        thermalFixture.put("field", "ClusterPreheatController.heatLevel");
        thermalFixture.put("persistedNbtKey", "clusterPreheat");
        thermalFixture.put("writeMethod", "cluster.getPreheat().setProgress(1D), public thermal API only");
        thermalFixture.put("beforeNormalizedProgress", before);
        thermalFixture.put("targetNormalizedProgress", 1D);
        thermalFixture.put(
            "readbackNormalizedProgress",
            cluster.getPreheat()
                .getProgress());
        thermalFixture.put("coldGate", coldGateStatus());
        thermalFixture.put("beforeFixtureControllerAndStockSnapshots", new ArrayList<>(beforeThermalFixtureSnapshots));
    }

    java.util.Map<String, Object> thermalFixtureEvidence() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("thermalFixtureApplied", directThermalWrites > 0);
        result.put("thermalNbtFixture", false);
        result.put("thermalNbtWrites", 0);
        result.put("directThermalWrites", directThermalWrites);
        result.put("fixture", new java.util.LinkedHashMap<>(thermalFixture));
        return result;
    }

    private String coldGateStatus() {
        if (sawColdInputPreserved) return "PASS_REAL_NATURAL_CHECK";
        return existingLayout && existingWarm ? "NOT_RUN_NO_COLD_WINDOW_ON_LOADED_LAYOUT" : "NOT_RUN_NOT_OBSERVED";
    }

    private java.util.Map<String, Object> freshProductionObservations() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("coldGate", coldGateStatus());
        result.put("actualColdGateObserved", sawColdInputPreserved);
        result.put("loadedHeatPercent", loadedHeatPercent);
        result.put("loadedPreheatReady", loadedPreheatReady);
        result.put("loadedHasNoBelow95PercentObservationWindow", existingLayout && existingWarm);
        result.put("recipeHitObserved", recipeHit);
        result.put("workInProgressObserved", sawRunning);
        result.put("initialProcessed", initialProcessed);
        result.put("initialOutputCount", initialOutputCount);
        if (cluster != null) {
            result.put("currentProcessed", cluster.getTotalProcessedOre());
            result.put("processedDelta", cluster.getTotalProcessedOre() - initialProcessed);
            result.put("nativePreheatReady", cluster.isPreheatReady());
        }
        if (logistics != null) result.put("pendingOutputsEmpty", !logistics.hasPendingOutputs());
        if (inputBus != null && input != null) result.put("inputRemaining", count(inputBus, input));
        if (outputBus != null && expected != null) {
            result.put("currentOutputCount", count(outputBus, expected));
            result.put("freshOutputDelta", count(outputBus, expected) - initialOutputCount);
        }
        return result;
    }

    @Override
    public void stop(WorldServer world) {
        for (IGregTechTileEntity tile : feeds) TestLabSupply.remove(tile);
        feeds.clear();
        for (Node node : nodes) node.tile.disableWorking();
        if (cluster != null) cluster.setMachineEnabled(false);
    }

    @Override
    public String debug(WorldServer world) {
        StringBuilder out = new StringBuilder("phase=").append(phase)
            .append(" formation=")
            .append(formation)
            .append(" recipesScanned=")
            .append(scannedRecipes);
        out.append(" coldGate=")
            .append(coldGateStatus())
            .append(" recipeHit=")
            .append(recipeHit)
            .append(" sawRunning=")
            .append(sawRunning)
            .append(" initialProcessed=")
            .append(initialProcessed);
        if (cluster != null) out.append(" heat=")
            .append(cluster.getPreheatProgress())
            .append(" supplyFlags=")
            .append(cluster.getSupplyFlags())
            .append(" processed=")
            .append(cluster.getTotalProcessedOre());
        if (logistics != null) out.append(" chainExecutable=")
            .append(logistics.isChainExecutableNow())
            .append(" cooldown=")
            .append(logistics.getChainCooldownTicks())
            .append(" pending=")
            .append(logistics.hasPendingOutputs());
        if (inputBus != null && input != null) out.append(" inputRemaining=")
            .append(count(inputBus, input));
        if (outputBus != null && expected != null) out.append(" expectedOutputCount=")
            .append(count(outputBus, expected));
        if (building != null && "BUILD".equals(phase)) out.append(" building=")
            .append(building.describe());
        for (Node node : nodes) {
            out.append(" [")
                .append(node.id)
                .append(" formed=")
                .append(node.machine.mMachine);
            if (node.machine instanceof MTEClusterUnitBase) {
                MTEClusterUnitBase unit = (MTEClusterUnitBase) node.machine;
                out.append(" tier=")
                    .append(unit.getUnitStructureTier())
                    .append(" clusterTier=")
                    .append(unit.getClusterTier())
                    .append(" segment=")
                    .append(unit.getSegmentIndex())
                    .append(" status=")
                    .append(unit.getUnitStatus());
            }
            out.append(" geometry=")
                .append(node.geometry)
                .append(" errors=")
                .append(structureErrors(node.machine))
                .append(" tile=")
                .append(TestLabFixtures.snapshot(node.tile))
                .append(']');
        }
        return out.toString();
    }

    private static ItemStack trigger() {
        return new ItemStack(Items.stick, 1);
    }

    private static String structureErrors(MTEMultiBlockBase machine) {
        try {
            Field errors = MTEMultiBlockBase.class.getDeclaredField("structureErrors");
            errors.setAccessible(true);
            return String.valueOf(errors.get(machine));
        } catch (ReflectiveOperationException exception) {
            return "unavailable:" + exception.getClass()
                .getSimpleName();
        }
    }

    private static boolean isWorking(MetaTileEntityID id) {
        return id.name()
            .startsWith("CLUSTER_UNIT_") && id != MetaTileEntityID.CLUSTER_UNIT_LOGISTICS;
    }

    private static ChainLink linkFor(MetaTileEntityID id) {
        switch (id) {
            case CLUSTER_UNIT_CRUSHER:
                return ChainLink.CRUSH;
            case CLUSTER_UNIT_ORE_WASHER:
                return ChainLink.ORE_WASH;
            case CLUSTER_UNIT_CENTRIFUGE:
                return ChainLink.CENTRIFUGE;
            case CLUSTER_UNIT_THERMOCENTRIFUGE:
                return ChainLink.THERMOCENTRIFUGE;
            case CLUSTER_UNIT_SIFTER:
                return ChainLink.SIFTER;
            case CLUSTER_UNIT_MAGNETIC_SEPARATOR:
                return ChainLink.MAGNETIC_SEPARATOR;
            case CLUSTER_BOOSTER_PARALLEL:
            case CLUSTER_BOOSTER_SPEED:
            case CLUSTER_BOOSTER_PRIMARY:
            case CLUSTER_BOOSTER_SECONDARY:
            case CLUSTER_BOOSTER_STEAM_SAVER:
                return ChainLink.CRUSH;
            default:
                return ChainLink.FURNACE;
        }
    }

    private static final class Spec {

        final MetaTileEntityID id;
        final char pad;
        final int segment;

        Spec(MetaTileEntityID id, char pad, int segment) {
            this.id = id;
            this.pad = pad;
            this.segment = segment;
        }
    }

    private static final class Node {

        final MetaTileEntityID id;
        final IGregTechTileEntity tile;
        final MTEMultiBlockBase machine;
        String geometry;

        Node(MetaTileEntityID id, IGregTechTileEntity tile) {
            this.id = id;
            this.tile = tile;
            machine = (MTEMultiBlockBase) tile.getMetaTileEntity();
        }
    }
}
