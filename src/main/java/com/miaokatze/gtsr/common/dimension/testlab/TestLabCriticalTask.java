package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.miaokatze.gtsr.common.critical.CriticalEnergyRuntime;
import com.miaokatze.gtsr.common.critical.CriticalGTRecipes;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalRenderAnchors;
import com.miaokatze.gtsr.common.critical.CriticalRuntime;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy;
import com.miaokatze.gtsr.common.critical.CriticalStructures;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.critical.CriticalWorldData;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;
import com.miaokatze.gtsr.common.debug.MachineDebugRegistry;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.mojang.authlib.GameProfile;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Mods;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gtnhintergalactic.config.IGConfig;

/** Live loom journal and real hatch IO. The task never writes formation, READY, or journal progress. */
public final class TestLabCriticalTask implements TestLabTask {

    private enum Phase {
        LOOM,
        TRACK,
        CONSTRUCT,
        PREPARE,
        RUN
    }

    private final String key;
    private final CriticalMachineKind kind;
    private final boolean loomOnly;
    private MTECriticalMachine machine;
    private CriticalMachineState state;
    private IGregTechTileEntity tile;
    private FakePlayer player;
    private TestLabStructure loom;
    private Phase phase;
    private List<CriticalGeometry.Voxel> tracked;
    private int trackingCursor, phaseStarted;
    private boolean sawBatch;
    private MTEHatchInput fluidInput;
    private MTEHatchOutput fluidOutput;
    private MTEHatchInputBus itemInput;
    private MTEHatchOutputBus itemOutput;
    private RecipeMatch expectedRecipe;
    private ItemStack[] initialItems;
    private FluidStack[] initialFluids;
    private FluidStack sourceFluid, targetFluid;
    private long suppliedFluid, consumedFluid, expectedFluid;
    private long initialEnergy;
    private BigInteger wirelessBefore, expectedWireless;
    private String production = "";
    private Map<String, Double> leasedSolarFactors;
    private String solarFactorKey;
    private boolean originalSolarFactorPresent;
    private Double originalSolarFactorEntry;
    private String solarEnvironment = "";

    public TestLabCriticalTask(String kind) {
        this.key = kind.toLowerCase(Locale.ROOT);
        loomOnly = "loom".equals(key);
        this.kind = loomOnly ? CriticalMachineKind.SUN : CriticalMachineKind.valueOf(key.toUpperCase(Locale.ROOT));
    }

    @Override
    public String id() {
        return "critical_" + key;
    }

    @Override
    public String displayName() {
        return "临界" + key + "真实织机与最小功能";
    }

