package com.miaokatze.gtsr.common.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;

/**
 * 蒸汽枢纽「缓存节点状态管理界面」。
 * 全部同步/行构建逻辑在基类 MTECacheHubStatusGui，本类仅委托枢纽实例方法
 * 并提供蒸汽系 3 种缓存节点的图标静态映射（纯客户端 instanceof 类型串 → 物品，零网络开销）。
 */
public class MTESteamHubStatusGui extends MTECacheHubStatusGui {

    private final MTESteamHubArray hub;

    public MTESteamHubStatusGui(MTESteamHubArray hub) {
        this.hub = hub;
    }

    @Override
    protected NBTTagList getCacheNodeListTag() {
        return hub.getCacheNodeListTag();
    }

    @Override
    protected void cycleNodeRate(int x, int y, int z, int dim) {
        hub.cycleCacheNodeRateFromGui(x, y, z, dim);
    }

    @Override
    protected void setNodeMode(int x, int y, int z, int dim, boolean output) {
        hub.setCacheNodeModeFromGui(x, y, z, dim, output);
    }

    @Override
    protected void setNodeAuto(int x, int y, int z, int dim, boolean auto) {
        hub.setCacheNodeAutoFromGui(x, y, z, dim, auto);
    }

    @Override
    protected void renameNode(int x, int y, int z, int dim, String name) {
        hub.renameCacheNodeFromGui(x, y, z, dim, name);
    }

    @Override
    protected String getTitleLangKey() {
        return "gtsr.cache_hub_status.title.steam";
    }

    /** 类型串由枢纽侧 instanceof 实际节点类生成（见 MTESteamHubArray.resolveCacheNodeType）。 */
    @Override
    protected ItemStack getNodeIcon(String type) {
        return switch (type) {
            case "steam" -> GTSRItemList.SteamCacheNode.get(1);
            case "reinforced_steam" -> GTSRItemList.ReinforcedSteamCacheNode.get(1);
            case "overpressure_steam" -> GTSRItemList.OverpressureSteamCacheNode.get(1);
            default -> null;
        };
    }
}
