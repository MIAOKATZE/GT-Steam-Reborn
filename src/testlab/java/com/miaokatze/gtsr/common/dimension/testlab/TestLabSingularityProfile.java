// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S39PacketPlayerAbilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity.SingularityType;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Explicit opt-in native server profile. Only the wrapper timer is synthetic; super.updateEntity runs naturally. */
public final class TestLabSingularityProfile implements TestLabTask {

    private static final String BASELINE_TILE = "c78ef639357ec645e3fa5aa747ab645f55d6b028c61d93f6542034635524a28d";
    private static final int WARMUP = 200, SAMPLES = 400, PERIOD = WARMUP + SAMPLES;
    private static final long SEED = 0x4754535253494e47L;
    private static TestLabSingularityProfile active;
    static {
        TileEntity.addMapping(MeasuredTile.class, "gtsr_test_singularity_profile");
    }
    private final Map<String, Object> evidence = new LinkedHashMap<>(), acceptance = new LinkedHashMap<>();
    private final List<Map<String, Object>> phases = new ArrayList<>();
    private final List<int[]> blocks = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<Long> all = new ArrayList<>(), scan = new ArrayList<>(), ordinary = new ArrayList<>(),
        worldTimes = new ArrayList<>();
    private WorldServer world;
    private MeasuredTile measured;
    private FakePlayer actor;
    private CountingHandler handler;
    private TileEntity machine;
    private Block machineBlock;
    private int x, y, z, phase, age, functionalAge, passes;
    private boolean candidate, sampleWorld;
    private long worldStart, phaseStart, startTime;
    private java.nio.file.Path incremental;
    private int flyingCancelled;
    private static final String[] NAMES = { "nature_r16_air_scan", "nature_r16_dense_scan",
        "nature_r16_xp128_items32" };

    public String id() {
        return "perf.singularity_profile";
    }

    public String displayName() {
        return "Natural singularity native timing and identity/drop gates";
    }

    public int timeoutTicks() {
        return 6500;
    }

    public String performancePhase() {
        return phase < NAMES.length ? NAMES[phase] + (age <= WARMUP ? ".warmup" : ".sample") : "identity-drop-gates";
    }

    public void start(WorldServer world, int x, int y, int z) throws Exception {
        this.world = world;
        this.x = x + 80;
        this.y = 100;
        this.z = z + 80;
        String hash = classHash(TileRunawaySingularity.class);
        candidate = !BASELINE_TILE.equals(hash);
        evidence.put("loadedTileClassSha256", hash);
        evidence.put("baselineTileClassSha256", BASELINE_TILE);
        evidence.put("mode", candidate ? "candidate-functional-gates" : "baseline-observation-not-new-contract-pass");
        evidence.put(
            "scope",
            "Natural WorldServer ticks, timed subclass calls production super.updateEntity; no direct updateEntity calls, no real network/client/FPS evidence");
        evidence.put("warmupTicksPerPhase", WARMUP);
        evidence.put("sampleTicksPerPhase", SAMPLES);
        evidence.put("seed", SEED);
        evidence.put("origin", Arrays.asList(this.x, this.y, this.z));
        evidence.put(
            "worldClass",
            world.getClass()
                .getName());
        evidence.put(
            "worldProviderClass",
            world.provider.getClass()
                .getName());
        incremental = world.getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .resolve(
                "gtsr-testlab/singularity-profile/" + UUID.randomUUID()
                    .toString() + ".json");
        evidence.put(
            "incrementalPath",
            incremental.toAbsolutePath()
                .toString());
        evidence.put("configuredWorldTimeNotMutated", true);
        evidence.put("positionsControlledEachTick", true);
        evidence.put("phases", phases);
        evidence.put("acceptance", acceptance);
        startTime = System.nanoTime();
        active = this;
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        phase = 0;
        beginPhase();
        persistEvidence();
    }

    private void put(int px, int py, int pz, Block block) {
        TestLabFixtures.track(world, px, py, pz);
        blocks.add(new int[] { px, py, pz });
        world.setBlock(px, py, pz, block, 0, 2);
    }

    private TileRunawaySingularity node(SingularityType type, int attr, double range, double speed, boolean destroy,
        boolean timer) {
        TestLabFixtures.track(world, x, y, z);
        blocks.add(new int[] { x, y, z });
        TileRunawaySingularity.spawnSingularity(world, x, y, z, range, speed, 0, -1, attr, "white", 10, destroy, type);
        TileRunawaySingularity tile = (TileRunawaySingularity) world.getTileEntity(x, y, z);
        if (!timer) return tile;
        NBTTagCompound data = new NBTTagCompound();
        tile.writeToNBT(data);
        MeasuredTile result = new MeasuredTile();
        result.readFromNBT(data);
        world.setTileEntity(x, y, z, result);
        measured = result;
        return result;
    }

