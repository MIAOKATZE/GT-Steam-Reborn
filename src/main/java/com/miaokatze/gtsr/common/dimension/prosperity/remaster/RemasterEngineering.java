package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress;

/** Shared eight-chapter engineering ledger, authenticated originals and resumable construction. */
public final class RemasterEngineering {

    private static final Map<Integer, List<Change>> CHANGES = new HashMap<>();

    private RemasterEngineering() {}

    public static boolean isEngineering(TileRemasterNode tile) {
        RemasterSite site = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        return site != null && site.prefab.equals("fiction_expansion_project") && tile.role.equals("control");
    }

    public static NBTTagCompound state(TileRemasterNode tile) {
        return state(tile.getWorldObj(), tile.siteId);
    }

    private static NBTTagCompound state(World world, String site) {
        NBTTagCompound root = RemasterData.get(world)
            .state(site);
        if (!root.hasKey("engineering")) root.setTag("engineering", new NBTTagCompound());
        return root.getCompoundTag("engineering");
    }

    public static int phase(World world, String site) {
        return state(world, site).getInteger("phase");
    }

    public static JsonObject spec(TileRemasterNode tile) {
        NBTTagCompound n = state(tile);
        int chapter = Math.min(7, Math.max(0, n.getInteger("chapter")));
        JsonObject authored = RemasterCatalog.config()
            .getAsJsonArray("engineeringChapters")
            .get(chapter)
            .getAsJsonObject();
        JsonObject result = new com.google.gson.JsonParser().parse(authored.toString())
            .getAsJsonObject();
        if (authored.has("sliders")) {
            JsonArray fields = new JsonArray();
            for (JsonElement e : authored.getAsJsonArray("sliders")) {
                JsonArray slider = e.getAsJsonArray(), field = new JsonArray(), choices = new JsonArray();
                field.add(slider.get(0));
                for (int i = slider.get(1)
                    .getAsInt(); i <= slider.get(2)
                        .getAsInt(); i++)
                    choices.add(new JsonPrimitive(Integer.toString(i)));
                field.add(choices);
                fields.add(field);
            }
            result.add("fields", fields);
        }
        result.addProperty("explain", RemasterRuntime.string(authored, "clue", ""));
        return result;
    }

    public static void decorateView(TileRemasterNode tile, JsonObject out) {
        NBTTagCompound n = state(tile);
        JsonObject spec = spec(tile);
        out.addProperty("title", RemasterRuntime.string(spec, "title", "工程验收"));
        out.addProperty("clue", RemasterRuntime.string(spec, "clue", ""));
        int chapter = n.getInteger("chapter");
        int tokens = 0;
        for (JsonElement e : RemasterWitness.roster()) if (n.getBoolean("testimony:" + e.getAsString())) tokens++;
        String info = n.getBoolean("final") ? "38身份原件已归档，受控高空终局已完成。"
            : "阶段" + (chapter + 1) + "/8 · 原件" + tokens + "/38 · " + n.getString("feedback");
        if (n.hasKey("jobPhase"))
            info = "装配" + n.getInteger("jobIndex") + "/" + changes(n.getInteger("jobPhase")).size();
        out.addProperty("status", info);
    }

    public static void readEvidence(EntityPlayer player, TileRemasterNode tile) {
        RemasterSite site = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        if (site == null || !site.prefab.equals("fiction_expansion_project")) return;
        JsonObject node = RemasterRuntime.node(tile);
        String target = RemasterRuntime.string(node, "target", "");
        if (!target.startsWith("fiction-station-")) return;
        int index = Integer.parseInt(target.substring("fiction-station-".length()));
        if (index < 0 || index > 7) return;
        NBTTagCompound n = state(tile);
        n.setBoolean("evidence:" + index, true);
        n.setString(
            "reader:" + index,
            player.getUniqueID()
                .toString());
        RemasterData.get(tile.getWorldObj())
            .markDirty();
    }

