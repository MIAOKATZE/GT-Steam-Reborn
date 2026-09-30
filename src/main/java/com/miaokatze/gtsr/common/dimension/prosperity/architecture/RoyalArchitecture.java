package com.miaokatze.gtsr.common.dimension.prosperity.architecture;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockVine;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;

/** Reusable royal timber and living-root palette; every entry has an independent block ID. */
public final class RoyalArchitecture {

    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

    private RoyalArchitecture() {}

    public static Block get(String id) {
        return BLOCKS.get(id);
    }

    private static Block cube(String id, Material material) {
        return new Cube(material).setBlockTextureName("gtsr:royal_" + id)
            .setHardness(3F);
    }

    private static void register(String id, Block block) {
        block.setBlockName("Royal_" + id);
        GameRegistry.registerBlock(block, "Royal_" + id);
        BLOCKS.put(id, block);
        CreativeTabManager.addItemToTab(new ItemStack(block));
    }

    public static void registerBlocks() {
        if (!BLOCKS.isEmpty()) return;
        register("heartwood", cube("heartwood", Material.wood));
        register("oathwood", cube("oathwood", Material.wood));
        register("crownwood", cube("crownwood", Material.wood));
        register("rootstone", cube("rootstone", Material.rock));
        register("patina_mosaic", cube("patina_mosaic", Material.rock));
        register("amber_lattice", cube("amber_lattice", Material.wood).setLightLevel(.35F));
        register("heartwood_slab", new Half("heartwood"));
        register("oathwood_slab", new Half("oathwood"));
        register("heartwood_stairs", new Stairs(get("heartwood")));
        register("rootstone_stairs", new Stairs(get("rootstone")));
        register("root_fence", new BlockFence("gtsr:royal_heartwood", Material.wood));
        register("oath_fence", new BlockFence("gtsr:royal_oathwood", Material.wood));
        register("amber_pane", new Pane());
        register("hanging_vine", new BlockVine().setBlockTextureName("gtsr:royal_hanging_vine"));
        register("oath_flower", new Sprig("oath_flower"));
        register("crown_fern", new Sprig("crown_fern"));
    }

    private static final class Cube extends Block {

        Cube(Material material) {
            super(material);
            setStepSound(material == Material.wood ? soundTypeWood : soundTypeStone);
        }
    }

    private static final class Pane extends BlockPane {

        Pane() {
            super("gtsr:royal_amber_lattice", "gtsr:royal_amber_lattice", Material.glass, true);
        }
    }

    private static final class Stairs extends BlockStairs {

        Stairs(Block base) {
            super(base, 0);
        }
    }

    /** Independent upper/lower half block, preserving its orientation in world metadata. */
    private static final class Half extends Block {

        Half(String texture) {
            super(Material.wood);
            setBlockTextureName("gtsr:royal_" + texture);
            setHardness(2F);
            setStepSound(soundTypeWood);
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
        public void setBlockBoundsBasedOnState(net.minecraft.world.IBlockAccess w, int x, int y, int z) {
            float bottom = w.getBlockMetadata(x, y, z) == 1 ? .5F : 0;
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

    private static final class Sprig extends Block {

        Sprig(String texture) {
            super(Material.plants);
            setBlockTextureName("gtsr:royal_" + texture);
            setStepSound(soundTypeGrass);
            setBlockBounds(.15F, 0, .15F, .85F, .9F, .85F);
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
