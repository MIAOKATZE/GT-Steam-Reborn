package com.miaokatze.gtsr.common.tick;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.GTSRSingularityFX;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.fx.GTSRFXEngine;
import com.miaokatze.gtsr.common.fx.GTSRGlowFX;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/* ==== [DEBUG-FX] 动画运行调试日志（用户实机检验后删除） ==== */

/**
 * 客户端 tick：为失控奇点生成吸积环粒子、电弧与中心辉光
 */
@SideOnly(Side.CLIENT)
public class SingularityClientFXHandler {

    private final Map<TileRunawaySingularity, Integer> arcCooldowns = new HashMap<TileRunawaySingularity, Integer>();

    private long diskAccum;
    private long arcAccum;
    private long glowAccum;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return;
        }
        boolean overBudget = GTSRFXEngine.instance()
            .totalParticles() > 400;
        int activeSingularities = 0;
        int diskThisTick = 0;
        int arcThisTick = 0;
        int glowThisTick = 0;
        for (Object obj : world.loadedTileEntityList) {
            if (obj == null || !(obj instanceof TileRunawaySingularity)) {
                continue;
            }
            TileRunawaySingularity t = (TileRunawaySingularity) obj;
            if (t.isInvalid()) {
                continue;
            }
            activeSingularities++;
            double effRange = t.getRange() * t.getActiveFactor();
            double cx = t.xCoord + 0.5D;
            double cy = t.yCoord + 0.5D;
            double cz = t.zCoord + 0.5D;

            if (!overBudget) {
                // 吸积环：概率生成
                if (world.rand.nextFloat() < 0.3F + 0.1F * (float) Math.min(1.0D, effRange / 32.0D)) {
                    GTSRFXEngine.spawnParticle(new GTSRSingularityFX(world, cx, cy, cz, effRange));
                    diskThisTick++;
                }
                // 电弧：独立冷却
                Integer cached = this.arcCooldowns.get(t);
                int cooldown = cached == null ? 10 + world.rand.nextInt(15) : cached.intValue();
                if (cooldown <= 0) {
                    double yaw = world.rand.nextDouble() * 2.0D * Math.PI;
                    double pitch = (world.rand.nextDouble() - 0.5D) * Math.PI;
                    double sx = cx + Math.cos(pitch) * Math.cos(yaw) * effRange;
                    double sy = cy + Math.sin(pitch) * effRange + (world.rand.nextDouble() - 0.5D) * effRange * 0.6D;
                    double sz = cz + Math.cos(pitch) * Math.sin(yaw) * effRange;
                    GTSRFXEngine.spawnArc(world, sx, sy, sz, cx, cy, cz, 0.08F, 7, 10, 0.5F, 8);
                    arcThisTick++;
                    cooldown = 15 + world.rand.nextInt(10);
                }
                this.arcCooldowns.put(t, Integer.valueOf(cooldown - 1));
            }
            // 中心辉光：无辉光才刷新
            float factor = (float) t.getActiveFactor();
            float radius = Math.min(2.5F, 0.5F + (float) effRange * 0.06F);
            if (!GTSRFXEngine.instance()
                .hasGlowNear(cx, cy, cz, 1.0D)) {
                GTSRGlowFX.spawn(world, cx, cy, cz, radius, 1.0F, 1.0F - 0.6F * factor, 1.0F, 40);
                glowThisTick++;
            }
        }
        if (this.arcCooldowns.size() > 64) {
            this.arcCooldowns.clear(); // 清理失效奇点条目
        }
        this.diskAccum += diskThisTick;
        this.arcAccum += arcThisTick;
        this.glowAccum += glowThisTick;
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
                    + " rate="
                    + (this.diskAccum + this.arcAccum + this.glowAccum) / 400); // DEBUG-FX:
            this.diskAccum = 0;
            this.arcAccum = 0;
            this.glowAccum = 0;
        }
    }
}
