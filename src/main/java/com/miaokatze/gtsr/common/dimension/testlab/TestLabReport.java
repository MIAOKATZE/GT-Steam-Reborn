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

    public final int schemaVersion = 1;
    public String gtsrVersion;
    public String gtVersion;
    public String fmlVersion;
    public String category;
    public String executionScope;
    public String runId;
    public String state;
    public String requested;
    public int dimension;
    public long startedAt;
    public long finishedAt;
    public String activeTask;
    public int activeTicks;
    public String activeDebug;
    public String reportPath;
    public final List<String> plannedTasks = new ArrayList<>();
    public final List<Entry> results = new ArrayList<>();
}
