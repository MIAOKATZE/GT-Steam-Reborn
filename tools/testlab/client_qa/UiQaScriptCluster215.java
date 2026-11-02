package audit;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Owned-only fixture and readback helpers. Actions use the real screen and production C2S handlers. */
final class UiQaScriptCluster215 {
    private static final String G = "com.miaokatze.gtsr.";
    private static final int X = 640, Y = 160, Z = 16;
    private static final String PREFIX = "cluster215.";

    static void execute(UiQaScript s, JsonObject step) throws Exception {
        String op = step.get("op").getAsString();
        if (op.equals("cluster215Seed")) seed(s);
        else if (op.equals("cluster215Open")) open(s);
        else if (op.equals("cluster215Bind")) bind(s, step);
        else if (op.equals("cluster215Hit")) hit(s, step);
        else if (op.equals("cluster215ClientProof")) clientProof(s, step);
        else if (op.equals("cluster215ServerProof")) serverProof(s, step);
        else if (op.equals("cluster215NbtProof")) nbtProof(s);
        else if (op.equals("cluster215StatsPreview")) statsPreview(s);
        else if (op.equals("cluster215InventoryProof")) inventoryProof(s, step);
        else if (op.equals("cluster215Cleanup")) cleanup(s);
        else throw new IllegalArgumentException("Unknown owned cluster215 operation " + op);
    }

