package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoDeathEvent;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinsEncounterData;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinsSitePlanner;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** One owner registered on Forge and FML buses; server events alone establish history evidence. */
public final class HistoryEvents {

    public static final String GIFT_KEY = "gtsr.skyCourtFoilGiven";
    private static final String RECORD_KEY = "gtsr.skyCourtWitnessRecorded";
    private static final int MAX_TRACKED_BOSSES = 256;
    private static final int MAX_PARTICIPANTS = 64;
    private final Map<UUID, CombatRecord> combat = new LinkedHashMap<UUID, CombatRecord>();

    private static final class CombatRecord {

        final int dimension;
        final Set<UUID> players = new HashSet<UUID>();

        CombatRecord(int dimension) {
            this.dimension = dimension;
        }
    }

    private static boolean prosperity(World world) {
        return world instanceof WorldServer && !world.isRemote
            && world.provider instanceof WorldProviderProsperityRuins;
    }

    private static boolean valid(EntityPlayer player, World world) {
        return player instanceof EntityPlayerMP && player.worldObj == world
            && prosperity(world)
            && player.isEntityAlive()
            && !player.capabilities.isCreativeMode;
    }

    /** LOWEST and default receiveCanceled=false exclude already cancelled damage requests. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void hurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.ammount <= 0
            || !(event.entityLiving instanceof EntityOldEcho)
            || !(event.source.getEntity() instanceof EntityPlayerMP)) return;
        EntityOldEcho echo = (EntityOldEcho) event.entityLiving;
        EntityPlayerMP player = (EntityPlayerMP) event.source.getEntity();
        if (!echo.isEntityAlive() || echo.isNightSpawn()
            || !echo.getKind()
                .hasBossBar()
            || !valid(player, echo.worldObj)) return;
        CombatRecord record = combat.get(echo.getUniqueID());
        if (record == null) {
            // Bounded transient bookkeeping: very distant, oldest fights lose participation first.
            if (combat.size() >= MAX_TRACKED_BOSSES) combat.remove(
                combat.keySet()
                    .iterator()
                    .next());
            record = new CombatRecord(echo.dimension);
            combat.put(echo.getUniqueID(), record);
        }
        if (record.players.size() < MAX_PARTICIPANTS) record.players.add(player.getUniqueID());
    }

    /** This event follows accepted onDeath; a cancelled LivingDeathEvent never reaches this handler. */
    @SubscribeEvent
    public void death(EchoDeathEvent event) {
        EntityOldEcho echo = event.entity;
        CombatRecord record = combat.remove(echo.getUniqueID());
        if (!prosperity(echo.worldObj) || echo.isNightSpawn()
            || !echo.getKind()
                .hasBossBar())
            return;
        Set<UUID> participants = new HashSet<UUID>();
        if (record != null) participants.addAll(record.players);
        if (event.source.getEntity() instanceof EntityPlayerMP) {
            EntityPlayerMP killer = (EntityPlayerMP) event.source.getEntity();
            if (valid(killer, echo.worldObj)) participants.add(killer.getUniqueID());
        }
        for (Object object : echo.worldObj.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (valid(player, echo.worldObj) && participants.contains(player.getUniqueID())) {
                HistoryProgress.bossDefeated(player, echo.getKind());
            }
        }
    }

