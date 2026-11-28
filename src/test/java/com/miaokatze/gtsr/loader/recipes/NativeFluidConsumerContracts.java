// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.loader.recipes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.Tags;
import com.miaokatze.gtsr.client.tooltip.GTSRTooltipCatalog;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.fluid.ProsperityFluidContainers;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;
import com.miaokatze.gtsr.crossmod.bq.BqQuestInjector;
import com.miaokatze.gtsr.register.GTSRItemOrderCatalog;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.tasks.TaskRegistry;
import bq_standard.tasks.TaskRetrieval;
import bq_standard.tasks.factory.FactoryTaskRetrieval;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModMetadata;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Actual registered fluids, native IC2 cells, native GT recipe matching and native BQ NBT matching. */
public final class NativeFluidConsumerContracts {

    private static int checks;

    private NativeFluidConsumerContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, NativeFluidConsumerContracts.class.getName(), args)) return;
        Class<?> registrations = Class
            .forName("com.miaokatze.gtsr.common.dimension.prosperity.fluid.NativeFluidRegistrationContracts");
        registrations.getMethod("bootstrapRegistrations")
            .invoke(null);
        try {
            bootstrapNativeCell();
            handshakeContracts();
            cellContracts();
            reactionContracts();
            questContracts();
            presentationContracts();
            System.out.println(
                "PASS native fluid consumers checks=" + checks
                    + " game_started=false original_world_io=false temporary_stamp_io=true");
        } finally {
            registrations.getMethod("cleanupFixture")
                .invoke(null);
        }
    }

    private static void handshakeContracts() throws Exception {
        com.miaokatze.gtsr.common.dimension.prosperity.industrial.compat.MaterialMappingHandshake.register();
        java.util.Set<String> tokens = com.miaokatze.gtsr.common.dimension.prosperity.industrial.compat.MaterialMappingHandshake
            .tokens(
                com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdRegistration
                    .startupIdentity().fingerprint);
        for (cpw.mods.fml.relauncher.Side side : cpw.mods.fml.relauncher.Side.values()) check(
            cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.channelNamesFor(side)
                .containsAll(tokens),
            "native FML handshake advertises complete mapping on " + side);
        // EventBus otherwise catches constructor errors and prints them without propagating.
        // Assert the production listener really registered, rather than accepting a quiet fixture.
        Field listeners = cpw.mods.fml.common.eventhandler.EventBus.class.getDeclaredField("listeners");
        listeners.setAccessible(true);
        java.util.Map<?, ?> registered = (java.util.Map<?, ?>) listeners.get(
            cpw.mods.fml.common.FMLCommonHandler.instance()
                .bus());
        boolean found = false;
        for (java.util.Map.Entry<?, ?> entry : registered.entrySet()) if (entry
            .getKey() instanceof com.miaokatze.gtsr.common.dimension.prosperity.industrial.compat.MaterialMappingHandshake)
            found = !((java.util.List<?>) entry.getValue()).isEmpty();
        check(found, "native FML registration event listener is attached before player login");
    }

    /** Calls the real IC2 constructor and uses its native capacity, fill, drain and NBT implementations. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void bootstrapNativeCell() throws Exception {
        Field controller = Loader.class.getDeclaredField("modController");
        controller.setAccessible(true);
        Object instance = controller.get(Loader.instance());
        Field active = LoadController.class.getDeclaredField("activeContainer");
        active.setAccessible(true);
        Object previous = active.get(instance);
        ModMetadata metadata = new ModMetadata();
        metadata.modId = "IC2";
        metadata.name = "Native IC2 cell contract";
        active.set(instance, new DummyModContainer(metadata));
        Item cell;
        try {
            Class<? extends Enum> names = (Class<? extends Enum>) Class.forName("ic2.core.init.InternalName");
            Object name = Enum.valueOf(names, "itemFluidCell");
            cell = (Item) Class.forName("ic2.core.item.ItemFluidCell")
                .getConstructor(names)
                .newInstance(name);
        } finally {
            active.set(instance, previous);
        }
        ItemStack empty = new ItemStack(cell);
        ItemList.Cell_Universal_Fluid.set(empty);
        Class.forName("ic2.core.Ic2Items")
            .getField("FluidCell")
            .set(null, empty.copy());
        new gregtech.common.items.ItemFluidDisplay();
    }

    private static void cellContracts() {
        FluidStack target = GTSRProsperityAirMaterials.WastesSigh.getGas(1000);
        ItemStack nativeCell = ProsperityFluidContainers.filledCell(target, 17);
        check(nativeCell.stackSize == 17 && target.amount == 1000, "fill copies quantity and does not consume source");
        check(
            GTUtility.getFluidForFilledItem(nativeCell, true)
                .isFluidEqual(target),
            "native full cell has named fluid");
        ItemStack empty = GTUtility.getContainerForFilledItem(nativeCell, true);
        check(
            empty.getItem() == nativeCell.getItem() && GTUtility.getFluidForFilledItem(empty, true) == null,
            "native shell is same universal cell and actually empty");
        ItemStack wrong = ProsperityFluidContainers.filledCell(GTSRProsperityAirMaterials.ThickGrease.getGas(1000));
        check(
            nativeCell.isItemEqual(wrong) && !ItemStack.areItemStackTagsEqual(nativeCell, wrong),
            "different fluids really share Item/Damage but have distinct native NBT");
    }

    private static void reactionContracts() {
        AbyssalObsessionRecipes.registerAbyssalReaction();
        check(
            RecipeMaps.chemicalReactorRecipes.getAllRecipes()
                .size() == 1,
            "one singleblock reaction");
        check(
            RecipeMaps.multiblockChemicalReactorRecipes.getAllRecipes()
                .size() == 1,
            "one LCR reaction");
        GTRecipe single = RecipeMaps.chemicalReactorRecipes.getAllRecipes()
            .iterator()
            .next();
        GTRecipe large = RecipeMaps.multiblockChemicalReactorRecipes.getAllRecipes()
            .iterator()
            .next();
        check(
            single.isNBTSensitive && single.mInputs.length == 1 && single.mOutputs.length == 1,
            "singleblock explicitly requires NBT and returns one physical shell");
        check(
            large.mInputs.length == 0 && large.mOutputs.length == 0 && large.mFluidInputs.length == 2,
            "LCR takes two fluids and cannot manufacture empty cells");
        for (GTRecipe recipe : new GTRecipe[] { single, large }) {
            check(
                recipe.mDuration == 200 && recipe.mEUt == 120
                    && recipe.mFluidOutputs.length == 1
                    && recipe.mFluidOutputs[0].amount == 2000,
                "reaction quantity/time/EU preserved");
        }
        FluidStack steam = GTSRProsperityAirMaterials.SanzuResidualSteam.getGas(1000);
        ItemStack correct = ProsperityFluidContainers.filledCell(
            ProsperityIndustrialMaterials.get("planned:prosperity_obsession")
                .getFluid(1000));
        check(
            RecipeMaps.chemicalReactorRecipes.findRecipeQuery()
                .items(correct)
                .fluids(steam)
                .voltage(120)
                .find() == single,
            "native GT accepts intended filled NBT cell");
        for (ItemStack wrong : new ItemStack[] { ProsperityFluidContainers.emptyCell(1),
            ProsperityFluidContainers.filledCell(new FluidStack(FluidRegistry.WATER, 1000)),
            ProsperityFluidContainers.filledCell(GTSRProsperityAirMaterials.WastesSigh.getGas(1000)) }) {
            check(
                RecipeMaps.chemicalReactorRecipes.findRecipeQuery()
                    .items(wrong)
                    .fluids(steam)
                    .voltage(120)
                    .cachedRecipe(single)
                    .find() == null,
                "native GT cached lookup rejects wrong/empty fluid cell");
        }
    }

    private static void questContracts() throws Exception {
        Method resolve = BqQuestInjector.class
            .getDeclaredMethod("resolveRuntimeTargets", com.google.gson.JsonElement.class);
        resolve.setAccessible(true);
        String[] gases = { "wastesigh", "thickgrease", "metalgrit", "umbralmire", "sanzu_residual_steam",
            "withered_breath" };
        String[] files = { "WasteSighFeedsto-R1RTUgAAAAAAAAAAAAAARw==", "ThickGreaseFeeds-R1RTUgAAAAAAAAAAAAAASA==",
            "MetalGritFeedsto-R1RTUgAAAAAAAAAAAAAASQ==", "UmbralMireFeedst-R1RTUgAAAAAAAAAAAAAASg==",
            "SanzuResidualSte-R1RTUgAAAAAAAAAAAAAASw==", "WitheredBreathFe-R1RTUgAAAAAAAAAAAAAATA==" };
        UUID player = UUID.fromString("47545352-0000-0000-0000-000000000175");
        for (int i = 0; i < gases.length; i++) {
            String gas = gases[i];
            JsonObject quest;
            String resource = "/assets/gtsr/bqquests/Quests/GTSteamReborn-R1RTUgAAAAAAAAAAAAAAAQ==/" + files[i]
                + ".json";
            try (InputStream stream = NativeFluidConsumerContracts.class.getResourceAsStream(resource)) {
                check(stream != null, "bundled gas quest exists");
                quest = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            }
            JsonObject task = quest.getAsJsonObject("tasks:9")
                .getAsJsonObject("0:10");
            check(
                task.get("ignoreNBT:1")
                    .getAsInt() == 0
                    && task.get("partialMatch:1")
                        .getAsInt() == 1,
                "bundled gas target checks Fluid NBT and allows unrelated extra tags");
            // A definition still carrying the old permissive flags must also be repaired at resolution.
            task.addProperty("ignoreNBT:1", 1);
            task.addProperty("partialMatch:1", 0);
            resolve.invoke(null, quest);
            check(
                quest.get("questIDHigh:4")
                    .getAsLong() == 5139824686389002240L
                    && quest.get("questIDLow:4")
                        .getAsLong() == 71 + i
                    && task.get("index:3")
                        .getAsInt() == 0,
                "target replacement preserves existing quest and task progress identities");
            NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(task, new NBTTagCompound(), true);
            TaskRetrieval retrieval = new TaskRetrieval();
            retrieval.readFromNBT(nbt);
            check(
                !retrieval.ignoreNBT && retrieval.partialMatch
                    && !retrieval.requiredItems.get(0)
                        .hasOreDict(),
                "only targeted retrieval uses exact NBT without ore fallback: " + gas);
            ItemStack required = retrieval.requiredItems.get(0)
                .getBaseStack();
            FluidStack contents = GTUtility.getFluidForFilledItem(required, true);
            check(
                contents != null && contents.amount == 1000
                    && gas.equals(
                        contents.getFluid()
                            .getName()),
                "BQ stores actual native fluid cell: " + gas);
            if (i == 0) upgradeContracts(quest, player);
            retrieval.consume = true; // Exercise native canAcceptItem's matching branch without a player/world.
            check(retrieval.canAcceptItem(player, null, required.copy()), "BQ native matcher accepts correct fluid");
            ItemStack named = required.copy();
            NBTTagCompound display = new NBTTagCompound();
            display.setString("Name", "Named correct native fluid cell");
            named.getTagCompound()
                .setTag("display", display);
            named.getTagCompound()
                .setString("gtsr_contract_extra", "preserved");
            check(
                retrieval.canAcceptItem(player, null, named),
                "BQ native matcher accepts correct Fluid NBT with display name and unrelated extra tag");
            ItemStack wrongAmount = required.copy();
            wrongAmount.getTagCompound()
                .getCompoundTag("Fluid")
                .setInteger("Amount", 999);
            check(
                !retrieval.canAcceptItem(player, null, wrongAmount),
                "BQ native matcher rejects incorrect amount in otherwise identical Fluid NBT");
            ItemStack wrongName = required.copy();
            wrongName.getTagCompound()
                .getCompoundTag("Fluid")
                .setString("FluidName", "water");
            check(
                !retrieval.canAcceptItem(player, null, wrongName),
                "BQ native matcher rejects incorrect FluidName with identical outer NBT shape");
            for (ItemStack wrong : new ItemStack[] { ProsperityFluidContainers.emptyCell(1),
                ProsperityFluidContainers.filledCell(new FluidStack(FluidRegistry.WATER, 1000)),
                ProsperityFluidContainers.filledCell(
                    Materials.get(gas.equals("wastesigh") ? "thickgrease" : "wastesigh")
                        .getGas(1000)) })
                check(
                    !retrieval.canAcceptItem(player, null, wrong),
                    "BQ native matcher rejects wrong gas/water/empty despite identical Item/Damage");
        }
        JsonObject foreign = new JsonParser()
            .parse(
                "{\"taskID:8\":\"bq_standard:retrieval\",\"ignoreNBT:1\":1,"
                    + "\"partialMatch:1\":1,\"requiredItems:9\":{\"0:10\":{\"OreDict:8\":\"cellHydrogen\"}}}")
            .getAsJsonObject();
        String before = foreign.toString();
        resolve.invoke(null, foreign);
        check(before.equals(foreign.toString()), "unrelated retrieval task remains byte-equivalent JSON");
    }

    private static void upgradeContracts(JsonObject definition, UUID player) throws Exception {
        TaskRegistry.INSTANCE.register(FactoryTaskRetrieval.INSTANCE);
        NBTTagCompound def = NBTConverter.JSONtoNBT_Object(definition, new NBTTagCompound(), true);
        // Reward parsing is unrelated to the container definition/unchanged progress path.
        def.removeTag("rewards");
        QuestInstance existing = new QuestInstance();
        existing.readFromNBT(def);
        TaskRetrieval old = (TaskRetrieval) existing.getTasks()
            .getValue(0);
        old.ignoreNBT = true;
        old.partialMatch = false;
        existing.setComplete(player, 123);
        existing.setClaimed(player, 124);
        UUID partial = UUID.fromString("47545352-0000-0000-0000-000000000176");
        NBTTagCompound user = new NBTTagCompound();
        user.setString("uuid", partial.toString());
        user.setIntArray("data", new int[] { 1 });
        NBTTagList users = new NBTTagList();
        users.appendTag(user);
        NBTTagCompound progress = new NBTTagCompound();
        progress.setTag("userProgress", users);
        old.readProgressFromNBT(progress, true);

        Method needsRefresh = BqQuestInjector.class.getDeclaredMethod("isDefinitionRefreshNeeded");
        Method refresh = BqQuestInjector.class
            .getDeclaredMethod("refreshDefinition", betterquesting.api.questing.IQuest.class, NBTTagCompound.class);
        Method writeStamp = BqQuestInjector.class.getDeclaredMethod("writeStamp", String.class);
        needsRefresh.setAccessible(true);
        refresh.setAccessible(true);
        writeStamp.setAccessible(true);
        java.io.File previous = BQ_Settings.curWorldDir;
        Path directory = Files.createTempDirectory("gtsr-bq-native-cell-stamp-");
        Path stamp = directory.resolve("gtsr-injected.json");
        try {
            BQ_Settings.curWorldDir = directory.toFile();
            JsonObject oldStamp = new JsonObject();
            oldStamp.addProperty("version", Tags.VERSION);
            Files.write(
                stamp,
                oldStamp.toString()
                    .getBytes(StandardCharsets.UTF_8));
            check((Boolean) needsRefresh.invoke(null), "same-version old stamp refreshes permissive cell definitions");
            refresh.invoke(null, existing, def);
            TaskRetrieval updated = (TaskRetrieval) existing.getTasks()
                .getValue(0);
            check(
                !updated.ignoreNBT && updated.partialMatch
                    && updated.requiredItems.get(0)
                        .getOreDict()
                        .isEmpty(),
                "existing definition receives exact native fluid target");
            check(
                updated.getUsersProgress(partial)[0] == 1 && !existing.isComplete(partial),
                "definition refresh retains submitted count and incomplete status by original task index");
            check(
                existing.isComplete(player) && existing.getCompletionInfo(player)
                    .getBoolean("claimed"),
                "definition refresh retains completed and claimed state");
            writeStamp.invoke(null, Tags.VERSION);
            check(!(Boolean) needsRefresh.invoke(null), "new definition revision makes repeat startup idempotent");
        } finally {
            BQ_Settings.curWorldDir = previous;
            Files.deleteIfExists(stamp);
            Files.delete(directory);
        }
    }

    private static void presentationContracts() throws Exception {
        List<ItemStack> generated = GTSRItemOrderCatalog.generatedDimensionItems();
        int cells = 0, displays = 0;
        for (ItemStack stack : generated) {
            if (stack.getItem() == ItemList.Cell_Universal_Fluid.getItem()) cells++;
            if (stack.getItem() == ItemList.Display_Fluid.getItem()) displays++;
        }
        check(cells == 64 && displays == 64, "all 64 native NBT variants and real GT displays are available");
        for (ItemStack stack : GTSRItemOrderCatalog.rawItems()) check(
            stack.getItem() != ItemList.Cell_Universal_Fluid.getItem(),
            "Core catalog never claims shared IC2 cell Item/Damage");
        List<ItemStack> nativeSubitems = new ArrayList<>();
        Item cell = ItemList.Cell_Universal_Fluid.getItem();
        cell.getSubItems(cell, null, nativeSubitems);
        for (ItemStack stack : generated) if (stack.getItem() == cell) {
            check(
                nativeSubitems.stream()
                    .anyMatch(
                        nativeStack -> nativeStack.isItemEqual(stack)
                            && ItemStack.areItemStackTagsEqual(nativeStack, stack)),
                "IC2 native subitems include real registered fluid NBT");
        }
        GTSRTooltipCatalog.initialize();
        for (ItemStack stack : generated)
            check(GTSRTooltipCatalog.descriptionKeys(stack) != null, "catalog form receives content-specific tooltip");
        check(
            GTSRTooltipCatalog.descriptionKeys(ProsperityFluidContainers.emptyCell(1)) == null,
            "empty IC2 cell remains outside GTSR tooltip ownership");
        check(
            GTSRTooltipCatalog.descriptionKeys(
                ProsperityFluidContainers.filledCell(new FluidStack(FluidRegistry.WATER, 1000))) == null,
            "native water cell retains its original tooltip");
    }

    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++;
    }
}
