package com.miaokatze.gtsr.api.recipe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.critical.CriticalChemicalMath;
import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;

/** Read-only NEI catalogues. Every entry is fake; custom runtimes never execute these maps. */
public final class CriticalRecipeMaps {

    public static final int CONSTRUCTION_PAGE_SIZE = 12;
    private static final EnumMap<CriticalMachineKind, RecipeMap<RecipeMapBackend>> MAPS = new EnumMap<>(
        CriticalMachineKind.class);
    private static final List<DisplayInfo> DISPLAY_INFO = new ArrayList<>();
    public static final RecipeMap<RecipeMapBackend> construction = displayBuilder("gtsr.recipe.critical.loom")
        .maxIO(CONSTRUCTION_PAGE_SIZE, 1, 1, 0)
        .frontend((ui, nei) -> new CriticalRecipeFrontend(ui, nei, true))
        .build();
    public static final RecipeMap<RecipeMapBackend> stellarIrradiation = displayBuilder(
        "gtsr.recipe.stellar_irradiation").maxIO(1, 1, 0, 0)
            .frontend(CriticalRecipeFrontend::new)
            .build();

    static {
        for (CriticalMachineKind kind : CriticalMachineKind.releasedValues()) MAPS.put(kind, create(kind));
    }

    private CriticalRecipeMaps() {}

    /** The loom indexes construction, while frozen controllers still have no processing catalogue. */
    public static RecipeMap<RecipeMapBackend> forKind(CriticalMachineKind kind) {
        return kind == null ? construction : MAPS.get(kind);
    }

    public static void addDisplayRecipe(CriticalMachineKind kind, GTRecipe recipe, String operation,
        BigInteger baseEu) {
        addDisplayRecipe(kind, recipe, operation, baseEu, -1);
    }

    public static void addDisplayRecipe(CriticalMachineKind kind, GTRecipe recipe, String operation, BigInteger baseEu,
        int spectrum) {
        RecipeMap<RecipeMapBackend> map = operation.equals("solar_dust") ? stellarIrradiation : forKind(kind);
        int pages = switch (operation) {
            case "sun", "battery" -> 3;
            default -> 2;
        };
        for (int page = 0; page < pages; page++) register(
            map,
            page == 0 ? recipe : recipe.copy(),
            new DisplayInfo(kind, operation, baseEu, spectrum, page, pages));
    }

    public static void addConstruction(GTRecipe recipe, CriticalMachineKind kind, int stage, int page, int pages) {
        register(construction, recipe, new DisplayInfo(kind, "build_" + stage, BigInteger.ZERO, -1, page, pages));
    }

    private static void register(RecipeMap<RecipeMapBackend> map, GTRecipe recipe, DisplayInfo info) {
        if (map == null) throw new IllegalArgumentException("No released critical catalogue: " + info.kind);
        recipe.mSpecialValue = DISPLAY_INFO.size();
        DISPLAY_INFO.add(info);
        recipe.mHidden = false;
        recipe.mFakeRecipe = true;
        // No native voltage filter: all power and timing below belong to the real runtime.
        recipe.mEUt = 0;
        recipe.setRecipeCategory(map.getDefaultRecipeCategory());
        map.getBackend()
            .compileRecipe(recipe);
    }

    public static DisplayInfo displayInfo(GTRecipe recipe) {
        int index = recipe.mSpecialValue;
        return index < 0 || index >= DISPLAY_INFO.size() ? null : DISPLAY_INFO.get(index);
    }

