package com.miaokatze.gtsr.client.gui.terminal;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 蒸汽枢纽「缓存节点状态管理界面」薄壳（terminal-native-ui N12，PLAN §4.3-B）。
 * 全部同步/绘制/动作逻辑在基类 GuiCacheHubStatusScreen，本类仅传 uiType
 * 并提供蒸汽系 5 型缓存节点（含两奇点仓室）的图标静态映射
 * （纯客户端 instanceof 类型串 → 物品，零网络开销，与旧蒸汽薄壳同表）。
 */
@SideOnly(Side.CLIENT)
public class GuiSteamHubStatusScreen extends GuiCacheHubStatusScreen {

    public GuiSteamHubStatusScreen(int x, int y, int z, int dim) {
        super(TerminalUiType.STEAM_HUB, x, y, z, dim);
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
            case "singularity_steam" -> GTSRItemList.SingularitySteamCompartment.get(1);
            case "singularity_steam_out" -> GTSRItemList.SingularitySteamOutputCompartment.get(1);
            default -> null;
        };
    }
}
