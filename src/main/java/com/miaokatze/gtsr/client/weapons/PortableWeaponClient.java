package com.miaokatze.gtsr.client.weapons;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.client.weapons.outpost.GatlingFxProfile;
import com.miaokatze.gtsr.client.weapons.outpost.OutpostHudRings;
import com.miaokatze.gtsr.common.weapons.Effect;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.Snapshot;
import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponNetwork;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Network callbacks only enqueue; all game and GL work runs on the client thread. */
public final class PortableWeaponClient implements WeaponNetwork.ClientSink {

    private static final ArrayBlockingQueue<Object> QUEUE = new ArrayBlockingQueue<>(256);
    private static final Map<Integer, State> STATES = new LinkedHashMap<>();
    private static final KeyBinding RELOAD = new KeyBinding(
        "key.gtsr.weapon.reload",
        Keyboard.KEY_R,
        "key.categories.gtsr.weapons");
    private static final KeyBinding SWITCH_AMMO = new KeyBinding(
        "key.gtsr.weapon.switch",
        Keyboard.KEY_V,
        "key.categories.gtsr.weapons");
    private static final WeaponVisuals VISUALS = new WeaponVisuals();
    private static boolean registered;
    private static Object world;
    private static long clock;
    private static float focus;
    private static float previousFocus;
    private static float renderPartialTicks;
    private static int lastSlot = -1;
    private static boolean lastFire, lastFocus;
    private static WeaponKind lastKind;

    private static final class State {

        Snapshot snapshot;
        long received, shot = -1000;
        int serial = -1;
        float previousSpinAngle, spinAngle;
    }

    public static void register() {
        if (registered) return;
        registered = true;
        PortableWeaponClient client = new PortableWeaponClient();
        ClientRegistry.registerKeyBinding(RELOAD);
        ClientRegistry.registerKeyBinding(SWITCH_AMMO);
        VISUALS.register();
        MinecraftForge.EVENT_BUS.register(client);
        MinecraftForge.EVENT_BUS.register(VISUALS);
        FMLCommonHandler.instance()
            .bus()
            .register(client);
        WeaponNetwork.setClientSink(client);
    }

    @Override
    public void state(Snapshot snapshot) {
        QUEUE.offer(snapshot);
    }

    @Override
    public void effect(Effect effect) {
        QUEUE.offer(effect);
    }

    public static Snapshot snapshot(EntityPlayer player) {
        State state = player == null ? null : STATES.get(player.getEntityId());
        if (state == null || state.snapshot == null) return null;
        WeaponKind held = PortableWeapons.kind(player.getHeldItem());
        return held != null && held.id == state.snapshot.kind ? state.snapshot : null;
    }

    public static float reloadProgress(EntityPlayer player, float partial) {
        State state = player == null ? null : STATES.get(player.getEntityId());
        Snapshot s = snapshot(player);
        if (state == null || s == null || s.reloadTicks <= 0) return 0;
        return clamp(1 - (s.reloadTicks - (clock - state.received) - partial) / Math.max(1F, s.reloadDuration));
    }

    public static float shotAge(EntityPlayer player, float partial) {
        State state = player == null ? null : STATES.get(player.getEntityId());
        Snapshot s = snapshot(player);
        return state == null ? 1000
            : Math.min(clock - state.shot + partial, s == null ? 1000 : s.shotAge + clock - state.received + partial);
    }

    public static float focusProgress() {
        return previousFocus + (focus - previousFocus) * renderPartialTicks;
    }

