package com.miaokatze.gtsr.common.dimension.testlab;

import net.minecraft.world.WorldServer;

/** Server-thread scenario. A null tick result means that real world execution is still pending. */
public interface TestLabTask {

    String id();

    String displayName();

    int timeoutTicks();

    /** UI/server model probes use only the runtime anchor and never generate fixture terrain. */
    default boolean requiresFixtureChunks() {
        return true;
    }

    /** Describes direct server-thread work, never the asynchronous machine's entire tick cost. */
    default String performancePhase() {
        return id().startsWith("ui.") ? "ui.probe" : "machine.execute";
    }

    void start(WorldServer world, int x, int y, int z) throws Exception;

    TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception;

    void stop(WorldServer world);

    default String debug(WorldServer world) {
        return id();
    }
}
