package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.gui.MTECriticalMachineTerminalGui;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.CommonBaseMetaTileEntity;
import gregtech.api.metatileentity.CommonMetaTileEntity;

/** One actual controller, no structure, no running recipe, and no player connection or GL context. */
final class TestLabUiTask implements TestLabTask {

    private final MetaTileEntityID type;
    private final boolean terminal;
    private TestLabReport.Result result;
    private IGregTechTileEntity tile;
    private final Map<String, Object> observations = new LinkedHashMap<>();

    TestLabUiTask(MetaTileEntityID type, boolean terminal) {
        this.type = type;
        this.terminal = terminal;
    }

    @Override
    public String id() {
        return "ui." + type.name()
            .toLowerCase(Locale.ROOT) + (terminal ? ".terminal" : "");
    }

    @Override
    public String displayName() {
        return type.name() + (terminal ? " terminal UI" : " controller UI");
    }

    @Override
    public int timeoutTicks() {
        return 100;
    }

    @Override
    public boolean requiresFixtureChunks() {
        return false;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        observations.clear();
        observations.put("probeCategory", "UI_SERVER");
        observations.put("probePhase", "fixture");
        try {
            startIsolated(world, x, y, z);
        } catch (LinkageError error) {
            Map<String, Object> details = observations;
            recordError(details, "linkageError", error);
            details.put(
                "errorClass",
                error.getClass()
                    .getName());
            details.put("missingClassOrLinkageMessage", error.getMessage());
            details.put(
                "renderEvidence",
                "NOT_RUN: dedicated-server class linkage does not support this production GUI path");
            result = new TestLabReport.Result(
                TestLabReport.Status.UNSUPPORTED,
                "Production GUI cannot link on dedicated server: " + error,
                details);
        } catch (AssertionError error) {
            Map<String, Object> details = observations;
            recordError(details, "assertionError", error);
            details.put(
                "errorClass",
                error.getClass()
                    .getName());
            details.put("assertion", error.getMessage());
            details.put("renderEvidence", "NOT_RUN: no client rendering executed");
            result = new TestLabReport.Result(TestLabReport.Status.FAIL, "Production GUI assertion: " + error, details);
        } catch (Exception error) {
            recordError(observations, "probeError", error);
            result = new TestLabReport.Result(
                TestLabReport.Status.FAIL,
                "Production GUI probe failed in " + observations.get("probePhase") + ": " + error,
                observations);
        }
    }

    static void recordError(Map<String, Object> details, String key, Throwable error) {
        List<Map<String, Object>> causes = new ArrayList<>();
        for (int depth = 0; error != null && depth < 4; depth++, error = error.getCause()) {
            Map<String, Object> cause = new LinkedHashMap<>();
            cause.put(
                "type",
                error.getClass()
                    .getName());
            cause.put("message", error.getMessage());
            List<String> frames = new ArrayList<>();
            StackTraceElement[] stack = error.getStackTrace();
            for (int i = 0; i < stack.length && i < 24; i++) frames.add(stack[i].toString());
            cause.put("stackTrace", frames);
            causes.add(cause);
        }
        details.put(key, causes);
    }

