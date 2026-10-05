package com.miaokatze.gtsr.client.encounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing;

/** Watched cast counters produce tick-owned notices; rendering never restarts their lifetime. */
public final class BossSkillVisuals {

    private static WorldClient world;
    private static final Map<UUID, Casts> casts = new HashMap<>();
    private static final int LIFETIME = 60;

    private static final class Notice {

        final int skill;
        int age;

        Notice(int skill) {
            this.skill = skill;
        }
    }

    private static final class Casts {

        final int[] kingCounters = new int[5];
        int serial;
        boolean seen;
        final List<Notice> notices = new ArrayList<>();
    }

    private BossSkillVisuals() {}

    public static void clear() {
        world = null;
        casts.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld) {
            casts.clear();
            world = mc.theWorld;
        }
        if (world == null || mc.thePlayer == null) {
            clear();
            return;
        }
        if (mc.isGamePaused()) return;
        for (Casts c : casts.values()) {
            c.seen = false;
            Iterator<Notice> it = c.notices.iterator();
            while (it.hasNext()) if (++it.next().age >= LIFETIME) it.remove();
        }
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof Entity)) continue;
            Entity e = (Entity) object;
            boolean king = e instanceof EntitySilentKing;
            if (!king && !(e instanceof EntityOldEcho && ((EntityOldEcho) e).getKind()
                .hasBossBar())) continue;
            if (e.isDead || mc.thePlayer.getDistanceSqToEntity(e) > 128 * 128) continue;
            boolean active = king ? ((EntitySilentKing) e).getEncounterState() == 2
                : ((EntityOldEcho) e).getEncounterState() == EntityOldEcho.COMBAT;
            if (!active) continue;
            Casts c = casts.get(e.getUniqueID());
            boolean fresh = c == null;
            if (fresh) {
                if (casts.size() >= 32) continue;
                c = new Casts();
                casts.put(e.getUniqueID(), c);
            }
            c.seen = true;
            if (e instanceof EntityOldEcho && ((EntityOldEcho) e).hasReinforcedBones()) {
                // Watched duration keeps this armor shell alive for the full server immunity window.
                for (int i = 0; i < 4; i++) {
                    double angle = e.ticksExisted * .14 + i * Math.PI / 2;
                    world.spawnParticle(
                        "crit",
                        e.posX + Math.cos(angle) * (e.width * .65),
                        e.posY + e.height * (.25 + i * .16),
                        e.posZ + Math.sin(angle) * (e.width * .65),
                        0,
                        .025,
                        0);
                }
            }
            if (king) {
                EntitySilentKing k = (EntitySilentKing) e;
                for (int i = 0; i < 5; i++) {
                    int counter = k.getSkillCastSerial(i + 1);
                    if (!fresh && counter != c.kingCounters[i]) add(c, i + 1);
                    c.kingCounters[i] = counter;
                }
                if (fresh && k.getSkillId() != 0) add(c, k.getSkillId());
                if (fresh && k.getMeteorTicks() >= 0) add(c, 3);
            } else {
                EntityOldEcho echo = (EntityOldEcho) e;
                int serial = echo.getSkillAnnouncementSerial();
                if (!fresh && serial != c.serial) {
                    add(c, echo.getAnnouncedSkill());
                    if (echo.getKind() == com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.DC08) add(c, 8);
                }
                if (fresh && echo.getSkillId() != 0) add(c, echo.getSkillId());
                c.serial = serial;
            }
        }
        Iterator<Casts> it = casts.values()
            .iterator();
        while (it.hasNext()) if (!it.next().seen) it.remove();
    }

    private static void add(Casts c, int skill) {
        if (skill < 1 || skill > 8) return;
        // A repeated cast jumps again, while distinct simultaneous royal skills remain separate.
        Iterator<Notice> it = c.notices.iterator();
        while (it.hasNext()) if (it.next().skill == skill) it.remove();
        if (c.notices.size() >= 5) c.notices.remove(0);
        c.notices.add(new Notice(skill));
    }

    public static int recentMask(Entity entity) {
        Casts c = casts.get(entity.getUniqueID());
        int mask = 0;
        if (c != null) for (Notice n : c.notices) if (n.age < 24) mask |= 1 << (n.skill - 1);
        return mask;
    }

    /** Labels sit beside the bar when possible, below it on narrow GUI scales. */
    public static int[] position(int screenWidth, int barX, int barY, int textWidth, int index, int age) {
        int width = Math.min(Math.max(0, textWidth), Math.max(0, screenWidth - 8));
        int x = barX - width - 10, y = barY + 12 + index * 14;
        if (x < 4) {
            x = Math.max(4, (screenWidth - width) / 2);
            y = barY + 47 + index * 14;
        }
        int bounce = age < 12 ? -(int) Math.round(Math.sin(Math.PI * Math.max(0, age) / 12) * 6) : 0;
        return new int[] { Math.max(4, Math.min(x, Math.max(4, screenWidth - width - 4))), y + bounce, width };
    }

    public static void draw(Entity entity, int screenWidth, int barX, int barY) {
        if (entity == null || world != Minecraft.getMinecraft().theWorld) return;
        Casts c = casts.get(entity.getUniqueID());
        if (c == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        String code = entity instanceof EntitySilentKing ? "dc-10" : ((EntityOldEcho) entity).getKind().code;
        int index = 0;
        for (Notice n : c.notices) {
            String key = "echo.skill." + code + "." + n.skill;
            String name = StatCollector.translateToLocal(key);
            if (name.equals(key)) name = StatCollector.translateToLocal("echo.warning." + code);
            String text = "§l" + name + "§r";
            text = mc.fontRenderer.trimStringToWidth(text, Math.max(1, screenWidth - 12));
            int[] p = position(screenWidth, barX, barY, mc.fontRenderer.getStringWidth(text), index++, n.age);
            net.minecraft.client.gui.Gui.drawRect(p[0] - 2, p[1] - 2, p[0] + p[2] + 2, p[1] + 11, 0xD018151F);
            mc.fontRenderer
                .drawStringWithShadow(text, p[0], p[1], entity instanceof EntitySilentKing ? 0xFFE39B : 0xF0C5FF);
        }
    }
}
