package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

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
            else if (entry.equals("对应工序见证") || entry.equals("故事原件") || entry.equals("非唯一事故摘录")) {
                stack = record(site, node, entry);
            } else throw new IllegalArgumentException("Unmapped authored loot pool: " + entry);
            chest.setInventorySlotContents(slot++, stack);
        }
    }

    private static ItemStack record(RemasterSite site, JsonObject node, String entry) {
        ItemStack book = new ItemStack(Items.written_book);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", entry);
        tag.setString("author", "遗迹交班档案");
        NBTTagList pages = new NBTTagList();
        pages.appendTag(
            new NBTTagString(
                RemasterRuntime.string(node, "room", site.prefab) + "\n"
                    + "出处："
                    + site.id()
                    + "\n"
                    + "工序："
                    + RemasterRuntime.string(node, "reference", "")
                    + "\n"
                    + "此份事故记录可与现场图互证；身份台座只接受具名原件。"));
        tag.setTag("pages", pages);
        tag.setString("gtsr.site", site.id());
        tag.setString("gtsr.node", RemasterRuntime.string(node, "id", ""));
        tag.setBoolean("gtsr.uniqueOriginal", entry.equals("故事原件"));
        book.setTagCompound(tag);
        return book;
    }
}
