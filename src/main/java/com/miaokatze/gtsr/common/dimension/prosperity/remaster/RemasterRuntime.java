package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
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

import cpw.mods.fml.common.registry.GameRegistry;

/** Immutable authored ownership plus server-only named state, independent of legacy encounter masks. */
public final class RemasterRuntime {

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
            .register(new RetryTicker());
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
                if (!(t instanceof TileRemasterNode)) return fallback;
                TileRemasterNode tile = (TileRemasterNode) t;
                if (w.isRemote && "spawner".equals(tile.role)) return spawnerHardness(tile);
                if (node(tile) == null) return fallback;
                if (!"spawner".equals(tile.role)) return -1;
                return spawnerHardness(tile);
            }
        });
    }

    /** The exact block callback, separated from FML registration so world-generation integration can exercise it. */
    public static boolean activateNode(World world, int x, int y, int z, EntityPlayer player) {
        if (world.isRemote) return true;
        TileEntity actual = world.getTileEntity(x, y, z);
        if (!(actual instanceof TileRemasterNode)) return false;
        TileRemasterNode tile = (TileRemasterNode) actual;
        if (!valid(player, tile) || disabledRole(tile.role)) return false;
        if ("memory".equals(tile.role) || "story".equals(tile.role)) return action(player, tile, 100);
        if ("spawner".equals(tile.role)) {
            player.addChatMessage(
                new ChatComponentText(string(view(tile), "status", "停用的旧笼体"))
                    .setChatStyle(new ChatStyle().setBold(true)));
            return true;
        }
        return false;
    }

    /** Every interactive coordinate is derived from the packaged prefab, including readable units. */
    public static synchronized List<JsonObject> nodes(RemasterSite s) {
        String cacheKey = s.prefab + ":" + s.variant;
        List<JsonObject> cached = NODE_CACHE.get(cacheKey);
        if (cached != null) return cached;
        Map<String, JsonObject> out = new LinkedHashMap<>();
        JsonObject m = s.plan().metadata;
        for (JsonElement e : array(m, "nodes")) {
            JsonObject n = e.getAsJsonObject();
            String role = string(n, "role", "");
            if (role.equals("memory") || role.equals("story")) putNode(out, s, n, role);
        }
        for (String name : new String[] { "lootPlan7", "spawnerPlan" }) for (JsonElement e : array(m, name)) {
            JsonObject n = e.getAsJsonObject();
            putNode(out, s, n, name.equals("lootPlan7") ? "chest" : "spawner");
        }
        List<JsonObject> result = java.util.Collections.unmodifiableList(new ArrayList<>(out.values()));
        NODE_CACHE.put(cacheKey, result);
        return result;
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

    private static void putNode(Map<String, JsonObject> out, RemasterSite s, JsonObject source, String role) {
        JsonObject n = new com.google.gson.JsonParser().parse(source.toString())
            .getAsJsonObject();
        if (disabledRole(role) || source.has("runtimeEnabled") && !source.get("runtimeEnabled")
            .getAsBoolean() || string(source, "id", "").equals("entrance-story-board") || source.has("navigationHint"))
            return;
        n.addProperty("role", role);
        int x = integer(n, "x", 0), y = integer(n, "y", 0), z = integer(n, "z", 0);
        String block = string(n, "block", material(s.plan(), x, y, z));
        if (!block.contains(":")) block = "gtsr:" + block;
        if (!block.contains("#")) block += "#0";
        // A readable plaque/pedestal needs its own tile even when the author used a native decorative support.
        RemasterBlock authored = RemasterBlocks.get(block.split("#")[0]);
        if ((role.equals("memory") || role.equals("story"))
            && (authored == null || !authored.hasTileEntity(meta(block)))) {
            block = "gtsr:draft_pressure_console#0";
        }
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

    /** Authored node coordinates belong to the admitted scene anchor. */
    public static int[] nodePosition(World world, RemasterSite site, JsonObject node) {
        if (site == null || node == null) return null;
        boolean natural = "natural-prefab".equals(site.layout);
        if (!"compact-prefab".equals(site.layout) && !natural) return null;
        if (natural && !RemasterRollout.allowsGeneration(site)) return null;
        return new int[] { site.x + integer(node, "x", 0), site.y + integer(node, "y", 0),
            site.z + integer(node, "z", 0) };
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
        if (role.equals("spawner") && RemasterData.get(world)
            .flag(site.id(), "destroyed:" + id)) return true;
        Block block = resolve(key);
        TileEntity prior = world.getTileEntity(x, y, z);
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
                && world.getBlock(x, y, z) == block
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
                chest.initializeRemaster(ChestTier.resolveTier(site, node), site.id(), id);
                chest.remasterGeometryOrigin = new NBTTagCompound();
                chest.markDirty();
                return true;
            }
            return chest.isRemasterNode(site.id(), id) && matches(world, x, y, z, key);
        }
        if (prior != null) return false;
        if (!world.setBlock(x, y, z, block, meta(key), 3) && !matches(world, x, y, z, key)) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (role.equals("chest")) {
            if (!(tile instanceof TileEntitySealedChest)) return false;
            ((TileEntitySealedChest) tile).initializeRemaster(ChestTier.resolveTier(site, node), site.id(), id);
        } else {
            if (!(tile instanceof TileRemasterNode)) return false;
            ((TileRemasterNode) tile).initialize(site.id(), id, role);
        }
        return true;
    }

    public static JsonObject node(TileRemasterNode tile) {
        if (tile.getWorldObj() == null || tile.getWorldObj().isRemote || disabledRole(tile.role)) return null;
        RemasterSite site = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        if (!RemasterRollout.allowsGeneration(site)) return null;
        for (JsonObject n : nodes(site)) if (tile.nodeId.equals(string(n, "id", "")) && atNode(tile, site, n)
            && tile.role.equals(string(n, "role", ""))
            && tile.getWorldObj()
                .getBlock(tile.xCoord, tile.yCoord, tile.zCoord) == resolve(string(n, "block", ""))
            && tile.getBlockMetadata() == meta(string(n, "block", ""))) return n;
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

    public static JsonObject view(TileRemasterNode tile) {
        JsonObject out = new JsonObject(), n = node(tile);
        if (n == null) return out;
        out.addProperty("title", string(n, "label", "现场记录"));
        out.addProperty("role", tile.role);
        out.addProperty("readOnly", true);
        if ("spawner".equals(tile.role)) {
            NBTTagCompound state = RemasterData.get(tile.getWorldObj())
                .state(tile.siteId)
                .getCompoundTag("spawner:" + tile.nodeId);
            int tier = spawnerTier(n);
            boolean spent = RemasterData.get(tile.getWorldObj())
                .flag(tile.siteId, "sealed:" + tile.nodeId);
            out.addProperty("tier", tier);
            out.addProperty("count", state.getInteger("count"));
            out.addProperty("quota", RemasterSpawnerContract.quota(tier));
            out.addProperty("spent", spent);
            out.addProperty("unlockAt", state.getLong("unlockAt"));
            out.addProperty("pulseAt", state.getLong("pulseAt"));
            out.addProperty("rechargeAt", state.getLong("unsealAt"));
            out.addProperty(
                "status",
                spent ? "符文已解锁；十分钟后恢复"
                    : "已生成 " + state.getInteger("count") + "/" + RemasterSpawnerContract.quota(tier));
        }
        return out;
    }

    public static boolean action(EntityPlayer player, TileRemasterNode tile, int button) {
        if (disabledRole(tile.role) || !valid(player, tile) || button != 100) return false;
        if ("memory".equals(tile.role) || "story".equals(tile.role)) {
            JsonObject record = node(tile);
            RemasterSite owner = RemasterData.get(tile.getWorldObj())
                .site(tile.siteId);
            player.addChatMessage(
                new ChatComponentText(RemasterOriginalContract.narrative(owner, record, new JsonObject())));
            RemasterData.get(tile.getWorldObj())
                .flag(tile.siteId, "story-read:" + tile.nodeId, true);
            HistoryProgress.sceneStage(player, owner.id(), owner.prefab, "read:" + tile.nodeId, false);
            if (player instanceof EntityPlayerMP) com.miaokatze.gtsr.common.dimension.prosperity.lore.FictionCinematic
                .read((EntityPlayerMP) player, owner, tile.nodeId);
            return true;
        }
        return false;
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

    public static void initializeLoaded(TileRemasterNode tile) {
        if (disabledRole(tile.role) || node(tile) == null) {
            invalidateDisplay(tile);
            return;
        }
        if (tile.role.equals("spawner")) {
            NBTTagCompound state = RemasterData.get(tile.getWorldObj())
                .state(tile.siteId);
            String key = "spawner:" + tile.nodeId;
            if (!state.hasKey(key)) state.setTag(key, new NBTTagCompound());
            if (!state.getCompoundTag(key)
                .hasKey("next"))
                state.getCompoundTag(key)
                    .setLong(
                        "next",
                        tile.getWorldObj()
                            .getTotalWorldTime() + RemasterSpawnerContract.delay(tile.getWorldObj().rand));
            RemasterData.get(tile.getWorldObj())
                .markDirty();
        }
        tile.refresh();
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
            && tile.getBlockMetadata() == meta(string(n, "block", ""))) return n;
        return null;
    }

    /** Used only for one-time migration of unopened legacy seals; existing reward containers are never touched. */
    public static int resolveChestTier(TileEntitySealedChest tile) {
        JsonObject node = chestNode(tile);
        if (node == null) return 0;
        return ChestTier.resolveTier(
            RemasterData.get(tile.getWorldObj())
                .site(tile.getRemasterSite()),
            node);
    }

    public static boolean chestReady(TileEntitySealedChest tile) {
        JsonObject n = chestNode(tile);
        if (n == null) return false;
        World w = tile.getWorldObj();
        RemasterData data = RemasterData.get(w);
        String site = tile.getRemasterSite();
        if (data.flag(site, "claimed:" + tile.getRemasterNode())) return false;
        String mode = string(n, "unlockMode", "");
        if (mode.equals("direct")) return true;
        if (mode.equals("story")) return storyReady(w, data.site(site), string(n, "storyNode", ""));
        if (mode.equals("puzzle")) {
            String puzzle = string(n, "puzzleNode", "");
            return !puzzle.isEmpty() && data.flag(site, "puzzle:" + puzzle);
        }
        return mode.equals("combat") && combatCleared(w, data.site(site), string(n, "combatModule", ""));
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
        if ("story".equals(string(n, "unlockMode", ""))) ready &= HistoryProgress
            .hasSceneStage(player, tile.getRemasterSite(), "read:" + string(n, "storyNode", ""));
        String mode = string(n, "unlockMode", "");
        String prerequisite = string(n, "combatModule", "").endsWith("-boss-group") ? "击败此处的首领后，再右击开启。"
            : "击败这间厂房的守卫后，再右击开启。";
        if (mode.equals("story")) {
            String storyId = string(n, "storyNode", ""), label = "附近的剧情记录";
            for (JsonObject story : nodes(
                RemasterData.get(world)
                    .site(tile.getRemasterSite())))
                if (storyId.equals(string(story, "id", ""))) label = string(story, "label", label);
            prerequisite = "先右击阅读“" + label + "”，再返回开启此箱。";
        }
        if (ready) player.addChatMessage(new ChatComponentText("封印正在解除……"));
        else player.addChatMessage(
            new ChatComponentText("封印尚未解除：")
                .appendSibling(new ChatComponentText(prerequisite).setChatStyle(new ChatStyle().setBold(true))));
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
        RemasterLoot.fill(chest, site, node, tile.getTier());
        data.flag(site.id(), "claimed:" + tile.getRemasterNode(), true);
        chest.markDirty();
        world.markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
        return true;
    }

    public static boolean clusterCleared(World w, RemasterSite s, String reference) {
        if (w.isRemote || !RemasterRollout.allowsGeneration(s)) return false;
        RemasterData data = RemasterData.get(w);
        for (JsonElement e : array(s.plan().metadata, "encounterClusters7")) {
            JsonObject cluster = e.getAsJsonObject();
            if (!reference.equals(string(cluster, "id", ""))) continue;
            JsonArray members = array(cluster, "members");
            JsonArray cages = array(cluster, "sealedSpawners");
            if (members.size() == 0 && cages.size() == 0) return false;
            for (JsonElement cage : cages) {
                String id = cage.getAsString();
                boolean authored = false;
                for (JsonObject node : nodes(s))
                    if (id.equals(string(node, "id", "")) && "spawner".equals(string(node, "role", "")))
                        authored = true;
                if (!authored || !data.flag(s.id(), "destroyed:" + id)) return false;
                NBTTagCompound state = data.state(s.id())
                    .getCompoundTag("spawner:" + id);
                if (state.getInteger("deadCount") < state.getInteger("serial")) return false;
            }
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
            for (JsonElement member : members) {
                boolean admitted = false;
                JsonArray spawns = array(s.plan().metadata, "spawns");
                for (int i = 0; i < spawns.size(); i++) if (member.getAsString()
                    .equals(
                        string(
                            spawns.get(i)
                                .getAsJsonObject(),
                            "id",
                            "")))
                    admitted = data.flag(s.id(), "entity:" + i);
                if (!admitted || !data.flag(s.id(), "dead:" + member.getAsString())) return false;
            }
            return true;
        }
        return false;
    }

    public static void death(World w, String id, int nodeIndex) {
        RemasterData data = RemasterData.get(w);
        RemasterSite s = data.site(id);
        if (s == null && id.contains(":spawn:")) {
            int split = id.lastIndexOf(":spawn:");
            String owner = id.substring(0, split), cage = id.substring(split + 7);
            s = data.site(owner);
            if (w.isRemote || !RemasterRollout.allowsGeneration(s) || nodeIndex < 0) return;
            boolean authored = false;
            for (JsonObject node : nodes(s))
                if (cage.equals(string(node, "id", "")) && "spawner".equals(string(node, "role", ""))) authored = true;
            String member = cage + ":" + nodeIndex;
            if (!authored || !data.flag(owner, "spawner-admitted:" + member)
                || data.flag(owner, "spawner-dead:" + member)) return;
            data.flag(owner, "spawner-dead:" + member, true);
            NBTTagCompound state = data.state(owner)
                .getCompoundTag("spawner:" + cage);
            state.setInteger("deadCount", state.getInteger("deadCount") + 1);
            data.markDirty();
            return;
        }
        if (w.isRemote || !RemasterRollout.allowsGeneration(s)) return;
        JsonArray spawns = array(s.plan().metadata, "spawns");
        if (nodeIndex < 0 || nodeIndex >= spawns.size() || !data.flag(id, "entity:" + nodeIndex)) return;
        JsonObject spawn = spawns.get(nodeIndex)
            .getAsJsonObject();
        data.flag(id, "dead:" + string(spawn, "id", Integer.toString(nodeIndex)), true);
        String code = string(spawn, "code", "");
        boolean boss = "boss".equals(string(spawn, "role", ""));
        if (boss) {
            data.flag(id, "boss-dead", true);
            data.flag(id, "boss-dead:" + code, true);
        }
    }

    public static boolean bossReady(World w, String id) {
        if (!active(w, id)) return false;
        RemasterSite owner = RemasterData.get(w)
            .site(id);
        if (industrialBossSite(owner)) return remainingSpawners(w, owner) == 0 && requiredSpawners(owner).size() > 0;
        boolean found = false;
        for (JsonElement element : array(owner.plan().metadata, "spawns")) {
            JsonObject spawn = element.getAsJsonObject();
            if (!"boss".equals(string(spawn, "role", ""))) continue;
            found = true;
            if (!spawnReady(w, owner, spawn)) return false;
        }
        return found;
    }

    /** Both first generation and loaded-chunk retries use the same authored activation contract. */
    public static boolean spawnReady(World world, RemasterSite site, JsonObject spawn) {
        if (world == null || world.isRemote || !RemasterRollout.allowsGeneration(site)) return false;
        if (industrialBossSite(site) && "boss".equals(string(spawn, "role", ""))) return true;
        if (spawn.has("activationModules") && !spawn.get("activationModules")
            .isJsonArray()) return false;
        for (JsonElement module : array(spawn, "activationModules")) {
            if (!module.isJsonPrimitive() || !module.getAsJsonPrimitive()
                .isString() || !combatCleared(world, site, module.getAsString())) return false;
        }
        return true;
    }

    public static boolean industrialBossSite(RemasterSite site) {
        return site != null && ("subsided_factory".equals(site.prefab) || "fallen_foundry".equals(site.prefab));
    }

    private static List<String> requiredSpawners(RemasterSite site) {
        List<String> ids = new ArrayList<>();
        JsonObject metadata = site.plan().metadata;
        if (!metadata.has("bossActivation") || !metadata.get("bossActivation")
            .isJsonObject()) return ids;
        JsonObject activation = metadata.getAsJsonObject("bossActivation");
        if (!"destroy-all-spawners".equals(string(activation, "kind", ""))) return ids;
        for (JsonElement id : array(activation, "spawnerIds")) {
            if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive()
                .isString() || ids.contains(id.getAsString())) return new ArrayList<>();
            boolean authored = false;
            for (JsonObject node : nodes(site)) if ("spawner".equals(string(node, "role", "")) && id.getAsString()
                .equals(string(node, "id", ""))) authored = true;
            if (!authored) return new ArrayList<>();
            ids.add(id.getAsString());
        }
        for (JsonObject node : nodes(site))
            if ("spawner".equals(string(node, "role", "")) && !ids.contains(string(node, "id", "")))
                return new ArrayList<>();
        return ids;
    }

    private static int remainingSpawners(World world, RemasterSite site) {
        int remaining = 0;
        for (String id : requiredSpawners(site)) if (!RemasterData.get(world)
            .flag(site.id(), "destroyed:" + id)) remaining++;
        return remaining;
    }

    /** Called by the actual block removal callback while the old tile is still installed. Never infer absence. */
    public static void spawnerDestroyed(World world, int x, int y, int z, Block oldBlock, int oldMeta) {
        if (world == null || world.isRemote) return;
        TileEntity actual = world.getTileEntity(x, y, z);
        if (!(actual instanceof TileRemasterNode)) return;
        TileRemasterNode tile = (TileRemasterNode) actual;
        RemasterData data = RemasterData.get(world);
        RemasterSite site = data.site(tile.siteId);
        if (!RemasterRollout.allowsGeneration(site) || !"spawner".equals(tile.role)
            || !data.flag(tile.siteId, "node:" + tile.nodeId)) return;
        for (JsonObject node : nodes(site))
            if (tile.nodeId.equals(string(node, "id", "")) && "spawner".equals(string(node, "role", ""))
                && atNode(tile, site, node)
                && oldBlock == resolve(string(node, "block", ""))
                && oldMeta == meta(string(node, "block", ""))) {
                    data.flag(tile.siteId, "destroyed:" + tile.nodeId, true);
                    return;
                }
    }

    /** Immutable server snapshot for a scene HUD; loaded entities do not decide cage completion. */
    public static final class BossStatus {

        public final int totalSpawners, remainingSpawners, state, revivalTicks, entityId;
        public final float health, maxHealth;
        public final String bossCode;

        private BossStatus(int total, int remaining, int state, int ticks, int entityId, float health, float max,
            String code) {
            this.totalSpawners = total;
            this.remainingSpawners = remaining;
            this.state = state;
            this.revivalTicks = ticks;
            this.entityId = entityId;
            this.health = health;
            this.maxHealth = max;
            this.bossCode = code;
        }
    }

    /** Persist the last authoritative phase even when the boss chunk later unloads. */
    public static void rememberBossState(EntityOldEcho boss) {
        World world = boss.worldObj;
        if (world == null || world.isRemote || !active(world, boss.getEncounterId())) return;
        RemasterData data = RemasterData.get(world);
        RemasterSite site = data.site(boss.getEncounterId());
        if (!industrialBossSite(site)) return;
        int index = boss.getPlatformId();
        JsonArray spawns = array(site.plan().metadata, "spawns");
        if (index < 0 || index >= spawns.size() || !data.flag(site.id(), "entity:" + index)) return;
        JsonObject spawn = spawns.get(index)
            .getAsJsonObject();
        if (!"boss".equals(string(spawn, "role", "")) || !boss.getKind().code.equals(string(spawn, "code", ""))) return;
        NBTTagCompound state = data.state(site.id());
        int phase = boss.getIndustrialBossStage(), ticks = boss.getRevivalTicks();
        float health = phase == 0 ? 0 : boss.getHealth();
        if (state.getInteger("industrialBossStage") == phase && state.getInteger("industrialRevivalTicks") == ticks
            && state.getFloat("industrialBossHealth") == health) return;
        state.setInteger("industrialBossStage", phase);
        state.setInteger("industrialRevivalTicks", ticks);
        state.setFloat("industrialBossHealth", health);
        data.markDirty();
    }

    public static BossStatus bossStatus(World world, String siteId) {
        if (!active(world, siteId)) return null;
        RemasterSite site = RemasterData.get(world)
            .site(siteId);
        if (!industrialBossSite(site)) return null;
        String code = "";
        for (JsonElement element : array(site.plan().metadata, "spawns")) {
            JsonObject spawn = element.getAsJsonObject();
            if ("boss".equals(string(spawn, "role", ""))) {
                code = string(spawn, "code", "");
                break;
            }
        }
        int total = requiredSpawners(site).size(), remaining = remainingSpawners(world, site);
        for (Object object : world.loadedEntityList) if (object instanceof EntityOldEcho) {
            EntityOldEcho boss = (EntityOldEcho) object;
            if (siteId.equals(boss.getEncounterId()) && code.equals(boss.getKind().code)) return new BossStatus(
                total,
                remaining,
                boss.getIndustrialBossStage(),
                boss.getRevivalTicks(),
                boss.getEntityId(),
                boss.getIndustrialBossStage() == 0 ? 0 : boss.getHealth(),
                boss.getMaxHealth(),
                code);
        }
        NBTTagCompound saved = RemasterData.get(world)
            .state(siteId);
        boolean defeated = RemasterData.get(world)
            .flag(siteId, "boss-dead");
        return new BossStatus(
            total,
            remaining,
            defeated ? 3 : saved.getInteger("industrialBossStage"),
            saved.getInteger("industrialRevivalTicks"),
            -1,
            defeated ? 0 : saved.getFloat("industrialBossHealth"),
            code.isEmpty() ? 0 : EchoKind.byCode(code).maxHealth,
            code);
    }

    public static boolean disabledRole(String role) {
        return !"memory".equals(role) && !"story".equals(role) && !"spawner".equals(role) && !"chest".equals(role);
    }

    private static boolean storyReady(World world, RemasterSite site, String reference) {
        if (site == null || reference.isEmpty()) return false;
        for (JsonObject node : nodes(site)) if (reference.equals(string(node, "id", ""))
            && ("memory".equals(string(node, "role", "")) || "story".equals(string(node, "role", ""))))
            return RemasterData.get(world)
                .flag(site.id(), "story-read:" + reference);
        return false;
    }

    /** A non-empty authored module needs successful admission and accepted deaths for every real member. */
    public static boolean combatCleared(World world, RemasterSite site, String module) {
        if (world == null || world.isRemote || !RemasterRollout.allowsGeneration(site) || module.isEmpty())
            return false;
        RemasterData data = RemasterData.get(world);
        for (JsonElement raw : array(site.plan().metadata, "encounterClusters7"))
            if (module.equals(string(raw.getAsJsonObject(), "id", ""))) return clusterCleared(world, site, module);
        JsonArray spawns = array(site.plan().metadata, "spawns");
        boolean found = false;
        for (int i = 0; i < spawns.size(); i++) {
            JsonObject spawn = spawns.get(i)
                .getAsJsonObject();
            boolean eligible = RemasterOriginalContract.eligibleGuard(spawn);
            if (module.equals("guards") ? !eligible : !module.equals(string(spawn, "module", ""))) continue;
            if (spawn.has("spawn") && !spawn.get("spawn")
                .getAsBoolean()) continue;
            if (!eligible && !"boss".equals(string(spawn, "role", ""))) continue;
            found = true;
            if (!data.flag(site.id(), "entity:" + i)
                || !data.flag(site.id(), "dead:" + string(spawn, "id", Integer.toString(i)))) return false;
        }
        return found;
    }

    public static int spawnerTier(JsonObject node) {
        String block = string(node, "block", string(node, "material", ""));
        if (block.contains("runaway")) return 3;
        if (block.contains("stable")) return 2;
        if (block.contains("fragile")) return 1;
        if (node.has("tier") && node.get("tier")
            .isJsonPrimitive()) {
            com.google.gson.JsonPrimitive tier = node.getAsJsonPrimitive("tier");
            if (tier.isNumber()) return Math.max(1, Math.min(3, tier.getAsInt()));
            String name = tier.getAsString();
            if ("runaway".equals(name)) return 3;
            if ("stable".equals(name)) return 2;
            if ("fragile".equals(name)) return 1;
        }
        JsonObject policy = node.has("policy") ? node.getAsJsonObject("policy") : new JsonObject();
        int quota = integer(policy, "sealAt", 10);
        return quota >= 45 ? 3 : quota >= 24 ? 2 : 1;
    }

    public static float spawnerHardness(TileRemasterNode tile) {
        if (tile.getWorldObj().isRemote) {
            try {
                JsonObject display = new com.google.gson.JsonParser().parse(tile.display)
                    .getAsJsonObject();
                return display.has("spent") && display.get("spent")
                    .getAsBoolean()
                    && tile.getWorldObj()
                        .getTotalWorldTime()
                        >= display.get("unlockAt")
                            .getAsLong() + 40 ? 10 : 200;
            } catch (RuntimeException ignored) {
                return 200;
            }
        }
        RemasterData data = RemasterData.get(tile.getWorldObj());
        NBTTagCompound state = data.state(tile.siteId)
            .getCompoundTag("spawner:" + tile.nodeId);
        return data.flag(tile.siteId, "sealed:" + tile.nodeId) && tile.getWorldObj()
            .getTotalWorldTime() >= state.getLong("unlockAt") + 40 ? 10 : 200;
    }

    /** Effects applied after spawn/tracker admission must also reach already tracking clients. */
    public static void synchronizeSpawnerEffects(EntityOldEcho entity) {
        if (!(entity.worldObj instanceof net.minecraft.world.WorldServer)) return;
        net.minecraft.world.WorldServer server = (net.minecraft.world.WorldServer) entity.worldObj;
        for (Object value : entity.getActivePotionEffects()) {
            net.minecraft.potion.PotionEffect effect = (net.minecraft.potion.PotionEffect) value;
            server.getEntityTracker()
                .func_151247_a(
                    entity,
                    new net.minecraft.network.play.server.S1DPacketEntityEffect(entity.getEntityId(), effect));
        }
    }

    public static void tick(TileRemasterNode tile) {
        World w = tile.getWorldObj();
        JsonObject n = node(tile);
        if (n == null) {
            invalidateDisplay(tile);
            return;
        }
        if (!"spawner".equals(tile.role)) return;
        RemasterData data = RemasterData.get(w);
        NBTTagCompound state = data.state(tile.siteId);
        String k = "spawner:" + tile.nodeId;
        if (!state.hasKey(k)) state.setTag(k, new NBTTagCompound());
        NBTTagCompound spawner = state.getCompoundTag(k);
        long now = w.getTotalWorldTime();
        int tier = spawnerTier(n), quota = RemasterSpawnerContract.quota(tier);
        if (spawner.getInteger("contract") != 1) {
            spawner.setInteger("contract", 1);
            if (data.flag(tile.siteId, "sealed:" + tile.nodeId)) {
                spawner
                    .setLong("unsealAt", Math.min(spawner.getLong("unsealAt"), now + RemasterSpawnerContract.COOLDOWN));
                if (!spawner.hasKey("unlockAt")) spawner.setLong("unlockAt", now - 40);
            }
            data.markDirty();
        }
        if (data.flag(tile.siteId, "sealed:" + tile.nodeId)) {
            if (now < spawner.getLong("unsealAt")) return;
            data.flag(tile.siteId, "sealed:" + tile.nodeId, false);
            spawner.setInteger("count", 0);
            spawner.setLong("next", now + RemasterSpawnerContract.delay(w.rand));
            w.playSoundEffect(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5, "gtsr:spawner.recharge", 1, 1);
            data.markDirty();
            tile.refresh();
        }
        EntityPlayer near = w.getClosestPlayer(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5, 16);
        if (near == null || !near.isEntityAlive() || near.capabilities.isCreativeMode || near instanceof FakePlayer)
            return;
        if (!spawner.hasKey("next")) {
            spawner.setLong("next", now + RemasterSpawnerContract.delay(w.rand));
            data.markDirty();
        }
        if (now < spawner.getLong("next")) return;
        spawner.setLong("next", now + RemasterSpawnerContract.delay(w.rand));
        EchoKind kind = EchoKind.byCode(string(n, "code", ""));
        if (kind == null) return;
        int batch = RemasterSpawnerContract.batch(tier, spawner.getInteger("count")), admitted = 0;
        // Four random admissions per requested mob, never bypass collision or loaded/support checks.
        for (int attempt = 0; attempt < batch * 4 && admitted < batch; attempt++) {
            double x = tile.xCoord + .5 + (w.rand.nextDouble() - w.rand.nextDouble()) * 4;
            double y = tile.yCoord + w.rand.nextInt(3) - 1;
            double z = tile.zCoord + .5 + (w.rand.nextDouble() - w.rand.nextDouble()) * 4;
            EntityOldEcho entity = RemasterSpawn
                .spawn(w, kind, tile.siteId + ":spawn:" + tile.nodeId, x, y, z, -1, false);
            if (entity == null) continue;
            int serial = spawner.getInteger("serial");
            entity.setNodeIndex(serial);
            spawner.setInteger("serial", serial + 1);
            data.flag(tile.siteId, "spawner-admitted:" + tile.nodeId + ":" + serial, true);
            entity.getEntityData()
                .setInteger("gtsr.spawnerTier", tier);
            entity.getEntityData()
                .setBoolean("gtsr.spawnerAura", true);
            int[] effects = { net.minecraft.potion.Potion.damageBoost.id, net.minecraft.potion.Potion.resistance.id,
                net.minecraft.potion.Potion.field_76434_w.id, net.minecraft.potion.Potion.regeneration.id };
            int[] chosen = RemasterSpawnerContract.buffs(tier, w.rand);
            for (int effect : chosen)
                entity.addPotionEffect(new net.minecraft.potion.PotionEffect(effects[effect], 24000, tier - 1));
            entity.setHealth(entity.getMaxHealth());
            synchronizeSpawnerEffects(entity);
            spawner.setInteger("count", spawner.getInteger("count") + 1);
            admitted++;
        }
        if (admitted > 0) {
            spawner.setLong("pulseAt", now);
            w.playSoundEffect(
                tile.xCoord + .5,
                tile.yCoord + .5,
                tile.zCoord + .5,
                "gtsr:spawner.spawn",
                .9F,
                1 + tier * .08F);
        }
        if (spawner.getInteger("count") >= quota) {
            data.flag(tile.siteId, "sealed:" + tile.nodeId, true);
            spawner.setLong("unlockAt", now);
            spawner.setLong("unsealAt", now + RemasterSpawnerContract.COOLDOWN);
            w.playSoundEffect(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5, "gtsr:spawner.unlock", 1, 1);
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
