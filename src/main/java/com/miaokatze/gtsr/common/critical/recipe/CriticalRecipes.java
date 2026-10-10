package com.miaokatze.gtsr.common.critical.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/** Server recipes, resolved once after material/item registration, never during every work tick. */
public final class CriticalRecipes {

    private static List<Spec> recipes;

    private CriticalRecipes() {}

    private static synchronized List<Spec> recipes() {
        if (recipes != null) return recipes;
        List<Spec> r = new ArrayList<>();
        add(
            r,
            "solar_water",
            CriticalMachineKind.SOLAR,
            none(),
            fluids(Materials.Water.getFluid(1000)),
            none(),
            fluids(Materials.Steam.getGas(160000)),
            400,
            0,
            0);
        add(
            r,
            "solar_sanzu",
            CriticalMachineKind.SOLAR,
            none(),
            fluids(fluid("sanzu_residual_steam", 1000)),
            none(),
            fluids(Materials.Steam.getGas(192000)),
            400,
            0,
            0);
        add(
            r,
            "turbine_steam",
            CriticalMachineKind.TURBINE,
            none(),
            fluids(Materials.Steam.getGas(64000)),
            none(),
            fluids(Materials.Water.getFluid(400)),
            400,
            0,
            32000);
        add(
            r,
            "processing_press",
            CriticalMachineKind.PROCESSING,
            items(ore(OrePrefixes.ingot, Materials.Steel, 16)),
            fluids(fluid("thickgrease", 100)),
            items(ore(OrePrefixes.plate, Materials.Steel, 16)),
            noFluids(),
            400,
            8192,
            0);
        add(
            r,
            "processing_grit",
            CriticalMachineKind.PROCESSING,
            none(),
            fluids(fluid("metalgrit", 10000)),
            items(ore(OrePrefixes.dust, Materials.Iron, 4), ore(OrePrefixes.dust, Materials.Copper, 2)),
            noFluids(),
            400,
            8192,
            0);
        add(
            r,
            "processing_wire",
            CriticalMachineKind.PROCESSING,
            items(ore(OrePrefixes.ingot, Materials.Copper, 16)),
            noFluids(),
            items(ore(OrePrefixes.wireGt01, Materials.Copper, 32)),
            noFluids(),
            400,
            8192,
            0);
        add(
            r,
            "entangler_critical",
            CriticalMachineKind.ENTANGLER,
            items(GTSRItemList.SteamEntangledSingularity.get(64)),
            fluids(fluid("abyssal_obsession", 10000)),
            items(GTSRItemList.CriticalSteamEntangledSingularity.get(1)),
            noFluids(),
            400,
            524288,
            0);
        add(
            r,
            "sun_fusion",
            CriticalMachineKind.SUN,
            none(),
            fluids(Materials.Deuterium.getGas(1000), Materials.Tritium.getGas(1000)),
            none(),
            fluids(Materials.Helium.getGas(1000)),
            400,
            0,
            209715200L);
        add(
            r,
            "dimension_waste",
            CriticalMachineKind.DIMENSION,
            none(),
            fluids(fluid("wastesigh", 10000)),
            items(ore(OrePrefixes.dust, Materials.Iron, 4)),
            fluids(Materials.Nitrogen.getGas(1000)),
            400,
            32768,
            0);
        add(
            r,
            "dimension_mire",
            CriticalMachineKind.DIMENSION,
            none(),
            fluids(fluid("umbralmire", 10000)),
            items(ore(OrePrefixes.dust, Materials.Carbon, 4)),
            fluids(Materials.Methane.getGas(1000)),
            400,
            32768,
            0);
        add(
            r,
            "dimension_withered",
            CriticalMachineKind.DIMENSION,
            none(),
            fluids(fluid("withered_breath", 10000)),
            items(ore(OrePrefixes.dust, Materials.Sulfur, 4)),
            fluids(Materials.Nitrogen.getGas(1000)),
            400,
            32768,
            0);
        add(
            r,
            "battery_chemical",
            CriticalMachineKind.BATTERY,
            items(ore(OrePrefixes.dust, Materials.Zinc, 1)),
            fluids(Materials.SulfuricAcid.getFluid(1000)),
            none(),
            fluids(Materials.Hydrogen.getGas(1000)),
            400,
            0,
            1048576);
        add(
            r,
            "assembly_anchor",
            CriticalMachineKind.ASSEMBLY,
            items(
                ItemList.Casing_HV.get(1),
                ItemList.Field_Generator_HV.get(1),
                ItemList.Sensor_HV.get(1),
                ItemList.Emitter_HV.get(1),
                ItemList.Circuit_Nanoprocessor.get(4),
                ore(OrePrefixes.plate, Materials.StainlessSteel, 8),
                GTSRItemList.SteamEntangledSingularity.get(1)),
            fluids(Materials.SolderingAlloy.getMolten(1152)),
            items(GTSRItemList.SpacetimeAnchorBeacon.get(1)),
            noFluids(),
            400,
            32768,
            0);
        add(
            r,
            "accelerator_uranium",
            CriticalMachineKind.ACCELERATOR,
            items(ItemList.RodUranium.get(1)),
            noFluids(),
            items(ItemList.DepletedRodUranium.get(1)),
            noFluids(),
            400,
            0,
            16000000);
        add(
            r,
            "accelerator_mox",
            CriticalMachineKind.ACCELERATOR,
            items(ItemList.RodMOX.get(1)),
            noFluids(),
            items(ItemList.DepletedRodMOX.get(1)),
            noFluids(),
            400,
            0,
            24000000);
        recipes = r;
        return r;
    }

