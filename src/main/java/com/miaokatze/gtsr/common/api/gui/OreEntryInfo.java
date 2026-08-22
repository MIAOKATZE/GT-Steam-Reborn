package com.miaokatze.gtsr.common.api.gui;

import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * 矿石浏览器条目：矿石、跨维权重和、所在维度缩写列表、是否被过滤、是否被定向瞄准。
 * 原 MTECrustMatterAggregator 公有内部类，SR-O2-B01② 下放顶层 DTO（machine/gui 共享数据，
 * 与 common.api.progress 同构分层，消除机器类兼任 GUI 数据 DTO 宿主的倒置）。
 */
public class OreEntryInfo {

    public final ItemStack ore;
    public float weight;
    public final List<String> dimAbbrs;
    public final boolean filtered;
    public final boolean aimed;

    public OreEntryInfo(ItemStack ore, float weight, List<String> dimAbbrs, boolean filtered) {
        this(ore, weight, dimAbbrs, filtered, false);
    }

    public OreEntryInfo(ItemStack ore, float weight, List<String> dimAbbrs, boolean filtered, boolean aimed) {
        this.ore = ore;
        this.weight = weight;
        this.dimAbbrs = dimAbbrs;
        this.filtered = filtered;
        this.aimed = aimed;
    }
}
