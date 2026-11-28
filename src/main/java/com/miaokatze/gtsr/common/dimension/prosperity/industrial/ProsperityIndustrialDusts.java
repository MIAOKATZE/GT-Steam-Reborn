// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.miaokatze.gtsr.main.GTSteamReborn;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TextureSet;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/**
 * Official Forge item identities and GT ore associations, without GT numeric material slots.
 * Each ledger dust owns three distinct, stable gtsr registry names at item damage zero.
 */
public final class ProsperityIndustrialDusts {

    private static final OrePrefixes[] FORMS = { OrePrefixes.dust, OrePrefixes.dustSmall, OrePrefixes.dustTiny };
    private static final Map<String, Map<OrePrefixes, DustItem>> ITEMS = new LinkedHashMap<>();
    private static boolean bound;

    private ProsperityIndustrialDusts() {}

    /** Called from GTSR preInit, while Forge's active mod container is GTSR. */
    public static void registerItems() {
        if (!ITEMS.isEmpty()) return;
        IndustrialMaterialColors.verifyCoverage(IndustrialRecipeLedger.get());
        for (IndustrialRecipeLedger.MaterialDefinition definition : IndustrialRecipeLedger.get().materials) {
            if (!"dust".equals(definition.phase)) continue;
            Map<OrePrefixes, DustItem> forms = new LinkedHashMap<>();
            for (OrePrefixes form : FORMS) {
                String name = registryName(definition.id, form);
                String path = name.substring(GTSteamReborn.MODID.length() + 1);
                DustItem item = new DustItem(definition, form, path);
                GameRegistry.registerItem(item, path);
                if (GameRegistry.findItem(GTSteamReborn.MODID, path) != item)
                    throw new IllegalStateException("Industrial dust registered outside GTSR: " + name);
                forms.put(form, item);
                CreativeTabManager.addItemToTab(new ItemStack(item, 1, 0));
            }
            ITEMS.put(definition.id, forms);
        }
    }

    /** Called at the end of the existing Materials handler, using GT's official canonical ore API. */
    public static void bindMaterials() {
        if (bound) return;
        if (ITEMS.isEmpty())
            throw new IllegalStateException("Register Forge industrial dust items during GTSR preInit");
        for (Map.Entry<String, Map<OrePrefixes, DustItem>> entry : ITEMS.entrySet()) {
            Materials material = ProsperityIndustrialMaterials.get(entry.getKey());
            if (material.mMetaItemSubID != -1) throw new IllegalStateException(
                "Industrial facade unexpectedly owns a GT numeric ID: " + material.mName);
            for (Map.Entry<OrePrefixes, DustItem> form : entry.getValue()
                .entrySet()) {
                form.getValue().material = material;
                GTOreDictUnificator.set(form.getKey(), material, new ItemStack(form.getValue(), 1, 0));
            }
        }
        bound = true;
    }

    public static ItemStack get(String ledgerId, OrePrefixes form, int count) {
        if (count <= 0) throw new IllegalArgumentException("Dust count must be positive");
        Map<OrePrefixes, DustItem> forms = ITEMS.get(ledgerId);
        DustItem item = forms == null ? null : forms.get(form);
        if (item == null)
            throw new IllegalArgumentException("Unregistered industrial dust form: " + ledgerId + " / " + form);
        return GTUtility.copyAmountUnsafe(count, new ItemStack(item, 1, 0));
    }

    /** Full registry identity; only the normal Forge numeric item map is responsible for persistence. */
    public static String registryName(String ledgerId, OrePrefixes form) {
        if (ledgerId == null || !ledgerId.matches("planned:[a-z0-9_]+"))
            throw new IllegalArgumentException("Invalid industrial ledger identity: " + ledgerId);
        String suffix;
        if (form == OrePrefixes.dust) suffix = "dust";
        else if (form == OrePrefixes.dustSmall) suffix = "dust_small";
        else if (form == OrePrefixes.dustTiny) suffix = "dust_tiny";
        else throw new IllegalArgumentException("Not an industrial dust form: " + form);
        return GTSteamReborn.MODID + ":industrial_" + suffix + "_" + ledgerId.substring("planned:".length());
    }

    /** Constant-time ownership lookup; callers can separately reject nonzero item damage. */
    public static String ledgerId(Item item) {
        if (!(item instanceof DustItem)) return null;
        DustItem dust = (DustItem) item;
        Map<OrePrefixes, DustItem> forms = ITEMS.get(dust.definition.id);
        return forms != null && forms.get(dust.form) == item ? dust.definition.id : null;
    }

    public static String ledgerId(ItemStack stack) {
        return stack == null || stack.getItemDamage() != 0 ? null : ledgerId(stack.getItem());
    }

    private static final class DustItem extends Item {

        private final IndustrialRecipeLedger.MaterialDefinition definition;
        private final OrePrefixes form;
        private final int color;
        private Materials material;

        private DustItem(IndustrialRecipeLedger.MaterialDefinition definition, OrePrefixes form, String path) {
            this.definition = definition;
            this.form = form;
            color = IndustrialMaterialColors.getARGB(definition.id) & 0xffffff;
            setUnlocalizedName(GTSteamReborn.MODID + "." + path);
            setCreativeTab(CreativeTabManager.CREATIVE_TAB);
            setMaxStackSize(form.getDefaultStackSize());
        }

        @Override
        public String getItemStackDisplayName(ItemStack stack) {
            return material == null ? definition.en : form.getLocalizedNameForItem(material);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void registerIcons(IIconRegister register) {
            // TextureSet already schedules its official ITEM atlas icons; no duplicate assets or registrations.
        }

        @Override
        @SideOnly(Side.CLIENT)
        public int getSpriteNumber() {
            return 1;
        }

        @SideOnly(Side.CLIENT)
        private IIconContainer icon() {
            return TextureSet.SET_DULL.mTextures[form.getTextureIndex()];
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getIconFromDamage(int damage) {
            return icon().getIcon();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public boolean requiresMultipleRenderPasses() {
            return true;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public int getRenderPasses(int damage) {
            return icon().getOverlayIcon() == null ? 1 : 2;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getIcon(ItemStack stack, int pass) {
            return pass == 0 ? icon().getIcon() : icon().getOverlayIcon();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public int getColorFromItemStack(ItemStack stack, int pass) {
            return pass == 0 ? color : 0xffffff;
        }
    }
}