    /** FML pickup fires after inventory insertion; Forge's earlier cancellable pickup is not evidence. */
    @SubscribeEvent
    public void pickedUp(PlayerEvent.ItemPickupEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.player.worldObj.isRemote
            || !event.player.isEntityAlive()
            || event.player.capabilities.isCreativeMode
            || event.pickedUp == null) return;
        ItemStack picked = event.pickedUp.getEntityItem();
        if (picked == null || picked.getItem() == null) return;
        Item item = picked.getItem();
        String id = null;
        for (Map.Entry<String, Item> entry : LoreRegistry.RELICS.entrySet()) {
            if (entry.getValue() == item) {
                id = entry.getKey();
                break;
            }
        }
        if (id == null) return;
        // ALLOW handlers can signal pickup without inserting anything. Verify a positive held stack.
        for (ItemStack held : event.player.inventory.mainInventory) {
            if (held != null && held.stackSize > 0
                && held.getItem() == item
                && ItemStack.areItemStackTagsEqual(held, picked)) {
                HistoryProgress.observeRelic(event.player, id);
                int cacheBit = witnessedCacheBit(id, held);
                if (cacheBit >= 0) {
                    // Carried, authentic cache evidence can be read elsewhere; visits stay dimension-scoped.
                    if ((HistoryProgress.snapshot(event.player) & (1 << cacheBit)) == 0)
                        HistoryProgress.event(event.player, cacheBit);
                    if (prosperity(event.player.worldObj)) HistoryProgress.visit(event.player, cacheBit + 19);
                }
                return;
            }
        }
    }

    /** Strict provenance: the bit alone, an unrelated relic, or a malformed cache origin never grants evidence. */
    public static int witnessedCacheBit(String relicId, ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return -1;
        NBTTagCompound proof = stack.getTagCompound();
        if (!proof.hasKey("gtsr.storyCache", 3) || !proof.hasKey("gtsr.storyOrigin", 8)) return -1;
        int bit = proof.getInteger("gtsr.storyCache");
        String type;
        if (bit == 8 && "mire_sounding_weight".equals(relicId)) type = "mire";
        else if (bit == 9 && "sealed_archive_tube".equals(relicId)) type = "archive";
        else if (bit == 10 && "rail_repair_token".equals(relicId)) type = "rail";
        else return -1;
        String[] origin = proof.getString("gtsr.storyOrigin")
            .split(":", -1);
        if (origin.length != 5 || !"cache".equals(origin[0]) || !type.equals(origin[1])) return -1;
        try {
            Long.parseLong(origin[2]);
            Integer.parseInt(origin[3]);
            Integer.parseInt(origin[4]);
        } catch (NumberFormatException malformed) {
            return -1;
        }
        return bit;
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !prosperity(event.world) || event.world.getTotalWorldTime() % 20 != 0)
            return;
        WorldServer world = (WorldServer) event.world;
        Set<UUID> aliveBosses = new HashSet<UUID>();
        List<EntityOldEcho> apparitions = new ArrayList<EntityOldEcho>();
        for (Object object : world.loadedEntityList) {
            if (object instanceof EntityOldEcho) {
                EntityOldEcho echo = (EntityOldEcho) object;
                if (echo.isEntityAlive() && echo.getKind()
                    .hasBossBar()) aliveBosses.add(echo.getUniqueID());
                if (echo.isEntityAlive() && echo.getKind()
                    .isRitual()) apparitions.add(echo);
            }
        }
        // Loaded encounter participation is transient; unloading/retiring cannot leave an unbounded UUID ledger.
        Iterator<Map.Entry<UUID, CombatRecord>> iterator = combat.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CombatRecord> entry = iterator.next();
            if (entry.getValue().dimension == world.provider.dimensionId && !aliveBosses.contains(entry.getKey()))
                iterator.remove();
        }
        for (Object object : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            // Entry presentation accepts real creative players; inventory and combat evidence do not.
            if (!(player instanceof EntityPlayerMP) || player.worldObj != world
                || !player.isEntityAlive()
                || player instanceof net.minecraftforge.common.util.FakePlayer) continue;
            if (valid(player, world)) observeInventory(player);
            int cx = ((int) Math.floor(player.posX)) >> 4, cz = ((int) Math.floor(player.posZ)) >> 4;
            if (!world.getChunkProvider()
                .chunkExists(cx, cz)) continue;
            for (com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite site : com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterData
                .get(world)
                .inChunk(cx, cz))
                com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterOriginalContract
                    .observePlayer(player, site);
            if (!valid(player, world)) continue;
            RuinsEncounterData data = RuinsEncounterData.get(world);
            for (RuinSite site : RuinsSitePlanner.near(world.getSeed(), cx, cz)) {
                if (data.created(site.id(), "geom:" + cx + ":" + cz) && withinSite(player, site)) {
                    HistoryProgress.visit(player, site.kind);
                    com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinObjectives.observePlayer(player, site);
                }
            }
            // Gifts can append an EntityItem to loadedEntityList; use the earlier apparition snapshot.
            for (EntityOldEcho echo : apparitions) witnessRitual((EntityPlayerMP) player, echo);
        }
    }

    /** Container transfers do not emit ground-item pickup events; inspect actually held witnesses once a second. */
    public static void observeInventory(EntityPlayer player) {
        if (!valid(player, player.worldObj)) return;
        for (ItemStack held : player.inventory.mainInventory) {
            if (held == null || held.stackSize <= 0) continue;
            for (Map.Entry<String, Item> entry : LoreRegistry.RELICS.entrySet()) {
                if (held.getItem() == entry.getValue()) {
                    HistoryProgress.observeRelic(player, entry.getKey());
                    break;
                }
            }
        }
    }

    /** Footprint plus bounded authored height range; underground factory paths are included. */
    public static boolean withinSite(EntityPlayer player, RuinSite site) {
        return player.posX >= site.x && player.posX < site.x + site.width
            && player.posZ >= site.z
            && player.posZ < site.z + site.depth
            && player.posY >= site.y - (site.kind == 1 ? 24 : 8)
            && player.posY <= site.y + 48;
    }

    private static boolean loadedRay(World world, double ax, double az, double bx, double bz) {
        int minX = ((int) Math.floor(Math.min(ax, bx))) >> 4, maxX = ((int) Math.floor(Math.max(ax, bx))) >> 4;
        int minZ = ((int) Math.floor(Math.min(az, bz))) >> 4, maxZ = ((int) Math.floor(Math.max(az, bz))) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!world.getChunkProvider()
                .chunkExists(x, z)) return false;
        }
        return true;
    }

    /** Inventory/drop acceptance precedes gift persistence, which precedes achievement permission. */
    public static boolean witnessRitual(EntityPlayerMP player, EntityOldEcho apparition) {
        if (apparition == null || !valid(player, apparition.worldObj)
            || !apparition.isEntityAlive()
            || !apparition.getKind()
                .isRitual()
            || player.getDistanceSqToEntity(apparition) > 128 * 128
            || !loadedRay(player.worldObj, player.posX, player.posZ, apparition.posX, apparition.posZ)
            || !player.canEntityBeSeen(apparition)) return false;
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIFT_KEY)) {
            // Recover a crash between the saved gift and its event. Once recorded, repeated viewing sends nothing.
            if (!persisted.getBoolean(RECORD_KEY)) finishWitness(player, persisted);
            return false;
        }
        Item foil = LoreRegistry.RELICS.get("sky_ritual_foil");
        if (foil == null) return false;
        ItemStack gift = new ItemStack(foil);
        if (!player.inventory.addItemStackToInventory(gift)) {
            EntityItem drop = new EntityItem(player.worldObj, player.posX, player.posY + .3, player.posZ, gift);
            drop.delayBeforeCanPickup = 10;
            drop.func_145797_a(player.getCommandSenderName());
            drop.func_145799_b(player.getCommandSenderName());
            if (!player.worldObj.spawnEntityInWorld(drop) || drop.isDead
                || (!player.worldObj.loadedEntityList.contains(drop)
                    && player.worldObj.getEntityByID(drop.getEntityId()) != drop))
                return false;
        } else {
            player.inventoryContainer.detectAndSendChanges();
        }
        persisted.setBoolean(GIFT_KEY, true);
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        finishWitness(player, persisted);
        return true;
    }

    private static void finishWitness(EntityPlayerMP player, NBTTagCompound persisted) {
        HistoryProgress.event(player, 7);
        if ((HistoryProgress.snapshot(player) & (1 << 7)) != 0) {
            persisted.setBoolean(RECORD_KEY, true);
            player.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
    }

    @SubscribeEvent
    public void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.player.getUniqueID();
        Iterator<CombatRecord> iterator = combat.values()
            .iterator();
        while (iterator.hasNext()) {
            CombatRecord record = iterator.next();
            record.players.remove(player);
            if (record.players.isEmpty()) iterator.remove();
        }
    }

    @SubscribeEvent
    public void unloaded(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        Iterator<CombatRecord> iterator = combat.values()
            .iterator();
        while (iterator.hasNext()) if (iterator.next().dimension == event.world.provider.dimensionId) iterator.remove();
    }
}