    private void startIsolated(WorldServer world, int x, int y, int z) throws Exception {
        if (!(world.provider instanceof TestLabWorldProvider))
            throw new IllegalArgumentException("Not a test lab world");
        // Runtime gives every UI probe the same journalled single-block station. Remove the old tile
        // explicitly: setting the same machine block again can otherwise retain its old MTE instance.
        world.removeTileEntity(x, y, z);
        world.setBlockToAir(x, y, z);
        tile = TestLabFixtures.machine(world, x, y, z, type.ID);
        EntityPlayer player = FakePlayerFactory
            .get(world, new com.mojang.authlib.GameProfile(TestLabFixtures.OWNER, "GTSR UI Probe"));
        player.setPosition(x + 0.5, y + 0.5, z + 0.5);
        Map<String, Object> details = observations;
        details.put("probeCategory", "UI_SERVER");
        details.put("fixture", "REAL_REGISTERED_CONTROLLER_IN_TEST_DIMENSION_WITHOUT_MULTIBLOCK_STRUCTURE");
        details.put("metaTileId", type.ID);
        details.put(
            "machineClass",
            tile.getMetaTileEntity()
                .getClass()
                .getName());
        details.put(
            "renderEvidence",
            "NOT_RUN: client font metrics, OpenGL, textures, full client lifecycle, network transport and final visual acceptance");
        details.put("playerContext", "Forge FakePlayer, no connected player or packets sent");
        details.put(
            "lifecycleBoundary",
            "Production builder and server sync registration; no actual container open, dynamic subpanel open, client global-mode initialization or client screen lifecycle");
        List<String> failures = new ArrayList<>();
        CommonMetaTileEntity machine = (CommonMetaTileEntity) tile.getMetaTileEntity();
        if (terminal || machine.hasMui2Gui()) {
            TestLabUiSnapshot.Manager manager = new TestLabUiSnapshot.Manager(player);
            // Match GuiManager.open's actual dedicated-server path. The no-arg constructor
            // instantiates client-only RecipeViewerSettingsImpl before the GUI can be built.
            UISettings settings = new UISettings(RecipeViewerSettings.DUMMY);
            details.put(
                "uiSettings",
                "Native server UISettings(RecipeViewerSettings.DUMMY), same as ModularUI2 GuiManager.open");
            details.put("probePhase", "productionMui2Builder");
            long builderTiming = TestLabPerformance.begin();
            ModularPanel panel;
            try {
                panel = terminal
                    ? new MTECriticalMachineTerminalGui((MTECriticalMachine) machine)
                        .build(new PosGuiData(player, x, y, z), manager, settings)
                    : machine.buildUI(new PosGuiData(player, x, y, z), manager, settings);
            } finally {
                TestLabPerformance.end("ui.mui2ProductionBuilder", builderTiming);
            }
            if (panel == null) failures.add("Production MUI2 builder returned null");
            else {
                details.put(
                    "productionBuilderEvidence",
                    "PASS: actual registered machine production builder returned real ModularPanel");
                long inspectTiming = TestLabPerformance.begin();
                try {
                    TestLabUiSnapshot.inspect(panel, manager, machine, details, failures);
                } finally {
                    TestLabPerformance.end("ui.mui2TreeSyncCallbacks", inspectTiming);
                }
            }
        } else {
            details.put("probePhase", "productionMui1Builder");
            long builderTiming = TestLabPerformance.begin();
            ModularWindow window;
            try {
                window = ((CommonBaseMetaTileEntity) tile).createWindow(new UIBuildContext(player));
            } finally {
                TestLabPerformance.end("ui.mui1ProductionBuilder", builderTiming);
            }
            if (window == null) {
                details.put("framework", "NO_MODULAR_WINDOW");
                result = new TestLabReport.Result(
                    TestLabReport.Status.UNSUPPORTED,
                    "Machine does not expose a modular GUI",
                    details);
                return;
            }
            long inspectTiming = TestLabPerformance.begin();
            try {
                TestLabUiSnapshot.inspectLegacy(window, details, failures);
            } finally {
                TestLabPerformance.end("ui.mui1TreeCallbacks", inspectTiming);
            }
        }
        details.put("probePhase", "complete");
        details.put("failures", failures);
        result = new TestLabReport.Result(
            failures.isEmpty() ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL,
            failures.isEmpty()
                ? "Production GUI construction and server-observable widget/sync checks passed; visual rendering NOT_RUN"
                : String.join("; ", failures),
            details);
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
        return result;
    }

    @Override
    public void stop(WorldServer world) {
        if (tile != null) {
            tile.disableWorking();
            // Leave only the journalled empty station; no UI fixture remains ticking between probes.
            TileEntity placed = world.getTileEntity(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
            if (placed == tile) {
                world.removeTileEntity(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
                world.setBlockToAir(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
            }
            tile = null;
        }
    }
}
