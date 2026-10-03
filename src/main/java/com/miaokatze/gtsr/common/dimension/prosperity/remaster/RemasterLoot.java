package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;

/** Concrete deterministic mappings for every authored R7 reward-pool entry. */
public final class RemasterLoot {

    private RemasterLoot() {}

    public static void fill(TileEntityUnsealedChest chest, RemasterSite site, JsonObject node) {
        int tier = RemasterRuntime.integer(node, "tier", 1);
        int slot = 10;
        for (JsonElement e : RemasterRuntime.array(node, "lootPool")) {
            String entry = e.getAsString();
            ItemStack stack;
            if (entry.equals("低阶维护耗材")) stack = new ItemStack(Items.coal, 2 * tier);
            else if (entry.equals("旧金属散件")) stack = new ItemStack(Items.iron_ingot, tier);
            else if (entry.equals("有限补给")) stack = new ItemStack(Items.bread, 2 + tier);
            else if (entry.equals("独立奖励池")) stack = TileEntitySealedChest.lootForTier(tier);
            else throw new IllegalArgumentException("Unmapped authored loot pool: " + entry);
            chest.setInventorySlotContents(slot++, stack);
        }
    }

}
