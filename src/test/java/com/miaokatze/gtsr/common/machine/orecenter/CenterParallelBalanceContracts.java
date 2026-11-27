// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.recipe.RecipeMaps;

/** Native recipe/planner fixtures only: no client, server, world or Testlab is started. */
public final class CenterParallelBalanceContracts {

    private static Method fixtureItem;
    private static int checks, identity;

    private CenterParallelBalanceContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, CenterParallelBalanceContracts.class.getName(), args)) return;
        Method setup = PortableWeaponRecipeContracts.class.getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        // CenterProcess resolves most maps by registry name, so trigger native GT's map registration first.
        check(RecipeMaps.maceratorRecipes != null, "native GT recipe maps are initialized");
        fixtureItem = PortableWeaponRecipeContracts.class.getDeclaredMethod("item", String.class);
        fixtureItem.setAccessible(true);
        parallelAndHostGrades();
        constantRoundTime();
        fairAdmission();
        weightedOrigins();
        mergedOrigins();
        multipliedAndBranchedOutputs();
        fixedTargetBatching();
        weightedBatching();
        subTickBatching();
        processingBillsAndPowerGate();
        speedBoosterBilling();
        queueAndBoosterPayment();
        unchangedBoosterValues();
        numericBoundaries();
        tickRemainderBoundaries();
        System.out.println("PASS center parallel balance: " + checks + " checks; game_started=false");
    }

    private static Item item(String name, boolean raw) throws Exception {
        int id = identity++;
        Item item = (Item) fixtureItem.invoke(null, "gtsr_parallel_contract:" + name + "_" + id);
        if (raw) OreDictionary.registerOre("rawOreParallelContract" + id, stack(item, 1));
        return item;
    }

    private static ItemStack stack(Item item, int amount) {
        return new ItemStack(item, amount);
    }

    private static void recipe(CenterProcess process, Item input, int duration, int eut, ItemStack... outputs) {
        check(
            !GTValues.RA.stdBuilder()
                .itemInputs(stack(input, 1))
                .itemOutputs(outputs)
                .duration(duration)
                .eut(eut)
                .addTo(process.recipes())
                .isEmpty(),
            "native recipe registration: " + process);
        check(
            CenterPlanner
                .findRecipe(process.recipes(), new ItemStack[] { stack(input, 1) }, new FluidStack[0], 8192, 4500)
                != null,
            "native recipe lookup: " + process);
    }

    private static CenterGraph graph(CenterGraph.Node... nodes) {
        return new CenterGraph(0, true, Collections.emptyList(), Arrays.asList(nodes));
    }

    private static CenterGraph.Node node(CenterProcess process, int... targets) {
        return new CenterGraph.Node(process, targets);
    }

    private static int[][] modules(CenterGraph graph, int grade) {
        int[][] counts = new int[CenterModule.values().length][3];
        for (CenterGraph.Node node : graph.nodes) counts[node.process.module.ordinal()][grade - 1] = 1;
        return counts;
    }

    private static CenterPlanner.Session session(List<CenterGraph> graphs, ItemStack[] inputs, int[][] modules,
        int tier, int sectors, int modifier, boolean queue, long power, FluidStack... fluids) {
        int rootLimit = CenterPlanner.MAX_BATCH_ROOTS;
        for (int retry = 0; retry < 128; retry++) {
            CenterPlanner.Session session = new CenterPlanner.Session(
                graphs,
                inputs,
                fluids,
                modules,
                tier,
                sectors,
                8192,
                power,
                4500,
                17,
                false,
                false,
                rootLimit,
                modifier);
            session.queueMode(queue);
            int slices = 0;
            while (!session.advance(7, Long.MAX_VALUE)) {
                if (++slices > 10000) throw new AssertionError("Bounded planning did not finish");
            }
            if (session.batch() != null) return session;
            int nextLimit = session.smallerRootLimit();
            if (nextLimit <= 0) return session;
            if (queue && nextLimit < session.parallelLimit()) {
                if (rootLimit <= session.parallelLimit()) return session;
                nextLimit = session.parallelLimit();
            }
            check(nextLimit < rootLimit, "controller-style batch retries monotonically reduce the root limit");
            rootLimit = nextLimit;
        }
        throw new AssertionError("Controller-style shrinking did not converge");
    }

    private static CenterPlanner.Batch batch(CenterGraph graph, ItemStack[] inputs, int grade, int tier) {
        return require(
            session(Collections.singletonList(graph), inputs, modules(graph, grade), tier, 1, 1, false, Long.MAX_VALUE)
                .batch(),
            "native fixture produces a batch");
    }

    private static CenterPlanner.Batch require(CenterPlanner.Batch batch, String description) {
        check(batch != null, description);
        check(batch.ticks > 0 && batch.roots > 0 && batch.parallel > 0, "positive batch dimensions");
        check(
            batch.capacity >= batch.roots && batch.capacity >= batch.parallel
                && batch.capacity <= CenterPlanner.MAX_BATCH_ROOTS,
            "batch capacity bounds admitted inputs independently of physical parallel");
        check(
            batch.chargedRounds == 1 + (batch.roots - 1) / batch.parallel,
            "charged recipe batches use actual inputs divided by physical P");
        check(
            batch.energy == Math.addExact(batch.processingEnergy, batch.maintenanceEnergy),
            "electric total contains processing and maintenance exactly once");
        check(
            batch.steam == Math.addExact(batch.processingSteam, batch.maintenanceSteam),
            "steam total contains processing and maintenance exactly once");
        check(
            batch.processingEnergy >= 0 && batch.maintenanceEnergy >= 0
                && batch.processingSteam >= 0
                && batch.maintenanceSteam >= 0,
            "all bill components are nonnegative");
        return batch;
    }

    private static void parallelAndHostGrades() throws Exception {
        Item raw = item("host", true), output = item("host_product", false);
        recipe(CenterProcess.CRUSHER, raw, 9600, 2, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.CRUSHER, CenterGraph.BUS));
        int[] parallel = { 240, 960, 1920, 5120 }, ticks = { 4800, 1920, 960, 480 };
        for (int tier = 0; tier < 4; tier++) {
            CenterPlanner.Batch single = batch(graph, new ItemStack[] { stack(raw, 1) }, 1, tier);
            check(
                single.parallel == parallel[tier] && single.ticks == ticks[tier],
                "host tier " + tier + " uses the declared parallel and speed");
            CenterPlanner.Batch sectors = require(
                session(
                    Collections.singletonList(graph),
                    new ItemStack[] { stack(raw, 1) },
                    modules(graph, 1),
                    tier,
                    3,
                    1,
                    false,
                    Long.MAX_VALUE).batch(),
                "three-sector batch");
            check(sectors.parallel == 3 * parallel[tier], "sectors multiply the host base parallel");
        }
        for (int grade = 1; grade <= 3; grade++) {
            CenterPlanner.Batch upgraded = batch(graph, new ItemStack[] { stack(raw, 1) }, grade, 3);
            check(
                upgraded.ticks == new int[] { 480, 80, 10 }[grade - 1],
                "processing package grade uses time divisor 1/6/48: " + grade);
        }
        int[][] logistics = modules(graph, 1);
        logistics[CenterModule.LOGISTICS.ordinal()] = new int[] { 2, 3, 4 };
        CenterPlanner.Batch expanded = require(
            session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(raw, 1) },
                logistics,
                3,
                2,
                1,
                false,
                Long.MAX_VALUE).batch(),
            "logistics batch");
        check(
            expanded.parallel == 5120 * 2 + 2 * 240 + 3 * 1440 + 4 * 11520,
            "logistics grades add their independent parallel per installed package");
        CenterPlanner.Batch base = batch(graph, new ItemStack[] { stack(raw, 1) }, 1, 3);
        check(expanded.ticks == base.ticks, "logistics and extra empty lanes cannot change one-chain time");
    }

    private static void constantRoundTime() throws Exception {
        Item raw = item("constant", true), output = item("constant_product", false);
        recipe(CenterProcess.CRUSHER, raw, 401, 2, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.CRUSHER, CenterGraph.BUS));
        for (int count : new int[] { 1, 120, 240 }) {
            CenterPlanner.Batch result = batch(graph, new ItemStack[] { stack(raw, count) }, 1, 0);
            check(
                result.ticks == 201 && result.roots == count && result.chargedRounds == 1,
                "one/half/full round has constant chain time: " + count);
        }
        ItemStack original = stack(raw, 241);
        CenterPlanner.Batch capped = batch(graph, new ItemStack[] { original }, 1, 0);
        check(
            capped.roots == 240 && original.stackSize == 241,
            "ordinary batch admits at most P and planning does not mutate caller input");
    }

    private static void weightedOrigins() throws Exception {
        Item longOre = item("weighted_long", true), shortOre = item("weighted_short", true);
        Item longMiddle = item("weighted_long_middle", false), shortMiddle = item("weighted_short_middle", false);
        Item output = item("weighted_product", false);
        recipe(CenterProcess.THERMAL_CENTRIFUGE, longOre, 4000, 8, stack(longMiddle, 1));
        recipe(CenterProcess.THERMAL_CENTRIFUGE, shortOre, 400, 8, stack(shortMiddle, 1));
        recipe(CenterProcess.EXTRACTOR, longMiddle, 1000, 4, stack(output, 1));
        recipe(CenterProcess.EXTRACTOR, shortMiddle, 100, 4, stack(output, 1));
        CenterGraph graph = graph(
            node(CenterProcess.THERMAL_CENTRIFUGE, 1),
            node(CenterProcess.EXTRACTOR, CenterGraph.BUS));
        CenterPlanner.Batch mixed = batch(graph, new ItemStack[] { stack(longOre, 90), stack(shortOre, 10) }, 1, 1);
        check(mixed.ticks == 910, "90/10 origins weight their longest chain by actual raw input count");
        check(mixed.processingEnergy == 327600, "native electric cost sums actual recipe executions at ten percent");
        CenterPlanner.Batch reverse = batch(graph, new ItemStack[] { stack(shortOre, 10), stack(longOre, 90) }, 1, 1);
        check(
            reverse.ticks == mixed.ticks && reverse.energy == mixed.energy && reverse.steam == mixed.steam,
            "origin weighting and bills are independent of input visitation order");

        String longTag = CenterGraph.tags(stack(longOre, 1))[0], shortTag = CenterGraph.tags(stack(shortOre, 1))[0];
        CenterGraph longGraph = new CenterGraph(0, true, Collections.singletonList(longTag), graph.nodes);
        CenterGraph shortGraph = new CenterGraph(0, true, Collections.singletonList(shortTag), graph.nodes);
        CenterPlanner.Batch separate = require(
            session(
                Arrays.asList(longGraph, shortGraph),
                new ItemStack[] { stack(longOre, 90), stack(shortOre, 10) },
                modules(graph, 1),
                1,
                1,
                1,
                false,
                Long.MAX_VALUE).batch(),
            "separate graph fixture");
        check(
            separate.ticks == mixed.ticks && separate.energy == mixed.energy && separate.steam == mixed.steam,
            "separating matching ores into two graphs adds no empty-slot weighting or graph tax");
    }

    private static void fairAdmission() throws Exception {
        Item a = item("fair_a", true), b = item("fair_b", true), missing = item("fair_missing", true);
        Item excessiveVoltage = item("fair_excessive_voltage", true);
        Item output = item("fair_product", false);
        recipe(CenterProcess.CRUSHER, a, 400, 2, stack(output, 1));
        recipe(CenterProcess.CRUSHER, b, 800, 2, stack(output, 1));
        recipe(CenterProcess.CRUSHER, excessiveVoltage, 4000, 1920, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.CRUSHER, CenterGraph.BUS));
        for (ItemStack[] inputs : new ItemStack[][] { { stack(a, 100000), stack(b, 100000) },
            { stack(b, 100000), stack(a, 100000) } }) {
            CenterPlanner.Batch mixed = batch(graph, inputs, 1, 0);
            check(
                mixed.roots == 240 && mixed.consumed.stream()
                    .anyMatch(s -> s.getItem() == a)
                    && mixed.consumed.stream()
                        .anyMatch(s -> s.getItem() == b),
                "two large valid mineral stocks both enter bounded P regardless of slot order");
        }
        for (boolean queue : new boolean[] { false, true }) {
            CenterPlanner.Batch valid = require(
                session(
                    Collections.singletonList(graph),
                    new ItemStack[] { stack(missing, 100000), stack(a, 240) },
                    modules(graph, 1),
                    0,
                    1,
                    1,
                    queue,
                    Long.MAX_VALUE).batch(),
                "valid ore following a large recipe-less ore stock");
            check(
                valid.roots == 240 && valid.consumed.size() == 1
                    && valid.consumed.get(0)
                        .getItem() == a,
                "recipe-less first stock cannot starve a complete later queue: " + queue);
            for (ItemStack[] inputs : new ItemStack[][] { { stack(excessiveVoltage, 100000), stack(a, 100000) },
                { stack(a, 100000), stack(excessiveVoltage, 100000) } }) {
                CenterPlanner.Batch refilled = require(
                    session(Collections.singletonList(graph), inputs, modules(graph, 1), 0, 1, 1, queue, Long.MAX_VALUE)
                        .batch(),
                    "native rejection releases provisional raw seats");
                check(
                    refilled.roots == 240 && refilled.ticks == 200
                        && refilled.consumed.size() == 1
                        && refilled.consumed.get(0)
                            .getItem() == a,
                    "native voltage rejection cannot block or dilute another complete raw batch: " + queue);
                check(
                    inputs[0].stackSize == 100000 && inputs[1].stackSize == 100000,
                    "native rejection and deferred refilling leave caller inventory unchanged");
            }
        }
    }

    private static void mergedOrigins() throws Exception {
        Item longOre = item("merged_long", true), shortOre = item("merged_short", true);
        Item common = item("merged_middle", false), output = item("merged_product", false);
        recipe(CenterProcess.THERMAL_CENTRIFUGE, longOre, 4000, 8, stack(common, 1));
        recipe(CenterProcess.THERMAL_CENTRIFUGE, shortOre, 400, 8, stack(common, 1));
        recipe(CenterProcess.EXTRACTOR, common, 1000, 4, stack(output, 1));
        CenterGraph graph = graph(
            node(CenterProcess.THERMAL_CENTRIFUGE, 1),
            node(CenterProcess.EXTRACTOR, CenterGraph.BUS));
        CenterPlanner.Batch mixed = batch(graph, new ItemStack[] { stack(longOre, 90), stack(shortOre, 10) }, 1, 1);
        check(mixed.ticks == 928, "merging identical intermediate items preserves each origin's accumulated path");
        check(mixed.processingEnergy == 331200, "merged intermediates charge their actual common second recipe");
    }

    private static void multipliedAndBranchedOutputs() throws Exception {
        Item ore = item("branch", true), doubled = item("branch_double", false), side = item("branch_side", false);
        Item output = item("branch_product", false);
        recipe(CenterProcess.CRUSHER, ore, 400, 2, stack(doubled, 2), stack(side, 3));
        recipe(CenterProcess.HAMMER, doubled, 1000, 2, stack(output, 1));
        recipe(CenterProcess.SIFTER, side, 2000, 2, stack(output, 1));
        CenterGraph graph = graph(
            node(CenterProcess.CRUSHER, 1, 2),
            node(CenterProcess.HAMMER, CenterGraph.BUS),
            node(CenterProcess.SIFTER, CenterGraph.BUS));
        CenterPlanner.Batch result = batch(graph, new ItemStack[] { stack(ore, 10) }, 1, 0);
        check(
            result.roots == 10 && result.ticks == 1200,
            "product multiplication and parallel branches retain original root weight and longest path");
        check(
            result.outputs.stream()
                .mapToInt(s -> s.stackSize)
                .sum() == 50,
            "both multiplied output branches still execute all native recipe inputs");
        check(
            result.processingSteam == 22500,
            "steam bill counts 10 crusher, 20 hammer and 30 sifter executions at native durations");
    }

    private static void fixedTargetBatching() throws Exception {
        Item output = item("batch_product", false);
        CenterGraph graph = graph(node(CenterProcess.CRUSHER, CenterGraph.BUS));
        for (int time : new int[] { 10, 100, 128, 200 }) {
            Item ore = item("batch_time_" + time, true);
            recipe(CenterProcess.CRUSHER, ore, time * 2, 2, stack(output, 1));
            int capacity = time < 128 ? 240 * 128 / time : 240;
            long maintenance = -1;
            for (int count : new int[] { 1, capacity / 2, capacity, capacity + 1 }) {
                CenterPlanner.Batch result = require(
                    session(
                        Collections.singletonList(graph),
                        new ItemStack[] { stack(ore, count) },
                        modules(graph, 1),
                        0,
                        1,
                        128,
                        false,
                        Long.MAX_VALUE).batch(),
                    "fixed target batch at unrounded t=" + time);
                check(
                    result.parallel == 240 && result.capacity == capacity && result.roots == Math.min(count, capacity),
                    "target capacity changes admission while physical P remains 240 at t=" + time);
                check(
                    result.ticks == Math.max(128, time) && result.batched == (time < 128)
                        && result.weightedTicks == time,
                    "one, half and full target capacity share the fixed duration at t=" + time);
                if (maintenance >= 0) check(
                    result.maintenanceSteam == maintenance,
                    "maintenance follows the same target duration for partial and full target capacity");
                maintenance = result.maintenanceSteam;
            }
            CenterPlanner.Batch ordinary = batch(graph, new ItemStack[] { stack(ore, capacity + 1) }, 1, 0);
            check(
                ordinary.parallel == 240 && ordinary.capacity == 240
                    && ordinary.roots == 240
                    && ordinary.ticks == time
                    && !ordinary.batched,
                "ordinary mode preserves P and native weighted time at t=" + time);
            if (time == 10) {
                CenterPlanner.Batch doubled = require(
                    session(
                        Collections.singletonList(graph),
                        new ItemStack[] { stack(ore, 1000) },
                        modules(graph, 1),
                        0,
                        1,
                        2,
                        false,
                        Long.MAX_VALUE).batch(),
                    "modifier two batch");
                check(
                    doubled.ticks == 20 && doubled.capacity == 480 && doubled.roots == 480 && doubled.batched,
                    "modifier two grows the target to 2t while retaining physical P");
            }
        }
    }

    private static void weightedBatching() throws Exception {
        Item shortOre = item("batch_short", true), longOre = item("batch_long", true);
        Item output = item("batch_mixed_product", false);
        recipe(CenterProcess.CRUSHER, shortOre, 20, 2, stack(output, 1));
        recipe(CenterProcess.CRUSHER, longOre, 200, 2, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.CRUSHER, CenterGraph.BUS));
        CenterPlanner.Batch first = null;
        for (ItemStack[] inputs : new ItemStack[][] { { stack(shortOre, 720), stack(longOre, 80) },
            { stack(longOre, 80), stack(shortOre, 720) } }) {
            CenterPlanner.Batch mixed = require(
                session(Collections.singletonList(graph), inputs, modules(graph, 1), 0, 1, 128, false, Long.MAX_VALUE)
                    .batch(),
                "global weighted mixed batch");
            check(
                mixed.roots == 800 && mixed.parallel == 240
                    && mixed.capacity == 1616
                    && mixed.ticks == 128
                    && mixed.weightedTicks == 19
                    && mixed.chargedRounds == 4,
                "90/10 global raw weighting defines one target without ordered per-round duration");
            check(mixed.processingSteam == 12667, "all mixed native recipe work is billed at ten percent once");
            if (first != null) check(
                mixed.energy == first.energy && mixed.steam == first.steam,
                "mixed batch final bills are independent of input visitation order");
            first = mixed;
        }
    }

    private static void subTickBatching() throws Exception {
        Item ore = item("batch_subtick", true), output = item("batch_subtick_product", false);
        recipe(CenterProcess.CRUSHER, ore, 15, 2, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.CRUSHER, CenterGraph.BUS));
        int capacity = 512 * 1920;
        for (int count : new int[] { 1, capacity / 2, capacity, capacity + 1 }) {
            CenterPlanner.Batch result = require(
                session(
                    Collections.singletonList(graph),
                    new ItemStack[] { stack(ore, count) },
                    modules(graph, 2),
                    2,
                    1,
                    128,
                    false,
                    Long.MAX_VALUE).batch(),
                "subtick fixed target batch");
            check(
                result.weightedTicks == .25 && result.parallel == 1920
                    && result.capacity == capacity
                    && result.roots == Math.min(count, capacity)
                    && result.ticks == 128
                    && result.batched,
                "unrounded quarter-tick work retains 512P capacity and a 128 tick target");
        }
        CenterPlanner.Batch full = require(
            session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(ore, capacity) },
                modules(graph, 2),
                2,
                1,
                128,
                false,
                Long.MAX_VALUE).batch(),
            "full subtick batch");
        check(
            full.chargedRounds == 512 && full.processingSteam == 2304000,
            "subtick batch charges 512 actual native batches rather than enlarging P");
        CenterPlanner.Batch ordinary = batch(graph, new ItemStack[] { stack(ore, capacity) }, 2, 2);
        check(
            ordinary.ticks == 1 && ordinary.weightedTicks == .25 && ordinary.roots == 1920 && !ordinary.batched,
            "ordinary subtick execution rounds only duration and admits physical P");
        CenterPlanner.Batch smallModifier = require(
            session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(ore, 20000) },
                modules(graph, 2),
                2,
                1,
                2,
                false,
                Long.MAX_VALUE).batch(),
            "subtick modifier two target");
        check(
            smallModifier.ticks == 2 && smallModifier.capacity == 15360
                && smallModifier.roots == 15360
                && smallModifier.parallel == 1920
                && smallModifier.chargedRounds == 8,
            "subtick modifier two expands capacity using t=.25 while its target remains two ticks");
    }

    private static void processingBillsAndPowerGate() throws Exception {
        Item ore = item("electric", true), output = item("electric_product", false);
        recipe(CenterProcess.THERMAL_CENTRIFUGE, ore, 400, 8, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.THERMAL_CENTRIFUGE, CenterGraph.BUS));
        for (int grade = 1; grade <= 3; grade++) {
            CenterPlanner.Batch result = batch(graph, new ItemStack[] { stack(ore, 10) }, grade, 3);
            check(
                result.processingEnergy == 3200 && result.processingSteam == 157,
                "grade " + grade + " changes time without applying old grade multipliers to processing bills");
        }
        CenterPlanner.Batch ample = batch(graph, new ItemStack[] { stack(ore, 10) }, 1, 1);
        check(
            session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(ore, 10) },
                modules(graph, 1),
                1,
                1,
                1,
                false,
                1).batch() == null,
            "insufficient electric supply rejects a batch instead of extending time");
        long enough = (ample.energy + ample.ticks - 1) / ample.ticks;
        CenterPlanner.Batch exact = require(
            session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(ore, 10) },
                modules(graph, 1),
                1,
                1,
                1,
                false,
                enough).batch(),
            "sufficient average native power");
        check(exact.ticks == ample.ticks, "power admission never stretches the weighted process duration");
        Item shortOre = item("electric_batched", true);
        recipe(CenterProcess.THERMAL_CENTRIFUGE, shortOre, 50, 8, stack(output, 1));
        CenterPlanner.Batch shortBatch = require(
            session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(shortOre, 12288) },
                modules(graph, 1),
                1,
                1,
                128,
                false,
                Long.MAX_VALUE).batch(),
            "full fixed-target electric batch");
        check(
            shortBatch.parallel == 960 && shortBatch.capacity == 12288
                && shortBatch.roots == 12288
                && shortBatch.ticks == 128
                && shortBatch.chargedRounds == 13,
            "short electric recipes use one fixed target and retain physical P");
        check(
            shortBatch.processingEnergy == 491520,
            "fixed-target electric batching pays ten percent of all 12288 native recipe executions");
    }

    private static void queueAndBoosterPayment() throws Exception {
        Item ore = item("queue", true), output = item("queue_product", false), missing = item("missing", true);
        recipe(CenterProcess.CRUSHER, ore, 20, 2, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.CRUSHER, CenterGraph.BUS));
        check(
            session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(ore, 239), stack(missing, 500) },
                modules(graph, 1),
                0,
                1,
                1,
                true,
                Long.MAX_VALUE).batch() == null,
            "queue counts successfully planned recipe roots rather than unrelated matching ores");
        Fluid original = Materials.NitricAcid.mFluid;
        Fluid acid = new Fluid("gtsr_parallel_contract_nitric");
        FluidRegistry.registerFluid(acid);
        Materials.NitricAcid.mFluid = acid;
        try {
            int[][] boosted = modules(graph, 1);
            boosted[CenterModule.PARALLEL.ordinal()][0] = 1;
            CenterPlanner.Session unpaid = session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(ore, 240) },
                boosted,
                0,
                1,
                1,
                true,
                Long.MAX_VALUE,
                new FluidStack(acid, 3));
            check(
                unpaid.parallelLimit() == 240 && unpaid.batch() != null,
                "unpaid booster neither changes P nor prevents the base queue from starting");
            CenterPlanner.Session waiting = session(
                Collections.singletonList(graph),
                new ItemStack[] { stack(ore, 379) },
                boosted,
                0,
                1,
                1,
                true,
                Long.MAX_VALUE,
                new FluidStack(acid, 4));
            check(
                waiting.parallelLimit() == 380 && waiting.batch() == null,
                "paid original parallel amplifier applies before queue admission");
            CenterPlanner.Batch paid = require(
                session(
                    Collections.singletonList(graph),
                    new ItemStack[] { stack(ore, 380) },
                    boosted,
                    0,
                    1,
                    1,
                    true,
                    Long.MAX_VALUE,
                    new FluidStack(acid, 4)).batch(),
                "fully paid parallel queue");
            check(
                paid.parallel == 380 && paid.boostFluids[1] == 4,
                "paid queue records current P and one original dose");
            CenterPlanner.Batch limited = require(
                session(
                    Collections.singletonList(graph),
                    new ItemStack[] { stack(ore, 760) },
                    boosted,
                    0,
                    1,
                    2,
                    false,
                    Long.MAX_VALUE,
                    new FluidStack(acid, 4)).batch(),
                "booster-affordable round");
            check(
                limited.chargedRounds == 1 && limited.roots == 380,
                "batch cannot admit a second boosted round without its dose");
            CenterPlanner.Batch tail = require(
                session(
                    Collections.singletonList(graph),
                    new ItemStack[] { stack(ore, 1) },
                    boosted,
                    0,
                    1,
                    1,
                    false,
                    Long.MAX_VALUE,
                    new FluidStack(acid, 4)).batch(),
                "fractional boosted tail bill");
            CenterPlanner.Batch two = require(
                session(
                    Collections.singletonList(graph),
                    new ItemStack[] { stack(ore, 381) },
                    boosted,
                    0,
                    1,
                    2,
                    false,
                    Long.MAX_VALUE,
                    new FluidStack(acid, 8)).batch(),
                "paid boosted full and partial round");
            check(
                two.chargedRounds == 2 && two.boostFluids[1] == 8
                    && two.ticks == 20
                    && two.capacity == 760
                    && two.batched,
                "modifier two uses the fixed target and charges both actual recipe batches");
            check(
                two.processingSteam == 3175 && paid.processingSteam == 3167 && tail.processingSteam == 9,
                "boosted batch sums unrounded native work and rounds only its final ledger");
            check(
                two.maintenanceSteam == paid.maintenanceSteam + tail.maintenanceSteam,
                "modifier two maintenance covers its twenty-tick fixed target");
            Item quarterTick = item("paid_subtick", true);
            recipe(CenterProcess.CRUSHER, quarterTick, 15, 2, stack(output, 1));
            int[][] subtickModules = modules(graph, 2);
            subtickModules[CenterModule.PARALLEL.ordinal()][0] = 1;
            int physicalP = 2763, targetCapacity = physicalP * 512;
            for (int doses : new int[] { 1, 2, 3, 512 }) {
                CenterPlanner.Batch affordable = require(
                    session(
                        Collections.singletonList(graph),
                        new ItemStack[] { stack(quarterTick, targetCapacity + 1) },
                        subtickModules,
                        2,
                        1,
                        128,
                        false,
                        Long.MAX_VALUE,
                        new FluidStack(acid, doses * 4)).batch(),
                    "subtick dose-limited batch");
                check(
                    affordable.parallel == physicalP && affordable.capacity == targetCapacity
                        && affordable.roots == physicalP * doses
                        && affordable.chargedRounds == doses
                        && affordable.boostFluids[1] == doses * 4
                        && affordable.ticks == 128,
                    "actual dose count limits admitted roots without inflating physical P: " + doses);
            }
        } finally {
            Materials.NitricAcid.mFluid = original;
        }
    }

    private static void speedBoosterBilling() throws Exception {
        Item ore = item("speed", true), output = item("speed_product", false);
        recipe(CenterProcess.THERMAL_CENTRIFUGE, ore, 400, 8, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.THERMAL_CENTRIFUGE, CenterGraph.BUS));
        CenterPlanner.Batch base = batch(graph, new ItemStack[] { stack(ore, 10) }, 1, 1);
        Fluid original = Materials.HydrochloricAcid.mFluid;
        Fluid acid = new Fluid("gtsr_parallel_contract_hydrochloric");
        FluidRegistry.registerFluid(acid);
        Materials.HydrochloricAcid.mFluid = acid;
        try {
            int[][] counts = modules(graph, 1);
            counts[CenterModule.SPEED.ordinal()][0] = 1;
            CenterPlanner.Batch result = require(
                session(
                    Collections.singletonList(graph),
                    new ItemStack[] { stack(ore, 10) },
                    counts,
                    1,
                    1,
                    1,
                    false,
                    Long.MAX_VALUE,
                    new FluidStack(acid, 4)).batch(),
                "paid original speed booster");
            check(
                base.ticks == 80 && result.ticks == 70 && result.boostFluids[0] == 4,
                "original speed gain applies both original factors and consumes one original dose");
            check(
                result.processingEnergy == base.processingEnergy && result.processingSteam == base.processingSteam,
                "booster surcharges apply to maintenance without inflating native processing bills");
        } finally {
            Materials.HydrochloricAcid.mFluid = original;
        }
    }

    private static void unchangedBoosterValues() throws Exception {
        Field field = CenterPlanner.class.getDeclaredField("BOOSTS");
        field.setAccessible(true);
        CenterPlanner.Boost[] boosts = (CenterPlanner.Boost[]) field.get(null);
        double[][] gain = { { 5, 10, 20 }, { 4, 8, 16 }, { .5, 1, 2 }, { 1, 2.5, 5 } };
        int[][] price = { { 4, 14, 45 }, { 4, 12, 36 }, { 8, 24, 75 }, { 3, 10, 30 } };
        double[][] steam = { { .05, .12, .24 }, { .04, .10, .20 }, { .08, .16, .30 }, { .05, .12, .22 } };
        double[][] eu = { { .03, .07, .14 }, { .02, .06, .12 }, { .04, .09, .18 }, { .03, .07, .14 } };
        double[] cap = { 50, 48, 8, 20 };
        for (int i = 0; i < boosts.length; i++) {
            check(
                Arrays.equals(boosts[i].gain, gain[i]) && Arrays.equals(boosts[i].price, price[i])
                    && Arrays.equals(boosts[i].steam, steam[i])
                    && Arrays.equals(boosts[i].eu, eu[i])
                    && boosts[i].cap == cap[i],
                "original gain, price, surcharge and cap for booster " + i);
        }
    }

    private static void numericBoundaries() throws Exception {
        Item ore = item("tiny", true), output = item("tiny_product", false);
        recipe(CenterProcess.EXTRACTOR, ore, 1, 1, stack(output, 1));
        CenterGraph graph = graph(node(CenterProcess.EXTRACTOR, CenterGraph.BUS));
        CenterPlanner.Batch tiny = batch(graph, new ItemStack[] { stack(ore, 1) }, 1, 1);
        check(
            tiny.ticks == 1 && tiny.processingEnergy == 1 && tiny.processingSteam == 1,
            "positive fractional processing costs and durations round up to positive integers");
        Item overflow = item("overflow", true), enormous = item("overflow_product", false);
        recipe(CenterProcess.CRUSHER, overflow, 40, 2, stack(enormous, Integer.MAX_VALUE));
        CenterGraph crusher = graph(node(CenterProcess.CRUSHER, CenterGraph.BUS));
        ItemStack caller = stack(overflow, Integer.MAX_VALUE);
        CenterPlanner.Batch overflowSafe = session(
            Collections.singletonList(crusher),
            new ItemStack[] { caller },
            modules(crusher, 1),
            0,
            1,
            1,
            false,
            Long.MAX_VALUE).batch();
        check(
            (overflowSafe == null || overflowSafe.roots == 1 && overflowSafe.outputs.size() == 1
                && overflowSafe.outputs.get(0).stackSize == Integer.MAX_VALUE) && caller.stackSize == Integer.MAX_VALUE,
            "native output overflow safely rejects or shrinks without wrapping or mutating inventory");
        CenterPlanner.Batch hugeInput = batch(graph, new ItemStack[] { stack(ore, Integer.MAX_VALUE) }, 1, 3);
        check(
            hugeInput.roots == 5120 && hugeInput.energy > 0 && hugeInput.steam > 0,
            "large caller stack is capped to P while Long.MAX_VALUE power cannot overflow capacity arithmetic");
        Item fast = item("capacity_limit", true);
        recipe(CenterProcess.CRUSHER, fast, 1, 2, stack(output, 1));
        CenterPlanner.Batch bounded = require(
            session(
                Collections.singletonList(crusher),
                new ItemStack[] { stack(fast, Integer.MAX_VALUE) },
                modules(crusher, 3),
                3,
                1,
                128,
                false,
                Long.MAX_VALUE).batch(),
            "global capacity ceiling");
        check(
            bounded.parallel == 5120 && bounded.capacity == 64000000
                && bounded.roots == 64000000
                && bounded.chargedRounds == 12500
                && bounded.ticks == 128,
            "subtick capacity clips to 64 million without enlarging P or truncating actual batch count");
        check(
            bounded.energy >= 0 && bounded.steam > 0,
            "maximum admitted capacity keeps final bills nonnegative and within long range");
    }

    private static void tickRemainderBoundaries() {
        for (long amount : new long[] { 0, 1, 23, Integer.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE }) {
            for (int ticks : new int[] { 1, 2, 3, 7, 24000, Integer.MAX_VALUE }) {
                for (int elapsed : new int[] { 0, ticks / 2, ticks - 1 }) {
                    BigInteger bill = BigInteger.valueOf(amount), duration = BigInteger.valueOf(ticks);
                    long expected = bill.multiply(BigInteger.valueOf((long) elapsed + 1))
                        .divide(duration)
                        .subtract(
                            bill.multiply(BigInteger.valueOf(elapsed))
                                .divide(duration))
                        .longValueExact();
                    check(
                        MTEMineralIntegrationCenter.tickCharge(amount, elapsed, ticks) == expected,
                        "tick remainder matches exact arithmetic at amount=" + amount
                            + ", elapsed="
                            + elapsed
                            + ", ticks="
                            + ticks);
                }
                if (ticks <= 24000) {
                    long sum = 0;
                    for (int elapsed = 0; elapsed < ticks; elapsed++)
                        sum = Math.addExact(sum, MTEMineralIntegrationCenter.tickCharge(amount, elapsed, ticks));
                    check(sum == amount, "tick charges distribute the exact full bill without overflow");
                }
            }
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
