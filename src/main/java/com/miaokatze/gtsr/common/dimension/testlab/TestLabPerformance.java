package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit-run instrumentation. No sampler, thread, listener or MXBean access exists outside a perf run. */
final class TestLabPerformance {

    static TestLabPerformance current;
    private final Map<String, Distribution> metrics = new LinkedHashMap<>();
    private final Map<String, Long> before;
    private Map<String, Long> after;
    private final List<String> limitations = new ArrayList<>(
        Arrays.asList(
            "SERVER_EVENT_WINDOW is this listener's START-to-END wall time; event priority and other listeners bound it, not the exact native tick",
            "Lab WorldTick START/END wraps MinecraftServer world.tick and updateEntities (including tile updates), but excludes the subsequent entityTracker; listener order still affects boundaries",
            "Whole-server timing, heap and GC include all mods, players, JIT and OS noise; changes are not strict attribution to this lab",
            "Direct phases overlap (for example task tick and nested structure build); never sum overlapping metrics",
            "Percentiles are conservative power-of-two histogram bucket upper bounds, capped at observed max; no raw sample retention",
            "MXBeans sampled every 20 real server ticks and at boundaries; heap deltas are not allocation or proven memory leaks; no forced GC",
            "UI probes execute server builders/models/sync only; no client GL, fonts, screen/network rendering or FPS measurement",
            "Final performance receipt serialization/write is excluded to end self-observation; terminal main-report IO is included",
            "The final receipt is written after the terminal server END window is recorded; that receipt's own IO remains excluded",
            "Budgets are heuristic warnings for local comparison, not universal performance requirements or functional assertions"));
    private final long started = System.nanoTime();
    private long serverStart;
    private long worldStart;
    private String worldPhase;
    private int worldTiles, worldEntities, peakWorldTiles;
    private int supplyFeedPeak;
    private long nonEmptySupplyTicks;
    private final Map<String, Object> plan = new LinkedHashMap<>();
    private long elapsedTicks;
    private long samplingNanos;
    private boolean gcUnavailable;
    private long peakUsedHeapBytes;
    private final long phaseBudgetNanos = 50_000_000L;
    private final long tickBudgetNanos = 50_000_000L;
    private final long heapGrowthBudgetBytes = 256L * 1024 * 1024;
    private final Map<String, Object> resources = new LinkedHashMap<>();

    TestLabPerformance() {
        before = resources();
        after = before;
    }

    void plan(String mode, int tickLimit, List<TestLabTask> tasks) {
        plan.put("mode", mode);
        plan.put("totalRealServerTickLimit", tickLimit);
        plan.put(
            "scope",
            "quick: two physical representatives plus one UI; all: two physical representatives plus one per specialized task family and all server UI probes; neither is exhaustive per-machine functional coverage");
        plan.put(
            "baselineAndCooldownPolicy",
            "Observed under the current server state; not controlled unloaded/clean-room baselines, no subtraction as strict attribution");
        List<Map<String, Object>> entries = new ArrayList<>();
        for (TestLabTask task : tasks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", task.id());
            entry.put("timeoutTicks", task.timeoutTicks());
            entry.put("fixtureChunks", task.requiresFixtureChunks() ? 169 : 0);
            entries.add(entry);
        }
        plan.put("tasks", entries);
    }

    void supply(int feeds) {
        supplyFeedPeak = Math.max(supplyFeedPeak, feeds);
        if (feeds > 0) nonEmptySupplyTicks++;
    }

    static long begin() {
        return current == null ? 0 : System.nanoTime();
    }

    static void end(String name, long start) {
        if (current != null && start != 0) current.add(name, System.nanoTime() - start);
    }

    private void add(String name, long nanos) {
        // Both names and buckets are bounded, independent of run duration.
        if (!metrics.containsKey(name) && metrics.size() >= 512) name = "instrumentation.metricOverflow";
        metrics.computeIfAbsent(name, ignored -> new Distribution())
            .add(Math.max(0, nanos));
    }

