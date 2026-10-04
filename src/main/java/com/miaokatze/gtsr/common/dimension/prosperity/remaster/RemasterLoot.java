package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;

/** Temporary reward contract: one item for the persisted tier, plus only an explicitly authored story relic. */
public final class RemasterLoot {

    private RemasterLoot() {}

    public static void fill(TileEntityUnsealedChest chest, RemasterSite site, JsonObject node) {
        fill(chest, site, node, ChestTier.resolveTier(site, node));
    }

    public static void fill(TileEntityUnsealedChest chest, RemasterSite site, JsonObject node, int tier) {
        if (!chest.beginReward(tier)) return;
        chest.setInventorySlotContents(13, TileEntitySealedChest.lootForTier(tier));
        net.minecraft.item.Item relic = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry.RELICS
            .get(RemasterRuntime.string(node, "storyRelic", ""));
        if (relic != null) chest.setInventorySlotContents(12, new ItemStack(relic));
    }
}
