package com.miaokatze.gtsr.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;

/**
 * 蓄水枢纽阵列「缓存节点状态管理界面」。
 * 全部同步/行构建逻辑在基类 MTECacheHubStatusGui，本类仅委托枢纽实例方法
 * 并提供三种通用流体缓存节点的图标静态映射（纯客户端类型串 → 物品，零网络开销）。
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
    protected void cycleNodeCap(int x, int y, int z, int dim) {
        hub.cycleCacheNodeCapFromGui(x, y, z, dim);
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
    protected void teleportNode(EntityPlayer player, int x, int y, int z, int dim) {
        hub.teleportPlayerToNodeFromGui(player, x, y, z, dim);
    }

    @Override
    protected String getTitleLangKey() {
        return "gtsr.cache_hub_status.title.water";
    }

    /** 类型串由枢纽侧 instanceof 实际节点类生成（见 MTEWaterHubArray.resolveCacheNodeType）。 */
    @Override
    protected ItemStack getNodeIcon(String type) {
        return switch (type) {
            case "water" -> GTSRItemList.WaterCacheNode.get(1);
            case "reinforced_water" -> GTSRItemList.ReinforcedWaterCacheNode.get(1);
            case "overpressure_water" -> GTSRItemList.OverpressureWaterCacheNode.get(1);
            case "singularity_fluid_in" -> GTSRItemList.SingularityFluidInputCompartment.get(1);
            case "singularity_fluid_out" -> GTSRItemList.SingularityFluidOutputCompartment.get(1);
            default -> null;
        };
    }
}
