package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.stats.Achievement;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;

/** Server-owned history evidence. Directory text never grants evidence or achievements. */
public final class HistoryProgress {

    public static final int VALID_MASK = 0x7FFFFFFF;
    private static final String KEY = "gtsr.lostChronicleEvidence";
    public static final Map<String, Achievement> ACHIEVEMENTS = new LinkedHashMap<>();
    public static final String[] NEW_RELICS = { "switchyard_key", "pump_valve_core", "cold_furnace_ember",
        "shelter_workbadge", "trench_medic_badge", "bridge_pass", "webbed_shuttle", "cracked_sighting_lens",
        "resonant_clapper", "foundry_heart_fragment", "hive_memory_knot", "sky_ritual_foil", "mire_sounding_weight",
        "sealed_archive_tube", "rail_repair_token" };
    private static final String[] BOSS_CODES = { "di-02", "di-05", "di-08", "di-10", "di-13", "dc-02", "dc-08" };

    private HistoryProgress() {}

    private static Achievement add(String id, int x, int y, String icon) {
        Item item = LoreRegistry.RELICS.get(icon);
        if (item == null) throw new IllegalStateException("missing lore icon " + icon);
        Achievement achievement = new Achievement(
            "achievement.gtsr." + id,
            "gtsr." + id,
            x,
            y,
            new ItemStack(item),
            null).registerStat();
        ACHIEVEMENTS.put(id, achievement);
        return achievement;
    }

    public static void register() {
        if (!ACHIEVEMENTS.isEmpty()) return;
        for (int kind = 7; kind < 27; kind++) {
            String relic = LoreSources.chestRelic(kind);
            if (!LoreRegistry.RELICS.containsKey(relic)) relic = "brass_chronicle";
            add(
                "ruinComplete_" + com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES[kind],
                (kind - 7) % 5 * 2 - 4,
                10 + (kind - 7) / 5 * 2,
                relic);
        }
        add("firstRuin", -4, -2, "switchyard_key");
        add("tenRuins", -2, -2, "shelter_workbadge");
        add("twentyRuins", 0, -2, "brass_chronicle").setSpecial();
        for (int i = 0; i < 5; i++) add(
            "watch" + i,
            -4 + i * 2,
            2,
            new String[] { "patina_seal", "webbed_shuttle", "cracked_sighting_lens", "bridge_pass",
                "resonant_clapper" }[i]);
        add("fiveWatches", 0, 4, "resonant_clapper").setSpecial();
        add("foundry", -3, 6, "foundry_heart_fragment").setSpecial();
        add("hive", 3, 6, "hive_memory_knot").setSpecial();
        add("ritual", 0, 8, "sky_ritual_foil").setSpecial();
        add("mireCache", -4, -5, "mire_sounding_weight");
        add("archiveCache", 0, -5, "sealed_archive_tube");
        add("railCache", 4, -5, "rail_repair_token");
        add("threeCaches", 0, -7, "patina_seal").setSpecial();
        add("sixWitnesses", 4, -2, "broken_edict").setSpecial();
        ProsperityAchievements.register();
    }

    public static Achievement[] achievements() {
        List<Achievement> list = new ArrayList<>(ACHIEVEMENTS.values());
        list.addAll(ProsperityAchievements.ACHIEVEMENTS.values());
        list.add(LoreRegistry.silentKingDefeated);
        return list.toArray(new Achievement[list.size()]);
    }

    private static boolean valid(EntityPlayer p) {
        return p instanceof EntityPlayerMP && p.isEntityAlive()
            && !p.worldObj.isRemote
            && !(p instanceof net.minecraftforge.common.util.FakePlayer)
            && !p.capabilities.isCreativeMode;
    }

    private static NBTTagCompound data(EntityPlayer p) {
        return p.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .getCompoundTag(KEY);
    }

