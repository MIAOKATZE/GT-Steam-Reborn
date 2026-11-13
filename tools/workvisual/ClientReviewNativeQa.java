package audit;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.gameevent.TickEvent;

/** Owned TASK03 extension. Native cold controllers and explicit client-only candidate-state benchmark. */
final class ClientReviewNativeQa {
    private final Task03ClientQa h;
    private final JsonObject receipt = new JsonObject();
    private final JsonArray frameSamples = new JsonArray(), qualitySamples = new JsonArray();
    private final List<Object> benchmarkEntries = new ArrayList<Object>(), originalSnapshots = new ArrayList<Object>();
    private final Object[] coldClientHosts = new Object[512];
    private Object world, clientWorldAtBootstrap;
    private int controllerId = -1, benchmarkPhase, benchmarkTicks, lastTick = -1;
    private volatile int placed, cleared;
    private long queryStart, memberStart, airStart, bootstrapTick, firstNewEntryTick = -1, lastSample = -1;
    private float lastPartial;
    private String lastMachine = "";
    private int sameTickPairs, recordedFrames;
    private volatile boolean bootstrapComplete, clearRequested;

    ClientReviewNativeQa(Task03ClientQa host) {
        h = host;
        receipt.addProperty("status", "PREPARED_NOT_RUN");
        receipt.addProperty("scope", "Registration=512 real unformed AtmosphericCentrifuge controllers, then one naturally working TASK03 machine. Registry/candidate caps=512/512. Twenty-state benchmark explicitly assigns client-only authority DTOs over real incomplete worlds; it is query-count feedback, not twenty naturally producing multiblocks or a CPU/GC benchmark. Original authority DTOs are restored before the natural fixture starts.");
        receipt.add("controlledQualitySamples", qualitySamples);
        receipt.add("nativeFrameSamples", frameSamples);
        h.q.receipt.add("clientReview", receipt);
    }

    private static int[] at(int index) {
        return new int[] { 88 + (index & 7) * 2, 100 + (index >> 6) * 2, 40 + (index >> 3 & 7) * 2 };
    }

