package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility.ItemId;

/**
 * Exact, complete native query results within one uninterrupted planner slice only. GT has no public recipe revision,
 * and its shared cache can change the first result between ticks. Session.advance clears this memo before every slice.
 */
final class CenterRecipeMemo {

    private static final int LIMIT = 128;
    private final Map<Key, List<GTRecipe>> results = new LinkedHashMap<>();
    private long hits, queries, checks;

    void clear() {
        results.clear();
    }

    GTRecipe find(RecipeMap<?> map, ItemStack[] items, FluidStack[] fluids, long voltage, int heat) {
        if (map == null) return null;
        // A custom backend can generate, reorder or mutate recipes/cache state during a query.
        // Never let an earlier ordinary result survive such a call, including calls into native furnace/recycler.
        if (map.getBackend()
            .getClass() != RecipeMapBackend.class
            || map.getBackend()
                .getProperties().specialSlotSensitive) {
            clear();
            queries++;
            return CenterPlanner.findRecipe(map, items, fluids, voltage, heat);
        }
        Key key = new Key(map, items, fluids, voltage, heat);
        List<GTRecipe> found = results.get(key);
        if (found != null) {
            boolean valid = true;
            for (GTRecipe recipe : found) {
                checks++;
                if (!recipe.mCanBeBuffered || !CenterPlanner.usableRecipe(recipe, heat)
                    || recipe.mEUt > voltage * map.getAmperage()
                    || !recipe.isRecipeInputEqual(false, false, 1, fluids, items)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                hits++;
                return CenterPlanner.selectRecipe(found);
            }
            results.remove(key);
        }
        queries++;
        found = CenterPlanner.findRecipeCandidates(map, items, fluids, voltage, heat);
        boolean complete = !found.isEmpty() && found.size() <= CenterPlanner.MAX_CANDIDATES;
        for (GTRecipe recipe : found) {
            if (recipe.getClass() != GTRecipe.class || !recipe.mCanBeBuffered) {
                // A subclass can itself have side effects; discard other memo entries as well.
                clear();
                complete = false;
                break;
            }
        }
        if (complete) {
            if (results.size() == LIMIT && !results.containsKey(key)) results.remove(
                results.keySet()
                    .iterator()
                    .next());
            results.put(key, found);
        }
        return CenterPlanner.selectRecipe(found);
    }

    long hits() {
        return hits;
    }

    long queries() {
        return queries;
    }

    long checks() {
        return checks;
    }

    int size() {
        return results.size();
    }

    private static final class Key {

        final RecipeMap<?> map;
        final ItemId[] items;
        final int[] quantities;
        final FluidStack[] fluids;
        final long voltage;
        final int heat, hash;

        Key(RecipeMap<?> map, ItemStack[] items, FluidStack[] fluids, long voltage, int heat) {
            this.map = map;
            this.items = new ItemId[items.length];
            quantities = new int[items.length];
            for (int i = 0; i < items.length; i++) if (items[i] != null) {
                this.items[i] = CenterPlanner.itemKey(items[i].copy());
                quantities[i] = items[i].stackSize;
            }
            this.fluids = new FluidStack[fluids.length];
            int fluidHash = 1;
            for (int i = 0; i < fluids.length; i++) {
                FluidStack fluid = fluids[i];
                this.fluids[i] = fluid == null ? null : fluid.copy();
                fluidHash = 31 * fluidHash + (fluid == null ? 0 : 31 * fluid.getFluidID() + fluid.amount);
                fluidHash = 31 * fluidHash + (fluid == null || fluid.tag == null ? 0 : fluid.tag.hashCode());
            }
            this.voltage = voltage;
            this.heat = heat;
            hash = 31
                * (31 * (31 * System.identityHashCode(map) + Arrays.hashCode(this.items)) + Arrays.hashCode(quantities))
                + fluidHash
                + 31 * Long.hashCode(voltage)
                + heat;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            if (map != key.map || voltage != key.voltage
                || heat != key.heat
                || !Arrays.equals(items, key.items)
                || !Arrays.equals(quantities, key.quantities)
                || fluids.length != key.fluids.length) return false;
            for (int i = 0; i < fluids.length; i++) {
                FluidStack left = fluids[i], right = key.fluids[i];
                if (left == null ? right != null
                    : right == null || left.amount != right.amount || !left.isFluidEqual(right)) return false;
            }
            return true;
        }
    }
}
