// SPDX-License-Identifier: AGPL-3.0-only
package io.github.miaokatze.recipeaudit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

/**
 * Portable testlab source: only Minecraft/Forge/GT dependencies, no consumer Mod or Core dependency.
 * Call on the server thread after final recipe registration. Never mutates recipe registration or consumes input.
 */
public final class NativeRecipeAudit {

    private final RecipeMap<?> map;
    private final Predicate<GTRecipe> interest;
    private final List<GTRecipe> recipes = new ArrayList<>();
    private final Map<GTRecipe, Integer> ids = new IdentityHashMap<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final List<Object> findings = new ArrayList<>(), errors = new ArrayList<>(),
        regressions = new ArrayList<>();
    private final List<String> missing = new ArrayList<>();
    private int cursor, checked, skipped, targetChecked, targetConflicts, conflicts;
    private int derivedResultMappings;

    public NativeRecipeAudit(RecipeMap<?> map, Predicate<GTRecipe> interest) {
        if (map == null || interest == null) throw new IllegalArgumentException("map and interest are required");
        this.map = map;
        this.interest = interest;
        recipes.addAll(map.getAllRecipes());
        for (int i = 0; i < recipes.size(); i++) ids.put(recipes.get(i), i);
        evidence.put("schema", 2);
        evidence.put("map", map.unlocalizedName);
        evidence.put("engine", "GT5U native findRecipeQuery().findAll(); no consumption; no explicit cache writes");
        evidence.put("scope", "Enabled registered non-fake recipes in this map; concrete nominal and buffered inputs");
        evidence.put(
            "limitations",
            Arrays.asList(
                "Not exhaustive: ore-dictionary substitutions, NBT variants and combinations of unrelated recipes in one inventory",
                "Machine-specific validation and special metadata may further restrict execution",
                "Rejected registrations are absent; only caller-supplied expected outputs are checked for presence",
                "Overlaps require review; equal-output alternatives may be intentional",
                "Input profiles: original consumed quantities or max(original, stack limit); fluids max(original, 64000); selectors present as one",
                "The interest predicate supplied by the caller determines target attribution",
                "Time and recipe count budgets are soft limits between queries; a single native query may exceed the time budget"));
        evidence.put("registeredRecipes", recipes.size());
        evidence.put("findings", findings);
        evidence.put("errors", errors);
        evidence.put("regressions", regressions);
        evidence.put("missingExpectedOutputs", missing);
    }

    public void expectOutput(String key, ItemStack wanted) {
        if (wanted == null) throw new IllegalArgumentException("expected output is null: " + key);
        if (recipes.stream()
            .noneMatch(r -> r.mEnabled && !r.mFakeRecipe && outputs(r, wanted))) missing.add(key);
    }

    /**
     * Optional caller-provided regression: previous concrete inputs selected a competing output, new inputs select only
     * wanted.
     */
    public void verifyReplacement(String key, ItemStack wanted, ItemStack[] oldInputs, FluidStack[] oldFluids,
        ItemStack previousCompetingOutput) {
        GTRecipe target = recipes.stream()
            .filter(r -> r.mEnabled && !r.mFakeRecipe && outputs(r, wanted))
            .findFirst()
            .orElse(null);
        if (target == null) {
            regressions.add(row("id", key, "pass", false, "reason", "Expected recipe missing"));
            return;
        }
        List<GTRecipe> prior = lookup(oldInputs, oldFluids, target.mEUt);
        List<GTRecipe> current = lookup(items(target, false), fluids(target, false), target.mEUt);
        boolean reproduced = prior.stream()
            .anyMatch(r -> outputs(r, previousCompetingOutput));
        regressions.add(
            row(
                "id",
                key,
                "oldConflictReproduced",
                reproduced,
                "oldMatches",
                prior.stream()
                    .map(this::describe)
                    .collect(Collectors.toList()),
                "fixedMatches",
                current.stream()
                    .map(this::describe)
                    .collect(Collectors.toList()),
                "pass",
                reproduced && current.size() == 1 && current.get(0) == target));
    }

    /** Returns true when the snapshot has been processed. Call over successive server ticks. */
    public boolean step(long budgetNanos, int maxRecipes) {
        if (budgetNanos <= 0 || maxRecipes <= 0) throw new IllegalArgumentException("positive budgets required");
        long started = System.nanoTime();
        int batch = 0;
        while (cursor < recipes.size() && batch++ < maxRecipes && System.nanoTime() - started < budgetNanos) {
            GTRecipe recipe = recipes.get(cursor++);
            if (!recipe.mEnabled || recipe.mFakeRecipe || recipe.mInputs.length + recipe.mFluidInputs.length == 0) {
                skipped++;
                continue;
            }
            checked++;
            try {
                boolean relevant = interest.test(recipe);
                if (relevant) targetChecked++;
                inspect(recipe, false, relevant);
                inspect(recipe, true, relevant);
            } catch (RuntimeException failure) {
                errors.add(row("recipe", describe(recipe), "error", failure.toString()));
            }
        }
        return cursor == recipes.size();
    }

