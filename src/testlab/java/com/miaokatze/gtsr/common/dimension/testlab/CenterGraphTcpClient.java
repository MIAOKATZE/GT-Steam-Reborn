// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualClient;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Drives native client input handlers inside the private desktop, without OS input or direct graph mutation. */
@SideOnly(Side.CLIENT)
public final class CenterGraphTcpClient {

    private static final String CONTROL = "center_graph_";
    private static final String FRAME = "reopened-native-graph.png";
    private static final String[] PAGES = { "assembly", "routes", "stats", "calculation" };

    private final String runId = System.getProperty(CenterGraphTcpFixture.PREFIX + "run");
    private final int port = Integer.getInteger(CenterGraphTcpFixture.PREFIX + "port");
    private final Path receipts = Paths.get(System.getProperty(CenterGraphTcpFixture.PREFIX + "receipts"));
    private final Minecraft mc = Minecraft.getMinecraft();
    private long since = System.nanoTime(), expectedRevision;
    private int phase, openCooldown, layoutStep, presetNodes;
    private boolean verifiedEmptyBoard, verifiedGraphHistory;
    private boolean screenshot, done;
    private int projectedHints, previewStep;
    private boolean controllerOverlay, reopenedRoutes, openedRules, verifiedCopper;
    private int trialStep, trialTicks;
    private int terminalSlot = -1;
    private int verifiedAssemblyMask;
    private final boolean fullCenter = Boolean.getBoolean(CenterGraphTcpFixture.PREFIX + "fullCenter");
    private boolean gtCaptured, gtVerified;
    private String capturePage;
    private final List<String> pageFrames = new ArrayList<>();
    private String lastTrace = "";
    private final boolean interactive = Boolean.getBoolean(CenterGraphTcpFixture.PREFIX + "interactive");
    private final boolean presetLoad = Boolean.getBoolean(CenterGraphTcpFixture.PREFIX + "presetLoad");
    private final CenterFrameProfile frameProfile = Boolean.getBoolean(CenterGraphTcpFixture.PREFIX + "profileFrames")
        ? new CenterFrameProfile(mc, receipts)
        : null;
    private int presetStage;
    private long crownFirstPose = -1, crownStartReplays;
    private final CenterNativeReviewInput reviewer = interactive ? new CenterNativeReviewInput(mc, receipts) : null;

