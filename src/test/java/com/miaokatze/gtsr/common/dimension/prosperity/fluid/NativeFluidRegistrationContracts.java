// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.fluid;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.postea.api.IDExtenderCompat;
import com.gtnewhorizons.postea.utility.IDRegistry;
import com.gtnewhorizons.postea.utility.TransformerRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.compatibility.ProsperityPosteaMigration;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialMaterialColors;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialRecipeLedger;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialDusts;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.MaterialBuilder;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/**
 * Exact .277 native materials/fluids, real Forge registration and FML ItemData contracts.
 * Registration uses no config, GT slot assignment for GTSR, placeholder items or generated GT dust.
 * Optional Postea integration reads only its own temporary historical cfg fixture.
 * Native IC2 cells come from the shared actual-IC2 fixture; this is not a game/world or client render test.
 */
public final class NativeFluidRegistrationContracts {

    private static boolean registered;
    private static int checks;
    private static final List<Materials> FLUID_FACADES = new ArrayList<>();
    private static final OrePrefixes[] FORMS = { OrePrefixes.dust, OrePrefixes.dustSmall, OrePrefixes.dustTiny };

    private NativeFluidRegistrationContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, NativeFluidRegistrationContracts.class.getName(), args)) return;
        bootstrapRegistrations();
        foreignNameRejectedBeforeMutation();
        Class.forName("com.miaokatze.gtsr.loader.recipes.NativeFluidConsumerContracts")
            .getMethod("bootstrapNativeCell")
            .invoke(null);
        for (Materials material : FLUID_FACADES) {
            FluidStack fluid = material.mGas != null ? material.getGas(1000) : material.getFluid(1000);
            ItemStack cell = ProsperityFluidContainers.filledCell(fluid, 3);
            FluidStack actual = GTUtility.getFluidForFilledItem(cell, true);
            check(
                cell.stackSize == 3 && actual.amount == 1000 && actual.isFluidEqual(fluid),
                "real native IC2 cell identity and amount: " + material.mName);
            check(fluid.amount == 1000, "native fill does not consume caller fluid: " + material.mName);
        }
        posteaIntegrationContracts();
        ProsperityIndustrialMaterials.auditRegistration();
        GTSRProsperityAirMaterials.logRegistrationProbe();
        System.out.println("PASS NativeFluidRegistrationContracts checks=" + checks);
        System.out.println(
            "BOUNDARY: real Forge126 items, GT277 ores/64 fluids, FML ItemData, IC2 cells and Postea dispatch; no world or client render claim");
    }

    /** Shared consumer bootstrap uses the same official Forge registry and actual production callbacks. */
    public static void bootstrapRegistrations() throws Exception {
        if (registered) return;
        Method setup = Class.forName("com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts")
            .getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        Path jar = Paths.get(
            ((JarURLConnection) Materials.class.getResource("/gregtech/api/enums/Materials.class")
                .openConnection()).getJarFileURL()
                    .toURI());
        check(
            sha256(Files.readAllBytes(jar)).equals("88a0ac96a01e11fa04f738784722ecf27d4c72488973ff41eeb1399a167801e7"),
            "exact GT277 artifact");
        Class<?> tableType = Class.forName("gregtech.api.enums.MaterialsIDMap");
        Constructor<?> constructor = tableType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object table = constructor.newInstance();
        tableType.getMethod("register")
            .invoke(table);
        check(Materials.DistilledWater.mMetaItemSubID == 705, "native705 remains distilled water");
        Field controller = Loader.class.getDeclaredField("modController");
        controller.setAccessible(true);
        Object instance = controller.get(Loader.instance());
        Field active = LoadController.class.getDeclaredField("activeContainer");
        active.setAccessible(true);
        Object previous = active.get(instance);
        ModMetadata metadata = new ModMetadata();
        metadata.modId = "gtsr";
        metadata.name = "Official Forge industrial dust fixture";
        active.set(instance, new DummyModContainer(metadata));
        try {
            ProsperityIndustrialDusts.registerItems();
            new GTSRProsperityAirMaterials().onMaterialsInit();
            new ProsperityIndustrialMaterials().onMaterialsInit();
        } finally {
            active.set(instance, previous);
        }
        // Native Materials.init populates only the genuine native numeric identities after addon callbacks.
        Field array = Materials.class.getDeclaredField("MATERIALS_ARRAY");
        array.setAccessible(true);
        array.set(
            null,
            Materials.getMaterialsMap()
                .values()
                .toArray(new Materials[0]));
        Method fill = Materials.class.getDeclaredMethod("fillGeneratedMaterialsMap");
        fill.setAccessible(true);
        fill.invoke(null);
        check(GregTechAPI.sGeneratedMaterials[705] == Materials.DistilledWater, "native final705 ownership unchanged");
        Map<String, Integer> itemData = GameData.buildItemDataList().idMap;
        Set<Item> owned = new HashSet<>();
        Set<Materials> facades = new HashSet<>();
        Set<String> names = new HashSet<>();
        int dust = 0;
        for (IndustrialRecipeLedger.MaterialDefinition definition : IndustrialRecipeLedger.get().materials) {
            Materials material = ProsperityIndustrialMaterials.get(definition.id);
            facades.add(material);
            check(
                Materials.get(definition.name) == material && material.mMetaItemSubID == -1,
                "public facade identity without numeric GT slot: " + definition.id);
            if ("dust".equals(definition.phase)) {
                check(!material.hasDustItems() && !material.hasCell(), "GT automatic item generation disabled");
                for (OrePrefixes form : FORMS) {
                    ItemStack stack = ProsperityIndustrialDusts.get(definition.id, form, 2);
                    ItemStack canonical = GTOreDictUnificator.get(form, material, 1);
                    ItemData association = GTOreDictUnificator.getItemData(stack);
                    String name = ProsperityIndustrialDusts.registryName(definition.id, form);
                    check(
                        owned.add(stack.getItem()) && stack.getItemDamage() == 0 && stack.stackSize == 2,
                        "distinct Forge item identity for each dust form: " + name);
                    check(
                        name.equals(Item.itemRegistry.getNameForObject(stack.getItem()))
                            && GameRegistry.findItem("gtsr", name.substring("gtsr:".length())) == stack.getItem(),
                        "real official Forge registry identity: " + name);
                    check(
                        itemData.containsKey("\u0002" + name)
                            && itemData.get("\u0002" + name) == Item.getIdFromItem(stack.getItem()),
                        "native FML ItemData owns persisted numeric item mapping: " + name);
                    check(
                        canonical != null && canonical.getItem() == stack.getItem()
                            && canonical.getItemDamage() == 0
                            && association != null
                            && association.mPrefix == form
                            && association.mMaterial.mMaterial == material,
                        "official GT canonical ore association: " + name);
                    check(
                        definition.id.equals(ProsperityIndustrialDusts.ledgerId(stack))
                            && definition.id.equals(ProsperityIndustrialDusts.ledgerId(stack.getItem())),
                        "constant-time owned dust lookup");
                    NBTTagCompound nbt = new NBTTagCompound();
                    stack.writeToNBT(nbt);
                    ItemStack reread = ItemStack.loadItemStackFromNBT(nbt);
                    check(
                        reread != null && reread.getItem() == stack.getItem()
                            && reread.getItemDamage() == 0
                            && reread.stackSize == 2,
                        "native ItemStack persistence roundtrip: " + name);
                }
                dust++;
            } else checkFluid(
                material,
                "gas".equals(definition.phase),
                definition.temperatureK,
                IndustrialMaterialColors.getARGB(definition.id) & 0xffffff,
                names);
        }
        Materials[] raw = { GTSRProsperityAirMaterials.WastesSigh, GTSRProsperityAirMaterials.ThickGrease,
            GTSRProsperityAirMaterials.MetalGrit, GTSRProsperityAirMaterials.UmbralMire,
            GTSRProsperityAirMaterials.SanzuResidualSteam, GTSRProsperityAirMaterials.WitheredBreath };
        String[] rawNames = { "wastesigh", "thickgrease", "metalgrit", "umbralmire", "sanzu_residual_steam",
            "withered_breath" };
        int[] colors = { 0xb08a4a, 0x4a3f28, 0x96613d, 0x2c251d, 0xa8c8d8, 0x8a7a52 };
        for (int i = 0; i < raw.length; i++) {
            facades.add(raw[i]);
            check(rawNames[i].equals(raw[i].mName), "raw gas stable name");
            checkFluid(raw[i], true, 295, colors[i], names);
        }
        facades.add(GTSRProsperityAirMaterials.AbyssalObsession);
        checkFluid(GTSRProsperityAirMaterials.AbyssalObsession, false, 295, 0x234a6b, names);
        check(
            "abyssal_obsession".equals(GTSRProsperityAirMaterials.AbyssalObsession.mName),
            "abyssal stable liquid identity");
        check(
            dust == 42 && owned.size() == 126 && names.size() == 64,
            "exact126 official dust items and64 native fluid identities");
        for (Materials generated : GregTechAPI.sGeneratedMaterials)
            check(!facades.contains(generated), "GTSR never occupies any native GT material slot");
        registered = true;
    }

    private static void checkFluid(Materials material, boolean gas, int temperature, int color, Set<String> names)
        throws Exception {
        check(names.add(material.mName), "unique fluid name: " + material.mName);
        Fluid fluid = gas ? material.mGas : material.mFluid;
        ProsperityNativeFluids.verify(material);
        check(
            fluid != null && FluidRegistry.getFluid(material.mName) == fluid
                && Materials.FLUID_MAP.get(fluid) == material,
            "native registry and FLUID_MAP identity: " + material.mName);
        check(
            fluid.isGaseous() == gas && fluid.getTemperature() == temperature
                && fluid.getColor() == color
                && fluid.getDensity() == (gas ? -100 : 1000)
                && fluid.getViscosity() == (gas ? 200 : 1000)
                && fluid.getLuminosity() == 0,
            "original physical properties: " + material.mName);
        Field still = fluid.getClass()
            .getDeclaredField("stillIconResourceLocation");
        Field flowing = fluid.getClass()
            .getDeclaredField("flowingIconResourceLocation");
        still.setAccessible(true);
        flowing.setAccessible(true);
        check(
            "gregtech:fluids/fluid.autogenerated".equals(
                still.get(fluid)
                    .toString())
                && flowing.get(fluid) == null,
            "original autogenerated texture: " + material.mName);
        check(
            (gas ? material.mFluid : material.mGas) == null && material.mMetaItemSubID == -1,
            "original phase without numeric material slot");
        check(
            !material.hasCorrespondingFluid() && !material.hasCorrespondingGas()
                && !material.mHasGas
                && !material.hasCell(),
            "GT duplicate fluid and cell generation disabled: " + material.mName);
        FLUID_FACADES.add(material);
    }

    /** Production optional registration and real Postea dispatch target the actual Forge items and IC2 cells. */
    private static void posteaIntegrationContracts() throws Exception {
        List<Integer> free = new ArrayList<>();
        for (int slot = 999; slot >= 0 && free.size() < 3; slot--)
            if (GregTechAPI.sGeneratedMaterials[slot] == null) free.add(slot);
        check(free.size() == 3, "native table provides three genuinely empty historical fixture slots");
        IndustrialRecipeLedger.MaterialDefinition dust = null, liquid = null;
        for (IndustrialRecipeLedger.MaterialDefinition entry : IndustrialRecipeLedger.get().materials) {
            if (dust == null && "dust".equals(entry.phase)) dust = entry;
            if (liquid == null && "fluid".equals(entry.phase) && !"planned:native_acid_cut".equals(entry.id))
                liquid = entry;
        }
        check(dust != null && liquid != null, "fixture uses actual ledger dust and liquid");
        Properties legacy = new Properties();
        legacy.setProperty(dust.id, Integer.toString(free.get(0)));
        legacy.setProperty(liquid.id, Integer.toString(free.get(1)));
        legacy.setProperty("air.wastesigh", Integer.toString(free.get(2)));
        legacy.setProperty("planned:native_acid_cut", "705");
        java.io.ByteArrayOutputStream encoded = new java.io.ByteArrayOutputStream();
        legacy.store(encoded, "Synthetic historical subset; actual registered production targets");
        byte[] original = encoded.toByteArray();
        Loader loader = Loader.instance();
        Field controllers = Loader.class.getDeclaredField("modController");
        Field mods = Loader.class.getDeclaredField("namedMods");
        controllers.setAccessible(true);
        mods.setAccessible(true);
        Object previousController = controllers.get(loader), previousMods = mods.get(loader);
        LoadController controller = new LoadController(loader);
        Field state = LoadController.class.getDeclaredField("state");
        state.setAccessible(true);
        state.set(controller, LoaderState.SERVER_ABOUT_TO_START);
        Map<String, ModContainer> present = new HashMap<>(loader.getIndexedModList());
        ModMetadata metadata = new ModMetadata();
        metadata.modId = "postea";
        metadata.version = "1.2.9";
        metadata.name = "Real Postea dispatch fixture";
        present.put("postea", new DummyModContainer(metadata));
        Path cfg = Files.createTempFile("gtsr-native-postea-contract-", ".cfg");
        try {
            Files.write(cfg, original);
            controllers.set(loader, controller);
            mods.set(loader, present);
            check(Loader.isModLoaded("postea"), "production optional Postea branch is enabled by the native Loader");
            ProsperityPosteaMigration.register(cfg);
            int historicalItemId = 32000;
            Map<String, Integer> worldRegistry = new HashMap<>();
            worldRegistry.put("\u0002gregtech:gt.metaitem.01", historicalItemId);
            IDRegistry.registerWorldSpecificIDs(worldRegistry);
            TransformerRegistry.onIdMappingsChanged();
            OrePrefixes[] prefixes = { OrePrefixes.dustTiny, OrePrefixes.dustSmall, OrePrefixes.dust };
            for (int prefix = 0; prefix < prefixes.length; prefix++) {
                NBTTagCompound stack = historicalStack(historicalItemId, prefix * 1000 + free.get(0));
                ItemStack expected = ProsperityIndustrialDusts.get(dust.id, prefixes[prefix], 1);
                TransformerRegistry.transformItem(stack);
                check(
                    IDExtenderCompat.getItemStackID(stack) == Item.getIdFromItem(expected.getItem())
                        && stack.getShort("Damage") == 0,
                    "production Postea rule selects actual owned dust form");
                checkMigrationPayload(stack);
            }
            FluidStack[] contents = { ProsperityIndustrialMaterials.get(liquid.id)
                .getFluid(1000), GTSRProsperityAirMaterials.WastesSigh.getGas(1000) };
            for (int index = 0; index < contents.length; index++) {
                NBTTagCompound stack = historicalStack(historicalItemId, 30000 + free.get(index + 1));
                TransformerRegistry.transformItem(stack);
                ItemStack actual = ItemStack.loadItemStackFromNBT(stack);
                ItemStack expected = ProsperityFluidContainers.filledCell(contents[index]);
                FluidStack fluid = GTUtility.getFluidForFilledItem(actual, true);
                check(
                    actual != null && actual.getItem() == expected.getItem()
                        && fluid != null
                        && fluid.amount == 1000
                        && fluid.isFluidEqual(contents[index]),
                    "production Postea rule creates real IC2 cell with actual registered liquid/gas");
                checkMigrationPayload(stack);
            }
            NBTTagCompound water = historicalStack(historicalItemId, 30705), before = (NBTTagCompound) water.copy();
            TransformerRegistry.transformItem(water);
            check(
                GregTechAPI.sGeneratedMaterials[705] == Materials.DistilledWater && water.equals(before),
                "future native distilled-water705 stays unchanged through actual registered Postea rules");
            check(Arrays.equals(original, Files.readAllBytes(cfg)), "optional historical cfg remains byte-identical");
        } finally {
            mods.set(loader, previousMods);
            controllers.set(loader, previousController);
            Files.delete(cfg);
        }
    }

    private static NBTTagCompound historicalStack(int itemId, int damage) {
        NBTTagCompound stack = new NBTTagCompound(), tag = new NBTTagCompound();
        stack.setShort("id", (short) itemId);
        stack.setShort("Damage", (short) damage);
        stack.setByte("Count", (byte) 37);
        stack.setInteger("IntSlot", 9);
        stack.setLong("Cnt", 5000000000L);
        stack.setBoolean("paidOutput", true);
        tag.setString("custom", "preserved");
        stack.setTag("tag", tag);
        return stack;
    }

    private static void checkMigrationPayload(NBTTagCompound stack) {
        check(
            stack.getByte("Count") == 37 && stack.getInteger("IntSlot") == 9
                && stack.getLong("Cnt") == 5000000000L
                && stack.getBoolean("paidOutput")
                && "preserved".equals(
                    stack.getCompoundTag("tag")
                        .getString("custom")),
            "actual migration preserves quantity, long storage count and unrelated paid-output/item NBT");
    }

    private static void foreignNameRejectedBeforeMutation() {
        String name = "gtsr_native_contract_foreign";
        Fluid foreign = new Fluid(name);
        check(FluidRegistry.registerFluid(foreign), "foreign fluid fixture registration");
        Materials facade = new MaterialBuilder().setName(name)
            .setDefaultLocalName("Foreign collision fixture")
            .constructMaterial();
        boolean rejected = false;
        try {
            ProsperityNativeFluids.register(facade, true, 777);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(
            rejected && FluidRegistry.getFluid(name) == foreign
                && foreign.getTemperature() == 300
                && !foreign.isGaseous()
                && facade.mGas == null
                && facade.mFluid == null
                && !Materials.FLUID_MAP.containsKey(foreign),
            "same-name foreign registration rejected without mutation");
    }

    /** Kept for shared fixture callers; registration no longer reads or creates any config/temp directory. */
    public static void cleanupFixture() {}

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256")
            .digest(bytes)) text.append(String.format("%02x", value & 255));
        return text.toString();
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
