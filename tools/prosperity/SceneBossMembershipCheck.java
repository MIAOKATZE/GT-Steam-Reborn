import java.lang.reflect.Field;
import java.util.ArrayList;

import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterCatalog;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterData;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterOriginalContract;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;

/** Actual authored bounds and saved authority. Sparse loaded world, not a running game. */
public final class SceneBossMembershipCheck {

    private static int checks;

    public static void main(String[] args) throws Exception {
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        RemasterRuntimeCheck.u = (sun.misc.Unsafe) unsafe.get(null);
        net.minecraft.init.Bootstrap.func_151354_b();
        for (String prefab : new String[] { "subsided_factory", "fallen_foundry" }) checkSite(prefab);
        System.out.println("PASS: " + checks + " actual scene membership/authority checks");
    }

    private static void checkSite(String prefab) throws Exception {
        RemasterRuntimeCheck.W world = (RemasterRuntimeCheck.W) RemasterRuntimeCheck.u.allocateInstance(RemasterRuntimeCheck.W.class);
        world.loaded = true;
        RemasterRuntimeCheck.set(world, World.class, "loadedEntityList", new ArrayList<>());
        RemasterRuntimeCheck.set(world, World.class, "perWorldStorage", new MapStorage(null));
        RemasterRuntimeCheck.P player = (RemasterRuntimeCheck.P) RemasterRuntimeCheck.u.allocateInstance(RemasterRuntimeCheck.P.class);
        player.worldObj = world;
        RemasterSite site = new RemasterSite(prefab, 0, 224, 4096, 96, 4096);
        RemasterData data = RemasterData.get(world);
        data.register(site);
        for (int cx = site.minX() >> 4; cx <= site.maxX() >> 4; cx++)
            for (int cz = site.minZ() >> 4; cz <= site.maxZ() >> 4; cz++) data.flag(site.id(), "geom:" + cx + ":" + cz, true);
        for (JsonElement raw : site.plan().metadata.getAsJsonArray("spawns")) {
            JsonObject actor = raw.getAsJsonObject();
            player.posX = site.x + actor.get("x").getAsInt() + .5;
            player.posY = site.y + actor.get("y").getAsInt();
            player.posZ = site.z + actor.get("z").getAsInt() + .5;
            check(RemasterOriginalContract.withinGeneratedSite(player, site), prefab + " authored actor remains a member: " + actor.get("id"));
        }
        RemasterRuntime.BossStatus dormant = RemasterRuntime.bossStatus(world, site.id());
        check(dormant != null && dormant.entityId == -1 && dormant.state == 0, prefab + " unloaded dormant boss retains a snapshot");
        check(dormant.totalSpawners == 9 && dormant.remainingSpawners == 9, prefab + " unloaded dormant boss retains all nine activation conditions");
        JsonObject descriptor = RemasterCatalog.descriptor(prefab, 0);
        player.posX = site.x + descriptor.getAsJsonArray("min").get(0).getAsInt() + 1;
        player.posZ = site.z + descriptor.getAsJsonArray("min").get(2).getAsInt() + 1;
        player.posY = site.y + descriptor.getAsJsonArray("max").get(1).getAsInt() + 2;
        check(RemasterOriginalContract.withinGeneratedSite(player, site), prefab + " roof headroom belongs to the scene");
        player.posY++;
        check(!RemasterOriginalContract.withinGeneratedSite(player, site), prefab + " flying above the authored top leaves the scene");
        player.posY = site.y;
        int cx = (int) Math.floor(player.posX) >> 4, cz = (int) Math.floor(player.posZ) >> 4;
        data.flag(site.id(), "geom:" + cx + ":" + cz, false);
        check(!RemasterOriginalContract.withinGeneratedSite(player, site), prefab + " ungenerated owner chunk is rejected");
        data.flag(site.id(), "geom:" + cx + ":" + cz, true);
        world.loaded = false;
        check(!RemasterOriginalContract.withinGeneratedSite(player, site), prefab + " unloaded player point is rejected");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
}
