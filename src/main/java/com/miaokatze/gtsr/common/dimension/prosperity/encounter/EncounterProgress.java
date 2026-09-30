package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.MegaTreeAnchors;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Server authority for progress and structure membership. */
public final class EncounterProgress {

    public static String encounterAt(EntityPlayer player) {
        if (!(player.worldObj.provider instanceof WorldProviderProsperityRuins)) return null;
        double[] anchor = new double[MegaTreeAnchors.ANCHOR_OUT_LEN];
        if (!MegaTreeAnchors.anchorAt(
            player.worldObj.getSeed(),
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posZ),
            anchor)) return null;
        String id = ForgottenLakeEncounterStructure.encounterId(player.worldObj, (int) anchor[0], (int) anchor[1]);
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(player.worldObj);
        if (!d.known(id)) return null;
        ForgottenLakeEncounterStructure.ensureBounds(player.worldObj, (int) anchor[0], (int) anchor[1]);
        return d.contains(id, player.posX, player.posY, player.posZ) ? id : null;
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
            || event.player.ticksExisted % 20 != 0) return;
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        String id = encounterAt(p);
        boolean active = false;
        int remaining = 0;
        if (id != null) {
            ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(p.worldObj);
            active = d.anyPlatformCleared(id) && !d.kingDead(id);
            if (active) remaining = d.remaining(id);
        }
        EncounterNetwork.progress(p, active, remaining);
    }
}
