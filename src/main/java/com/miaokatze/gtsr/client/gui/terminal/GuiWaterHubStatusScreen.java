package com.miaokatze.gtsr.client.gui.terminal;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 蓄水枢纽阵列「缓存节点状态管理界面」薄壳（terminal-native-ui N13，PLAN §4.3-B）。
 * 全部同步/绘制/动作逻辑在基类 GuiCacheHubStatusScreen，本类仅传 uiType
 * 并提供蓄水系 5 型缓存节点（三种水缓存 + 两通用流体奇点仓室）的图标静态映射
 * （纯客户端类型串 → 物品，零网络开销，与旧蓄水薄壳同表）。
 */
@SideOnly(Side.CLIENT)
public class GuiWaterHubStatusScreen extends GuiCacheHubStatusScreen {

    public GuiWaterHubStatusScreen(int x, int y, int z, int dim) {
        super(TerminalUiType.WATER_HUB, x, y, z, dim);
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