    public static boolean deposit(EntityPlayer player, TileRemasterNode tile) {
        JsonObject node = RemasterRuntime.node(tile);
        String code = RemasterRuntime.string(node, "code", "");
        NBTTagCompound n = state(tile);
        if (n.getBoolean("testimony:" + code)) return true;
        if (n.getInteger("chapter") < 7) {
            n.setString("feedback", "先完成七阶段工程，再归档身份原件。");
            tile.refresh();
            return false;
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!RemasterWitness.authentic(stack, tile.getWorldObj(), code)) continue;
            n.setTag("original:" + code, stack.writeToNBT(new NBTTagCompound()));
            n.setBoolean("testimony:" + code, true);
            n.setString(
                "depositor:" + code,
                player.getUniqueID()
                    .toString());
            player.inventory.decrStackSize(i, 1);
            player.inventory.markDirty();
            RemasterData.get(tile.getWorldObj())
                .markDirty();
            HistoryProgress.observeRemasterEvidence(player, tile.siteId, "archived:" + code);
            tile.refresh();
            return true;
        }
        n.setString("feedback", "此台座只接受对应身份、出处可核查的真实原件。");
        tile.refresh();
        return false;
    }

    public static boolean action(EntityPlayer player, TileRemasterNode tile, int button) {
        NBTTagCompound n = state(tile);
        if (n.getBoolean("final") || n.hasKey("jobPhase")) return false;
        int chapter = n.getInteger("chapter");
        JsonObject node = RemasterRuntime.node(tile);
        if (!RemasterRuntime.string(node, "id", "")
            .equals("fiction-station-" + chapter)) {
            n.setString("feedback", "当前工序请前往第" + (chapter + 1) + "工作站，沿检修廊回返。");
            tile.refresh();
            return false;
        }
        JsonObject spec = spec(tile);
        JsonArray fields = spec.getAsJsonArray("fields"), answer = spec.getAsJsonArray("answer");
        if (button >= 0 && button < fields.size()) {
            n.setInteger(
                "field" + button,
                Math.floorMod(
                    n.getInteger("field" + button) + 1,
                    fields.get(button)
                        .getAsJsonArray()
                        .get(1)
                        .getAsJsonArray()
                        .size()));
            n.setInteger("stableTicks", 0);
            n.setBoolean("pending", false);
        } else if (button == 101) {
            for (int i = 0; i < fields.size(); i++) n.setInteger("field" + i, 0);
            n.setInteger("stableTicks", 0);
            n.setBoolean("pending", false);
            // Completed chapters, escrow and archived originals are irreversible.
        } else if (button == 100 || button == 102) {
            boolean correct = n.getBoolean("evidence:" + chapter);
            for (int i = 0; i < answer.size(); i++) correct &= n.getInteger("field" + i) == answer.get(i)
                .getAsInt();
            if (!correct) n.setString("feedback", "先现场读取本章档案，依据工单和工程图校核参数。");
            else if (chapter == 4) {
                n.setBoolean("pending", true);
                n.setString("feedback", "压强4、冷却2、泄放1开始连续200tick稳定验收。");
            } else if (spec.has("phase")) {
                beginConstruction(
                    player,
                    tile,
                    spec.get("phase")
                        .getAsInt());
            } else if (chapter == 7) finish(player, tile);
            else advance(tile);
        } else return false;
        RemasterData.get(tile.getWorldObj())
            .markDirty();
        tile.refresh();
        return true;
    }

    private static void advance(TileRemasterNode tile) {
        NBTTagCompound n = state(tile);
        n.setBoolean("chapter-complete:" + n.getInteger("chapter"), true);
        n.setInteger("chapter", n.getInteger("chapter") + 1);
        n.setBoolean("pending", false);
        for (int i = 0; i < 8; i++) n.setInteger("field" + i, 0);
        n.setString("feedback", "工序已归档，沿可回返检修廊前往下一站。");
        RemasterRuntime.updateHints(tile);
    }

    private static void finish(EntityPlayer player, TileRemasterNode tile) {
        NBTTagCompound n = state(tile);
        for (JsonElement e : RemasterWitness.roster()) {
            if (!n.getBoolean("testimony:" + e.getAsString())) {
                n.setString("feedback", "38原件尚未齐全；重复身份不能抵数。");
                return;
            }
        }
        World w = tile.getWorldObj();
        RemasterSite site = RemasterData.get(w)
            .site(tile.siteId);
        int y = Math.min(254, site.y + 128);
        if (!w.blockExists(site.x + 72, y, site.z + 72)) {
            n.setString("feedback", "高空终局所在区块尚未加载。");
            return;
        }
        EntityOldEcho finale = new EntityOldEcho(w);
        finale.initializeEcho(EchoKind.DO02, tile.siteId, site.x + 72.5, y, site.z + 72.5, false);
        finale.setNodeIndex(-1);
        finale.prepareControlledAntimeme();
        if (!w.spawnEntityInWorld(finale)) return;
        // The single world ledger gate is written in the same server event, before the controlled erasure.
        n.setBoolean("final", true);
        n.setBoolean("chapter-complete:7", true);
        n.setLong("finaleAppearedAt", w.getTotalWorldTime());
        n.setLong("finaleEraseAt", w.getTotalWorldTime() + 20);
        n.setString(
            "finaleUUID",
            finale.getUniqueID()
                .toString());
        n.setBoolean("finaleVisible", true);
        n.setBoolean("pending", false);
        n.removeTag("jobPhase");
        RemasterData.get(w)
            .markDirty();
        RemasterData.get(w)
            .state(tile.siteId)
            .setTag("site-puzzle", solvedState());
        HistoryProgress.observeRemasterEvidence(player, tile.siteId, "engineering-finale");
        RemasterRuntime.updateHints(tile);
    }

    private static NBTTagCompound solvedState() {
        NBTTagCompound n = new NBTTagCompound();
        n.setBoolean("solved", true);
        return n;
    }

    /** Never respawn a persisted finale. A late-loaded original is removed using its stable UUID. */
    public static void updateFinale(World world, String siteId) {
        RemasterData data = RemasterData.get(world);
        RemasterSite site = data.site(siteId);
        if (site == null || !site.prefab.equals("fiction_expansion_project")) return;
        NBTTagCompound n = state(world, siteId);
        if (!n.getBoolean("final") || !n.hasKey("finaleUUID") || world.getTotalWorldTime() < n.getLong("finaleEraseAt"))
            return;
        String uuid = n.getString("finaleUUID");
        if (world.loadedEntityList != null) for (Object loaded : new ArrayList<>(world.loadedEntityList)) {
            if (!(loaded instanceof EntityOldEcho)) continue;
            EntityOldEcho entity = (EntityOldEcho) loaded;
            if (entity.getKind() == EchoKind.DO02 && uuid.equals(
                entity.getUniqueID()
                    .toString()))
                entity.finishControlledAntimeme();
        }
        if (n.getBoolean("finaleVisible")) {
            n.setBoolean("finaleVisible", false);
            n.setLong("finaleErasedAt", world.getTotalWorldTime());
            data.markDirty();
        }
    }

    private static final class Change {

        final int x, y, z;
        final String before, after;

        Change(int x, int y, int z, String before, String after) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.before = before;
            this.after = after;
        }
    }

    private static synchronized List<Change> changes(int phase) {
        List<Change> known = CHANGES.get(phase);
        if (known != null) return known;
        RemasterPrefab a = RemasterCatalog.get("fiction_expansion_project", phase - 1);
        RemasterPrefab b = RemasterCatalog.get("fiction_expansion_project", phase);
        Map<String, int[]> coordinates = new java.util.TreeMap<>();
        Map<String, String> previous = new HashMap<>(), next = new HashMap<>();
        collect(a, coordinates, previous);
        collect(b, coordinates, next);
        List<Change> result = new ArrayList<>();
        for (Map.Entry<String, int[]> e : coordinates.entrySet()) {
            String old = previous.containsKey(e.getKey()) ? previous.get(e.getKey()) : "minecraft:air#0";
            String replacement = next.containsKey(e.getKey()) ? next.get(e.getKey()) : "minecraft:air#0";
            if (old.equals(replacement)) continue;
            int[] at = e.getValue();
            // Furnished basement, service routes, controls and archived witnesses are phase invariant.
            if (at[1] <= 5) continue;
            result.add(new Change(at[0], at[1], at[2], old, replacement));
        }
        result.sort(
            (x, y) -> x.y != y.y ? Integer.compare(x.y, y.y)
                : x.z != y.z ? Integer.compare(x.z, y.z) : Integer.compare(x.x, y.x));
        CHANGES.put(phase, result);
        return result;
    }

    private static void collect(RemasterPrefab p, Map<String, int[]> coordinates, Map<String, String> materials) {
        for (int cx = Math.floorDiv(p.min[0], 16); cx <= Math.floorDiv(p.max[0], 16); cx++)
            for (int cz = Math.floorDiv(p.min[2], 16); cz <= Math.floorDiv(p.max[2], 16); cz++)
                for (RemasterPrefab.Run run : p.slice(cx, cz)) for (int x = run.x; x < run.x + run.length; x++) {
                    String key = x + "," + run.y + "," + run.z;
                    coordinates.put(key, new int[] { x, run.y, run.z });
                    materials.put(key, p.palette[run.paletteIndex]);
                }
    }

    private static boolean safe(World w, RemasterSite s, Change c) {
        int x = s.x + c.x, y = s.y + c.y, z = s.z + c.z;
        if (y < 1 || y > 254 || !w.blockExists(x, y, z) || w.getTileEntity(x, y, z) != null) return false;
        if (!c.after.equals("minecraft:air#0")
            && !w.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1))
                .isEmpty())
            return false;
        return w.getBlock(x, y, z) == RemasterRuntime.resolve(c.before)
            && w.getBlockMetadata(x, y, z) == RemasterRuntime.meta(c.before);
    }

    private static void beginConstruction(EntityPlayer player, TileRemasterNode tile, int phase) {
        NBTTagCompound n = state(tile);
        if (phase != n.getInteger("phase") + 1 || phase > 2) return;
        RemasterSite s = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        List<Change> changes = changes(phase);
        for (Change c : changes) if (!safe(tile.getWorldObj(), s, c)) {
            n.setString("feedback", "装配范围有未加载区块、人员、改动物或设施；保持工程与已归档原件。");
            return;
        }
        // Each delivery assembles one batch of 16 authored blocks; the bill is persistent and exact.
        int batches = Math.max(1, (changes.size() + 15) / 16);
        Item material = phase == 1 ? Items.iron_ingot : Items.redstone;
        int delivered = n.getInteger("delivered:" + phase);
        for (int i = 0; i < player.inventory.getSizeInventory() && delivered < batches; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack == null || stack.getItem() != material) continue;
            int take = Math.min(stack.stackSize, batches - delivered);
            player.inventory.decrStackSize(i, take);
            delivered += take;
        }
        n.setInteger("delivered:" + phase, delivered);
        player.inventory.markDirty();
        if (delivered < batches) {
            n.setString("feedback", "材料已入装配库 " + delivered + "/" + batches + (phase == 1 ? " 铁锭" : " 红石"));
            return;
        }
        n.setInteger("jobPhase", phase);
        n.setInteger("jobIndex", 0);
        n.setString("feedback", "从基础向高空逐批装配；让开构件并保持检修区块加载。");
    }

    public static void tick(TileRemasterNode tile) {
        if (!isEngineering(tile)) return;
        World w = tile.getWorldObj();
        updateFinale(w, tile.siteId);
        NBTTagCompound n = state(tile);
        if (n.getBoolean("final")) return;
        long now = w.getTotalWorldTime();
        if (n.getLong("tick") == now) return;
        if (n.hasKey("tick") && now - n.getLong("tick") > 1) n.setInteger("stableTicks", 0);
        n.setLong("tick", now);
        if (n.getBoolean("pending")) {
            n.setInteger("stableTicks", n.getInteger("stableTicks") + 1);
            if (n.getInteger("stableTicks") >= 200) advance(tile);
        }
        if (n.hasKey("jobPhase")) {
            List<Change> changes = changes(n.getInteger("jobPhase"));
            int i = n.getInteger("jobIndex");
            RemasterSite s = RemasterData.get(w)
                .site(tile.siteId);
            for (int budget = 0; budget < 32 && i < changes.size(); budget++) {
                Change c = changes.get(i);
                if (!safe(w, s, c)) {
                    n.setString("feedback", "装配暂停：构件被占用或区块卸载；进度已保存。");
                    break;
                }
                int x = s.x + c.x, y = s.y + c.y, z = s.z + c.z;
                if (!w.setBlock(x, y, z, RemasterRuntime.resolve(c.after), RemasterRuntime.meta(c.after), 3)) {
                    n.setString("feedback", "构件放置失败，装配暂停。");
                    break;
                }
                i++;
                n.setInteger("jobIndex", i);
            }
            if (i == changes.size()) {
                n.setInteger("phase", n.getInteger("jobPhase"));
                n.removeTag("jobPhase");
                advance(tile);
            }
        }
        if (n.getBoolean("pending") || n.hasKey("jobPhase") || now % 20 == 0) {
            RemasterData.get(w)
                .markDirty();
            if (now % 20 == 0) tile.refresh();
        }
    }
}