    private static void save(EntityPlayer p, NBTTagCompound n) {
        NBTTagCompound persisted = p.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        persisted.setTag(KEY, n);
        p.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private static void award(EntityPlayer p, String id) {
        Achievement a = ACHIEVEMENTS.get(id);
        if (a == null && ProsperityAchievements.earnable(id)) a = ProsperityAchievements.ACHIEVEMENTS.get(id);
        if (valid(p) && a != null
            && !((EntityPlayerMP) p).func_147099_x()
                .hasAchievementUnlocked(a))
            p.addStat(a, 1);
    }

    private static Set<String> strings(NBTTagCompound n, String key) {
        NBTTagList list = n.getTagList(key, 8);
        Set<String> set = new HashSet<>();
        for (int i = 0; i < list.tagCount(); i++) set.add(list.getStringTagAt(i));
        return set;
    }

    private static void storeStrings(NBTTagCompound n, String key, Set<String> set) {
        NBTTagList list = new NBTTagList();
        for (String value : set) list.appendTag(new net.minecraft.nbt.NBTTagString(value));
        n.setTag(key, list);
    }

    public static boolean hasSceneStage(EntityPlayer player, String site, String stage) {
        return strings(data(player), "sceneStages").contains(site + ":" + stage);
    }

    /** Server callers must prove membership in a generated structure before calling this entry point. */
    public static void sceneEntered(EntityPlayer player, String site, String prefab) {
        sceneStage(player, site, prefab, "entry", true);
    }

    /** Called only after server geometry, interaction, or death ownership has been proved. */
    public static void sceneStage(EntityPlayer player, String site, String prefab, String stage, boolean title) {
        if (!(player instanceof EntityPlayerMP) || !player.isEntityAlive()
            || player.worldObj.isRemote
            || player instanceof net.minecraftforge.common.util.FakePlayer
            || !(player.worldObj.provider instanceof WorldProviderProsperityRuins)) return;
        if (stage.startsWith("battle") && player.capabilities.isCreativeMode) return;
        NBTTagCompound n = data(player);
        if ("entry".equals(stage)) {
            // Stable type evidence and upgrades are independent of per-site presentation replay.
            Set<String> enteredTypes = strings(n, "sceneEntryTypes");
            if (enteredTypes.add(prefab)) {
                storeStrings(n, "sceneEntryTypes", enteredTypes);
                save(player, n);
            }
            award(player, "explore." + prefab);
        }
        Set<String> stages = strings(n, "sceneStages");
        if (!stages.add(site + ":" + stage)) return;
        storeStrings(n, "sceneStages", stages);
        int kind = -1;
        for (int i = 0; i < com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES.length; i++)
            if (prefab.equals(com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES[i])) kind = i;
        int bit = prefab.equals("forgotten_lake_court") ? 30 : kind;
        if (bit >= 0 && bit <= 30) {
            n.setInteger("sceneEntries", n.getInteger("sceneEntries") | (1 << bit));
            if (stage.startsWith("battle")) n.setInteger("sceneBattles", n.getInteger("sceneBattles") | (1 << bit));
        }
        save(player, n);
        String nameKey = prefab.equals("forgotten_lake_court") ? "lore.chapter.hanging_great_tree.title"
            : "lore.entry.structures." + prefab + ".title";
        player.addChatMessage(
            new net.minecraft.util.ChatComponentTranslation(
                "gtsr.scene.story_unlocked",
                new net.minecraft.util.ChatComponentTranslation(nameKey)));
        if (title && "entry".equals(stage)) LoreNetwork.title((EntityPlayerMP) player, nameKey);
        LoreNetwork.send((EntityPlayerMP) player, false);
    }

    public static int sceneEntries(EntityPlayer player) {
        return data(player).getInteger("sceneEntries") & VALID_MASK;
    }

    public static int sceneBattles(EntityPlayer player) {
        return data(player).getInteger("sceneBattles") & VALID_MASK;
    }

    public static void visit(EntityPlayer p, int kind) {
        if (!valid(p) || !(p.worldObj.provider instanceof WorldProviderProsperityRuins) || kind < 0 || kind > 29)
            return;
        NBTTagCompound n = data(p);
        Set<String> visits = strings(n, "visited");
        if (!visits.add(Integer.toString(kind))) return;
        storeStrings(n, "visited", visits);
        save(p, n);
        award(p, "firstRuin");
        int small = 0;
        for (int i = 7; i < 27; i++) if (visits.contains(Integer.toString(i))) small++;
        if (small >= 10) award(p, "tenRuins");
        if (small == 20) award(p, "twentyRuins");
        LoreNetwork.send((EntityPlayerMP) p, false);
    }

    /** Personal evidence remains separate from the world's shared revision-seven machinery. */
    public static void observeRemasterEvidence(EntityPlayer p, String site, String evidence) {
        if (!valid(p) || !(p.worldObj.provider instanceof WorldProviderProsperityRuins)) return;
        NBTTagCompound n = data(p);
        Set<String> observed = strings(n, "remasterEvidence");
        if (!observed.add(site + ":" + evidence)) return;
        storeStrings(n, "remasterEvidence", observed);
        save(p, n);
        LoreNetwork.send((EntityPlayerMP) p, false);
    }

    public static void completeRuin(EntityPlayer p, int kind) {
        if (!valid(p) || !(p.worldObj.provider instanceof WorldProviderProsperityRuins) || kind < 7 || kind >= 27)
            return;
        NBTTagCompound n = data(p);
        Set<String> completed = strings(n, "completed");
        if (!completed.add(Integer.toString(kind))) return;
        storeStrings(n, "completed", completed);
        save(p, n);
        award(p, "ruinComplete_" + com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES[kind]);
        LoreNetwork.send((EntityPlayerMP) p, false);
        p.addChatMessage(
            new net.minecraft.util.ChatComponentTranslation(
                "gtsr.ruin.ending." + com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES[kind]));
    }

    public static void observeRelic(EntityPlayer p, String id) {
        if (!valid(p) || !LoreRegistry.RELICS.containsKey(id)) return;
        boolean held = false;
        for (ItemStack stack : p.inventory.mainInventory)
            if (stack != null && stack.stackSize > 0 && stack.getItem() == LoreRegistry.RELICS.get(id)) held = true;
        if (!held) return;
        NBTTagCompound n = data(p);
        Set<String> relics = strings(n, "relics");
        boolean first = relics.add(id);
        // Recover catalog awards from existing save evidence after upgrading, without replaying presentation.
        award(p, "relic." + id);
        if (!first) return;
        storeStrings(n, "relics", relics);
        save(p, n);
        boolean six = true;
        for (String r : new String[] { "brass_chronicle", "patina_seal", "sigh_glass", "mire_memory", "royal_sap",
            "broken_edict" }) six &= relics.contains(r);
        if (six) award(p, "sixWitnesses");
        LoreNetwork.send((EntityPlayerMP) p, false);
    }

    public static void bossDefeated(EntityPlayer p, EchoKind kind) {
        if (!valid(p) || kind == null) return;
        int bit = -1;
        for (int i = 0; i < BOSS_CODES.length; i++) if (BOSS_CODES[i].equals(kind.code)) bit = i;
        if (bit < 0) return;
        NBTTagCompound n = data(p);
        int bits = n.getInteger("events") | (1 << bit);
        n.setInteger("events", bits);
        save(p, n);
        if (bit < 5) award(p, "watch" + bit);
        else award(p, bit == 5 ? "foundry" : "hive");
        if (bit == 5 || bit == 6) majorBossDefeated(p, kind.code);
        if ((bits & 31) == 31) award(p, "fiveWatches");
        LoreNetwork.send((EntityPlayerMP) p, false);
    }

    /** Only existing accepted-death hooks may call this. Future boss identities stay pending. */
    public static void majorBossDefeated(EntityPlayer player, String code) {
        if (!valid(player) || !(player.worldObj.provider instanceof WorldProviderProsperityRuins)
            || !ProsperityAchievements.earnable("defeat." + code)) return;
        NBTTagCompound n = data(player);
        Set<String> bosses = strings(n, "catalogBossDeaths");
        bosses.add(code);
        storeStrings(n, "catalogBossDeaths", bosses);
        save(player, n);
        award(player, "defeat." + code);
    }

    /** Caller must prove the real event; first returns true exactly once per persisted player. */
    public static boolean event(EntityPlayer p, int bit) {
        if (!valid(p) || bit < 7 || bit > 10) return false;
        NBTTagCompound n = data(p);
        int mask = n.getInteger("events");
        boolean first = (mask & (1 << bit)) == 0;
        if (first) {
            mask |= 1 << bit;
            n.setInteger("events", mask);
            save(p, n);
        }
        award(p, new String[] { "ritual", "mireCache", "archiveCache", "railCache" }[bit - 7]);
        if ((mask & 0x700) == 0x700) award(p, "threeCaches");
        LoreNetwork.send((EntityPlayerMP) p, false);
        return first;
    }

    public static int snapshot(EntityPlayer p) {
        if (!valid(p)) return 0;
        int mask = 0;
        for (int i = 0; i < 5; i++) if (((EntityPlayerMP) p).func_147099_x()
            .hasAchievementUnlocked(ACHIEVEMENTS.get("watch" + i))) mask |= 1 << i;
        String[] ids = { "foundry", "hive", "ritual", "mireCache", "archiveCache", "railCache" };
        for (int i = 0; i < ids.length; i++) if (((EntityPlayerMP) p).func_147099_x()
            .hasAchievementUnlocked(ACHIEVEMENTS.get(ids[i]))) mask |= 1 << (i + 5);
        for (int kind = 7; kind < 27; kind++) if (((EntityPlayerMP) p).func_147099_x()
            .hasAchievementUnlocked(
                ACHIEVEMENTS
                    .get("ruinComplete_" + com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES[kind])))
            mask |= 1 << (kind + 4);
        return mask & VALID_MASK;
    }
}
