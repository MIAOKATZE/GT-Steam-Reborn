// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.fluid;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialMaterialColors;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialRecipeLedger;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdPreflight;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdRegistration;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdSchema;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.MaterialBuilder;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTUtility;

/**
 * Real .277 table, MaterialBuilder, GTFluidFactory and production handler contracts.
 * Runs in a fresh JVM: default = fresh config, argument legacy = complete synthetic legacy source with acid705.
 * GT item generation and the full FML lifecycle are deliberately outside this fixture; no dummy dust/cell is
 * registered to make the production item-generation audit pass. Native IC2 container tests use the shared real-cell
 * fixture.
 */
public final class NativeFluidRegistrationContracts {

    private static boolean registered;
    private static int checks;
    private static Path directory;
    private static final List<Materials> FLUID_FACADES = new ArrayList<>();

    private NativeFluidRegistrationContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, NativeFluidRegistrationContracts.class.getName(), args)) return;
        try {
            bootstrapRegistrations(args.length > 0 && "legacy".equals(args[0]));
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
            System.out.println("PASS NativeFluidRegistrationContracts checks=" + checks);
            System.out.println(
                "BOUNDARY: real .277 materials/fluids and IC2 cells; no GT generated-item, world, canner GUI or FML lifecycle claim");
        } finally {
            cleanupFixture();
        }
    }

    /** Shared by consumer fixtures; no production config/world path is read or modified. */
    public static void bootstrapRegistrations() throws Exception {
        bootstrapRegistrations(false);
    }

    private static void bootstrapRegistrations(boolean legacy) throws Exception {
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
            MaterialIdPreflight.sha256(Files.readAllBytes(jar))
                .equals("88a0ac96a01e11fa04f738784722ecf27d4c72488973ff41eeb1399a167801e7"),
            "exact GT .277 artifact");
        Class<?> tableType = Class.forName("gregtech.api.enums.MaterialsIDMap");
        Constructor<?> constructor = tableType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object table = constructor.newInstance();
        tableType.getMethod("register")
            .invoke(table);
        Map<Integer, String> occupied = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) table).entrySet())
            occupied.put((Integer) entry.getKey(), ((Materials) entry.getValue()).mName);
        check(Materials.DistilledWater.mMetaItemSubID == 705, "native slot705 retains distilled water");
        directory = Files.createTempDirectory("gtsr-native-fluid-contract-");
        Path source = directory.resolve("industrial-material-ids.cfg");
        byte[] sourceBytes = null;
        if (legacy) {
            List<MaterialIdPreflight.Definition> definitions = new ArrayList<>();
            for (IndustrialRecipeLedger.MaterialDefinition definition : IndustrialRecipeLedger.get().materials)
                definitions.add(
                    new MaterialIdPreflight.Definition(
                        definition.id,
                        definition.name,
                        definition.introducedVersion > 0,
                        false));
            definitions.addAll(GTSRProsperityAirMaterials.cellIdDefinitions());
            MaterialIdPreflight.Result allocation = MaterialIdPreflight
                .inspect(null, true, definitions, occupied, new HashMap<>());
            allocation.requireValid("synthetic old config allocation");
            allocation.ids.setProperty("planned:native_acid_cut", "705");
            sourceBytes = MaterialIdPreflight.encode(allocation, "5.09.54.265");
            Files.write(source, sourceBytes);
        }
        ProsperityIndustrialMaterials.configure(source.toFile());
        new GTSRProsperityAirMaterials().onMaterialsInit();
        new ProsperityIndustrialMaterials().onMaterialsInit();
        // Native Materials.init performs these two operations after invoking addon handlers.
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
        check(GregTechAPI.sGeneratedMaterials[705] == Materials.DistilledWater, "real native final slot705 ownership");
        MaterialIdSchema.Identity identity = MaterialIdRegistration.startupIdentity();
        check(
            identity.activeDustIds.size() == 42 && MaterialIdRegistration.snapshot()
                .size() == 42,
            "only42 active dust IDs");
        check(
            legacy ? identity.legacyFluidAliases.size() == 62 : identity.legacyFluidAliases.isEmpty(),
            "safe legacy aliases are explicit and absent on fresh config");
        if (legacy) {
            check(
                identity.conflictedFluidIds.size() == 1
                    && identity.conflictedFluidIds.get("planned:native_acid_cut") == 705,
                "acid705 remains quarantined evidence");
            check(Arrays.equals(sourceBytes, Files.readAllBytes(source)), "legacy source remains byte-identical");
        } else check(!Files.exists(source), "fresh configuration does not manufacture old source evidence");
        byte[] published = Files.readAllBytes(directory.resolve("industrial-material-ids-v2.cfg"));
        MaterialIdRegistration.prepareEarlyRegistration();
        check(
            Arrays.equals(published, Files.readAllBytes(directory.resolve("industrial-material-ids-v2.cfg"))),
            "repeated startup reservation leaves published bytes unchanged");
        int dust = 0;
        Set<String> names = new HashSet<>();
        for (IndustrialRecipeLedger.MaterialDefinition definition : IndustrialRecipeLedger.get().materials) {
            Materials material = ProsperityIndustrialMaterials.get(definition.id);
            check(Materials.get(definition.name) == material, "public facade identity: " + definition.id);
            if ("dust".equals(definition.phase)) {
                check(
                    material.mMetaItemSubID == identity.activeDustIds.get(definition.id)
                        && GregTechAPI.sGeneratedMaterials[material.mMetaItemSubID] == material
                        && material.hasDustItems()
                        && !material.hasCell(),
                    "native dust registration: " + definition.id);
                dust++;
            } else {
                check(
                    material.mMetaItemSubID == MaterialIdRegistration.legacyFluidId("planned", definition.id),
                    "numeric identity only when safe legacy alias: " + definition.id);
                checkFluid(
                    material,
                    "gas".equals(definition.phase),
                    definition.temperatureK,
                    IndustrialMaterialColors.getARGB(definition.id) & 0xffffff,
                    names);
            }
        }
        Materials[] raw = { GTSRProsperityAirMaterials.WastesSigh, GTSRProsperityAirMaterials.ThickGrease,
            GTSRProsperityAirMaterials.MetalGrit, GTSRProsperityAirMaterials.UmbralMire,
            GTSRProsperityAirMaterials.SanzuResidualSteam, GTSRProsperityAirMaterials.WitheredBreath };
        String[] rawNames = { "wastesigh", "thickgrease", "metalgrit", "umbralmire", "sanzu_residual_steam",
            "withered_breath" };
        int[] colors = { 0xb08a4a, 0x4a3f28, 0x96613d, 0x2c251d, 0xa8c8d8, 0x8a7a52 };
        for (int i = 0; i < raw.length; i++) {
            check(
                rawNames[i].equals(raw[i].mName)
                    && raw[i].mMetaItemSubID == MaterialIdRegistration.legacyFluidId("air", rawNames[i]),
                "raw gas stable name and optional numeric alias");
            checkFluid(raw[i], true, 295, colors[i], names);
        }
        checkFluid(GTSRProsperityAirMaterials.AbyssalObsession, false, 295, 0x234a6b, names);
        check(
            "abyssal_obsession".equals(GTSRProsperityAirMaterials.AbyssalObsession.mName)
                && GTSRProsperityAirMaterials.AbyssalObsession.mMetaItemSubID == -1,
            "abyssal stable liquid never receives ID");
        check(
            dust == 42 && names.size() == 64 && FLUID_FACADES.size() == 64,
            "exact42 dust and64 independent fluid identities");
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
            "original autogenerated still texture and native flowing fallback: " + material.mName);
        check((gas ? material.mFluid : material.mGas) == null, "facade retains only its original phase");
        check(
            !material.hasCorrespondingFluid() && !material.hasCorrespondingGas() && !material.mHasGas,
            "GT automatic duplicate fluid paths disabled: " + material.mName);
        check(
            material.hasCell() == (material.mMetaItemSubID >= 0),
            "GT cells exist only as explicit historical aliases");
        FLUID_FACADES.add(material);
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

    /** Remove only this fixture's created temporary tree, including native registration locks/configs. */
    public static void cleanupFixture() throws Exception {
        if (directory == null) return;
        Path target = directory.toAbsolutePath()
            .normalize();
        Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir"))
            .toRealPath();
        if (Files.isSymbolicLink(target) || !target.toRealPath()
            .getParent()
            .equals(tempRoot)
            || !target.getFileName()
                .toString()
                .startsWith("gtsr-native-fluid-contract-"))
            throw new java.io.IOException(
                "Refusing fixture cleanup outside its created temporary directory: " + target);
        List<Path> owned;
        // One finite cleanup of the fixture-owned tree; no game paths or running-world data are inspected.
        try (Stream<Path> paths = Files.walk(target)) {
            owned = paths.sorted(
                Comparator.comparingInt(Path::getNameCount)
                    .reversed())
                .collect(Collectors.toList());
        }
        for (Path path : owned) {
            if (!path.toAbsolutePath()
                .normalize()
                .startsWith(target) || Files.isSymbolicLink(path))
                throw new java.io.IOException("Unexpected path in fixture cleanup: " + path);
        }
        for (Path path : owned) Files.delete(path);
        directory = null;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
