package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import net.minecraft.world.WorldServer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Reports live outside tile NBT; interrupted sessions are observations, never resumed successes. */
public final class TestLabState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    private final File directory;

    public TestLabState(WorldServer overworld) {
        this(
            new File(
                overworld.getSaveHandler()
                    .getWorldDirectory(),
                "gtsr-testlab"));
    }

    TestLabState(File directory) {
        this.directory = directory;
    }

    public File reportFile(String runId) {
        return new File(directory, runId + ".json");
    }

    public TestLabReport readLatest() throws IOException {
        File file = new File(directory, "latest.json");
        if (!file.isFile()) return null;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, TestLabReport.class);
        }
    }

    static java.util.Map<String, Object> machineBaseline(WorldServer world, String id) throws IOException {
        TestLabState state = new TestLabState(world);
        File[] files = state.directory.listFiles(
            (dir, name) -> name.endsWith(".json") && !name.equals("latest.json")
                && !name.endsWith("-performance.json"));
        TestLabReport latest = null;
        TestLabReport.Entry found = null;
        if (files != null) for (File file : files) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                TestLabReport candidate = GSON.fromJson(reader, TestLabReport.class);
                if (candidate == null || !"MACHINE".equals(candidate.category)
                    || latest != null && candidate.startedAt <= latest.startedAt) continue;
                for (TestLabReport.Entry entry : candidate.results) if (id.equals(entry.id)) {
                    latest = candidate;
                    found = entry;
                    break;
                }
            }
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("found", found != null);
        if (found != null) {
            result.put("runId", latest.runId);
            result.put("version", latest.gtsrVersion);
            result.put("reportPath", latest.reportPath);
            result.put("status", found.status.name());
            result.put("detail", found.detail);
            result.put("details", found.details);
            result.put("originX", found.originX);
            result.put("originY", found.originY);
            result.put("originZ", found.originZ);
        }
        return result;
    }

    /** Immutable payload and per-target progress: retry never re-appends results or rewrites a committed target. */
    static final class PendingWrite {

        final File reportFile;
        final File latestFile;
        final String json;
        boolean reportSaved;
        boolean latestSaved;

        PendingWrite(File reportFile, File latestFile, String json) {
            this.reportFile = reportFile;
            this.latestFile = latestFile;
            this.json = json;
        }
    }

    PendingWrite prepare(TestLabReport report) {
        long timing = TestLabPerformance.begin();
        try {
            report.reportPath = reportFile(report.runId).getAbsolutePath();
            return new PendingWrite(reportFile(report.runId), new File(directory, "latest.json"), GSON.toJson(report));
        } finally {
            TestLabPerformance.end("report.serialize", timing);
        }
    }

    String performanceReceiptPath(String runId) {
        return new File(directory, runId + "-performance.json").getAbsolutePath();
    }

    /** One final receipt includes terminal main-report IO; its own IO is explicitly outside the observation. */
    void savePerformance(TestLabReport report, java.util.Map<String, Object> performance) throws IOException {
        java.util.Map<String, Object> receipt = new java.util.LinkedHashMap<>();
        receipt.put("runId", report.runId);
        receipt.put("state", report.state);
        receipt.put("mainReportPath", report.reportPath);
        receipt.put("performance", performance);
        write(new File(performanceReceiptPath(report.runId)), GSON.toJson(receipt));
    }

    void save(PendingWrite pending) throws IOException {
        long timing = TestLabPerformance.begin();
        try {
            Files.createDirectories(directory.toPath());
            if (!pending.reportSaved) {
                write(pending.reportFile, pending.json);
                pending.reportSaved = true;
            }
            if (!pending.latestSaved) {
                write(pending.latestFile, pending.json);
                pending.latestSaved = true;
            }
        } finally {
            TestLabPerformance.end("report.writeBothTargets", timing);
        }
    }

    private static void write(File file, String json) throws IOException {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
