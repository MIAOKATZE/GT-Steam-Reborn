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
        int size = owner.loadedEntityList.size();
        int start = size == 0 ? 0 : (int) (owner.getTotalWorldTime() % size);
        for (int cursor = 0; cursor < size && emitted < TICK_BUDGET / 2 && particles.size() < MAX_CUBES / 2; cursor++) {
            Object o = owner.loadedEntityList.get((cursor + start) % size);
            if (o instanceof com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoCombatProjectile) {
                com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoCombatProjectile bolt = (com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoCombatProjectile) o;
                if (!bolt.hasExploded() && mc.thePlayer.getDistanceSqToEntity(bolt) < 4096) emit(
                    owner,
                    bolt.posX,
                    bolt.posY,
                    bolt.posZ,
                    -bolt.motionX * .05,
                    .008,
                    -bolt.motionZ * .05,
                    .08,
                    bolt.getPalette() == 0 ? 1F : .7F,
                    .3F,
                    bolt.getPalette() == 0 ? .15F : 1F,
                    16);
                continue;
            }
            if (!(o instanceof ProsperityDeathVisual) || !(o instanceof Entity)) continue;
            Entity e = (Entity) o;
            ProsperityDeathVisual deathVisual = (ProsperityDeathVisual) o;
            int death = deathVisual.getDeathAnimationTicks();
            if (death <= deathVisual.getDeathAnimationHoldTicks() || e.isDead
                || mc.thePlayer.getDistanceSqToEntity(e) > 4096) continue;
            int count = deathVisual.isGoldenDeath() ? 3 : 2;
            for (int i = 0; i < count; i++) {
                long seed = (long) e.getEntityId() * 7919 + death * 31L + i * 163;
                double a = (seed & 1023) * Math.PI / 512;
                double h = ((seed >>> 10) & 255) / 255D;
                boolean gold = deathVisual.isGoldenDeath();
                emit(
                    owner,
                    e.posX + Math.cos(a) * e.width * .45,
                    e.posY + h * e.height,
                    e.posZ + Math.sin(a) * e.width * .45,
                    Math.cos(a) * .012,
                    .025,
                    Math.sin(a) * .012,
                    .055 + .065 * h,
                    gold ? 1F : .66F,
                    gold ? .73F : .32F,
                    gold ? .2F : .92F,
                    gold ? 48 : 30);
            }
            if (emitted >= TICK_BUDGET / 2) break;
        }
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
        double ox = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double oy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double oz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
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
            for (CubeRuneParticle c : particles) {
                double age = c.age + partial, fade = Math.max(0, 1 - age / c.life);
                GL11.glColor4f(c.r, c.g, c.b, (float) (.65 * fade));
                box(c.x + c.vx * age, c.y + c.vy * age, c.z + c.vz * age, c.size * (.35 + .65 * fade));
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
                GL11.glColor4f(
                    (float) c[4],
                    (float) c[5],
                    (float) c[6],
                    (float) (c[7] * (1 - (rune.age + partial) / 24D)));
                box(ex + rx, ey + c[1], ez + rz, c[3]);
            }
            int budget = 192;
            for (Object o : owner.loadedEntityList) {
                if (!(o instanceof EntityEncounterBase)) continue;
                EntityEncounterBase e = (EntityEncounterBase) o;
                int tier = e.getSpawnerTier();
                if (tier <= 0 || e.getHealth() <= 0 || e.isDead || camera.getDistanceSqToEntity(e) > 4096) continue;
                int count = 4 + Math.min(3, tier) * 2;
                float[] color = TIERS[Math.min(2, tier - 1)];
                double ex = e.lastTickPosX + (e.posX - e.lastTickPosX) * partial;
                double ey = e.lastTickPosY + (e.posY - e.lastTickPosY) * partial;
                double ez = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partial;
                for (int i = 0; i < count && budget-- > 0; i++) {
                    double a = (e.ticksExisted + partial) * .07 + i * Math.PI * 2 / count;
                    GL11.glColor4f(color[0], color[1], color[2], .65F);
                    box(
                        ex + Math.cos(a) * (e.width * .6 + .35),
                        ey + e.height * .55 + Math.sin(a * 2) * .22,
                        ez + Math.sin(a) * (e.width * .6 + .35),
                        .07 + .02 * Math.sin(a * 3));
                }
                if (budget <= 0) break;
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
