package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockSealedChest extends BlockContainer {

    public BlockSealedChest() {
        super(Material.iron);
        setBlockName("sealedChest");
        setHardness(-1);
        setResistance(6000000);
        setBlockTextureName("gtsr:prosperity_rust_log_side");
        setBlockBounds(.0625F, 0, .0625F, .9375F, .875F, .9375F);
    }

    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int direction = MathHelper.floor_double(placer.rotationYaw * 4 / 360.0 + .5) & 3;
        w.setBlockMetadataWithNotify(x, y, z, new int[] { 2, 5, 3, 4 }[direction], 3);
    }

    public TileEntity createNewTileEntity(World w, int m) {
        return new TileEntitySealedChest();
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

    public int getMobilityFlag() {
        return 2;
    }

    public boolean removedByPlayer(World w, EntityPlayer p, int x, int y, int z, boolean harvest) {
        return false;
    }

    public boolean removedByPlayer(World w, EntityPlayer p, int x, int y, int z) {
        return false;
    }

    public boolean canEntityDestroy(IBlockAccess w, int x, int y, int z, net.minecraft.entity.Entity e) {
        return false;
    }

    public boolean canDropFromExplosion(Explosion e) {
        return false;
    }

    public void onBlockExploded(World w, int x, int y, int z, Explosion e) {}

    public ArrayList<ItemStack> getDrops(World w, int x, int y, int z, int m, int fortune) {
        return new ArrayList<>();
    }

    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float a, float b, float c) {
        if (!w.isRemote) {
            TileEntity t = w.getTileEntity(x, y, z);
            if (t instanceof TileEntitySealedChest) ((TileEntitySealedChest) t).tryUnlockByClick();
        }
        return true;
    }
}
