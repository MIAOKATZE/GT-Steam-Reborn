package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;

public final class RuinMechanisms {

    public static BlockContainer block;

    private RuinMechanisms() {}

    public static void register() {
        if (block != null) return;
        block = new MechanismBlock();
        GameRegistry.registerBlock(block, "RuinMechanism");
        GameRegistry.registerTileEntity(TileRuinMechanism.class, "gtsr.ruinMechanism");
    }

    private static final class MechanismBlock extends BlockContainer {

        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        private net.minecraft.util.IIcon activeIcon;

        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister r) {
            super.registerBlockIcons(r);
            activeIcon = r.registerIcon("gtsr:ruins_amber_lamp");
        }

        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public net.minecraft.util.IIcon getIcon(int side, int meta) {
            return meta == 1 ? activeIcon : blockIcon;
        }

        private MechanismBlock() {
            super(Material.iron);
            setBlockName("ruinMechanism");
            setBlockTextureName("gtsr:ruins_pressure_gauge");
            setHardness(-1);
            setResistance(6000000);
        }

        public TileEntity createNewTileEntity(World world, int meta) {
            return new TileRuinMechanism();
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

        public boolean canEntityDestroy(net.minecraft.world.IBlockAccess w, int x, int y, int z,
            net.minecraft.entity.Entity e) {
            return false;
        }

        public boolean canDropFromExplosion(net.minecraft.world.Explosion e) {
            return false;
        }

        public void onBlockExploded(World w, int x, int y, int z, net.minecraft.world.Explosion e) {}

        public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
            return new ArrayList<>();
        }

        public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int side, float a, float b,
            float c) {
            if (!w.isRemote && w.getTileEntity(x, y, z) instanceof TileRuinMechanism)
                RuinObjectives.interact(p, (TileRuinMechanism) w.getTileEntity(x, y, z));
            return true;
        }
    }
}