    public Map<String, Object> report() {
        boolean done = cursor == recipes.size();
        evidence.put("checked", checked);
        evidence.put("skipped", skipped);
        evidence.put("targetChecked", targetChecked);
        evidence.put("targetConflictingProfiles", targetConflicts);
        evidence.put("conflictingProfiles", conflicts);
        evidence.put("derivedResultMappings", derivedResultMappings);
        boolean regressionsOK = regressions.stream()
            .allMatch(r -> Boolean.TRUE.equals(((Map<?, ?>) r).get("pass")));
        evidence
            .put("collectionComplete", done && checked > 0 && errors.isEmpty() && missing.isEmpty() && regressionsOK);
        evidence.put("lookupCoverageComplete", done && errors.isEmpty());
        return new LinkedHashMap<>(evidence);
    }

    public String progress() {
        return cursor + "/" + recipes.size() + "; findings=" + findings.size();
    }

    private void inspect(GTRecipe target, boolean buffered, boolean relevant) {
        ItemStack[] items = items(target, buffered);
        FluidStack[] fluids = fluids(target, buffered);
        List<GTRecipe> matches = lookup(items, fluids, target.mEUt);
        if (!matches.contains(target)) {
            errors.add(
                row(
                    "recipe",
                    describe(target),
                    "profile",
                    buffered ? "buffered" : "nominal",
                    "error",
                    "Own concrete inputs do not find the registered recipe"));
        }
        List<GTRecipe> other = matches.stream()
            .filter(r -> r != target)
            .collect(Collectors.toList());
        if (other.isEmpty()) return;
        conflicts++;
        if (relevant || other.stream()
            .anyMatch(interest)) targetConflicts++;
        Map<String, Object> finding = row(
            "target",
            describe(target),
            "profile",
            buffered ? "buffered" : "nominal",
            "targetRelevant",
            relevant || other.stream()
                .anyMatch(interest),
            "inputs",
            itemRows(items),
            "fluids",
            fluidRows(fluids),
            "firstMatch",
            matches.isEmpty() ? null : describe(matches.get(0)));
        List<Object> rivals = new ArrayList<>();
        for (GTRecipe rival : other) {
            GTRecipe cached = map.findRecipeQuery()
                .items(items)
                .fluids(fluids)
                .voltage(Math.max(1, target.mEUt))
                .cachedRecipe(rival)
                .find();
            rivals.add(row("recipe", describe(rival), "selectedWhenCached", cached != null && origin(cached) == rival));
        }
        finding.put("competitors", rivals);
        findings.add(finding);
    }

    private List<GTRecipe> lookup(ItemStack[] items, FluidStack[] fluids, long voltage) {
        return map.findRecipeQuery()
            .items(items)
            .fluids(fluids)
            .voltage(Math.max(1, voltage))
            .findAll()
            .map(this::origin)
            .collect(Collectors.toList());
    }

    /**
     * Some backends return a temporary copy. Accept only a unique, semantically equal base recipe.
     * Bufferability and a dropped display category are excluded: neither changes input matching or payment.
     * Unknown subclasses or changed NBT/metadata remain unsupported rather than guessed equivalent.
     */
    private GTRecipe origin(GTRecipe result) {
        if (ids.containsKey(result)) return result;
        GTRecipe found = null;
        for (GTRecipe candidate : recipes) {
            if (!sameRecipe(candidate, result)) continue;
            if (found != null)
                throw new IllegalStateException("Runtime result has multiple equivalent registered origins");
            found = candidate;
        }
        if (found == null)
            throw new IllegalStateException("Runtime result cannot be mapped to an unchanged registered recipe");
        derivedResultMappings++;
        return found;
    }

    private static boolean sameRecipe(GTRecipe a, GTRecipe b) {
        return a.getClass() == GTRecipe.class && b.getClass() == GTRecipe.class
            && a.mEUt == b.mEUt
            && a.mDuration == b.mDuration
            && a.mSpecialValue == b.mSpecialValue
            && a.mEnabled == b.mEnabled
            && a.mFakeRecipe == b.mFakeRecipe
            && a.mHidden == b.mHidden
            && a.mNeedsEmptyOutput == b.mNeedsEmptyOutput
            && a.isNBTSensitive == b.isNBTSensitive
            && (b.getRecipeCategory() == null || a.getRecipeCategory() == b.getRecipeCategory())
            && Objects.equals(a.mSpecialItems, b.mSpecialItems)
            && sameItems(a.mInputs, b.mInputs)
            && sameItems(a.mOutputs, b.mOutputs)
            && sameFluids(a.mFluidInputs, b.mFluidInputs)
            && sameFluids(a.mFluidOutputs, b.mFluidOutputs)
            && sameAlternativeFluids(a.mAltFluidInputs, b.mAltFluidInputs)
            && Arrays.equals(a.mInputChances, b.mInputChances)
            && Arrays.equals(a.mOutputChances, b.mOutputChances)
            && Arrays.equals(a.mFluidInputChances, b.mFluidInputChances)
            && Arrays.equals(a.mFluidOutputChances, b.mFluidOutputChances)
            && Objects.equals(
                a.getMetadataStorage()
                    .getEntries(),
                b.getMetadataStorage()
                    .getEntries());
    }

