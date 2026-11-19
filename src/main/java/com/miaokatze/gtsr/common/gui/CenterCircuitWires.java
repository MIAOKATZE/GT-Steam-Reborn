// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import com.cleanroommc.modularui.drawable.BufferBuilder;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.utils.Platform.DrawMode;
import com.cleanroommc.modularui.utils.Platform.VertexFormat;

/** Orthogonal circuit routes with arrowheads and explicit, non-joining crossovers. */
final class CenterCircuitWires {

    private int[][] previousInput = new int[0][];
    private List<Wire> wires = new ArrayList<>();

    void draw(int width, int height, int gridX, int gridY, int background, int grid, List<int[]> edges, List<int[]> boxes) {
        List<int[]> geometry = new ArrayList<>(edges.size() + boxes.size() + 1);
        geometry.add(new int[] { width, height, edges.size() });
        geometry.addAll(edges);
        geometry.addAll(boxes);
        int[][] input = geometry.toArray(new int[0][]);
        if (!Arrays.deepEquals(input, previousInput)) {
            wires = plan(edges, boxes);
            previousInput = input;
        }
        Platform.setupDrawColor();
        try {
            Platform.startDrawing(DrawMode.QUADS, VertexFormat.POS_COLOR, buffer -> {
                for (Wire wire : wires) {
                    for (Run run : wire.runs) {
                        for (Hop hop : run.hops) {
                            float[] arc = arc(run, hop);
                            for (int i = 2; i < arc.length; i += 2) {
                                stroke(buffer, width, height, arc[i - 2], arc[i - 1], arc[i], arc[i + 1], 2.4f, background);
                            }
                            int x = run.horizontal ? hop.at : run.axis;
                            int y = run.horizontal ? run.axis : hop.at;
                            // Repaint the board grid where the bridge's broad stroke clears the crossing.
                            for (int ax = x - 4; ax <= x + 4; ax++) for (int ay = y - 4; ay <= y + 4; ay++) {
                                if (ax < 0 || ay < 0 || ax >= width || ay >= height
                                    || Math.floorMod(ax - gridX, 10) != 0 && Math.floorMod(ay - gridY, 10) != 0) continue;
                                if (nearArc(ax + .5f, ay + .5f, arc))
                                    quad(buffer, grid, ax, ay, ax, ay + 1, ax + 1, ay + 1, ax + 1, ay);
                            }
                        }
                        drawRun(buffer, width, height, wire, run);
                    }
                    arrow(buffer, width, height, wire);
                }
            });
        } finally {
            Platform.endDrawColor();
        }
    }

    private static List<Wire> plan(List<int[]> edges, List<int[]> boxes) {
        List<Wire> routed = new ArrayList<>();
        List<Run> prior = new ArrayList<>();
        for (int style = 0; style <= 1; style++) for (int[] edge : edges) {
            if (edge[5] != style) continue;
            List<Run> runs = route(edge, boxes, prior);
            if (runs == null) continue;
            for (Run run : runs) {
                TreeSet<Integer> crossings = new TreeSet<>();
                for (Run earlier : prior) {
                    if (run.horizontal != earlier.horizontal && run.inside(earlier.axis, 4) && earlier.inside(run.axis, 3))
                        crossings.add(earlier.axis);
                }
                for (int at : crossings) {
                    if (!run.hops.isEmpty() && at - run.hops.get(run.hops.size() - 1).at < 7) continue;
                    for (int side : new int[] { -1, 1 }) {
                        int x = run.horizontal ? at : run.axis + side * 2;
                        int y = run.horizontal ? run.axis + side * 2 : at;
                        if (boxes.stream().noneMatch(box -> x + 3 > box[0] && x - 3 < box[2]
                            && y + 3 > box[1] && y - 3 < box[3])) {
                            run.hops.add(new Hop(at, side));
                            break;
                        }
                    }
                }
            }
            prior.addAll(runs);
            routed.add(new Wire(runs, edge[4], style != 0));
        }
        return routed;
    }

