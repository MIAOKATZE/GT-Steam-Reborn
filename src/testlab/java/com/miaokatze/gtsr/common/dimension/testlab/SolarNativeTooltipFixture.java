// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

import net.minecraftforge.client.event.GuiScreenEvent;

import com.google.gson.*;

import cpw.mods.fml.common.eventhandler.*;

/** Explicit owned-client test. Natural inventory/NEI hover; synthetic in-process input only. */
public final class SolarNativeTooltipFixture {

    public static final String SCRIPT = "gtsr-native-tooltip-table-v1";

    public interface Hooks {

        void complete(Map<String, Object> receipt, Throwable failure);

        void screenshot(String name);
    }

    private final Hooks hooks;
    private final Path save, output;
    private final JsonArray cases;
    private final JsonObject expected;
    private final Map<String, Object> receipt = new LinkedHashMap<String, Object>();
    private final List<Map<String, Object>> captures = new ArrayList<Map<String, Object>>();
    private volatile Object serverActor, serverInventory, originalSlot, stack;
    private volatile boolean serverReady, serverRestored, cancel;
    private volatile Throwable failure;
    private Object mc, gui, originalItems, grid;
    private boolean opened, finished, configured, frameOwned;
    private int caseIndex = -1, settle, draws, oldScale, oldMouseX, oldMouseY, oldFrameCap;
    private boolean oldUnicode, oldPause, oldFontUnicode;
    private ByteBuffer originalKeys;
    private Map<String, Object> current;
    private JsonObject row;
    private int hoverX, hoverY, selectedPage, totalPages = 1;
    private boolean caseVerified, captureRequested, togglePending;
    private int solarMeta, effectStep;
    private Object ops, profile, originalOp;
    private final List<Map<String, Object>> keyChecks = new ArrayList<Map<String, Object>>();
    private long started = System.nanoTime();

