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
    private boolean recipeHit;
    private boolean sawRunning;
    private boolean sawColdInputPreserved;
    private int productionStart;
    private int scannedRecipes;
    private String formation = "pending";

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
                if (count(inputBus, input) != input.stackSize || cluster.getTotalProcessedOre() != 0
                    || count(outputBus, expected) != 0) return result(TestLabReport.Status.FAIL, "低温门控失败：预热期间吞料/输出/记账");
                sawColdInputPreserved = true;
            }
            sawRunning |= logistics.isWorkInProgress();
            recipeHit |= lastBatchLinks().contains(link);
            if (count(outputBus, expected) >= 1) {
                if (!cluster.isPreheatReady() || !sawColdInputPreserved
                    || !recipeHit
                    || !sawRunning
                    || count(inputBus, input) != 0
                    || cluster.getTotalProcessedOre() < 1
                    || logistics.hasPendingOutputs())
                    return result(TestLabReport.Status.FAIL, "已见产物但预热/真实配方命中/运行进度/扣料/排空证据不完整");
                if (TestLabSupply.replenished(steamFeed) <= 0L || TestLabSupply.replenished(lubricantFeed) <= 0L)
                    return result(TestLabReport.Status.FAIL, "已见产物但未观测到真实蒸汽/润滑消耗");
                if (energyFeed != null && energyFeed.getStoredEU() >= initialEnergy)
                    return result(TestLabReport.Status.FAIL, "需持续供电链步未观察到能源仓真实EU扣除");
                if (booster != null && (booster.isLastBatchPrecheckFailed()
                    || boosterInitial - boosterHatch.getFluidAmount() != booster.batchCost(input.stackSize)))
                    return result(TestLabReport.Status.FAIL, "增幅液未按原生批单价真实扣除");
                return result(
                    TestLabReport.Status.PASS,
                    "formation=" + formation
                        + " binding=PASS coldGate=PASS preheat=natural recipeHit="
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
                return result(TestLabReport.Status.FAIL, "自然tick生产超时（不强写热量/成型/进度）");
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
            energyFeed.increaseStoredEnergyUnits(Math.min(1_000_000L, energyFeed.getEUCapacity()), false);
            initialEnergy = energyFeed.getStoredEU();
        }
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
        return new TestLabReport.Result(status, detail + "; " + debug(null));
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
