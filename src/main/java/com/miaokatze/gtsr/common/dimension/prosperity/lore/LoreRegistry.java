package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.AchievementPage;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/** Statistics own chapter access; the persisted flag owns only the first gift. */
public final class LoreRegistry {

    public static final String GIFT_KEY = "gtsr.prosperityHistoryGiven";
    public static ItemProsperityJournal journal;
    public static Achievement silentKingDefeated;
    public static final Map<String, Item> RELICS = new LinkedHashMap<>();
    private static boolean initialized;

    private LoreRegistry() {}

    public static void register() {
        if (initialized) return;
        journal = new ItemProsperityJournal();
        GameRegistry.registerItem(journal, "ProsperityHistory");
        CreativeTabManager.addItemToTab(new ItemStack(journal));
        // A lost chronicle can be rebound; this never grants the achievement or its sealed ending.
        GameRegistry.addShapelessRecipe(new ItemStack(journal), Items.book, Items.gold_nugget);
        for (String id : new String[] { "oath_fragment", "old_crown", "brass_chronicle", "patina_seal", "sigh_glass",
            "mire_memory", "royal_sap", "broken_edict" }) {
            Item relic = id.equals("oath_fragment") ? new ItemOathFragment() : new ItemProsperityRelic(id);
            RELICS.put(id, relic);
            GameRegistry.registerItem(relic, "ProsperityRelic_" + id);
            CreativeTabManager.addItemToTab(new ItemStack(relic));
        }
        for (String id : HistoryProgress.NEW_RELICS) {
            Item relic = new ItemProsperityRelic(id);
            relic.setMaxStackSize(1);
            RELICS.put(id, relic);
            GameRegistry.registerItem(relic, "ProsperityRelic_" + id);
            CreativeTabManager.addItemToTab(new ItemStack(relic));
        }
        silentKingDefeated = new Achievement("achievement.gtsr.silentKing", "gtsr.silentKing", 0, 0, kingCrown(), null)
            .setSpecial()
            .registerStat();
        HistoryProgress.register();
        AchievementPage.registerAchievementPage(new AchievementPage("GTSR · 失落纪年", HistoryProgress.achievements()));
        FMLCommonHandler.instance()
            .bus()
            .register(new LoreRegistry());
        initialized = true;
    }

    public static ItemStack oathFragment() {
        return new ItemStack(RELICS.get("oath_fragment"));
    }

    public static ItemStack kingCrown() {
        return new ItemStack(RELICS.get("old_crown"));
    }

    public static boolean kingChapterUnlocked(EntityPlayer player) {
        return player instanceof EntityPlayerMP && silentKingDefeated != null
            && ((EntityPlayerMP) player).func_147099_x()
                .hasAchievementUnlocked(silentKingDefeated);
    }

    /** Consume the gift marker only after insertion or an owner-tagged drop succeeds. */
    public static boolean giveFirstJournal(EntityPlayer player) {
        if (player.worldObj.isRemote || !(player.worldObj.provider instanceof WorldProviderProsperityRuins))
            return false;
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIFT_KEY)) return false;
        ItemStack gift = new ItemStack(journal);
        if (!player.inventory.addItemStackToInventory(gift)) {
            EntityItem drop = player.dropPlayerItemWithRandomChoice(gift, false);
            if (drop == null || drop.isDead) return false;
            drop.func_145797_a(player.getCommandSenderName());
        }
        persisted.setBoolean(GIFT_KEY, true);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        if (player instanceof EntityPlayerMP) ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
        return true;
    }

    /** Participants get the achievement; the entity's normal death owns the single crown drop. */
    public static void kingDefeated(EntitySilentKing king, DamageSource source, Collection<EntityPlayer> participants) {
        Set<UUID> awarded = new HashSet<>();
        if (source.getEntity() instanceof EntityPlayer) award((EntityPlayer) source.getEntity(), king, awarded);
        for (EntityPlayer player : participants) award(player, king, awarded);
    }

    private static void award(EntityPlayer player, EntitySilentKing king, Set<UUID> awarded) {
        if (!(player instanceof EntityPlayerMP) || player.worldObj != king.worldObj
            || player.capabilities.isCreativeMode
            || !awarded.add(player.getUniqueID())) return;
        if (!kingChapterUnlocked(player)) player.addStat(silentKingDefeated, 1);
        LoreNetwork.send((EntityPlayerMP) player, false);
    }

    @SubscribeEvent
    public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        giveFirstJournal(event.player);
    }

    @SubscribeEvent
    public void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        giveFirstJournal(event.player);
    }

    @SubscribeEvent
    public void respawned(PlayerEvent.PlayerRespawnEvent event) {
        giveFirstJournal(event.player);
    }
}
