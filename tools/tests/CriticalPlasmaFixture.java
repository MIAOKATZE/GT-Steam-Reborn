import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Materials;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;

/** Registered fusion/fuel fixtures, including GT++ style fluids; GG derives their exchanger recipes. */
final class CriticalPlasmaFixture {
    static final Materials[] MATERIALS = { Materials.Nitrogen, Materials.Sulfur, Materials.Calcium,
        Materials.Nickel, Materials.Zinc, Materials.Niobium, Materials.Silver, Materials.Tin,
        Materials.Bismuth, Materials.Radon };
    static final int[] FUELS = {129024,170393,188416,213811,226304,269516,282685,150000,425984,450560};
    static int cellIndex;

    static Fluid fluid(String name) {
        Fluid value=FluidRegistry.getFluid(name);
        if(value==null){value=new Fluid(name);FluidRegistry.registerFluid(value);}
        return value;
    }

    static void seed() {
        fluid("ic2distilledwater");fluid("ic2superheatedsteam");
        Materials.DenseSupercriticalSteam.mGas=fluid("densesupercriticalsteam");
        for(int i=0;i<MATERIALS.length;i++) {
            Materials material=MATERIALS[i];
            String name=material.mName.toLowerCase(java.util.Locale.ROOT);
            material.mPlasma=fluid("plasma."+name);
            fuel(material.mPlasma,FUELS[i]);
            fusion(false,false,material.getPlasma(1));
            // MaterialsInit.loadSulfur adds plasma and NO_SMELTING, but no fluid/gas or molten form.
            if(material==Materials.Sulfur) continue;
            Fluid cold=fluid(i==0||i==9?name:"molten."+name);
            if(i==0||i==9)material.mGas=cold;else material.mStandardMoltenFluid=cold;
        }
        for(Materials material:new Materials[]{Materials.Lead,Materials.Thorium,Materials.Plutonium241}) {
            String name=material.mName.toLowerCase(java.util.Locale.ROOT);
            material.mPlasma=fluid("plasma."+name);material.mStandardMoltenFluid=fluid("molten."+name);
            fuel(material.mPlasma,300000);fusion(false,false,material.getPlasma(1));
        }
        Fluid celestial=generic("celestialtungsten","Celestial Tungsten Plasma",720000);
        fusion(false,false,new FluidStack(celestial,1));
        fusion(false,false,new FluidStack(celestial,2)); // More than one fusion path, one catalogue entry.
        Fluid second=generic("fixturesecondoutput","Second Fusion Output Plasma",200000);
        fusion(false,false,new FluidStack(fluid("fixture_non_plasma_output"),1),new FluidStack(second,1));
        generic("fixturegodforgeonly","Godforge-only Plasma",300000); // Fuel + EHE, never a fusion output.
        fusion(true,false,new FluidStack(generic("fixturedisabledfusion","Disabled Fusion Plasma",300000),1));
        fusion(false,true,new FluidStack(generic("fixturefakefusion","Fake Fusion Plasma",300000),1));
        for(Materials material:new Materials[]{Materials.Helium,Materials.Iron,Materials.Americium}) {
            if(material.mPlasma==null)material.mPlasma=fluid("plasma."+material.mName.toLowerCase(java.util.Locale.ROOT));
            fuel(material.mPlasma,200000);fusion(false,false,material.getPlasma(1));
        }
        goodgenerator.loader.RecipeLoader2.FinishLoadRecipe();
        // Exclusion must work even when all three maps contain these fluids. Energy fixtures can use custom ids.
        for(Materials material:new Materials[]{Materials.Helium,Materials.Iron,Materials.Americium}) {
            goodgenerator.util.MyRecipeAdder.instance.addExtremeHeatExchangerRecipe(material.getPlasma(2500),
                new FluidStack(fluid("fixture_cold_"+material.mName.toLowerCase(java.util.Locale.ROOT)),2500),
                new FluidStack(fluid("ic2distilledwater"),100000),new FluidStack(fluid("ic2superheatedsteam"),0),
                Materials.DenseSupercriticalSteam.getGas(16000),1);
        }
    }

    static void fuel(Fluid plasma,int value) {
        ItemStack cell=new ItemStack(Items.flint,1,30000+cellIndex++);
        FluidContainerRegistry.registerFluidContainer(new FluidStack(plasma,1000),cell,new ItemStack(Items.glass_bottle));
        RecipeMaps.plasmaFuels.addRecipe(new GTRecipe(false,new ItemStack[]{cell},null,null,null,null,null,null,
            null,null,0,0,value));
    }

    static Fluid generic(String name,final String label,int value) {
        Fluid plasma=new Fluid("plasma."+name) {
            @Override public String getLocalizedName(FluidStack stack) { return label; }
        };
        FluidRegistry.registerFluid(plasma);fluid("molten."+name);fuel(plasma,value);return plasma;
    }

    static void fusion(boolean disabled,boolean fake,FluidStack... outputs) {
        GTRecipe recipe=GTRecipeBuilder.builder().fluidInputs(new FluidStack(fluid("fixture_fusion_input"),1),
                new FluidStack(fluid("fixture_fusion_input_second"),1))
            .fluidOutputs(outputs).duration(1).eut(Integer.MAX_VALUE)
            .metadata(GTRecipeConstants.FUSION_THRESHOLD,Long.MAX_VALUE).build().orElseThrow();
        recipe.mEnabled=!disabled;RecipeMaps.fusionRecipes.addRecipe(recipe,false,fake,false);
    }

    static java.util.Set<String> eligibleIds() {
        return new java.util.HashSet<>(java.util.Arrays.asList("plasma.nitrogen","plasma.calcium","plasma.nickel",
            "plasma.zinc","plasma.niobium","plasma.silver","plasma.tin","plasma.bismuth","plasma.radon",
            "plasma.lead","plasma.thorium","plasma.plutonium241","plasma.celestialtungsten","plasma.fixturesecondoutput"));
    }
}
