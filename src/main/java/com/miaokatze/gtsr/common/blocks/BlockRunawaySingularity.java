// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.blocks;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.util.SingularityDropExplosion;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Natural nodes can be harvested; stable and runaway nodes cannot. Tile identity is authoritative in existing worlds.
 * Current item metadata 1/2/3 explicitly declares natural/stable/runaway placement; metadata zero is retired.
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
        TileRunawaySingularity node = new TileRunawaySingularity();
        initializePlacedNode(node, meta);
        return node;
    }

    private static void initializePlacedNode(TileRunawaySingularity node, int meta) {
        switch (meta) {
            case ItemBlockRunawaySingularity.META_NATURAL:
                node.setType(TileRunawaySingularity.SingularityType.NATURAL);
                break;
            case ItemBlockRunawaySingularity.META_STABLE:
                node.setType(TileRunawaySingularity.SingularityType.STABLE);
                break;
            case ItemBlockRunawaySingularity.META_RUNAWAY:
                node.setType(TileRunawaySingularity.SingularityType.RUNAWAY);
                break;
            default:
                break; // Existing world/NBT semantics are left intact; retired items cannot place this metadata.
        }
    }

    private boolean isNatural(World world, int x, int y, int z) {
        return world.getBlock(x, y, z) == this && world.getTileEntity(x, y, z) instanceof TileRunawaySingularity node
            && node.getType() == TileRunawaySingularity.SingularityType.NATURAL;
    }

    /**
     * 位置感知硬度：nature 奇点（自然生成专用）硬度 4，空手可破；其余奇点仍为 bedrock 级不可破（构造 setHardness(-1)）。
     */
    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        if (isNatural(world, x, y, z)) {
            return 4.0F;
        }
        return blockHardness;
    }

    /**
     * Emit the nature effect and its 1-2 timed item entities only after a successful survival removal.
     * The normal harvest path returns no additional drops, including Silk Touch and explosions.
     */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        boolean natural = isNatural(world, x, y, z);
        if (!natural && (player == null || !player.capabilities.isCreativeMode)) return false;
        boolean removed = super.removedByPlayer(world, player, x, y, z, willHarvest);
        if (removed && natural && !world.isRemote && player != null && !player.capabilities.isCreativeMode) {
            SingularityDropExplosion.explodeNature(world, x + 0.5D, y + 0.5D, z + 0.5D);
        }
        return removed;
    }

    /**
     * Current item placement owns its metadata classification and safe defaults. No item can inject tile coordinates,
     * machine ownership or a conflicting type through NBT. Direct spawn paths retain their explicit parameters.
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileRunawaySingularity) || stack == null) {
            return;
        }
        initializePlacedNode((TileRunawaySingularity) te, ItemBlockRunawaySingularity.variantIndex(stack));
        te.markDirty();
        world.markBlockForUpdate(x, y, z);
    }

    @Override
    public int getDamageValue(World world, int x, int y, int z) {
        if (getClass() != BlockRunawaySingularity.class) return super.getDamageValue(world, x, y, z);
        return world.getTileEntity(x, y, z) instanceof TileRunawaySingularity node
            ? ItemBlockRunawaySingularity.metadataForType(node.getType())
            : super.getDamageValue(world, x, y, z);
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return new ItemStack(this, 1, getDamageValue(world, x, y, z));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, java.util.List variants) {
        if (getClass() != BlockRunawaySingularity.class) {
            super.getSubBlocks(item, tab, variants);
            return;
        }
        for (int meta = ItemBlockRunawaySingularity.META_NATURAL; meta
            <= ItemBlockRunawaySingularity.META_RUNAWAY; meta++) variants.add(new ItemStack(item, 1, meta));
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
    protected boolean canSilkHarvest() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        // 原使用 gregtech:iconsets/TRANSPARENT，但 GT5U jar 不随包发布该文件（解析到
        // assets/gregtech/textures/blocks/iconsets/TRANSPARENT.png），无材质包时方块显示紫黑缺失图案；
        // 改用原版 1.7.10 自带的全透明方块贴图（16x16 alpha 全 0），零外部依赖，材质包覆盖同名文件亦全透明。
        this.blockIcon = reg.registerIcon("minecraft:redstone_dust_cross_overlay");
    }
}
