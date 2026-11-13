// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Explicit owned-client-only profile. Runtime reflection follows the existing SRG QA helper contract. */
public class MegaTurbineProfileFixture {

    public static final String SCRIPT = "gtsr-issue155-mega-turbine-profile-v1";
    public static final String PROXIMITY_SCRIPT = "gtsr-mega-turbine-proximity-v1";
    private final String scriptIdentity;
    private static final String LAB = "com.miaokatze.gtsr.common.dimension.testlab.";
    private static final String VIS = "com.miaokatze.gtsr.client.machinevisual.MachineVisualClient";
    private static final String COMMAND = "/sparkc profiler start --timeout 30 --interval 4 --force-java-sampler --save-to-file";

    public interface Hooks {

        void complete(Map<String, Object> receipt, Throwable failure);

        void screenshot(String name);
    }

    private final Hooks hooks;
    private final Path save, output, config;
    private final String runId;
    private final JsonArray stages;
    private final JsonObject expectedArtifacts;
    private final Map<String, Object> receipt = new LinkedHashMap<String, Object>();
    private final List<Map<String, Object>> segments = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> ports = new ArrayList<Map<String, Object>>();
    private volatile Object world, actor, controller, machine, input, dynamo, outlet;
    private Object structure, originalQuality, nativeTask;
    private int controllerX, controllerY, controllerZ;
    private double cameraX, cameraY, cameraZ;
    private final double cameraDistance;
    private final float cameraPitch;
    private volatile boolean tickSampling;
    private long serverTickStarted;
    private final List<Long> serverTickDurations = Collections.synchronizedList(new ArrayList<Long>());
    private final List<Object> tickets = new ArrayList<Object>();
    private boolean renderConfigured;
    private int originalDistance, originalFrameCap, originalServerDistance = -1;
    private float originalFov;
    private boolean originalVsync;
    private volatile Map<String, Object> serverState = Collections.emptyMap();
    private volatile Throwable failure;
    private volatile boolean cancel, cleaned, ready, wantRunning, stageApplied;
    private boolean opened, returned, finished, sampling, sparkStarted;
    private int serverPhase, serverTicks, clientTicks, stage, settle, cameraWaitTicks, cameraStableTicks;
    private long started = System.nanoTime(), stageStarted, frames, frameStart, lastFrameStart, frameNanos, intervals;
    private volatile long generatedEU;
    private long initialSegmentEU, maxFrameNanos, stagePrepared;
    private double oldX, oldY, oldZ;
    private float oldYaw, oldPitch;
    private int oldDimension;
    private Set<Path> previousProfiles;
    private Map<String, Object> segment;
    private final List<Long> frameDurations = new ArrayList<Long>();
    private final List<Long> frameIntervals = new ArrayList<Long>();

    public MegaTurbineProfileFixture(Path plan, Hooks hooks) throws Exception {
        this.hooks = hooks;
        JsonObject document = new JsonParser().parse(new String(Files.readAllBytes(plan), StandardCharsets.UTF_8))
            .getAsJsonObject();
        scriptIdentity = document.get("scriptOnly")
            .getAsString();
        require(
            SCRIPT.equals(scriptIdentity) || PROXIMITY_SCRIPT.equals(scriptIdentity),
            "Unexpected explicit scriptOnly identity");
        require(
            "mega_steam_turbine_array".equals(
                document.get("nativeCase")
                    .getAsString()),
            "Only the existing native mega_steam_turbine_array case is authorized");
        stages = document.getAsJsonArray("stages");
        expectedArtifacts = document.getAsJsonObject("expectedArtifacts");
        require(
            expectedArtifacts.has("spark") && expectedArtifacts.get("spark")
                .getAsString()
                .equals("36c0bc7c5665dbd2af384f3609e06842d3c14a52b5dfaf7632288f8f0a57314f"),
            "Frozen Spark artifact required");
        JsonObject render = document.getAsJsonObject("render");
        if (PROXIMITY_SCRIPT.equals(scriptIdentity)) {
            cameraDistance = document.getAsJsonObject("camera")
                .get("horizontalDistance")
                .getAsDouble();
            cameraPitch = document.getAsJsonObject("camera")
                .get("pitch")
                .getAsFloat();
            require(cameraDistance == 16d && cameraPitch == -35f, "Explicit fixed near camera required");
        } else {
            cameraDistance = 48d;
            cameraPitch = -12f;
        }

        require(
            render.get("distance")
                .getAsInt() == 8
                && render.get("heapMiB")
                    .getAsInt() == 4096
                && render.get("fov")
                    .getAsFloat() == 70f
                && render.get("frameCap")
                    .getAsInt() == 260
                && !render.get("vsync")
                    .getAsBoolean(),
            "Frozen RD8/4GiB/FOV70/uncapped260/vsync-off settings required");
        String[] names = { "stopped-standard", "running-standard", "running-off" };
        String[] quality = { "STANDARD", "STANDARD", "OFF" };
        boolean[] running = { false, true, true };
        int[] yaw = { 0, 0, 0 };
        require(stages.size() == names.length, "Exactly three bounded native-case profile segments required");
        for (int i = 0; i < names.length; i++) {
            JsonObject spec = stages.get(i)
                .getAsJsonObject();
            require(
                names[i].equals(
                    spec.get("name")
                        .getAsString())
                    && quality[i].equals(
                        spec.get("quality")
                            .getAsString())
                    && running[i] == spec.get("running")
                        .getAsBoolean()
                    && yaw[i] == spec.get("yaw")
                        .getAsInt(),
                "Unexpected segment sequence or settings");
        }
        runId = System.getProperty("miaotest.host.runId");
        UUID.fromString(runId);
        save = Paths.get(System.getProperty("miaotest.host.worldPath"))
            .toAbsolutePath()
            .normalize();
        require(
            save.getFileName()
                .toString()
                .equals("MiaoHost-" + runId),
            "Exact run-owned world name required");
        JsonObject owner = new JsonParser()
            .parse(new String(Files.readAllBytes(save.resolve(".miaotest-host-owner.json")), StandardCharsets.UTF_8))
            .getAsJsonObject();
        require(
            runId.equals(
                owner.get("runId")
                    .getAsString())
                && save.getParent()
                    .toString()
                    .equals(
                        owner.get("parent")
                            .getAsString()),
            "Owned world marker mismatch");
        output = Paths.get(System.getProperty("gtsr.uiqa.receipt"))
            .getParent();
        config = save.getParent()
            .getParent()
            .resolve("config");
        receipt.put("scriptOnly", scriptIdentity);
        receipt.put("runId", runId);
        receipt.put("planSHA256", sha(plan));
        receipt.put("javaVersion", System.getProperty("java.version"));
        receipt.put("javaHome", System.getProperty("java.home"));
        receipt.put(
            "maxHeapBytes",
            Runtime.getRuntime()
                .maxMemory());
        receipt.put("sparkCommand", COMMAND);
        receipt.put("segments", segments);
        receipt.put("nativePorts", ports);
        receipt.put("nativeCase", "mega_steam_turbine_array");
        receipt.put(
            "mutationScope",
            "Exact fresh run-owned save; existing native mega_steam_turbine_array case and catalog station; no separate structure or texture changes");
        receipt.put(
            "measurementScope",
            PROXIMITY_SCRIPT.equals(scriptIdentity)
                ? "Client RenderTick START-to-END and start-to-start wall time; server ServerTick START-to-END on its own thread; Spark client Java sampling. Includes background mods; not exclusive GPU or machine CPU timing"
                : "Natural render frame wall time and sparkc Java sampler; integrated server has its own thread; not exclusive GPU timing");
        receipt.put("cleanupPending", true);
    }

