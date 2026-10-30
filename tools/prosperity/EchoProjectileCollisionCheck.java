package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;

/** Offline actual projectile tick + native vanilla DDA/AABB regression, not an in-game test.
 * The world supplies only block/chunk reads; effect transport is a no-op test seam.
 */
public final class EchoProjectileCollisionCheck {

    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        NativeRayWorld world = world(-1);
        Vec3 from = Vec3.createVectorHelper(.25, 64.8, .5);
        world.rayTraceBlocks(from, Vec3.createVectorHelper(4.25, 64.8, .5));
        check(from.xCoord > 3, "native DDA really mutates its input start");
        crossing(-1, 2, 1.5, "air segment cannot skip a player in an earlier voxel");
        crossing(3, 2, 1.5, "player before wall wins over later block hit");
        crossing(1, 2, 1, "wall before player remains authoritative");
        crossing(-1, .25, .25, "starting inside a player remains an immediate hit");
        System.out.println("PASS: " + checks + " native echo projectile collision checks");
    }

    private static void crossing(int wall, double playerX, double expected, String reason) throws Exception {
        NativeRayWorld world = world(wall);
        Target player = allocate(Target.class);
        player.worldObj = world;
        player.capabilities = new PlayerCapabilities();
        player.posX = playerX;
        player.posY = 64;
        player.posZ = .5;
        set(Entity.class, player, "boundingBox", AxisAlignedBB.getBoundingBox(playerX - .3, 64, .2, playerX + .3, 65.8, .8));
        world.playerEntities.add(player);
        Owner owner = allocate(Owner.class);
        owner.worldObj = world;
        EchoCombatProjectile bolt = new EchoCombatProjectile(world);
        set(EchoCombatProjectile.class, bolt, "owner", owner);
        set(EchoCombatProjectile.class, bolt, "damage", 4f);
        set(EchoCombatProjectile.class, bolt, "blastRadius", .5d);
        bolt.setPosition(.25, 64.8, .5);
        bolt.motionX = 4;
        bolt.onUpdate();
        check(bolt.hasExploded(), reason + " (detonated)");
        check(Math.abs(bolt.posX - expected) < 1e-7, reason + ": expected " + expected + ", got " + bolt.posX);
    }

    private static NativeRayWorld world(int wall) throws Exception {
        NativeRayWorld world = allocate(NativeRayWorld.class);
        world.wall = wall;
        world.playerEntities = new ArrayList();
        set(World.class, world, "provider", new WorldProviderSurface());
        set(World.class, world, "theProfiler", new Profiler());
        set(World.class, world, "chunkProvider", Proxy.newProxyInstance(EchoProjectileCollisionCheck.class.getClassLoader(),
            new Class<?>[] { IChunkProvider.class }, (proxy, method, values) -> {
                if (method.getName().equals("chunkExists")) return true;
                throw new UnsupportedOperationException(method.getName());
            }));
        return world;
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }

    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    private static final class Owner extends EntityOldEcho {
        private Owner() { super(null); }
        @Override public EchoKind getKind() { return EchoKind.DR03; }
        @Override public boolean isEntityAlive() { return true; }
    }

    private static final class Target extends EntityPlayerMP {
        private Target() { super(null, null, null, null); }
        @Override public boolean isEntityAlive() { return true; }
        @Override public float getEyeHeight() { return 1.62f; }
        @Override public boolean attackEntityFrom(DamageSource source, float damage) { return true; }
    }

    private static final class NativeRayWorld extends World {
        private int wall;
        private NativeRayWorld() { super(null, "", (net.minecraft.world.WorldSettings) null, null, null); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int getRenderDistanceChunks() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public Block getBlock(int x, int y, int z) { return wall >= 0 && x == wall ? Blocks.stone : Blocks.air; }
        @Override public int getBlockMetadata(int x, int y, int z) { return 0; }
        @Override public boolean handleMaterialAcceleration(AxisAlignedBB box, Material material, Entity entity) { return false; }
        @Override public boolean isMaterialInBB(AxisAlignedBB box, Material material) { return false; }
        @Override public boolean isAABBInMaterial(AxisAlignedBB box, Material material) { return false; }
        @Override public void playSoundEffect(double x, double y, double z, String sound, float volume, float pitch) { }
    }
}
