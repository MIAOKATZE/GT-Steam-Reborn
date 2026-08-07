package com.miaokatze.gtsr.common.tick;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.GTSRSingularityFX;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.fx.GTSRBeamFX;
import com.miaokatze.gtsr.common.fx.GTSRFXEngine;
import com.miaokatze.gtsr.common.fx.GTSRGlowFX;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端 tick：驱动奇点动画（吸积盘/电弧/光束/辉光/消散）
 */
@SideOnly(Side.CLIENT)
public class SingularityClientFXHandler {

    private final Map<TileRunawaySingularity, Integer> arcCooldowns = new HashMap<TileRunawaySingularity, Integer>();
    /** 每奇点 1 束探照灯式竖光片，实例防重复生成 */
    private final Map<TileRunawaySingularity, GTSRBeamFX> beams = new HashMap<TileRunawaySingularity, GTSRBeamFX>();
    /** 每奇点 1 个常驻辉光，实例防重复生成 */
    private final Map<TileRunawaySingularity, GTSRGlowFX> glows = new HashMap<TileRunawaySingularity, GTSRGlowFX>();
    /** 上一帧见过的奇点 TE → 坐标 hash（消散检测；TE 引用失效时触发一次消散） */
    private final Map<TileRunawaySingularity, Integer> lastSeen = new HashMap<TileRunawaySingularity, Integer>();
    private World lastWorld;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return;
        }
        if (world != this.lastWorld) {
            // 切换维度/世界：清空旧世界状态，避免跨世界误触发消散与残留特效
            for (GTSRBeamFX beam : this.beams.values()) {
                beam.setDead();
            }
            this.beams.clear();
            for (GTSRGlowFX glow : this.glows.values()) {
                glow.setDead();
            }
            this.glows.clear();
            this.arcCooldowns.clear();
            this.lastSeen.clear();
            this.lastWorld = world;
        }
        Set<TileRunawaySingularity> prev = new HashSet<TileRunawaySingularity>(this.lastSeen.keySet());
        Set<TileRunawaySingularity> current = new HashSet<TileRunawaySingularity>();
        for (Object obj : world.loadedTileEntityList) {
            if (obj == null || !(obj instanceof TileRunawaySingularity)) {
                continue;
            }
            TileRunawaySingularity t = (TileRunawaySingularity) obj;
            if (t.isInvalid()) {
                continue;
            }
            current.add(t);
            this.lastSeen.put(t, (t.xCoord & 0xFFFF) << 16 | (t.zCoord & 0xFFFF));
            double af = t.getActiveFactor();
            float darkScale = 0.15F + 0.85F * (float) af;
            double effRange = t.getRange() * af;
            double cx = t.xCoord + 0.5D;
            double cy = t.yCoord + 0.5D;
            double cz = t.zCoord + 0.5D;
            // 吸积盘（vanilla 传统管道）：概率生成，随活性系数变暗
            if (world.rand.nextFloat() < 0.55F + 0.15F * (float) Math.min(1.0D, effRange / 32.0D)) {
                GTSRSingularityFX.spawnDisk(world, cx, cy, cz, effRange, darkScale);
            }
            // 电弧：外向（中心→边缘），随活性系数变暗
            Integer cached = this.arcCooldowns.get(t);
            int cooldown = cached == null ? 3 + world.rand.nextInt(3) : cached.intValue();
            if (cooldown <= 0) {
                double yaw = world.rand.nextDouble() * 2.0D * Math.PI;
                double pitch = (world.rand.nextDouble() - 0.5D) * Math.PI * 0.8D;
                double ex = cx + Math.cos(pitch) * Math.cos(yaw) * effRange;
                double ey = cy + Math.sin(pitch) * effRange + (world.rand.nextDouble() - 0.5D) * effRange * 0.4D;
                double ez = cz + Math.cos(pitch) * Math.sin(yaw) * effRange;
                GTSRFXEngine.spawnArc(world, cx, cy, cz, ex, ey, ez, 0.08F, 7, 10, 0.5F, 8, darkScale); // 起点=中心，终点=边缘（外向）
                cooldown = 5 + world.rand.nextInt(4); // 5~8 tick
            }
            this.arcCooldowns.put(t, Integer.valueOf(cooldown - 1));
            // 光束：每奇点 1 片探照灯竖光片，长度 = 光效半径 × 150%，随活性系数收缩变暗
            float glowRadius = Math.min(4.0F, 1.0F + (float) effRange * 0.09F);
            float beamLen = glowRadius * (float) af * 1.5F;
            GTSRBeamFX beam = this.beams.get(t);
            if (beam == null || beam.isDead) {
                beam = GTSRBeamFX.add(world, cx, cy, cz, beamLen, 0.5F);
                this.beams.put(t, beam);
            }
            beam.updateParams(beamLen, darkScale);
            // 辉光：灰白微青常驻，半径与暗度随活性系数
            GTSRGlowFX glow = this.glows.get(t);
            if (glow == null || glow.isDead()) {
                glow = GTSRGlowFX.spawn(world, cx, cy, cz, glowRadius, 0.85F, 0.9F, 1.0F, 10000);
                this.glows.put(t, glow);
            }
            glow.updateParams(glowRadius * (float) af, darkScale);
        }
        // 消散检测：上帧活跃但本帧消失的奇点 → 特效随衰减系数收缩、变暗直至消失（无亮闪爆发）
        for (TileRunawaySingularity gone : prev) {
            if (current.contains(gone)) {
                continue;
            }
            this.lastSeen.remove(gone);
            this.arcCooldowns.remove(gone);
            GTSRBeamFX goneBeam = this.beams.remove(gone);
            if (goneBeam != null) {
                goneBeam.setDead();
            }
            GTSRGlowFX goneGlow = this.glows.remove(gone);
            if (goneGlow != null) {
                goneGlow.setDead();
            }
            double tx = gone.xCoord + 0.5D;
            double ty = gone.yCoord + 0.5D;
            double tz = gone.zCoord + 0.5D;
            // 暗色收缩过渡辉光：颜色接近黑、随 shrinkPerTick 收缩、随寿命渐出
            GTSRGlowFX fade = GTSRGlowFX.spawn(world, tx, ty, tz, 1.0F, 0.1F, 0.1F, 0.12F, 10);
            fade.setShrinkPerTick(0.1F);
        }
        // 清理：dead 光束 / 辉光条目
        Iterator<Map.Entry<TileRunawaySingularity, GTSRBeamFX>> beamIt = this.beams.entrySet()
            .iterator();
        while (beamIt.hasNext()) {
            GTSRBeamFX beam = beamIt.next()
                .getValue();
            if (beam == null || beam.isDead) {
                beamIt.remove();
            }
        }
        Iterator<Map.Entry<TileRunawaySingularity, GTSRGlowFX>> glowIt = this.glows.entrySet()
            .iterator();
        while (glowIt.hasNext()) {
            GTSRGlowFX glow = glowIt.next()
                .getValue();
            if (glow == null || glow.isDead()) {
                glowIt.remove();
            }
        }
        if (this.arcCooldowns.size() > 64) {
            this.arcCooldowns.clear(); // 清理失效奇点条目
        }
    }
}
