package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress;

/** Thirty-eight distinct, provenance-bearing original witnesses. No creative variants grant evidence. */
public final class RemasterWitness extends Item {

    public static final RemasterWitness ITEM = new RemasterWitness();

    private RemasterWitness() {
        setUnlocalizedName("gtsr.remasterWitness");
        setTextureName("gtsr:lore/brass_chronicle");
        setMaxStackSize(1);
        setHasSubtypes(true);
    }

    public static JsonArray roster() {
        return RemasterCatalog.get("fiction_expansion_project", 0).metadata.getAsJsonObject("longPuzzle")
            .getAsJsonArray("requiredTestimonies");
    }

    public static String code(int damage) {
        JsonArray roster = roster();
        return damage >= 0 && damage < roster.size() ? roster.get(damage)
            .getAsString() : "";
    }

    public static boolean sourceAllows(String code, RemasterSite site) {
        if (code.equals("dc-02")) return site.prefab.equals("fallen_foundry");
        if (code.equals("dc-08")) return site.prefab.equals("subsided_factory");
        if (code.equals("dc-10")) return site.prefab.equals("forgotten_lake_court");
        JsonObject entities = RemasterCatalog.config()
            .getAsJsonObject("story")
            .getAsJsonObject("entities");
        if (!entities.has(code)) return false;
        for (JsonElement e : entities.getAsJsonObject(code)
            .getAsJsonArray("sites")) {
            if (site.prefab.equals(e.getAsString())) return true;
        }
        return false;
    }

    public static ItemStack create(RemasterSite source, String code, String event) {
        if (!sourceAllows(code, source)) return null;
        int subtype = -1;
        JsonArray roster = roster();
        for (int i = 0; i < roster.size(); i++) if (roster.get(i)
            .getAsString()
            .equals(code)) subtype = i;
        if (subtype < 0) return null;
        ItemStack item = new ItemStack(ITEM, 1, subtype);
        NBTTagCompound n = new NBTTagCompound();
        n.setString("code", code);
        n.setString("site", source.id());
        n.setString("prefab", source.prefab);
        n.setString("event", event);
        n.setInteger("revision", 7);
        item.setTagCompound(n);
        return item;
    }

    public static boolean authentic(ItemStack item, net.minecraft.world.World world, String required) {
        if (item == null || item.getItem() != ITEM
            || !required.equals(code(item.getItemDamage()))
            || !item.hasTagCompound()) return false;
        NBTTagCompound n = item.getTagCompound();
        RemasterData data = RemasterData.get(world);
        RemasterSite site = data.site(n.getString("site"));
        return n.getInteger("revision") == 7 && required.equals(n.getString("code"))
            && site != null
            && site.prefab.equals(n.getString("prefab"))
            && sourceAllows(required, site)
            && data.flag(site.id(), "witness-issued:" + required + ":" + n.getString("event"));
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "身份原件 · " + code(stack.getItemDamage());
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, net.minecraft.world.World world, EntityPlayer player) {
        String code = code(stack.getItemDamage());
        if (!world.isRemote && authentic(stack, world, code)) {
            HistoryProgress.observeRemasterEvidence(
                player,
                stack.getTagCompound()
                    .getString("site"),
                "witness:" + code);
        }
        return stack;
    }
}
