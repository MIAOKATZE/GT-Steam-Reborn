package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EncounterNetwork;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EncounterProgress;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntityResidualOathguard;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterData;

/** Reusable short-lived particle guidance to a surviving guardian of this tree. */
public final class ItemOathFragment extends ItemProsperityRelic {

    public ItemOathFragment() {
        super("oath_fragment");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || !(player instanceof EntityPlayerMP)) return stack;
        long now = world.getTotalWorldTime();
        if (player.getEntityData()
            .hasKey("gtsrOathGuideTick")) {
            long elapsed = now - player.getEntityData()
                .getLong("gtsrOathGuideTick");
            if (elapsed >= 0 && elapsed < 20) return stack;
        }
        player.getEntityData()
            .setLong("gtsrOathGuideTick", now);
        String id = EncounterProgress.encounterAt(player);
        if (id == null) {
            player.addChatMessage(new ChatComponentTranslation("encounter.guidance.outside"));
            return stack;
        }
        ForgottenLakeEncounterData data = ForgottenLakeEncounterData.get(world);
        if (data.remaining(id) == 0) {
            player.addChatMessage(new ChatComponentTranslation("encounter.guidance.cleared"));
            return stack;
        }
        List<EntityResidualOathguard> guards = new ArrayList<>();
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof EntityResidualOathguard)) continue;
            EntityResidualOathguard guard = (EntityResidualOathguard) object;
            if (!guard.isDead && guard.getHealth() > 0 && id.equals(guard.getEncounterId())) guards.add(guard);
        }
        guards.sort(Comparator.comparingDouble(player::getDistanceSqToEntity));
        EntityResidualOathguard selected = guards.isEmpty() ? null : guards.get(0);
        for (int i = 0; i < Math.min(3, guards.size()); i++) {
            if (reachable(world, player, guards.get(i))) {
                selected = guards.get(i);
                break;
            }
        }
        if (selected != null) EncounterNetwork.guidance(
            (EntityPlayerMP) player,
            selected.getEntityId(),
            selected.posX,
            selected.posY + selected.height * .6,
            selected.posZ);
        else {
            data.ensureGuardAnchors(world, id);
            double[] anchor = data.nearestLivingAnchor(id, player.posX, player.posY, player.posZ);
            if (anchor != null)
                EncounterNetwork.guidance((EntityPlayerMP) player, -1, anchor[0], anchor[1] + 1.5, anchor[2]);
        }
        return stack;
    }

    private static boolean reachable(World world, EntityPlayer player, EntityResidualOathguard guard) {
        if (player.getDistanceSqToEntity(guard) > 32 * 32) return false;
        int x = MathHelper.floor_double(player.posX), y = MathHelper.floor_double(player.posY),
            z = MathHelper.floor_double(player.posZ);
        int radius = 40;
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++)
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) if (!world.getChunkProvider()
                .chunkExists(cx, cz)) return false;
        ChunkCache cache = new ChunkCache(
            world,
            x - radius,
            y - radius,
            z - radius,
            x + radius,
            y + radius,
            z + radius,
            0);
        PathEntity path = new PathFinder(cache, true, false, true, false).createEntityPathTo(player, guard, 32);
        if (path == null || path.getFinalPathPoint() == null) return false;
        double dx = path.getFinalPathPoint().xCoord + .5 - guard.posX;
        double dy = path.getFinalPathPoint().yCoord - guard.posY;
        double dz = path.getFinalPathPoint().zCoord + .5 - guard.posZ;
        return dx * dx + dy * dy + dz * dz <= 4;
    }
}
