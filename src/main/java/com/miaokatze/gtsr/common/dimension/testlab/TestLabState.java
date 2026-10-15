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
        report.reportPath = reportFile(report.runId).getAbsolutePath();
        return new PendingWrite(reportFile(report.runId), new File(directory, "latest.json"), GSON.toJson(report));
    }

    void save(PendingWrite pending) throws IOException {
        Files.createDirectories(directory.toPath());
        if (!pending.reportSaved) {
            write(pending.reportFile, pending.json);
            pending.reportSaved = true;
        }
        if (!pending.latestSaved) {
            write(pending.latestFile, pending.json);
            pending.latestSaved = true;
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
