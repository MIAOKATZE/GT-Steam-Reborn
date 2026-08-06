package com.miaokatze.gtsr.common.blocks;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 失控奇点方块
 * 不可破坏的透明方块骨架，业务逻辑由 TileRunawaySingularity 承载。
 */
public class BlockRunawaySingularity extends BlockContainer {

    public BlockRunawaySingularity() {
        super(Material.portal);
        setHardness(-1.0F);
        setResistance(6000000.0F);
        setBlockName("RunawaySingularity");
        setLightLevel(1.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileRunawaySingularity();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public boolean canRenderInPass(int pass) {
        return true;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public int getLightOpacity() {
        return 0;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.blockIcon = reg.registerIcon("gregtech:iconsets/TRANSPARENT");
    }
}
