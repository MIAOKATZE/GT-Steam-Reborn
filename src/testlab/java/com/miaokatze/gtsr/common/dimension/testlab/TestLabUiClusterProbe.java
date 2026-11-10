package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.miaokatze.gtsr.common.gui.mui2.cluster.ClusterSnapshot;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;

/** Original 32-key codec and native five-page tree, without bypassing the client session lifecycle guard. */
final class TestLabUiClusterProbe {

    private TestLabUiClusterProbe() {}

    static void inspect(ModularPanel panel, TestLabUiSnapshot.Manager manager, MTESteamMineralLogisticsCluster machine,
        Map<String, Object> details, List<String> failures) {
        List<IWidget> before = widgets(panel);
        Map<String, SyncHandler<?>> sync = new LinkedHashMap<>(manager.handlers);
        for (IWidget widget : before) {
            if (!"cluster_pages".equals(widget.getName()) || !(widget instanceof PagedWidget<?>pages)) continue;
            int originalPage = pages.getCurrentPageIndex();
            List<Integer> visited = new ArrayList<>();
            try {
                if (pages.getPages()
                    .size() != 5) failures.add("Cluster production panel must contain five pages");
                for (int page = 0; page < pages.getPages()
                    .size(); page++) {
                    pages.setPage(page);
                    if (pages.getCurrentPageIndex() != page || !pages.getCurrentPage()
                        .isEnabled()) failures.add("Cluster native page transition failed " + page);
                    visited.add(pages.getCurrentPageIndex());
                    if (!before.equals(widgets(panel)) || !sync.equals(manager.handlers))
                        failures.add("Cluster page transition rebuilt widget/sync identities " + page);
                }
            } finally {
                pages.setPage(originalPage);
            }
            details.put("nativePagesVisited", visited);
            details.put("nativePageRestored", pages.getCurrentPageIndex() == originalPage);
        }
        if (!details.containsKey("nativePagesVisited")) failures.add("Missing actual cluster_pages widget");
        UUID session = UUID
            .nameUUIDFromBytes("gtsr-isolated-cluster-ui-codec".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ClusterTerminalData.resetSession(session);
        try {
            byte[] full = ClusterTerminalData.assembleSnapshot(machine, session);
            ClusterSnapshot decoded = ClusterSnapshot.merge(null, 1, full);
            int fullMask = ClusterSnapshot.mask(full);
            details.put("productionFullSnapshotBytes", full.length);
            details.put("productionFullChangedMaskUnsigned", Integer.toUnsignedLong(fullMask));
            if (decoded == null || fullMask != -1)
                failures.add("Cluster production first packet must decode all 32 keys");
            byte[] idle = ClusterTerminalData.assembleSnapshot(machine, session);
            details.put("productionImmediateIdleSnapshotBytes", idle.length);
            details
                .put("productionImmediateIdleChangedMaskUnsigned", Integer.toUnsignedLong(ClusterSnapshot.mask(idle)));
            if (ClusterSnapshot.mask(idle) != 0)
                failures.add("Cluster unchanged same-tick snapshot emitted changed keys");
            details.put(
                "clusterSessionDisplayEvidence",
                "NOT_RUN: actual Session.clientTick requires a valid client manager; no validity or client-side guard bypassed");
            details.put(
                "clusterPageEvidence",
                "NATIVE_PAGED_WIDGET_ONLY: visited all five pages on one tree; real tab hit-test/scroll/draft/client lifecycle NOT_RUN");
            details.put(
                "clusterCodecEvidence",
                "PRODUCTION_SOURCE_CODEC_ONLY: isolated UUID full/idle assembly and production ClusterSnapshot.merge; real network/client cache NOT_RUN");
        } finally {
            ClusterTerminalData.releaseSession(session);
        }
    }

    private static List<IWidget> widgets(IWidget root) {
        List<IWidget> widgets = new ArrayList<>();
        widgets.add(root);
        for (IWidget child : root.getChildren()) widgets.addAll(widgets(child));
        return widgets;
    }
}
