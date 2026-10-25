import java.math.BigInteger;
import java.util.*;
import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.critical.*;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.fluids.GTPPFluids;

/** Production arithmetic/catalogue contracts with isolated native GT205 registration fixtures. */
public final class CriticalCompoundContract {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalCompoundRuntime", "5.09.54.205");
    }
}

class CriticalCompoundRuntime {
    static int checks;
    static BigInteger b(long n) { return BigInteger.valueOf(n); }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    static Fluid register(String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        if (fluid == null) { fluid = new Fluid(name); FluidRegistry.registerFluid(fluid); }
        return fluid;
    }
    static void verifyCatalog(List<CriticalGTRecipes.ChemicalFuel> fuels) {
        var catalog=CriticalChemicalRecipes.catalog(fuels);
        check(catalog.size()==256,"256 distinct fixed combinations");
        check(catalog==CriticalChemicalRecipes.catalog(fuels),"immutable catalogue cached");
        Set<String> ids=new HashSet<>(),identities=new HashSet<>(); Set<Integer> peaks=new HashSet<>();
        for(var recipe:catalog) {
            check(ids.add(recipe.id),"stable ID unique "+recipe.id);
            List<String> names=new ArrayList<>(); BigInteger raw=b(0),total=b(0);
            BigInteger[] amounts=new BigInteger[recipe.components.size()]; int[] ratios=new int[amounts.length];
            for(int i=0;i<amounts.length;i++) {
                var part=recipe.components.get(i); names.add(part.fuel.fluid.getFluid().getName());
                amounts[i]=b(part.ratio); ratios[i]=part.ratio;
                total=total.add(amounts[i]); raw=raw.add(amounts[i].multiply(part.fuel.euPerL));
            }
            Collections.sort(names);
            check(identities.add(String.join("+",names)),"fluid identity combination unique "+recipe.id);
            var value=CriticalChemicalMath.evaluate(amounts,ratios,total,raw,recipe.peakMultiplier);
            check(value.eligible&&value.generatedEU.equals(raw.multiply(b(recipe.peakMultiplier)))
                &&value.efficiencyHundredths()==100*recipe.peakMultiplier,"NEI ideal agrees "+recipe.id);
            peaks.add(recipe.peakMultiplier);
        }
        check(peaks.size()>25&&Collections.max(peaks)==100,"meaningful peak range with 100x ceiling");
        check(CriticalChemicalRecipes.byId("promoted.3")!=null,"cached localization lookup");
        var reversed=new ArrayList<>(fuels); Collections.reverse(reversed);
        check(CriticalChemicalRecipes.catalog(reversed)==catalog,"native registration order cannot change catalogue");
        try { catalog.clear(); throw new AssertionError("mutable catalogue"); }
        catch(UnsupportedOperationException expected) { checks++; }
    }

    static void verifyMathAndSelection(List<CriticalGTRecipes.ChemicalFuel> fuels) {
        var ideal=CriticalChemicalMath.evaluate(new BigInteger[]{b(2),b(3)},new int[]{2,3},b(5),b(100),100);
        check(ideal.generatedEU.equals(b(10000)),"ideal 100x without old cap");
        var dilute=CriticalChemicalMath.evaluate(new BigInteger[]{b(2),b(3)},new int[]{2,3},b(10),b(100),100);
        check(dilute.efficiencyNumerator.equals(b(103))&&dilute.efficiencyDenominator.equals(b(4)),
            "half ideal fractions give 25.75x");
        var deviated=CriticalChemicalMath.evaluate(new BigInteger[]{b(2),b(8)},new int[]{2,3},b(10),b(100),100);
        check(deviated.efficiencyNumerator.equals(dilute.efficiencyNumerator)
            &&deviated.efficiencyDenominator.equals(dilute.efficiencyDenominator),"excess component reduces quality");
        BigInteger scale=BigInteger.TEN.pow(80);
        var huge=CriticalChemicalMath.evaluate(new BigInteger[]{b(2).multiply(scale),b(3).multiply(scale)},
            new int[]{2,3},b(10).multiply(scale),b(101).multiply(scale),100);
        var small=CriticalChemicalMath.evaluate(new BigInteger[]{b(2),b(3)},new int[]{2,3},b(10),b(101),100);
        check(huge.efficiencyNumerator.equals(small.efficiencyNumerator)
            &&huge.efficiencyDenominator.equals(small.efficiencyDenominator),"huge scaling preserves efficiency");
        check(huge.generatedNumerator.multiply(small.generatedDenominator)
            .equals(small.generatedNumerator.multiply(scale).multiply(huge.generatedDenominator)),
            "exact energy scales linearly beyond long");
        check(!CriticalChemicalMath.evaluate(new BigInteger[]{b(2),b(0)},new int[]{2,3},b(2),b(100),100).eligible,
            "missing component cannot start");
        check(!CriticalChemicalMath.evaluate(new BigInteger[]{b(0),b(0)},new int[]{2,3},b(0),b(0),100).eligible,
            "empty input cannot start");
        selection(fuels);
    }

