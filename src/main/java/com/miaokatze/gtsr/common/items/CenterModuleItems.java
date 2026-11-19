// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.items;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class CenterModuleItems extends Item {

    public static final int GRADES = 3;
    private static final int SHELL_START = 51;
    // Save identifiers. Append new kinds using free metadata, without reassigning these IDs.
    private static final CenterModule[] MODULES = { CenterModule.CRUSHER, CenterModule.WASHER, CenterModule.SIFTER,
        CenterModule.CENTRIFUGE, CenterModule.ELECTROLYZER, CenterModule.DRYER, CenterModule.FURNACE,
        CenterModule.REACTOR, CenterModule.SEPARATOR, CenterModule.EXTRACTOR, CenterModule.THERMAL_CENTRIFUGE,
        CenterModule.SMELTING, CenterModule.SPEED, CenterModule.PARALLEL, CenterModule.YIELD, CenterModule.SECONDARY,
        CenterModule.LOGISTICS };
    private static final Map<CenterModule, Integer> IDS = new EnumMap<>(CenterModule.class);
    private static CenterModuleItems instance;

    static {
        for (int i = 0; i < MODULES.length; i++) IDS.put(MODULES[i], i * GRADES);
    }

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    private CenterModuleItems() {
        setUnlocalizedName("ore_integration_module");
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
    }

    public static void register() {
        instance = new CenterModuleItems();
        GameRegistry.registerItem(instance, "ore_integration_module");
        for (int metadata = 0; metadata < SHELL_START + GRADES; metadata++) {
            CreativeTabManager.addItemToTab(new ItemStack(instance, 1, metadata));
        }
    }

    public static ItemStack stack(CenterModule module, int grade) {
        return new ItemStack(instance, 1, IDS.get(module) + gradeOffset(grade));
    }

    public static ItemStack shell(int grade) {
        return new ItemStack(instance, 1, SHELL_START + gradeOffset(grade));
    }

    public static CenterModule moduleOf(ItemStack stack) {
        if (stack == null || stack.getItem() != instance) return null;
        int metadata = stack.getItemDamage();
        return metadata >= 0 && metadata < SHELL_START ? MODULES[metadata / GRADES] : null;
    }

    /** Returns zero for unrelated stacks, shells and invalid metadata. */
    public static int gradeOf(ItemStack stack) {
        return moduleOf(stack) == null ? 0 : stack.getItemDamage() % GRADES + 1;
    }

    private static int gradeOffset(int grade) {
        if (grade < 1 || grade > GRADES) throw new IllegalArgumentException("Invalid center module grade: " + grade);
        return grade - 1;
    }

    private static String variant(int metadata) {
        if (metadata >= 0 && metadata < SHELL_START) {
            return MODULES[metadata / GRADES].key + "-" + (metadata % GRADES + 1);
        }
        if (metadata >= SHELL_START && metadata < SHELL_START + GRADES) {
            return "shell-" + (metadata - SHELL_START + 1);
        }
        return "invalid";
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return getUnlocalizedName() + "." + variant(stack.getItemDamage());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[SHELL_START + GRADES];
        for (int metadata = 0; metadata < icons.length; metadata++) {
            icons[metadata] = register.registerIcon("gtsr:orecenter/" + variant(metadata));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int metadata) {
        return icons[metadata >= 0 && metadata < icons.length ? metadata : SHELL_START];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int metadata = 0; metadata < SHELL_START + GRADES; metadata++) {
            list.add(new ItemStack(item, 1, metadata));
        }
    }
}
