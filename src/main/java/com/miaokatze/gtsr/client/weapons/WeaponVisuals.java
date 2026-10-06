package com.miaokatze.gtsr.client.weapons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.client.weapons.outpost.CurvedAimTrajectoryRenderer;
import com.miaokatze.gtsr.client.weapons.outpost.EffectBudgets;
import com.miaokatze.gtsr.client.weapons.outpost.EntityMuzzleFlash;
import com.miaokatze.gtsr.client.weapons.outpost.EntityVisualCasing;
import com.miaokatze.gtsr.client.weapons.outpost.ExplosionEffectFactory;
import com.miaokatze.gtsr.client.weapons.outpost.FxEntityWorldPass;
import com.miaokatze.gtsr.client.weapons.outpost.GatlingFxProfile;
import com.miaokatze.gtsr.client.weapons.outpost.MotorLoopSound;
import com.miaokatze.gtsr.client.weapons.outpost.ProjectileTrailFx;
import com.miaokatze.gtsr.client.weapons.outpost.QlzImpactFx;
import com.miaokatze.gtsr.client.weapons.outpost.RenderVisualCasing;
import com.miaokatze.gtsr.client.weapons.outpost.SmokePuffEntity;
import com.miaokatze.gtsr.client.weapons.outpost.SoundDistancePolicy;
import com.miaokatze.gtsr.common.weapons.Effect;
import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.Snapshot;
import com.miaokatze.gtsr.common.weapons.WeaponKind;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Portable wiring for GT-Outpost (MiaoKatze, AGPL-3.0-or-later) source FX. */
public final class WeaponVisuals {

    private final List<EntityVisualCasing> casings = new ArrayList<>();
    private final List<EntityMuzzleFlash> flashes = new ArrayList<>();

    private static final class Motor {

        final EntityPlayer player;
        final MotorLoopSound sound = new MotorLoopSound();
        long attempt;

        Motor(EntityPlayer player) {
            this.player = player;
        }
    }

    private final Map<Integer, Motor> motors = new LinkedHashMap<>();
    private boolean localMotorAllowed;
    private int localSlot = -1, reloadTicks;
    private boolean overheated;
    private int shotBudget;