    private static void seed(UiQaScript s) throws Exception {
        Mui2ClientQa q = s.q;
        Object world = s.target("world"), player = q.serverPlayer;
        q.check(PREFIX + "owned-dimension", q.number(Mui2ClientQa.field(player, "dimension|field_71093_bK")) == 1048577,
            "The explicit fixture is restricted to the owned TestLab dimension");
        Object inv = Mui2ClientQa.field(player, "inventory|field_71071_by");
        int slots = q.number(Mui2ClientQa.call(inv, "getSizeInventory|func_70302_i_"));
        List<Object> prior = new ArrayList<Object>();
        for (int i = 0; i < slots; i++) prior.add(copy(Mui2ClientQa.call(inv, "getStackInSlot|func_70301_a", i)));
        s.variables.put(PREFIX + "priorInventory", prior);
        s.variables.put(PREFIX + "priorCursor", copy(Mui2ClientQa.call(inv, "getItemStack|func_70445_o")));
        s.variables.put(PREFIX + "priorHotbar", Mui2ClientQa.field(inv, "currentItem|field_70461_c"));
        s.variables.put(PREFIX + "priorPosition", new Object[] {
            Mui2ClientQa.field(player, "posX|field_70165_t"), Mui2ClientQa.field(player, "posY|field_70163_u"),
            Mui2ClientQa.field(player, "posZ|field_70161_v") });
        List<int[]> floors = new ArrayList<int[]>();
        for (int n = 0; n < 5; n++) {
            int x = X + n * 4;
            Mui2ClientQa.call(Mui2ClientQa.call(world, "getChunkProvider|func_72863_F"), "loadChunk|func_73158_c", x >> 4, Z >> 4);
            if (!Boolean.TRUE.equals(Mui2ClientQa.call(world, "isAirBlock|func_147437_c", x, Y, Z)))
                throw new AssertionError("Owned fixture target is not air: " + x + "," + Y + "," + Z);
            for (int px = x - 1; px <= x + 2; px++) for (int pz = Z - 1; pz <= Z + 4; pz++) {
                if (!Boolean.TRUE.equals(Mui2ClientQa.call(world, "isAirBlock|func_147437_c", px, Y - 1, pz)))
                    throw new AssertionError("Owned support footprint is not air");
                floors.add(new int[] { px, Y - 1, pz });
            }
        }
        s.variables.put(PREFIX + "floors", floors);
        String[] names = { "cluster215Controller", "cluster215Logistics0", "cluster215Logistics1", "cluster215Crusher", "cluster215Furnace" };
        String[] classes = { "MTESteamMineralLogisticsCluster", "MTEBasicLogisticsUnit", "MTEBasicLogisticsUnit", "MTEUnitCrusher", "MTEUnitFurnace" };
        for (int i = 0; i < names.length; i++) {
            JsonObject placement = new JsonObject();
            placement.addProperty("name", names[i]);
            placement.addProperty("class", G + "common.machine.cluster." + classes[i]);
            placement.addProperty("x", X + i * 4); placement.addProperty("y", Y); placement.addProperty("z", Z);
            Object machine = s.placeGt(placement);
            Mui2ClientQa.set(machine, "mStartUpCheck", Integer.MAX_VALUE);
            Mui2ClientQa.set(machine, "mUpdate", Integer.MAX_VALUE);
            Mui2ClientQa.set(machine, "mUpdated", false);
            Mui2ClientQa.set(machine, "mMachine", true);
        }
        Object controller = s.target("cluster215Controller");
        for (String family : new String[] { "mCasingTier", "mPipeTier", "mFrameTier", "mFireboxTier" })
            Mui2ClientQa.set(controller, family, 1);
        Mui2ClientQa.call(controller, "setMachineEnabled", false);
        Mui2ClientQa.set(controller, "extensionCount", 1);
        for (int i = 1; i < names.length; i++) {
            Object unit = s.target(names[i]);
            Mui2ClientQa.set(unit, "unitStructureTier", 1);
            Mui2ClientQa.call(unit, "onStructureTier", 1);
            int pad = i < 3 ? 2 : 0, segment = i == 2 || i == 4 ? 1 : 0;
            q.check(PREFIX + "fixture-register-" + names[i], Boolean.TRUE.equals(Mui2ClientQa.call(controller, "addClusterUnit", unit, pad, segment)),
                "Real GT instance explicitly registered into the owned topology model");
        }
        Mui2ClientQa.call(Mui2ClientQa.call(controller, "getTopology"), "setSegmentCount", 2);
        Object furnace = Enum.valueOf((Class) Mui2ClientQa.type(G + "common.machine.cluster.ChainLink"), "FURNACE");
        for (String name : new String[] { "cluster215Logistics0", "cluster215Logistics1" }) {
            Object unit = s.target(name);
            Mui2ClientQa.call(Mui2ClientQa.call(unit, "getChain"), "setLinks", Arrays.asList(furnace));
            Mui2ClientQa.call(unit, "setBatchMode", false);
        }
        // Select the other module initially, so clicking slot 2 must perform a genuine selection transaction.
        Mui2ClientQa.call(controller, "setSelectedLogisticsIndex", 1);
        Object terminal = Mui2ClientQa.call(Mui2ClientQa.field(Mui2ClientQa.type(G + "common.api.enums.GTSRItemList"), "HubTerminal"), "get", 1);
        Mui2ClientQa.call(inv, "setInventorySlotContents|func_70299_a", 0, terminal);
        Mui2ClientQa.set(inv, "currentItem|field_70461_c", 0);
        Mui2ClientQa.call(player, "setPositionAndUpdate|func_70634_a", X + .5, Y + 1d, Z + 2.5);
        seedStatistics(s, controller);
        q.receipt.addProperty("cluster215FixtureScope",
            "Five actual placed registered GT tiles in owned TestLab, with explicit topology/tier/mMachine visual-model signals and held GT structure-check gates. Controller power is OFF, no recipe inputs exist. No physical multiblock formation, recipe execution, resource charging, remote TCP or restart claim. Button selection/batch/save and S2C readback use production GUI/session handlers.");
        JsonObject event = new JsonObject(); event.addProperty("cluster215Fixture", "EXPLICIT_OWNED_TOPOLOGY_AND_STATISTICS_MODEL");
        event.addProperty("syntheticFormationSignals", true); event.addProperty("physicalStructureBuilt", false);
        event.addProperty("statisticsPerLedger", 64); event.addProperty("dimension", 1048577);
        event.addProperty("origin", X + "," + Y + "," + Z); s.events.add(event);
    }

