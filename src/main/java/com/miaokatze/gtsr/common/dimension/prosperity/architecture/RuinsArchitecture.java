package com.miaokatze.gtsr.common.dimension.prosperity.architecture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Thirty independently registered ruins materials, shared by industrial and living-root ruins. */
public final class RuinsArchitecture {

    private static final Map<String, Block> REGISTRY = new LinkedHashMap<>();
    public static final Map<String, Block> BLOCKS = Collections.unmodifiableMap(REGISTRY);

    /** Client assigns this without loading any client class on a dedicated server. */
    public static int detailRenderId;

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
        register("iron_catwalk_fence", new Detail(5));
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

        @Override
        public boolean isSideSolid(IBlockAccess w, int x, int y, int z, ForgeDirection side) {
            return side == ForgeDirection.UP && (w.getBlockMetadata(x, y, z) & 1) == 1
                || side == ForgeDirection.DOWN && (w.getBlockMetadata(x, y, z) & 1) == 0;
        }
    }

    /** Geometry is shared by rendering, selection and collision. Side metadata follows vanilla 0..5. */
    public static final class Detail extends Cube {

        public final int shape;
        @SideOnly(Side.CLIENT)
        private IIcon componentIcon;

        @SideOnly(Side.CLIENT)
        @Override
        public void registerBlockIcons(IIconRegister register) {
            super.registerBlockIcons(register);
            componentIcon = register.registerIcon("gtsr:ruins_component_metal");
        }

        @SideOnly(Side.CLIENT)
        @Override
        public IIcon getIcon(int face, int meta) {
            return componentIcon;
        }

        @SideOnly(Side.CLIENT)
        public IIcon getDialIcon() {
            return blockIcon;
        }

        Detail(int shape) {
            super(Material.iron);
            this.shape = shape;
        }

        @Override
        public int getRenderType() {
            return detailRenderId;
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

        @Override
        public int onBlockPlaced(World w, int x, int y, int z, int side, float hx, float hy, float hz, int meta) {
            return shape == 3 ? (side == 0 || (side != 1 && hy > .5F) ? 1 : 0) : side;
        }

        public List<double[]> parts(IBlockAccess w, int x, int y, int z, boolean collision) {
            int mask = w == null ? (shape == 5 ? 12 : shape == 4 ? 3 : 0) : 0;
            int[][] d = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
            if (w != null) for (int side = 0; side < 6; side++) {
                if (w instanceof World && !((World) w).blockExists(x + d[side][0], y + d[side][1], z + d[side][2]))
                    continue;
                Block n = w.getBlock(x + d[side][0], y + d[side][1], z + d[side][2]);
                boolean connect = shape == 5 ? (side >= 2 && (n == this || n.isNormalCube()))
                    : shape == 4
                        ? (side < 2 && (n == this || n.isSideSolid(
                            w,
                            x + d[side][0],
                            y + d[side][1],
                            z + d[side][2],
                            ForgeDirection.getOrientation(side ^ 1))))
                        : (n instanceof Detail && ((Detail) n).shape <= 2);
                if (connect) mask |= 1 << side;
            }
            boolean supported = w == null || w.getBlock(x, y - 1, z)
                .isSideSolid(w, x, y - 1, z, ForgeDirection.UP);
            return RuinsGeometry.parts(shape, w == null ? 1 : w.getBlockMetadata(x, y, z), mask, supported, collision);
        }

        @Override
        public void addCollisionBoxesToList(World w, int x, int y, int z, AxisAlignedBB mask, List<AxisAlignedBB> out,
            Entity entity) {
            for (double[] p : parts(w, x, y, z, true)) {
                AxisAlignedBB bb = AxisAlignedBB
                    .getBoundingBox(x + p[0], y + p[1], z + p[2], x + p[3], y + p[4], z + p[5]);
                if (bb.intersectsWith(mask)) out.add(bb);
            }
        }

        @Override
        public void setBlockBoundsBasedOnState(IBlockAccess w, int x, int y, int z) {
            double[] b = RuinsGeometry.envelope(parts(w, x, y, z, false));
            setBlockBounds((float) b[0], (float) b[1], (float) b[2], (float) b[3], (float) b[4], (float) b[5]);
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World w, int x, int y, int z) {
            double[] b = RuinsGeometry.envelope(parts(w, x, y, z, true));
            return AxisAlignedBB.getBoundingBox(x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5]);
        }

        @Override
        public MovingObjectPosition collisionRayTrace(World w, int x, int y, int z, Vec3 start, Vec3 end) {
            MovingObjectPosition closest = null;
            double distance = Double.MAX_VALUE;
            for (double[] p : parts(w, x, y, z, false)) {
                MovingObjectPosition hit = AxisAlignedBB
                    .getBoundingBox(x + p[0], y + p[1], z + p[2], x + p[3], y + p[4], z + p[5])
                    .calculateIntercept(start, end);
                if (hit != null && start.squareDistanceTo(hit.hitVec) < distance) {
                    closest = hit;
                    distance = start.squareDistanceTo(hit.hitVec);
                }
            }
            return closest == null ? null : new MovingObjectPosition(x, y, z, closest.sideHit, closest.hitVec);
        }

        @Override
        public void setBlockBoundsForItemRender() {
            setBlockBounds(0, 0, 0, 1, 1, 1);
        }

        @Override
        public boolean isSideSolid(IBlockAccess w, int x, int y, int z, ForgeDirection side) {
            return shape == 3 && (side == ForgeDirection.UP ? (w.getBlockMetadata(x, y, z) & 1) == 1
                : side == ForgeDirection.DOWN && (w.getBlockMetadata(x, y, z) & 1) == 0);
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
