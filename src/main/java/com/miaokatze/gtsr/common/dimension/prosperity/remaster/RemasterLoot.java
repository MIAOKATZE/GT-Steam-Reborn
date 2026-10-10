package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.SealedChestLoot;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;

/** Shared sealed reward contract, plus only an explicitly authored story relic. */
public final class RemasterLoot {

    private RemasterLoot() {}

    public static SealedChestLoot.Prepared prepare(TileEntitySealedChest chest, JsonObject node) {
        net.minecraft.item.Item relic = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry.RELICS
            .get(RemasterRuntime.string(node, "storyRelic", ""));
        return SealedChestLoot.prepare(chest, relic == null ? null : new ItemStack(relic));
    }

    public static void fill(TileEntityUnsealedChest chest, RemasterSite site, JsonObject node) {
        fill(chest, site, node, ChestTier.resolveTier(site, node));
    }

    public static void fill(TileEntityUnsealedChest chest, RemasterSite site, JsonObject node, int tier) {
        net.minecraft.item.Item relic = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry.RELICS
            .get(RemasterRuntime.string(node, "storyRelic", ""));
        SealedChestLoot.fill(chest, tier, relic == null ? null : new ItemStack(relic));
    }
}