    private static void seedStatistics(UiQaScript s, Object controller) throws Exception {
        Object registry = Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.item.Item"), "itemRegistry|field_150901_e");
        TreeMap<String, Object> items = new TreeMap<String, Object>();
        for (Object item : (Iterable<?>) registry) {
            String id = String.valueOf(Mui2ClientQa.call(registry, "getNameForObject|func_148750_c", item));
            if (id.startsWith("minecraft:") && !id.equals("minecraft:air")) items.put(id, item);
        }
        if (items.size() < 64) throw new AssertionError("Fewer than 64 registered vanilla fixture items");
        Map<Long, Long> bonus = new LinkedHashMap<Long, Long>(); int i = 0;
        for (Map.Entry<String, Object> entry : items.entrySet()) {
            if (i == 64) break;
            JsonObject spec = new JsonObject(); spec.addProperty("id", entry.getKey()); spec.addProperty("count", 1);
            Object stack = s.makeStack(spec);
            Mui2ClientQa.call(controller, "addStatInput", stack, 1000000L + i);
            Mui2ClientQa.call(controller, "addStatOutput", stack, 2000000L + i);
            int itemId = s.q.number(Mui2ClientQa.call(Mui2ClientQa.type("net.minecraft.item.Item"), "getIdFromItem|func_150891_b", entry.getValue()));
            bonus.put(((long) itemId) << 32, 3000000L + i);
            if (i == 0) s.variables.put(PREFIX + "lastStatisticsItem", entry.getKey());
            i++;
        }
        Mui2ClientQa.call(controller, "addStatBonusEntries", bonus);
    }

    private static void open(UiQaScript s) throws Exception {
        Object direction = Enum.valueOf((Class) Mui2ClientQa.type("net.minecraftforge.common.util.ForgeDirection"), "UP");
        Object accepted = Mui2ClientQa.call(s.target("cluster215ControllerHost"), "onRightclick", s.q.serverPlayer, direction, .5f, .5f, .5f);
        s.q.check(PREFIX + "production-right-click-open", Boolean.TRUE.equals(accepted),
            "Production BaseMetaTileEntity.onRightclick with actual server player and held HubTerminal");
    }

    private static void bind(UiQaScript s, JsonObject step) throws Exception {
        boolean server = UiQaScript.string(step, "side", "client").equals("server");
        Object owner;
        if (server) owner = s.target("container");
        else { if (!s.capture()) throw new AssertionError("Real MUI2 screen missing"); owner = s.q.screen; }
        Object session = Mui2ClientQa.call(Mui2ClientQa.call(Mui2ClientQa.call(owner, "getSyncManager"), "getMainPSM"), "findSyncHandlerNullable", "cluster_session", 0);
        if (session == null) throw new AssertionError("Production cluster_session handler missing");
        String name = PREFIX + (server ? "serverSession" : "clientSession");
        Object old = s.variables.put(name, session);
        if (old != null && old != session) {
            s.q.check(PREFIX + "reopen-old-" + (server ? "server" : "client") + "-disposed", !Boolean.TRUE.equals(Mui2ClientQa.call(old, "isValid")), "Old formal session disposed after close");
            long previous = ((Number) Mui2ClientQa.field(Mui2ClientQa.field(old, "data"), "epoch")).longValue();
            long current = ((Number) Mui2ClientQa.field(Mui2ClientQa.field(session, "data"), "epoch")).longValue();
            s.q.check(PREFIX + "reopen-new-epoch-" + (server ? "server" : "client"), current > previous, "epoch " + previous + " -> " + current);
        }
    }

    private static void hit(UiQaScript s, JsonObject step) throws Exception {
        String name = step.get("widget").getAsString();
        if (!s.capture()) throw new AssertionError("Real MUI2 screen missing");
        Object widget = s.q.find(s.q.panel, name);
        // MUI2 dropdown options belong to a real floating MenuPanel, not the main panel's child tree.
        if (widget == null && name.startsWith("cluster_dropdown_unit_")) {
            Object source = s.q.find(s.q.panel, "cluster_logistics_dropdown");
            if (source != null && Boolean.TRUE.equals(Mui2ClientQa.call(source, "isOpen"))) {
                Object menu = Mui2ClientQa.field(source, "menu");
                if (menu != null) widget = s.q.find(menu, name);
            }
        }
        if (widget == null) throw new AssertionError("Target widget missing: " + name);
        int[] point = s.q.screenPoint(widget);
        Object context = Mui2ClientQa.call(s.q.screen, "getContext");
        Mui2ClientQa.call(context, "updateState", point[0], point[1], 0f);
        Mui2ClientQa.call(s.q.screen, "onFrameUpdate");
        Object hovered = Mui2ClientQa.call(context, "getTopHovered");
        s.q.check(PREFIX + "actual-hit-" + name, hovered == widget && Boolean.TRUE.equals(Mui2ClientQa.call(widget, "areAncestorsEnabled")),
            "Actual transformed screen point=" + point[0] + "," + point[1] + " top=" + (hovered == null ? "null" : Mui2ClientQa.call(hovered, "getName")));
        if (step.has("afterScroll")) {
            Object list = s.q.find(s.q.panel, step.get("afterScroll").getAsString());
            int scroll = s.q.number(Mui2ClientQa.call(Mui2ClientQa.call(list, "getScrollData"), "getScroll"));
            s.q.check(PREFIX + "scroll-before-" + name, scroll > 0, "Actual native list scroll=" + scroll);
        }
        if (!step.has("click") || step.get("click").getAsBoolean()) s.q.click(widget);
    }

