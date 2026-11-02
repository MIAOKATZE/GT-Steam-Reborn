package com.miaokatze.gtsr.client.encounter;

import java.util.UUID;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Actual scene packet/cache lifecycle checks. Does not claim a native client render or server tick. */
public final class SceneBossHudCheck {

    private static final UUID PLAYER = UUID.fromString("784275ef-7d48-4039-858b-f08421d5d809");
    private static int checks;

    public static void main(String[] args) {
        for (int kind = 1; kind <= 3; kind++) lifecycle(kind);
        System.out.println("PASS: " + checks + " actual scene packet/cache checks");
    }

    private static void lifecycle(int kind) {
        Object world = new Object();
        SceneBossCache cache = new SceneBossCache();
        SceneBossSignal dormant = packet(kind, 0);
        cache.receive(world, 78, PLAYER, dormant);
        check(cache.get(world) == dormant, "dormant kind " + kind + " retains activation counts without an entity");
        for (int i = 0; i < 50; i++) {
            cache.tick(world, 78, PLAYER);
            if (i % 10 == 9) cache.receive(world, 78, PLAYER, packet(kind, 0));
            check(cache.get(world) != null, "ten-tick heartbeat retains dormant kind " + kind);
        }
        cache.receive(world, 78, PLAYER, packet(kind, 1));
        cache.tick(world, 78, PLAYER);
        check(cache.get(world).visibleHealth(.5F) > 1, "revival animates kind " + kind);
        cache.receive(world, 78, PLAYER, packet(kind, 2));
        check(cache.get(world).state == 2, "combat snapshot replaces revival kind " + kind);
        cache.receive(world, 78, PLAYER, packet(kind, 3));
        check(cache.get(world).state == 3, "completed snapshot replaces combat kind " + kind);
        cache.receive(world, 78, PLAYER, packet(0, 0));
        check(cache.get(world) == null, "explicit leave clears kind " + kind);
        cache.receive(world, 78, PLAYER, packet(kind, 0));
        cache.tick(world, 0, PLAYER);
        check(cache.get(world) == null, "dimension change clears kind " + kind);
        cache.receive(world, 78, PLAYER, packet(kind, 0));
        cache.tick(new Object(), 78, PLAYER);
        check(cache.get(world) == null, "world replacement clears kind " + kind);
        cache.receive(world, 78, PLAYER, packet(kind, 0));
        cache.tick(world, 78, UUID.randomUUID());
        check(cache.get(world) == null, "player replacement clears kind " + kind);
        cache.receive(world, 78, PLAYER, packet(kind, 0));
        for (int i = 0; i < 90; i++) cache.tick(world, 78, PLAYER);
        check(cache.get(world) != null, "server stall retains the last authoritative scene kind " + kind);
        SceneBossSignal updated = packet(kind, 0);
        cache.receive(world, 78, PLAYER, updated);
        check(cache.get(world) == updated, "next heartbeat replaces the stalled snapshot kind " + kind);
        cache.receive(world, 78, PLAYER, packet(kind, 1));
        for (int i = 0; i < 90; i++) cache.tick(world, 78, PLAYER);
        float limit = 1 + (cache.get(world).maxHealth - 1) * 10F / cache.get(world).revivalDuration();
        check(cache.get(world).visibleHealth(1) <= limit, "stalled revival never predicts beyond one heartbeat kind " + kind);
        cache.receive(world, 78, PLAYER, packet(0, 0));
        check(cache.get(world) == null, "server leave after a stall clears kind " + kind);
        cache.receive(world, 78, PLAYER, packet(kind, 0));
        cache.tick(null, 78, PLAYER);
        check(cache.get(world) == null, "disconnect after a stall clears kind " + kind);
    }

    private static SceneBossSignal packet(int kind, int state) {
        SceneBossSignal source = new SceneBossSignal();
        source.dimension = 78;
        source.player = PLAYER;
        source.kind = kind;
        source.state = state;
        if (kind != 0) {
            source.site = kind == 1 ? "lake:224:100:200"
                : "echo:r7:224:" + (kind == 2 ? "subsided_factory" : "fallen_foundry") + ":0:100:200:compact-prefab";
            source.code = kind == 1 ? "dc-10" : kind == 2 ? "dc-08" : "dc-02";
            source.total = kind == 1 ? 32 : 9;
            source.remaining = state == 0 ? source.total : 0;
            source.maxHealth = kind == 1 ? 3000 : kind == 2 ? 2000 : 2400;
            source.health = state == 1 ? 1 : state == 2 ? source.maxHealth : 0;
        }
        check(source.sane(), "source snapshot sane kind " + kind + " state " + state);
        ByteBuf bytes = Unpooled.buffer();
        try {
            source.toBytes(bytes);
            SceneBossSignal decoded = new SceneBossSignal();
            decoded.fromBytes(bytes);
            check(decoded.valid && decoded.sane(), "wire roundtrip kind " + kind + " state " + state);
            return decoded;
        } finally {
            bytes.release();
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
