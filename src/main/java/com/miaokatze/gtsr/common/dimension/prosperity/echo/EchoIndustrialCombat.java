package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/** Server-owned industrial skill timelines; cooldowns persist independently of canceled casts. */
final class EchoIndustrialCombat {

    private final EntityOldEcho owner;
    private int cooldown = 80, serial, lastPhase = -1;
    private final int[] skillCooldown = new int[6];
    private int bonesCooldown, bonesTicks, retaliationCooldown, phaseBurstRemaining, phaseBurstInterval;
    private double x, y, z, ox, oz;
    private int broodCount, broodIndex;

    EchoIndustrialCombat(EntityOldEcho owner) {
        this.owner = owner;
    }

    private boolean hive() {
        return owner.getKind() == EchoKind.DC08;
    }

    private int palette() {
        return hive() ? 3 : 0;
    }

    private void sound(String event) {
        owner.worldObj.playSoundEffect(
            owner.posX,
            owner.posY,
            owner.posZ,
            "gtsr:" + (hive() ? "hive." : "colossus.") + event,
            1,
            .85F);
    }

    boolean rangedImmune() {
        return !hive() && bonesTicks > 0;
    }

    void tickTimers() {
        if (cooldown > 0) cooldown--;
        for (int i = 1; i <= 5; i++) if (skillCooldown[i] > 0) skillCooldown[i]--;
        if (bonesCooldown > 0) bonesCooldown--;
        if (bonesTicks > 0) bonesTicks--;
        if (retaliationCooldown > 0) retaliationCooldown--;
        owner.getDataWatcher()
            .updateObject(16, bonesTicks);
        if (phaseBurstRemaining > 0 && phaseBurstInterval-- <= 0) phaseBurst();
    }

    private void phaseBurst() {
        EntityPlayer target = owner.getAttackTarget() instanceof EntityPlayer ? (EntityPlayer) owner.getAttackTarget()
            : null;
        if (target == null || !valid(target)) {
            target = null;
            for (Object object : owner.worldObj.playerEntities) {
                EntityPlayer candidate = (EntityPlayer) object;
                if (valid(candidate) && owner.getDistanceSqToEntity(candidate) <= 32 * 32
                    && (target == null || owner.getDistanceSqToEntity(candidate) < owner.getDistanceSqToEntity(target)))
                    target = candidate;
            }
        }
        if (target == null) return;
        square(target, 8 - phaseBurstRemaining);
        phaseBurstRemaining--;
        phaseBurstInterval = 11;
    }

    private boolean valid(EntityPlayer p) {
        return p.isEntityAlive() && !p.capabilities.isCreativeMode;
    }

