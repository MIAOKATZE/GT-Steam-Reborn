package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Server-only execution of the frozen per-species event timelines. No terrain writes. */
final class AuthoredEchoAbilities {

    private static final Map<String, JsonObject> PROFILES = new HashMap<>();
    private final EntityOldEcho owner;
    private final List<Bolt> bolts = new ArrayList<>();
    private final Map<Integer, CombatGeometry> warnings = new HashMap<>();
    private double targetX, targetY, targetZ;
    private int serial;

    AuthoredEchoAbilities(EntityOldEcho owner) {
        this.owner = owner;
    }

    static JsonObject profile(EchoKind kind) {
        if (kind.style != EchoKind.BattleStyle.AUTHORED && kind.style != EchoKind.BattleStyle.CONTROLLED) return null;
        synchronized (PROFILES) {
            JsonObject p = PROFILES.get(kind.code);
            if (p != null) return p;
            String path = "/assets/gtsr/liminal/" + kind.code + "/code/behavior.json";
            try (InputStream stream = AuthoredEchoAbilities.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing echo profile " + path);
                p = new JsonParser().parse(new InputStreamReader(stream, "UTF-8"))
                    .getAsJsonObject();
                PROFILES.put(kind.code, p);
                return p;
            } catch (Exception failure) {
                throw new IllegalStateException(path, failure);
            }
        }
    }

    static double number(JsonObject p, String key, double fallback) {
        return p.has(key) ? p.get(key)
            .getAsDouble() : fallback;
    }

    static String text(JsonObject p, String key, String fallback) {
        return p.has(key) ? p.get(key)
            .getAsString() : fallback;
    }

    JsonObject skill(int index) {
        JsonArray skills = profile(owner.getKind()).getAsJsonArray("skills");
        return skills.get(Math.max(0, Math.min(skills.size() - 1, index - 1)))
            .getAsJsonObject();
    }

    double range(int index) {
        return number(skill(index), "range", 3);
    }

    String clip(int index) {
        return text(skill(index), "clip", "attack");
    }

    int duration(int index) {
        return (int) number(skill(index), "duration", 40);
    }

    int cooldown(int index) {
        return (int) number(skill(index), "cooldown", 80);
    }

    int select() {
        return 1 + owner.getRNG()
            .nextInt(
                profile(owner.getKind()).getAsJsonArray("skills")
                    .size());
    }

    int deathDuration() {
        return (int) number(profile(owner.getKind()).getAsJsonObject("death"), "duration", 60);
    }

    int spawnDuration() {
        return (int) number(profile(owner.getKind()).getAsJsonObject("spawn"), "duration", 40);
    }

    boolean stationary() {
        return "stationary".equals(text(profile(owner.getKind()), "locomotion", "ground"));
    }

    boolean hovering() {
        String mode = text(profile(owner.getKind()), "locomotion", "ground");
        return "hover".equals(mode) || "flying".equals(mode);
    }

    void begin(EntityPlayer target, int index) {
        warnings.clear();
        targetX = target.posX;
        targetY = target.posY;
        targetZ = target.posZ;
        serial = (serial + 1) % 1000000;
        for (JsonElement e : skill(index).getAsJsonArray("events")) {
            JsonObject event = e.getAsJsonObject();
            String type = text(event, "type", "");
            if ("damage".equals(type) || "projectile".equals(type)) {
                CombatGeometry g = geometry(event);
                int key = 10000000 + serial * 128
                    + event.get("at")
                        .getAsInt();
                warnings.put(key, g);
                CombatEffects.send(
                    owner,
                    key,
                    0,
                    event.get("at")
                        .getAsInt(),
                    1,
                    g);
            }
        }
        events(skill(index), 0);
    }

    void tickSkill(int index, int ticks) {
        // Actual leap movement is swept against the loaded collision world, never a teleport.
        if ((owner.getKind() == EchoKind.DI04 || owner.getKind() == EchoKind.DR18) && ticks >= 10 && ticks < 28) {
            double dx = targetX - owner.posX, dz = targetZ - owner.posZ,
                len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            double vy = owner.getKind() == EchoKind.DI04 ? (ticks < 18 ? .18 : -.18) : 0;
            if (loaded(owner.posX + dx / len * .18, owner.posZ + dz / len * .18))
                owner.moveEntity(dx / len * .18, vy, dz / len * .18);
        }
        events(skill(index), ticks);
    }

    void death(int ticks) {
        events(profile(owner.getKind()).getAsJsonObject("death"), ticks);
    }

    void cancel() {
        bolts.clear();
        if (owner.worldObj != null && !owner.worldObj.isRemote)
            for (Map.Entry<Integer, CombatGeometry> warning : warnings.entrySet())
                CombatEffects.send(owner, warning.getKey(), 2, 0, 1, warning.getValue());
        warnings.clear();
    }

