package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.WorldServer;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.gui.mui2.TcdsTerminalFactory;
import com.miaokatze.gtsr.common.gui.mui2.TcdsTerminalPanel;
import com.miaokatze.gtsr.common.gui.mui2.cluster.ClusterSessionSyncHandler;
import com.miaokatze.gtsr.common.gui.mui2.cluster.ClusterTerminalFactory;
import com.miaokatze.gtsr.common.gui.mui2.cluster.ClusterTerminalPanel;
import com.miaokatze.gtsr.common.gui.mui2.hub.HubTerminalFactory;
import com.miaokatze.gtsr.common.gui.mui2.hub.HubTerminalPanel;
import com.miaokatze.gtsr.common.gui.mui2.inventory.AggregatorConfigFactory;
import com.miaokatze.gtsr.common.gui.mui2.inventory.AggregatorConfigPanel;
import com.miaokatze.gtsr.common.gui.mui2.inventory.AggregatorConfigSession;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import gregtech.api.metatileentity.CommonMetaTileEntity;

/** Production window inventory separate from the historical 78 machine / 10 critical builders. */
final class TestLabUiTerminalCatalog {

    private TestLabUiTerminalCatalog() {}

    abstract static class Entry {

        final String id, factory, panel;
        final MetaTileEntityID machine;

        Entry(String id, MetaTileEntityID machine, String factory, String panel) {
            this.id = id;
            this.machine = machine;
            this.factory = factory;
            this.panel = panel;
        }

        abstract ModularPanel build(PosGuiData data, CommonMetaTileEntity machine, TestLabUiSnapshot.Manager manager,
            UISettings settings);

        void inspect(ModularPanel panel, TestLabUiSnapshot.Manager manager, CommonMetaTileEntity machine,
            Map<String, Object> details, List<String> failures) throws Exception {}
    }

    static void addTasks(List<TestLabTask> tasks) {
        tasks.add(
            new TestLabUiTask(
                new Entry(
                    "tcds",
                    MetaTileEntityID.TCDS_CONTROLLER,
                    TcdsTerminalFactory.FACTORY_NAME,
                    TcdsTerminalFactory.PANEL_NAME) {

                    @Override
                    ModularPanel build(PosGuiData data, CommonMetaTileEntity machine, TestLabUiSnapshot.Manager manager,
                        UISettings settings) {
                        return TcdsTerminalPanel.build(data, (MTEThermoChemicalDenseSteamGenerator) machine, manager);
                    }

                    @Override
                    void inspect(ModularPanel panel, TestLabUiSnapshot.Manager manager, CommonMetaTileEntity machine,
                        Map<String, Object> details, List<String> failures) throws Exception {
                        TestLabUiTcdsProbe
                            .inspect(panel, manager, (MTEThermoChemicalDenseSteamGenerator) machine, details, failures);
                    }
                }));
        tasks.add(new TestLabUiChestTask());
        tasks.add(
            new TestLabUiTask(
                new Entry(
                    "aggregator",
                    MetaTileEntityID.SINGULARITY_CRUST_STEAM_BORER,
                    AggregatorConfigFactory.FACTORY_NAME,
                    AggregatorConfigFactory.PANEL_NAME) {

                    @Override
                    ModularPanel build(PosGuiData data, CommonMetaTileEntity machine, TestLabUiSnapshot.Manager manager,
                        UISettings settings) {
                        return AggregatorConfigPanel.build(data, (MTECrustMatterAggregator) machine, manager, settings);
                    }

                    @Override
                    void inspect(ModularPanel panel, TestLabUiSnapshot.Manager manager, CommonMetaTileEntity machine,
                        Map<String, Object> details, List<String> failures) {
                        if (!(manager.handlers
                            .get(AggregatorConfigSession.KEY + ":0") instanceof AggregatorConfigSession))
                            failures.add("Missing production aggregator_session:0 handler");
                        long storage = manager.handlers.keySet()
                            .stream()
                            .filter(key -> key.startsWith("aggregator_dimension:"))
                            .count();
                        long inventory = manager.handlers.keySet()
                            .stream()
                            .filter(key -> key.startsWith("player:"))
                            .count();
                        details.put("registeredStorageSlots", storage);
                        details.put("registeredPlayerSlots", inventory);
                        if (storage != 25 || inventory != 36) failures
                            .add("Aggregator production slots expected 25+36, got " + storage + "+" + inventory);
                        details.put(
                            "terminalDynamicStateEvidence",
                            "NOT_RUN: real player session, ore list source transitions and complete client display");
                    }
                }));
        tasks.add(
            new TestLabUiTask(
                new Entry(
                    "cluster",
                    MetaTileEntityID.CLUSTER_CONTROLLER,
                    ClusterTerminalFactory.FACTORY_NAME,
                    ClusterTerminalFactory.PANEL_NAME) {

                    @Override
                    ModularPanel build(PosGuiData data, CommonMetaTileEntity machine, TestLabUiSnapshot.Manager manager,
                        UISettings settings) {
                        return ClusterTerminalPanel.build(
                            new ClusterTerminalFactory.ClusterGuiData(
                                data.getPlayer(),
                                data.getX(),
                                data.getY(),
                                data.getZ(),
                                0,
                                1L),
                            (MTESteamMineralLogisticsCluster) machine,
                            manager);
                    }

                    @Override
                    void inspect(ModularPanel panel, TestLabUiSnapshot.Manager manager, CommonMetaTileEntity machine,
                        Map<String, Object> details, List<String> failures) throws Exception {
                        if (!(manager.handlers
                            .get(ClusterSessionSyncHandler.SYNC_KEY + ":0") instanceof ClusterSessionSyncHandler))
                            failures.add("Missing production cluster_session:0 handler");
                        TestLabUiClusterProbe
                            .inspect(panel, manager, (MTESteamMineralLogisticsCluster) machine, details, failures);
                    }
                }));
        hub(
            tasks,
            "steam_hub",
            MetaTileEntityID.STEAM_HUB_ARRAY,
            TerminalUiType.STEAM_HUB,
            HubTerminalFactory.STEAM_FACTORY);
        hub(
            tasks,
            "water_hub",
            MetaTileEntityID.WATER_HUB_ARRAY,
            TerminalUiType.WATER_HUB,
            HubTerminalFactory.WATER_FACTORY);
        hub(
            tasks,
            "drilling_hub",
            MetaTileEntityID.SINGULARITY_DRILLING_HUB,
            TerminalUiType.SINGULARITY_HUB,
            HubTerminalFactory.DRILLING_FACTORY);
        for (MetaTileEntityID node : new MetaTileEntityID[] { MetaTileEntityID.STEAM_CACHE_NODE,
            MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE, MetaTileEntityID.OVERPRESSURE_STEAM_CACHE_NODE,
            MetaTileEntityID.WATER_CACHE_NODE, MetaTileEntityID.REINFORCED_WATER_CACHE_NODE,
            MetaTileEntityID.OVERPRESSURE_WATER_CACHE_NODE, MetaTileEntityID.SINGULARITY_STEAM_COMPARTMENT,
            MetaTileEntityID.SINGULARITY_STEAM_OUTPUT_COMPARTMENT, MetaTileEntityID.SINGULARITY_FLUID_INPUT_COMPARTMENT,
            MetaTileEntityID.SINGULARITY_FLUID_OUTPUT_COMPARTMENT }) {
            hub(
                tasks,
                "node_editor." + node.name()
                    .toLowerCase(java.util.Locale.ROOT),
                node,
                TerminalUiType.CACHE_NODE,
                HubTerminalFactory.NODE_FACTORY);
        }
        // Keep only string descriptors here: discovering the server catalog must not resolve client classes.
        for (String id : new String[] { "tuning_wand", "machine_debug", "prosperity_journal", "prosperity_navigator",
            "spacetime_altar_story" }) tasks.add(new ClientOnly(id));
    }