    private static List<Run> route(int[] edge, List<int[]> boxes, List<Run> prior) {
        int ax = edge[0], ay = edge[1], bx = edge[2], by = edge[3];
        if (ax == bx && ay == by) return null;
        for (int pad : new int[] { 4, 1 }) {
            int sx = ax + pad, ex = bx - pad;
            Route best = new Route(boxes, prior, pad);
            best.offer(ax, ay, sx, ay, ex, ay, ex, by, bx, by);
            best.offer(ax, ay, sx, ay, sx, by, ex, by, bx, by);
            best.offer(ax, ay, sx, ay, (sx + ex) / 2, ay, (sx + ex) / 2, by, ex, by, bx, by);
            if (best.cost <= Math.abs(ax - bx) + Math.abs(ay - by) + (ay == by ? 5 : 19)) return best.path;
            TreeSet<Integer> xLanes = new TreeSet<>(), yLanes = new TreeSet<>();
            xLanes.add(sx);
            xLanes.add(ex);
            yLanes.add((ay + by) / 2);
            for (int[] box : boxes) {
                xLanes.add(box[0] - pad);
                xLanes.add(box[2] + pad);
                yLanes.add(box[1] - pad);
                yLanes.add(box[3] + pad);
            }
            for (int x : xLanes)
                best.offer(ax, ay, sx, ay, x, ay, x, by, ex, by, bx, by);
            for (int y : yLanes)
                best.offer(ax, ay, sx, ay, sx, y, ex, y, ex, by, bx, by);
            if (best.path != null) return best.path;
        }
        return null;
    }

    private static final class Route {
        private final List<int[]> boxes;
        private final List<Run> prior;
        private final int padding;
        private List<Run> path;
        private double cost = Double.POSITIVE_INFINITY;

        Route(List<int[]> boxes, List<Run> prior, int padding) {
            this.boxes = boxes;
            this.prior = prior;
            this.padding = padding;
        }

        void offer(int... points) {
            int count = 2;
            for (int i = 2; i < points.length; i += 2) {
                int x = points[i], y = points[i + 1];
                if (points[count - 2] == x && points[count - 1] == y) continue;
                if (count >= 4 && (points[count - 2] == x && points[count - 4] == x
                    || points[count - 1] == y && points[count - 3] == y)) count -= 2;
                points[count++] = x;
                points[count++] = y;
            }
            double score = 0;
            List<Run> trial = new ArrayList<>(count / 2);
            for (int i = 2; i < count; i += 2) {
                Run run = new Run(points[i - 2], points[i - 1], points[i], points[i + 1]);
                if (blocked(run, points[0], points[1], points[count - 2], points[count - 1])) return;
                score += run.high - run.low + (i == 2 ? 0 : 9);
                if (score >= cost) return;
                for (Run other : prior) {
                    if (run.horizontal == other.horizontal && run.axis == other.axis) {
                        score += 1.6 * Math.max(0, Math.min(run.high, other.high) - Math.max(run.low, other.low));
                    } else if (run.horizontal != other.horizontal && run.inside(other.axis, 0) && other.inside(run.axis, 0)) {
                        score += 3;
                    }
                }
                trial.add(run);
            }
            if (!trial.isEmpty() && score < cost) {
                path = trial;
                cost = score;
            }
        }

        private boolean blocked(Run run, int startX, int startY, int endX, int endY) {
            for (int[] box : boxes) {
                boolean touchesTerminal = run.horizontal && (run.axis == startY && run.contains(startX)
                    && inside(box, startX, startY) || run.axis == endY && run.contains(endX) && inside(box, endX, endY));
                if (touchesTerminal) continue;
                int rowMin = run.horizontal ? box[1] : box[0], rowMax = run.horizontal ? box[3] : box[2];
                int low = run.horizontal ? box[0] : box[1], high = run.horizontal ? box[2] : box[3];
                if (run.axis > rowMin - padding && run.axis < rowMax + padding
                    && run.low < high + padding && run.high > low - padding) return true;
            }
            return false;
        }

