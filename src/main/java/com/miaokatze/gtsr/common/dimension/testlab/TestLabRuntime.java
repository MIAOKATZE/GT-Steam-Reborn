package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.gtsr.Tags;
import com.miaokatze.gtsr.main.GTSteamReborn;
import com.miaokatze.miaokatzecore.testkit.Deadline;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** One task at a time, advanced only by real dedicated-server ticks. No sleeping command thread. */
public final class TestLabRuntime {

    public static final TestLabRuntime INSTANCE = new TestLabRuntime();
    public static final String PREFIX = "[GTSR-TESTLAB] ";
    private final Deque<TestLabTask> pending = new ArrayDeque<>();
    private final List<ForgeChunkManager.Ticket> tickets = new ArrayList<>();
    private final Map<ChunkCoordIntPair, ForgeChunkManager.Ticket> manualSupplyTickets = new HashMap<>();
    /** Keeps the world ticking across task ticket hand-offs and bounded regeneration. */
    private ForgeChunkManager.Ticket anchorTicket;
    private TestLabTask active;
    private TestLabReport report;
    private TestLabState state;
    private WorldServer world;
    private ICommandSender listener;
    private boolean initialized;
    private boolean readinessAnnounced;
    private boolean resetting;
    private String afterReset;
    private int ticks;
    private int loadIndex;
    private int targetChunks;
    private int watchdog;
    private int originX;
    private int originZ;
    private TestLabState.PendingWrite pendingWrite;
    private int persistenceTicks;
    private int persistenceAttempts;
    private boolean persistenceError;
    private boolean activePrepared;
    private String terminalAnnouncement;
    private String deferredStopReason;
    private boolean stopping;
    private int performanceTicks;
    private int performanceTickLimit;
    private boolean handlingTick;
    private String performanceTerminal;
    /** Forge clears the global ticket registry on the first world unload during server shutdown. */
    private boolean forgeTicketsUnloaded;

    private TestLabRuntime() {}

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    public void serverStarting() {
        TestLabPerformance.current = null;
        handlingTick = false;
        performanceTerminal = null;
        TestLabFixtures.cancelReset();
        releaseTickets();
        releaseAnchor();
        clearManualSupply();
        forgeTicketsUnloaded = false;
        pending.clear();
        active = null;
        report = null;
        state = null;
        world = null;
        listener = null;
        resetting = false;
        afterReset = null;
        initialized = false;
        readinessAnnounced = false;
        pendingWrite = null;
        persistenceError = false;
        activePrepared = false;
        terminalAnnouncement = null;
        deferredStopReason = null;
        stopping = false;
    }

