// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;
import com.miaokatze.gtsr.common.machine.MTESingularityCompartmentBase;
import com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.main.GTSteamReborn;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipBlocks;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.interfaces.ISecondaryDescribable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/** Resolves reviewed copy by registration after init. Missing translations retain the mod's original localized page. */
public final class GTSRReviewedPages {

    private static final int DAMAGE_IS_STATE = -1;
    private static final Pattern MACHINE_ICON = Pattern.compile("\\[item:gregtech:gt\\.blockmachines@(\\d+)\\|");
    private static final Map<Item, Map<Integer, Entry>> owners = new IdentityHashMap<>();
    private static String locale;
    private static boolean watching;

    private GTSRReviewedPages() {}

    public static void initialize() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!watching && mc.getResourceManager() instanceof IReloadableResourceManager manager) {
            watching = true;
            manager.registerReloadListener(ignored -> {
                owners.clear();
                locale = null;
            });
        }
        reload(mc);
    }

    static boolean apply(ItemStack stack, EntityPlayer player, List<String> lines, IMetaTileEntity meta) {
        if (stack == null || !watching) return false;
        Minecraft mc = Minecraft.getMinecraft();
        reload(mc);
        Map<Integer, Entry> forms = owners.get(stack.getItem());
        if (forms == null
            || meta instanceof ISecondaryDescribable secondary && secondary.isDisplaySecondaryDescription())
            return false;
        Entry entry = forms.get(stack.getItemDamage());
        if (entry == null) entry = forms.get(DAMAGE_IS_STATE);
        if (entry == null || entry.machine != (meta != null)) return false;

        // Both removals and the new body are committed together. Never remove arbitrary lines after another mod's edit.
        List<String> candidate = new ArrayList<>(lines);
        List<String> owned = new ArrayList<>();
        if (meta instanceof MTEFilteredCacheNode cache) cache.addCachePageInformation(stack, owned);
        else if (meta instanceof MTERemoteWorkerNode worker) worker.addWorkerPageInformation(stack, owned);
        else if (meta instanceof MTESingularityCompartmentBase bay) bay.addCompartmentTooltip(owned);
        else if (meta == null) stack.getItem()
            .addInformation(stack.copy(), player, owned, mc.gameSettings.advancedItemTooltips);
        if (!removeOwned(candidate, owned)) return false;
        NativeTooltipPage page;
        try {
            page = entry.page(stack, meta);
        } catch (IllegalArgumentException | IllegalStateException malformedContent) {
            // A resource pack may replace a table or chart. Drop this broken page until reload; retain the old text.
            forms.values()
                .remove(entry);
            GTSteamReborn.LOG.warn("Invalid reviewed tooltip for " + stack.getUnlocalizedName(), malformedContent);
            return false;
        }
        if (meta == null) {
            if (!page.appendTo(stack, candidate)) return false;
        } else if (!page.apply(stack, candidate)) {
            // Empty-description hatches may carry all their own content in addAdditionalTooltipInformation.
            String[] raw = meta.getDescription();
            if ((raw != null && raw.length != 0) || !page.appendTo(stack, candidate)) return false;
        }
        lines.clear();
        lines.addAll(candidate);
        return true;
    }

    static boolean removeOwned(List<String> lines, List<String> owned) {
        if (owned.isEmpty()) return true;
        int at = Collections.indexOfSubList(lines, owned);
        if (at < 1 || at != Collections.lastIndexOfSubList(lines, owned)) return false;
        lines.subList(at, at + owned.size())
            .clear();
        return true;
    }

    private static void reload(Minecraft mc) {
        String language = mc.gameSettings.language;
        if (language.equals(locale)) return;
        locale = language;
        owners.clear();
        IResourceManager manager = mc.getResourceManager();
        try (Reader reader = new InputStreamReader(
            manager.getResource(new ResourceLocation("gtsr", "tooltip/" + language + ".json"))
                .getInputStream(),
            StandardCharsets.UTF_8)) {
            JsonObject doc = new JsonParser().parse(reader)
                .getAsJsonObject();
            JsonObject icons = doc.getAsJsonObject("machineIcons");
            for (JsonElement item : doc.getAsJsonArray("items")) {
                JsonObject o = item.getAsJsonObject(), owner = o.getAsJsonObject("owner");
                boolean machine = owner.has("machine");
                Item target;
                int damage;
                if (machine) {
                    GTSRItemList registration = GTSRItemList.valueOf(
                        owner.get("machine")
                            .getAsString());
                    if (!registration.hasBeenSet()) continue;
                    target = registration.getItem();
                    damage = registration.getMeta();
                } else {
                    String name = owner.get("item")
                        .getAsString();
                    if (!name.startsWith("gtsr:")) throw new IllegalArgumentException("Foreign page: " + name);
                    target = GameRegistry.findItem("gtsr", name.substring(5));
                    if (target == null) continue; // Optional registry content may be disabled.
                    damage = owner.has("meta") ? owner.get("meta")
                        .getAsInt() : DAMAGE_IS_STATE;
                    if (damage < 0
                        && !(target instanceof PortableWeapons.AmmoItem || target instanceof SpacetimeAnchorBeacon))
                        throw new IllegalArgumentException("Unexpected wildcard: " + name);
                }
                if (target == null) continue;
                JsonObject tiers = o.getAsJsonObject("tiers");
                // Default catalog coordinates describe the example instance; icon stacks use this installation's IDs.
                String json = tiers.toString();
                Matcher matcher = MACHINE_ICON.matcher(json);
                StringBuffer resolved = new StringBuffer();
                while (matcher.find()) {
                    JsonElement alias = icons.get(matcher.group(1));
                    if (alias == null) continue;
                    GTSRItemList registration = GTSRItemList.valueOf(alias.getAsString());
                    if (!registration.hasBeenSet()) continue;
                    matcher
                        .appendReplacement(resolved, "[item:gregtech:gt.blockmachines@" + registration.getMeta() + "|");
                }
                matcher.appendTail(resolved);
                Entry entry = new Entry(
                    machine,
                    new JsonParser().parse(resolved.toString())
                        .getAsJsonObject());
                if (owners.computeIfAbsent(target, ignored -> new HashMap<>())
                    .put(damage, entry) != null)
                    throw new IllegalArgumentException("Duplicate reviewed item " + o.get("id"));
            }
        } catch (FileNotFoundException unavailableTranslation) {
            // The reviewed catalog ships in its authored language; legacy resource keys cover other locales.
        } catch (IOException | RuntimeException invalidPack) {
            owners.clear();
            GTSteamReborn.LOG.warn("Could not load GTSR reviewed tooltip catalog for " + language, invalidPack);
        }
    }

    private static final class Entry {

        final boolean machine;
        final JsonObject tiers;
        final Map<NativeTooltipPage.Tier, NativeTooltipPage> fixed = new EnumMap<>(NativeTooltipPage.Tier.class);

        Entry(boolean machine, JsonObject tiers) {
            this.machine = machine;
            this.tiers = tiers;
        }

        NativeTooltipPage page(ItemStack stack, IMetaTileEntity meta) {
            NativeTooltipPage page = NativeTooltipPage.forItem(stack);
            if (!tiers.has("quick")) page.singleTier();
            else if (!tiers.has("detail")) page.twoTiers();
            NativeTooltipPage.Tier tier = page.tier();
            if (fixed.containsKey(tier)) return fixed.get(tier);
            boolean cache = !(stack.getItem() instanceof PortableWeapons.AmmoItem
                || stack.getItem() instanceof SpacetimeAnchorBeacon);
            for (JsonElement e : tiers.getAsJsonArray(
                tier.name()
                    .toLowerCase(java.util.Locale.ROOT))) {
                JsonObject b = e.getAsJsonObject();
                switch (b.get("type")
                    .getAsString()) {
                    case "curve" -> page.block(GTSRReviewedCurve.chart(b));
                    case "binding", "ammo", "damage", "charge", "anchor", "profiles" -> {
                        GTSRReviewedState.append(page, b, stack, meta);
                        cache = false; // Retain no stack, NBT, player or rendered state between hovers.
                    }
                    default -> {
                        if (!NativeTooltipBlocks.append(page, b))
                            throw new IllegalArgumentException("Unknown block: " + b);
                    }
                }
            }
            if (cache) fixed.put(tier, page);
            return page;
        }
    }
}
