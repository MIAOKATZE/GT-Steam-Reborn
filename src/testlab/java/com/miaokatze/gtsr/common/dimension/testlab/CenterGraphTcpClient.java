// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
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

    private final String runId = System.getProperty(CenterGraphTcpFixture.PREFIX + "run");
    private final int port = Integer.getInteger(CenterGraphTcpFixture.PREFIX + "port");
    private final Path receipts = Paths.get(System.getProperty(CenterGraphTcpFixture.PREFIX + "receipts"));
    private final Minecraft mc = Minecraft.getMinecraft();
    private long since = System.nanoTime(), expectedRevision;
    private int phase, openCooldown;
    private boolean screenshot, done;
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
            if (phase == 1 || phase == 7) {
                if (screen == null) {
                    trace("waiting for graph panel, screen=" + currentScreen());
                    open();
                    return;
                }
                long revision = displayedRevision(screen);
                if (revision < expectedRevision) return;
                CenterGraphTcpFixture.require("rawOreCopper".equals(field(screen, "rules").getText()),
                    "The graph snapshot arrived but the selected copper route was not displayed: " + lastTrace);
                if (phase == 1) {
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
        if (event.phase != TickEvent.Phase.END || !screenshot || done) return;
        try {
            ScreenShotHelper.saveScreenshot(receipts.toFile(), FRAME, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            // The vanilla screenshot helper always creates its screenshots/ subdirectory.
            Path captured = receipts.resolve("screenshots").resolve(FRAME);
            Files.move(captured, receipts.resolve(FRAME));
            finish(null);
        } catch (Exception failure) {
            finish(failure.toString());
        }
    }

    private void advance() {
        phase++;
        since = System.nanoTime();
    }

    private void open() {
        if (openCooldown-- > 0 || mc.theWorld == null) return;
        int x = CenterGraphTcpFixture.X, y = CenterGraphTcpFixture.Y, z = CenterGraphTcpFixture.Z;
        if (mc.thePlayer.getDistanceSq(x + .5, y + .5, z + .5) > 16) return;
        TileEntity tile = mc.theWorld.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity) || !(((IGregTechTileEntity) tile).getMetaTileEntity() instanceof MTEMineralIntegrationCenter)) return;
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x, y, z,
            ForgeDirection.NORTH.ordinal(), Vec3.createVectorHelper(x + .5, y + .5, z));
        openCooldown = 20;
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
        int width = widget.getArea().width / 9;
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
        CenterGraphTcpFixture.require(value.equals(field.getText()), "Native text entry differs for " + name);
    }

    private void finish(String error) {
        done = true;
        Map<String, Object> report = CenterGraphTcpFixture.report(runId, error == null ? "PASS" : "FAIL");
        report.put("phase", phase);
        report.put("error", error);
        report.put("lastTrace", lastTrace);
        report.put("reopenedFrame", error == null ? FRAME : null);
        CenterGraphTcpFixture.write(receipts, "client-checks.json", report);
        mc.shutdown();
    }
}
