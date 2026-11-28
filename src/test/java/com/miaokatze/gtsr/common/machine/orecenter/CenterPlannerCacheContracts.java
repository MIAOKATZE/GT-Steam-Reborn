package com.miaokatze.gtsr.common.machine.orecenter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import ic2.api.recipe.IListRecipeManager;
import ic2.api.recipe.Recipes;

/** Runs after CenterParallelBalanceContracts' native bootstrap; never starts a world or client. */
final class CenterPlannerCacheContracts {

    private static Method itemFactory;
    private static int identity, checks;

    static void run(Method factory) throws Exception {
        itemFactory = factory;
        routesAndInvalidation();
        entrySlicesAndLateRecipes();
        nodeStockReferences();
        exactNativeQueries();
        chemicalAndCircuitQueries();
        nativeCandidateBoundary();
        dynamicBackendFallback();
        System.out.println("PASS center planner cache: " + checks + " checks; game_started=false");
    }

    private static Item item(boolean raw) throws Exception {
        int id = identity++;
        Item item = (Item) itemFactory.invoke(null, "gtsr_center_cache:fixture_" + id);
        if (raw) OreDictionary.registerOre("rawOreCacheContract" + id, stack(item, 1));
        return item;
    }

    private static ItemStack stack(Item item, int amount) {
        return new ItemStack(item, amount);
    }