    public void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityVisualCasing.class, new RenderVisualCasing());
    }

    void clear() {
        for (Motor motor : motors.values()) stopMotor(motor);
        motors.clear();
        localMotorAllowed = false;
        localSlot = -1;
        for (EntityVisualCasing c : casings) c.setDead();
        for (EntityMuzzleFlash f : flashes) f.setDead();
        casings.clear();
        flashes.clear();
        SmokePuffEntity.resetCounter();
        EffectBudgets.resetAll();
        ProjectileTrailFx.reset();
        QlzImpactFx.reset();
        reloadTicks = 0;
        overheated = false;
    }

    private static void stopMotor(Motor motor) {
        motor.sound.requestStop();
        Minecraft.getMinecraft()
            .getSoundHandler()
            .stopSound(motor.sound);
    }

    void controls(EntityPlayer p, boolean active, boolean firing, WeaponKind kind) {
        boolean slotChanged = localSlot >= 0 && localSlot != p.inventory.currentItem;
        localMotorAllowed = active && kind == WeaponKind.LM12;
        if (!localMotorAllowed || slotChanged) {
            Motor old = motors.remove(p.getEntityId());
            if (old != null) stopMotor(old);
        }
        localSlot = active ? p.inventory.currentItem : -1;
    }

    private void tickMotors() {
        Minecraft mc = Minecraft.getMinecraft();
        Set<Integer> eligible = new HashSet<>();
        for (Object object : mc.theWorld.playerEntities) {
            if (!(object instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) object;
            if (!player.isEntityAlive() || PortableWeapons.kind(player.getHeldItem()) != WeaponKind.LM12
                || PortableWeaponClient.snapshot(player) == null
                || player == mc.thePlayer && !localMotorAllowed) continue;
            double distance = Math.sqrt(mc.thePlayer.getDistanceSqToEntity(player));
            if (distance >= SoundDistancePolicy.range("gatling_motor")) continue;
            int id = player.getEntityId();
            eligible.add(id);
            Motor motor = motors.get(id);
            if (motor != null && motor.player != player) {
                stopMotor(motor);
                motors.remove(id);
                motor = null;
            }
            float omega = Math
                .max(0, PortableWeaponClient.spinAngle(player, 1) - PortableWeaponClient.spinAngle(player, 0));
            if (omega <= GatlingFxProfile.MOTOR_STOP_OMEGA) {
                if (motor != null) motor.sound.beginFadeOut();
                continue;
            }
            long now = mc.theWorld.getTotalWorldTime();
            if (motor != null && (motor.sound.isDonePlaying() || now - motor.attempt >= 5 && !mc.getSoundHandler()
                .isSoundPlaying(motor.sound))) {
                stopMotor(motor);
                motors.remove(id);
                motor = null;
            }
            boolean first = motor == null;
            if (first) {
                if (motors.size() >= 64) {
                    Integer oldest = motors.keySet()
                        .iterator()
                        .next();
                    stopMotor(motors.remove(oldest));
                }
                motor = new Motor(player);
                motors.put(id, motor);
            }
            float gain = SoundDistancePolicy.gain("gatling_motor", distance, GatlingFxProfile.motorVolume(omega));
            // Current GTO DistanceSound policy owns falloff; NONE avoids a second engine attenuation.
            motor.sound.tick(player, GatlingFxProfile.motorPitch(omega), gain);
            if (first) {
                motor.attempt = now;
                mc.getSoundHandler()
                    .playSound(motor.sound);
            }
        }
        Iterator<Map.Entry<Integer, Motor>> it = motors.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Motor> entry = it.next();
            Motor motor = entry.getValue();
            if (!eligible.contains(entry.getKey()) || motor.sound.isDonePlaying()) {
                stopMotor(motor);
                it.remove();
            }
        }
    }

    void state(Snapshot s) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || s.entityId != mc.thePlayer.getEntityId()) return;
        if (PortableWeapons.kind(mc.thePlayer.getHeldItem()) != WeaponKind.fromId(s.kind)) return;
        if (s.reloadTicks > 0 && reloadTicks <= 0)
            sound(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, "magazine_reload", .45F, 1);
        if (s.overheated && !overheated)
            sound(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, "barrel_overheat", .5F, 1);
        reloadTicks = s.reloadTicks;
        overheated = s.overheated;
    }

    void tick() {
        shotBudget = 0;
        casings.removeIf(c -> c.isDead);
        flashes.removeIf(f -> f.isDead);
        Minecraft mc = Minecraft.getMinecraft();
        tickMotors();
        int budget = 64;
        for (Object object : mc.theWorld.loadedEntityList.toArray()) {
            if (budget <= 0) break;
            if (object instanceof EntityWeaponProjectile && !((Entity) object).isDead) {
                ProjectileTrailFx.tick((Entity) object);
                budget--;
            }
        }
    }

    void effect(Effect e, WeaponKind kind) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.type == 2) {
            sound(e.x, e.y, e.z, "reload_done", .4F, 1);
            return;
        }
        if (e.type == 1) {
            if (kind != WeaponKind.LM12) {
                if (kind == WeaponKind.QLZ04) QlzImpactFx.impact(mc.theWorld, e.x, e.y, e.z);
                else ExplosionEffectFactory.spawnExplosion(mc.theWorld, e.x, e.y, e.z, false);
                mc.theWorld.playSound(e.x, e.y, e.z, "random.explode", .7F, 1.2F, false);
            }
            return;
        }
        if (e.type != 0 || shotBudget++ >= 64) return;
        // xyz is the server's shared WeaponPose muzzle, ejectXYZ its measured ejection port.
        if (flashes.size() < 128) {
            EntityMuzzleFlash flash = new EntityMuzzleFlash(mc.theWorld, e.x, e.y, e.z, -e.yaw);
            if (mc.theWorld.spawnEntityInWorld(flash)) flashes.add(flash);
        }
        if (casings.size() < 48) {
            EntityVisualCasing casing = new EntityVisualCasing(mc.theWorld, e.ejectX, e.ejectY, e.ejectZ, -e.yaw, true);
            casing.getEntityData()
                .setBoolean("gtsr.heavyCasing", kind == WeaponKind.T20);
            if (mc.theWorld.spawnEntityInWorld(casing)) casings.add(casing);
        }
        // MachineGunFireFx/MultiMuzzleFireFx: one puff, forward jitter, backsuck, rise and end swell.
        double yaw = Math.toRadians(e.yaw), pitch = Math.toRadians(e.pitch);
        double[] dir = { -Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch) };
        int life = GatlingFxProfile.MUZZLESMOKE_LIFE_MIN + mc.theWorld.rand
            .nextInt(GatlingFxProfile.MUZZLESMOKE_LIFE_MAX - GatlingFxProfile.MUZZLESMOKE_LIFE_MIN + 1);
        if (EffectBudgets.BLAST.acquire(mc.theWorld.getTotalWorldTime(), life)) {
            double[] jitter = new double[3], motion = new double[3];
            for (int i = 0; i < 3; i++) {
                jitter[i] = (mc.theWorld.rand.nextDouble() - .5) * 2 * GatlingFxProfile.MUZZLESMOKE_JITTER;
                motion[i] = (mc.theWorld.rand.nextDouble() - .5) * 2 * GatlingFxProfile.MUZZLESMOKE_DRIFT
                    - dir[i] * GatlingFxProfile.MUZZLESMOKE_BACKSUCK;
            }
            motion[1] += GatlingFxProfile.MUZZLESMOKE_RISE;
            boolean spawned = SmokePuffEntity.spawn(
                mc.theWorld,
                e.x + dir[0] * GatlingFxProfile.MUZZLESMOKE_SPAWN_FORWARD + jitter[0],
                e.y + dir[1] * GatlingFxProfile.MUZZLESMOKE_SPAWN_FORWARD + jitter[1],
                e.z + dir[2] * GatlingFxProfile.MUZZLESMOKE_SPAWN_FORWARD + jitter[2],
                motion[0],
                motion[1],
                motion[2],
                GatlingFxProfile.MUZZLESMOKE_SCALE0,
                GatlingFxProfile.MUZZLESMOKE_SCALE1,
                GatlingFxProfile.MUZZLESMOKE_ALPHA0,
                life,
                GatlingFxProfile.MUZZLESMOKE_R,
                GatlingFxProfile.MUZZLESMOKE_G,
                GatlingFxProfile.MUZZLESMOKE_B,
                GatlingFxProfile.MUZZLESMOKE_SWELL_FACTOR,
                false);
            if (!spawned) EffectBudgets.BLAST.refund(mc.theWorld.getTotalWorldTime(), life);
        }
        sound(
            e.x,
            e.y,
            e.z,
            kind == WeaponKind.LM12 ? "gatling_fire" : kind == WeaponKind.T20 ? "autocannon_fire" : "qlz04_fire",
            1.4F,
            1);
    }

    private static void sound(double x, double y, double z, String key, float volume, float pitch) {
        Minecraft.getMinecraft().theWorld.playSound(x, y, z, "gtsr:weapons." + key, volume, pitch, false);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        FxEntityWorldPass.render(mc.theWorld, e.partialTicks);
        QlzImpactFx.render(mc.theWorld, e.partialTicks);
        if (mc.currentScreen == null && mc.inGameHasFocus && PortableWeaponClient.focusProgress() > .1F) {
            WeaponKind kind = PortableWeapons.kind(mc.thePlayer.getHeldItem());
            if (kind == null) return;
            try (GlScope scope = new GlScope()) {
                org.lwjgl.opengl.GL11.glTranslated(
                    -net.minecraft.client.renderer.entity.RenderManager.renderPosX,
                    -net.minecraft.client.renderer.entity.RenderManager.renderPosY,
                    -net.minecraft.client.renderer.entity.RenderManager.renderPosZ);
                CurvedAimTrajectoryRenderer.draw(mc.thePlayer, kind, e.partialTicks);
            }
        }
    }
}
