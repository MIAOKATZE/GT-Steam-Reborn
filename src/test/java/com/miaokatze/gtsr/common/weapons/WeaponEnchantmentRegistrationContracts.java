// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.weapons;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.config.Configuration;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.gtnewhorizons.postea.utility.IDRegistry;
import com.gtnewhorizons.postea.utility.TransformerRegistry;

import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import gregtech.api.enchants.EnchantmentHazmat;
import gregtech.api.util.GTConfig;

/** Native GT config, Enchantment constructors and Postea dispatch; synthetic NBT, no worlds or instance edits. */
public final class WeaponEnchantmentRegistrationContracts {

    private static final String[] NAMES = { "piercing", "incendiary", "hollowPoint", "economy", "destruction",
        "diffusion", "duration", "tearing", "quenching" };
    private static int checks;

    private WeaponEnchantmentRegistrationContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        java.lang.reflect.Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, WeaponEnchantmentRegistrationContracts.class.getName(), args)) return;
        java.lang.reflect.Method gt = Class.forName("com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts")
            .getDeclaredMethod("bootstrapGt");
        gt.setAccessible(true);
        gt.invoke(null);
        run();
        if (!ForgeReflectionTransformer.transformed)
            throw new AssertionError("The Java-17 Forge reflection compatibility transformer was not installed");
        System.out.println(
            "PASS weapon enchantment registration: " + checks
                + " checks; native GT config/Enchantment/Postea; synthetic NBT; world_access=false");
    }

    private static void run() throws Exception {
        Path scratch = Files.createTempDirectory("gtsr-enchantment-contract-");
        Configuration previousConfig = GTConfig.sConfigFileIDs;
        Enchantment[] previousTable = Enchantment.enchantmentsList;
        // The contract launcher does not apply EndlessIDs' table-extension mixin. Model its 32768
        // legal slots in this isolated process; registration still executes Minecraft's real constructor.
        replaceTable(Arrays.copyOf(previousTable, Math.max(previousTable.length, 32768)));
        try {
            Enchantment foreign = new ForeignEnchantment(32767);
            Path official = scratch.resolve("IDs.cfg");
            GTConfig.sConfigFileIDs = new Configuration(official.toFile());
            int[] ids = WeaponEnchantments.configuredIds();
            for (int i = 0; i < ids.length; i++) {
                check(ids[i] == 240 + i, "audited explicit default " + NAMES[i]);
                check(
                    GTConfig.sConfigFileIDs.hasKey("enchantments", "GTSR." + NAMES[i]),
                    "native GT category/key exists " + NAMES[i]);
            }
            GTConfig.sConfigFileIDs.getCategory("enchantments")
                .get("GTSR.piercing")
                .set(249);
            GTConfig.sConfigFileIDs.save();
            GTConfig.sConfigFileIDs = new Configuration(official.toFile());
            check(
                WeaponEnchantments.configuredIds()[0] == 249,
                "GTConfig reads an explicit operator override after real Forge file reload");
            GTConfig.sConfigFileIDs.getCategory("enchantments")
                .get("GTSR.piercing")
                .set(240);
            GTConfig.sConfigFileIDs.save();
            Enchantment occupied = new ForeignEnchantment(240);
            try {
                WeaponEnchantments.init();
                throw new AssertionError("native collision accepted");
            } catch (IllegalStateException expected) {
                check(
                    expected.getMessage()
                        .contains("GregTech/IDs.cfg")
                        && expected.getMessage()
                            .contains("GTSR.piercing=240"),
                    "native collision names actual official config/key and refuses fallback");
                check(
                    WeaponEnchantments.piercing == null && Enchantment.enchantmentsList[240] == occupied,
                    "complete collision check precedes any of the nine native registrations");
            }
            Enchantment.enchantmentsList[240] = null; // Remove only the synthetic collision in this isolated process.
            int[] duplicate = ids.clone();
            duplicate[1] = duplicate[0];
            try {
                WeaponEnchantments.requireAvailable(duplicate);
                throw new AssertionError("duplicate configured enchantments accepted");
            } catch (IllegalStateException expected) {
                check(
                    expected.getMessage()
                        .contains("GTSR.incendiary=240"),
                    "duplicate official ID configuration identifies the second conflicting key");
            }
            WeaponEnchantments.init();
            WeaponEnchantments[] actual = { WeaponEnchantments.piercing, WeaponEnchantments.incendiary,
                WeaponEnchantments.hollowPoint, WeaponEnchantments.economy, WeaponEnchantments.destruction,
                WeaponEnchantments.diffusion, WeaponEnchantments.duration, WeaponEnchantments.tearing,
                WeaponEnchantments.quenching };
            for (int i = 0; i < actual.length; i++) check(
                actual[i].effectId == 240 + i && Enchantment.enchantmentsList[240 + i] == actual[i],
                "native constructor owns declared default " + NAMES[i]);
            check(
                Enchantment.enchantmentsList[32767] == foreign,
                "pre-existing 32767 claim remains untouched; defaults never compete with Tofu's reported slot");
            WeaponEnchantments.init();
            check(WeaponEnchantments.piercing == actual[0], "postInit compatibility call remains idempotent");
            if (Enchantment.enchantmentsList[13] == null) new EnchantmentHazmat();
            check(
                Enchantment.enchantmentsList[13] instanceof EnchantmentHazmat,
                "real native GT Hazmat is present for the non-target preservation contract");
            migration(scratch);
        } finally {
            GTConfig.sConfigFileIDs = previousConfig;
            replaceTable(previousTable);
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(scratch)) {
                for (Path file : files) Files.delete(file);
            }
            Files.delete(scratch);
        }
    }

    private static void migration(Path scratch) throws Exception {
        Path source = scratch.resolve("old-enchantments.cfg");
        Configuration old = new Configuration(source.toFile());
        for (int i = 0; i < NAMES.length; i++) old.get("ids", NAMES[i], 32767 - i);
        old.save();
        byte[] beforeCfg = Files.readAllBytes(source);
        check(
            Arrays.equals(
                WeaponEnchantments.legacyIds(source),
                new int[] { 32767, 32766, 32765, 32764, 32763, 32762, 32761, 32760, 32759 }),
            "historical Forge cfg is parsed through the native parser without reallocating its IDs");
        WeaponEnchantments.registerLegacyTransformations(source);
        check(
            Arrays.equals(beforeCfg, Files.readAllBytes(source)),
            "migration reads original cfg byte-for-byte unchanged");
        Path protectedSource = scratch.resolve("native-id-in-old.cfg");
        Configuration nativeSource = new Configuration(protectedSource.toFile());
        for (int i = 0; i < NAMES.length; i++) nativeSource.get("ids", NAMES[i], i == 0 ? 13 : 32767 - i);
        nativeSource.save();
        WeaponEnchantments.registerLegacyTransformations(protectedSource);
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
            Map<String, Integer> registry = new HashMap<>();
            registry.put("\u0002gtsr:PortableLM12", 1000);
            registry.put("\u0002gtsr:PortableT20", 1001);
            registry.put("\u0002gtsr:PortableQLZ04", 1002);
            registry.put("\u0002gtsr:PortableSingularity", 1003);
            registry.put("\u0002gregtech:gt.metatool.01", 1004);
            registry.put("\u0002other:weapon", 1005);
            IDRegistry.registerWorldSpecificIDs(registry);
            TransformerRegistry.onIdMappingsChanged();
            for (int weapon = 1000; weapon <= 1002; weapon++) {
                NBTTagCompound stack = stack(weapon, 32766, 3, 32764, 5);
                NBTTagCompound expected = (NBTTagCompound) stack.copy();
                enchantments(expected).getCompoundTagAt(0)
                    .setShort("id", (short) 241);
                enchantments(expected).getCompoundTagAt(1)
                    .setShort("id", (short) 243);
                TransformerRegistry.transformItem(stack);
                check(
                    stack.equals(expected),
                    "official Postea dispatch changes only proved own weapon ench IDs " + weapon);
                NBTTagCompound once = (NBTTagCompound) stack.copy();
                TransformerRegistry.transformItem(stack);
                check(stack.equals(once), "loaded current weapon is stable on second Postea pass " + weapon);
            }
            NBTTagCompound singularity = stack(1003, 32763, 4, 32764, 5);
            TransformerRegistry.transformItem(singularity);
            check(
                enchantments(singularity).getCompoundTagAt(0)
                    .getShort("id") == 244
                    && enchantments(singularity).getCompoundTagAt(1)
                        .getShort("id") == 243,
                "singularity-only enchantment and shared economy migrate through exact own item name");
            unchanged(
                stack(1000, 32767, 3),
                "currently occupied foreign 32767 is never migrated even on the exact own weapon");
            NBTTagCompound inapplicable = stack(1003, 32766, 3);
            unchanged(inapplicable, "non-applicable historic combat enchantment is not relabeled on singularity");
            unchanged(stack(1004, 32767, 3), "native GT tool enchantment data is outside all own-item handlers");
            unchanged(stack(1005, 32767, 3), "other-mod weapon with same legacy number is untouched");
            NBTTagCompound natives = stack(1000, 13, 1, Enchantment.sharpness.effectId, 3);
            unchanged(natives, "native GT Hazmat and vanilla sharpness IDs/levels stay unchanged on own weapon");
            NBTTagCompound malformed = stack(1000, 32766, 3);
            enchantments(malformed).getCompoundTagAt(0)
                .setInteger("id", 32766);
            unchanged(malformed, "unreviewed enchantment encoding remains unchanged");
            new ForeignEnchantment(32766); // Claim the old slot only AFTER native Postea rules were registered.
            NBTTagCompound late = stack(1000, 32766, 3, 32764, 5);
            NBTTagCompound expectedLate = (NBTTagCompound) late.copy();
            enchantments(expectedLate).getCompoundTagAt(1)
                .setShort("id", (short) 243);
            TransformerRegistry.transformItem(late);
            check(
                late.equals(expectedLate),
                "late foreign occupation stops the same old32766 transformation while safe economy still migrates");
        } finally {
            controllerField.set(loader, previousController);
        }
        Path partial = scratch.resolve("partial.cfg");
        Files.write(partial, "ids {\n I:piercing=32767\n}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] before = Files.readAllBytes(partial);
        try {
            WeaponEnchantments.legacyIds(partial);
            throw new AssertionError("partial historical mapping accepted");
        } catch (java.io.IOException expected) {
            check(
                Arrays.equals(before, Files.readAllBytes(partial)),
                "partial historical cfg is refused without modifying it");
        }
    }

    private static void unchanged(NBTTagCompound stack, String message) {
        NBTTagCompound before = (NBTTagCompound) stack.copy();
        TransformerRegistry.transformItem(stack);
        check(stack.equals(before), message);
    }

    private static NBTTagCompound stack(int itemId, int... idAndLevel) {
        NBTTagCompound stack = new NBTTagCompound(), tag = new NBTTagCompound();
        stack.setShort("id", (short) itemId);
        stack.setShort("Damage", (short) 42);
        stack.setInteger("Count", 50000);
        stack.setLong("Cnt", 5000000000L);
        stack.setLong("Req", 8000000000L);
        stack.setInteger("IntSlot", 9);
        tag.setString("custom", "preserved");
        tag.setInteger("paidAmmo", 123);
        NBTTagList ench = new NBTTagList();
        for (int index = 0; index < idAndLevel.length; index += 2) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setShort("id", (short) idAndLevel[index]);
            entry.setShort("lvl", (short) idAndLevel[index + 1]);
            entry.setString("extra", "preserved");
            ench.appendTag(entry);
        }
        tag.setTag("ench", ench);
        stack.setTag("tag", tag);
        return stack;
    }

    private static NBTTagList enchantments(NBTTagCompound stack) {
        return stack.getCompoundTag("tag")
            .getTagList("ench", 10);
    }

    private static void replaceTable(Enchantment[] table) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Field field = Enchantment.class.getDeclaredField("enchantmentsList");
        Object base = unsafeClass.getMethod("staticFieldBase", Field.class)
            .invoke(unsafe, field);
        long offset = (Long) unsafeClass.getMethod("staticFieldOffset", Field.class)
            .invoke(unsafe, field);
        unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class)
            .invoke(unsafe, base, offset, table);
    }

    private static final class ForeignEnchantment extends Enchantment {

        ForeignEnchantment(int id) {
            super(id, 1, EnumEnchantmentType.weapon);
        }
    }

    /**
     * Supply the two lwjgl3ify ForgePatchTransformer behaviors needed by native addToBookList.
     * Forge's Java-8 setup depends on removed sun.reflect accessors and Field.modifiers. Upstream
     * lwjgl3ify makes setup a no-op and delegates reference writes to its actual UnsafeHacks.setField.
     * Only this isolated launcher is transformed: native Enchantment constructors, addToBookList,
     * book-list contents and production code stay unchanged. This is not a general enum-extension launcher.
     */
    public static final class ForgeReflectionTransformer implements IClassTransformer {

        static boolean transformed;

        @Override
        public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (!"net.minecraftforge.common.util.EnumHelper".equals(transformedName)) return bytes;
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int patched = 0;
            for (MethodNode method : node.methods) {
                boolean setup = "setup".equals(method.name) && "()V".equals(method.desc);
                boolean write = "setFailsafeFieldValue".equals(method.name)
                    && "(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V".equals(method.desc);
                if (!setup && !write) continue;
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables = null;
                if (write) {
                    method.visitVarInsn(Opcodes.ALOAD, 0);
                    method.visitVarInsn(Opcodes.ALOAD, 1);
                    method.visitVarInsn(Opcodes.ALOAD, 2);
                    method.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "me/eigenraven/lwjgl3ify/UnsafeHacks",
                        "setField",
                        method.desc,
                        false);
                }
                method.visitInsn(Opcodes.RETURN);
                method.maxStack = write ? 3 : 0;
                method.maxLocals = write ? 3 : 0;
                patched++;
            }
            if (patched != 2) throw new IllegalStateException("Unexpected native Forge EnumHelper method shape");
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            transformed = true;
            return writer.toByteArray();
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
