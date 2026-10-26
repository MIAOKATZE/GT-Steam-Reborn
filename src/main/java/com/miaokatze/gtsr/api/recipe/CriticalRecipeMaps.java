package com.miaokatze.gtsr.api.recipe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CompoundRecipe;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalSolarPolicy;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.util.GTRecipe;

/** Read-only NEI catalogues. Every entry is fake; custom runtimes never execute these maps. */
public final class CriticalRecipeMaps {

    public static final int CONSTRUCTION_PAGE_SIZE = 18;
    private static final EnumMap<CriticalMachineKind, RecipeMap<RecipeMapBackend>> MAPS = new EnumMap<>(
        CriticalMachineKind.class);
    private static final List<DisplayInfo> DISPLAY_INFO = new ArrayList<>();
    public static final RecipeMap<RecipeMapBackend> construction = displayBuilder("gtsr.recipe.critical.loom")
        .maxIO(CONSTRUCTION_PAGE_SIZE, 1, 6, 0)
        .neiItemInputsGetter(CriticalRecipeMaps::constructionItems)
        .neiFluidInputsGetter(CriticalRecipeMaps::constructionFluids)
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
        register(map, recipe, new DisplayInfo(map, kind, operation, baseEu, spectrum, 0, 1));
    }

    public static void addChemicalRecipe(GTRecipe recipe, CompoundRecipe compound, BigInteger inputHeat,
        BigInteger outputHeat) {
        RecipeMap<RecipeMapBackend> map = forKind(CriticalMachineKind.BATTERY);
        DisplayInfo info = new DisplayInfo(map, CriticalMachineKind.BATTERY, "chemical_compound", outputHeat, -1, 0, 1);
        info.compound = compound;
        info.inputHeat = inputHeat;
        register(map, recipe, info);
    }

    public static void addFusionRecipe(GTRecipe recipe, long nativeEut, int nativeTicks) {
        RecipeMap<RecipeMapBackend> map = forKind(CriticalMachineKind.SUN);
        DisplayInfo info = new DisplayInfo(
            map,
            CriticalMachineKind.SUN,
            "sun",
            BigInteger.valueOf(
                com.miaokatze.gtsr.common.critical.CriticalStellarPolicy.runEuPerTick(nativeEut, nativeTicks, 1)),
            -1,
            0,
            1);
        info.nativeEut = nativeEut;
        info.nativeTicks = nativeTicks;
        register(map, recipe, info);
    }

    /** The same immutable entry drives the paid runtime medium and its NEI example. */
    public static void addSolarMedium(GTRecipe recipe, CriticalSolarPolicy.Entry entry, boolean coolant) {
        RecipeMap<RecipeMapBackend> map = forKind(CriticalMachineKind.SOLAR);
        DisplayInfo info = new DisplayInfo(
            map,
            CriticalMachineKind.SOLAR,
            coolant ? "solar_coolant" : "solar_exciter",
            BigInteger.ZERO,
            -1,
            0,
            1);
        info.solarMedium = entry;
        register(map, recipe, info);
    }

    /** One page per major phase; native slot indices preserve each of the three paid rows. */
    public static void addConstruction(GTRecipe recipe, CriticalMachineKind kind, int group, ItemStack[][] items,
        FluidStack[][] fluids) {
        addConstruction(recipe, kind, group, items, fluids, new int[] { 1, 1, 1 });
    }

    public static void addConstruction(GTRecipe recipe, CriticalMachineKind kind, int group, ItemStack[][] items,
        FluidStack[][] fluids, int[] steps) {
        DisplayInfo info = new DisplayInfo(construction, kind, "build_" + group, BigInteger.ZERO, -1, 0, 1);
        info.constructionItems = new ItemStack[CONSTRUCTION_PAGE_SIZE];
        info.constructionFluids = new FluidStack[6];
        if (items.length != 3 || fluids.length != 3 || steps.length != 3)
            throw new IllegalArgumentException("Three construction rows required");
        for (int row = 0; row < 3; row++) {
            if (steps[row] < 1) throw new IllegalArgumentException("Positive construction step count required");
            info.constructionSteps[row] = steps[row];
            if (items[row].length > 6 || fluids[row].length > 2) throw new IllegalArgumentException(
                "Construction row exceeds six items / two fluids: " + kind + "/" + group + "/" + row);
            System.arraycopy(items[row], 0, info.constructionItems, row * 6, items[row].length);
            System.arraycopy(fluids[row], 0, info.constructionFluids, row * 2, fluids[row].length);
        }
        if (group == 0) {
            // Display-only optional accelerator, absent from recipe.mInputs and the paid bill.
            // Zero size uses GT205's native non-consumed example marker.
            ItemStack accelerator = GTSRItemList.CriticalSteamEntangledSingularity.get(1);
            accelerator.stackSize = 0;
            info.constructionItems[CriticalNEILayout.CURING_EXAMPLE_INDEX] = accelerator;
        }
        register(construction, recipe, info);
    }

    public static ItemStack[] constructionItems(GTRecipe recipe) {
        DisplayInfo info = displayInfo(recipe);
        return info == null || info.constructionItems == null ? recipe.mInputs : info.constructionItems;
    }

    public static FluidStack[] constructionFluids(GTRecipe recipe) {
        DisplayInfo info = displayInfo(recipe);
        return info == null || info.constructionFluids == null ? recipe.mFluidInputs : info.constructionFluids;
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
        return displayBuilder(name, false);
    }

    private static RecipeMapBuilder<RecipeMapBackend> displayBuilder(String name, boolean chemical) {
        return RecipeMapBuilder.of(name)
            .minInputs(0, 0)
            .dontUseProgressBar()
            .neiRecipeComparator(java.util.Comparator.comparingInt(recipe -> recipe.mSpecialValue))
            .neiTransferRect(
                (chemical ? CriticalNEILayout.CHEMICAL_BAR_X : CriticalNEILayout.ARROW_X) + CriticalNEILayout.WINDOW_X,
                (chemical ? CriticalNEILayout.CHEMICAL_BAR_Y : CriticalNEILayout.ITEM_Y + 3)
                    + CriticalNEILayout.WINDOW_Y,
                chemical ? CriticalNEILayout.CHEMICAL_BAR_WIDTH : CriticalNEILayout.ARROW_WIDTH,
                chemical ? CriticalNEILayout.CHEMICAL_BAR_HEIGHT : 37)
            .neiRecipeBackgroundSize(CriticalNEILayout.WIDTH, CriticalNEILayout.HEIGHT)
            .neiRecipeBackgroundOffset(CriticalNEILayout.WINDOW_X, CriticalNEILayout.WINDOW_Y)
            .neiHandlerInfo(
                builder -> builder.setHeight(180)
                    .setMaxRecipesPerPage(1));
    }

    private static RecipeMap<RecipeMapBackend> create(CriticalMachineKind kind) {
        return displayBuilder("gtsr.recipe.critical." + kind.key, kind == CriticalMachineKind.BATTERY)
            .maxIO(
                kind == CriticalMachineKind.BATTERY ? 1 : 0,
                0,
                kind == CriticalMachineKind.BATTERY ? 9
                    : kind == CriticalMachineKind.SUN ? 3 : kind == CriticalMachineKind.SOLAR ? 3 : 1,
                kind == CriticalMachineKind.BATTERY ? 0 : 1)
            .frontend((ui, nei) -> new CriticalRecipeFrontend(ui, nei, false, kind == CriticalMachineKind.BATTERY))
            .build();
    }

    public static String text(String key) {
        return StatCollector.translateToLocal("gtsr.critical.nei." + key);
    }

    public static String formatted(String key, Object... values) {
        return StatCollector.translateToLocalFormatted("gtsr.critical.nei." + key, values);
    }

    public static final class DisplayInfo {

        public final RecipeMap<?> recipeMap;
        public final CriticalMachineKind kind;
        public final String operation;
        public final BigInteger baseEu;
        public final int spectrum, page, pages;
        private ItemStack[] constructionItems;
        private FluidStack[] constructionFluids;
        private final int[] constructionSteps = { 1, 1, 1 };
        public CompoundRecipe compound;
        public BigInteger inputHeat = BigInteger.ZERO;
        public long nativeEut;
        public int nativeTicks;
        public CriticalSolarPolicy.Entry solarMedium;

        private DisplayInfo(RecipeMap<?> recipeMap, CriticalMachineKind kind, String operation, BigInteger baseEu,
            int spectrum, int page, int pages) {
            this.recipeMap = recipeMap;
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

        /** Three major phases map directly to the three construction pages. */
        public int constructionGroup() {
            return constructionStage();
        }

        public int constructionSteps(int row) {
            return constructionSteps[row];
        }
    }
}
