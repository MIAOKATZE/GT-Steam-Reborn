package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;

/** One-block classical witness plinth, drawn by the shared node TESR using GT marble. */
public final class FictionPedestalBlock extends RemasterBlock {

    public FictionPedestalBlock(boolean starter) {
        super(
            starter ? "gtsr:fiction_starter" : "gtsr:fiction_witness_pedestal",
            "fiction",
            false,
            false,
            "boxes",
            new double[][][] { { { .125, 0, .125, .875, 1, .875 } } },
            new String[][] {
                { "minecraft:quartz_block_side", "minecraft:quartz_block_side", "minecraft:quartz_block_side",
                    "minecraft:quartz_block_side", "minecraft:quartz_block_side", "minecraft:quartz_block_side" } });
        setHardness(-1F);
        setResistance(5000F);
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        return -1F;
    }

    @Override
    public int getMobilityFlag() {
        return 2;
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean harvest) {
        return false;
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {}

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        return GregTechAPI.sBlockStones.getIcon(side, 0);
    }

    @Override
    public int getRenderType() {
        return -1;
    }
}
