package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.CombatEffects;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.CombatGeometry;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;

/** Server authoritative throne encounter. Summoned guards never alter the native platform ledger. */
public class EntitySilentKing extends EntityEncounterBase {

    public static final int DORMANT = 0, AWAKENING = 1, ACTIVE = 2, RECOVERING = 3, DEFEATED = 4;
    public static final int ALERT_RADIUS = 55, AWAKENING_TICKS = 200;
    public static final float MAX_HEALTH = 3000F;
    public static final int SKILL_NONE = 0, SKILL_ECHO = 1, SKILL_PULSE = 2, SKILL_CROWN = 3, SKILL_SHOCK = 4,
        SKILL_SWEEP = 5, SKILL_CROWN_FALL = 6, SKILL_DOUBLE_PULSE = 7;
    private final Set<UUID> condemned = new HashSet<>(), participants = new HashSet<>(),
        pendingGuards = new HashSet<>();
    private final Map<UUID, Integer> behindTicks = new HashMap<>();
    private final int[] cooldowns = new int[8];
    private int combatTicks, nextSkill = 160, attackSerial, lockTicks, absentTicks, guardWaveTicks;
    private boolean resolvingDamage, damageReachedFloor;
    private float resolvingFloor;
    private long lastPlayerPresence = -1;
    private int meteorTicks = -1, pullDirection, meteorSerial, initialGuardTarget;
    private double echoX, echoY, echoZ, meteorX, meteorY, meteorZ;
    private UUID meteorTarget;
    private boolean engaged, deathRecorded, initialGuardsSummoned;

