package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.fx.GTSRGlowFX;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Author-supplied material and geometry states; collision and rendering consume the same boxes. */
public class RemasterBlock extends Block {

    private final String id;
    private final String category;
    private final boolean fullCube;
    private final boolean transparent;
    private final String render;
    private final double[][][] parts;
    private final String[][] textures;
    @SideOnly(Side.CLIENT)
    private IIcon[][] icons;

    public RemasterBlock(String id, String category, boolean fullCube, boolean transparent, String render,
        double[][][] parts, String[][] textures) {
        super("cross".equals(render) || "vine".equals(render) ? Material.plants : Material.iron);
        this.id = id;
        this.category = category;
        this.fullCube = fullCube;
        this.transparent = transparent;
        this.render = render;
        this.parts = parts;
        this.textures = textures;
        setBlockName("Remaster_" + id.substring(id.indexOf(':') + 1));
        setHardness("spawner".equals(category) ? 200F : 3F);
        setResistance("spawner".equals(category) ? 25000F / 3F : 8F);
        setStepSound(soundTypeMetal);
        setLightOpacity(fullCube && !transparent ? 255 : 0);
    }

    public String id() {
        return id;
    }

    public String category() {
        return category;
    }

    public String renderShape() {
        return render;
    }

    public boolean isInteractive() {
        return id.equals("gtsr:draft_pressure_console") || "spawner".equals(category);
    }

    public boolean isHintObject() {
        return id.equals("gtsr:draft_pressure_console");
    }

    public boolean guidanceActive(IBlockAccess world, int x, int y, int z) {
        if (!isHintObject()) return false;
        if (id.startsWith("gtsr:draft7_") && completed(world.getBlockMetadata(x, y, z))) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileRemasterNode node && RemasterRollout.allowsSavedId(node.siteId)
            && !node.nodeId.isEmpty()
            && !node.role.isEmpty()
            && !"{}".equals(node.display);
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        if (!guidanceActive(world, x, y, z)) return super.getLightValue(world, x, y, z);
        return id.startsWith("gtsr:draft7_") ? 12 : 9;
    }

    public boolean completed(int meta) {
        return (meta & 4) != 0;
    }

    public int stateCount() {
        return parts.length;
    }

    /** Six coordinates per cuboid, copied so callers cannot mutate the shared geometry. */
    public double[][] boxes(int meta) {
        double[][] source = parts[state(meta)];
        double[][] result = new double[source.length][];
        for (int i = 0; i < source.length; i++) result[i] = source[i].clone();
        return result;
    }

