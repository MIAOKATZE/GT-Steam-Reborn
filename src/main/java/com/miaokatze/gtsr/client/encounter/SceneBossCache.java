package com.miaokatze.gtsr.client.encounter;

import java.util.UUID;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal;

/** The ordered server stream owns scene departures; client ticks must not expire a stalled server's scene. */
public final class SceneBossCache {

    private Object world;
    private SceneBossSignal signal;

    public void receive(Object current, int dimension, UUID player, SceneBossSignal s) {
        if (current == null || !s.valid || !s.sane() || dimension != s.dimension || !s.player.equals(player)) return;
        if (s.state == 1) {
            s.renderRevivalTicks = s.revivalTicks;
            if (current == world && signal != null
                && signal.state == 1
                && s.kind == signal.kind
                && s.entity == signal.entity
                && s.site.equals(signal.site)) {
                s.renderRevivalTicks = Math.max(s.renderRevivalTicks, signal.renderRevivalTicks);
                s.minimumVisibleHealth = signal.minimumVisibleHealth;
            }
        }
        world = current;
        signal = s.kind == 0 ? null : s;
    }

    public void tick(Object current, int dimension, UUID player) {
        if (signal != null && (signal.dimension != dimension || !signal.player.equals(player))) clear();
        tick(current);
    }

    public void tick(Object current) {
        if (current == null || current != world) clear();
        else if (signal != null && signal.state == 1) signal.renderRevivalTicks = Math
            .min(signal.revivalDuration(), Math.min(signal.revivalTicks + 10, signal.renderRevivalTicks + 1));
    }

    public SceneBossSignal get(Object current) {
        return current != null && current == world ? signal : null;
    }

    public void clear() {
        world = null;
        signal = null;
    }
}
