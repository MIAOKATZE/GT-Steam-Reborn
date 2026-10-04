package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.BattleStyle;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntityEncounterBase;

/** Weak echoes: server owns all combat; clients only consume watched animation phases. */
public class EntityOldEcho extends EntityEncounterBase
    implements cpw.mods.fml.common.registry.IEntityAdditionalSpawnData {

    public static final int IDLE = 0, COMBAT = 1, RETURNING = 2, DYING = 4;
    private static final int MINI_BIRTH_TICKS = 40;
    public static final int DEMONSTRATION_BIRTH_TICKS = 120, DEMONSTRATION_DEATH_START = 200,
        DEMONSTRATION_END_TICKS = 440;
    private boolean initialized, nightSpawn, deathRecorded;
    private int objectiveLayout, objectiveZone = -1;
    private boolean objectiveBoss;

    public void configureObjective(int layout, int zone, boolean boss) {
        objectiveLayout = layout;
        objectiveZone = zone;
        objectiveBoss = boss;
        if (industrialBoss()) {
            dataWatcher.updateObject(29, 0);
            dataWatcher.updateObject(30, 0);
            dataWatcher.updateObject(28, 120);
            setHealth(1);
        }
    }

    private boolean industrialBoss() {
        return getEncounterId().startsWith("echo:r7:") && (getKind() == EchoKind.DC02 || getKind() == EchoKind.DC08);
    }

    /** 0 dormant, 1 restoring, 2 active, 3 defeated; synchronized separately from combat animation. */
    public int getIndustrialBossStage() {
        return dead || deathRecorded ? 3 : dataWatcher.getWatchableObjectInt(29);
    }

    public int getRevivalTicks() {
        return dataWatcher.getWatchableObjectInt(30);
    }

    private boolean industrialFrozen() {
        return industrialBoss() && getIndustrialBossStage() < 2;
    }

    private boolean tickIndustrialRevival() {
        if (!industrialFrozen()) return false;
        if (getIndustrialBossStage() == 0 && com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime
            .bossReady(worldObj, getEncounterId())) dataWatcher.updateObject(29, 1);
        if (getSkillId() != 0) stopEffects();
        skill(0, 0);
        if (getAttackTarget() != null) setAttackTarget(null);
        getNavigator().clearPathEntity();
        motionX = motionY = motionZ = 0;
        setPosition(anchorX, anchorY, anchorZ);
        state(IDLE);
        if (getIndustrialBossStage() == 1) {
            int ticks = Math.min(208, getRevivalTicks() + 1);
            dataWatcher.updateObject(30, ticks);
            setHealth(1 + (getMaxHealth() - 1) * ticks / 208F);
            if (ticks == 208) {
                dataWatcher.updateObject(29, 2);
                cooldown = 40;
            }
        } else setHealth(1);
        com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime.rememberBossState(this);
        return true;
    }

    private boolean dormant() {
        return objectiveLayout >= 2 && objectiveBoss
            && !(getEncounterId().startsWith("echo:r7:")
                ? com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime
                    .bossReady(worldObj, getEncounterId())
                : RuinObjectives.isBossReady(worldObj, getEncounterId()));
    }

    private int cooldown = 40, returnTicks, pathFailures, ritualTicks;
    private int resonanceCooldown, resonanceHeals;
    private float resonanceDamage;
    private double aimX, aimY, aimZ, originX, originZ;
    private final AuthoredEchoAbilities authored = new AuthoredEchoAbilities(this);
    private final EchoIndustrialCombat industrialCombat = new EchoIndustrialCombat(this);
    private final MiniBossCombat miniBossCombat = new MiniBossCombat(this);
    private int summonBudget = 8, summonedLifetime;
    private String summoner = "";
    private float lastAcceptedPlayerDamage = 5;
    private boolean controlledPrepared, controlledFinished;
    private int lightningCooldown;
    private boolean resolvingDamage;

    private boolean authoredKind() {
        return getKind().style == BattleStyle.AUTHORED;
    }

    public boolean isSummonedEcho() {
        return !summoner.isEmpty();
    }

    boolean isSummonedBy(EntityOldEcho parent) {
        return summoner.equals(
            parent.getUniqueID()
                .toString());
    }

    int summonsRemaining() {
        return summonBudget;
    }

    void consumeSummon() {
        summonBudget--;
    }

    void markSummoned(EntityOldEcho parent, int lifetime) {
        summoner = parent.getUniqueID()
            .toString();
        summonedLifetime = lifetime;
        summonBudget = 0;
    }

    float repriseDamage() {
        return Math.max(2, Math.min(12, lastAcceptedPlayerDamage));
    }

    /** Call before spawning the terminal apparition; the caller owns its short visible delay. */
    public boolean prepareControlledAntimeme() {
        if (worldObj.isRemote || getKind() != EchoKind.DO02 || controlledPrepared || controlledFinished) return false;
        controlledPrepared = true;
        setCustomNameTag("(旧日虚影)" + getKind().displayName + " 99*");
        return true;
    }

    /** Explicit server-only terminal removal; never scheduled by the natural-spawn handler. */
    public boolean finishControlledAntimeme() {
        return beginDemonstrationDeath();
    }

    /** Story-only visual disappearance. No combat death hook, rewards or objective ledger. */
    public boolean beginDemonstrationDeath() {
        if (worldObj.isRemote || getKind() != EchoKind.DO02 || controlledFinished) return false;
        controlledFinished = true;
        setAttackTarget(null);
        authored.cancel();
        skill(0, 0);
        state(DYING);
        phase(0);
        setHealth(0);
        return true;
    }

    /** Restore the story's authoritative elapsed time without replaying combat or birth. */
    public void restoreDemonstrationTimeline(int elapsedTicks) {
        if (worldObj.isRemote || getKind() != EchoKind.DO02) return;
        int elapsed = Math.max(0, elapsedTicks);
        setAttackTarget(null);
        skill(0, 0);
        getNavigator().clearPathEntity();
        motionX = motionY = motionZ = 0;
        dataWatcher.updateObject(28, Math.min(DEMONSTRATION_BIRTH_TICKS, elapsed));
        if (elapsed >= DEMONSTRATION_DEATH_START) {
            beginDemonstrationDeath();
            deathTime = Math
                .min(DEMONSTRATION_END_TICKS - DEMONSTRATION_DEATH_START, elapsed - DEMONSTRATION_DEATH_START);
            syncDeathAnimation();
            if (elapsed >= DEMONSTRATION_END_TICKS) setDead();
        } else if (!controlledFinished) state(IDLE);
    }

    public int getDemonstrationStage() {
        if (getKind() != EchoKind.DO02) return -1;
        return getHealth() <= 0 || controlledFinished || getEncounterState() == DYING ? 2
            : dataWatcher.getWatchableObjectInt(28) < 120 ? 0 : 1;
    }

    /** Birth, idle and scripted disappearance are the apparition's only executable states. */
    boolean tickDemonstration() {
        if (getKind() != EchoKind.DO02) return false;
        setAttackTarget(null);
        skill(0, 0);
        getNavigator().clearPathEntity();
        motionX = motionY = motionZ = 0;
        setPosition(anchorX, anchorY, anchorZ);
        if (!controlledFinished && dataWatcher.getWatchableObjectInt(28) < 120)
            dataWatcher.updateObject(28, dataWatcher.getWatchableObjectInt(28) + 1);
        return true;
    }

    /** A real lightning source charges and heals the rooted vine, with a bounded gain. */
    @Override
    public void onStruckByLightning(net.minecraft.entity.effect.EntityLightningBolt bolt) {
        if (!worldObj.isRemote && getKind() == EchoKind.DI11) {
            if (lightningCooldown == 0) {
                heal(Math.min(20, getMaxHealth() * .15F));
                cooldown = 0;
                lightningCooldown = 200;
            }
            return;
        }
        super.onStruckByLightning(bolt);
    }

    public EntityOldEcho(World world) {
        super(world);
        configure(EchoKind.DR01, true);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(14, 0);
        dataWatcher.addObject(17, 0);
        dataWatcher.addObject(18, 0);
        dataWatcher.addObject(25, 0);
        dataWatcher.addObject(26, 0);
        dataWatcher.addObject(27, 0);
        dataWatcher.addObject(28, 0);
        dataWatcher.addObject(29, 2);
        dataWatcher.addObject(30, 0);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(60);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.20);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(.4);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    public void func_145781_i(int id) {
        super.func_145781_i(id);
        if (id == 25) {
            EchoKind kind = getKind();
            setSize(kind.width, kind.height);
            noClip = kind.isRitual();
            ignoreFrustumCheck = kind.isRitual();
        }
    }

    public EchoKind getKind() {
        return EchoKind.byWireId(dataWatcher.getWatchableObjectInt(25));
    }

    @Override
    public void writeSpawnData(io.netty.buffer.ByteBuf buffer) {
        buffer.writeLong(getUniqueID().getMostSignificantBits());
        buffer.writeLong(getUniqueID().getLeastSignificantBits());
    }

    @Override
    public void readSpawnData(io.netty.buffer.ByteBuf buffer) {
        entityUniqueID = new java.util.UUID(buffer.readLong(), buffer.readLong());
    }

    public int getPhaseLockTicks() {
        return dataWatcher.getWatchableObjectInt(14);
    }

    public boolean isNightSpawn() {
        return nightSpawn;
    }

    public int getSkillId() {
        return dataWatcher.getWatchableObjectInt(26);
    }

    public int getSkillTicks() {
        return dataWatcher.getWatchableObjectInt(27);
    }

    public void setNodeIndex(int node) {
        dataWatcher.updateObject(23, node);
    }

    public void initializeEcho(EchoKind kind, String structureId, double x, double y, double z, boolean night) {
        configure(kind, true);
        initialize(structureId == null ? "" : structureId, -1, x, y, z);
        initialized = true;
        nightSpawn = night;
        controlledPrepared = controlledFinished = false;
        deathRecorded = false;
        ritualTicks = returnTicks = pathFailures = 0;
        cooldown = 40;
        state(IDLE);
        skill(0, 0);
        dataWatcher.updateObject(28, 0);
        dataWatcher.updateObject(14, 0);
    }

    private void configure(EchoKind kind, boolean fill) {
        dataWatcher.updateObject(25, kind.ordinal());
        setSize(kind.width, kind.height);
        experienceValue = kind.isRitual() || kind == EchoKind.DO02 ? 0
            : kind.isHeavy() ? 100 : kind.hasBossBar() ? 30 : 8;
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(kind.maxHealth);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(kind.isHeavy() ? .12 : .20);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(kind.hasBossBar() ? 1 : .4);
        if (kind.style == BattleStyle.AUTHORED) {
            com.google.gson.JsonObject stats = AuthoredEchoAbilities.profile(kind)
                .getAsJsonObject("stats");
            getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                .setBaseValue(AuthoredEchoAbilities.number(stats, "move_speed", .2));
            if (kind == EchoKind.DR08)
                getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(.85);
        }
        setCustomNameTag("(旧日虚影)" + kind.displayName);
        boolean fixed = kind.stationary() || kind.isRitual() || kind == EchoKind.DC08 || kind == EchoKind.DO02;
        if (!fixed) {
            boolean smallCrawler = kind.code.startsWith("dr-") && kind.height <= 1 && !kind.flies();
            double minimum = kind.flies() ? .28 : smallCrawler ? .25 : .23;
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(
                Math.max(minimum, getEntityAttribute(SharedMonsterAttributes.movementSpeed).getBaseValue()));
        }
        if (fill) setHealth(kind.maxHealth);
        noClip = kind.isRitual();
        ignoreFrustumCheck = kind.isRitual();
    }

    void skill(int id, int ticks) {
        dataWatcher.updateObject(26, id);
        dataWatcher.updateObject(27, ticks);
    }

    public int getSkillAnnouncementSerial() {
        return dataWatcher.getWatchableObjectInt(18);
    }

    public int getAnnouncedSkill() {
        return dataWatcher.getWatchableObjectInt(17);
    }

    /** A cast event, not a skill animation tick; repeated casts of the same skill remain distinct. */
    void announceSkill(int id) {
        if (worldObj.isRemote || id <= 0) return;
        dataWatcher.updateObject(17, id);
        dataWatcher
            .updateObject(18, getSkillAnnouncementSerial() == Integer.MAX_VALUE ? 1 : getSkillAnnouncementSerial() + 1);
    }

    public String getVisualClip() {
        if (getKind() == EchoKind.DO02)
            return getHealth() <= 0 || controlledFinished || getEncounterState() == DYING ? "death_a"
                : dataWatcher.getWatchableObjectInt(28) < 120 ? "spawn" : "idle";
        if (getHealth() <= 0 || getEncounterState() == DYING) return "death";
        if (MiniBossCombat.supports(getKind()) && dataWatcher.getWatchableObjectInt(28) < MINI_BIRTH_TICKS)
            return "spawn";
        if (authoredKind() && dataWatcher.getWatchableObjectInt(28) < authored.spawnDuration()) return "spawn";
        if (MiniBossCombat.supports(getKind()) && getSkillId() != 0) return MiniBossCombat.clip(getKind());
        if (authoredKind() && getSkillId() != 0) return authored.clip(getSkillId());
        if (getKind().isRitual()) return getVisualPhaseTicks() < 80 ? "spawn" : "idle";
        if (getSkillId() != 0) {
            if (getKind() == EchoKind.DC02) return getSkillId() == 2 ? "fault_line" : "seismic_impact";
            if (getKind() == EchoKind.DC08) return getSkillId() == 1 ? "command_pulse" : "brood_mortar";
            return getKind().skillClip;
        }
        return limbSwingAmount > .02F ? "walk" : "idle";
    }

    public double getVisualTicks(float partial) {
        if (getKind() == EchoKind.DO02) return getHealth() <= 0 ? getDeathAnimationTicks() + partial
            : dataWatcher.getWatchableObjectInt(28) < 120 ? dataWatcher.getWatchableObjectInt(28) + partial
                : ticksExisted + partial;
        if (getHealth() <= 0) return deathTime + partial;
        if (MiniBossCombat.supports(getKind()) && dataWatcher.getWatchableObjectInt(28) < MINI_BIRTH_TICKS)
            return dataWatcher.getWatchableObjectInt(28) + partial;
        if (authoredKind() && dataWatcher.getWatchableObjectInt(28) < authored.spawnDuration())
            return dataWatcher.getWatchableObjectInt(28) + partial;
        if (getKind().isRitual()) return getVisualPhaseTicks() + partial;
        return getSkillId() != 0 ? getSkillTicks() + partial : ticksExisted + partial;
    }

    private boolean valid(EntityPlayer player) {
        return player != null && player.worldObj == worldObj
            && player.isEntityAlive()
            && !player.capabilities.isCreativeMode;
    }

    public boolean isInRangeToRenderDist(double distance) {
        return getKind().isRitual() ? distance < 256 * 256 : super.isInRangeToRenderDist(distance);
    }

    private double leash() {
        return getKind().hasBossBar() ? 48 : 24;
    }

    private EntityPlayer acquire() {
        EntityPlayer best = null;
        double distance = getKind().hasBossBar() ? 32 * 32 : 14 * 14;
        for (Object object : worldObj.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            double d = getDistanceSqToEntity(player);
            if (valid(player) && d < distance
                && player.getDistanceSq(anchorX, anchorY, anchorZ) < leash() * leash()
                && canEntityBeSeen(player)) {
                best = player;
                distance = d;
            }
        }
        return best;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !getKind().isRitual() && super.canBeCollidedWith();
    }

    @Override
    public boolean canBePushed() {
        return !getKind().isRitual() && super.canBePushed();
    }

    @Override
    public void applyEntityCollision(Entity entity) {
        if (!getKind().isRitual()) super.applyEntityCollision(entity);
    }

    /** Clamp the health actually accepted after Forge hurt hooks, armor and absorption. */
    @Override
    public void setHealth(float health) {
        float before = getHealth();
        boolean transition = false;
        if (Float.isNaN(health)) health = Float.isFinite(before) ? before : 1;
        if (resolvingDamage && worldObj != null && !worldObj.isRemote && getKind().isHeavy() && before > 0) {
            if (getPhaseLockTicks() > 0 && health < before) health = before;
            else {
                float upper = getMaxHealth() * 2F / 3F, lower = getMaxHealth() / 3F;
                float floor = before > upper ? upper : before > lower ? lower : 0;
                if (floor > 0 && health <= floor) {
                    health = floor;
                    transition = true;
                }
            }
        }
        super.setHealth(health);
        if (transition) {
            dataWatcher.updateObject(14, 60);
            stopEffects();
            skill(0, 0);
            getNavigator().clearPathEntity();
            motionX = motionY = motionZ = 0;
            industrialCombat.enteredPhase();
            worldObj.playSoundEffect(posX, posY, posZ, combatSound("phase"), 1, .85F);
        }
    }

    @Override
    protected void damageEntity(DamageSource source, float amount) {
        if (getKind() == EchoKind.DO02 || Float.isNaN(amount) || amount <= 0 || getPhaseLockTicks() > 0) return;
        boolean previous = resolvingDamage;
        resolvingDamage = true;
        try {
            super.damageEntity(source, Math.min(amount, 1.0E30F));
        } finally {
            resolvingDamage = previous;
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (getKind() == EchoKind.DO02 || Float.isNaN(amount) || amount <= 0 || getPhaseLockTicks() > 0) return false;
        if (getKind().isRitual() || industrialFrozen() || (!worldObj.isRemote && dormant())) return false;
        if (!worldObj.isRemote && source.getEntity() instanceof EntityPlayer
            && valid((EntityPlayer) source.getEntity())) {
            setAttackTarget((EntityPlayer) source.getEntity());
            if (getEncounterState() != COMBAT) state(COMBAT);
        }
        // Mirror guard has a vulnerable rear; frontal hits during windup are softened, never fully rejected.
        if (getKind().style == BattleStyle.MIRROR && getSkillId() != 0 && source.getEntity() != null) {
            double dx = source.getEntity().posX - posX, dz = source.getEntity().posZ - posZ;
            double angle = Math.toRadians(rotationYaw);
            if (dx * -Math.sin(angle) + dz * Math.cos(angle) > 0) amount *= .35F;
        }
        float before = getHealth();
        boolean accepted = super.attackEntityFrom(source, Math.min(amount, 1.0E30F));
        if (accepted && source.getEntity() instanceof EntityPlayer)
            lastAcceptedPlayerDamage = Math.max(2, Math.min(12, before - getHealth()));
        if (accepted && getKind() == EchoKind.DC08 && getSkillId() == 4)
            resonanceDamage += Math.max(0, before - getHealth());
        return accepted;
    }

    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        if (industrialFrozen() || getPhaseLockTicks() > 0 || getKind() == EchoKind.DO02) {
            motionX = motionY = motionZ = 0;
            return;
        }
        if (!getKind().flies()) {
            if (getKind().stationary()) {
                motionX = motionZ = 0;
                strafe = forward = 0;
            }
            super.moveEntityWithHeading(strafe, forward);
            return;
        }
        // EntityLivingBase's normal path applies gravity even when combat AI is idle or spawning.
        moveFlying(strafe, forward, .025F);
        moveEntity(motionX, motionY, motionZ);
        motionX *= .8;
        motionY *= .8;
        motionZ *= .8;
        fallDistance = 0;
    }

    private void flyToward(double x, double y, double z, double speed) {
        getNavigator().clearPathEntity();
        double dx = x - posX, dy = y - posY, dz = z - posZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .25) {
            motionX = motionY = motionZ = 0;
        } else {
            motionX = dx / length * speed;
            motionY = dy / length * speed;
            motionZ = dz / length * speed;
        }
    }

    boolean tickMiniBossBirth() {
        if (!MiniBossCombat.supports(getKind()) || dataWatcher.getWatchableObjectInt(28) >= MINI_BIRTH_TICKS)
            return false;
        dataWatcher.updateObject(28, dataWatcher.getWatchableObjectInt(28) + 1);
        getNavigator().clearPathEntity();
        motionX = motionY = motionZ = 0;
        return true;
    }

    boolean tickPhaseTransition() {
        if (getPhaseLockTicks() <= 0) return false;
        dataWatcher.updateObject(14, getPhaseLockTicks() - 1);
        getNavigator().clearPathEntity();
        motionX = motionY = motionZ = 0;
        return true;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote || !isEntityAlive()) return;
        if (!initialized) {
            initializeEcho(getKind(), "", posX, posY, posZ, false);
        }
        if (tickIndustrialRevival()) return;
        if (tickPhaseTransition()) return;
        if (industrialBoss())
            com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime.rememberBossState(this);
        if (isSummonedEcho()) {
            boolean parentLoaded = false;
            for (Object object : worldObj.loadedEntityList) if (object instanceof EntityOldEcho) {
                EntityOldEcho parent = (EntityOldEcho) object;
                if (parent.isEntityAlive() && summoner.equals(
                    parent.getUniqueID()
                        .toString())) {
                    parentLoaded = true;
                    break;
                }
            }
            if (!parentLoaded || --summonedLifetime <= 0) {
                setDead();
                return;
            }
        }
        if (tickDemonstration()) return;
        if (tickMiniBossBirth()) return;
        if (authoredKind()) authored.tickBolts();
        if (lightningCooldown > 0) lightningCooldown--;
        if (authoredKind() && dataWatcher.getWatchableObjectInt(28) < authored.spawnDuration()) {
            dataWatcher.updateObject(28, dataWatcher.getWatchableObjectInt(28) + 1);
            getNavigator().clearPathEntity();
            return;
        }
        if (getKind().isRitual()) {
            noClip = true;
            motionX = motionY = motionZ = 0;
            setPosition(anchorX, anchorY, anchorZ);
            phase(++ritualTicks);
            if (ritualTicks >= 600) setDead();
            return;
        }
        if (dormant()) {
            if (getSkillId() != 0) stopEffects();
            skill(0, 0);
            setAttackTarget(null);
            getNavigator().clearPathEntity();
            if (getDistanceSq(anchorX, anchorY, anchorZ) > 1) returnHome();
            faceHome();
            return;
        }
        if (cooldown > 0) cooldown--;
        if (resonanceCooldown > 0) resonanceCooldown--;
        EntityLivingBase target = getAttackTarget();
        if (!(target instanceof EntityPlayer) || !valid((EntityPlayer) target)
            || target.getDistanceSq(anchorX, anchorY, anchorZ) > leash() * leash()
            || getDistanceSq(anchorX, anchorY, anchorZ) > leash() * leash()) {
            setAttackTarget(null);
            if (authoredKind()) {
                authored.cancel();
                skill(0, 0);
            } else if (MiniBossCombat.supports(getKind())) miniBossCombat.cancel();
            if (getDistanceSq(anchorX, anchorY, anchorZ) > 1) {
                returnHome();
                return;
            }
            EntityPlayer candidate = acquire();
            if (candidate == null) {
                if (getSkillId() != 0) stopEffects();
                if (getEncounterState() != IDLE) state(IDLE);
                skill(0, 0);
                heal(getKind().hasBossBar() ? 2 : .25F);
                getNavigator().clearPathEntity();
                faceHome();
                return;
            }
            setAttackTarget(candidate);
            target = candidate;
            state(COMBAT);
            returnTicks = pathFailures = 0;
        }
        if (getEncounterState() != COMBAT) state(COMBAT);
        getLookHelper().setLookPositionWithEntity(target, 30, 30);
        if (authoredKind()) {
            if (getSkillId() != 0) {
                int ticks = getSkillTicks() + 1, index = getSkillId();
                skill(index, ticks);
                authored.tickSkill(index, ticks);
                if (ticks >= authored.duration(index)) {
                    cooldown = authored.cooldown(index);
                    skill(0, 0);
                }
                return;
            }
            int selected = authored.select();
            double reach = authored.range(selected);
            if (cooldown == 0 && getDistanceSqToEntity(target) <= reach * reach && canEntityBeSeen(target)) {
                aimX = target.posX;
                aimY = target.posY;
                aimZ = target.posZ;
                skill(selected, 0);
                announceSkill(selected);
                authored.begin((EntityPlayer) target, selected);
                getNavigator().clearPathEntity();
            } else if (getKind().flies()) flyToward(target.posX, anchorY, target.posZ, .32);
            else if (!authored.stationary() && ticksExisted % 10 == 0) getNavigator().tryMoveToEntityLiving(target, 1);
            return;
        }
        if (MiniBossCombat.supports(getKind())) {
            miniBossCombat.tick((EntityPlayer) target);
            return;
        }
        if (getKind().isHeavy()) {
            industrialCombat.tick((EntityPlayer) target);
            return;
        }
        if (getSkillId() != 0) {
            advanceSkill((EntityPlayer) target);
            return;
        }
        BattleStyle style = getKind().style;
        boolean ranged = style == BattleStyle.BOMBER || style == BattleStyle.SNIPER
            || style == BattleStyle.GAZER
            || style == BattleStyle.WEAVER
            || style == BattleStyle.HIVE;
        double reach = ranged ? (style == BattleStyle.SNIPER ? 28 : 18) : getKind().width + 3;
        if (cooldown == 0 && getDistanceSqToEntity(target) < reach * reach && canEntityBeSeen(target)) {
            beginSkill((EntityPlayer) target);
        } else if (!ranged || getDistanceSqToEntity(target) > reach * reach * .6) {
            if (getKind().flies()) flyToward(target.posX, anchorY, target.posZ, .32);
            else if (ticksExisted % 10 == 0)
                getNavigator().tryMoveToEntityLiving(target, style == BattleStyle.STALKER ? 1.2 : 1);
        }
    }

    private void returnHome() {
        if (getEncounterState() != RETURNING) {
            state(RETURNING);
            stopEffects();
            skill(0, 0);
            returnTicks = pathFailures = 0;
            getNavigator().clearPathEntity();
        }
        heal(getKind().hasBossBar() ? 4 : .5F);
        returnTicks++;
        if (getKind().flies()) {
            flyToward(anchorX, anchorY, anchorZ, .32);
            if (getDistanceSq(anchorX, anchorY, anchorZ) < .25) state(IDLE);
            return;
        }
        if (ticksExisted % 10 == 0 && !getNavigator().tryMoveToXYZ(anchorX, anchorY, anchorZ, 1)) pathFailures++;
        if (getDistanceSq(anchorX, anchorY, anchorZ) < 1) {
            motionX = motionY = motionZ = 0;
            getNavigator().clearPathEntity();
            state(IDLE);
            cooldown = Math.max(40, cooldown);
        }
    }

    private int windup() {
        BattleStyle style = getKind().style;
        return getKind().isHeavy() ? 48 : style == BattleStyle.SNIPER ? 40 : getKind().hasBossBar() ? 30 : 18;
    }

    private void beginSkill(EntityPlayer player) {
        aimX = player.posX;
        aimY = CombatGeometry.groundY(worldObj, player.posX, player.posY, player.posZ);
        aimZ = player.posZ;
        originX = posX;
        originZ = posZ;
        int pool = getCombatPhase() == 0 ? 3 : getCombatPhase() == 1 ? 4 : 5;
        int selected = getKind().isHeavy() ? 1 + rand.nextInt(pool) : 1;
        if (getKind() == EchoKind.DC08 && selected == 4 && resonanceCooldown > 0) selected = 1 + rand.nextInt(3);
        skill(selected, 0);
        if (!attackSpaceLoaded()) {
            skill(0, 0);
            cooldown = 40;
            return;
        }
        if (getKind() == EchoKind.DC08 && selected == 4) {
            resonanceCooldown = 800;
            resonanceDamage = 0;
        }
        announceSkill(selected);
        attackSerial = attackSerial >= 60000000 ? 1 : attackSerial + 1;
        for (int j = 0; j < attackSteps(); j++)
            CombatEffects.send(this, attackSerial * 32 + j, 0, windup() + j * stepDelay(), palette(), geometry(j));
        getNavigator().clearPathEntity();
        worldObj.playSoundEffect(posX, posY, posZ, combatSound("attack"), .8F, .9F);
    }

    private boolean targeted() {
        BattleStyle s = getKind().style;
        return s == BattleStyle.BOMBER || s == BattleStyle.SNIPER
            || s == BattleStyle.WEAVER
            || s == BattleStyle.GAZER
            || s == BattleStyle.CINDER
            || (s == BattleStyle.HIVE && getSkillId() == 2);
    }

    private double radius() {
        switch (getKind().style) {
            case SEISMIC:
                return getSkillId() == 1 ? 12 : 4;
            case HIVE:
                return getSkillId() == 1 ? 10 : 4;
            case SPIRAL:
                return 7;
            case RESONATOR:
                return 9;
            case BELL:
                return 5;
            case WEAVER:
                return 4;
            case BOMBER:
                return 3;
            case SNIPER:
            case GAZER:
                return 1.6;
            default:
                return getKind().width + 2;
        }
    }

    private boolean hurt(EntityPlayer player, float damage, double knock) {
        if (!valid(player) || !canEntityBeSeen(player)) return false;
        boolean accepted = player.attackEntityFrom(DamageSource.causeMobDamage(this), damage);
        if (accepted && knock > 0) {
            double dx = player.posX - posX, dz = player.posZ - posZ;
            double len = Math.max(.01, Math.sqrt(dx * dx + dz * dz));
            player.addVelocity(dx / len * knock, .2, dz / len * knock);
            player.velocityChanged = true;
        }
        return accepted;
    }

    public int getCombatPhase() {
        return getHealth() <= getMaxHealth() / 3F ? 2 : getHealth() <= getMaxHealth() * 2F / 3F ? 1 : 0;
    }

    private int attackSerial;

    private int palette() {
        return getKind() == EchoKind.DC02 ? 0
            : getKind() == EchoKind.DC08 ? 3 : getKind().style == BattleStyle.CINDER ? 2 : 1;
    }

    private boolean attackSpaceLoaded() {
        int r = getKind().isHeavy() ? 26 : getKind().hasBossBar() ? 20 : 8;
        for (int cx = ((int) originX - r) >> 4; cx <= ((int) originX + r) >> 4; cx++)
            for (int cz = ((int) originZ - r) >> 4; cz <= ((int) originZ + r) >> 4; cz++)
                if (!worldObj.getChunkProvider()
                    .chunkExists(cx, cz)) return false;
        return true;
    }

    private CombatGeometry geometry(int step) {
        int id = getSkillId();
        double ax = aimX - originX, az = aimZ - originZ, len = Math.max(.01, Math.sqrt(ax * ax + az * az));
        if (getKind() == EchoKind.DC02) {
            if (id == 1 || (id == 5 && step == 0))
                return new CombatGeometry(CombatGeometry.RING, originX, aimY, originZ, 0, 0, 12, 7);
            if (id == 2 || id == 5) {
                double off = (id == 5 ? step - 1 : step) == 0 ? -3 : 3;
                double x = originX - az / len * off, z = originZ + ax / len * off;
                return new CombatGeometry(
                    CombatGeometry.LINE,
                    x,
                    aimY,
                    z,
                    x + ax / len * 24,
                    z + az / len * 24,
                    1.6,
                    0);
            }
            if (id == 3) return new CombatGeometry(CombatGeometry.POINT, aimX + (step - 1) * 4, aimY, aimZ, 0, 0, 3, 0);
            return new CombatGeometry(CombatGeometry.CONE, originX, aimY, originZ, aimX, aimZ, 15, 0);
        }
        if (getKind() == EchoKind.DC08) {
            if (id == 1 || (id == 5 && step == 3))
                return new CombatGeometry(CombatGeometry.POINT, aimX, aimY, aimZ, 0, 0, 4, 0);
            if (id == 2) return new CombatGeometry(
                CombatGeometry.RING,
                originX,
                aimY,
                originZ,
                0,
                0,
                6 + step * 4,
                3 + step * 4);
            if (id == 3 || id == 5) {
                double off = (step - 1) * 5, x = originX - az / len * off, z = originZ + ax / len * off;
                return new CombatGeometry(CombatGeometry.LINE, x, aimY, z, x + ax / len * 22, z + az / len * 22, 1, 0);
            }
            return new CombatGeometry(CombatGeometry.RING, originX, aimY, originZ, 0, 0, 14, 8);
        }
        boolean line = getKind().style == BattleStyle.SNIPER || getKind().style == BattleStyle.GAZER;
        boolean cone = getKind().style == BattleStyle.SWEEPER || getKind().style == BattleStyle.SENTINEL
            || getKind().style == BattleStyle.MIRROR;
        return new CombatGeometry(
            line ? CombatGeometry.LINE : cone ? CombatGeometry.CONE : CombatGeometry.CIRCLE,
            targeted() && !line ? aimX : originX,
            aimY,
            targeted() && !line ? aimZ : originZ,
            line ? originX + ax / len * 28 : aimX,
            line ? originZ + az / len * 28 : aimZ,
            line ? 1.6 : radius(),
            0);
    }

    private int attackSteps() {
        if (getKind() == EchoKind.DC02)
            return getSkillId() == 2 ? 2 : getSkillId() == 3 ? 3 : getSkillId() == 5 ? 3 : 1;
        if (getKind() == EchoKind.DC08) return getSkillId() == 2 || getSkillId() == 3 ? 3 : getSkillId() == 5 ? 4 : 1;
        return 1;
    }

    private int stepDelay() {
        return getKind() == EchoKind.DC02 ? 16 : 18;
    }

    private void stopEffects() {
        if (MiniBossCombat.supports(getKind())) {
            miniBossCombat.cancel();
            return;
        }
        if (getKind().isHeavy()) {
            industrialCombat.cancel();
            return;
        }
        if (authoredKind()) {
            authored.cancel();
            return;
        }
        for (int j = 0; j < attackSteps(); j++)
            CombatEffects.send(this, attackSerial * 32 + j, 2, 0, palette(), geometry(j));
    }

    private void advanceSkill(EntityPlayer target) {
        if (!attackSpaceLoaded()) {
            stopEffects();
            skill(0, 0);
            cooldown = 40;
            return;
        }
        int ticks = getSkillTicks() + 1;
        skill(getSkillId(), ticks);
        int warning = windup();
        for (int j = 0; j < attackSteps(); j++) if (ticks == warning + j * stepDelay()) {
            CombatGeometry g = geometry(j);
            CombatEffects.send(this, attackSerial * 32 + j, 1, 8, palette(), g);
            resolveGeometry(target, g);
            CombatEffects.send(this, attackSerial * 32 + j, 2, 18, palette(), g);
            worldObj.playSoundEffect(posX, posY, posZ, combatSound("attack"), .8F, .9F);
        }
        if (ticks >= warning + (attackSteps() - 1) * stepDelay() + 20) {
            skill(0, 0);
            cooldown = getKind().isHeavy() ? 100 - getCombatPhase() * 20 : getKind().hasBossBar() ? 70 : 35;
        }
    }

    @SuppressWarnings("unchecked")
    private void resolveGeometry(EntityPlayer target, CombatGeometry g) {
        BattleStyle style = getKind().style;
        if (style == BattleStyle.MENDER || (getKind() == EchoKind.DC08 && getSkillId() == 4)) {
            List<EntityOldEcho> allies = worldObj
                .getEntitiesWithinAABB(EntityOldEcho.class, boundingBox.expand(10, 6, 10));
            int count = 0;
            for (EntityOldEcho ally : allies) if (ally != this && ally.isEntityAlive()
                && !ally.getKind()
                    .isRitual()
                && ally.getEncounterId()
                    .equals(getEncounterId())
                && canEntityBeSeen(ally)
                && count++ < 12) {
                    ally.heal(style == BattleStyle.MENDER ? 8 : 25);
                    CombatGeometry link = new CombatGeometry(
                        CombatGeometry.CHAIN,
                        posX,
                        posY,
                        posZ,
                        ally.posX,
                        ally.posZ,
                        .12,
                        0);
                    CombatEffects.send(this, attackSerial * 32 + 6 + count, 1, 18, palette(), link);
                }
            if (style == BattleStyle.MENDER) {
                heal(4);
            }
            // The core can rebuild itself only twice. Sustained accepted damage during the
            // 48-tick warning disrupts the repair, while the advertised ring still resolves.
            if (resonanceHeals < 2 && resonanceDamage < 30) {
                heal(Math.min(45, getMaxHealth() * .03F));
                resonanceHeals++;
                worldObj.playSoundEffect(posX, posY, posZ, "portal.travel", .45F, 1.4F);
            } else if (resonanceDamage >= 30) {
                worldObj.playSoundEffect(posX, posY, posZ, "random.glass", .7F, .7F);
            }
        }
        if (style == BattleStyle.BOMBER || style == BattleStyle.SNIPER
            || style == BattleStyle.GAZER
            || style == BattleStyle.WEAVER) {
            EchoCombatProjectile.fire(
                this,
                posX,
                posY + Math.min(height * .6, 3),
                posZ,
                aimX,
                aimY + 1,
                aimZ,
                style == BattleStyle.SNIPER ? 1.2 : .65,
                style == BattleStyle.SNIPER ? 9 : 4,
                style == BattleStyle.BOMBER ? 3 : 1.4,
                style == BattleStyle.BOMBER ? .015 : 0,
                palette());
            return;
        }
        for (Object o : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (!valid(p) || !g.contains(p.posX, p.posY, p.posZ)) continue;
            float damage = getKind().isHeavy() ? 10 : getKind().hasBossBar() ? 7 : 4;
            if (style == BattleStyle.SNIPER) damage = 9;
            if (style == BattleStyle.STALKER) damage = 5;
            if (!hurt(
                p,
                damage,
                style == BattleStyle.SEISMIC ? .9
                    : style == BattleStyle.CHARGER ? .65
                        : style == BattleStyle.SENTINEL || style == BattleStyle.MIRROR ? .5 : 0))
                continue;
            if (style == BattleStyle.CINDER) p.setFire(2);
            if (style == BattleStyle.WEAVER || style == BattleStyle.BELL || style == BattleStyle.RESONATOR)
                p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, style == BattleStyle.WEAVER ? 1 : 0));
            if (style == BattleStyle.GAZER) p.addPotionEffect(new PotionEffect(Potion.weakness.id, 80, 0));
            if (style == BattleStyle.SPIRAL) {
                double dx = p.posX - originX, dz = p.posZ - originZ;
                p.addVelocity(-dz * .06, .1, dx * .06);
                p.velocityChanged = true;
            }
        }
        if (style == BattleStyle.CHARGER) {
            double dx = aimX - posX, dz = aimZ - posZ, len = Math.max(.01, Math.sqrt(dx * dx + dz * dz)),
                step = Math.min(4, len), nx = dx / len * step, nz = dz / len * step;
            if (worldObj.getCollidingBoundingBoxes(this, boundingBox.addCoord(nx, 0, nz))
                .isEmpty()) moveEntity(nx, 0, nz);
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        if (getKind() == EchoKind.DO02) {
            beginDemonstrationDeath();
            return;
        }
        if (dead || deathRecorded) return;
        super.onDeath(source);
        if (!dead && !worldObj.isRemote && getHealth() <= 0) {
            setHealth(1);
            deathTime = 0;
            return;
        }
        if (dead && !worldObj.isRemote && !deathRecorded) {
            deathRecorded = true;
            if (getSkillId() != 0) stopEffects();
            if (!isSummonedEcho() && getEncounterId().startsWith("echo:"))
                RuinObjectives.onGuardDeath(worldObj, getEncounterId(), getPlatformId(), objectiveZone);
            state(DYING);
            skill(0, 0);
            if (!isSummonedEcho()) MinecraftForge.EVENT_BUS.post(new EchoDeathEvent(this, source));
        }
    }

    @Override
    public int getDeathAnimationDuration() {
        return getKind() == EchoKind.DO02 ? 240 : getKind() == EchoKind.DR09 ? 200 : getKind().hasBossBar() ? 140 : 100;
    }

    @Override
    public int getDeathAnimationHoldTicks() {
        return getKind() == EchoKind.DR09 ? 80 : 12;
    }

    @Override
    public boolean isGoldenDeath() {
        return getKind() == EchoKind.DR09;
    }

    private String combatSound(String event) {
        return "gtsr:" + (MiniBossCombat.supports(getKind()) ? "mini." + getKind().code + "."
            : getKind() == EchoKind.DC02 ? "colossus."
                : getKind() == EchoKind.DC08 ? "hive." : "entity." + getKind().code + ".")
            + event;
    }

    @Override
    protected String getLivingSound() {
        return getKind().isRitual() ? null : combatSound("idle");
    }

    @Override
    protected String getHurtSound() {
        return combatSound("hurt");
    }

    @Override
    protected String getDeathSound() {
        return combatSound("death");
    }

    @Override
    protected void onDeathUpdate() {
        deathTime++;
        syncDeathAnimation();
        if (authoredKind() && !worldObj.isRemote) authored.death(deathTime);
        finishDeathAnimation();
    }

    @Override
    protected int getExperiencePoints(EntityPlayer player) {
        return isSummonedEcho() || getKind() == EchoKind.DO02 ? 0 : super.getExperiencePoints(player);
    }

    @Override
    protected void dropFewItems(boolean hit, int looting) {
        if (worldObj.isRemote || nightSpawn || isSummonedEcho() || getKind() == EchoKind.DO02) return;
        String witness = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreSources.bossRelic(getKind());
        net.minecraft.item.Item item = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry.RELICS
            .get(witness);
        if (item != null) entityDropItem(new net.minecraft.item.ItemStack(item), .1F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
        industrialCombat.write(n);
        miniBossCombat.write(n);
        authored.write(n);
        n.setInteger("objectiveLayout", objectiveLayout);
        n.setInteger("objectiveZone", objectiveZone);
        n.setBoolean("objectiveBoss", objectiveBoss);
        n.setString("echoKind", getKind().code);
        n.setBoolean("echoInitialized", initialized);
        n.setBoolean("echoNight", nightSpawn);
        n.setBoolean("echoDeathRecorded", deathRecorded);
        n.setInteger("echoCooldown", cooldown);
        n.setInteger("resonanceCooldown", resonanceCooldown);
        n.setInteger("resonanceHeals", resonanceHeals);
        n.setFloat("resonanceDamage", resonanceDamage);
        n.setInteger("attackSerial", attackSerial);
        n.setInteger("echoReturn", returnTicks);
        n.setInteger("echoPathFailures", pathFailures);
        n.setInteger("echoRitualTicks", ritualTicks);
        n.setInteger("echoPhaseLockTicks", getPhaseLockTicks());
        n.setInteger("echoSkillAnnouncementSerial", getSkillAnnouncementSerial());
        n.setInteger("echoAnnouncedSkill", getAnnouncedSkill());
        n.setInteger("echoSkill", getSkillId());
        n.setInteger("echoSkillTicks", getSkillTicks());
        n.setDouble("echoAimX", aimX);
        n.setDouble("echoAimY", aimY);
        n.setDouble("echoAimZ", aimZ);
        n.setDouble("echoOriginX", originX);
        n.setDouble("echoOriginZ", originZ);
        n.setInteger("echoSummonBudget", summonBudget);
        n.setInteger("echoSummonedLifetime", summonedLifetime);
        n.setString("echoSummoner", summoner);
        n.setFloat("echoRepriseDamage", lastAcceptedPlayerDamage);
        n.setBoolean("echoControlledFinished", controlledFinished);
        n.setBoolean("echoControlledPrepared", controlledPrepared);
        n.setInteger("echoLightningCooldown", lightningCooldown);
        n.setInteger("echoSpawnTicks", dataWatcher.getWatchableObjectInt(28));
        n.setInteger("industrialBossStage", getIndustrialBossStage());
        n.setInteger("industrialRevivalTicks", getRevivalTicks());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound n) {
        objectiveLayout = n.getInteger("objectiveLayout");
        objectiveZone = n.hasKey("objectiveZone") ? n.getInteger("objectiveZone") : -1;
        objectiveBoss = n.getBoolean("objectiveBoss");
        EchoKind kind;
        try {
            kind = EchoKind.byCode(n.getString("echoKind"));
        } catch (IllegalArgumentException e) {
            kind = EchoKind.DR01;
        }
        configure(kind, false);
        NBTTagCompound body = n;
        if (kind == EchoKind.DO02) {
            body = (NBTTagCompound) n.copy();
            body.setInteger("encounterDeathXP", 0);
            body.setBoolean("encounterDeathXPReleased", true);
        }
        super.readEntityFromNBT(body);
        configure(kind, false);
        initialized = n.getBoolean("echoInitialized");
        nightSpawn = n.getBoolean("echoNight");
        deathRecorded = n.getBoolean("echoDeathRecorded");
        cooldown = Math.max(0, n.getInteger("echoCooldown"));
        resonanceCooldown = Math.max(0, Math.min(800, n.getInteger("resonanceCooldown")));
        resonanceHeals = Math.max(0, Math.min(2, n.getInteger("resonanceHeals")));
        float savedDamage = n.getFloat("resonanceDamage");
        resonanceDamage = Float.isFinite(savedDamage) ? Math.max(0, savedDamage) : 30;
        attackSerial = Math.max(0, n.getInteger("attackSerial"));
        returnTicks = n.getInteger("echoReturn");
        pathFailures = n.getInteger("echoPathFailures");
        ritualTicks = Math.max(0, n.getInteger("echoRitualTicks"));
        skill(Math.max(0, Math.min(5, n.getInteger("echoSkill"))), Math.max(0, n.getInteger("echoSkillTicks")));
        aimX = n.getDouble("echoAimX");
        aimY = n.getDouble("echoAimY");
        aimZ = n.getDouble("echoAimZ");
        originX = n.getDouble("echoOriginX");
        originZ = n.getDouble("echoOriginZ");
        summonBudget = n.hasKey("echoSummonBudget") ? Math.max(0, Math.min(8, n.getInteger("echoSummonBudget"))) : 8;
        summonedLifetime = Math.max(0, Math.min(1200, n.getInteger("echoSummonedLifetime")));
        summoner = n.getString("echoSummoner");
        lastAcceptedPlayerDamage = n.hasKey("echoRepriseDamage") ? n.getFloat("echoRepriseDamage") : 5;
        if (!Float.isFinite(lastAcceptedPlayerDamage)) lastAcceptedPlayerDamage = 5;
        controlledFinished = n.getBoolean("echoControlledFinished");
        controlledPrepared = n.getBoolean("echoControlledPrepared");
        if (controlledPrepared) setCustomNameTag("(旧日虚影)" + getKind().displayName + " 99*");
        lightningCooldown = Math.max(0, Math.min(200, n.getInteger("echoLightningCooldown")));
        dataWatcher.updateObject(
            28,
            n.hasKey("echoSpawnTicks") ? Math.max(0, Math.min(120, n.getInteger("echoSpawnTicks"))) : 120);
        if (industrialBoss()) {
            objectiveLayout = 2;
            objectiveBoss = true;
        }
        if (industrialBoss()) dataWatcher.updateObject(28, 120);
        dataWatcher.updateObject(
            29,
            n.hasKey("industrialBossStage") ? Math.max(0, Math.min(3, n.getInteger("industrialBossStage")))
                : industrialBoss() ? 0 : 2);
        dataWatcher.updateObject(30, Math.max(0, Math.min(208, n.getInteger("industrialRevivalTicks"))));
        if (industrialFrozen())
            setHealth(getIndustrialBossStage() == 0 ? 1 : 1 + (getMaxHealth() - 1) * getRevivalTicks() / 208F);
        if (getKind().isHeavy()) industrialCombat.read(n);
        if (MiniBossCombat.supports(kind)) miniBossCombat.read(n);
        authored.read(n);
        dataWatcher.updateObject(14, Math.max(0, Math.min(60, n.getInteger("echoPhaseLockTicks"))));
        dataWatcher.updateObject(18, Math.max(0, n.getInteger("echoSkillAnnouncementSerial")));
        dataWatcher.updateObject(17, Math.max(0, Math.min(5, n.getInteger("echoAnnouncedSkill"))));
        if (kind == EchoKind.DO02) {
            initialized = true;
            summoner = "";
            summonedLifetime = summonBudget = 0;
            objectiveLayout = 0;
            objectiveZone = -1;
            objectiveBoss = false;
            deathRecorded = false;
            setAttackTarget(null);
            authored.cancel();
            skill(0, 0);
            dataWatcher.updateObject(14, 0);
            dataWatcher.updateObject(17, 0);
            dataWatcher.updateObject(18, 0);
            if (controlledFinished || getHealth() <= 0) {
                controlledFinished = true;
                state(DYING);
                setHealth(0);
            } else state(IDLE);
        }
        // Cancel a partial authored cast on reload: old events and transient projectiles never replay.
        if (authoredKind()) {
            authored.cancel();
            skill(0, 0);
            cooldown = Math.max(40, cooldown);
        }
    }

    @Override
    public void setDead() {
        if (authored != null) authored.cancel();
        if (miniBossCombat != null && MiniBossCombat.supports(getKind())) miniBossCombat.cancel();
        super.setDead();
    }
}
