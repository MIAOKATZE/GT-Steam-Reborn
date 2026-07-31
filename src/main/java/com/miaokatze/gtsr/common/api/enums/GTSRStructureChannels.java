package com.miaokatze.gtsr.common.api.enums;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.structure.IStructureChannels;

public enum GTSRStructureChannels implements IStructureChannels {

    STACK("stack", "gtsr.channel.stack.description");

    private final String channel;
    private final String defaultTooltip;

    GTSRStructureChannels(String channel, String defaultTooltip) {
        this.channel = channel;
        this.defaultTooltip = defaultTooltip;
    }

    @Override
    public String get() {
        return channel;
    }

    @Override
    public String getDefaultTooltip() {
        return defaultTooltip;
    }

    @Override
    public void registerAsIndicator(ItemStack indicator, int channelValue) {
        StructureLibAPI.registerChannelItem(get(), GTSteamReborn.MODID, channelValue, indicator);
    }

    public static void register() {
        for (GTSRStructureChannels value : values()) {
            StructureLibAPI.registerChannelDescription(value.get(), GTSteamReborn.MODID, value.getDefaultTooltip());
        }
    }
}
