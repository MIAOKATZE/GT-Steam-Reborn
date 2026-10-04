package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/** Industrial bosses have explicit stage-dependent physical attacks and a bounded brood. */
final class EchoIndustrialCombat {

    private final EntityOldEcho owner;
    private int cooldown = 80, serial, lastPhase = -1;
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

    void enteredPhase() {
        lastPhase = owner.getCombatPhase();
    }

    void cancel() {
        if (owner.getSkillId() != 0) CombatEffects
            .send(owner, serial * 32, 2, 0, palette(), geometry(owner.getSkillId()), owner.getSkillId(), 0);
        owner.skill(0, 0);
        broodCount = broodIndex = 0;
        cooldown = Math.max(40, cooldown);
    }

    void tick(EntityPlayer target) {
        int phase = owner.getCombatPhase();
        if (lastPhase != phase) {
            lastPhase = phase;
            sound("phase");
        }
        if (cooldown > 0) cooldown--;
        int id = owner.getSkillId();
        if (id == 0) {
            if (cooldown > 0 || !owner.canEntityBeSeen(target)) {
                if (!hive() && owner.ticksExisted % 10 == 0) owner.getNavigator()
                    .tryMoveToEntityLiving(target, 1);
                return;
            }
            id = 1 + owner.getRNG()
                .nextInt(3 + phase);
            owner.skill(id, 0);
            owner.announceSkill(id);
            serial = (serial + 1) % 1000000;
            x = target.posX;
            y = target.posY;
            z = target.posZ;
            ox = owner.posX;
            oz = owner.posZ;
            broodCount = hive() && (id == 1 || id == 4) ? 6 + owner.getRNG()
                .nextInt(7) : 0;
            broodIndex = 0;
            owner.getNavigator()
                .clearPathEntity();
            if (hive() && id != 1 && id != 4) fanEffects(id, 0, windup(id), 0);
            else CombatEffects.send(owner, serial * 32, 0, windup(id), palette(), geometry(id), id, 0);
            sound(
                hive() ? id == 1 || id == 4 ? "summon" : "launch" : id == 2 ? "charge" : id == 3 ? "stomp" : "attack");
            return;
        }
        int ticks = owner.getSkillTicks() + 1;
        owner.skill(id, ticks);
        int delay = windup(id);
        if (!hive() && id == 2 && ticks >= delay && ticks < delay + 24) {
            double dx = x - ox, dz = z - oz, len = Math.max(.1, Math.sqrt(dx * dx + dz * dz));
            double beforeX = owner.posX, beforeZ = owner.posZ;
            owner.moveEntity(dx / len * .65, 0, dz / len * .65);
            hit(new CombatGeometry(CombatGeometry.CIRCLE, owner.posX, owner.posY, owner.posZ, 0, 0, 3, 0), 10, 1.1);
            if (Math.abs(owner.posX - beforeX) + Math.abs(owner.posZ - beforeZ) < .05) owner.skill(id, delay + 24);
        }
        if (ticks == delay) {
            if (!hive() || id == 1 || id == 4)
                CombatEffects.send(owner, serial * 32, 1, 20, palette(), geometry(id), id, 0);
            if (hive()) {
                if (id == 4) pulse(6, 3);
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
        if (hive() && (id == 1 || id == 4) && ticks >= delay && (ticks - delay) % 2 == 0 && broodIndex < broodCount)
            summonNext();
        if (hive() && id != 1 && id != 4 && ticks >= delay && ticks <= delay + 36 && (ticks - delay) % 12 == 0) {
            x = target.posX;
            y = target.posY;
            z = target.posZ;
            ox = owner.posX;
            oz = owner.posZ;
            int step = (ticks - delay) / 12;
            fanEffects(id, 1, 12, step);
            volley(id == 5 ? 6 : id == 3 ? 4 : 2, id == 5 ? 9 : 6, id == 5 ? 3 : 2);
        }
        if (ticks >= delay + 44) {
            CombatEffects.send(owner, serial * 32, 2, 0, palette(), geometry(id), id, 0);
            owner.skill(0, 0);
            broodCount = broodIndex = 0;
            cooldown = (hive() ? 100 : 120) - phase * 25;
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
            hive() && id != 1 && id != 4 ? x : ox,
            y,
            hive() && id != 1 && id != 4 ? z : oz,
            0,
            0,
            hive() ? 4 : id == 5 ? 14 : id == 3 ? 7 : 10,
            0);
    }

    private void hit(CombatGeometry geometry, float damage, double knock) {
        for (Object object : owner.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) object;
            if (!p.isEntityAlive() || p.capabilities.isCreativeMode
                || !geometry.contains(p.posX, p.posY, p.posZ)
                || !owner.canEntityBeSeen(p)) continue;
            if (p.attackEntityFrom(DamageSource.causeMobDamage(owner), damage)) {
                double dx = p.posX - owner.posX, dz = p.posZ - owner.posZ,
                    len = Math.max(.1, Math.sqrt(dx * dx + dz * dz));
                p.addVelocity(dx / len * knock, .25, dz / len * knock);
                p.velocityChanged = true;
            }
        }
    }

    private void pulse(float damage, double radius) {
        hit(new CombatGeometry(CombatGeometry.CIRCLE, owner.posX, owner.posY, owner.posZ, 0, 0, radius, 0), damage, .4);
    }

    private void fanEffects(int id, int stage, int duration, int step) {
        double dx = x - ox, dz = z - oz;
        if (dx * dx + dz * dz < .01) {
            dx = -Math.sin(Math.toRadians(owner.rotationYaw));
            dz = Math.cos(Math.toRadians(owner.rotationYaw));
        }
        for (int side = 0; side < 2; side++) {
            double sign = side == 0 ? 1 : -1;
            CombatEffects.send(
                owner,
                serial * 32 + step * 2 + side,
                stage,
                duration,
                palette(),
                new CombatGeometry(CombatGeometry.CONE, ox, y, oz, ox + dx * sign, oz + dz * sign, 24, 0),
                id,
                side);
        }
    }

    private void volley(int count, float damage, double radius) {
        sound("launch");
        double dx = x - owner.posX, dz = z - owner.posZ, distance = Math.max(8, Math.sqrt(dx * dx + dz * dz));
        double heading = dx * dx + dz * dz < .01 ? Math.toRadians(owner.rotationYaw + 90) : Math.atan2(dz, dx);
        for (int side = 0; side < 2; side++) for (int i = 0; i < count; i++) {
            double angle = heading + side * Math.PI + (i / (double) (count - 1) - .5) * Math.PI / 2;
            EchoCombatProjectile.fire(
                owner,
                owner.posX,
                owner.posY + Math.min(5, owner.height * .4),
                owner.posZ,
                owner.posX + Math.cos(angle) * distance,
                y + 1,
                owner.posZ + Math.sin(angle) * distance,
                .8,
                damage,
                radius,
                .012,
                3);
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
            child.addPotionEffect(new PotionEffect(Potion.damageBoost.id, 1200, owner.getCombatPhase()));
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
    }

    void read(NBTTagCompound n) {
        cooldown = Math.max(40, n.getInteger("industrialCombatCooldown"));
        lastPhase = n.getInteger("industrialCombatPhase");
        serial = Math.max(0, Math.min(999999, n.getInteger("industrialCombatSerial")));
        owner.skill(0, 0);
        broodCount = broodIndex = 0;
    }
}
