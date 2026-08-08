package com.miaokatze.gtsr.common.blocks;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.util.SingularityDropExplosion;

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
        setLightLevel(14.0F / 15.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileRunawaySingularity();
    }

    /**
     * 位置感知硬度：nature 奇点（自然生成专用）硬度 4，空手可破；其余奇点仍为 bedrock 级不可破（构造 setHardness(-1)）。
     */
    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileRunawaySingularity
            && ((TileRunawaySingularity) te).getAttributeId() == TileRunawaySingularity.ATTRIBUTE_NATURE) {
            return 4.0F;
        }
        return blockHardness;
    }

    /**
     * nature 奇点被玩家挖掉时触发爆炸（TNT 视觉、不破坏方块、不伤 HP、只击退、产 0-1 个蒸汽纠缠奇点）；
     * 其余奇点不可破坏，不会进入此分支的爆炸逻辑。
     */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TileRunawaySingularity
            && ((TileRunawaySingularity) te).getAttributeId() == TileRunawaySingularity.ATTRIBUTE_NATURE) {
            SingularityDropExplosion.explodeNature(world, x + 0.5D, y + 0.5D, z + 0.5D);
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
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
