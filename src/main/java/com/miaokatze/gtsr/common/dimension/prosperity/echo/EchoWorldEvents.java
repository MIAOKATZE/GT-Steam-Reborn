package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Register this owner on both the FML bus (ticks) and Forge bus (confirmed echo death).
 * Only loaded chunks participate. This handler never generates terrain or performs retrogen.
 */
public final class EchoWorldEvents {

    public static final int WORLD_CAP = 24, KIND_CAP = 8, PLAYER_CAP = 4;
    private static final EchoKind[] NIGHT_KINDS = { EchoKind.DR01, EchoKind.DR12, EchoKind.DR20 };

    /** Pure eligibility predicate for provider/night/sky regression, independent of any World fixture. */
    public static boolean nightEligible(boolean prosperityProvider, boolean daytime, long time, boolean seesSky) {
        long dayTick = Math.floorMod(time, 24000L);
        return prosperityProvider && !daytime && dayTick >= 13000L && dayTick <= 23000L && seesSky;
    }

    public static boolean ritualKind(int kind) {
        return kind == 11 || kind == 14 || kind == 20;
    }

    public static boolean distanceEligible(double nearestPlayerSquared, double ownerPlayerSquared) {
        return nearestPlayerSquared >= 24 * 24 && ownerPlayerSquared <= 48 * 48;
    }

    private static boolean valid(EntityPlayer player, World world) {
        return player != null && player.worldObj == world
            && player.isEntityAlive()
            && !player.capabilities.isCreativeMode;
    }

    /** Eligible survival players; no chunk reads. */
    private static List<EntityPlayer> players(World world) {
        List<EntityPlayer> result = new ArrayList<EntityPlayer>();
        for (Object object : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (valid(player, world)) result.add(player);
        }
        return result;
    }

