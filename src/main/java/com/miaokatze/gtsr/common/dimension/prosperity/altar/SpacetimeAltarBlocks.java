package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlockRunawaySingularity;

import cpw.mods.fml.common.registry.GameRegistry;

public final class SpacetimeAltarBlocks {

    public static Block core;

    private SpacetimeAltarBlocks() {}

    public static void register() {
        core = new Core();
        GameRegistry.registerBlock(core, "SpacetimeAltarCore");
        GameRegistry.registerTileEntity(TileSpacetimeAltar.class, "gtsr.spacetimeAltar");
        GameRegistry.registerTileEntity(TileSpacetimeAltarStory.class, "gtsr.spacetimeAltarStory");
        SpacetimeAltarStoryNetwork.register();
    }

    private static final class Core extends BlockRunawaySingularity {

        Core() {
            setBlockName("SpacetimeAltarCore");
            setLightLevel(1);
        }

        @Override
        public TileEntity createNewTileEntity(World world, int meta) {
            return new TileSpacetimeAltar();
        }

        @Override
        public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx,
            float hy, float hz) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!world.isRemote && tile instanceof TileSpacetimeAltar) ((TileSpacetimeAltar) tile).activate(player);
            return true;
        }

        @Override
        public void breakBlock(World world, int x, int y, int z, Block old, int meta) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!world.isRemote && tile instanceof TileSpacetimeAltar) SpacetimeAltarIndex.get(world)
                .retire(((TileSpacetimeAltar) tile).instanceId());
            super.breakBlock(world, x, y, z, old, meta);
        }
    }
}
