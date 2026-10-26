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
    static Map<GTUtility.FluidId,BigInteger> idealInput(CompoundRecipe recipe) {
        Map<GTUtility.FluidId,BigInteger> input=new LinkedHashMap<>();
        for(var part:recipe.components) input.put(GTUtility.FluidId.create(part.fuel.fluid),b(part.ratio));
        return input;
    }
    static BigInteger raw(List<CriticalGTRecipes.ChemicalFuel> fuels,Map<GTUtility.FluidId,BigInteger> input) {
        BigInteger value=b(0);
        for(var fuel:fuels) value=value.add(input.getOrDefault(GTUtility.FluidId.create(fuel.fluid),b(0)).multiply(fuel.euPerL));
        return value;
    }
    static void verifyCatalog(List<CriticalGTRecipes.ChemicalFuel> fuels) {
        var catalog=CriticalChemicalRecipes.catalog(fuels);
        check(catalog.size()==1024,"1024 distinct fixed combinations");
        check(catalog==CriticalChemicalRecipes.catalog(fuels),"immutable catalogue cached");
        Set<String> ids=new HashSet<>(),identities=new HashSet<>();
        int[] counts=new int[10],minPeak=new int[10],maxPeak=new int[10]; Arrays.fill(minPeak,101);
        int[] minimum={2,13,25,37,49,61,73,87},maximum={12,24,36,48,60,72,86,100};
        Fluid[] gates={Materials.GasolineRegular.getFluid(1).getFluid(),Materials.GasolinePremium.getFluid(1).getFluid(),
            GTPPFluids.RP1RocketFuel,GTPPFluids.DenseHydrazineFuelMixture,GTPPFluids.CN3H7O3RocketFuel,
            GTPPFluids.H8N4C2O4RocketFuel,FluidRegistry.getFluid("combustionpromotor")};
        for(var recipe:catalog) {
            check(ids.add(recipe.id),"stable ID unique "+recipe.id);
            int size=recipe.components.size(); counts[size]++;
            check(recipe.selectorCircuit==size,"selector exactly identifies complexity "+recipe.id);
            minPeak[size]=Math.min(minPeak[size],recipe.peakMultiplier);
            maxPeak[size]=Math.max(maxPeak[size],recipe.peakMultiplier);
            check(CriticalChemicalRecipes.byId(recipe.id)==recipe,"indexed identity "+recipe.id);
            List<String> names=new ArrayList<>();
            for(var part:recipe.components) names.add(part.fuel.fluid.getFluid().getName());
            Collections.sort(names);
            check(identities.add(String.join("+",names)),"fluid combination unique "+recipe.id);
            check(recipe.id.startsWith("reaction."),"new directory never reuses legacy IDs "+recipe.id);
            if(size>=3) check(recipe.components.get(0).fuel.fluid.getFluid()==gates[Math.min(size-3,5)],
                "single industrial acquisition gate "+recipe.id);
            if(size==9) check(recipe.components.get(1).fuel.fluid.getFluid()==gates[6],"final separate promoter gate");
            var input=idealInput(recipe); var match=CriticalChemicalRecipes.best(fuels,input,size);
            check(match!=null&&match.recipe.id.equals(recipe.id)&&match.evaluation.generatedEU.equals(raw(fuels,input).multiply(b(recipe.peakMultiplier))),
                "ideal selects its exact circuit group "+recipe.id);
            check(CriticalChemicalRecipes.matchExact(fuels,input,size,recipe.id)!=null,"frozen exact identity "+recipe.id);
            check(CriticalChemicalRecipes.matchExact(fuels,input,size==9?8:size+1,recipe.id)==null,"exact cannot cross group "+recipe.id);
            if(size<9) check(CriticalChemicalRecipes.best(fuels,input,size+1)==null,"higher selector cannot fall back "+recipe.id);
            input.remove(GTUtility.FluidId.create(recipe.components.get(size-1).fuel.fluid));
            check(CriticalChemicalRecipes.matchExact(fuels,input,size,recipe.id)==null,"every component required "+recipe.id);
        }
        check(Arrays.equals(counts,new int[]{0,0,128,128,128,128,128,128,128,128}),"exact circuit distribution");
        for(int size=2;size<=9;size++) check(minPeak[size]==minimum[size-2]&&maxPeak[size]==maximum[size-2],
            "strict complete complexity reward band "+size);
        var reversed=new ArrayList<>(fuels);Collections.reverse(reversed);
        check(CriticalChemicalRecipes.catalog(reversed)==catalog,"native registration order cannot change catalogue");
        try {catalog.clear();throw new AssertionError("mutable catalogue");}
        catch(UnsupportedOperationException expected){checks++;}
        verifyDiversity(catalog);
        verifyLegacy(fuels,2,256);verifyLegacy(fuels,3,512);verifyLegacy(fuels,4,1024);
        check(CriticalChemicalRecipes.byId("pair.0.1")!=null,"legacy in-flight receipt retains display labels");
        System.out.println("CATALOG count="+catalog.size()+" complexity="+Arrays.toString(counts)
            +" minPeak="+Arrays.toString(minPeak)+" maxPeak="+Arrays.toString(maxPeak));
    }
    static void verifyLegacy(List<CriticalGTRecipes.ChemicalFuel> fuels,int version,int count) {
        var catalog=version==2?CriticalChemicalRecipes.catalogLegacyV2(fuels):version==3?CriticalChemicalRecipes.catalogLegacyV3(fuels):CriticalChemicalRecipes.catalogLegacyV4(fuels);
        check(catalog.size()==count,"legacy frozen count "+version);
        for(var recipe:catalog) {
            var input=idealInput(recipe);
            var match=version==2?CriticalChemicalRecipes.bestLegacyV2(fuels,input):version==3?CriticalChemicalRecipes.bestLegacyV3(fuels,input):
                CriticalChemicalRecipes.matchExactLegacyV4(fuels,input,recipe.selectorCircuit,recipe.id);
            check(match!=null&&match.recipe.id.equals(recipe.id)
                &&match.evaluation.generatedEU.equals(raw(fuels,input).multiply(b(recipe.peakMultiplier))),
                "frozen legacy ideal "+version+" "+recipe.id);
            if(version==2) check(recipe.peakMultiplier==legacyPeak(recipe.id),"original legacy2 multiplier "+recipe.id);
            if(version==4) verifyLegacyV4Recipe(fuels,recipe,input);

        }
    }
    static Fluid originalMaterial(String field) {
        try {return ((Materials)Materials.class.getField(field).get(null)).getFluid(1).getFluid();}
        catch(Exception e){throw new AssertionError(e);}
    }
    static Fluid originalRocket(String field) {
        try {return (Fluid)GTPPFluids.class.getField(field).get(null);}catch(Exception e){throw new AssertionError(e);}
    }
    static void verifyLegacyV4Recipe(List<CriticalGTRecipes.ChemicalFuel> fuels,CompoundRecipe recipe,
        Map<GTUtility.FluidId,BigInteger> input) {
        check(CriticalChemicalRecipes.matchExact(fuels,input,recipe.selectorCircuit,recipe.id)==null,"new catalog rejects old V4 identity");
        check(CriticalChemicalRecipes.byId(recipe.id)!=null,"V4 receipt retains label "+recipe.id);
        String[] fields=recipe.id.split("\\.");int size=Integer.parseInt(fields[1]),first=Integer.parseInt(fields[2]),second=Integer.parseInt(fields[3]);
        check(recipe.components.get(0).ratio==4+first%3&&recipe.components.get(1).ratio==2+second%3,"V4 original ratios "+recipe.id);
        String[] base={"Diesel","LightFuel","HeavyFuel","NitroFuel","Ethanol","Methanol","Hydrogen","Methane","Benzene",
            "Ethylene","Propene","Butene","Butadiene","Ethane","Propane","Butane","Toluene"};
        check(recipe.components.get(0).fuel.fluid.getFluid()==originalMaterial(base[first])
            &&recipe.components.get(1).fuel.fluid.getFluid()==originalMaterial(base[second]),"V4 original two feedstocks "+recipe.id);
        int variant=first*(33-first)/2+second-first-1;
        int[] minimum={2,13,25,37,49,61,73,87},maximum={12,24,36,48,60,72,86,100};
        check(recipe.peakMultiplier==minimum[size-2]+variant%(maximum[size-2]-minimum[size-2]+1),"V4 original reward "+recipe.id);
        Fluid[] chain={originalMaterial("GasolineRegular"),originalMaterial("GasolinePremium"),originalRocket("RP1RocketFuel"),
            originalRocket("DenseHydrazineFuelMixture"),originalRocket("CN3H7O3RocketFuel"),originalRocket("H8N4C2O4RocketFuel"),
            FluidRegistry.getFluid("combustionpromotor")};
        for(int i=2;i<size;i++)check(recipe.components.get(i).fuel.fluid.getFluid()==chain[i-2]
            &&recipe.components.get(i).ratio==(i<5?3:1),"V4 original fixed tail "+recipe.id);
    }

    static void verifyDiversity(List<CompoundRecipe> catalog) {
        for(int size=2;size<=9;size++) {
            int fixed=size==2?0:size==9?2:1;
            Set<GTUtility.FluidId> coverage=new HashSet<>();
            Set<Set<GTUtility.FluidId>> subsets=new HashSet<>();
            List<Set<GTUtility.FluidId>> slots=new ArrayList<>();
            Map<GTUtility.FluidId,Integer> usage=new HashMap<>();
            for(int i=fixed;i<size;i++)slots.add(new HashSet<>());
            Set<GTUtility.FluidId> intersection=null;
            for(var recipe:catalog) if(recipe.selectorCircuit==size) {
                Set<GTUtility.FluidId> subset=new HashSet<>();
                for(int i=fixed;i<size;i++) {
                    var id=GTUtility.FluidId.create(recipe.components.get(i).fuel.fluid);
                    subset.add(id);slots.get(i-fixed).add(id);coverage.add(id);
                    usage.put(id,usage.getOrDefault(id,0)+1);
                }
                check(subsets.add(subset),"real set after removing at most two gates is unique "+recipe.id);
                if(intersection==null)intersection=new HashSet<>(subset);else intersection.retainAll(subset);
            }
            int expectedPool=size<4?17:size==9?23:15+size;
            check(subsets.size()==128,"128 actual variable subsets, never order aliases "+size);
            check(coverage.size()==expectedPool,"entire eligible intermediate/advanced pool covered "+size);
            check(intersection.isEmpty(),"no additional fixed tail hidden behind acquisition gates "+size);
            for(var slot:slots)check(slot.size()>=10,"each actual variable slot has at least ten real identities "+size);
            int min=Collections.min(usage.values()),max=Collections.max(usage.values());
            check(min>=128*(size-fixed)/expectedPool/2&&max<=2*((128*(size-fixed)+expectedPool-1)/expectedPool),
                "stratified material usage stays balanced "+size);
            System.out.println("DIVERSITY circuit="+size+" gates="+fixed+" variableSets="+subsets.size()
                +" pool="+coverage.size()+" slotChoices="+slots.stream().map(Set::size).collect(java.util.stream.Collectors.toList())
                +" useRange="+min+".."+max);
        }
    }

    static int legacyPeak(String id) {
        String[] fields=id.split("\\.");int first=Integer.parseInt(fields[1]);
        if(fields[0].equals("pair")) {int second=Integer.parseInt(fields[2]);
            return 12+(first/8==second/8?0:12)+(second>=12?12:0)+(first%4)*3+second%4;}
        if(fields[0].equals("blend"))return 48+first*3+Integer.parseInt(fields[2])*4+Integer.parseInt(fields[3])*5;
        return fields[0].equals("promoted")?94+first*2:88+first*3;
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
        for(int size=2;size<=9;size++) {
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
        try {CriticalChemicalMath.evaluate(new BigInteger[10],new int[10],b(1),b(1),100);
            throw new AssertionError("ten components accepted");} catch(IllegalArgumentException expected){checks++;}
        selection(fuels);
    }

    static void selection(List<CriticalGTRecipes.ChemicalFuel> fuels) {
        var recipe=CriticalChemicalRecipes.byId("reaction.2.0.1");var amounts=idealInput(recipe);
        var ideal=CriticalChemicalRecipes.best(fuels,amounts,2);
        check(ideal!=null&&ideal.rawHeat.equals(raw(fuels,amounts)),"native price heat is exact");
        check(CriticalChemicalRecipes.best(fuels,amounts,0)==null&&CriticalChemicalRecipes.best(fuels,amounts,10)==null,
            "missing and invalid selector never start");
        check(CriticalChemicalRecipes.matchExact(fuels,amounts,2,"missing")==null,"unknown frozen identity rejected");
        List<CriticalGTRecipes.ChemicalFuel> frozen=new ArrayList<>();
        for(var fuel:fuels) if(amounts.containsKey(GTUtility.FluidId.create(fuel.fluid)))frozen.add(fuel);
        check(CriticalChemicalRecipes.matchExact(frozen,amounts,2,recipe.id).evaluation.generatedEU.equals(ideal.evaluation.generatedEU),
            "receipt price subset preserves exact selected identity");
        var unknown=GTUtility.FluidId.create(register("gtsr_compound_unknown"));amounts.put(unknown,b(7));
        var diluted=CriticalChemicalRecipes.best(fuels,amounts,2);
        check(diluted.rawHeat.equals(ideal.rawHeat)&&diluted.totalLitres.equals(ideal.totalLitres.add(b(7)))
            &&diluted.evaluation.generatedEU.compareTo(ideal.evaluation.generatedEU)<0,"whole warehouse unknown dilution");
        amounts.put(GTUtility.FluidId.create(Materials.Ethanol.getFluid(1)),b(5));
        check(CriticalChemicalRecipes.best(fuels,amounts,2).rawHeat.equals(ideal.rawHeat.add(b(960))),"all recognized extra heat conserved");
        var extra=CriticalChemicalRecipes.best(fuels,amounts,2);var exactBest=CriticalChemicalRecipes.matchExact(fuels,amounts,2,extra.recipe.id);
        check(extra.evaluation.generatedNumerator.equals(exactBest.evaluation.generatedNumerator)
            &&extra.evaluation.generatedDenominator.equals(exactBest.evaluation.generatedDenominator),"selected rational equals exact receipt rational");
        Map<GTUtility.FluidId,BigInteger> roundedTie=new LinkedHashMap<>();
        for(var candidate:CriticalChemicalRecipes.catalog(fuels))if(candidate.selectorCircuit==2)
            for(var part:candidate.components)roundedTie.put(GTUtility.FluidId.create(part.fuel.fluid),b(7));
        roundedTie.put(unknown,BigInteger.TEN.pow(60));
        CriticalChemicalRecipes.Match rationalBest=null;
        boolean equalFloorDistinctRational=false;
        for(var candidate:CriticalChemicalRecipes.catalog(fuels))if(candidate.selectorCircuit==2) {
            var value=CriticalChemicalRecipes.matchExact(fuels,roundedTie,2,candidate.id);
            if(rationalBest!=null&&value.evaluation.generatedEU.equals(rationalBest.evaluation.generatedEU)
                &&value.evaluation.generatedNumerator.multiply(rationalBest.evaluation.generatedDenominator).compareTo(
                    rationalBest.evaluation.generatedNumerator.multiply(value.evaluation.generatedDenominator))!=0) equalFloorDistinctRational=true;
            if(rationalBest==null||value.evaluation.generatedNumerator.multiply(rationalBest.evaluation.generatedDenominator).compareTo(
                rationalBest.evaluation.generatedNumerator.multiply(value.evaluation.generatedDenominator))>0)rationalBest=value;
        }
        check(equalFloorDistinctRational,"candidate fixture shares rounded output with different exact rationals");
        check(CriticalChemicalRecipes.best(fuels,roundedTie,2).recipe.id.equals(rationalBest.recipe.id),"selection compares exact rationals before final floor");
        check(CriticalChemicalRecipes.matchExact(fuels,roundedTie,2,recipe.id).recipe.id.equals(recipe.id),"frozen identity never reselects");
        List<CriticalGTRecipes.ChemicalFuel> legacyOnly=new ArrayList<>();
        for(var fuel:fuels) if(fuel.fluid.isFluidEqual(Materials.GasolineRegular.getFluid(1))
            ||fuel.fluid.isFluidEqual(Materials.GasolinePremium.getFluid(1)))legacyOnly.add(fuel);
        Map<GTUtility.FluidId,BigInteger> oldOnly=new LinkedHashMap<>();
        oldOnly.put(GTUtility.FluidId.create(Materials.GasolineRegular.getFluid(1)),b(1));
        oldOnly.put(GTUtility.FluidId.create(Materials.GasolinePremium.getFluid(1)),b(3));
        var legacy4=CriticalChemicalRecipes.catalogLegacyV4(fuels).get(1023);
        var legacy4Input=idealInput(legacy4);
        List<CriticalGTRecipes.ChemicalFuel> legacy4Prices=new ArrayList<>();
        for(var fuel:fuels)if(legacy4Input.containsKey(GTUtility.FluidId.create(fuel.fluid)))legacy4Prices.add(fuel);
        check(CriticalChemicalRecipes.matchExactLegacyV4(legacy4Prices,legacy4Input,9,legacy4.id).evaluation.generatedEU.equals(
            raw(fuels,legacy4Input).multiply(b(legacy4.peakMultiplier))),"V4 receipt price subset preserves frozen nine-fluid reaction");
        check(CriticalChemicalRecipes.matchExactLegacyV4(legacy4Prices,legacy4Input,8,legacy4.id)==null,"V4 exact cannot cross selector");
        check(CriticalChemicalRecipes.catalog(legacyOnly).isEmpty(),"legacy receipt may have no new catalogue candidates");
        check(CriticalChemicalRecipes.bestLegacyV2(legacyOnly,oldOnly).recipe.id.equals("pair.4.5")
            &&CriticalChemicalRecipes.bestLegacyV3(legacyOnly,oldOnly).recipe.id.equals("pair.4.5"),
            "empty new catalogue cannot reuse stale legacy prices or lose old receipt");
        check(CriticalChemicalRecipes.catalog(fuels).size()==1024&&CriticalChemicalRecipes.catalogLegacyV2(fuels).size()==256
            &&CriticalChemicalRecipes.catalogLegacyV3(fuels).size()==512&&CriticalChemicalRecipes.catalogLegacyV4(fuels).size()==1024,"full to legacy-only to full signature restores all three catalogues");
        check(CriticalChemicalRecipes.catalog(Collections.emptyList()).isEmpty()
            &&CriticalChemicalRecipes.catalogLegacyV3(Collections.emptyList()).isEmpty()&&CriticalChemicalRecipes.catalogLegacyV4(Collections.emptyList()).isEmpty(),"empty manifest has no stale legacy catalogue");
        check(CriticalChemicalRecipes.catalog(fuels).size()==1024&&CriticalChemicalRecipes.catalogLegacyV2(fuels).size()==256
            &&CriticalChemicalRecipes.catalogLegacyV3(fuels).size()==512&&CriticalChemicalRecipes.catalogLegacyV4(fuels).size()==1024,"empty to full signature restores all three catalogues");
        check(CriticalChemicalRecipes.byId(recipe.id).id.equals(recipe.id)&&CriticalChemicalRecipes.byId("pair.0.1")!=null,
            "subset and empty pricing never erase full display labels");
        FluidStack tagged=Materials.LightFuel.getFluid(1);tagged.tag=new net.minecraft.nbt.NBTTagCompound();tagged.tag.setString("identity","different");
        Map<GTUtility.FluidId,BigInteger> taggedInput=idealInput(recipe);taggedInput.remove(GTUtility.FluidId.create(Materials.LightFuel.getFluid(1)));
        taggedInput.put(GTUtility.FluidId.create(tagged),b(3));
        check(CriticalChemicalRecipes.best(fuels,taggedInput,2)==null,"NBT-distinct fluid cannot impersonate component");
    }

    public static void run(String version) throws Exception {
        sun.misc.Unsafe unsafe = Issue37Runtime.bootstrap(version);
        for (Class<?> type : List.of(CriticalChemicalMath.class, CriticalChemicalRecipes.class, CompoundRecipe.class))
            System.out.println("SOURCE " + type.getSimpleName() + " " + type.getProtectionDomain().getCodeSource().getLocation());
        check(CriticalChemicalRecipes.catalog(List.of()).isEmpty(), "empty initialization not cached forever");
        String[] materials = {"Diesel","LightFuel","HeavyFuel","NitroFuel","GasolineRegular",
            "GasolinePremium","Ethanol","Methanol","Hydrogen","Methane","Benzene","NefariousGas",
            "Ethylene","Propene","Butene","Butadiene","Ethane","Propane","Butane","Toluene"};
        int[] heat = {480,305,240,1000,576,2500,192,84,20,104,360,1200,128,192,256,206,168,232,296,328};
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
