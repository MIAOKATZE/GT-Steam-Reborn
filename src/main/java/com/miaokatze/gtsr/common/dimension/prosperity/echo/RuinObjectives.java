package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress;

/** Server-owned authored objectives. Shared site progress and individual exploration evidence are distinct. */
public final class RuinObjectives {

    private RuinObjectives() {}

    public static boolean exploration(int k) {
        return k == 9 || k == 10 || k == 12 || k == 17 || k == 18 || k == 19 || k == 24 || k == 26;
    }

    public static boolean combat(int k) {
        return k == 13 || k == 15 || k == 20 || k == 22 || k == 25;
    }

    private static boolean valid(EntityPlayer p) {
        return p instanceof EntityPlayerMP && !p.worldObj.isRemote
            && p.isEntityAlive()
            && !p.capabilities.isCreativeMode
            && p.worldObj.provider instanceof WorldProviderProsperityRuins;
    }

    public static boolean newSiteAllowed(World world, RuinSite s) {
        if (s.layout < 2) return true;
        for (RuinSite old : RuinsEncounterData.get(world)
            .existingSitesNear(s.x + s.width / 2, s.z + s.depth / 2, 512))
            if (old.layout < 2 && s.overlaps(old, 0)) return false;
        return true;
    }

    public static void initializeOwnedNodes(World w, RuinSite s, List<RuinsBlueprint.Node> nodes, int cx, int cz) {
        if (w.isRemote || s.layout < 2
            || RuinMechanisms.block == null
            || !RuinsEncounterData.get(w)
                .created(s.id(), "geom:" + cx + ":" + cz))
            return;
        RuinsEncounterData d = RuinsEncounterData.get(w);
        for (RuinsBlueprint.Node n : nodes) {
            if (!"CONTROL".equals(n.role) && !"MEMORY".equals(n.role)) continue;
            int x = s.x + n.x, y = s.y + n.y, z = s.z + n.z;
            if ((x >> 4) != cx || (z >> 4) != cz || d.created(s.id(), "control" + n.index)) continue;
            if (!w.isAirBlock(x, y, z) || !w.isAirBlock(x, y + 1, z) || w.isAirBlock(x, y - 1, z)) continue;
            if (!w.setBlock(x, y, z, RuinMechanisms.block, 0, 2)) continue;
            TileEntity tile = w.getTileEntity(x, y, z);
            if (tile instanceof TileRuinMechanism) {
                ((TileRuinMechanism) tile).initialize(s.id(), n);
                d.markCreated(s.id(), "control" + n.index);
            }
        }
    }

    public static void configureChest(TileEntitySealedChest chest, RuinSite s, RuinsBlueprint.Node n) {
        chest.initializeClickUnlock(n.tier, s.id());
        chest.setStoryRelic(com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreSources.chestRelic(s.kind));
    }

    private static boolean allGuards(World w, RuinSite s, int zone) {
        RuinsEncounterData d = RuinsEncounterData.get(w);
        boolean found = false;
        for (RuinsBlueprint.Node n : RuinsBlueprint.nodes(s))
            if ("GUARD".equals(n.role) && (zone < 0 || n.zone == zone)) {
                found = true;
                if (!d.created(s.id(), "entity" + n.index) || !d.dead(s.id(), n.index)) return false;
            }
        return found;
    }

    private static int required(RuinSite s, int zone, String role) {
        int mask = 0;
        for (RuinsBlueprint.Node n : RuinsBlueprint.nodes(s))
            if (role.equals(n.role) && (zone < 0 || zone == n.zone) && n.objectiveIndex >= 0 && n.objectiveIndex < 30)
                mask |= 1 << n.objectiveIndex;
        return mask;
    }

    public static boolean isBossReady(World w, String id) {
        if (id.startsWith("echo:r7:"))
            return com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime.bossReady(w, id);
        RuinSite s = RuinsEncounterData.get(w)
            .site(id);
        if (s == null || s.layout < 2) return true;
        return s.kind < 7 && allGuards(w, s, -1);
    }

    public static boolean isChestReady(World w, String id, int zone) {
        RuinsEncounterData d = RuinsEncounterData.get(w);
        RuinSite s = d.site(id);
        if (s == null) return false;
        if (s.layout < 2) return d.dead(id, 0);
        if (s.kind < 7) {
            if (zone < 0) return d.dead(id, 0);
            int mask = required(s, zone, "CONTROL");
            return allGuards(w, s, zone);
        }
        if (exploration(s.kind)) {
            int mask = required(s, -1, "MEMORY");
            return true;
        }
        if (combat(s.kind)) return allGuards(w, s, -1);
        int mask = required(s, -1, "CONTROL");
        return true;
    }

    public static void onGuardDeath(World w, String id, int node, int zone) {
        if (id.startsWith("echo:r7:")) {
            com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime.death(w, id, node);
            return;
        }
        RuinsEncounterData d = RuinsEncounterData.get(w);
        d.died(id, node);
        RuinSite s = d.site(id);
        if (s != null && s.layout >= 2) {
            int mask = 0;
            for (int i = 0; i < 3; i++) if (allGuards(w, s, i)) mask |= 1 << i;
            d.zoneClearMask(id, mask);
        }
    }

