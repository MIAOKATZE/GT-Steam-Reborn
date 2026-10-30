package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.lang.reflect.Field;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

import com.google.gson.JsonObject;

/** Actual Runtime.death, actual ledger/storage/NBT, and packaged authored spawner nodes; no game tick loop. */
public final class RemasterSpawnerLedgerCheck {

    private static int checks;

    public static void main(String[] args) throws Exception {
        World world = allocate(WorldServer.class);
        World.class.getField("perWorldStorage").set(world, new MapStorage(null));
        RemasterData data = RemasterData.get(world);
        RemasterSite site = new RemasterSite("fallen_foundry", 0, 123, 0, 64, 0);
        data.register(site);
        String cage = null;
        for (JsonObject node : RemasterRuntime.nodes(site)) if ("spawner".equals(RemasterRuntime.string(node, "role", ""))) {
            cage = RemasterRuntime.string(node, "id", ""); break;
        }
        check(cage != null, "real packaged spawner found");
        NBTTagCompound state = data.state(site.id()), spawner = new NBTTagCompound();
        state.setTag("spawner:" + cage, spawner);
        String admitted = "spawner-admitted:" + cage + ":", dead = "spawner-dead:" + cage + ":";
        for (int i = 0; i < 1000; i++) {
            state.setBoolean(admitted + i, true);
            state.setBoolean(dead + i, true);
        }
        state.setBoolean(admitted + 1000, true); // Survives unloaded, and can die after migration/reload.
        state.setBoolean(admitted + 1001, true);
        state.setBoolean("spawner-admitted:other:7", true);
        state.setBoolean("spawner-dead:other:7", true);
        state.setString(dead + "opaque", "preserve");
        spawner.setInteger("serial", 1002);
        spawner.setInteger("deadCount", 1000);
        RemasterRuntime.death(world, site.id() + ":spawn:" + cage, 1001);
        check(spawner.getInteger("deadCount") == 1001 && spawner.getInteger("serial") == 1002, "cumulative counters unchanged");
        check(state.getBoolean(admitted + 1000), "unloaded surviving admission retained");
        check(!state.hasKey(admitted + 1001) && !state.hasKey(dead + 1001), "completed admission consumed without tombstone");
        check(state.getKeySet().size() == 5, "1000 completed historical pairs compacted without touching unrelated keys");
        check("preserve".equals(state.getString(dead + "opaque")), "opaque future marker preserved");
        RemasterRuntime.death(world, site.id() + ":spawn:" + cage, 1001);
        check(spawner.getInteger("deadCount") == 1001, "duplicate confirmed death cannot count twice");
        RemasterRuntime.death(world, site.id() + ":spawn:" + cage, 0);
        check(spawner.getInteger("deadCount") == 1001, "compacted legacy death remains rejected");
        RemasterRuntime.death(world, site.id() + ":spawn:" + cage, 1009);
        check(spawner.getInteger("deadCount") == 1001, "never-admitted death rejected");
        RemasterRuntime.death(world, site.id() + ":spawn:unknown", 1000);
        check(state.getBoolean(admitted + 1000), "unauthored cage cannot consume an admission");
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        RemasterData restored = new RemasterData(); restored.readFromNBT((NBTTagCompound) saved.copy());
        ((MapStorage) World.class.getField("perWorldStorage").get(world)).setData("gtsr.prosperityRemaster7", restored);
        state = restored.state(site.id()); spawner = state.getCompoundTag("spawner:" + cage);
        RemasterRuntime.death(world, site.id() + ":spawn:" + cage, 1000);
        check(spawner.getInteger("deadCount") == spawner.getInteger("serial"), "delayed survivor death closes exact count after reload");
        for (int i = 1002; i < 11002; i++) {
            state.setBoolean(admitted + i, true);
            spawner.setInteger("serial", i + 1);
            RemasterRuntime.death(world, site.id() + ":spawn:" + cage, i);
            RemasterRuntime.death(world, site.id() + ":spawn:" + cage, i);
        }
        check(state.getKeySet().size() == 4, "10000 future deaths add no permanent per-kill identities");
        check(spawner.getInteger("deadCount") == 11002, "10000 repeated-cycle deaths counted exactly once");
        check(data.state(site.id()).getCompoundTag("spawner:" + cage).getInteger("deadCount") == 1001,
            "native deep-copy reload does not alias the original ledger");
        world.isRemote = true;
        state.setBoolean(admitted + 11002, true);
        RemasterRuntime.death(world, site.id() + ":spawn:" + cage, 11002);
        check(state.getBoolean(admitted + 11002) && spawner.getInteger("deadCount") == 11002, "client cannot consume admission");
        System.out.println("PASS: " + checks + " native runtime spawner ledger checks");
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
}
