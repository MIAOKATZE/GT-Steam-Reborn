package com.miaokatze.gtsr.client.encounter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntityEncounterBase;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.ProsperityDeathVisual;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Tick-owned, bounded translucent voxel stream; never joins vanilla's open particle batch. */
public final class CubeRuneParticle {

    private static final int MAX_CUBES = 512, TICK_BUDGET = 48;
    private static final List<CubeRuneParticle> particles = new ArrayList<>();
    private static final List<BodyRune> bodyRunes = new ArrayList<>();

    private static final class BodyRune {

        final int entity, kind, mask, index;
        final java.util.UUID source;
        final boolean awakening;
        int age;

        BodyRune(Entity e, int kind, int mask, int index, boolean awakening) {
            entity = e.getEntityId();
            source = e.getUniqueID();
            this.kind = kind;
            this.mask = mask;
            this.index = index;
            this.awakening = awakening;
        }
    }

    private static final java.util.Map<java.util.UUID, DeathBurst> deaths = new java.util.LinkedHashMap<>();
    private static final java.util.Set<java.util.UUID> deathSeen = new java.util.HashSet<>();
    private static final java.util.Map<java.util.UUID, SpawnRune> spawns = new java.util.LinkedHashMap<>();
    private static final java.util.Set<java.util.UUID> spawnSeen = new java.util.HashSet<>();

    private static final class DeathBurst {

        final double x, y, z, width, height;
        final long born, seed;
        final boolean gold;
        final int count;

        DeathBurst(Entity source, ProsperityDeathVisual visual, long time) {
            x = source.posX;
            y = source.posY;
            z = source.posZ;
            width = source.width;
            height = source.height;
            gold = visual.isGoldenDeath();
            count = BoundedDeathVisual.count(width, height);
            born = time - visual.getDeathAnimationTicks() + visual.getDeathAnimationHoldTicks();
            seed = source.getUniqueID()
                .getMostSignificantBits()
                ^ source.getUniqueID()
                    .getLeastSignificantBits();
        }
    }

    private static final class SpawnRune {

        final int entity;
        final java.util.UUID source;
        final long born;

        SpawnRune(Entity source, long time) {
            entity = source.getEntityId();
            this.source = source.getUniqueID();
            born = time;
        }
    }

    private static WorldClient owner;
    private static int emitted;
    private static final float[][] TIERS = { { 1F, .42F, .08F }, { 1F, .12F, .16F }, { .7F, .2F, 1F } };
    private final double x, y, z, vx, vy, vz, size;
    private final float r, g, b;
    private final int life;
    private int age;

    public CubeRuneParticle() {
        this(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
    }

    private CubeRuneParticle(double x, double y, double z, double vx, double vy, double vz, double size, float r,
        float g, float b, int life) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.size = size;
        this.r = r;
        this.g = g;
        this.b = b;
        this.life = Math.max(1, life);
    }