    @Override
    public int timeoutTicks() {
        return 24000;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        if (!loomOnly && (!kind.isReleased() || !implemented())) return;
        TestLabFixtures.attach(world);
        tile = TestLabFixtures.machine(world, x + 80, y + 4, z + 80, CriticalRuntime.getLoomMetaId());
        machine = (MTECriticalMachine) tile.getMetaTileEntity();
        state = machine.getCriticalState();
        player = FakePlayerFactory.get(world, new GameProfile(TestLabFixtures.OWNER, "[GTSR Test Lab]"));
        player.capabilities.isCreativeMode = true;
        player.isDead = false;
        // The fake is never added to playerEntities; permission checks still use its real proximity and UUID.
        player.setPosition(tile.getXCoord() + .5, tile.getYCoord() + .5, tile.getZCoord() - 2.5);
        if (!state.configure(player, CriticalTier.T3, kind, 8, 0, 0))
            throw new IllegalStateException("Test owner/configuration rejected");
        MachineDebugRegistry.Adapter debug = MachineDebugRegistry.find(machine);
        if (debug == null || !"construction".equals(debug.family()) || !debug.canUse(machine, player))
            throw new IllegalStateException("Construction debug adapter unavailable to test owner");
        debug.apply(machine, player, new int[] { 3, 3, 1 }, false);
        if (state.getConstructionDebugRequirements() != 3 || state.getConstructionDebugSpeed() != 3
            || !state.getConstructionDebugIgnoreEnergy())
            throw new IllegalStateException("Construction debug not applied");
        state.setEnabled(player, false);
        loom = new TestLabStructure(world, machine, CriticalRuntime.getLoomControllerStack());
        // Journal-generated blocks and controllers must also survive a server restart in the reset manifest.
        tracked = new ArrayList<>(CriticalStructures.voxels(state));
        tracked.addAll(CriticalGeometry.optionalWorkPorts(state));
        TestLabFixtures.track(
            world,
            CriticalRenderAnchors.getX(state),
            CriticalRenderAnchors.getY(state),
            CriticalRenderAnchors.getZ(state));
        phase = Phase.LOOM;
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        if (!loomOnly && !kind.isReleased()) return result(TestLabReport.Status.UNSUPPORTED, "该临界种类处于维护冻结，未施工");
        if (!loomOnly && !implemented())
            return result(TestLabReport.Status.UNSUPPORTED, "本真实施工测试尚无该种类的专用生产fixture，未施工；不代表生产后端未实现");
        switch (phase) {
            case LOOM:
                if (!loom.buildTick()) return null;
                if (!machine.forceLoomStructureCheck()) return result(TestLabReport.Status.FAIL, "手搭织机校验失败");
                phase = Phase.TRACK;
                return null;
            case TRACK:
                for (int budget = 0; budget < 256 && trackingCursor < tracked.size(); budget++, trackingCursor++) {
                    CriticalGeometry.Voxel v = tracked.get(trackingCursor);
                    TestLabFixtures.track(world, state.blockWorldX(v), state.blockWorldY(v.y), state.blockWorldZ(v));
                }
                if (trackingCursor < tracked.size()) return null;
                phase = Phase.CONSTRUCT;
                phaseStarted = elapsedTicks;
                return null;
            case CONSTRUCT:
                // FakePlayer is intentionally offline. Retry the public request while its normal chunk/clearance
                // windows settle, because the machine's automatic retry only searches online playerEntities.
                if (state.getState() == 0 && !state.startConstruction(player)) {
                    String reason = state.getStatus();
                    // Chunk windows and the sparse clearance scan are resumable. A concrete
                    // foreign block, denied permission, or overlapping region needs a report,
                    // not thousands of repeated scans that hide the original obstruction.
                    if (!reason.contains("等待") && !reason.startsWith("首次施工占位检查 "))
                        return result(TestLabReport.Status.FAIL, "正常施工请求受阻：" + reason);
                }
                if (state.getState() != 2 || !state.isStructureValidated()) {
                    if (elapsedTicks - phaseStarted > 18000) return result(TestLabReport.Status.FAIL, "正常施工未完成");
                    return null;
                }
                state.setEnabled(player, false);
                if (!pairedMain(world)) return result(TestLabReport.Status.FAIL, "自然施工未生成同账本的目标控制器");
                if (loomOnly)
                    return result(TestLabReport.Status.PASS, "织机施工功能：真实手搭织机→正常净空/施工journal→目标SUN结构校验完成；未声明SUN生产通过");
                phase = Phase.PREPARE;
                return null;
            case PREPARE:
                TestLabReport.Result prepared = prepare(world);
                if (prepared != null) return prepared;
                phase = Phase.RUN;
                phaseStarted = elapsedTicks;
                state.setEnabled(player, true);
                return null;
            case RUN:
                if (!state.isStructureValidated()) return result(TestLabReport.Status.FAIL, "生产期真实结构失效");
                sawBatch |= state.getBatchDuration() == 400 && state.getBatchProgress() > 0;
                if (expectedRecipe != null) {
                    if (sawBatch && state.getBatchId()
                        .isEmpty()) return verifyRecipe();
                } else if (sawBatch && !state.energyRuntimeData()
                    .getBoolean("funded")) return verifyFluids();
                if (elapsedTicks - phaseStarted > 1000) return result(TestLabReport.Status.FAIL, "400tick最小生产批次未交付");
                return null;
            default:
                throw new IllegalStateException("Unknown test phase");
        }
    }

    private boolean pairedMain(WorldServer world) {
        TileEntity partner = world
            .getTileEntity(state.blockWorldX(56, 35), state.blockWorldY(4), state.blockWorldZ(56, 35));
        return partner instanceof IGregTechTileEntity
            && ((IGregTechTileEntity) partner).getMetaTileEntity() instanceof MTECriticalMachine
            && ((MTECriticalMachine) ((IGregTechTileEntity) partner).getMetaTileEntity()).getPresetKind() == kind
            && ((MTECriticalMachine) ((IGregTechTileEntity) partner).getMetaTileEntity()).getCriticalState() == state;
    }

    private boolean implemented() {
        return kind == CriticalMachineKind.SOLAR || kind == CriticalMachineKind.TURBINE;
    }

