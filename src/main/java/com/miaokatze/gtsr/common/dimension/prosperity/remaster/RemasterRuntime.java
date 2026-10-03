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
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
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
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new RetryTicker());
        GameRegistry.registerItem(RemasterWitness.ITEM, "RemasterWitness7");
        GameRegistry.registerTileEntity(TileRemasterNode.class, "gtsr.remasterNode7");
        RemasterBlocks.setInteractionHandler(new RemasterBlocks.InteractionHandler() {

            public boolean activate(World w, int x, int y, int z, EntityPlayer player) {
                return activateNode(w, x, y, z, player);
            }

            public TileEntity createTileEntity(World w, int meta, String id) {
                return new TileRemasterNode();
            }

            public float hardness(World w, int x, int y, int z, float fallback) {
                TileEntity t = w.getTileEntity(x, y, z);
                if (!(t instanceof TileRemasterNode)) return 3;
                TileRemasterNode tile = (TileRemasterNode) t;
                if (node(tile) == null || "ambient-notice".equals(tile.role)) return fallback;
                if (!"spawner".equals(tile.role)) return -1;
                return RemasterData.get(w)
                    .flag(tile.siteId, "sealed:" + tile.nodeId) ? 100 : -1;
            }
        });
    }

    /** The exact block callback, separated from FML registration so world-generation integration can exercise it. */
    public static boolean activateNode(World world, int x, int y, int z, EntityPlayer player) {
        if (player instanceof FakePlayer || !(player instanceof EntityPlayerMP) && !world.isRemote) return false;
        if (world.isRemote) return true;
        TileEntity t = world.getTileEntity(x, y, z);
        if (!(t instanceof TileRemasterNode)) return false;
        TileRemasterNode tile = (TileRemasterNode) t;
        if (paused(world, tile.siteId) && presentPlayer(player, tile)) {
            player.addChatMessage(new ChatComponentText("此场景暂缓，当前开放三Boss。"));
            return true;
        }
        if (!valid(player, tile)) {
            player.addChatMessage(new ChatComponentText("此设备当前不可操作。请沿灯光和楼梯前进。"));
            return true;
        }
        if ("ambient-notice".equals(tile.role)) return action(player, tile, 100);
        if ("chest".equals(tile.role)) openChest(player, tile);
        else {
            if (navigation(node(tile))) action(player, tile, 100);
            tile.refresh();
            player.openGui(AggregatorGuiHandler.modInstance(), 1, world, x, y, z);
        }
        return true;
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
        for (JsonElement e : array(m, "navigationHints")) putNode(out, s, e.getAsJsonObject(), "memory");
        if (m.has("productionSiteController"))
            putNode(out, s, m.getAsJsonObject("productionSiteController"), "control");
        else if (m.has("productionPuzzle")) {
            JsonObject puzzle = m.getAsJsonObject("productionPuzzle");
            String kind = string(puzzle, "kind", "");
            if ((kind.equals("archive") || kind.equals("evidence")) && array(puzzle, "fields").size() > 0) {
                JsonObject record = null;
                for (JsonObject candidate : out.values()) {
                    String id = string(candidate, "id", "");
                    if ("memory".equals(string(candidate, "role", ""))
                        && (id.equals("objective-2") || id.equals("cache-clue"))) {
                        record = candidate;
                        break;
                    }
                }
                if (record != null) {
                    JsonObject controller = new com.google.gson.JsonParser().parse(record.toString())
                        .getAsJsonObject();
                    controller.addProperty("evidenceRecordId", string(record, "id", ""));
                    controller.addProperty("id", "site-puzzle-controller");
                    controller.addProperty("block", "gtsr:draft_pressure_console#0");
                    putNode(out, s, controller, "control");
                }
            }
        }
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
        Map<String, JsonObject> ambient = new LinkedHashMap<>();
        for (JsonElement e : array(m, "productionAmbientNotices"))
            putNode(ambient, s, e.getAsJsonObject(), "ambient-notice");
        // Remove collisions before appending: LinkedHashMap replacement would reorder formal records.
        for (String coordinate : out.keySet()) ambient.remove(coordinate);
        ambient.putAll(out);
        List<JsonObject> result = java.util.Collections.unmodifiableList(new ArrayList<>(ambient.values()));
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
                if (entity.getKind() == EchoKind.DO02 && active(event.world, owner)) {
                    FINALE_SITES.computeIfAbsent(event.world, k -> new java.util.HashSet<>())
                        .add(owner);
                }
            }
            java.util.Set<String> sites = FINALE_SITES.get(event.world);
            if (sites != null) for (String site : new ArrayList<>(sites))
                if (active(event.world, site)) RemasterEngineering.updateFinale(event.world, site);
        }
    }

    /** Bounded, loaded-owner retries; successful entity/chest ledgers remain authoritative. */
    public static final class RetryTicker {

        private final Map<World, Integer> cursors = new java.util.WeakHashMap<>();

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void worldTick(cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent event) {
            World world = event.world;
            if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END || world.isRemote
                || !(world.provider instanceof WorldProviderProsperityRuins)
                || world.getTotalWorldTime() % 40 != 0) return;
            List<RemasterData.GeneratedOwner> owners = RemasterData.get(world)
                .retryOwners();
            if (owners.isEmpty()) return;
            int start = Math.floorMod(cursors.getOrDefault(world, 0), owners.size()), scanned = 0, completed = 0;
            while (scanned < Math.min(64, owners.size()) && completed < 16) {
                RemasterData.GeneratedOwner owner = owners.get((start + scanned) % owners.size());
                scanned++;
                if (!RemasterRollout.allowsGeneration(owner.site)) continue;
                if (!world.getChunkProvider()
                    .chunkExists(owner.x, owner.z)) continue;
                RemasterWorldgen.completeChunk(world, owner.site, owner.x, owner.z);
                completed++;
            }
            cursors.put(world, (start + scanned) % owners.size());
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
        RemasterBlock authored = RemasterBlocks.get(block.split("#")[0]);
        if ((role.equals("memory") || role.equals("testimony"))
            && (authored == null || !authored.hasTileEntity(meta(block)))) {
            block = "gtsr:draft_notice_board#0";
        }
        if ((role.equals("control") || role.equals("shape"))
            && (authored == null || !authored.hasTileEntity(meta(block)))) block = "gtsr:draft_pressure_console#0";
        if (role.equals("chest")) block = "gtsr:SealedChest#2";
        n.addProperty("block", block);
        out.put(x + "," + y + "," + z, n);
    }

    public static String material(RemasterPrefab p, int x, int y, int z) {
        for (RemasterPrefab.Run run : p.slice(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
            if (run.y == y && run.z == z && x >= run.x && x < run.x + run.length) return p.palette[run.paletteIndex];
        }
        return "minecraft:air#0";
    }

    /** Called only for the new tile identity produced by an admitted natural geometry write. */
    public static void recordGeneratedTile(World world, RemasterSite site, int x, int y, int z, TileEntity before) {
        if (!RemasterRollout.allowsGeneration(site)) return;
        TileEntity current = world.getTileEntity(x, y, z);
        if (world.isRemote || current == null || current == before || current.getWorldObj() != world) return;
        if (current instanceof TileRemasterNode) {
            TileRemasterNode node = (TileRemasterNode) current;
            if (node.siteId.isEmpty() && node.nodeId.isEmpty() && node.role.isEmpty()) recordOrigin(current, site);
        } else if (current instanceof TileEntitySealedChest) {
            TileEntitySealedChest chest = (TileEntitySealedChest) current;
            if (chest.getRemasterSite()
                .isEmpty()
                && chest.getRemasterNode()
                    .isEmpty()
                && chest.getEncounterId()
                    .isEmpty()
                && chest.getOpeningTicks() < 0) recordOrigin(current, site);
        }
    }

    private static void recordOrigin(TileEntity tile, RemasterSite site) {
        NBTTagCompound origin = new NBTTagCompound();
        origin.setString("site", site.id());
        origin.setInteger("x", tile.xCoord);
        origin.setInteger("y", tile.yCoord);
        origin.setInteger("z", tile.zCoord);
        if (tile instanceof TileRemasterNode) ((TileRemasterNode) tile).geometryOrigin = origin;
        else((TileEntitySealedChest) tile).remasterGeometryOrigin = origin;
        tile.markDirty();
    }

    static boolean generatedFor(TileEntity tile, RemasterSite site) {
        NBTTagCompound origin = tile instanceof TileRemasterNode ? ((TileRemasterNode) tile).geometryOrigin
            : ((TileEntitySealedChest) tile).remasterGeometryOrigin;
        return site.id()
            .equals(origin.getString("site")) && tile.xCoord == origin.getInteger("x")
            && tile.yCoord == origin.getInteger("y")
            && tile.zCoord == origin.getInteger("z");
    }

    /** Recover a naturally created empty tile after a delayed save or chunk load, without touching geometry. */
    public static boolean initializeNatural(TileEntity tile) {
        World world = tile.getWorldObj();
        if (world == null || world.isRemote) return false;
        NBTTagCompound origin = tile instanceof TileRemasterNode ? ((TileRemasterNode) tile).geometryOrigin
            : tile instanceof TileEntitySealedChest ? ((TileEntitySealedChest) tile).remasterGeometryOrigin
                : new NBTTagCompound();
        RemasterData data = RemasterData.get(world);
        RemasterSite site = data.site(origin.getString("site"));
        if (!RemasterRollout.allowsGeneration(site) || !generatedFor(tile, site)) return false;
        for (JsonObject node : nodes(site)) {
            if (!atNode(tile, site, node)) continue;
            if (!installNode(world, site, node)) return false;
            data.flag(site.id(), "node:" + string(node, "id", ""), true);
            return true;
        }
        return false;
    }

    /** World placement uses the saved natural tree anchor, never the clamped scene anchor. */
    public static int[] nodePosition(World world, RemasterSite site, JsonObject node) {
        if ("tree-overlay".equals(site.layout) && RemasterData.get(world)
            .legacyTreeLayout(site)) {
            int[] legacy = legacyTreeNode(string(node, "id", ""));
            if (legacy != null) return new int[] { site.x + legacy[0], site.y + legacy[1], site.z + legacy[2] };
            return new int[] { site.x + integer(node, "x", 0), site.y + integer(node, "y", 0),
                site.z + integer(node, "z", 0) };
        }
        JsonObject metadata = site.plan().metadata;
        JsonObject binding = metadata.has("legacyEntranceBinding") && metadata.get("legacyEntranceBinding")
            .isJsonObject() ? metadata.getAsJsonObject("legacyEntranceBinding") : null;
        if (binding != null && string(binding, "node", "").equals(string(node, "id", ""))) {
            NBTTagCompound state = RemasterData.get(world)
                .state(site.id());
            if (!state.hasKey("legacyAnchorX") || !state.hasKey("legacyAnchorY") || !state.hasKey("legacyAnchorZ"))
                return null;
            JsonArray offset = array(binding, "anchorOffset");
            if (offset.size() != 3) return null;
            return new int[] { state.getInteger("legacyAnchorX") + offset.get(0)
                .getAsInt(), state.getInteger("legacyAnchorY")
                    + offset.get(1)
                        .getAsInt(),
                state.getInteger("legacyAnchorZ") + offset.get(2)
                    .getAsInt() };
        }
        return new int[] { site.x + integer(node, "x", 0), site.y + integer(node, "y", 0),
            site.z + integer(node, "z", 0) };
    }

    /** Frozen physical coordinates from the released v66 royal prefab (7bdf411). */
    private static int[] legacyTreeNode(String id) {
        switch (id) {
            case "entrance-story-board":
                return new int[] { 156, 5, 24 };
            case "loot7-8":
                return new int[] { 224, 89, 221 };
            case "loot7-9":
                return new int[] { 254, 89, 221 };
            case "loot7-10":
                return new int[] { 224, 89, 225 };
            case "loot7-26":
                return new int[] { 66, 107, 221 };
            case "loot7-34":
                return new int[] { 63, 116, 146 };
            case "loot7-35":
                return new int[] { 63, 116, 150 };
            case "loot7-41":
                return new int[] { 66, 125, 63 };
            case "loot7-42":
                return new int[] { 96, 125, 63 };
            case "loot7-53":
                return new int[] { 224, 143, 63 };
            case "loot7-54":
                return new int[] { 254, 143, 63 };
            default:
                return null;
        }
    }

    private static boolean atNode(TileEntity tile, RemasterSite site, JsonObject node) {
        int[] position = nodePosition(tile.getWorldObj(), site, node);
        return position != null && tile.xCoord == position[0]
            && tile.yCoord == position[1]
            && tile.zCoord == position[2];
    }

    /** Install only after geometry admission. Existing empty player tiles never imply natural provenance. */
    public static boolean installNode(World world, RemasterSite site, JsonObject node) {
        if (world.isRemote || !RemasterRollout.allowsGeneration(site)) return false;
        int[] position = nodePosition(world, site, node);
        if (position == null) return false;
        int x = position[0], y = position[1], z = position[2];
        if (world.isRemote || y < 1 || y >= world.getActualHeight() - 1 || !world.blockExists(x, y, z)) return false;
        String role = string(node, "role", ""), id = string(node, "id", ""), key = string(node, "block", "");
        if (id.isEmpty() || role.isEmpty()) return false;
        if (role.equals("chest") && RemasterData.get(world)
            .flag(site.id(), "claimed:" + id)) return true;
        Block block = resolve(key);
        TileEntity prior = world.getTileEntity(x, y, z);
        // Ambient reading may adopt admitted natural geometry, never rebuild a removed notice.
        if (role.equals("ambient-notice") && prior == null) return false;
        JsonObject metadata = site.plan().metadata;
        if (prior == null && metadata.has("legacyEntranceBinding")
            && metadata.get("legacyEntranceBinding")
                .isJsonObject()
            && id.equals(string(metadata.getAsJsonObject("legacyEntranceBinding"), "node", ""))) return false;
        if (prior == null && RemasterData.get(world)
            .flag(site.id(), "node:" + id)) return false;
        if (prior instanceof TileRemasterNode) {
            TileRemasterNode existing = (TileRemasterNode) prior;
            if (!role.equals("chest") && existing.siteId.isEmpty()
                && existing.nodeId.isEmpty()
                && existing.role.isEmpty()
                && generatedFor(existing, site)
                && matches(world, x, y, z, key)) {
                existing.initialize(site.id(), id, role);
                existing.geometryOrigin = new NBTTagCompound();
                existing.markDirty();
                return true;
            }
            return site.id()
                .equals(existing.siteId) && id.equals(existing.nodeId)
                && role.equals(existing.role)
                && (world.getBlock(x, y, z) == block
                    || role.equals("chest") && world.getBlock(x, y, z) == RemasterBlocks.get("gtsr:draft7_seal_chest"))
                && node(existing) != null;
        }
        if (prior instanceof TileEntitySealedChest && role.equals("chest")) {
            TileEntitySealedChest chest = (TileEntitySealedChest) prior;
            if (chest.getRemasterSite()
                .isEmpty()
                && chest.getRemasterNode()
                    .isEmpty()
                && generatedFor(chest, site)
                && matches(world, x, y, z, key)) {
                chest.initializeRemaster(integer(node, "tier", 1), site.id(), id);
                chest.remasterGeometryOrigin = new NBTTagCompound();
                chest.markDirty();
                return true;
            }
            return chest.isRemasterNode(site.id(), id) && chest.getTier() == integer(node, "tier", 1)
                && matches(world, x, y, z, key);
        }
        if (prior != null) return false;
        if (!world.setBlock(x, y, z, block, meta(key), 3) && !matches(world, x, y, z, key)) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (role.equals("chest")) {
            if (!(tile instanceof TileEntitySealedChest)) return false;
            ((TileEntitySealedChest) tile).initializeRemaster(integer(node, "tier", 1), site.id(), id);
        } else {
            if (!(tile instanceof TileRemasterNode)) return false;
            ((TileRemasterNode) tile).initialize(site.id(), id, role);
        }
        return true;
    }

    public static JsonObject node(TileRemasterNode tile) {
        if (tile.getWorldObj() == null || tile.getWorldObj().isRemote) return null;
        RemasterSite s = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        if (!RemasterRollout.allowsGeneration(s)) return null;
        for (JsonObject n : nodes(s)) if (tile.nodeId.equals(string(n, "id", "")) && atNode(tile, s, n)
            && tile.role.equals(string(n, "role", ""))
            && (tile.getWorldObj()
                .getBlock(tile.xCoord, tile.yCoord, tile.zCoord) == resolve(string(n, "block", ""))
                || "chest".equals(tile.role) && tile.getWorldObj()
                    .getBlock(tile.xCoord, tile.yCoord, tile.zCoord) == RemasterBlocks.get("gtsr:draft7_seal_chest"))
            && ("puzzle-object".equals(tile.role)
                ? tile.getBlockMetadata() == meta(string(n, "block", ""))
                    || tile.getBlockMetadata() == meta(string(n, "completedBlock", string(n, "block", "")))
                : "chest".equals(tile.role) && tile.getBlockType() == RemasterBlocks.get("gtsr:draft7_seal_chest")
                    || tile.getBlockMetadata() == meta(string(n, "block", ""))))
            return n;
        return null;
    }

    public static boolean valid(EntityPlayer player, TileRemasterNode tile) {
        return presentPlayer(player, tile) && node(tile) != null;
    }

    private static boolean active(World world, String id) {
        return world != null && !world.isRemote
            && RemasterRollout.allowsGeneration(
                RemasterData.get(world)
                    .site(id));
    }

    private static boolean paused(World world, String id) {
        RemasterSite site = RemasterData.get(world)
            .site(id);
        return site != null && !RemasterRollout.allowsGeneration(site);
    }

    private static boolean presentPlayer(EntityPlayer player, TileEntity tile) {
        World w = tile.getWorldObj();
        return player instanceof EntityPlayerMP && !(player instanceof FakePlayer)
            && w != null
            && !w.isRemote
            && player.worldObj == w
            && w.provider instanceof WorldProviderProsperityRuins
            && player.isEntityAlive()
            && player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) <= 64
            && w.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)
            && w.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile;
    }

    static JsonObject spec(TileRemasterNode tile) {
        RemasterSite s = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        if (!RemasterRollout.allowsGeneration(s)) return new JsonObject();
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
        if (!active(tile.getWorldObj(), tile.siteId)) return new NBTTagCompound();
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
        if ("ambient-notice".equals(tile.role)) {
            out.addProperty("title", string(n, "label", "现场封锁告示"));
            out.addProperty("clue", string(n, "text", ""));
            out.addProperty("role", tile.role);
            out.addProperty("nodeId", tile.nodeId);
            out.addProperty("readOnly", true);
            out.addProperty("status", "此告示仅说明现场；请沿保留通路前往正式记录，不计入剧情证据或工序核验。");
            out.addProperty("actionLabel", "重读告示");
            out.add("fields", new JsonArray());
            absoluteGuideTarget(tile, n, out);
            return out;
        }
        if (RemasterSimpleScene.handles(tile)) return RemasterSimpleScene.view(tile, n);
        JsonObject spec = spec(tile);
        out.addProperty("title", string(n, "label", string(spec, "title", "现场工单")));
        RemasterSite originalOwner = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        out.addProperty("clue", RemasterOriginalContract.narrative(originalOwner, n, spec));
        out.addProperty("role", tile.role);
        out.addProperty("nodeId", tile.nodeId);
        absoluteGuideTarget(tile, n, out);
        if (navigation(n)) {
            out.addProperty("title", string(n, "label", "行进方向告示"));
            out.addProperty("actionLabel", "读取方向告示");
            out.addProperty("status", "沿告示方向前往下一段；此牌仅指路，不计作剧情证据或工序核验。");
            out.addProperty("readOnly", true);
            out.addProperty("solved", false);
            out.addProperty("navigationHint", true);
            out.add("fields", new JsonArray());
            return out;
        }
        JsonArray fields = new JsonArray();
        NBTTagCompound state = RemasterEngineering.isEngineering(tile) || "testimony".equals(tile.role)
            ? RemasterEngineering.state(tile)
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
        boolean guideDone = "puzzle-object".equals(tile.role) ? hintComplete(tile.getWorldObj(), tile.siteId, n)
            : state.getBoolean("solved");
        out.addProperty("solved", guideDone);
        out.addProperty(
            "readOnly",
            "memory".equals(tile.role) || "puzzle-object".equals(tile.role) || "spawner".equals(tile.role));
        out.addProperty(
            "actionLabel",
            "testimony".equals(tile.role) ? "归档原件"
                : "control".equals(tile.role) || "shape".equals(tile.role) ? "核验工序" : "读取现场记录");
        out.addProperty("feedback", state.getString("feedback"));
        if (n.has("evidenceRecordId")) {
            out.addProperty("title", string(spec, "title", "证据核验") + " · 原始记录终端");
            out.addProperty("requires", "先本人读取其余原始记录；本终端确认时记录当前证据，随后核验全部参数。读取本身不解除封印。");
        }
        if (!n.has("evidenceRecordId")
            && (string(spec, "kind", "").equals("archive") || string(spec, "kind", "").equals("evidence")))
            out.addProperty("requires", "本人先读取本站所有原始记录；多人分别取证，再根据原件核验参数。");
        if ("testimony".equals(tile.role)) {
            String code = string(n, "code", "");
            out.addProperty("solved", state.getBoolean("testimony:" + code));
            out.addProperty(
                "status",
                state.getBoolean("testimony:" + code) ? "此身份原件已归档。"
                    : "待归档 " + code + " · " + state.getString("feedback"));
            out.addProperty("requires", "第八章开放后，手持或携带对应身份、出处与发行账本可鉴真的原件。");
        }
        if ("spawner".equals(tile.role)) {
            NBTTagCompound source = RemasterData.get(tile.getWorldObj())
                .state(tile.siteId)
                .getCompoundTag("spawner:" + tile.nodeId);
            out.addProperty(
                "status",
                RemasterData.get(tile.getWorldObj())
                    .flag(tile.siteId, "sealed:" + tile.nodeId) ? "内源点封印中；已停产。"
                        : "内源点活跃 · 本轮成功生成 " + source.getInteger("count") + " · " + source.getString("feedback"));
        }
        if ("puzzle-object".equals(tile.role)) {
            RemasterSite site = RemasterData.get(tile.getWorldObj())
                .site(tile.siteId);
            String completion = string(n, "completion", "site-puzzle");
            String target = string(n, "target", "");
            for (JsonObject candidate : nodes(site)) {
                String candidateId = string(candidate, "id", "");
                if (candidateId.equals(completion) || candidateId.equals("shape:" + target)
                    || candidateId.equals(target)
                    || completion.equals("site-puzzle") && string(candidate, "role", "").equals("control")) {
                    int[] position = nodePosition(tile.getWorldObj(), site, candidate);
                    if (position == null) continue;
                    JsonArray at = new JsonArray();
                    for (int coordinate : position) at.add(new com.google.gson.JsonPrimitive(coordinate));
                    if (!out.has("guideTarget")) out.add("guideTarget", at);
                    break;
                }
            }
            out.addProperty("status", guideDone ? "关联工序已完成，指引已熄灭。" : "指引保持点亮；读取不计作工序完成。");
        }
        if (RemasterEngineering.isEngineering(tile)) RemasterEngineering.decorateView(tile, out);
        return out;
    }

    private static boolean navigation(JsonObject node) {
        return node != null && node.has("navigationHint")
            && node.get("navigationHint")
                .getAsBoolean();
    }

    private static void absoluteGuideTarget(TileRemasterNode tile, JsonObject node, JsonObject view) {
        JsonArray local = array(node, "guideTarget");
        if (local.size() != 3) return;
        RemasterSite site = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        if (site == null) return;
        JsonArray absolute = new JsonArray();
        absolute.add(
            new com.google.gson.JsonPrimitive(
                site.x + local.get(0)
                    .getAsInt()));
        absolute.add(
            new com.google.gson.JsonPrimitive(
                site.y + local.get(1)
                    .getAsInt()));
        absolute.add(
            new com.google.gson.JsonPrimitive(
                site.z + local.get(2)
                    .getAsInt()));
        view.add("guideTarget", absolute);
    }

    public static boolean action(EntityPlayer player, TileRemasterNode tile, int button) {
        if (!valid(player, tile) || button < 0) return false;
        JsonObject n = node(tile);
        if ("ambient-notice".equals(tile.role)) {
            if (button != 100) return false;
            player.addChatMessage(new ChatComponentText(string(n, "text", "")));
            return true;
        }
        if (player.capabilities.isCreativeMode && ("testimony".equals(tile.role)
            || RemasterEngineering.isEngineering(tile) && (button == 100 || button == 102))) {
            NBTTagCompound preview = RemasterEngineering.state(tile);
            preview.setString("feedback", "创造模式可检查现场；原件归档、材料托管与终局需生存模式验收。");
            RemasterData.get(tile.getWorldObj())
                .markDirty();
            tile.refresh();
            return false;
        }
        if ("testimony".equals(tile.role)) return button == 100 && RemasterEngineering.deposit(player, tile);
        if ("memory".equals(tile.role) || "puzzle-object".equals(tile.role)) {
            if (button != 100) return false;
            RemasterSite owner = RemasterData.get(tile.getWorldObj())
                .site(tile.siteId);
            player.addChatMessage(new ChatComponentText(RemasterOriginalContract.narrative(owner, n, spec(tile))));
            if (navigation(n)) return true;
            if ("memory".equals(tile.role)) RemasterOriginalContract.record(player, owner, tile.nodeId);
            NBTTagCompound personal = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            personal.setBoolean("gtsr.r7.evidence:" + tile.siteId + ":" + tile.nodeId, true);
            player.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, personal);
            HistoryProgress.observeRemasterEvidence(player, tile.siteId, tile.nodeId);
            RemasterEngineering.readEvidence(player, tile);
            JsonObject evidenceSpec = spec(tile);
            String kind = string(evidenceSpec, "kind", "");
            if (kind.equals("archive") || kind.equals("evidence")) {
                boolean complete = true;
                int required = 0;
                for (JsonObject record : nodes(owner)) {
                    String recordId = string(record, "id", "");
                    if (navigation(record) || !string(record, "role", "").equals("memory")
                        || recordId.equals("entrance-story-board")) continue;
                    required++;
                    complete &= personal.getBoolean("gtsr.r7.evidence:" + tile.siteId + ":" + recordId);
                }
                NBTTagCompound evidence = puzzleState(tile);
                evidence.setString(
                    "feedback",
                    complete && required > 0 ? "当前可读记录已读齐；请在原始记录终端读取末份并核验参数，读取不会解除封印。" : "尚有原始记录未读取，沿现场指引继续取证。");
                RemasterData.get(tile.getWorldObj())
                    .markDirty();
                tile.refresh();
                updateHints(tile);
            }
            return true;
        }
        if (!"control".equals(tile.role) && !"shape".equals(tile.role)) return false;
        if (RemasterSimpleScene.handles(tile)) return RemasterSimpleScene.action(player, tile, n, button);
        if (RemasterEngineering.isEngineering(tile)) return RemasterEngineering.action(player, tile, button);
        JsonObject spec = spec(tile);
        JsonArray fields = array(spec, "fields"), targets = array(spec, "answer");
        if (targets.size() == 0) targets = array(spec, "target");
        NBTTagCompound state = puzzleState(tile);
        if ((button == 100 || button == 102) && n.has("evidenceRecordId")) {
            NBTTagCompound personal = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            personal.setBoolean("gtsr.r7.evidence:" + tile.siteId + ":" + string(n, "evidenceRecordId", ""), true);
            player.getEntityData()
                .setTag(EntityPlayer.PERSISTED_NBT_TAG, personal);
        }
        if (button < fields.size()) {
            if (state.getBoolean("solved") && !applyShape(tile, false)) return false;
            if (!"shape".equals(tile.role)) RemasterOriginalContract.resetControls(
                tile.getWorldObj(),
                RemasterData.get(tile.getWorldObj())
                    .site(tile.siteId));
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
            if (!"shape".equals(tile.role)) RemasterOriginalContract.resetControls(
                tile.getWorldObj(),
                RemasterData.get(tile.getWorldObj())
                    .site(tile.siteId));
            for (int i = 0; i < fields.size(); i++) state.setInteger("field" + i, 0);
            state.setInteger("progress", 0);
            state.setBoolean("solved", false);
            state.setBoolean("pending", false);
            state.setInteger("stableTicks", 0);
        } else if (button == 100 || button == 102) {
            RemasterSite owner = RemasterData.get(tile.getWorldObj())
                .site(tile.siteId);
            int originalKind = RemasterOriginalContract.kind(owner);
            if (!"shape".equals(tile.role) && originalKind >= 0
                && originalKind < 7
                && (player.capabilities.isCreativeMode
                    || !RemasterOriginalContract.guardsCleared(tile.getWorldObj(), owner, n))) {
                state.setString(
                    "feedback",
                    player.capabilities.isCreativeMode ? "创造模式可检查现场；原守位与控制台确认需生存模式。" : "设备仍被残响控制，先清除本区守卫。");
                RemasterData.get(tile.getWorldObj())
                    .markDirty();
                tile.refresh();
                return false;
            }
            if (!"shape".equals(tile.role)
                && (string(spec, "kind", "").equals("archive") || string(spec, "kind", "").equals("evidence"))) {
                NBTTagCompound personal = player.getEntityData()
                    .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
                int required = 0, read = 0;
                for (JsonObject record : nodes(owner)) {
                    String recordId = string(record, "evidenceRecordId", string(record, "id", ""));
                    if (navigation(record)
                        || (!string(record, "role", "").equals("memory") && !record.has("evidenceRecordId"))
                        || recordId.equals("entrance-story-board")) continue;
                    required++;
                    if (personal.getBoolean("gtsr.r7.evidence:" + tile.siteId + ":" + recordId)) read++;
                }
                if (required > 0 && read < required) {
                    state.setString("feedback", "本人现场取证 " + read + "/" + required + "；请先读取未核对的原始记录。");
                    RemasterData.get(tile.getWorldObj())
                        .markDirty();
                    tile.refresh();
                    return false;
                }
            }
            boolean ok = fields.size() > 0 && fields.size() == targets.size();
            for (int i = 0; i < targets.size(); i++) ok &= state.getInteger("field" + i) == targets.get(i)
                .getAsInt();
            JsonArray order = array(spec, "order");
            ok &= order.size() == 0 || state.getInteger("progress") == order.size();
            if (ok && !"shape".equals(tile.role) && originalKind >= 0 && originalKind < 7)
                RemasterData.get(tile.getWorldObj())
                    .flag(tile.siteId, "original-control:" + tile.nodeId, true);
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

    static boolean applyShape(TileRemasterNode tile, boolean open) {
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
            if (!w.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 2, z + 1))
                .isEmpty()) return false;
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
        for (int[] p : positions) {
            w.notifyBlocksOfNeighborChange(p[0], p[1], p[2], w.getBlock(p[0], p[1], p[2]));
            w.markBlockForUpdate(p[0], p[1], p[2]);
            w.func_147451_t(p[0], p[1], p[2]);
        }
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
        if (id.equals("gtsr:ProsperityStone")) block = com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperityStone;
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
        if (!active(w, id)) return false;
        if (target.equals("site-puzzle") && RemasterSimpleScene.complete(w, data.site(id))) return true;
        if (target.equals("fiction")) return data.state(id)
            .getCompoundTag("engineering")
            .getBoolean("final");
        return data.state(id)
            .getCompoundTag(target)
            .getBoolean("solved");
    }

    static void updateHints(TileRemasterNode tile) {
        if (!active(tile.getWorldObj(), tile.siteId)) return;
        World w = tile.getWorldObj();
        RemasterSite s = RemasterData.get(w)
            .site(tile.siteId);
        for (JsonElement e : array(s.plan().metadata, "puzzleObjects7")) {
            JsonObject h = e.getAsJsonObject();
            int[] position = nodePosition(w, s, h);
            if (position == null) continue;
            int x = position[0], y = position[1], z = position[2];
            if (!w.blockExists(x, y, z)) continue;
            boolean done = hintComplete(w, s.id(), h);
            String key = string(h, done ? "completedBlock" : "block", "");
            if (!key.isEmpty() && w.getBlock(x, y, z) == resolve(key)) if (w.getBlockMetadata(x, y, z) != meta(key)) {
                w.setBlockMetadataWithNotify(x, y, z, meta(key), 3);
                w.func_147451_t(x, y, z);
            }
        }
    }

    public static void initializeLoaded(TileRemasterNode tile) {
        if (node(tile) == null) {
            invalidateDisplay(tile);
            return;
        }
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
            tile.getWorldObj()
                .func_147451_t(tile.xCoord, tile.yCoord, tile.zCoord);
        }
        tile.refresh();
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
        if (player.capabilities.isCreativeMode) {
            player.addChatMessage(new ChatComponentText("创造模式检查封印：一次奖励仅在生存模式领取。"));
            return;
        }
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

    private static JsonObject chestNode(TileEntitySealedChest tile) {
        World world = tile.getWorldObj();
        RemasterSite site = RemasterData.get(world)
            .site(tile.getRemasterSite());
        if (world.isRemote || !RemasterRollout.allowsGeneration(site)
            || world.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) != tile
            || world.getBlock(tile.xCoord, tile.yCoord, tile.zCoord) != ForgottenLakeEncounterRegistry.sealedChest)
            return null;
        for (JsonObject n : nodes(site)) if ("chest".equals(string(n, "role", "")) && tile.getRemasterNode()
            .equals(string(n, "id", ""))
            && atNode(tile, site, n)
            && tile.getTier() == integer(n, "tier", 1)
            && tile.getBlockMetadata() == meta(string(n, "block", ""))) return n;
        return null;
    }

    public static boolean chestReady(TileEntitySealedChest tile) {
        JsonObject n = chestNode(tile);
        if (n == null) return false;
        World w = tile.getWorldObj();
        RemasterData data = RemasterData.get(w);
        String site = tile.getRemasterSite(), unlock = string(n, "unlock", ""), reference = string(n, "reference", "");
        if (data.flag(site, "claimed:" + tile.getRemasterNode())) return false;
        int tier = integer(n, "tier", 1);
        boolean exploration = string(n, "kind", "").equals("exploration");
        if (tier < 1 || tier > 5 || exploration && tier != integer(n, "referenceTier", tier + 2) - 2) return false;
        if (unlock.equals("right-click")) return exploration;
        if (unlock.equals("puzzle-completed"))
            return solved(w, site, reference.startsWith("shape-") ? "shape:" + reference : "site-puzzle");
        if (unlock.equals("boss-defeated")) {
            RemasterSite owner = data.site(site);
            if (owner.prefab.equals("forgotten_lake_court")) {
                String legacy = data.state(site)
                    .getString("legacyEncounter");
                com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterData original = com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterData
                    .get(w);
                return !legacy.isEmpty() && original.known(legacy) && original.kingDead(legacy);
            }
            return data.flag(site, "dead:" + reference);
        }
        return unlock.equals("cluster-cleared") && clusterCleared(w, data.site(site), reference);
    }

    public static boolean chestClick(EntityPlayer player, TileEntitySealedChest tile) {
        World world = tile.getWorldObj();
        if (paused(world, tile.getRemasterSite()) && presentPlayer(player, tile)) {
            player.addChatMessage(new ChatComponentText("此场景暂缓，当前开放三Boss。"));
            return false;
        }
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer
            || world.isRemote
            || player.worldObj != world
            || !player.isEntityAlive()
            || !(world.provider instanceof WorldProviderProsperityRuins)
            || player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) > 64
            || chestNode(tile) == null) return false;
        boolean ready = chestReady(tile);
        JsonObject n = chestNode(tile);
        String prerequisite = string(n, "unlock", "") + " / " + string(n, "reference", "现场工序");
        RemasterSite owner = RemasterData.get(world)
            .site(tile.getRemasterSite());
        if (owner.prefab.equals("forgotten_lake_court") && string(n, "unlock", "").equals("cluster-cleared"))
            prerequisite = "原王庭守位账本：有平台标记按该室；无平台标记须八室32守位全部解除";
        if (player.capabilities.isCreativeMode) {
            player.addChatMessage(
                new ChatComponentText("封印检查：" + (ready ? "前置已齐" : "缺少前置 " + prerequisite) + "；一次奖励请在生存模式领取。"));
            return false;
        }
        player.addChatMessage(
            new ChatComponentText(ready ? "封印解除中，60tick后打开；奖励只生成一次。" : "封印尚未解除：" + prerequisite + "。沿现场工单完成关联事件。"));
        return ready;
    }

    public static boolean completeChest(TileEntitySealedChest tile) {
        if (!chestReady(tile)) return false;
        World world = tile.getWorldObj();
        JsonObject node = chestNode(tile);
        RemasterData data = RemasterData.get(world);
        RemasterSite site = data.site(tile.getRemasterSite());
        if (!world.setBlock(
            tile.xCoord,
            tile.yCoord,
            tile.zCoord,
            ForgottenLakeEncounterRegistry.unsealedChest,
            tile.getBlockMetadata(),
            3)) return false;
        TileEntity opened = world.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord);
        if (!(opened instanceof TileEntityUnsealedChest))
            throw new IllegalStateException("Missing remaster reward container");
        TileEntityUnsealedChest chest = (TileEntityUnsealedChest) opened;
        RemasterLoot.fill(chest, site, node);
        if (!string(node, "kind", "").equals("exploration")) issueWitnesses(world, site, tile.getRemasterNode(), chest);
        data.flag(site.id(), "claimed:" + tile.getRemasterNode(), true);
        chest.markDirty();
        world.markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
        return true;
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
        if (w.isRemote || !RemasterRollout.allowsGeneration(s)) return false;
        RemasterData data = RemasterData.get(w);
        for (JsonElement e : array(s.plan().metadata, "encounterClusters7")) {
            JsonObject cluster = e.getAsJsonObject();
            if (!reference.equals(string(cluster, "id", ""))) continue;
            JsonArray members = array(cluster, "members");
            if (members.size() == 0) return false;
            if (s.prefab.equals("forgotten_lake_court")) {
                String legacy = data.state(s.id())
                    .getString("legacyEncounter");
                com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterData original = com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterData
                    .get(w);
                if (legacy.isEmpty() || !original.known(legacy)) return false;
                int platform = integer(cluster, "platform", -1);
                return platform >= 0 && platform < 8 ? original.platformCleared(legacy, platform)
                    : original.allGuardsDead(legacy);
            }
            for (JsonElement member : members) if (!data.flag(s.id(), "dead:" + member.getAsString())) return false;
            return true;
        }
        return false;
    }

    public static void death(World w, String id, int nodeIndex) {
        RemasterData data = RemasterData.get(w);
        RemasterSite s = data.site(id);
        if (w.isRemote || !RemasterRollout.allowsGeneration(s)) return;
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
        if (!active(w, id)) return false;
        RemasterSite owner = RemasterData.get(w)
            .site(id);
        int kind = RemasterOriginalContract.kind(owner);
        boolean simple = RemasterSimpleScene.complete(w, owner);
        boolean legacy = dataLegacyComplete(w, owner);
        return (simple || legacy) && (kind < 0 || kind >= 7 || RemasterOriginalContract.guardsCleared(w, owner, null));
    }

    private static boolean dataLegacyComplete(World w, RemasterSite owner) {
        return RemasterData.get(w)
            .state(owner.id())
            .getCompoundTag("site-puzzle")
            .getBoolean("solved") && RemasterOriginalContract.controlsConfirmed(w, owner);
    }

    public static void tick(TileRemasterNode tile) {
        World w = tile.getWorldObj();
        if (node(tile) == null) {
            invalidateDisplay(tile);
            return;
        }
        if (tile.role.equals("spawner") && engineeringFinished(tile)) {
            disableEngineeringSource(tile);
            return;
        }
        if (tile.role.equals("memory") && !navigation(node(tile)) && w.getTotalWorldTime() % 20 == 0) {
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
        if (tile.role.equals("control") && !RemasterSimpleScene.handles(tile)
            && !RemasterEngineering.isEngineering(tile)) {
            NBTTagCompound puzzle = puzzleState(tile);
            RemasterSite owner = RemasterData.get(w)
                .site(tile.siteId);
            int originalKind = RemasterOriginalContract.kind(owner);
            if (puzzle.getBoolean("pending") && !puzzle.getBoolean("solved")
                && (originalKind < 0 || originalKind >= 7 || RemasterOriginalContract.guardsCleared(w, owner, null))) {
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
        int admitted = 0;
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
            EntityOldEcho entity = RemasterSpawn
                .spawn(w, kind, tile.siteId + ":spawn:" + tile.nodeId, x + .5, y, z + .5, -1, false);
            if (entity != null) {
                spawner.setInteger("count", spawner.getInteger("count") + 1);
                admitted++;
            }
        }
        spawner.setString(
            "feedback",
            admitted == 0 ? "出生范围支撑、净空、液体或邻区块条件未齐；未消耗配额。" : "本批成功生成 " + admitted + "；其余位置待空间条件满足后重试。");
        if (spawner.getInteger("count") >= quota) {
            data.flag(tile.siteId, "sealed:" + tile.nodeId, true);
            spawner.setLong("unsealAt", now + integer(policy, "unsealAfterTicks", 36000));
        }
        data.markDirty();
        tile.refresh();
    }

    /** A cached inactive display must also invalidate Minecraft's saved emitted-light calculation. */
    private static void invalidateDisplay(TileRemasterNode tile) {
        World world = tile.getWorldObj();
        if (world == null || world.isRemote || "{}".equals(tile.display)) return;
        tile.refresh();
        world.func_147451_t(tile.xCoord, tile.yCoord, tile.zCoord);
    }
}