    public static void emit(WorldClient world, double x, double y, double z, double vx, double vy, double vz,
        double size, float r, float g, float b, int life) {
        if (owner != world || particles.size() >= MAX_CUBES || emitted >= TICK_BUDGET) return;
        particles.add(new CubeRuneParticle(x, y, z, vx, vy, vz, size, r, g, b, life));
        emitted++;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        emitted = 0;
        if (mc.theWorld != owner) {
            particles.clear();
            bodyRunes.clear();
            deaths.clear();
            deathSeen.clear();
            spawns.clear();
            spawnSeen.clear();
            owner = mc.theWorld;
        }
        if (owner == null || mc.thePlayer == null || mc.isGamePaused()) return;
        Iterator<CubeRuneParticle> it = particles.iterator();
        while (it.hasNext()) {
            CubeRuneParticle c = it.next();
            if (++c.age >= c.life) it.remove();
        }
        Iterator<BodyRune> bodyIt = bodyRunes.iterator();
        while (bodyIt.hasNext()) {
            BodyRune rune = bodyIt.next();
            Entity source = owner.getEntityByID(rune.entity);
            if (++rune.age >= 24 || source == null
                || !source.getUniqueID()
                    .equals(rune.source)
                || !bodyActive(source)) bodyIt.remove();
        }
        int bodyBudget = 6;
        for (Object object : owner.loadedEntityList) {
            if (!(object instanceof Entity)) continue;
            Entity source = (Entity) object;
            if (!bodyActive(source) || mc.thePlayer.getDistanceSqToEntity(source) > 64 * 64
                || source.ticksExisted % 4 != 0) continue;
            boolean king = source instanceof com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing;
            int kind = king ? 0
                : ((com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho) source).getKind()
                    == com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.DC02 ? 1 : 2;
            boolean awakening = king
                ? ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) source)
                    .getEncounterState() == 1
                : ((com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho) source).getIndustrialBossStage()
                    == 1;
            int skill = king
                ? ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) source).getSkillId()
                : ((com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho) source).getSkillId();
            int mask = BossSkillVisuals.recentMask(source) | (skill > 0 ? 1 << (skill - 1) : 0);
            if (king
                && ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) source).getMeteorTicks()
                    >= 0)
                mask |= 4;
            for (int i = 0; i < 2 && bodyBudget > 0 && bodyRunes.size() < 96; i++, bodyBudget--)
                bodyRunes.add(new BodyRune(source, kind, mask, source.ticksExisted * 2 + i, awakening));
            if (bodyBudget <= 0) break;
        }
        long now = owner.getTotalWorldTime();
        java.util.Set<java.util.UUID> loaded = new java.util.HashSet<>();
        for (Object object : owner.loadedEntityList) {
            if (!(object instanceof Entity)) continue;
            Entity e = (Entity) object;
            loaded.add(e.getUniqueID());
            if (e instanceof ProsperityDeathVisual) {
                ProsperityDeathVisual visual = (ProsperityDeathVisual) e;
                if (visual.getDeathAnimationTicks() > 0 && !deathSeen.contains(e.getUniqueID())
                    && deaths.size() < 96
                    && mc.thePlayer.getDistanceSqToEntity(e) < 64 * 64) {
                    deaths.put(e.getUniqueID(), new DeathBurst(e, visual, now));
                    deathSeen.add(e.getUniqueID());
                }
            }
            if (e instanceof EntityEncounterBase && !e.isDead
                && ((EntityEncounterBase) e).getHealth() > 0
                && e.ticksExisted < 60
                && !bodyActive(e)
                && !spawnSeen.contains(e.getUniqueID())
                && (((EntityEncounterBase) e).getSpawnerTier() > 0 || positiveBuffMask((EntityEncounterBase) e) > 0)
                && spawns.size() < 24
                && mc.thePlayer.getDistanceSqToEntity(e) < 64 * 64) {
                spawns.put(e.getUniqueID(), new SpawnRune(e, now));
                spawnSeen.add(e.getUniqueID());
            }
            if (e instanceof com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoCombatProjectile) {
                com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoCombatProjectile bolt = (com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoCombatProjectile) e;
                if (!bolt.hasExploded() && emitted < 12 && mc.thePlayer.getDistanceSqToEntity(bolt) < 4096) emit(
                    owner,
                    bolt.posX,
                    bolt.posY,
                    bolt.posZ,
                    -bolt.motionX * .05,
                    .008,
                    -bolt.motionZ * .05,
                    .045,
                    bolt.getPalette() == 0 ? 1F : .7F,
                    .3F,
                    bolt.getPalette() == 0 ? .15F : 1F,
                    12);
            }
        }
        Iterator<DeathBurst> dead = deaths.values()
            .iterator();
        while (dead.hasNext()) {
            DeathBurst burst = dead.next();
            if (now - burst.born >= BoundedDeathVisual.duration(burst.gold)) dead.remove();
        }
        deathSeen.retainAll(loaded);
        spawnSeen.retainAll(loaded);
        Iterator<SpawnRune> births = spawns.values()
            .iterator();
        while (births.hasNext()) {
            SpawnRune spawn = births.next();
            Entity source = owner.getEntityByID(spawn.entity);
            if (now - spawn.born >= 32 || source == null
                || source.isDead
                || !source.getUniqueID()
                    .equals(spawn.source))
                births.remove();
        }
    }

    public static int positiveBuffMask(net.minecraft.entity.EntityLivingBase entity) {
        int mask = 0;
        for (Object object : entity.getActivePotionEffects()) {
            net.minecraft.potion.PotionEffect effect = (net.minecraft.potion.PotionEffect) object;
            int id = effect.getPotionID();
            if (id == 5) mask |= 1;
            else if (id == 11) mask |= 2;
            else if (id == 21) mask |= 4;
            else if (id == 10) mask |= 8;
            else if (id == 1) mask |= 16;
        }
        return mask;
    }

    public static float spawnAlpha(Entity entity, float partial) {
        SpawnRune spawn = spawns.get(entity.getUniqueID());
        if (spawn == null || owner == null) return 1;
        return (float) Math.max(.25, Math.min(1, (owner.getTotalWorldTime() - spawn.born + partial) / 18D));
    }

    private static boolean bodyActive(Entity entity) {
        if (entity.isDead || !(entity instanceof net.minecraft.entity.EntityLivingBase)
            || ((net.minecraft.entity.EntityLivingBase) entity).getHealth() <= 0) return false;
        if (entity instanceof com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) {
            int state = ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) entity)
                .getEncounterState();
            return state == 1 || state == 2;
        }
        if (entity instanceof com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho) {
            com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho echo = (com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho) entity;
            boolean kind = echo.getKind() == com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.DC02
                || echo.getKind() == com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.DC08;
            return kind && (echo.getIndustrialBossStage() == 1 || echo.getIndustrialBossStage() == 2);
        }
        return false;
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (owner == null || owner != mc.theWorld || mc.renderViewEntity == null) return;
        Entity camera = mc.renderViewEntity;
        double partial = event.partialTicks;
        double ox = net.minecraft.client.renderer.entity.RenderManager.renderPosX,
            oy = net.minecraft.client.renderer.entity.RenderManager.renderPosY,
            oz = net.minecraft.client.renderer.entity.RenderManager.renderPosZ;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(-ox, -oy, -oz);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            FineRuneRenderer.unlit();
            for (CubeRuneParticle c : particles) {
                double age = c.age + partial, fade = Math.max(0, 1 - age / c.life);
                FineRuneRenderer.voxel(
                    c.x + c.vx * age,
                    c.y + c.vy * age,
                    c.z + c.vz * age,
                    c.size * (.35 + .65 * fade),
                    c.r,
                    c.g,
                    c.b,
                    (float) (.6 * fade),
                    c.size > .07);
            }
            for (BodyRune rune : bodyRunes) {
                Entity source = owner.getEntityByID(rune.entity);
                if (source == null || !source.getUniqueID()
                    .equals(rune.source) || !bodyActive(source)) continue;
                double[] c = BossBodyRunes
                    .sample(rune.kind, rune.mask, source.ticksExisted + partial, rune.index, rune.awakening);
                double yaw = source instanceof com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing
                    ? ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) source).getHomeYaw()
                    : source.prevRotationYaw + (source.rotationYaw - source.prevRotationYaw) * partial;
                double angle = Math.toRadians(180 - yaw);
                double rx = c[0] * Math.cos(angle) + c[2] * Math.sin(angle),
                    rz = -c[0] * Math.sin(angle) + c[2] * Math.cos(angle);
                double ex = source.lastTickPosX + (source.posX - source.lastTickPosX) * partial;
                double ey = source.lastTickPosY + (source.posY - source.lastTickPosY) * partial;
                double ez = source.lastTickPosZ + (source.posZ - source.lastTickPosZ) * partial;
                FineRuneRenderer.voxel(
                    ex + rx,
                    ey + c[1],
                    ez + rz,
                    c[3],
                    (float) c[4],
                    (float) c[5],
                    (float) c[6],
                    (float) (c[7] * (1 - (rune.age + partial) / 24D)),
                    true);
            }
            int coreBudget = 3;
            for (Object object : owner.loadedEntityList) {
                if (!(object instanceof Entity) || coreBudget <= 0) continue;
                Entity source = (Entity) object;
                if (!bodyActive(source) || camera.getDistanceSqToEntity(source) > 64 * 64) continue;
                boolean royal = source instanceof com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing;
                boolean foundry = !royal
                    && ((com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho) source).getKind()
                        == com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.DC02;
                int skill = royal
                    ? ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) source).getSkillId()
                    : ((com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho) source).getSkillId();
                int mask = BossSkillVisuals.recentMask(source) | (skill > 0 ? 1 << (skill - 1) : 0);
                boolean awake = royal
                    ? ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) source)
                        .getEncounterState() == 1
                    : ((com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho) source)
                        .getIndustrialBossStage() == 1;
                double yaw = royal
                    ? ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) source).getHomeYaw()
                    : source.prevRotationYaw + (source.rotationYaw - source.prevRotationYaw) * partial;
                try (GlScope core = new GlScope()) {
                    GL11.glTranslated(
                        source.lastTickPosX + (source.posX - source.lastTickPosX) * partial,
                        source.lastTickPosY + (source.posY - source.lastTickPosY) * partial,
                        source.lastTickPosZ + (source.posZ - source.lastTickPosZ) * partial);
                    GL11.glRotated(180 - yaw, 0, 1, 0);
                    GL11.glTranslated(0, royal ? 6.53 : foundry ? 9.1 : 1.415, royal ? -.81 : foundry ? 1.07 : -.506);
                    if (royal || foundry) GL11.glRotated(90, 1, 0, 0);
                    float[] color = foundry ? new float[] { 1, .52F, .13F }
                        : royal && (mask & 5) == 0 ? new float[] { 1, .75F, .24F } : new float[] { .77F, .38F, 1 };
                    FineRuneRenderer.orbit(
                        royal ? 1.05 : foundry ? .8 : 1.05,
                        source.ticksExisted + partial,
                        awake ? 6 : mask == 0 ? 3 : 5,
                        color[0],
                        color[1],
                        color[2],
                        awake ? .7F : mask == 0 ? .38F : .62F,
                        awake ? .3 : .08);
                }
                coreBudget--;
            }
            int deathBudget = 384;
            double now = owner.getTotalWorldTime() + partial;
            for (DeathBurst burst : deaths.values()) {
                if (camera.getDistanceSq(burst.x, burst.y, burst.z) > 64 * 64) continue;
                for (int i = 0; i < burst.count && deathBudget > 0; i++) {
                    double[] c = BoundedDeathVisual
                        .sample(burst.seed, i, now - burst.born, burst.width, burst.height, burst.gold);
                    if (c[4] <= 0) continue;
                    FineRuneRenderer.voxel(
                        burst.x + c[0],
                        burst.y + c[1],
                        burst.z + c[2],
                        c[3],
                        burst.gold ? 1F : .66F,
                        burst.gold ? .73F : .32F,
                        burst.gold ? .2F : .92F,
                        (float) c[4],
                        i % 3 == 0);
                    deathBudget--;
                }
            }
            for (SpawnRune spawn : spawns.values()) {
                Entity source = owner.getEntityByID(spawn.entity);
                if (source == null || !source.getUniqueID()
                    .equals(spawn.source)) continue;
                double age = now - spawn.born, p = Math.max(0, Math.min(1, age / 32D));
                try (GlScope scopeSpawn = new GlScope()) {
                    GL11.glTranslated(source.posX, source.posY + .12 + Math.sin(p * Math.PI) * .2, source.posZ);
                    int tier = ((EntityEncounterBase) source).getSpawnerTier();
                    float[] color = TIERS[Math.max(0, Math.min(2, tier - 1))];
                    double radius = Math.max(.5, source.width * .7) * (1.7 - .7 * p);
                    FineRuneRenderer.orbit(
                        radius,
                        age * 4,
                        6,
                        color[0],
                        color[1],
                        color[2],
                        (float) (Math.sin(Math.PI * p) * .7),
                        1 - p);
                    GL11.glColor4f(color[0], color[1], color[2], (float) ((1 - p) * .28));
                    FineRuneRenderer.arc(radius * .8, .014, source.height * p, age, 1, 1);
                }
            }
            int runeBudget = 96;
            for (Object object : owner.loadedEntityList) {
                if (!(object instanceof EntityEncounterBase)) continue;
                EntityEncounterBase e = (EntityEncounterBase) object;
                int buffs = positiveBuffMask(e), tier = e.getSpawnerTier();
                if (buffs == 0 || e.getHealth() <= 0 || e.isDead || camera.getDistanceSqToEntity(e) > 48 * 48) continue;
                int count = Math.min(runeBudget, 4 + Integer.bitCount(buffs) * 2);
                if (count <= 0) break;
                runeBudget -= count;
                float[] color = TIERS[Math.max(0, Math.min(2, tier - 1))];
                try (GlScope ring = new GlScope()) {
                    GL11.glTranslated(
                        e.lastTickPosX + (e.posX - e.lastTickPosX) * partial,
                        e.lastTickPosY + (e.posY - e.lastTickPosY) * partial + e.height * .55,
                        e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partial);
                    FineRuneRenderer.orbit(
                        Math.max(.4, e.width * .6 + .2),
                        e.ticksExisted + partial,
                        count,
                        color[0],
                        color[1],
                        color[2],
                        .7F,
                        .2);
                }
            }
        }
    }

    /** Filled six-faced cubes keep the Minecraft silhouette from every camera angle. */
    public static void box(double x, double y, double z, double s) {
        double[][] v = { { x - s, y - s, z - s }, { x + s, y - s, z - s }, { x + s, y + s, z - s },
            { x - s, y + s, z - s }, { x - s, y - s, z + s }, { x + s, y - s, z + s }, { x + s, y + s, z + s },
            { x - s, y + s, z + s } };
        int[] f = { 0, 3, 2, 1, 4, 5, 6, 7, 0, 1, 5, 4, 3, 7, 6, 2, 0, 4, 7, 3, 1, 2, 6, 5 };
        GL11.glBegin(GL11.GL_QUADS);
        for (int i : f) GL11.glVertex3d(v[i][0], v[i][1], v[i][2]);
        GL11.glEnd();
    }
}
