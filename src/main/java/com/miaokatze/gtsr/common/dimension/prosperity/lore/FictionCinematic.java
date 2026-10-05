package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterData;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Reading and collection create a world presentation, never a fight or a block-changing ritual. */
public final class FictionCinematic {

    public static final String[] REQUIRED = { "future_pressure_witness", "future_recovery_witness",
        "future_weft_witness", "future_canal_witness", "future_lens_witness", "future_oath_witness",
        "future_ash_witness", "future_sound_witness", "future_road_witness", "future_anchor_witness",
        "future_city_address_witness", "future_city_return_witness", "foundry_heart_fragment", "hive_memory_knot",
        "old_crown" };

    private static boolean server(World world) {
        return world instanceof WorldServer && !world.isRemote
            && world.provider instanceof WorldProviderProsperityRuins;
    }

    public static List<String> missing(EntityPlayer player) {
        List<String> result = new ArrayList<>();
        for (String id : REQUIRED) {
            HistoryProgress.verifyFictionRelic(player, id);
            if (!HistoryProgress.hasRelicEvidence(player, id)) result.add(id);
        }
        return result;
    }

    /** Deposits are server-authoritative, consume exactly one, and remain in the world ledger. */
    public static boolean deposit(EntityPlayer player,
        com.miaokatze.gtsr.common.dimension.prosperity.remaster.TileRemasterNode tile,
        com.google.gson.JsonObject node) {
        if (!server(player.worldObj) || !(player instanceof EntityPlayerMP)
            || player instanceof FakePlayer
            || !player.isEntityAlive()
            || tile.getWorldObj() != player.worldObj
            || player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) > 64
            || RemasterRuntime.node(tile) == null
            || !tile.nodeId.startsWith("witness-")) return false;
        node = RemasterRuntime.node(tile);
        RemasterSite owner = RemasterData.get(player.worldObj)
            .site(tile.siteId);
        if (owner == null || !"fiction_expansion_project".equals(owner.prefab)) return false;
        String relic = RemasterRuntime.string(node, "relicId", "");
        if (!java.util.Arrays.asList(REQUIRED)
            .contains(relic)) return false;
        RemasterData data = RemasterData.get(player.worldObj);
        if (data.flag(tile.siteId, "witness:" + tile.nodeId)) {
            player.addChatMessage(new ChatComponentTranslation("gtsr.fiction.pedestal_filled"));
            return true;
        }
        net.minecraft.item.ItemStack held = player.getHeldItem();
        if (held == null || held.stackSize <= 0 || held.getItem() != LoreRegistry.RELICS.get(relic)) {
            requirement(player, relic, "gtsr.fiction.pedestal_requires");
            return true;
        }
        HistoryProgress.verifyFictionRelic(player, relic);
        if (!HistoryProgress.hasRelicEvidence(player, relic)) return false;
        // No removable inventory or reward exists: the stored copy is proof of the one consumed offering.
        net.minecraft.nbt.NBTTagCompound offering = new net.minecraft.nbt.NBTTagCompound();
        net.minecraft.item.ItemStack deposited = held.copy();
        deposited.stackSize = 1;
        deposited.writeToNBT(offering);
        data.state(tile.siteId)
            .setTag("offering:" + tile.nodeId, offering);
        data.flag(tile.siteId, "witness:" + tile.nodeId, true);
        held.stackSize--;
        if (held.stackSize == 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
        player.inventory.markDirty();
        ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
        tile.refresh();
        player.addChatMessage(new ChatComponentTranslation("gtsr.fiction.pedestal_deposited"));
        return true;
    }

    private static void requirement(EntityPlayer player, String relic, String key) {
        ProsperityAchievements.Definition definition = ProsperityAchievements.CATALOG.get("relic." + relic);
        String source = definition == null ? "unknown" : definition.source;
        player.addChatMessage(
            new ChatComponentTranslation(
                key,
                new ChatComponentTranslation("item.prosperity." + relic + ".name"),
                new ChatComponentTranslation("lore.entry.structures." + source + ".title")));
    }

