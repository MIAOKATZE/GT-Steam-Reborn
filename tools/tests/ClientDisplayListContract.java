package com.miaokatze.gtsr.client.render;

import java.util.*;

public final class ClientDisplayListContract {

    static int checks;

    static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
        checks++;
    }

    static final class Fake implements ClientDisplayList.Driver {

        boolean active, failEnd, failRestore, failBegin;
        int ambient, next = 1, alloc, ends, deletes, restores;
        Set<Integer> live = new HashSet<>();

        public ClientDisplayList.Snapshot snapshot() {
            if (active) throw new IllegalStateException("ambient tess");
            return () -> {
                restores++;
                active = false;
                if (failRestore) throw new IllegalStateException("restore");
            };
        }

        public int current() {
            return ambient;
        }

        public int allocate() {
            alloc++;
            live.add(next);
            return next++;
        }

        public void begin(int id) {
            if (failBegin) throw new IllegalStateException("begin");
            ambient = id;
        }

        public void end() {
            ends++;
            ambient = 0;
            if (failEnd) throw new IllegalStateException("end");
        }

        public void delete(int id) {
            deletes++;
            live.remove(id);
        }
    }

    public static void main(String[] args) {
        Fake f = new Fake();
        IllegalArgumentException original = new IllegalArgumentException("vertices");
        try {
            ClientDisplayList.compile(() -> {
                f.active = true;
                throw original;
            }, f);
            throw new AssertionError();
        } catch (IllegalArgumentException e) {
            check(e == original, "preserve original failure");
        }
        check(
            f.ends == 1 && f.deletes == 1 && f.restores == 1 && !f.active && f.live.isEmpty(),
            "failed own list releases every owner");
        int good = ClientDisplayList.compile(() -> {}, f);
        check(f.live.contains(good) && f.ends == 2 && f.deletes == 1, "success hands live list to owner");
        f.active = true;
        int allocations = f.alloc;
        try {
            ClientDisplayList.compile(() -> {}, f);
            throw new AssertionError();
        } catch (IllegalStateException e) {}
        check(f.active && f.alloc == allocations, "ambient batch untouched before allocation");
        f.active = false;
        f.ambient = 90;
        try {
            ClientDisplayList.compile(() -> {}, f);
            throw new AssertionError();
        } catch (IllegalStateException e) {}
        check(f.ambient == 90 && f.alloc == allocations, "ambient display list untouched before allocation");
        Fake cleanup = new Fake();
        cleanup.failEnd = true;
        try {
            ClientDisplayList.compile(() -> { throw original; }, cleanup);
            throw new AssertionError();
        } catch (IllegalArgumentException e) {
            check(e == original && e.getSuppressed().length > 0, "cleanup failure is suppressed onto original");
        }
        check(
            cleanup.restores == 1 && cleanup.deletes == 1 && cleanup.live.isEmpty(),
            "end failure still restores and deletes");
        cleanup = new Fake();
        cleanup.failRestore = true;
        try {
            ClientDisplayList.compile(() -> {}, cleanup);
            throw new AssertionError();
        } catch (IllegalStateException e) {}
        check(cleanup.deletes == 1 && cleanup.live.isEmpty(), "restore failure cannot publish a live list");
        cleanup = new Fake();
        cleanup.failBegin = true;
        try {
            ClientDisplayList.compile(() -> {}, cleanup);
            throw new AssertionError();
        } catch (IllegalStateException e) {}
        check(
            cleanup.ends == 0 && cleanup.restores == 1 && cleanup.live.isEmpty(),
            "failed begin deletes only allocated own list");
        Fake group = new Fake();
        int a = ClientDisplayList.compile(() -> {}, group), b = ClientDisplayList.compile(() -> {}, group);
        ClientDisplayList.rollback(List.of(a, b), original, group);
        check(group.live.isEmpty() && group.deletes == 2, "unpublished group rollback is complete");
        System.out.println("PASS display-list owner failure contracts checks=" + checks);
    }
}
