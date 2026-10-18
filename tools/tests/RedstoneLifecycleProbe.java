import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import net.minecraft.launchwrapper.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;

/** Real GT205/Mixin micro bootstrap; no Minecraft/FML server launch. */
public final class RedstoneLifecycleProbe {
    public static void main(String[] args) throws Exception {
        ArrayList<URL> urls = new ArrayList<>();
        for (String item : System.getProperty("java.class.path").split(File.pathSeparator))
            urls.add(new File(item).toURI().toURL());
        Launch.classLoader = new LaunchClassLoader(urls.toArray(URL[]::new));
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        for (String prefix : new String[] { "org.spongepowered.", "com.llamalad7.mixinextras.", "org.lwjgl.",
            "RedstoneLifecycleProbe" }) Launch.classLoader.addClassLoaderExclusion(prefix);
        Field platform = MixinBootstrap.class.getDeclaredField("platform");
        platform.setAccessible(true);
        platform.set(null, new org.spongepowered.asm.launch.platform.MixinPlatformManager() {
            @Override public void init() {}
            @Override public Collection<String> getPhaseProviderClasses() { return Collections.emptyList(); }
        });
        MixinBootstrap.init();
        Class.forName("com.llamalad7.mixinextras.MixinExtrasBootstrap").getMethod("init").invoke(null);
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("mixins.gtsr.redstone-probe.json");
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Launch.classLoader.registerTransformer("org.spongepowered.asm.mixin.transformer.Proxy");
        Class<?> runtime = Class.forName("RedstoneLifecycleRuntime", true, Launch.classLoader);
        Method exercise = runtime.getMethod("exercise"); exercise.setAccessible(true);
        Object report = exercise.invoke(null);
        Files.writeString(Path.of(args[0]), report.toString());
        System.out.println(report);
    }
}