    private void events(JsonObject timeline, int ticks) {
        JsonArray events = timeline.getAsJsonArray("events");
        if (events == null) return;
        for (JsonElement e : events) {
            JsonObject event = e.getAsJsonObject();
            int at = (int) number(event, "at", -1);
            if (at == ticks || ("pull".equals(text(event, "type", "")) && ticks > at
                && ticks < at + Math.min(20, (int) number(event, "duration", 1)))) execute(event);
        }
    }

    private double[] point(JsonObject e) {
        boolean target = "target".equals(text(e, "anchor", "self"));
        double[] p = { target ? targetX : owner.posX, target ? targetY : owner.posY, target ? targetZ : owner.posZ };
        JsonArray offset = e.getAsJsonArray("offset");
        if (offset != null) for (int i = 0; i < 3; i++) p[i] += offset.get(i)
            .getAsDouble();
        return p;
    }

    private CombatGeometry geometry(JsonObject e) {
        double[] p = point(e);
        String shape = text(e, "shape", "sphere");
        return new CombatGeometry(
            "cone".equals(shape) ? CombatGeometry.CONE
                : "line".equals(shape) || "projectile".equals(text(e, "type", "")) ? CombatGeometry.LINE
                    : CombatGeometry.CIRCLE,
            p[0],
            p[1],
            p[2],
            targetX,
            targetZ,
            number(e, "radius", .25),
            0);
    }

    private boolean valid(EntityPlayer p) {
        return p.worldObj == owner.worldObj && p.isEntityAlive() && !p.capabilities.isCreativeMode;
    }

