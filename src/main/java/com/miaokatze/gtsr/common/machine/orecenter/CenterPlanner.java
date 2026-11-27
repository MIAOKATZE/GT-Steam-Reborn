package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.BoosterType;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;

import gregtech.api.enums.ItemList;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTUtility.ItemId;

public final class CenterPlanner {

    private static final int BASE_WORK = 4096, MAX_LOTS = 4096, MAX_CANDIDATES = 32;
    static final int MAX_OUTPUTS = 2048, MAX_ROOTS = 4_000_000, MAX_BATCH_ROOTS = 64_000_000;
    private static final int MAX_BATCH_WORK = Integer.MAX_VALUE - 1;
    private static final double BATCH_TICKS = 128;
    private static final int[] STEAM = { 4000, 24000, 112000, 384000 }, WIDTH = { 24, 48, 64, 128 },
        VOLTAGE = { 32, 128, 512, 2048 }, CONTROL_EU = { 0, 32, 128, 512 };
    static final int[] HEAT = { 0, 0, 2700, 4500 };
    private static final double[] MACHINE_SPEED = { 1, 2.5, 5, 10 }, GRADE_STEAM = { 1, 1.45, 2.1 };
    private static final int[] LOGISTICS_PARALLEL = { 240, 1440, 11520 };
    private static final CenterModule[] MODULES = CenterModule.values();
    private static final Pattern RAW = Pattern.compile("(?:ore|rawOre|variantOre)[A-Z0-9].*");
    private static final Pattern FEED = Pattern
        .compile("(?:ore|rawOre|variantOre|crushed|dustImpure|dustPure)[A-Z0-9].*");

    public static final class Batch {

        public final List<ItemStack> consumed, outputs;
        public final List<ItemStack> bonuses;
        public final List<FluidStack> consumedFluids, outputFluids;
        public final int ticks, parallel, capacity, roots, chargedRounds, batchModifier;
        public final boolean batched;
        public final double weightedTicks;
        public final List<ChainTime> chains;
        final int[] boostFluids;
        final int usedModules;
        public final long steam, energy, processingSteam, maintenanceSteam, processingEnergy, maintenanceEnergy;

        private Batch(Session plan, int ticks, long processingSteam, long maintenanceSteam, long processingEnergy,
            long maintenanceEnergy) {
            this.consumed = new ArrayList<>(plan.consumed.values());
            this.outputs = new ArrayList<>(plan.outputs.values());
            this.bonuses = new ArrayList<>(plan.bonuses.values());
            this.boostFluids = new int[BOOSTS.length];
            this.chargedRounds = plan.chargedRounds();
            if (!plan.ignoreFluids) for (int i = 0; i < boostFluids.length; i++)
                boostFluids[i] = Math.multiplyExact(chargedRounds, plan.boostPrice[i]);
            int used = 0;
            for (CenterModule module : MODULES)
                if (module.kind == 1 && plan.weight[module.ordinal()] == 1) used |= 1 << module.ordinal();
            for (int i = 0; i < BOOSTS.length; i++)
                if (plan.gain[i] > 0) used |= 1 << (CenterModule.SPEED.ordinal() + i);
            this.usedModules = used;
            this.consumedFluids = plan.consumedFluids;
            this.outputFluids = plan.outputFluids;
            this.ticks = ticks;
            this.roots = plan.rootPaid;
            this.parallel = plan.parallelLimit();
            this.capacity = plan.capacity;
            this.batched = plan.batched;
            this.batchModifier = plan.batchRounds;
            this.weightedTicks = plan.weightedTicks / plan.rootPaid / plan.machineSpeed();
            this.chains = new ArrayList<>();
            for (Origin origin : plan.origins.values())
                if (origin.roots > 0) chains.add(new ChainTime(origin, plan.machineSpeed()));
            this.processingSteam = processingSteam;
            this.maintenanceSteam = maintenanceSteam;
            this.processingEnergy = processingEnergy;
            this.maintenanceEnergy = maintenanceEnergy;
            this.steam = Math.addExact(processingSteam, maintenanceSteam);
            this.energy = Math.addExact(processingEnergy, maintenanceEnergy);
        }
    }

    public static final class ChainTime {

        public final long graphId;
        public final String name;
        public final int roots;
        public final double ticks;

        private ChainTime(Origin origin, double speed) {
            graphId = origin.graphId;
            name = origin.name;
            roots = origin.roots;
            ticks = origin.path / speed;
        }
    }

    /** Provenance only: carried through the existing lots, never a second recipe traversal. */
    private static final class Origin {

        final long graphId;
        final String name;
        int roots;
        double path;
        boolean rejected;

        Origin(long graphId, ItemStack sample) {
            this.graphId = graphId;
            this.name = sample.getDisplayName();
        }
    }

    private static final class LotKey {

        final ItemId item;
        final Origin origin;

