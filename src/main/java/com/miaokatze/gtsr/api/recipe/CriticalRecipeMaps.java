package com.miaokatze.gtsr.api.recipe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.critical.recipe.CriticalRecipes;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;

/** Standard GT/NEI maps mirror the authoritative server batch definitions. */
public final class CriticalRecipeMaps {

    private static final EnumMap<CriticalMachineKind, RecipeMap<RecipeMapBackend>> MAPS = new EnumMap<>(
        CriticalMachineKind.class);
    public static final RecipeMap<RecipeMapBackend> displayRecipes = create("loom");
    public static final RecipeMap<RecipeMapBackend> stellarIrradiation = RecipeMapBuilder
        .of("gtsr.recipe.stellar_irradiation")
        .maxIO(1, 1, 0, 0)
        .minInputs(1, 0)
        .build();

    static {
        for (CriticalMachineKind kind : CriticalMachineKind.values()) MAPS.put(kind, create(kind.key));
    }

    private CriticalRecipeMaps() {}

    public static RecipeMap<RecipeMapBackend> forKind(CriticalMachineKind kind) {
        return kind == null ? displayRecipes : MAPS.get(kind);
    }

    public static int displayCode(CriticalConfiguration cfg, int index) {
        return cfg.tier.ordinal() * 100 + cfg.kind.ordinal() * 10 + index;
    }

    private static RecipeMap<RecipeMapBackend> create(String key) {
        return RecipeMapBuilder.of("gtsr.recipe.critical." + key)
            .maxIO(9, 9, 4, 4)
            .minInputs(0, 0)
            .neiSpecialInfoFormatter(info -> {
                int code = info.recipe.mSpecialValue;
                if (key.equals("solar") || key.equals("turbine")) {
                    List<String> energy = new ArrayList<>();
                    energy.add(StatCollector.translateToLocal("gtsr.critical.nei.energy." + key));
                    if (key.equals("turbine")) {
                        int[] rates = { 16, 48, 80, 16000, 48000, 80000 };
                        energy.add(
                            StatCollector.translateToLocalFormatted(
                                "gtsr.critical.nei.energy.rate",
                                rates[Math.floorMod(code, 6)]));
                    }
                    energy.add(StatCollector.translateToLocal("gtsr.critical.nei.energy.cycle"));
                    return energy;
                }
                CriticalTier tier = CriticalTier.byId(code / 100);
                CriticalMachineKind kind = CriticalMachineKind.byId(code % 100 / 10);
                List<RecipeMatch> batches = CriticalRecipes.list(new CriticalConfiguration(tier, kind, 0, 0, 0));
                List<String> lines = new ArrayList<>();
                lines.add(StatCollector.translateToLocalFormatted("gtsr.critical.nei.tier", tier.ordinal() + 1));
                lines.add(StatCollector.translateToLocal("gtsr.critical.kind." + kind.key));
                if (code % 10 < batches.size()) {
                    RecipeMatch batch = batches.get(code % 10);
                    lines.add(StatCollector.translateToLocal("gtsr.critical.recipe." + batch.id));
                    if (batch.generatedEu > 0) lines
                        .add(StatCollector.translateToLocalFormatted("gtsr.critical.nei.generated", batch.generatedEu));
                }
                lines.add(StatCollector.translateToLocal("gtsr.critical.nei.batch"));
                return lines;
            })
            .build();
    }
}
