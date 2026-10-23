package audit;

import com.google.gson.JsonObject;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Owned QA scheduling isolation: drain existing BQ broadcasts before the first player joins. */
final class UiGalleryBatchBqStartup {
    static void beforeFirstLogin(UiGalleryBatch batch) throws Exception {
        if (!Loader.isModLoaded("betterquesting")) return;
        Object server = FMLCommonHandler.instance().getMinecraftServerInstance();
        Object players = UiGalleryBatch.field(UiGalleryBatch.call(server,
            "getConfigurationManager|func_71203_ab"), "playerEntityList|field_72404_b");
        batch.q.check("batch.bq-barrier.before-login", ((List<?>)players).isEmpty(),
            "Owned serverStarted callback before first login; no player can receive only a broadcast suffix");
        Object io = UiGalleryBatch.field(UiGalleryBatch.type("betterquesting.api2.utils.BQThreadedIO"), "INSTANCE");
        long start = System.nanoTime(), deadline = start + TimeUnit.SECONDS.toNanos(30);
        String[] worker = new String[2];
        for (int phase = 0; phase < 2; phase++) {
            // NetQuestSync/NetChapterSync enqueue a builder which then enqueues PacketSender.
            // Barrier 1 drains those builders; barrier 2 drains their nested send/split jobs.
            Future<?> barrier = (Future<?>)UiGalleryBatch.call(io, "enqueue", new Callable<String>() {
                @Override public String call() { return Thread.currentThread().getName(); }
            });
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new java.util.concurrent.TimeoutException("Owned BQ pre-login barrier deadline");
            worker[phase] = String.valueOf(barrier.get(remaining, TimeUnit.NANOSECONDS));
        }
        batch.q.check("batch.bq-barrier.after-drain", ((List<?>)players).isEmpty() && worker[0].equals(worker[1]),
            "Both barriers ran on the same real BQ worker; player list remains empty before the callback returns");
        JsonObject proof = new JsonObject();
        proof.addProperty("barriers", 2); proof.addProperty("worker", worker[0]);
        proof.addProperty("elapsedMillis", (System.nanoTime() - start) / 1e6);
        proof.addProperty("playersBeforeAndAfter", 0);
        proof.addProperty("bqCodeSource", String.valueOf(io.getClass().getProtectionDomain().getCodeSource().getLocation()));
        proof.addProperty("scope", "Owned QA serverStarted scheduling only: drains already queued broadcasts before first login; no BQ jar, packet, quest data or product changed; not a BQ or remote acceptance claim");
        batch.q.receipt.add("ownedBqStartupBarrier", proof);
        System.out.println("OWNED_BQ_STARTUP_BARRIER " + proof);
    }

    private UiGalleryBatchBqStartup() {}
}
