package com.miaokatze.gtsr.client.encounter;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Real overlay owner selection; only the three drawing entrypoints become recorders, avoiding GL. */
public final class SceneBossHudOwnerCheck {

    private static final UUID PLAYER = UUID.fromString("784275ef-7d48-4039-858b-f08421d5d809");
    private static sun.misc.Unsafe unsafe;
    private static int draws, drawnKind;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "instrument".equals(args[0])) {
            instrument(args[1], args[2]);
            return;
        }
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
        JsonObject report = new JsonObject();
        report.addProperty("scope", "Actual SceneBossSignal/SceneBossCache/EncounterSignals/EncounterClient.overlay; drawing entrypoints recorded; independent simulated clocks; no game loop or GL");
        JsonArray results = new JsonArray();
        boolean stable = true;
        for (int kind = 2; kind <= 3; kind++) {
            JsonObject result = scenario(kind);
            results.add(result);
            stable &= result.get("firstMissingTick").getAsInt() == -1;
        }
        report.add("results", results);
        report.addProperty("status", stable ? "PASS" : "FAIL");
        if (args.length > 0) Files.write(Paths.get(args[0]), report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(report);
        if (!stable) throw new AssertionError("A valid industrial scene lost its HUD owner during 90 client ticks without a server leave packet");
    }

    private static JsonObject scenario(int kind) throws Exception {
        Minecraft mc = allocate(Minecraft.class);
        WorldClient world = allocate(WorldClient.class);
        EntityClientPlayerMP player = allocate(EntityClientPlayerMP.class);
        player.worldObj = world;
        player.dimension = 78;
        set(Entity.class, player, "entityUniqueID", PLAYER);
        set(World.class, world, "provider", allocate(WorldProviderProsperityRuins.class));
        ArrayList<Entity> entities = new ArrayList<>();
        EntityOldEcho boss = allocate(EntityOldEcho.class);
        DataWatcher watcher = new DataWatcher(boss);
        watcher.addObject(21, "echo:r7:224:" + (kind == 2 ? "subsided_factory" : "fallen_foundry") + ":0:100:200:compact-prefab");
        set(Entity.class, boss, "dataWatcher", watcher);
        entities.add(boss);
        set(World.class, world, "loadedEntityList", entities);
        mc.theWorld = world;
        mc.thePlayer = player;
        set(Minecraft.class, null, "theMinecraft", mc);
        EncounterSignals.clear();
        set(EncounterSignals.class, null, "world", world);
        Field cacheField = EncounterSignals.class.getDeclaredField("SCENE");
        cacheField.setAccessible(true);
        SceneBossCache cache = (SceneBossCache) cacheField.get(null);
        cache.receive(world, 78, PLAYER, packet(kind));
        EncounterClient owner = new EncounterClient();
        RenderGameOverlayEvent.Post event = new RenderGameOverlayEvent.Post(
            new RenderGameOverlayEvent(0, null, 0, 0), RenderGameOverlayEvent.ElementType.ALL);
        check(render(owner, event) == kind, "initial packet selects the real scene owner");
        int firstMissing = -1;
        for (int tick = 1; tick <= 90; tick++) {
            EncounterSignals.tick();
            if (render(owner, event) != kind && firstMissing < 0) firstMissing = tick;
        }
        JsonObject result = new JsonObject();
        result.addProperty("kind", kind);
        result.addProperty("simulatedServerPauseMillis", 4500);
        result.addProperty("clientTicks", 90);
        result.addProperty("firstMissingTick", firstMissing);
        result.addProperty("worldUnchanged", mc.theWorld == world);
        result.addProperty("playerUnchanged", mc.thePlayer == player);
        result.addProperty("leavePacketsDuringPause", 0);
        cache.receive(world, 78, PLAYER, packet(0));
        check(render(owner, event) == 0, "explicit server leave still clears the real owner");
        result.addProperty("explicitLeaveCleared", true);
        return result;
    }

    private static SceneBossSignal packet(int kind) {
        SceneBossSignal source = new SceneBossSignal();
        source.dimension = 78;
        source.player = PLAYER;
        source.kind = kind;
        if (kind != 0) {
            source.site = "echo:r7:224:" + (kind == 2 ? "subsided_factory" : "fallen_foundry") + ":0:100:200:compact-prefab";
            source.code = kind == 2 ? "dc-08" : "dc-02";
            source.total = source.remaining = 9;
            source.maxHealth = kind == 2 ? 2000 : 2400;
        }
        ByteBuf bytes = Unpooled.buffer();
        try {
            source.toBytes(bytes);
            SceneBossSignal decoded = new SceneBossSignal();
            decoded.fromBytes(bytes);
            check(decoded.valid, "real scene wire decoder accepts the fixture");
            return decoded;
        } finally { bytes.release(); }
    }

    public static void record(SceneBossSignal scene) { draws++; drawnKind = scene.kind; }
    public static void fallback() { draws++; drawnKind = -1; }

    private static int render(EncounterClient owner, RenderGameOverlayEvent.Post event) {
        draws = drawnKind = 0;
        owner.overlay(event);
        check(draws <= 1, "at most one HUD owner draws per overlay");
        return drawnKind;
    }

    private static void instrument(String input, String output) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(Files.readAllBytes(Paths.get(input))).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("drawScene") && !name.equals("drawKing") && !name.equals("draw")) return method;
                method.visitCode();
                String check = SceneBossHudOwnerCheck.class.getName().replace('.', '/');
                if (name.equals("drawScene")) {
                    method.visitVarInsn(Opcodes.ALOAD, 2);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, check, "record",
                        "(Lcom/miaokatze/gtsr/common/dimension/prosperity/encounter/SceneBossSignal;)V", false);
                } else method.visitMethodInsn(Opcodes.INVOKESTATIC, check, "fallback", "()V", false);
                method.visitInsn(Opcodes.RETURN);
                method.visitMaxs(1, 3);
                method.visitEnd();
                return null;
            }
        }, 0);
        Files.write(Paths.get(output), writer.toByteArray());
    }

    private static void set(Class<?> owner, Object object, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe.allocateInstance(type)); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