    private boolean loaded(double x, double z) {
        return owner.worldObj.getChunkProvider()
            .chunkExists(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    private boolean loadedFootprint(double[] p, double radius) {
        for (int x = ((int) Math.floor(p[0] - radius)) >> 4; x <= ((int) Math.floor(p[0] + radius)) >> 4; x++)
            for (int z = ((int) Math.floor(p[2] - radius)) >> 4; z <= ((int) Math.floor(p[2] + radius)) >> 4; z++)
                if (!owner.worldObj.getChunkProvider()
                    .chunkExists(x, z)) return false;
        return true;
    }

    private boolean contains(JsonObject e, double[] c, EntityPlayer p) {
        double x = p.posX - c[0], y = p.posY + p.height * .5 - c[1], z = p.posZ - c[2];
        double r = number(e, "radius", 1), d2 = x * x + z * z;
        if ("line".equals(text(e, "shape", ""))) return geometry(e).contains(p.posX, p.posY, p.posZ);
        if (d2 > r * r || Math.abs(y) > r + p.height * .5) return false;
        if ("cone".equals(text(e, "shape", ""))) {
            double ax = targetX - c[0], az = targetZ - c[2], len = Math.max(.001, Math.sqrt(ax * ax + az * az));
            return (x * ax + z * az) / len >= Math.sqrt(d2) * Math.cos(Math.toRadians(number(e, "angle", 90) * .5));
        }
        return x * x + y * y + z * z <= (r + p.width * .5) * (r + p.width * .5);
    }

    private void execute(JsonObject event) {
        String type = text(event, "type", "");
        double[] p = point(event);
        if (!loadedFootprint(p, number(event, "radius", 1))) return;
        if ("projectile".equals(type)) {
            launch(event, p);
            return;
        }
        if ("summon".equals(type)) {
            summon(event, p);
            return;
        }
        if ("vfx".equals(type)) return;
        for (Object object : owner.worldObj.playerEntities) {
            EntityPlayer target = (EntityPlayer) object;
            if (!valid(target) || !contains(event, p, target) || !owner.canEntityBeSeen(target)) continue;
            if ("damage".equals(type)) {
                float amount = (float) number(event, "amount", 0);
                if (owner.getKind() == EchoKind.DR19) amount = owner.repriseDamage();
                if (target.attackEntityFrom(DamageSource.causeMobDamage(owner), amount)) {
                    double strength = number(event, "knockback", 0), dx = target.posX - p[0], dz = target.posZ - p[2];
                    double length = Math.max(.01, Math.sqrt(dx * dx + dz * dz));
                    target.addVelocity(dx / length * strength, strength > 0 ? .1 : 0, dz / length * strength);
                    target.velocityChanged = true;
                }
            } else if ("status".equals(type)) {
                String effect = text(event, "effect", "");
                Potion potion = "slowness".equals(effect) ? Potion.moveSlowdown
                    : "weakness".equals(effect) ? Potion.weakness
                        : "blindness".equals(effect) ? Potion.blindness : null;
                if (potion != null) target.addPotionEffect(
                    new PotionEffect(
                        potion.id,
                        (int) number(event, "duration", 20),
                        (int) number(event, "amplifier", 0)));
            } else if ("pull".equals(type)) {
                double dx = p[0] - target.posX, dy = p[1] - target.posY, dz = p[2] - target.posZ;
                double length = Math.max(1, Math.sqrt(dx * dx + dy * dy + dz * dz)),
                    strength = Math.min(.15, number(event, "strength", .1));
                target.addVelocity(dx / length * strength, dy / length * strength, dz / length * strength);
                target.velocityChanged = true;
                target.motionX = Math.max(-.6, Math.min(.6, target.motionX));
                target.motionY = Math.max(-.3, Math.min(.3, target.motionY));
                target.motionZ = Math.max(-.6, Math.min(.6, target.motionZ));
            }
        }
    }

    private void summon(JsonObject e, double[] p) {
        if (owner.isSummonedEcho() || owner.summonsRemaining() <= 0) return;
        int count = Math.min(4, (int) number(e, "count", 1));
        for (int i = 0; i < count && owner.summonsRemaining() > 0; i++) {
            double angle = i * Math.PI * 2 / count, radius = number(e, "radius", 3),
                x = p[0] + Math.cos(angle) * radius, z = p[2] + Math.sin(angle) * radius;
            if (!loaded(x, z)) continue;
            double y = CombatGeometry.groundY(owner.worldObj, x, p[1] + 2, z);
            int bx = (int) Math.floor(x), by = (int) Math.floor(y - .01), bz = (int) Math.floor(z);
            if (by < 0 || by > 254
                || owner.worldObj.getBlock(bx, by, bz)
                    .getCollisionBoundingBoxFromPool(owner.worldObj, bx, by, bz) == null)
                continue;
            EntityOldEcho child = new EntityOldEcho(owner.worldObj);
            child.initializeEcho(EchoKind.byCode(text(e, "entity", "dr-04")), "", x, y, z, false);
            child.markSummoned(owner, Math.min(400, (int) number(e, "lifetime", 400)));
            if (child.boundingBox.maxY > 256 || !loadedFootprint(new double[] { x, y, z }, child.width)
                || !owner.worldObj.getCollidingBoundingBoxes(child, child.boundingBox)
                    .isEmpty()
                || !owner.worldObj.checkNoEntityCollision(child.boundingBox)
                || owner.worldObj.isAnyLiquid(child.boundingBox)) continue;
            if (owner.worldObj.spawnEntityInWorld(child)) owner.consumeSummon();
        }
    }

    private void launch(JsonObject e, double[] p) {
        for (int i = 0; i < Math.min(4, (int) number(e, "count", 1)) && bolts.size() < 16; i++) {
            double dx = targetX - p[0], dy = targetY + .6 - p[1], dz = targetZ - p[2],
                len = Math.max(.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double spread = Math.toRadians(number(e, "spread", 0)) * (i - ((int) number(e, "count", 1) - 1) * .5),
                speed = number(e, "speed", .6);
            bolts.add(
                new Bolt(
                    p[0],
                    p[1],
                    p[2],
                    (dx * Math.cos(spread) - dz * Math.sin(spread)) / len * speed,
                    dy / len * speed,
                    (dz * Math.cos(spread) + dx * Math.sin(spread)) / len * speed,
                    (float) number(e, "amount", 3),
                    number(e, "gravity", 0),
                    (int) number(e, "lifetime", 60)));
        }
    }

    void tickBolts() {
        Iterator<Bolt> it = bolts.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            double nx = b.x + b.vx, ny = b.y + b.vy, nz = b.z + b.vz;
            if (--b.life <= 0 || !loaded(nx, nz)) {
                it.remove();
                continue;
            }
            Vec3 from = Vec3.createVectorHelper(b.x, b.y, b.z), to = Vec3.createVectorHelper(nx, ny, nz);
            net.minecraft.util.MovingObjectPosition block = owner.worldObj.rayTraceBlocks(from, to);
            EntityPlayer hit = null;
            double nearest = block == null ? Double.POSITIVE_INFINITY : from.squareDistanceTo(block.hitVec);
            for (Object o : owner.worldObj.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                if (!valid(p)) continue;
                AxisAlignedBB box = p.boundingBox.expand(.12, .12, .12);
                net.minecraft.util.MovingObjectPosition intercept = box.calculateIntercept(from, to);
                if (intercept != null || box.isVecInside(from)) {
                    double d = box.isVecInside(from) ? 0 : from.squareDistanceTo(intercept.hitVec);
                    if (d < nearest) {
                        nearest = d;
                        hit = p;
                    }
                }
            }
            CombatEffects.send(
                owner,
                20000000 + serial * 32,
                1,
                2,
                1,
                new CombatGeometry(CombatGeometry.LINE, b.x, b.y, b.z, nx, nz, .08, 0));
            if (hit != null) {
                hit.attackEntityFrom(DamageSource.causeMobDamage(owner), b.damage);
                it.remove();
                continue;
            }
            if (block != null) {
                it.remove();
                continue;
            }
            b.x = nx;
            b.y = ny;
            b.z = nz;
            b.vy -= b.gravity;
        }
    }

    private static final class Bolt {

        double x, y, z, vx, vy, vz, gravity;
        float damage;
        int life;

        Bolt(double x, double y, double z, double vx, double vy, double vz, float damage, double gravity, int life) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.damage = damage;
            this.gravity = gravity;
            this.life = Math.min(100, life);
        }
    }
}
