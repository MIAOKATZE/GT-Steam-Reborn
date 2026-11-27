// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.loader.recipes.ClusterRecipes;
import com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.recipe.RecipeMaps;

/** Isolated native recipe and planner checks; no server, client or world is created. */
public final class CenterQueueMigrationContracts {

    private static Method fixtureItem;
    private static int checks;

    private CenterQueueMigrationContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, CenterQueueMigrationContracts.class.getName(), args)) return;
        Method nativeSetup = PortableWeaponRecipeContracts.class.getDeclaredMethod("bootstrapGt");
        nativeSetup.setAccessible(true);
        nativeSetup.invoke(null);
        fixtureItem = PortableWeaponRecipeContracts.class.getDeclaredMethod("item", String.class);
        fixtureItem.setAccessible(true);
        check(MetaTileEntityID.ORE_INTEGRATION_CENTER.ID == 14858, "center uses work-machine ID 14858");
        check(
            Arrays.stream(MetaTileEntityID.values())
                .mapToInt(entry -> entry.ID)
                .distinct()
                .count() == MetaTileEntityID.values().length,
            "all GTSR MTE IDs remain unique");
        check(
            Arrays.stream(MetaTileEntityID.values())
                .noneMatch(entry -> entry.ID == 14972),
            "former center slot is no longer registered by GTSR");
        queueAdmission();
        queuePersistence();
        migrationRecipes();
        System.out.println("PASS center queue and legacy migration: " + checks + " checks; game_started=false");
    }

    private static Item item(String key) throws Exception {
        return (Item) fixtureItem.invoke(null, "gtsr_queue_contract:" + key);
    }

    private static ItemStack ore(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static void queueAdmission() throws Exception {
        Item a = item("ore_a"), b = item("ore_b"), missing = item("ore_without_recipe");
        Item unrelated = item("unrelated"), output = item("product");
        OreDictionary.registerOre("rawOreQueueAlpha", ore(a, 1));
        OreDictionary.registerOre("rawOreQueueBeta", ore(b, 1));
        OreDictionary.registerOre("rawOreQueueMissing", ore(missing, 1));
        for (Item input : Arrays.asList(a, b)) GTValues.RA.stdBuilder()
            .itemInputs(ore(input, 1))
            .itemOutputs(ore(output, 1))
            .duration(40)
            .eut(2)
            .addTo(RecipeMaps.maceratorRecipes);

        ItemStack[] scarce = { ore(a, 12), ore(b, 11), ore(unrelated, 100000) };
        check(plan(scarce, true) == null, "unrelated items cannot fill the current 24 parallel lanes");
        check(scarce[0].stackSize == 12 && scarce[1].stackSize == 11, "waiting leaves input stacks unchanged");
        CenterPlanner.Batch mixed = plan(new ItemStack[] { ore(a, 12), ore(b, 12) }, true);
        check(mixed != null && mixed.roots == 24, "different ores combine to fill current parallel exactly");
        check(mixed.consumed.size() == 2, "both ore identities are consumed by the accepted plan");
        check(
            plan(new ItemStack[] { ore(a, 6), ore(missing, 24) }, true) == null,
            "a matching ore without a native recipe cannot fill the committed parallel lanes");
        CenterPlanner.Batch immediate = plan(new ItemStack[] { ore(a, 1) }, false);
        check(immediate != null && immediate.roots == 1, "disabled queue retains immediate small batches");
        check(plan(new ItemStack[] { ore(a, 23) }, true) == null, "one short of full parallel remains queued");
        CenterPlanner.Session expanded = planner(new ItemStack[] { ore(a, 47) }, true, 0, 2, 0);
        check(expanded.parallelLimit() == 48 && expanded.batch() == null, "sector expansion raises the queue target");
        check(
            planner(new ItemStack[] { ore(a, 48) }, true, 0, 2, 0).batch().roots == 48,
            "expanded sectors accept their full parallel count");
        CenterPlanner.Session upgraded = planner(new ItemStack[] { ore(a, 95) }, true, 1, 1, 0);
        check(upgraded.parallelLimit() == 96 && upgraded.batch() == null, "structure grade raises the queue target");
        check(
            planner(new ItemStack[] { ore(a, 96) }, true, 1, 1, 0).batch().roots == 96,
            "upgraded structure accepts its full parallel count");

        Fluid original = Materials.NitricAcid.mFluid;
        Fluid acid = new Fluid("gtsr_queue_contract_nitric");
        FluidRegistry.registerFluid(acid);
        Materials.NitricAcid.mFluid = acid;
        try {
            CenterPlanner.Session unpaid = planner(
                new ItemStack[] { ore(a, 24) },
                true,
                0,
                1,
                1,
                new FluidStack(acid, 3));
            check(
                unpaid.parallelLimit() == 24 && unpaid.batch() != null,
                "a booster without its dose cannot increase the admission target");
            CenterPlanner.Session boosted = planner(
                new ItemStack[] { ore(a, 37) },
                true,
                0,
                1,
                1,
                new FluidStack(acid, 4));
            check(
                boosted.parallelLimit() == 38 && boosted.batch() == null,
                "the paid parallel boost updates the target before native planning");
            check(
                planner(new ItemStack[] { ore(a, 38) }, true, 0, 1, 1, new FluidStack(acid, 4)).batch().roots == 38,
                "paid parallel boost admits a full batch");
        } finally {
            Materials.NitricAcid.mFluid = original;
        }
    }

    private static CenterPlanner.Batch plan(ItemStack[] input, boolean queue) {
        return planner(input, queue, 0, 1, 0).batch();
    }

    private static CenterPlanner.Session planner(ItemStack[] input, boolean queue, int tier, int sectors,
        int parallelModules, FluidStack... supply) {
        CenterGraph graph = new CenterGraph(
            1,
            "queue",
            0,
            true,
            Collections.emptyList(),
            Collections.singletonList(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { CenterGraph.BUS })));
        int[][] modules = new int[CenterModule.values().length][3];
        modules[CenterModule.CRUSHER.ordinal()][0] = 1;
        modules[CenterModule.PARALLEL.ordinal()][0] = parallelModules;
        CenterPlanner.Session session = new CenterPlanner.Session(
            Collections.singletonList(graph),
            input,
            supply,
            modules,
            tier,
            sectors,
            32,
            0,
            0,
            17,
            false,
            true,
            100000,
            1);
        session.queueMode(queue);
        int slices = 0;
        while (!session.advance(64, Long.MAX_VALUE)) {
            if (++slices > 100) throw new AssertionError("Queue planning did not finish");
        }
        return session;
    }

    private static void migrationRecipes() throws Exception {
        GTSRItemList[] legacy = { GTSRItemList.ClusterController, GTSRItemList.ClusterUnitCrusher,
            GTSRItemList.ClusterUnitOreWasher, GTSRItemList.ClusterUnitCentrifuge,
            GTSRItemList.ClusterUnitThermalCentrifuge, GTSRItemList.ClusterUnitSifter,
            GTSRItemList.ClusterUnitMagneticSeparator, GTSRItemList.ClusterUnitFurnace,
            GTSRItemList.ClusterBoosterParallel, GTSRItemList.ClusterBoosterSpeed, GTSRItemList.ClusterBoosterPrimary,
            GTSRItemList.ClusterBoosterSecondary, GTSRItemList.ClusterBoosterSteamSaver,
            GTSRItemList.ClusterUnitLogistics };
        CenterModule[] targets = { null, CenterModule.CRUSHER, CenterModule.WASHER, CenterModule.CENTRIFUGE,
            CenterModule.THERMAL_CENTRIFUGE, CenterModule.SIFTER, CenterModule.SEPARATOR, CenterModule.SMELTING,
            CenterModule.PARALLEL, CenterModule.SPEED, CenterModule.YIELD, CenterModule.SECONDARY, CenterModule.SPEED,
            CenterModule.LOGISTICS };
        for (GTSRItemList machine : legacy) machine.set(ore(item(machine.name()), 1));
        GTSRItemList.OreIntegrationCenter.set(ore(item("new_controller"), 1));
        Constructor<CenterModuleItems> constructor = CenterModuleItems.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Field instance = CenterModuleItems.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, constructor.newInstance());
        List<IRecipe> recipes = CraftingManager.getInstance()
            .getRecipeList();
        int start = recipes.size();
        int assemblers = RecipeMaps.assemblerRecipes.getAllRecipes()
            .size();
        ClusterRecipes.addRecipes();
        List<IRecipe> migrations = new ArrayList<>(recipes.subList(start, recipes.size()));
        check(migrations.size() == 14, "exactly one migration for each retained legacy machine");
        check(
            RecipeMaps.assemblerRecipes.getAllRecipes()
                .size() == assemblers,
            "legacy registration adds no manufacturing recipes");
        for (int i = 0; i < legacy.length; i++) {
            IRecipe recipe = migrations.get(i);
            ItemStack expected = i == 0 ? GTSRItemList.OreIntegrationCenter.get(1)
                : CenterModuleItems.stack(targets[i], 1);
            check(ItemStack.areItemStacksEqual(recipe.getRecipeOutput(), expected), "migration output " + legacy[i]);
            for (int slot : new int[] { 0, 4, 8 }) {
                InventoryCrafting inventory = new InventoryCrafting(new Container() {

                    @Override
                    public boolean canInteractWith(EntityPlayer player) {
                        return true;
                    }
                }, 3, 3);
                inventory.setInventorySlotContents(slot, legacy[i].get(1));
                check(recipe.matches(inventory, null), "shapeless migration " + legacy[i] + " at " + slot);
                check(
                    ItemStack.areItemStacksEqual(recipe.getCraftingResult(inventory), expected),
                    "one legacy machine returns one new item " + legacy[i]);
            }
        }
    }

    private static void queuePersistence() throws Exception {
        Constructor<MTEMineralIntegrationCenter> constructor = MTEMineralIntegrationCenter.class
            .getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        MTEMineralIntegrationCenter center = constructor.newInstance("queue_contract");
        center.setBaseMetaTileEntity(new BaseMetaTileEntity());
        NBTTagCompound saved = new NBTTagCompound();
        saved.setBoolean("CenterQueueMode", true);
        saved.setInteger("CenterDuration", 100);
        saved.setInteger("CenterElapsed", 37);
        saved.setLong("CenterSteam", 12345);
        saved.setLong("CenterEnergy", 20000);
        center.loadNBTData(saved);
        NBTTagCompound restored = new NBTTagCompound();
        center.saveNBTData(restored);
        check(restored.getBoolean("CenterQueueMode"), "queue selection survives the production NBT round trip");
        check(
            restored.getInteger("CenterDuration") == 100 && restored.getInteger("CenterElapsed") == 37
                && restored.getLong("CenterSteam") == 12345
                && restored.getLong("CenterEnergy") == 20000,
            "queue persistence preserves the paid batch progress and bill");
        check(
            "status.read_only".equals(center.configureQueue(null, false)),
            "queue settings require the normal player authority");
        center.loadNBTData(new NBTTagCompound());
        center.saveNBTData(restored);
        check(
            !restored.getBoolean("CenterQueueMode"),
            "old saves retain immediate processing until queue mode is enabled");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