    private TestLabReport.Result prepare(WorldServer world) throws Exception {
        if (kind == CriticalMachineKind.PROCESSING || kind == CriticalMachineKind.ASSEMBLY) {
            itemInput = (MTEHatchInputBus) install(ItemList.Hatch_Input_Bus_IV).getMetaTileEntity();
            itemOutput = (MTEHatchOutputBus) install(ItemList.Hatch_Output_Bus_IV).getMetaTileEntity();
            install(ItemList.Hatch_Energy_IV);
            machine.forceLoomStructureCheck();
            return prepareRecipe();
        }
        fluidInput = (MTEHatchInput) install(ItemList.Hatch_Input_IV).getMetaTileEntity();
        fluidOutput = (MTEHatchOutput) install(ItemList.Hatch_Output_IV).getMetaTileEntity();
        MTEHatchInput solarCoolant = null, solarExciter = null;
        if (kind == CriticalMachineKind.SOLAR) {
            solarCoolant = (MTEHatchInput) install(ItemList.Hatch_Input_IV).getMetaTileEntity();
            solarExciter = (MTEHatchInput) install(ItemList.Hatch_Input_IV).getMetaTileEntity();
        }
        machine.forceLoomStructureCheck();
        sourceFluid = kind == CriticalMachineKind.SOLAR ? GTModHandler.getDistilledWater(100)
            : GTModHandler.getSteam(1600);
        targetFluid = kind == CriticalMachineKind.SOLAR ? CriticalEnergyRuntime.steam(0)
            : GTModHandler.getDistilledWater(1);
        if (sourceFluid == null || targetFluid == null) return result(TestLabReport.Status.BLOCKED, "所需GT流体未注册");
        suppliedFluid = fluidInput.fill(ForgeDirection.UNKNOWN, sourceFluid.copy(), true);
        if (suppliedFluid != sourceFluid.amount) return result(TestLabReport.Status.FAIL, "真实输入仓拒绝最小流体");
        if (kind == CriticalMachineKind.SOLAR) {
            leaseSolarFactor(world);
            CriticalSolarPolicy.Entry coolant = CriticalSolarPolicy.COOLANTS.get(0);
            CriticalSolarPolicy.Entry exciter = CriticalSolarPolicy.EXCITERS.get(0);
            BigInteger budget = CriticalSolarPolicy
                .batchBudgetEU(CriticalEnergyRuntime.dimensionFactor(world), coolant, exciter, false);
            BigInteger heat = CriticalSolarPolicy
                .saturatedWaterHalfHeat(BigInteger.valueOf(suppliedFluid), budget.divide(BigInteger.TEN));
            expectedFluid = CriticalSolarPolicy.steamLitres(heat, 0)
                .longValueExact();
            consumedFluid = CriticalSolarPolicy.waterLitres(BigInteger.valueOf(expectedFluid), 0)
                .longValueExact();
            if (consumedFluid <= 0) return result(TestLabReport.Status.BLOCKED, "????????????????");
            BigInteger work = heat.multiply(BigInteger.TEN);
            FluidStack coolantInput = CriticalEnergyRuntime.solarCoolant(coolant)
                .copy();
            coolantInput.amount = CriticalSolarPolicy.costLitres(coolant, work)
                .intValueExact();
            FluidStack exciterInput = CriticalEnergyRuntime.solarExciter(exciter)
                .copy();
            exciterInput.amount = CriticalSolarPolicy.costLitres(exciter, work)
                .intValueExact();
            if (solarCoolant.fill(ForgeDirection.UNKNOWN, coolantInput.copy(), true) != coolantInput.amount
                || solarExciter.fill(ForgeDirection.UNKNOWN, exciterInput.copy(), true) != exciterInput.amount)
                return result(TestLabReport.Status.FAIL, "????????????");
            state.energyRuntimeData()
                .setInteger("steamMode", 0);
            state.energyRuntimeData()
                .setBoolean("solarSaltMode", false);
        } else {
            // Refuse even a test UUID that somebody has explicitly joined to a different team.
            if (!TestLabFixtures.OWNER.equals(SpaceProjectManager.getLeader(TestLabFixtures.OWNER)))
                return result(TestLabReport.Status.BLOCKED, "测试UUID已加入其他无线团队，拒绝触碰其账户");
            WirelessNetworkManager.strongCheckOrAddUser(TestLabFixtures.OWNER);
            wirelessBefore = WirelessNetworkManager.getUserEU(TestLabFixtures.OWNER);
            expectedWireless = BigInteger.valueOf(suppliedFluid)
                .divide(BigInteger.valueOf(2));
            consumedFluid = suppliedFluid;
            expectedFluid = suppliedFluid / 160;
        }
        production = "source=" + sourceFluid.getFluid()
            .getName()
            + " supplied="
            + suppliedFluid
            + " expectedConsumed="
            + consumedFluid
            + " target="
            + targetFluid.getFluid()
                .getName()
            + " expectedOutput="
            + expectedFluid;
        return null;
    }