    public EntitySilentKing(World w) {
        super(w);
        setSize(6F, 12F);
        setCustomNameTag("旧日王座");
    }

    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(17, 0);
        dataWatcher.addObject(18, 0);
        for (int i = 25; i <= 30; i++) dataWatcher.addObject(i, 0);
        dataWatcher.updateObject(27, 1);
        dataWatcher.updateObject(30, -1);
    }

    public int getSkillId() {
        return dataWatcher.getWatchableObjectInt(25);
    }

    public int getSkillTicks() {
        return dataWatcher.getWatchableObjectInt(26);
    }

    public int getCombatPhase() {
        return dataWatcher.getWatchableObjectInt(27);
    }

    public boolean isEnraged() {
        return dataWatcher.getWatchableObjectInt(28) != 0;
    }

    public int getLockTicks() {
        return dataWatcher.getWatchableObjectInt(29) & 65535;
    }

    public int getMeteorTicks() {
        return dataWatcher.getWatchableObjectInt(30);
    }

    public int getGravityDirection() {
        return dataWatcher.getWatchableObjectInt(29) >>> 16;
    }

    /** Independent modulo-64 counters retain simultaneous and instant casts in one watcher snapshot. */
    public int getSkillCastSerial(int id) {
        if (id < 1 || id > 5) return 0;
        return (dataWatcher.getWatchableObjectInt(17) >>> ((id - 1) * 6)) & 63;
    }

    public int getSkillAnnouncementSerial() {
        return dataWatcher.getWatchableObjectInt(18);
    }

    private void announceSkill(int id) {
        int shift = (id - 1) * 6, packed = dataWatcher.getWatchableObjectInt(17);
        int next = (getSkillCastSerial(id) + 1) & 63;
        dataWatcher.updateObject(17, (packed & ~(63 << shift)) | (next << shift));
        dataWatcher.updateObject(18, getSkillAnnouncementSerial() + 1);
    }

    private void skill(int id, int ticks) {
        dataWatcher.updateObject(25, id);
        dataWatcher.updateObject(26, ticks);
    }

    private void lock(int ticks) {
        lockTicks = ticks;
        dataWatcher.updateObject(29, ticks | (pullDirection << 16));
    }

    protected float getSoundPitch() {
        return isEnraged() ? .85F : .6F;
    }

    protected String getLivingSound() {
        return "gtsr:king.idle";
    }

    protected String getHurtSound() {
        return "gtsr:king.hurt";
    }

    protected String getDeathSound() {
        return "gtsr:king.death";
    }

    public int getDeathAnimationDuration() {
        return 240;
    }

    public int getDeathAnimationHoldTicks() {
        return 80;
    }

    public boolean isGoldenDeath() {
        return true;
    }

    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(MAX_HEALTH);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0);
    }

    public boolean isEntityInvulnerable() {
        return getEncounterState() != ACTIVE || lockTicks > 0
            || getCombatPhase() == 2 && livingSummons() > 0
            || super.isEntityInvulnerable();
    }

    public void moveEntity(double x, double y, double z) {}

    public boolean canBePushed() {
        return false;
    }

    private boolean valid(EntityPlayer p) {
        return p.isEntityAlive() && p.worldObj == worldObj && !p.capabilities.isCreativeMode;
    }

    private boolean front(Entity source) {
        double yaw = getHomeYaw() * Math.PI / 180;
        return -Math.sin(yaw) * (source.posX - posX) + Math.cos(yaw) * (source.posZ - posZ) >= 0;
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (worldObj.isRemote || isEntityInvulnerable()
            || !(attacker instanceof EntityPlayer)
            || !valid((EntityPlayer) attacker)
            || !canEntityBeSeen(attacker)) return false;
        EntityPlayer player = (EntityPlayer) attacker;
        boolean wasParticipant = participants.contains(player.getUniqueID());
        participants.add(player.getUniqueID());
        engaged = true;
        if (Float.isNaN(amount) || amount <= 0) {
            if (!wasParticipant) participants.remove(player.getUniqueID());
            return false;
        }
        boolean accepted = super.attackEntityFrom(source, Float.isInfinite(amount) ? Float.MAX_VALUE : amount);
        if (!accepted && !wasParticipant) participants.remove(player.getUniqueID());
        return accepted;
    }

    /** Enforce the phase floor at the final health mutation, after Forge hurt hooks, armor and absorption. */
    @Override
    protected void damageEntity(DamageSource source, float amount) {
        if (resolvingDamage || isEntityInvulnerable()) return;
        int phaseBefore = getCombatPhase();
        float threshold = phaseBefore == 1 ? 1500 : phaseBefore == 2 ? 1000 : phaseBefore == 3 ? 500 : 0;
        resolvingFloor = Math.min(threshold, getHealth());
        damageReachedFloor = false;
        resolvingDamage = true;
        try {
            super.damageEntity(source, amount);
        } finally {
            resolvingDamage = false;
            if (!Float.isFinite(getAbsorptionAmount())) setAbsorptionAmount(0);
        }
        if (damageReachedFloor && threshold > 0 && getCombatPhase() == phaseBefore && getHealth() > 0)
            enterPhase(phaseBefore + 1, players());
    }

    @Override
    public void setHealth(float health) {
        if (resolvingDamage) {
            float before = getHealth();
            if (Float.isNaN(health) || health == Float.NEGATIVE_INFINITY) health = resolvingFloor;
            else if (health == Float.POSITIVE_INFINITY) health = before;
            health = Math.max(resolvingFloor, health);
            if (resolvingFloor > 0 && health <= resolvingFloor && health < before) damageReachedFloor = true;
        }
        super.setHealth(health);
    }

    @Override
    protected float applyArmorCalculations(DamageSource source, float amount) {
        return super.applyArmorCalculations(source, Float.isInfinite(amount) ? Float.MAX_VALUE : amount);
    }

    @Override
    protected float applyPotionDamageCalculations(DamageSource source, float amount) {
        return super.applyPotionDamageCalculations(source, Float.isInfinite(amount) ? Float.MAX_VALUE : amount);
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

    private void sound(String event) {
        worldObj.playSoundEffect(posX, posY + 4, posZ, "gtsr:king." + event, 2F, isEnraged() ? .85F : .6F);
    }

    private void trackRear(List<EntityPlayer> ps) {
        for (EntityPlayer p : ps) if (!front(p)) {
            UUID id = p.getUniqueID();
            int t = behindTicks.containsKey(id) ? behindTicks.get(id) + 1 : 1;
            behindTicks.put(id, t);
            if (t == 1) {
                p.addChatMessage(
                    new ChatComponentTranslation("lore.king.condemned")
                        .setChatStyle(new net.minecraft.util.ChatStyle().setBold(true)));
                sound("warning");
            }
            if (t >= 200) {
                condemned.add(id);
                if (!isEnraged()) {
                    dataWatcher.updateObject(28, 1);
                    nextSkill = Math.min(nextSkill, attackInterval());
                }
            }
        }
    }

    public void onLivingUpdate() {
        super.onLivingUpdate();
        motionX = motionY = motionZ = 0;
        faceHome();
        updateEncounter();
    }

    /** Start ordinary awakening even when the mission administrator is in creative mode. */
    public boolean awakenForMission() {
        if (worldObj.isRemote || !isEntityAlive()
            || getEncounterState() == DEFEATED
            || !ForgottenLakeEncounterData.get(worldObj)
                .kingReady(getEncounterId()))
            return false;
        if (getEncounterState() != DORMANT && getEncounterState() != RECOVERING) return true;
        state(AWAKENING);
        setHealth(MAX_HEALTH);
        setCustomNameTag("(旧日虚影)缄王");
        initialGuardTarget = 20 + rand.nextInt(11);
        summonGuards(initialGuardTarget, false, true);
        initialGuardsSummoned = true;
        sound("chant");
        return true;
    }

    private void updateEncounter() {
        if (worldObj.isRemote || !isEntityAlive()) return;
        setPosition(anchorX, anchorY, anchorZ);
        if (getEncounterId().isEmpty()) return;
        for (int i = 1; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        List<EntityPlayer> ps = players();
        ForgottenLakeEncounterData data = ForgottenLakeEncounterData.get(worldObj);
        if (data.kingDead(getEncounterId())) {
            if (getEncounterState() != DEFEATED) setDead();
            return;
        }
        long now = worldObj.getTotalWorldTime();
        if (lastPlayerPresence < 0 || now < lastPlayerPresence) lastPlayerPresence = now;
        long absence = Math.max(0, now - lastPlayerPresence);
        if (getEncounterState() != DORMANT && getEncounterState() != RECOVERING && absence >= 1200) {
            resetEncounter();
            lastPlayerPresence = now;
            absentTicks = 0;
            return;
        }
        if (!ps.isEmpty()) {
            lastPlayerPresence = now;
            absentTicks = 0;
        } else absentTicks = (int) Math.min(Integer.MAX_VALUE, absence);
        if (getEncounterState() == DORMANT) {
            for (EntityPlayer p : ps) if (getDistanceSqToEntity(p) <= 24 * 24 && data.kingReady(getEncounterId())) {
                awakenForMission();
                break;
            }
            return;
        }
        if (getEncounterState() == AWAKENING) {
            phase(getVisualPhaseTicks() + 1);
            if (getVisualPhaseTicks() % 20 == 0 && livingSummons() < initialGuardTarget)
                summonGuards(initialGuardTarget - livingSummons(), false, true);
            if (getVisualPhaseTicks() >= AWAKENING_TICKS && livingSummons() >= 20) {
                state(ACTIVE);
                nextSkill = attackInterval();
            }
            return;
        }
        if (ps.isEmpty()) {
            return;
        }
        absentTicks = 0;
        if (getEncounterState() == RECOVERING) {
            state(AWAKENING);
            initialGuardTarget = 20 + rand.nextInt(11);
            summonGuards(initialGuardTarget, false, true);
            initialGuardsSummoned = true;
            sound("chant");
            return;
        }
        if (!initialGuardsSummoned && getCombatPhase() == 1) {
            initialGuardTarget = 20 + rand.nextInt(11);
            summonGuards(initialGuardTarget, false, true);
            initialGuardsSummoned = true;
            state(AWAKENING);
            sound("chant");
            return;
        }
        engaged = true;
        trackRear(ps);
        phase(getVisualPhaseTicks() + 1);
        combatTicks++;
        if (lockTicks > 0) lock(lockTicks - 1);
        advanceMeteor(ps);
        if (getCombatPhase() == 2 && livingSummons() > 0) return;
        if (getCombatPhase() == 4 && ++guardWaveTicks >= 600) {
            summonGuards(8, true, false);
            guardWaveTicks = 0;
        }
        if (lockTicks > 0) return;
        // The proximity field owns its cooldown and does not consume the normal attack schedule.
        if (cooldowns[SKILL_SHOCK] == 0) for (EntityPlayer p : ps) if (horizontalBodyDistanceSq(p) <= 9) {
            domain(ps);
            cooldowns[SKILL_SHOCK] = 400;
            break;
        }
        if (getSkillId() != SKILL_NONE) advanceSkill(ps);
        if (--nextSkill <= 0 && getSkillId() == SKILL_NONE) {
            nextSkill = chooseSkill(ps) ? attackInterval() : 0;
        }
    }

    private int attackInterval() {
        int ticks = getCombatPhase() == 1 ? 160 : getCombatPhase() == 2 ? 120 : getCombatPhase() == 3 ? 100 : 80;
        return isEnraged() ? Math.max(1, ticks / 3) : ticks;
    }

    /** Horizontal separation between collision bodies, not the six-wide king's inaccessible center. */
    public double horizontalBodyDistanceSq(Entity entity) {
        double dx = Math
            .max(0, Math.max(boundingBox.minX - entity.boundingBox.maxX, entity.boundingBox.minX - boundingBox.maxX));
        double dz = Math
            .max(0, Math.max(boundingBox.minZ - entity.boundingBox.maxZ, entity.boundingBox.minZ - boundingBox.maxZ));
        return dx * dx + dz * dz;
    }

    private double horizontalSq(Entity p, double x, double z) {
        double dx = p.posX - x, dz = p.posZ - z;
        return dx * dx + dz * dz;
    }

    private EntityPlayer priorityPlayer(List<EntityPlayer> ps) {
        for (EntityPlayer p : ps) if (isCondemned(p)) return p;
        return ps.get(rand.nextInt(ps.size()));
    }

    private List<EntityResidualOathguard> summons() {
        List<EntityResidualOathguard> out = new ArrayList<>();
        for (Object o : worldObj.loadedEntityList) if (o instanceof EntityResidualOathguard) {
            EntityResidualOathguard g = (EntityResidualOathguard) o;
            if (g.isKingSummon(getUniqueID())) out.add(g);
        }
        return out;
    }

    public boolean ownsSummonedGuard(UUID id) {
        return pendingGuards.contains(id);
    }

    public void summonedGuardDied(UUID id) {
        pendingGuards.remove(id);
    }

    private int livingSummons() {
        for (EntityResidualOathguard g : summons()) if (!g.isEntityAlive()) pendingGuards.remove(g.getUniqueID());
        return pendingGuards.size();
    }

    private void summonGuards(int count, boolean elite, boolean dormant) {
        List<EntityPlayer> ps = players();
        double yaw = Math.toRadians(getHomeYaw()), fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double sx = Math.cos(yaw), sz = Math.sin(yaw);
        for (int i = 0; i < count; i++) {
            double side = (i & 1) == 0 ? -1 : 1;
            int row = i / 2;
            double x = 0, y = -1, z = 0;
            // Front-facing staggered banks leave the western entrance's z +/-3 walk clear.
            for (int attempt = 0; attempt < 60; attempt++) {
                double forward = 2 + ((row + attempt) % (elite ? 4 : 7)) * 2.8;
                double lateral = side * ((elite ? 8 : 9) + ((row / 4 + attempt / 7) % 4) * 3.2);
                double cx = anchorX + fx * forward + sx * lateral;
                double cz = anchorZ + fz * forward + sz * lateral;
                double cy = guardSurface(cx, cz);
                if (cy < 0) continue;
                net.minecraft.util.AxisAlignedBB body = net.minecraft.util.AxisAlignedBB
                    .getBoundingBox(cx - .65, cy + .01, cz - .65, cx + .65, cy + 2.6, cz + .65);
                if (!worldObj.getCollidingBoundingBoxes(this, body)
                    .isEmpty() || worldObj.isAnyLiquid(body) || !worldObj.checkNoEntityCollision(body)) continue;
                boolean supported = true;
                for (double dx : new double[] { -.55, .55 }) for (double dz : new double[] { -.55, .55 }) {
                    double floor = guardSurface(cx + dx, cz + dz);
                    if (floor < 0 || Math.abs(floor - cy) > .5) supported = false;
                }
                if (!supported) continue;
                x = cx;
                y = cy;
                z = cz;
                break;
            }
            if (y < 0) continue;
            EntityResidualOathguard g = new EntityResidualOathguard(worldObj);
            g.initialize(getEncounterId(), -1, x, y, z);
            g.configureKingSummon(getUniqueID(), dormant, elite);
            if (worldObj.spawnEntityInWorld(g)) {
                pendingGuards.add(g.getUniqueID());
                if (!dormant && !ps.isEmpty()) g.activateKingSummon(priorityPlayer(ps));
            }
        }
    }

    private double guardSurface(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!worldObj.getChunkProvider()
            .chunkExists(bx >> 4, bz >> 4)) return -1;
        for (int by = Math.min(254, (int) anchorY + 8); by >= Math.max(0, (int) anchorY - 6); by--) {
            net.minecraft.util.AxisAlignedBB support = worldObj.getBlock(bx, by, bz)
                .getCollisionBoundingBoxFromPool(worldObj, bx, by, bz);
            if (support != null && support.maxY >= anchorY - 5 && support.maxY <= anchorY + 8) return support.maxY;
        }
        return -1;
    }

    private double surface(double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!worldObj.getChunkProvider()
            .chunkExists(bx >> 4, bz >> 4)) return -1;
        for (int by = Math.min(254, (int) y); by >= 0; by--) {
            net.minecraft.util.AxisAlignedBB box = worldObj.getBlock(bx, by, bz)
                .getCollisionBoundingBoxFromPool(worldObj, bx, by, bz);
            if (box != null) return box.maxY;
        }
        return -1;
    }

    private void resetEncounter() {
        for (EntityResidualOathguard g : summons()) g.setDead();
        pendingGuards.clear();
        stopEffects();
        skill(SKILL_NONE, 0);
        if (meteorTicks >= 0) sendMeteor(2, 0);
        meteorTicks = -1;
        dataWatcher.updateObject(30, -1);
        setHealth(MAX_HEALTH);
        dataWatcher.updateObject(27, 1);
        dataWatcher.updateObject(28, 0);
        behindTicks.clear();
        condemned.clear();
        lock(0);
        guardWaveTicks = 0;
        nextSkill = 160;
        java.util.Arrays.fill(cooldowns, 0);
        initialGuardsSummoned = false;
        state(RECOVERING);
    }

    private void enterPhase(int stage, List<EntityPlayer> ps) {
        dataWatcher.updateObject(27, stage);
        stopEffects();
        skill(SKILL_NONE, 0);
        sound("phase");
        nextSkill = attackInterval();
        if (stage == 2) {
            for (EntityResidualOathguard g : summons()) if (!ps.isEmpty()) g.activateKingSummon(priorityPlayer(ps));
            lock(60);
        } else if (stage == 3) {
            lock(200);
            silence(ps);
            if (!ps.isEmpty()) startMeteor(priorityPlayer(ps));
        } else if (stage == 4) {
            announceSkill(SKILL_ECHO);
            lock(60);
            guardWaveTicks = 0;
            for (EntityPlayer p : ps) {
                p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 9));
                // Resolve both impulses in this tick: a checked 20-block lift and immediate ground crush.
                p.moveEntity(0, 20, 0);
                p.velocityChanged = true;
                crush(p);
            }
            effect(1, 20, new CombatGeometry(CombatGeometry.CIRCLE, posX, posY, posZ, 0, 0, 24, 0), SKILL_ECHO);
        }
    }

    private boolean chooseSkill(List<EntityPlayer> ps) {
        int[] choices = getCombatPhase() >= 3 ? new int[] { 1, 2, 3, 5 } : new int[] { 1, 2, 5 };
        List<Integer> available = new ArrayList<>();
        for (int id : choices) if (cooldowns[id] == 0 && (id != 3 || meteorTicks < 0)) available.add(id);
        if (available.isEmpty()) return false;
        int id = available.get(rand.nextInt(available.size()));
        EntityPlayer p = priorityPlayer(ps);
        if (id == 2) {
            silence(ps);
            return true;
        }
        if (id == 3) {
            startMeteor(p);
            return true;
        }
        echoX = p.posX;
        echoY = surface(p.posX, p.posY, p.posZ);
        if (echoY < 0) echoY = p.posY;
        echoZ = p.posZ;
        pullDirection = 1 + rand.nextInt(5);
        lock(lockTicks);
        announceSkill(id);
        skill(id, 0);
        attackSerial++;
        cooldowns[id] = 120;
        effect(0, 20, geometry());
        sound(id == 1 ? "crush" : "pull");
        return true;
    }

    private CombatGeometry geometry() {
        return new CombatGeometry(CombatGeometry.POINT, echoX, echoY, echoZ, 0, 0, 3, 0);
    }

    private void effect(int stage, int duration, CombatGeometry geometry) {
        effect(stage, duration, geometry, getSkillId());
    }

    private void effect(int stage, int duration, CombatGeometry geometry, int id) {
        CombatEffects.send(
            this,
            attackSerial * 32,
            stage,
            duration,
            2,
            geometry,
            id,
            id == SKILL_SWEEP ? getGravityDirection() : 0);
    }

    private void stopEffects() {
        if (getSkillId() != 0) effect(2, 0, geometry());
    }

    private void damage(EntityPlayer p, DamageSource type, float amount) {
        if (valid(p)) {
            p.hurtResistantTime = 0;
            if (p.attackEntityFrom(type, amount)) participants.add(p.getUniqueID());
        }
    }

    private void crush(EntityPlayer p) {
        double ground = surface(p.posX, p.posY, p.posZ);
        if (ground < 0) return;
        double height = Math.max(0, p.posY - ground);
        damage(p, DamageSource.magic, 4);
        if (height > .1) damage(p, DamageSource.causeMobDamage(this), (float) (height * 2));
        p.moveEntity(0, -height, 0);
        p.motionY = -.8;
        p.velocityChanged = true;
    }

    private void traction(EntityPlayer p) {
        damage(p, DamageSource.magic, 4);
        double dx = pullDirection == 2 ? -5 : pullDirection == 3 ? 5 : 0;
        double dy = pullDirection == 1 ? 5 : 0;
        double dz = pullDirection == 4 ? -5 : pullDirection == 5 ? 5 : 0;
        double x = p.posX, y = p.posY, z = p.posZ;
        // moveEntity performs vanilla swept AABB clipping: no teleport through a wall.
        p.moveEntity(dx, dy, dz);
        p.velocityChanged = true;
        if (Math.abs(p.posX - x - dx) > .01 || Math.abs(p.posY - y - dy) > .01 || Math.abs(p.posZ - z - dz) > .01)
            damage(p, DamageSource.causeMobDamage(this), 10);
    }

    private void advanceSkill(List<EntityPlayer> ps) {
        int t = getSkillTicks() + 1;
        skill(getSkillId(), t);
        if (t == 20) {
            effect(1, 12, geometry());
            for (EntityPlayer p : ps) if (horizontalSq(p, echoX, echoZ) <= 9) {
                if (getSkillId() == 1) crush(p);
                else traction(p);
            }
        }
        if (t >= 20) {
            skill(SKILL_NONE, 0);
        }
    }

    private void silence(List<EntityPlayer> ps) {
        announceSkill(SKILL_PULSE);
        cooldowns[2] = 1200;
        sound("silence");
        attackSerial++;
        effect(1, 20, new CombatGeometry(CombatGeometry.CIRCLE, posX, posY, posZ, 0, 0, 55, 0), SKILL_PULSE);
        for (EntityPlayer p : ps) {
            p.addPotionEffect(new PotionEffect(Potion.weakness.id, 400, 2));
            p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 400, 0));
        }
    }

    private void domain(List<EntityPlayer> ps) {
        announceSkill(SKILL_SHOCK);
        sound("domain");
        attackSerial++;
        effect(1, 12, new CombatGeometry(CombatGeometry.CIRCLE, posX, posY, posZ, 0, 0, 3, 0), SKILL_SHOCK);
        for (EntityPlayer p : ps) if (horizontalBodyDistanceSq(p) <= 9) {
            damage(p, DamageSource.magic, 2);
            double dx = p.posX - posX, dz = p.posZ - posZ, length = Math.sqrt(dx * dx + dz * dz);
            if (length < .001) {
                dx = -Math.sin(Math.toRadians(getHomeYaw()));
                dz = Math.cos(Math.toRadians(getHomeYaw()));
                length = 1;
            }
            p.addVelocity(dx / length * 1.6, .45, dz / length * 1.6);
            p.velocityChanged = true;
        }
    }

    private void startMeteor(EntityPlayer p) {
        announceSkill(SKILL_CROWN);
        meteorSerial++;
        meteorTicks = 0;
        meteorTarget = p.getUniqueID();
        meteorX = p.posX;
        meteorZ = p.posZ;
        meteorY = surface(p.posX, p.posY, p.posZ);
        if (meteorY < 0) meteorY = p.posY;
        cooldowns[3] = 2400;
        sound("meteor");
        dataWatcher.updateObject(30, 0);
        sendMeteor(0, 120);
    }

    private void sendMeteor(int stage, int duration) {
        CombatEffects.send(
            this,
            1900000000 + meteorSerial * 32,
            stage,
            duration,
            2,
            new CombatGeometry(CombatGeometry.POINT, meteorX, meteorY, meteorZ, 0, 0, 5, 0),
            SKILL_CROWN,
            0);
    }

    private void advanceMeteor(List<EntityPlayer> ps) {
        if (meteorTicks < 0) return;
        meteorTicks++;
        dataWatcher.updateObject(30, meteorTicks);
        if (meteorTicks <= 40) for (EntityPlayer p : ps) if (p.getUniqueID()
            .equals(meteorTarget)) {
                meteorX = p.posX;
                meteorZ = p.posZ;
                double ground = surface(p.posX, p.posY, p.posZ);
                if (ground >= 0) meteorY = ground;
                if (meteorTicks % 5 == 0) sendMeteor(0, 140 - meteorTicks);
            }
        // One-second warning, then five seconds of descent (impact six seconds after casting).
        if (meteorTicks == 120) {
            sendMeteor(1, 20);
            sound("impact");
            DamageSource explosion = new DamageSource("explosion").setExplosion();
            for (EntityPlayer p : ps) if (horizontalSq(p, meteorX, meteorZ) <= 25) {
                damage(p, DamageSource.magic, 10);
                damage(p, explosion, 50);
            }
            meteorTicks = -1;
            dataWatcher.updateObject(30, -1);
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
            for (EntityResidualOathguard guard : summons()) guard.setDead();
            if (meteorTicks >= 0) sendMeteor(2, 0);
            meteorTicks = -1;
            dataWatcher.updateObject(30, -1);
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
        n.setInteger("skillAnnouncements", dataWatcher.getWatchableObjectInt(17));
        n.setInteger("skillAnnouncementSerial", getSkillAnnouncementSerial());
        n.setInteger("healthSchema", 3);
        n.setInteger("combat", combatTicks);
        n.setInteger("attackSerial", attackSerial);
        n.setInteger("skillId", getSkillId());
        n.setInteger("skillTicks", getSkillTicks());
        n.setInteger("nextSkill", nextSkill);
        n.setInteger("kingPhase", getCombatPhase());
        n.setInteger("lockTicks", lockTicks);
        n.setInteger("absentTicks", absentTicks);
        n.setLong("lastPlayerPresence", lastPlayerPresence);
        n.setInteger("guardWaveTicks", guardWaveTicks);
        n.setBoolean("enraged", isEnraged());
        n.setBoolean("engaged", engaged);
        n.setBoolean("deathRecorded", deathRecorded);
        n.setInteger("initialGuardTarget", initialGuardTarget);
        n.setBoolean("initialGuards", initialGuardsSummoned);
        n.setInteger("pullDirection", pullDirection);
        for (int i = 1; i < cooldowns.length; i++) n.setInteger("cooldown" + i, cooldowns[i]);
        n.setDouble("ex", echoX);
        n.setDouble("ey", echoY);
        n.setDouble("ez", echoZ);
        n.setInteger("meteorSerial", meteorSerial);
        n.setInteger("meteorTicks", meteorTicks);
        n.setDouble("mx", meteorX);
        n.setDouble("my", meteorY);
        n.setDouble("mz", meteorZ);
        if (meteorTarget != null) n.setString("meteorTarget", meteorTarget.toString());
        NBTTagList rear = new NBTTagList();
        for (Map.Entry<UUID, Integer> e : behindTicks.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(
                "uuid",
                e.getKey()
                    .toString());
            entry.setInteger("ticks", e.getValue());
            rear.appendTag(entry);
        }
        n.setTag("rearTicks", rear);
        writeIds(n, "pendingGuards", pendingGuards);
        writeIds(n, "condemned", condemned);
        writeIds(n, "participants", participants);
    }

    public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        dataWatcher.updateObject(17, n.getInteger("skillAnnouncements"));
        dataWatcher.updateObject(18, n.getInteger("skillAnnouncementSerial"));
        if (!n.hasKey("healthSchema")) {
            getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(MAX_HEALTH);
            setHealth(Math.min(MAX_HEALTH, getHealth() * 3));
        }
        combatTicks = n.getInteger("combat");
        attackSerial = Math.max(0, n.getInteger("attackSerial"));
        skill(n.getInteger("skillId"), n.getInteger("skillTicks"));
        nextSkill = n.getInteger("nextSkill");
        int legacyPhase = getHealth() <= 500 ? 4 : getHealth() <= 1000 ? 3 : getHealth() <= 1500 ? 2 : 1;
        dataWatcher.updateObject(
            27,
            n.hasKey("kingPhase") ? Math.max(1, Math.min(4, n.getInteger("kingPhase"))) : legacyPhase);
        dataWatcher.updateObject(28, n.getBoolean("enraged") ? 1 : 0);
        lock(Math.max(0, n.getInteger("lockTicks")));
        absentTicks = Math.max(0, n.getInteger("absentTicks"));
        long now = worldObj.getTotalWorldTime();
        lastPlayerPresence = n.hasKey("lastPlayerPresence") && n.getLong("lastPlayerPresence") >= 0
            ? Math.min(now, n.getLong("lastPlayerPresence"))
            : Math.max(0, now - absentTicks);
        guardWaveTicks = n.getInteger("guardWaveTicks");
        engaged = n.getBoolean("engaged");
        deathRecorded = n.getBoolean("deathRecorded");
        initialGuardsSummoned = n.getBoolean("initialGuards");
        initialGuardTarget = n.hasKey("initialGuardTarget") ? n.getInteger("initialGuardTarget") : 20;
        pullDirection = n.getInteger("pullDirection");
        lock(lockTicks);
        for (int i = 1; i < cooldowns.length; i++) cooldowns[i] = Math.max(0, n.getInteger("cooldown" + i));
        echoX = n.getDouble("ex");
        echoY = n.getDouble("ey");
        echoZ = n.getDouble("ez");
        meteorSerial = n.getInteger("meteorSerial");
        meteorTicks = n.hasKey("meteorTicks") ? n.getInteger("meteorTicks") : -1;
        meteorX = n.getDouble("mx");
        meteorY = n.getDouble("my");
        meteorZ = n.getDouble("mz");
        dataWatcher.updateObject(30, meteorTicks);
        try {
            meteorTarget = UUID.fromString(n.getString("meteorTarget"));
        } catch (IllegalArgumentException ignored) {
            meteorTarget = null;
        }
        behindTicks.clear();
        NBTTagList rear = n.getTagList("rearTicks", 10);
        for (int i = 0; i < rear.tagCount(); i++) {
            NBTTagCompound entry = rear.getCompoundTagAt(i);
            try {
                behindTicks.put(UUID.fromString(entry.getString("uuid")), Math.max(0, entry.getInteger("ticks")));
            } catch (IllegalArgumentException ignored) {}
        }
        readIds(n, "pendingGuards", pendingGuards);
        readIds(n, "condemned", condemned);
        readIds(n, "participants", participants);
    }
}
