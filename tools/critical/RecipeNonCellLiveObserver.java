package audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialRecipeLedger;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialRecipes;
import com.miaokatze.miaokatzecore.api.recipe.RecipeRegistration;

import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Read-only first-tick observer, packaged only in the owned compatibility audit mod. */
public final class RecipeNonCellLiveObserver {
    private static final String ID = "gtsr.noncell.live_observer";
    private static RecipeMap<?>[][] pairs;
    private static final Map<RecipeMap<?>, Set<GTRecipe>> beforeSource = new IdentityHashMap<>();
    private static final Map<RecipeMap<?>, Set<GTRecipe>> beforeTarget = new IdentityHashMap<>();
    private static final JsonArray checks = new JsonArray(), maps = new JsonArray(), recipes = new JsonArray();
    private static boolean before, derived, done;
    private static int failures;
    private static String status;

    public static void register() {
        RecipeRegistration.register(ID, RecipeNonCellLiveObserver::capture,
            RecipeNonCellLiveObserver::afterShared);
    }

    private static Set<GTRecipe> snapshot(RecipeMap<?> map) {
        Set<GTRecipe> result = Collections.newSetFromMap(new IdentityHashMap<GTRecipe, Boolean>());
        result.addAll(map.getAllRecipes());
        return result;
    }

    private static void check(String id, boolean pass, String detail) {
        JsonObject row = new JsonObject(); row.addProperty("id", id); row.addProperty("pass", pass);
        row.addProperty("detail", detail); checks.add(row); if (!pass) failures++;
    }

    private static void capture() {
        pairs = new RecipeMap<?>[][] {
            { RecipeMaps.centrifugeRecipes, RecipeMaps.centrifugeNonCellRecipes },
            { RecipeMaps.electrolyzerRecipes, RecipeMaps.electrolyzerNonCellRecipes },
            { RecipeMaps.mixerRecipes, RecipeMaps.mixerNonCellRecipes },
            { RecipeMaps.chemicalDehydratorRecipes, RecipeMaps.chemicalDehydratorNonCellRecipes },
            { RecipeMaps.coldTrapRecipes, RecipeMaps.nuclearSaltProcessingPlantRecipes },
            { RecipeMaps.reactorProcessingUnitRecipes, RecipeMaps.nuclearSaltProcessingPlantRecipes }
        };
        check("observer-before-gtsr-main", RecipeRegistration.registeredIds().equals(Arrays.asList(ID, "gtsr"))
            && !RecipeRegistration.isComplete(), RecipeRegistration.registeredIds().toString());
        for (RecipeMap<?>[] pair : pairs) {
            beforeSource.put(pair[0], snapshot(pair[0]));
            if (!beforeTarget.containsKey(pair[1])) beforeTarget.put(pair[1], snapshot(pair[1]));
        }
        before = true;
    }

    private static void afterShared() {
        check("shared-before-consumer-derived", before && !RecipeRegistration.isComplete(),
            "Core observer derived runs after all main and shared GT derivation, before GTSR derived");
        derived = true;
    }