    /**
     * The native default is zero for unknown dimensions. Lease only this lab's lookup key; never
     * change the default, the persisted config, another dimension, or the production resolver.
     */
    @SuppressWarnings("unchecked")
    private void leaseSolarFactor(WorldServer world) throws ReflectiveOperationException {
        TestLabFixtures.attach(world);
        double original = CriticalEnergyRuntime.dimensionFactor(world);
        if (!Mods.GalacticraftCore.isModLoaded()) {
            solarEnvironment = "solarFactor original=" + original + " test=" + original + " lease=not_required";
            return;
        }
        solarFactorKey = String.valueOf(world.provider.dimensionId);
        // getPowerFactor lazily initializes its private cache. There is no public per-key setter in
        // GT183/205 IGConfig; retain this map identity and restore exactly the one borrowed key.
        IGConfig.dysonSwarm.getPowerFactor(solarFactorKey);
        Field cache = IGConfig.dysonSwarm.getClass()
            .getDeclaredField("cachedPowerFactors");
        cache.setAccessible(true);
        leasedSolarFactors = (Map<String, Double>) cache.get(IGConfig.dysonSwarm);
        originalSolarFactorPresent = leasedSolarFactors.containsKey(solarFactorKey);
        originalSolarFactorEntry = leasedSolarFactors.get(solarFactorKey);
        leasedSolarFactors.put(solarFactorKey, 1D);
        double actual = CriticalEnergyRuntime.dimensionFactor(world);
        solarEnvironment = "solarFactor original=" + original
            + " test="
            + actual
            + " scope=lab_dimension_key:"
            + solarFactorKey
            + " restore=on_task_stop";
        if (actual != 1D) throw new IllegalStateException("Native solar resolver did not observe the lab factor lease");
    }

    private void restoreSolarFactor() {
        if (leasedSolarFactors == null) return;
        if (originalSolarFactorPresent) leasedSolarFactors.put(solarFactorKey, originalSolarFactorEntry);
        else leasedSolarFactors.remove(solarFactorKey);
        leasedSolarFactors = null;
        solarEnvironment += " restored=true";
    }

    private IGregTechTileEntity install(ItemList item) throws Exception {
        // After commissioning, the manual loom is fabrication-only. Production reads the canonical
        // main controller's optional chamber row, so never put recipe stock in a loom hatch.
        WorldServer world = (WorldServer) tile.getWorld();
        for (CriticalGeometry.Voxel port : state.getResourceHatchPorts()) {
            int x = state.blockWorldX(port), y = state.blockWorldY(port.y), z = state.blockWorldZ(port);
            if (!CriticalStructures.isOptionalWorkPort(port) || !world.isAirBlock(x, y, z)) continue;
            IGregTechTileEntity hatch = TestLabFixtures.machine(
                world,
                x,
                y,
                z,
                item.get(1)
                    .getItemDamage());
            if (!state.isStandardHatch(port))
                throw new IllegalStateException("Canonical work port rejected " + item.name());
            machine.forceLoomStructureCheck();
            return hatch;
        }
        throw new IllegalStateException("No empty canonical work port accepts " + item.name());
    }

