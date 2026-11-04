package com.miaokatze.gtsr.common.workeffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable visual-only cells. StructureLib ABC uses B downwards; no world or production state is retained. */
public final class WorkEffectDefinition {

    private final String id;
    private final List<Cell> fireboxes;
    private final List<Emitter> emitters;

    public WorkEffectDefinition(String id, List<Cell> fireboxes, List<Emitter> emitters) {
        this.id = id;
        this.fireboxes = Collections.unmodifiableList(new ArrayList<>(fireboxes));
        this.emitters = Collections.unmodifiableList(new ArrayList<>(emitters));
    }

    public String id() {
        return id;
    }

    public List<Cell> fireboxes() {
        return fireboxes;
    }

    public List<Emitter> emitters() {
        return emitters;
    }

    public static final class Cell {

        private final int a, b, c;

        public Cell(int a, int b, int c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        public int a() {
            return a;
        }

        public int b() {
            return b;
        }

        public int c() {
            return c;
        }
    }

    /** Existing thermal outlets have no extra above-cell requirement; selected new outlets require both cells empty. */
    public static final class Emitter {

        private final String id;
        private final Cell cell, above;
        private final double centerYOffset;
        private final boolean residualHeat;
        private final double[] preciseABC;

        public Emitter(String id, Cell cell, Cell above, double centerYOffset, boolean residualHeat) {
            this.id = id;
            this.cell = cell;
            this.above = above;
            this.centerYOffset = centerYOffset;
            this.residualHeat = residualHeat;
            this.preciseABC = null;
        }

        /** Controller-relative aperture point; the queue checks the actual jittered world position for air. */
        public Emitter(String id, double a, double b, double c) {
            this.id = id;
            this.cell = new Cell((int) Math.round(a), (int) Math.round(b), (int) Math.round(c));
            this.above = null;
            this.centerYOffset = 0D;
            this.residualHeat = false;
            this.preciseABC = new double[] { a, b, c };
        }

        public double[] preciseABC() {
            return preciseABC == null ? null : preciseABC.clone();
        }

        public String id() {
            return id;
        }

        public Cell cell() {
            return cell;
        }

        public Cell above() {
            return above;
        }

        public double centerYOffset() {
            return centerYOffset;
        }

        public boolean residualHeat() {
            return residualHeat;
        }
    }
}
