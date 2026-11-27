// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.ItemList;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** A bounded, read-only trace of the selected live recipe maps. It never changes world or inventory. */
public final class CenterGraphPreview {

    private static final int MAX_VISITS = 128;
    private static final Map<CenterProcess, FluidStack[]> WASHING_FLUIDS = new ConcurrentHashMap<>();
    private final CenterGraph graph;
    private final int tier;
    private final int entryNode;
    private final long voltage;
    private final FluidStack[] fluids;
    private final Queue<Visit> queue = new ArrayDeque<>();
    private final List<List<Product>> products;
    private final List<Set<GTUtility.ItemId>> visited;
    private final boolean[] conditionalRecipes;
    private int visits;

    public static final class Product {

        public final ItemStack item;
        public final FluidStack fluid;
        public final int port;
        public final int chance;

        private Product(ItemStack item, FluidStack fluid, int port, int chance) {
            this.item = item;
            this.fluid = fluid;
            this.port = port;
            this.chance = chance;
        }

        public boolean canEnter(CenterProcess process) {
            if (process == CenterProcess.RECYCLER) return item != null;
            if (process == CenterProcess.SMELTING) return item != null && FurnaceRecipes.smelting()
                .getSmeltingResult(item) != null;
            var recipes = process.recipes();
            return recipes != null
                && (item != null ? recipes.containsInput(item) : recipes.containsInput(fluid.getFluid()));
        }
    }

    private static final class Visit {

        final int node;
        final ItemStack item;
        final boolean intermediate;

        Visit(int node, ItemStack item, boolean intermediate) {
            this.node = node;
            this.item = item;
            this.intermediate = intermediate;
        }
    }

    public CenterGraphPreview(CenterGraph graph, ItemStack sample, NBTTagCompound state) {
        this.graph = graph;
        this.tier = Math.max(0, Math.min(3, state.getInteger("Tier")));
        this.voltage = state.getLong("Voltage");
        this.products = new ArrayList<>(graph.nodes.size());
        this.visited = new ArrayList<>(graph.nodes.size());
        this.conditionalRecipes = new boolean[graph.nodes.size()];
        for (int i = 0; i < graph.nodes.size(); i++) {
            products.add(new ArrayList<>());
            visited.add(new HashSet<>());
        }
        this.fluids = fluids(state);
        entryNode = graph.nodes.isEmpty() ? -1 : graph.entry(sample, CenterGraph.tags(sample));
        if (entryNode >= 0) queue.add(new Visit(entryNode, sample.copy(), CenterPlanner.isIntermediate(sample)));
    }

