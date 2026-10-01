package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.CombatEffects;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.CombatGeometry;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;

/** A fixed, front-facing throne encounter. All damage and progression are server authoritative. */
public class EntitySilentKing extends EntityEncounterBase {

    public static final int DORMANT = 0, AWAKENING = 1, ACTIVE = 2, RECOVERING = 3, DEFEATED = 4;
    public static final int ALERT_RADIUS = 55, AWAKENING_TICKS = 208;
    public static final float MAX_HEALTH = 3000F;
    public static final int SKILL_NONE = 0, SKILL_ECHO = 1, SKILL_PULSE = 2, SKILL_CROWN = 3, SKILL_SHOCK = 4,
        SKILL_SWEEP = 5, SKILL_CROWN_FALL = 6, SKILL_DOUBLE_PULSE = 7;
    private final Set<UUID> condemned = new HashSet<>(), participants = new HashSet<>();
    private final int[] cooldowns = new int[8];
    private int combatTicks, nextSkill;
    private double echoX, echoY, echoZ;
    private boolean engaged, deathRecorded;

    public EntitySilentKing(World w) {
        super(w);
        setSize(6F, 12F);
        setCustomNameTag("旧日王座");
    }

    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(25, 0);
        dataWatcher.addObject(26, 0);
    }

    public int getSkillId() {
        return dataWatcher.getWatchableObjectInt(25);
    }

    public int getSkillTicks() {
        return dataWatcher.getWatchableObjectInt(26);
    }

    private void skill(int id, int ticks) {
        dataWatcher.updateObject(25, id);
        dataWatcher.updateObject(26, ticks);
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(MAX_HEALTH);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
    }

    public boolean isEntityInvulnerable() {
        return getEncounterState() != ACTIVE || super.isEntityInvulnerable();
    }

    protected void onDeathUpdate() {
        if (++deathTime >= 120) setDead();
    }

    public void moveEntity(double x, double y, double z) {}

    public boolean canBePushed() {
        return false;
    }

    private boolean valid(EntityPlayer p) {
        return p.isEntityAlive() && p.worldObj == worldObj && !p.capabilities.isCreativeMode;
    }

    /** Minecraft yaw 90 points west. Position, never the attacker's look vector, defines the half-plane. */
    private boolean front(Entity source) {
        double yaw = getHomeYaw() * Math.PI / 180;
        return -Math.sin(yaw) * (source.posX - posX) + Math.cos(yaw) * (source.posZ - posZ) >= -1.0e-7;
    }

    public boolean attackEntityFrom(DamageSource s, float a) {
        Entity source = s.getEntity(); // Indirect sources return their shooter, not the projectile's impact position.
        if (worldObj.isRemote || getEncounterState() != ACTIVE
            || !(source instanceof EntityPlayer)
            || !valid((EntityPlayer) source)
            || !canEntityBeSeen(source)) return false;
        EntityPlayer p = (EntityPlayer) source;
        engaged = true;
        if (!front(p)) {
            condemn(p);
            return false;
        }
        // Register before super: a killing hit invokes onDeath synchronously.
        boolean wasParticipant = participants.contains(p.getUniqueID());
        participants.add(p.getUniqueID());
        boolean accepted = super.attackEntityFrom(s, a);
        if (!accepted && !wasParticipant) participants.remove(p.getUniqueID());
        return accepted;
    }

    private List<EntityPlayer> players() {
        List<EntityPlayer> out = new ArrayList<>();
        for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(55, 55, 55))) {
            EntityPlayer p = (EntityPlayer) o;
            if (valid(p) && getDistanceSqToEntity(p) <= ALERT_RADIUS * ALERT_RADIUS) out.add(p);
        }
        return out;
    }

    public boolean isCondemned(EntityPlayer p) {
        return condemned.contains(p.getUniqueID());
    }

    private void condemn(EntityPlayer player) {
        if (!condemned.add(player.getUniqueID())) return;
        ChatComponentTranslation warning = new ChatComponentTranslation("lore.king.condemned");
        warning.getChatStyle()
            .setColor(EnumChatFormatting.DARK_RED);
        player.addChatMessage(warning);
        worldObj.playSoundEffect(posX, posY + 4, posZ, "note.bassattack", 1F, .5F);
    }

    public void onLivingUpdate() {
        super.onLivingUpdate();
        motionX = motionY = motionZ = 0;
        faceHome();
        if (worldObj.isRemote) return;
        setPosition(anchorX, anchorY, anchorZ);
        if (getEncounterId().isEmpty()) return;
        for (int i = 1; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(worldObj);
        if (d.kingDead(getEncounterId())) {
            if (getEncounterState() != DEFEATED) setDead();
            return;
        }
        int s = getEncounterState();
        List<EntityPlayer> ps = players();
        if (s == DORMANT) {
            boolean close = false;
            for (EntityPlayer p : ps) if (getDistanceSqToEntity(p) <= 24 * 24) close = true;
            if (d.allGuardsDead(getEncounterId()) && close) {
                state(AWAKENING);
                setHealth(1);
                setCustomNameTag("(旧日虚影)缄王");
            }
            return;
        }
        if (s == AWAKENING) {
            int t = getVisualPhaseTicks() + 1;
            phase(t);
            setHealth(Math.max(1, MAX_HEALTH * t / AWAKENING_TICKS));
            if (t >= AWAKENING_TICKS) {
                setHealth(MAX_HEALTH);
                state(ACTIVE);
            }
            return;
        }
        if (ps.isEmpty()) {
            if (s != RECOVERING) {
                state(RECOVERING);
                if (getSkillId() != 0) stopEffects();
                skill(SKILL_NONE, 0);
                nextSkill = 30;
            }
            setHealth(Math.min(MAX_HEALTH, getHealth() + 20));
            return;
        }
        if (s == RECOVERING) state(ACTIVE);
        if (getEncounterState() != ACTIVE) return;
        engaged = true;
        for (EntityPlayer p : ps) if (engaged && !front(p)) condemn(p);
        phase(getVisualPhaseTicks() + 1);
        combatTicks++;
        if (getSkillId() != SKILL_NONE) {
            advanceSkill(ps);
        } else if (--nextSkill <= 0) {
            chooseSkill(ps);
        }
    }

    private EntityPlayer priorityPlayer(List<EntityPlayer> ps) {
        for (EntityPlayer p : ps) if (isCondemned(p)) return p;
        return ps.get(rand.nextInt(ps.size()));
    }

    private void chooseSkill(List<EntityPlayer> ps) {
        int id = SKILL_NONE;
        if (cooldowns[SKILL_SHOCK] == 0) {
            for (EntityPlayer p : ps) if (getDistanceSqToEntity(p) <= 8 * 8) id = SKILL_SHOCK;
        }
        if (id == SKILL_NONE) {
            int phase = getCombatPhase();
            if (phase > 0 && rand.nextInt(3) == 0) {
                int candidate = phase == 2 ? SKILL_SWEEP + rand.nextInt(3)
                    : rand.nextBoolean() ? SKILL_SWEEP : SKILL_CROWN_FALL;
                if (cooldowns[candidate] == 0) id = candidate;
            }
        }
        if (id == SKILL_NONE) {
            boolean punitive = false;
            for (EntityPlayer p : ps) if (isCondemned(p)) punitive = true;
            int[] weights = { 0, punitive ? 6 : 4, 3, punitive ? 6 : 2, 0 };
            int sum = 0;
            for (int i = 1; i <= 3; i++) if (cooldowns[i] == 0) sum += weights[i];
            if (sum == 0) {
                nextSkill = 10;
                return;
            }
            int pick = rand.nextInt(sum);
            for (int i = 1; i <= 3; i++) if (cooldowns[i] == 0) {
                pick -= weights[i];
                if (pick < 0) {
                    id = i;
                    break;
                }
            }
        }
        EntityPlayer p = priorityPlayer(ps);
        echoX = p.posX;
        echoY = CombatGeometry.groundY(worldObj, p.posX, p.posY, p.posZ);
        echoZ = p.posZ;
        skill(id, 0);
        if (!attackSpaceLoaded()) {
            skill(SKILL_NONE, 0);
            nextSkill = 40;
            return;
        }
        attackSerial = attackSerial >= 60000000 ? 1 : attackSerial + 1;
        for (int j = 0; j < steps(); j++)
            CombatEffects.send(this, attackSerial * 32 + j, 0, warning() + j * 20, 2, geometry(j));
        cooldowns[id] = id == SKILL_SHOCK ? 1200
            : id == SKILL_CROWN ? 400 + rand.nextInt(401) : id == SKILL_ECHO ? 80 : 120;
    }

    private void hurt(EntityPlayer p, float amount, double knockback) {
        float damage = isCondemned(p) ? amount * 2.2F : amount;
        if (!valid(p) || !canEntityBeSeen(p) || !p.attackEntityFrom(DamageSource.causeMobDamage(this), damage)) return;
        participants.add(p.getUniqueID());
        if (knockback > 0) {
            double dx = p.posX - posX, dz = p.posZ - posZ, length = Math.sqrt(dx * dx + dz * dz);
            if (length < .001) {
                dx = -1;
                dz = 0;
                length = 1;
            }
            p.addVelocity(dx / length * knockback, .35, dz / length * knockback);
            p.velocityChanged = true;
        }
    }

    public int getCombatPhase() {
        return getHealth() <= MAX_HEALTH * .3F ? 2 : getHealth() <= MAX_HEALTH * .6F ? 1 : 0;
    }

    private int attackSerial;

    private boolean attackSpaceLoaded() {
        for (int cx = ((int) anchorX - 28) >> 4; cx <= ((int) anchorX + 28) >> 4; cx++)
            for (int cz = ((int) anchorZ - 28) >> 4; cz <= ((int) anchorZ + 28) >> 4; cz++)
                if (!worldObj.getChunkProvider()
                    .chunkExists(cx, cz)) return false;
        return true;
    }

    private int steps() {
        return getSkillId() == SKILL_CROWN_FALL ? 3 : getSkillId() == SKILL_DOUBLE_PULSE ? 2 : 1;
    }

    private int warning() {
        return getSkillId() == SKILL_ECHO ? 40
            : getSkillId() == SKILL_CROWN || getSkillId() == SKILL_CROWN_FALL ? 60 : 40;
    }

    private CombatGeometry geometry(int step) {
        int id = getSkillId();
        if (id == SKILL_ECHO) return new CombatGeometry(CombatGeometry.POINT, echoX, echoY, echoZ, 0, 0, 4, 0);
        if (id == SKILL_CROWN || id == SKILL_CROWN_FALL) return new CombatGeometry(
            CombatGeometry.POINT,
            echoX + (id == SKILL_CROWN_FALL ? (step - 1) * 6 : 0),
            echoY,
            echoZ,
            0,
            0,
            7,
            0);
        if (id == SKILL_SWEEP) {
            double a = Math.toRadians(getHomeYaw());
            return new CombatGeometry(
                CombatGeometry.CONE,
                anchorX,
                anchorY,
                anchorZ,
                anchorX - Math.sin(a) * 24,
                anchorZ + Math.cos(a) * 24,
                24,
                0);
        }
        if (id == SKILL_DOUBLE_PULSE) return new CombatGeometry(
            CombatGeometry.RING,
            anchorX,
            anchorY,
            anchorZ,
            0,
            0,
            step == 0 ? 14 : 24,
            step == 0 ? 7 : 17);
        return new CombatGeometry(
            CombatGeometry.CIRCLE,
            anchorX,
            anchorY,
            anchorZ,
            0,
            0,
            id == SKILL_SHOCK ? 10 : 24,
            0);
    }

    private void stopEffects() {
        for (int j = 0; j < steps(); j++) CombatEffects.send(this, attackSerial * 32 + j, 2, 0, 2, geometry(j));
    }

    private void advanceSkill(List<EntityPlayer> ps) {
        if (!attackSpaceLoaded()) {
            stopEffects();
            skill(SKILL_NONE, 0);
            nextSkill = 40;
            return;
        }
        int id = getSkillId(), t = getSkillTicks() + 1;
        skill(id, t);
        int windup = warning();
        for (int j = 0; j < steps(); j++) if (t == windup + j * 20) {
            CombatGeometry g = geometry(j);
            CombatEffects.send(this, attackSerial * 32 + j, 1, 8, 2, g);
            for (EntityPlayer p : ps) if (g.contains(p.posX, p.posY, p.posZ)) hurt(
                p,
                id == SKILL_ECHO ? 10 : id == SKILL_CROWN || id == SKILL_CROWN_FALL ? 24 : id == SKILL_SHOCK ? 6 : 8,
                id == SKILL_ECHO ? 0 : id == SKILL_SHOCK ? 1.5 : .6);
            CombatEffects.send(this, attackSerial * 32 + j, 2, 18, 2, g);
            worldObj.playSoundEffect(posX, posY, posZ, "random.explode", 1F, .6F);
        }
        if (t >= windup + (steps() - 1) * 20 + 18) {
            skill(SKILL_NONE, 0);
            nextSkill = 25 - getCombatPhase() * 5;
        }
    }

    public void onDeath(DamageSource s) {
        if (dead || deathRecorded) return;
        super.onDeath(s);
        if (!dead && !worldObj.isRemote && getHealth() <= 0) {
            setHealth(1);
            deathTime = 0;
            return;
        }
        if (dead && !worldObj.isRemote && !deathRecorded) {
            deathRecorded = true;
            if (getSkillId() != 0) stopEffects();
            ForgottenLakeEncounterData.get(worldObj)
                .kingDied(getEncounterId());
            state(DEFEATED);
            skill(SKILL_NONE, 0);
            List<EntityPlayer> credited = new ArrayList<>();
            for (Object o : worldObj.playerEntities) {
                EntityPlayer p = (EntityPlayer) o;
                if (valid(p) && participants.contains(p.getUniqueID())) credited.add(p);
            }
            LoreRegistry.kingDefeated(this, s, credited);
        }
    }

    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {
        entityDropItem(LoreRegistry.kingCrown(), 0);
    }

    private void writeIds(NBTTagCompound n, String key, Set<UUID> ids) {
        NBTTagList l = new NBTTagList();
        for (UUID id : ids) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("uuid", id.toString());
            l.appendTag(entry);
        }
        n.setTag(key, l);
    }

    private void readIds(NBTTagCompound n, String key, Set<UUID> ids) {
        ids.clear();
        NBTTagList l = n.getTagList(key, 10);
        for (int i = 0; i < l.tagCount(); i++) {
            try {
                ids.add(
                    UUID.fromString(
                        l.getCompoundTagAt(i)
                            .getString("uuid")));
            } catch (IllegalArgumentException ignored) { /*
                                                          * Ignore invalid legacy entry without clearing valid
                                                          * participants.
                                                          */ }
        }
    }

    public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        n.setInteger("healthSchema", 2);
        n.setInteger("combat", combatTicks);
        n.setInteger("attackSerial", attackSerial);
        n.setInteger("skillId", getSkillId());
        n.setInteger("skillTicks", getSkillTicks());
        n.setInteger("nextSkill", nextSkill);
        for (int i = 1; i < cooldowns.length; i++) n.setInteger("cooldown" + i, cooldowns[i]);
        n.setDouble("ex", echoX);
        n.setDouble("ey", echoY);
        n.setDouble("ez", echoZ);
        n.setBoolean("engaged", engaged);
        n.setBoolean("deathRecorded", deathRecorded);
        writeIds(n, "condemned", condemned);
        writeIds(n, "participants", participants);
    }

    public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        if (getCustomNameTag().equals("（旧日虚影）缄王")) setCustomNameTag("(旧日虚影)缄王");
        if (!n.hasKey("healthSchema")) {
            float oldHealth = getHealth();
            getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(MAX_HEALTH);
            setHealth(Math.min(MAX_HEALTH, oldHealth * 3));
            if (getEncounterState() == AWAKENING) phase(getVisualPhaseTicks() * 2);
        }
        combatTicks = n.getInteger("combat");
        attackSerial = Math.max(0, n.getInteger("attackSerial"));
        skill(n.getInteger("skillId"), n.getInteger("skillTicks"));
        nextSkill = n.getInteger("nextSkill");
        for (int i = 1; i < cooldowns.length; i++) cooldowns[i] = Math.max(0, n.getInteger("cooldown" + i));
        echoX = n.getDouble("ex");
        echoY = n.getDouble("ey");
        echoZ = n.getDouble("ez");
        engaged = n.getBoolean("engaged");
        deathRecorded = n.getBoolean("deathRecorded");
        readIds(n, "condemned", condemned);
        readIds(n, "participants", participants);
    }
}