    static void register() {
        if (System.getProperty(CenterGraphTcpFixture.PREFIX + "port") != null) {
            FMLCommonHandler.instance()
                .bus()
                .register(new CenterGraphTcpClient());
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        try {
            if (Files.exists(receipts.resolve("stop.json"))) {
                if (interactive) {
                    done = true;
                    CenterGraphTcpFixture
                        .write(receipts, "review-closed.json", java.util.Collections.singletonMap("runId", runId));
                    mc.shutdown();
                } else finish("Cancelled by host");
                return;
            }
            if (phase == 0) {
                if (mc.currentScreen == null) return;
                FMLClientHandler forge = FMLClientHandler.instance();
                forge.setupServerList();
                ServerData endpoint = new ServerData("GTSR TestLab", "127.0.0.1:" + port);
                forge.connectToServer(new GuiMainMenu(), endpoint);
                CenterGraphTcpFixture
                    .require(mc.func_147104_D() == endpoint, "Forge did not select the owned endpoint");
                advance();
                return;
            }
            if (!interactive) CenterGraphTcpFixture.require(
                System.nanoTime() - since < TimeUnit.SECONDS.toNanos(presetLoad ? 390 : 120),
                "Client phase timed out: " + phase + "/" + presetStage);
            if (mc.currentScreen instanceof GuiDisconnected) {
                IChatComponent reason = (IChatComponent) readField(mc.currentScreen, "field_146304_f");
                throw new IllegalStateException("Dedicated TCP connection ended: " + reason.getUnformattedText());
            }
            if (mc.thePlayer == null) {
                trace("waiting for TCP player, screen=" + currentScreen());
                return;
            }

            if (frameProfile != null) {
                Path server = receipts.resolve("server-checks.json");
                JsonObject result = Files.isRegularFile(server) ? json(server) : null;
                if (result != null) CenterGraphTcpFixture.require(
                    "PASS".equals(
                        result.get("status")
                            .getAsString()),
                    "Server frame workload failed: " + result.get("error"));
                if (frameProfile.tick() && result != null) finish(null);
                return;
            }
            ModularScreen screen = screen();
            if (presetLoad) {
                verifyPresetLoad(screen);
                return;
            }
            if (interactive) {
                if (screen == null) open();
                return;
            }
            if (phase == 1 && (fullCenter || projectedHints != 0) && !gtVerified) {
                if (!fullCenter || Files.isRegularFile(receipts.resolve("server-full-ready.json"))) verifyGtWindow();
                return;
            }
            if (phase == 1 || phase == 7) {
                if (screen == null) {
                    trace("waiting for graph panel, screen=" + currentScreen());
                    open();
                    return;
                }
                if (phase == 1 && previewStep <= PAGES.length + 5) {
                    if (capturePage != null) return;
                    Object editor = readField(control(screen, "tabs"), "editor");
                    Object sync = readField(editor, "sync");
                    var terminal = (net.minecraft.nbt.NBTTagCompound) readField(sync, "terminal");
                    if (!terminal.getBoolean("Formed") || terminal.getIntArray("Grades")[3] == 0) return;
                    if (previewStep == 0) {
                        var costs = terminal.getTagList("BoostCosts", 10);
                        for (int i = 0; i < 4; i++) CenterGraphTcpFixture.require(
                            net.minecraftforge.fluids.FluidRegistry.getFluid(
                                costs.getCompoundTagAt(i)
                                    .getString("FluidName"))
                                != null,
                            "Missing synchronized additive fluid " + i);
                        IWidget diagram = find(screen.getMainPanel(), "center_assembly_scene");
                        if (diagram == null || (Integer) readField(diagram, "visibleMask") != 63) return;
                        int[] sx = (int[]) readField(diagram, "sx"), sy = (int[]) readField(diagram, "sy");
                        mouse(screen, diagram, sx[1], sy[1], true);
                        mouse(screen, diagram, sx[1], sy[1], false);
                        Object assembly = readField(diagram, "this$0");
                        CenterGraphTcpFixture.require(
                            (Integer) readField(assembly, "slot") == 1,
                            "Diagram did not select the installed crusher pad");
                        verifiedAssemblyMask = terminal.getInteger("Sectors");
                        CenterGraphTcpFixture
                            .require(!fullCenter || verifiedAssemblyMask == 63, "Expected all six live sectors");
                        capturePage = PAGES[0];
                    } else if (previewStep == 1) click(screen, "page_routes");
                    else if (previewStep == 2) {
                        if (!previewCopper(screen, editor)) return;
                        capturePage = "routes";
                    } else if (previewStep == 3) {
                        click(screen, "page_stats");
                        capturePage = "stats";
                    } else if (previewStep == 4) click(screen, "page_calculation");
                    else if (previewStep == 5) {
                        if (!previewTrial(screen)) return;
                        capturePage = "calculation";
                    } else if (previewStep == 6) click(screen, "page_routes");
                    else if (previewStep == 7) click(screen, "generator_presets");
                    else if (previewStep == 8) {
                        var presetPanel = screen.getPanelManager()
                            .getOpenPanel("gtsr_center_presets");
                        if (presetPanel == null) return;
                        CenterGraphTcpFixture.require(
                            find(presetPanel, CONTROL + "preset_regular-MPMC") != null
                                && find(presetPanel, CONTROL + "preset_special-dehydrator") != null,
                            "Generator lost its regular or expression-only presets");
                        capturePage = "presets";
                    } else screen.getPanelManager()
                        .closeTopPanel();
                    previewStep++;
                    return;
                }
                if (phase == 7 && !reopenedRoutes) {
                    click(screen, "page_routes");
                    reopenedRoutes = true;
                    return;
                }
                long revision = displayedRevision(screen);
                if (revision < expectedRevision) return;
                if (phase == 7 && layoutStep > 0) {
                    if (capturePage == null) verifyGraphLayout(screen);
                    return;
                }
                CenterGraphTcpFixture.require(
                    "rawOreCopper".equals(field(screen, "rules").getText()),
                    "The graph snapshot arrived but the selected copper route was not displayed: " + lastTrace);
                if (phase == 1) {
                    if (!openedRules) {
                        click(screen, "rules_toggle");
                        openedRules = true;
                        return;
                    }
                    if (!field(screen, "rules").areAncestorsEnabled()) return;
                    replace(screen, "rules", CenterGraphTcpFixture.ORE_RULE);
                    click(screen, "save_rules");
                    expectedRevision = revision + 1;
                } else {
                    tab(screen, 1, 1);
                    CenterGraphTcpFixture.require(
                        CenterGraphTcpFixture.ORE_RULE.equals(field(screen, "rules").getText())
                            && CenterGraphTcpFixture.FILTER_RULE.equals(field(screen, "filter").getText()),
                        "Server's reopened graph did not display both persisted expressions");
                    if (fullCenter) {
                        click(screen, "add");
                        layoutStep = 1;
                        return;
                    }
                    screenshot = true;
                }
                advance();
                return;
            }
            if (phase == 8) return;
            CenterGraphTcpFixture.require(screen != null, "Production graph screen closed at phase " + phase);
            long revision = displayedRevision(screen);
            if (revision < expectedRevision) return;
            switch (phase) {
                case 2:
                    click(screen, "filter_mode");
                    replace(screen, "filter", CenterGraphTcpFixture.FILTER_RULE);
                    click(screen, "save_filter");
                    break;
                case 3:
                case 4:
                    click(screen, "enabled");
                    break;
                case 5:
                    tab(screen, 1, 0);
                    break;
                case 6:
                    if (!Files.isRegularFile(receipts.resolve("server-checks.json"))) return;
                    mc.thePlayer.closeScreen();
                    advance();
                    return;
                default:
                    throw new IllegalStateException("Invalid client phase " + phase);
            }
            expectedRevision = revision + 1;
            advance();
        } catch (Exception failure) {
            GTSteamReborn.LOG.error("Ore center native TCP client failed at phase " + phase, failure);
            finish(failure.toString());
        }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (done) return;
        try {
            if (frameProfile != null) {
                if (event.phase == TickEvent.Phase.START) frameProfile.renderStart();
                else frameProfile.renderEnd();
                return;
            }
            if (event.phase != TickEvent.Phase.END) return;
            if (interactive) {
                ModularScreen current = screen();
                if (current != null && !reviewer.isReady()) {
                    Object editor = readField(control(current, "tabs"), "editor");
                    Object sync = readField(editor, "sync");
                    var terminal = (net.minecraft.nbt.NBTTagCompound) readField(sync, "terminal");
                    reviewer.initialize(terminal.getBoolean("Formed") && terminal.getIntArray("Grades")[3] != 0);
                }
                reviewer.render(current);
                return;
            }
            if (!screenshot && capturePage == null) return;
            String frame = screenshot ? presetLoad ? "native-preset-stats.png" : FRAME
                : "native-" + capturePage + ".png";
            ScreenShotHelper
                .saveScreenshot(receipts.toFile(), frame, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            // The vanilla screenshot helper always creates its screenshots/ subdirectory.
            Path captured = receipts.resolve("screenshots")
                .resolve(frame);
            Files.move(captured, receipts.resolve(frame));
            if (screenshot) finish(null);
            else {
                pageFrames.add(frame);
                capturePage = null;
            }
        } catch (Exception failure) {
            finish(failure.toString());
        }
    }

    private void advance() {
        phase++;
        since = System.nanoTime();
    }

    private void verifyPresetLoad(ModularScreen terminal) throws Exception {
        Path resultFile = receipts.resolve("server-checks.json");
        JsonObject result = Files.isRegularFile(resultFile) ? json(resultFile) : null;
        if (result != null && !"PASS".equals(
            result.get("status")
                .getAsString()))
            throw new IllegalStateException("Server preset failed: " + result.get("error"));
        if (capturePage != null) return;
        if (presetStage == 0) {
            Path active = receipts.resolve("preset-active.json");
            if (!Files.isRegularFile(active)) return;
            if (terminalSlot < 0) {
                terminalSlot = mc.thePlayer.inventory.currentItem;
                int empty = (terminalSlot + 1) % 9;
                CenterGraphTcpFixture.require(
                    mc.thePlayer.inventory.getStackInSlot(empty) == null,
                    "Preset recipe view requires an empty selected slot");
                mc.thePlayer.closeScreen();
                mc.thePlayer.inventory.currentItem = empty;
                openCooldown = 0;
                return;
            }
            ModularScreen current = ModularScreen.getCurrent();
            if (mc.currentScreen == null || current == null) {
                open();
                return;
            }
            var manager = current.getSyncManager()
                .getMainPSM();
            var output = manager.getSyncHandlerFromMapKey("itemOutput:0");
            CenterGraphTcpFixture
                .require(output instanceof GenericListSyncHandler, "Native GT recipe item synchronization is missing");
            Map<String, Long> actual = new LinkedHashMap<>();
            for (Object value : ((GenericListSyncHandler<?>) output).getValue()) {
                if (!(value instanceof ItemStack item)) continue;
                actual.merge(TestLabCenterPresetLoad.key(item), (long) item.stackSize, Long::sum);
            }
            JsonObject batch = json(active);
            var elapsed = manager.getSyncHandlerFromMapKey("progressTime:0");
            var duration = manager.getSyncHandlerFromMapKey("maxProgressTime:0");
            CenterGraphTcpFixture.require(
                elapsed instanceof IntSyncValue && duration instanceof IntSyncValue,
                "Native GT progress synchronization is missing");
            int total = ((IntSyncValue) duration).getIntValue();
            int currentTick = ((IntSyncValue) elapsed).getIntValue();
            if (total != batch.get("duration")
                .getAsInt() || currentTick <= 0
                || currentTick >= total
                || !actual.equals(quantities(batch.getAsJsonObject("products")))) return;
            capturePage = "preset-recipe";
            presetStage = 1;
            return;
        }
        if (presetStage == 1) {
            mc.thePlayer.closeScreen();
            CenterGraphTcpFixture.write(receipts, "preset-camera-crown.json", Collections.emptyMap());
            crownStartReplays = MachineVisualClient.motionMeshReplays;
            presetStage = 4;
            return;
        }
        if (presetStage == 4) {
            if (mc.thePlayer.posY < CenterGraphTcpFixture.Y + 61) return;
            TileEntity tile = mc.theWorld
                .getTileEntity(CenterGraphTcpFixture.X, CenterGraphTcpFixture.Y, CenterGraphTcpFixture.Z);
            if (!(tile instanceof IGregTechTileEntity gt)
                || !(gt.getMetaTileEntity() instanceof MTEMineralIntegrationCenter center)) return;
            int safe = MachineVisualClient.safeGroupMask(center),
                uploaded = MachineVisualClient.uploadedGroupMask(center);
            long replays = MachineVisualClient.motionMeshReplays - crownStartReplays;
            if ((safe & uploaded) == 0 || replays <= 0 || !MachineVisualClient.isAnimating(center)) {
                CenterGraphTcpFixture.require(
                    result == null,
                    "Crown never reached active cached rendering: bound=" + safe
                        + " uploaded="
                        + uploaded
                        + " replays="
                        + replays);
                return;
            }
            long pose = MachineVisualClient.decorativeTime(center);
            if (pose < 0) return;
            if (crownFirstPose < 0) {
                crownFirstPose = pose;
                capturePage = "preset-crown-a";
            } else if (pose >= crownFirstPose + 450) {
                capturePage = "preset-crown-b";
                presetStage = 5;
            }
            return;
        }
        if (presetStage == 5) {
            CenterGraphTcpFixture.write(receipts, "preset-camera-home.json", Collections.emptyMap());
            mc.thePlayer.inventory.currentItem = terminalSlot;
            openCooldown = 0;
            presetStage = 2;
            return;
        }
        if (result == null) return;
        if (terminal == null) {
            open();
            return;
        }
        if (presetStage == 2) {
            click(terminal, "page_stats");
            presetStage = 3;
            return;
        }
        Object editor = readField(control(terminal, "tabs"), "editor");
        Object sync = readField(editor, "sync");
        NBTTagList rows = (NBTTagList) readField(sync, "ledger");
        JsonObject preset = result.getAsJsonObject("preset");
        String[] columns = { "ledgerDebits", "ledgerDelivered", "ledgerBonuses" };
        for (int column = 0; column < columns.length; column++) {
            Map<String, Long> actual = new LinkedHashMap<>();
            for (int i = 0; i < rows.tagCount(); i++) {
                NBTTagCompound row = rows.getCompoundTagAt(i);
                long total = row.getLong("C" + column);
                ItemStack item = ItemStack.loadItemStackFromNBT(row.getCompoundTag("Item"));
                if (item != null && total > 0) actual.put(TestLabCenterPresetLoad.key(item), total);
            }
            if (!actual.equals(quantities(preset.getAsJsonObject(columns[column])))) return;
        }
        screenshot = true;
    }

    private static JsonObject json(Path file) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    private static Map<String, Long> quantities(JsonObject object) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> item : object.entrySet()) result.put(
            item.getKey(),
            item.getValue()
                .getAsLong());
        return result;
    }

