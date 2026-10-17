package com.miaokatze.gtsr.common.gui.mui2.hub;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Evaluated only by client text/tooltip callbacks; production layout contains ordinary native text widgets. */
public final class HubClientText {

    private HubClientText() {}

    public static String clip(String text, int width) {
        return NetworkUtils.isDedicatedClient() ? clientClip(text, width) : text;
    }

    @SideOnly(Side.CLIENT)
    private static String clientClip(String text, int width) {
        if (Minecraft.getMinecraft().fontRenderer.getStringWidth(text) <= width) return text;
        return Minecraft.getMinecraft().fontRenderer.trimStringToWidth(text, Math.max(0, width - 9)) + "...";
    }

    public static String language() {
        return NetworkUtils.isDedicatedClient() ? clientLanguage() : "";
    }

    @SideOnly(Side.CLIENT)
    private static String clientLanguage() {
        return Minecraft.getMinecraft().gameSettings.language;
    }

    public static String name(HubTerminalViewState.Row row) {
        if (row == null) return "—";
        if (!row.name()
            .isEmpty()) return row.name();
        if (row.worker != null) return StatCollector
            .translateToLocal(row.worker.isMiner ? "gtsr.drilling.node_miner" : "gtsr.drilling.node_driller");
        ItemStack item = icon(row.type());
        return item == null ? StatCollector.translateToLocal("gtsr.cache_hub_status.unknown_node")
            : item.getDisplayName();
    }

    static ItemStack icon(String type) {
        switch (type) {
            case "steam":
                return GTSRItemList.SteamCacheNode.get(1);
            case "reinforced_steam":
                return GTSRItemList.ReinforcedSteamCacheNode.get(1);
            case "overpressure_steam":
                return GTSRItemList.OverpressureSteamCacheNode.get(1);
            case "singularity_steam":
                return GTSRItemList.SingularitySteamCompartment.get(1);
            case "singularity_steam_out":
                return GTSRItemList.SingularitySteamOutputCompartment.get(1);
            case "water":
                return GTSRItemList.WaterCacheNode.get(1);
            case "reinforced_water":
                return GTSRItemList.ReinforcedWaterCacheNode.get(1);
            case "overpressure_water":
                return GTSRItemList.OverpressureWaterCacheNode.get(1);
            case "singularity_fluid_in":
                return GTSRItemList.SingularityFluidInputCompartment.get(1);
            case "singularity_fluid_out":
                return GTSRItemList.SingularityFluidOutputCompartment.get(1);
            default:
                return null;
        }
    }

    public static String fluid(String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        return fluid == null ? StatCollector.translateToLocal("gtsr.cache_hub_status.no_fluid")
            : fluid.getLocalizedName(new FluidStack(fluid, 1));
    }

    public static String amount(long value) {
        if (value >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fG", value / 1_000_000_000d);
        if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000d);
        if (value >= 1_000L) return String.format(Locale.ROOT, "%.1fK", value / 1_000d);
        return Long.toString(value);
    }
}
