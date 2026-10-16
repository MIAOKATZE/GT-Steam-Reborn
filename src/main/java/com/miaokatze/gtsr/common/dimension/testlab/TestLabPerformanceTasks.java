package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.world.WorldServer;

/** Module coverage is deliberately independent of exhaustive per-machine functional coverage. */
final class TestLabPerformanceTasks {

    private TestLabPerformanceTasks() {}

    static List<TestLabTask> select(String mode, String id) {
        boolean all = "all".equals(mode);
        List<TestLabTask> result = new ArrayList<>();
        result.add(new Window("baseline", all ? 100 : 40));
        List<TestLabTask> machines = TestLabCatalog.tasks();
        Set<String> families = new LinkedHashSet<>();
        boolean selected = false;
        for (TestLabTask task : machines) {
            boolean choose = "machine".equals(mode) ? task.id()
                .equals(id)
                : !"ui".equals(mode) && (task.id()
                    .equals("steam_input_hatch_generic")
                    || task.id()
                        .equals("air_compressor")
                    || all && !(task instanceof TestLabMachineTask)
                        && families.add(
                            task.getClass()
                                .getName()));
            if (!choose) continue;
            selected = true;
            Reset before = new Reset(task.id(), "before", all ? 300 : 400);
            result.add(before);
            result.add(new Bounded(task, before, all ? 400 : 800));
            result.add(new Reset(task.id(), "after", all ? 300 : 400));
        }
        List<TestLabTask> ui = TestLabUiProbes.tasks();
        for (TestLabTask task : ui) {
            if ("machine".equals(mode)) break;
            boolean choose = "ui".equals(mode) ? task.id()
                .equals(id)
                || task.id()
                    .equals("ui." + id)
                : all || task.id()
                    .equals("ui.steam_cache_node");
            if (choose) {
                selected = true;
                result.add(new Bounded(task, null, 100));
            }
        }
        if (!selected) throw new IllegalArgumentException("性能场景没有匹配项：" + mode + " " + id);
        result.add(new Window("cooldown", all ? 100 : 40));
        return result;
    }

    private static final class Window implements TestLabTask {

        private final String name;
        private final int ticks;

        Window(String name, int ticks) {
            this.name = name;
            this.ticks = ticks;
        }

        public String id() {
            return "perf." + name;
        }

        public String displayName() {
            return "性能背景观察窗口 " + name;
        }

        public int timeoutTicks() {
            return ticks + 20;
        }

        public boolean requiresFixtureChunks() {
            return false;
        }

        public void start(WorldServer world, int x, int y, int z) {}

        public TestLabReport.Result tick(WorldServer world, int elapsed) {
            return elapsed < ticks ? null
                : new TestLabReport.Result(TestLabReport.Status.PASS, "真实服务器tick观察窗口完成；全服背景噪声，不作为测试台严格归因或功能覆盖");
        }

        public void stop(WorldServer world) {}
    }

    private static final class Reset implements TestLabTask {

        private final String target, when;
        private final int budget;
        boolean complete;

        Reset(String target, String when, int budget) {
            this.target = target;
            this.when = when;
            this.budget = budget;
        }

        public String id() {
            return "perf.reset." + when + "." + target;
        }

        public String displayName() {
            return "渐进清场 " + when + " " + target;
        }

        public int timeoutTicks() {
            return budget + 20;
        }

        public boolean requiresFixtureChunks() {
            return false;
        }

        public String performancePhase() {
            return "reset.tick";
        }

        public void start(WorldServer world, int x, int y, int z) {
            TestLabFixtures.reset(world, TestLabRuntime.originX(target), TestLabRuntime.originZ(target));
        }

        public TestLabReport.Result tick(WorldServer world, int elapsed) {
            complete = TestLabFixtures.tickReset(world);
            if (complete) return new TestLabReport.Result(TestLabReport.Status.PASS, "所选工位渐进清场与原生逐块验证完成");
            return elapsed < budget ? null
                : new TestLabReport.Result(TestLabReport.Status.BLOCKED, "清场达到有限性能场景预算；后续物理任务禁止布置，未宣称清场完成");
        }

        public void stop(WorldServer world) {
            TestLabFixtures.cancelReset();
        }
    }

    private static final class Bounded implements TestLabTask {

        private final TestLabTask task;
        private final Reset reset;
        private final int budget;
        private boolean started;

        Bounded(TestLabTask task, Reset reset, int budget) {
            this.task = task;
            this.reset = reset;
            this.budget = budget;
        }

        public String id() {
            return task.id();
        }

        public String displayName() {
            return task.displayName() + " [bounded performance sample]";
        }

        public int timeoutTicks() {
            return budget + 20;
        }

        public boolean requiresFixtureChunks() {
            return task.requiresFixtureChunks();
        }

        public String performancePhase() {
            return task.performancePhase();
        }

        public void start(WorldServer world, int x, int y, int z) throws Exception {
            if (reset == null || reset.complete) {
                started = true;
                task.start(world, x, y, z);
            }
        }

        public TestLabReport.Result tick(WorldServer world, int elapsed) throws Exception {
            if (!started) return new TestLabReport.Result(TestLabReport.Status.BLOCKED, "前置清场未完成；禁止覆盖既有工位");
            TestLabReport.Result observed = task.tick(world, elapsed);
            if (observed != null) return observed;
            return elapsed < budget ? null
                : new TestLabReport.Result(
                    TestLabReport.Status.BLOCKED,
                    "有限性能采样窗口结束；未完成的原生工序不作为功能FAIL或PASS；" + task.debug(world));
        }

        public void stop(WorldServer world) {
            if (started) task.stop(world);
        }

        public String debug(WorldServer world) {
            return started ? task.debug(world) : "pre-reset incomplete";
        }
    }
}
