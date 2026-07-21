package com.miaokatze.gtsr.common.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;

/**
 * 蓄水枢纽「缓存节点状态管理界面」。
 * 全部同步/行构建逻辑在基类 MTECacheHubStatusGui，本类仅委托枢纽实例方法
 * 并提供蓄水缓存节点的图标静态映射（蓄水枢纽当前仅接受 water 一种类型）。
 */
public class MTEWaterHubStatusGui extends MTECacheHubStatusGui {

    private final MTEWaterHubArray hub;

    public MTEWaterHubStatusGui(MTEWaterHubArray hub) {
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
    protected void renameNode(int x, int y, int z, int dim, String name) {
        hub.renameCacheNodeFromGui(x, y, z, dim, name);
    }

    @Override
    protected String getTitleLangKey() {
        return "gtsr.cache_hub_status.title.water";
    }

    /** 类型串由枢纽侧 instanceof 实际节点类生成（见 MTEWaterHubArray.resolveCacheNodeType）。 */
    @Override
    protected ItemStack getNodeIcon(String type) {
        return "water".equals(type) ? GTSRItemList.WaterCacheNode.get(1) : null;
    }
}
