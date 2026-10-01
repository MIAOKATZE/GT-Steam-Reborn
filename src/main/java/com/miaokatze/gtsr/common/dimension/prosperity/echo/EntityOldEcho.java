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
public class EntityOldEcho extends EntityEncounterBase {

    public static final int IDLE = 0, COMBAT = 1, RETURNING = 2, DYING = 4;
    private boolean initialized, nightSpawn, deathRecorded;
    private int objectiveLayout, objectiveZone = -1;
    private boolean objectiveBoss;

    public void configureObjective(int layout, int zone, boolean boss) {
        objectiveLayout = layout;
        objectiveZone = zone;
        objectiveBoss = boss;
    }

    private boolean dormant() {
        return objectiveLayout >= 2 && objectiveBoss && !RuinObjectives.isBossReady(worldObj, getEncounterId());
    }

    private int cooldown = 40, returnTicks, pathFailures, ritualTicks;
    private int resonanceCooldown, resonanceHeals;
    private float resonanceDamage;
    private double aimX, aimY, aimZ, originX, originZ;

    public EntityOldEcho(World world) {
        super(world);
        configure(EchoKind.DR01, true);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(25, 0);
        dataWatcher.addObject(26, 0);
        dataWatcher.addObject(27, 0);
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
        deathRecorded = false;
        ritualTicks = returnTicks = pathFailures = 0;
        cooldown = 40;
        state(IDLE);
        skill(0, 0);
    }

    private void configure(EchoKind kind, boolean fill) {
        dataWatcher.updateObject(25, kind.ordinal());
        setSize(kind.width, kind.height);
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(kind.maxHealth);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(kind.isHeavy() ? .12 : .20);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(kind.hasBossBar() ? 1 : .4);
        setCustomNameTag("(旧日虚影)" + kind.displayName);
        if (fill) setHealth(kind.maxHealth);
        noClip = kind.isRitual();
        ignoreFrustumCheck = kind.isRitual();
    }

    private void skill(int id, int ticks) {
        dataWatcher.updateObject(26, id);
        dataWatcher.updateObject(27, ticks);
    }

    public String getVisualClip() {
        if (getHealth() <= 0 || getEncounterState() == DYING) return "death";
        if (getKind().isRitual()) return getVisualPhaseTicks() < 80 ? "spawn" : "idle";
        if (getSkillId() != 0) {
            if (getKind() == EchoKind.DC02) return getSkillId() == 1 ? "seismic_impact" : "fault_line";
            if (getKind() == EchoKind.DC08) return getSkillId() == 1 ? "command_pulse" : "brood_mortar";
            return getKind().skillClip;
        }
        return limbSwingAmount > .02F ? "walk" : "idle";
    }

    public double getVisualTicks(float partial) {
        if (getHealth() <= 0) return deathTime + partial;
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

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (getKind().isRitual() || (!worldObj.isRemote && dormant())) return false;
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
        boolean accepted = super.attackEntityFrom(source, amount);
        if (accepted && getKind() == EchoKind.DC08 && getSkillId() == 4)
            resonanceDamage += Math.max(0, before - getHealth());
        return accepted;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote || !isEntityAlive()) return;
        if (!initialized) {
            initializeEcho(getKind(), "", posX, posY, posZ, false);
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
            setPosition(anchorX, anchorY, anchorZ);
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
        } else if (ticksExisted % 10 == 0 && (!ranged || getDistanceSqToEntity(target) > reach * reach * .6)) {
            getNavigator().tryMoveToEntityLiving(target, style == BattleStyle.STALKER ? 1.2 : .8);
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
        if (ticksExisted % 10 == 0 && !getNavigator().tryMoveToXYZ(anchorX, anchorY, anchorZ, 1)) pathFailures++;
        if (returnTicks >= 100 || pathFailures >= 3) {
            setPosition(anchorX, anchorY, anchorZ);
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
        attackSerial = attackSerial >= 60000000 ? 1 : attackSerial + 1;
        for (int j = 0; j < attackSteps(); j++)
            CombatEffects.send(this, attackSerial * 32 + j, 0, windup() + j * stepDelay(), palette(), geometry(j));
        getNavigator().clearPathEntity();
        worldObj.playSoundEffect(posX, posY, posZ, "note.harp", .65F, getKind().isHeavy() ? .55F : 1.1F);
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
        return getHealth() <= getMaxHealth() * .3F ? 2 : getHealth() <= getMaxHealth() * .6F ? 1 : 0;
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
            worldObj.playSoundEffect(posX, posY, posZ, "random.explode", .55F, getKind().isHeavy() ? .6F : 1.6F);
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
                return;
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
            if (getEncounterId().startsWith("echo:"))
                RuinObjectives.onGuardDeath(worldObj, getEncounterId(), getPlatformId(), objectiveZone);
            state(DYING);
            skill(0, 0);
            MinecraftForge.EVENT_BUS.post(new EchoDeathEvent(this, source));
        }
    }

    @Override
    protected void onDeathUpdate() {
        if (++deathTime >= (getKind().hasBossBar() ? 100 : 60)) setDead();
    }

    @Override
    protected void dropFewItems(boolean hit, int looting) {
        if (worldObj.isRemote || nightSpawn) return;
        String witness = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreSources.bossRelic(getKind());
        net.minecraft.item.Item item = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry.RELICS
            .get(witness);
        if (item != null) entityDropItem(new net.minecraft.item.ItemStack(item), .1F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n);
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
        n.setInteger("echoSkill", getSkillId());
        n.setInteger("echoSkillTicks", getSkillTicks());
        n.setDouble("echoAimX", aimX);
        n.setDouble("echoAimY", aimY);
        n.setDouble("echoAimZ", aimZ);
        n.setDouble("echoOriginX", originX);
        n.setDouble("echoOriginZ", originZ);
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
        super.readEntityFromNBT(n);
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
    }
}
