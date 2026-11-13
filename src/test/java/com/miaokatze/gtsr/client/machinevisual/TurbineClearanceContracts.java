// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.machinevisual;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.mixin.ChunkMotionClearanceMixin;

/** Real stamp and mutation-hook contracts; no Minecraft launch, renderer, GL context or Testlab fixture. */
public final class TurbineClearanceContracts {

    private static int checks;

    private TurbineClearanceContracts() {}

    public static void main(String[] args) throws Exception {
        stampContracts();
        hookContracts();
        bindingContracts();
        System.out.println("PASS TurbineClearance contracts: " + checks);
    }

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    private static void stampContracts() throws Exception {
        StubWorld world = allocate(StubWorld.class);
        world.loaded = new HashMap<>();
        MachineVisualClearance.GroupStamp stamp = stamp(
            -1,
            16,
            -17,
            new int[] { 0, 0, 0, 1, 15, 1, -16, -1, 17, 0, 0, 0 },
            new int[] { 17, 16, -16, 0, 0, 0 });
        int[] coords = (int[]) field(MachineVisualClearance.GroupStamp.class, "coords").get(stamp);
        Set<String> sections = new HashSet<>();
        for (int i = 0; i < coords.length; i += 3) {
            sections.add(coords[i] + ":" + coords[i + 1] + ":" + coords[i + 2]);
            world.load(coords[i], coords[i + 2]);
        }
        check(
            sections.equals(new HashSet<>(Arrays.asList("-1:1:-2", "0:1:-1", "-2:0:0", "1:2:-3")))
                && coords.length == 12,
            "member and swept-air sections include signed boundaries and deduplicate overlap");
        check(!stamp.reuse(world, 0), "new stamp has no verified clearance");
        check(stamp.capture(world), "all tracked loaded sections can be captured");
        check(!stamp.reuse(world, 0), "capturing revisions alone never grants safety");
        verifyUntil(stamp, 100);
        check(stamp.reuse(world, 99), "unchanged loaded chunks reuse before expiry");
        check(!stamp.reuse(world, 100), "deadline tick requires a fresh safety scan");
        verifyUntil(stamp, 100);

        TrackedChunk watched = (TrackedChunk) world.loaded.get(key(-1, -2));
        watched.revisions[2]++;
        check(stamp.reuse(world, 10), "unwatched vertical section does not invalidate the group");
        watched.revisions[1]++;
        check(!stamp.reuse(world, 10), "watched section mutation invalidates immediately within the tick");
        check(!stamp.reuse(world, 11), "invalidated group cannot reuse its previous successful scan");
        check(stamp.capture(world), "mutated sections can establish a new revision baseline");
        check(!stamp.reuse(world, 11), "new baseline still needs an actual successful clearance scan");
        verifyUntil(stamp, 100);
        check(stamp.reuse(world, 11), "newly verified changed geometry can reuse");

        world.loaded.put(key(-1, -2), world.newTracked());
        check(!stamp.reuse(world, 12), "replacement chunk with identical revision numbers invalidates by identity");
        check(stamp.capture(world), "replacement tracked chunk can be recaptured");
        verifyUntil(stamp, 100);
        Chunk saved = world.loaded.put(key(-1, -2), allocate(EmptyChunk.class));
        check(!stamp.reuse(world, 13), "unloaded EmptyChunk cannot retain clearance");
        world.loaded.put(key(-1, -2), saved);
        check(!stamp.reuse(world, 14), "reloading the same chunk cannot resurrect an invalidated stamp");
        check(stamp.capture(world), "returned loaded chunk permits another real scan");
        verifyUntil(stamp, 100);
        world.loaded.put(key(-1, -2), allocate(Chunk.class));
        check(!stamp.reuse(world, 15), "chunk without mutation tracking fails closed");
        check(!stamp.capture(world), "untracked chunks cannot create a cache baseline");

        for (int y : new int[] { -1, 256 }) {
            MachineVisualClearance.GroupStamp outside = stamp(0, y, 0, new int[] { 0, 0, 0 }, new int[0]);
            int lookups = world.lookups;
            check(!outside.capture(world), "outside build height cannot be cached: " + y);
            check(world.lookups == lookups, "invalid height fails before querying chunks: " + y);
        }
        for (int coordinate : new int[] { -30000000, 30000000 }) {
            MachineVisualClearance.GroupStamp boundary = stamp(
                coordinate,
                255,
                coordinate,
                new int[] { 0, 0, 0 },
                new int[0]);
            int[] actual = (int[]) field(MachineVisualClearance.GroupStamp.class, "coords").get(boundary);
            check(
                Arrays.equals(actual, new int[] { coordinate >> 4, 15, coordinate >> 4 }),
                "world horizontal boundary and highest section round-trip: " + coordinate);
        }

        StubWorld physical = allocate(StubWorld.class);
        physical.loaded = new HashMap<>();
        TrackedChunk changed = physical.newTracked();
        changed.hooks = new HookSubject();
        physical.loaded.put(key(0, 0), changed);
        MachineVisualClearance.GroupStamp input = stamp(0, 32, 0, new int[] { 0, 0, 0 }, new int[0]);
        check(input.capture(physical), "real mutation tracker can provide the safety baseline");
        verifyUntil(input, 100);
        check(input.reuse(physical, 1), "baseline is reusable before the physical input changes");
        invoke(
            changed.hooks,
            "gtsr$metadata",
            0,
            32,
            0,
            7,
            new CallbackInfoReturnable<Boolean>("metadata", false, true));
        check(!input.reuse(physical, 1), "real successful metadata hook revokes cached safety in the same tick");
    }