    public static List<String> missingPedestals(World world, RemasterSite site) {
        List<String> missing = new ArrayList<>();
        RemasterData data = RemasterData.get(world);
        for (int i = 0; i < REQUIRED.length; i++)
            if (!data.flag(site.id(), "witness:witness-" + i)) missing.add(REQUIRED[i]);
        return missing;
    }

    /** Prefab maxY is a local coordinate, not an extent; site.y is its persisted world anchor. */
    public static int demonstrationY(RemasterSite site) {
        return site.y + site.plan().max[1] + 20;
    }

    /** Runtime must first prove the real generated reading tile and persist its story-read flag. */
    public static boolean read(EntityPlayerMP player, RemasterSite site, String nodeId) {
        if (site == null || !"fiction_expansion_project".equals(site.prefab)
            || !server(player.worldObj)
            || player instanceof FakePlayer
            || !player.isEntityAlive()
            || !("core-story-reading".equals(nodeId) || "single-archive-submit".equals(nodeId))) return false;
        World world = player.worldObj;
        RemasterData remaster = RemasterData.get(world);
        if (remaster.site(site.id()) == null || !remaster.flag(site.id(), "story-read:" + nodeId)) return false;
        int[] reading = null, center = null;
        for (com.google.gson.JsonObject node : RemasterRuntime.nodes(site)) {
            String id = RemasterRuntime.string(node, "id", "");
            if (nodeId.equals(id)) reading = RemasterRuntime.nodePosition(world, site, node);
            if ("core-story-reading".equals(id)) center = RemasterRuntime.nodePosition(world, site, node);
        }
        if (reading == null || center == null
            || player.getDistanceSq(reading[0] + .5, reading[1] + .5, reading[2] + .5) > 64) return false;
        if ("core-story-reading".equals(nodeId))
            player.addChatMessage(new ChatComponentTranslation("gtsr.fiction.reading"));
        FictionTimeline timeline = FictionTimeline.get(world);
        if (!timeline.worldCompleted()) {
            if (timeline.sites.containsKey(site.id())) return false;
            for (NBTTagCompound prior : timeline.sites.values()) if (!prior.getBoolean("complete")) return false;
        }
        List<String> missing = timeline.worldCompleted() ? missing(player) : missingPedestals(world, site);
        if (!missing.isEmpty() || !HistoryProgress.hasSceneStage(player, site.id(), "read:core-story-reading")) {
            player.addChatMessage(
                new ChatComponentTranslation("gtsr.fiction.requirements", 15 - missing.size())
                    .setChatStyle(new net.minecraft.util.ChatStyle().setBold(true)));
            for (String id : missing) {
                ProsperityAchievements.Definition definition = ProsperityAchievements.CATALOG.get("relic." + id);
                String source = definition == null ? "unknown" : definition.source;
                player.addChatMessage(
                    new ChatComponentTranslation(
                        "gtsr.fiction.missing",
                        new ChatComponentTranslation("item.prosperity." + id + ".name"),
                        new ChatComponentTranslation("lore.entry.structures." + source + ".title"))
                            .setChatStyle(new net.minecraft.util.ChatStyle().setBold(true)));
            }
            return false;
        }
        if (timeline.worldCompleted()) {
            HistoryProgress.fictionArchived(player);
            HistoryProgress.fictionFinished(player);
            player.addChatMessage(
                new ChatComponentTranslation("gtsr.fiction.world_completed")
                    .setChatStyle(new net.minecraft.util.ChatStyle().setBold(true)));
            return false;
        }
        NBTTagCompound record = new NBTTagCompound();
        record.setString("id", site.id());
        record.setLong("start", world.getTotalWorldTime());
        record.setLong("startWall", System.currentTimeMillis());
        record.setInteger("x", center[0]);
        record.setInteger("y", demonstrationY(site));
        record.setInteger("z", center[2]);
        record.setString(
            "reader",
            player.getUniqueID()
                .toString());
        timeline.sites.put(site.id(), record);
        timeline.markDirty();
        HistoryProgress.fictionArchived(player);
        sync(world, timeline, 1);
        return true;
    }