    private boolean previewCopper(ModularScreen screen, Object editor) throws ReflectiveOperationException {
        IWidget canvas = control(screen, "canvas");
        if (!canvas.areAncestorsEnabled() || readField(canvas, "laidOut") == null) return false;
        if (readField(canvas, "sample") == null) {
            IWidget results = control(screen, "matching");
            if (!results.areAncestorsEnabled()) {
                int x = canvasNumber(canvas, "ruleX", 0) + 10;
                int y = canvasNumber(canvas, "ruleY", 0) + 7;
                clickBoard(screen, canvas, x, y);
            } else if (!((List<?>) readField(results, "matches")).isEmpty()) clickBoardAtPixel(screen, results, 8, 7);
            return false;
        }
        var trace = (com.miaokatze.gtsr.common.machine.orecenter.CenterGraphPreview) readField(canvas, "trace");
        if (trace == null || trace.products(2)
            .isEmpty()) return false;
        if (!verifiedCopper) {
            verifiedCopper = true;
            click(screen, "source");
            return false;
        }
        return (Boolean) readField(editor, "showSources");
    }

    private void verifyGraphLayout(ModularScreen screen) throws ReflectiveOperationException {
        IWidget canvas = control(screen, "canvas");
        var graph = (com.miaokatze.gtsr.common.machine.orecenter.CenterGraph) readField(canvas, "laidOut");
        if (graph == null || graph != readField(readField(canvas, "editor"), "graph")) return;
        switch (layoutStep) {
            case 1 -> {
                if (graph.id <= 102 || !graph.nodes.isEmpty()) return;
                verifyEmptyAnchors(canvas);
                capturePage = "graph-empty";
            }
            case 2 -> {
                int x = (int) readField(canvas, "offX"), y = (int) readField(canvas, "offY");
                double zoom = (double) readField(canvas, "zoom");
                Area area = canvas.getArea();
                mouse(screen, canvas, 12, area.height - 38, true);
                aim(screen, canvas, 39, area.height - 17);
                screen.onMouseDrag(0, 20);
                mouse(screen, canvas, 39, area.height - 17, false);
                click(screen, "zoom_in");
                CenterGraphTcpFixture.require(
                    (int) readField(canvas, "offX") != x && (int) readField(canvas, "offY") != y
                        && (double) readField(canvas, "zoom") > zoom,
                    "Native canvas did not pan and zoom");
                verifyEmptyAnchors(canvas);
                verifiedEmptyBoard = true;
                click(screen, "enabled");
            }
            case 3 -> {
                if (graph.enabled) return;
                click(screen, "undo");
            }
            case 4 -> {
                if (!graph.enabled) return;
                click(screen, "redo");
            }
            case 5 -> {
                if (graph.enabled) return;
                verifiedGraphHistory = true;
                click(screen, "delete");
                click(screen, "delete");
            }
            case 6 -> {
                if (graph.id > 102) return;
                click(screen, "generator_presets");
            }
            case 7 -> {
                var modal = screen.getPanelManager()
                    .getOpenPanel("gtsr_center_presets");
                if (modal == null) return;
                IWidget preset = find(modal, CONTROL + "preset_regular-MPMC");
                IWidget apply = find(modal, CONTROL + "preset_apply");
                if (preset == null || apply == null) return;
                clickBoardAtPixel(screen, preset, preset.getArea().width / 2, 7);
                clickBoardAtPixel(screen, apply, apply.getArea().width / 2, 7);
            }
            case 8 -> {
                if (graph.id <= 102 || graph.nodes.size() < 10) return;
                int[][] nodes = (int[][]) readField(canvas, "nodes");
                var height = canvas.getClass()
                    .getDeclaredMethod(
                        "cardHeight",
                        graph.nodes.get(0)
                            .getClass());
                height.setAccessible(true);
                for (int i = 0; i < nodes.length; i++) {
                    int[] a = nodes[i];
                    int h = (int) height.invoke(null, graph.nodes.get(i));
                    for (int j = i + 1; j < nodes.length; j++) {
                        int[] b = nodes[j];
                        int otherHeight = (int) height.invoke(null, graph.nodes.get(j));
                        CenterGraphTcpFixture.require(
                            Math.abs(a[0] - b[0]) >= 92 || a[1] + h <= b[1] || b[1] + otherHeight <= a[1],
                            "Preset cards overlap: " + i + " and " + j);
                    }
                }
                presetNodes = nodes.length;
                capturePage = "graph-preset";
            }
            case 9 -> {
                tab(screen, 1, 1);
                screenshot = true;
                advance();
            }
            default -> throw new IllegalStateException("Unexpected layout step " + layoutStep);
        }
        layoutStep++;
    }

