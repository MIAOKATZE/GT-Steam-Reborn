package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.FakePlayer;

import com.google.gson.JsonObject;

/** Actual complete Runtime.tick through activation/timer initialization, native nearest-player and NBT APIs. */
public final class RemasterSpawnerPlayerCheck {
    private static int checks;
    private static TestWorld world;
    private static final Block TEST_BLOCK = new TestBlock();
    private static TileRemasterNode tile;
    private static NBTTagCompound state;
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        world = allocate(TestWorld.class);
        World.class.getField("perWorldStorage").set(world, new MapStorage(null));
        World.class.getField("playerEntities").set(world, new ArrayList<>());
        World.class.getField("rand").set(world, new Random(123));
        RemasterData data = RemasterData.get(world);
        RemasterSite site = new RemasterSite("fallen_foundry", 0, 123, 0, 64, 0); data.register(site);
        JsonObject authored = null;
        for (JsonObject node : RemasterRuntime.nodes(site)) if ("spawner".equals(RemasterRuntime.string(node, "role", ""))) { authored = node; break; }
        check(authored != null, "actual packaged spawner selected");
        String key = RemasterRuntime.string(authored, "block", "");
        String id = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
        // Native registry stand-in for the one unbooted GTSR material, not for admission or activation logic.
        Block.blockRegistry.addObject(4090, id, TEST_BLOCK);
        tile = new TileRemasterNode(); tile.setWorldObj(world);
        tile.siteId = site.id(); tile.nodeId = RemasterRuntime.string(authored, "id", ""); tile.role = "spawner";
        int[] p = RemasterRuntime.nodePosition(world, site, authored);
        tile.xCoord = p[0]; tile.yCoord = p[1]; tile.zCoord = p[2];
        tile.blockMetadata = RemasterRuntime.meta(key);
        world.tile = tile; world.meta = tile.blockMetadata;
        check(RemasterRuntime.node(tile) != null, "native coordinate/block/metadata ownership validated");
        state = data.state(site.id());
        String scenario = args.length == 0 ? "masked" : args[0];
        if (scenario.equals("masked")) {
            run(player(1, true, false, false), player(2, false, false, false), true, "creative bystander cannot mask survival player");
            run(player(1, false, true, false), player(2, false, false, false), true, "dead bystander cannot mask living player");
            run(player(1, false, false, true), player(2, false, false, false), true, "FakePlayer cannot mask real survival player");
        } else {
            run(null, null, false, "empty world stays inactive");
            run(player(1, true, false, false), null, false, "creative-only world stays inactive");
            run(player(1, false, false, true), null, false, "fake-only world stays inactive");
            run(player(1, false, true, false), null, false, "dead-only world stays inactive");
            run(player(16, false, false, false), null, false, "exact native 16-block boundary remains excluded");
            run(player(15.999, false, false, false), null, true, "just inside radius activates");
            run(player(17, false, false, false), null, false, "outside radius stays inactive");
        }
        System.out.println("PASS: " + checks + " native runtime spawner player " + scenario + " checks");
    }
    @SuppressWarnings("unchecked") private static void run(EntityPlayerMP first, EntityPlayerMP second, boolean active, String reason) {
        world.playerEntities.clear(); if (first != null) world.playerEntities.add(first); if (second != null) world.playerEntities.add(second);
        NBTTagCompound spawner = new NBTTagCompound(); state.setTag("spawner:" + tile.nodeId, spawner);
        Random expected = new Random(123); world.rand.setSeed(123);
        RemasterRuntime.tick(tile);
        check(spawner.hasKey("next") == active, reason);
        if (active) check(spawner.getLong("next") == 1000 + RemasterSpawnerContract.delay(expected), "eligible activation retains exact next-deadline RNG draw");
        check(world.rand.nextLong() == expected.nextLong(), "candidate scan consumes no random draws");
        check(!spawner.hasKey("serial") && !spawner.hasKey("count"), "timer activation admits no entity or quota early");
    }
    private static EntityPlayerMP player(double distance, boolean creative, boolean dead, boolean fake) throws Exception {
        EntityPlayerMP p = fake ? allocate(FakePlayer.class) : allocate(EntityPlayerMP.class);
        p.worldObj = world; p.posX = tile.xCoord + .5 + distance; p.posY = tile.yCoord + .5; p.posZ = tile.zCoord + .5;
        p.capabilities = new PlayerCapabilities(); p.capabilities.isCreativeMode = creative; p.isDead = dead;
        DataWatcher watcher = new DataWatcher(p); watcher.addObject(6, 20F);
        Field dw = Entity.class.getDeclaredField("dataWatcher"); dw.setAccessible(true); dw.set(p, watcher);
        return p;
    }
    public static final class TestBlock extends Block {
        private TestBlock() { super(net.minecraft.block.material.Material.rock); }
    }
    public static final class TestWorld extends WorldServer {
        TileRemasterNode tile; int meta;
        private TestWorld() { super(null, null, "fixture", 0, null, null); }
        @Override public long getTotalWorldTime() { return 1000; }
        @Override public Block getBlock(int x, int y, int z) { return TEST_BLOCK; }
        @Override public int getBlockMetadata(int x, int y, int z) { return meta; }
        @Override public TileEntity getTileEntity(int x, int y, int z) { return tile; }
    }
    @SuppressWarnings("unchecked") private static <T> T allocate(Class<T> type) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return (T) ((sun.misc.Unsafe) f.get(null)).allocateInstance(type);
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); checks++; }
}