    private static MachineVisualClearance.GroupStamp stamp(int x, int y, int z, int[] members, int[] air)
        throws Exception {
        MachineVisualClient.Entry entry = allocate(MachineVisualClient.Entry.class);
        field(MachineVisualClient.Entry.class, "originX").setInt(entry, x);
        field(MachineVisualClient.Entry.class, "originY").setInt(entry, y);
        field(MachineVisualClient.Entry.class, "originZ").setInt(entry, z);
        MachineVisualClearance.GroupGeometry geometry = allocate(MachineVisualClearance.GroupGeometry.class);
        field(MachineVisualClearance.GroupGeometry.class, "members").set(geometry, members);
        field(MachineVisualClearance.GroupGeometry.class, "air").set(geometry, air);
        return new MachineVisualClearance.GroupStamp(entry, geometry);
    }

    private static void verifyUntil(MachineVisualClearance.GroupStamp stamp, long tick) throws Exception {
        field(MachineVisualClearance.GroupStamp.class, "verifiedUntil").setLong(stamp, tick);
    }

    private static void hookContracts() throws Exception {
        HookSubject hooks = new HookSubject();
        Field revisions = field(ChunkMotionClearanceMixin.class, "gtsr$motionRevisions");
        invoke(hooks, "gtsr$tileRemoved", 0, 32, 0, new CallbackInfo("remove", false));
        check(revisions.get(hooks) == null, "unobserved chunks allocate no tracking array on writes");
        check(hooks.gtsr$motionRevision(2) == 0, "observation lazily starts section tracking");
        invoke(hooks, "gtsr$block", 0, 32, 0, null, 0, new CallbackInfoReturnable<Boolean>("block", false, false));
        check(hooks.gtsr$motionRevision(2) == 0, "failed block placement leaves revisions stable");
        invoke(hooks, "gtsr$block", 0, 32, 0, null, 0, new CallbackInfoReturnable<Boolean>("block", false, true));
        check(hooks.gtsr$motionRevision(2) == 1, "successful physical block write dirties its section");
        invoke(hooks, "gtsr$metadata", 0, 32, 0, 7, new CallbackInfoReturnable<Boolean>("metadata", false, true));
        check(hooks.gtsr$motionRevision(2) == 2, "metadata packet change dirties the same section");
        invoke(hooks, "gtsr$tileAdded", 0, 32, 0, null, new CallbackInfo("add", false));
        invoke(hooks, "gtsr$tileRemoved", 0, 32, 0, new CallbackInfo("remove", false));
        check(hooks.gtsr$motionRevision(2) == 4, "tile entity insertion and removal both invalidate clearance");
        invoke(hooks, "gtsr$tileRemoved", 0, -1, 0, new CallbackInfo("remove", false));
        invoke(hooks, "gtsr$tileRemoved", 0, 256, 0, new CallbackInfo("remove", false));
        check(hooks.gtsr$motionRevision(2) == 4, "out-of-height mutations never alias valid sections");
        invoke(hooks, "gtsr$chunkPacket", new byte[0], 1 << 2, 1 << 3, false, new CallbackInfo("fill", false));
        check(
            hooks.gtsr$motionRevision(2) == 5 && hooks.gtsr$motionRevision(3) == 1 && hooks.gtsr$motionRevision(4) == 0,
            "partial chunk packet invalidates primary and extra section masks only");
        invoke(hooks, "gtsr$chunkPacket", new byte[0], 0, 0, true, new CallbackInfo("fill", false));
        check(
            hooks.gtsr$motionRevision(2) == 6 && hooks.gtsr$motionRevision(15) == 1,
            "full chunk packet invalidates every tracked section even with empty masks");
        invoke(hooks, "gtsr$storage", new ExtendedBlockStorage[16], new CallbackInfo("storage", false));
        check(
            hooks.gtsr$motionRevision(2) == 7 && hooks.gtsr$motionRevision(15) == 2,
            "direct section storage replacement invalidates all sections");
    }

