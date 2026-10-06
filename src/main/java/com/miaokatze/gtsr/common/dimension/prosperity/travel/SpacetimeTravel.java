package com.miaokatze.gtsr.common.dimension.prosperity.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.config.Config;

import ic2.api.item.ElectricItem;

/** All target discovery and transfers happen on the server tick thread. Never edits terrain. */
public final class SpacetimeTravel {

    public static final double COST = 20480000;
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final java.util.Set<UUID> TRANSFERRING = new java.util.HashSet<>();

    private SpacetimeTravel() {}

    public static void init() {
        AnchorIntent.init();
        SpacetimeEffects.init();
    }

    private static long now() {
        return MinecraftServer.getServer()
            .worldServerForDimension(0)
            .getTotalWorldTime();
    }

    public static boolean eligible(EntityPlayerMP p) {
        return p != null && !TRANSFERRING.contains(p.getUniqueID())
            && !p.isDead
            && p.getHealth() > 0
            && p.ridingEntity == null
            && p.riddenByEntity == null
            && p.playerNetServerHandler != null
            && p.playerNetServerHandler.netManager.isChannelOpen();
    }

    public static void message(EntityPlayerMP p, String suffix) {
        p.addChatMessage(new ChatComponentTranslation("gtsr.anchor." + suffix));
    }

    public static void mark(EntityPlayerMP p, ItemStack stack) {
        if (!eligible(p)) return;
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        NBTTagCompound mark = new NBTTagCompound();
        mark.setInteger("dimension", p.dimension);
        mark.setDouble("x", p.posX);
        mark.setDouble("y", p.posY);
        mark.setDouble("z", p.posZ);
        mark.setFloat("yaw", p.rotationYaw);
        mark.setFloat("pitch", p.rotationPitch);
        mark.setString(
            "author",
            p.getUniqueID()
                .toString());
        root.setTag("gtsrAnchor", mark);
        p.inventoryContainer.detectAndSendChanges();
        message(p, "marked");
    }