    void serverStart() {
        serverStart = System.nanoTime();
    }

    void worldStart(String phase) {
        worldStart = System.nanoTime();
        worldPhase = phase;
    }

    boolean sampleWorldCounts() {
        return elapsedTicks % 20 == 0;
    }

    void worldEnd(int tiles, int entities) {
        if (worldStart != 0) {
            long nanos = System.nanoTime() - worldStart;
            add("background.labWorldEventWindow", nanos);
            add("background.labWorldPhase." + worldPhase, nanos);
        }
        worldStart = 0;
        if (tiles >= 0) {
            worldTiles = tiles;
            worldEntities = entities;
            peakWorldTiles = Math.max(peakWorldTiles, tiles);
        }
    }

    void serverEnd(String phase) {
        if (serverStart != 0) {
            long nanos = System.nanoTime() - serverStart;
            add("background.serverEventWindow", nanos);
            add("background.phase." + phase, nanos);
        }
        serverStart = 0;
        elapsedTicks++;
        if (elapsedTicks % 20 == 0) after = resources();
    }

    private Map<String, Long> resources() {
        long start = System.nanoTime();
        Map<String, Long> result = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        result.put("usedHeapBytes", runtime.totalMemory() - runtime.freeMemory());
        peakUsedHeapBytes = Math.max(peakUsedHeapBytes, result.get("usedHeapBytes"));
        result.put("committedHeapBytes", runtime.totalMemory());
        result.put("maxHeapBytes", runtime.maxMemory());
        try {
            if (gcUnavailable) {
                samplingNanos += System.nanoTime() - start;
                return result;
            }
            long count = 0, millis = 0;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean.getCollectionCount() >= 0) count += bean.getCollectionCount();
                if (bean.getCollectionTime() >= 0) millis += bean.getCollectionTime();
            }
            result.put("gcCount", count);
            result.put("gcMillis", millis);
        } catch (RuntimeException | LinkageError unavailable) {
            gcUnavailable = true;
            limitations.add(
                "GC MXBeans unavailable: " + unavailable.getClass()
                    .getSimpleName());
        }
        samplingNanos += System.nanoTime() - start;
        return result;
    }

    void cleanup(int taskTickets, int manualTickets, boolean anchor, int feeds, boolean resetTicket, Boolean uiEmpty) {
        resources.put("remainingTaskTickets", taskTickets);
        resources.put("remainingManualSupplyTickets", manualTickets);
        resources.put("remainingAnchorTicket", anchor);
        resources.put("remainingSupplyFeeds", feeds);
        resources.put("remainingResetTicket", resetTicket);
        resources.put("uiStationEmptyAfterCleanup", uiEmpty);
        resources.put(
            "ownedTransientResourcesReleased",
            taskTickets == 0 && manualTickets == 0
                && !anchor
                && feeds == 0
                && !resetTicket
                && !Boolean.FALSE.equals(uiEmpty));
        resources.put(
            "baselinePolicy",
            "Performance start rejects pre-existing manual supply tickets or supply feeds; other physical workstations remain outside the selected reset scope");
        resources.put(
            "scope",
            "Lab-owned transient handles only; selected fixture journal positions deliberately retained for repeatable reset; other mods and cached loaded chunks are not claimed as leaks");
    }

    Map<String, Object> snapshot(boolean terminal) {
        long cost = System.nanoTime();
        if (terminal) after = resources();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("coveragePlan", plan);
        result.put("elapsedRealServerTicks", elapsedTicks);
        result.put("elapsedWallNanos", System.nanoTime() - started);
        result.put("limits", limitations);
        result.put("heapAndGcBefore", before);
        result.put("heapAndGcLatest", after);
        result.put("sampledPeakUsedHeapBytes", peakUsedHeapBytes);
        result.put("sampledLabLoadedTilesLatest", worldTiles);
        result.put("sampledLabLoadedEntitiesLatest", worldEntities);
        result.put("sampledLabLoadedTilesPeak", peakWorldTiles);
        result.put("observedSupplyFeedPeak", supplyFeedPeak);
        result.put("nonEmptySupplyTickSamples", nonEmptySupplyTicks);
        Map<String, Long> delta = new LinkedHashMap<>();
        for (String key : before.keySet()) if (after.containsKey(key)) delta.put(key, after.get(key) - before.get(key));
        result.put("backgroundResourceDeltaNotAttribution", delta);
        result.put("resourcesAfterCleanup", new LinkedHashMap<>(resources));
        Map<String, Object> summaries = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, Distribution> entry : metrics.entrySet()) {
            Distribution distribution = entry.getValue();
            summaries.put(entry.getKey(), distribution.summary());
            long budget = entry.getKey()
                .startsWith("background.") ? tickBudgetNanos : phaseBudgetNanos;
            if (distribution.max > budget)
                warnings.add(entry.getKey() + " max=" + distribution.max + "ns > heuristic " + budget + "ns");
        }
        if (delta.get("usedHeapBytes") > heapGrowthBudgetBytes) warnings.add(
            "Observed heap growth exceeds heuristic 256 MiB; background/GC noise, investigate, not proof of a leak");
        if (Boolean.FALSE.equals(resources.get("ownedTransientResourcesReleased")))
            warnings.add("Lab transient resource handles remain after cleanup");
        result.put("directAndBackgroundMetrics", summaries);
        Map<String, Boolean> observed = new LinkedHashMap<>();
        for (String phase : Arrays.asList(
            "dimension.createOrReuse",
            "chunk.require",
            "chunk.anchorAcquire",
            "chunk.taskTicketAcquire",
            "direct.reset.tick",
            "structure.definitionScan",
            "structure.placeBatch",
            "structure.hatchInstall",
            "structure.canonicalCheck",
            "direct.machine.execute",
            "supply.refill",
            "ui.mui1ProductionBuilder",
            "ui.mui2ProductionBuilder",
            "ui.mui1TreeCallbacks",
            "ui.mui2TreeSyncCallbacks",
            "report.serialize",
            "report.writeBothTargets",
            "cleanup.taskStop",
            "cleanup.taskTicketRelease",
            "cleanup.anchorRelease",
            "background.serverEventWindow",
            "background.labWorldEventWindow")) observed.put(phase, metrics.containsKey(phase));
        result.put("instrumentedPhaseObservedNotFunctionalPass", observed);
        result.put("heuristicWarnings", warnings);
        result.put("directCallMaxBudgetNanos", phaseBudgetNanos);
        result.put("serverEventMaxBudgetNanos", tickBudgetNanos);
        result.put("heapGrowthBudgetBytes", heapGrowthBudgetBytes);
        result.put("resourceSamplingCostNanos", samplingNanos);
        result.put(
            "instrumentationTimingPolicy",
            "nanoTime pairs and fixed 64-bucket histograms; resource/snapshot cost observed separately; timer calls and map insert cost are not fully isolated");
        add("instrumentation.snapshot", System.nanoTime() - cost);
        return result;
    }

    private static final class Distribution {

        final long[] buckets = new long[64];
        long count, total, max;

        void add(long nanos) {
            count++;
            total += nanos;
            max = Math.max(max, nanos);
            buckets[nanos == 0 ? 0 : 64 - Long.numberOfLeadingZeros(nanos)]++;
        }

        long percentile(int percent) {
            long rank = (count * percent + 99) / 100, sum = 0;
            for (int i = 0; i < buckets.length; i++) {
                sum += buckets[i];
                if (sum >= rank) return Math.min(max, i >= 63 ? Long.MAX_VALUE : (1L << i) - 1);
            }
            return max;
        }

        Map<String, Long> summary() {
            Map<String, Long> result = new LinkedHashMap<>();
            result.put("samples", count);
            result.put("totalNanos", total);
            result.put("maxNanos", max);
            result.put("p50UpperBoundNanos", percentile(50));
            result.put("p95UpperBoundNanos", percentile(95));
            return result;
        }
    }
}
