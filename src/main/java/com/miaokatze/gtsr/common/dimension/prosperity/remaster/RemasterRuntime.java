package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress;
import com.miaokatze.gtsr.common.terminal.AggregatorGuiHandler;

import cpw.mods.fml.common.registry.GameRegistry;

/** Immutable authored ownership plus server-only named state, independent of legacy encounter masks. */
public final class RemasterRuntime {

    private static final Map<World, java.util.Set<String>> FINALE_SITES = new java.util.WeakHashMap<>();

    private static final Map<String, List<JsonObject>> NODE_CACHE = new LinkedHashMap<String, List<JsonObject>>(
        16,
        .75f,
        true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<JsonObject>> eldest) {
            return size() > 16;
        }
    };

    private RemasterRuntime() {}

    public static String string(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key)
            .isJsonNull() ? o.get(key)
                .getAsString() : fallback;
    }

    public static int integer(JsonObject o, String key, int fallback) {
        return o.has(key) && !o.get(key)
            .isJsonNull() ? o.get(key)
                .getAsInt() : fallback;
    }

    public static JsonArray array(JsonObject o, String key) {
        return o.has(key) && o.get(key)
            .isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    public static void register() {
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new FinaleTicker());
        GameRegistry.registerItem(RemasterWitness.ITEM, "RemasterWitness7");
        GameRegistry.registerTileEntity(TileRemasterNode.class, "gtsr.remasterNode7");
        RemasterBlocks.setInteractionHandler(new RemasterBlocks.InteractionHandler() {

            public boolean activate(World w, int x, int y, int z, EntityPlayer player) {
                if (w.isRemote) return true;
                TileEntity t = w.getTileEntity(x, y, z);
                if (!(t instanceof TileRemasterNode)) return false;
                TileRemasterNode tile = (TileRemasterNode) t;
                if (!valid(player, tile)) return true;
                if ("chest".equals(tile.role)) openChest(player, tile);
                else {
                    tile.refresh();
                    player.openGui(AggregatorGuiHandler.modInstance(), 1, w, x, y, z);
                }
                return true;
            }

            public TileEntity createTileEntity(World w, int meta, String id) {
                return new TileRemasterNode();
            }

            public float hardness(World w, int x, int y, int z, float fallback) {
                TileEntity t = w.getTileEntity(x, y, z);
                if (!(t instanceof TileRemasterNode)) return 3;
                TileRemasterNode tile = (TileRemasterNode) t;
                if (!"spawner".equals(tile.role)) return -1;
                return RemasterData.get(w)
                    .flag(tile.siteId, "sealed:" + tile.nodeId) ? 100 : -1;
            }
        });
    }

    /** Every interactive coordinate is derived from the packaged prefab, including shape controls. */
    public static synchronized List<JsonObject> nodes(RemasterSite s) {
        String cacheKey = s.prefab + ":" + s.variant;
        List<JsonObject> cached = NODE_CACHE.get(cacheKey);
        if (cached != null) return cached;
        Map<String, JsonObject> out = new LinkedHashMap<>();
        JsonObject m = s.plan().metadata;
        for (JsonElement e : array(m, "nodes")) {
            JsonObject n = e.getAsJsonObject();
            String role = string(n, "role", "");
            if (role.equals("control") || role.equals("memory") || role.equals("testimony")) putNode(out, s, n, role);
        }
        for (String name : new String[] { "lootPlan7", "spawnerPlan", "puzzleObjects7" })
            for (JsonElement e : array(m, name)) {
                JsonObject n = e.getAsJsonObject();
                putNode(
                    out,
                    s,
                    n,
                    name.equals("lootPlan7") ? "chest" : name.equals("spawnerPlan") ? "spawner" : "puzzle-object");
            }
        for (JsonElement e : array(m, "testimonyPedestals")) putNode(out, s, e.getAsJsonObject(), "testimony");
        for (JsonElement e : array(m, "shapeMechanisms")) {
            JsonObject shape = e.getAsJsonObject();
            JsonObject n = new JsonObject();
            n.addProperty(
                "id",
                "shape:" + shape.get("id")
                    .getAsString());
            n.addProperty("role", "shape");
            JsonArray p = shape.getAsJsonArray("control");
            n.addProperty(
                "x",
                p.get(0)
                    .getAsInt());
            n.addProperty(
                "y",
                p.get(1)
                    .getAsInt());
            n.addProperty(
                "z",
                p.get(2)
                    .getAsInt());
            n.addProperty("label", string(shape, "title", "现场工序"));
            n.addProperty("block", material(s.plan(), integer(n, "x", 0), integer(n, "y", 0), integer(n, "z", 0)));
            putNode(out, s, n, "shape");
        }
        List<JsonObject> result = java.util.Collections.unmodifiableList(new ArrayList<>(out.values()));
        NODE_CACHE.put(cacheKey, result);
        return result;
    }

    public static final class FinaleTicker {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void worldTick(cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent event) {
            if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END || event.world.isRemote) return;
            // Entity-only chunk loading after a restart need not load any of the engineering consoles.
            if (event.world.loadedEntityList != null) for (Object loaded : event.world.loadedEntityList) {
                if (!(loaded instanceof EntityOldEcho)) continue;
                EntityOldEcho entity = (EntityOldEcho) loaded;
                String owner = entity.getEncounterId();
                if (entity.getKind() == EchoKind.DO02 && owner.startsWith("echo:r7:")) {
                    FINALE_SITES.computeIfAbsent(event.world, k -> new java.util.HashSet<>())
                        .add(owner);
                }
            }
            java.util.Set<String> sites = FINALE_SITES.get(event.world);
            if (sites != null)
                for (String site : new ArrayList<>(sites)) RemasterEngineering.updateFinale(event.world, site);
        }
    }

    private static boolean engineeringFinished(TileRemasterNode tile) {
        RemasterData data = RemasterData.get(tile.getWorldObj());
        RemasterSite site = data.site(tile.siteId);
        return site != null && site.prefab.equals("fiction_expansion_project")
            && data.state(tile.siteId)
                .getCompoundTag("engineering")
                .getBoolean("final");
    }

    private static void disableEngineeringSource(TileRemasterNode tile) {
        RemasterData data = RemasterData.get(tile.getWorldObj());
        NBTTagCompound state = data.state(tile.siteId);
        String key = "spawner:" + tile.nodeId;
        if (!state.hasKey(key)) state.setTag(key, new NBTTagCompound());
        NBTTagCompound source = state.getCompoundTag(key);
        if (!source.getBoolean("permanentlyDisabled")) {
            source.setBoolean("permanentlyDisabled", true);
            source.setLong("next", Long.MAX_VALUE);
            source.setLong("unsealAt", Long.MAX_VALUE);
            data.flag(tile.siteId, "sealed:" + tile.nodeId, true);
        }
    }

    private static void putNode(Map<String, JsonObject> out, RemasterSite s, JsonObject source, String role) {
        JsonObject n = new com.google.gson.JsonParser().parse(source.toString())
            .getAsJsonObject();
        n.addProperty("role", role);
        int x = integer(n, "x", 0), y = integer(n, "y", 0), z = integer(n, "z", 0);
        String block = string(n, "block", material(s.plan(), x, y, z));
        if (!block.contains(":")) block = "gtsr:" + block;
        if (!block.contains("#")) block += "#0";
        // A readable plaque/pedestal needs its own tile even when the author used a native decorative support.
        if ((role.equals("memory") || role.equals("testimony")) && RemasterBlocks.get(block.split("#")[0]) == null) {
            block = "gtsr:draft_notice_board#0";
        }
        n.addProperty("block", block);
        out.put(x + "," + y + "," + z, n);
    }

    public static String material(RemasterPrefab p, int x, int y, int z) {
        for (RemasterPrefab.Run run : p.slice(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
            if (run.y == y && run.z == z && x >= run.x && x < run.x + run.length) return p.palette[run.paletteIndex];
        }
        return "minecraft:air#0";
    }

    public static JsonObject node(TileRemasterNode tile) {
        RemasterSite s = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        if (s == null) return null;
        for (JsonObject n : nodes(s))
            if (tile.nodeId.equals(string(n, "id", "")) && tile.xCoord == s.x + integer(n, "x", 0)
                && tile.yCoord == s.y + integer(n, "y", 0)
                && tile.zCoord == s.z + integer(n, "z", 0)
                && tile.role.equals(string(n, "role", ""))
                && tile.getWorldObj()
                    .getBlock(tile.xCoord, tile.yCoord, tile.zCoord) == resolve(string(n, "block", "")))
                return n;
        return null;
    }

    public static boolean valid(EntityPlayer player, TileRemasterNode tile) {
        World w = tile.getWorldObj();
        return player instanceof EntityPlayerMP && !(player instanceof FakePlayer)
            && !w.isRemote
            && player.worldObj == w
            && w.provider instanceof WorldProviderProsperityRuins
            && player.isEntityAlive()
            && !player.capabilities.isCreativeMode
            && player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) <= 64
            && w.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)
            && w.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile
            && node(tile) != null;
    }

    static JsonObject spec(TileRemasterNode tile) {
        RemasterSite s = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        if (s == null) return new JsonObject();
        if (RemasterEngineering.isEngineering(tile)) return RemasterEngineering.spec(tile);
        if ("shape".equals(tile.role)) {
            for (JsonElement e : array(s.plan().metadata, "shapeMechanisms")) {
                JsonObject v = e.getAsJsonObject();
                if (tile.nodeId.equals("shape:" + string(v, "id", ""))) return v;
            }
        }
        JsonObject m = s.plan().metadata;
        return m.has("productionPuzzle") ? m.getAsJsonObject("productionPuzzle") : new JsonObject();
    }

    private static String stateKey(TileRemasterNode tile) {
        return "shape".equals(tile.role) ? tile.nodeId : "site-puzzle";
    }

    private static boolean needsStability(JsonObject spec) {
        String kind = string(spec, "kind", "");
        return kind.equals("foundry") || kind.equals("pressure")
            || kind.equals("drain")
            || kind.equals("distill")
            || kind.equals("power");
    }

    static NBTTagCompound puzzleState(TileRemasterNode tile) {
        NBTTagCompound s = RemasterData.get(tile.getWorldObj())
            .state(tile.siteId);
        String key = stateKey(tile);
        if (!s.hasKey(key)) s.setTag(key, new NBTTagCompound());
        return s.getCompoundTag(key);
    }

    public static JsonObject view(TileRemasterNode tile) {
        JsonObject out = new JsonObject();
        JsonObject n = node(tile);
        if (n == null) return out;
        JsonObject spec = spec(tile);
        out.addProperty("title", string(n, "label", string(spec, "title", "现场工单")));
        out.addProperty("clue", string(n, "clue", string(n, "text", string(spec, "explain", "读取现场线索，核对工序。"))));
        JsonArray fields = new JsonArray();
        NBTTagCompound state = RemasterEngineering.isEngineering(tile) ? RemasterEngineering.state(tile)
            : puzzleState(tile);
        JsonArray authored = "memory".equals(tile.role) || "puzzle-object".equals(tile.role)
            || "testimony".equals(tile.role) ? new JsonArray() : array(spec, "fields");
        for (int i = 0; i < authored.size(); i++) {
            JsonArray f = authored.get(i)
                .getAsJsonArray();
            JsonArray choices = f.get(1)
                .getAsJsonArray();
            int value = Math.floorMod(state.getInteger("field" + i), choices.size());
            fields.add(
                new com.google.gson.JsonPrimitive(
                    f.get(0)
                        .getAsString() + "："
                        + choices.get(value)
                            .getAsString()));
        }
        out.add("fields", fields);
        out.addProperty(
            "status",
            state.getBoolean("solved") ? "现场已核验；已领取的奖励不会复位。"
                : "工序待核验 · 已校核 " + state.getInteger("progress")
                    + " · 连续稳定 "
                    + state.getInteger("stableTicks")
                    + "tick · "
                    + state.getString("feedback"));
        out.addProperty("readOnly", "memory".equals(tile.role) || "puzzle-object".equals(tile.role));
        if (RemasterEngineering.isEngineering(tile)) RemasterEngineering.decorateView(tile, out);
        return out;
    }

    public static boolean action(EntityPlayer player, TileRemasterNode tile, int button) {
        if (!valid(player, tile) || button < 0) return false;
        JsonObject n = node(tile);
        if ("testimony".equals(tile.role)) return button == 100 && RemasterEngineering.deposit(player, tile);
        if ("memory".equals(tile.role) || "puzzle-object".equals(tile.role)) {
            if (button != 100) return false;
            player.addChatMessage(
                new ChatComponentText(string(n, "clue", string(n, "text", string(n, "label", "现场记录")))));
            NBTTagCompound personal = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            personal.setBoolean("gtsr.r7.evidence:" + tile.siteId + ":" + tile.nodeId, true);
            player.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, personal);
            HistoryProgress.observeRemasterEvidence(player, tile.siteId, tile.nodeId);
            RemasterEngineering.readEvidence(player, tile);
            return true;
        }
        if (!"control".equals(tile.role) && !"shape".equals(tile.role)) return false;
        if (RemasterEngineering.isEngineering(tile)) return RemasterEngineering.action(player, tile, button);
        JsonObject spec = spec(tile);
        JsonArray fields = array(spec, "fields"), targets = array(spec, "answer");
        if (targets.size() == 0) targets = array(spec, "target");
        NBTTagCompound state = puzzleState(tile);
        if (button < fields.size()) {
            if (state.getBoolean("solved") && !applyShape(tile, false)) return false;
            int choices = fields.get(button)
                .getAsJsonArray()
                .get(1)
                .getAsJsonArray()
                .size();
            int value = Math.floorMod(state.getInteger("field" + button) + 1, choices);
            state.setInteger("field" + button, value);
            state.setBoolean("solved", false);
            state.setInteger("stableTicks", 0);
            state.setBoolean("pending", false);
            JsonArray order = array(spec, "order");
            int progress = state.getInteger("progress");
            if (order.size() > 0 && button < targets.size()) {
                if (value != targets.get(button)
                    .getAsInt()) progress = 0;
                else if (progress < order.size() && button == order.get(progress)
                    .getAsInt()) progress++;
                state.setInteger("progress", progress);
            }
        } else if (button == 101) {
            if (!applyShape(tile, false)) return false;
            for (int i = 0; i < fields.size(); i++) state.setInteger("field" + i, 0);
            state.setInteger("progress", 0);
            state.setBoolean("solved", false);
            state.setBoolean("pending", false);
            state.setInteger("stableTicks", 0);
        } else if (button == 100 || button == 102) {
            boolean ok = fields.size() > 0 && fields.size() == targets.size();
            for (int i = 0; i < targets.size(); i++) ok &= state.getInteger("field" + i) == targets.get(i)
                .getAsInt();
            JsonArray order = array(spec, "order");
            ok &= order.size() == 0 || state.getInteger("progress") == order.size();
            if (ok && needsStability(spec)) {
                state.setBoolean("pending", true);
                state.setString("feedback", "联锁成立，保持压强/水位200tick；改动参数会重新计时。");
            } else if (ok && applyShape(tile, true)) state.setBoolean("solved", true);
            else state.setString("feedback", "条件未齐或构件空间被占用；原工序保持。");
        } else return false;
        RemasterData.get(tile.getWorldObj())
            .markDirty();
        tile.refresh();
        updateHints(tile);
        return true;
    }

    private static boolean applyShape(TileRemasterNode tile, boolean open) {
        if (!"shape".equals(tile.role)) return true;
        World w = tile.getWorldObj();
        RemasterSite s = RemasterData.get(w)
            .site(tile.siteId);
        List<int[]> positions = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        List<Block> originalBlocks = new ArrayList<>();
        List<Integer> originalMetas = new ArrayList<>();
        for (JsonElement e : array(spec(tile), "delta")) {
            JsonObject d = e.getAsJsonObject();
            JsonArray at = d.getAsJsonArray("at");
            int x = s.x + at.get(0)
                .getAsInt(), y = s.y
                    + at.get(1)
                        .getAsInt(),
                z = s.z + at.get(2)
                    .getAsInt();
            if (!w.blockExists(x, y, z) || y < 1 || y > 254 || w.getTileEntity(x, y, z) != null) return false;
            String next = string(d, open ? "open" : "closed", "minecraft:air#0");
            String previous = string(d, open ? "closed" : "open", "minecraft:air#0");
            if (!matches(w, x, y, z, previous) && !matches(w, x, y, z, next)) return false;
            if (!next.startsWith("minecraft:air")
                && !w.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1))
                    .isEmpty())
                return false;
            // Resolve every replacement before mutating any block, so a bad registry cannot half-apply a mechanism.
            resolve(next);
            positions.add(new int[] { x, y, z });
            blocks.add(next);
            originalBlocks.add(w.getBlock(x, y, z));
            originalMetas.add(w.getBlockMetadata(x, y, z));
        }
        for (int i = 0; i < positions.size(); i++) {
            int[] p = positions.get(i);
            String next = blocks.get(i);
            if (!w.setBlock(p[0], p[1], p[2], resolve(next), meta(next), 2) && !matches(w, p[0], p[1], p[2], next)) {
                for (int j = i; j >= 0; j--) {
                    int[] old = positions.get(j);
                    w.setBlock(old[0], old[1], old[2], originalBlocks.get(j), originalMetas.get(j), 2);
                }
                return false;
            }
        }
        for (int[] p : positions) w.notifyBlocksOfNeighborChange(p[0], p[1], p[2], w.getBlock(p[0], p[1], p[2]));
        return true;
    }

    public static Block resolve(String key) {
        String id = key.indexOf('#') >= 0 ? key.substring(0, key.indexOf('#')) : key;
        Block block = RemasterBlocks.get(id);
        if (id.equals("minecraft:air")) block = Blocks.air;
        if (block == null && id.startsWith("gtsr:ruins_"))
            block = com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture
                .get(id.substring("gtsr:ruins_".length()));
        if (block == null && id.startsWith("gtsr:royal_"))
            block = com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture
                .get(id.substring("gtsr:royal_".length()));
        if (id.equals("gtsr:ruin_debris_rivet_plate")) block = com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinDebris;
        if (id.equals("gtsr:ruined_casing_rusted")) block = com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinedCasing;
        if (id.equals("gtsr:ProsperityZenithLog"))
            block = com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityZenithLog;
        if (id.equals("gtsr:ProsperityJadeLeaves"))
            block = com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityJadeLeaves;
        if (id.equals("gtsr:SealedChest")) block = ForgottenLakeEncounterRegistry.sealedChest;
        if (id.equals("gt5u:CasingBronzePlated")) block = gregtech.api.GregTechAPI.sBlockCasings1;
        if (id.equals("gt5u:CasingSolidSteel")) block = gregtech.api.GregTechAPI.sBlockCasings2;
        if (block == null && Block.blockRegistry.containsKey(id)) block = (Block) Block.blockRegistry.getObject(id);
        if (block == null) throw new IllegalArgumentException("Missing remaster material " + key);
        return block;
    }

    public static int meta(String key) {
        if (key.startsWith("gtsr:ruin_debris_rivet_plate")) return 2;
        return key.indexOf('#') >= 0 ? Integer.parseInt(key.substring(key.indexOf('#') + 1)) : 0;
    }

    private static boolean matches(World w, int x, int y, int z, String key) {
        return w.getBlock(x, y, z) == resolve(key) && w.getBlockMetadata(x, y, z) == meta(key);
    }

    public static boolean solved(World w, String id, String target) {
        RemasterData data = RemasterData.get(w);
        if (data.site(id) == null) return false;
        if (target.equals("fiction")) return data.state(id)
            .getCompoundTag("engineering")
            .getBoolean("final");
        return data.state(id)
            .getCompoundTag(target)
            .getBoolean("solved");
    }

    static void updateHints(TileRemasterNode tile) {
        World w = tile.getWorldObj();
        RemasterSite s = RemasterData.get(w)
            .site(tile.siteId);
        for (JsonElement e : array(s.plan().metadata, "puzzleObjects7")) {
            JsonObject h = e.getAsJsonObject();
            int x = s.x + integer(h, "x", 0), y = s.y + integer(h, "y", 0), z = s.z + integer(h, "z", 0);
            if (!w.blockExists(x, y, z)) continue;
            boolean done = hintComplete(w, s.id(), h);
            String key = string(h, done ? "completedBlock" : "block", "");
            if (!key.isEmpty() && w.getBlock(x, y, z) == resolve(key))
                w.setBlockMetadataWithNotify(x, y, z, meta(key), 3);
        }
    }

    public static void initializeLoaded(TileRemasterNode tile) {
        if (node(tile) == null) return;
        RemasterSite owner = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        if (owner != null && owner.prefab.equals("fiction_expansion_project")) {
            FINALE_SITES.computeIfAbsent(tile.getWorldObj(), k -> new java.util.HashSet<>())
                .add(tile.siteId);
            RemasterEngineering.updateFinale(tile.getWorldObj(), tile.siteId);
        }
        if (tile.role.equals("spawner")) {
            if (engineeringFinished(tile)) {
                disableEngineeringSource(tile);
                tile.refresh();
                return;
            }
            NBTTagCompound state = RemasterData.get(tile.getWorldObj())
                .state(tile.siteId);
            String key = "spawner:" + tile.nodeId;
            if (!state.hasKey(key)) state.setTag(key, new NBTTagCompound());
            state.getCompoundTag(key)
                .setLong(
                    "next",
                    tile.getWorldObj()
                        .getTotalWorldTime() + 100);
            RemasterData.get(tile.getWorldObj())
                .markDirty();
        }
        if (tile.role.equals("puzzle-object")) synchronizeHint(tile);
        tile.refresh();
    }

    private static void synchronizeHint(TileRemasterNode tile) {
        JsonObject h = node(tile);
        if (h == null) return;
        boolean done = hintComplete(tile.getWorldObj(), tile.siteId, h);
        String key = string(h, done ? "completedBlock" : "block", "");
        if (!key.isEmpty() && tile.getBlockMetadata() != meta(key)) {
            tile.getWorldObj()
                .setBlockMetadataWithNotify(tile.xCoord, tile.yCoord, tile.zCoord, meta(key), 3);
        }
    }

    private static boolean hintComplete(World w, String siteId, JsonObject hint) {
        if (string(hint, "completion", "").equals("fiction")) {
            String target = string(hint, "target", "");
            if (target.startsWith("fiction-station-")) {
                String index = target.substring("fiction-station-".length());
                return RemasterData.get(w)
                    .state(siteId)
                    .getCompoundTag("engineering")
                    .getBoolean("chapter-complete:" + index);
            }
        }
        return solved(w, siteId, string(hint, "completion", "site-puzzle"));
    }

    private static void openChest(EntityPlayer player, TileRemasterNode tile) {
        World w = tile.getWorldObj();
        RemasterData data = RemasterData.get(w);
        JsonObject n = node(tile);
        if (n == null || data.flag(tile.siteId, "claimed:" + tile.nodeId)) return;
        String unlock = string(n, "unlock", "");
        boolean ready = "right-click".equals(unlock) && "exploration".equals(string(n, "kind", ""));
        String reference = string(n, "reference", "");
        if (unlock.equals("puzzle-completed"))
            ready = reference.startsWith("shape-") ? solved(w, tile.siteId, "shape:" + reference)
                : solved(w, tile.siteId, "site-puzzle");
        if (unlock.equals("boss-defeated")) ready = data.flag(tile.siteId, "dead:" + reference);
        if (unlock.equals("cluster-cleared"))
            ready = clusterCleared(w, data.site(tile.siteId), string(n, "reference", ""));
        if (!ready) {
            player.addChatMessage(new ChatComponentText("封印尚未解除，检查关联的工序或守位群。"));
            return;
        }
        int tier = integer(n, "tier", 1);
        boolean exploration = "exploration".equals(string(n, "kind", ""));
        if (tier < 1 || tier > 5 || exploration && tier != integer(n, "referenceTier", tier + 2) - 2) return;
        // The physical container is the one-shot reward. Generation never recreates this node.
        if (!w.setBlock(tile.xCoord, tile.yCoord, tile.zCoord, ForgottenLakeEncounterRegistry.unsealedChest, 2, 3))
            return;
        TileEntity opened = w.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord);
        if (!(opened instanceof TileEntityUnsealedChest)) throw new IllegalStateException("Missing unsealed container");
        TileEntityUnsealedChest chest = (TileEntityUnsealedChest) opened;
        RemasterLoot.fill(chest, data.site(tile.siteId), n);
        if (!exploration) issueWitnesses(w, data.site(tile.siteId), tile.nodeId, chest);
        data.flag(tile.siteId, "claimed:" + tile.nodeId, true);
        player.displayGUIChest((TileEntityUnsealedChest) opened);
        HistoryProgress.observeRemasterEvidence(player, tile.siteId, "chest:" + tile.nodeId);
    }

    private static void issueWitnesses(World w, RemasterSite site, String event, TileEntityUnsealedChest chest) {
        RemasterData data = RemasterData.get(w);
        int slot = 0;
        for (JsonElement e : RemasterWitness.roster()) {
            String code = e.getAsString();
            if (!RemasterWitness.sourceAllows(code, site) || data.flag(site.id(), "witness-issued-once:" + code))
                continue;
            if (code.startsWith("dc-") && !data.flag(site.id(), "boss-dead:" + code)) continue;
            ItemStack witness = RemasterWitness.create(site, code, event);
            if (witness == null) continue;
            while (slot >= 10 && slot <= 15) slot++;
            if (slot >= chest.getSizeInventory()) break;
            chest.setInventorySlotContents(slot++, witness);
            data.flag(site.id(), "witness-issued:" + code + ":" + event, true);
            data.flag(site.id(), "witness-issued-once:" + code, true);
        }
    }

    public static boolean clusterCleared(World w, RemasterSite s, String reference) {
        if (s == null) return false;
        RemasterData data = RemasterData.get(w);
        for (JsonElement e : array(s.plan().metadata, "encounterClusters7")) {
            JsonObject cluster = e.getAsJsonObject();
            if (!reference.equals(string(cluster, "id", ""))) continue;
            JsonArray members = array(cluster, "members");
            if (members.size() == 0) return false;
            for (JsonElement member : members) if (!data.flag(s.id(), "dead:" + member.getAsString())) return false;
            return true;
        }
        return false;
    }

    public static void death(World w, String id, int nodeIndex) {
        RemasterData data = RemasterData.get(w);
        RemasterSite s = data.site(id);
        if (s == null) return;
        JsonArray spawns = array(s.plan().metadata, "spawns");
        if (nodeIndex < 0 || nodeIndex >= spawns.size()) return;
        JsonObject spawn = spawns.get(nodeIndex)
            .getAsJsonObject();
        data.flag(id, "dead:" + string(spawn, "id", Integer.toString(nodeIndex)), true);
        String code = string(spawn, "code", "");
        boolean boss = "boss".equals(string(spawn, "role", ""));
        if (boss) {
            data.flag(id, "boss-dead", true);
            data.flag(id, "boss-dead:" + code, true);
        }
        if (boss || code.equals("di-07") && s.prefab.equals("prosperity_city_full")) {
            if (RemasterWitness.sourceAllows(code, s) && !data.flag(id, "witness-issued-once:" + code)) {
                String spawnId = string(spawn, "id", "");
                String event = (boss ? "boss:" : "guard:") + spawnId;
                ItemStack witness = RemasterWitness.create(s, code, event);
                if (witness != null) {
                    NBTTagCompound position = data.state(id)
                        .getCompoundTag("spawn-pos:" + spawnId);
                    net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(
                        w,
                        position.hasKey("x") ? position.getDouble("x") : s.x + integer(spawn, "x", 0) + .5,
                        position.hasKey("y") ? position.getDouble("y") : s.y + integer(spawn, "y", 0),
                        position.hasKey("z") ? position.getDouble("z") : s.z + integer(spawn, "z", 0) + .5,
                        witness);
                    if (w.spawnEntityInWorld(drop)) {
                        data.flag(id, "witness-issued:" + code + ":" + event, true);
                        data.flag(id, "witness-issued-once:" + code, true);
                    }
                }
            }
        }
    }

    public static boolean bossReady(World w, String id) {
        return solved(w, id, "site-puzzle");
    }

    public static void tick(TileRemasterNode tile) {
        World w = tile.getWorldObj();
        if (tile.role.equals("spawner") && engineeringFinished(tile)) {
            disableEngineeringSource(tile);
            return;
        }
        if (tile.role.equals("memory") && w.getTotalWorldTime() % 20 == 0) {
            RemasterData data = RemasterData.get(w);
            RemasterSite site = data.site(tile.siteId);
            if (site != null && site.prefab.equals("forgotten_lake_court")) {
                String legacy = data.state(site.id())
                    .getString("legacyEncounter");
                if (!legacy.isEmpty()
                    && com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterData.get(w)
                        .kingDead(legacy)
                    && !data.flag(site.id(), "witness-issued-once:dc-10")) {
                    ItemStack witness = RemasterWitness.create(site, "dc-10", "legacy-king");
                    net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(
                        w,
                        tile.xCoord + .5,
                        tile.yCoord + 1,
                        tile.zCoord + .5,
                        witness);
                    if (w.spawnEntityInWorld(drop)) {
                        data.flag(site.id(), "boss-dead:dc-10", true);
                        data.flag(site.id(), "witness-issued:dc-10:legacy-king", true);
                        data.flag(site.id(), "witness-issued-once:dc-10", true);
                    }
                }
            }
        }
        if (RemasterEngineering.isEngineering(tile)) RemasterEngineering.tick(tile);
        if (w.getTotalWorldTime() % 20 == 0 && tile.role.equals("puzzle-object")) synchronizeHint(tile);
        if (tile.role.equals("control") && !RemasterEngineering.isEngineering(tile)) {
            NBTTagCompound puzzle = puzzleState(tile);
            if (puzzle.getBoolean("pending") && !puzzle.getBoolean("solved")) {
                long now = w.getTotalWorldTime();
                if (puzzle.getLong("lastTick") != now) {
                    if (puzzle.hasKey("lastTick") && now - puzzle.getLong("lastTick") > 1)
                        puzzle.setInteger("stableTicks", 0);
                    puzzle.setLong("lastTick", now);
                    puzzle.setInteger("pressure", Math.min(40, puzzle.getInteger("pressure") + 1));
                    puzzle.setInteger("waterLevel", Math.max(20, puzzle.getInteger("waterLevel") - 1));
                    int stable = puzzle.getInteger("pressure") == 40 && puzzle.getInteger("waterLevel") == 20
                        ? puzzle.getInteger("stableTicks") + 1
                        : 0;
                    puzzle.setInteger("stableTicks", stable);
                    if (stable >= 200) {
                        puzzle.setBoolean("solved", true);
                        puzzle.setBoolean("pending", false);
                        puzzle.setString("feedback", "压强40、水位20连续稳定，联锁验收完成。");
                        updateHints(tile);
                    }
                    RemasterData.get(w)
                        .markDirty();
                    if (now % 20 == 0) tile.refresh();
                }
            }
        }
        if (!"spawner".equals(tile.role) || w.getTotalWorldTime() % 20 != 0 || node(tile) == null) return;
        JsonObject n = node(tile), policy = n.has("policy") ? n.getAsJsonObject("policy") : new JsonObject();
        RemasterData data = RemasterData.get(w);
        NBTTagCompound state = data.state(tile.siteId);
        String k = "spawner:" + tile.nodeId;
        if (!state.hasKey(k)) state.setTag(k, new NBTTagCompound());
        NBTTagCompound spawner = state.getCompoundTag(k);
        long now = w.getTotalWorldTime();
        if (data.flag(tile.siteId, "sealed:" + tile.nodeId)) {
            if (now < spawner.getLong("unsealAt")) return;
            data.flag(tile.siteId, "sealed:" + tile.nodeId, false);
            spawner.setInteger("count", 0);
            spawner.setLong("next", now + 100);
        }
        EntityPlayer near = w.getClosestPlayer(
            tile.xCoord + .5,
            tile.yCoord + .5,
            tile.zCoord + .5,
            integer(policy, "activationDistance", 18));
        if (near == null || !near.isEntityAlive() || near.capabilities.isCreativeMode || near instanceof FakePlayer) {
            spawner.setLong("next", now + 100);
            data.markDirty();
            return;
        }
        if (!spawner.hasKey("next")) spawner.setLong("next", now + 100);
        if (now < spawner.getLong("next")) return;
        spawner.setLong("next", now + integer(policy, "intervalTicks", 100));
        int quota = integer(policy, "sealAt", 10),
            batch = Math.min(integer(policy, "batch", 2), quota - spawner.getInteger("count"));
        JsonObject zone = n.getAsJsonObject("spawnZone");
        if (zone == null) return;
        JsonArray center = zone.getAsJsonArray("center");
        RemasterSite s = data.site(tile.siteId);
        EchoKind kind = EchoKind.byCode(string(n, "code", ""));
        String spawnCode = string(n, "code", "");
        if (kind == null || !(spawnCode.equals("dr-02") || spawnCode.equals("dr-04")
            || spawnCode.equals("dr-08")
            || spawnCode.equals("dr-15")
            || spawnCode.equals("dr-18"))) return;
        for (int i = 0; i < batch; i++) {
            double x = s.x + center.get(0)
                .getAsDouble() + (i % 3 - 1) * (kind.width + .5), y = s.y
                    + center.get(1)
                        .getAsDouble(),
                z = s.z + center.get(2)
                    .getAsDouble() + (i / 3) * (kind.width + .5);
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            if (!w.blockExists(bx, by, bz) || !w.blockExists(bx - 2, by, bz - 2)
                || !w.blockExists(bx + 2, by + 3, bz + 2)) continue;
            EntityOldEcho entity = new EntityOldEcho(w);
            entity.initializeEcho(kind, tile.siteId + ":spawn:" + tile.nodeId, x + .5, y, z + .5, false);
            entity.setNodeIndex(-1);
            if (!w.getCollidingBoundingBoxes(entity, entity.boundingBox)
                .isEmpty()) continue;
            boolean aerial = zone.has("clearance")
                && "aerial".equals(string(zone.getAsJsonObject("clearance"), "mode", "ground"));
            if (!aerial && !w.isSideSolid(bx, by - 1, bz, net.minecraftforge.common.util.ForgeDirection.UP)) continue;
            if (w.spawnEntityInWorld(entity)) spawner.setInteger("count", spawner.getInteger("count") + 1);
        }
        if (spawner.getInteger("count") >= quota) {
            data.flag(tile.siteId, "sealed:" + tile.nodeId, true);
            spawner.setLong("unsealAt", now + integer(policy, "unsealAfterTicks", 36000));
        }
        data.markDirty();
        tile.refresh();
    }
}
