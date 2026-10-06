package com.miaokatze.gtsr.common.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.util.GTSRUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Keeps the existing visible singularity item icon with a single dedicated stable variant. */
public final class ItemBlockSpacetimeSingularity extends ItemBlockRunawaySingularity {

    public ItemBlockSpacetimeSingularity(Block block) {
        super(block);
        setHasSubtypes(false);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.SpacetimeSingularity";
    }

    @Override
    public int getMetadata(int damage) {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.spacetime.node.tooltip"));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.spacetime.node.owner"));
        lines.add(GTSRUtils.getAddedByLine());
    }
}