    private static Object session(UiQaScript s, boolean server) {
        Object session = s.variables.get(PREFIX + (server ? "serverSession" : "clientSession"));
        if (session == null) throw new AssertionError("Bind the current formal session first");
        return session;
    }

    private static void clientProof(UiQaScript s, JsonObject step) throws Exception {
        Object session = session(s, false), draft = Mui2ClientQa.call(session, "draft");
        String id = step.get("name").getAsString();
        if (step.has("mode")) {
            int selected = s.q.number(Mui2ClientQa.call(session, "integer", "cl.selLogi", -1));
            String[] rows = String.valueOf(Mui2ClientQa.call(session, "string", "cl.le.units")).split(",");
            String[] fields = selected >= 0 && selected < rows.length ? rows[selected].split(":") : new String[0];
            boolean enabled = fields.length >= 5 && (Integer.parseInt(fields[1]) & 16) != 0;
            s.q.check(id + ".authoritative-mode", fields.length >= 5 && enabled == step.get("mode").getAsBoolean(), "Actual S2C CSV=" + Arrays.toString(rows));
            s.q.check(id + ".selected-slot2", s.q.number(Mui2ClientQa.call(session, "selectedSlot")) == 2, "Production selected member identity");
        }
        if (step.has("ordinals")) {
            List<?> actual = (List<?>) Mui2ClientQa.field(draft, "ordinals");
            List<Integer> expected = new ArrayList<Integer>();
            for (int i = 0; i < step.getAsJsonArray("ordinals").size(); i++) expected.add(step.getAsJsonArray("ordinals").get(i).getAsInt());
            s.q.check(id + ".draft-ordinals", actual.equals(expected), "Actual local draft=" + actual);
        }
        if (step.has("dirty")) s.q.check(id + ".draft-dirty", step.get("dirty").getAsBoolean() == Boolean.TRUE.equals(Mui2ClientQa.field(draft, "dirty")), "Actual draft revision state");
        if (step.has("valid")) s.q.check(id + ".draft-valid", step.get("valid").getAsBoolean() == Boolean.TRUE.equals(Mui2ClientQa.call(draft, "valid")), "Production shared chain FSM");
        if (step.has("sequence")) s.q.check(id + ".command-sequence", s.q.number(Mui2ClientQa.field(session, "commandSequence")) == step.get("sequence").getAsInt(), "Actual client command sequence");
        if (step.has("receipt")) s.q.check(id + ".receipt", step.get("receipt").getAsString().equals(Mui2ClientQa.call(session, "receipt")), "Actual command result receipt");
        s.q.check(id + ".not-awaiting", !Boolean.TRUE.equals(Mui2ClientQa.field(session, "awaiting")), "No outstanding production command");
    }

