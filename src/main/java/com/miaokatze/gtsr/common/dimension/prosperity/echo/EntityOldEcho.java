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
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.BattleStyle;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntityEncounterBase;

/** Weak echoes: server owns all combat; clients only consume watched animation phases. */
public class EntityOldEcho extends EntityEncounterBase {

    public static final int IDLE = 0, COMBAT = 1, RETURNING = 2, DYING = 4;
    private boolean initialized, nightSpawn, deathRecorded;
    private int cooldown = 40, returnTicks, pathFailures, ritualTicks;
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
        if (getKind().isRitual()) return false;
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
        return super.attackEntityFrom(source, amount);
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
        if (cooldown > 0) cooldown--;
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
        aimY = player.posY;
        aimZ = player.posZ;
        originX = posX;
        originZ = posZ;
        skill(getKind().isHeavy() ? 1 + rand.nextInt(2) : 1, 0);
        getNavigator().clearPathEntity();
        worldObj.playSoundEffect(posX, posY, posZ, "note.harp", .65F, getKind().isHeavy() ? .55F : 1.1F);
    }

    private void particle(double x, double y, double z) {
        if (worldObj instanceof WorldServer) ((WorldServer) worldObj).func_147487_a("reddust", x, y, z, 1, 0, 0, 0, 0);
    }

    private void ring(double x, double y, double z, double radius) {
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI / 10;
            particle(x + radius * Math.cos(angle), y + .2, z + radius * Math.sin(angle));
        }
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

    private void advanceSkill(EntityPlayer target) {
        int ticks = getSkillTicks() + 1;
        skill(getSkillId(), ticks);
        int warning = windup();
        if (ticks < warning) {
            if (ticks % 6 == 0) {
                if (getKind().style == BattleStyle.SEISMIC && getSkillId() == 2) warnLine();
                else ring(targeted() ? aimX : originX, targeted() ? aimY : posY, targeted() ? aimZ : originZ, radius());
            }
            return;
        }
        if (ticks == warning) {
            resolveSkill(target);
            worldObj.playSoundEffect(posX, posY, posZ, "random.explode", .55F, getKind().isHeavy() ? .6F : 1.6F);
        }
        if (ticks >= warning + 16) {
            skill(0, 0);
            cooldown = getKind().isHeavy() ? 100 + rand.nextInt(61) : getKind().hasBossBar() ? 70 : 35;
        }
    }

    private void warnLine() {
        double dx = aimX - originX, dz = aimZ - originZ, length = Math.max(.01, Math.sqrt(dx * dx + dz * dz));
        for (int i = 1; i <= 20; i++) {
            particle(originX + dx / length * i, posY + .15, originZ + dz / length * i);
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

    @SuppressWarnings("unchecked")
    private void resolveSkill(EntityPlayer target) {
        BattleStyle style = getKind().style;
        if (style == BattleStyle.MENDER || (style == BattleStyle.HIVE && getSkillId() == 1)) {
            List<EntityOldEcho> allies = worldObj
                .getEntitiesWithinAABB(EntityOldEcho.class, boundingBox.expand(10, 6, 10));
            for (EntityOldEcho ally : allies) if (ally != this && ally.isEntityAlive()
                && !ally.getKind()
                    .isRitual()
                && ally.getEncounterId()
                    .equals(getEncounterId())) {
                        ally.heal(style == BattleStyle.MENDER ? 8 : 25);
                        if (ally.getAttackTarget() == null) ally.setAttackTarget(target);
                    }
            if (style == BattleStyle.MENDER) {
                heal(4);
                return;
            }
        }
        for (Object object : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) object;
            if (!valid(p) || Math.abs(p.posY - (targeted() ? aimY : posY)) > 5) continue;
            double dx = p.posX - originX, dz = p.posZ - originZ;
            double distance = targeted() ? p.getDistanceSq(aimX, aimY, aimZ) : dx * dx + dz * dz;
            boolean hit = distance < radius() * radius();
            if (style == BattleStyle.SEISMIC && getSkillId() == 2) {
                double ax = aimX - originX, az = aimZ - originZ, len = Math.max(.01, Math.sqrt(ax * ax + az * az));
                double along = (dx * ax + dz * az) / len, across = Math.abs(dx * az - dz * ax) / len;
                hit = along > 0 && along < 20 && across < 2;
            }
            if (style == BattleStyle.SWEEPER || style == BattleStyle.SENTINEL || style == BattleStyle.MIRROR) {
                double a = Math.toRadians(rotationYaw);
                hit &= dx * -Math.sin(a) + dz * Math.cos(a) >= 0;
            }
            if (!hit) continue;
            float damage = getKind().isHeavy() ? 10 : getKind().hasBossBar() ? 7 : 4;
            if (style == BattleStyle.SNIPER) damage = 9;
            if (style == BattleStyle.STALKER) damage = 5;
            double knock = style == BattleStyle.SEISMIC ? .9
                : style == BattleStyle.CHARGER ? .65
                    : style == BattleStyle.SENTINEL || style == BattleStyle.MIRROR ? .5 : 0;
            if (!hurt(p, damage, knock)) continue;
            if (style == BattleStyle.CINDER && canEntityBeSeen(p)) p.setFire(2);
            if ((style == BattleStyle.WEAVER || style == BattleStyle.BELL || style == BattleStyle.RESONATOR)
                && canEntityBeSeen(p))
                p.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, style == BattleStyle.WEAVER ? 1 : 0));
            if (style == BattleStyle.GAZER && canEntityBeSeen(p))
                p.addPotionEffect(new PotionEffect(Potion.weakness.id, 80, 0));
            if (style == BattleStyle.SPIRAL) {
                p.addVelocity(-dz * .06, .1, dx * .06);
                p.velocityChanged = true;
            }
        }
        // Charge is a bounded dash toward the locked point, only through loaded, empty collision space.
        if (style == BattleStyle.CHARGER) {
            double dx = aimX - posX, dz = aimZ - posZ, len = Math.max(.01, Math.sqrt(dx * dx + dz * dz));
            double step = Math.min(4, len), nx = dx / len * step, nz = dz / len * step;
            if (worldObj.getChunkProvider()
                .chunkExists(((int) Math.floor(posX + nx)) >> 4, ((int) Math.floor(posZ + nz)) >> 4)
                && worldObj.getCollidingBoundingBoxes(this, boundingBox.addCoord(nx, 0, nz))
                    .isEmpty())
                moveEntity(nx, 0, nz);
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
        n.setString("echoKind", getKind().code);
        n.setBoolean("echoInitialized", initialized);
        n.setBoolean("echoNight", nightSpawn);
        n.setBoolean("echoDeathRecorded", deathRecorded);
        n.setInteger("echoCooldown", cooldown);
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
        returnTicks = n.getInteger("echoReturn");
        pathFailures = n.getInteger("echoPathFailures");
        ritualTicks = Math.max(0, n.getInteger("echoRitualTicks"));
        skill(Math.max(0, Math.min(2, n.getInteger("echoSkill"))), Math.max(0, n.getInteger("echoSkillTicks")));
        aimX = n.getDouble("echoAimX");
        aimY = n.getDouble("echoAimY");
        aimZ = n.getDouble("echoAimZ");
        originX = n.getDouble("echoOriginX");
        originZ = n.getDouble("echoOriginZ");
    }
}
