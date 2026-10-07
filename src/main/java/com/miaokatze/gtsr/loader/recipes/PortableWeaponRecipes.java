package com.miaokatze.gtsr.loader.recipes;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Portable weapons and full ammunition packs; missing required ingredients fail explicitly. */
public final class PortableWeaponRecipes {

    private static final List<Spec> EXPECTED = new ArrayList<>();

    private PortableWeaponRecipes() {}

    public static void register() {
        if (!EXPECTED.isEmpty()) throw failure("Duplicate registration attempt");
        add(
            "LM12",
            GTSRItemList.PortableLM12,
            TierEU.RECIPE_LV,
            600,
            null,
            ore(OrePrefixes.plate, Materials.Steel, 6),
            ore(OrePrefixes.stickLong, Materials.Steel, 6),
            ItemList.Electric_Motor_LV.get(2),
            ItemList.Electric_Piston_LV.get(1),
            ore(OrePrefixes.circuit, Materials.LV, 2),
            GTUtility.getIntegratedCircuit(1));
        add(
            "T20",
            GTSRItemList.PortableT20,
            TierEU.RECIPE_MV,
            800,
            null,
            ore(OrePrefixes.plate, Materials.Aluminium, 8),
            ore(OrePrefixes.stickLong, Materials.Steel, 4),
            ItemList.Electric_Motor_MV.get(2),
            ItemList.Electric_Piston_MV.get(1),
            ore(OrePrefixes.circuit, Materials.MV, 2),
            GTUtility.getIntegratedCircuit(2));
        add(
            "QLZ04",
            GTSRItemList.PortableQLZ04,
            TierEU.RECIPE_HV,
            1000,
            null,
            ore(OrePrefixes.plate, Materials.StainlessSteel, 8),
            ore(OrePrefixes.stickLong, Materials.StainlessSteel, 2),
            ItemList.Electric_Motor_HV.get(2),
            ItemList.Electric_Piston_HV.get(1),
            ore(OrePrefixes.circuit, Materials.HV, 2),
            GTUtility.getIntegratedCircuit(3));
        add(
            "107",
            GTSRItemList.PortableSingularity,
            TierEU.RECIPE_EV,
            1200,
            null,
            ore(OrePrefixes.plate, Materials.Titanium, 8),
            ore(OrePrefixes.stickLong, Materials.Titanium, 2),
            ItemList.Field_Generator_EV.get(1),
            ore(OrePrefixes.circuit, Materials.EV, 2),
            GTSRItemList.SteamEntangledSingularity.get(1),
            GTUtility.getIntegratedCircuit(4));

        ammo("7.62mm/500", GTSRItemList.Ammo762Pack, Materials.Steel, Materials.LV, 16, 11, TierEU.RECIPE_LV, 200);
        ammo("20mm/160", GTSRItemList.Ammo20Pack, Materials.Aluminium, Materials.MV, 24, 12, TierEU.RECIPE_MV, 300);
        ammo("35mm/60", GTSRItemList.Ammo35Pack, Materials.StainlessSteel, Materials.HV, 32, 13, TierEU.RECIPE_HV, 400);

        // Native TConstruct materials metadata 6 is the moss ball, not an ore-dictionary substitute.
        ItemStack moss = GTModHandler.getModItem("TConstruct", "materials", 1, 6);
        require(moss, "TConstruct:materials:6 moss ball");
        if (GTSRProsperityAirMaterials.AbyssalObsession == null)
            throw failure("Missing required AbyssalObsession material");
        FluidStack abyss = GTSRProsperityAirMaterials.AbyssalObsession.getFluid(1000);
        if (abyss == null || abyss.getFluid() == null) throw failure("Missing required AbyssalObsession fluid");
        mimic("Mimic7.62mm", GTSRItemList.Ammo762Pack, GTSRItemList.MimicAmmo762Pack, moss, abyss, 21);
        mimic("Mimic20mm", GTSRItemList.Ammo20Pack, GTSRItemList.MimicAmmo20Pack, moss, abyss, 22);
        mimic("Mimic35mm", GTSRItemList.Ammo35Pack, GTSRItemList.MimicAmmo35Pack, moss, abyss, 23);
        auditRegisteredRecipes();
        GTSteamReborn.LOG.info("[GTSR-PortableRecipes] Verified all 10 live assembler recipes");
    }

    private static void ammo(String id, GTSRItemList output, Materials casing, Materials circuit, int charge,
        int configuration, long eut, int duration) {
        add(
            id,
            output,
            eut,
            duration,
            null,
            ore(OrePrefixes.plate, casing, 2),
            ore(OrePrefixes.dust, Materials.Lead, charge),
            ore(OrePrefixes.dust, Materials.Gunpowder, charge),
            ore(OrePrefixes.foil, Materials.Copper, charge),
            ore(OrePrefixes.circuit, circuit, 1),
            GTUtility.getIntegratedCircuit(configuration));
    }

