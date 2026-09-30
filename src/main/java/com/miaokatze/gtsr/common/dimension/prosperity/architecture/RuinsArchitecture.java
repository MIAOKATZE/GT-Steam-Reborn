package com.miaokatze.gtsr.common.dimension.prosperity.architecture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;

/** Thirty independently registered ruins materials, shared by industrial and living-root ruins. */
public final class RuinsArchitecture {

    private static final Map<String, Block> REGISTRY = new LinkedHashMap<>();
    public static final Map<String, Block> BLOCKS = Collections.unmodifiableMap(REGISTRY);

    private RuinsArchitecture() {}

    public static Block get(String id) {
        return REGISTRY.get(id);
    }

    private static String texture(String id) {
        return "gtsr:ruins_" + id;
    }

    private static void register(String id, Block block) {
        block.setBlockName("Ruins_" + id)
            .setHardness(3F)
            .setResistance(8F);
        block.setBlockTextureName(texture(id));
        GameRegistry.registerBlock(block, "Ruins_" + id);
        REGISTRY.put(id, block);
        CreativeTabManager.addItemToTab(new ItemStack(block));
    }

    public static void registerBlocks() {
        if (!REGISTRY.isEmpty()) return;
        final String[] metal = { "rust_riveted_plate", "cast_iron_pillar", "riveted_hatch", "foundry_nameplate",
            "boiler_casing", "pressure_reservoir", "patina_trim" };
        for (String id : metal) register(id, new Cube(Material.iron));
        final String[] stone = { "verdigris_pressed_brick", "furnace_firebrick", "slag_masonry", "oath_inscription",
            "silent_bell_base", "mossroot_paving", "sootstone_tiles", "rootbound_brick" };
        for (String id : stone) register(id, new Cube(Material.rock));
        register("rootwood_archive", new Cube(Material.wood));
        register("salvage_workbench", new Cube(Material.wood));
        register("amber_lamp", new Cube(Material.glass).setLightLevel(.65F));
        register("pressure_pipe", new Detail(0));
        register("steam_valve", new Detail(1));
        register("pressure_gauge", new Detail(2));
        register("rust_floor_grate", new Detail(3));
        register("iron_catwalk_fence", new BlockFence(texture("iron_catwalk_fence"), Material.iron));
        register("patina_window", new Window());
        register("firebrick_slab", new Half(Material.rock));
        register("firebrick_stairs", new Stairs(get("furnace_firebrick")));
        register("riveted_plate_slab", new Half(Material.iron));
        register("riveted_plate_stairs", new Stairs(get("rust_riveted_plate")));
        register("suspended_chain", new Detail(4));
        register("mire_reeds", new Reeds());
    }

    private static class Cube extends Block {

        Cube(Material material) {
            super(material);
            setStepSound(
                material == Material.wood ? soundTypeWood
                    : material == Material.iron ? soundTypeMetal : soundTypeStone);
        }
    }

    private static final class Window extends BlockPane {

        Window() {
            super(texture("patina_window"), texture("patina_window"), Material.glass, true);
        }
    }

    private static final class Stairs extends BlockStairs {

        Stairs(Block base) {
            super(base, 0);
        }
    }

    /** Item damage remains zero; placement metadata is assigned by onBlockPlaced. */
    private static final class Half extends Cube {

        Half(Material material) {
            super(material);
            setBlockBounds(0, 0, 0, 1, .5F, 1);
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
        public int onBlockPlaced(World w, int x, int y, int z, int side, float hx, float hy, float hz, int meta) {
            return side == 0 || (side != 1 && hy > .5F) ? 1 : 0;
        }

        @Override
        public void setBlockBoundsBasedOnState(IBlockAccess w, int x, int y, int z) {
            float bottom = (w.getBlockMetadata(x, y, z) & 1) == 1 ? .5F : 0;
            setBlockBounds(0, bottom, 0, 1, bottom + .5F, 1);
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World w, int x, int y, int z) {
            setBlockBoundsBasedOnState(w, x, y, z);
            return super.getCollisionBoundingBoxFromPool(w, x, y, z);
        }

        @Override
        public void setBlockBoundsForItemRender() {
            setBlockBounds(0, 0, 0, 1, .5F, 1);
        }

        @Override
        public int damageDropped(int meta) {
            return 0;
        }
    }

    /** Vanilla cuboid renderer; geometry and collisions use the same orientation bounds. */
    private static final class Detail extends Cube {

        private final int shape;

        Detail(int shape) {
            super(Material.iron);
            this.shape = shape;
            bounds(1);
        }

        private void bounds(int side) {
            if (shape == 3) {
                setBlockBounds(0, 0, 0, 1, .125F, 1);
            } else if (shape == 4) {
                setBlockBounds(.40625F, 0, .40625F, .59375F, 1, .59375F);
            } else if (shape == 2) {
                if (side == 2) setBlockBounds(.2F, .2F, .75F, .8F, .8F, 1);
                else if (side == 3) setBlockBounds(.2F, .2F, 0, .8F, .8F, .25F);
                else if (side == 4) setBlockBounds(.75F, .2F, .2F, 1, .8F, .8F);
                else if (side == 5) setBlockBounds(0, .2F, .2F, .25F, .8F, .8F);
                else setBlockBounds(.2F, 0, .2F, .8F, .25F, .8F);
            } else {
                float lo = shape == 0 ? .3125F : .1875F;
                float hi = 1 - lo;
                if (side == 2 || side == 3) setBlockBounds(lo, lo, 0, hi, hi, 1);
                else if (side == 4 || side == 5) setBlockBounds(0, lo, lo, 1, hi, hi);
                else setBlockBounds(lo, 0, lo, hi, 1, hi);
            }
        }

        @Override
        public int onBlockPlaced(World w, int x, int y, int z, int side, float hx, float hy, float hz, int meta) {
            return side;
        }

        @Override
        public void setBlockBoundsBasedOnState(IBlockAccess w, int x, int y, int z) {
            bounds(w.getBlockMetadata(x, y, z));
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World w, int x, int y, int z) {
            setBlockBoundsBasedOnState(w, x, y, z);
            return super.getCollisionBoundingBoxFromPool(w, x, y, z);
        }

        @Override
        public void setBlockBoundsForItemRender() {
            bounds(1);
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
        public int damageDropped(int meta) {
            return 0;
        }
    }

    private static final class Reeds extends Cube {

        Reeds() {
            super(Material.plants);
            setStepSound(soundTypeGrass);
            setBlockBounds(.1F, 0, .1F, .9F, 1, .9F);
        }

        @Override
        public int getRenderType() {
            return 1;
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
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World w, int x, int y, int z) {
            return null;
        }
    }
}
