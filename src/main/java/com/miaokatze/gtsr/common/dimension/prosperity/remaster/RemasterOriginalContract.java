package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.FakePlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress;

/** Revision seven keeps original structure identities, individual routes and physical endpoints. */
public final class RemasterOriginalContract {

    private static final Properties ORIGINAL_TEXT = new Properties();
    static {
        try (InputStream in = RemasterOriginalContract.class.getResourceAsStream("/assets/gtsr/lang/zh_CN.lang")) {
            if (in != null) ORIGINAL_TEXT.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException failed) {
            throw new IllegalStateException("Cannot read original ruin narrative", failed);
        }
    }

    private RemasterOriginalContract() {}

    public static int kind(RemasterSite site) {
        if (site == null) return -1;
        for (int i = 0; i < RuinSite.NAMES.length; i++) if (RuinSite.NAMES[i].equals(site.prefab)) return i;
        return -1;
    }

    private static String text(String key) {
        if (!ORIGINAL_TEXT.containsKey(key)) return "";
        String translated = StatCollector.translateToLocal(key);
        return translated.equals(key) ? ORIGINAL_TEXT.getProperty(key) : translated;
    }

    public static String narrative(RemasterSite site, JsonObject node, JsonObject unused) {
        String story = RemasterRuntime.string(node, "text", "");
        return story.isEmpty() ? text("lore.entry.structures." + site.prefab + ".p0") : story;
    }

    public static boolean withinGeneratedSite(EntityPlayer player, RemasterSite site) {
        int x = (int) Math.floor(player.posX), y = (int) Math.floor(player.posY), z = (int) Math.floor(player.posZ);
        if (!player.worldObj.blockExists(x, y, z) || !RemasterData.get(player.worldObj)
            .flag(site.id(), "geom:" + (x >> 4) + ":" + (z >> 4))) return false;
        if ("tree-overlay".equals(site.layout)) {
            NBTTagCompound tree = RemasterData.get(player.worldObj)
                .state(site.id());
            if (!tree.hasKey("legacyAnchorX") || !tree.hasKey("legacyAnchorY") || !tree.hasKey("legacyAnchorZ"))
                return false;
            int ax = tree.getInteger("legacyAnchorX"), az = tree.getInteger("legacyAnchorZ");
            int base = tree.getInteger("legacyAnchorY");
            int height = com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer
                .islandTreeHeightAt(player.worldObj.getSeed(), ax, az);
            return Math.abs((long) x - ax) <= 160 && Math.abs((long) z - az) <= 160
                && y >= base
                && y <= base + height + 32;
        }
        JsonObject descriptor = RemasterCatalog.descriptor(site.prefab, site.variant);
        JsonArray min = descriptor.getAsJsonArray("min"), max = descriptor.getAsJsonArray("max");
        return x >= site.x + min.get(0)
            .getAsInt() && x <= site.x
                + max.get(0)
                    .getAsInt()
            && z >= site.z + min.get(2)
                .getAsInt()
            && z <= site.z + max.get(2)
                .getAsInt()
            && y >= site.y + min.get(1)
                .getAsInt()
            && y <= site.y + max.get(1)
                .getAsInt() + 2;
    }

    private static boolean valid(EntityPlayer player) {
        return player instanceof EntityPlayerMP && !(player instanceof FakePlayer)
            && player.isEntityAlive()
            && !player.worldObj.isRemote
            && player.worldObj.provider instanceof WorldProviderProsperityRuins;
    }

    /** Called from the actual history world tick, independent of any persisted legacy sites. */
    public static void observePlayer(EntityPlayer player, RemasterSite site) {
        int kind = kind(site);
        if (!valid(player) || !RemasterRollout.allowsGeneration(site) || !withinGeneratedSite(player, site)) return;
        HistoryProgress.sceneEntered(player, site.id(), site.prefab);
        if (RemasterData.get(player.worldObj)
            .flag(site.id(), "boss-dead")) HistoryProgress.sceneStage(player, site.id(), site.prefab, "battle", false);
        if (site.prefab.equals("forgotten_lake_court")) {
            String legacy = RemasterData.get(player.worldObj)
                .state(site.id())
                .getString("legacyEncounter");
            if (!legacy.isEmpty() && com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterData
                .get(player.worldObj)
                .kingDead(legacy)) HistoryProgress.sceneStage(player, site.id(), site.prefab, "battle", false);
        }
        if (site.layout.equals("compact-prefab")) {
            for (JsonObject node : RemasterRuntime.nodes(site)) {
                if ("combat".equals(RemasterRuntime.string(node, "unlockMode", "")) && RemasterRuntime
                    .combatCleared(player.worldObj, site, RemasterRuntime.string(node, "combatModule", "")))
                    HistoryProgress.sceneStage(
                        player,
                        site.id(),
                        site.prefab,
                        "battle:" + RemasterRuntime.string(node, "combatModule", ""),
                        false);
            }
            if (kind >= 0) HistoryProgress.visit(player, kind);
            return;
        }
    }

    public static boolean eligibleGuard(JsonObject spawn) {
        String role = RemasterRuntime.string(spawn, "role", ""), code = RemasterRuntime.string(spawn, "code", "");
        return (role.equals("guard") || role.equals("elite")) && !code.equals("dr-09")
            && !code.equals("dc-10")
            && com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.byCode(code) != null
            && !com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind.byCode(code)
                .isRitual()
            && (!spawn.has("spawn") || spawn.get("spawn")
                .getAsBoolean());
    }

}
