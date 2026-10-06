package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterData;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterOriginalContract;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;
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
            || event.player.ticksExisted % 10 != 0) return;
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        SceneBossSignal signal = new SceneBossSignal();
        if (p.isEntityAlive() && !(p instanceof FakePlayer)
            && p.worldObj.provider instanceof WorldProviderProsperityRuins) {
            String id = encounterAt(p);
            if (id != null) {
                HistoryProgress.sceneEntered(p, id, "forgotten_lake_court");
                signal = nativeScene(p.worldObj, id);
            } else {
                for (RemasterSite site : RemasterData.get(p.worldObj)
                    .inChunk(MathHelper.floor_double(p.posX) >> 4, MathHelper.floor_double(p.posZ) >> 4)) {
                    int kind = "subsided_factory".equals(site.prefab) ? 2
                        : "fallen_foundry".equals(site.prefab) ? 3 : 0;
                    if (kind == 0 || !RemasterOriginalContract.withinGeneratedSite(p, site)) continue;
                    RemasterRuntime.BossStatus boss = RemasterRuntime.bossStatus(p.worldObj, site.id());
                    if (boss == null) continue;
                    signal.kind = kind;
                    signal.site = site.id();
                    signal.code = boss.bossCode;
                    signal.remaining = boss.remainingSpawners;
                    signal.total = boss.totalSpawners;
                    signal.state = boss.state;
                    signal.entity = boss.entityId;
                    signal.revivalTicks = boss.revivalTicks;
                    signal.health = boss.health;
                    signal.maxHealth = boss.maxHealth;
                    break;
                }
            }
        }
        EncounterNetwork.scene(p, signal);
    }

    /** Snapshot the native authority without changing its guard/proximity activation rules. */
    public static SceneBossSignal nativeScene(World world, String id) {
        SceneBossSignal signal = new SceneBossSignal();
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(world);
        signal.kind = 1;
        signal.site = id;
        signal.code = "dc-10";
        int v = d.layoutVersion(id);
        signal.total = v == 1 ? 12 : v == 2 ? 16 : 32;
        signal.remaining = d.remaining(id);
        signal.maxHealth = EntitySilentKing.MAX_HEALTH;
        signal.state = d.kingDead(id) ? 3 : 0;
        for (Object object : world.loadedEntityList) if (object instanceof EntitySilentKing) {
            EntitySilentKing king = (EntitySilentKing) object;
            if (!id.equals(king.getEncounterId()) || king.isDead) continue;
            int state = king.getEncounterState();
            signal.state = d.kingDead(id) ? 3 : state == 0 ? 0 : state == 1 ? 1 : 2;
            signal.entity = king.getEntityId();
            signal.maxHealth = king.getMaxHealth();
            signal.health = signal.state == 0 || signal.state == 3 ? 0 : king.getHealth();
            signal.revivalTicks = signal.state == 1 ? Math.min(208, king.getVisualPhaseTicks()) : 0;
            break;
        }
        return signal;
    }

}
