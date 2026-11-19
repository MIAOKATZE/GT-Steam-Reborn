// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.item.ItemStack;
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
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.structurelib.StructureLibAPI;
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
    private int phase, openCooldown;
    private boolean screenshot, done;
    private int projectedHints, previewStep;
    private boolean controllerOverlay, reopenedRoutes, openedRules, verifiedCopper;
    private int trialStep, trialTicks;
    private int terminalSlot = -1;
    private boolean gtCaptured, gtVerified;
    private String capturePage;
    private final List<String> pageFrames = new ArrayList<>();
    private String lastTrace = "";

    static void register() {
        if (System.getProperty(CenterGraphTcpFixture.PREFIX + "port") != null) {
            FMLCommonHandler.instance().bus().register(new CenterGraphTcpClient());
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        try {
            if (Files.exists(receipts.resolve("stop.json"))) {
                finish("Cancelled by host");
                return;
            }
            if (phase == 0) {
                if (mc.currentScreen == null) return;
                FMLClientHandler forge = FMLClientHandler.instance();
                forge.setupServerList();
                ServerData endpoint = new ServerData("GTSR TestLab", "127.0.0.1:" + port);
                forge.connectToServer(new GuiMainMenu(), endpoint);
                CenterGraphTcpFixture.require(mc.func_147104_D() == endpoint, "Forge did not select the owned endpoint");
                advance();
                return;
            }
            CenterGraphTcpFixture.require(System.nanoTime() - since < TimeUnit.SECONDS.toNanos(120), "Client phase timed out: " + phase);
            if (mc.currentScreen instanceof GuiDisconnected) {
                IChatComponent reason = (IChatComponent) readField(mc.currentScreen, "field_146304_f");
                throw new IllegalStateException("Dedicated TCP connection ended: " + reason.getUnformattedText());
            }
            if (mc.thePlayer == null) {
                trace("waiting for TCP player, screen=" + currentScreen());
                return;
            }

            ModularScreen screen = screen();
            if (phase == 1 && projectedHints != 0 && !gtVerified) {
                verifyGtWindow();
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
                        for (int i = 0; i < 4; i++)
                            CenterGraphTcpFixture.require(net.minecraftforge.fluids.FluidRegistry.getFluid(
                                costs.getCompoundTagAt(i).getString("FluidName")) != null,
                                "Missing synchronized additive fluid " + i);
                        capturePage = PAGES[0];
                    }
                    else if (previewStep == 1) click(screen, "page_routes");
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
                        var presetPanel = screen.getPanelManager().getOpenPanel("gtsr_center_presets");
                        if (presetPanel == null) return;
                        CenterGraphTcpFixture.require(find(presetPanel, CONTROL + "preset_regular-MPMC") != null
                            && find(presetPanel, CONTROL + "preset_special-dehydrator") != null,
                            "Generator lost its regular or expression-only presets");
                        capturePage = "presets";
                    } else screen.getPanelManager().closeTopPanel();
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
                CenterGraphTcpFixture.require("rawOreCopper".equals(field(screen, "rules").getText()),
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
                    CenterGraphTcpFixture.require(CenterGraphTcpFixture.ORE_RULE.equals(field(screen, "rules").getText())
                        && CenterGraphTcpFixture.FILTER_RULE.equals(field(screen, "filter").getText()),
                        "Server's reopened graph did not display both persisted expressions");
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
        if (event.phase != TickEvent.Phase.END || (!screenshot && capturePage == null) || done) return;
        try {
            String frame = screenshot ? FRAME : "native-" + capturePage + ".png";
            ScreenShotHelper.saveScreenshot(receipts.toFile(), frame, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            // The vanilla screenshot helper always creates its screenshots/ subdirectory.
            Path captured = receipts.resolve("screenshots").resolve(frame);
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

    private boolean previewCopper(ModularScreen screen, Object editor) throws ReflectiveOperationException {
        IWidget canvas = control(screen, "canvas");
        if (!canvas.areAncestorsEnabled()) return false;
        if (readField(canvas, "sample") == null) {
            Area area = canvas.getArea();
            int x = Math.max(1, Math.min(100, Math.max(27, area.width / 7)) - 78) + 22;
            int y = area.height / 2 - 5;
            mouse(screen, canvas, x, y, true);
            mouse(screen, canvas, x, y, false);
            return false;
        }
        var trace = (com.miaokatze.gtsr.common.machine.orecenter.CenterGraphPreview) readField(canvas, "trace");
        if (trace == null || trace.products(2).isEmpty()) return false;
        if (!verifiedCopper) {
            verifiedCopper = true;
            click(screen, "source");
            return false;
        }
        return (Boolean) readField(editor, "showSources");
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
        CenterGraphTcpFixture.require("terminal.trial_ready".equals(status), "Copper estimate did not complete: " + status);
        var result = (com.miaokatze.gtsr.common.machine.orecenter.CenterPlanner.Batch) readField(view, "trialBatch");
        CenterGraphTcpFixture.require(result.ticks > 0 && result.steam > 0
            && result.consumed.stream().mapToInt(item -> item.stackSize).sum() == 16, "Wrong copper estimate");
        trialTicks = result.ticks;
        return true;
    }

    private void open() throws ReflectiveOperationException {
        if (capturePage != null) return;
        if (openCooldown-- > 0 || mc.theWorld == null) return;
        int x = CenterGraphTcpFixture.X, y = CenterGraphTcpFixture.Y, z = CenterGraphTcpFixture.Z;
        if (mc.thePlayer.getDistanceSq(x + .5, y + .5, z + .5) > 16) return;
        TileEntity tile = mc.theWorld.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity) || !(((IGregTechTileEntity) tile).getMetaTileEntity() instanceof MTEMineralIntegrationCenter)) return;
        if (phase == 1 && projectedHints == 0) {
            IGregTechTileEntity base = (IGregTechTileEntity) tile;
            MTEMineralIntegrationCenter controller = (MTEMineralIntegrationCenter) base.getMetaTileEntity();
            ForgeDirection facing = base.getFrontFacing();
            controllerOverlay = controller.getTexture(base, facing, facing, 0, false, false).length == 2
                && controller.getTexture(base, facing, facing, 0, true, false).length == 2
                && controller.getTexture(base, facing.getOpposite(), facing, 0, false, false).length == 1;
            CenterGraphTcpFixture.require(controllerOverlay, "The controller front is missing its ore-drill overlay");
            ItemStack projector = new ItemStack(StructureLibAPI.getDefaultHologramItem());
            projector.getItem().onItemUseFirst(projector, mc.thePlayer, mc.theWorld, x, y, z,
                ForgeDirection.NORTH.ordinal(), .5f, .5f, 0f);
            Field groups = Class.forName("com.gtnewhorizon.structurelib.ClientProxy").getDeclaredField("allGroups");
            groups.setAccessible(true);
            List<?> holograms = (List<?>) groups.get(null);
            CenterGraphTcpFixture.require(!holograms.isEmpty(), "Projector right click produced no hologram");
            Object last = holograms.get(holograms.size() - 1);
            projectedHints = ((List<?>) readField(last, "hints")).size();
            CenterGraphTcpFixture.require(projectedHints > 1024, "Center projection contains only a floor fragment");
            capturePage = "projector";
            return;
        }
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x, y, z,
            ForgeDirection.NORTH.ordinal(), Vec3.createVectorHelper(x + .5, y + .5, z));
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
            CenterGraphTcpFixture.require(mc.thePlayer.inventory.getStackInSlot(empty) == null,
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
        trace("editorRevision=" + revision + ", receivedRevision=" + received + ", expectedRevision="
            + expectedRevision + ", awaitingResult=" + waiting + ", status=" + readField(sync, "status") + ", rules="
            + field(screen, "rules").getText() + ", outlet=" + field(screen, "filter").getText());
        return waiting ? -1 : revision;
    }

    private String currentScreen() {
        return mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName();
    }

    private void trace(String value) {
        String detail = "phase=" + phase + ", " + value;
        if (detail.equals(lastTrace)) return;
        lastTrace = detail;
        GTSteamReborn.LOG.info("[GTSR center graph TCP] {}", detail);
    }

    private static Object readField(Object receiver, String name) throws ReflectiveOperationException {
        Field field = receiver.getClass().getDeclaredField(name);
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
        var transform = LocatedWidget.of(widget).getTransformationMatrix();
        screen.getContext().updateState((int) transform.transformX(x, y), (int) transform.transformY(x, y), 0);
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
        int length = field.getText().length();
        click(screen, name);
        screen.onKeyPressed('\0', Keyboard.KEY_END);
        for (int i = 0; i < length; i++) screen.onKeyPressed('\b', Keyboard.KEY_BACK);
        Class<?> events = Class.forName("me.eigenraven.lwjgl3ify.api.InputEvents");
        Class<?> textEvent = Class.forName(events.getName() + "$TextEvent");
        events.getMethod("injectTextEvent", textEvent).invoke(null, textEvent.getConstructor(String.class).newInstance(value));
        CenterGraphTcpFixture.require(value.equals(field.getText()), "Native text entry differs for " + name
            + " (focused=" + field.isFocused() + ", enabled=" + field.areAncestorsEnabled() + ", actual=" + field.getText() + ")");
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
        report.put("trialTicks", trialTicks);
        report.put("pageFrames", pageFrames);
        report.put("reopenedFrame", error == null ? FRAME : null);
        CenterGraphTcpFixture.write(receipts, "client-checks.json", report);
        mc.shutdown();
    }
}
