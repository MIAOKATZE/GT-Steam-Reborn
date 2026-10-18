package com.miaokatze.gtsr.api.recipe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;

/** Dedicated NEI views of the released runtimes. The loom is never a production catalyst. */
public final class CriticalRecipeMaps {

    private static final EnumMap<CriticalMachineKind, RecipeMap<RecipeMapBackend>> MAPS = new EnumMap<>(
        CriticalMachineKind.class);
    private static final List<DisplayInfo> DISPLAY_INFO = new ArrayList<>();
    public static final RecipeMap<RecipeMapBackend> stellarIrradiation = RecipeMapBuilder
        .of("gtsr.recipe.stellar_irradiation")
        .maxIO(1, 1, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(
            builder -> builder.setHeight(180)
                .setMaxRecipesPerPage(1))
        .frontend(CriticalRecipeFrontend::new)
        .build();

    static {
        for (CriticalMachineKind kind : CriticalMachineKind.releasedValues()) {
            MAPS.put(kind, create(kind));
        }
    }

    private CriticalRecipeMaps() {}

    /** null also covers the loom and all frozen controllers, so GT does not register them as catalysts. */
    public static RecipeMap<RecipeMapBackend> forKind(CriticalMachineKind kind) {
        return MAPS.get(kind);
    }

    public static void addDisplayRecipe(CriticalMachineKind kind, GTRecipe recipe, String operation,
        BigInteger baseEu) {
        RecipeMap<RecipeMapBackend> map = operation.equals("solar_dust") ? stellarIrradiation : forKind(kind);
        if (map == null) throw new IllegalArgumentException("No released critical recipe map: " + kind);
        recipe.mSpecialValue = DISPLAY_INFO.size();
        DISPLAY_INFO.add(new DisplayInfo(operation, baseEu));
        recipe.mDuration = CriticalEnergyMath.CYCLE_TICKS;
        recipe.mHidden = false;
        // The runtime has no voltage gate. Its actual consumption/generation is rendered explicitly,
        // rather than letting NEI's standard overclock filter hide recipes from the controller.
        recipe.mEUt = 0;
        recipe.setRecipeCategory(map.getDefaultRecipeCategory());
        map.getBackend()
            .compileRecipe(recipe);
    }

    public static List<String> description(GTRecipe recipe) {
        List<String> lines = new ArrayList<>();
        int index = recipe.mSpecialValue;
        if (index < 0 || index >= DISPLAY_INFO.size()) return lines;
        DisplayInfo info = DISPLAY_INFO.get(index);
        lines.add(text("cycle"));
        if (info.operation.equals("sun")) {
            lines.add(text("unit_parallel"));
            lines.add(formatted("fusion_eut", info.baseEu));
            lines.add(text("fusion_parallel"));
            lines.add(text("fusion_start"));
        } else {
            lines.add(text("ratio"));
            if (info.operation.equals("battery") || info.operation.equals("turbine")) {
                lines.add(formatted("base_generated", info.baseEu));
                lines.add(text("all_inputs"));
                lines.add(text("dynamic_efficiency"));
                lines.add(text(info.operation.equals("battery") ? "no_promoter" : "condensation"));
            } else {
                lines.add(text("no_energy"));
                if (info.operation.equals("solar_dust")) {
                    lines.add(formatted("solar_irradiation", CriticalEnergyMath.IRRADIATION_PARALLEL));
                } else {
                    lines.add(text("solar_volume"));
                }
            }
        }
        return lines;
    }

    private static RecipeMap<RecipeMapBackend> create(CriticalMachineKind kind) {
        boolean fusion = kind == CriticalMachineKind.SUN;
        return RecipeMapBuilder.of("gtsr.recipe.critical." + kind.key)
            .maxIO(0, 0, fusion ? 2 : 1, kind == CriticalMachineKind.BATTERY ? 0 : 1)
            .minInputs(0, 0)
            .neiRecipeBackgroundSize(170, 82)
            .neiHandlerInfo(
                builder -> builder.setHeight(180)
                    .setMaxRecipesPerPage(1))
            .frontend(CriticalRecipeFrontend::new)
            .build();
    }

    private static String text(String key) {
        return StatCollector.translateToLocal("gtsr.critical.nei." + key);
    }

    private static String formatted(String key, Object value) {
        return StatCollector.translateToLocalFormatted("gtsr.critical.nei." + key, value);
    }

    private static final class DisplayInfo {

        private final String operation;
        private final BigInteger baseEu;

        private DisplayInfo(String operation, BigInteger baseEu) {
            this.operation = operation;
            this.baseEu = baseEu;
        }
    }
}