    private int state(int meta) {
        return meta >= 0 && meta < parts.length ? meta : 0;
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return isInteractive();
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        RemasterBlocks.InteractionHandler handler = RemasterBlocks.interactionHandler();
        return handler == null ? null : handler.createTileEntity(world, meta, id);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!isInteractive()) return false;
        RemasterBlocks.InteractionHandler handler = RemasterBlocks.interactionHandler();
        return handler != null && handler.activate(world, x, y, z, player);
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        RemasterBlocks.InteractionHandler handler = RemasterBlocks.interactionHandler();
        return handler == null ? super.getBlockHardness(world, x, y, z)
            : handler.hardness(world, x, y, z, super.getBlockHardness(world, x, y, z));
    }

    @Override
    public int quantityDropped(Random random) {
        return "spawner".equals(category) ? 0 : super.quantityDropped(random);
    }

    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int metadata) {
        return !"spawner".equals(category) && super.canSilkHarvest(world, player, x, y, z, metadata);
    }

    @Override
    public int getExpDrop(IBlockAccess world, int metadata, int fortune) {
        // Forge awards this only for successful, noncreative player harvesting; explosions never award it.
        return "spawner".equals(category) ? RemasterSpawnerContract.experience(new Random()) : 0;
    }

    @Override
    public float getExplosionResistance(Entity entity) {
        return "spawner".equals(category) ? 5000F : super.getExplosionResistance(entity);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block oldBlock, int oldMeta) {
        RemasterRuntime.spawnerDestroyed(world, x, y, z, oldBlock, oldMeta);
        super.breakBlock(world, x, y, z, oldBlock, oldMeta);
    }

    @Override
    public boolean isOpaqueCube() {
        return fullCube && !transparent;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return fullCube;
    }

    @Override
    public int getRenderType() {
        return fullCube && !isHintObject() ? 0 : RemasterBlocks.renderId;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random random) {
        if (!guidanceActive(world, x, y, z) || random.nextInt(3) != 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.getDistanceSq(x + .5, y + .75, z + .5) > 24 * 24) return;
        Vec3 at = Vec3.createVectorHelper(x + .5, y + 1.1, z + .5);
        Vec3 eye = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        MovingObjectPosition obstruction = world.func_147447_a(eye, at, false, true, false);
        if (obstruction != null && (obstruction.blockX != x || obstruction.blockY != y || obstruction.blockZ != z))
            return;
        GTSRGlowFX.spawn(world, at.xCoord, at.yCoord, at.zCoord, .16F, .70F, .94F, .58F, 18);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return transparent ? 1 : 0;
    }

    @Override
    public int damageDropped(int meta) {
        return state(meta);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if ("vine".equals(render)) return;
        int quadrant = MathHelper.floor_double(placer.rotationYaw * 4D / 360D + .5D) & 3;
        // Vanilla player quadrants S/W/N/E map to authored E/W/S/N metadata.
        int[] direction = { 3, 0, 2, 1 };
        int meta = id.startsWith("gtsr:draft_") ? quadrant : direction[quadrant];
        if ("stairs".equals(render)) {
            int[] stairDirection = { 2, 1, 3, 0 };
            meta = stairDirection[quadrant] | (world.getBlockMetadata(x, y, z) & 4);
        }
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
        if ("stairs".equals(render)) return (meta & 3) | (side == 0 || side != 1 && hitY > .5F ? 4 : 0);
        if ("vine".equals(render)) {
            switch (side) {
                case 2:
                    return 1;
                case 3:
                    return 4;
                case 4:
                    return 8;
                case 5:
                    return 2;
                default:
                    return 0;
            }
        }
        return state(meta);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        bounds(world.getBlockMetadata(x, y, z));
    }

    @Override
    public void setBlockBoundsForItemRender() {
        bounds(0);
    }

    private void bounds(int meta) {
        double[][] boxes = parts[state(meta)];
        if (boxes.length == 0) {
            setBlockBounds(0, 0, 0, 1, 1, 1);
            return;
        }
        double[] lo = { 1, 1, 1 }, hi = { 0, 0, 0 };
        for (double[] b : boxes) for (int axis = 0; axis < 3; axis++) {
            lo[axis] = Math.min(lo[axis], b[axis]);
            hi[axis] = Math.max(hi[axis], b[axis + 3]);
        }
        setBlockBounds((float) lo[0], (float) lo[1], (float) lo[2], (float) hi[0], (float) hi[1], (float) hi[2]);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        if ("cross".equals(render) || parts[state(world.getBlockMetadata(x, y, z))].length == 0) return null;
        bounds(world.getBlockMetadata(x, y, z));
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list,
        Entity entity) {
        if ("cross".equals(render)) return;
        for (double[] b : parts[state(world.getBlockMetadata(x, y, z))]) {
            AxisAlignedBB box = AxisAlignedBB
                .getBoundingBox(x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5]);
            if (mask.intersectsWith(box)) list.add(box);
        }
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        MovingObjectPosition nearest = null;
        double distance = Double.MAX_VALUE;
        double[][] boxes = parts[state(world.getBlockMetadata(x, y, z))];
        if ("cross".equals(render) || boxes.length == 0) return super.collisionRayTrace(world, x, y, z, start, end);
        for (double[] b : boxes) {
            MovingObjectPosition hit = AxisAlignedBB
                .getBoundingBox(x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5])
                .calculateIntercept(start, end);
            if (hit != null && hit.hitVec.squareDistanceTo(start) < distance) {
                distance = hit.hitVec.squareDistanceTo(start);
                nearest = hit;
            }
        }
        return nearest == null ? null : new MovingObjectPosition(x, y, z, nearest.sideHit, nearest.hitVec);
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        if (fullCube) return true;
        // A staircase only presents a complete horizontal support face on its full slab side.
        if ("stairs".equals(render))
            return side == ((world.getBlockMetadata(x, y, z) & 4) == 0 ? ForgeDirection.DOWN : ForgeDirection.UP);
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[textures.length][6];
        for (int meta = 0; meta < textures.length; meta++) for (int side = 0; side < 6; side++) {
            icons[meta][side] = register.registerIcon(textures[meta][side]);
        }
        blockIcon = icons[0][0];
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        return icons[state(meta)][side < 0 || side > 5 ? 0 : side];
    }
}