    private static FluidStack[] fluids(NBTTagCompound state) {
        NBTTagList tanks = state.getTagList("Fluids", 10);
        List<FluidStack> available = new ArrayList<>(tanks.tagCount());
        for (int i = 0; i < tanks.tagCount(); i++) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(tanks.getCompoundTagAt(i));
            if (fluid != null) available.add(fluid);
        }
        return available.toArray(new FluidStack[0]);
    }

    /** A read-only estimate of the configured route using the same bounded planner as the controller. */
    public static CenterPlanner.Session trial(CenterGraph graph, ItemStack item, int quantity, NBTTagCompound state) {
        int mask = state.getInteger("Sectors"), tier = state.getInteger("Tier");
        if (graph == null || item == null
            || quantity < 1
            || quantity > 4096
            || !state.getBoolean("Formed")
            || mask == 0
            || tier < 0
            || tier > 3) return null;
        CenterModule[] kinds = CenterModule.values();
        int[][] counts = new int[kinds.length][3];
        int[] modules = state.getIntArray("Modules"), grades = state.getIntArray("Grades");
        for (int i = 0; i < Math.min(36, Math.min(modules.length, grades.length)); i++) {
            if ((mask & 1 << (i / 6)) == 0 || grades[i] < 1
                || grades[i] > 3
                || modules[i] < 0
                || modules[i] >= kinds.length) continue;
            counts[modules[i]][grades[i] - 1]++;
        }
        if (counts[CenterModule.LOGISTICS.ordinal()][0] + counts[CenterModule.LOGISTICS.ordinal()][1]
            + counts[CenterModule.LOGISTICS.ordinal()][2] == 0) return null;
        ItemStack sample = item.copy();
        sample.stackSize = quantity;
        return new CenterPlanner.Session(
            Collections.singletonList(graph),
            new ItemStack[] { sample },
            fluids(state),
            counts,
            tier,
            Integer.bitCount(mask),
            state.getLong("Voltage"),
            state.getLong("Power"),
            CenterPlanner.HEAT[tier],
            0L);
    }

    public List<Product> products(int node) {
        return node < products.size() ? products.get(node) : Collections.emptyList();
    }

    public boolean conditionalRecipe(int node) {
        return conditionalRecipes[node];
    }

    public int entryNode() {
        return entryNode;
    }

    private GTRecipe previewRecipe(CenterGraph.Node node, RecipeMap<?> map, ItemStack[] items) {
        if (map == null || !map.containsInput(items[0])) return null;
        FluidStack[] inputs = switch (node.process) {
            case WASHER, SIMPLE_WASHER, CHEMICAL_BATH -> WASHING_FLUIDS.computeIfAbsent(node.process, process -> {
                Map<Fluid, FluidStack> supply = new java.util.LinkedHashMap<>();
                for (GTRecipe recipe : map.getAllRecipes()) for (FluidStack fluid : recipe.mFluidInputs) {
                    if (fluid != null) supply.putIfAbsent(fluid.getFluid(), fluid);
                }
                return supply.values()
                    .toArray(new FluidStack[0]);
            });
            default -> fluids;
        };
        return map.findRecipeQuery()
            .items(items)
            .fluids(inputs)
            .dontCheckStackSizes(true)
            .filter(recipe -> CenterPlanner.usableRecipe(recipe, CenterPlanner.HEAT[3]))
            .find();
    }

    public void advance() {
        for (int i = 0; i < 4 && !queue.isEmpty() && visits < MAX_VISITS; i++, visits++) {
            Visit visit = queue.remove();
            ItemStack input = visit.item.copy();
            input.stackSize = Math.max(1, input.stackSize);
            if (!visited.get(visit.node)
                .add(GTUtility.ItemId.createNoCopy(input))) continue;
            CenterGraph.Node node = graph.nodes.get(visit.node);
            long capacity = CenterPlanner.recipeVoltage(node.process, tier, voltage);
            ItemStack[] items = node.circuit == 0 ? new ItemStack[] { input }
                : new ItemStack[] { input, ItemList.Circuit_Integrated.getWithDamage(0, node.circuit) };
            RecipeMap<?> map = node.process.recipes();
            GTRecipe recipe = CenterPlanner.findRecipe(map, items, fluids, capacity, CenterPlanner.HEAT[tier]);
            if (recipe == null || !recipe.isRecipeInputEqual(false, false, 1, fluids, items)) {
                recipe = previewRecipe(node, map, items);
                if (recipe != null) conditionalRecipes[visit.node] = true;
            }
            if (recipe == null) {
                if (visit.intermediate && visit.node + 1 < graph.nodes.size())
                    queue.add(new Visit(visit.node + 1, visit.item, true));
                continue;
            }
            List<Product> output = products.get(visit.node);
            for (int slot = 0; slot < recipe.mOutputs.length; slot++) {
                ItemStack stack = recipe.mOutputs[slot];
                int chance = recipe.getOutputChance(slot);
                if (stack == null || stack.stackSize <= 0 || chance == 0) continue;
                int outlet = node.routedOutlet(slot, stack);
                int port = node.process.direct ? 0
                    : Math.min(Math.max(node.process.outletCount(), node.targetCount()) - 1, outlet);
                output.add(new Product(stack.copy(), null, port, chance));
                int target = node.targetAtOutlet(outlet, stack);
                if (target >= 0 && queue.size() < MAX_VISITS) queue.add(new Visit(target, stack, false));
            }
            for (int slot = 0; slot < recipe.mFluidOutputs.length; slot++) {
                FluidStack fluid = recipe.mFluidOutputs[slot];
                int chance = recipe.getFluidOutputChance(slot);
                if (fluid != null && fluid.amount > 0 && chance > 0)
                    output.add(new Product(null, fluid.copy(), 0, chance));
            }
        }
    }
}
