package audit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.netty.buffer.ByteBuf;

/** Read-only live-client/server observations for the owned v2.1.8 native fixture. */
public final class SceneBossHudProbe {

    private static final String P = "com.miaokatze.gtsr.";

    public static JsonObject server(Object player) throws Exception {
        Object world = field(player, "worldObj|field_70170_p");
        Object data = call(type(P + "common.dimension.prosperity.remaster.RemasterData"), "get", world);
        double x = number(field(player, "posX|field_70165_t"));
        double y = number(field(player, "posY|field_70163_u"));
        double z = number(field(player, "posZ|field_70161_v"));
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        JsonObject out = new JsonObject();
        out.addProperty("x", x); out.addProperty("y", y); out.addProperty("z", z);
        out.addProperty("dimension", (Number) field(player, "dimension|field_71093_bK"));
        out.addProperty("ticks", (Number) field(player, "ticksExisted|field_70173_aa"));
        out.addProperty("worldTime", (Number) call(world, "getTotalWorldTime|func_82737_E"));
        out.addProperty("blockExists", (Boolean) call(world, "blockExists|func_72899_e", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        JsonArray scenes = new JsonArray();
        for (Object site : (List<?>) call(data, "inChunk", cx, cz)) {
            String prefab = (String) field(site, "prefab");
            if (!"subsided_factory".equals(prefab) && !"fallen_foundry".equals(prefab)) continue;
            String id = (String) call(site, "id");
            JsonObject scene = new JsonObject();
            scene.addProperty("site", id);
            scene.addProperty("geom", (Boolean) call(data, "flag", id, "geom:" + cx + ":" + cz));
            scene.addProperty("within", (Boolean) call(type(P + "common.dimension.prosperity.remaster.RemasterOriginalContract"), "withinGeneratedSite", player, site));
            Object boss = call(type(P + "common.dimension.prosperity.remaster.RemasterRuntime"), "bossStatus", world, id);
            scene.addProperty("hasBossStatus", boss != null);
            if (boss != null) for (String name : new String[] { "totalSpawners", "remainingSpawners", "state", "revivalTicks", "entityId", "health", "maxHealth" })
                scene.addProperty(name, (Number) field(boss, name));
            if (boss != null) scene.addProperty("bossCode", (String) field(boss, "bossCode"));
            scenes.add(scene);
        }
        out.add("scenes", scenes);
        return out;
    }

    public static JsonObject client() throws Exception {
        Object mc = call(type("net.minecraft.client.Minecraft"), "getMinecraft|func_71410_x");
        Object world = field(mc, "theWorld|field_71441_e");
        Class<?> signals = type(P + "client.encounter.EncounterSignals");
        Object signal = call(signals, "scene");
        Object cache = field(signals, "SCENE");
        JsonObject out = new JsonObject();
        out.addProperty("worldPresent", world != null);
        out.addProperty("cacheWorldMatches", world != null && field(cache, "world") == world);
        try {
            out.addProperty("cacheTicks", (Number) field(cache, "ticks"));
        } catch (NoSuchFieldException removedLease) {
            out.addProperty("clientTickLeaseRemoved", true);
        }
        out.addProperty("hasSignal", signal != null);
        if (signal != null) {
            for (String name : new String[] { "kind", "state", "remaining", "total", "entity", "health", "maxHealth" })
                out.addProperty(name, (Number) field(signal, name));
            out.addProperty("site", (String) field(signal, "site"));
        }
        JsonArray bosses = new JsonArray();
        if (world != null) for (Object entity : (List<?>) field(world, "loadedEntityList|field_72996_f")) {
            if (!entity.getClass().getName().equals(P + "common.dimension.prosperity.echo.EntityOldEcho")) continue;
            Object kind = call(entity, "getKind");
            String code = (String) field(kind, "code");
            if (!"dc-08".equals(code) && !"dc-02".equals(code)) continue;
            JsonObject boss = new JsonObject();
            boss.addProperty("code", code);
            boss.addProperty("site", (String) call(entity, "getEncounterId"));
            boss.addProperty("stage", (Number) call(entity, "getIndustrialBossStage"));
            boss.addProperty("combatState", (Number) call(entity, "getEncounterState"));
            boss.addProperty("dead", (Boolean) field(entity, "isDead|field_70128_L"));
            bosses.add(boss);
        }
        out.add("loadedBosses", bosses);
        return out;
    }

    /** Parse a duplicate of an observed native FML payload; never consumes the live reader index. */
    public static JsonObject packet(ByteBuf payload) throws Exception {
        ByteBuf bytes = payload.duplicate();
        JsonObject out = new JsonObject();
        int discriminator = bytes.readUnsignedByte();
        out.addProperty("discriminator", discriminator);
        if (discriminator != 1) return out;
        Object signal = type(P + "common.dimension.prosperity.encounter.SceneBossSignal").getConstructor().newInstance();
        call(signal, "fromBytes", bytes);
        out.addProperty("valid", (Boolean) field(signal, "valid"));
        for (String name : new String[] { "dimension", "kind", "state", "remaining", "total", "entity", "health", "maxHealth" })
            out.addProperty(name, (Number) field(signal, name));
        out.addProperty("site", (String) field(signal, "site"));
        return out;
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, Thread.currentThread().getContextClassLoader());
    }

    private static double number(Object value) { return ((Number) value).doubleValue(); }

    private static Object field(Object object, String aliases) throws Exception {
        for (Class<?> c = object instanceof Class ? (Class<?>) object : object.getClass(); c != null; c = c.getSuperclass())
            for (String name : aliases.split("\\|")) try {
                Field f = c.getDeclaredField(name); f.setAccessible(true);
                return f.get(object instanceof Class ? null : object);
            } catch (NoSuchFieldException absent) { /* Try the next native alias. */ }
        throw new NoSuchFieldException(aliases);
    }

    private static Object call(Object object, String aliases, Object... args) throws Exception {
        for (Method method : (object instanceof Class ? (Class<?>) object : object.getClass()).getMethods()) {
            boolean named = false;
            for (String name : aliases.split("\\|")) if (method.getName().equals(name)) named = true;
            if (!named || method.getParameterTypes().length != args.length) continue;
            try { return method.invoke(object instanceof Class ? null : object, args); }
            catch (IllegalArgumentException overload) { /* Try the other public overload. */ }
        }
        throw new NoSuchMethodException(aliases);
    }
}
