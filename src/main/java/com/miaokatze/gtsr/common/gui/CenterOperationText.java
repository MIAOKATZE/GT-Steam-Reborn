// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidRegistry;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

/** Common labels for the synchronized server totals shown by both center interfaces. */
final class CenterOperationText {

    static final int ROWS = 8;
    private static final String[] BOOSTS = { "speed", "parallel", "yield", "secondary" };

    private CenterOperationText() {}

    static String line(NBTTagCompound state, int index) {
        return line(state, index, false);
    }

    static String nativeLine(NBTTagCompound state, int index) {
        return line(state, index, true) + EnumChatFormatting.RESET;
    }

    private static String line(NBTTagCompound state, int index, boolean nativeGui) {
        int duration = state.getInteger("Duration");
        long steam = state.getLong("Steam"), energy = state.getLong("Energy");
        long density = Math.max(1, state.getInteger("SteamDensity"));
        long steamUnits = (steam + density - 1) / density;
        int tier = state.getInteger("Tier");
        int sectors = Integer.bitCount(state.getInteger("Sectors"));
        String tierName = tier >= 0 && tier < 4
            ? StatCollector.translateToLocal("gtsr.ore_center.graph.terminal.tier." + tier) : "–";
        String white = nativeGui ? EnumChatFormatting.WHITE.toString() : "";
        String yellow = nativeGui ? EnumChatFormatting.YELLOW.toString() : "";
        return switch (index) {
            case 0 -> nativeGui ? yellow + local("native.structure", structureState(state, sectors),
                EnumChatFormatting.GOLD + tierName, white + sectors)
                : local("overview", tierName, sectors, state.getInteger("Installed"), state.getInteger("Graphs"));
            case 1 -> nativeGui ? yellow + local("native.progress", white + state.getInteger("Elapsed"), duration,
                state.getInteger("Installed"), state.getInteger("Graphs"))
                : local("progress", state.getInteger("Elapsed"), duration, format(state.getLong("Power")));
            case 2 -> yellow + local("steam", white + rate(steamUnits, duration), format(steamUnits),
                (nativeGui ? EnumChatFormatting.AQUA.toString() : "") + fluidName(state.getString("SteamFluid")));
            case 3 -> yellow + local("energy", white + rate(energy, duration), format(energy));
            default -> {
                int boost = index - 4;
                NBTTagCompound cost = state.getTagList("BoostCosts", 10).getCompoundTagAt(boost);
                String fluid = fluidName(cost.getString("FluidName"));
                yield yellow + local(BOOSTS[boost], (nativeGui ? EnumChatFormatting.AQUA.toString() : "") + fluid,
                    white + format(cost.getInteger("Amount")));
            }
        };
    }

    private static String structureState(NBTTagCompound state, int sectors) {
        if (state.getBoolean("Formed")) return EnumChatFormatting.GREEN + local("native.formed");
        return EnumChatFormatting.RED + local(sectors == 0 ? "native.unformed" : "native.ports");
    }

    private static String fluidName(String id) {
        var fluid = FluidRegistry.getFluidStack(id, 1);
        return fluid == null ? "-" : fluid.getLocalizedName();
    }

    private static String rate(long total, int duration) {
        return duration <= 0 ? "0" : format((total + duration - 1) / duration);
    }

    private static String format(long value) {
        return NumberFormatUtil.formatNumber(value);
    }

    private static String local(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("gtsr.ore_center.status." + key, args);
    }
}