    public void cancel(Throwable cause) {
        if (failure == null) failure = cause;
        cancel = true;
    }

    public boolean completeForExit() {
        return finished;
    }

    public void serverTick(Object server) {
        if (cleaned) return;
        try {
            if (cancel) {
                cleanup(server);
                return;
            }
            if (++serverTicks > 14000) throw new IllegalStateException("Bounded server fixture tick limit");
            Object runtime = field(type(LAB + "TestLabRuntime"), "INSTANCE");
            if (serverPhase != 1)
                require(!Boolean.TRUE.equals(call(runtime, "busy")), "Another testlab task is active");
            if (serverPhase == 0) {
                List<?> players = (List<?>) field(
                    call(server, "getConfigurationManager|func_71203_ab"),
                    "playerEntityList|field_72404_b");
                if (players.isEmpty()) return;
                require(players.size() == 1, "Only the owned offline actor may be present");
                actor = players.get(0);
                require(
                    actor.getClass()
                        .getName()
                        .equals("net.minecraft.entity.player.EntityPlayerMP"),
                    "Connected native actor required");
                require(
                    !Boolean.TRUE
                        .equals(field(field(actor, "capabilities|field_71075_bZ"), "isCreativeMode|field_75098_d")),
                    "Survival actor required");
                Object overworld = call(server, "worldServerForDimension|func_71218_a", 0);
                Path actual = ((java.io.File) call(
                    call(overworld, "getSaveHandler|func_72860_G"),
                    "getWorldDirectory|func_75765_b")).toPath()
                        .toAbsolutePath()
                        .normalize();
                require(actual.equals(save), "Refuse non-owned save");
                Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
                Object mods = call(call(type("cpw.mods.fml.common.Loader"), "instance"), "getIndexedModList");
                for (String modId : Arrays.asList("gtsr", "miaokatzecore", "spark")) {
                    Object container = call(mods, "get", modId);
                    require(container != null, "Required loaded mod missing: " + modId);
                    Path source = ((java.io.File) call(container, "getSource")).toPath();
                    String actualHash = sha(source);
                    Map<String, Object> artifact = new LinkedHashMap<String, Object>();
                    artifact.put(
                        "source",
                        source.toAbsolutePath()
                            .toString());
                    artifact.put("sha256", actualHash);
                    artifact.put("version", call(container, "getVersion"));
                    artifacts.put(modId, artifact);
                    if (expectedArtifacts.has(modId)) require(
                        actualHash.equals(
                            expectedArtifacts.get(modId)
                                .getAsString()),
                        "Loaded artifact differs from the frozen baseline: " + modId);
                }
                receipt.put("artifacts", artifacts);
                world = call(type(LAB + "TestLabDimension"), "world");
                require(
                    Boolean.TRUE
                        .equals(call(type("com.miaokatze.miaokatzecore.testlab.LabWorldPolicy"), "owns", world)),
                    "Core must own this precise test world");
                require(
                    ((Number) call(type(LAB + "TestLabSupply"), "feedCount", world)).intValue() == 0,
                    "Existing supply is not adopted");
                oldDimension = ((Number) field(actor, "dimension|field_71093_bK")).intValue();
                oldX = num(field(actor, "posX|field_70165_t"));
                oldY = num(field(actor, "posY|field_70163_u"));
                oldZ = num(field(actor, "posZ|field_70161_v"));
                oldYaw = (float) num(field(actor, "rotationYaw|field_70177_z"));
                oldPitch = (float) num(field(actor, "rotationPitch|field_70125_A"));
                require(
                    !Boolean.TRUE.equals(call(actor, "canCommandSenderUseCommand|func_70003_b", 2, "gtsrtest")),
                    "Business actor must remain non-OP; server console controls the native case");
                require(
                    Boolean.TRUE.equals(call(server, "canCommandSenderUseCommand|func_70003_b", 2, "gtsrtest")),
                    "Independent server console must hold OP2 control");
                call(
                    call(server, "getCommandManager|func_71187_D"),
                    "executeCommand|func_71556_a",
                    server,
                    "gtsrtest run mega_steam_turbine_array");
                serverPhase = 1;
                receipt.put("control", "OP2 SERVER_CONSOLE; connected survival actor is non-OP");
                return;
            } else if (serverPhase == 1) {
                Object active = field(runtime, "active");
                if (active != null && "mega_steam_turbine_array".equals(call(active, "id"))) nativeTask = active;
                if (Boolean.TRUE.equals(call(runtime, "busy"))) return;
                Map<?, ?> snapshot = (Map<?, ?>) call(runtime, "snapshot");
                if (nativeTask == null)
                    throw new IllegalStateException("Native case did not expose its selected task: " + snapshot);
                require(
                    "PASS".equals(snapshot.get("outcome")) && Boolean.TRUE.equals(snapshot.get("reportWritten"))
                        && !Boolean.TRUE.equals(snapshot.get("cleanupPending")),
                    "Native turbine case failed: " + snapshot);
                Path report = Paths.get(String.valueOf(snapshot.get("reportPath")));
                Files.copy(
                    report,
                    output.resolve("native-mega-steam-turbine-report.json"),
                    StandardCopyOption.REPLACE_EXISTING);
                receipt.put("nativeCaseSnapshot", snapshot);
                receipt.put("nativeCaseReportSHA256", sha(report));
                controller = field(nativeTask, "controller");
                machine = call(controller, "getMetaTileEntity");
                structure = field(nativeTask, "structure");
                controllerX = ((Number) call(controller, "getXCoord")).intValue();
                controllerY = ((Number) call(controller, "getYCoord")).intValue();
                controllerZ = ((Number) call(controller, "getZCoord")).intValue();
                cameraX = controllerX + .5d;
                cameraY = 65d;
                cameraZ = controllerZ + .5d - cameraDistance;
                for (Object port : (List<?>) field(nativeTask, "ports")) {
                    Object meta = call(port, "getMetaTileEntity");
                    if (type("gregtech.api.metatileentity.implementations.MTEHatchDynamo").isInstance(meta))
                        dynamo = port;
                    else if (type("gregtech.api.metatileentity.implementations.MTEHatchOutput").isInstance(meta))
                        outlet = port;
                    else if (type("gregtech.api.metatileentity.implementations.MTEHatchInput").isInstance(meta))
                        input = port;
                    Map<String, Object> entry = new LinkedHashMap<String, Object>();
                    entry.put("actualMetaId", call(port, "getMetaTileID"));
                    entry.put(
                        "position",
                        Arrays.asList(call(port, "getXCoord"), call(port, "getYCoord"), call(port, "getZCoord")));
                    entry.put(
                        "nativeClass",
                        meta.getClass()
                            .getName());
                    ports.add(entry);
                }
                require(input != null && dynamo != null && outlet != null, "Native case lacks required existing ports");
                require(
                    Boolean.TRUE.equals(call(type(LAB + "TestLabStructure"), "checkStructure", machine, controller)),
                    "Existing native case no longer forms");
                Set<String> chunks = new LinkedHashSet<String>();
                for (Object cell : (List<?>) field(structure, "cells")) {
                    int x = ((Number) field(cell, "x")).intValue(), z = ((Number) field(cell, "z")).intValue();
                    require(
                        Math.abs((x >> 4) - (((int) cameraX) >> 4)) <= 7
                            && Math.abs((z >> 4) - (((int) cameraZ) >> 4)) <= 7,
                        "Native station exceeds RD8 camera coverage");
                    chunks.add((x >> 4) + "," + (z >> 4));
                }
                int cameraChunkX = ((int) cameraX) >> 4, cameraChunkZ = ((int) cameraZ) >> 4;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) chunks.add((cameraChunkX + dx) + "," + (cameraChunkZ + dz));
                require(chunks.size() <= 64, "Native fixture and camera support exceed bounded retention");
                Object mod = call(
                    call(call(type("cpw.mods.fml.common.Loader"), "instance"), "getIndexedModList"),
                    "get",
                    "gtsr");
                Object normal = Enum
                    .valueOf((Class) type("net.minecraftforge.common.ForgeChunkManager$Type"), "NORMAL");
                Object ticket = null;
                int remaining = 0;
                for (String chunk : chunks) {
                    if (remaining == 0) {
                        require(tickets.size() < 4, "Maximum four owned tickets");
                        ticket = call(
                            type("net.minecraftforge.common.ForgeChunkManager"),
                            "requestTicket",
                            call(mod, "getMod"),
                            world,
                            normal);
                        require(ticket != null, "No owned Forge retention ticket");
                        tickets.add(ticket);
                        remaining = ((Number) call(ticket, "getMaxChunkListDepth")).intValue();
                        require(remaining > 0, "Zero ticket capacity");
                        call(call(ticket, "getModData"), "setString|func_74778_a", "OwnerType", "TestLabTransient");
                        call(call(ticket, "getModData"), "setString|func_74778_a", "profileRunId", runId);
                    }
                    String[] point = chunk.split(",");
                    int x = Integer.parseInt(point[0]), z = Integer.parseInt(point[1]);
                    call(type(LAB + "TestLabFixtures"), "requireChunk", world, x, z);
                    Object pair = type("net.minecraft.world.ChunkCoordIntPair").getConstructor(int.class, int.class)
                        .newInstance(x, z);
                    call(type("net.minecraftforge.common.ForgeChunkManager"), "forceChunk", ticket, pair);
                    remaining--;
                }
                receipt.put("ownedTicketCount", tickets.size());
                receipt.put("forcedChunkCount", chunks.size());
                receipt.put("loadedChunks", new ArrayList<String>(chunks));
                receipt.put("canonicalCapturedCells", ((List<?>) field(structure, "cells")).size());
                write(output.resolve("turbine-fixture-setup.json"), receipt);
                Object configuration = call(server, "getConfigurationManager|func_71203_ab");
                originalServerDistance = ((Number) call(configuration, "getViewDistance|func_72395_o")).intValue();
                call(configuration, "setViewDistance|func_152611_a", 8);
                require(
                    ((Number) call(configuration, "getViewDistance|func_72395_o")).intValue() == 8,
                    "Integrated server view distance must match RD8");
                receipt.put("serverViewDistance", 8);
                call(
                    type(LAB + "TestLabCommand"),
                    "transfer",
                    actor,
                    world,
                    cameraX,
                    cameraY + 1d,
                    cameraZ,
                    0f,
                    cameraPitch);
                serverPhase = 2;
                ready = true;
                stageApplied = false;
                receipt.put("structure", String.valueOf(call(structure, "describe")));
                receipt.put("actorType", "SURVIVAL; no health, difficulty or gamerule changes");
                receipt.put(
                    "ownedFixtureBlocks",
                    "Journalled and retained until host verifies unload and removes this exact marker-owned temporary save");
                receipt.put("controller", Arrays.asList(controllerX, controllerY, controllerZ));
                receipt.put("cameraFeet", Arrays.asList(cameraX, cameraY, cameraZ));
                receipt.put("controllerHorizontalDistance", cameraDistance);
                receipt.put("requestedPitch", cameraPitch);
                receipt.put("initialTransferFeet", Arrays.asList(cameraX, cameraY + 1d, cameraZ));
            } else {
                if (!stageApplied) {
                    if (wantRunning) {
                        Object steam = call(field(type("gregtech.api.enums.Materials"), "Steam"), "getGas", 1L);
                        call(type(LAB + "TestLabSupply"), "add", world, input, steam);
                        call(input, "enableWorking");
                        call(outlet, "enableWorking");
                        call(dynamo, "enableWorking");
                        call(controller, "enableWorking");
                    } else {
                        call(type(LAB + "TestLabSupply"), "remove", input);
                        call(controller, "disableWorking");
                        Object reason = field(type("gregtech.api.util.shutdown.ShutDownReasonRegistry"), "NONE");
                        call(machine, "stopMachine", reason);
                    }
                    stageApplied = true;
                }
                long stored = ((Number) call(dynamo, "getStoredEU")).longValue();
                if (stored > 0) {
                    require(
                        Boolean.TRUE.equals(call(dynamo, "decreaseStoredEnergyUnits", stored, false)),
                        "Native dynamo load refused");
                    generatedEU += stored;
                }
                Object tank = call(outlet, "getMetaTileEntity");
                call(
                    tank,
                    "drain",
                    Enum.valueOf((Class) type("net.minecraftforge.common.util.ForgeDirection"), "UNKNOWN"),
                    Integer.MAX_VALUE,
                    true);
            }
            publishState();
        } catch (Throwable cause) {
            cancel(unwrap(cause));
        }
    }

    public void serverTiming(boolean start) {
        if (!tickSampling || System.nanoTime() - stageStarted > 30_000_000_000L) return;
        if (start) serverTickStarted = System.nanoTime();
        else if (serverTickStarted != 0) {
            serverTickDurations.add(System.nanoTime() - serverTickStarted);
            serverTickStarted = 0;
        }
    }

    private void publishState() throws Exception {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("serverTicks", serverTicks);
        result.put("phase", serverPhase);
        result.put("runningRequested", wantRunning);
        result.put("stageApplied", stageApplied);
        if (structure != null) result.put("nativeBuild", String.valueOf(call(structure, "describe")));
        if (controller != null) {
            result.put("active", call(controller, "isActive"));
            result.put("allowedToWork", call(controller, "isAllowedToWork"));
            result.put("formed", field(machine, "mMachine"));
            result.put("materialTier", field(machine, "mMaterialTier"));
            result.put("casingTier", field(machine, "mCasingTier"));
            result.put("generatedEU", generatedEU);
            result
                .put("supplyReplenished", input == null ? 0 : call(type(LAB + "TestLabSupply"), "replenished", input));
        }
        serverState = Collections.unmodifiableMap(result);
    }

    public void clientTick(Object mc) throws Exception {
        if (finished) return;
        if (!opened) {
            require(field(mc, "theWorld|field_71441_e") == null, "Refuse existing client world");
            originalQuality = call(type(VIS), "quality");
            Object survival = Enum.valueOf((Class) type("net.minecraft.world.WorldSettings$GameType"), "SURVIVAL");
            Object settings = type("net.minecraft.world.WorldSettings")
                .getConstructor(
                    long.class,
                    type("net.minecraft.world.WorldSettings$GameType"),
                    boolean.class,
                    boolean.class,
                    type("net.minecraft.world.WorldType"))
                .newInstance(
                    15520261113L,
                    survival,
                    false,
                    false,
                    field(type("net.minecraft.world.WorldType"), "FLAT|field_77138_c"));
            // Keep the connected business actor non-OP; control uses the server console.
            call(
                mc,
                "launchIntegratedServer|func_71371_a",
                save.getFileName()
                    .toString(),
                "Owned turbine profile",
                settings);
            opened = true;
            return;
        }
        clientTicks++;
        if (cancel) {
            if (sparkStarted) {
                try {
                    spark(mc, "/sparkc profiler stop --save-to-file");
                } catch (Throwable ignored) {}
                sparkStarted = false;
            }
            if (!cleaned) return;
            if (originalQuality != null) call(type(VIS), "setQuality", originalQuality);
            if (renderConfigured) {
                Object settings = field(mc, "gameSettings|field_71474_y");
                try {
                    set(settings, "renderDistanceChunks|field_151451_c", originalDistance);
                    set(settings, "limitFramerate|field_74350_i", originalFrameCap);
                    set(settings, "fovSetting|field_74334_X", originalFov);
                    set(settings, "enableVsync|field_74352_v", originalVsync);
                    call(type("com.gtnewhorizons.angelica.glsm.GLStateManager"), "setVSyncEnabled", originalVsync);
                    call(field(mc, "renderGlobal|field_71438_f"), "loadRenderers|func_72712_a");
                    call(settings, "sendSettingsToServer|func_82879_c");
                    receipt.put("clientRenderSettingsRestored", true);
                } finally {
                    // Preserve a restore failure as FAIL, then allow normal unload instead of retrying GL forever.
                    renderConfigured = false;
                }
            }
            if (failure == null && segments.size() != stages.size())
                failure = new IllegalStateException("Profile cancelled before all three segments completed");
            receipt.put("cleanupPending", false);
            receipt.put("actorReturned", returned);
            receipt.put("serverFinal", serverState);
            receipt.put("status", failure == null ? "PASS" : "FAIL");
            write(output.resolve("turbine-profile.json"), receipt);
            finished = true;
            hooks.complete(receipt, failure);
            return;
        }
        if ((System.nanoTime() - started) > 840_000_000_000L) {
            cancel(new IllegalStateException("Profile fixture wall-clock timeout"));
            return;
        }
        if (!ready) return;
        Object player = field(mc, "thePlayer|field_71439_g"), clientWorld = field(mc, "theWorld|field_71441_e");
        if (player == null || clientWorld == null
            || ((Number) field(player, "dimension|field_71093_bK")).intValue()
                != ((Number) call(type(LAB + "TestLabDimension"), "id")).intValue())
            return;
        if (stage >= stages.size()) {
            cancel = true;
            return;
        }
        require(
            ((Number) field(mc, "displayWidth|field_71443_c")).intValue() == 1280
                && ((Number) field(mc, "displayHeight|field_71440_d")).intValue() == 720,
            "Frozen 1280x720 viewport required");
        Object settings = field(mc, "gameSettings|field_71474_y");
        if (!renderConfigured) {
            originalDistance = ((Number) field(settings, "renderDistanceChunks|field_151451_c")).intValue();
            originalFrameCap = ((Number) field(settings, "limitFramerate|field_74350_i")).intValue();
            originalFov = ((Number) field(settings, "fovSetting|field_74334_X")).floatValue();
            originalVsync = Boolean.TRUE.equals(field(settings, "enableVsync|field_74352_v"));
            renderConfigured = true;
            set(settings, "renderDistanceChunks|field_151451_c", 8);
            set(settings, "limitFramerate|field_74350_i", 260);
            set(settings, "fovSetting|field_74334_X", 70f);
            set(settings, "enableVsync|field_74352_v", false);
            call(type("com.gtnewhorizons.angelica.glsm.GLStateManager"), "setVSyncEnabled", false);
            call(field(mc, "renderGlobal|field_71438_f"), "loadRenderers|func_72712_a");
            call(settings, "sendSettingsToServer|func_82879_c");
            receipt.put(
                "clientRenderTransition",
                "Explicit RD8 native renderer reload and C15 settings; original options bytes owned by host");
            return;
        }
        require(
            ((Number) field(settings, "renderDistanceChunks|field_151451_c")).intValue() == 8,
            "Frozen render distance 8 required");
        require(
            ((Number) field(settings, "fovSetting|field_74334_X")).floatValue() == 70f
                && ((Number) field(settings, "limitFramerate|field_74350_i")).intValue() == 260
                && !Boolean.TRUE.equals(field(settings, "enableVsync|field_74352_v")),
            "Frozen FOV/frame-cap/vsync changed");
        boolean atCamera = Math.abs(num(field(player, "posX|field_70165_t")) - cameraX) < .01d
            && Math.abs(num(field(field(player, "boundingBox|field_70121_D"), "minY|field_72338_b")) - cameraY) < .01d
            && Math.abs(num(field(player, "posZ|field_70161_v")) - cameraZ) < .01d;
        boolean supportLoaded = Boolean.TRUE
            .equals(call(clientWorld, "blockExists|func_72899_e", (int) cameraX, (int) cameraY - 1, (int) cameraZ))
            && !Boolean.TRUE
                .equals(call(clientWorld, "isAirBlock|func_147437_c", (int) cameraX, (int) cameraY - 1, (int) cameraZ));
        boolean grounded = Boolean.TRUE.equals(field(player, "onGround|field_70122_E"));
        // Only initial entry may settle naturally. Never relocate or sample an unloaded/airborne camera.
        if (stage == 0 && !sampling) {
            cameraStableTicks = atCamera && supportLoaded && grounded ? cameraStableTicks + 1 : 0;
            if (cameraStableTicks < 20) {
                require(
                    ++cameraWaitTicks <= 400,
                    "Initial camera did not naturally settle on loaded support; actual=" + Arrays.asList(
                        field(player, "posX|field_70165_t"),
                        field(field(player, "boundingBox|field_70121_D"), "minY|field_72338_b"),
                        field(player, "posZ|field_70161_v"),
                        supportLoaded,
                        grounded));
                return;
            }
        }
        require(
            atCamera,
            "Frozen natural camera position changed; actual=" + Arrays.asList(
                field(player, "posX|field_70165_t"),
                field(field(player, "boundingBox|field_70121_D"), "minY|field_72338_b"),
                field(player, "posZ|field_70161_v")));
        require(field(mc, "currentScreen|field_71462_r") == null, "Profile requires natural world view without a GUI");
        JsonObject spec = stages.get(stage)
            .getAsJsonObject();
        if (segment == null) {
            wantRunning = spec.get("running")
                .getAsBoolean();
            stageApplied = false;
            settle = 0;
            call(
                type(VIS),
                "setQuality",
                Enum.valueOf(
                    (Class) type(VIS + "$Quality"),
                    spec.get("quality")
                        .getAsString()));
            segment = new LinkedHashMap<String, Object>();
            segment.put(
                "name",
                spec.get("name")
                    .getAsString());
            segment.put("running", wantRunning);
            segment.put(
                "quality",
                spec.get("quality")
                    .getAsString());
            segment.put(
                "requestedYaw",
                spec.get("yaw")
                    .getAsFloat());
            segment.put("serverBefore", serverState);
            initialSegmentEU = generatedEU;
            stagePrepared = System.nanoTime();
        }
        float yaw = spec.get("yaw")
            .getAsFloat();
        set(player, "rotationYaw|field_70177_z", yaw);
        set(player, "prevRotationYaw|field_70126_B", yaw);
        set(player, "rotationPitch|field_70125_A", cameraPitch);
        set(player, "prevRotationPitch|field_70127_C", cameraPitch);
        if (!sampling) {
            require(
                System.nanoTime() - stagePrepared < 120_000_000_000L,
                "Client running/visibility gate timeout: " + segment);
            if (!stageApplied || Boolean.TRUE.equals(serverState.get("active")) != wantRunning
                || !Boolean.TRUE.equals(serverState.get("formed"))
                || (wantRunning && generatedEU <= initialSegmentEU)) {
                settle = 0;
                return;
            }
            if (++settle < 100) return;
            if (clientTicks % 10 != 0) return;
            segment.put("lastServerGateState", serverState);
            if (clientTicks % 100 == 0) write(output.resolve("turbine-profile-wait.json"), segment);
            Object clientTile = call(clientWorld, "getTileEntity|func_147438_o", controllerX, controllerY, controllerZ);
            if (clientTile == null) return;
            Object clientMachine = call(clientTile, "getMetaTileEntity");
            if (clientMachine == null) return;
            segment.put("clientIsWorking", call(type(VIS), "isWorking", clientMachine));
            segment.put("clientSafeMask", call(type(VIS), "safeGroupMask", clientMachine));
            segment.put("clientUploadedMask", call(type(VIS), "uploadedGroupMask", clientMachine));
            if (wantRunning && !"OFF".equals(
                spec.get("quality")
                    .getAsString())
                && !Boolean.TRUE.equals(call(type(VIS), "isWorking", clientMachine))) return;
            int mask = ((Number) call(type(VIS), "safeGroupMask", clientMachine)).intValue();
            int uploaded = ((Number) call(type(VIS), "uploadedGroupMask", clientMachine)).intValue();
            if (wantRunning && spec.get("quality")
                .getAsString()
                .equals("STANDARD") && (mask == 0 || uploaded != mask)) return;
            Map<?, ?> visibility = (Map<?, ?>) call(type(VIS), "restorationDiagnostics");
            if (!visibilityReady(
                visibility,
                spec.get("quality")
                    .getAsString()
                    .equals("OFF"))) {
                if (settle > 2400) throw new IllegalStateException("Visibility mask/restoration ACK did not settle");
                return;
            }
            segment.put("visibilityBefore", summary(visibility));
            segment.put("serverAtStart", serverState);
            segment.put("cameraGroundProof", Arrays.asList(supportLoaded, grounded, cameraStableTicks));
            segment.put(
                "camera",
                Arrays.asList(
                    num(field(player, "posX|field_70165_t")),
                    num(field(player, "posY|field_70163_u")),
                    num(field(player, "posZ|field_70161_v")),
                    yaw,
                    cameraPitch));
            segment.put("safeGroupMask", mask);
            segment.put("uploadedGroupMask", uploaded);
            segment.put("cameraFeetY", num(field(field(player, "boundingBox|field_70121_D"), "minY|field_72338_b")));
            segment.put(
                "actualRenderParameters",
                Arrays.asList(
                    field(settings, "renderDistanceChunks|field_151451_c"),
                    field(settings, "limitFramerate|field_74350_i"),
                    field(settings, "fovSetting|field_74334_X")));
            previousProfiles = profiles();
            frames = frameNanos = intervals = maxFrameNanos = 0;
            lastFrameStart = 0;
            frameDurations.clear();
            frameIntervals.clear();
            serverTickDurations.clear();
            tickSampling = true;
            spark(mc, COMMAND);
            sparkStarted = true;
            segment.put("clientCountersAtStart", diagnosticCounters());
            stageStarted = System.nanoTime();
            sampling = true;
            hooks.screenshot(
                "turbine-" + spec.get("name")
                    .getAsString());
            return;
        }
        long elapsed = System.nanoTime() - stageStarted;
        if (elapsed < 32_000_000_000L) return;
        Set<Path> now = profiles();
        now.removeAll(previousProfiles);
        if (now.isEmpty()) {
            if (elapsed > 65_000_000_000L) throw new IllegalStateException("sparkc produced no new local profile file");
            return;
        }
        for (Path file : now) if (System.currentTimeMillis() - Files.getLastModifiedTime(file)
            .toMillis() < 1000) return;
        require(frames >= 30, "Insufficient natural render frames");
        require(
            Boolean.TRUE.equals(serverState.get("active")) == wantRunning,
            "Machine activity changed during profile segment");
        if (wantRunning) require(generatedEU > initialSegmentEU, "No native dynamo generation during running profile");
        tickSampling = false;
        synchronized (serverTickDurations) {
            segment.put("serverTickDurationsNanos", new ArrayList<Long>(serverTickDurations));
        }
        segment.put("elapsedNanos", elapsed);
        segment.put("frameCount", frames);
        segment.put("renderNanos", frameNanos);
        segment.put("maxFrameNanos", maxFrameNanos);
        segment.put("frameIntervalsNanos", new ArrayList<Long>(frameIntervals));
        segment.put("renderDurationsNanos", new ArrayList<Long>(frameDurations));
        segment.put("serverAfter", serverState);
        if (!segment.containsKey("clientCountersAtEnd")) segment.put("clientCountersAtEnd", diagnosticCounters());
        segment.put(
            "clientCounterDelta",
            counterDelta(
                (Map<String, Object>) segment.get("clientCountersAtStart"),
                (Map<String, Object>) segment.get("clientCountersAtEnd")));
        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        for (Path file : now) {
            require(Files.size(file) > 0, "Empty spark profile");
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put(
                "path",
                file.toAbsolutePath()
                    .toString());
            entry.put("bytes", Files.size(file));
            entry.put("sha256", sha(file));
            files.add(entry);
        }
        segment.put("sparkProfiles", files);
        segments.add(segment);
        write(output.resolve("turbine-profile-progress.json"), receipt);
        segment = null;
        sampling = false;
        sparkStarted = false;
        stage++;
    }

    public void render(boolean start) {
        if (!sampling || cancel) return;
        if (System.nanoTime() - stageStarted > 30_000_000_000L) {
            if (!segment.containsKey("clientCountersAtEnd")) try {
                segment.put("clientCountersAtEnd", diagnosticCounters());
            } catch (Exception e) {
                cancel(e);
            }
            return;
        }
        long now = System.nanoTime();
        if (start) {
            frameStart = now;
            if (lastFrameStart != 0 && frameIntervals.size() < 20000) frameIntervals.add(now - lastFrameStart);
            lastFrameStart = now;
        } else if (frameStart != 0) {
            long duration = now - frameStart;
            frames++;
            frameNanos += duration;
            maxFrameNanos = Math.max(maxFrameNanos, duration);
            if (frameDurations.size() < 20000) frameDurations.add(duration);
            frameStart = 0;
        }
    }

    public void serverStopping(Object server) {
        if (!cleaned) cancel(new IllegalStateException("Server stopped before profile cleanup completed"));
        try {
            cleanup(server);
        } catch (Throwable cause) {
            if (failure == null) failure = unwrap(cause);
        }
    }

    private void cleanup(Object server) throws Exception {
        if (cleaned) return;
        Object runtime = field(type(LAB + "TestLabRuntime"), "INSTANCE");
        if (Boolean.TRUE.equals(call(runtime, "busy"))) call(runtime, "stop", "Owned client profile cleanup");
        if (input != null) call(type(LAB + "TestLabSupply"), "remove", input);
        if (controller != null) {
            call(controller, "disableWorking");
            call(machine, "stopMachine", field(type("gregtech.api.util.shutdown.ShutDownReasonRegistry"), "NONE"));
        }
        if (actor != null && world != null) {
            Object original = call(server, "worldServerForDimension|func_71218_a", oldDimension);
            call(type(LAB + "TestLabCommand"), "transfer", actor, original, oldX, oldY, oldZ, oldYaw, oldPitch);
            returned = true;
        }
        for (Object ticket : tickets)
            call(type("net.minecraftforge.common.ForgeChunkManager"), "releaseTicket", ticket);
        tickets.clear();
        if (world != null) require(
            ((Number) call(type(LAB + "TestLabSupply"), "feedCount", world)).intValue() == 0,
            "Fixture supply remains");
        if (controller != null) publishState();
        if (originalServerDistance >= 0) {
            call(
                call(server, "getConfigurationManager|func_71203_ab"),
                "setViewDistance|func_152611_a",
                originalServerDistance);
            originalServerDistance = -1;
        }
        cleaned = true;
    }

    private static Map<String, Object> diagnosticCounters() throws Exception {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String name : Arrays.asList(
            "hostAdmissions",
            "hostRetirements",
            "clearanceWorldQueries",
            "clearanceMemberChecks",
            "clearanceAirChecks",
            "renderCalls",
            "renderedCubes",
            "motionSubmissions",
            "motionSubmittedBytes",
            "motionMeshCompiles",
            "motionMeshReplays")) {
            try {
                result.put(name, field(type(VIS), name));
            } catch (NoSuchFieldException absent) {
                result.put(name, "MISSING_IN_ARTIFACT");
            }
        }
        return result;
    }

    private static Map<String, Object> counterDelta(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String key : before.keySet()) {
            Object a = before.get(key), b = after.get(key);
            result.put(
                key,
                a instanceof Number && b instanceof Number ? ((Number) b).longValue() - ((Number) a).longValue()
                    : "NOT_AVAILABLE");
        }
        return result;
    }

    private static Map<String, Object> summary(Map<?, ?> visibility) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("pendingRequests", visibility.get("pendingRequests"));
        out.put("rendererRetirementAcks", visibility.get("rendererRetirementAcks"));
        List<Object> entries = new ArrayList<Object>();
        Object rows = visibility.get("entries");
        if (rows instanceof Iterable) for (Object row : (Iterable<?>) rows) {
            Map<?, ?> r = (Map<?, ?>) row;
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (String k : Arrays.asList("definition", "origin", "currentWorld", "mask", "restoringMask", "retired"))
                copy.put(k, r.get(k));
            entries.add(copy);
        }
        out.put("entries", entries);
        return out;
    }

    private static boolean visibilityReady(Map<?, ?> visibility, boolean off) {
        if (((Number) visibility.get("pendingRequests")).intValue() != 0) return false;
        boolean seen = false;
        Object rows = visibility.get("entries");
        if (rows instanceof Iterable) for (Object row : (Iterable<?>) rows) {
            Map<?, ?> r = (Map<?, ?>) row;
            if (!Boolean.TRUE.equals(r.get("currentWorld"))) continue;
            String definition = String.valueOf(r.get("definition"));
            if (!definition.startsWith("mega_steam_turbine")) continue;
            seen = true;
            if (((Number) r.get("restoringMask")).intValue() != 0) return false;
            if (off && ((Number) r.get("mask")).intValue() != 0) return false;
        }
        return off || seen;
    }

    private Set<Path> profiles() throws Exception {
        Set<Path> result = new LinkedHashSet<Path>();
        if (Files.isDirectory(config))
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(config, "profile-*.sparkprofile")) {
                for (Path p : paths) result.add(p.toAbsolutePath());
            }
        return result;
    }

    private static void spark(Object mc, String command) throws Exception {
        Object handler = field(type("net.minecraftforge.client.ClientCommandHandler"), "instance");
        Object player = field(mc, "thePlayer|field_71439_g");
        Object accepted = call(handler, "executeCommand|func_71556_a", player, command);
        require(accepted instanceof Number && ((Number) accepted).intValue() > 0, "Client command refused: " + command);
    }

    private static void write(Path path, Object value) throws Exception {
        Files.write(
            path,
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(value)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String sha(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) > 0;) digest.update(buffer, 0, n);
        }
        StringBuilder s = new StringBuilder();
        for (byte b : digest.digest()) s.append(String.format("%02x", b & 255));
        return s.toString();
    }

    private static double num(Object n) {
        return ((Number) n).doubleValue();
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
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
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