    private void clearOwned() {
        measured = null;
        for (Entity entity : entities) {
            entity.setDead();
            world.removeEntity(entity);
        }
        entities.clear();
        actor = null;
        handler = null;
        for (int[] p : blocks) {
            world.removeTileEntity(p[0], p[1], p[2]);
            world.setBlock(p[0], p[1], p[2], Blocks.air, 0, 2);
        }
        blocks.clear();
    }

    private void beginPhase() {
        clearOwned();
        all.clear();
        scan.clear();
        ordinary.clear();
        worldTimes.clear();
        age = 0;
        flyingCancelled = 0;
        world.rand.setSeed(SEED);
        phaseStart = world.getWorldTime();
        boolean natural = true, destroy = phase < 2;
        int range = 16;
        node(
            natural ? SingularityType.NATURAL : SingularityType.RUNAWAY,
            natural ? -4 : -2,
            range,
            phase == 1 ? 3 : 1,
            destroy,
            true);
        if (phase == 1)
            for (int dx = -6; dx <= 6; dx++) for (int dy = -6; dy <= 6; dy++) for (int dz = -6; dz <= 6; dz++)
                if (dx != 0 || dy != 0 || dz != 0) put(x + dx, y + dy, z + dz, Blocks.stone);
        if (phase == 2) for (int i = 0; i < 128; i++) add(new EntityXPOrb(world, x + 12, y + .5, z + .5, 1));
        if (phase == 2) for (int i = 0; i < 32; i++) {
            EntityItem item = new EntityItem(world, x + 8, y + .5, z + .5, new ItemStack(Blocks.stone));
            item.delayBeforeCanPickup = 32767;
            add(item);
        }
        positionEntities();
    }

    private void add(Entity entity) {
        entity.getEntityData()
            .setBoolean("GTSRSingularityProfileOwned", true);
        entity.getEntityData()
            .setInteger("MiaoTestFixtureDimension", world.provider.dimensionId);
        entity.getEntityData()
            .setString("MiaoTestFixtureOwner", "gtsr");
        entity.getEntityData()
            .setString(
                "MiaoTestFixtureProvider",
                world.provider.getClass()
                    .getName());
        entities.add(entity);
        if (!world.spawnEntityInWorld(entity))
            throw new IllegalStateException("Explicit benchmark entity spawn refused");
    }

    private void positionEntities() {
        int index = 0;
        for (Entity entity : entities) {
            double angle = (index++ * 2.399963229728653) + age * .012;
            double r = entity instanceof EntityItem ? 8 : 12 + 5 * Math.sin(age * .04);
            if (entity == actor) {
                entity.setPosition(x + 5.5, y + .5, z + .5);
                actor.capabilities.allowFlying = true;
                actor.capabilities.isFlying = true;
            } else entity
                .setPosition(x + .5 + Math.cos(angle) * r, y + .5 + (index % 3) * .12, z + .5 + Math.sin(angle) * r);
            entity.motionX = entity.motionY = entity.motionZ = 0;
            if (entity instanceof EntityItem) ((EntityItem) entity).age = 0;
        }
    }

    public TestLabReport.Result tick(WorldServer world, int ticks) throws Exception {
        try {
            return tickProfile(world, ticks);
        } catch (Exception | LinkageError failure) {
            evidence.put("failure", failure.toString());
            persistEvidence();
            return new TestLabReport.Result(TestLabReport.Status.FAIL, "Profile retained after " + failure, evidence);
        }
    }

