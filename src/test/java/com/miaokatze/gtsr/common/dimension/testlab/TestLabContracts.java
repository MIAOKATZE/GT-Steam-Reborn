package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Executable contracts: no server, client, GL context or world is created. */
public final class TestLabContracts {

    public static void main(String[] args) throws Exception {
        bootstrapWithoutImplementation();
        catalogSelection();
        worldHooks();
        persistenceRetry();
        terminalSnapshots();
        requestCorrelation();
        if (args.length > 0) exportDeclaredCases(java.nio.file.Paths.get(args[0]));
        System.out.println(
            "GTSR TestLab contracts PASS: disabled linkage, explicit missing support, 104/95/119 catalog, performance selection, independent report retry, request correlation");
    }

    private static void bootstrapWithoutImplementation() throws Exception {
        final String bootstrap = "com.miaokatze.gtsr.common.testlab.TestLabBootstrap";
        final int[] resolutions = { 0 };
        ClassLoader isolated = new ClassLoader(TestLabContracts.class.getClassLoader()) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.miaokatze.gtsr.common.dimension.testlab.")) {
                    resolutions[0]++;
                    throw new ClassNotFoundException("deliberately absent testlab implementation");
                }
                if (!name.equals(bootstrap)) return super.loadClass(name, resolve);
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                        byte[] value = bytes.toByteArray();
                        found = defineClass(name, value, 0, value.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(found);
                return found;
            }
        };
        Class<?> type = isolated.loadClass(bootstrap);
        type.getMethod("preInit", boolean.class)
            .invoke(null, false);
        require(resolutions[0] == 0, "disabled bootstrap resolved testlab");
        try {
            type.getMethod("preInit", boolean.class)
                .invoke(null, true);
            throw new AssertionError("explicit enable accepted absent support");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() instanceof IllegalStateException, "missing support error type");
            require(
                expected.getCause()
                    .getMessage()
                    .contains("-testlab"),
                "missing support actionable error");
        }
        require(resolutions[0] == 1, "only explicit enable should resolve support");
    }

    private static void catalogSelection() {
        List<TestLabTask> tasks = TestLabCatalog.tasks();
        require(tasks.size() == 104, "machine catalog coverage changed: " + tasks.size());
        int defaults = 0, representatives = 0;
        for (TestLabTask task : tasks) {
            if (TestLabMetadata.defaultIncluded(task)) defaults++;
            if (TestLabMetadata.performanceRepresentative(task)) {
                representatives++;
                require(
                    !TestLabMetadata.describe(task)
                        .tags()
                        .contains("followup"),
                    "performance includes followup");
                require(
                    !TestLabMetadata.describe(task)
                        .tags()
                        .contains("natural"),
                    "performance includes natural followup");
            }
            if (task instanceof TestLabAmmoniaNaturalTask) {
                require(
                    TestLabMetadata.describe(task)
                        .tags()
                        .contains("natural"),
                    "natural task metadata absent");
                require(!TestLabMetadata.defaultIncluded(task), "natural followup selected implicitly");
            }
        }
        require(defaults == 95, "default machine suite changed: " + defaults);
        require(representatives == 6, "explicit performance representatives changed: " + representatives);
        require(
            TestLabUiProbes.tasks()
                .size() == 119,
            "UI catalog lost cases");
        List<TestLabTask> performance = TestLabPerformanceTasks.select("all", null);
        require(performance.size() == 139, "performance plan changed: " + performance.size());
        for (TestLabTask task : performance) require(
            !task.id()
                .contains("followup_"),
            "implicit performance followup");
    }

    private static void worldHooks() {
        TestLabWorldProvider provider = new TestLabWorldProvider();
        require(!provider.allowsOwnedGeneration(), "lab allows owned world generation");
        require(!provider.canDoRainSnowIce(null), "lab allows rain/snow/ice");
        require(!provider.canDoLightning(null), "lab allows lightning");
        provider.updateWeather(); // Deliberately no world: must not dereference shared WorldInfo.
        TestLabChunkProvider chunks = new TestLabChunkProvider(null, 1L);
        for (net.minecraft.entity.EnumCreatureType type : net.minecraft.entity.EnumCreatureType.values()) require(
            chunks.getPossibleCreatures(type, 0, 65, 0)
                .isEmpty(),
            "lab has natural creature table");
    }

    private static void persistenceRetry() throws Exception {
        Path directory = Files.createTempDirectory("gtsr-testlab-contract-");
        try {
            TestLabState state = new TestLabState(directory.toFile());
            TestLabReport report = new TestLabReport();
            report.runId = "independent-targets";
            report.state = "STOPPED";
            report.plannedTasks.add("cancelled-task");
            TestLabReport.Entry entry = new TestLabReport.Entry();
            entry.id = "cancelled-task";
            entry.status = TestLabReport.Status.BLOCKED;
            report.results.add(entry);
            Files.createDirectory(directory.resolve("latest.json"));
            Files.write(directory.resolve("latest.json/blocked"), new byte[] { 1 });
            TestLabState.PendingWrite pending = state.prepare(report);
            try {
                state.save(pending);
                throw new AssertionError("nonempty directory accepted as report file");
            } catch (IOException expected) {
                require(pending.reportSaved && !pending.latestSaved, "per-target progress lost");
                require(
                    Files.isRegularFile(directory.resolve(report.runId + ".json")),
                    "run report lost with latest failure");
            }
            byte[] committed = Files.readAllBytes(directory.resolve(report.runId + ".json"));
            Files.delete(directory.resolve("latest.json/blocked"));
            Files.delete(directory.resolve("latest.json"));
            state.save(pending);
            require(pending.reportSaved && pending.latestSaved, "retry did not complete both targets");
            require(
                java.util.Arrays.equals(committed, Files.readAllBytes(directory.resolve(report.runId + ".json"))),
                "retry rewrote committed per-run payload");
            TestLabReport read = state.readLatest();
            require(read.results.size() == 1, "retry duplicated cancelled result");
            require("STOPPED".equals(read.state), "retry lost terminal cancellation state");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(directory)) {
                for (Path file : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator)
                    Files.delete(file);
            }
        }
    }

    private static void terminalSnapshots() throws Exception {
        TestLabRuntime runtime = TestLabRuntime.INSTANCE;
        java.lang.reflect.Field reportField = TestLabRuntime.class.getDeclaredField("report");
        java.lang.reflect.Field resettingField = TestLabRuntime.class.getDeclaredField("resetting");
        java.lang.reflect.Field pendingField = TestLabRuntime.class.getDeclaredField("pendingWrite");
        reportField.setAccessible(true);
        resettingField.setAccessible(true);
        pendingField.setAccessible(true);
        Object original = reportField.get(runtime);
        try {
            TestLabReport report = new TestLabReport();
            TestLabReport.Entry skipped = new TestLabReport.Entry();
            skipped.id = "not-executed";
            skipped.status = TestLabReport.Status.SKIP;
            report.results.add(skipped);
            reportField.set(runtime, report);
            report.runId = "snapshot-contract";
            report.state = "ERROR";
            assertRawOutcome(runtime, report, "ERROR");
            require(
                "ERROR".equals(
                    runtime.snapshot()
                        .get("outcome")),
                "SKIP masked report infrastructure error");
            report.state = "STOPPED";
            assertRawOutcome(runtime, report, "CANCELLED");
            require(
                "CANCELLED".equals(
                    runtime.snapshot()
                        .get("outcome")),
                "SKIP masked cancellation");
            report.state = "COMPLETE";
            assertRawOutcome(runtime, report, "INCOMPLETE");
            skipped.status = TestLabReport.Status.FAIL;
            assertRawOutcome(runtime, report, "FAIL");
            skipped.status = TestLabReport.Status.SKIP;
            require(
                "INCOMPLETE".equals(
                    runtime.snapshot()
                        .get("outcome")),
                "COMPLETE with SKIP claimed PASS");
            resettingField.set(runtime, true);
            require(
                "IN_PROGRESS".equals(
                    runtime.snapshot()
                        .get("outcome")),
                "old terminal report masked active reset");
            resettingField.set(runtime, false);
            pendingField.set(runtime, new TestLabState.PendingWrite(null, null, ""));
            require(
                "REPORT_RETRY".equals(
                    runtime.snapshot()
                        .get("state")),
                "report retry missing from snapshot");
            require(
                Boolean.FALSE.equals(
                    runtime.snapshot()
                        .get("reportWritten")),
                "uncommitted report claimed written");
        } finally {
            pendingField.set(runtime, null);
            resettingField.set(runtime, false);
            reportField.set(runtime, original);
        }
    }

    private static void requestCorrelation() throws Exception {
        TestLabRuntime runtime = TestLabRuntime.INSTANCE;
        java.lang.reflect.Field reportField = TestLabRuntime.class.getDeclaredField("report");
        java.lang.reflect.Field resetting = TestLabRuntime.class.getDeclaredField("resetting");
        java.lang.reflect.Field rerun = TestLabRuntime.class.getDeclaredField("afterReset");
        java.lang.reflect.Field deferred = TestLabRuntime.class.getDeclaredField("afterResetRequestId");
        java.lang.reflect.Field command = TestLabRuntime.class.getDeclaredField("commandRequestId");
        java.lang.reflect.Field initialized = TestLabRuntime.class.getDeclaredField("initialized");
        java.lang.reflect.Field stateField = TestLabRuntime.class.getDeclaredField("state");
        java.lang.reflect.Method newReport = TestLabRuntime.class.getDeclaredMethod("newReport");
        java.lang.reflect.Method clear = TestLabRuntime.class.getDeclaredMethod("clearAfterReset");
        java.lang.reflect.Field[] fields = { reportField, resetting, rerun, deferred, command, initialized,
            stateField };
        Object[] originals = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i].setAccessible(true);
            originals[i] = fields[i].get(runtime);
        }
        newReport.setAccessible(true);
        clear.setAccessible(true);
        String oldRequest = "11111111-1111-4111-8111-111111111111";
        String request = "22222222-2222-4222-8222-222222222222";
        String pollRequest = "33333333-3333-4333-8333-333333333333";
        net.minecraft.command.ICommandSender sender = (net.minecraft.command.ICommandSender) java.lang.reflect.Proxy
            .newProxyInstance(
                TestLabContracts.class.getClassLoader(),
                new Class<?>[] { net.minecraft.command.ICommandSender.class },
                (proxy, method, args) -> {
                    if ("canCommandSenderUseCommand".equals(method.getName())) return true;
                    if ("addChatMessage".equals(method.getName())) return null;
                    throw new AssertionError("Unexpected sender operation: " + method.getName());
                });
        Path stopDirectory = Files.createTempDirectory("gtsr-stop-request-contract-");
        try {
            initialized.set(runtime, true);
            TestLabReport old = new TestLabReport();
            old.state = "COMPLETE";
            old.runId = "previous-business-run";
            old.requestId = oldRequest;
            old.reportPath = "previous-report.json";
            reportField.set(runtime, old);
            resetting.set(runtime, true);
            rerun.set(runtime, "steam_cache_node");
            deferred.set(runtime, request);
            java.util.Map<String, Object> during = runtime.snapshot();
            require(request.equals(during.get("requestId")), "reset lost the new request correlation");
            require(
                during.get("runId") == null && during.get("reportPath") == null,
                "new reset request inherited previous business identity");
            require(Boolean.FALSE.equals(during.get("reportWritten")), "reset inherited old report completion");
            new TestLabSupport().execute(sender, new String[] { "report" }, pollRequest);
            new TestLabSupport().execute(sender, new String[] { "status" }, pollRequest);
            require(request.equals(deferred.get(runtime)), "poll request overwrote deferred run request");
            require(oldRequest.equals(old.requestId), "poll rewrote previous report request identity");
            require(command.get(runtime) == null, "report/status poll leaked its command request");
            String carried = (String) deferred.get(runtime);
            clear.invoke(runtime);
            resetting.set(runtime, false);
            TestLabReport[] started = { null };
            runtime.withRequest(carried, () -> {
                try {
                    started[0] = (TestLabReport) newReport.invoke(runtime);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            });
            require(deferred.get(runtime) == null && rerun.get(runtime) == null, "reset correlation was not consumed");
            require(command.get(runtime) == null, "request leaked outside command scope");
            require(request.equals(started[0].requestId), "asynchronous run lost its initiating request");
            started[0].state = "COMPLETE";
            started[0].runId = "next-business-run";
            reportField.set(runtime, started[0]);
            new TestLabSupport().execute(sender, new String[] { "report" }, pollRequest);
            require(
                request.equals(
                    runtime.snapshot()
                        .get("requestId")),
                "business snapshot lost its request");
            TestLabState state = new TestLabState(
                new java.io.File(System.getProperty("java.io.tmpdir"), "gtsr-request-contract-no-write"));
            TestLabReport raw = new com.google.gson.Gson()
                .fromJson(state.prepare(started[0]).json, TestLabReport.class);
            require(request.equals(raw.requestId), "raw report lost initiating request");
            require(((TestLabReport) newReport.invoke(runtime)).requestId == null, "legacy run reused previous UUID");
            try {
                runtime.withRequest(request, () -> { throw new IllegalStateException("request-failure-fixture"); });
            } catch (IllegalStateException expected) {
                require(command.get(runtime) == null, "failed request leaked UUID");
            }
            try {
                new TestLabSupport().execute(sender, new String[] { "unknown-action" }, request);
                throw new AssertionError("unsupported command accepted");
            } catch (IllegalArgumentException expected) {
                require(command.get(runtime) == null, "canonical three-argument execute leaked failed request");
            }
            resetting.set(runtime, true);
            rerun.set(runtime, "steam_cache_node");
            deferred.set(runtime, oldRequest);
            started[0].state = "RUNNING";
            TestLabState stoppingState = new TestLabState(stopDirectory.toFile());
            stateField.set(runtime, stoppingState);
            new TestLabSupport().execute(sender, new String[] { "stop" }, pollRequest);
            TestLabReport cancelled = stoppingState.readLatest();
            require(
                "STOPPED".equals(cancelled.state) && "CANCELLED".equals(cancelled.outcome),
                "stop did not persist cancellation outcome");
            require(request.equals(cancelled.requestId), "stop report attributed to polling/stopping request");
            require(
                deferred.get(runtime) == null && rerun.get(runtime) == null,
                "stop retained a cancelled reset UUID");
            require(request.equals(started[0].requestId), "stop rewrote the original report request identity");
            require(((TestLabReport) newReport.invoke(runtime)).requestId == null, "run after stop reused UUID");
        } finally {
            for (int i = 0; i < fields.length; i++) fields[i].set(runtime, originals[i]);
            try (java.util.stream.Stream<Path> files = Files.walk(stopDirectory)) {
                for (Path file : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator)
                    Files.delete(file);
            }
        }
    }

    private static void assertRawOutcome(TestLabRuntime runtime, TestLabReport report, String expected) {
        TestLabState state = new TestLabState(
            new java.io.File(System.getProperty("java.io.tmpdir"), "gtsr-snapshot-contract-no-write"));
        String lifecycle = report.state;
        TestLabReport serialized = new com.google.gson.Gson().fromJson(state.prepare(report).json, TestLabReport.class);
        require(expected.equals(serialized.outcome), "raw report outcome differs: " + serialized.outcome);
        require(
            expected.equals(
                runtime.snapshot()
                    .get("outcome")),
            "snapshot/raw business outcome differs");
        require(lifecycle.equals(serialized.state), "legacy lifecycle state changed during serialization");
    }

    private static void exportDeclaredCases(Path destination) throws IOException {
        java.util.Map<String, Object> declared = new java.util.LinkedHashMap<>();
        declared.put("module", "gtsr");
        declared.put("evidenceKind", "declared");
        declared.put("runtimeValidated", false);
        declared.put("runtimeAuthority", "/miaotest gtsr list");
        declared.put(
            "limitations",
            java.util.Arrays.asList(
                "JVM declaration export only; no Minecraft server, client, actor or world was started",
                "Dynamic structure and recovery entries require actually registered GregTech prototypes; missing prototypes are not fabricated",
                "Catalog presence does not establish machine construction, production, UI GL or optional-mod support"));
        declared.put("cases", new TestLabSupport().cases());
        Files.createDirectories(
            destination.toAbsolutePath()
                .getParent());
        try (java.io.Writer writer = Files.newBufferedWriter(destination, java.nio.charset.StandardCharsets.UTF_8)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(declared, writer);
        }
    }

    private static void require(boolean accepted, String message) {
        if (!accepted) throw new AssertionError(message);
    }
}
