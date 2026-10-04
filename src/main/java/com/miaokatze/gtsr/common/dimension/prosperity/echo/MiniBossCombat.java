package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;

/** Ten mechanical custodians: locked footprints, finite telegraphs and no terrain writes. */
final class MiniBossCombat {

    private final EntityOldEcho owner;
    private int cooldown = 60, serial;
    private double x, y, z, ox, oz;

    MiniBossCombat(EntityOldEcho owner) {
        this.owner = owner;
    }

    static boolean supports(EchoKind kind) {
        return MiniBossDefinitions.index(kind.code) >= 0;
    }

    static String clip(EchoKind kind) {
        return MiniBossDefinitions.CLIPS[MiniBossDefinitions.index(kind.code)];
    }

    void tick(EntityPlayer target) {
        int i = MiniBossDefinitions.index(owner.getKind().code);
        if (i < 0) return;
        if (cooldown > 0) cooldown--;
        if (owner.getSkillId() == 0) {
            if (cooldown > 0 || !owner.canEntityBeSeen(target) || owner.getDistanceSqToEntity(target) > 24 * 24) {
                if (owner.ticksExisted % 10 == 0) owner.getNavigator()
                    .tryMoveToEntityLiving(target, 1);
                return;
            }
            ox = owner.posX;
            oz = owner.posZ;
            x = target.posX;
            z = target.posZ;
            y = CombatGeometry.groundY(owner.worldObj, x, target.posY, z);
            serial = (serial + 1) % 1000000;
            owner.getNavigator()
                .clearPathEntity();
            owner.skill(1, 0);
            owner.announceSkill(1);
            for (int j = 0; j < steps(i); j++)
                CombatEffects.send(owner, serial * 32 + j, 0, delay(i, j), i % 4, geometry(i, j), 1, 0);
            sound("windup");
            return;
        }
        int ticks = owner.getSkillTicks() + 1;
        owner.skill(1, ticks);
        for (int j = 0; j < steps(i); j++) if (ticks == delay(i, j)) {
            CombatGeometry g = geometry(i, j);
            CombatEffects.send(owner, serial * 32 + j, 1, 12, i % 4, g, 1, 0);
            hit(i, g);
            sound("release");
        }
        if (ticks >= delay(i, steps(i) - 1) + 24) {
            clear(i);
            owner.skill(0, 0);
            cooldown = MiniBossDefinitions.COOLDOWNS[i];
        }
    }

    private void sound(String event) {
        owner.worldObj.playSoundEffect(
            owner.posX,
            owner.posY,
            owner.posZ,
            "gtsr:mini." + owner.getKind().code + "." + event,
            .85F,
            1F);
    }

    private static int steps(int i) {
        return i == 2 || i == 4 || i == 7 ? 2 : i == 6 ? 3 : 1;
    }

    private static int delay(int i, int step) {
        return MiniBossDefinitions.WINDUPS[i] + step * (i == 7 ? 22 : 12);
    }

    private CombatGeometry geometry(int i, int step) {
        double dx = x - ox, dz = z - oz, len = Math.max(.1, Math.sqrt(dx * dx + dz * dz));
        double ax = dx / len, az = dz / len;
        switch (i) {
            case 0:
                return new CombatGeometry(CombatGeometry.RING, ox, y, oz, 0, 0, 7, 3);
            case 1:
                return new CombatGeometry(CombatGeometry.CONE, ox, y, oz, x, z, 10, 0);
            case 2:
                return new CombatGeometry(
                    CombatGeometry.LINE,
                    x - (step == 0 ? 7 : 0),
                    y,
                    z - (step == 1 ? 7 : 0),
                    x + (step == 0 ? 7 : 0),
                    z + (step == 1 ? 7 : 0),
                    .8,
                    0);
            case 3:
                return new CombatGeometry(CombatGeometry.RING, x, y, z, 0, 0, 8, 4.5);
            case 4: {
                double offset = step == 0 ? -2.5 : 2.5;
                return new CombatGeometry(
                    CombatGeometry.LINE,
                    ox - az * offset,
                    y,
                    oz + ax * offset,
                    ox + ax * 16 - az * offset,
                    oz + az * 16 + ax * offset,
                    .7,
                    0);
            }
            case 5:
                return new CombatGeometry(CombatGeometry.CONE, ox, y, oz, x, z, 7, 0);
            case 6:
                return new CombatGeometry(
                    CombatGeometry.POINT,
                    x + (step - 1) * 3,
                    y,
                    z + (step == 1 ? 2 : -1),
                    0,
                    0,
                    2,
                    0);
            case 7:
                return new CombatGeometry(CombatGeometry.RING, ox, y, oz, 0, 0, step == 0 ? 5 : 9, step == 0 ? 0 : 5);
            case 8:
                return new CombatGeometry(
                    CombatGeometry.LINE,
                    x - az * 7,
                    y,
                    z + ax * 7,
                    x + az * 7,
                    z - ax * 7,
                    1.2,
                    0);
            default:
                return new CombatGeometry(CombatGeometry.RING, x, y, z, 0, 0, 6, 2);
        }
    }

    private void hit(int i, CombatGeometry g) {
        for (Object o : owner.worldObj.playerEntities) if (o instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.capabilities.isCreativeMode || p.isDead || !g.contains(p.posX, p.posY, p.posZ)) continue;
            if (p.attackEntityFrom(DamageSource.causeMobDamage(owner), MiniBossDefinitions.DAMAGE[i])) {
                double dx = p.posX - ox, dz = p.posZ - oz, len = Math.max(.1, Math.sqrt(dx * dx + dz * dz));
                double force = i == 1 ? -.7 : i == 3 ? 1.1 : i == 9 ? -.25 : .35;
                p.addVelocity(dx / len * force, .15, dz / len * force);
                p.velocityChanged = true;
            }
        }
    }

    private void clear(int i) {
        for (int j = 0; j < steps(i); j++)
            CombatEffects.send(owner, serial * 32 + j, 2, 0, i % 4, geometry(i, j), 1, 0);
    }

    void cancel() {
        int i = MiniBossDefinitions.index(owner.getKind().code);
        if (i >= 0 && owner.getSkillId() != 0) clear(i);
        owner.skill(0, 0);
        cooldown = Math.max(40, cooldown);
    }

    void write(NBTTagCompound n) {
        n.setInteger("MiniCooldown", cooldown);
        n.setInteger("MiniSerial", serial);
        n.setDouble("MiniX", x);
        n.setDouble("MiniY", y);
        n.setDouble("MiniZ", z);
        n.setDouble("MiniOX", ox);
        n.setDouble("MiniOZ", oz);
    }

    void read(NBTTagCompound n) {
        cooldown = Math.max(40, Math.min(240, n.getInteger("MiniCooldown")));
        serial = Math.max(0, n.getInteger("MiniSerial")) % 1000000;
        x = finite(n.getDouble("MiniX"));
        y = finite(n.getDouble("MiniY"));
        z = finite(n.getDouble("MiniZ"));
        ox = finite(n.getDouble("MiniOX"));
        oz = finite(n.getDouble("MiniOZ"));
        // A resumed cast cannot preserve a pre-reload warning on clients: restart safely after cooldown.
        owner.skill(0, 0);
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0;
    }
}