    static void selection(List<CriticalGTRecipes.ChemicalFuel> fuels) {
        var diesel=GTUtility.FluidId.create(Materials.Diesel.getFluid(1));
        var light=GTUtility.FluidId.create(Materials.LightFuel.getFluid(1));
        Map<GTUtility.FluidId,BigInteger> amounts=new LinkedHashMap<>();
        amounts.put(diesel,b(1));
        check(CriticalChemicalRecipes.best(fuels,amounts)==null,"single fuel cannot start");
        amounts.put(light,b(2));
        var ideal=CriticalChemicalRecipes.best(fuels,amounts);
        check(ideal!=null&&ideal.recipe.id.equals("pair.0.1")&&ideal.rawHeat.equals(b(1090)),
            "pair uses frozen native prices");
        List<CriticalGTRecipes.ChemicalFuel> frozen=fuels.stream().filter(f->
            amounts.containsKey(GTUtility.FluidId.create(f.fluid))).toList();
        check(CriticalChemicalRecipes.best(frozen,amounts).recipe.id.equals(ideal.recipe.id),
            "receipt manifest price subset keeps same stable recipe");
        var unknown=GTUtility.FluidId.create(register("gtsr_compound_unknown"));
        amounts.put(unknown,b(3)); var diluted=CriticalChemicalRecipes.best(fuels,amounts);
        check(diluted.rawHeat.equals(ideal.rawHeat)&&diluted.totalLitres.equals(b(6))
            &&diluted.evaluation.generatedEU.compareTo(ideal.evaluation.generatedEU)<0,
            "unknown fluid adds no heat and dilutes whole volume");
        amounts.put(GTUtility.FluidId.create(Materials.Ethanol.getFluid(1)),b(5));
        var extra=CriticalChemicalRecipes.best(fuels,amounts);
        check(extra.rawHeat.equals(b(2050)),"all recognized extra fuel heat is conserved across candidates");
        FluidStack tagged=Materials.LightFuel.getFluid(1); tagged.tag=new net.minecraft.nbt.NBTTagCompound();
        tagged.tag.setString("identity","different");
        check(CriticalChemicalRecipes.best(fuels,Map.of(diesel,b(1),GTUtility.FluidId.create(tagged),b(2)))==null,
            "NBT-distinct fluid does not impersonate native component");
    }

    public static void run(String version) throws Exception {
        sun.misc.Unsafe unsafe = Issue37Runtime.bootstrap(version);
        for (Class<?> type : List.of(CriticalChemicalMath.class, CriticalChemicalRecipes.class, CompoundRecipe.class))
            System.out.println("SOURCE " + type.getSimpleName() + " " + type.getProtectionDomain().getCodeSource().getLocation());
        check(CriticalChemicalRecipes.catalog(List.of()).isEmpty(), "empty initialization not cached forever");
        String[] materials = {"Diesel","LightFuel","HeavyFuel","NitroFuel","GasolineRegular",
            "GasolinePremium","Ethanol","Methanol","Hydrogen","Methane","Benzene","NefariousGas"};
        int[] heat = {480,305,240,512,576,2500,192,84,20,104,360,1200};
        for (int i=0;i<materials.length;i++) {
            Fluid fluid=register("gtsr_compound_"+materials[i].toLowerCase(Locale.ROOT));
            Materials material=(Materials)unsafe.allocateInstance(Materials.class);
            material.mFluid=fluid; Materials.class.getField(materials[i]).set(null,material);
            var map=i>=8?gregtech.api.recipe.RecipeMaps.gasTurbineFuels:
                i==2?gregtech.api.recipe.RecipeMaps.denseLiquidFuels:gregtech.api.recipe.RecipeMaps.dieselFuels;
            map.getBackend().compileRecipe(gregtech.api.util.GTRecipeBuilder.builder()
                .fluidInputs(new FluidStack(fluid,1)).duration(20).eut(0).specialValue(heat[i]).build().orElseThrow());
        }
        String[] rockets={"RP1RocketFuel","DenseHydrazineFuelMixture","CN3H7O3RocketFuel","H8N4C2O4RocketFuel"};
        int[] rocketHeat={512,1024,2048,4196};
        var rocketMap=com.miaokatze.gtsr.api.compat.GTVersionCompat.requireGppRecipeMap("rocketFuels");
        for(int i=0;i<rockets.length;i++) {
            Fluid fluid=register("gtsr_compound_"+rockets[i].toLowerCase(Locale.ROOT));
            GTPPFluids.class.getField(rockets[i]).set(null,fluid);
            rocketMap.getBackend().compileRecipe(gregtech.api.util.GTRecipeBuilder.builder()
                .fluidInputs(new FluidStack(fluid,1)).duration(20).eut(0).specialValue(rocketHeat[i]).build().orElseThrow());
        }
        Materials ozone=(Materials)unsafe.allocateInstance(Materials.class);
        ozone.mGas=register("gtsr_compound_ozone"); Materials.Ozone=ozone;
        register("combustionpromotor");
        var fuels=CriticalGTRecipes.chemicalFuels();
        verifyCatalog(fuels);
        verifyMathAndSelection(fuels);
        System.out.println("PASS CriticalCompoundContract checks="+checks);
    }
}