    private void initialize() {
        if (initialized) return;
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) throw new IllegalStateException("主世界尚未加载");
        state = new TestLabState(overworld);
        try {
            report = state.readLatest();
            if (report != null && "RUNNING".equals(report.state)) {
                for (String id : report.plannedTasks) {
                    boolean recorded = false;
                    for (TestLabReport.Entry entry : report.results) if (id.equals(entry.id)) recorded = true;
                    if (!recorded) append(
                        id,
                        id,
                        new TestLabReport.Result(TestLabReport.Status.BLOCKED, "服务器在运行期间中断；未恢复任务，必须显式重新运行"),
                        0);
                }
                report.state = "INTERRUPTED";
                report.finishedAt = System.currentTimeMillis();
                report.activeTask = null;
                persist();
            }
            initialized = true;
        } catch (Exception e) {
            GTSteamReborn.LOG.error(PREFIX + "无法读取旧报告", e);
            throw new IllegalStateException("旧报告读取失败，请检查日志", e);
        }
    }

    public void run(String requested, ICommandSender sender) {
        initialize();
        if (busy()) throw new IllegalStateException("已有任务/重生成在运行；使用 status 或 stop");
        // Physical scenarios are repeatable only after their persisted hosts/regions and blocks are retired.
        reset(requested, sender);
    }

    private void startRun(String requested, ICommandSender sender) {
        List<TestLabTask> selected = new ArrayList<>();
        for (TestLabTask task : TestLabCatalog.tasks()) if ("all".equals(requested) || task.id()
            .equals(requested)) selected.add(task);
        if (selected.isEmpty()) throw new IllegalArgumentException("未知测试项：" + requested + "；使用 list");
        begin(selected, requested, "MACHINE", sender);
    }

    public void runUi(String requested, ICommandSender sender) {
        List<TestLabTask> selected = new ArrayList<>();
        for (TestLabTask task : TestLabUiProbes.tasks()) if ("all".equals(requested) || task.id()
            .equals(requested)
            || task.id()
                .equals("ui." + requested))
            selected.add(task);
        if (selected.isEmpty()) throw new IllegalArgumentException("未知 UI 探针：" + requested + "；使用 ui list");
        begin(selected, "ui:" + requested, "UI", sender);
    }

    /** Existing-layout verification deliberately bypasses run/reset and never reconstructs its station. */
    public void runExisting(String requested, ICommandSender sender) {
        List<TestLabTask> selected = new ArrayList<>();
        for (TestLabTask task : TestLabCatalog.tasks()) {
            if (("all".equals(requested) || task.id()
                .equals(requested)) && TestLabExistingLayoutTask.supports(task))
                selected.add(new TestLabExistingLayoutTask(task));
        }
        if (selected.isEmpty())
            throw new IllegalArgumentException("Unknown non-loom existing-layout task: " + requested);
        begin(selected, "existing-layout:" + requested, "EXISTING_LAYOUT", sender);
    }

    public void runStructure(String requested, ICommandSender sender) {
        begin(TestLabStructureContractTask.select(requested), "structure:" + requested, "STRUCTURE_CONTRACT", sender);
    }

    public void runPerformance(String mode, String id, ICommandSender sender) {
        // Recover any interrupted previous report before assigning this run's instrumentation.
        initialize();
        if (busy()) throw new IllegalStateException("已有任务/重生成在运行；性能场景共用同一队列");
        if (!manualSupplyTickets.isEmpty() || world != null && TestLabSupply.feedCount(world) != 0)
            throw new IllegalStateException("存在手动/既有供液；先保存证据并 supply clear，再执行性能场景");
        List<TestLabTask> selected = TestLabPerformanceTasks.select(mode, id);
        TestLabPerformance.current = new TestLabPerformance();
        performanceTicks = 0;
        performanceTickLimit = "all".equals(mode) ? 7200 : 3600;
        TestLabPerformance.current.plan(mode, performanceTickLimit, selected);
        try {
            begin(selected, "perf:" + mode + (id == null ? "" : ":" + id), "PERFORMANCE", sender);
        } catch (RuntimeException | LinkageError failure) {
            TestLabPerformance.current = null;
            throw failure;
        }
    }

    private void begin(List<TestLabTask> selected, String requested, String category, ICommandSender sender) {
        initialize();
        if (busy()) throw new IllegalStateException("已有任务/重生成在运行；使用 status 或 stop");
        long dimensionTiming = TestLabPerformance.begin();
        try {
            world = TestLabDimension.world();
        } finally {
            TestLabPerformance.end("dimension.createOrReuse", dimensionTiming);
        }
        persistenceError = false;
        report = new TestLabReport();
        report.gtsrVersion = Tags.VERSION;
        report.gtVersion = Loader.instance()
            .getIndexedModList()
            .containsKey("gregtech")
                ? Loader.instance()
                    .getIndexedModList()
                    .get("gregtech")
                    .getVersion()
                : "unavailable";
        report.fmlVersion = Loader.instance()
            .getFMLVersionString();
        report.category = category;
        report.executionScope = "EXISTING_LAYOUT".equals(category)
            ? "EXISTING_SAVED_STATIONS_NO_REBUILD; PRE_MUTATION_STOCK_SNAPSHOT_THEN_ISOLATED_FIXTURE_RESUPPLY_AND_NATURAL_TICKS"
            : "STRUCTURE_CONTRACT".equals(category) ? "ISOLATED_STRUCTURE_CONTRACT_FIXTURES"
                : "PERFORMANCE".equals(category)
                    ? "BOUNDED_MODULE_REPRESENTATIVES_AND_SERVER_UI; NOT_EXHAUSTIVE_MACHINE_COVERAGE; BACKGROUND_NOT_STRICT_ATTRIBUTION"
                    : "UI".equals(category) ? "SERVER_UI_MODEL_AND_SYNC_PROBES; CLIENT_GL_AND_FONT_RENDER_NOT_EXECUTED"
                        : "REAL_SERVER_WORLD_TICKS";
        report.runId = System.currentTimeMillis() + "-"
            + UUID.randomUUID()
                .toString()
                .substring(0, 8);
        report.state = "RUNNING";
        report.requested = requested;
        report.dimension = TestLabDimension.id();
        report.startedAt = System.currentTimeMillis();
        if (TestLabPerformance.current != null)
            report.performanceReceiptPath = state.performanceReceiptPath(report.runId);
        for (TestLabTask task : selected) report.plannedTasks.add(task.id());
        listener = sender;
        ticks = 0;
        try {
            ensureAnchor();
            persist();
        } catch (RuntimeException failure) {
            report.state = "ERROR";
            releaseAnchor();
            throw failure;
        }
        if (persistenceError) throw new IllegalStateException("报告基础设施失败；检查日志");
        pending.addAll(selected);
        announce("runId=" + report.runId + " state=RUNNING tasks=" + selected.size() + " report=" + report.reportPath);
    }

    public boolean busy() {
        return pendingWrite != null || resetting || active != null || !pending.isEmpty();
    }

    public static int slot(String id) {
        List<TestLabTask> tasks = TestLabCatalog.tasks();
        for (int i = 0; i < tasks.size(); i++) if (tasks.get(i)
            .id()
            .equals(id)) return i;
        throw new IllegalArgumentException("未知测试项：" + id);
    }

    public static int originX(String id) {
        return 128 + slot(id) % 4 * 256;
    }

    public static int originZ(String id) {
        return slot(id) / 4 * 256;
    }

    public void retainSupply(WorldServer target, int x, int z) {
        world = target;
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(x >> 4, z >> 4);
        if (manualSupplyTickets.containsKey(chunk)) return;
        if (manualSupplyTickets.size() >= 64) throw new IllegalStateException("手动供给最多强载64区块；先 supply clear");
        Object mod = Loader.instance()
            .getIndexedModList()
            .get(GTSteamReborn.MODID)
            .getMod();
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(mod, target, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) throw new IllegalStateException("Forge 无可用强载票");
        ticket.getModData()
            .setString("OwnerType", "TestLabTransient");
        ForgeChunkManager.forceChunk(ticket, chunk);
        manualSupplyTickets.put(chunk, ticket);
    }

    public void clearManualSupply() {
        if (world != null) TestLabSupply.clear(world);
        for (ForgeChunkManager.Ticket ticket : manualSupplyTickets.values()) releaseOwnedTicket(ticket);
        manualSupplyTickets.clear();
    }

    public void pruneManualSupply() {
        if (world == null) return;
        java.util.Iterator<Map.Entry<ChunkCoordIntPair, ForgeChunkManager.Ticket>> iterator = manualSupplyTickets
            .entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkCoordIntPair, ForgeChunkManager.Ticket> entry = iterator.next();
            ChunkCoordIntPair chunk = entry.getKey();
            if (!TestLabSupply.hasFeedAtChunk(world, chunk.chunkXPos, chunk.chunkZPos)) {
                releaseOwnedTicket(entry.getValue());
                iterator.remove();
            }
        }
    }

    public String status() {
        initialize();
        if (pendingWrite != null) return "state=REPORT_RETRY attempts=" + persistenceAttempts
            + " ticks="
            + persistenceTicks
            + " runId="
            + report.runId
            + " report="
            + report.reportPath;
        if (resetting) return "state=RESETTING";
        if (report == null) return "state=IDLE";
        return "runId=" + report.runId
            + " state="
            + report.state
            + " completed="
            + report.results.size()
            + "/"
            + report.plannedTasks.size()
            + " active="
            + report.activeTask
            + " ticks="
            + ticks
            + " loadedChunks="
            + loadIndex
            + "/"
            + targetChunks
            + " report="
            + report.reportPath;
    }

    public void reset(String rerun, ICommandSender sender) {
        initialize();
        if (pendingWrite != null) throw new IllegalStateException("报告正在跨 tick 重试写入；使用 status 查询");
        if (rerun != null && !"all".equals(rerun)) {
            boolean found = false;
            for (TestLabTask task : TestLabCatalog.tasks()) if (task.id()
                .equals(rerun)) found = true;
            if (!found) throw new IllegalArgumentException("未知测试项：" + rerun);
        }
        stop("显式重生成场地");
        if (pendingWrite != null) throw new IllegalStateException("停止报告等待落盘；稍后重新执行重生成");
        persistenceError = false;
        clearManualSupply();
        world = TestLabDimension.world();
        ensureAnchor();
        try {
            if (rerun == null || "all".equals(rerun)) TestLabFixtures.reset(world);
            else TestLabFixtures.reset(world, originX(rerun), originZ(rerun));
        } catch (RuntimeException failure) {
            releaseAnchor();
            throw failure;
        }
        resetting = true;
        watchdog = 0;
        afterReset = rerun;
        listener = sender;
        announce("state=RESETTING rerun=" + rerun);
    }

    public void stop(String reason) {
        if (pendingWrite != null) {
            deferredStopReason = reason;
            return;
        }
        stopping = true;
        try {
            if (active != null) complete(new TestLabReport.Result(TestLabReport.Status.BLOCKED, reason));
        } finally {
            stopping = false;
            TestLabFixtures.cancelReset();
            while (!pending.isEmpty()) {
                TestLabTask task = pending.removeFirst();
                append(task.id(), task.displayName(), new TestLabReport.Result(TestLabReport.Status.SKIP, reason), 0);
            }
            resetting = false;
            afterReset = null;
            releaseTickets();
            try {
                if (report != null && "RUNNING".equals(report.state)) finish("STOPPED");
            } finally {
                if (pendingWrite == null) releaseAnchor();
            }
        }
    }

    @SubscribeEvent
    public void worldTick(TickEvent.WorldTickEvent event) {
        TestLabPerformance performance = TestLabPerformance.current;
        if (performance == null || event.side != cpw.mods.fml.relauncher.Side.SERVER || event.world != world) return;
        if (event.phase == TickEvent.Phase.START) performance.worldStart(performancePhase());
        else {
            boolean sample = performance.sampleWorldCounts();
            performance
                .worldEnd(sample ? world.loadedTileEntityList.size() : -1, sample ? world.loadedEntityList.size() : -1);
        }
    }

    private String performancePhase() {
        return active == null ? "queue"
            : active.id() + "." + (loadIndex < targetChunks ? "chunk.loading" : active.performancePhase());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        TestLabPerformance performance = TestLabPerformance.current;
        if (event.phase == TickEvent.Phase.START) {
            if (performance != null) performance.serverStart();
            return;
        }
        long timing = TestLabPerformance.begin();
        String eventPhase = performance == null ? null : performancePhase();
        handlingTick = true;
        try {
            if (performance != null && ++performanceTicks > performanceTickLimit && pendingWrite == null) {
                stop("性能场景达到总真实tick预算；未执行项SKIP，未完成项BLOCKED");
                return;
            }
            tickEnd();
        } finally {
            TestLabPerformance.end("runtime.endTick", timing);
            if (TestLabPerformance.current == performance && performance != null) performance.serverEnd(eventPhase);
            handlingTick = false;
            if (performanceTerminal != null) announcePerformanceTerminal();
        }
    }

    private void tickEnd() {
        if (DimensionManager.getWorld(0) == null) return;
        if (!initialized) {
            try {
                initialize();
            } catch (RuntimeException e) {
                return;
            }
        }
        if (pendingWrite != null) {
            retryPersistence();
            return;
        }
        if (deferredStopReason != null) {
            String reason = deferredStopReason;
            deferredStopReason = null;
            stop(reason);
            return;
        }
        if (!readinessAnnounced) {
            readinessAnnounced = true;
            GTSteamReborn.LOG.info(
                PREFIX + "state="
                    + (TestLabDimension.enabled() ? "READY" : "UNAVAILABLE")
                    + " dimension="
                    + TestLabDimension.id()
                    + " version="
                    + Tags.VERSION);
        }
        if (world == null) return;
        try {
            long supplyTiming = TestLabPerformance.begin();
            try {
                if (TestLabPerformance.current != null)
                    TestLabPerformance.current.supply(TestLabSupply.feedCount(world));
                TestLabSupply.tick(world);
            } finally {
                TestLabPerformance.end("supply.refill", supplyTiming);
            }
            pruneManualSupply();
            if (resetting) {
                if (++watchdog > 20000) throw new IllegalStateException("清场超时");
                if (TestLabFixtures.tickReset(world)) {
                    resetting = false;
                    String rerun = afterReset;
                    afterReset = null;
                    announce("state=RESET_COMPLETE");
                    if (rerun != null) startRun(rerun, listener);
                    else releaseAnchor();
                }
                return;
            }
            if (active == null && !pending.isEmpty()) {
                active = pending.removeFirst();
                activePrepared = false;
                ticks = 0;
                loadIndex = 0;
                targetChunks = active.requiresFixtureChunks() ? 169 : 0;
                originX = active.requiresFixtureChunks() ? originX(active.id()) : 0;
                originZ = active.requiresFixtureChunks() ? originZ(active.id()) : 0;
                report.activeTask = active.id();
                report.activeTicks = 0;
                report.activeDebug = null;
                if (!persist()) return;
            }
            if (active != null && !activePrepared) {
                activePrepared = true;
                if (targetChunks == 0) {
                    startActive(0, 0);
                    announce("task=" + active.id() + " phase=RUNNING category=" + report.category + " fixtureChunks=0");
                    return;
                }
                if (!requestTickets()) {
                    complete(
                        new TestLabReport.Result(
                            TestLabReport.Status.BLOCKED,
                            "Forge 强制区块加载票不足（需要169区块，票数按实际每票配额分配）；检查 forgeChunkLoading.cfg"));
                    return;
                }
                announce("task=" + active.id() + " phase=LOADING origin=" + originX + ",65," + originZ);
            }
            if (active == null) return;
            if (loadIndex < targetChunks) {
                // Bound both work and wall time, without a fixed 169-tick tax on every small fixture.
                Deadline deadline = Deadline.afterNanos(5_000_000L);
                for (int burst = 0; loadIndex < targetChunks && burst < 16
                    && (burst == 0 || deadline.hasTimeRemaining()); burst++) {
                    int i = loadIndex++;
                    ChunkCoordIntPair chunk = new ChunkCoordIntPair((originX >> 4) + i % 13, (originZ >> 4) + i / 13);
                    int offset = i;
                    ForgeChunkManager.Ticket ticket = null;
                    for (ForgeChunkManager.Ticket candidate : tickets) {
                        if (offset < ticketCapacity(candidate)) {
                            ticket = candidate;
                            break;
                        }
                        offset -= ticketCapacity(candidate);
                    }
                    if (ticket == null) throw new IllegalStateException("强载区块配额不足");
                    ForgeChunkManager.forceChunk(ticket, chunk);
                    if (!ticket.getChunkList()
                        .contains(chunk)) throw new IllegalStateException("强载区块配额不足");
                    long chunkTiming = TestLabPerformance.begin();
                    try {
                        TestLabFixtures.requireChunk(world, chunk.chunkXPos, chunk.chunkZPos);
                    } finally {
                        TestLabPerformance.end("chunk.require", chunkTiming);
                    }
                }
                if (loadIndex == targetChunks) {
                    startActive(originX, originZ);
                    announce("task=" + active.id() + " phase=RUNNING");
                }
                return;
            }
            ticks++;
            report.activeTicks = ticks;
            if (ticks > Math.min(72000, Math.max(20, active.timeoutTicks()))) {
                complete(new TestLabReport.Result(TestLabReport.Status.FAIL, "任务超过有限 tick 预算；" + active.debug(world)));
            } else {
                long taskTiming = TestLabPerformance.begin();
                String phase = TestLabPerformance.current == null ? null : active.performancePhase();
                TestLabReport.Result result;
                try {
                    result = active.tick(world, ticks);
                } finally {
                    if (taskTiming != 0) TestLabPerformance.end("direct." + phase, taskTiming);
                }
                if (result != null) complete(result);
            }
            if (active != null && ticks % 200 == 0) {
                try {
                    String snapshot = active.debug(world);
                    report.activeDebug = snapshot != null && snapshot.length() > 32768
                        ? snapshot.substring(0, 32768) + "... (snapshot truncated)"
                        : snapshot;
                } catch (Exception | LinkageError diagnosticFailure) {
                    report.activeDebug = "diagnostic unavailable: " + diagnosticFailure;
                }
                persist();
                announce("task=" + active.id() + " phase=RUNNING ticks=" + ticks);
            }
        } catch (Exception | LinkageError e) {
            GTSteamReborn.LOG.error(PREFIX + "任务异常", e);
            try {
                if (active != null) {
                    String debug;
                    try {
                        debug = active.debug(world);
                    } catch (Exception | LinkageError diagnosticFailure) {
                        debug = "diagnostic unavailable: " + diagnosticFailure;
                    }
                    complete(
                        new TestLabReport.Result(
                            TestLabReport.Status.FAIL,
                            e.getClass()
                                .getName() + ": "
                                + e.getMessage()
                                + "; "
                                + debug));
                }
                if (resetting) {
                    TestLabFixtures.cancelReset();
                    resetting = false;
                    afterReset = null;
                    announce("state=RESET_FAILED detail=" + e.getMessage());
                    releaseAnchor();
                }
            } catch (Exception | LinkageError infrastructureFailure) {
                // A disk/report failure must neither crash the server nor continue unreported work.
                GTSteamReborn.LOG.error(PREFIX + "基础设施失败，终止本轮测试", infrastructureFailure);
                if (active != null) {
                    try {
                        active.stop(world);
                    } catch (Exception | LinkageError ignored) {}
                }
                active = null;
                pending.clear();
                TestLabFixtures.cancelReset();
                resetting = false;
                afterReset = null;
                releaseTickets();
                releaseAnchor();
                if (report != null) report.state = "ERROR";
                if (TestLabPerformance.current != null) performanceTerminal = "state=ERROR cause=INFRASTRUCTURE";
                announce("state=ERROR detail=" + infrastructureFailure.getMessage());
            }
        } finally {
            // Includes early return after ticket denial and start/tick exceptions.
            settleRun();
        }
    }

    private void startActive(int x, int z) throws Exception {
        long timing = TestLabPerformance.begin();
        try {
            active.start(world, x, 65, z);
        } finally {
            TestLabPerformance.end(
                active.id()
                    .startsWith("ui.") ? "ui.start" : "task.fixtureStart",
                timing);
        }
    }

    private void settleRun() {
        if (pendingWrite == null && !persistenceError
            && !resetting
            && active == null
            && pending.isEmpty()
            && report != null
            && "RUNNING".equals(report.state)) {
            try {
                finish("COMPLETE");
            } catch (RuntimeException | LinkageError failure) {
                report.state = "ERROR";
                releaseAnchor();
                if (TestLabPerformance.current != null) performanceTerminal = "state=ERROR cause=FINAL_REPORT";
                GTSteamReborn.LOG.error(PREFIX + "最终报告写入失败", failure);
            }
        }
    }

    private void ensureAnchor() {
        long timing = TestLabPerformance.begin();
        try {
            ensureAnchorInternal();
        } finally {
            TestLabPerformance.end("chunk.anchorAcquire", timing);
        }
    }

    private void ensureAnchorInternal() {
        if (anchorTicket != null) return;
        Object mod = Loader.instance()
            .getIndexedModList()
            .get(GTSteamReborn.MODID)
            .getMod();
        anchorTicket = ForgeChunkManager.requestTicket(mod, world, ForgeChunkManager.Type.NORMAL);
        if (anchorTicket == null) throw new IllegalStateException("Forge 无可用测试维度锚点票");
        anchorTicket.getModData()
            .setString("OwnerType", "TestLabTransient");
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(0, 0);
        ForgeChunkManager.forceChunk(anchorTicket, chunk);
        if (!anchorTicket.getChunkList()
            .contains(chunk)) {
            releaseAnchor();
            throw new IllegalStateException("Forge 拒绝测试维度锚点强载");
        }
        try {
            TestLabFixtures.requireChunk(world, 0, 0);
        } catch (RuntimeException failure) {
            releaseAnchor();
            throw failure;
        }
    }

    private void releaseAnchor() {
        long timing = TestLabPerformance.begin();
        try {
            releaseOwnedTicket(anchorTicket);
            anchorTicket = null;
        } finally {
            TestLabPerformance.end("cleanup.anchorRelease", timing);
        }
    }

    private boolean requestTickets() {
        long timing = TestLabPerformance.begin();
        try {
            return requestTicketsInternal();
        } finally {
            TestLabPerformance.end("chunk.taskTicketAcquire", timing);
        }
    }

    private boolean requestTicketsInternal() {
        Object mod = Loader.instance()
            .getIndexedModList()
            .get(GTSteamReborn.MODID)
            .getMod();
        int capacity = 0;
        while (capacity < 169) {
            ForgeChunkManager.Ticket ticket = ForgeChunkManager
                .requestTicket(mod, world, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) {
                releaseTickets();
                return false;
            }
            ticket.getModData()
                .setString("OwnerType", "TestLabTransient");
            // Existing Critical callback rejects these after restart: tasks never silently resume.
            tickets.add(ticket);
            capacity += ticketCapacity(ticket);
        }
        return true;
    }

    private static int ticketCapacity(ForgeChunkManager.Ticket ticket) {
        // Forge's zero depth means unlimited, not zero allowed chunks.
        return ticket.getChunkListDepth() <= 0 ? 169 : ticket.getChunkListDepth();
    }

    private void complete(TestLabReport.Result result) {
        TestLabTask task = active;
        if (task == null) return;
        long cleanupTiming = TestLabPerformance.begin();
        try {
            task.stop(world);
        } catch (Exception | LinkageError e) {
            result = new TestLabReport.Result(
                TestLabReport.Status.FAIL,
                result.detail + "; cleanup=" + e,
                result.details);
        } finally {
            TestLabPerformance.end("cleanup.taskStop", cleanupTiming);
        }
        append(task.id(), task.displayName(), result, ticks);
        String brief = result.detail.replace('\n', ' ')
            .replace('\r', ' ');
        if (brief.length() > 280) brief = brief.substring(0, 280) + "... (完整证据见JSON)";
        announce("task=" + task.id() + " result=" + result.status + " ticks=" + ticks + " detail=" + brief);
        active = null;
        report.activeTask = null;
        report.activeDebug = null;
        releaseTickets();
        if (!stopping) persist();
    }

    private void append(String id, String name, TestLabReport.Result result, int elapsed) {
        TestLabReport.Entry entry = new TestLabReport.Entry();
        entry.id = id;
        entry.name = name;
        entry.status = result.status;
        entry.detail = result.detail;
        entry.category = "EXISTING_LAYOUT".equals(report.category) ? "EXISTING_LAYOUT"
            : "STRUCTURE_CONTRACT".equals(report.category) ? "STRUCTURE_CONTRACT"
                : id.startsWith("perf.") ? "PERFORMANCE" : id.startsWith("ui.") ? "UI" : "MACHINE";
        entry.details = new java.util.LinkedHashMap<>(result.details);
        if ("UI".equals(entry.category)) {
            entry.details.put("clientGlExecuted", false);
            entry.details.put("fontRenderingExecuted", false);
        }
        entry.elapsedTicks = elapsed;
        entry.originY = 65;
        try {
            if (!"UI".equals(entry.category)) {
                entry.originX = originX(id);
                entry.originZ = originZ(id);
            }
        } catch (IllegalArgumentException ignored) {
            // A report from an older mod version may mention a retired task ID.
        }
        report.results.add(entry);
    }

    private void finish(String terminalState) {
        report.state = terminalState;
        report.finishedAt = System.currentTimeMillis();
        report.activeTask = null;
        terminalAnnouncement = "runId=" + report.runId + " state=" + terminalState + " report=" + report.reportPath;
        if (persist()) announceTerminal();
    }

    private void releaseTickets() {
        long timing = TestLabPerformance.begin();
        try {
            for (ForgeChunkManager.Ticket ticket : tickets) releaseOwnedTicket(ticket);
            tickets.clear();
        } finally {
            TestLabPerformance.end("cleanup.taskTicketRelease", timing);
        }
    }

    static void releaseOwnedTicket(ForgeChunkManager.Ticket ticket) {
        // Do not infer ticket validity from forced chunks: a live ticket can have no chunks yet.
        // Before Forge's shutdown teardown, release failures must still propagate to the caller.
        if (!INSTANCE.forgeTicketsUnloaded) ForgeChunkManager.releaseTicket(ticket);
    }

    private boolean persist() {
        if (pendingWrite != null) return false;
        try {
            if (TestLabPerformance.current != null) report.performance = TestLabPerformance.current.snapshot(false);
            pendingWrite = state.prepare(report);
            persistenceTicks = 0;
            persistenceAttempts = 1;
            state.save(pendingWrite);
            pendingWrite = null;
            return true;
        } catch (IOException failure) {
            GTSteamReborn.LOG.warn(PREFIX + "state=REPORT_RETRY runId=" + report.runId + " detail=" + failure);
            return false;
        } catch (Exception | LinkageError e) {
            failPersistence(e);
            return false;
        }
    }

    private void retryPersistence() {
        // No sleeps or loops: at most one disk attempt every five real server END ticks, for 200 ticks.
        persistenceTicks++;
        if (persistenceTicks % 5 != 0) return;
        try {
            persistenceAttempts++;
            state.save(pendingWrite);
            pendingWrite = null;
            announce("state=REPORT_RECOVERED runId=" + report.runId + " attempts=" + persistenceAttempts);
            if (terminalAnnouncement != null) announceTerminal();
        } catch (IOException failure) {
            if (persistenceTicks >= 200) failPersistence(failure);
        } catch (Exception | LinkageError failure) {
            failPersistence(failure);
        }
    }

    private void announceTerminal() {
        String message = terminalAnnouncement;
        terminalAnnouncement = null;
        releaseAnchor();
        if (TestLabPerformance.current == null) announce(message);
        else {
            performanceTerminal = message;
            if (!handlingTick) announcePerformanceTerminal();
        }
    }

    private void announcePerformanceTerminal() {
        String message = performanceTerminal;
        performanceTerminal = null;
        if (finishPerformance()) announce(message);
        else {
            report.state = "ERROR";
            // Finite best effort error correction, without recursive measurement/receipt writes.
            persist();
            announce("runId=" + report.runId + " state=ERROR cause=PERFORMANCE_RECEIPT report=" + report.reportPath);
        }
    }

    private boolean finishPerformance() {
        TestLabPerformance performance = TestLabPerformance.current;
        if (performance == null) return true;
        try {
            boolean uiPlanned = false;
            for (String id : report.plannedTasks) if (id.startsWith("ui.")) uiPlanned = true;
            Boolean uiEmpty = !uiPlanned ? null
                : world != null && world.isAirBlock(0, 65, 0) && world.getTileEntity(0, 65, 0) == null;
            performance.cleanup(
                tickets.size(),
                manualSupplyTickets.size(),
                anchorTicket != null,
                world == null ? 0 : TestLabSupply.feedCount(world),
                TestLabFixtures.hasResetTicket(),
                uiEmpty);
            report.performance = performance.snapshot(true);
            state.savePerformance(report, report.performance);
            announce("performanceReceipt=" + report.performanceReceiptPath);
            return true;
        } catch (Exception | LinkageError failure) {
            GTSteamReborn.LOG.error(PREFIX + "最终性能收据写入失败（主报告仍保留先前快照）", failure);
            announce("performanceReceipt=ERROR detail=" + failure.getMessage());
            if (report.performance != null) report.performance.put("receiptError", failure.toString());
            return false;
        } finally {
            TestLabPerformance.current = null;
        }
    }

    private void failPersistence(Throwable failure) {
        // This is an infrastructure failure, never a synthetic machine FAIL or a second result append.
        GTSteamReborn.LOG.error(PREFIX + "报告基础设施失败，停止本轮测试", failure);
        pendingWrite = null;
        persistenceError = true;
        terminalAnnouncement = null;
        deferredStopReason = null;
        if (active != null) {
            try {
                active.stop(world);
            } catch (Exception | LinkageError cleanupFailure) {
                GTSteamReborn.LOG.error(PREFIX + "报告失败后清理异常", cleanupFailure);
            }
        }
        active = null;
        pending.clear();
        TestLabFixtures.cancelReset();
        resetting = false;
        afterReset = null;
        releaseTickets();
        releaseAnchor();
        if (report != null) {
            report.state = "ERROR";
            report.finishedAt = System.currentTimeMillis();
            report.activeTask = null;
            try {
                // Best effort terminal receipt; a still-locked latest.json must not prevent the per-run ERROR file.
                state.save(state.prepare(report));
            } catch (Exception | LinkageError receiptFailure) {
                GTSteamReborn.LOG.error(PREFIX + "基础设施 ERROR 报告仍无法落盘", receiptFailure);
            }
            if (TestLabPerformance.current != null) {
                performanceTerminal = "runId=" + report.runId
                    + " state=ERROR cause=REPORT_IO report="
                    + report.reportPath;
                if (!handlingTick) announcePerformanceTerminal();
            }
            announce(
                "runId=" + report.runId
                    + " state=ERROR cause=REPORT_IO attempts="
                    + persistenceAttempts
                    + " report="
                    + report.reportPath
                    + " detail="
                    + failure);
        }
    }

    private void announce(String text) {
        GTSteamReborn.LOG.info(PREFIX + text);
        if (listener != null) listener.addChatMessage(new ChatComponentText(PREFIX + text));
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        // ForgeInternalHandler runs at HIGHEST and clears *all* world tickets when the server stops.
        // Observe every server world, including when this dimension is the first world to unload.
        if (!event.world.isRemote && !MinecraftServer.getServer()
            .isServerRunning()) forgeTicketsUnloaded = true;
        if (event.world == world && world != null) {
            try {
                stop("测试维度卸载/服务器关闭");
            } finally {
                TestLabFixtures.cancelReset();
                releaseTickets();
                releaseAnchor();
                clearManualSupply();
                world = null;
            }
        }
    }
}
