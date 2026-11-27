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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.loader.recipes.ClusterRecipes;
import com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts;

import gregtech.api.enums.GTValues;
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

        ItemStack[] scarce = { ore(a, 2048), ore(b, 2047), ore(unrelated, 100000) };
        check(plan(scarce, 4096) == null, "unrelated items cannot satisfy the queue minimum");
        check(scarce[0].stackSize == 2048 && scarce[1].stackSize == 2047, "waiting leaves input stacks unchanged");
        CenterPlanner.Batch mixed = plan(new ItemStack[] { ore(a, 2048), ore(b, 2048) }, 4096);
        check(mixed != null && mixed.roots == 4096, "different ores combine to meet the exact threshold");
        check(mixed.consumed.size() == 2, "both ore identities are consumed by the accepted plan");
        check(
            plan(new ItemStack[] { ore(a, 1024), ore(missing, 4096) }, 4096) == null,
            "a matching ore without a native recipe cannot pad the committed batch");
        CenterPlanner.Batch immediate = plan(new ItemStack[] { ore(a, 1) }, 1);
        check(immediate != null && immediate.roots == 1, "disabled queue retains immediate small batches");
        check(plan(new ItemStack[] { ore(a, 4095) }, 4096) == null, "one short of the minimum remains queued");
    }

    private static CenterPlanner.Batch plan(ItemStack[] input, int minimum) {
        CenterGraph graph = new CenterGraph(
            1,
            "queue",
            0,
            true,
            Collections.emptyList(),
            Collections.singletonList(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { CenterGraph.BUS })));
        int[][] modules = new int[CenterModule.values().length][3];
        modules[CenterModule.CRUSHER.ordinal()][0] = 1;
        CenterPlanner.Session session = new CenterPlanner.Session(
            Collections.singletonList(graph),
            input,
            new FluidStack[0],
            modules,
            0,
            1,
            32,
            0,
            0,
            17,
            true,
            true,
            100000,
            1);
        session.requireRoots(minimum);
        int slices = 0;
        while (!session.advance(64, Long.MAX_VALUE)) {
            if (++slices > 100) throw new AssertionError("Queue planning did not finish");
        }
        return session.batch();
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
        saved.setInteger("CenterQueueMinimum", 6000);
        saved.setInteger("CenterDuration", 100);
        saved.setInteger("CenterElapsed", 37);
        saved.setLong("CenterSteam", 12345);
        saved.setLong("CenterEnergy", 20000);
        center.loadNBTData(saved);
        NBTTagCompound restored = new NBTTagCompound();
        center.saveNBTData(restored);
        check(
            restored.getBoolean("CenterQueueMode") && restored.getInteger("CenterQueueMinimum") == 6000,
            "queue selection survives the production NBT round trip");
        check(
            restored.getInteger("CenterDuration") == 100 && restored.getInteger("CenterElapsed") == 37
                && restored.getLong("CenterSteam") == 12345
                && restored.getLong("CenterEnergy") == 20000,
            "queue persistence preserves the paid batch progress and bill");
        check(
            "status.read_only".equals(center.configureQueue(null, false, 1)),
            "queue settings require the normal player authority");
        center.loadNBTData(new NBTTagCompound());
        center.saveNBTData(restored);
        check(
            !restored.getBoolean("CenterQueueMode") && restored.getInteger("CenterQueueMinimum") == 4096,
            "old saves retain immediate processing and the default optional minimum");
        saved.setInteger("CenterQueueMinimum", -1);
        center.loadNBTData(saved);
        center.saveNBTData(restored);
        check(restored.getInteger("CenterQueueMinimum") == 1, "invalid saved minima are clamped");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
