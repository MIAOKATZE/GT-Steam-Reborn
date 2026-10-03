package com.miaokatze.gtsr.client.encounter;

import java.util.UUID;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal;

/** World identity and short leases prevent stale scene bars after disconnects and teleports. */
public final class SceneBossCache {

    private Object world;
    private SceneBossSignal signal;
    private int ticks;

    public void receive(Object current, int dimension, UUID player, SceneBossSignal s) {
        if (current == null || !s.valid || !s.sane() || dimension != s.dimension || !s.player.equals(player)) return;
        world = current;
        signal = s.kind == 0 ? null : s;
        ticks = signal == null ? 0 : 60;
    }

    public void tick(Object current, int dimension, UUID player) {
        if (signal != null && (signal.dimension != dimension || !signal.player.equals(player))) clear();
        tick(current);
    }

    public void tick(Object current) {
        if (current == null || current != world || ticks <= 1) clear();
        else ticks--;
    }

    public SceneBossSignal get(Object current) {
        return current != null && current == world && ticks > 0 ? signal : null;
    }

    public void clear() {
        world = null;
        signal = null;
        ticks = 0;
    }
}
