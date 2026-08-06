package com.miaokatze.gtsr.common.tick;

import com.miaokatze.gtsr.common.blocks.GTSRSingularityFX;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端世界 tick：为奇点方块生成粒子与中心光晕
 */
@SideOnly(Side.CLIENT)
public class SingularityClientFXHandler {

    private static int budget;

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!event.world.isRemote) {
            return;
        }
        for (Object obj : event.world.loadedTileEntityList) {
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
            double effRange = t.getRange() * t.getActiveFactor();
            double cx = t.xCoord + 0.5D;
            double cy = t.yCoord + 0.5D;
            double cz = t.zCoord + 0.5D;

            int n = Math.max(1, Math.min(6, 2 + (int) Math.floor(effRange * 0.2D)));
            for (int i = 0; i < n; i++) {
                event.world.spawnEntityInWorld(new GTSRSingularityFX(event.world, cx, cy, cz, effRange));
                budget++;
            }

            if (event.world.getTotalWorldTime() % 8 == 0) {
                event.world.spawnEntityInWorld(new GTSRSingularityFX(event.world, cx, cy, cz, 1.0D));
                budget++;
            }
        }
        budget = 0; // 每 tick 结束后重置预算
    }
}
