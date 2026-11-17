// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Three current metadata variants share one block registration: 1 natural, 2 stable, 3 runaway.
 * Metadata zero is retired and cannot be placed. Item NBT cannot override the declared classification.
 * 方块本体在世界中透明无贴图（原版全透明贴图），物品栏图标单独使用物品贴图，
 * 否则 ItemBlock 默认渲染方块图标会在创造物品栏中不可见。
 * 注意：ItemBlock.registerIcons 覆写不调 super，Item.itemIcon 永不填充，须自持 IIcon。
 */
public class ItemBlockRunawaySingularity extends ItemBlock {

    /** 变体 meta 常量（与 onBlockPlacedBy 分型映射共用口径） */
    public static final int META_OLD = 0;
    public static final int META_NATURAL = 1;
    public static final int META_STABLE = 2;
    public static final int META_RUNAWAY = 3;

    private static final String[] VARIANT_KEYS = { "", "natural", "stable", "runaway" };

    private IIcon singularityIcon;

    public ItemBlockRunawaySingularity(Block block) {
        super(block);
        this.setHasSubtypes(true);
    }

    /** Current variants alone have a classified name. Retired/invalid stacks retain the ordinary block fallback. */
    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int variant = variantIndex(stack);
        return variant == 0 ? super.getUnlocalizedName(stack) : "item.RunawaySingularity." + VARIANT_KEYS[variant];
    }

    /** 1/2/3 are current identities; zero is an invalid sentinel, never a natural alias. */
    public static int variantIndex(ItemStack stack) {
        if (stack == null) return 0;
        int meta = stack.getItemDamage();
        return meta >= META_NATURAL && meta <= META_RUNAWAY ? meta : 0;
    }

    public static int metadataForType(TileRunawaySingularity.SingularityType type) {
        return switch (type) {
            case NATURAL -> META_NATURAL;
            case STABLE -> META_STABLE;
            case RUNAWAY -> META_RUNAWAY;
        };
    }

    @Override
    public int getMetadata(int damage) {
        return damage >= META_NATURAL && damage <= META_RUNAWAY ? damage : 0;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (getClass() != ItemBlockRunawaySingularity.class)
            return super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
        return variantIndex(stack) != 0 && super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        if (getClass() != ItemBlockRunawaySingularity.class)
            return super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
        int variant = variantIndex(stack);
        return variant != 0 && super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, variant);
    }

    /**
     * 分型词条不串：首行为变体专属分型说明，尾行为四变体共用通用说明。
     */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        if (variantIndex(stack) == 0) return;
        lines.add(
            EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("gtsr.tooltip.node." + VARIANT_KEYS[variantIndex(stack)]));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IIconRegister reg) {
        this.singularityIcon = reg.registerIcon("gtsr:RunawaySingularity");
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return this.singularityIcon != null ? this.singularityIcon : super.getIconFromDamage(damage);
    }
}
