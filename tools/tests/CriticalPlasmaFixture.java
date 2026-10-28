import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import gregtech.api.enums.Materials;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;

/** Run native GG recipe derivation from rotor fuels; Sulfur deliberately has no native cold fluid. */
final class CriticalPlasmaFixture {
    static final Materials[] MATERIALS = { Materials.Nitrogen, Materials.Sulfur, Materials.Calcium,
        Materials.Nickel, Materials.Zinc, Materials.Niobium, Materials.Silver, Materials.Tin,
        Materials.Bismuth, Materials.Radon };
    static final int[] FUELS = {129024,170393,188416,213811,226304,269516,282685,150000,425984,450560};

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
            ItemStack cell=new ItemStack(Items.flint,1,30000+i);
            FluidContainerRegistry.registerFluidContainer(material.getPlasma(1000),cell,new ItemStack(Items.glass_bottle));
            RecipeMaps.plasmaFuels.addRecipe(new GTRecipe(false,new ItemStack[]{cell},null,null,null,null,null,null,
                null,null,0,0,FUELS[i]));
            // MaterialsInit.loadSulfur adds plasma and NO_SMELTING, but no fluid/gas or molten form.
            if(material==Materials.Sulfur) continue;
            Fluid cold=fluid(i==0||i==9?name:"molten."+name);
            if(i==0||i==9)material.mGas=cold;else material.mStandardMoltenFluid=cold;
        }
        goodgenerator.loader.RecipeLoader2.FinishLoadRecipe();
    }
}
