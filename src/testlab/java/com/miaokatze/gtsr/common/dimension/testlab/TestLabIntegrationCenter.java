// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.machine.orecenter.CenterPlanner;
import com.miaokatze.gtsr.common.machine.orecenter.CenterProcess;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Uses actual GT recipe registration and the production planner on the test dimension's server thread. */
final class TestLabIntegrationCenter implements TestLabTask {

    static final String PLANNER = "ore_integration_center";
    static final String PRESSURE = "perf.ore_integration_center";
    private static final ItemStack[] NO_ITEMS = {};
    private static final FluidStack[] NO_FLUIDS = {};
    private static final int SAMPLE_COUNT = 128;
    private static final int GRAPH_COUNT = 18;
    private static final long SOFT_FRAME_NS = 2_000_000L;
    private static final long ADMISSION_NS = 1_700_000L;
    private static final long SOFT_JOB_NS = 250_000L;
    private static final long SEED = 0x7210C0BBE0L;
    private static final int[] CONTROLLERS = { 1, 8, 32 };

    private final boolean pressure;
    private ItemStack copper;
    private ItemStack[] oreBank;
    private List<CenterGraph> compiled;
    private final List<Map<String, Object>> measurements = new ArrayList<>();
    private final long[] invocations = new long[SAMPLE_COUNT];
    private final List<Long> frames = new ArrayList<>(), starts = new ArrayList<>(), slices = new ArrayList<>();
    private final List<PressureFrame> pressureFrames = new ArrayList<>();
    private final ArrayDeque<PressureJob> queue = new ArrayDeque<>();
    private ThreadMXBean threadClock;
    private List<Object> pressureSignature;
    private int maxQueued, startedInPhase, measuredJobs;
    private int phase;
    private int invocationsInPhase;
    private int served;

    TestLabIntegrationCenter(boolean pressure) {
        this.pressure = pressure;
    }

    @Override
    public String id() {
        return pressure ? PRESSURE : PLANNER;
    }

    @Override
    public String displayName() {
        return pressure ? "Ore center native planner stress, 1/8/32 load" : "Ore center native planning and settlement";
    }

    @Override
    public boolean requiresFixtureChunks() {
        return false;
    }

    @Override
    public String performancePhase() {
        return pressure ? "orecenter.plan.stress" : "orecenter.plan.contract";
    }

    @Override
    public int timeoutTicks() {
        return pressure ? 6000 : 200;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) {
        copper = GTOreDictUnificator.get(OrePrefixes.rawOre, Materials.Copper, 64L);
        require(copper != null, "Missing registered GT raw copper");
        if (pressure) {
            oreBank = collectOreBank(copper);
            compiled = pressureGraphs();
            measurements.clear();
            phase = 0;
            invocationsInPhase = startedInPhase = measuredJobs = maxQueued = 0;
            pressureSignature = null;
            queue.clear();
            starts.clear();
            slices.clear();
            frames.clear();
            pressureFrames.clear();
            ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            threadClock = threads.isCurrentThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled() ? threads : null;
            served = 0;
        }
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
        require(world != null && world.provider instanceof TestLabWorldProvider, "Expected owned test dimension");
        return pressure ? stressTick(world) : verifyPlanning();
    }

    @Override
    public void stop(WorldServer world) {
        copper = null;
        oreBank = null;
        compiled = null;
        queue.clear();
    }

    @Override
    public String debug(WorldServer world) {
        return "ore center phase=" + phase + " samples=" + invocationsInPhase + " served=" + served;
    }