    private static void serverProof(UiQaScript s, JsonObject step) throws Exception {
        Object current = session(s, true), unit = s.target("cluster215Logistics0"), other = s.target("cluster215Logistics1");
        String id = step.get("name").getAsString();
        s.q.check(id + ".mode", Boolean.TRUE.equals(Mui2ClientQa.call(unit, "isBatchModeEnabled")) == step.get("mode").getAsBoolean(), "Actual GT batch mode on selected member");
        s.q.check(id + ".other-mode-unchanged", !Boolean.TRUE.equals(Mui2ClientQa.call(other, "isBatchModeEnabled")), "Other logistics member unchanged");
        s.q.check(id + ".selected-index", s.q.number(Mui2ClientQa.call(s.target("cluster215Controller"), "getSelectedLogisticsIndex")) == 0, "Server selected slot2 / logistics index0");
        s.q.check(id + ".sequence", s.q.number(Mui2ClientQa.field(current, "processedSequence")) == step.get("sequence").getAsInt(), "Actual server processed sequence");
        if (step.get("sequence").getAsInt() > 0)
            s.q.check(id + ".accepted", s.q.number(Mui2ClientQa.field(current, "lastResultStatus")) == 0, "Production server result status for the processed action");
        int[] expected = new int[step.getAsJsonArray("ordinals").size()];
        for (int i = 0; i < expected.length; i++) expected[i] = step.getAsJsonArray("ordinals").get(i).getAsInt();
        s.q.check(id + ".chain", Arrays.equals((int[]) Mui2ClientQa.call(Mui2ClientQa.call(unit, "getChain"), "toOrdinalArray"), expected), "Actual authoritative selected chain");
        s.q.check(id + ".other-chain-unchanged", Arrays.equals((int[]) Mui2ClientQa.call(Mui2ClientQa.call(other, "getChain"), "toOrdinalArray"), new int[] { 9 }), "Non-target chain remains baseline FURNACE");
    }

    private static void nbtProof(UiQaScript s) throws Exception {
        Object tag = Mui2ClientQa.type("net.minecraft.nbt.NBTTagCompound").newInstance();
        Mui2ClientQa.call(s.target("cluster215Logistics0"), "saveNBTData", tag);
        s.q.check(PREFIX + "mode-persisted-by-production-nbt", Boolean.TRUE.equals(Mui2ClientQa.call(tag, "getBoolean|func_74767_n", "batchMode")), "Actual production saveNBTData field batchMode");
    }

    private static void statsPreview(UiQaScript s) throws Exception {
        Object session = session(s, false); int before = s.q.number(Mui2ClientQa.field(session, "commandSequence"));
        Object registry = Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.item.Item"), "itemRegistry|field_150901_e");
        for (int ledger = 0; ledger < 3; ledger++) {
            String name = "cluster_stat_icon_" + ledger + "_63";
            Object widget = s.q.find(s.q.panel, name), stack = Mui2ClientQa.call(widget, "getStackForRecipeViewer");
            String actual = stack == null ? "null" : String.valueOf(Mui2ClientQa.call(registry, "getNameForObject|func_148750_c", Mui2ClientQa.call(stack, "getItem|func_77973_b")));
            s.q.check(PREFIX + "top64-last-item-" + ledger, actual.equals(s.variables.get(PREFIX + "lastStatisticsItem")), "Actual sorted entry63 item=" + actual);
            JsonObject click = new JsonObject(); click.addProperty("widget", name); click.addProperty("afterScroll", "cluster_stats_list_" + ledger);
            hit(s, click);
        }
        s.q.check(PREFIX + "preview-clicks-no-command", s.q.number(Mui2ClientQa.field(session, "commandSequence")) == before, "Three actual bottom-icon hit-test clicks sent no cluster command");
        Object container = Mui2ClientQa.call(s.q.screen, "getContainer");
        List<?> slots = (List<?>) Mui2ClientQa.field(container, "inventorySlots|field_75151_b");
        Object playerInventory = Mui2ClientQa.field(s.target("player"), "inventory|field_71071_by");
        boolean onlyPlayerSlots = slots.size() == 36;
        java.util.Set<Integer> indices = new java.util.HashSet<Integer>();
        for (Object slot : slots) {
            Object handler = Mui2ClientQa.call(slot, "getItemHandler");
            int index = s.q.number(Mui2ClientQa.call(slot, "getSlotIndex"));
            onlyPlayerSlots &= handler.getClass().getName().equals("com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper")
                && Mui2ClientQa.call(handler, "getInventoryPlayer") == playerInventory
                && "player_inventory".equals(Mui2ClientQa.call(slot, "getSlotGroupName"))
                && index >= 0 && index < 36;
            indices.add(index);
        }
        s.q.check(PREFIX + "stat-widgets-add-no-transaction-slots", onlyPlayerSlots && indices.size() == 36,
            "MUI2 automatically binds exactly the real player main inventory indices 0..35; statistics add no slots. Actual count=" + slots.size());
    }

