package com.miaokatze.gtsr.common.critical.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.critical.CriticalMaterialDefinitions;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;

/** The 24 physical RC2 casing identities. Aliases never create extra recipe or billing identities. */
public final class CriticalShellCatalog {

    public enum Family {

        S(30, 2304, "alloy_frame", "reinforced_panel", "carbon_frame"),
        H(20, 1152, "ceramic_armor", "ceramic_dark", "heat_grille", "maintenance_grate"),
        E(40, 4608, "copper_busbar", "superconducting_contact", "magnetic_coil", "luminous_rail"),
        O(30, 1152, "blue_field_glass", "violet_phase_glass", "stellar_lens"),
        F(60, 2304, "fuel_socket", "coolant_channel", "gt_bus", "gt_hatch"),
        M(60, 2304, "loom_spool", "loom_guide"),
        C(40, 4608, "loom_controller"),
        G(60, 4608, "gravity_modulator", "thermal_confinement_coil", "stellar_plasma_skin");

        public final int seconds, solder;
        public final List<String> identities;

        Family(int seconds, int solder, String... ids) {
            this.seconds = seconds;
            this.solder = solder;
            List<String> names = new ArrayList<>();
            for (String id : ids) names.add("v2_" + id);
            identities = Collections.unmodifiableList(names);
        }
    }

    private static final Map<String, Family> FAMILIES = new LinkedHashMap<>();
    static {
        for (Family family : Family.values()) for (String id : family.identities) FAMILIES.put(id, family);
    }

    private CriticalShellCatalog() {}

    public static Map<String, Family> identities() {
        return Collections.unmodifiableMap(FAMILIES);
    }

    public static Family family(String alias) {
        Family family = FAMILIES.get(CriticalMaterialDefinitions.canonicalId(alias));
        if (family == null) throw new IllegalArgumentException("Not an RC2 physical casing: " + alias);
        return family;
    }

    public static ItemStack shell(String alias, int count) {
        family(alias);
        return new ItemStack(CriticalMaterials.block(alias, false), count, 0);
    }

    public static ItemStack carbon(int count) {
        ItemStack stack = GTModHandler.getIC2Item("carbonPlate", count);
        if (stack == null) throw new IllegalStateException("Missing IC2 carbonPlate");
        return stack;
    }

    private static ItemStack ore(OrePrefixes prefix, Materials material, int count) {
        return CriticalRecipes.ore(prefix, material, count);
    }

    /** Consumed inputs only; caller adds the non-consumed programmed circuit for each physical identity. */
    public static ItemStack[] ingredients(Family family) {
        return switch (family) {
            case S -> new ItemStack[] { ore(OrePrefixes.plate, Materials.TungstenSteel, 32),
                ore(OrePrefixes.plate, Materials.Neutronium, 16), carbon(8),
                ore(OrePrefixes.stickLong, Materials.Neutronium, 4) };
            case H -> new ItemStack[] { ore(OrePrefixes.plate, Materials.TungstenCarbide, 32),
                ore(OrePrefixes.dust, Materials.Graphite, 16), ore(OrePrefixes.dust, Materials.SiliconDioxide, 16),
                carbon(8) };
            case E -> new ItemStack[] { ore(OrePrefixes.plate, Materials.NaquadahAlloy, 16),
                ore(OrePrefixes.wireGt01, Materials.SuperconductorUV, 32),
                ore(OrePrefixes.wireFine, Materials.Neutronium, 64),
                ore(OrePrefixes.plate, Materials.Polytetrafluoroethylene, 16) };
            case O -> new ItemStack[] { ore(OrePrefixes.plate, Materials.Glass, 32),
                ore(OrePrefixes.foil, Materials.Europium, 16), ore(OrePrefixes.foil, Materials.Neutronium, 16),
                ore(OrePrefixes.plate, Materials.NaquadahAlloy, 8) };
            case F -> new ItemStack[] { ore(OrePrefixes.plate, Materials.NaquadahAlloy, 16),
                ore(OrePrefixes.plate, Materials.Polytetrafluoroethylene, 16),
                ore(OrePrefixes.pipeMedium, Materials.Neutronium, 8), ItemList.Electric_Pump_UV.get(2) };
            case M -> new ItemStack[] { ore(OrePrefixes.plate, Materials.Neutronium, 16),
                ore(OrePrefixes.stickLong, Materials.Neutronium, 16),
                ore(OrePrefixes.gearGtSmall, Materials.NaquadahAlloy, 8), ItemList.Electric_Motor_UV.get(4) };
            case C -> new ItemStack[] { ore(OrePrefixes.plate, Materials.Neutronium, 16),
                ore(OrePrefixes.circuit, Materials.UV, 8), ore(OrePrefixes.circuit, Materials.ZPM, 16),
                ore(OrePrefixes.foil, Materials.Europium, 16), ore(OrePrefixes.wireFine, Materials.Neutronium, 16) };
            case G -> new ItemStack[] { ore(OrePrefixes.plate, Materials.CosmicNeutronium, 16),
                ore(OrePrefixes.wireFine, Materials.Neutronium, 32), ItemList.Field_Generator_UV.get(4),
                ItemList.Emitter_UV.get(4), ore(OrePrefixes.plate, Materials.NaquadahAlloy, 16) };
        };
    }
}
