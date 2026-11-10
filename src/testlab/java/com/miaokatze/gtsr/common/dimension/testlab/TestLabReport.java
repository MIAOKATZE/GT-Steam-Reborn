package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Machine-readable observations; registration or formation alone is never production PASS. */
public final class TestLabReport {

    public enum Status {
        PASS,
        FAIL,
        BLOCKED,
        UNSUPPORTED,
        SKIP
    }

    public static final class Result {

        public final Status status;
        public final String detail;
        public final Map<String, Object> details;

        public Result(Status status, String detail) {
            this(status, detail, Collections.emptyMap());
        }

        public Result(Status status, String detail, Map<String, Object> details) {
            if (status == null) throw new IllegalArgumentException("status");
            this.status = status;
            this.detail = detail == null ? "" : detail;
            this.details = details == null ? Collections.emptyMap() : new LinkedHashMap<>(details);
        }
    }

    public static final class Entry {

        public String id;
        public String name;
        public Status status;
        public String detail;
        public String category;
        public Map<String, Object> details;
        public int elapsedTicks;
        public int originX;
        public int originY;
        public int originZ;
    }

    public final int schemaVersion = 2;
    public Map<String, String> artifactIdentity;
    public Map<String, String> coreArtifactIdentity;
    public boolean requiresClient = false;
    public boolean requiresRealActor = false;
    public String cleanupScope = "OWNED_TASK_AND_ANCHOR_RESOURCES; MANUAL_SUPPLY_RETAINED_UNTIL_CLEAR_RESET_OR_UNLOAD; PHYSICAL_STATIONS_AND_JOURNAL_RETAINED; NOT_WORLD_ROLLBACK";
    public final List<Map<String, Object>> events = new ArrayList<>();
    public final List<Map<String, Object>> cleanup = new ArrayList<>();
    public final List<Map<String, Object>> restoration = new ArrayList<>();
    public final List<Map<String, Object>> selectedCases = new ArrayList<>();
    public String gtsrVersion;
    public String gtVersion;
    public String fmlVersion;
    public String category;
    public String executionScope;
    public String runId;
    /** Core command request that initiated this business run; null for legacy commands. */
    public String requestId;
    public String state;
    /** Business outcome, separate from the legacy queue lifecycle state. Refreshed before every serialization. */
    public String outcome;
    public String requested;
    public int dimension;
    public long startedAt;
    public long finishedAt;
    public String activeTask;
    public int activeTicks;
    public String activeDebug;
    public String reportPath;
    /** Present only for explicitly requested performance runs. */
    public Map<String, Object> performance;
    public String performanceReceiptPath;
    public final List<String> plannedTasks = new ArrayList<>();
    public final List<Entry> results = new ArrayList<>();

    public String calculateOutcome() {
        if ("RUNNING".equals(state)) return "IN_PROGRESS";
        if ("STOPPED".equals(state)) return "CANCELLED";
        if (!"COMPLETE".equals(state)) return state == null ? "NOT_RUN" : state;
        String result = "PASS";
        for (Entry entry : results) {
            if (entry.status == Status.FAIL) return "FAIL";
            if (entry.status == Status.BLOCKED) result = "BLOCKED";
            else if (!"BLOCKED".equals(result) && entry.status != Status.PASS) result = "INCOMPLETE";
        }
        return result;
    }
}
