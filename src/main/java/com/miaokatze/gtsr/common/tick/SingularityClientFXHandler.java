package com.miaokatze.gtsr.common.tick;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.GTSRSingularityFX;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/* ==== [DEBUG] 奇点调试日志（用户实机检验后删除） ==== */

/**
 * 客户端每帧 tick：为奇点方块生成粒子与中心光晕
 */
@SideOnly(Side.CLIENT)
public class SingularityClientFXHandler {

    private static int budget;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return;
        }
        int activeSingularities = 0;
        int spawnedThisTick = 0;
        for (Object obj : world.loadedTileEntityList) {
            if (budget > 80) {
                return;
            }
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

            int n = Math.max(1, Math.min(6, 2 + (int) Math.floor(effRange * 0.2D)));
            for (int i = 0; i < n; i++) {
                Minecraft.getMinecraft().effectRenderer.addEffect(new GTSRSingularityFX(world, cx, cy, cz, effRange));
                budget++;
                spawnedThisTick++;
            }

            if (world.getTotalWorldTime() % 8 == 0) {
                Minecraft.getMinecraft().effectRenderer.addEffect(new GTSRSingularityFX(world, cx, cy, cz, 1.0D));
                budget++;
                spawnedThisTick++;
            }
        }
        if (world.getTotalWorldTime() % 400 == 0) {
            GTSteamReborn.LOG.info(
                "[Singularity] client: activeSingularities=" + activeSingularities
                    + " spawnedThisTick="
                    + spawnedThisTick); // DEBUG-SINGULARITY:
        }
        budget = 0; // 每 tick 结束后重置预算
    }
}
