// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.compatibility;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.postea.api.IDExtenderCompat;
import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.gtnewhorizons.postea.utility.IDRegistry;
import com.gtnewhorizons.postea.utility.TransformerRegistry;

import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;

/** Real Postea dispatch and vanilla target items; synthetic NBT only, no worlds or instance access. */
public final class ProsperityPosteaMigrationContracts {

    private static int checks;

    private ProsperityPosteaMigrationContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        java.lang.reflect.Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, ProsperityPosteaMigrationContracts.class.getName(), args)) return;
        run();
        System.out.println(
            "PASS Postea prosperity migration: " + checks + " checks; synthetic NBT; no game/world verification");
    }

    private static void run() throws Exception {
        Materials nativeWater = Materials.DistilledWater; // Finish native table initialization before choosing a free
                                                          // slot.
        int free = 999;
        while (free >= 0 && GregTechAPI.sGeneratedMaterials[free] != null) free--;
        check(free >= 0, "fixture requires a genuinely unoccupied GT slot");
        Map<Integer, ItemStack> rules = new HashMap<>();
        rules.put(free, new ItemStack(Items.stick, 1));
        rules.put(1000 + free, new ItemStack(Items.paper, 1));
        rules.put(2000 + free, new ItemStack(Items.redstone, 1));
        ItemStack fluid = new ItemStack(Items.bucket, 1);
        NBTTagCompound template = new NBTTagCompound(), contents = new NBTTagCompound();
        contents.setString("FluidName", "synthetic_postea_fluid");
        contents.setInteger("Amount", 1000);
        template.setTag("Fluid", contents);
        fluid.setTagCompound(template);
        rules.put(30000 + free, fluid);
        rules.put(30705, fluid); // Deliberately try the unsafe rule: the production handler must still refuse it.
        Materials previousWater = GregTechAPI.sGeneratedMaterials[705];
        GregTechAPI.sGeneratedMaterials[705] = nativeWater;
        Loader loader = Loader.instance();
        Field controllerField = Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object previousController = controllerField.get(loader);
        LoadController controller = new LoadController(loader);
        Field stateField = LoadController.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(controller, LoaderState.SERVER_ABOUT_TO_START);
        controllerField.set(loader, controller);
        try {
            ItemStackReplacementManager.addTransformationHandler(
                ProsperityPosteaMigration.ORIGINAL,
                (name, tag) -> ProsperityPosteaMigration.transform(name, tag, rules));
            Map<String, Integer> registry = new HashMap<>();
            registry.put("\u0002" + ProsperityPosteaMigration.ORIGINAL, 1000);
            IDRegistry.registerWorldSpecificIDs(registry);
            TransformerRegistry.onIdMappingsChanged();
            for (int prefix = 0; prefix < 3; prefix++) {
                NBTTagCompound stack = stack(prefix * 1000 + free);
                stack.setInteger("IntSlot", 9);
                stack.setInteger("Count", 50000);
                NBTTagCompound before = (NBTTagCompound) stack.copy();
                TransformerRegistry.transformItem(stack);
                check(
                    IDExtenderCompat.getItemStackID(stack) == Item.getIdFromItem(
                        rules.get(prefix * 1000 + free)
                            .getItem()),
                    "exact tiny/small/full dust prefix selects corresponding registered target via Postea");
                stack.setShort("id", (short) 1000);
                stack.setShort("Damage", (short) (prefix * 1000 + free));
                check(stack.equals(before), "GT int Count, paid-output NBT and IntSlot preserved");
            }
            NBTTagCompound ae = stack(30000 + free);
            ae.setByte("Count", (byte) 0);
            ae.setLong("Cnt", 5000000000L);
            ae.setLong("Req", 8000000000L);
            ae.setLong("ReqMade", 7000000000L);
            ae.setBoolean("Craft", true);
            ae.setString("StackType", "item");
            ae.getCompoundTag("tag")
                .setString("custom", "preserved");
            TransformerRegistry.transformItem(ae);
            check(
                ae.getCompoundTag("tag")
                    .getCompoundTag("Fluid")
                    .equals(contents),
                "native target Fluid NBT merged");
            check(
                ae.getLong("Cnt") == 5000000000L && ae.getLong("Req") == 8000000000L
                    && ae.getLong("ReqMade") == 7000000000L
                    && ae.getBoolean("Craft")
                    && ae.getByte("Count") == 0,
                "AE/pattern long counts and flags never narrowed");
            check(
                ae.getCompoundTag("tag")
                    .getString("custom")
                    .equals("preserved"),
                "unrelated item NBT preserved");
            NBTTagCompound once = (NBTTagCompound) ae.copy();
            TransformerRegistry.transformItem(ae);
            check(ae.equals(once), "target identity is idempotent under later Postea loads");
            NBTTagCompound water = stack(30705), beforeWater = (NBTTagCompound) water.copy();
            TransformerRegistry.transformItem(water);
            check(
                water.equals(beforeWater),
                "future native water705 is never rewritten even with an unsafe requested rule");
            NBTTagCompound customFluid = stack(30000 + free);
            customFluid.getCompoundTag("tag")
                .setString("Fluid", "another payload");
            NBTTagCompound beforeConflict = (NBTTagCompound) customFluid.copy();
            TransformerRegistry.transformItem(customFluid);
            check(customFluid.equals(beforeConflict), "existing conflicting Fluid payload stays unchanged");
            NBTTagCompound unlisted = stack(31000 + free), beforeUnlisted = (NBTTagCompound) unlisted.copy();
            TransformerRegistry.transformItem(unlisted);
            check(unlisted.equals(beforeUnlisted), "unlisted cellPlasma prefix remains untouched");
            NBTTagCompound extendedId = stack(free);
            extendedId.setInteger("idExt", 1000);
            TransformerRegistry.transformItem(extendedId);
            check(
                !extendedId.hasKey("idExt")
                    && IDExtenderCompat.getItemStackID(extendedId) == Item.getIdFromItem(Items.stick),
                "replaced small item ID cannot retain stale idExt");
        } finally {
            controllerField.set(loader, previousController);
            GregTechAPI.sGeneratedMaterials[705] = previousWater;
        }
        Properties cfg = new Properties();
        cfg.setProperty("a", "5");
        cfg.setProperty("b", "6");
        check(
            ProsperityPosteaMigration.mapping(cfg, new HashSet<>(Arrays.asList("a", "b")))
                .size() == 2,
            "exact historical source keys parsed without registration side effects");
        cfg.setProperty("b", "5");
        rejected(
            () -> ProsperityPosteaMigration.mapping(cfg, new HashSet<>(Arrays.asList("a", "b"))),
            "duplicate IDs rejected");
        cfg.setProperty("b", "1000");
        rejected(
            () -> ProsperityPosteaMigration.mapping(cfg, new HashSet<>(Arrays.asList("a", "b"))),
            "out of range source rejected");
        cfg.remove("b");
        Map<String, Integer> subset = ProsperityPosteaMigration.mapping(cfg, new HashSet<>(Arrays.asList("a", "b")));
        check(
            subset.size() == 1 && subset.get("a") == 5 && !subset.containsKey("b"),
            "historical subset preserves exact known IDs without inventing later missing materials");
        cfg.setProperty("a", "not-a-number");
        rejected(
            () -> ProsperityPosteaMigration.mapping(cfg, new HashSet<>(Arrays.asList("a", "b"))),
            "invalid known entry still rejects optional migration");
        cfg.setProperty("a", "5");
        cfg.setProperty("unknown", "7");
        rejected(
            () -> ProsperityPosteaMigration.mapping(cfg, new HashSet<>(Arrays.asList("a", "b"))),
            "unknown historical keys cannot become migration rules");
        legacyV2Contracts();
        Path missing = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "gtsr-absent-postea-" + java.util.UUID.randomUUID(),
            "industrial-material-ids.cfg");
        Path missingV2 = missing.resolveSibling("industrial-material-ids-v2.cfg");
        check(!Files.exists(missing.getParent()), "both historical input fixtures start absent");
        ProsperityPosteaMigration.register(missing);
        check(
            !Files.exists(missing) && !Files.exists(missingV2) && !Files.exists(missing.getParent()),
            "missing v1 and v2 inputs return normally and never create files or their parent");
    }

    private static void legacyV2Contracts() throws Exception {
        Path scratch = Files.createTempDirectory("gtsr-postea-old-input-");
        Path v1 = scratch.resolve("industrial-material-ids.cfg"),
            v2 = scratch.resolve("industrial-material-ids-v2.cfg");
        java.util.Set<String> dust = new HashSet<>(Arrays.asList("planned:dust_fixture", "planned:later_dust"));
        java.util.Set<String> fluids = new HashSet<>(Arrays.asList("planned:fluid_fixture", "air.wastesigh"));
        java.util.Set<String> expected = new HashSet<>(dust);
        expected.addAll(fluids);
        try {
            Properties old = new Properties();
            old.setProperty("active:planned:dust_fixture", "17");
            old.setProperty("retired:planned:fluid_fixture", "18");
            old.setProperty("retired:air.wastesigh", "19");
            old.setProperty("alias:planned:fluid_fixture", "18");
            old.setProperty("__gtsr.schema", "2");
            old.setProperty("__gtsr.mapping-sha256", "ignored-old-input-metadata");
            old.setProperty("active:planned:unknown", "42");
            old.setProperty("active:planned:fluid_fixture", "99");
            try (java.io.OutputStream output = Files.newOutputStream(v2)) {
                old.store(output, "Synthetic historical Properties input only");
            }
            byte[] originalV2 = Files.readAllBytes(v2);
            check(
                new String(originalV2, java.nio.charset.StandardCharsets.ISO_8859_1)
                    .contains("active\\:planned\\:dust_fixture=17"),
                "native Properties serialization escapes both v2 namespace colons");
            check(
                ProsperityPosteaMigration.migrationInput(v1)
                    .equals(v2),
                "missing v1 selects sibling historical v2");
            Properties extracted = ProsperityPosteaMigration
                .extractV2(ProsperityPosteaMigration.read(v2), dust, fluids);
            Map<String, Integer> parsed = ProsperityPosteaMigration.mapping(extracted, expected);
            check(
                parsed.size() == 3 && parsed.get("planned:dust_fixture") == 17
                    && parsed.get("planned:fluid_fixture") == 18
                    && parsed.get("air.wastesigh") == 19
                    && !parsed.containsKey("planned:later_dust"),
                "v2 active dust and retired fluid/air exact subset extracted without guessing later keys");
            check(
                !extracted.containsKey("__gtsr.schema") && !extracted.containsKey("alias:planned:fluid_fixture")
                    && !extracted.containsKey("planned:unknown"),
                "v2 metadata aliases and unknown targets do not restore old machinery");
            Properties onlyActive = new Properties();
            onlyActive.setProperty("active:planned:dust_fixture", "17");
            check(
                ProsperityPosteaMigration
                    .mapping(ProsperityPosteaMigration.extractV2(onlyActive, dust, fluids), expected)
                    .size() == 1,
                "v2 with active powder entries only is a valid historical subset");
            Files.write(v1, "planned\\:dust_fixture=23\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            byte[] originalV1 = Files.readAllBytes(v1);
            check(
                ProsperityPosteaMigration.migrationInput(v1)
                    .equals(v1),
                "existing v1 takes priority without merging sibling v2");
            Map<String, Integer> preferred = ProsperityPosteaMigration
                .mapping(ProsperityPosteaMigration.read(v1), expected);
            check(
                preferred.size() == 1 && preferred.get("planned:dust_fixture") == 23
                    && !preferred.containsKey("planned:fluid_fixture"),
                "v1 selection never fills its missing fields from v2");
            check(
                Arrays.equals(originalV1, Files.readAllBytes(v1)) && Arrays.equals(originalV2, Files.readAllBytes(v2)),
                "both historical input files remain byte-identical after selection and parsing");
        } finally {
            Files.deleteIfExists(v1);
            Files.deleteIfExists(v2);
            Files.deleteIfExists(scratch);
        }
    }

    private static NBTTagCompound stack(int damage) {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setShort("id", (short) 1000);
        stack.setShort("Damage", (short) damage);
        stack.setByte("Count", (byte) 64);
        stack.setTag("tag", new NBTTagCompound());
        return stack;
    }

    private interface Operation {

        void run();
    }

    private static void rejected(Operation operation, String message) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
