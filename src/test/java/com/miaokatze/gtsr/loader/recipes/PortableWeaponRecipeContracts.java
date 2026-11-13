// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;

import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.Side;
import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.FindRecipeQuery;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;

/**
 * Production registration and native GT .265 lookup with isolated item identities and three source-equivalent
 * schematic recipes. No FML mod lifecycle, game, complete modpack recipe graph or third-party transformer runs.
 */
public final class PortableWeaponRecipeContracts {

    private static int checks;
    private static int nextItem = 29000;

    private PortableWeaponRecipeContracts() {}

    public static void main(String[] args) throws Exception {
        // Reuse the established registry/side bootstrap; this creates no client, server or world.
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, PortableWeaponRecipeContracts.class.getName(), args)) return;
        bootstrapGt();
        prepareIngredients();

        GTSRItemList[] outputs = { GTSRItemList.PortableLM12, GTSRItemList.PortableT20, GTSRItemList.PortableQLZ04,
            GTSRItemList.PortableSingularity, GTSRItemList.Ammo762Pack, GTSRItemList.Ammo20Pack,
            GTSRItemList.Ammo35Pack, GTSRItemList.MimicAmmo762Pack, GTSRItemList.MimicAmmo20Pack,
            GTSRItemList.MimicAmmo35Pack };
        for (GTSRItemList output : outputs) output.set(item("gtsr_contract:" + output.name()));
        GTSRItemList.SteamEntangledSingularity.set(item("gtsr_contract:singularity"));

        check(
            RecipeMaps.assemblerRecipes.getAllRecipes()
                .isEmpty(),
            "fixture starts with an empty assembler map");
        // GT .265 AssemblerRecipes.java:974-1002. These are fixtures, not an invocation of the upstream loader.
        GTRecipe[] schematics = { schematic(Materials.Steel, Materials.LV, 4, 4, 600, 15),
            schematic(Materials.Aluminium, Materials.MV, 3, 2, 300, 30),
            schematic(Materials.StainlessSteel, Materials.HV, 2, 1, 150, 48) };
        PortableWeaponRecipes.register();
        PortableWeaponRecipes.auditRegisteredRecipes();
        check(
            RecipeMaps.assemblerRecipes.getAllRecipes()
                .size() == 13,
            "ten production recipes plus three schematics");

        List<GTRecipe> portable = new ArrayList<>();
        int[] ticks = { 600, 800, 1000, 1200, 200, 300, 400, 1200, 1200, 1200 };
        int[] eut = { 30, 120, 480, 1920, 30, 120, 480, 30720, 30720, 30720 };
        for (int i = 0; i < outputs.length; i++) {
            final GTSRItemList expected = outputs[i];
            List<GTRecipe> found = new ArrayList<>();
            for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes()) {
                if (recipe.mOutputs.length == 1 && expected.isStackEqual(recipe.mOutputs[0])) found.add(recipe);
            }
            check(found.size() == 1, "one production recipe for " + expected);
            GTRecipe recipe = found.get(0);
            check(
                recipe.mDuration == ticks[i] && recipe.mEUt == eut[i],
                "unchanged duration and voltage for " + expected);
            check(recipe.mInputs.length == 6, "five consumable inputs and one selector for " + expected);
            int selectors = 0;
            for (ItemStack input : recipe.mInputs) {
                if (input.getItem() == ItemList.Circuit_Integrated.getItem()) {
                    selectors++;
                    check(
                        input.getItemDamage() == 24 && input.stackSize == 0,
                        "shared non-consumed selector 24 for " + expected);
                }
            }
            check(selectors == 1, "exactly one selector for " + expected);
            portable.add(recipe);
        }

        GTRecipe qlz = portable.get(2);
        ItemStack[] oldQlz = inputs(qlz, 3);
        check(
            schematics[2].isRecipeInputEqual(false, new FluidStack[0], oldQlz),
            "positive control: old QLZ inputs satisfy the native schematic matching rule");
        check(
            query(oldQlz, new FluidStack[0], schematics[2]).find() == schematics[2],
            "positive control: cached schematic captures old selector 3 QLZ inputs");

        for (GTRecipe recipe : portable) {
            RecipeMaps.assemblerRecipes.getBackend()
                .reInit();
            unique(recipe, null, "cold lookup");
            // Every previously selected family member must be rejected or select this exact recipe again.
            for (GTRecipe cached : portable) unique(recipe, cached, "cross-family machine cache");
            for (GTRecipe schematic : schematics) unique(recipe, schematic, "cached schematic");
            unique(recipe, null, "warm shared cache");
            ItemStack[] wrongSelector = inputs(recipe, 3);
            List<GTRecipe> wrongMatches = query(wrongSelector, copyFluids(recipe), recipe).findAll()
                .collect(java.util.stream.Collectors.toList());
            check(!wrongMatches.contains(recipe), "old selector cannot select a portable recipe");
        }
        for (GTRecipe schematic : schematics) {
            RecipeMaps.assemblerRecipes.getBackend()
                .reInit();
            unique(schematic, null, "original schematic selector 3");
            for (GTRecipe cached : portable) unique(schematic, cached, "schematic after portable machine cache");
        }

        // The production receipt audit must continue to accept an intentional downstream recipe edit.
        int originalDuration = qlz.mDuration;
        qlz.mDuration = originalDuration + 17;
        PortableWeaponRecipes.auditRegisteredRecipes();
        check(qlz.mDuration == originalDuration + 17, "receipt audit neither rejects nor restores downstream edits");
        qlz.mDuration = originalDuration;
        System.out.println(
            "PASS portable recipe lookup: checks=" + checks
                + " production=10 schematic_fixtures=3 selector=24 game_started=false full_modpack_graph=false");
    }

    private static void unique(GTRecipe expected, GTRecipe cached, String scenario) {
        ItemStack[] items = inputs(expected, -1);
        FluidStack[] fluids = copyFluids(expected);
        List<GTRecipe> found = query(items, fluids, cached).findAll()
            .collect(java.util.stream.Collectors.toList());
        check(found.size() == 1 && found.get(0) == expected, scenario + ": exactly the intended native match");
        check(
            query(items, fluids, cached).voltage(expected.mEUt)
                .find() == expected,
            scenario + ": intended voltage");
        check(
            query(items, fluids, cached).caching(true)
                .find() == expected,
            scenario + ": first selected output");
    }

    private static FindRecipeQuery query(ItemStack[] items, FluidStack[] fluids, GTRecipe cached) {
        return RecipeMaps.assemblerRecipes.findRecipeQuery()
            .items(items)
            .fluids(fluids)
            .voltage(Integer.MAX_VALUE)
            .cachedRecipe(cached);
    }

    private static ItemStack[] inputs(GTRecipe recipe, int configuration) {
        ItemStack[] items = new ItemStack[recipe.mInputs.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = recipe.mInputs[i].copy();
            if (items[i].getItem() == ItemList.Circuit_Integrated.getItem()) {
                items[i].stackSize = 1; // An actual machine contains one physical, non-consumed circuit.
                if (configuration >= 0) items[i].setItemDamage(configuration);
            }
        }
        return items;
    }

    private static FluidStack[] copyFluids(GTRecipe recipe) {
        FluidStack[] fluids = new FluidStack[recipe.mFluidInputs.length];
        for (int i = 0; i < fluids.length; i++) fluids[i] = recipe.mFluidInputs[i].copy();
        return fluids;
    }

    private static GTRecipe schematic(Materials plate, Materials circuit, int plates, int circuits, int ticks,
        int eut) {
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.plate, plate, plates),
                GTOreDictUnificator.get(OrePrefixes.circuit, circuit, circuits))
            .circuit(3)
            .itemOutputs(ItemList.Schematic.get(1))
            .duration(ticks)
            .eut(eut)
            .build()
            .get();
        return RecipeMaps.assemblerRecipes.getBackend()
            .compileRecipe(recipe);
    }

    private static void prepareIngredients() throws Exception {
        for (Materials material : Arrays
            .asList(Materials.Steel, Materials.Aluminium, Materials.StainlessSteel, Materials.Titanium, Materials.HSSS))
            ore(OrePrefixes.plate, material);
        for (Materials material : Arrays.asList(Materials.Steel, Materials.StainlessSteel, Materials.Titanium))
            ore(OrePrefixes.stickLong, material);
        for (Materials material : Arrays.asList(Materials.LV, Materials.MV, Materials.HV, Materials.EV, Materials.LuV))
            ore(OrePrefixes.circuit, material);
        ore(OrePrefixes.dust, Materials.Lead);
        ore(OrePrefixes.dust, Materials.Gunpowder);
        ore(OrePrefixes.foil, Materials.Copper);
        for (ItemList item : Arrays.asList(
            ItemList.Circuit_Integrated,
            ItemList.Schematic,
            ItemList.Paper_Printed_Pages,
            ItemList.Electric_Motor_LV,
            ItemList.Electric_Motor_MV,
            ItemList.Electric_Motor_HV,
            ItemList.Electric_Piston_LV,
            ItemList.Electric_Piston_MV,
            ItemList.Electric_Piston_HV,
            ItemList.Field_Generator_EV,
            ItemList.Sensor_LuV)) item.set(item("gtsr_contract:" + item.name()));
        item("TConstruct:materials"); // Only the moss item's identity/meta is supplied; no TConstruct lifecycle runs.
        Fluid abyss = new Fluid("gtsr_portable_contract_abyss");
        check(FluidRegistry.registerFluid(abyss), "isolated abyss fluid registration");
        GTSRProsperityAirMaterials.AbyssalObsession = Materials.Water;
        Materials.Water.mFluid = abyss;
    }

    private static void ore(OrePrefixes prefix, Materials material) throws Exception {
        GTOreDictUnificator
            .set(prefix, material, new ItemStack(item("gtsr_contract:" + prefix.name() + material.mName)));
    }

    private static Item item(String name) throws Exception {
        Item item = new Item().setHasSubtypes(true);
        Field controllerField = cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(cpw.mods.fml.common.Loader.instance());
        Field active = cpw.mods.fml.common.LoadController.class.getDeclaredField("activeContainer");
        active.setAccessible(true);
        Object previous = active.get(controller);
        cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
        metadata.modId = name.substring(0, name.indexOf(':'));
        metadata.name = "Isolated recipe ingredient identities";
        active.set(controller, new cpw.mods.fml.common.DummyModContainer(metadata));
        try {
            GameData.getItemRegistry()
                .addObject(nextItem++, name, item);
        } finally {
            active.set(controller, previous);
        }
        check(name.equals(item.delegate.name()), "fixture item has its registered ore-dictionary identity: " + name);
        return item;
    }

    private static void bootstrapGt() throws Exception {
        // The published dev jar inlines/removes GT_Version; its Mod annotation retains the actual artifact version.
        String version = GTMod.class.getAnnotation(cpw.mods.fml.common.Mod.class)
            .version();
        check(version.contains("5.09.54.265"), "native GT dependency is .265: " + version);
        System.out.println(
            "SOURCE GT " + GTRecipe.class.getProtectionDomain()
                .getCodeSource()
                .getLocation());
        System.out.println(
            "SOURCE production " + PortableWeaponRecipes.class.getProtectionDomain()
                .getCodeSource()
                .getLocation());
        set(cpw.mods.fml.relauncher.FMLRelaunchLog.class, null, "side", Side.SERVER);
        cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
        metadata.modId = "gregtech";
        metadata.version = version;
        set(
            cpw.mods.fml.common.Loader.class,
            cpw.mods.fml.common.Loader.instance(),
            "namedMods",
            Collections.singletonMap("gregtech", new cpw.mods.fml.common.DummyModContainer(metadata)));
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object allocator = field.get(null);
        Method allocate = unsafe.getMethod("allocateInstance", Class.class);
        GTMod.GT = (GTMod) allocate.invoke(allocator, GTMod.class);
        GTMod.proxy = (gregtech.common.GTProxy) allocate.invoke(allocator, gregtech.common.GTProxy.class);
        for (String name : Arrays.asList(
            "steam",
            "ic2superheatedsteam",
            "supercriticalsteam",
            "densesteam",
            "densesuperheatedsteam",
            "densesupercriticalsteam")) {
            if (FluidRegistry.getFluid(name) == null) FluidRegistry.registerFluid(new Fluid(name));
        }
        GregTechAPI.sPreloadStarted = true;
        // Supply only the recipe-builder entry point; builder, registration, matching and cache are upstream code.
        GTValues.RA = (gregtech.api.interfaces.internal.IGTRecipeAdder) Proxy.newProxyInstance(
            PortableWeaponRecipeContracts.class.getClassLoader(),
            new Class<?>[] { gregtech.api.interfaces.internal.IGTRecipeAdder.class },
            (proxy, method, values) -> {
                if (method.getName()
                    .equals("stdBuilder")) return GTRecipeBuilder.builder();
                throw new AssertionError("Unexpected recipe-adder call: " + method);
            });
    }

    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