    private static void verifyEmptyAnchors(IWidget canvas) throws ReflectiveOperationException {
        int x = (int) readField(canvas, "offX"), y = (int) readField(canvas, "offY");
        int ruleX = canvasNumber(canvas, "ruleX", 0), ruleY = canvasNumber(canvas, "ruleY", 0);
        int output = (int) readField(canvas, "outputX");
        var hit = canvas.getClass()
            .getDeclaredMethod("hit", int.class, int.class);
        hit.setAccessible(true);
        CenterGraphTcpFixture.require(
            ruleY == y + 48 && (int) hit.invoke(canvas, ruleX + 8, ruleY + 8) == -5
                && (int) hit.invoke(canvas, x + 8, ruleY + 8) == -3
                && (int) hit.invoke(canvas, x + 130, ruleY + 8) == -4
                && (int) hit.invoke(canvas, x + output + 8, ruleY + 8) == -2,
            "The empty board lost one or more aligned canvas anchors");
    }

    private static int canvasNumber(IWidget canvas, String name, int index) throws ReflectiveOperationException {
        var method = canvas.getClass()
            .getDeclaredMethod(name, int.class);
        method.setAccessible(true);
        return (int) method.invoke(canvas, index);
    }

    private static void clickBoard(ModularScreen screen, IWidget widget, int x, int y)
        throws ReflectiveOperationException {
        double zoom = (double) readField(widget, "zoom");
        clickBoardAtPixel(screen, widget, (int) Math.round(x * zoom), (int) Math.round(y * zoom));
    }