    private static boolean sameItems(ItemStack[] a, ItemStack[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b[i]) continue;
            if (a[i] == null || b[i] == null
                || a[i].getItem() != b[i].getItem()
                || a[i].getItemDamage() != b[i].getItemDamage()
                || a[i].stackSize != b[i].stackSize
                || !Objects.equals(a[i].getTagCompound(), b[i].getTagCompound())) return false;
        }
        return true;
    }

    private static boolean sameFluids(FluidStack[] a, FluidStack[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b[i]) continue;
            if (a[i] == null || b[i] == null || a[i].amount != b[i].amount || !a[i].isFluidEqual(b[i])) return false;
        }
        return true;
    }

    private static boolean sameAlternativeFluids(FluidStack[][] a, FluidStack[][] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!sameFluids(a[i], b[i])) return false;
        return true;
    }

    private Map<String, Object> describe(GTRecipe recipe) {
        return row(
            "id",
            ids.get(recipe),
            "inputs",
            itemRows(recipe.mInputs),
            "fluids",
            fluidRows(recipe.mFluidInputs),
            "outputs",
            itemRows(recipe.mOutputs),
            "fluidOutputs",
            fluidRows(recipe.mFluidOutputs),
            "duration",
            recipe.mDuration,
            "eut",
            recipe.mEUt,
            "specialValue",
            recipe.mSpecialValue,
            "inputChances",
            recipe.mInputChances,
            "outputChances",
            recipe.mOutputChances,
            "fluidInputChances",
            recipe.mFluidInputChances,
            "fluidOutputChances",
            recipe.mFluidOutputChances,
            "nbtSensitive",
            recipe.isNBTSensitive,
            "owners",
            recipe.owners == null ? null
                : recipe.owners.stream()
                    .map(o -> o.getModId())
                    .collect(Collectors.toList()));
    }

    private static boolean outputs(GTRecipe recipe, ItemStack wanted) {
        return Arrays.stream(recipe.mOutputs)
            .anyMatch(s -> s != null && s.isItemEqual(wanted));
    }

    private static ItemStack[] items(GTRecipe recipe, boolean buffered) {
        ItemStack[] result = new ItemStack[recipe.mInputs.length];
        for (int i = 0; i < result.length; i++) {
            if (recipe.mInputs[i] == null) continue;
            result[i] = recipe.mInputs[i].copy();
            // A zero-count recipe selector must still be physically present. Never increase selector quantity.
            result[i].stackSize = recipe.mInputs[i].stackSize <= 0 ? 1
                : buffered ? Math.max(result[i].stackSize, result[i].getMaxStackSize()) : result[i].stackSize;
        }
        return result;
    }

    private static FluidStack[] fluids(GTRecipe recipe, boolean buffered) {
        FluidStack[] result = new FluidStack[recipe.mFluidInputs.length];
        for (int i = 0; i < result.length; i++) {
            if (recipe.mFluidInputs[i] == null) continue;
            result[i] = recipe.mFluidInputs[i].copy();
            if (buffered) result[i].amount = Math.max(result[i].amount, 64000);
        }
        return result;
    }

    private static List<Object> itemRows(ItemStack[] stacks) {
        List<Object> rows = new ArrayList<>();
        for (ItemStack s : stacks) if (s != null) rows.add(
            row(
                "item",
                String.valueOf(Item.itemRegistry.getNameForObject(s.getItem())),
                "meta",
                s.getItemDamage(),
                "count",
                s.stackSize,
                "name",
                s.getDisplayName(),
                "nbt",
                s.hasTagCompound() ? s.getTagCompound()
                    .toString() : null));
        return rows;
    }

    private static List<Object> fluidRows(FluidStack[] stacks) {
        List<Object> rows = new ArrayList<>();
        for (FluidStack s : stacks) if (s != null) rows.add(
            row(
                "fluid",
                s.getFluid()
                    .getName(),
                "amount",
                s.amount,
                "nbt",
                s.tag == null ? null : s.tag.toString()));
        return rows;
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

}
