// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ScreenShotHelper;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.machinevisual.MachineVisualClient;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Timed views use real RenderTick events. Screenshot readback is outside the sample windows. */
@SideOnly(Side.CLIENT)
final class CenterFrameProfile {

    private final Minecraft mc;
    private final Path receipts;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final long[] intervals = new long[8192], cpu = new long[8192];
    private int phase, samples, rendered, activeFrames;
    private long phaseAt, previousStart, frameStart, meshBuilds, meshReplays, submissions, bytes, requestedWork;
    private long dirtyRanges;
    private String name, capture;
    private MTEMineralIntegrationCenter center;

    CenterFrameProfile(Minecraft mc, Path receipts) {
        this.mc = mc;
        this.receipts = receipts;
    }

    Map<String, Object> report() {
        return report;
    }

    boolean tick() {
        if (capture != null) return false;
        long now = System.nanoTime();
        if (phase == 0) {
            if (!Files.isRegularFile(receipts.resolve("frame-profile-ready.json"))
                || mc.thePlayer.posY < CenterGraphTcpFixture.Y + 86) return false;
            mc.gameSettings.enableVsync = false;
            mc.gameSettings.limitFramerate = 260;
            report.put("viewport", Arrays.asList(mc.displayWidth, mc.displayHeight));
            report.put("renderDistanceChunks", mc.gameSettings.renderDistanceChunks);
            report.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            phaseAt = now;
            phase = 1;
        } else if (phase == 1 && seconds(now, phaseAt, 3)) {
            begin("lookAway");
            phase = 2;
        } else if (phase == 2 && end(now, 4)) {
            begin("firstOverview");
            mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 0;
            phase = 3;
        } else if (phase == 3 && end(now, 3)) {
            begin("overviewIdle");
            phase = 4;
        } else if (phase == 4 && end(now, 4)) {
            capture = "overview";
            phase = 5;
        } else if (phase == 5) {
            signal("preset-camera-crown.json");
            phase = 6;
        } else if (phase == 6 && mc.thePlayer.posY < CenterGraphTcpFixture.Y + 70) {
            TileEntity tile = mc.theWorld
                .getTileEntity(CenterGraphTcpFixture.X, CenterGraphTcpFixture.Y, CenterGraphTcpFixture.Z);
            if (!(tile instanceof IGregTechTileEntity gt)
                || !(gt.getMetaTileEntity() instanceof MTEMineralIntegrationCenter machine)) return false;
            center = machine;
            phaseAt = now;
            phase = 7;
        } else if (phase == 7 && seconds(now, phaseAt, 3)) {
            begin("crownIdle");
            phase = 8;
        } else if (phase == 8 && end(now, 4)) {
            capture = "idle";
            phase = 9;
        } else if (phase == 9) {
            requestedWork = now;
            begin("firstAnimation");
            signal("frame-profile-start.json");
            phase = 10;
        } else if (phase == 10 && activeFrames != 0 && end(now, 4)) {
            begin("crownActive");
            phase = 11;
        } else if (phase == 11 && end(now, 8)) {
            capture = "active";
            phase = 12;
        } else if (phase == 12) {
            signal("frame-profile-finish.json");
            phase = 13;
        }
        return phase == 13;
    }

    private void signal(String file) {
        CenterGraphTcpFixture.write(receipts, file, Collections.emptyMap());
    }

    void renderStart() {
        if (name == null) return;
        frameStart = System.nanoTime();
        if (previousStart != 0 && samples < intervals.length) intervals[samples++] = frameStart - previousStart;
        previousStart = frameStart;
    }

    void renderEnd() throws Exception {
        if (name != null && rendered < cpu.length && frameStart != 0) {
            long now = System.nanoTime();
            cpu[rendered++] = now - frameStart;
            frameStart = 0;
            if (center != null && MachineVisualClient.isAnimating(center)
                && (MachineVisualClient.safeGroupMask(center) & MachineVisualClient.uploadedGroupMask(center)) != 0) {
                activeFrames++;
                if (phase == 10 && !report.containsKey("firstAnimatedFrameMs")
                    && MachineVisualClient.motionMeshReplays > meshReplays) {
                    report.put("firstAnimatedFrameMs", (now - requestedWork) / 1_000_000.0);
                }
            }
        }
        if (capture == null) return;
        String file = "native-frame-" + capture + ".png";
        ScreenShotHelper
            .saveScreenshot(receipts.toFile(), file, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        Files.move(
            receipts.resolve("screenshots")
                .resolve(file),
            receipts.resolve(file));
        capture = null;
    }

    private void begin(String window) {
        name = window;
        samples = rendered = activeFrames = 0;
        previousStart = frameStart = 0;
        phaseAt = System.nanoTime();
        meshBuilds = MachineVisualClient.motionMeshCompiles;
        meshReplays = MachineVisualClient.motionMeshReplays;
        submissions = MachineVisualClient.motionSubmissions;
        bytes = MachineVisualClient.motionSubmittedBytes;
        dirtyRanges = MachineVisualVisibility.motionDirtyRanges;
    }

    private boolean end(long now, int duration) {
        if (!seconds(now, phaseAt, duration)) return false;
        CenterGraphTcpFixture.require(samples >= 20, "Too few native frame intervals for " + name);
        boolean active = "crownActive".equals(name);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("frames", rendered);
        data.put("activeFrames", activeFrames);
        data.put(
            "fps",
            samples * 1.0e9
                / Arrays.stream(intervals, 0, samples)
                    .sum());
        data.put("intervalMs", distribution(intervals, samples));
        data.put("renderEventMs", distribution(cpu, rendered));
        data.put("meshCompiles", MachineVisualClient.motionMeshCompiles - meshBuilds);
        data.put("meshReplays", MachineVisualClient.motionMeshReplays - meshReplays);
        data.put("meshSubmissions", MachineVisualClient.motionSubmissions - submissions);
        data.put("meshSubmittedBytes", MachineVisualClient.motionSubmittedBytes - bytes);
        data.put("renderInvalidations", MachineVisualVisibility.motionDirtyRanges - dirtyRanges);
        report.put(name, data);
        name = null;
        if (active) CenterGraphTcpFixture.require(
            activeFrames >= rendered * .80 && MachineVisualClient.motionMeshReplays > meshReplays,
            "Active sample did not mostly render the native cached crown: " + activeFrames + "/" + rendered);
        return true;
    }

    private static boolean seconds(long now, long start, int seconds) {
        return now - start >= TimeUnit.SECONDS.toNanos(seconds);
    }

    private static Map<String, Double> distribution(long[] data, int size) {
        Arrays.sort(data, 0, size);
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("p50", data[(size - 1) / 2] / 1_000_000.0);
        result.put("p95", data[(int) Math.ceil(size * .95) - 1] / 1_000_000.0);
        result.put("p99", data[(int) Math.ceil(size * .99) - 1] / 1_000_000.0);
        result.put("max", data[size - 1] / 1_000_000.0);
        return result;
    }
}