    private static void clickBoardAtPixel(ModularScreen screen, IWidget widget, int x, int y)
        throws ReflectiveOperationException {
        mouse(screen, widget, x, y, true);
        mouse(screen, widget, x, y, false);
    }

    private boolean previewTrial(ModularScreen screen) throws ReflectiveOperationException {
        IWidget button = control(screen, "calc_run");
        if (!button.areAncestorsEnabled()) return false;
        if (trialStep == 0) {
            click(screen, "calc_route");
            trialStep++;
            return false;
        }
        if (trialStep == 1) {
            replace(screen, "calc_batch_size", "16");
            click(screen, "calc_run");
            trialStep++;
            return false;
        }
        IWidget table = find(screen.getMainPanel(), "center_calculation_results");
        if (table == null) throw new IllegalStateException("Missing estimate table");
        Object view = readField(table, "this$0");
        String status = (String) readField(view, "trialStatus");
        if ("terminal.trial_working".equals(status)) return false;
        CenterGraphTcpFixture
            .require("terminal.trial_ready".equals(status), "Copper estimate did not complete: " + status);
        var result = (com.miaokatze.gtsr.common.machine.orecenter.CenterPlanner.Batch) readField(view, "trialBatch");
        CenterGraphTcpFixture.require(
            result.ticks > 0 && result.steam > 0
                && result.consumed.stream()
                    .mapToInt(item -> item.stackSize)
                    .sum() == 16,
            "Wrong copper estimate");
        trialTicks = result.ticks;
        return true;
    }

