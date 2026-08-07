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
import com.miaokatze.gtsr.common.network.GTSRFXNet;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/* ==== [DEBUG-FX] 动画运行调试日志（用户实机检验后删除） ==== */

/**
 * 客户端 tick：驱动奇点动画（吸积盘/电弧/光束/辉光/消散）
 */
@SideOnly(Side.CLIENT)
public class SingularityClientFXHandler {

    private final Map<TileRunawaySingularity, Integer> arcCooldowns = new HashMap<TileRunawaySingularity, Integer>();
    /** 每奇点 3 束光束（不同宽度），实例防重复生成 */
    private final Map<TileRunawaySingularity, GTSRBeamFX[]> beams = new HashMap<TileRunawaySingularity, GTSRBeamFX[]>();
    /** 上一帧见过的奇点 TE → 坐标 hash（消散检测；TE 引用失效时触发一次消散） */
    private final Map<TileRunawaySingularity, Integer> lastSeen = new HashMap<TileRunawaySingularity, Integer>();
    private World lastWorld;

    private long diskAccum;
    private long arcAccum;
    private long glowAccum;
    private long beamAccum;

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
            // 切换维度/世界：清空旧世界状态，避免跨世界误触发消散与残留光束
            for (GTSRBeamFX[] beamGroup : this.beams.values()) {
                for (GTSRBeamFX beam : beamGroup) {
                    beam.setDead();
                }
            }
            this.beams.clear();
            this.arcCooldowns.clear();
            this.lastSeen.clear();
            this.lastWorld = world;
        }
        Set<TileRunawaySingularity> prev = new HashSet<TileRunawaySingularity>(this.lastSeen.keySet());
        Set<TileRunawaySingularity> current = new HashSet<TileRunawaySingularity>();
        int activeSingularities = 0;
        for (Object obj : world.loadedTileEntityList) {
            if (obj == null || !(obj instanceof TileRunawaySingularity)) {
                continue;
            }
            TileRunawaySingularity t = (TileRunawaySingularity) obj;
            if (t.isInvalid()) {
                continue;
            }
            activeSingularities++;
            current.add(t);
            this.lastSeen.put(t, (t.xCoord & 0xFFFF) << 16 | (t.zCoord & 0xFFFF));
            double effRange = t.getRange() * t.getActiveFactor();
            double cx = t.xCoord + 0.5D;
            double cy = t.yCoord + 0.5D;
            double cz = t.zCoord + 0.5D;
            // 吸积盘（vanilla 传统管道）：概率生成
            if (world.rand.nextFloat() < 0.25F + 0.1F * (float) Math.min(1.0D, effRange / 32.0D)) {
                GTSRSingularityFX.spawnDisk(world, cx, cy, cz, effRange);
                this.diskAccum++;
            }
            // 电弧：×3 频率 + 外向（中心→边缘）
            Integer cached = this.arcCooldowns.get(t);
            int cooldown = cached == null ? 3 + world.rand.nextInt(3) : cached.intValue();
            if (cooldown <= 0) {
                double yaw = world.rand.nextDouble() * 2.0D * Math.PI;
                double pitch = (world.rand.nextDouble() - 0.5D) * Math.PI * 0.8D;
                double ex = cx + Math.cos(pitch) * Math.cos(yaw) * effRange;
                double ey = cy + Math.sin(pitch) * effRange + (world.rand.nextDouble() - 0.5D) * effRange * 0.4D;
                double ez = cz + Math.cos(pitch) * Math.sin(yaw) * effRange;
                GTSRFXEngine.spawnArc(world, cx, cy, cz, ex, ey, ez, 0.08F, 7, 10, 0.5F, 8); // 起点=中心，终点=边缘（外向）
                this.arcAccum++;
                cooldown = 5 + world.rand.nextInt(4); // 5~8 tick ≈ 原 15~24 的 1/3
            }
            this.arcCooldowns.put(t, Integer.valueOf(cooldown - 1));
            // 光束：每奇点 3 束不同宽度，长度 = 光效边界×2.5；任一 dead 整组重建
            float glowRadius = Math.min(4.0F, 1.0F + (float) effRange * 0.09F);
            GTSRBeamFX[] beamGroup = this.beams.get(t);
            boolean beamOk = beamGroup != null;
            if (beamOk) {
                for (GTSRBeamFX b : beamGroup) {
                    if (b == null || b.isDead) {
                        beamOk = false;
                        break;
                    }
                }
            }
            if (!beamOk) {
                beamGroup = new GTSRBeamFX[3];
                for (int i = 0; i < 3; i++) {
                    beamGroup[i] = GTSRBeamFX
                        .add(world, cx, cy, cz, glowRadius * 2.5F, 0.45F + i * 0.3F + world.rand.nextFloat() * 0.2F);
                    this.beamAccum++;
                }
                this.beams.put(t, beamGroup);
            }
            // 辉光：灰白微青 + 半径加大 + 呼吸已减弱（GTSRGlowFX 内部改）
            if (!GTSRFXEngine.instance()
                .hasGlowNear(cx, cy, cz, 1.0D)) {
                GTSRGlowFX.spawn(world, cx, cy, cz, glowRadius, 0.85F, 0.9F, 1.0F, 40);
                this.glowAccum++;
            }
        }
        // 消散检测：上帧活跃但本帧消失的奇点 → 灰白闪光 + 外扩粒子 + 外向电弧爆发
        for (TileRunawaySingularity gone : prev) {
            if (current.contains(gone)) {
                continue;
            }
            this.lastSeen.remove(gone);
            this.arcCooldowns.remove(gone);
            GTSRBeamFX[] goneBeams = this.beams.remove(gone);
            if (goneBeams != null) {
                for (GTSRBeamFX b : goneBeams) {
                    b.setDead();
                }
            }
            double tx = gone.xCoord + 0.5D;
            double ty = gone.yCoord + 0.5D;
            double tz = gone.zCoord + 0.5D;
            GTSRSingularityFX.spawnBurst(world, tx, ty, tz); // 外扩粒子
            GTSRGlowFX.spawn(world, tx, ty, tz, 4.5F, 0.9F, 0.93F, 1.0F, 15); // 闪光
            for (int i = 0; i < 2; i++) { // 2 条外向电弧爆发
                double yaw = world.rand.nextDouble() * 2.0D * Math.PI;
                double pitch = (world.rand.nextDouble() - 0.5D) * Math.PI * 0.8D;
                double ex = tx + Math.cos(pitch) * Math.cos(yaw) * 8.0D;
                double ey = ty + Math.sin(pitch) * 8.0D;
                double ez = tz + Math.cos(pitch) * Math.sin(yaw) * 8.0D;
                GTSRFXEngine.spawnArc(world, tx, ty, tz, ex, ey, ez, 0.1F, 7, 8, 0.6F, 10);
            }
        }
        // 清理：dead 光束条目
        Iterator<Map.Entry<TileRunawaySingularity, GTSRBeamFX[]>> beamIt = this.beams.entrySet()
            .iterator();
        while (beamIt.hasNext()) {
            GTSRBeamFX[] group = beamIt.next()
                .getValue();
            boolean anyDead = false;
            for (GTSRBeamFX b : group) {
                if (b == null || b.isDead) {
                    anyDead = true;
                    break;
                }
            }
            if (anyDead) {
                beamIt.remove();
            }
        }
        if (this.arcCooldowns.size() > 64) {
            this.arcCooldowns.clear(); // 清理失效奇点条目
        }
        if (world.getTotalWorldTime() % 400 == 0) {
            GTSteamReborn.LOG.info(
                "[GTSRFX] t=" + world.getTotalWorldTime()
                    + " active="
                    + activeSingularities
                    + " particles=total:"
                    + GTSRFXEngine.instance()
                        .totalParticles()
                    + ":"
                    + GTSRFXEngine.instance()
                        .particleCount(0)
                    + ":"
                    + GTSRFXEngine.instance()
                        .particleCount(2)
                    + " disk="
                    + this.diskAccum
                    + " arc="
                    + this.arcAccum
                    + " glow="
                    + this.glowAccum
                    + " beam="
                    + this.beamAccum
                    + " absorb="
                    + GTSRFXNet.absorbCount
                    + " rate="
                    + String.format(
                        "%.1f",
                        (double) (this.diskAccum + this.arcAccum
                            + this.glowAccum
                            + this.beamAccum
                            + GTSRFXNet.absorbCount) / 400.0D)); // DEBUG-FX:
            this.diskAccum = 0;
            this.arcAccum = 0;
            this.glowAccum = 0;
            this.beamAccum = 0;
            GTSRFXNet.absorbCount = 0;
        }
    }
}
