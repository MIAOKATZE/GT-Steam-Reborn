// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.weapons;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.config.Configuration;

import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.ConfigCategories;
import gregtech.api.util.GTConfig;

/** Native Enchantment registration using GT's official ID configuration; no free-slot allocator. */
public final class WeaponEnchantments extends Enchantment {

    public static WeaponEnchantments piercing, incendiary, hollowPoint, economy, destruction, diffusion, duration,
        tearing, quenching;
    private final int category;
    private static final String[] NAMES = { "piercing", "incendiary", "hollowPoint", "economy", "destruction",
        "diffusion", "duration", "tearing", "quenching" };
    /*
     * GTNH 2.9.2 config audit: GT=13..15, CoFH=103/104, Battlegear=126..132,
     * TC=153/154, TT=166..173/220..225, DE=180, Railcraft=190..192, Biblio=196/197,
     * Gadomancy=200, OpenBlocks=211..213, Et Futurum=226..228. No configured enchantment
     * claims 240..248. These explicit defaults fit vanilla's 256 slots, leave Tofu's 32767
     * untouched, and remain editable in GregTech/IDs.cfg. They are not a universal reservation:
     * another pack must resolve an actual collision there; never scan or silently pick a new ID.
     */
    private static final int[] DEFAULT_IDS = { 240, 241, 242, 243, 244, 245, 246, 247, 248 };

    private WeaponEnchantments(int id, int category, String name) {
        super(id, 5, EnumEnchantmentType.weapon);
        this.category = category;
        setName("gtsr." + name);
    }

    public static void init() {
        if (piercing != null) return;
        if (GTConfig.sConfigFileIDs == null) throw new IllegalStateException(
            "Register GTSR enchantments from GregTechAPI.sAfterGTPreload after GT IDs initialize");
        int[] ids = configuredIds();
        requireAvailable(ids);
        piercing = new WeaponEnchantments(ids[0], 0, "piercing");
        incendiary = new WeaponEnchantments(ids[1], 1, "incendiary");
        hollowPoint = new WeaponEnchantments(ids[2], 2, "hollowPoint");
        economy = new WeaponEnchantments(ids[3], 3, "economy");
        destruction = new WeaponEnchantments(ids[4], 4, "destruction");
        diffusion = new WeaponEnchantments(ids[5], 5, "diffusion");
        duration = new WeaponEnchantments(ids[6], 6, "duration");
        tearing = new WeaponEnchantments(ids[7], 7, "tearing");
        quenching = new WeaponEnchantments(ids[8], 8, "quenching");
        for (WeaponEnchantments e : new WeaponEnchantments[] { destruction, diffusion, duration, tearing, quenching })
            Enchantment.addToBookList(e);
        Enchantment.addToBookList(piercing);
        Enchantment.addToBookList(incendiary);
        Enchantment.addToBookList(hollowPoint);
        Enchantment.addToBookList(economy);
        if (Loader.isModLoaded("postea")) {
            try {
                registerLegacyTransformations(
                    new File(
                        Loader.instance()
                            .getConfigDir(),
                        "gtsr-portable-enchantments.cfg").toPath());
            } catch (IOException | RuntimeException | LinkageError invalid) {
                GTSteamReborn.LOG.warn(
                    "[GTSR] Historical weapon enchantments unchanged; optional source cfg could not be used: "
                        + invalid.getMessage());
            }
        }
    }

    static int[] configuredIds() {
        int[] ids = new int[NAMES.length];
        for (int i = 0; i < ids.length; i++)
            ids[i] = GTConfig.addIDConfig(ConfigCategories.IDs.enchantments, "GTSR." + NAMES[i], DEFAULT_IDS[i]);
        return ids;
    }