    boolean beforeServer(Object player) throws Exception {
        if (bootstrapComplete) {
            if (clearRequested && cleared < placed) {
                for (int n = 0; n < 16 && cleared < placed; n++, cleared++) {
                    int[] p = at(cleared);
                    Object tile = Task03ClientQa.call(world, "getTileEntity|func_147438_o", p[0], p[1], p[2]);
                    if (tile == null || Task03ClientQa.number(Task03ClientQa.call(tile, "getMetaTileID")) != controllerId)
                        throw new AssertionError("Owned cold controller changed before cleanup " + cleared);
                    Task03ClientQa.call(world, "setBlockToAir|func_147468_f", p[0], p[1], p[2]);
                }
            }
            return false;
        }
        if (world == null) {
            Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.LAB + "TestLabCommand"), "enter", player);
            world = Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.LAB + "TestLabDimension"), "world");
            for (Object prototype : (Object[]) Task03ClientQa.field(Task03ClientQa.type("gregtech.api.GregTechAPI"), "METATILEENTITIES"))
                if (prototype != null && prototype.getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge")) {
                    controllerId = (int) Task03ClientQa.number(Task03ClientQa.call(Task03ClientQa.call(prototype, "getStackForm", 1L), "getItemDamage|func_77960_j"));
                    break;
                }
            if (controllerId < 0) throw new AssertionError("Registered atmospheric controller prototype missing");
            Task03ClientQa.call(Task03ClientQa.field(player, "playerNetServerHandler|field_71135_a"), "setPlayerLocation|func_147364_a", 94.5, 100D, 23D, 0F, 0F);
            Object caps = Task03ClientQa.field(player, "capabilities|field_71075_bZ");
            Task03ClientQa.set(caps, "isFlying|field_75100_b", true);
            Task03ClientQa.call(player, "sendPlayerAbilities|func_71016_p");
        }
        for (int n = 0; n < 16 && placed < 512; n++, placed++) {
            int[] p = at(placed);
            Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.LAB + "TestLabFixtures"), "machine", world, p[0], p[1], p[2], controllerId);
        }
        return true;
    }

    private Object clientHost(int index) throws Exception {
        int[] p = at(index);
        Object clientWorld = Task03ClientQa.field(h.q.mc, "theWorld|field_71441_e");
        if (clientWorld == null) return null;
        Object tile = Task03ClientQa.call(clientWorld, "getTileEntity|func_147438_o", p[0], p[1], p[2]);
        return tile == null ? null : Task03ClientQa.call(tile, "getMetaTileEntity");
    }

    private long counter(String key) throws Exception {
        return Task03ClientQa.number(Task03ClientQa.field(Task03ClientQa.type(Task03ClientQa.CLIENT), key));
    }

    void client() throws Exception {
        if (!bootstrapComplete) {
            if (placed < 512) return;
            if (benchmarkEntries.isEmpty()) {
                for (int i = 0; i < 512; i++) {
                    Object host = clientHost(i);
                    if (host == null || Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "entry", host) == null) return;
                    coldClientHosts[i] = host;
                }
                clientWorldAtBootstrap = Task03ClientQa.field(h.q.mc, "theWorld|field_71441_e");
                receipt.addProperty("initialClientWorldIdentity", System.identityHashCode(clientWorldAtBootstrap));
                receipt.addProperty("initialClientWorldClass", clientWorldAtBootstrap.getClass().getName());
                h.check("review.registry-filled", counter("registeredHosts") == 512, "512 real cold client controllers filled the registration table before the new working host exists");
                receipt.addProperty("initialRegisteredHosts", counter("registeredHosts"));
                for (int i = 0; i < 20; i++) {
                    Object entry = Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "entry", clientHost(i));
                    benchmarkEntries.add(entry);
                    originalSnapshots.add(Task03ClientQa.field(entry, "snapshot"));
                }
                Task03ClientQa.set(Task03ClientQa.field(h.q.mc, "gameSettings|field_71474_y"), "limitFramerate|field_74350_i", 120);
                quality("OFF");
                beginCounters();
            }
            for (int i = 0; i < benchmarkEntries.size(); i++) controlledFrame(benchmarkEntries.get(i));
            if (++benchmarkTicks < 20) return;
            String name = new String[] { "OFF", "LOW", "STANDARD" }[benchmarkPhase];
            JsonObject sample = new JsonObject();
            sample.addProperty("quality", name); sample.addProperty("controlledHosts", 20); sample.addProperty("ticks", benchmarkTicks);
            sample.addProperty("worldQueries", counter("clearanceWorldQueries") - queryStart);
            sample.addProperty("memberChecks", counter("clearanceMemberChecks") - memberStart);
            sample.addProperty("airChecks", counter("clearanceAirChecks") - airStart);
            sample.addProperty("heldMembers", counter("heldMotionMembers"));
            sample.addProperty("scope", "Decoration authority and budget over real incomplete world cells; explicit client authority DTOs; retired spatial-query counters must stay zero; CPU/GC/p50/p95 NOT_RUN");
            qualitySamples.add(sample);
            long expectedMembers = name.equals("OFF") ? 0 : name.equals("LOW") ? 126 : 360;
            h.check("review.controlled-" + name, sample.get("heldMembers").getAsLong() == expectedMembers
                && sample.get("worldQueries").getAsLong() == 0 && sample.get("memberChecks").getAsLong() == 0
                && sample.get("airChecks").getAsLong() == 0, sample.toString());
            if (++benchmarkPhase < 3) {
                quality(new String[] { "OFF", "LOW", "STANDARD" }[benchmarkPhase]);
                benchmarkTicks = 0; beginCounters(); return;
            }
            h.check("review.controlled-low-decoration-budget", qualitySamples.get(1).getAsJsonObject().get("heldMembers").getAsLong() == 126,
                "Seven complete 18-member decorations fit LOW; admission does not scan their incomplete world volume");
            for (int i = 0; i < benchmarkEntries.size(); i++) Task03ClientQa.set(benchmarkEntries.get(i), "snapshot", originalSnapshots.get(i));
            benchmarkEntries.clear();originalSnapshots.clear();
            bootstrapComplete = true;
            bootstrapTick = Task03ClientQa.number(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "clientTick"));
            receipt.addProperty("newHostReleasedAtClientTick", bootstrapTick);
            return;
        }
        if (firstNewEntryTick >= 0 && clearRequested) return;
        if (h.host == null || h.machine == null) return;
        Object newHost = h.clientMachine();
        if (newHost == null) return;
        Object entry = Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "entry", newHost);
        long now = Task03ClientQa.number(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "clientTick"));
        if (entry == null) {
            if (now - bootstrapTick > 150) throw new AssertionError("513th native host still unregistered after150 ticks");
            return;
        }
        if (firstNewEntryTick < 0) {
            firstNewEntryTick = now;
            receipt.addProperty("newHostRegistrationWaitTicks", now - bootstrapTick);
            receipt.addProperty("registeredAtNewHostAdmission", counter("registeredHosts"));
            Object clientWorld = Task03ClientQa.field(h.q.mc, "theWorld|field_71441_e");
            Object player = Task03ClientQa.field(h.q.mc, "thePlayer|field_71439_g");
            int loaded = 0, sameHosts = 0, nearHosts = 0;
            for (int i = 0; i < 512; i++) {
                int[] p = at(i);
                if (Boolean.TRUE.equals(Task03ClientQa.call(clientWorld, "blockExists|func_72899_e", p[0], p[1], p[2]))) loaded++;
                if (clientHost(i) == coldClientHosts[i]) sameHosts++;
                if (((Number) Task03ClientQa.call(player, "getDistanceSq|func_70092_e", p[0] + .5, p[1] + .5, p[2] + .5)).doubleValue() <= 4096) nearHosts++;
            }
            JsonObject premises = new JsonObject();
            premises.addProperty("sameClientWorldObject", clientWorld == clientWorldAtBootstrap);
            premises.addProperty("initialClientWorldIdentity", System.identityHashCode(clientWorldAtBootstrap));
            premises.addProperty("admissionClientWorldIdentity", System.identityHashCode(clientWorld));
            premises.addProperty("coldHostCellsLoaded", loaded);premises.addProperty("sameColdHostObjects", sameHosts);premises.addProperty("coldHostsWithin64Blocks", nearHosts);
            receipt.add("newHostAdmissionPremises", premises);
            h.check("review.513-same-client-world", clientWorld == clientWorldAtBootstrap, premises.toString());
            h.check("review.513-cold-host-premises", loaded == 512 && sameHosts == 512 && nearHosts == 512, premises.toString());
            h.check("review.513-native-registration", counter("registeredHosts") == 512 && now - bootstrapTick <= 150,
                "New real host entered while cold controllers remained loaded; wait=" + (now - bootstrapTick));
        }
        if (Boolean.TRUE.equals(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "isWorking", newHost))
            && Task03ClientQa.number(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "uploadedGroupMask", newHost)) != 0) {
            receipt.addProperty("newHostNativeWorkingAndUploaded", true);
            receipt.addProperty("newHostNonce", Task03ClientQa.number(Task03ClientQa.field(entry, "nonce")));
            receipt.addProperty("newHostSnapshotSequence", Task03ClientQa.number(Task03ClientQa.field(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "snapshot", newHost), "sequence")));
            clearRequested = true;
        }
    }

    private void quality(String name) throws Exception {
        Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "setQuality", Task03ClientQa.en(Task03ClientQa.CLIENT + "$Quality", name));
    }

    private void beginCounters() throws Exception {
        queryStart = counter("clearanceWorldQueries");memberStart = counter("clearanceMemberChecks");airStart = counter("clearanceAirChecks");
    }

    private void controlledFrame(Object entry) throws Exception {
        Object host = Task03ClientQa.field(entry, "host"), tile = Task03ClientQa.field(entry, "tile");
        Constructor<?> ctor = Task03ClientQa.type("com.miaokatze.gtsr.common.machinevisual.MachineVisualSnapshot").getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object snapshot = ctor.newInstance((int) Task03ClientQa.number(Task03ClientQa.field(Task03ClientQa.field(world, "provider|field_73011_w"), "dimensionId|field_76574_g")),
            (int) Task03ClientQa.number(Task03ClientQa.call(tile, "getXCoord")), (int) Task03ClientQa.number(Task03ClientQa.call(tile, "getYCoord")), (int) Task03ClientQa.number(Task03ClientQa.call(tile, "getZCoord")),
            (int) Task03ClientQa.number(Task03ClientQa.call(tile, "getMetaTileID")), (int) Task03ClientQa.number(Task03ClientQa.call(Task03ClientQa.call(host, "getExtendedFacing"), "getIndex")),
            7, 0, 100, 1L, Task03ClientQa.number(Task03ClientQa.field(entry, "nonce")), 1L,
            Task03ClientQa.number(Task03ClientQa.field(Task03ClientQa.call(host, "visualDefinition"), "revision")), Long.MAX_VALUE, 0L, 0L, 0L);
        Task03ClientQa.set(entry, "snapshot", snapshot);Task03ClientQa.set(entry, "receivedAt", System.nanoTime());
        Task03ClientQa.set(entry, "advanceUntil", System.nanoTime() + 1000000000L);
    }

    void render(TickEvent.RenderTickEvent event) throws Exception {
        if (!bootstrapComplete || h.done || h.finishing || event.phase != TickEvent.Phase.END || h.machine == null) return;
        Object host = h.clientMachine(); if (host == null) return;
        Object entry = Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "entry", host);
        if (entry == null || !Boolean.TRUE.equals(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "isAnimating", host))) return;
        int groups = ((List<?>) Task03ClientQa.field(Task03ClientQa.call(host, "visualDefinition"), "groups")).size();
        if (Task03ClientQa.number(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "uploadedGroupMask", host)) != (1 << groups) - 1) return;
        if (Task03ClientQa.number(Task03ClientQa.field(entry, "lastRenderFrame")) != Task03ClientQa.number(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "renderFrame"))) return;
        int tick = (int) Task03ClientQa.number(Task03ClientQa.call(Task03ClientQa.type(Task03ClientQa.CLIENT), "clientTick"));
        long sampled = Task03ClientQa.number(Task03ClientQa.field(entry, "sampledDecorativeMs"));
        if (!lastMachine.equals(h.machineId)) { lastMachine = h.machineId;lastTick = -1;sameTickPairs = 0;recordedFrames = 0; }
        if (lastTick == tick && event.renderTickTime > lastPartial && sampled > lastSample) sameTickPairs++;
        if (recordedFrames++ < 120) {
            JsonObject frame = new JsonObject();frame.addProperty("machine", h.machineId);frame.addProperty("clientTick", tick);
            frame.addProperty("partialTick", event.renderTickTime);frame.addProperty("sampledDecorativeMs", sampled);
            frame.addProperty("previousDecorativeMs", Task03ClientQa.number(Task03ClientQa.field(entry, "previousDecorativeMs")));
            frame.addProperty("decorativeMs", Task03ClientQa.number(Task03ClientQa.field(entry, "decorativeMs")));
            frameSamples.add(frame);
        }
        lastTick = tick;lastPartial = event.renderTickTime;lastSample = sampled;
    }

    void completeCase() throws Exception {
        h.check("review.native-partial-tick", sameTickPairs > 0, "Real TESR frames with increasing partialTick and sampledDecorativeMs within one client tick=" + sameTickPairs);
        JsonObject perCase = new JsonObject();perCase.addProperty("sameTickChangingFrames", sameTickPairs);
        receipt.add(h.machineId, perCase);
        receipt.addProperty("status", "NATIVE_CASES_IN_PROGRESS");
    }

    void finish() {
        receipt.addProperty("coldControllersRemoved", cleared);
        receipt.addProperty("coldControllerCleanup", cleared == 512 ? "COMPLETED" : "INCOMPLETE");
        if (h.failure != null) {
            receipt.addProperty("status", "FAIL");
            receipt.addProperty("primaryFailure", h.failure.toString());
            return;
        }
        h.check("review.cold-controller-cleanup", cleared == 512, "Remove only the512 exact cold controllers placed by this helper before the ordinary owned world unload");
        receipt.addProperty("status", "COMPLETED_SEE_NATIVE_CHECKS");
    }
}