    private static void mimic(String id, GTSRItemList ordinary, GTSRItemList output, ItemStack moss, FluidStack abyss,
        int configuration) {
        add(
            id,
            output,
            TierEU.RECIPE_LuV,
            1200,
            abyss.copy(),
            full(ordinary),
            moss.copy(),
            ore(OrePrefixes.plate, Materials.HSSS, 2),
            ItemList.Sensor_LuV.get(1),
            ore(OrePrefixes.circuit, Materials.LuV, 1),
            GTUtility.getIntegratedCircuit(configuration));
    }

    private static ItemStack ore(OrePrefixes prefix, Materials material, int amount) {
        ItemStack result = GTOreDictUnificator.get(prefix, material, amount);
        require(result, prefix.name() + material.mName);
        return result;
    }

    private static ItemStack full(GTSRItemList item) {
        ItemStack stack = item.get(1);
        require(stack, item.name());
        stack.setItemDamage(0);
        return stack;
    }

    private static void add(String id, GTSRItemList output, long eut, int duration, FluidStack fluid,
        ItemStack... inputs) {
        if (inputs.length > 6) throw failure(id + " exceeds six assembler item inputs");
        for (int i = 0; i < inputs.length; i++) require(inputs[i], id + " input " + i);
        ItemStack result = full(output);
        Collection<GTRecipe> added = GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .itemOutputs(result)
            .fluidInputs(fluid == null ? new FluidStack[0] : new FluidStack[] { fluid })
            .duration(duration)
            .eut(eut)
            .addTo(assemblerRecipes);
        if (added.isEmpty()) throw failure(id + " was rejected by assembler recipe map");
        EXPECTED.add(new Spec(id, result.copy(), inputs, fluid, duration, (int) eut));
    }

    /** Audits the actual map, including uniqueness, complete ammunition, inputs, fluid, time and voltage. */
    public static void auditRegisteredRecipes() {
        if (EXPECTED.size() != 10) throw failure("Expected 10 specifications, found " + EXPECTED.size());
        for (Spec spec : EXPECTED) {
            int matches = 0;
            for (GTRecipe recipe : assemblerRecipes.getAllRecipes()) {
                if (recipe.mOutputs.length != 1 || !same(recipe.mOutputs[0], spec.output)) continue;
                matches++;
                if (recipe.mDuration != spec.duration || recipe.mEUt != spec.eut
                    || !sameInputs(recipe.mInputs, spec.inputs)
                    || !sameFluid(recipe.mFluidInputs, spec.fluid))
                    throw failure(spec.id + " live recipe differs from its required specification");
            }
            if (matches != 1) throw failure(spec.id + " expected one live recipe, found " + matches);
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage()
            && a.stackSize == b.stackSize
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static boolean sameInputs(ItemStack[] actual, ItemStack[] expected) {
        if (actual.length != expected.length) return false;
        boolean[] used = new boolean[actual.length];
        for (ItemStack item : expected) {
            boolean found = false;
            for (int i = 0; i < actual.length; i++) {
                if (!used[i] && same(actual[i], item)) {
                    used[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean sameFluid(FluidStack[] actual, FluidStack expected) {
        if (expected == null) return actual == null || actual.length == 0;
        return actual != null && actual.length == 1
            && actual[0] != null
            && actual[0].isFluidEqual(expected)
            && actual[0].amount == expected.amount;
    }

    private static void require(ItemStack item, String name) {
        // GTUtility.getIntegratedCircuit deliberately returns a zero-size, non-consumed selector.
        if (item == null || item.getItem() == null
            || item.stackSize < 0
            || (item.stackSize == 0 && item.getItem() != ItemList.Circuit_Integrated.getItem()))
            throw failure("Missing required item " + name);
    }

    private static IllegalStateException failure(String message) {
        GTSteamReborn.LOG.error("[GTSR-PortableRecipes] " + message);
        return new IllegalStateException("[GTSR-PortableRecipes] " + message);
    }

    private static final class Spec {

        private final String id;
        private final ItemStack output;
        private final ItemStack[] inputs;
        private final FluidStack fluid;
        private final int duration, eut;

        private Spec(String id, ItemStack output, ItemStack[] inputs, FluidStack fluid, int duration, int eut) {
            this.id = id;
            this.output = output;
            this.inputs = new ItemStack[inputs.length];
            for (int i = 0; i < inputs.length; i++) this.inputs[i] = inputs[i].copy();
            this.fluid = fluid == null ? null : fluid.copy();
            this.duration = duration;
            this.eut = eut;
        }
    }
}