    static void requireAvailable(int[] ids) {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            if (id < 0 || id >= Enchantment.enchantmentsList.length
                || !used.add(id)
                || Enchantment.enchantmentsList[id] != null)
                throw new IllegalStateException(
                    "GTSR enchantment ID conflict: GregTech/IDs.cfg [enchantments] GTSR." + NAMES[i]
                        + "="
                        + id
                        + "; choose an explicit non-conflicting ID; automatic reallocation is disabled");
        }
    }

    /** Old cfg is migration input only. Forge's parser runs on a disposable copy: it may rename malformed files. */
    static int[] legacyIds(Path source) throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source) || Files.size(source) > 1024 * 1024)
            throw new IOException("Unsafe or oversized historical weapon enchantment cfg");
        byte[] original = Files.readAllBytes(source);
        Path scratch = Files.createTempDirectory("gtsr-enchantment-read-");
        try {
            Path copy = scratch.resolve("legacy.cfg");
            Files.write(copy, original);
            Configuration cfg = new Configuration(copy.toFile());
            int[] ids = new int[NAMES.length];
            Set<Integer> used = new HashSet<>();
            for (int i = 0; i < ids.length; i++) {
                if (!cfg.hasKey("ids", NAMES[i]))
                    throw new IOException("Incomplete historical enchantment cfg: " + NAMES[i]);
                String value = cfg.getCategory("ids")
                    .get(NAMES[i])
                    .getString();
                if (!value.matches("[0-9]+")) throw new IOException("Invalid historical enchantment ID: " + NAMES[i]);
                ids[i] = Integer.parseInt(value);
                if (ids[i] > Short.MAX_VALUE || !used.add(ids[i]))
                    throw new IOException("Invalid/duplicate historical enchantment ID: " + NAMES[i]);
            }
            if (!java.util.Arrays.equals(original, Files.readAllBytes(source)))
                throw new IOException("Historical enchantment source changed while reading");
            return ids;
        } finally {
            try (java.nio.file.DirectoryStream<Path> copies = Files.newDirectoryStream(scratch)) {
                for (Path copy : copies) Files.delete(copy);
            }
            Files.delete(scratch);
        }
    }

    static void registerLegacyTransformations(Path source) throws IOException {
        if (!Files.exists(source)) return;
        int[] old = legacyIds(source);
        WeaponEnchantments[] current = { piercing, incendiary, hollowPoint, economy, destruction, diffusion, duration,
            tearing, quenching };
        Map<Integer, WeaponEnchantments> known = new HashMap<>();
        Set<Integer> currentIds = new HashSet<>();
        for (WeaponEnchantments enchantment : current) currentIds.add(enchantment.effectId);
        for (int i = 0; i < old.length; i++) {
            if (old[i] == current[i].effectId || currentIds.contains(old[i])) continue;
            // A permanent item-load rule cannot distinguish a historical GTSR enchantment from a
            // currently registered enchantment sharing that number, even on our own weapons.
            if (hasCurrentOwner(old[i])) continue;
            known.put(old[i], current[i]);
        }
        if (known.isEmpty()) return;
        for (WeaponKind kind : WeaponKind.values()) {
            String name = registryName(kind);
            ItemStackReplacementManager
                .addTransformationHandler(name, (original, stack) -> transformLegacy(original, stack, kind, known));
        }
    }

    private static boolean hasCurrentOwner(int id) {
        return id >= 0 && id < Enchantment.enchantmentsList.length && Enchantment.enchantmentsList[id] != null;
    }

    private static String registryName(WeaponKind kind) {
        switch (kind) {
            case LM12:
                return "gtsr:PortableLM12";
            case T20:
                return "gtsr:PortableT20";
            case QLZ04:
                return "gtsr:PortableQLZ04";
            case SINGULARITY:
                return "gtsr:PortableSingularity";
            default:
                throw new IllegalArgumentException("Unknown portable weapon");
        }
    }

    /** Official Postea item-load callback; only proved old IDs on our exact weapon registry names. */
    static boolean transformLegacy(String original, NBTTagCompound stack, WeaponKind kind,
        Map<Integer, WeaponEnchantments> known) {
        if (!registryName(kind).equals(original) || !stack.hasKey("tag", 10)) return false;
        NBTTagCompound tag = stack.getCompoundTag("tag");
        if (!tag.hasKey("ench", 9)) return false;
        NBTTagList ench = tag.getTagList("ench", 10);
        boolean changed = false;
        for (int i = 0; i < ench.tagCount(); i++) {
            NBTTagCompound entry = ench.getCompoundTagAt(i);
            if (!entry.hasKey("id", 2) || !entry.hasKey("lvl", 2)) continue;
            int old = entry.getShort("id");
            // Providers may register after our GT preload callback. Recheck the one exact slot on
            // every Postea invocation; never turn a later Tofu/other-mod enchantment into our old one.
            if (hasCurrentOwner(old)) continue;
            WeaponEnchantments target = known.get(old);
            if (target == null || !(target.category == 3
                || (target.category >= 4 ? kind == WeaponKind.SINGULARITY : kind != WeaponKind.SINGULARITY))) continue;
            entry.setShort("id", (short) target.effectId);
            changed = true;
        }
        return changed;
    }

    @Override
    public int getMaxLevel() {
        return category >= 3 ? 5 : 3;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 8 + level * 8;
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + 20;
    }

    @Override
    public boolean canApply(ItemStack s) {
        WeaponKind kind = PortableWeapons.kind(s);
        return kind != null
            && (category == 3 || (category >= 4 ? kind == WeaponKind.SINGULARITY : kind != WeaponKind.SINGULARITY));
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack s) {
        return canApply(s);
    }

    @Override
    public boolean canApplyTogether(Enchantment other) {
        if (other == this) return false;
        return !(other instanceof WeaponEnchantments) || category >= 3 || ((WeaponEnchantments) other).category >= 3;
    }

    public static int level(ItemStack s, WeaponEnchantments e) {
        return e == null || !e.canApply(s) ? 0
            : Math.max(0, Math.min(e.getMaxLevel(), EnchantmentHelper.getEnchantmentLevel(e.effectId, s)));
    }

    public static float damage(WeaponKind k, ItemStack s) {
        int ap = level(s, piercing), fire = ap == 0 ? level(s, incendiary) : 0,
            hp = ap == 0 && fire == 0 ? level(s, hollowPoint) : 0;
        return k.damage * (1 + .05f * (ap + fire) + .25f * hp);
    }

    public static float penetration(WeaponKind k, ItemStack s) {
        int ap = level(s, piercing), hp = ap == 0 && level(s, incendiary) == 0 ? level(s, hollowPoint) : 0;
        return k.armorPenetration * (1 + .2f * ap - .1f * hp);
    }
}