    public static float renderPartialTicks() {
        return renderPartialTicks;
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) renderPartialTicks = event.renderTickTime;
    }

    public static float spin(EntityPlayer player, float partial) {
        Snapshot s = snapshot(player);
        return s == null ? 0 : clamp(s.spin);
    }

    public static float spinAngle(EntityPlayer player, float partial) {
        State state = player == null ? null : STATES.get(player.getEntityId());
        return state == null ? 0 : state.previousSpinAngle + (state.spinAngle - state.previousSpinAngle) * partial;
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.START) {
            // Vanilla checks heldStack == itemInUse. Inventory NBT sync replaces that stack object.
            // Rebind before the entity tick, retaining remaining time; focus is independent of Use/acks.
            if (mc.thePlayer != null) {
                EntityPlayer p = mc.thePlayer;
                boolean using = mc.theWorld == world && mc.currentScreen == null
                    && mc.inGameHasFocus
                    && p.isEntityAlive()
                    && PortableWeapons.kind(p.getHeldItem()) != null
                    && Mouse.isButtonDown(1);
                if (using && p.getItemInUse() != p.getHeldItem()) {
                    int remaining = p.isUsingItem() ? Math.max(1, p.getItemInUseCount()) : 72000;
                    p.setItemInUse(p.getHeldItem(), remaining);
                } else if (!using && PortableWeapons.kind(p.getItemInUse()) != null) p.stopUsingItem();
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END) return;
        clock++;
        if (world != mc.theWorld) {
            world = mc.theWorld;
            QUEUE.clear();
            STATES.clear();
            VISUALS.clear();
            lastSlot = -1;
            lastKind = null;
            lastFire = lastFocus = false;
            focus = previousFocus = 0;
            OutpostHudRings.reset();
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            VISUALS.clear();
            return;
        }
        int budget = 64;
        Object message;
        while (budget-- > 0 && (message = QUEUE.poll()) != null) {
            if (message instanceof Snapshot) {
                Snapshot s = (Snapshot) message;
                if (WeaponKind.fromId(s.kind) == null || mc.theWorld.getEntityByID(s.entityId) == null) continue;
                State state = getState(s.entityId);
                state.snapshot = s;
                state.received = clock;
                VISUALS.state(s);
            } else {
                Effect e = (Effect) message;
                WeaponKind kind = WeaponKind.fromId(e.kind);
                if (kind == null || mc.thePlayer.getDistanceSq(e.x, e.y, e.z) > 96 * 96) continue;
                if (e.type == 0) {
                    State state = getState(e.entityId);
                    if (e.shotSerial <= state.serial) continue;
                    state.serial = e.shotSerial;
                    state.shot = clock;
                    if (e.entityId == mc.thePlayer.getEntityId()
                        && PortableWeapons.kind(mc.thePlayer.getHeldItem()) == kind) {
                        float kick = kind == WeaponKind.LM12 ? .38F : kind == WeaponKind.T20 ? 1.5F : 2.2F;
                        mc.thePlayer.rotationPitch = Math
                            .max(-90, mc.thePlayer.rotationPitch - kick * (1 - focus * .65F));
                    }
                }
                VISUALS.effect(e, kind);
            }
        }
        Iterator<State> states = STATES.values()
            .iterator();
        while (states.hasNext()) {
            State state = states.next();
            if (clock - Math.max(state.received, state.shot) > 100) {
                states.remove();
                continue;
            }
            state.previousSpinAngle = state.spinAngle;
            if (state.snapshot != null && state.snapshot.kind == WeaponKind.LM12.id)
                state.spinAngle += clamp(state.snapshot.spin) * GatlingFxProfile.MOTOR_OMEGA_MAX;
            if (state.spinAngle >= 360) {
                state.spinAngle -= 360;
                state.previousSpinAngle -= 360;
            }
        }
        EntityPlayer player = mc.thePlayer;
        WeaponKind kind = PortableWeapons.kind(player.getHeldItem());
        boolean active = kind != null && mc.currentScreen == null && mc.inGameHasFocus && player.isEntityAlive();
        boolean firing = active && Mouse.isButtonDown(0);
        boolean focusing = active && Mouse.isButtonDown(1);
        boolean reload = false, switchAmmo = false;
        while (SWITCH_AMMO.isPressed()) switchAmmo |= active;
        while (RELOAD.isPressed()) reload |= active;
        int slot = player.inventory.currentItem;
        if (lastSlot >= 0 && (slot != lastSlot || kind != lastKind || !active)) {
            if (lastFire || lastFocus) WeaponNetwork
                .sendControls(lastSlot, false, false, false, false, player.rotationYaw, player.rotationPitch);
            lastFire = lastFocus = false;
            State local = STATES.get(player.getEntityId());
            if (local != null && (slot != lastSlot || kind != lastKind)) local.snapshot = null;
        }
        // Each active tick carries the current aim, including continuous fire and recoil.
        if (active) WeaponNetwork
            .sendControls(slot, firing, focusing, reload, switchAmmo, player.rotationYaw, player.rotationPitch);
        lastSlot = active ? slot : -1;
        lastKind = kind;
        lastFire = firing;
        lastFocus = focusing;
        previousFocus = focus;
        focus += ((focusing ? 1 : 0) - focus) * .25F;
        if (!active) {
            focus = previousFocus = 0;
            if (kind != null && player.isUsingItem()) player.stopUsingItem();
        }
        VISUALS.controls(player, active, firing, kind);
        VISUALS.tick();
    }

    public static float remoteProgress(EntityPlayer player, float partial) {
        State state = player == null ? null : STATES.get(player.getEntityId());
        Snapshot s = snapshot(player);
        if (state == null || s == null || s.remoteTicks <= 0) return 0;
        return clamp(1 - (s.remoteTicks - (clock - state.received) - partial) / 50F);
    }

    private static boolean hasAlternative(EntityPlayer p, WeaponKind kind) {
        boolean first = false, second = false;
        for (net.minecraft.item.ItemStack stack : p.inventory.mainInventory) {
            if (stack == null) continue;
            if (kind == WeaponKind.SINGULARITY) {
                first |= stack.getItem() instanceof com.miaokatze.gtsr.common.items.SteamEntangledSingularity;
                second |= stack.getItem() instanceof com.miaokatze.gtsr.common.items.CriticalSteamEntangledSingularity;
            } else if (PortableWeapons.ammoKind(stack) == kind) {
                if (stack.getItem() == PortableWeapons.mimicAmmo[kind.id]) second = true;
                else first = true;
            }
        }
        return first && second;
    }

    private static State getState(int id) {
        State state = STATES.get(id);
        if (state == null) {
            if (STATES.size() >= 128) STATES.remove(
                STATES.keySet()
                    .iterator()
                    .next());
            state = new State();
            STATES.put(id, state);
        }
        return state;
    }

    @SubscribeEvent
    public void mouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.button == 0 && mc.thePlayer != null
            && mc.currentScreen == null
            && PortableWeapons.kind(mc.thePlayer.getHeldItem()) != null) {
            event.setCanceled(true);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        }
    }

    @SubscribeEvent
    public void fov(FOVUpdateEvent event) {
        if (event.entity == Minecraft.getMinecraft().thePlayer) event.newfov *= 1 - focusProgress() * .22F;
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
        if (mc.thePlayer == null || mc.currentScreen != null
            || PortableWeapons.kind(mc.thePlayer.getHeldItem()) == null) {
            OutpostHudRings.reset();
            return;
        }
        event.setCanceled(true);
        int x = event.resolution.getScaledWidth() / 2, y = event.resolution.getScaledHeight() / 2;
        Snapshot s = snapshot(mc.thePlayer);
        try (GlScope scope = new GlScope()) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            float gap = 5 + 8 * (1 - focusProgress());
            WeaponKind held = PortableWeapons.kind(mc.thePlayer.getHeldItem());
            if (held == WeaponKind.QLZ04 || held == WeaponKind.SINGULARITY) {
                int spacing = held == WeaponKind.SINGULARITY ? 14 : 9;
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(.35F, .95F, .8F, .95F);
                GL11.glLineWidth(2F * event.resolution.getScaleFactor());
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(x - 5, y);
                GL11.glVertex2f(x + 5, y);
                for (int i = 1; i <= 4; i++) {
                    int d = i * spacing, w = 5 + i * 3;
                    GL11.glVertex2f(x - w, y + d - 4);
                    GL11.glVertex2f(x - w + 3, y + d);
                    GL11.glVertex2f(x - w + 3, y + d);
                    GL11.glVertex2f(x + w - 3, y + d);
                    GL11.glVertex2f(x + w - 3, y + d);
                    GL11.glVertex2f(x + w, y + d - 4);
                }
                GL11.glEnd();
            } else for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                for (int p = 0; p < 7; p++) {
                    int px = x + (int) Math.round(Math.cos(a) * (gap + p));
                    int py = y + (int) Math.round(Math.sin(a) * (gap + p));
                    Gui.drawRect(px, py, px + 2, py + 2, 0xEF59F2CC);
                }
            }
            State state = STATES.get(mc.thePlayer.getEntityId());
            float cooldown = s == null || state == null ? 0
                : Math.max(0, s.shotCooldown - (clock - state.received) - event.partialTicks);
            float rate = s == null ? 1 : 1 - cooldown / Math.max(1, s.shotInterval);
            OutpostHudRings.draw(
                s == null ? "unknown" : WeaponKind.fromId(s.kind).modelKey,
                x + 28,
                y - 28,
                rate,
                s == null ? 0 : s.heat);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            String ammo = s == null ? "-- / --" : s.magazine + " / " + s.reserve;
            mc.fontRenderer.drawStringWithShadow(
                ammo,
                x - mc.fontRenderer.getStringWidth(ammo) / 2,
                y + (held == WeaponKind.SINGULARITY ? 70 : 48),
                0xD6FFEE);
            if (s != null) {
                String selected = StatCollector.translateToLocal(
                    "gtsr.weapon.ammo."
                        + (held == WeaponKind.SINGULARITY ? (s.ammoType == 1 ? "critical" : "singularity")
                            : (s.ammoType == 1 ? "mimic" : "portable")));
                if (hasAlternative(mc.thePlayer, held)) selected += "  " + StatCollector.translateToLocalFormatted(
                    "gtsr.weapon.switch_hint",
                    Keyboard.getKeyName(SWITCH_AMMO.getKeyCode()));
                mc.fontRenderer.drawStringWithShadow(
                    selected,
                    x - mc.fontRenderer.getStringWidth(selected) / 2,
                    y + (held == WeaponKind.SINGULARITY ? 94 : 72),
                    s.ammoType == 1 ? 0xD7ACFF : 0xD6FFEE);
            }
            if (s != null && (s.overheated || s.reloadTicks > 0)) {
                String text = StatCollector
                    .translateToLocal(s.overheated ? "gtsr.weapon.overheated" : "gtsr.weapon.reloading");
                mc.fontRenderer.drawStringWithShadow(
                    text,
                    x - mc.fontRenderer.getStringWidth(text) / 2,
                    y + (held == WeaponKind.SINGULARITY ? 82 : 60),
                    s.overheated ? 0xFF7040 : 0xFFECAA);
            }
        }
    }

}
