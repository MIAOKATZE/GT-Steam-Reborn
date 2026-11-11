// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Forge fluids use a flat inventory icon, whose tint comes from the item rather than the block. */
public final class ItemBlockAbyssalFluid extends ItemBlock {

    public ItemBlockAbyssalFluid(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return field_150939_a.getRenderColor(stack.getItemDamage());
    }
}
