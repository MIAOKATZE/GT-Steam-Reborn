// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.miaokatze.gtsr.common.machine.orecenter.CenterTickTiming;
import com.miaokatze.gtsr.common.machine.orecenter.CenterTickTiming.Stage;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Uses the same tick boundary for an empty window and each pre-warmed entity scale. */
public final class TestLabCenterTickSamples implements CenterTickTiming.Observer {

    private static final Stage[] STAGES = Stage.values();
    private static final long SERVER_BUDGET_NS = 50_000_000L, PLANNING_BUDGET_NS = 2_000_000L;
    private static final long PREVIOUS_PLANNER_PEAK_NS = 13_793_900L;
    private final WorldServer world;
    private final IGregTechTileEntity[] observed;
    private final List<Sample> rows = new ArrayList<>();
    private final long[] stage = new long[STAGES.length], stageMax = new long[STAGES.length];
    private final int[] calls = new int[STAGES.length];
    private final long[] planningByMachine;
    private final int[] runningCounts;
    private int activeCount, target, worldTilesAtStart, worldChunksAtStart;
    private long serverStart, worldStart, worldElapsed, epochAtStart;
    private boolean registered, missingWorldTick;

    TestLabCenterTickSamples(WorldServer world, IGregTechTileEntity[] observed) {
        this.world = world;
        this.observed = observed;
        planningByMachine = new long[observed.length];
        runningCounts = new int[observed.length + 1];
    }