    public SolarNativeTooltipFixture(Path plan, Hooks hooks) throws Exception {
        this.hooks = hooks;
        JsonObject doc = new JsonParser().parse(new String(Files.readAllBytes(plan), StandardCharsets.UTF_8))
            .getAsJsonObject();
        require(
            SCRIPT.equals(
                doc.get("scriptOnly")
                    .getAsString()),
            "Wrong script identity");
        cases = doc.getAsJsonArray("cases");
        expected = doc.getAsJsonObject("expectedArtifacts");
        require(cases.size() == 16, "Explicit 16-case font/scale/context/shift matrix required");
        save = Paths.get(System.getProperty("miaotest.host.worldPath"))
            .toAbsolutePath()
            .normalize();
        output = Paths.get(System.getProperty("gtsr.uiqa.receipt"))
            .getParent();
        String run = System.getProperty("miaotest.host.runId");
        JsonObject marker = new JsonParser()
            .parse(new String(Files.readAllBytes(save.resolve(".miaotest-host-owner.json")), StandardCharsets.UTF_8))
            .getAsJsonObject();
        require(
            run.equals(
                marker.get("runId")
                    .getAsString()),
            "Owned world marker mismatch");
        receipt.put("scriptOnly", SCRIPT);
        receipt.put("runId", run);
        receipt.put("captures", captures);
        receipt.put("keyChecks", keyChecks);
        receipt.put(
            "input",
            "Synthetic DrawScreen.Pre pointer plus temporary lwjgl3ify client mouse/keyboard snapshots; no OS input");
        receipt.put("cleanupPending", true);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    public void cancel(Throwable cause) {
        while (cause instanceof InvocationTargetException && cause.getCause() != null) cause = cause.getCause();
        if (failure == null) failure = cause;
        cancel = true;
    }

    public boolean completeForExit() {
        return finished;
    }

    public void serverStopping(Object server) {
        if (!serverRestored) cancel(new IllegalStateException("Stopped before restoring actor slot"));
    }

    public void serverTick(Object server) {
        try {
            if (cancel) {
                if (serverInventory != null) {
                    call(serverInventory, "setInventorySlotContents|func_70299_a", 0, originalSlot);
                    call(serverInventory, "markDirty|func_70296_d");
                }
                if (ops != null) {
                    if (originalOp == null) call(ops, "func_152684_c", profile);
                    else call(ops, "func_152687_a", originalOp);
                    ops = null;
                }
                serverRestored = true;
                return;
            }
            if (serverReady) return;
            List<?> actors = (List<?>) field(
                call(server, "getConfigurationManager|func_71203_ab"),
                "playerEntityList|field_72404_b");
            if (actors.isEmpty()) return;
            require(actors.size() == 1, "One owned native actor required");
            serverActor = actors.get(0);
            Object w = field(serverActor, "worldObj|field_70170_p");
            Path actual = ((java.io.File) call(
                call(w, "getSaveHandler|func_72860_G"),
                "getWorldDirectory|func_75765_b")).toPath()
                    .toAbsolutePath()
                    .normalize();
            require(save.equals(actual), "Refuse existing save");
            Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
            Map<?, ?> mods = (Map<?, ?>) call(
                call(type("cpw.mods.fml.common.Loader"), "instance"),
                "getIndexedModList");
            for (String id : new String[] { "gtsr", "miaokatzecore" }) {
                Object mod = mods.get(id);
                Path jar = ((java.io.File) call(mod, "getSource")).toPath();
                String hash = sha(jar);
                require(
                    hash.equals(
                        expected.get(id)
                            .getAsString()),
                    "Loaded candidate mismatch " + id);
                Map<String, Object> a = new LinkedHashMap<String, Object>();
                a.put("path", jar.toString());
                a.put("sha256", hash);
                a.put("version", call(mod, "getVersion"));
                artifacts.put(id, a);
            }
            receipt.put("artifacts", artifacts);
            Object manager = call(server, "getConfigurationManager|func_71203_ab");
            ops = call(manager, "func_152603_m");
            profile = call(serverActor, "getGameProfile|func_146103_bH");
            originalOp = call(ops, "func_152683_b", profile);
            call(
                ops,
                "func_152687_a",
                type("net.minecraft.server.management.UserListOpsEntry")
                    .getConstructor(type("com.mojang.authlib.GameProfile"), int.class)
                    .newInstance(profile, 2));
            boolean op2 = Boolean.TRUE
                .equals(call(serverActor, "canCommandSenderUseCommand|func_70003_b", 2, "miaotest")),
                op4 = Boolean.TRUE.equals(call(serverActor, "canCommandSenderUseCommand|func_70003_b", 4, "miaotest"));
            require(op2 && !op4, "Control actor requires exact OP2");
            receipt.put("actorOp2", op2);
            receipt.put("actorOp4", op4);
            receipt.put("businessPermissionScope", "NOT_TESTED: visual read-only actor, no business operation");
            require(
                !Boolean.TRUE
                    .equals(field(field(serverActor, "capabilities|field_71075_bZ"), "isCreativeMode|field_75098_d")),
                "Survival actor");
            serverInventory = field(serverActor, "inventory|field_71071_by");
            originalSlot = call(serverInventory, "getStackInSlot|func_70301_a", 0);
            if (originalSlot != null) originalSlot = call(originalSlot, "copy|func_77946_l");
            stack = call(
                field(type("com.miaokatze.gtsr.common.api.enums.GTSRItemList"), "CriticalSolar"),
                "get",
                1L,
                new Object[0]);
            solarMeta = ((Number) call(stack, "getItemDamage|func_77960_j")).intValue();
            Object meta = call(type("gregtech.common.blocks.ItemMachines"), "getMetaTileEntity", stack);
            require("SOLAR".equals(String.valueOf(call(meta, "getPresetKind"))), "Registered solar machine kind");
            receipt.put("registeredSolarMeta", solarMeta);
            call(serverInventory, "setInventorySlotContents|func_70299_a", 0, stack);
            call(serverInventory, "markDirty|func_70296_d");
            serverReady = true;
        } catch (Throwable t) {
            cancel(t);
        }
    }

    public void clientTick(Object value) throws Exception {
        mc = value;
        if (finished) return;
        if (!opened) {
            require(field(mc, "theWorld|field_71441_e") == null, "Refuse existing world");
            Object gt = Enum.valueOf((Class) type("net.minecraft.world.WorldSettings$GameType"), "SURVIVAL");
            Object settings = type("net.minecraft.world.WorldSettings")
                .getConstructor(
                    long.class,
                    type("net.minecraft.world.WorldSettings$GameType"),
                    boolean.class,
                    boolean.class,
                    type("net.minecraft.world.WorldType"))
                .newInstance(
                    20261114L,
                    gt,
                    false,
                    false,
                    field(type("net.minecraft.world.WorldType"), "FLAT|field_77138_c"));
            call(
                mc,
                "launchIntegratedServer|func_71371_a",
                save.getFileName()
                    .toString(),
                "Owned native Tooltip",
                settings);
            opened = true;
            return;
        }
        if (cancel) {
            restoreFrame();
            if (!serverRestored) return;
            if (configured) {
                Object settings = field(mc, "gameSettings|field_71474_y");
                set(settings, "guiScale|field_74335_Z", oldScale);
                set(settings, "forceUnicodeFont|field_151455_aw", oldUnicode);
                set(settings, "pauseOnLostFocus|field_82881_y", oldPause);
                set(settings, "limitFramerate|field_74350_i", oldFrameCap);
                call(field(mc, "fontRenderer|field_71466_p"), "setUnicodeFlag|func_78264_a", oldFontUnicode);
                if (originalItems != null) call(type("codechicken.nei.ItemPanel"), "updateItemList", originalItems);
            }
            receipt.put("slotRestored", serverRestored);
            receipt.put("clientSettingsRestored", true);
            receipt.put("cleanupPending", false);
            if (failure != null) {
                receipt.put("failure", failure.toString());
                List<String> trace = new ArrayList<String>();
                for (StackTraceElement at : failure.getStackTrace()) trace.add(at.toString());
                receipt.put("stack", trace);
            }
            receipt.put(
                "status",
                failure == null && caseIndex == cases.size() && captures.size() >= cases.size() ? "PASS" : "FAIL");
            finished = true;
            Files.write(
                output.resolve("native-tooltip-table.json"),
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(receipt)
                    .getBytes(StandardCharsets.UTF_8));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
            hooks.complete(receipt, failure);
            return;
        }
        if (System.nanoTime() - started > 800_000_000_000L) {
            cancel(new IllegalStateException("Bounded Tooltip deadline"));
            return;
        }
        if (!serverReady || field(mc, "thePlayer|field_71439_g") == null) return;
        Object player = field(mc, "thePlayer|field_71439_g");
        Object clientStack = call(field(player, "inventory|field_71071_by"), "getStackInSlot|func_70301_a", 0);
        if (clientStack == null || ((Number) call(clientStack, "getItemDamage|func_77960_j")).intValue() != solarMeta)
            return;
        if (((Number) field(player, "ticksExisted|field_70173_aa")).intValue() < 80) return;
        if (!configured) {
            Object settings = field(mc, "gameSettings|field_71474_y"), font = field(mc, "fontRenderer|field_71466_p");
            oldScale = ((Number) field(settings, "guiScale|field_74335_Z")).intValue();
            oldUnicode = (Boolean) field(settings, "forceUnicodeFont|field_151455_aw");
            oldPause = (Boolean) field(settings, "pauseOnLostFocus|field_82881_y");
            oldFrameCap = ((Number) field(settings, "limitFramerate|field_74350_i")).intValue();
            oldFontUnicode = (Boolean) call(font, "getUnicodeFlag|func_82883_a");
            set(settings, "pauseOnLostFocus|field_82881_y", false);
            set(settings, "limitFramerate|field_74350_i", 30);
            Class<?> display = type("org.lwjglx.opengl.Display");
            require(!Boolean.TRUE.equals(call(display, "isFullscreen")), "Windowed private desktop");
            call(
                display,
                "setDisplayMode",
                type("org.lwjglx.opengl.DisplayMode").getConstructor(int.class, int.class)
                    .newInstance(2560, 1440));
            call(mc, "resize|func_71370_a", 2560, 1440);
            configured = true;
        }
        if (caseIndex < 0) {
            caseIndex = 0;
            prepare(player);
            return;
        }
        if (draws < 6) {
            if (++settle > 1000) cancel(new IllegalStateException("Natural hover timeout: " + row));
            return;
        }
        if (current != null && !captureRequested) {
            verifyCapture();
            captures.add(current);
            hooks.screenshot(
                row.get("id")
                    .getAsString() + "-p"
                    + selectedPage);
            captureRequested = true;
            return;
        }
        captureRequested = false;
        if (selectedPage + 1 < totalPages) {
            selectedPage++;
            draws = 0;
            current = new LinkedHashMap<String, Object>();
            current.put("case", row);
            call(type("com.slprime.chromatictooltips.TooltipHandler"), "nextTooltipPage");
            return;
        }
        if (caseIndex == 0 && effectStep < 2) {
            effectStep++;
            prepare(player);
            row.addProperty("id", effectStep == 1 ? "effects-off" : "effects-restored");
            togglePending = true;
            return;
        }
        require(caseVerified, "Case did not produce native table/structure proof");
        if (++caseIndex == cases.size()) {
            cancel = true;
            return;
        }
        prepare(player);
    }

    private void prepare(Object player) throws Exception {
        row = new JsonParser().parse(
            cases.get(caseIndex)
                .toString())
            .getAsJsonObject();
        draws = settle = 0;
        selectedPage = 0;
        captureRequested = false;
        totalPages = 1;
        caseVerified = false;
        current = new LinkedHashMap<String, Object>();
        current.put("case", row);
        Object settings = field(mc, "gameSettings|field_71474_y");
        set(
            settings,
            "guiScale|field_74335_Z",
            row.get("scale")
                .getAsInt());
        set(
            settings,
            "forceUnicodeFont|field_151455_aw",
            row.get("unicode")
                .getAsBoolean());
        call(
            field(mc, "fontRenderer|field_71466_p"),
            "setUnicodeFlag|func_78264_a",
            row.get("unicode")
                .getAsBoolean());
        call(mc, "resize|func_71370_a", 2560, 1440);
        gui = type("net.minecraft.client.gui.inventory.GuiInventory")
            .getConstructor(type("net.minecraft.entity.player.EntityPlayer"))
            .newInstance(player);
        call(mc, "displayGuiScreen|func_147108_a", gui);
        Object panel = field(type("codechicken.nei.ItemPanels"), "itemPanel");
        grid = call(panel, "getGrid");
        if (originalItems == null) originalItems = new ArrayList<Object>((List<?>) call(panel, "getItems"));
        ArrayList<Object> one = new ArrayList<Object>();
        one.add(stack);
        call(type("codechicken.nei.ItemPanel"), "updateItemList", one);
        current.put("actualFontUnicode", call(field(mc, "fontRenderer|field_71466_p"), "getUnicodeFlag|func_82883_a"));
        current.put(
            "fontSampleWidth",
            call(field(mc, "fontRenderer|field_71466_p"), "getStringWidth|func_78256_a", "ABC 123 中"));
        current.put("physicalWindowWidth", call(type("org.lwjglx.opengl.Display"), "getWidth"));
        current.put("physicalWindowHeight", call(type("org.lwjglx.opengl.Display"), "getHeight"));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void before(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (cancel || gui == null || event.gui != gui || current == null) return;
        try {
            restoreFrame();
            int w = ((Number) field(gui, "width|field_146294_l")).intValue(),
                h = ((Number) field(gui, "height|field_146295_m")).intValue();
            if (row.get("context")
                .getAsString()
                .equals("nei")) {
                Object rect = call(grid, "getItemRect", 0);
                if (rect == null) return;
                hoverX = ((Number) field(rect, "x")).intValue() + 8;
                hoverY = ((Number) field(rect, "y")).intValue() + 8;
            } else {
                int left = ((Number) field(gui, "guiLeft|field_147003_i")).intValue(),
                    top = ((Number) field(gui, "guiTop|field_147009_r")).intValue();
                hoverX = left + 16;
                hoverY = top + 150;
            }
            set(event, "mouseX", hoverX);
            set(event, "mouseY", hoverY);
            Class<?> mouse = type("org.lwjglx.input.Mouse");
            oldMouseX = ((Number) field(mouse, "x")).intValue();
            oldMouseY = ((Number) field(mouse, "y")).intValue();
            set(mouse, "x", (hoverX * 2560 / w));
            set(mouse, "y", ((h - hoverY - 1) * 1440 / h));
            frameOwned = true;
            Class<?> kb = type("org.lwjglx.input.Keyboard");
            originalKeys = (ByteBuffer) field(kb, "sdlKeyPressedArray");
            ByteBuffer local = ByteBuffer.allocateDirect(originalKeys.capacity());
            for (int i = 0; i < originalKeys.capacity(); i++) local.put(i, originalKeys.get(i));
            int scan = ((Number) call(type("org.lwjglx.input.KeyCodes"), "lwjglToSdlScancode", 42)).intValue();
            local.put(
                scan,
                (byte) (row.get("shift")
                    .getAsBoolean() ? 1 : 0));
            set(kb, "sdlKeyPressedArray", local);
            Object hit = call(type("codechicken.nei.guihook.GuiContainerManager"), "getStackMouseOver", gui);
            if (hit != null) {
                require(
                    ((Number) call(hit, "getItemDamage|func_77960_j")).intValue() == solarMeta,
                    "Natural hit must be solar item");
                current.put("hitItemMeta", solarMeta);
                current.put(
                    "hitContext",
                    row.get("context")
                        .getAsString());
            }
            current.put("guiWidth", w);
            current.put("guiHeight", h);
        } catch (Throwable t) {
            cancel(t);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void after(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui == gui) try {
            if (togglePending && draws >= 2) {
                toggleEffect();
                togglePending = false;
                draws = 0;
            }
            restoreFrame();
        } catch (Throwable t) {
            cancel(t);
        }
    }

    @SubscribeEvent
    public void painted(cpw.mods.fml.common.eventhandler.Event event) {
        if (current == null || cancel
            || !event.getClass()
                .getName()
                .equals("com.slprime.chromatictooltips.event.RenderTooltipEvent"))
            return;
        try {
            Object context = field(event, "context"), item = call(context, "getItem");
            if (item == null) return;
            if (((Number) call(item, "getItemDamage|func_77960_j")).intValue() != solarMeta) return;
            Map<String, Object> box = new LinkedHashMap<String, Object>();
            box.put("x", field(event, "x"));
            box.put("y", field(event, "y"));
            box.put("width", field(event, "width"));
            box.put("height", field(event, "height"));
            box.put("scale", call(context, "getScaleFactor"));
            current.put("actualRenderEvent", box);
            int actual = ((Number) field(context, "currentPage")).intValue();
            if (actual != selectedPage) {
                for (int i = 0; i < 40 && actual != selectedPage; i++) {
                    call(
                        type("com.slprime.chromatictooltips.TooltipHandler"),
                        actual > selectedPage ? "previousTooltipPage" : "nextTooltipPage");
                    actual = ((Number) field(context, "currentPage")).intValue();
                }
                draws = 0;
                return;
            }
            Object active = call(context, "getActivePageComponent");
            List<String> texts = new ArrayList<String>();
            texts(active, texts);
            current.put("activeTexts", texts);
            List<String> allTexts = new ArrayList<String>();
            for (Object p : (List<?>) field(context, "pagedComponents")) texts(p, allTexts);
            current.put("allTexts", allTexts);
            List<String> classes = new ArrayList<String>();
            collect(active, classes);
            current.put("componentClasses", classes);
            List<?> pages = (List<?>) field(context, "pagedComponents");
            List<Object> layouts = new ArrayList<Object>();
            for (Object page : pages) tables(page, layouts);
            current.put("allTableLayouts", layouts);
            totalPages = pages.size();
            current.put("pages", ((List<?>) field(context, "pagedComponents")).size());
            current.put("actualPage", field(context, "currentPage"));
            current.put(
                "animationEnabled",
                call(
                    type("com.miaokatze.miaokatzecore.api.tooltip.TooltipPages"),
                    "animationEnabled",
                    call(type("com.miaokatze.miaokatzecore.api.tooltip.TooltipPages"), "identity", item)));
            Object shader = field(type("com.miaokatze.miaokatzecore.client.tooltip.art.SealShader"), "INSTANCE");
            current.put("sealShaderProgram", field(shader, "program"));
            current.put("sealShaderFailed", field(shader, "failed"));
            try { current.put("openGL30", field(call(type("org.lwjgl.opengl.GL"), "getCapabilities"), "OpenGL30")); }
            catch (ReflectiveOperationException unavailable) { current.put("openGL30", "DIAGNOSTIC_UNAVAILABLE: "+unavailable); }
            draws++;
        } catch (Throwable t) {
            cancel(t);
        }
    }

    private void tables(Object component, List<Object> layouts) throws Exception {
        if (component.getClass()
            .getName()
            .equals("com.miaokatze.miaokatzecore.client.tooltip.NativeTableComponent"))
            layouts.add(call(component, "layoutSnapshot"));
        try {
            Object children = call(component, "getComponents");
            if (children instanceof Iterable) for (Object child : (Iterable<?>) children) tables(child, layouts);
        } catch (NoSuchMethodException ignored) {}
    }

    private void verifyCapture() throws Exception {
        require(current.containsKey("hitItemMeta"), "Natural item hit was never observed");
        require(
            ((Number) ((Map<?, ?>) current.get("actualRenderEvent")).get("scale")).intValue() == row.get("scale")
                .getAsInt(),
            "Actual scale");
        boolean shift = row.get("shift")
            .getAsBoolean();
        List<?> layouts = (List<?>) current.get("allTableLayouts");
        if (shift) {
            String text = String.valueOf(current.get("allTexts"));
            require(
                (text.contains("控制器") || text.contains("Controller"))
                    && (text.contains("结构") || text.contains("Structure")),
                "Actual GT structure markers");
            require(layouts.isEmpty(), "Native tables must not replace structure page");
            caseVerified = true;
        } else {
            require(layouts.size() >= 2, "Both coolant and activator tables must be present across native pages");
            List<Object> allRows = new ArrayList<Object>();
            for (Object table : layouts) allRows.addAll((List<?>) ((Map<?, ?>) table).get("rows"));
            require(allRows.size() == 9, "Three coolant and six activator rows, complete across pages");
            for (String group : new String[] { "COOLANTS", "EXCITERS" }) for (Object entry : (List<?>) field(
                type("com.miaokatze.gtsr.common.critical.CriticalSolarPolicy"),
                group)) {
                    String id = String.valueOf(field(entry, "id")),
                        prefix = group.equals("COOLANTS") ? "coolant" : "exciter";
                    String label = String.valueOf(
                        call(
                            type("net.minecraft.util.StatCollector"),
                            "translateToLocal|func_74838_a",
                            "gtsr.critical.solar." + prefix + "." + id));
                    String flow = String.valueOf(
                        call(type("com.miaokatze.gtsr.common.critical.CriticalTooltips"), "solarMediumFlow", entry));
                    String multiplier = String.valueOf(
                        call(
                            type("com.miaokatze.gtsr.common.critical.CriticalTooltips"),
                            "solarMediumMultiplier",
                            entry));
                    boolean found = false;
                    for (Object cells : allRows) {
                        String r = String.valueOf(cells)
                            .replaceAll("§.", "");
                        if (r.contains(label) && r.contains(flow + " L/s") && r.contains("×" + multiplier))
                            found = true;
                    }
                    require(found, "Native table row differs from authoritative policy: " + id);
                }
            caseVerified = true;
        }
        require(((Number) current.get("actualPage")).intValue() == selectedPage, "Actual selected native page");
        require(org.lwjgl.opengl.GL11.glGetError() == 0, "Client GL error");
        current.put("glError", 0);
        current.put("nativeHoverVerified", true);
    }

    private void toggleEffect() throws Exception {
        Class<?> kb = type("org.lwjglx.input.Keyboard");
        Object queue = field(kb, "eventQueue");
        ByteBuffer keys = (ByteBuffer) field(kb, "sdlKeyPressedArray");
        ByteBuffer local = ByteBuffer.allocateDirect(keys.capacity());
        for (int i = 0; i < keys.capacity(); i++) local.put(i, keys.get(i));
        int alt = ((Number) call(type("org.lwjglx.input.KeyCodes"), "lwjglToSdlScancode", 56)).intValue();
        local.put(alt, (byte) 1);
        java.util.concurrent.ArrayBlockingQueue<Object> events = new java.util.concurrent.ArrayBlockingQueue<Object>(2);
        Object press = Enum.valueOf((Class) type("org.lwjglx.input.Keyboard$KeyState"), "PRESS");
        events.add(
            type("org.lwjglx.input.Keyboard$KeyEvent")
                .getConstructor(int.class, int.class, int.class, type("org.lwjglx.input.Keyboard$KeyState"), long.class)
                .newInstance(47, 47, 118, press, System.nanoTime()));
        Object api = type("com.miaokatze.miaokatzecore.api.tooltip.TooltipPages"), key = call(api, "identity", stack);
        boolean before = (Boolean) call(api, "animationEnabled", key);
        try {
            set(kb, "sdlKeyPressedArray", local);
            set(kb, "eventQueue", events);
            Object event = type("com.cleanroommc.modularui.api.event.KeyboardInputEvent$Pre")
                .getConstructor(type("net.minecraft.client.gui.GuiScreen"))
                .newInstance(gui);
            call(net.minecraftforge.common.MinecraftForge.EVENT_BUS, "post", event);
            boolean after = (Boolean) call(api, "animationEnabled", key);
            require(
                before != after && Boolean.TRUE.equals(call(event, "isCanceled")),
                "Actual Core Alt+V event must consume and toggle animation");
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("before", before);
            result.put("after", after);
            result.put("canceled", true);
            result.put("input", "synthetic KeyboardInputEvent.Pre under current natural hovered item");
            keyChecks.add(result);
        } finally {
            set(kb, "sdlKeyPressedArray", keys);
            set(kb, "eventQueue", queue);
        }
    }

    private void texts(Object c, List<String> out) throws Exception {
        if (c.getClass()
            .getName()
            .equals("com.slprime.chromatictooltips.component.TextComponent"))
            out.addAll((List<String>) call(c, "getLines"));
        try {
            Object children = call(c, "getComponents");
            if (children instanceof Iterable) for (Object child : (Iterable<?>) children) texts(child, out);
        } catch (NoSuchMethodException ignored) {}
    }

    private void collect(Object component, List<String> types) throws Exception {
        types.add(
            component.getClass()
                .getName());
        try {
            Object children = call(component, "getComponents");
            if (children instanceof Iterable) for (Object child : (Iterable<?>) children) collect(child, types);
        } catch (NoSuchMethodException ignored) {}
    }

    public void render(boolean start) {}

    private void restoreFrame() throws Exception {
        if (originalKeys != null) {
            set(type("org.lwjglx.input.Keyboard"), "sdlKeyPressedArray", originalKeys);
            originalKeys = null;
        }
        if (frameOwned) {
            set(type("org.lwjglx.input.Mouse"), "x", oldMouseX);
            set(type("org.lwjglx.input.Mouse"), "y", oldMouseY);
            frameOwned = false;
        }
    }

    private static String sha(Path p) throws Exception {
        StringBuilder b = new StringBuilder();
        for (byte v : MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(p))) b.append(String.format("%02x", v & 255));
        return b.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static Throwable unwrap(Throwable t) {
        while (t instanceof InvocationTargetException && t.getCause() != null) t = t.getCause();
        return t;
    }

    private static Class<?> type(String name) throws Exception {
        return Class.forName(name);
    }

    private static Object field(Object target, String aliases) throws Exception {
        for (String name : aliases.split("\\|"))
            for (Class<?> c = target instanceof Class ? (Class<?>) target : target.getClass(); c
                != null; c = c.getSuperclass()) try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target instanceof Class ? null : target);
                } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(aliases);
    }

    private static void set(Object target, String aliases, Object value) throws Exception {
        for (String name : aliases.split("\\|"))
            for (Class<?> c = target instanceof Class ? (Class<?>) target : target.getClass(); c
                != null; c = c.getSuperclass()) try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    f.set(target instanceof Class ? null : target, value);
                    return;
                } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(aliases);
    }