    private static boolean sameItems(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) return false;
        for (int i=0;i<a.length;i++)
            if (a[i].stackSize != b[i].stackSize || !GTUtility.areStacksEqual(a[i], b[i])) return false;
        return true;
    }

    private static boolean sameFluids(FluidStack[] a, FluidStack[] b) {
        if (a.length != b.length) return false;
        for (int i=0;i<a.length;i++) if (a[i].amount != b[i].amount || !a[i].isFluidEqual(b[i])) return false;
        return true;
    }

    private static boolean sameRecipe(GTRecipe a, GTRecipe b) {
        return sameItems(a.mInputs,b.mInputs) && sameItems(a.mOutputs,b.mOutputs)
            && sameFluids(a.mFluidInputs,b.mFluidInputs) && sameFluids(a.mFluidOutputs,b.mFluidOutputs)
            && a.mDuration==b.mDuration && a.mEUt==b.mEUt && a.mSpecialValue==b.mSpecialValue
            && Arrays.equals(a.mInputChances,b.mInputChances) && Arrays.equals(a.mOutputChances,b.mOutputChances)
            && Arrays.equals(a.mFluidInputChances,b.mFluidInputChances) && Arrays.equals(a.mFluidOutputChances,b.mFluidOutputChances)
            && a.mEnabled==b.mEnabled && a.mHidden==b.mHidden && a.mFakeRecipe==b.mFakeRecipe
            && a.mCanBeBuffered==b.mCanBeBuffered && a.mNeedsEmptyOutput==b.mNeedsEmptyOutput
            && a.isNBTSensitive==b.isNBTSensitive && a.getMetadataStorage().getEntries().equals(b.getMetadataStorage().getEntries());
    }

    public static boolean finish(Path receipt) throws Exception {
        if (done) return true;
        if (!RecipeRegistration.isComplete()) return false;
        try {
            check("natural-core-complete", before && derived, "Natural Core main/shared/derived callbacks observed");
            Map<RecipeMap<?>, Integer> expectedTargets = new IdentityHashMap<>();
            int total=0, mixer=0, centrifuge=0;
            List<String> expectedStages = new ArrayList<>();
            for (IndustrialRecipeLedger.Stage stage : IndustrialRecipeLedger.get().recipes)
                if (stage.machineMap.equals("mixerRecipes") || stage.machineMap.equals("centrifugeRecipes")) expectedStages.add(stage.id);
            List<String> observedStages = new ArrayList<>();
            for (RecipeMap<?>[] pair : pairs) {
                int added=0;
                for (GTRecipe source : pair[0].getAllRecipes()) {
                    if (beforeSource.get(pair[0]).contains(source)) continue;
                    added++; total++;
                    if (pair[0]==RecipeMaps.mixerRecipes) mixer++;
                    if (pair[0]==RecipeMaps.centrifugeRecipes) centrifuge++;
                    String stage=source.getMetadataOrDefault(ProsperityIndustrialRecipes.INDUSTRIAL_STAGE, "");
                    if (!stage.isEmpty()) observedStages.add(stage);
                    GTRecipe found=pair[1].findRecipeQuery().items(GTUtility.copyItemArray(source.mInputs))
                        .fluids(GTUtility.copyFluidArray(source.mFluidInputs)).voltage(Long.MAX_VALUE)
                        .filter(candidate -> sameRecipe(candidate,source)).find();
                    JsonObject row=new JsonObject();row.addProperty("source",pair[0].unlocalizedName);
                    row.addProperty("target",pair[1].unlocalizedName);row.addProperty("stage",stage);
                    row.addProperty("lookup",found!=null);row.addProperty("EUt",source.mEUt);row.addProperty("ticks",source.mDuration);
                    recipes.add(row);
                    check("native-lookup-"+total,found!=null,"Exact IO/chances/metadata/flags/EUt/duration: "+stage);
                }
                Integer prior=expectedTargets.get(pair[1]);expectedTargets.put(pair[1],(prior==null?0:prior)+added);
                JsonObject row=new JsonObject();row.addProperty("source",pair[0].unlocalizedName);row.addProperty("added",added);maps.add(row);
            }
            Collections.sort(expectedStages);Collections.sort(observedStages);
            check("actual-r6-stages-covered",expectedStages.equals(observedStages),observedStages.toString());
            check("current-gtsr-scope",total==expectedStages.size()+2 && mixer==3 && centrifuge==26,
                "total="+total+" mixer="+mixer+" centrifuge="+centrifuge);
            for (Map.Entry<RecipeMap<?>,Set<GTRecipe>> entry : beforeTarget.entrySet()) {
                Set<GTRecipe> after=snapshot(entry.getKey());
                check("foreign-identities-kept-"+entry.getKey().unlocalizedName,after.containsAll(entry.getValue()),"Existing destination object identities unchanged");
                after.removeAll(entry.getValue());
                check("no-foreign-reimport-"+entry.getKey().unlocalizedName,after.size()==expectedTargets.get(entry.getKey()),
                    "new="+after.size()+" expected="+expectedTargets.get(entry.getKey()));
            }
            boolean clean=true;
            for(String name:RecipeMap.ALL_RECIPE_MAPS.keySet()) if(name.startsWith("miaokatzecore.recipe.noncell_")) clean=false;
            for(String name:RecipeCategory.ALL_RECIPE_CATEGORIES.keySet()) if(name.startsWith("miaokatzecore.recipe.noncell_")) clean=false;
            check("no-temporary-map-or-category",clean,"No private conversion graph remains");
        } catch (Exception failure) {
            check("observer-execution",false,failure.toString()); failure.printStackTrace();
        }
        status=failures==0?"PASS":"FAIL";done=true;
        JsonObject result=new JsonObject();result.addProperty("status",status);result.add("checks",checks);
        result.add("maps",maps);result.add("recipes",recipes);result.addProperty("failures",failures);
        result.addProperty("coreSource",RecipeRegistration.class.getProtectionDomain().getCodeSource().getLocation().toString());
        result.addProperty("gtSource",RecipeMaps.class.getProtectionDomain().getCodeSource().getLocation().toString());
        result.addProperty("productSource",ProsperityIndustrialRecipes.class.getProtectionDomain().getCodeSource().getLocation().toString());
        result.addProperty("scope","Natural first Core tick, real registered GTSR recipe objects and native lookup; no virtual recipe or world mutation");
        Files.createDirectories(receipt.getParent());
        Files.write(receipt,new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));
        System.out.println("[GTSR-RECIPE-LIVE] COMPLETE status="+status+" receipt="+receipt);
        return true;
    }

    public static String status() { return status; }
}
