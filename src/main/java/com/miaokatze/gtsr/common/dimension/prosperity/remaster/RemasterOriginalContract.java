package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.FakePlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinObjectives;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;
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

    private static void append(StringBuilder out, String part) {
        if (part.isEmpty() || out.indexOf(part) >= 0) return;
        if (out.length() > 0) out.append('\n');
        out.append(part);
    }

    /** Use only existing keys for this exact original structure; endings belong to completion. */
    public static String narrative(RemasterSite site, JsonObject node, JsonObject puzzle) {
        StringBuilder out = new StringBuilder();
        if (node.has("navigationHint") && node.get("navigationHint")
            .getAsBoolean()) {
            append(out, RemasterRuntime.string(node, "text", ""));
            append(out, RemasterRuntime.string(node, "clue", ""));
            return out.toString();
        }
        String id = RemasterRuntime.string(node, "id", "");
        if (kind(site) >= 0) {
            String base = "lore.entry.structures." + site.prefab;
            if (id.equals("entrance-story-board")) {
                append(out, text(base + ".title"));
                append(out, text(base + ".p0"));
            } else {
                List<JsonObject> originals = originalNodes(site);
                int index = -1;
                int slot = 0;
                String nodeRole = RemasterRuntime.string(node, "role", "");
                for (JsonObject original : originals) {
                    if (!nodeRole.equals(RemasterRuntime.string(original, "role", ""))) continue;
                    if (id.equals(RemasterRuntime.string(original, "id", ""))) index = slot;
                    slot++;
                }
                if (id.startsWith("objective-")) {
                    try {
                        index = Integer.parseInt(id.substring("objective-".length()));
                    } catch (NumberFormatException ignored) {
                        index = -1;
                    }
                } else if (id.startsWith("control-")) {
                    try {
                        index = Integer.parseInt(id.substring("control-".length()));
                    } catch (NumberFormatException ignored) {
                        index = -1;
                    }
                }
                String role = RemasterRuntime.string(node, "role", "");
                if (index >= 0) append(
                    out,
                    text(
                        "gtsr.ruin."
                            + (role.equals("memory") || RuinObjectives.exploration(kind(site)) ? "memory." : "clue.")
                            + site.prefab
                            + "."
                            + index));
                if (out.length() == 0 && role.equals("memory")) append(out, text(base + ".p0"));
            }
        }
        append(out, RemasterRuntime.string(node, "text", ""));
        append(out, RemasterRuntime.string(node, "clue", ""));
        append(out, RemasterRuntime.string(puzzle, "explain", ""));
        if (out.length() == 0) append(out, RemasterRuntime.string(node, "label", "现场记录"));
        return out.toString();
    }

    private static List<JsonObject> originalNodes(RemasterSite site) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : RemasterRuntime.array(site.plan().metadata, "nodes")) {
            JsonObject n = element.getAsJsonObject();
            String id = RemasterRuntime.string(n, "id", ""), role = RemasterRuntime.string(n, "role", "");
            if ((role.equals("control") || role.equals("memory")) && !id.startsWith("shape-")
                && !id.equals("entrance-story-board")
                && !n.has("navigationHint")) result.add(n);
        }
        return result;
    }

    public static boolean withinGeneratedSite(EntityPlayer player, RemasterSite site) {
        int x = (int) Math.floor(player.posX), y = (int) Math.floor(player.posY), z = (int) Math.floor(player.posZ);
        if (!player.worldObj.blockExists(x, y, z) || !RemasterData.get(player.worldObj)
            .flag(site.id(), "geom:" + (x >> 4) + ":" + (z >> 4))) return false;
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
            && !player.capabilities.isCreativeMode
            && !player.worldObj.isRemote
            && player.worldObj.provider instanceof WorldProviderProsperityRuins;
    }

    public static List<JsonObject> records(RemasterSite site) {
        List<JsonObject> out = new ArrayList<>();
        int kind = kind(site);
        if (RuinObjectives.exploration(kind)) {
            // Original RuinsBlueprint has three landmarks for the crane and boardwalk, two elsewhere.
            // A revised puzzle may put a console at the landmark; its physical record remains personal.
            int count = kind == 10 || kind == 19 ? 3 : 2;
            for (int i = 0; i < count; i++) {
                JsonObject found = null;
                for (JsonObject n : RemasterRuntime.nodes(site)) {
                    String id = RemasterRuntime.string(n, "evidenceRecordId", RemasterRuntime.string(n, "id", ""));
                    if (id.equals("objective-" + i) && RemasterRuntime.string(n, "id", "")
                        .equals(id) && !n.has("navigationHint")) found = n;
                }
                if (found == null) return new ArrayList<>();
                out.add(found);
            }
            return out;
        }
        for (JsonObject node : RemasterRuntime.nodes(site)) {
            String id = RemasterRuntime.string(node, "id", "");
            if (RemasterRuntime.string(node, "role", "")
                .equals("memory") && !id.equals("entrance-story-board")
                && !node.has("navigationHint")) out.add(node);
        }
        return out;
    }

    public static void record(EntityPlayer player, RemasterSite site, String nodeId) {
        if (!valid(player) || !RuinObjectives.exploration(kind(site))) return;
        NBTTagCompound personal = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        List<JsonObject> records = records(site);
        for (JsonObject record : records) {
            String id = RemasterRuntime.string(record, "id", "");
            if (id.equals(nodeId)) {
                personal.setBoolean("gtsr.r7.route:" + site.id() + ":" + id, true);
                player.getEntityData()
                    .setTag(EntityPlayer.PERSISTED_NBT_TAG, personal);
                return;
            }
            if (!personal.getBoolean("gtsr.r7.route:" + site.id() + ":" + id)) return;
        }
    }

    /** Called from the actual history world tick, independent of any persisted legacy sites. */
    public static void observePlayer(EntityPlayer player, RemasterSite site) {
        int kind = kind(site);
        if (!valid(player) || kind < 0 || !withinGeneratedSite(player, site)) return;
        HistoryProgress.visit(player, kind);
        for (JsonObject record : records(site)) {
            int[] at = RemasterRuntime.nodePosition(player.worldObj, site, record);
            if (at == null || player.getDistanceSq(at[0] + .5, at[1] + .5, at[2] + .5) > 9) continue;
            TileEntity actual = player.worldObj.getTileEntity(at[0], at[1], at[2]);
            if (actual instanceof TileRemasterNode && site.id()
                .equals(((TileRemasterNode) actual).siteId)
                && RemasterRuntime.string(record, "id", "")
                    .equals(((TileRemasterNode) actual).nodeId)
                && RemasterRuntime.node((TileRemasterNode) actual) != null)
                record(player, site, RemasterRuntime.string(record, "id", ""));
        }
        if (kind < 7 || !RemasterRuntime.solved(player.worldObj, site.id(), "site-puzzle")) return;
        NBTTagCompound personal = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (RuinObjectives.exploration(kind)) {
            List<JsonObject> records = records(site);
            if (records.isEmpty()) return;
            for (JsonObject record : records)
                if (!personal.getBoolean("gtsr.r7.route:" + site.id() + ":" + RemasterRuntime.string(record, "id", "")))
                    return;
        }
        if (RuinObjectives.combat(kind) && !guardsCleared(player.worldObj, site, null)) return;
        for (JsonObject node : RemasterRuntime.nodes(site)) {
            if (!RemasterRuntime.string(node, "role", "")
                .equals("chest") || RemasterRuntime.string(node, "kind", "")
                    .equals("exploration")
                || RemasterRuntime.string(node, "unlock", "")
                    .equals("right-click"))
                continue;
            int[] at = RemasterRuntime.nodePosition(player.worldObj, site, node);
            if (at == null || player.getDistanceSq(at[0] + .5, at[1] + .5, at[2] + .5) > 16) continue;
            TileEntity tile = player.worldObj.getTileEntity(at[0], at[1], at[2]);
            if (tile instanceof TileEntitySealedChest && RemasterRuntime.chestReady((TileEntitySealedChest) tile)
                || tile instanceof TileEntityUnsealedChest && player.worldObj.getBlock(at[0], at[1], at[2])
                    == com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry.unsealedChest
                    && RemasterData.get(player.worldObj)
                        .flag(site.id(), "node:" + RemasterRuntime.string(node, "id", ""))
                    && RemasterData.get(player.worldObj)
                        .flag(site.id(), "claimed:" + RemasterRuntime.string(node, "id", ""))) {
                HistoryProgress.completeRuin(player, kind);
                return;
            }
        }
    }

    public static boolean guardsCleared(net.minecraft.world.World world, RemasterSite site, JsonObject control) {
        if (site == null) return false;
        int kind = kind(site);
        java.util.Set<String> modules = new java.util.HashSet<>();
        if (kind >= 0 && kind < 7) {
            if (control == null) {
                for (JsonObject original : originalNodes(site)) if (RemasterRuntime.string(original, "role", "")
                    .equals("control")) {
                        String module = guardModule(site, RemasterRuntime.string(original, "id", ""));
                        if (module.isEmpty()) return false;
                        modules.add(module);
                    }
            } else {
                String module = guardModule(site, RemasterRuntime.string(control, "id", ""));
                if (module.isEmpty()) return false;
                modules.add(module);
            }
        }
        java.util.Set<String> populated = new java.util.HashSet<>();
        RemasterData data = RemasterData.get(world);
        JsonArray spawns = RemasterRuntime.array(site.plan().metadata, "spawns");
        boolean found = false;
        for (int i = 0; i < spawns.size(); i++) {
            JsonObject spawn = spawns.get(i)
                .getAsJsonObject();
            String module = RemasterRuntime.string(spawn, "module", "");
            if (!eligibleGuard(spawn) || !modules.isEmpty() && !modules.contains(module)) continue;
            found = true;
            populated.add(module);
            if (!data.flag(site.id(), "entity:" + i)
                || !data.flag(site.id(), "dead:" + RemasterRuntime.string(spawn, "id", Integer.toString(i))))
                return false;
        }
        // The R7 pilgrimage arch deliberately retains a ritual instead of any admitted guard.
        // Its real puzzle and conditional chest still gate the ending. If future source adds a guard,
        // the loop above immediately requires its actual entity/death ledger like every other combat site.
        return (found || site.prefab.equals("pilgrim_arch") && modules.isEmpty()) && populated.containsAll(modules);
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

    /** Frozen v0 semantic rooms, rather than reassigning side lines when a variant shifts its buildings. */
    public static String guardModule(RemasterSite site, String control) {
        String suffix = "";
        switch (site.prefab) {
            case "fallen_foundry":
                suffix = control.equals("control-0") ? "1"
                    : control.equals("control-1") ? "2" : control.equals("control-2") ? "4" : "";
                break;
            case "subsided_factory":
                suffix = control.equals("factory-stage-0") ? "0"
                    : control.equals("factory-stage-1") ? "4"
                        : control.equals("factory-stage-2") ? "8" : control.equals("factory-stage-3") ? "12" : "";
                break;
            case "boiler_shrine":
                if (control.equals("regulator") || control.equals("isolation") || control.equals("drain")) suffix = "0";
                break;
            case "weaving_mill":
                suffix = control.equals("tension") ? "0"
                    : control.equals("shuttle") || control.equals("drive") ? "field" : "";
                break;
            case "sniper_watch":
                suffix = control.equals("scope") ? "field"
                    : control.equals("cover") || control.equals("lock") ? "1" : "";
                break;
            case "mirror_barracks":
                // V2 module-2 contains only excluded dr-09. Its nearest actual eligible cluster is cluster7-0,
                // module-3, 87.114866699 blocks from shutter; share that real dead ledger, never add an actor.
                suffix = control.equals("mirror") ? "3"
                    : control.equals("shutter") ? (site.variant == 2 ? "3" : "2") : control.equals("lock") ? "0" : "";
                break;
            case "resonant_station":
                if (control.equals("bell-0") || control.equals("bell-1") || control.equals("channel")) suffix = "0";
                break;
            default:
                break;
        }
        return suffix.isEmpty() ? "" : site.prefab + (suffix.equals("field") ? "-field-module" : "-module-" + suffix);
    }

    public static boolean controlsConfirmed(net.minecraft.world.World world, RemasterSite site) {
        if (site == null) return false;
        RemasterData data = RemasterData.get(world);
        int count = 0;
        for (JsonObject n : originalNodes(site)) if (RemasterRuntime.string(n, "role", "")
            .equals("control")) {
                String id = RemasterRuntime.string(n, "id", "");
                count++;
                if (!data.flag(site.id(), "node:" + id) || !data.flag(site.id(), "original-control:" + id))
                    return false;
            }
        return count > 0;
    }

    public static void resetControls(net.minecraft.world.World world, RemasterSite site) {
        if (kind(site) < 0 || kind(site) >= 7) return;
        RemasterData data = RemasterData.get(world);
        for (JsonObject n : originalNodes(site)) if (RemasterRuntime.string(n, "role", "")
            .equals("control")) data.flag(site.id(), "original-control:" + RemasterRuntime.string(n, "id", ""), false);
    }
}