    public static boolean recall(EntityPlayerMP p, ItemStack stack) {
        if (!eligible(p)) return false;
        long tick = now();
        if (COOLDOWN.getOrDefault(p.getUniqueID(), 0L) > tick) {
            message(p, "cooldown");
            return false;
        }
        if (!stack.hasTagCompound() || !stack.getTagCompound()
            .hasKey("gtsrAnchor", 10)) {
            message(p, "unmarked");
            return false;
        }
        NBTTagCompound m = stack.getTagCompound()
            .getCompoundTag("gtsrAnchor");
        double x = m.getDouble("x"), y = m.getDouble("y"), z = m.getDouble("z");
        float yaw = m.getFloat("yaw"), pitch = m.getFloat("pitch");
        if (!Double.isFinite(x) || !Double.isFinite(y)
            || !Double.isFinite(z)
            || Math.abs(x) > 29999980
            || Math.abs(z) > 29999980
            || !Float.isFinite(yaw)
            || !Float.isFinite(pitch)) {
            message(p, "unsafe");
            return false;
        }
        WorldServer destination = destination(m.getInteger("dimension"));
        double[] point = destination == null ? null : safeLanding(destination, p, x, y, z);
        if (point == null) {
            message(p, "unsafe");
            return false;
        }
        if (!eligible(p)) return false;
        if (ElectricItem.manager.discharge(stack, COST, 6, true, false, true) < COST) {
            message(p, "energy");
            return false;
        }
        // Own vanilla IElectricItem uses IC2's synchronous NBT manager: reserve by simulation, transfer, then commit.
        NBTTagCompound saved = (NBTTagCompound) stack.getTagCompound()
            .copy();
        WorldServer source = (WorldServer) p.worldObj;
        double sx = p.posX, sy = p.posY, sz = p.posZ;
        float oldYaw = p.rotationYaw, oldPitch = p.rotationPitch;
        try {
            move(p, destination, point, yaw, pitch);
            if (p.worldObj != destination || p.getDistanceSq(point[0], point[1], point[2]) > .01)
                throw new IllegalStateException("Transfer did not arrive");
            if (ElectricItem.manager.discharge(stack, COST, 6, true, false, false) < COST)
                throw new IllegalStateException("Charge reservation changed");
        } catch (RuntimeException failure) {
            stack.setTagCompound(saved);
            try {
                move(p, source, new double[] { sx, sy, sz }, oldYaw, oldPitch);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            message(p, "failed");
            return false;
        }
        COOLDOWN.entrySet()
            .removeIf(e -> e.getValue() <= tick);
        COOLDOWN.put(p.getUniqueID(), tick + 600);
        SpacetimeEffects.burst(source, sx, sy, sz);
        SpacetimeEffects.burst(destination, point[0], point[1], point[2]);
        p.inventoryContainer.detectAndSendChanges();
        message(p, "success");
        return true;
    }

    public static boolean enterProsperity(EntityPlayerMP p) {
        if (!eligible(p) || Config.prosperityDimId < 0) return false;
        WorldServer w = destination(Config.prosperityDimId);
        if (w == null) return false;
        ChunkCoordinates spawn = w.getSpawnPoint();
        // At most four generated chunks, 81 columns and 256 heights. No bed/spawn fallback on failed scan.
        double[] point = null;
        long deadline = System.nanoTime() + 250000000L;
        for (int r = 0; r <= 4 && point == null; r++)
            for (int dx = -r; dx <= r && point == null; dx++) for (int dz = -r; dz <= r && point == null; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                int x = spawn.posX + dx, z = spawn.posZ + dz;
                w.getChunkFromChunkCoords(x >> 4, z >> 4);
                if (System.nanoTime() > deadline) {
                    message(p, "unsafe");
                    return false;
                }
                for (int y = Math.min(254, w.getActualHeight() - 2); y > 0; y--) {
                    if (System.nanoTime() > deadline) {
                        message(p, "unsafe");
                        return false;
                    }
                    if (safe(w, p, x + .5, y, z + .5)) {
                        point = new double[] { x + .5, y, z + .5 };
                        break;
                    }
                }
            }
        if (point == null) {
            message(p, "unsafe");
            return false;
        }
        WorldServer source = (WorldServer) p.worldObj;
        double x = p.posX, y = p.posY, z = p.posZ;
        float yaw = p.rotationYaw, pitch = p.rotationPitch;
        try {
            move(p, w, point, yaw, pitch);
            if (p.worldObj != w || p.getDistanceSq(point[0], point[1], point[2]) > .01)
                throw new IllegalStateException("Transfer did not arrive");
        } catch (RuntimeException failure) {
            try {
                move(p, source, new double[] { x, y, z }, yaw, pitch);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            message(p, "failed");
            return false;
        }
        SpacetimeEffects.burst(source, x, y, z);
        SpacetimeEffects.burst(w, point[0], point[1], point[2]);
        return true;
    }

    private static WorldServer destination(int dim) {
        if (!DimensionManager.isDimensionRegistered(dim)) return null;
        try {
            if (DimensionManager.getWorld(dim) == null) DimensionManager.initDimension(dim);
        } catch (RuntimeException failure) {
            return null;
        }
        return DimensionManager.getWorld(dim);
    }

    static double[] safeLanding(WorldServer w, EntityPlayerMP player, double x, double y, double z) {
        long deadline = System.nanoTime() + 250000000L;
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y), bz = MathHelper.floor_double(z);
        // Include one-block hazard margin; no more than four chunks for this eleven-block footprint.
        for (int cx = (bx - 5) >> 4; cx <= (bx + 5) >> 4; cx++)
            for (int cz = (bz - 5) >> 4; cz <= (bz + 5) >> 4; cz++) {
                w.getChunkFromChunkCoords(cx, cz);
                if (System.nanoTime() > deadline) return null;
            }
        java.util.List<int[]> offsets = new java.util.ArrayList<>();
        for (int dx = -4; dx <= 4; dx++)
            for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) offsets.add(new int[] { dx, dy, dz });
        offsets.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2]));
        for (int[] o : offsets) {
            if (System.nanoTime() > deadline) return null;
            double px = x + o[0], py = y + o[1], pz = z + o[2];
            if (safe(w, player, px, py, pz)) return new double[] { px, py, pz };
        }
        return null;
    }

    private static boolean safe(WorldServer w, EntityPlayerMP player, double x, double y, double z) {
        if (y < 1 || y + 1.8 >= w.getActualHeight()) return false;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
        if (!w.getCollidingBoundingBoxes(player, box)
            .isEmpty() || w.isAnyLiquid(box)) return false;
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y), bz = MathHelper.floor_double(z);
        for (int fx = MathHelper.floor_double(x - .3); fx <= MathHelper.floor_double(x + .3); fx++)
            for (int fz = MathHelper.floor_double(z - .3); fz <= MathHelper.floor_double(z + .3); fz++)
                if (!w.getBlock(fx, by - 1, fz)
                    .isSideSolid(w, fx, by - 1, fz, ForgeDirection.UP)) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
            Block b = w.getBlock(bx + dx, by + dy, bz + dz);
            Material m = b.getMaterial();
            if (m.isLiquid() || m == Material.fire
                || b == Blocks.cactus
                || b == Blocks.web
                || b == Blocks.portal
                || b == Blocks.end_portal) return false;
            String n = b.getUnlocalizedName()
                .toLowerCase(java.util.Locale.ROOT);
            if (n.contains("hazard") || n.contains("toxic") || n.contains("spike")) return false;
        }
        return true;
    }

    private static void move(EntityPlayerMP p, WorldServer w, double[] point, float yaw, float pitch) {
        if (!eligible(p)) throw new IllegalStateException("Player cannot transfer");
        if (!TRANSFERRING.add(p.getUniqueID())) throw new IllegalStateException("Transfer in progress");
        try {
            if (p.dimension != w.provider.dimensionId) MinecraftServer.getServer()
                .getConfigurationManager()
                .transferPlayerToDimension(p, w.provider.dimensionId, new ExactTeleporter(w, point, yaw, pitch));
            p.playerNetServerHandler.setPlayerLocation(point[0], point[1], point[2], yaw, pitch);
            p.motionX = p.motionY = p.motionZ = 0;
            p.fallDistance = 0;
        } finally {
            TRANSFERRING.remove(p.getUniqueID());
        }
    }

    private static final class ExactTeleporter extends Teleporter {

        final double[] point;
        final float yaw, pitch;

        ExactTeleporter(WorldServer w, double[] p, float y, float t) {
            super(w);
            point = p;
            yaw = y;
            pitch = t;
        }

        @Override
        public void placeInPortal(Entity e, double x, double y, double z, float rotation) {
            e.setLocationAndAngles(point[0], point[1], point[2], yaw, pitch);
        }

        @Override
        public boolean placeInExistingPortal(Entity e, double x, double y, double z, float r) {
            return true;
        }

        @Override
        public boolean makePortal(Entity e) {
            return false;
        }

        @Override
        public void removeStalePortalLocations(long t) {}
    }
}