    private static boolean loadedSquare(World world, int x, int z, int radius) {
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) {
                if (!world.getChunkProvider()
                    .chunkExists(cx, cz)) return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)
            || event.world.isRemote
            || !(event.world.provider instanceof WorldProviderProsperityRuins)) return;
        WorldServer world = (WorldServer) event.world;
        long now = world.getTotalWorldTime();
        if (now % 20 != 0) return;
        boolean night = nightEligible(true, world.isDaytime(), world.getWorldTime(), true);
        List<EntityOldEcho> nightMobs = new ArrayList<EntityOldEcho>();
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof EntityOldEcho)) continue;
            EntityOldEcho echo = (EntityOldEcho) object;
            if (!echo.isNightSpawn() || echo.isDead) continue;
            if (night) {
                if (echo.isEntityAlive()) nightMobs.add(echo);
            } else {
                fadeAtSunrise(world, echo);
            }
        }
        if (!night) return;
        List<EntityPlayer> players = players(world);
        // Ritual proximity is cheap and independent of natural spawn density.
        triggerRituals(world, players);
        if (now % 200 != 0 || players.isEmpty()
            || nightMobs.size() >= WORLD_CAP
            || !world.getGameRules()
                .getGameRuleBooleanValue("doMobSpawning")
            || world.difficultySetting == EnumDifficulty.PEACEFUL) return;
        spawnNight(world, players, nightMobs);
    }

    private void fadeAtSunrise(WorldServer world, EntityOldEcho echo) {
        echo.setAttackTarget(null);
        echo.getNavigator()
            .clearPathEntity();
        world.func_147487_a("smoke", echo.posX, echo.posY + .7, echo.posZ, 16, .4, .8, .4, .025);
        // Particle afterglow supplies the fade. Immediate retirement prevents another dawn attack.
        echo.setDead(); // Retirement, never a combat death/reward/node completion.
    }

    private void spawnNight(WorldServer world, List<EntityPlayer> players, List<EntityOldEcho> nightMobs) {
        EnumMap<EchoKind, Integer> counts = new EnumMap<EchoKind, Integer>(EchoKind.class);
        for (EchoKind kind : NIGHT_KINDS) counts.put(kind, 0);
        for (EntityOldEcho echo : nightMobs) if (counts.containsKey(echo.getKind())) {
            counts.put(echo.getKind(), counts.get(echo.getKind()) + 1);
        }
        for (EntityPlayer owner : players) {
            if (nightMobs.size() >= WORLD_CAP) return;
            if (nearCount(owner, nightMobs) >= PLAYER_CAP) continue;
            // Separate stream: no consumption of World.rand or terrain/populate RNG.
            Random random = new Random(
                world.getSeed() ^ world.getTotalWorldTime()
                    ^ owner.getUniqueID()
                        .getMostSignificantBits()
                    ^ owner.getUniqueID()
                        .getLeastSignificantBits()
                    ^ 0x4E49474854454348L);
            for (int attempt = 0; attempt < 12; attempt++) {
                if (nightMobs.size() >= WORLD_CAP || nearCount(owner, nightMobs) >= PLAYER_CAP) break;
                EchoKind kind = NIGHT_KINDS[random.nextInt(NIGHT_KINDS.length)];
                if (counts.get(kind) >= KIND_CAP) continue;
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = 24 + random.nextDouble() * 24;
                int x = (int) Math.floor(owner.posX + Math.cos(angle) * radius);
                int z = (int) Math.floor(owner.posZ + Math.sin(angle) * radius);
                if (!loadedSquare(world, x, z, 3)) continue;
                int y = world.getTopSolidOrLiquidBlock(x, z);
                if (y < 30 || y > 240
                    || !world.getBlock(x, y - 1, z)
                        .getMaterial()
                        .blocksMovement()
                    || world.getBlock(x, y - 1, z)
                        .getMaterial()
                        .isLiquid()
                    || !nightEligible(true, world.isDaytime(), world.getWorldTime(), world.canBlockSeeTheSky(x, y, z)))
                    continue;
                double nearest = Double.MAX_VALUE;
                boolean cappedNeighbor = false;
                for (EntityPlayer player : players) {
                    double distance = player.getDistanceSq(x + .5, y, z + .5);
                    nearest = Math.min(nearest, distance);
                    if (distance <= 48 * 48 && nearCount(player, nightMobs) >= PLAYER_CAP) cappedNeighbor = true;
                }
                if (cappedNeighbor || !distanceEligible(nearest, owner.getDistanceSq(x + .5, y, z + .5))) continue;
                EntityOldEcho echo = new EntityOldEcho(world);
                echo.initializeEcho(kind, "", x + .5, y, z + .5, true);
                echo.setHomeYaw(random.nextFloat() * 360);
                if (!world.checkNoEntityCollision(echo.boundingBox)
                    || !world.getCollidingBoundingBoxes(echo, echo.boundingBox)
                        .isEmpty()
                    || world.isAnyLiquid(echo.boundingBox)) continue;
                if (world.spawnEntityInWorld(echo)) {
                    counts.put(kind, counts.get(kind) + 1);
                    nightMobs.add(echo);
                }
            }
        }
    }

    private static int nearCount(EntityPlayer player, List<EntityOldEcho> mobs) {
        int count = 0;
        for (EntityOldEcho mob : mobs) if (player.getDistanceSqToEntity(mob) <= 48 * 48) count++;
        return count;
    }

    private void triggerRituals(WorldServer world, List<EntityPlayer> players) {
        Set<String> checked = new HashSet<String>();
        RuinsEncounterData data = RuinsEncounterData.get(world);
        for (EntityPlayer player : players) {
            int cx = ((int) Math.floor(player.posX)) >> 4, cz = ((int) Math.floor(player.posZ)) >> 4;
            for (RuinSite site : RuinsSitePlanner.near(world.getSeed(), cx, cz)) {
                if (!ritualKind(site.kind) || checked.contains(site.id())) continue;
                double x = site.x + site.width / 2.0, z = site.z + site.depth / 2.0;
                // The owner node proves this is a generated site, not a merely planned terrain candidate.
                if (player.getDistanceSq(x, site.y, z) <= 12 * 12 && data.created(site.id(), "entity0")) {
                    checked.add(site.id());
                    summonRitual(world, site);
                }
            }
        }
    }

    /** Interaction hook: caller supplies a genuine generated site; successful summons consume one-day cooldown. */
    public static boolean summonRitual(WorldServer world, RuinSite site) {
        if (world == null || world.isRemote
            || !(world.provider instanceof WorldProviderProsperityRuins)
            || site == null
            || !ritualKind(site.kind)
            || !nightEligible(true, world.isDaytime(), world.getWorldTime(), true)) return false;
        int x = site.x + site.width / 2, z = site.z + site.depth / 2;
        if (!loadedSquare(world, x, z, 1)) return false;
        int y = Math.min(205, site.y + 55); // Leaves room for the 45.5-block authored apparition below world ceiling.
        RuinsEncounterData data = RuinsEncounterData.get(world);
        long now = world.getTotalWorldTime();
        if (!data.created(site.id(), "entity0") || !data.ritualReady(site.id(), now)) return false;
        for (Object object : world.loadedEntityList) if (object instanceof EntityOldEcho) {
            EntityOldEcho echo = (EntityOldEcho) object;
            if (!echo.isDead && echo.getKind()
                .isRitual()
                && echo.getEncounterId()
                    .equals(site.id()))
                return false;
        }
        EntityOldEcho apparition = new EntityOldEcho(world);
        apparition.initializeEcho(EchoKind.DO01, site.id(), x + .5, y, z + .5, false);
        apparition.setNodeIndex(-1);
        apparition.setHomeYaw(90);
        if (!world.spawnEntityInWorld(apparition)) return false;
        data.ritual(site.id(), now);
        world.playSoundEffect(x + .5, site.y + 1, z + .5, "portal.travel", .65F, .65F);
        return true;
    }

    @SubscribeEvent
    public void death(EchoDeathEvent event) {
        EntityOldEcho echo = event.entity;
        if (!echo.worldObj.isRemote && !echo.isNightSpawn()
            && !echo.getKind()
                .isRitual()
            && echo.getPlatformId() >= 0
            && echo.getEncounterId()
                .startsWith("echo:")) {
            RuinsEncounterData.get(echo.worldObj)
                .died(echo.getEncounterId(), echo.getPlatformId());
        }
    }
}