    private static void hub(List<TestLabTask> tasks, String id, MetaTileEntityID machine, TerminalUiType type,
        String factory) {
        tasks.add(
            new TestLabUiTask(
                new Entry(
                    id,
                    machine,
                    factory,
                    type == TerminalUiType.CACHE_NODE ? "cache_node_editor" : HubTerminalFactory.PANEL_NAME) {

                    @Override
                    ModularPanel build(PosGuiData data, CommonMetaTileEntity machine, TestLabUiSnapshot.Manager manager,
                        UISettings settings) {
                        return HubTerminalPanel.build(HubTerminalFactory.forType(type), data, machine, manager);
                    }

                    @Override
                    void inspect(ModularPanel panel, TestLabUiSnapshot.Manager manager, CommonMetaTileEntity machine,
                        Map<String, Object> details, List<String> failures) throws Exception {
                        TestLabUiHubProbe.inspect(panel, manager, details, failures);
                    }
                }));
    }

    private static final class ClientOnly implements TestLabTask {

        private final String name;
        private TestLabReport.Result result;

        ClientOnly(String name) {
            this.name = name;
        }

        public String id() {
            return "ui.client." + name;
        }

        public String displayName() {
            return name + " CLIENT_ONLY (real client required)";
        }

        public int timeoutTicks() {
            return 20;
        }

        public boolean requiresFixtureChunks() {
            return false;
        }

        public void start(WorldServer world, int x, int y, int z) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("probeCategory", "UI_CLIENT_ONLY");
            details.put("framework", "MUI2_CLIENT_ONLY");
            details.put("executionStatus", "NOT_RUN");
            details.put("productionBuilderEvidence", "NOT_RUN: client-only panel is not loaded on dedicated server");
            details
                .put("actualOpenEvidence", "NOT_RUN: requires real connected client and authorized production route");
            details.put("renderEvidence", "NOT_RUN: real Minecraft GL/font/input verification belongs to client QA");
            result = new TestLabReport.Result(
                TestLabReport.Status.SKIP,
                "CLIENT_ONLY / NOT_RUN on dedicated server",
                details);
        }

        public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
            return result;
        }

        public void stop(WorldServer world) {}
    }
}
