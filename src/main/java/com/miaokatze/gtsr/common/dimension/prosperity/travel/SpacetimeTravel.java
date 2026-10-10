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
import net.minecraft.util.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.config.Config;

import ic2.api.item.ElectricItem;

/** All target discovery and transfers happen on the server tick thread. Never edits terrain. */
public final class SpacetimeTravel {

    public static final double COST = 320000;
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
        com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon.clampCharge(stack);
        if (ElectricItem.manager.discharge(stack, COST, 3, true, false, true) < COST) {
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
            if (ElectricItem.manager.discharge(stack, COST, 3, true, false, false) < COST)
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
        return enterProsperity(p, true);
    }

    public static boolean enterProsperity(EntityPlayerMP p, boolean reportFailure) {
        if (!eligible(p) || Config.prosperityDimId < 0) return false;
        WorldServer w = destination(Config.prosperityDimId);
        if (w == null) {
            if (reportFailure) message(p, "failed");
            return false;
        }
        double[] point;
        try {
            point = findProsperityLanding(w, p);
        } catch (RuntimeException failure) {
            if (reportFailure) message(p, "failed");
            return false;
        }
        if (point == null) {
            if (reportFailure) message(p, "unsafe");
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
            if (reportFailure) message(p, "failed");
            return false;
        }
        SpacetimeEffects.burst(source, x, y, z);
        SpacetimeEffects.burst(w, point[0], point[1], point[2]);
        return true;
    }

    /** Server-thread diagnostic entry; initializes only the already registered configured dimension. */
    public static double[] findProsperityLanding() {
        if (Config.prosperityDimId < 0) return null;
        WorldServer world = destination(Config.prosperityDimId);
        return world == null ? null : findProsperityLanding(world, null);
    }

    private static double[] findProsperityLanding(WorldServer world, EntityPlayerMP player) {
        java.util.List<int[]> columns = new java.util.ArrayList<>();
        for (int x = -32; x <= 32; x++) for (int z = -32; z <= 32; z++) {
            if (x * x + z * z <= 32 * 32) columns.add(new int[] { x, z });
        }
        // Squared distance of block centers to (0,0); the origin column wins equal-distance ties.
        columns.sort(
            java.util.Comparator
                .<int[]>comparingInt(c -> (2 * c[0] + 1) * (2 * c[0] + 1) + (2 * c[1] + 1) * (2 * c[1] + 1))
                .thenComparingInt(c -> c[0] * c[0] + c[1] * c[1]));
        java.util.Set<Long> loaded = new java.util.HashSet<>();
        for (int[] column : columns) {
            int x = column[0], z = column[1];
            // Load before reading height/collision. Cold generation cannot consume a scan-time deadline.
            // The entity collision query expands its footprint, so include two blocks at chunk edges.
            for (int cx = (x - 2) >> 4; cx <= (x + 2) >> 4; cx++)
                for (int cz = (z - 2) >> 4; cz <= (z + 2) >> 4; cz++) {
                    long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                    if (loaded.add(key)) world.theChunkProviderServer.loadChunk(cx, cz);
                }
            int height = world.getHeightValue(x, z);
            // A fixed five-height surface window bounds CPU work without descending into caves.
            for (int y = height; y <= height + 2; y++)
                if (surfaceSafe(world, player, x + .5, y, z + .5)) return new double[] { x + .5, y, z + .5 };
            for (int y = height - 1; y >= height - 2; y--)
                if (surfaceSafe(world, player, x + .5, y, z + .5)) return new double[] { x + .5, y, z + .5 };
        }
        return null;
    }

    /** Checks the same exposed surface and safety predicate used by portal discovery; writes no terrain. */
    public static boolean verifyProsperityLanding(WorldServer world, double[] point) {
        return world != null && world.provider.dimensionId == Config.prosperityDimId
            && point != null
            && point.length == 3
            && Double.isFinite(point[0])
            && Double.isFinite(point[1])
            && Double.isFinite(point[2])
            && (point[0] - .5) * (point[0] - .5) + (point[2] - .5) * (point[2] - .5) <= 32 * 32
            && surfaceSafe(world, null, point[0], point[1], point[2]);
    }

    private static boolean surfaceSafe(WorldServer world, EntityPlayerMP player, double x, double y, double z) {
        if (y < 1 || y + 1.8 >= world.getActualHeight()) return false;
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y), bz = MathHelper.floor_double(z);
        Material floor = world.getBlock(bx, by - 1, bz)
            .getMaterial();
        return floor != Material.leaves && floor != Material.wood
            && world.canBlockSeeTheSky(bx, by, bz)
            && safe(world, player, x, y, z);
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
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y), bz = MathHelper.floor_double(z);
        // Include one-block hazard margin; no more than four chunks for this eleven-block footprint.
        for (int cx = (bx - 5) >> 4; cx <= (bx + 5) >> 4; cx++)
            for (int cz = (bz - 5) >> 4; cz <= (bz + 5) >> 4; cz++) {
                w.theChunkProviderServer.loadChunk(cx, cz);
            }
        long deadline = System.nanoTime() + 250000000L;
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
        if (hasLandingCollision(w, player, box) || w.isAnyLiquid(box)) return false;
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

    private static boolean hasLandingCollision(WorldServer world, EntityPlayerMP player, AxisAlignedBB box) {
        if (player != null) return !world.getCollidingBoundingBoxes(player, box)
            .isEmpty();
        // Vanilla's entity collision loop dereferences the querying entity. Diagnostics have no player.
        java.util.List<AxisAlignedBB> collisions = new java.util.ArrayList<>();
        for (int x = MathHelper.floor_double(box.minX); x <= MathHelper.floor_double(box.maxX); x++)
            for (int z = MathHelper.floor_double(box.minZ); z <= MathHelper.floor_double(box.maxZ); z++)
                for (int y = MathHelper.floor_double(box.minY) - 1; y <= MathHelper.floor_double(box.maxY); y++) {
                    world.getBlock(x, y, z)
                        .addCollisionBoxesToList(world, x, y, z, box, collisions, null);
                    if (!collisions.isEmpty()) return true;
                }
        for (Object candidate : world.getEntitiesWithinAABBExcludingEntity(null, box.expand(.25, .25, .25))) {
            AxisAlignedBB entityBox = ((Entity) candidate).getBoundingBox();
            if (entityBox != null && entityBox.intersectsWith(box)) return true;
        }
        return false;
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