        LotKey(ItemStack stack, Origin origin) {
            this.item = itemKey(stack);
            this.origin = origin;
        }

        @Override
        public int hashCode() {
            return 31 * item.hashCode() + System.identityHashCode(origin);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof LotKey)) return false;
            LotKey key = (LotKey) other;
            return origin == key.origin && item.equals(key.item);
        }
    }

    private static final class Lot {

        final ItemStack stack;
        final boolean incoming, primary, seekingEntry;
        final Origin origin;
        double path;

        Lot(ItemStack stack, boolean incoming, boolean primary, boolean seekingEntry, Origin origin, double path) {
            this.stack = stack;
            this.incoming = incoming;
            this.primary = primary;
            this.seekingEntry = seekingEntry;
            this.origin = origin;
            this.path = path;
        }
    }

    private static final class WorkGraph {

        final CenterGraph graph;
        final List<LotQueue> input;

        WorkGraph(CenterGraph graph) {
            this.graph = graph;
            input = new ArrayList<>();
            for (int i = 0; i < graph.nodes.size(); i++) input.add(new LotQueue());
        }
    }

    private static final class LotQueue {

        final List<Lot> lots = new ArrayList<>();
        int cursor;
        private final Map<LotKey, Lot> incoming = new HashMap<>(), main = new HashMap<>(), side = new HashMap<>();

        void add(ItemStack item, boolean feed, boolean primary, boolean seekingEntry, Origin origin, double path) {
            Map<LotKey, Lot> index = feed ? incoming : primary ? main : side;
            LotKey key = new LotKey(item, origin);
            Lot prior = index.get(key);
            if (prior == null) {
                Lot lot = new Lot(item, feed, primary, seekingEntry, origin, path);
                lots.add(lot);
                index.put(key, lot);
            } else {
                prior.stack.stackSize = Math.addExact(prior.stack.stackSize, item.stackSize);
                prior.path = Math.max(prior.path, path);
            }
        }

        Lot next() {
            Lot lot = lots.get(cursor++);
            (lot.incoming ? incoming : lot.primary ? main : side).remove(new LotKey(lot.stack, lot.origin));
            return lot;
        }
    }

    private static final class FeedRoute {

        final LotQueue queue;
        final boolean raw, intermediate;
        final long graphId;

        FeedRoute(LotQueue queue, boolean raw, boolean intermediate, long graphId) {
            this.queue = queue;
            this.raw = raw;
            this.intermediate = intermediate;
            this.graphId = graphId;
        }
    }

    public static Batch plan(List<CenterGraph> graphs, ItemStack[] items, FluidStack[] fluids, int[] grades, int tier,
        int sectors, long voltage, long availableEUt, int heat, long seed) {
        Session planner = start(graphs, items, fluids, grades, tier, sectors, voltage, availableEUt, heat, seed);
        while (!planner.advance(32, Long.MAX_VALUE)) {
            // Offline/test entry point. The controller resumes one bounded slice per server tick.
        }
        return planner.batch();
    }

    public static Session start(List<CenterGraph> graphs, ItemStack[] items, FluidStack[] fluids, int[] grades,
        int tier, int sectors, long voltage, long availableEUt, int heat, long seed) {
        int[][] counts = new int[MODULES.length][3];
        for (int i = 0; i < grades.length && i < counts.length; i++) {
            int grade = grades[i];
            if (grade >= 1 && grade <= 3) counts[i][grade - 1] = 1;
        }
        return new Session(graphs, items, fluids, counts, tier, sectors, voltage, availableEUt, heat, seed);
    }

    static final class Boost {

        final BoosterType type;
        final double[] gain, steam, eu;
        final int[] price;
        final double cap;

        Boost(BoosterType type, double[] gain, int[] price, double[] steam, double[] eu, double cap) {
            this.type = type;
            this.gain = gain;
            this.price = price;
            this.steam = steam;
            this.eu = eu;
            this.cap = cap;
        }
    }

    private static final Boost[] BOOSTS = {
        new Boost(
            BoosterType.SPEED,
            new double[] { 5, 10, 20 },
            new int[] { 4, 14, 45 },
            new double[] { .05, .12, .24 },
            new double[] { .03, .07, .14 },
            50),
        new Boost(
            BoosterType.PARALLEL,
            new double[] { 4, 8, 16 },
            new int[] { 4, 12, 36 },
            new double[] { .04, .10, .20 },
            new double[] { .02, .06, .12 },
            48),
        new Boost(
            BoosterType.PRIMARY_OUTPUT,
            new double[] { .5, 1, 2 },
            new int[] { 8, 24, 75 },
            new double[] { .08, .16, .30 },
            new double[] { .04, .09, .18 },
            8),
        new Boost(
            BoosterType.SECONDARY_OUTPUT,
            new double[] { 1, 2.5, 5 },
            new int[] { 3, 10, 30 },
            new double[] { .05, .12, .22 },
            new double[] { .03, .07, .14 },
            20) };

    static Fluid boostFluid(int index) {
        return MTEBasicAmplifierUnit.resolveBoosterFluid(BOOSTS[index].type);
    }

    public static final class Session {

        private final List<WorkGraph> graphs = new ArrayList<>();
        private final Map<ItemId, ItemStack> consumed = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
        private final Map<ItemId, ItemStack> bonuses = new LinkedHashMap<>();
        private final List<FluidStack> consumedFluids = new ArrayList<>(), outputFluids = new ArrayList<>();
        private final Map<ItemId, ItemStack> auxiliary = new LinkedHashMap<>();
        private final Map<ItemId, FeedRoute> feedRoutes = new HashMap<>();
        private final Map<ItemId, Origin> origins = new LinkedHashMap<>();
        private final List<FluidStack> available = new ArrayList<>();
        private final List<ItemStack> inputs = new ArrayList<>();
        private final List<ItemStack> deferredInputs = new ArrayList<>();
        private final int[][] counts;
        private final int[] best = new int[MODULES.length];
        private final double[] weight = new double[MODULES.length], gain = new double[4];
        private final int[] boostPrice = new int[4];
        private final int tier, sectors, heat, rootLimit, batchRounds;
        private final long voltage, maxEUt;
        private final boolean ignoreFluids, ignoreEnergy;
        private final Random random;
        private int graphIndex, nodeIndex, inputIndex, deferredIndex, workLimit, admissionLimit, parallel;
        private int capacity, capacityRetry;
        private boolean batched;
        private boolean queueMode;
        private int executions, rootPaid, proposedRoots;
        private double weightedTicks, workSteam, recipeEU, steamMultiplier = 1, euMultiplier = 1;
        private boolean finished, failed, workLimitReached, boostConfigured;
        private Batch result;

        Session(List<CenterGraph> configured, ItemStack[] items, FluidStack[] fluids, int[][] counts, int tier,
            int sectors, long voltage, long maxEUt, int heat, long seed) {
            this(configured, items, fluids, counts, tier, sectors, voltage, maxEUt, heat, seed, false, false);
        }

        Session(List<CenterGraph> configured, ItemStack[] items, FluidStack[] fluids, int[][] counts, int tier,
            int sectors, long voltage, long maxEUt, int heat, long seed, boolean ignoreFluids, boolean ignoreEnergy) {
            this(
                configured,
                items,
                fluids,
                counts,
                tier,
                sectors,
                voltage,
                maxEUt,
                heat,
                seed,
                ignoreFluids,
                ignoreEnergy,
                MAX_ROOTS,
                1);
        }

        Session(List<CenterGraph> configured, ItemStack[] items, FluidStack[] fluids, int[][] counts, int tier,
            int sectors, long voltage, long maxEUt, int heat, long seed, boolean ignoreFluids, boolean ignoreEnergy,
            int rootLimit, int batchRounds) {
            if (tier < 0 || tier > 3 || sectors < 1 || sectors > 6 || configured.size() > CenterGraph.MAX_GRAPHS) {
                throw new IllegalArgumentException("Invalid center configuration");
            }
            this.tier = tier;
            this.sectors = sectors;
            this.voltage = voltage;
            this.maxEUt = maxEUt;
            this.heat = heat;
            this.rootLimit = Math.max(1, Math.min(MAX_BATCH_ROOTS, rootLimit));
            this.batchRounds = Math.max(1, Math.min(128, batchRounds));
            this.ignoreFluids = ignoreFluids;
            this.ignoreEnergy = ignoreEnergy;
            this.random = new Random(seed);
            this.counts = counts;
            for (CenterModule module : MODULES) {
                int id = module.ordinal();
                for (int g = 0; g < 3; g++) if (counts[id][g] > 0 && module.acceptsGrade(g + 1, tier)) best[id] = g + 1;
                if (best[id] > 0) weight[id] = .10;
            }
            for (FluidStack fluid : fluids)
                if (fluid != null && fluid.amount > 0) mergeFluid(available, fluid, fluid.amount);
            List<CenterGraph> sorted = new ArrayList<>(configured);
            sorted.sort(
                Comparator.comparingInt((CenterGraph g) -> g.priority)
                    .reversed());
            for (CenterGraph graph : sorted) {
                if (!graph.enabled || !graph.runnable()
                    || graph.nodes.stream()
                        .anyMatch(n -> !n.process.installed(best)))
                    continue;
                graphs.add(new WorkGraph(graph));
                for (CenterGraph.Node node : graph.nodes) weight[node.process.module.ordinal()] = .20;
            }
            for (ItemStack stack : items) if (stack != null && stack.stackSize > 0) inputs.add(stack.copy());
        }

        private void prepare(ItemStack stack, boolean reserveOthers) {
            FeedRoute route = feedRoutes.computeIfAbsent(itemKey(stack), key -> route(stack));
            LotQueue queue = route.queue;
            if (queue != null && proposedRoots < admissionLimit) {
                Origin known = origins.get(itemKey(stack));
                if (known != null && known.rejected) return;
                int quantity = Math.min(stack.stackSize, admissionLimit - proposedRoots);
                if (reserveOthers) {
                    int slots = inputs.size() - inputIndex + 1;
                    quantity = Math.min(quantity, (admissionLimit - proposedRoots + slots - 1) / slots);
                    if (quantity < stack.stackSize) {
                        ItemStack deferred = stack.copy();
                        deferred.stackSize -= quantity;
                        deferredInputs.add(deferred);
                    }
                }
                stack.stackSize = quantity;
                Origin origin = origins.computeIfAbsent(itemKey(stack), key -> new Origin(route.graphId, stack));
                queue.add(stack, true, route.raw, route.intermediate, origin, 0);
                if (queue.lots.size() > MAX_LOTS) failed = workLimitReached = true;
                proposedRoots += quantity;
            } else if (queue == null && auxiliary.size() < MAX_LOTS) {
                merge(auxiliary, stack, stack.stackSize);
            }
        }

        private FeedRoute route(ItemStack stack) {
            String[] tags = CenterGraph.tags(stack);
            WorkGraph graph = pick(stack, tags);
            if (graph == null) return new FeedRoute(null, false, false, 0);
            boolean raw = anyTag(tags, RAW);
            int entry = graph.graph.entry(stack, tags);
            RecipeMap<?> map = graph.graph.nodes.get(entry).process.recipes();
            // Use the existing native input index, not a second recipe search or a recipe-table scan.
            if (raw && (map == null || !map.containsInput(stack))) return new FeedRoute(null, false, false, 0);
            return new FeedRoute(graph.input.get(entry), raw, !raw && anyTag(tags, FEED), graph.graph.id);
        }

        private WorkGraph pick(ItemStack stack, String[] rules) {
            WorkGraph fallback = null;
            for (WorkGraph graph : graphs) {
                if (!graph.graph.fallback() && graph.graph.matches(stack, rules)) return graph;
                if (fallback == null && graph.graph.fallback() && anyTag(rules, FEED)) fallback = graph;
            }
            return fallback;
        }

        private int configureBoosts() {
            // The native batch modifier bounds the time window, not the number of sub-tick recipe groups.
            // Dose affordability still limits every actual P-sized group admitted to this transaction.
            int affordableRounds = batchRounds > 1 ? rootLimit : 1;
            for (int i = 0; i < BOOSTS.length; i++) {
                Boost b = BOOSTS[i];
                int module = CenterModule.SPEED.ordinal() + i;
                if (best[module] == 0) continue;
                Fluid ingredient = boostFluid(i);
                if (ingredient == null) continue;
                for (int g = 0; g < best[module]; g++) {
                    int required = b.price[g];
                    for (int repeat = 0; repeat < counts[module][g]; repeat++) {
                        FluidStack fluid = new FluidStack(ingredient, required);
                        if (!ignoreFluids && !useFluid(available, fluid, required)) break;
                        if (!ignoreFluids) mergeFluid(consumedFluids, fluid, required);
                        boostPrice[i] += b.price[g];
                        gain[i] = Math.min(b.cap, gain[i] + b.gain[g]);
                        steamMultiplier += b.steam[g];
                        euMultiplier += b.eu[g];
                    }
                }
                if (!ignoreFluids && boostPrice[i] > 0 && affordableRounds > 1) {
                    FluidStack fluid = new FluidStack(ingredient, 1);
                    long remaining = 0;
                    for (FluidStack stock : available) if (stock.isFluidEqual(fluid)) remaining += stock.amount;
                    affordableRounds = (int) Math.min(affordableRounds, 1 + remaining / boostPrice[i]);
                }
            }
            return affordableRounds;
        }

        public Batch batch() {
            return result;
        }

        void queueMode(boolean enabled) {
            queueMode = enabled;
        }

        int parallelLimit() {
            if (!boostConfigured) return 0;
            if (parallel == 0) parallel = (int) Math.ceil(processingWidth());
            return parallel;
        }

        int smallerRootLimit() {
            if (capacityRetry > 0) return capacityRetry;
            return workLimitReached && proposedRoots > 1 ? proposedRoots / 2 : 0;
        }

        int executions() {
            return executions;
        }

        int workLimit() {
            return workLimit;
        }

        public boolean advance(int queryBudget, long timeBudgetNanos) {
            if (finished) return true;
            long start = System.nanoTime();
            int used = 0;
            if (!boostConfigured) {
                int affordableRounds = configureBoosts();
                boostConfigured = true;
                admissionLimit = (int) Math.min(rootLimit, (long) parallelLimit() * affordableRounds);
                workLimit = MAX_BATCH_WORK;
            }
            while (!failed && inputIndex < inputs.size()) {
                if (used >= queryBudget || used > 0 && System.nanoTime() - start >= timeBudgetNanos) return false;
                prepare(inputs.get(inputIndex++), true);
                used++;
            }
            while (!failed && deferredIndex < deferredInputs.size() && proposedRoots < admissionLimit) {
                if (used >= queryBudget || used > 0 && System.nanoTime() - start >= timeBudgetNanos) return false;
                prepare(deferredInputs.get(deferredIndex++), false);
                used++;
            }
            if (proposedRoots == 0 || queueMode && proposedRoots < parallelLimit()) {
                finished = true;
                return true;
            }
            while (graphIndex < graphs.size() && !failed) {
                if (used >= queryBudget || used > 0 && System.nanoTime() - start >= timeBudgetNanos) return false;
                WorkGraph work = graphs.get(graphIndex);
                if (nodeIndex == work.graph.nodes.size()) {
                    graphIndex++;
                    nodeIndex = 0;
                    continue;
                }
                LotQueue lots = work.input.get(nodeIndex);
                if (lots.cursor == lots.lots.size()) {
                    nodeIndex++;
                    continue;
                }
                Lot lot = lots.next();
                if (lot.stack.stackSize > 0) {
                    try {
                        process(work, work.graph.nodes.get(nodeIndex), lot);
                    } catch (ArithmeticException overflow) {
                        failed = workLimitReached = true;
                    }
                    used++;
                }
            }
            if (!failed && proposedRoots < admissionLimit && deferredIndex < deferredInputs.size()) {
                // Refill seats released by native recipe rejection using the same bounded queues.
                // Their cursors retain completed work; no recipe pre-scan or second planner is involved.
                graphIndex = nodeIndex = 0;
                return false;
            }
            finished = true;
            if (!failed && rootPaid >= (queueMode ? parallelLimit() : 1)) {
                try {
                    result = conclude();
                } catch (ArithmeticException overflow) {
                    workLimitReached = true;
                }
            }
            return true;
        }

        private void process(WorkGraph work, CenterGraph.Node node, Lot lot) {
            int index = node.process.module.ordinal();
            long recipeVoltage = ignoreEnergy ? Integer.MAX_VALUE : recipeVoltage(node.process, tier, voltage);
            RecipeMap<?> map = node.process.recipes();
            var candidates = new ArrayList<ItemStack>();
            var matchedAuxiliary = new ArrayList<ItemStack>();
            candidates.add(lot.stack.copy());
            if (node.process.auxiliarySlots() > 0) {
                for (ItemStack stack : auxiliary.values()) if (stack.stackSize > 0 && node.acceptsAuxiliary(stack)) {
                    candidates.add(stack.copy());
                    matchedAuxiliary.add(stack);
                }
            }
            if (node.circuit != 0) candidates.add(ItemList.Circuit_Integrated.getWithDamage(0, node.circuit));
            ItemStack[] itemStocks = candidates.toArray(new ItemStack[0]);
            List<FluidStack> matchedFluids = new ArrayList<>();
            for (FluidStack fluid : available)
                if (fluid.amount > 0 && node.acceptsAuxiliary(fluid)) matchedFluids.add(fluid);
            FluidStack[] fluidStocks = matchedFluids.stream()
                .map(FluidStack::copy)
                .toArray(FluidStack[]::new);
            GTRecipe recipe = findRecipe(map, itemStocks, fluidStocks, recipeVoltage, heat);
            if (ignoreFluids && recipe == null)
                recipe = findWithoutFluidStock(map, node, itemStocks, recipeVoltage, heat);
            int times = 0;
            if (recipe != null) {
                int remainingWork = workLimit - executions;
                int limit = Math.min(lot.stack.stackSize, remainingWork + 1);
                if (ignoreFluids) fluidStocks = virtualFluids(recipe, limit);
                int lo;
                if (recipe.mInputs.length == 1 && recipe.mFluidInputs.length <= 1) {
                    lo = (int) recipe.maxParallelCalculatedByInputs(limit, fluidStocks, itemStocks);
                } else {
                    // Several input slots can compete for the same alternative. Keep the native bounded probes there.
                    lo = 0;
                    int hi = limit;
                    while (lo < hi) {
                        int mid = (lo + hi + 1) >>> 1;
                        if (recipe.isRecipeInputEqual(false, false, mid, fluidStocks, itemStocks)) lo = mid;
                        else hi = mid - 1;
                    }
                }
                if (lo > remainingWork) {
                    failed = workLimitReached = true;
                    return;
                }
                if (lo > 0 && recipe.isRecipeInputEqual(true, false, lo, fluidStocks, itemStocks)
                    && itemStocks[0].stackSize < lot.stack.stackSize) times = lo;
            }
            if (times == 0) {
                if (lot.seekingEntry && nodeIndex + 1 < work.input.size()) work.input.get(nodeIndex + 1)
                    .add(lot.stack, true, false, true, lot.origin, lot.path);
                else if (!lot.incoming) send(lot.stack, false);
                else {
                    proposedRoots -= lot.stack.stackSize;
                    lot.origin.rejected = true;
                }
                return;
            }
            int paid = lot.stack.stackSize - itemStocks[0].stackSize;
            if (lot.incoming) {
                merge(consumed, lot.stack, paid);
                payRoots(lot.origin, paid);
                proposedRoots -= lot.stack.stackSize - paid;
            } else if (itemStocks[0].stackSize > 0) send(itemStocks[0], false);
            for (int i = 0; i < matchedAuxiliary.size(); i++) {
                ItemStack original = matchedAuxiliary.get(i);
                int spent = original.stackSize - itemStocks[i + 1].stackSize;
                if (spent > 0) {
                    merge(consumed, original, spent);
                    original.stackSize -= spent;
                }
            }
            if (!ignoreFluids) for (int i = 0; i < fluidStocks.length; i++) {
                FluidStack original = matchedFluids.get(i);
                int spent = original.amount - fluidStocks[i].amount;
                if (spent > 0) {
                    mergeFluid(consumedFluids, original, spent);
                    original.amount -= spent;
                }
            }
            executions += times;
            weight[index] = 1;
            int grade = best[index] - 1;
            CenterModule module = node.process.module;
            double processingTicks = recipe.mDuration * module.timeMultiplier(grade + 1);
            workSteam += (double) recipe.mDuration * times * module.steamRate * module.energyMultiplier();
            double path = lot.path + processingTicks;
            recordPath(lot.origin, path);
            if (node.process.module.standbyEU > 0) recipeEU += (double) times * recipe.mDuration
                * Math.abs((long) recipe.mEUt)
                * module.energyMultiplier();
            for (int slot = 0; slot < recipe.mOutputs.length; slot++) {
                ItemStack original = recipe.mOutputs[slot];
                if (original == null || original.stackSize <= 0) continue;
                double chance = recipe.getOutputChance(slot) / 10000.0;
                int ordinary = sample(times, chance);
                int extra = 0;
                if (slot > 0 && node.process.mineralYield && chance > 0 && chance < 1) {
                    extra = sample(times - ordinary, Math.min(1 - chance, chance * gain[3] / 100) / (1 - chance));
                }
                int target = node.target(slot, original);
                boolean primary = slot == 0 && lot.primary && node.process.mineralYield;
                produce(work, target, original, (long) ordinary * original.stackSize, primary, lot.origin, path);
                if (extra > 0) {
                    int added = Math.multiplyExact(extra, original.stackSize);
                    if (bonuses.size() < MAX_OUTPUTS) merge(bonuses, original, added);
                    produce(work, target, original, added, false, lot.origin, path);
                }
            }
            for (int i = 0; i < recipe.mFluidOutputs.length; i++) {
                FluidStack f = recipe.mFluidOutputs[i];
                if (f != null && f.amount > 0) {
                    int amount = Math.multiplyExact(f.amount, sample(times, recipe.getFluidOutputChance(i) / 10000.0));
                    if (amount > 0) mergeFluid(outputFluids, f, amount);
                }
            }
            if (outputs.size() + outputFluids.size() > MAX_OUTPUTS) failed = workLimitReached = true;
        }

        private void produce(WorkGraph graph, int target, ItemStack stack, long quantity, boolean primary,
            Origin origin, double path) {
            if (quantity <= 0) return;
            ItemStack out = stack.copy();
            out.stackSize = Math.toIntExact(quantity);
            if (target < 0) send(out, primary);
            else {
                LotQueue queue = graph.input.get(target);
                queue.add(out, false, primary, false, origin, path);
                if (queue.lots.size() > MAX_LOTS) failed = workLimitReached = true;
            }
        }

        private void send(ItemStack stack, boolean eligible) {
            int quantity = stack.stackSize;
            if (eligible && gain[2] > 0) {
                int extra = sample(quantity, gain[2] / 100);
                if (extra > 0 && bonuses.size() < MAX_OUTPUTS) merge(bonuses, stack, extra);
                quantity = Math.addExact(quantity, extra);
            }
            merge(outputs, stack, quantity);
        }

        private int sample(int n, double p) {
            if (n > BASE_WORK && n * Math.min(p, 1 - p) >= 32) return CenterBinomial.sample(random, n, p);
            if (p <= 0 || n == 0) return 0;
            if (p >= 1) return n;
            if (p > .5) return n - sample(n, 1 - p);
            return CenterBinomial.geometric(random, n, p);
        }

        private double machineSpeed() {
            return MACHINE_SPEED[tier] * (1 + gain[0] / BOOSTS[0].cap) * (1 + gain[0] / 100);
        }

        private double processingWidth() {
            long logistics = 0;
            int module = CenterModule.LOGISTICS.ordinal();
            for (int g = 0; g < 3; g++)
                if (moduleGradeAccepted(module, g)) logistics += (long) LOGISTICS_PARALLEL[g] * counts[module][g];
            double base = 10.0 * WIDTH[tier] * (tier + 1) * sectors + logistics;
            double boost = (1 + gain[1] / WIDTH[tier]) * (1 + gain[1] / BOOSTS[1].cap)
                * (1 + 3 * gain[1] / BOOSTS[1].cap);
            return Math.min(MAX_ROOTS, base * boost);
        }

        private boolean moduleGradeAccepted(int module, int grade) {
            return counts[module][grade] > 0 && MODULES[module].acceptsGrade(grade + 1, tier);
        }

        private int chargedRounds() {
            return 1 + (rootPaid - 1) / parallelLimit();
        }

        private void payRoots(Origin origin, int amount) {
            rootPaid += amount;
            origin.roots += amount;
            weightedTicks += origin.path * amount;
        }

        private void recordPath(Origin origin, double path) {
            if (path <= origin.path) return;
            double extra = path - origin.path;
            weightedTicks += extra * origin.roots;
            origin.path = path;
        }

        private boolean settleBoosts() {
            if (ignoreFluids) return true;
            // The first round was reserved before recipe matching. Recipes can also use these fluids,
            // so verify the rest against the actual remaining stock before committing the batch.
            int additionalRounds = chargedRounds() - 1;
            if (additionalRounds == 0) return true;
            for (int i = 0; i < BOOSTS.length; i++) {
                int cost = Math.multiplyExact(additionalRounds, boostPrice[i]);
                if (cost == 0) continue;
                FluidStack fluid = new FluidStack(boostFluid(i), cost);
                if (!useFluid(available, fluid, cost)) return false;
                mergeFluid(consumedFluids, fluid, cost);
            }
            return true;
        }

        private Batch conclude() {
            double scale = 1 + .06 * (sectors - 1);
            double time = weightedTicks / rootPaid / machineSpeed();
            if (!Double.isFinite(time) || time <= 0 || time > 24000) return null;
            batched = batchRounds > 1 && time < BATCH_TICKS;
            // Preserve fractional recipe time. Batch capacity is serial work over a fixed window;
            // the machine's actual parallelLimit never changes when batch mode is switched on.
            double window = batched ? Math.min(BATCH_TICKS, batchRounds * Math.max(1, time)) : time;
            capacity = batched ? (int) Math.min(MAX_BATCH_ROOTS, Math.floor(parallelLimit() * (window / time)))
                : parallelLimit();
            if (rootPaid > capacity) {
                capacityRetry = capacity;
                return null;
            }
            int ticks = Math.max(1, (int) Math.ceil(window));
            double idleEU = 0, hold = 0;
            for (CenterModule module : MODULES) {
                int i = module.ordinal();
                for (int g = 0; g < best[i]; g++) {
                    hold += weight[i] * module.steamRate * GRADE_STEAM[g] * counts[i][g] * .20;
                    idleEU += weight[i] * module.standbyEU * counts[i][g];
                }
            }
            if (idleEU > 0) idleEU += CONTROL_EU[tier];
            long processingEnergy = ignoreEnergy ? 0 : bill(recipeEU);
            long maintenanceEnergy = ignoreEnergy ? 0 : bill(idleEU * ticks * scale * euMultiplier);
            long energy = Math.addExact(processingEnergy, maintenanceEnergy);
            // Do not stretch a recipe's fixed time to hide insufficient supply.
            if (!ignoreEnergy && (maxEUt < energy / ticks + (energy % ticks == 0 ? 0 : 1))) return null;
            if (!settleBoosts()) {
                workLimitReached = true;
                return null;
            }
            long processingSteam = ignoreFluids ? 0 : bill(workSteam / (20 * WIDTH[tier]));
            long maintenanceSteam = ignoreFluids ? 0
                : bill(scale * (STEAM[tier] * (1 + .1 * (sectors - 1)) + steamMultiplier * hold) * ticks / 20);
            return new Batch(this, ticks, processingSteam, maintenanceSteam, processingEnergy, maintenanceEnergy);
        }

        private static long bill(double amount) {
            if (!Double.isFinite(amount) || amount < 0 || amount >= Long.MAX_VALUE)
                throw new ArithmeticException("Center bill overflow");
            return (long) Math.ceil(amount);
        }

    }

    static boolean equal(ItemStack left, ItemStack right) {
        return left.getItem() == right.getItem() && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static boolean anyTag(String[] tags, Pattern pattern) {
        for (String tag : tags) if (pattern.matcher(tag)
            .matches()) return true;
        return false;
    }

    static boolean isIntermediate(ItemStack item) {
        String[] tags = CenterGraph.tags(item);
        return !anyTag(tags, RAW) && anyTag(tags, FEED);
    }

    static GTRecipe findRecipe(RecipeMap<?> map, ItemStack[] items, FluidStack[] fluids, long voltage, int heat) {
        if (map == null) return null;
        List<GTRecipe> found = new ArrayList<>();
        map.findRecipeQuery()
            .items(items)
            .fluids(fluids)
            .voltage(voltage)
            .caching(false)
            .filter(r -> usableRecipe(r, heat))
            .findAll()
            .limit(MAX_CANDIDATES + 1L)
            .forEach(found::add);
        return found.isEmpty() || found.size() > MAX_CANDIDATES ? null : found.get(0);
    }

    static boolean usableRecipe(GTRecipe recipe, int heat) {
        return !recipe.mFakeRecipe && recipe.mEnabled
            && !recipe.mHidden
            && !recipe.getMetadataOrDefault(GTRecipeConstants.CLEANROOM, false)
            && !recipe.getMetadataOrDefault(GTRecipeConstants.LOW_GRAVITY, false)
            && recipe.getMetadataOrDefault(GTRecipeConstants.COIL_HEAT, 0) <= heat
            && ordinaryInputs(recipe);
    }

    private static GTRecipe findWithoutFluidStock(RecipeMap<?> map, CenterGraph.Node node, ItemStack[] items,
        long voltage, int heat) {
        if (map == null || !map.containsInput(items[0])) return null;
        for (GTRecipe recipe : map.getAllRecipes()) {
            if (recipe.mFluidInputs.length == 0 || recipe.mEUt > voltage * map.getAmperage()
                || !usableRecipe(recipe, heat)) continue;
            boolean accepted = true;
            for (FluidStack fluid : recipe.mFluidInputs) {
                if (fluid != null && !node.acceptsAuxiliary(fluid)) {
                    accepted = false;
                    break;
                }
            }
            if (!accepted) continue;
            FluidStack[] fluids = virtualFluids(recipe, 1);
            if (!recipe.isRecipeInputEqual(false, false, 1, fluids, items)) continue;
            ItemStack[] sample = java.util.Arrays.stream(items)
                .map(ItemStack::copy)
                .toArray(ItemStack[]::new);
            if (recipe.isRecipeInputEqual(true, false, 1, fluids, sample) && sample[0].stackSize < items[0].stackSize)
                return recipe;
        }
        return null;
    }

    private static FluidStack[] virtualFluids(GTRecipe recipe, int limit) {
        List<FluidStack> fluids = new ArrayList<>();
        for (FluidStack fluid : recipe.mFluidInputs) if (fluid != null) mergeFluid(
            fluids,
            fluid,
            (int) Math.min(Integer.MAX_VALUE / recipe.mFluidInputs.length, (long) fluid.amount * limit));
        return fluids.toArray(new FluidStack[0]);
    }

    static long recipeVoltage(CenterProcess process, int tier, long suppliedVoltage) {
        return process.module.standbyEU == 0 ? VOLTAGE[tier] : suppliedVoltage;
    }

    private static boolean ordinaryInputs(GTRecipe recipe) {
        if (recipe.mInputChances != null) for (int chance : recipe.mInputChances) if (chance != 10000) return false;
        if (recipe.mFluidInputChances != null)
            for (int chance : recipe.mFluidInputChances) if (chance != 10000) return false;
        return true;
    }

    static ItemId itemKey(ItemStack stack) {
        return ItemId.createNoCopy(stack.getItem(), stack.getItemDamage(), stack.getTagCompound());
    }

    private static void merge(Map<ItemId, ItemStack> stacks, ItemStack item, int amount) {
        ItemId key = itemKey(item);
        ItemStack prior = stacks.get(key);
        if (prior != null) {
            prior.stackSize = Math.addExact(prior.stackSize, amount);
            return;
        }
        ItemStack copy = item.copy();
        copy.stackSize = amount;
        stacks.put(key, copy);
    }

    private static void mergeFluid(List<FluidStack> stacks, FluidStack item, int amount) {
        for (FluidStack stack : stacks) if (stack.isFluidEqual(item)) {
            int moved = Math.min(Integer.MAX_VALUE - stack.amount, amount);
            stack.amount += moved;
            amount -= moved;
            if (amount == 0) return;
        }
        FluidStack copy = item.copy();
        copy.amount = amount;
        stacks.add(copy);
    }

    private static boolean useFluid(List<FluidStack> stacks, FluidStack item, int amount) {
        int remaining = amount;
        for (FluidStack stack : stacks) if (stack.isFluidEqual(item)) {
            if (stack.amount >= remaining) {
                remaining = 0;
                break;
            }
            remaining -= stack.amount;
        }
        if (remaining != 0) return false;
        for (FluidStack stack : stacks) if (stack.isFluidEqual(item)) {
            int used = Math.min(amount, stack.amount);
            stack.amount -= used;
            amount -= used;
            if (amount == 0) break;
        }
        return true;
    }
}
