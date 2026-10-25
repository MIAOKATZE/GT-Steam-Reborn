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
        check(catalog.size()==512,"512 distinct fixed combinations");
        check(catalog==CriticalChemicalRecipes.catalog(fuels),"immutable catalogue cached");
        Set<String> ids=new HashSet<>(),identities=new HashSet<>(); Set<Integer> peaks=new HashSet<>();
        int[] counts=new int[7], minPeak=new int[7], maxPeak=new int[7]; Arrays.fill(minPeak,101);
        for(var recipe:catalog) {
            check(ids.add(recipe.id),"stable ID unique "+recipe.id);
            int size=recipe.components.size(); counts[size]++;
            minPeak[size]=Math.min(minPeak[size],recipe.peakMultiplier);
            maxPeak[size]=Math.max(maxPeak[size],recipe.peakMultiplier);
            check(CriticalChemicalRecipes.byId(recipe.id)==recipe,"indexed identity "+recipe.id);
            List<String> names=new ArrayList<>(); BigInteger raw=b(0),total=b(0);
            Map<GTUtility.FluidId,BigInteger> input=new LinkedHashMap<>();
            BigInteger[] amounts=new BigInteger[recipe.components.size()]; int[] ratios=new int[amounts.length];
            for(int i=0;i<amounts.length;i++) {
                var part=recipe.components.get(i); names.add(part.fuel.fluid.getFluid().getName());
                amounts[i]=b(part.ratio); ratios[i]=part.ratio;
                input.put(GTUtility.FluidId.create(part.fuel.fluid),amounts[i]);
                total=total.add(amounts[i]); raw=raw.add(amounts[i].multiply(part.fuel.euPerL));
            }
            Collections.sort(names);
            check(identities.add(String.join("+",names)),"fluid identity combination unique "+recipe.id);
            var value=CriticalChemicalMath.evaluate(amounts,ratios,total,raw,recipe.peakMultiplier);
            check(value.eligible&&value.generatedEU.equals(raw.multiply(b(recipe.peakMultiplier)))
                &&value.efficiencyHundredths()==100*recipe.peakMultiplier,"NEI ideal agrees "+recipe.id);
            var match=CriticalChemicalRecipes.best(fuels,input);
            check(match!=null&&match.recipe.id.equals(recipe.id)&&match.evaluation.generatedEU.equals(value.generatedEU),
                "ideal selection preserves complexity reward "+recipe.id);
            if(size<=3) {
                var legacy=CriticalChemicalRecipes.bestLegacyV2(fuels,input);
                check(legacy!=null&&legacy.recipe.id.equals(recipe.id)&&legacy.recipe.peakMultiplier==legacyPeak(recipe.id)
                    &&legacy.evaluation.generatedEU.equals(raw.multiply(b(legacyPeak(recipe.id)))),
                    "schema-2 frozen ideal reward "+recipe.id);
                check(CriticalChemicalRecipes.byId(recipe.id)==recipe,"legacy lookup cannot replace new display "+recipe.id);
            }
            peaks.add(recipe.peakMultiplier);
        }
        check(Arrays.equals(counts,new int[]{0,0,120,136,96,80,80}),"exact complexity distribution");
        for(int size=2;size<=6;size++) {
            check(minPeak[size]==(size==2?2:(size-2)*20+1)&&maxPeak[size]==(size-1)*20,
                "complete complexity peak band "+size);
            if(size>2) check(minPeak[size]>maxPeak[size-1],"strictly increasing complexity reward "+size);
        }
        verifyLegacyIdentities();
        System.out.println("CATALOG count="+catalog.size()+" complexity="+Arrays.toString(counts)
            +" minPeak="+Arrays.toString(minPeak)+" maxPeak="+Arrays.toString(maxPeak));
        check(peaks.size()>25&&Collections.max(peaks)==100,"meaningful peak range with 100x ceiling");
        check(CriticalChemicalRecipes.byId("promoted.3")!=null,"cached localization lookup");
        var reversed=new ArrayList<>(fuels); Collections.reverse(reversed);
        check(CriticalChemicalRecipes.catalog(reversed)==catalog,"native registration order cannot change catalogue");
        try { catalog.clear(); throw new AssertionError("mutable catalogue"); }
        catch(UnsupportedOperationException expected) { checks++; }
    }

    static int legacyPeak(String id) {
        String[] fields=id.split("\\.");
        int first=Integer.parseInt(fields[1]);
        if(fields[0].equals("pair")) {
            int second=Integer.parseInt(fields[2]);
            return 12+(first/8==second/8?0:12)+(second>=12?12:0)+(first%4)*3+second%4;
        }
        if(fields[0].equals("blend")) return 48+first*3+Integer.parseInt(fields[2])*4+Integer.parseInt(fields[3])*5;
        return fields[0].equals("promoted")?94+first*2:88+first*3;
    }

    static void verifyLegacyIdentities() {
        for(int i=0;i<16;i++) for(int j=i+1;j<16;j++) {
            var recipe=CriticalChemicalRecipes.byId("pair."+i+"."+j);
            check(recipe!=null&&recipe.components.size()==2&&recipe.components.get(0).ratio==1+i%4
                &&recipe.components.get(1).ratio==1+j%3,"legacy pair ID and ratios");
        }
        for(int liquid=0;liquid<8;liquid++) for(int gas=0;gas<4;gas++) for(int rocket=0;rocket<4;rocket++) {
            var recipe=CriticalChemicalRecipes.byId("blend."+liquid+"."+gas+"."+rocket);
            check(recipe!=null&&recipe.components.size()==3&&recipe.components.get(0).ratio==4+liquid%3
                &&recipe.components.get(1).ratio==2+gas&&recipe.components.get(2).ratio==1+rocket,
                "legacy blend ID and ratios");
        }
        for(int rocket=0;rocket<4;rocket++) {
            var promoted=CriticalChemicalRecipes.byId("promoted."+rocket);
            var ozonated=CriticalChemicalRecipes.byId("ozonated."+rocket);
            check(promoted!=null&&promoted.components.get(0).ratio==8&&promoted.components.get(1).ratio==2+rocket
                &&promoted.components.get(2).ratio==1,"legacy promoted ID and ratios");
            check(ozonated!=null&&ozonated.components.get(0).ratio==6&&ozonated.components.get(1).ratio==2+rocket
                &&ozonated.components.get(2).ratio==1,"legacy ozonated ID and ratios");
        }
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
        for(int size=2;size<=6;size++) {
            BigInteger[] amounts=new BigInteger[size],scaled=new BigInteger[size]; int[] ratios=new int[size];
            BigInteger total=b(0);
            for(int i=0;i<size;i++) {ratios[i]=i+1;amounts[i]=b(i+1);scaled[i]=amounts[i].multiply(scale);total=total.add(amounts[i]);}
            var exact=CriticalChemicalMath.evaluate(amounts,ratios,total,b(101),100);
            var diluted=CriticalChemicalMath.evaluate(amounts,ratios,total.multiply(b(2)),b(101),100);
            var big=CriticalChemicalMath.evaluate(scaled,ratios,total.multiply(b(2)).multiply(scale),b(101).multiply(scale),100);
            check(exact.eligible&&exact.generatedEU.equals(b(10100)),"ideal any complexity "+size);
            check(diluted.qualityNumerator.equals(b(1))&&diluted.qualityDenominator.equals(b(2))
                &&diluted.generatedEU.equals(b(2600)),"dilution floors only final rational "+size);
            check(big.generatedNumerator.multiply(diluted.generatedDenominator).equals(
                diluted.generatedNumerator.multiply(scale).multiply(big.generatedDenominator)),"unbounded scaling "+size);
            amounts[size-1]=b(0);
            check(!CriticalChemicalMath.evaluate(amounts,ratios,total,b(101),100).eligible,"all components required "+size);
        }
        try {CriticalChemicalMath.evaluate(new BigInteger[7],new int[7],b(1),b(1),100);
            throw new AssertionError("seven components accepted");} catch(IllegalArgumentException expected){checks++;}
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
        var legacy=CriticalChemicalRecipes.bestLegacyV2(frozen,amounts);
        check(legacy.recipe.id.equals("pair.0.1")&&legacy.recipe.peakMultiplier==13
            &&legacy.evaluation.generatedEU.equals(b(14170)),"schema-2 price subset keeps its original 13x reward");
        check(CriticalChemicalRecipes.byId("pair.0.1").peakMultiplier==3,"legacy price subset never reprices display");
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
