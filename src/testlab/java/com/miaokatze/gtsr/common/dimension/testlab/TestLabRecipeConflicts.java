// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.Arrays;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog;
import com.miaokatze.miaokatzecore.api.recipe.RecipeRegistration;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import io.github.miaokatze.recipeaudit.NativeRecipeAudit;

/** Consumer adapter; the reusable engine is vendored from GT-GTNH配方冲突检查 into the testlab source set. */
final class TestLabRecipeConflicts implements TestLabTask {

    static final String ID = "recipes.assembler_conflicts";
    private NativeRecipeAudit audit;

    public String id() {
        return ID;
    }

    public String displayName() {
        return "Native assembler lookup overlap audit (whole registered map)";
    }

    public int timeoutTicks() {
        return 12000;
    }

    public boolean requiresFixtureChunks() {
        return false;
    }

    public String performancePhase() {
        return "recipes.native-lookup-audit";
    }

    public void start(WorldServer world, int x, int y, int z) {
        if (!RecipeRegistration.isComplete())
            throw new IllegalStateException("Core recipe registration is not complete");
        audit = new NativeRecipeAudit(RecipeMaps.assemblerRecipes, TestLabRecipeConflicts::gtsr);
        for (String alias : CriticalShellCatalog.identities()
            .keySet()) audit.expectOutput(alias, CriticalShellCatalog.shell(alias, 16));
        regression("v2_superconducting_contact", 2);
        regression("v2_luminous_rail", 4);
    }

    private void regression(String alias, int circuit) {
        ItemStack[] old = CriticalShellCatalog.ingredients(CriticalShellCatalog.Family.E);
        old = Arrays.copyOf(old, old.length + 1);
        old[old.length - 1] = GTUtility.copyAmount(1, GTUtility.getIntegratedCircuit(circuit));
        audit.verifyReplacement(
            alias,
            CriticalShellCatalog.shell(alias, 16),
            old,
            new net.minecraftforge.fluids.FluidStack[] {
                Materials.SolderingAlloy.getMolten(CriticalShellCatalog.Family.E.solder) },
            GTOreDictUnificator
                .get(circuit == 2 ? OrePrefixes.wireGt02 : OrePrefixes.wireGt04, Materials.SuperconductorUV, 1));
    }

    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
        if (!audit.step(8_000_000L, 32)) return null;
        Map<String, Object> data = audit.report();
        data.put("targetLabel", "GTSR");
        data.put("requiredRegressions", 2);
        data.put("requireTargetRecipes", true);
        boolean pass = Boolean.TRUE.equals(data.get("collectionComplete"))
            && ((Number) data.get("targetChecked")).intValue() > 0
            && ((Number) data.get("targetConflictingProfiles")).intValue() == 0;
        return new TestLabReport.Result(
            pass ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL,
            "Native assembler audit; " + audit.progress(),
            data);
    }

    private static boolean gtsr(GTRecipe recipe) {
        if (recipe.owners != null && recipe.owners.stream()
            .anyMatch(o -> "gtsr".equals(o.getModId()))) return true;
        for (ItemStack output : recipe.mOutputs) {
            if (output == null) continue;
            String name = String.valueOf(Item.itemRegistry.getNameForObject(output.getItem()));
            if (name.startsWith("gtsr:") || name.startsWith("gto:")) return true;
            if ("gregtech:gt.blockmachines".equals(name)) {
                for (MetaTileEntityID id : MetaTileEntityID.values()) if (id.ID == output.getItemDamage()) return true;
            }
        }
        return false;
    }

    public void stop(WorldServer world) { /* No fixture or recipe mutation to restore. */ }

    public String debug(WorldServer world) {
        return audit == null ? "Not started" : audit.progress();
    }
}
