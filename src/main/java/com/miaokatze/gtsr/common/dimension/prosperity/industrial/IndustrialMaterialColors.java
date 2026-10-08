package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Material-owned RGB tints shared by generated dust/cells and GT native fluids. */
public final class IndustrialMaterialColors {

    private static final Map<String, Integer> COLORS = createPalette();

    private IndustrialMaterialColors() {}

    private static Map<String, Integer> createPalette() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("planned:acid_mineral_cake", 0xff9d967e);
        colors.put("planned:acid_mineral_liquor", 0xff667a5f);
        colors.put("planned:aromatic_conversion_feed", 0xffb58c49);
        colors.put("planned:carbon_heavy_liquor", 0xff35312b);
        colors.put("planned:cleaned_withered_feed", 0xffd3dbcc);
        colors.put("planned:crude_krypton", 0xffb2cad7);
        colors.put("planned:crude_native_fluorine", 0xffece999);
        colors.put("planned:crude_neon", 0xffd9bacd);
        colors.put("planned:crude_xenon", 0xffabafcf);
        colors.put("planned:desulfurized_grease", 0xff746039);
        colors.put("planned:electrothermal_heavy_crack", 0xff594326);
        colors.put("planned:electrothermal_light_crack", 0xffd2b477);
        colors.put("planned:grease_aromatic_cut", 0xffc59b52);
        colors.put("planned:grease_bioorganic_fraction", 0xff82783f);
        colors.put("planned:grease_heavy_cut", 0xff49382b);
        colors.put("planned:grease_light_cut", 0xffe0c782);
        colors.put("planned:grease_oxygenate_cut", 0xffe4dec2);
        colors.put("planned:heated_grease_r3", 0xff65502d);
        colors.put("planned:homogenized_mire_r3", 0xff302b22);
        colors.put("planned:mire_alcohol_fraction", 0xffd9e6e0);
        colors.put("planned:mire_ammonium_fraction", 0xffc8d5b2);
        colors.put("planned:native_acid_condensate", 0xffe3dca6);
        colors.put("planned:native_acid_cut", 0xfff0ebc8);
        colors.put("planned:native_chlorine_fraction", 0xffccd886);
        colors.put("planned:native_coolant_polyol", 0xff80bdb3);
        colors.put("planned:native_fluorine_fraction", 0xffe4e8af);
        colors.put("planned:native_hcl_fraction", 0xffe5ead6);
        colors.put("planned:native_hf_fraction", 0xffddede3);
        colors.put("planned:native_mixed_acids", 0xffe8dfc4);
        colors.put("planned:native_sanzu_hydrogen", 0xffc2dee8);
        colors.put("planned:petro_mineral_cake", 0xff7d786a);
        colors.put("planned:reformed_aromatic_feed", 0xffd5b065);
        colors.put("planned:sanzu_condensate_r3", 0xffbed8dd);
        colors.put("planned:sanzu_mineral_liquor", 0xff536b63);
        colors.put("planned:vegetal_aqueous_fraction", 0xffa6ba73);
        colors.put("planned:vegetal_carbon_silica_cake", 0xff3f4137);
        colors.put("planned:vegetal_filtercake_r3", 0xff665c37);
        colors.put("planned:vegetal_mineral_cake", 0xffaca17f);
        colors.put("planned:washed_mire_r3", 0xff504a32);
        colors.put("planned:washed_sanzu", 0xffd2e3df);
        colors.put("planned:withered_bulk_air", 0xffc4d2db);
        colors.put("planned:withered_carbon_fraction", 0xffadb5ae);
        colors.put("planned:withered_condensate_r3", 0xffbab38b);
        colors.put("planned:withered_hydrogen_cut", 0xffd5e9ed);
        colors.put("planned:withered_noble_fraction", 0xff9cafc9);
        colors.put("planned:withered_trace_nobles", 0xffbdd1d8);
        colors.put("planned:wet_captured_metalgrit", 0xff96704c);
        colors.put("planned:magnetic_metal_cake", 0xff686c6e);
        colors.put("planned:base_metal_cake_r3", 0xffba8b65);
        colors.put("planned:refractory_metal_cake", 0xffa2a7aa);
        colors.put("planned:grit_nonmetal_cake", 0xffbaac91);
        colors.put("planned:rare_resource_liquor", 0xff746c48);
        colors.put("planned:washed_base_metal_cake_r3", 0xffcaa583);
        colors.put("planned:pgm_native_concentrate", 0xff999fa9);
        colors.put("planned:rareearth_native_concentrate", 0xffc3aa88);
        colors.put("planned:radioactive_native_concentrate", 0xff879756);
        colors.put("planned:tantalite_native_concentrate", 0xff575d61);
        colors.put("planned:rare_nonmetal_native_concentrate", 0xffb7aa63);
        colors.put("planned:pgm_mixed_cake_r3", 0xffb6bec4);
        colors.put("planned:radioactive_mixed_cake_r3", 0xff697748);
        colors.put("planned:uranium_native_cake", 0xff9b9d42);
        colors.put("planned:thorium_native_cake", 0xff7c858a);
        colors.put("planned:radioactive_nonmetal_cake", 0xffa18d70);
        colors.put("planned:native_rare_liquor", 0xff6c786f);
        colors.put("planned:acid_trace_cake", 0xff9d846a);
        colors.put("planned:acid_boron_barium_cake", 0xffbab394);
        colors.put("planned:withered_mineral_cake", 0xffa1ab95);
        colors.put("planned:withered_trace_cake", 0xff8c9d89);
        colors.put("planned:withered_indium_cake", 0xff828f9e);
        colors.put("planned:magnetic_manganese_cake", 0xff837687);
        colors.put("planned:magnetic_neodymium_cake", 0xff858998);
        colors.put("planned:magnetic_nickel_cake", 0xff87946f);
        colors.put("planned:magnetic_samarium_cake", 0xffb9b385);
        colors.put("planned:grit_tungsten_antimony_cake", 0xff9c9877);
        colors.put("planned:grit_gallium_molybdenum_cake", 0xff8498a7);
        colors.put("planned:grit_indium_cake", 0xff777e99);
        colors.put("planned:leached_uranium_cake", 0xff949a43);
        colors.put("planned:grit_antimony_cake", 0xffb0a695);
        colors.put("planned:grit_molybdenum_cake", 0xff989cac);
        colors.put("planned:pgm_leached_cake", 0xffb5b9b5);
        colors.put("planned:pgm_separated_cake", 0xff969bb0);
        colors.put("planned:rareearth_separated_cake", 0xffb3a082);
        colors.put("planned:tantalite_separated_cake", 0xff666972);
        colors.put("planned:washed_grit_indium_cake", 0xff87959e);
        colors.put("planned:mixed_grit_indium_cake", 0xff98a0ab);
        colors.put("planned:washed_withered_indium_cake", 0xff929baa);
        colors.put("planned:grit_indium_solution", 0xff96a4b5);
        colors.put("planned:grit_indium_waste_liquor", 0xff74858f);
        colors.put("planned:sanzu_mineral_filtercake", 0xff8c8972);
        colors.put("planned:degassed_sanzu_filtercake", 0xffac9b75);
        colors.put("planned:washed_sanzu_filtercake", 0xffb8b19c);
        colors.put("planned:sanzu_phosphate_salt_cake", 0xffc0b7a0);
        colors.put("planned:sanzu_organic_fraction", 0xff6e6850);
        colors.put("planned:active_fungal_suspension", 0xff8ba766);
        colors.put("planned:fungal_conversion_liquor", 0xffadbd87);
        colors.put("planned:clarified_fungal_liquor", 0xffc7d6a0);
        colors.put("planned:prosperity_dust", 0xff8d9ba9);
        colors.put("planned:prosperity_reminiscence", 0xff7da6bd);
        colors.put("planned:prosperity_obsession", 0xff456785);
        return Collections.unmodifiableMap(colors);
    }

    public static int getARGB(String id) {
        Integer argb = COLORS.get(id);
        if (argb == null) throw new IllegalStateException("Missing industrial material color: " + id);
        return argb;
    }

    /** Fail before allocating IDs if a material was added without a deliberate color. */
    public static void verifyCoverage(IndustrialRecipeLedger ledger) {
        Set<String> ids = new HashSet<>();
        for (IndustrialRecipeLedger.MaterialDefinition definition : ledger.materials) {
            if (!ids.add(definition.id)) {
                throw new IllegalStateException("Duplicate industrial material: " + definition.id);
            }
            getARGB(definition.id);
        }
        if (!ids.equals(COLORS.keySet())) {
            throw new IllegalStateException("Industrial material color palette differs from registered definitions");
        }
    }
}