    public static RecipeMatch match(CriticalConfiguration cfg, ItemStack[] inputs, FluidStack[] fluids) {
        for (Spec s : recipes()) {
            if (s.kind != cfg.kind || s.eu > cfg.tier.recipeVoltage()) continue;
            for (int n = 1; n <= cfg.parallelMultiplier(); n++) {
                if (!exactItems(inputs, s.in, n) || !exactFluids(fluids, s.fin, n)) continue;
                long eu = s.eu == 0 ? 0 : cfg.energy(Math.multiplyExact(s.eu, n));
                return new RecipeMatch(
                    s.id,
                    scale(s.in, n),
                    scale(s.fin, n),
                    scale(s.out, n),
                    scale(s.fout, n),
                    cfg.duration(s.duration),
                    eu,
                    Math.multiplyExact(s.generated, n));
            }
        }
        return null;
    }

    /** Reference batches for the terminal, built from the same resolved definitions as server matching. */
    public static List<RecipeMatch> list(CriticalConfiguration cfg) {
        List<RecipeMatch> result = new ArrayList<>();
        for (Spec spec : recipes()) {
            if (spec.kind == cfg.kind && spec.eu <= cfg.tier.recipeVoltage()) {
                result.add(
                    new RecipeMatch(
                        spec.id,
                        scale(spec.in, 1),
                        scale(spec.fin, 1),
                        scale(spec.out, 1),
                        scale(spec.fout, 1),
                        cfg.duration(spec.duration),
                        spec.eu == 0 ? 0 : cfg.energy(spec.eu),
                        spec.generated));
            }
        }
        return result;
    }

    /** Exact multiset matching includes NBT and combines split stacks; unrelated/excess material rejects the batch. */
    public static boolean exactItems(ItemStack[] actual, ItemStack[] required, int multiplier) {
        long[] remaining = new long[required.length];
        for (int i = 0; i < required.length; i++) remaining[i] = (long) required[i].stackSize * multiplier;
        for (ItemStack a : actual) {
            if (a == null || a.stackSize <= 0) continue;
            long count = a.stackSize;
            for (int i = 0; i < required.length && count > 0; i++) {
                if (!a.isItemEqual(required[i]) || !ItemStack.areItemStackTagsEqual(a, required[i])) continue;
                long used = Math.min(count, remaining[i]);
                remaining[i] -= used;
                count -= used;
            }
            if (count != 0) return false;
        }
        for (long count : remaining) if (count != 0) return false;
        return true;
    }

    public static boolean exactFluids(FluidStack[] actual, FluidStack[] required, int multiplier) {
        long[] remaining = new long[required.length];
        for (int i = 0; i < required.length; i++) remaining[i] = (long) required[i].amount * multiplier;
        for (FluidStack a : actual) {
            if (a == null || a.amount <= 0) continue;
            long count = a.amount;
            for (int i = 0; i < required.length && count > 0; i++) {
                if (!a.isFluidEqual(required[i])) continue;
                long used = Math.min(count, remaining[i]);
                remaining[i] -= used;
                count -= used;
            }
            if (count != 0) return false;
        }
        for (long count : remaining) if (count != 0) return false;
        return true;
    }

    private static ItemStack[] scale(ItemStack[] source, int n) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].copy();
            result[i].stackSize = Math.multiplyExact(source[i].stackSize, n);
        }
        return result;
    }

    private static FluidStack[] scale(FluidStack[] source, int n) {
        FluidStack[] result = new FluidStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].copy();
            result[i].amount = Math.multiplyExact(source[i].amount, n);
        }
        return result;
    }

    static ItemStack ore(OrePrefixes prefix, Materials material, int count) {
        ItemStack stack = GTOreDictUnificator.get(prefix, material, count);
        if (stack == null) throw new IllegalStateException("Missing critical material: " + prefix + material);
        return stack;
    }

    private static FluidStack fluid(String name, int count) {
        FluidStack result = FluidRegistry.getFluidStack(name, count);
        if (result == null) throw new IllegalStateException("Missing critical fluid: " + name);
        return result;
    }

    private static ItemStack[] none() {
        return new ItemStack[0];
    }

    private static FluidStack[] noFluids() {
        return new FluidStack[0];
    }

    private static ItemStack[] items(ItemStack... stacks) {
        return stacks;
    }

    private static FluidStack[] fluids(FluidStack... stacks) {
        return stacks;
    }

    private static void add(List<Spec> list, String id, CriticalMachineKind kind, ItemStack[] in, FluidStack[] fin,
        ItemStack[] out, FluidStack[] fout, int duration, long eu, long generated) {
        for (ItemStack s : in) if (s == null || s.stackSize <= 0) throw new IllegalStateException(id);
        for (ItemStack s : out) if (s == null || s.stackSize <= 0) throw new IllegalStateException(id);
        for (FluidStack s : fin) if (s == null || s.amount <= 0) throw new IllegalStateException(id);
        for (FluidStack s : fout) if (s == null || s.amount <= 0) throw new IllegalStateException(id);
        list.add(new Spec(id, kind, in, fin, out, fout, duration, eu, generated));
    }

    private static final class Spec {

        final String id;
        final CriticalMachineKind kind;
        final ItemStack[] in, out;
        final FluidStack[] fin, fout;
        final int duration;
        final long eu, generated;

        Spec(String id, CriticalMachineKind kind, ItemStack[] in, FluidStack[] fin, ItemStack[] out, FluidStack[] fout,
            int duration, long eu, long generated) {
            this.id = id;
            this.kind = kind;
            this.in = in;
            this.fin = fin;
            this.out = out;
            this.fout = fout;
            this.duration = duration;
            this.eu = eu;
            this.generated = generated;
        }
    }
}