    private static NBTTagCompound personal(EntityPlayer p, String id) {
        net.minecraft.nbt.NBTTagList routes = p.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .getTagList("gtsr.ruinRoutes", 10);
        for (int i = 0; i < routes.tagCount(); i++) {
            NBTTagCompound n = routes.getCompoundTagAt(i);
            if (id.equals(n.getString("site"))) return n;
        }
        NBTTagCompound n = new NBTTagCompound();
        n.setString("site", id);
        return n;
    }

    private static void savePersonal(EntityPlayer p, NBTTagCompound n) {
        NBTTagCompound persisted = p.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        net.minecraft.nbt.NBTTagList old = persisted.getTagList("gtsr.ruinRoutes", 10),
            routes = new net.minecraft.nbt.NBTTagList();
        int start = Math.max(0, old.tagCount() - 15);
        for (int i = start; i < old.tagCount(); i++) {
            NBTTagCompound e = old.getCompoundTagAt(i);
            if (!e.getString("site")
                .equals(n.getString("site"))) routes.appendTag(e);
        }
        routes.appendTag(n);
        persisted.setTag("gtsr.ruinRoutes", routes);
        p.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private static boolean close(EntityPlayer p, int x, int y, int z, double r) {
        return p.getDistanceSq(x + .5, y + .5, z + .5) <= r * r;
    }

    public static void observePlayer(EntityPlayer p, RuinSite s) {
        if (!valid(p) || s.layout < 2) return;
        RuinsEncounterData d = RuinsEncounterData.get(p.worldObj);
        if (d.site(s.id()) == null) return;
        NBTTagCompound personal = personal(p, s.id());
        int mask = personal.getInteger("mask");
        for (RuinsBlueprint.Node n : RuinsBlueprint.nodes(s))
            if ("MEMORY".equals(n.role) && d.created(s.id(), "control" + n.index)
                && close(p, s.x + n.x, s.y + n.y, s.z + n.z, 3)) {
                    int bit = 1 << n.objectiveIndex;
                    // Route landmarks must be visited in authored order by this player.
                    if ((mask & (bit - 1)) == bit - 1 && (mask & bit) == 0) {
                        mask |= bit;
                        p.addChatMessage(
                            new ChatComponentTranslation(
                                "gtsr.ruin.memory." + RuinSite.NAMES[s.kind] + "." + n.objectiveIndex));
                    }
                    d.objectiveMask(s.id(), d.objectiveMask(s.id()) | bit);
                }
        personal.setInteger("mask", mask);
        savePersonal(p, personal);
        if (s.kind < 7 || !isChestReady(p.worldObj, s.id(), 0)) return;
        int needed = required(s, -1, "MEMORY");
        if (exploration(s.kind) && (mask & needed) != needed) return;
        for (RuinsBlueprint.Node n : RuinsBlueprint.nodes(s))
            if ("CHEST".equals(n.role) && d.created(s.id(), "chest" + n.index)
                && close(p, s.x + n.x, s.y + n.y, s.z + n.z, 4)) {
                    HistoryProgress.completeRuin(p, s.kind);
                    return;
                }
    }

    public static void interact(EntityPlayer p, TileRuinMechanism tile) {
        if (!valid(p) || tile.getWorldObj() != p.worldObj
            || !close(p, tile.xCoord, tile.yCoord, tile.zCoord, 6)
            || p.worldObj.getBlock(tile.xCoord, tile.yCoord, tile.zCoord) != RuinMechanisms.block
            || p.worldObj.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) != tile) return;
        RuinsEncounterData d = RuinsEncounterData.get(p.worldObj);
        RuinSite s = d.site(tile.siteId);
        if (s == null || s.layout < 2 || !d.created(s.id(), "control" + tile.nodeIndex)) return;
        RuinsBlueprint.Node authored = null;
        for (RuinsBlueprint.Node n : RuinsBlueprint.nodes(s))
            if (n.index == tile.nodeIndex && n.objectiveIndex == tile.objectiveIndex
                && n.zone == tile.zone
                && n.role.equals(tile.role)
                && s.x + n.x == tile.xCoord
                && s.y + n.y == tile.yCoord
                && s.z + n.z == tile.zCoord) authored = n;
        if (authored == null) return;
        if ("CONTROL".equals(tile.role)) {
            p.addChatMessage(new net.minecraft.util.ChatComponentText("此旧遗迹机关已暂缓；原有宝箱领取记录保留。当前开放三类 Boss 标准场景。"));
            return;
        }
        p.addChatMessage(
            new ChatComponentTranslation(
                ("MEMORY".equals(tile.role) ? "gtsr.ruin.memory." : "gtsr.ruin.clue.") + RuinSite.NAMES[s.kind]
                    + "."
                    + tile.objectiveIndex));
        p.addChatMessage(new ChatComponentTranslation("gtsr.ruin.inspect"));
        if (p.isSneaking()) return;
        if ("MEMORY".equals(tile.role)) {
            observePlayer(p, s);
            return;
        }
    }
}