    private void open() throws ReflectiveOperationException {
        if (capturePage != null) return;
        if (openCooldown-- > 0 || mc.theWorld == null) return;
        int x = CenterGraphTcpFixture.X, y = CenterGraphTcpFixture.Y, z = CenterGraphTcpFixture.Z;
        if (mc.thePlayer.getDistanceSq(x + .5, y + .5, z + .5) > 16) {
            trace(
                "outside controller reach, player=" + (int) mc.thePlayer.posX
                    + ","
                    + (int) mc.thePlayer.posY
                    + ","
                    + (int) mc.thePlayer.posZ);
            return;
        }
        TileEntity tile = mc.theWorld.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity)
            || !(((IGregTechTileEntity) tile).getMetaTileEntity() instanceof MTEMineralIntegrationCenter)) return;
        if (!presetLoad && !fullCenter && phase == 1 && projectedHints == 0) {
            IGregTechTileEntity base = (IGregTechTileEntity) tile;
            MTEMineralIntegrationCenter controller = (MTEMineralIntegrationCenter) base.getMetaTileEntity();
            ForgeDirection facing = base.getFrontFacing();
            controllerOverlay = controller.getTexture(base, facing, facing, 0, false, false).length == 2
                && controller.getTexture(base, facing, facing, 0, true, false).length == 2
                && controller.getTexture(base, facing.getOpposite(), facing, 0, false, false).length == 1;
            CenterGraphTcpFixture.require(controllerOverlay, "The controller front is missing its ore-drill overlay");
            ItemStack projector = new ItemStack(StructureLibAPI.getDefaultHologramItem());
            projector.getItem()
                .onItemUseFirst(
                    projector,
                    mc.thePlayer,
                    mc.theWorld,
                    x,
                    y,
                    z,
                    ForgeDirection.NORTH.ordinal(),
                    .5f,
                    .5f,
                    0f);
            Field groups = Class.forName("com.gtnewhorizon.structurelib.ClientProxy")
                .getDeclaredField("allGroups");
            groups.setAccessible(true);
            List<?> holograms = (List<?>) groups.get(null);
            CenterGraphTcpFixture.require(!holograms.isEmpty(), "Projector right click produced no hologram");
            Object last = holograms.get(holograms.size() - 1);
            projectedHints = ((List<?>) readField(last, "hints")).size();
            CenterGraphTcpFixture.require(projectedHints > 1024, "Center projection contains only a floor fragment");
            capturePage = "projector";
            return;
        }
        mc.playerController.onPlayerRightClick(
            mc.thePlayer,
            mc.theWorld,
            mc.thePlayer.getHeldItem(),
            x,
            y,
            z,
            ForgeDirection.NORTH.ordinal(),
            Vec3.createVectorHelper(x + .5, y + .5, z));
        openCooldown = 20;
    }

    private void verifyGtWindow() throws ReflectiveOperationException {
        if (capturePage != null) return;
        if (gtCaptured) {
            mc.thePlayer.closeScreen();
            mc.thePlayer.inventory.currentItem = terminalSlot;
            openCooldown = 0;
            gtVerified = true;
            return;
        }
        if (terminalSlot < 0) {
            terminalSlot = mc.thePlayer.inventory.currentItem;
            int empty = (terminalSlot + 1) % 9;
            CenterGraphTcpFixture.require(
                mc.thePlayer.inventory.getStackInSlot(empty) == null,
                "GT bare-hand check requires an empty hotbar slot");
            mc.thePlayer.inventory.currentItem = empty;
            openCooldown = 0;
        }
        ModularScreen current = ModularScreen.getCurrent();
        if (mc.currentScreen == null || current == null) {
            open();
            return;
        }
        IWidget row = find(current.getMainPanel(), "center_gt_operation_7");
        CenterGraphTcpFixture.require(row != null && row.isValid(), "Ordinary controller click did not open GT status");
        CenterGraphTcpFixture.require(
            find(current.getMainPanel(), "center_gt_batch_progress") != null,
            "GT terminal lacks the native batch progress texture widget");
        capturePage = "gt-operation";
        gtCaptured = true;
    }

    private ModularScreen screen() {
        if (mc.currentScreen == null) return null;
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen == null) return null;
        IWidget tabs = find(screen.getMainPanel(), CONTROL + "tabs");
        return tabs != null && tabs.isValid() ? screen : null;
    }

    private static IWidget find(IWidget root, String name) {
        if (root.isName(name)) return root;
        for (IWidget child : root.getChildren()) {
            IWidget found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static IWidget control(ModularScreen screen, String name) {
        IWidget widget = find(screen.getMainPanel(), CONTROL + name);
        CenterGraphTcpFixture.require(widget != null && widget.isValid(), "Missing live UI control " + name);
        return widget;
    }

    private static TextFieldWidget field(ModularScreen screen, String name) {
        return (TextFieldWidget) control(screen, name);
    }

    private long displayedRevision(ModularScreen screen) throws ReflectiveOperationException {
        Object editor = readField(control(screen, "tabs"), "editor");
        Object sync = readField(editor, "sync");
        boolean waiting = (Boolean) readField(sync, "waiting");
        Object snapshot = readField(editor, "snapshot");
        long revision = (Long) readField(snapshot, "revision");
        long received = (Long) readField(readField(sync, "snapshot"), "revision");
        trace(
            "editorRevision=" + revision
                + ", receivedRevision="
                + received
                + ", expectedRevision="
                + expectedRevision
                + ", awaitingResult="
                + waiting
                + ", status="
                + readField(sync, "status")
                + ", rules="
                + field(screen, "rules").getText()
                + ", outlet="
                + field(screen, "filter").getText());
        return waiting ? -1 : revision;
    }

    private String currentScreen() {
        return mc.currentScreen == null ? "none"
            : mc.currentScreen.getClass()
                .getName();
    }

    private void trace(String value) {
        String detail = "phase=" + phase + ", " + value;
        if (detail.equals(lastTrace)) return;
        lastTrace = detail;
        GTSteamReborn.LOG.info("[GTSR center graph TCP] {}", detail);
    }

    private static Object readField(Object receiver, String name) throws ReflectiveOperationException {
        Field field = receiver.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static void click(ModularScreen screen, String name) throws ReflectiveOperationException {
        IWidget widget = control(screen, name);
        Area area = widget.getArea();
        mouse(screen, widget, area.width / 2, area.height / 2, true);
        mouse(screen, widget, area.width / 2, area.height / 2, false);
    }

    private static void tab(ModularScreen screen, int start, int end) throws ReflectiveOperationException {
        IWidget widget = control(screen, "tabs");
        int width = 64;
        mouse(screen, widget, start * width + width / 2, 7, true);
        if (start != end) aim(screen, widget, width, 7);
        mouse(screen, widget, end * width + width / 2, 7, false);
    }

    private static void aim(ModularScreen screen, IWidget widget, int x, int y) {
        var transform = LocatedWidget.of(widget)
            .getTransformationMatrix();
        screen.getContext()
            .updateState((int) transform.transformX(x, y), (int) transform.transformY(x, y), 0);
        screen.onFrameUpdate();
    }

    private static void mouse(ModularScreen screen, IWidget widget, int x, int y, boolean pressed)
        throws ReflectiveOperationException {
        aim(screen, widget, x, y);
        Field button = GuiContext.class.getDeclaredField("mouseButton");
        button.setAccessible(true);
        button.setInt(screen.getContext(), 0);
        boolean handled = screen.onMouseInputPre(0, pressed)
            || (pressed ? screen.onMousePressed(0) : screen.onMouseRelease(0));
        if (pressed) CenterGraphTcpFixture.require(handled, "MUI2 rejected mouse press on " + widget.getName());
    }

    private static void replace(ModularScreen screen, String name, String value) throws ReflectiveOperationException {
        TextFieldWidget field = field(screen, name);
        int length = field.getText()
            .length();
        click(screen, name);
        screen.onKeyPressed('\0', Keyboard.KEY_END);
        for (int i = 0; i < length; i++) screen.onKeyPressed('\b', Keyboard.KEY_BACK);
        Class<?> events = Class.forName("me.eigenraven.lwjgl3ify.api.InputEvents");
        Class<?> textEvent = Class.forName(events.getName() + "$TextEvent");
        events.getMethod("injectTextEvent", textEvent)
            .invoke(
                null,
                textEvent.getConstructor(String.class)
                    .newInstance(value));
        CenterGraphTcpFixture.require(
            value.equals(field.getText()),
            "Native text entry differs for " + name
                + " (focused="
                + field.isFocused()
                + ", enabled="
                + field.areAncestorsEnabled()
                + ", actual="
                + field.getText()
                + ")");
    }

    private void finish(String error) {
        done = true;
        Map<String, Object> report = CenterGraphTcpFixture.report(runId, error == null ? "PASS" : "FAIL");
        report.put("phase", phase);
        report.put("error", error);
        report.put("lastTrace", lastTrace);
        report.put("projectedHints", projectedHints);
        report.put("controllerOverlay", controllerOverlay);
        report.put("verifiedCopper", verifiedCopper);
        report.put("verifiedEmptyBoardPanAndZoom", verifiedEmptyBoard);
        report.put("verifiedGraphHistory", verifiedGraphHistory);
        report.put("nonOverlappingPresetCards", presetNodes);
        report.put("trialTicks", trialTicks);
        report.put("assemblySectors", verifiedAssemblyMask);
        report.put("pageFrames", pageFrames);
        if (frameProfile != null) report.put("frameProfile", frameProfile.report());
        report.put("reopenedFrame", error == null ? presetLoad ? "native-preset-stats.png" : FRAME : null);
        CenterGraphTcpFixture.write(receipts, "client-checks.json", report);
        mc.shutdown();
    }
}
