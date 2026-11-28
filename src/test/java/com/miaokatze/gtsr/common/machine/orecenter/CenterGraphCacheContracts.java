package com.miaokatze.gtsr.common.machine.orecenter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts;

import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;

/** Offline native registration, admission and semantic preview invalidation; no world or instance. */
public final class CenterGraphCacheContracts {

    private static int checks;
    private static Method fixtureItem;

    private CenterGraphCacheContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, CenterGraphCacheContracts.class.getName(), args)) return;
        Method setup = PortableWeaponRecipeContracts.class.getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        fixtureItem = PortableWeaponRecipeContracts.class.getDeclaredMethod("item", String.class);
        fixtureItem.setAccessible(true);
        fallbackExclusions();
        outletInvalidation();
        previewEnvironment();
        conditionalPreview();
        System.out.println("PASS center graph/cache contracts: " + checks + " checks; game_started=false");
    }

    private static ItemStack item(String key, int quantity) throws Exception {
        return new ItemStack((Item) fixtureItem.invoke(null, "gtsr_graph_cache:" + key), quantity);
    }

    private static void fallbackExclusions() throws Exception {
        ItemStack excluded = item("excluded", 12), accepted = item("accepted", 12), output = item("output", 1);
        OreDictionary.registerOre("rawOreGraphCacheExcluded", excluded);
        OreDictionary.registerOre("rawOreGraphCacheAccepted", accepted);
        for (ItemStack input : Arrays.asList(excluded, accepted)) GTValues.RA.stdBuilder()
            .itemInputs(new ItemStack(input.getItem(), 1))
            .itemOutputs(output.copy())
            .duration(40)
            .eut(2)
            .addTo(RecipeMaps.maceratorRecipes);
        CenterGraph graph = new CenterGraph(
            1,
            "fallback",
            0,
            true,
            Collections.<String>emptyList(),
            Collections.singletonList(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { CenterGraph.BUS })),
            Collections.<CenterGraph.EntryRoute>emptyList(),
            Collections.singletonList("rawOreGraphCacheExcluded"));
        graph = CenterGraph.load(graph.save());
        check(graph.fallback(), "cleared rules persist as fallback");
        check(!graph.permitsInputExclusions(excluded, CenterGraph.tags(excluded)), "saved exclusions still reject");
        int[] grades = new int[CenterModule.values().length];
        grades[CenterModule.CRUSHER.ordinal()] = grades[CenterModule.LOGISTICS.ordinal()] = 1;
        CenterPlanner.Batch batch = CenterPlanner.plan(
            Collections.singletonList(graph),
            new ItemStack[] { excluded, accepted },
            new FluidStack[0],
            grades,
            0,
            1,
            0,
            0,
            0,
            41);
        check(batch != null && batch.consumed.size() == 1, "only eligible raw material enters native plan");
        check(
            batch.consumed.get(0)
                .getItem() == accepted.getItem(),
            "fallback cannot consume excluded raw material");
        check(excluded.stackSize == 12 && accepted.stackSize == 12, "read-only plan leaves actual stacks untouched");
    }

    private static void outletInvalidation() throws Exception {
        ItemStack stack = item("changing_tags", 1);
        CenterGraph.OutletFilter filter = new CenterGraph.OutletFilter(
            0,
            true,
            Collections.singletonList("rawOreGraphCacheLate"));
        check(!filter.accepts(stack), "unregistered ore tag rejected on cold lookup");
        check(!filter.accepts(stack), "negative outlet result reused");
        OreDictionary.registerOre("rawOreGraphCacheLate", stack);
        check(filter.accepts(stack), "late ore registration invalidates negative outlet decision");
        stack.setTagCompound(new NBTTagCompound());
        for (int n = 0; n < 600; n++) {
            stack.getTagCompound()
                .setInteger("Variant", n);
            check(filter.accepts(stack), "tag-aware cache preserves acceptance " + n);
        }
        Field memo = CenterGraph.OutletFilter.class.getDeclaredField("decisions");
        memo.setAccessible(true);
        check(((Map<?, ?>) memo.get(filter)).size() <= 512, "outlet cache remains bounded with mutable source NBT");
    }

    private static void previewEnvironment() {
        NBTTagCompound before = new NBTTagCompound();
        before.setInteger("Tier", 2);
        before.setLong("Voltage", 512);
        before.setLong("Power", 2048);
        before.setIntArray("Grades", new int[] { 1, 2 });
        before.setTag("Fluids", new NBTTagList());
        NBTTagCompound progress = (NBTTagCompound) before.copy();
        progress.setInteger("Elapsed", 99);
        progress.setLong("Energy", 1234);
        check(CenterGraphPreview.sameEnvironment(before, progress), "progress traffic does not restart preview");
        for (String key : Arrays
            .asList("Tier", "Voltage", "Power", "Formed", "Sectors", "Modules", "Grades", "Fluids")) {
            NBTTagCompound changed = (NBTTagCompound) before.copy();
            if (key.equals("Modules") || key.equals("Grades")) changed.setIntArray(key, new int[] { 3 });
            else if (key.equals("Fluids")) {
                NBTTagList tanks = new NBTTagList();
                NBTTagCompound fluid = new NBTTagCompound();
                fluid.setString("FluidName", "water");
                fluid.setInteger("Amount", 1000);
                tanks.appendTag(fluid);
                changed.setTag(key, tanks);
            } else changed.setInteger(key, 7);
            check(!CenterGraphPreview.sameEnvironment(before, changed), "preview invalidates on " + key);
        }
    }

    private static void conditionalPreview() throws Exception {
        ItemStack input = item("wash_input", 1), output = item("wash_output", 1);
        Fluid water = new Fluid("gtsr_graph_cache_wash");
        check(FluidRegistry.registerFluid(water), "fixture registers actual washing fluid");
        GTValues.RA.stdBuilder()
            .itemInputs(input.copy())
            .itemOutputs(output.copy())
            .fluidInputs(new FluidStack(water, 1000))
            .duration(40)
            .eut(4)
            .addTo(RecipeMaps.oreWasherRecipes);
        CenterGraph graph = new CenterGraph(
            0,
            true,
            Collections.<String>emptyList(),
            Collections.singletonList(new CenterGraph.Node(CenterProcess.WASHER, new int[] { CenterGraph.BUS })));
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Tier", 0);
        state.setLong("Voltage", 32);
        CenterGraphPreview preview = new CenterGraphPreview(graph, input, state);
        for (int n = 0; n < 200 && preview.products(0)
            .isEmpty(); n++) preview.advance();
        check(
            !preview.products(0)
                .isEmpty() && preview.conditionalRecipe(0),
            "bounded native category collection eventually shows a conditional wash recipe");
        NBTTagList tanks = new NBTTagList();
        tanks.appendTag(new FluidStack(water, 1000).writeToNBT(new NBTTagCompound()));
        state.setTag("Fluids", tanks);
        CenterGraphPreview supplied = new CenterGraphPreview(graph, input, state);
        supplied.advance();
        check(
            !supplied.products(0)
                .isEmpty() && !supplied.conditionalRecipe(0),
            "new supply snapshot resolves the real recipe without stale conditional feedback");
        ItemStack noise = item("wash_noise", 1);
        GTValues.RA.stdBuilder()
            .itemInputs(noise.copy())
            .itemOutputs(output.copy())
            .fluidInputs(new FluidStack(water, 1000))
            .duration(40)
            .eut(4)
            .addTo(RecipeMaps.oreWasherRecipes);
        GTRecipe noiseRecipe = null;
        for (GTRecipe recipe : RecipeMaps.oreWasherRecipes.getAllRecipes()) {
            if (recipe.mInputs.length > 0 && recipe.mInputs[0].getItem() == noise.getItem()) noiseRecipe = recipe;
        }
        check(noiseRecipe != null, "native registered recipe supplies long-table fixture");
        for (int n = 0; n < 700; n++) RecipeMaps.oreWasherRecipes.getBackend()
            .compileRecipe(noiseRecipe);
        CenterGraphPreview.Resources resources = new CenterGraphPreview.Resources();
        Fluid unrelated = new Fluid("gtsr_graph_cache_changing");
        check(FluidRegistry.registerFluid(unrelated), "register unrelated changing supply");
        boolean visible = false;
        for (int snapshot = 0; snapshot < 64 && !visible; snapshot++) {
            NBTTagList changed = new NBTTagList();
            changed.appendTag(new FluidStack(unrelated, 100 + snapshot).writeToNBT(new NBTTagCompound()));
            state.setTag("Fluids", changed);
            CenterGraphPreview changing = new CenterGraphPreview(graph, input, state, resources);
            for (int tick = 0; tick < 10; tick++) changing.advance();
            visible = !changing.products(0)
                .isEmpty();
        }
        check(visible, "supply-independent cold collection survives repeated ten-tick environment refreshes");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
