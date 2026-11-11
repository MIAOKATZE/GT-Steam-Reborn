// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.blocks;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Machine-owned stable spacetime core, with no collision or terrain effects. */
public final class BlockSpacetimeSingularity extends BlockRunawaySingularity {

    public BlockSpacetimeSingularity() {
        setBlockName("SpacetimeSingularity");
        setLightLevel(1.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSpacetimeSingularity();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        // Item placement cannot grant a machine lease or change the stable classification.
    }
}