    private TestLabReport.Result prepareRecipe() {
        RecipeMap<?> map = kind == CriticalMachineKind.ASSEMBLY ? RecipeMaps.assemblerRecipes
            : RecipeMaps.maceratorRecipes;
        if (!state.selectProcessingMode(player, map.unlocalizedName))
            return result(TestLabReport.Status.BLOCKED, "真实RecipeMap模式不可选：" + map.unlocalizedName);
        GTRecipe selected = null;
        // Deliberately bounded discovery over registered native recipes; no synthetic recipe is registered.
        int inspected = 0;
        for (GTRecipe candidate : map.getAllRecipes()) {
            if (++inspected > 20000) break;
            if (minimalRecipe(candidate)) {
                selected = candidate;
                break;
            }
        }
        if (selected == null)
            return result(TestLabReport.Status.BLOCKED, "原生RecipeMap没有确定性、无流体的最小配方：" + map.unlocalizedName);
        int slot = 0;
        for (ItemStack input : selected.mInputs) if (input != null) {
            while (slot == itemInput.getCircuitSlot()) slot++;
            ItemStack stock = input.copy();
            stock.stackSize = Math.max(1, stock.stackSize);
            itemInput.setInventorySlotContents(slot++, stock);
        }
        initialItems = machine.criticalRecipeItemsSnapshot();
        initialFluids = machine.criticalPhysicalFluidsSnapshot();
        // Seed only this test controller's actual EU buffer; construction ignoreEnergy never bypasses recipe EU.
        machine.setEUVar(10_000_000L);
        initialEnergy = tile.getStoredEU();
        expectedRecipe = CriticalGTRecipes.match(state, machine);
        if (expectedRecipe == null || expectedRecipe.itemOutputs.length == 0 || expectedRecipe.durationTicks != 400)
            return result(TestLabReport.Status.FAIL, "真实仓口原生配方匹配失败");
        // Ambiguous chance recipes cannot provide a stable oracle; reject such an input fixture before execution.
        java.util.Iterator<GTRecipe> matches = map.findRecipeQuery()
            .items(initialItems)
            .fluids(initialFluids)
            .voltage(machine.criticalRecipeVoltage())
            .findAll()
            .iterator();
        while (matches.hasNext())
            if (!deterministic(matches.next())) return result(TestLabReport.Status.BLOCKED, "原生输入命中概率配方，无法给出确定性最小断言");
        production = "map=" + map.unlocalizedName
            + " duration="
            + expectedRecipe.durationTicks
            + " EUt="
            + expectedRecipe.euPerTick
            + " input="
            + describe(expectedRecipe.itemInputs)
            + " expectedOutput="
            + describe(expectedRecipe.itemOutputs);
        return null;
    }

    private static boolean minimalRecipe(GTRecipe recipe) {
        if (!recipe.mEnabled || recipe.mFakeRecipe
            || recipe.mEUt <= 0
            || recipe.mEUt > 8192
            || recipe.mInputs.length > 8
            || recipe.mOutputs.length == 0
            || recipe.mOutputs.length > 4
            || recipe.mFluidInputs.length != 0
            || recipe.mFluidOutputs.length != 0
            || !deterministic(recipe)) return false;
        boolean consumed = false, produced = false;
        for (ItemStack stack : recipe.mInputs) if (stack != null) {
            if (stack.stackSize < 0 || stack.stackSize > Math.min(64, stack.getMaxStackSize())) return false;
            consumed |= stack.stackSize > 0;
        }
        for (ItemStack stack : recipe.mOutputs) if (stack != null && stack.stackSize > 0) produced = true;
        return consumed && produced;
    }

    private static boolean deterministic(GTRecipe recipe) {
        for (int i = 0; i < recipe.mOutputs.length; i++) if (recipe.getOutputChance(i) != 10000) return false;
        for (int i = 0; i < recipe.mFluidOutputs.length; i++) if (recipe.getFluidOutputChance(i) != 10000) return false;
        for (int i = 0; i < recipe.mInputs.length; i++) if (recipe.getInputChance(i) != 10000) return false;
        return true;
    }

    private TestLabReport.Result verifyRecipe() {
        state.setEnabled(player, false);
        ItemStack[] remaining = machine.criticalRecipeItemsSnapshot();
        for (ItemStack cost : initialItems)
            if (count(initialItems, cost) - count(remaining, cost) != count(expectedRecipe.itemInputs, cost))
                return result(TestLabReport.Status.FAIL, "原生配方真实输入扣除不符");
        ItemStack[] outputs = new ItemStack[itemOutput.getSizeInventory()];
        for (int i = 0; i < outputs.length; i++) outputs[i] = itemOutput.getStackInSlot(i);
        for (ItemStack output : expectedRecipe.itemOutputs)
            if (count(outputs, output) != count(expectedRecipe.itemOutputs, output))
                return result(TestLabReport.Status.FAIL, "原生配方真实输出不符 actual=" + describe(outputs));
        for (ItemStack output : outputs)
            if (output != null && count(outputs, output) != count(expectedRecipe.itemOutputs, output))
                return result(TestLabReport.Status.FAIL, "原生配方产生额外输出 actual=" + describe(outputs));
        long energySpent = initialEnergy - tile.getStoredEU();
        if (energySpent != expectedRecipe.euPerTick * 400L)
            return result(TestLabReport.Status.FAIL, "400tick真实EU差额不符 actual=" + energySpent);
        return result(TestLabReport.Status.PASS, "原生RecipeMap真实扣料、400tick交付、真实输出和EU差额通过；" + production);
    }