class RedstoneLifecycleRuntime {
    static sun.misc.Unsafe unsafe;
    static com.google.gson.JsonArray checks = new com.google.gson.JsonArray();
    static void check(boolean pass, String name) {
        var row = new com.google.gson.JsonObject(); row.addProperty("name", name); row.addProperty("pass", pass);
        checks.add(row);
    }
    static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    static Fixture fixture(int count) throws Exception {
        Fixture f = (Fixture) unsafe.allocateInstance(Fixture.class);
        for (Class<?> c = Fixture.class; c != null; c = c.getSuperclass()) for (Field field : c.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (List.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true); field.set(f, field.getType().isInterface() ? new ArrayList<>() : field.getType().getConstructor().newInstance());
            }
        }
        set(f, "mInventory", new net.minecraft.item.ItemStack[2]);
        f.world = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        f.world.tiles = new HashMap<>(); f.hatches = new ArrayList<>();
        f.base = (gregtech.api.interfaces.tileentity.IGregTechTileEntity) Proxy.newProxyInstance(
            Fixture.class.getClassLoader(), new Class<?>[] { gregtech.api.interfaces.tileentity.IGregTechTileEntity.class },
            (p, m, a) -> {
                if (m.getName().equals("isServerSide")) return true;
                if (m.getName().equals("getMetaTileEntity")) return f;
                if (m.getReturnType() == boolean.class) return false;
                if (m.getReturnType() == int.class) return 0;
                if (m.getReturnType() == long.class) return 0L;
                return null;
            });
        for (int i = 0; i < count; i++) {
            var h = (com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch)
                unsafe.allocateInstance(com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch.class);
            var tile = (WitnessTile) unsafe.allocateInstance(WitnessTile.class);
            set(h, "mBaseMetaTileEntity", tile); tile.setMetaTileEntity(h);
            if (tile.getMetaTileEntity() != h) throw new AssertionError("invalid hatch fixture");
            f.world.tiles.put(i, tile); f.hatches.add(h);
        }
        return f;
    }
    public static Object exercise() throws Exception {
        Field u = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); u.setAccessible(true);
        unsafe = (sun.misc.Unsafe) u.get(null);
        Fixture f = fixture(5);
        check(!f.checkStructure(true, f.base), "five hatches across two real checker instances rejected");
        check(f.hatches.stream().allMatch(h -> h.getController() == null), "overlimit leaves no bindings");
        f = fixture(4);
        check(f.checkStructure(true, f.base), "four hatches across two real checker instances accepted");
        Fixture firstOwner = f;
        check(f.hatches.stream().allMatch(h -> h.getController() == firstOwner), "success commits all bindings");
        f.fail = true;
        check(!f.checkStructure(true, f.base), "ordinary later-piece failure rejects structure");
        check(f.hatches.stream().allMatch(h -> h.getController() == null), "ordinary failure clears all bindings");
        f.fail = false; f.checkStructure(true, f.base);
        var removed = f.hatches.remove(3); f.world.tiles.remove(3);
        check(f.checkStructure(true, f.base) && removed.getController() == null, "removed hatch loses old binding");
        f.explode = true;
        try { f.checkStructure(true, f.base); check(false, "checkMachine exception propagates"); }
        catch (IllegalStateException expected) { check(true, "checkMachine exception propagates"); }
        check(f.hatches.stream().allMatch(h -> h.getController() == null), "exception rolls back binding");
        f.explode = false;
        check(f.checkStructure(true, f.base), "next check recovers after exception");
        f = fixture(5); f.probeThenRescan = true;
        check(f.checkStructure(true, f.base), "clearHatches discards failed probe before accepted four-hatch rescan");
        check(f.hatches.get(4).getController() == null, "discarded probe hatch stays unbound");
        f = fixture(4); f.repeat = true;
        check(f.checkStructure(true, f.base), "overlapping piece visits count each hatch once");
        check(f.deferred, "bindings remain pending while checkMachine is executing");
        f.fail = true; Fixture owner = f;
        check(f.checkStructure(false, f.base) && f.hatches.get(0).getController() == owner,
            "cached checkStructure does not discard established bindings");
        f = fixture(4); f.nested = true;
        check(f.checkStructure(true, f.base) && f.deferred,
            "nested checkMachine_TT commits only at outermost successful boundary");
        f.innerExplode = true;
        try { f.checkStructure(true, f.base); check(false, "nested exception propagates"); }
        catch (IllegalStateException expected) { check(true, "nested exception propagates"); }
        check(f.hatches.stream().allMatch(h -> h.getController() == null), "nested exception rolls back every scope");
        f.innerExplode = false;
        check(f.tt(), "TT entry recovers after nested exception without stale depth");
        f = fixture(4); f.partner = fixture(4);
        check(f.checkStructure(true, f.base), "different nested controllers keep independent hatch counts");
        owner = f; Fixture other = f.partner;
        check(f.hatches.get(0).getController() == owner && other.hatches.get(0).getController() == other,
            "different nested controllers retain their own bindings");
        f = fixture(4); f.unloaded = true;
        check(!f.checkStructure(true, f.base) && f.hatches.stream().allMatch(h -> h.getController() == null),
            "real blockNotLoaded error rolls back the complete check");
        f = fixture(4); f.tt(); f.clearHatches();
        check(f.hatches.stream().allMatch(h -> h.getController() == null), "native clearHatches outside check removes bindings");
        var report = new com.google.gson.JsonObject(); report.add("checks", checks);
        report.addProperty("pass", checks.asList().stream().allMatch(e -> e.getAsJsonObject().get("pass").getAsBoolean()));
        report.addProperty("mode", "real GT checkStructure, clearHatches, StructureChecker and applied production mixins; sparse World; no FML server");
        return report;
    }
    static final class Fixture extends com.miaokatze.gtsr.common.machine.MTELargeCokeOven {
        SparseWorld world;
        List<com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch> hatches;
        gregtech.api.interfaces.tileentity.IGregTechTileEntity base;
        boolean fail, explode, probeThenRescan, repeat, nested, inNested, innerExplode, unloaded, deferred;
        Fixture partner;
        Fixture() { super("unused"); }
        @Override public void checkMachine(gregtech.api.interfaces.tileentity.IGregTechTileEntity t,
            net.minecraft.item.ItemStack stack, List<gregtech.api.structure.error.StructureError> errors) {
            scan(errors, hatches.size());
            if (probeThenRescan) { clearHatches(); errors.clear(); scan(errors, 4); }
            if (repeat) scan(errors, hatches.size());
            if (nested && !inNested) {
                inNested = true;
                try { tt(); } finally { inNested = false; }
            }
            if (partner != null) partner.checkStructure(true, partner.base);
            deferred = hatches.stream().allMatch(h -> h.getController() != this);
            if (unloaded) new gregtech.api.structure.StructureChecker<>(this, false, errors)
                .blockNotLoaded(ELEMENT, world, 100, 64, 0, 0, 0, 0);
            if (fail) new gregtech.api.structure.StructureChecker<>(this, true, errors)
                .visit(ELEMENT, world, 100, 64, 0, 0, 0, 0);
            if (explode || inNested && innerExplode) throw new IllegalStateException("fixture checkMachine failure");
        }
        boolean tt() { return checkMachine_TT(base); }
        void scan(List<gregtech.api.structure.error.StructureError> errors, int count) {
            for (int start = 0; start < count; start += 3) {
                var checker = new gregtech.api.structure.StructureChecker<>(this, true, errors);
                for (int i = start; i < Math.min(count, start + 3); i++)
                    checker.visit(ELEMENT, world, i, 64, 0, 0, 0, 0);
            }
        }
        @Override protected void onStructureCheckFinished(gregtech.api.interfaces.tileentity.IGregTechTileEntity t) {}
    }
    static final com.gtnewhorizon.structurelib.structure.IStructureElement<Fixture> ELEMENT =
        new com.gtnewhorizon.structurelib.structure.IStructureElement<>() {
            public boolean check(Fixture f, net.minecraft.world.World w, int x, int y, int z) { return false; }
            public boolean spawnHint(Fixture f, net.minecraft.world.World w, int x, int y, int z, net.minecraft.item.ItemStack s) { return false; }
            public boolean placeBlock(Fixture f, net.minecraft.world.World w, int x, int y, int z, net.minecraft.item.ItemStack s) { return false; }
        };
    static final class WitnessTile extends gregtech.api.metatileentity.BaseMetaTileEntity {
    }
    static final class SparseWorld extends net.minecraft.world.World {
        Map<Integer, net.minecraft.tileentity.TileEntity> tiles;
        SparseWorld() { super(null, "unused", (net.minecraft.world.WorldProvider) null, null, null); }
        @Override public net.minecraft.tileentity.TileEntity getTileEntity(int x, int y, int z) { return tiles.get(x); }
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() { throw new UnsupportedOperationException(); }
        @Override protected int func_152379_p() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.entity.Entity getEntityByID(int id) { throw new UnsupportedOperationException(); }
    }
}
