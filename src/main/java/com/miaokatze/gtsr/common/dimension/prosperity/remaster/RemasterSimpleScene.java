package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Two visible scene actions, independent of legacy parameter and evidence state. */
public final class RemasterSimpleScene {

    private RemasterSimpleScene() {}

    public static boolean enabled(RemasterSite site) {
        return RemasterRollout.allowsGeneration(site);
    }

    private static String[] controls(RemasterSite site) {
        if (site.prefab.equals("fallen_foundry")) return new String[] { "control-0", "control-2" };
        if (site.prefab.equals("subsided_factory")) return new String[] { "factory-stage-0", "factory-stage-3" };
        return new String[0];
    }

    private static int index(RemasterSite site, String id) {
        String[] controls = controls(site);
        for (int i = 0; i < controls.length; i++) if (controls[i].equals(id)) return i;
        return -1;
    }

    private static String label(RemasterSite site, int index) {
        return index == 0 ? "确认入口联锁" : "确认主场联锁";
    }

    public static boolean complete(World world, RemasterSite site) {
        if (!enabled(site)) return false;
        String[] controls = controls(site);
        if (controls.length == 0) return false;
        for (String id : controls) if (!RemasterData.get(world)
            .flag(site.id(), "simple-scene:" + id)) return false;
        return true;
    }

    static boolean handles(TileRemasterNode tile) {
        RemasterSite site = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        return enabled(site) && (tile.role.equals("control") || tile.role.equals("shape"));
    }

    static JsonObject view(TileRemasterNode tile, JsonObject node) {
        RemasterSite site = RemasterData.get(tile.getWorldObj())
            .site(tile.siteId);
        int index = index(site, tile.nodeId);
        boolean shape = tile.role.equals("shape"), readOnly = !shape && index < 0;
        boolean done = shape ? RemasterRuntime.puzzleState(tile)
            .getBoolean("solved")
            : !readOnly && RemasterData.get(tile.getWorldObj())
                .flag(site.id(), "simple-scene:" + tile.nodeId);
        JsonObject out = new JsonObject();
        out.addProperty("title", shape ? "可选侧门" : readOnly ? "设备记录" : label(site, index));
        out.addProperty(
            "clue",
            shape ? "点击打开侧门；主路可直接通行。"
                : readOnly ? "此设备已退出主线。沿灯光和楼梯前往主场。" : "清除附近守卫后点击一次；沿灯光和楼梯继续前进。两处设备完成并清除守卫后可挑战 Boss。");
        out.addProperty("role", tile.role);
        out.addProperty("nodeId", tile.nodeId);
        out.addProperty("readOnly", readOnly || done);
        out.addProperty("singleAction", true);
        out.addProperty("solved", done);
        String feedback = RemasterData.get(tile.getWorldObj())
            .state(site.id())
            .getString("simple-scene-feedback:" + tile.nodeId);
        out.addProperty(
            "status",
            done ? shape ? "侧门已打开，无需再次操作。" : "联锁已确认；清除守卫后挑战 Boss，击败后才能领取封印奖励。"
                : readOnly ? "现场记录，无需核验。" : feedback.isEmpty() ? "点击确认联锁；此操作不改变场景方块。" : feedback);
        out.addProperty("actionLabel", shape ? "打开侧门" : readOnly ? "查看记录" : label(site, index));
        out.add("fields", new JsonArray());
        return out;
    }

    static boolean action(EntityPlayer player, TileRemasterNode tile, JsonObject node, int button) {
        if (button != 100 || player.capabilities.isCreativeMode) return false;
        World world = tile.getWorldObj();
        RemasterSite site = RemasterData.get(world)
            .site(tile.siteId);
        if (tile.role.equals("shape")) {
            if (RemasterRuntime.puzzleState(tile)
                .getBoolean("solved")) return true;
            if (!RemasterRuntime.applyShape(tile, true)) {
                feedback(tile, "侧门空间被占用，移开阻挡后再点击打开。");
                return false;
            }
            RemasterRuntime.puzzleState(tile)
                .setBoolean("solved", true);
        } else {
            if (index(site, tile.nodeId) < 0) return true;
            if (RemasterData.get(world)
                .flag(site.id(), "simple-scene:" + tile.nodeId)) return true;
            if (!RemasterOriginalContract.guardsCleared(world, site, node)) {
                feedback(tile, "本区守卫尚未清除；清除守卫后再确认联锁。");
                return false;
            }
            RemasterData.get(world)
                .flag(site.id(), "simple-scene:" + tile.nodeId, true);
        }
        RemasterData.get(world)
            .markDirty();
        tile.refresh();
        RemasterRuntime.updateHints(tile);
        return true;
    }

    private static void feedback(TileRemasterNode tile, String message) {
        RemasterData data = RemasterData.get(tile.getWorldObj());
        data.state(tile.siteId)
            .setString("simple-scene-feedback:" + tile.nodeId, message);
        data.markDirty();
        tile.refresh();
    }
}