    void start() {
        CenterTickTiming.observe(this);
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(this);
            registered = true;
        } finally {
            if (!registered) CenterTickTiming.stop(this);
        }
    }

    void stop() {
        if (!registered) return;
        CenterTickTiming.stop(this);
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        registered = false;
        target = 0;
    }

    void beginWindow(int centers, int ticks) {
        activeCount = centers;
        target = ticks;
        rows.clear();
        Arrays.fill(planningByMachine, 0);
        Arrays.fill(runningCounts, 0);
        worldTilesAtStart = world.loadedTileEntityList.size();
        worldChunksAtStart = world.getChunkProvider()
            .getLoadedChunkCount();
        epochAtStart = System.currentTimeMillis();
        GTSteamReborn.LOG
            .info("Ore center entity timing window opened centers={} epochMillis={}", centers, epochAtStart);
    }

    boolean isRecording() {
        return target > 0;
    }

    int count() {
        return rows.size();
    }

    boolean isComplete() {
        if (missingWorldTick) throw new IllegalStateException("Missing Lab world tick within the server tick");
        return rows.size() == target;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (!isRecording()) return;
        if (event.phase == TickEvent.Phase.START) {
            Arrays.fill(stage, 0);
            Arrays.fill(stageMax, 0);
            Arrays.fill(calls, 0);
            worldStart = worldElapsed = 0;
            serverStart = System.nanoTime();
        } else if (serverStart != 0 && rows.size() < target) {
            long serverElapsed = System.nanoTime() - serverStart;
            if (worldElapsed == 0) {
                missingWorldTick = true;
                serverStart = 0;
                return;
            }
            rows.add(
                new Sample(
                    serverElapsed,
                    worldElapsed,
                    System.currentTimeMillis(),
                    world.getTotalWorldTime(),
                    MinecraftServer.getServer()
                        .getTickCounter(),
                    stage.clone(),
                    stageMax.clone(),
                    calls.clone()));
            int running = 0;
            for (int i = 0; i < activeCount; i++) if (observed[i].isActive()) running++;
            runningCounts[running]++;
            serverStart = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void worldTick(TickEvent.WorldTickEvent event) {
        if (!isRecording() || serverStart == 0 || event.world != world) return;
        if (event.phase == TickEvent.Phase.START) worldStart = System.nanoTime();
        else if (worldStart != 0) worldElapsed = System.nanoTime() - worldStart;
    }

    @Override
    public void record(IGregTechTileEntity tile, Stage part, long nanos) {
        if (serverStart == 0 || worldStart == 0 || worldElapsed != 0) return;
        for (int i = 0; i < activeCount; i++) if (tile == observed[i]) {
            int index = part.ordinal();
            stage[index] += nanos;
            stageMax[index] = Math.max(stageMax[index], nanos);
            calls[index]++;
            if (part == Stage.PLANNING) planningByMachine[i]++;
            return;
        }
    }

    Map<String, Object> finishWindow(List<Map<String, Object>> machines) {
        int structure = Stage.STRUCTURE.ordinal(), planning = Stage.PLANNING.ordinal();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("simultaneousCenters", activeCount);
        result.put("elapsedTicks", rows.size());
        result.put("ticksByNumberOfRunningCenters", Arrays.copyOf(runningCounts, activeCount + 1));
        if (runningCounts[activeCount] == 0) throw new IllegalStateException("No tick with all loaded centers running");
        result.put("epochMillisStart", epochAtStart);
        result.put("epochMillisEnd", rows.get(rows.size() - 1).epochMillis);
        result.put("loadedLabTilesStartEnd", Arrays.asList(worldTilesAtStart, world.loadedTileEntityList.size()));
        result.put(
            "loadedLabChunksStartEnd",
            Arrays.asList(
                worldChunksAtStart,
                world.getChunkProvider()
                    .getLoadedChunkCount()));
        result.put("serverTickNanos", distribution(s -> s.serverNanos, SERVER_BUDGET_NS));
        result.put("labWorldTickNanos", distribution(s -> s.worldNanos, SERVER_BUDGET_NS));
        for (Stage part : STAGES) {
            int index = part.ordinal();
            String key = part.name()
                .toLowerCase(Locale.ROOT);
            result.put(
                key + "NanosPerTick",
                distribution(
                    s -> s.stage[index],
                    part == Stage.PLANNING ? PLANNING_BUDGET_NS : part == Stage.TOTAL ? 1_000_000 : Long.MAX_VALUE));
            result.put(
                key + "Calls",
                rows.stream()
                    .mapToLong(s -> s.calls[index])
                    .sum());
        }
        result.put(
            "planningTicksAtOrAboveHistoricalPeak",
            rows.stream()
                .filter(s -> s.stage[planning] >= PREVIOUS_PLANNER_PEAK_NS)
                .count());
        result.put("worstServerTicks", worst(s -> s.serverNanos));
        result.put("worstLabWorldTicks", worst(s -> s.worldNanos));
        result.put("worstStructureTicks", worst(s -> s.stage[structure]));
        result.put("worstPlanningTicks", worst(s -> s.stage[planning]));
        result.put("worstControllerTicks", worst(s -> s.stage[Stage.TOTAL.ordinal()]));
        for (int i = 0; i < activeCount; i++) {
            if (planningByMachine[i] == 0) throw new IllegalStateException("No planner ticks sampled for center " + i);
            machines.get(i)
                .put("planningSlices", planningByMachine[i]);
        }
        result.put("machines", machines);
        GTSteamReborn.LOG.info(
            "Ore center entity timing window closed centers={} samples={} epochMillis={}",
            activeCount,
            rows.size(),
            rows.get(rows.size() - 1).epochMillis);
        target = 0;
        return result;
    }

    private Map<String, Object> distribution(ToLongFunction<Sample> measure, long budget) {
        long[] values = rows.stream()
            .mapToLong(measure)
            .sorted()
            .toArray();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("p50", percentile(values, .50));
        detail.put("p95", percentile(values, .95));
        detail.put("p99", percentile(values, .99));
        detail.put("max", values[values.length - 1]);
        if (budget != Long.MAX_VALUE) {
            detail.put("budget", budget);
            detail.put(
                "overBudget",
                Arrays.stream(values)
                    .filter(nanos -> nanos > budget)
                    .count());
        }
        return detail;
    }

    private static long percentile(long[] sorted, double percentile) {
        return sorted[(int) Math.ceil(sorted.length * percentile) - 1];
    }

    private List<Map<String, Object>> worst(ToLongFunction<Sample> measure) {
        List<Map<String, Object>> peak = new ArrayList<>();
        rows.stream()
            .filter(s -> measure.applyAsLong(s) > 0)
            .sorted(
                Comparator.comparingLong(measure)
                    .reversed())
            .limit(8)
            .forEach(s -> peak.add(s.fields()));
        return peak;
    }

    private static final class Sample {

        final long serverNanos, worldNanos, epochMillis, worldTick;
        final int serverTick;
        final long[] stage, stageMax;
        final int[] calls;

        Sample(long serverNanos, long worldNanos, long epochMillis, long worldTick, int serverTick, long[] stage,
            long[] stageMax, int[] calls) {
            this.serverNanos = serverNanos;
            this.worldNanos = worldNanos;
            this.epochMillis = epochMillis;
            this.worldTick = worldTick;
            this.serverTick = serverTick;
            this.stage = stage;
            this.stageMax = stageMax;
            this.calls = calls;
        }

        Map<String, Object> fields() {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("epochMillisEnd", epochMillis);
            detail.put("worldTick", worldTick);
            detail.put("serverTick", serverTick);
            detail.put("serverNanos", serverNanos);
            detail.put("labWorldNanos", worldNanos);
            for (Stage part : STAGES) {
                int index = part.ordinal();
                String key = part.name()
                    .toLowerCase(Locale.ROOT);
                detail.put(key + "Nanos", stage[index]);
                detail.put(key + "LargestCallNanos", stageMax[index]);
                detail.put(key + "Calls", calls[index]);
            }
            return detail;
        }
    }
}