    private static String playerInventory(UiQaScript s) throws Exception {
        Object inv = Mui2ClientQa.field(s.q.serverPlayer, "inventory|field_71071_by");
        Object tag = Mui2ClientQa.type("net.minecraft.nbt.NBTTagList").newInstance();
        Mui2ClientQa.call(inv, "writeToNBT|func_70442_a", tag);
        Object cursor = Mui2ClientQa.call(inv, "getItemStack|func_70445_o");
        Object cursorTag = Mui2ClientQa.type("net.minecraft.nbt.NBTTagCompound").newInstance();
        if (cursor != null) Mui2ClientQa.call(cursor, "writeToNBT|func_77955_b", cursorTag);
        return s.nbt(tag) + "|cursor=" + (cursor == null ? "null" : s.nbt(cursorTag));
    }

    private static void inventoryProof(UiQaScript s, JsonObject step) throws Exception {
        if (step.has("baseline") && step.get("baseline").getAsBoolean()) {
            s.variables.put(PREFIX + "inventoryBaseline", playerInventory(s));
            JsonObject event = new JsonObject();
            event.addProperty("cluster215InventoryBaseline", "Captured immediately before statistics navigation and preview clicks, after normal first-open terminal identity NBT creation; no NBT fields excluded");
            s.events.add(event);
            return;
        }
        s.q.check(PREFIX + "player-inventory-and-cursor-conserved", playerInventory(s).equals(s.variables.get(PREFIX + "inventoryBaseline")), "Complete real server inventory and cursor canonical NBT immediately before/after statistics preview clicks; no NBT fields excluded");
        Object controller = s.target("cluster215Controller");
        for (String field : new String[] { "statInput", "statOutput", "statBonus" })
            s.q.check(PREFIX + "all64-still-recorded-" + field, ((Map<?, ?>) Mui2ClientQa.field(controller, field)).size() == 64, "All 64 explicit server fixture entries preserved");
        s.q.check(PREFIX + "no-ore-processing-claim", ((Number) Mui2ClientQa.call(controller, "getTotalProcessedOre")).longValue() == 0L, "Controller stayed OFF with no recipe inputs or ore execution");
    }

    private static Object copy(Object stack) throws Exception {
        return stack == null ? null : Mui2ClientQa.call(stack, "copy|func_77946_l");
    }

    private static void cleanup(UiQaScript s) throws Exception {
        JsonObject cleanup = new JsonObject(); cleanup.addProperty("op", "galleryCleanup"); s.gallery.execute(cleanup);
        Object world = s.target("world"), stone = Mui2ClientQa.field(Mui2ClientQa.type("net.minecraft.init.Blocks"), "stone|field_150348_b");
        List<int[]> floors = (List<int[]>) s.variables.get(PREFIX + "floors");
        if (floors != null) for (int[] p : floors) {
            Object block = Mui2ClientQa.call(world, "getBlock|func_147439_a", p[0], p[1], p[2]);
            if (block == stone) Mui2ClientQa.call(world, "setBlockToAir|func_147468_f", p[0], p[1], p[2]);
            else if (!Boolean.TRUE.equals(Mui2ClientQa.call(world, "isAirBlock|func_147437_c", p[0], p[1], p[2])))
                throw new AssertionError("Owned support identity changed during cleanup");
        }
        List<?> prior = (List<?>) s.variables.get(PREFIX + "priorInventory");
        if (prior != null) {
            Object inv = Mui2ClientQa.field(s.q.serverPlayer, "inventory|field_71071_by");
            for (int i = 0; i < prior.size(); i++) Mui2ClientQa.call(inv, "setInventorySlotContents|func_70299_a", i, prior.get(i));
            Mui2ClientQa.call(inv, "setItemStack|func_70437_b", s.variables.get(PREFIX + "priorCursor"));
            Mui2ClientQa.set(inv, "currentItem|field_70461_c", s.variables.get(PREFIX + "priorHotbar"));
            Object[] position = (Object[]) s.variables.get(PREFIX + "priorPosition");
            Mui2ClientQa.call(s.q.serverPlayer, "setPositionAndUpdate|func_70634_a", position[0], position[1], position[2]);
        }
        s.q.check(PREFIX + "owned-fixture-cleaned", s.gallery.positions.isEmpty(), "Tracked GT hosts removed, newly created air support footprint cleared, prior owned-player inventory restored");
    }
}
