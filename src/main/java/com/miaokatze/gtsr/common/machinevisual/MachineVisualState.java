package com.miaokatze.gtsr.common.machinevisual;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** One transient observer per installation. No extra persisted state and no production entry points. */
public final class MachineVisualState {

    private static final AtomicLong GENERATIONS = new AtomicLong();
    private final long generation = GENERATIONS.incrementAndGet();
    private long workSequence, lastAdvanceTick = Long.MIN_VALUE, sequence, lastSentTick = Long.MIN_VALUE;
    private int previousProgress, previousFlags = -1, previousOrientation = -1, previousDuration = -1;
    private MachineVisualSnapshot current;
    private final Map<EntityPlayerMP, Long> subscriptions = new java.util.IdentityHashMap<>();

    /** Invoked only after the original protected increment method returns normally. */
    public void advanced(long tick) {
        workSequence++;
        lastAdvanceTick = tick;
    }

    public void observe(MachineVisualHost host, long tick, boolean formed, int progress, int duration,
        boolean motionShape) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        if (tile.isClientSide()) {
            GTSteamReborn.proxy.observeMachineVisual(host, tick);
            return;
        }
        // A successful advance can finish and restart a one-tick recipe before postTick observes it.
        boolean advancing = formed && lastAdvanceTick == tick;
        int flags = (formed ? 1 : 0) | (motionShape ? 2 : 0) | (advancing ? 4 : 0) | (!tile.isAllowedToWork() ? 8 : 0);
        int orientation = host.getExtendedFacing()
            .getIndex();
        boolean changed = flags != previousFlags || orientation != previousOrientation
            || duration != previousDuration
            || progress < previousProgress;
        previousProgress = progress;
        previousFlags = flags;
        previousOrientation = orientation;
        previousDuration = duration;
        current = new MachineVisualSnapshot(
            tile.getWorld().provider.dimensionId,
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            tile.getMetaTileID(),
            orientation,
            flags,
            Math.max(0, Math.min(progress, duration)),
            Math.max(0, duration),
            MachineVisualNetwork.session(),
            0,
            generation,
            host.visualDefinition().revision,
            ++sequence,
            tick,
            workSequence,
            lastAdvanceTick);
        if (!changed && lastSentTick != Long.MIN_VALUE && tick - lastSentTick < 20) return;
        lastSentTick = tick;
        Iterator<Map.Entry<EntityPlayerMP, Long>> iterator = subscriptions.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityPlayerMP, Long> subscription = iterator.next();
            if (!MachineVisualNetwork.canObserve(subscription.getKey(), host)) {
                iterator.remove();
                continue;
            }
            MachineVisualNetwork.send(subscription.getKey(), current.withNonce(subscription.getValue()));
        }
    }

    void subscribe(MachineVisualHost host, EntityPlayerMP player, long nonce) {
        if (current == null || !MachineVisualNetwork.canObserve(player, host)) return;
        subscriptions.put(player, nonce);
        MachineVisualNetwork.send(player, current.withNonce(nonce));
    }

    /** Diagnostic authority frame; reading it cannot start, advance or finish production. */
    public MachineVisualSnapshot snapshot() {
        return current;
    }
}