    private static ItemStack tagged(Item item, int marker) {
        ItemStack stack = stack(item, 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("CacheFixture", marker);
        stack.setTagCompound(tag);
        return stack;
    }

    private static Fluid fluid() {
        Fluid fluid = new Fluid("gtsr_center_cache_" + identity++);
        check(FluidRegistry.registerFluid(fluid), "fixture fluid registration");
        return fluid;
    }

    private static GTRecipe recipe(RecipeMap<?> map, ItemStack[] inputs, FluidStack[] fluids, Item output, int eut,
        int heat, boolean nbt) {
        GTRecipeBuilder builder = GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .itemOutputs(stack(output, 1))
            .fluidInputs(fluids)
            .duration(20)
            .eut(eut);
        if (heat > 0) builder.metadata(GTRecipeConstants.COIL_HEAT, heat);
        if (nbt) builder.nbtSensitive();
        GTRecipe recipe = builder.build()
            .get();
        map.getBackend()
            .compileRecipe(recipe);
        return recipe;
    }

    private static RecipeMap<?> map() {
        return RecipeMapBuilder.of("gtsr.center.cache.fixture." + identity++)
            .maxIO(4, 1, 2, 0)
            .build();
    }

    private static CenterGraph graph(long id, CenterGraph.Node... nodes) {
        return new CenterGraph(id, "cache", 0, true, Collections.emptyList(), Arrays.asList(nodes));
    }

    private static int[][] modules(List<CenterGraph> graphs, int grade) {
        int[][] counts = new int[CenterModule.values().length][3];
        for (CenterGraph graph : graphs)
            for (CenterGraph.Node node : graph.nodes) counts[node.process.module.ordinal()][grade - 1] = 1;
        return counts;
    }

    private static CenterPlanner.Session session(List<CenterGraph> graphs, ItemStack[] items, FluidStack[] fluids,
        int grade, CenterPlanner.RouteCache cache, long revision) {
        return new CenterPlanner.Session(
            graphs,
            items,
            fluids,
            modules(graphs, grade),
            3,
            1,
            8192,
            Long.MAX_VALUE,
            4500,
            17,
            false,
            false,
            CenterPlanner.MAX_ROOTS,
            1,
            cache,
            revision);
    }

    private static CenterPlanner.Batch complete(CenterPlanner.Session session, int budget) {
        int slices = 0;
        while (!session.advance(budget, Long.MAX_VALUE)) {
            if (++slices > 10000) throw new AssertionError("Cache fixture exceeded bounded slices");
        }
        return session.batch();
    }

    private static CenterPlanner.Batch plan(List<CenterGraph> graphs, ItemStack[] items, FluidStack[] fluids, int grade,
        CenterPlanner.RouteCache cache, long revision) {
        return complete(session(graphs, items, fluids, grade, cache, revision), 64);
    }

    private static void equivalent(CenterPlanner.Batch left, CenterPlanner.Batch right, String message) {
        check(left != null && right != null, message + " produces both batches");
        check(
            left.roots == right.roots && left.parallel == right.parallel
                && left.ticks == right.ticks
                && left.steam == right.steam
                && left.energy == right.energy
                && left.weightedTicks == right.weightedTicks
                && left.chargedRounds == right.chargedRounds,
            message + " preserves dimensions and bills");
        equalItems(left.consumed, right.consumed, message + " consumption");
        equalItems(left.outputs, right.outputs, message + " outputs");
        check(left.consumedFluids.size() == right.consumedFluids.size(), message + " fluid count");
        for (int i = 0; i < left.consumedFluids.size(); i++) {
            FluidStack a = left.consumedFluids.get(i), b = right.consumedFluids.get(i);
            check(a.amount == b.amount && a.isFluidEqual(b), message + " fluids");
        }
        check(left.chains.size() == right.chains.size(), message + " origins");
        for (int i = 0; i < left.chains.size(); i++) check(
            left.chains.get(i).graphId == right.chains.get(i).graphId
                && left.chains.get(i).roots == right.chains.get(i).roots
                && left.chains.get(i).ticks == right.chains.get(i).ticks,
            message + " origin chain");
    }

    private static void equalItems(List<ItemStack> left, List<ItemStack> right, String message) {
        check(left.size() == right.size(), message + " count");
        for (int i = 0; i < left.size(); i++)
            check(ItemStack.areItemStacksEqual(left.get(i), right.get(i)), message + " identity/NBT/amount");
    }

    private static void routesAndInvalidation() throws Exception {
        Item input = item(true), output = item(false);
        recipe(
            CenterProcess.CRUSHER.recipes(),
            new ItemStack[] { stack(input, 1) },
            new FluidStack[0],
            output,
            2,
            0,
            false);
        CenterGraph graph = graph(10, new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { -1 }));
        List<CenterGraph> graphs = Collections.singletonList(graph);
        CenterPlanner.RouteCache cache = new CenterPlanner.RouteCache();
        ItemStack[] stock = { tagged(input, 1) };
        CenterPlanner.Batch cold = plan(graphs, stock, new FluidStack[0], 3, null, 0);
        equivalent(cold, plan(graphs, stock, new FluidStack[0], 3, cache, 1), "cold route cache");
        long hits = cache.hits();
        equivalent(cold, plan(graphs, stock, new FluidStack[0], 3, cache, 1), "hot route cache");
        check(cache.hits() == hits + 1, "same immutable identity hits across independent sessions");
        long misses = cache.misses();
        plan(graphs, new ItemStack[] { tagged(input, 2) }, new FluidStack[0], 3, cache, 1);
        check(cache.misses() == misses + 1 && cache.size() == 2, "NBT isolates route identities");
        stock[0].getTagCompound()
            .setInteger("CacheFixture", 99);
        hits = cache.hits();
        plan(graphs, new ItemStack[] { tagged(input, 1) }, new FluidStack[0], 3, cache, 1);
        check(cache.hits() == hits + 1, "caller NBT mutation cannot change stored route keys");
        misses = cache.misses();
        plan(graphs, stock, new FluidStack[0], 3, cache, 2);
        check(cache.misses() == misses + 1 && cache.size() == 1, "allocation revision invalidates decisions");
        misses = cache.misses();
        equivalent(
            plan(graphs, stock, new FluidStack[0], 2, null, 0),
            plan(graphs, stock, new FluidStack[0], 2, cache, 2),
            "changed module grade");
        check(cache.misses() == misses + 1, "effective module grade invalidates decisions");
        cache.clear();
        check(cache.size() == 0, "lifecycle clear releases all decisions");
        for (int i = 0; i < 514; i++)
            plan(graphs, new ItemStack[] { tagged(input, i) }, new FluidStack[0], 3, cache, 3);
        check(cache.size() == 512 && cache.evictions() >= 2, "cross-batch decision cache remains bounded");

        String newTag = "rawOreCacheChanged" + identity++;
        CenterGraph preferred = new CenterGraph(
            11,
            "preferred",
            10,
            true,
            Collections.singletonList(newTag),
            graph.nodes);
        graphs = Arrays.asList(preferred, graph);
        CenterPlanner.Batch before = plan(graphs, new ItemStack[] { stack(input, 1) }, new FluidStack[0], 3, cache, 4);
        OreDictionary.registerOre(newTag, stack(input, 1));
        CenterPlanner.Batch after = plan(graphs, new ItemStack[] { stack(input, 1) }, new FluidStack[0], 3, cache, 4);
        check(
            before.chains.get(0).graphId == 10 && after.chains.get(0).graphId == 11,
            "late ore registration changes the route without a guessed global ore revision");
        CenterGraph excluded = new CenterGraph(
            12,
            "excluded",
            20,
            true,
            Collections.emptyList(),
            graph.nodes,
            Collections.emptyList(),
            Collections.singletonList(newTag));
        CenterPlanner.Batch fallback = plan(
            Arrays.asList(excluded, graph),
            new ItemStack[] { stack(input, 1) },
            new FluidStack[0],
            3,
            cache,
            4);
        check(fallback.chains.get(0).graphId == 10, "excluded fallback cannot override the next legal graph");
    }