    private void slow(int duration, int amplifier) {
        for (Object object : owner.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) object;
            if (valid(p) && owner.getDistanceSqToEntity(p) <= 32 * 32)
                p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, duration, amplifier));
        }
    }

    private void cast(int id) {
        owner.announceSkill(id);
        if (!hive()) slow(100, 0);
        if (hive()) {
            owner.addPotionEffect(new PotionEffect(Potion.resistance.id, 40, 3));
            for (Object object : owner.worldObj.playerEntities) {
                EntityPlayer p = (EntityPlayer) object;
                if (!valid(p) || owner.getDistanceSqToEntity(p) > 25 || !owner.canEntityBeSeen(p)) continue;
                p.attackEntityFrom(DamageSource.causeIndirectMagicDamage(owner, owner), 3);
                push(p, 1.45, .25);
            }
        }
    }

    void damaged(DamageSource source, boolean ranged) {
        if (hive()) return;
        if (ranged && bonesCooldown == 0) {
            bonesTicks = 300;
            bonesCooldown = 1000;
            owner.getDataWatcher()
                .updateObject(16, bonesTicks);
            cast(6);
        } else if (!ranged && source.getEntity() instanceof EntityPlayer && retaliationCooldown == 0) {
            retaliationCooldown = owner.getCombatPhase() == 2 ? 400 : 600;
            cast(7);
            double multiplier = owner.getCombatPhase() == 2 ? 2 : 1;
            CombatGeometry area = new CombatGeometry(
                CombatGeometry.CIRCLE,
                owner.posX,
                owner.posY,
                owner.posZ,
                0,
                0,
                8 * multiplier,
                0);
            for (Object object : owner.worldObj.playerEntities) {
                EntityPlayer p = (EntityPlayer) object;
                if (!valid(p) || !area.contains(p.posX, p.posY, p.posZ) || !owner.canEntityBeSeen(p)) continue;
                p.attackEntityFrom(DamageSource.causeMobDamage(owner), 8);
                push(p, .5 * multiplier, .9 * Math.sqrt(multiplier));
            }
            CombatEffects.send(owner, ++serial * 32, 1, 20, palette(), area, 7, 0);
            sound("stomp");
        }
    }

    void enteredPhase() {
        int phase = owner.getCombatPhase();
        if (phase <= lastPhase) return;
        lastPhase = phase;
        if (phase == 0) return;
        if (!hive()) {
            cast(8);
            hit(new CombatGeometry(CombatGeometry.CIRCLE, owner.posX, owner.posY, owner.posZ, 0, 0, 32, 0), 15, .7);
            slow(600, 2);
        } else if (phase == 1) {
            cast(6);
            phaseBurstRemaining = 8;
            phaseBurstInterval = 0;
            phaseBurst();
        } else {
            cast(7);
            slow(400, 1);
            skillCooldown[1] = skillCooldown[5] = 0;
            cooldown = 0;
        }
        sound("phase");
    }

    void cancel() {
        if (owner.getSkillId() != 0) CombatEffects
            .send(owner, serial * 32, 2, 0, palette(), geometry(owner.getSkillId()), owner.getSkillId(), 0);
        owner.skill(0, 0);
        broodCount = broodIndex = 0;
    }

    private int desire() {
        return (hive() ? new int[] { 160, 120, 80 } : new int[] { 120, 80, 60 })[owner.getCombatPhase()];
    }

    private int skillDelay(int id) {
        int phase = owner.getCombatPhase();
        if (hive()) return id == 1 ? new int[] { 800, 600, 500 }[phase] : id == 2 ? 120 : id == 5 ? 1000 : 500;
        return new int[] { 0, 100, 200, 300, 400, 1200 }[id];
    }

    private boolean hasBrood() {
        for (Object object : owner.worldObj.loadedEntityList)
            if (object instanceof EntityOldEcho && ((EntityOldEcho) object).isSummonedBy(owner)
                && ((EntityOldEcho) object).isEntityAlive()) return true;
        return false;
    }

    void tick(EntityPlayer target) {
        if (lastPhase != owner.getCombatPhase()) enteredPhase();
        int id = owner.getSkillId();
        if (id == 0) {
            if (cooldown > 0 || !owner.canEntityBeSeen(target)) {
                if (!hive() && owner.ticksExisted % 10 == 0) owner.getNavigator()
                    .tryMoveToEntityLiving(target, 1);
                return;
            }
            int[] available = new int[5];
            int count = 0;
            for (int candidate = 1; candidate <= 5; candidate++) {
                if (skillCooldown[candidate] > 0) continue;
                if (!hive()
                    && (candidate == 4 && owner.getCombatPhase() < 1 || candidate == 5 && owner.getCombatPhase() < 2))
                    continue;
                if (hive() && candidate == 5 && (owner.getCombatPhase() < 1 || !hasBrood())) continue;
                available[count++] = candidate;
            }
            if (count == 0) return;
            id = available[owner.getRNG()
                .nextInt(count)];
            skillCooldown[id] = skillDelay(id);
            cooldown = desire();
            owner.skill(id, 0);
            cast(id);
            serial = (serial + 1) % 1000000;
            x = target.posX;
            y = target.posY;
            z = target.posZ;
            ox = owner.posX;
            oz = owner.posZ;
            broodCount = hive() && id == 1 ? 6 + owner.getRNG()
                .nextInt(7) : 0;
            broodIndex = 0;
            owner.getNavigator()
                .clearPathEntity();
            CombatEffects.send(owner, serial * 32, 0, windup(id), palette(), geometry(id), id, 0);
            sound(
                hive() ? id == 1 || id == 5 ? "summon" : "launch" : id == 2 ? "charge" : id == 3 ? "stomp" : "attack");
            return;
        }
        int ticks = owner.getSkillTicks() + 1;
        owner.skill(id, ticks);
        int delay = windup(id);
        if (!hive() && id == 2 && ticks >= delay && ticks < delay + 24) {
            double dx = x - ox, dz = z - oz, len = Math.max(.1, Math.sqrt(dx * dx + dz * dz));
            double beforeX = owner.posX, beforeZ = owner.posZ;
            if (loaded(owner.posX + dx / len * .65, owner.posZ + dz / len * .65))
                owner.moveEntity(dx / len * .65, 0, dz / len * .65);
            hit(new CombatGeometry(CombatGeometry.CIRCLE, owner.posX, owner.posY, owner.posZ, 0, 0, 3, 0), 10, 1.1);
            if (Math.abs(owner.posX - beforeX) + Math.abs(owner.posZ - beforeZ) < .05) owner.skill(id, delay + 24);
        }
        if (ticks == delay) {
            CombatEffects.send(owner, serial * 32, 1, 20, palette(), geometry(id), id, 0);
            if (hive()) {
                if (id == 5) bless();
            } else {
                if (id != 2) hit(geometry(id), id == 5 ? 16 : id == 4 ? 14 : 10, id == 4 ? 1.3 : .7);
                sound(id == 3 ? "stomp" : "slam");
            }
        }
        if (!hive() && id == 3 && (ticks == delay + 16 || ticks == delay + 32)) {
            hit(geometry(id), 8, .6);
            sound("stomp");
            CombatEffects
                .send(owner, serial * 32 + (ticks == delay + 16 ? 1 : 2), 1, 12, palette(), geometry(id), id, 0);
        }
        if (hive() && id == 1 && ticks >= delay && (ticks - delay) % 2 == 0 && broodIndex < broodCount) summonNext();
        if (hive() && id >= 2 && id <= 4 && ticks >= delay && (ticks - delay) % 12 == 0) {
            int step = (ticks - delay) / 12;
            if (id == 2 && step < 3) square(target, step);
            else if (id == 3 && step < 4) ring(step);
            else if (id == 4 && step < 4) mortar(step);
        }
        if (ticks >= delay + 44) {
            CombatEffects.send(owner, serial * 32, 2, 0, palette(), geometry(id), id, 0);
            owner.skill(0, 0);
            broodCount = broodIndex = 0;
        }
    }

    private int windup(int id) {
        return !hive() && id == 2 ? 30 : 40;
    }

    private CombatGeometry geometry(int id) {
        if (!hive() && id == 2) return new CombatGeometry(CombatGeometry.LINE, ox, y, oz, x, z, 3, 0);
        if (!hive() && id == 4) return new CombatGeometry(CombatGeometry.CONE, ox, y, oz, x, z, 14, 0);
        return new CombatGeometry(
            CombatGeometry.CIRCLE,
            hive() && id == 2 ? x : ox,
            y,
            hive() && id == 2 ? z : oz,
            0,
            0,
            hive() ? 4 : id == 5 ? 14 : id == 3 ? 7 : 10,
            0);
    }

    private void push(EntityPlayer p, double knock, double up) {
        double dx = p.posX - owner.posX, dz = p.posZ - owner.posZ, len = Math.max(.1, Math.sqrt(dx * dx + dz * dz));
        p.addVelocity(dx / len * knock, up, dz / len * knock);
        p.velocityChanged = true;
    }

    private void hit(CombatGeometry geometry, float damage, double knock) {
        for (Object object : owner.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) object;
            if (!valid(p) || !geometry.contains(p.posX, p.posY, p.posZ) || !owner.canEntityBeSeen(p)) continue;
            if (p.attackEntityFrom(DamageSource.causeMobDamage(owner), damage)) push(p, knock, .25);
        }
    }

    private boolean loaded(double px, double pz) {
        return owner.worldObj.getChunkProvider()
            .chunkExists(((int) Math.floor(px)) >> 4, ((int) Math.floor(pz)) >> 4);
    }

    private void spore(double px, double py, double pz, int key) {
        if (!loaded(px, pz)) return;
        CombatEffects.send(
            owner,
            serial * 128 + key,
            0,
            12,
            3,
            new CombatGeometry(CombatGeometry.CIRCLE, px, py, pz, 0, 0, 2, 0),
            owner.getSkillId(),
            0);
        EchoCombatProjectile.fire(
            owner,
            owner.posX,
            owner.posY + Math.min(5, owner.height * .4),
            owner.posZ,
            px,
            py + .2,
            pz,
            .8,
            6,
            2,
            0,
            3);
    }

    private void square(EntityPlayer target, int step) {
        x = target.posX;
        y = target.posY;
        z = target.posZ;
        for (int corner = 0; corner < 4; corner++)
            spore(x + (corner % 2 == 0 ? -1.5 : 1.5), y, z + (corner < 2 ? -1.5 : 1.5), step * 16 + corner);
        sound("launch");
    }

    private void ring(int step) {
        int count = 8 + step * 4;
        double radius = 4 + step * 4;
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            spore(owner.posX + Math.cos(a) * radius, owner.posY, owner.posZ + Math.sin(a) * radius, step * 24 + i);
        }
        sound("launch");
    }

    private void mortar(int step) {
        // Four segments across four longitudinal axes; alternating offset covers the returning sweep.
        for (int side = 0; side < 4; side++) {
            double a = Math.toRadians(owner.rotationYaw) + side * Math.PI / 2;
            double r = 5 + step * 5, offset = (step % 2 == 0 ? -1.5 : 1.5);
            spore(
                owner.posX + Math.cos(a) * r - Math.sin(a) * offset,
                owner.posY,
                owner.posZ + Math.sin(a) * r + Math.cos(a) * offset,
                step * 4 + side);
        }
        sound("launch");
    }

    private void bless() {
        for (Object object : owner.worldObj.loadedEntityList) {
            if (!(object instanceof EntityOldEcho)) continue;
            EntityOldEcho child = (EntityOldEcho) object;
            if (!child.isEntityAlive() || !child.isSummonedBy(owner)) continue;
            child.addPotionEffect(new PotionEffect(Potion.resistance.id, 2400, 2));
            child.addPotionEffect(new PotionEffect(Potion.regeneration.id, 2400, 2));
            child.addPotionEffect(new PotionEffect(Potion.damageBoost.id, 2400, 2));
        }
    }

    private void summonNext() {
        int index = broodIndex++;
        int live = 0;
        for (Object object : owner.worldObj.loadedEntityList)
            if (object instanceof EntityOldEcho && ((EntityOldEcho) object).isSummonedBy(owner)
                && ((EntityOldEcho) object).isEntityAlive()) live++;
        // At the fastest phase cadence at most ten 12-member waves coexist in the 1200-tick TTL.
        if (live >= 120) return;
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = 2 * Math.PI * index / broodCount + attempt * .21;
            double sx = owner.posX + Math.cos(a) * (8 + attempt), sz = owner.posZ + Math.sin(a) * (8 + attempt);
            if (!owner.worldObj.getChunkProvider()
                .chunkExists(((int) Math.floor(sx)) >> 4, ((int) Math.floor(sz)) >> 4)) continue;
            double sy = CombatGeometry.groundY(owner.worldObj, sx, owner.posY + 3, sz);
            int bx = (int) Math.floor(sx), by = (int) Math.floor(sy - .01), bz = (int) Math.floor(sz);
            if (by < 0 || by > 254
                || owner.worldObj.getBlock(bx, by, bz)
                    .getCollisionBoundingBoxFromPool(owner.worldObj, bx, by, bz) == null)
                continue;
            EntityOldEcho child = new EntityOldEcho(owner.worldObj);
            child.initializeEcho(index % 2 == 0 ? EchoKind.DR04 : EchoKind.DR14, "", sx, sy, sz, false);
            child.markSummoned(owner, 1200);
            int[] buffs = { Potion.regeneration.id, Potion.resistance.id, Potion.damageBoost.id,
                Potion.field_76434_w.id };
            for (int i = buffs.length - 1; i > 0; i--) {
                int j = owner.getRNG()
                    .nextInt(i + 1), t = buffs[i];
                buffs[i] = buffs[j];
                buffs[j] = t;
            }
            int count = 1 + owner.getRNG()
                .nextInt(4);
            for (int i = 0; i < count; i++) child.addPotionEffect(new PotionEffect(buffs[i], 1200, 1));
            if (!owner.worldObj.getCollidingBoundingBoxes(child, child.boundingBox)
                .isEmpty() || !owner.worldObj.checkNoEntityCollision(child.boundingBox)
                || owner.worldObj.isAnyLiquid(child.boundingBox)) continue;
            if (owner.worldObj.spawnEntityInWorld(child)) {
                CombatEffects.send(
                    owner,
                    serial * 32 + 8 + index,
                    1,
                    16,
                    palette(),
                    new CombatGeometry(CombatGeometry.CIRCLE, sx, sy, sz, 0, 0, 1.5, 0),
                    owner.getSkillId(),
                    0);
                sound("summon");
                return;
            }
        }
    }

    void write(NBTTagCompound n) {
        n.setInteger("industrialCombatCooldown", cooldown);
        n.setInteger("industrialCombatPhase", lastPhase);
        n.setInteger("industrialCombatSerial", serial);
        for (int i = 1; i <= 5; i++) n.setInteger("industrialSkillCooldown" + i, skillCooldown[i]);
        n.setInteger("industrialBonesCooldown", bonesCooldown);
        n.setInteger("industrialBonesTicks", bonesTicks);
        n.setInteger("industrialRetaliationCooldown", retaliationCooldown);
        n.setInteger("industrialPhaseBurstRemaining", phaseBurstRemaining);
        n.setInteger("industrialPhaseBurstInterval", phaseBurstInterval);
    }

    void read(NBTTagCompound n) {
        cooldown = Math.max(0, n.getInteger("industrialCombatCooldown"));
        lastPhase = n.hasKey("industrialCombatPhase") ? n.getInteger("industrialCombatPhase") : owner.getCombatPhase();
        serial = Math.max(0, Math.min(999999, n.getInteger("industrialCombatSerial")));
        for (int i = 1; i <= 5; i++) skillCooldown[i] = Math.max(0, n.getInteger("industrialSkillCooldown" + i));
        bonesCooldown = Math.max(0, n.getInteger("industrialBonesCooldown"));
        bonesTicks = Math.max(0, n.getInteger("industrialBonesTicks"));
        retaliationCooldown = Math.max(0, n.getInteger("industrialRetaliationCooldown"));
        phaseBurstRemaining = Math.max(0, Math.min(8, n.getInteger("industrialPhaseBurstRemaining")));
        phaseBurstInterval = Math.max(0, n.getInteger("industrialPhaseBurstInterval"));
        owner.getDataWatcher()
            .updateObject(16, bonesTicks);
        owner.skill(0, 0);
        broodCount = broodIndex = 0;
    }
}