    private TestLabReport.Result verifyPlanning() {
        Map<String, Integer> registrations = verifyRegistrations();
        GTRecipe crushing = nativeRecipe(RecipeMaps.maceratorRecipes, copper, NO_FLUIDS);
        GTRecipe milling = nativeRecipe(RecipeMaps.maceratorRecipes, crushing.mOutputs[0], NO_FLUIDS);
        GTRecipe separating = nativeRecipe(
            RecipeMap.ALL_RECIPE_MAPS.get("gt.recipe.multicentrifuge"),
            milling.mOutputs[0],
            NO_FLUIDS);
        require(
            crushing.mOutputs[0].stackSize == 2 && crushing.getOutputChance(0) == 10000
                && milling.mOutputs[0].stackSize == 1 && milling.getOutputChance(0) == 10000
                && separating.mOutputs[0].stackSize == 1 && separating.getOutputChance(0) == 10000,
            "Unexpected native copper path");
        List<CenterGraph.Node> crush = Arrays.asList(node(CenterProcess.CRUSHER, -1));
        List<CenterGraph.Node> dry = Arrays.asList(
            node(CenterProcess.CRUSHER, 1),
            node(CenterProcess.CRUSHER, 2),
            new CenterGraph.Node(CenterProcess.CENTRIFUGE, new int[] { -1, -1 }));
        CenterGraph early = graph(0, true, "rawOreCopper", crush);
        CenterGraph later = graph(0, true, "^rawOreCopper$", dry);
        ItemStack[] items = { copper };
        int[] bronze = grades(CenterModule.CRUSHER, CenterModule.CENTRIFUGE, CenterModule.LOGISTICS);
        CenterPlanner.Batch first = plan(Arrays.asList(early, later), items, NO_FLUIDS, bronze, 0, 1, 0, 0, SEED);
        CenterPlanner.Batch ranked = plan(
            Arrays.asList(early, graph(1, true, "rawOreCopper", dry)), items, NO_FLUIDS, bronze, 0, 1, 0, 0, SEED);
        CenterPlanner.Batch disabled = plan(
            Arrays.asList(graph(9, false, "rawOreCopper", dry), early), items, NO_FLUIDS, bronze, 0, 1, 0, 0, SEED);
        CenterPlanner.Batch roundTrip = plan(
            Arrays.asList(CenterGraph.load(early.save()), CenterGraph.load(later.save())),
            items, NO_FLUIDS, bronze, 0, 1, 0, 0, SEED);
        int input = quantity(ranked.consumed, copper);
        require(input > 0 && input <= copper.stackSize, "Consumed count must match actual supplied ore");
        require(quantity(first.outputs, crushing.mOutputs[0]) == 2 * quantity(first.consumed, copper), "Tie must select the first graph");
        require(signature(first).equals(signature(roundTrip)), "Graph NBT round-trip changed plan");
        require(quantity(ranked.outputs, separating.mOutputs[0]) == 2 * input, "Raised priority must execute all copper nodes");
        require(quantity(disabled.outputs, crushing.mOutputs[0]) == 2 * quantity(disabled.consumed, copper), "Disabled graph must not intercept");
        require(quantity(ranked.outputs, crushing.mOutputs[0]) == 0, "Intermediate copper leaked across shared output");
        require(ranked.ticks > 0 && ranked.steam > 0 && ranked.energy == 0, "Bronze planning must account steam without EU");
        require(copper.stackSize == 64, "Planning changed the borrowed inventory");
        verifyGraphFilters(early, dry, crushing, separating, bronze);
        require(CenterPlanner.plan(
            Arrays.asList(early), NO_ITEMS, NO_FLUIDS, bronze, 0, 1, 0, 0, 0, SEED) == null, "Empty inventory planned a free batch");

        int[] boosted = bronze.clone();
        boosted[CenterModule.YIELD.ordinal()] = 1;
        FluidStack acid = Materials.SulfuricAcid.getFluid(4_096L);
        require(acid != null, "Missing registered sulfuric acid");
        int availableAcid = acid.amount;
        FluidStack[] fluids = { acid };
        List<CenterGraph> settings = Arrays.asList(later);
        CenterPlanner.Batch unboosted = plan(settings, items, NO_FLUIDS, boosted, 0, 1, 0, 0, SEED);
        CenterPlanner.Batch sameSeedFirst = plan(settings, items, fluids, boosted, 0, 1, 0, 0, SEED);
        CenterPlanner.Batch sameSeedLater = plan(settings, items, fluids, boosted, 0, 1, 0, 0, SEED);
        require(signature(sameSeedFirst).equals(signature(sameSeedLater)), "Identical seed or borrowed inventory changed the batch");
        require(
            sameSeedFirst.consumedFluids.stream()
                .filter(acid::isFluidEqual)
                .mapToInt(value -> value.amount)
                .sum() == 8 * quantity(sameSeedFirst.consumed, copper),
            "Primary booster must charge one sulfuric-acid rate per accepted ore");
        require(unboosted.consumedFluids.stream().noneMatch(acid::isFluidEqual), "Unavailable additive was consumed");

        int extras = 0, trials = 0;
        for (int i = 0; i < 96; i++) {
            CenterPlanner.Batch batch = plan(settings, items, fluids, boosted, 0, 1, 0, 0, SEED + i);
            int main = quantity(batch.consumed, copper) * 2;
            int observed = quantity(batch.outputs, separating.mOutputs[0]) - main;
            require(observed >= 0, "Primary booster lost deterministic main product");
            trials += main;
            extras += observed;
        }
        // A six-sigma range tests real nonzero bonus and catches multiplication along the path.
        double mean = trials * .005D;
        double deviation = Math.sqrt(mean * .995D);
        require(extras > 0 && Math.abs(extras - mean) <= 6 * deviation, "Primary bonus is inconsistent with one 0.5% draw");
        require(acid.amount == availableAcid && copper.stackSize == 64, "Planning mutated borrowed resources");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "native registered recipes and production planner; ownership, world output and reboot use separate cases");
        details.put("copper", copper.toString());
        details.put("nativeDurations", Arrays.asList(crushing.mDuration, milling.mDuration, separating.mDuration));
        details.put("consumedOre", input);
        details.put("copperDust", quantity(ranked.outputs, separating.mOutputs[0]));
        details.put("steam", ranked.steam);
        details.put("energy", ranked.energy);
        details.put("bonusTrials", trials);
        details.put("bonusExpected", mean);
        details.put("bonusActual", extras);
        details.put("registeredRecipes", registrations);
        details.put("graphControls", "bounded generator syntax, allow/deny per port, persisted order and switch");
        return new TestLabReport.Result(TestLabReport.Status.PASS, "Native selection, priority, batch costs and seeded bonus", details);
    }

    private void verifyGraphFilters(CenterGraph early, List<CenterGraph.Node> dry, GTRecipe crushing, GTRecipe separating,
        int[] bronze) {
        String entry="^(?!(?:ore|variantOre|rawOre)(?:CopperSulphate|Tin)$)(?:ore|rawOre|variantOre)(?:Any)?Copper.*$";
        List<String> filter=Arrays.asList("^(?=crushed[A-Z])crushed(?:Any)?(?:Copper|Iron)$");
        List<CenterGraph.Node> nodes=new ArrayList<>(dry);
        nodes.set(0,new CenterGraph.Node(CenterProcess.CRUSHER,new int[]{1},0,
            Arrays.asList(new CenterGraph.OutletFilter(0,true,filter))));
        CenterGraph graph=new CenterGraph(10,"Copper refinement",0,true,Arrays.asList(entry),nodes);
        CenterGraph saved=CenterGraph.load(graph.save());
        require(saved.id==10 && saved.name.equals(graph.name) && saved.nodes.get(0).filters().size()==1,
            "Graph identity or output filter not persisted");
        ItemStack tin=GTOreDictUnificator.get(OrePrefixes.rawOre,Materials.Tin,4);
        require(tin!=null,"Missing native tin sample");
        ItemStack[] sample={copper,tin};
        CenterPlanner.Batch first=plan(Arrays.asList(early,saved),sample,NO_FLUIDS,bronze,0,1,0,0,SEED);
        CenterPlanner.Batch afterDrag=plan(Arrays.asList(saved,early),sample,NO_FLUIDS,bronze,0,1,0,0,SEED);
        require(quantity(afterDrag.consumed,tin)==0,"Negative assertion accepted tin");
        require(quantity(first.outputs,crushing.mOutputs[0])==128
                && quantity(afterDrag.outputs,separating.mOutputs[0])==128,
            "Same priority must follow persisted tab order");
        nodes.set(0,new CenterGraph.Node(CenterProcess.CRUSHER,new int[]{1},0,
            Arrays.asList(new CenterGraph.OutletFilter(0,false,filter))));
        CenterGraph denied=new CenterGraph(10,graph.name,0,true,Arrays.asList(entry),nodes);
        CenterPlanner.Batch divert=plan(Arrays.asList(denied,early),sample,NO_FLUIDS,bronze,0,1,0,0,SEED);
        require(quantity(divert.outputs,crushing.mOutputs[0])==128
                && quantity(divert.outputs,separating.mOutputs[0])==0,
            "Denied outlet must fall through to the shared output bus");
        try {
            new CenterGraph(9,"",0,true,Arrays.asList("^(a+)+$"),dry);
            throw new AssertionError("Nested unbounded repetition was accepted");
        } catch (IllegalArgumentException expected) {
            // Only the bounded card syntax is accepted.
        }
    }

    private static Map<String, Integer> verifyRegistrations() {
        int workbench = 0, assembler = 0;
        requireCrafting(GTSRItemList.OreIntegrationCenter.get(1));
        workbench++;
        for (int grade = 1; grade <= 3; grade++) {
            ItemStack shell = CenterModuleItems.shell(grade);
            if (grade == 1) {
                requireCrafting(shell);
                workbench++;
            } else {
                requireAssembler(shell, null, null);
                assembler++;
            }
            for (CenterModule module : CenterModule.values()) {
                ItemStack pack = CenterModuleItems.stack(module, grade);
                if (module.tier == 0 && grade == 1) {
                    requireCrafting(pack);
                    workbench++;
                } else {
                    ItemStack previous = grade > 1 ? CenterModuleItems.stack(module, grade - 1) : null;
                    requireAssembler(pack, shell, previous);
                    assembler++;
                }
            }
        }
        require(workbench == 12 && assembler == 43, "Incomplete registered crafting or assembly catalog");
        Map<String, Integer> registered = new LinkedHashMap<>();
        registered.put("workbench", workbench);
        registered.put("assembler", assembler);
        return registered;
    }

    private static void requireCrafting(ItemStack output) {
        int found = 0;
        for (Object raw : CraftingManager.getInstance().getRecipeList()) {
            if (!(raw instanceof IRecipe recipe)) continue;
            ItemStack candidate = recipe.getRecipeOutput();
            if (GTUtility.areStacksEqual(candidate, output, false) && candidate.stackSize == 1) found++;
        }
        require(found == 1, "Expected exactly one workbench recipe for " + output + ", observed " + found);
    }

    private static void requireAssembler(ItemStack output, ItemStack shell, ItemStack previous) {
        int found = 0;
        for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes()) {
            if (!recipe.mEnabled || recipe.mFakeRecipe || recipe.mOutputs.length != 1
                || !GTUtility.areStacksEqual(recipe.mOutputs[0], output, false)) continue;
            if (recipe.mOutputs[0].stackSize != 1) continue;
            require(shell == null || quantity(Arrays.asList(recipe.mInputs), shell) == 1, "Incorrect shell input for " + output);
            require(previous == null || quantity(Arrays.asList(recipe.mInputs), previous) == 1, "Incorrect prior pack for " + output);
            found++;
        }
        require(found == 1, "Expected exactly one assembler recipe for " + output + ", observed " + found);
    }

    private TestLabReport.Result stressTick(WorldServer world) {
        int width = CONTROLLERS[phase / 2];
        boolean recompile = phase % 2 == 0;
        int[] moduleGrades = grades(CenterModule.CRUSHER, CenterModule.LOGISTICS);
        long frameStart = System.nanoTime();
        boolean measuring = invocationsInPhase >= 16;
        long largestBegin = 0, largestSlice = 0, largestSliceStart = 0, largestSliceCpu = -1;
        while (queue.size() < width && startedInPhase < SAMPLE_COUNT + 16) {
            long start = System.nanoTime();
            List<CenterGraph> graphs = recompile ? pressureGraphs() : compiled;
            CenterPlanner.Session session = CenterPlanner.start(
                graphs, oreBank, NO_FLUIDS, moduleGrades, 3, 6, 2048, 131072, 4500, SEED);
            long elapsed = System.nanoTime() - start;
            largestBegin = Math.max(largestBegin, elapsed);
            boolean measured = startedInPhase++ >= 16;
            if (measured) starts.add(elapsed);
            queue.addLast(new PressureJob(session, measured, elapsed));
            maxQueued = Math.max(maxQueued, queue.size());
            if (System.nanoTime() - frameStart >= ADMISSION_NS) break;
        }
        int ready = queue.size();
        for (int i = 0; i < ready && System.nanoTime() - frameStart < ADMISSION_NS; i++) {
            PressureJob job = queue.removeFirst();
            long cpuStart = measuring && threadClock != null ? threadClock.getCurrentThreadCpuTime() : -1;
            long start = System.nanoTime();
            boolean done = job.session.advance(
                32, Math.min(SOFT_JOB_NS, Math.max(1L, SOFT_FRAME_NS - (start - frameStart))));
            long elapsed = System.nanoTime() - start;
            long cpuEnd = cpuStart >= 0 ? threadClock.getCurrentThreadCpuTime() : -1;
            if (elapsed > largestSlice) {
                largestSlice = elapsed;
                largestSliceStart = start - frameStart;
                largestSliceCpu = cpuEnd >= cpuStart && cpuStart >= 0 ? cpuEnd - cpuStart : -1;
            }
            job.nanoseconds += elapsed;
            if (job.measured) slices.add(elapsed);
            if (!done) {
                queue.addLast(job);
                continue;
            }
            CenterPlanner.Batch batch = job.session.batch();
            require(batch != null && batch.ticks > 0 && !batch.consumed.isEmpty() && !batch.outputs.isEmpty(),
                "Empty pressure sample");
            require(oreBank[0].stackSize == 2, "Pressure plan mutated supplied stacks");
            // Check first and last per workload outside the timed slice.
            if (invocationsInPhase == 0 || invocationsInPhase + 1 == SAMPLE_COUNT + 16) {
                List<Object> actual = signature(batch);
                if (pressureSignature == null) pressureSignature = actual;
                require(pressureSignature.equals(actual), "Cold/warm work or slicing changed pressure output");
            }
            if (job.measured) invocations[measuredJobs++] = job.nanoseconds;
            invocationsInPhase++;
            served++;
        }
        if (measuring) {
            long elapsed = System.nanoTime() - frameStart;
            frames.add(elapsed);
            pressureFrames.add(new PressureFrame(System.currentTimeMillis(), world.getTotalWorldTime(),
                MinecraftServer.getServer().getTickCounter(), elapsed, largestBegin, largestSlice, largestSliceStart,
                largestSliceCpu));
        }
        if (invocationsInPhase < SAMPLE_COUNT + 16) return null;
        require(measuredJobs == SAMPLE_COUNT && maxQueued == width, "Invalid pressure sample or concurrency");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("controllerLoad", width);
        row.put("peakConcurrentPlans", maxQueued);
        row.put("graphState", recompile ? "recompile per job" : "shared compiled graphs");
        row.put("finishedPlanNanos", statistics(invocations.clone(), Long.MAX_VALUE));
        row.put("beginNanos", statistics(samples(starts), SOFT_JOB_NS));
        row.put("sliceNanos", statistics(samples(slices), SOFT_JOB_NS));
        row.put("framePlanningNanos", statistics(samples(frames), SOFT_FRAME_NS));
        row.put("worstFrames", worstPressureFrames(Comparator.comparingLong(f -> f.elapsed)));
        row.put("worstAdvanceCalls", worstPressureFrames(Comparator.comparingLong(f -> f.largestSlice)));
        measurements.add(row);
        frames.clear();
        pressureFrames.clear();
        starts.clear();
        slices.clear();
        invocationsInPhase = startedInPhase = measuredJobs = maxQueued = 0;
        if (++phase < CONTROLLERS.length * 2) return null;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "production planner sessions on server thread; framePlanningNanos measures probe work, not full server tick");
        details.put("inputIdentities", oreBank.length);
        details.put("graphsPerJob", GRAPH_COUNT);
        details.put("jobsIncludingWarmup", served);
        details.put("frameAdmissionNanos", ADMISSION_NS);
        details.put("threadCpuTimeAvailable", threadClock != null);
        details.put("performance", measurements);
        return new TestLabReport.Result(TestLabReport.Status.PASS,
            "All native pressure sessions produced matching plans; inspect independent latency budget counters", details);
    }

    private List<Map<String, Object>> worstPressureFrames(Comparator<PressureFrame> rank) {
        List<Map<String, Object>> tail = new ArrayList<>();
        pressureFrames.stream().sorted(rank.reversed()).limit(8).forEach(frame -> tail.add(frame.fields()));
        return tail;
    }

    private static final class PressureFrame {

        final long epochMillis, worldTick, elapsed, largestBegin, largestSlice, largestSliceStart, largestSliceCpu;
        final int serverTick;

        PressureFrame(long epochMillis, long worldTick, int serverTick, long elapsed, long largestBegin,
            long largestSlice, long largestSliceStart, long largestSliceCpu) {
            this.epochMillis = epochMillis;
            this.worldTick = worldTick;
            this.serverTick = serverTick;
            this.elapsed = elapsed;
            this.largestBegin = largestBegin;
            this.largestSlice = largestSlice;
            this.largestSliceStart = largestSliceStart;
            this.largestSliceCpu = largestSliceCpu;
        }

        Map<String, Object> fields() {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("epochMillisEnd", epochMillis);
            detail.put("serverTick", serverTick);
            detail.put("worldTick", worldTick);
            detail.put("frameNanos", elapsed);
            detail.put("largestBeginNanos", largestBegin);
            detail.put("largestAdvanceNanos", largestSlice);
            detail.put("largestAdvanceStartOffsetNanos", largestSliceStart);
            if (largestSliceCpu >= 0) detail.put("largestAdvanceThreadCpuNanos", largestSliceCpu);
            return detail;
        }
    }

    private static final class PressureJob {
        final CenterPlanner.Session session;
        final boolean measured;
        long nanoseconds;

        PressureJob(CenterPlanner.Session session, boolean measured, long nanoseconds) {
            this.session = session;
            this.measured = measured;
            this.nanoseconds = nanoseconds;
        }
    }

    private static long[] samples(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).toArray();
    }

    private static Map<String, Number> statistics(long[] samples, long budget) {
        require(samples.length > 0, "No effective latency samples");
        Arrays.sort(samples);
        Map<String, Number> result = new LinkedHashMap<>();
        result.put("samples", samples.length);
        result.put("p50", percentile(samples, .50));
        result.put("p95", percentile(samples, .95));
        result.put("p99", percentile(samples, .99));
        result.put("max", samples[samples.length - 1]);
        if (budget < Long.MAX_VALUE) {
            result.put("targetNanos", budget);
            result.put("overTarget", Arrays.stream(samples).filter(n -> n > budget).count());
        }
        return result;
    }

    private static ItemStack[] collectOreBank(ItemStack copper) {
        List<ItemStack> result = new ArrayList<>();
        ItemStack first = copper.copy();
        first.stackSize = 2;
        result.add(first);
        Set<Long> identities = new HashSet<>();
        identities.add(identity(first));
        String[] names = OreDictionary.getOreNames();
        Arrays.sort(names);
        for (String name : names) {
            if (!name.startsWith("rawOre")) continue;
            for (ItemStack stack : OreDictionary.getOres(name)) {
                if (stack == null || stack.getItem() == null || stack.getItemDamage() == OreDictionary.WILDCARD_VALUE)
                    continue;
                if (!identities.add(identity(stack))) continue;
                ItemStack copy = stack.copy();
                copy.stackSize = 2;
                result.add(copy);
                if (result.size() == 128) return result.toArray(NO_ITEMS);
            }
        }
        throw new IllegalStateException("Fewer than 128 registered raw ore identities");
    }

    private static long identity(ItemStack stack) {
        return (long) Item.getIdFromItem(stack.getItem()) << 32 | stack.getItemDamage() & 0xffffffffL;
    }

    private static List<CenterGraph> pressureGraphs() {
        List<CenterGraph> graphs = new ArrayList<>();
        for (int i = 0; i < GRAPH_COUNT - 1; i++) {
            graphs.add(graph(GRAPH_COUNT - i, true, "^rawOreCopper$", Arrays.asList(node(CenterProcess.CRUSHER, -1))));
        }
        graphs.add(new CenterGraph(0, true, Arrays.asList(), Arrays.asList(node(CenterProcess.CRUSHER, -1))));
        return graphs;
    }

    private static GTRecipe nativeRecipe(RecipeMap<?> map, ItemStack input, FluidStack[] fluids) {
        require(map != null && input != null, "Missing native map or input");
        GTRecipe recipe = map.findRecipeQuery().items(input.copy()).fluids(fluids).voltage(32L).find();
        require(recipe != null && recipe.mOutputs.length > 0, "Native query failed for " + input);
        return recipe;
    }

    private static CenterGraph.Node node(CenterProcess process, int mainTarget) {
        return new CenterGraph.Node(process, new int[] { mainTarget, -1, -1 });
    }

    private static CenterGraph graph(int priority, boolean enabled, String rule, List<CenterGraph.Node> nodes) {
        return new CenterGraph(priority, enabled, Arrays.asList(rule), nodes);
    }

    private static int[] grades(CenterModule... modules) {
        int[] result = new int[CenterModule.values().length];
        for (CenterModule module : modules) result[module.ordinal()] = 1;
        return result;
    }

    private static CenterPlanner.Batch plan(
        List<CenterGraph> configs, ItemStack[] items, FluidStack[] fluids, int[] grades,
        int tier, int sectors, long voltage, long power, long seed
    ) {
        CenterPlanner.Batch batch = CenterPlanner.plan(configs, items, fluids, grades, tier, sectors, voltage, power, 0, seed);
        require(batch != null, "Production planner rejected a native fixture");
        return batch;
    }

    private static int quantity(List<ItemStack> stacks, ItemStack target) {
        return stacks.stream().filter(stack -> GTUtility.areStacksEqual(stack, target, false)).mapToInt(s -> s.stackSize).sum();
    }

    private static List<Object> signature(CenterPlanner.Batch batch) {
        return Arrays.asList(batch.outputs.toString(), batch.consumed.toString(), batch.consumedFluids.toString(),
            batch.outputFluids.toString(), batch.ticks, batch.steam, batch.energy);
    }

    private static long percentile(long[] sorted, double p) {
        return sorted[(int) Math.ceil(sorted.length * p) - 1];
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