    private TestLabReport.Result tickProfile(WorldServer world, int ticks) throws Exception {
        if (phase < NAMES.length) {
            if (actor != null && !actor.capabilities.isFlying && !actor.capabilities.allowFlying) flyingCancelled++;
            if (age >= PERIOD) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", NAMES[phase]);
                row.put("worldTimeStart", phaseStart);
                row.put("worldTimeEnd", world.getWorldTime());
                row.put("range", 16);
                row.put("speed", phase == 1 ? 3 : 1);
                row.put("destroyBlocks", phase < 2);
                row.put("attribute", -4);
                row.put(
                    "type",
                    measured.getType()
                        .name());
                row.put("duration", measured.getDuration());
                row.put("entitiesConfigured", entities.size());
                row.put(
                    "entitiesAlive",
                    entities.stream()
                        .filter(e -> !e.isDead)
                        .count());
                row.put("tile", stats(all));
                row.put("scanModuloTicks", stats(scan));
                row.put("otherTicks", stats(ordinary));
                row.put("worldTickEventInterval", stats(worldTimes));
                row.put("tileNanos", new ArrayList<>(all));
                row.put("scanNanos", new ArrayList<>(scan));
                row.put("worldNanos", new ArrayList<>(worldTimes));
                row.put(
                    "remainingOwnedStone",
                    blocks.stream()
                        .filter(p -> world.getBlock(p[0], p[1], p[2]) == Blocks.stone)
                        .count());
                row.put("worldLoadedTiles", world.loadedTileEntityList.size());
                row.put("worldLoadedEntities", world.loadedEntityList.size());
                row.put("flyingCancelledTicks", flyingCancelled);
                if (handler != null) {
                    row.put("velocityPacketsToFakeHandler", handler.velocity);
                    row.put("abilityPacketsToFakeHandler", handler.abilities);
                }
                phases.add(row);
                persistEvidence();
                if (all.size() != SAMPLES)
                    throw new IllegalStateException("Natural timing sample count mismatch " + all.size());
                phase++;
                if (phase < NAMES.length) beginPhase();
                else {
                    clearOwned();
                    beginFunctional();
                }
            } else positionEntities();
            return null;
        }
        functionalAge++;
        if (functionalAge == 80) restoreMeasured();
        if (functionalAge <= 160 && phase == 3) {
            if (world.getBlock(x + 2, y, z) != machineBlock || world.getTileEntity(x + 2, y, z) != machine)
                acceptance.put("stableMachinePreserved", false);
            if (functionalAge < 160) return null;
            acceptance.put(
                "stableTypeAndParamsRetained",
                measured.getType() == SingularityType.STABLE && measured.getAttributeId() == 0
                    && measured.getSpeed() == 100
                    && measured.getRange() == 4);
            clearOwned();
            positive(SingularityType.NATURAL);
            phase = 4;
            functionalAge = 0;
            return null;
        }
        if (phase == 4 && functionalAge >= 24) {
            acceptance.put(
                "naturalAbsorbsMachinePositive",
                world.getBlock(x + 2, y, z) == Blocks.air && world.getTileEntity(x + 2, y, z) == null);
            clearOwned();
            positive(SingularityType.RUNAWAY);
            phase = 5;
            functionalAge = 0;
            return null;
        }
        if (phase == 5 && functionalAge >= 24) {
            acceptance.put(
                "runawayAbsorbsMachinePositive",
                world.getBlock(x + 2, y, z) == Blocks.air && world.getTileEntity(x + 2, y, z) == null);
            clearOwned();
            harvestGates();
            boolean valid = acceptance.values()
                .stream()
                .allMatch(Boolean.TRUE::equals);
            evidence.put("newFunctionalRequirementsSatisfied", valid);
            evidence.put("elapsedWallSeconds", (System.nanoTime() - startTime) / 1e9);
            persistEvidence();
            evidence.put(
                "cleanupScope",
                "Only tracked blocks and explicitly spawned benchmark entities; no world reset outside this station");
            return new TestLabReport.Result(
                !candidate || valid ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL,
                candidate ? "Native sample and candidate functional gates"
                    : "Baseline observations only; false new-contract gates are retained",
                evidence);
        }
        return null;
    }

    private void beginFunctional() {
        node(SingularityType.STABLE, 0, 4, 100, true, true);
        restoreMeasured();
        placeMachine();
        functionalAge = 0;
        acceptance.put("stableMachinePreserved", true);
    }

    private void restoreMeasured() {
        if (measured == null) throw new IllegalStateException("Measured stable node missing");
        NBTTagCompound tag = new NBTTagCompound();
        measured.writeToNBT(tag);
        MeasuredTile restored = new MeasuredTile();
        restored.readFromNBT(tag);
        world.setTileEntity(x, y, z, restored);
        measured = restored;
    }

    private void placeMachine() {
        machine = (TileEntity) TestLabFixtures
            .machine(world, x + 2, y, z, MetaTileEntityID.STEAM_INPUT_HATCH_GENERIC.ID);
        blocks.add(new int[] { x + 2, y, z });
        machineBlock = world.getBlock(x + 2, y, z);
    }

    private void positive(SingularityType type) {
        node(type, type == SingularityType.NATURAL ? -4 : 0, 4, 100, true, false);
        world.rand.setSeed(SEED);
        placeMachine();
    }

    private void harvestGates() {
        List<Map<String, Object>> rows = new ArrayList<>();
        evidence.put("harvestTrials", rows);
        boolean naturalValid = true, othersValid = true;
        FakePlayer miner = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("10a3a7d0-ef4d-4f03-9ec7-e429b9e1e8c5"), "SingularityMiner"));
        miner.playerNetServerHandler = new CountingHandler(miner);
        for (int i = 0; i < 24; i++) {
            clearOwned();
            SingularityType type = i < 16 ? SingularityType.NATURAL
                : i < 20 ? SingularityType.STABLE : SingularityType.RUNAWAY;
            boolean creative = i >= 12 && i < 16;
            miner.theItemInWorldManager
                .setGameType(creative ? WorldSettings.GameType.CREATIVE : WorldSettings.GameType.SURVIVAL);
            miner.setPosition(x + 3, y + .5, z + .5);
            miner.setCurrentItemOrArmor(0, null);
            node(type, -4, 4, 1, false, false);
            world.rand.setSeed(SEED + i);
            boolean harvested = miner.theItemInWorldManager.tryHarvestBlock(x, y, z);
            int drops = 0;
            List<EntityItem> list = world.getEntitiesWithinAABB(
                EntityItem.class,
                AxisAlignedBB.getBoundingBox(x - 8, y - 8, z - 8, x + 9, y + 9, z + 9));
            for (EntityItem item : list) if (!item.isDead && item.getEntityItem() != null
                && item.getEntityItem()
                    .getItem()
                    == GTSRItemList.SteamEntangledSingularity.get(1)
                        .getItem()) {
                            drops += item.getEntityItem().stackSize;
                            item.setDead();
                            world.removeEntity(item);
                        }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", type.name());
            row.put("creative", creative);
            row.put("harvested", harvested);
            row.put("drops", drops);
            rows.add(row);
            if (type == SingularityType.NATURAL && !creative) naturalValid &= harvested && drops >= 1 && drops <= 2;
            else othersValid &= drops == 0;
        }
        acceptance.put("naturalSuccessfulHarvestDrops1To2", naturalValid);
        acceptance.put("creativeAndNonNaturalNoDrop", othersValid);
        miner.theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
    }

    public void stop(WorldServer world) {
        if (active == this) active = null;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        clearOwned();
    }

    private void persistEvidence() throws java.io.IOException {
        java.nio.file.Files.createDirectories(incremental.getParent());
        evidence.put("completedPerformancePhases", phases.size());
        evidence.put("currentPhase", phase);
        com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher.publishAtomic(
            incremental,
            new com.google.gson.GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(evidence)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String debug(WorldServer world) {
        return performancePhase() + " phase=" + phase + " naturalTicks=" + age + " samples=" + all.size();
    }

    @SubscribeEvent
    public void worldTick(TickEvent.WorldTickEvent event) {
        if (event.world != world || phase >= NAMES.length) return;
        if (event.phase == TickEvent.Phase.START) {
            sampleWorld = age >= WARMUP && age < PERIOD;
            worldStart = System.nanoTime();
        } else if (sampleWorld) worldTimes.add(System.nanoTime() - worldStart);
    }

    public static final class MeasuredTile extends TileRunawaySingularity {

        @Override
        public void updateEntity() {
            TestLabSingularityProfile p = active;
            boolean record = p != null && p.measured == this
                && p.phase < NAMES.length
                && p.age >= WARMUP
                && p.age < PERIOD;
            boolean scanning = worldObj != null && worldObj.getWorldTime() % 4 == 0;
            long begin = System.nanoTime();
            try {
                super.updateEntity();
            } finally {
                long elapsed = System.nanoTime() - begin;
                if (p != null && p.measured == this && p.phase < NAMES.length) {
                    p.age++;
                    if (record) {
                        p.all.add(elapsed);
                        (scanning ? p.scan : p.ordinary).add(elapsed);
                    }
                }
            }
        }
    }

    private static final class CountingHandler extends NetHandlerPlayServer {

        int velocity, abilities;

        CountingHandler(FakePlayer player) {
            super(MinecraftServer.getServer(), new NetworkManager(false), player);
        }

        @Override
        public void sendPacket(Packet packet) {
            if (packet instanceof S12PacketEntityVelocity) velocity++;
            if (packet instanceof S39PacketPlayerAbilities) abilities++;
        }
    }

    private static Map<String, Object> stats(List<Long> samples) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("samples", samples.size());
        if (samples.isEmpty()) return result;
        long[] sorted = new long[samples.size()];
        long sum = 0;
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = samples.get(i);
            sum += sorted[i];
        }
        Arrays.sort(sorted);
        result.put("meanUs", sum / (double) sorted.length / 1000);
        result.put("maxUs", sorted[sorted.length - 1] / 1000.0);
        for (int percentile : new int[] { 50, 95, 99 }) result.put(
            "p" + percentile + "Us",
            sorted[Math.max(0, (int) Math.ceil(sorted.length * percentile / 100.0) - 1)] / 1000.0);
        return result;
    }

    private static String classHash(Class<?> type) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = type.getResourceAsStream(
            "/" + type.getName()
                .replace('.', '/') + ".class")) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) value.append(String.format("%02x", b & 255));
        return value.toString();
    }
}