    private static void entrySlicesAndLateRecipes() throws Exception {
        Item input = item(true), output = item(false);
        CenterGraph graph = graph(20, new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { -1 }));
        List<CenterGraph> graphs = Collections.singletonList(graph);
        CenterPlanner.RouteCache cache = new CenterPlanner.RouteCache();
        check(
            plan(graphs, new ItemStack[] { stack(input, 1) }, new FluidStack[0], 3, cache, 1) == null,
            "native input index rejects a raw input before recipe registration");
        recipe(
            CenterProcess.CRUSHER.recipes(),
            new ItemStack[] { stack(input, 1) },
            new FluidStack[0],
            output,
            2,
            0,
            false);
        check(
            plan(graphs, new ItemStack[] { stack(input, 1) }, new FluidStack[0], 3, cache, 1) != null
                && cache.hits() > 0,
            "route hit rechecks the native index after late recipe registration");
        Item hammerOutput = item(false);
        recipe(
            CenterProcess.HAMMER.recipes(),
            new ItemStack[] { stack(input, 1) },
            new FluidStack[0],
            hammerOutput,
            2,
            0,
            false);
        graph = new CenterGraph(
            21,
            "entry",
            0,
            true,
            Collections.emptyList(),
            Arrays.asList(
                new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { 1 }),
                new CenterGraph.Node(CenterProcess.HAMMER, new int[] { -1 })),
            Arrays.asList(
                new CenterGraph.EntryRoute(0, "rawOreNeverCacheFixture"),
                new CenterGraph.EntryRoute(1, CenterGraph.tags(stack(input, 1))[0])),
            Collections.emptyList());
        graphs = Collections.singletonList(graph);
        CenterPlanner.Session sliced = session(
            graphs,
            new ItemStack[] { stack(input, 1) },
            new FluidStack[0],
            3,
            cache,
            2);
        check(
            !sliced.advance(1, Long.MAX_VALUE) && sliced.batch() == null,
            "cold graph decisions consume an advance work unit");
        CenterPlanner.Batch result = complete(sliced, 1);
        check(
            result != null && result.outputs.get(0)
                .getItem() == hammerOutput,
            "sliced entry branches retain first matching branch semantics");
        equivalent(
            result,
            plan(graphs, new ItemStack[] { stack(input, 1) }, new FluidStack[0], 3, cache, 2),
            "entry cache binding");
    }

    private static void nodeStockReferences() throws Exception {
        Item first = item(true), second = item(true), auxiliary = item(false), unrelated = item(false),
            output = item(false);
        Fluid fluid = fluid(), otherFluid = fluid();
        for (Item input : Arrays.asList(first, second)) recipe(
            CenterProcess.REACTOR.recipes(),
            new ItemStack[] { stack(input, 1), stack(auxiliary, 1) },
            new FluidStack[] { new FluidStack(fluid, 1) },
            output,
            8,
            0,
            false);
        CenterGraph.Node open = new CenterGraph.Node(CenterProcess.REACTOR, new int[] { -1 });
        CenterGraph.Node filtered = open.withEmptyAuxiliary()
            .withAuxiliary(0, GameRegistry.findUniqueIdentifierFor(auxiliary) + "@0")
            .withAuxiliary(3, fluid.getName());
        ItemStack[] stock = { stack(first, 1), stack(second, 1), stack(auxiliary, 1), stack(unrelated, 50) };
        FluidStack[] fluids = { new FluidStack(fluid, 1), new FluidStack(otherFluid, 50) };
        CenterPlanner.Batch legacy = plan(Collections.singletonList(graph(30, open)), stock, fluids, 3, null, 0);
        CenterGraph filteredGraph = graph(30, filtered);
        CenterPlanner.Batch reduced = complete(
            session(Collections.singletonList(filteredGraph), stock, fluids, 3, new CenterPlanner.RouteCache(), 0),
            1);
        equivalent(legacy, reduced, "legacy null whitelist and matching filtered views");
        check(
            reduced.roots == 1 && reduced.consumedFluids.get(0).amount == 1,
            "shared node references observe auxiliary/fluid depletion before the next origin");
        check(stock[2].stackSize == 1 && fluids[0].amount == 1, "planner copies do not debit caller inventory");
    }

    private static void exactNativeQueries() throws Exception {
        RecipeMap<?> map = map();
        Item input = item(false), output = item(false), extra = item(false);
        Fluid fluid = fluid();
        GTRecipe small = recipe(map, new ItemStack[] { stack(input, 1) }, new FluidStack[0], output, 8, 0, false);
        GTRecipe large = recipe(
            map,
            new ItemStack[] { stack(input, 2), stack(extra, 1) },
            new FluidStack[] { new FluidStack(fluid, 2) },
            output,
            128,
            2700,
            false);
        CenterRecipeMemo memo = new CenterRecipeMemo();
        ItemStack[] one = { stack(input, 1) };
        check(memo.find(map, one, new FluidStack[0], 32, 0) == small, "ordinary native first result");
        long queries = memo.queries();
        check(
            memo.find(map, one, new FluidStack[0], 32, 0) == small && memo.hits() == 1
                && memo.queries() == queries
                && memo.checks() > 0,
            "exact slice hit performs native revalidation");
        ItemStack[] full = { stack(input, 2), stack(extra, 1) };
        FluidStack[] supplied = { new FluidStack(fluid, 2) };
        check(
            memo.find(map, full, supplied, 128, 2700) == CenterPlanner.findRecipe(map, full, supplied, 128, 2700),
            "quantity/auxiliary/fluid change matches native");
        queries = memo.queries();
        memo.find(map, full, supplied, 32, 2700);
        memo.find(map, full, supplied, 128, 0);
        check(memo.queries() == queries + 2, "voltage and temperature changes cannot hit an old decision");
        small.mEnabled = false;
        check(memo.find(map, one, new FluidStack[0], 32, 0) == null, "disabled cached recipe is truly rechecked");
        small.mEnabled = true;
        memo.clear();
        check(memo.find(map, one, new FluidStack[0], 32, 0) == small, "new slice sees re-enabled recipe");
        large.mCanBeBuffered = false;
        memo.clear();
        memo.find(map, full, supplied, 128, 2700);
        check(memo.size() == 0, "one unbufferable candidate prevents reusing a partial candidate set");
        RecipeMap<?> nbtMap = map();
        GTRecipe nbtRecipe = recipe(
            nbtMap,
            new ItemStack[] { tagged(input, 1) },
            new FluidStack[0],
            output,
            8,
            0,
            true);
        memo.clear();
        check(
            memo.find(nbtMap, new ItemStack[] { tagged(input, 1) }, new FluidStack[0], 32, 0) == nbtRecipe,
            "native NBT-sensitive fixture matches");
        check(
            memo.find(nbtMap, new ItemStack[] { tagged(input, 2) }, new FluidStack[0], 32, 0)
                == CenterPlanner.findRecipe(nbtMap, new ItemStack[] { tagged(input, 2) }, new FluidStack[0], 32, 0),
            "different NBT preserves native match semantics");
        RecipeMap<?> lateMap = map();
        memo.clear();
        check(
            memo.find(lateMap, one, new FluidStack[0], 32, 0) == null && memo.size() == 0,
            "negative queries are not cached");
        GTRecipe late = recipe(lateMap, one, new FluidStack[0], output, 8, 0, false);
        check(memo.find(lateMap, one, new FluidStack[0], 32, 0) == late, "late recipe appears after a miss");
        RecipeMap<?> wetMap = map();
        recipe(wetMap, one, supplied, output, 8, 0, false);
        memo.clear();
        check(
            memo.find(wetMap, one, new FluidStack[] { new FluidStack(fluid, 1) }, 32, 0) == null,
            "insufficient fluid is rejected");
        check(memo.find(wetMap, one, supplied, 32, 0) != null, "supplemented fluid returns to native lookup");
    }

    private static void nativeCandidateBoundary() throws Exception {
        RecipeMap<?> map = map();
        Item input = item(false), output = item(false);
        ItemStack[] stock = { stack(input, 64) };
        List<GTRecipe> registered = new ArrayList<>();
        for (int i = 1; i <= 32; i++)
            registered.add(recipe(map, new ItemStack[] { stack(input, i) }, new FluidStack[0], output, 8, 0, false));
        CenterRecipeMemo memo = new CenterRecipeMemo();
        GTRecipe first = CenterPlanner.findRecipe(map, stock, new FluidStack[0], 32, 0);
        check(
            first != null && CenterPlanner.findRecipeCandidates(map, stock, new FluidStack[0], 32, 0)
                .size() == 32,
            "exactly 32 effective native matches are accepted");
        check(
            memo.find(map, stock, new FluidStack[0], 32, 0) == first
                && memo.find(map, stock, new FluidStack[0], 32, 0) == first
                && memo.hits() == 1,
            "complete 32-candidate memo retains native first order");
        recipe(map, new ItemStack[] { stack(input, 33) }, new FluidStack[0], output, 8, 0, false);
        memo.clear();
        check(
            memo.find(map, stock, new FluidStack[0], 32, 0) == null && memo.size() == 0,
            "33 effective matches reject and never cache the truncated set");
        registered.get(0).mHidden = true;
        check(
            memo.find(map, stock, new FluidStack[0], 32, 0) != null,
            "33 visited candidates with one unusable recipe still accept 32 effective matches");
    }

    private static void chemicalAndCircuitQueries() throws Exception {
        Item input = item(false), firstOutput = item(false), secondOutput = item(false);
        if (!ItemList.Circuit_Integrated.hasBeenSet()) ItemList.Circuit_Integrated.set(item(false));
        ItemStack firstCircuit = ItemList.Circuit_Integrated.getWithDamage(0, 1);
        ItemStack secondCircuit = ItemList.Circuit_Integrated.getWithDamage(0, 2);
        Fluid firstFluid = fluid(), secondFluid = fluid();
        RecipeMap<?> map = map();
        GTRecipe first = recipe(
            map,
            new ItemStack[] { stack(input, 1), firstCircuit },
            new FluidStack[] { new FluidStack(firstFluid, 1) },
            firstOutput,
            8,
            0,
            false);
        GTRecipe second = recipe(
            map,
            new ItemStack[] { stack(input, 1), secondCircuit },
            new FluidStack[] { new FluidStack(secondFluid, 1) },
            secondOutput,
            8,
            0,
            false);
        CenterRecipeMemo memo = new CenterRecipeMemo();
        ItemStack[] firstStock = { stack(input, 1), firstCircuit.copy() };
        ItemStack[] secondStock = { stack(input, 1), secondCircuit.copy() };
        FluidStack[] firstSupply = { new FluidStack(firstFluid, 1) };
        FluidStack[] secondSupply = { new FluidStack(secondFluid, 1) };
        check(memo.find(map, firstStock, firstSupply, 32, 0) == first, "first chemical/circuit selects first recipe");
        check(
            memo.find(map, secondStock, secondSupply, 32, 0) == second,
            "same main item with another chemical/circuit selects native alternative");
        check(
            memo.find(map, firstStock, secondSupply, 32, 0) == null,
            "another chemical does not borrow a cached recipe from the same main item");
        long queries = memo.queries();
        FluidStack taggedFluid = firstSupply[0].copy();
        taggedFluid.tag = new NBTTagCompound();
        taggedFluid.tag.setInteger("CacheFixture", 1);
        check(
            memo.find(map, firstStock, new FluidStack[] { taggedFluid }, 32, 0)
                == CenterPlanner.findRecipe(map, firstStock, new FluidStack[] { taggedFluid }, 32, 0)
                && memo.queries() == queries + 1,
            "fluid NBT isolates memo keys while preserving native matching");
        ItemStack[] smaller = { stack(input, 0), firstCircuit.copy() };
        check(
            memo.find(map, smaller, firstSupply, 32, 0) == CenterPlanner.findRecipe(map, smaller, firstSupply, 32, 0),
            "depleted main quantities return to native matching");
    }

    private static void dynamicBackendFallback() throws Exception {
        final int[] calls = { 0 };
        final GTRecipe[] selected = { null };
        RecipeMap<?> map = RecipeMapBuilder
            .of("gtsr.center.cache.dynamic." + identity++, properties -> new RecipeMapBackend(properties) {

                @Override
                public boolean doesOverwriteFindRecipe() {
                    return true;
                }

                @Override
                protected GTRecipe overwriteFindRecipe(ItemStack[] items, FluidStack[] fluids, ItemStack special,
                    GTRecipe cached) {
                    calls[0]++;
                    return selected[0];
                }
            })
            .maxIO(1, 1, 0, 0)
            .build();
        Item input = item(false), output = item(false);
        selected[0] = recipe(map, new ItemStack[] { stack(input, 1) }, new FluidStack[0], output, 8, 0, false);
        CenterRecipeMemo memo = new CenterRecipeMemo();
        ItemStack[] stock = { stack(input, 1) };
        check(memo.find(map, stock, new FluidStack[0], 32, 0) == selected[0], "native overwrite backend is called");
        selected[0] = null;
        check(
            memo.find(map, stock, new FluidStack[0], 32, 0) == null && calls[0] == 2 && memo.size() == 0,
            "dynamic backend results never become planner candidate memo entries");
        GameRegistry.addSmelting(stack(input, 1), stack(output, 1), 0);
        nativeDynamicQuery(memo, CenterProcess.SMELTING.recipes(), stock, "native furnace");
        nativeRecyclerQuery(memo, stock);
    }

    private static void nativeDynamicQuery(CenterRecipeMemo memo, RecipeMap<?> map, ItemStack[] stock,
        String description) {
        long hits = memo.hits();
        GTRecipe actual = memo.find(map, stock, new FluidStack[0], 8192, 4500);
        GTRecipe expected = CenterPlanner.findRecipe(map, stock, new FluidStack[0], 8192, 4500);
        check(actual != null && expected != null, description + " yields native generated recipes");
        check(
            actual.mDuration == expected.mDuration && actual.mEUt == expected.mEUt
                && memo.hits() == hits
                && memo.size() == 0,
            description + " preserves native query and bypasses memo");
        equalItems(Arrays.asList(actual.mInputs), Arrays.asList(expected.mInputs), description + " inputs");
        equalItems(Arrays.asList(actual.mOutputs), Arrays.asList(expected.mOutputs), description + " outputs");
        for (int i = 0; i < actual.mOutputs.length; i++)
            check(actual.getOutputChance(i) == expected.getOutputChance(i), description + " native chance");
    }

    private static void nativeRecyclerQuery(CenterRecipeMemo memo, ItemStack[] stock) throws Exception {
        IListRecipeManager originalWhitelist = Recipes.recyclerWhitelist, originalBlacklist = Recipes.recyclerBlacklist;
        Field whitelistCache = GTModHandler.class.getDeclaredField("recyclerWhitelist");
        Field blacklistCache = GTModHandler.class.getDeclaredField("recyclerBlacklist");
        whitelistCache.setAccessible(true);
        blacklistCache.setAccessible(true);
        Object originalWhitelistCache = whitelistCache.get(null), originalBlacklistCache = blacklistCache.get(null);
        try {
            if (Recipes.recyclerWhitelist == null || Recipes.recyclerBlacklist == null) {
                Class<?> manager = null;
                try {
                    manager = Class.forName("ic2.core.BasicListRecipeManager");
                } catch (ClassNotFoundException | NoClassDefFoundError unavailable) {
                    // The isolated native loader may expose IC2's API without its implementation.
                }
                if (manager != null) {
                    if (Recipes.recyclerWhitelist == null)
                        Recipes.recyclerWhitelist = (IListRecipeManager) manager.getConstructor()
                            .newInstance();
                    if (Recipes.recyclerBlacklist == null)
                        Recipes.recyclerBlacklist = (IListRecipeManager) manager.getConstructor()
                            .newInstance();
                    System.out.println(
                        "SOURCE recycler fixture manager " + manager.getProtectionDomain()
                            .getCodeSource()
                            .getLocation());
                }
            }
            // Use GT's actual cache initialization against the supplied actual IC2 manager, without inherited state.
            whitelistCache.set(null, null);
            blacklistCache.set(null, null);
            if (Recipes.recyclerWhitelist == null || Recipes.recyclerBlacklist == null) {
                boolean reachedMissingManager = false;
                try {
                    memo.find(CenterProcess.RECYCLER.recipes(), stock, new FluidStack[0], 8192, 4500);
                } catch (NullPointerException expectedBoundary) {
                    for (StackTraceElement frame : expectedBoundary.getStackTrace()) if (frame.getClassName()
                        .equals(GTModHandler.class.getName())
                        && frame.getMethodName()
                            .equals("generateRecyclerCache"))
                        reachedMissingManager = true;
                    if (!reachedMissingManager) throw expectedBoundary;
                }
                check(
                    reachedMissingManager && memo.size() == 0,
                    "native recycler reaches missing IC2 manager boundary without memoizing a result");
                System.out.println(
                    "LIMIT native recycler game business unverified: IC2 BasicListRecipeManager "
                        + "unavailable and Recipes.recyclerWhitelist/recyclerBlacklist not initialized; "
                        + "asserted native GTModHandler.generateRecyclerCache boundary only");
                return;
            }
            if (!ItemList.IC2_Scrap.hasBeenSet()) ItemList.IC2_Scrap.set(item(false));
            nativeDynamicQuery(
                memo,
                CenterProcess.RECYCLER.recipes(),
                stock,
                "native recycler with actual IC2 manager");
        } finally {
            Recipes.recyclerWhitelist = originalWhitelist;
            Recipes.recyclerBlacklist = originalBlacklist;
            whitelistCache.set(null, originalWhitelistCache);
            blacklistCache.set(null, originalBlacklistCache);
        }
    }

    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++;
    }
}
