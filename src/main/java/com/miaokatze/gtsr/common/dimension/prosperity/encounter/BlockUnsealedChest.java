package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Breakable reward vessel. Every removal spills contents, and never drops the vessel itself. */
public final class BlockUnsealedChest extends BlockContainer {

    public BlockUnsealedChest() {
        super(Material.wood);
        setBlockName("unsealedChest");
        setHardness(2.5F);
        setStepSound(soundTypeWood);
        setBlockTextureName("gtsr:prosperity_rust_log_side");
        setBlockBounds(.0625F, 0, .0625F, .9375F, .875F, .9375F);
    }

    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityUnsealedChest();
    }

    public boolean isOpaqueCube() {
        return false;
    }

    public boolean renderAsNormalBlock() {
        return false;
    }

    public int getRenderType() {
        return -1;
    }

    public boolean hasComparatorInputOverride() {
        return true;
    }

    public int getComparatorInputOverride(World world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof IInventory ? Container.calcRedstoneFromInventory((IInventory) tile) : 0;
    }

    public int getMobilityFlag() {
        return 2;
    }

    public Item getItemDropped(int meta, Random random, int fortune) {
        return null;
    }

    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int direction = MathHelper.floor_double(placer.rotationYaw * 4 / 360.0 + .5) & 3;
        world.setBlockMetadataWithNotify(x, y, z, new int[] { 2, 5, 3, 4 }[direction], 3);
    }

    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float a, float b,
        float c) {
        if (!world.isRemote && !world.isSideSolid(x, y + 1, z, net.minecraftforge.common.util.ForgeDirection.DOWN)) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityUnsealedChest) {
                ((TileEntityUnsealedChest) tile).recordStory(player);
                player.displayGUIChest((TileEntityUnsealedChest) tile);
            }
        }
        return true;
    }

    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!world.isRemote && tile instanceof TileEntityUnsealedChest) {
            TileEntityUnsealedChest chest = (TileEntityUnsealedChest) tile;
            for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
                ItemStack stack = chest.getStackInSlotOnClosing(slot);
                if (stack == null) continue;
                EntityItem drop = new EntityItem(
                    world,
                    x + .2 + world.rand.nextDouble() * .6,
                    y + .25,
                    z + .2 + world.rand.nextDouble() * .6,
                    stack);
                drop.motionX = world.rand.nextGaussian() * .05;
                drop.motionY = .15 + world.rand.nextDouble() * .1;
                drop.motionZ = world.rand.nextGaussian() * .05;
                world.spawnEntityInWorld(drop);
            }
            world.func_147453_f(x, y, z, block);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