    private static Object call(Object target, String aliases, Object... args) throws Exception {
        Class<?> root = target instanceof Class ? (Class<?>) target : target.getClass();
        for (Class<?> c = root; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods()) for (String name : aliases.split("\\|")) {
                if (!m.getName()
                    .equals(name) || m.getParameterTypes().length != args.length) continue;
                Class<?>[] ts = m.getParameterTypes();
                Object[] converted = args.clone();
                boolean fits = true;
                for (int i = 0; i < ts.length; i++) {
                    Object a = args[i];
                    if (a == null) {
                        if (ts[i].isPrimitive()) fits = false;
                    } else if (ts[i].isPrimitive() && a instanceof Number) {
                        Number n = (Number) a;
                        if (ts[i] == long.class) converted[i] = n.longValue();
                        else if (ts[i] == int.class) converted[i] = n.intValue();
                        else if (ts[i] == float.class) converted[i] = n.floatValue();
                        else if (ts[i] == double.class) converted[i] = n.doubleValue();
                        else if (ts[i] == short.class) converted[i] = n.shortValue();
                        else if (ts[i] == byte.class) converted[i] = n.byteValue();
                        else fits = false;
                    } else if (ts[i] == boolean.class && a instanceof Boolean) {} else
                        if (!ts[i].isInstance(a)) fits = false;
                }
                if (!fits) continue;
                m.setAccessible(true);
                return m.invoke(target instanceof Class ? null : target, converted);
            }
        throw new NoSuchMethodException(aliases + " on " + root);
    }
}