    private TestLabReport.Result verifyFluids() {
        state.setEnabled(player, false);
        long remaining = amount(fluidInput.getTankInfo(ForgeDirection.UNKNOWN), sourceFluid);
        long output = amount(fluidOutput.getTankInfo(ForgeDirection.UNKNOWN), targetFluid);
        if (suppliedFluid - remaining != consumedFluid || output != expectedFluid) return result(
            TestLabReport.Status.FAIL,
            "真实流体差额不符 remaining=" + remaining + " output=" + output + "; " + production);
        if (kind == CriticalMachineKind.TURBINE) {
            BigInteger delta = WirelessNetworkManager.getUserEU(TestLabFixtures.OWNER)
                .subtract(wirelessBefore);
            if (!delta.equals(expectedWireless)) return result(
                TestLabReport.Status.FAIL,
                "专属测试无线账户增量不符 actual=" + delta + " expected=" + expectedWireless);
            production += " wirelessDelta=" + delta;
        }
        return result(TestLabReport.Status.PASS, "真实仓口400tick批次输入/输出差额通过；" + production);
    }

    private static long amount(FluidTankInfo[] tanks, FluidStack type) {
        long result = 0;
        for (FluidTankInfo tank : tanks)
            if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(type)) result += tank.fluid.amount;
        return result;
    }

    private static long count(ItemStack[] items, ItemStack type) {
        if (type == null) return 0;
        long result = 0;
        for (ItemStack stack : items)
            if (stack != null && stack.isItemEqual(type) && ItemStack.areItemStackTagsEqual(stack, type))
                result += stack.stackSize;
        return result;
    }

    private static String describe(ItemStack[] items) {
        StringBuilder result = new StringBuilder();
        for (ItemStack stack : items) if (stack != null) result.append(stack.stackSize)
            .append('x')
            .append(stack.getUnlocalizedName())
            .append(':')
            .append(stack.getItemDamage())
            .append(';');
        return result.toString();
    }

    private TestLabReport.Result result(TestLabReport.Status status, String message) {
        if (leasedSolarFactors != null) {
            try {
                state.setEnabled(player, false);
            } finally {
                // Capture successful restoration in the completed report, before runtime calls stop.
                restoreSolarFactor();
            }
        }
        return new TestLabReport.Result(
            status,
            message + "; " + diagnostics() + (production.isEmpty() ? "" : "; fixture=" + production));
    }

    private String diagnostics() {
        if (state == null) return "phase=" + phase;
        return "phase=" + phase
            + " kind="
            + kind.key
            + " tier="
            + state.getTier()
            + " journalState="
            + state.getState()
            + " validated="
            + state.isStructureValidated()
            + " loom="
            + machine.isLoomGeometryValid()
            + " stage="
            + state.getStage()
            + " cursor="
            + state.getCursor()
            + " placed="
            + state.getConstructionPlaced()
            + " structureId="
            + state.getStructureId()
            + " jobId="
            + state.getJobId()
            + " status="
            + state.getStatus()
            + " chunkLoading="
            + state.getChunkLoading()
                .getStatus()
            + " "
            + (loom == null ? "" : loom.describe())
            + (solarEnvironment.isEmpty() ? "" : "; " + solarEnvironment);
    }

    @Override
    public String debug(WorldServer world) {
        return diagnostics() + "; " + production + "; " + TestLabFixtures.snapshot(tile);
    }

    @Override
    public void stop(WorldServer world) {
        try {
            if (state == null) return;
            state.setEnabled(player, false);
            state.setChunkLoadingEnabled(false);
            state.getChunkLoading()
                .release(state.getHost());
            CriticalWorldData.get(world)
                .releaseNight(state.getStructureId());
            // Keep the journal/region as inspectable evidence. Explicit fixture regeneration releases it before
            // removal.
        } finally {
            restoreSolarFactor();
        }
    }
}
