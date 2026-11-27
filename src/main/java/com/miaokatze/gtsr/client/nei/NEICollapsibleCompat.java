// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.client.nei.GTSRNEIGroups.Group;

import codechicken.nei.CollapsibleItems;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.API;

/** Keeps the NEI 2.8.176 API optional while using 2.8.171's own group parser and state persistence. */
public final class NEICollapsibleCompat {

    private static final Method ADD_GROUP = findGroupApi();
    private static final List<Object> legacyGroups = new ArrayList<>();
    private static List<String> legacyRules = Collections.emptyList();
    private static boolean registered;

    private NEICollapsibleCompat() {}

    public static boolean usesNativeApi() {
        return ADD_GROUP != null;
    }

    /** Called by the NEI plugin before NEI loads its groups. No reference to the optional ApiGroup type. */
    public static synchronized void register(Map<Group, List<ItemStack>> groups) {
        if (registered) return;
        try {
            if (usesNativeApi()) {
                Method setName = ADD_GROUP.getReturnType()
                    .getMethod("setName", String.class);
                for (Group group : Group.values()) {
                    List<ItemStack> stacks = groups.get(group);
                    if (stacks != null && !stacks.isEmpty()) {
                        List<ItemStack> snapshot = new ArrayList<>();
                        for (ItemStack stack : stacks) snapshot.add(stack.copy());
                        setName.invoke(ADD_GROUP.invoke(null, snapshot), "gtsr.nei.group." + group.id);
                    }
                }
            } else {
                List<String> rules = new ArrayList<>();
                for (Group group : Group.values()) {
                    List<ItemStack> stacks = groups.get(group);
                    if (stacks == null || stacks.isEmpty()) continue;
                    TreeSet<String> selectors = new TreeSet<>();
                    for (ItemStack stack : stacks) {
                        if (stack == null || stack.getItem() == null) continue;
                        String name = Item.itemRegistry.getNameForObject(stack.getItem());
                        if (name != null) selectors.add("<" + name + ":" + stack.getItemDamage() + ">");
                    }
                    if (selectors.isEmpty()) continue;
                    String key = "gtsr.nei.group." + group.id;
                    JsonObject metadata = new JsonObject();
                    metadata.addProperty("unlocalizedName", key);
                    metadata.addProperty("displayName", StatCollector.translateToLocal(key));
                    metadata.addProperty("expanded", false);
                    rules.add("; " + metadata);
                    rules.add(String.join(" | ", selectors));
                }
                legacyRules = rules;
            }
            registered = true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot register GTSR collapsible groups with this NEI version", failure);
        }
    }

    /**
     * Legacy NEI posts this event after native groups/states load, before starting the item-list rebuild.
     * Append only our rules; NEI constructs their filters/GUIDs and restores the user's saved expansion states.
     */
    public static synchronized void onConfigsLoaded() {
        if (usesNativeApi() || !registered || legacyRules.isEmpty()) return;
        try {
            Field field = CollapsibleItems.class.getDeclaredField("groups");
            field.setAccessible(true);
            List<?> groups = (List<?>) field.get(null);
            if (!NEIClientConfig.enableCollapsibleItems()) {
                if (groups.removeAll(legacyGroups)) CollapsibleItems.clearCache();
                legacyGroups.clear();
                return;
            }
            // Native load() clears this list in place, so a list identity or a permanent boolean is insufficient.
            if (!legacyGroups.isEmpty() && groups.containsAll(legacyGroups)) return;
            Method parse = CollapsibleItems.class.getDeclaredMethod("parseFile", List.class);
            parse.setAccessible(true);
            Method loadStates = CollapsibleItems.class.getDeclaredMethod("loadStates");
            loadStates.setAccessible(true);
            // Resolve the complete legacy contract before changing any native group collection.
            groups.removeAll(legacyGroups);
            legacyGroups.clear();
            int before = groups.size();
            try {
                parse.invoke(null, legacyRules);
                if (groups.size() - before != legacyRules.size() / 2)
                    throw new IllegalStateException("Legacy NEI did not parse every GTSR collapsible group");
                loadStates.invoke(null);
                legacyGroups.addAll(groups.subList(before, groups.size()));
            } catch (ReflectiveOperationException | RuntimeException failure) {
                // The native parser may append before failing. Roll back only this append, never preset groups.
                groups.subList(before, groups.size())
                    .clear();
                throw failure;
            } finally {
                CollapsibleItems.clearCache();
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot load GTSR collapsible groups with legacy NEI", failure);
        }
    }

    private static Method findGroupApi() {
        try {
            return API.class.getMethod("addCollapsibleGroup", Iterable.class);
        } catch (NoSuchMethodException legacyVersion) {
            return null;
        }
    }
}
