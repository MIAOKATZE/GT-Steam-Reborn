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
    /** 每奇点 2 片锥形光片（数组，独立随机方位与旋转轴），实例防重复生成 */
    private final Map<TileRunawaySingularity, GTSRBeamFX[]> beams = new HashMap<TileRunawaySingularity, GTSRBeamFX[]>();
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
            for (GTSRBeamFX[] beamGroup : this.beams.values()) {
                for (GTSRBeamFX beam : beamGroup) {
                    beam.setDead();
                }
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
            float[] rgb = t.getColorRGB();
            this.lastSeen.put(t, (t.xCoord & 0xFFFF) << 16 | (t.zCoord & 0xFFFF));
            double af = t.getActiveFactor();
            float darkScale = 0.05F + 0.95F * (float) af;
            double effRange = t.getRange() * af;
            double fxR = t.getFxRadius() * af; // 光效半径随活性系数收缩
            double cx = t.xCoord + 0.5D;
            double cy = t.yCoord + 0.5D;
            double cz = t.zCoord + 0.5D;
            // 距离裁剪（对齐原渲染侧 64/100 格裁剪；粒子管道无渲染侧裁剪，改在生成侧限制）：
            // 玩家超过 100 格不维护该奇点 FX（仅记录 lastSeen，防消散误判）
            float playerDist = 10000.0F;
            if (Minecraft.getMinecraft().thePlayer != null) {
                playerDist = (float) Minecraft.getMinecraft().thePlayer.getDistance(cx, cy, cz);
            }
            if (playerDist > 100.0F) {
                continue;
            }
            // 吸积盘（vanilla 传统管道）：概率生成 × 活性系数（af→0 概率趋零，消散时不再中心冒泡），随活性系数变暗。
            // 概率公式基于 effRange（吸收范围）不变；生成半径取光效半径 fxR。
            // nullplus（attributeId=-3）：跳过吸积盘（无粒子），光片/辉光保留
            double diskP = (0.55D + 0.15D * Math.min(1.0D, effRange / 32.0D)) * af;
            if (t.getAttributeId() != TileRunawaySingularity.ATTRIBUTE_NULL_PLUS && world.rand.nextFloat() < diskP) {
                GTSRSingularityFX.spawnDisk(world, cx, cy, cz, fxR, darkScale, t.getDuration(), t.getElapsedTicks());
            }
            // 电弧：外向（中心→边缘），频率中位数 + 大幅波动；一次可多条（1~3 条、方向均匀间隔）。
            // onlypull（-2）与 nullplus（-3）：无吸收行为，跳过电弧；光片/辉光保留（onlypull 仍表现牵引动画，nullplus 为纯静置）
            if (t.getAttributeId() != TileRunawaySingularity.ATTRIBUTE_ONLY_PULL
                && t.getAttributeId() != TileRunawaySingularity.ATTRIBUTE_NULL_PLUS) {
                Integer cached = this.arcCooldowns.get(t);
                int cooldown = cached == null ? 3 + world.rand.nextInt(12) : cached.intValue();
                if (cooldown <= 0) {
                    int count = 1 + world.rand.nextInt(3); // 1~3 条
                    double baseYaw = world.rand.nextDouble() * 2.0D * Math.PI;
                    for (int i = 0; i < count; i++) {
                        double yaw = baseYaw + (double) i * 2.0D * Math.PI / (double) count
                            + (world.rand.nextDouble() - 0.5D) * 0.4D;
                        double pitch = (world.rand.nextDouble() - 0.5D) * Math.PI * 0.8D;
                        double ex = cx + Math.cos(pitch) * Math.cos(yaw) * effRange;
                        double ey = cy + Math.sin(pitch) * effRange
                            + (world.rand.nextDouble() - 0.5D) * effRange * 0.4D;
                        double ez = cz + Math.cos(pitch) * Math.sin(yaw) * effRange;
                        GTSRFXEngine.spawnArc(world, cx, cy, cz, ex, ey, ez, 0.08F, 7, 10, 0.5F, 8, darkScale); // 起点=中心，终点=边缘（外向）
                    }
                    cooldown = 4 + world.rand.nextInt(12); // 4~15 tick
                }
                this.arcCooldowns.put(t, Integer.valueOf(cooldown - 1));
            }
            // 光束：每奇点 2 片锥形光片（独立随机初始方位与旋转轴），长度 = 光效半径 × af × 200%，随活性系数收缩变暗。
            // 基准半径取光效半径本身（不含 af），af 收缩在 beamLen/glowR 公式中应用一次，避免二次收缩
            float glowRadius = (float) t.getFxRadius();
            float beamLen = glowRadius * (float) af * 2.0F;
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
                beamGroup = new GTSRBeamFX[2];
                for (int i = 0; i < 2; i++) {
                    beamGroup[i] = GTSRBeamFX.add(
                        world,
                        cx,
                        cy,
                        cz,
                        beamLen,
                        0.5F,
                        0.02618F,
                        0.55F,
                        0.85F * rgb[0],
                        0.92F * rgb[1],
                        1.0F * rgb[2],
                        1.0F,
                        10000);
                }
                this.beams.put(t, beamGroup);
            }
            for (GTSRBeamFX b : beamGroup) {
                b.updateParams(beamLen, darkScale);
                // 颜色实时同步：客户端 TE 颜色初始可能为默认 white（NBT 同步延迟），每 tick 用最新颜色更新
                b.updateColor(rgb[0], rgb[1], rgb[2]);
            }
            // 辉光：TC4 节点式多层光晕常驻，半径 = 光效半径 × 100%，随活性系数收缩变暗
            float glowR = glowRadius * (float) af;
            GTSRGlowFX glow = this.glows.get(t);
            if (glow == null || glow.isDead()) {
                glow = GTSRGlowFX.spawn(world, cx, cy, cz, glowR, 0.85F * rgb[0], 0.9F * rgb[1], 1.0F * rgb[2], 10000);
                this.glows.put(t, glow);
            }
            glow.updateParams(glowR, darkScale);
            // 颜色实时同步（同光束：NBT 同步延迟期间初始为 white，到达后立即生效）
            glow.updateColor(rgb[0], rgb[1], rgb[2]);
        }
        // 消散检测：上帧活跃但本帧消失的奇点 → 特效随衰减系数收缩、变暗直至消失（无亮闪爆发）
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
        // 清理：dead 光束（任一片死亡整组重建）/ 辉光条目
        Iterator<Map.Entry<TileRunawaySingularity, GTSRBeamFX[]>> beamIt = this.beams.entrySet()
            .iterator();
        while (beamIt.hasNext()) {
            GTSRBeamFX[] beamGroup = beamIt.next()
                .getValue();
            boolean anyDead = false;
            if (beamGroup != null) {
                for (GTSRBeamFX b : beamGroup) {
                    if (b == null || b.isDead) {
                        anyDead = true;
                        break;
                    }
                }
            }
            if (anyDead) {
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
