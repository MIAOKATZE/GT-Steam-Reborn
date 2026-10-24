package com.miaokatze.gtsr.api.recipe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.critical.CriticalMachineKind;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;

/** Read-only NEI catalogues. Every entry is fake; custom runtimes never execute these maps. */
public final class CriticalRecipeMaps {

    public static final int CONSTRUCTION_PAGE_SIZE = 28;
    private static final EnumMap<CriticalMachineKind, RecipeMap<RecipeMapBackend>> MAPS = new EnumMap<>(
        CriticalMachineKind.class);
    private static final List<DisplayInfo> DISPLAY_INFO = new ArrayList<>();
    public static final RecipeMap<RecipeMapBackend> construction = displayBuilder("gtsr.recipe.critical.loom")
        .maxIO(CONSTRUCTION_PAGE_SIZE, 1, 4, 0)
        .frontend((ui, nei) -> new CriticalRecipeFrontend(ui, nei, true))
        .build();
    public static final RecipeMap<RecipeMapBackend> stellarIrradiation = displayBuilder(
        "gtsr.recipe.stellar_irradiation").maxIO(3, 1, 3, 1)
            .frontend(CriticalRecipeFrontend::new)
            .build();

    public static final RecipeMap<RecipeMapBackend> stellarLifecycle = displayBuilder("gtsr.recipe.stellar_lifecycle")
        .maxIO(0, 0, 1, 1)
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
        RecipeMap<RecipeMapBackend> map = operation.equals("solar_dust") ? stellarIrradiation
            : operation.startsWith("stellar_") ? stellarLifecycle : forKind(kind);
        register(map, recipe, new DisplayInfo(kind, operation, baseEu, spectrum, 0, 1));
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

    private static RecipeMapBuilder<RecipeMapBackend> displayBuilder(String name) {
        return RecipeMapBuilder.of(name)
            .minInputs(0, 0)
            .dontUseProgressBar()
            .neiRecipeComparator(java.util.Comparator.comparingInt(recipe -> recipe.mSpecialValue))
            .neiTransferRect(
                CriticalNEILayout.ARROW_X + CriticalNEILayout.WINDOW_X,
                CriticalNEILayout.ITEM_Y + 3 + CriticalNEILayout.WINDOW_Y,
                CriticalNEILayout.ARROW_WIDTH,
                37)
            .neiRecipeBackgroundSize(CriticalNEILayout.WIDTH, CriticalNEILayout.HEIGHT)
            .neiRecipeBackgroundOffset(CriticalNEILayout.WINDOW_X, CriticalNEILayout.WINDOW_Y)
            .neiHandlerInfo(
                builder -> builder.setHeight(180)
                    .setMaxRecipesPerPage(1));
    }

    private static RecipeMap<RecipeMapBackend> create(CriticalMachineKind kind) {
        return displayBuilder("gtsr.recipe.critical." + kind.key)
            .maxIO(
                0,
                0,
                kind == CriticalMachineKind.SUN || kind == CriticalMachineKind.BATTERY ? 3 : 1,
                kind == CriticalMachineKind.BATTERY ? 0 : 1)
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
