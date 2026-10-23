import java.lang.reflect.Method;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.critical.CriticalGTRecipes;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;

/** Real GT205 builder/typed metadata, isolated bootstrap; no full modpack recipe registration. */
public final class CriticalStellarNativeContract {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalStellarNativeRuntime", "5.09.54.205");
    }
}

class CriticalStellarNativeRuntime {
    private static int checks;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private static GTRecipeBuilder recipe(FluidStack input, FluidStack output) {
        return GTRecipeBuilder.builder().fluidInputs(input).fluidOutputs(output).duration(17).eut(7_864_320);
    }

    private static boolean capability(GTRecipe recipe) {
        return CriticalStellarPolicy.nativeCapability(
            recipe.getMetadataOrDefault(GTRecipeConstants.FUSION_THRESHOLD, -1L), recipe.mEUt,
            gregtech.api.enums.GTValues.V[10]);
    }

    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        String jar = System.getProperty("stellar.jar");
        for (Class<?> type : new Class<?>[] { CriticalGTRecipes.class, CriticalStellarPolicy.class }) {
            java.net.URL actual = type.getProtectionDomain().getCodeSource().getLocation();
            if (jar != null) {
                java.net.URL container = actual.getProtocol().equals("jar")
                    ? ((java.net.JarURLConnection) actual.openConnection()).getJarFileURL() : actual;
                if (!java.nio.file.Path.of(container.toURI()).toRealPath().equals(java.nio.file.Path.of(jar).toRealPath()))
                    throw new AssertionError("Product overlay: " + actual);
            }
            System.out.println("SOURCE " + type.getName() + "=" + actual);
        }
        Fluid inputFluid = new Fluid("gtsr_stellar_native_input"), outputFluid = new Fluid("gtsr_stellar_native_output");
        FluidRegistry.registerFluid(inputFluid); FluidRegistry.registerFluid(outputFluid);
        FluidStack input = new FluidStack(inputFluid, 125), output = new FluidStack(outputFluid, 125);
        GTRecipe legal = recipe(input, output).metadata(GTRecipeConstants.FUSION_THRESHOLD, 20_480_000_000L).build().orElseThrow();
        Method contract = CriticalGTRecipes.class.getDeclaredMethod("knownFusionContract", GTRecipe.class);
        contract.setAccessible(true);
        check((Boolean) contract.invoke(null, legal), "Real GT205 builder defaults satisfy the native fusion contract");
        check(capability(legal), "Typed MkV long threshold and voltage are accepted");
        check(CriticalStellarPolicy.duration(legal.mDuration) == 2, "Real native duration rounds upward by sixteen");
        GTRecipe missing = recipe(input, output).specialValue(1).build().orElseThrow();
        check(!capability(missing), "Legacy mSpecialValue does not impersonate typed threshold");
        GTRecipe over = recipe(input, output).metadata(GTRecipeConstants.FUSION_THRESHOLD, 20_480_000_001L).build().orElseThrow();
        check(!capability(over), "Above-MkV startup capability is refused");
        GTRecipe voltage = recipe(input, output).eut(gregtech.api.enums.TierEU.RECIPE_UIV)
            .metadata(GTRecipeConstants.FUSION_THRESHOLD, 1L).build().orElseThrow();
        check(!capability(voltage), "Above-MkV native voltage is refused independently of startup");
        var unknown = gregtech.api.recipe.metadata.SimpleRecipeMetadataKey.create(Integer.class, "gtsr_stellar_unknown_contract");
        GTRecipe extended = recipe(input, output).metadata(GTRecipeConstants.FUSION_THRESHOLD, 1L)
            .metadata(unknown, 1).build().orElseThrow();
        check(!(Boolean) contract.invoke(null, extended), "Unknown metadata is refused without widening native capability");
        Method fingerprint = CriticalGTRecipes.class.getDeclaredMethod("fusionFingerprint", GTRecipe.class, long.class);
        fingerprint.setAccessible(true);
        input.tag = new NBTTagCompound(); input.tag.setInteger("Aa", 1); input.tag.setInteger("BB", 2);
        GTRecipe taggedA = recipe(input.copy(), output).metadata(GTRecipeConstants.FUSION_THRESHOLD, 1L).build().orElseThrow();
        input.tag = new NBTTagCompound(); input.tag.setInteger("BB", 2); input.tag.setInteger("Aa", 1);
        GTRecipe taggedB = recipe(input.copy(), output).metadata(GTRecipeConstants.FUSION_THRESHOLD, 1L).build().orElseThrow();
        check(fingerprint.invoke(null, taggedA, 1L).equals(fingerprint.invoke(null, taggedB, 1L)),
            "Recipe fingerprint ignores compound insertion order, including colliding NBT keys");
        input.tag.setInteger("Aa", 3);
        GTRecipe taggedC = recipe(input.copy(), output).metadata(GTRecipeConstants.FUSION_THRESHOLD, 1L).build().orElseThrow();
        check(!fingerprint.invoke(null, taggedA, 1L).equals(fingerprint.invoke(null, taggedC, 1L)),
            "Recipe fingerprint distinguishes actual NBT identity");
        System.out.println("PASS CriticalStellarNativeContract checks=" + checks);
    }
}