    private static EntityOldEcho entity(World world, String id) {
        for (Object raw : world.loadedEntityList) if (raw instanceof EntityOldEcho) {
            EntityOldEcho echo = (EntityOldEcho) raw;
            if (echo.getKind() == EchoKind.DO02 && id.equals(echo.getEncounterId()) && !echo.isDead) return echo;
        }
        return null;
    }

    private static void sync(World world, FictionTimeline data, int phase) {
        for (Object raw : world.playerEntities) if (raw instanceof EntityPlayerMP)
            FictionNetwork.send((EntityPlayerMP) raw, phase, world.getTotalWorldTime());
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (event.phase != TickEvent.Phase.END || !server(world)) return;
        FictionTimeline timeline = FictionTimeline.get(world);
        int phase = timeline.blue() ? 4 : 0;
        for (NBTTagCompound record : timeline.sites.values()) {
            String id = record.getString("id");
            // Old saves can contain a later pending site after a completed world; never revive that presentation.
            if (timeline.worldCompleted()) {
                if (!record.getBoolean("complete")) {
                    record.setBoolean("complete", true);
                    record.setInteger("elapsed", 440);
                    timeline.markDirty();
                }
                EntityOldEcho remaining = entity(world, id);
                if (remaining != null) remaining.setDead();
                continue;
            }
            int elapsed = FictionTimeline.elapsed(record, world.getTotalWorldTime(), System.currentTimeMillis());
            record.setInteger("elapsed", elapsed);
            timeline.markDirty();
            EntityOldEcho echo = entity(world, id);
            if (elapsed >= 440) {
                record.setBoolean("complete", true);
                if (echo != null) echo.setDead();
                phase = Math.max(phase, 4);
                for (Object raw : world.playerEntities) if (raw instanceof EntityPlayerMP) {
                    EntityPlayerMP player = (EntityPlayerMP) raw;
                    if (player.getUniqueID()
                        .toString()
                        .equals(record.getString("reader"))) HistoryProgress.fictionFinished(player);
                }
                sync(world, timeline, 4);
                continue;
            }
            phase = FictionTimeline.phase(elapsed);
            int x = record.getInteger("x"), y = record.getInteger("y"), z = record.getInteger("z");
            if (world.getChunkProvider()
                .chunkExists(x >> 4, z >> 4)) {
                if (echo == null) {
                    echo = new EntityOldEcho(world);
                    echo.initializeEcho(EchoKind.DO02, id, x + .5, y, z + .5, false);
                    echo.restoreDemonstrationTimeline(elapsed);
                    world.spawnEntityInWorld(echo);
                } else echo.restoreDemonstrationTimeline(elapsed);
            }
        }
        if (world.getTotalWorldTime() % 20 == 0) sync(world, timeline, phase);
        // Recover the completion award after reconnect; it never supplies items or replays the entity.
        for (NBTTagCompound record : timeline.sites.values()) if (record.getBoolean("complete"))
            for (Object raw : world.playerEntities) if (raw instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) raw;
                if (player.getUniqueID()
                    .toString()
                    .equals(record.getString("reader"))) HistoryProgress.fictionFinished(player);
            }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (!server(event.world)) return;
        FictionTimeline timeline = FictionTimeline.get(event.world);
        // Persist the remaining presentation. Offline wall time reaches blue without replaying the birth.
        for (NBTTagCompound record : timeline.sites.values()) {
            record.setInteger(
                "elapsed",
                FictionTimeline.elapsed(record, event.world.getTotalWorldTime(), System.currentTimeMillis()));
            EntityOldEcho echo = entity(event.world, record.getString("id"));
            if (echo != null) echo.setDead();
        }
        timeline.markDirty();
    }
}
