// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ScreenShotHelper;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Browser input is applied in the private JVM through ModularUI2. No messages go to the system desktop. */
@SideOnly(Side.CLIENT)
final class CenterNativeReviewInput {

    private final Minecraft mc;
    private final Path session;
    private long accepted, nextFrame, activeUntil, inputRevision = -1;
    private int frame, button = -1;
    private boolean ready, showing;
    private Field mouseButton, latestX, latestY, modifiers;
    private Method poll, injectText;
    private java.lang.reflect.Constructor<?> textEvent;
    private ByteBuffer reviewKeys;

    CenterNativeReviewInput(Minecraft mc, Path session) {
        this.mc = mc;
        this.session = session;
    }

    private void prepare() throws ReflectiveOperationException {
        Class<?> mouse = Class.forName("org.lwjglx.input.Mouse");
        mouseButton = field(GuiContext.class, "mouseButton");
        latestX = field(mouse, "latestX");
        latestY = field(mouse, "latestY");
        poll = mouse.getMethod("poll");
        Class<?> keyboard = Class.forName("org.lwjglx.input.Keyboard");
        modifiers = field(keyboard, "sdlKeyPressedArray");
        reviewKeys = ByteBuffer.allocateDirect(512);
        Class<?> events = Class.forName("me.eigenraven.lwjgl3ify.api.InputEvents");
        Class<?> type = Class.forName(events.getName() + "$TextEvent");
        textEvent = type.getConstructor(String.class);
        injectText = events.getMethod("injectTextEvent", type);
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    boolean isReady() {
        return ready;
    }

    void initialize(boolean formed) {
        ready = formed;
    }

    private void input(ModularScreen screen) throws Exception {
        Path slot = session.resolve("review-input.json");
        if (!Files.isRegularFile(slot)) return;
        long revision = Files.getLastModifiedTime(slot)
            .toMillis();
        if (revision == inputRevision) return;
        JsonObject batch = new JsonParser().parse(new String(Files.readAllBytes(slot), StandardCharsets.UTF_8))
            .getAsJsonObject();
        long sequence = batch.get("seq")
            .getAsLong();
        if (sequence <= accepted) return;
        inputRevision = revision;
        if (mouseButton == null) prepare();
        for (var event : batch.getAsJsonArray("events")) apply(screen, event.getAsJsonObject());
        accepted = sequence;
        activeUntil = System.nanoTime() + 1_500_000_000L;
        nextFrame = 0;
    }

    private void apply(ModularScreen screen, JsonObject event) throws ReflectiveOperationException {
        if (ModularScreen.getCurrent() != screen) return;
        Object actualKeys = modifiers.get(null);
        int held = event.has("mod") ? event.get("mod")
            .getAsInt() : 0;
        // SDL's left control, shift and alt scancodes. Scope the buffer to this event.
        reviewKeys.put(224, (byte) (held & 1));
        reviewKeys.put(225, (byte) (held & 2));
        reviewKeys.put(226, (byte) (held & 4));
        modifiers.set(null, reviewKeys);
        try {
            String type = event.get("type")
                .getAsString();
            if (event.has("x")) {
                var scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
                int x = event.get("x")
                    .getAsInt(),
                    y = event.get("y")
                        .getAsInt();
                latestX.setInt(null, x);
                latestY.setInt(null, mc.displayHeight - y - 1);
                poll.invoke(null);
                screen.getContext()
                    .updateState(
                        x * scale.getScaledWidth() / mc.displayWidth,
                        y * scale.getScaledHeight() / mc.displayHeight,
                        0);
                screen.onFrameUpdate();
            }
            switch (type) {
                case "move" -> {
                    if (button >= 0) screen.onMouseDrag(button, 0);
                }
                case "down", "up" -> {
                    boolean pressed = type.equals("down");
                    int click = event.get("button")
                        .getAsInt();
                    if (pressed) button = click;
                    else if (button == click) button = -1;
                    mouseButton.setInt(screen.getContext(), click);
                    if (!screen.onMouseInputPre(click, pressed)) {
                        if (pressed) screen.onMousePressed(click);
                        else screen.onMouseRelease(click);
                    }
                }
                case "wheel" -> {
                    int steps = event.get("steps")
                        .getAsInt();
                    screen.onMouseScroll(steps > 0 ? UpOrDown.UP : UpOrDown.DOWN, Math.abs(steps));
                }
                case "key" -> {
                    char typed = event.has("char") ? (char) event.get("char")
                        .getAsInt() : '\0';
                    screen.onKeyPressed(
                        typed,
                        event.get("key")
                            .getAsInt());
                }
                case "text" -> injectText.invoke(
                    null,
                    textEvent.newInstance(
                        event.get("text")
                            .getAsString()));
                case "leave" -> {
                    if (button >= 0) {
                        mouseButton.setInt(screen.getContext(), button);
                        screen.onMouseRelease(button);
                        button = -1;
                    }
                }
                default -> throw new IllegalArgumentException("Unknown review input");
            }
        } finally {
            modifiers.set(null, actualKeys);
        }
    }

    void render(ModularScreen screen) throws Exception {
        if (screen == null) {
            showing = false;
            return;
        }
        if (!ready) return;
        long now = System.nanoTime();
        if (!showing) {
            showing = true;
            activeUntil = now + 1_500_000_000L;
        }
        // By this point the last input has gone through a complete native draw, including tooltip hover.
        boolean hasFrame = now >= nextFrame && now <= activeUntil;
        if (!hasFrame) {
            input(screen);
            return;
        }
        nextFrame = now + 125_000_000L;
        String staged = "review-next.png";
        ScreenShotHelper
            .saveScreenshot(session.toFile(), staged, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        Files.move(
            session.resolve("screenshots")
                .resolve(staged),
            session.resolve("review-frame.png"),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);

        var scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        Map<String, Object> widgets = new LinkedHashMap<>();
        list(screen.getMainPanel(), widgets, scale);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("seq", accepted);
        response.put("frame", ++frame);
        response.put("width", mc.displayWidth);
        response.put("height", mc.displayHeight);
        response.put("widgets", widgets);
        CenterGraphTcpFixture.write(session, "review-state.json", response);
        input(screen);
    }

    private void list(IWidget widget, Map<String, Object> result, ScaledResolution scale) {
        if (!widget.isValid() || !widget.isEnabled()) return;
        String name = widget.getName();
        if (name != null && name.startsWith("center_")) {
            var matrix = LocatedWidget.of(widget)
                .getTransformationMatrix();
            var area = widget.getArea();
            double sx = (double) mc.displayWidth / scale.getScaledWidth();
            double sy = (double) mc.displayHeight / scale.getScaledHeight();
            result.put(
                name,
                new int[] { (int) (matrix.transformX(0, 0) * sx), (int) (matrix.transformY(0, 0) * sy),
                    (int) (area.width * sx), (int) (area.height * sy) });
        }
        for (IWidget child : widget.getChildren()) list(child, result, scale);
    }
}
