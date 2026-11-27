// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The reviewed generator routes and ore rules. Actual recipes stay in the live game recipe maps. */
public final class CenterPresets {

    private static final List<Preset> PRESETS = load();

    private CenterPresets() {}

    public static final class Expression {

        private final String label, rule;

        private Expression(String label, String rule) {
            this.label = label;
            this.rule = rule;
        }

        public String label() {
            return label;
        }

        public String rule() {
            return rule;
        }
    }

    public static final class Preset {

        public final String id, name, group, description;
        public final List<Expression> expressions;
        private final JsonObject source;

        private Preset(JsonObject source) {
            this.source = source;
            id = source.get("id")
                .getAsString();
            name = source.get("label")
                .getAsString();
            group = source.get("category")
                .getAsString();
            description = source.get("description")
                .getAsString();
            List<Expression> list = new ArrayList<>();
            for (JsonElement element : source.getAsJsonArray("expressions")) {
                JsonObject row = element.getAsJsonObject();
                list.add(
                    new Expression(
                        row.get("label")
                            .getAsString(),
                        row.get("expression")
                            .getAsString()));
            }
            expressions = Collections.unmodifiableList(list);
        }

        public boolean importable() {
            return source.has("nodes");
        }

        public int nodeCount() {
            return importable() ? source.getAsJsonArray("nodes")
                .size() : 0;
        }

        public CenterGraph graph(String name) {
            if (!importable()) throw new IllegalArgumentException("No whole graph for " + id);
            List<CenterGraph.Node> nodes = new ArrayList<>();
            for (JsonElement element : source.getAsJsonArray("nodes")) {
                JsonObject row = element.getAsJsonObject();
                JsonArray links = row.getAsJsonArray("links");
                int[] targets = new int[links.size()];
                for (int i = 0; i < targets.length; i++) targets[i] = links.get(i)
                    .getAsInt();
                List<CenterGraph.OutletFilter> filters = new ArrayList<>();
                for (JsonElement link : row.getAsJsonArray("filters")) {
                    JsonArray parts = link.getAsJsonArray();
                    filters.add(
                        new CenterGraph.OutletFilter(
                            parts.get(0)
                                .getAsInt(),
                            parts.get(1)
                                .getAsBoolean(),
                            strings(
                                parts.get(2)
                                    .getAsJsonArray())));
                }
                nodes.add(
                    new CenterGraph.Node(
                        CenterProcess.valueOf(
                            row.get("process")
                                .getAsString()),
                        targets,
                        0,
                        filters));
            }
            List<CenterGraph.EntryRoute> entries = new ArrayList<>();
            for (JsonElement element : source.getAsJsonArray("entries")) {
                JsonArray row = element.getAsJsonArray();
                entries.add(
                    new CenterGraph.EntryRoute(
                        row.get(0)
                            .getAsInt(),
                        row.get(1)
                            .getAsString()));
            }
            return new CenterGraph(
                0,
                name,
                0,
                true,
                strings(source.getAsJsonArray("rules")),
                nodes,
                entries,
                strings(source.getAsJsonArray("exclusions")));
        }
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    public static Preset get(String id) {
        for (Preset preset : PRESETS) if (preset.id.equals(id)) return preset;
        throw new IllegalArgumentException("Unknown center preset");
    }

    private static List<Preset> load() {
        var stream = CenterPresets.class.getResourceAsStream("/assets/gtsr/orecenter/generator-presets.json");
        try (var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            List<Preset> presets = new ArrayList<>();
            for (JsonElement row : new JsonParser().parse(reader)
                .getAsJsonArray()) presets.add(new Preset(row.getAsJsonObject()));
            return Collections.unmodifiableList(presets);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read center generator presets", error);
        }
    }

    private static List<String> strings(JsonArray rows) {
        List<String> result = new ArrayList<>(rows.size());
        for (JsonElement element : rows) result.add(element.getAsString());
        return result;
    }
}