    public static List<String> description(GTRecipe recipe) {
        List<String> lines = new ArrayList<>();
        DisplayInfo info = displayInfo(recipe);
        if (info == null) return lines;
        switch (info.operation) {
            case "build_0" -> {
                lines.add(text("showcase.foundation_bill"));
                lines.add(text("showcase.foundation_time"));
                lines.add(text("showcase.foundation_reuse"));
            }
            case "build_1", "build_2", "build_3", "build_4" -> {
                lines.add(formatted("showcase.page", info.page + 1, info.pages));
                lines.add(text("showcase.physical_bill"));
                lines.add(text("showcase.build_pause"));
                lines.add(text("showcase.world_product"));
            }
            case "sun" -> {
                lines.add(formatted("showcase.ignition", CriticalStellarPolicy.IGNITION_EU));
                lines.add(text("showcase.stellar_ready"));
                lines.add(text("showcase.hold"));
                lines.add(text("showcase.run"));
                lines.add(formatted("showcase.fusion_time", recipe.mDuration, CriticalStellarPolicy.MAX_PARALLEL));
                lines.add(formatted("showcase.fusion_cap", CriticalStellarPolicy.MKV_STARTUP));
                lines.add(text("showcase.fusion_ratio"));
            }
            case "battery" -> {
                lines.add(formatted("showcase.fuel", info.spectrum + 1, info.baseEu));
                lines.add(formatted("showcase.chemical_q", CriticalChemicalMath.CHEMICAL_BASE_HEAT_CAP_EU));
                for (int window = 0; window < CriticalChemicalMath.WINDOW_COUNT; window++)
                    lines.add(formatted("showcase.window_" + window, CriticalChemicalMath.multiplier(window)));
                lines.add(text("showcase.chemical_paid"));
            }
            case "turbine" -> {
                lines.add(text("showcase.turbine_cycle"));
                lines.add(formatted("base_generated", info.baseEu));
                lines.add(text("showcase.ratchet"));
                lines.add(text("showcase.ratchet_idle"));
                lines.add(text("condensation"));
                lines.add(text("showcase.wireless"));
            }
            default -> {
                if (info.operation.equals("solar_water")) lines.add(text("showcase.solar_water"));
                else if (info.operation.equals("solar_salt")) lines.add(text("showcase.solar_salt"));
                else lines.add(formatted("showcase.solar_dust", CriticalEnergyMath.IRRADIATION_PARALLEL));
                lines.add(text("showcase.solar_factor"));
                lines.add(text("showcase.solar_cycle"));
                lines.add(text("showcase.solar_ratio"));
            }
        }
        return lines;
    }

    private static RecipeMapBuilder<RecipeMapBackend> displayBuilder(String name) {
        return RecipeMapBuilder.of(name)
            .minInputs(0, 0)
            .dontUseProgressBar()
            .neiRecipeComparator(java.util.Comparator.comparingInt(recipe -> recipe.mSpecialValue))
            .neiTransferRect(63, 49, 71, 16)
            .neiRecipeBackgroundSize(170, 174)
            .neiRecipeBackgroundOffset(5, 11)
            .neiHandlerInfo(
                builder -> builder.setHeight(180)
                    .setMaxRecipesPerPage(1));
    }

    private static RecipeMap<RecipeMapBackend> create(CriticalMachineKind kind) {
        return displayBuilder("gtsr.recipe.critical." + kind.key)
            .maxIO(0, 0, kind == CriticalMachineKind.SUN ? 2 : 1, kind == CriticalMachineKind.BATTERY ? 0 : 1)
            .frontend(CriticalRecipeFrontend::new)
            .build();
    }

    public static String text(String key) {
        return StatCollector.translateToLocal("gtsr.critical.nei." + key);
    }

    public static String formatted(String key, Object... values) {
        return StatCollector.translateToLocalFormatted("gtsr.critical.nei." + key, values);
    }

    public static final class DisplayInfo {

        public final CriticalMachineKind kind;
        public final String operation;
        public final BigInteger baseEu;
        public final int spectrum, page, pages;

        private DisplayInfo(CriticalMachineKind kind, String operation, BigInteger baseEu, int spectrum, int page,
            int pages) {
            this.kind = kind;
            this.operation = operation;
            this.baseEu = baseEu;
            this.spectrum = spectrum;
            this.page = page;
            this.pages = pages;
        }

        public boolean construction() {
            return operation.startsWith("build_");
        }

        public int constructionStage() {
            return construction() ? Integer.parseInt(operation.substring(6)) : -1;
        }
    }
}