        private static boolean inside(int[] box, int x, int y) {
            return x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3];
        }
    }

    private static final class Run {
        final int x, y, dx, dy, axis, low, high;
        final boolean horizontal;
        final List<Hop> hops = new ArrayList<>();

        Run(int x, int y, int dx, int dy) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            horizontal = y == dy;
            axis = horizontal ? y : x;
            low = Math.min(horizontal ? x : y, horizontal ? dx : dy);
            high = Math.max(horizontal ? x : y, horizontal ? dx : dy);
        }

        boolean contains(int at) {
            return at >= low && at <= high;
        }

        boolean inside(int at, int margin) {
            return at > low + margin && at < high - margin;
        }
    }

    private static final class Hop {
        final int at, direction;
        Hop(int at, int direction) {
            this.at = at;
            this.direction = direction;
        }
    }

    private static final class Wire {
        final List<Run> runs;
        final int color;
        final boolean dashed;
        Wire(List<Run> runs, int color, boolean dashed) {
            this.runs = runs;
            this.color = color;
            this.dashed = dashed;
        }
    }

    private static float[] arc(Run run, Hop hop) {
        float[] points = new float[18];
        for (int i = 0; i <= 8; i++) {
            double t = i / 8d;
            float along = hop.at - 3.5f + 7f * (float) t;
            float rise = 15f * (float) (t * (1 - t)) * hop.direction;
            points[i * 2] = run.horizontal ? along : run.axis + rise;
            points[i * 2 + 1] = run.horizontal ? run.axis + rise : along;
        }
        return points;
    }

    private static boolean nearArc(float x, float y, float[] arc) {
        for (int i = 0; i < arc.length; i += 2)
            if ((arc[i] - x) * (arc[i] - x) + (arc[i + 1] - y) * (arc[i + 1] - y) < 1.35f) return true;
        return false;
    }

    private static void drawRun(BufferBuilder b, int w, int h, Wire wire, Run run) {
        float from = run.low;
        for (Hop hop : run.hops) {
            straight(b, w, h, wire, run, from, hop.at - 3.5f);
            float[] arc = arc(run, hop);
            for (int i = 2; i < arc.length; i += 2)
                stroke(b, w, h, arc[i - 2], arc[i - 1], arc[i], arc[i + 1], .63f, wire.color);
            from = hop.at + 3.5f;
        }
        straight(b, w, h, wire, run, from, run.high);
    }

    private static void straight(BufferBuilder b, int w, int h, Wire wire, Run run, float lo, float hi) {
        float start = Math.max(lo, 0);
        float end = Math.min(hi, run.horizontal ? w : h);
        if (start >= end) return;
        if (!wire.dashed) {
            segment(b, w, h, wire.color, run, start, end, .62f);
            return;
        }
        float first = lo + (float) Math.floor((start - lo) / 5) * 5;
        for (float pos = first; pos < end; pos += 5)
            segment(b, w, h, wire.color, run, Math.max(pos, start), Math.min(pos + 3, end), .49f);
    }

    private static void segment(BufferBuilder b, int w, int h, int color, Run run, float start, float end, float radius) {
        if (start >= end) return;
        if (run.horizontal) stroke(b, w, h, start, run.axis, end, run.axis, radius, color);
        else stroke(b, w, h, run.axis, start, run.axis, end, radius, color);
    }

    private static void arrow(BufferBuilder b, int w, int h, Wire wire) {
        Run last = wire.runs.get(wire.runs.size() - 1);
        int dx = Integer.compare(last.dx, last.x), dy = Integer.compare(last.dy, last.y);
        float x = last.dx - dx * 4, y = last.dy - dy * 4;
        if (last.dx < 1 || last.dx >= w - 1 || last.dy < 2 || last.dy >= h - 2) return;
        quad(b, wire.color, last.dx, last.dy, x - dy * 1.7f, y + dx * 1.7f,
            x + dy * 1.7f, y - dx * 1.7f, x + dy * 1.7f, y - dx * 1.7f);
    }

    private static void stroke(BufferBuilder b, int w, int h, float x, float y, float ex, float ey, float radius, int color) {
        if (Math.max(x, ex) < 0 || Math.min(x, ex) >= w || Math.max(y, ey) < 0 || Math.min(y, ey) >= h) return;
        float dx = ex - x, dy = ey - y, length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;
        float nx = dy * radius / length, ny = -dx * radius / length;
        quad(b, color, bound(x + nx, w), bound(y + ny, h), bound(x - nx, w), bound(y - ny, h),
            bound(ex - nx, w), bound(ey - ny, h), bound(ex + nx, w), bound(ey + ny, h));
    }

    private static float bound(float value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private static void quad(BufferBuilder b, int color, float x, float y, float x2, float y2, float x3, float y3, float x4, float y4) {
        int r = color >> 16 & 255, g = color >> 8 & 255, blue = color & 255;
        b.pos(x, y, 0).color(r, g, blue, 255).endVertex();
        b.pos(x2, y2, 0).color(r, g, blue, 255).endVertex();
        b.pos(x3, y3, 0).color(r, g, blue, 255).endVertex();
        b.pos(x4, y4, 0).color(r, g, blue, 255).endVertex();
    }
}