    /**
     * Checks actual compiled hook selectors against the real MCP Chunk resource, without claiming Mixin application.
     */
    private static void bindingContracts() throws Exception {
        ClassNode chunk = classNode("net/minecraft/world/chunk/Chunk.class");
        ClassNode mixin = classNode("com/miaokatze/gtsr/mixin/ChunkMotionClearanceMixin.class");
        Map<String, String> targets = new HashMap<>();
        targets.put("func_150807_a", "(IIILnet/minecraft/block/Block;I)Z");
        targets.put("setBlockMetadata", "(IIII)Z");
        targets.put("func_150812_a", "(IIILnet/minecraft/tileentity/TileEntity;)V");
        targets.put("removeTileEntity", "(III)V");
        targets.put("fillChunk", "([BIIZ)V");
        targets.put("setStorageArrays", "([Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;)V");
        Set<String> bound = new HashSet<>();
        for (MethodNode method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                @SuppressWarnings("unchecked")
                java.util.List<String> selectors = (java.util.List<String>) annotationValue(annotation, "method");
                check(
                    Integer.valueOf(1)
                        .equals(annotationValue(annotation, "require")),
                    "mutation hook requires binding");
                for (String selector : selectors) {
                    check(targets.containsKey(selector), "known physical mutation selector: " + selector);
                    boolean found = false;
                    for (MethodNode candidate : chunk.methods)
                        if (candidate.name.equals(selector) && candidate.desc.equals(targets.get(selector)))
                            found = true;
                    check(found, "real Chunk declares exact hook target: " + selector);
                    check(bound.add(selector), "physical mutation selector is bound once: " + selector);
                }
            }
        }
        check(bound.equals(targets.keySet()), "all six physical mutation entry points have mandatory hooks");
        try (InputStream stream = resource("mixins.gtsr.json")) {
            JsonObject config = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            int client = 0, common = 0;
            for (JsonElement value : config.getAsJsonArray("client")) if (value.getAsString()
                .equals("ChunkMotionClearanceMixin")) client++;
            for (JsonElement value : config.getAsJsonArray("mixins")) if (value.getAsString()
                .equals("ChunkMotionClearanceMixin")) common++;
            check(client == 1 && common == 0, "chunk tracker is registered exactly once and only on client");
        }
    }

    private static Object annotationValue(AnnotationNode node, String key) {
        for (int i = 0; i < node.values.size(); i += 2)
            if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        return null;
    }

    private static ClassNode classNode(String resource) throws Exception {
        try (InputStream stream = resource(resource)) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static InputStream resource(String name) {
        InputStream stream = TurbineClearanceContracts.class.getClassLoader()
            .getResourceAsStream(name);
        if (stream == null) throw new AssertionError("Missing real classpath resource " + name);
        return stream;
    }

    private static void invoke(HookSubject instance, String name, Object... arguments) throws Exception {
        for (Method method : ChunkMotionClearanceMixin.class.getDeclaredMethods()) if (method.getName()
            .equals(name)) {
                method.setAccessible(true);
                method.invoke(instance, arguments);
                return;
            }
        throw new AssertionError("Missing physical mutation hook " + name);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Object unsafe = field(unsafeClass, "theUnsafe").get(null);
        return type.cast(
            unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, type));
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    private static final class HookSubject extends ChunkMotionClearanceMixin {
    }

    private static final class TrackedChunk extends Chunk implements MotionSectionChanges {

        long[] revisions;
        HookSubject hooks;

        private TrackedChunk() {
            super((World) null, 0, 0);
        }

        @Override
        public long gtsr$motionRevision(int section) {
            return hooks == null ? revisions[section] : hooks.gtsr$motionRevision(section);
        }
    }

    private static final class StubWorld extends World {

        Map<Long, Chunk> loaded;
        int lookups;

        private StubWorld() {
            super(null, "unused", (WorldProvider) null, null, null);
        }

        TrackedChunk newTracked() throws Exception {
            TrackedChunk chunk = allocate(TrackedChunk.class);
            chunk.revisions = new long[16];
            return chunk;
        }

        void load(int x, int z) throws Exception {
            if (!loaded.containsKey(key(x, z))) loaded.put(key(x, z), newTracked());
        }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) {
            lookups++;
            Chunk chunk = loaded.get(key(x, z));
            if (chunk == null) throw new AssertionError("Unexpected chunk lookup " + x + ":" + z);
            return chunk;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected int func_152379_p() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getEntityByID(int id) {
            throw new UnsupportedOperationException();
        }
    }
}
