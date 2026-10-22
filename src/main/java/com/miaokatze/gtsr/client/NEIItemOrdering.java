package com.miaokatze.gtsr.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.items.AmmoniaCatalyst;
import com.miaokatze.gtsr.common.items.GeothermalOverheatChip;
import com.miaokatze.gtsr.common.items.HubSingularityChip;
import com.miaokatze.gtsr.common.items.RareGasSeparationChip;
import com.miaokatze.gtsr.common.items.ReinforcedHubSingularityChip;
import com.miaokatze.gtsr.common.items.SteamTurbineCycleOverlimitChip;
import com.miaokatze.gtsr.common.items.TCDSDenseSteamChip;
import com.miaokatze.gtsr.common.items.VeinPyrolyzerChip;
import com.miaokatze.gtsr.register.CreativeTabManager;

import codechicken.nei.ItemSorter;
import codechicken.nei.event.NEIConfigsLoadedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Keeps functional items in creative-tab order when NEI sorts by numeric item ID. */
public final class NEIItemOrdering {

    @SubscribeEvent
    public void onConfigsLoaded(NEIConfigsLoadedEvent event) {
        // NEI loads the user's sort chain before this event. Keep its priorities and option names intact.
        List<ItemSorter.SortEntry> active = new ArrayList<>(ItemSorter.list);
        for (int i = 0; i < ItemSorter.entries.size(); i++) {
            ItemSorter.SortEntry original = ItemSorter.entries.get(i);
            if (!"nei.itemsort.id".equals(original.name) || original.comparator instanceof FunctionalItemComparator) {
                continue;
            }
            ItemSorter.SortEntry replacement = new ItemSorter.SortEntry(
                original.name,
                new FunctionalItemComparator(original.comparator));
            ItemSorter.entries.set(i, replacement);
            for (int j = 0; j < active.size(); j++) {
                if (active.get(j) == original) active.set(j, replacement);
            }
        }
        ItemSorter.list = new ArrayList<>(active);
    }

    private static final class FunctionalItemComparator implements Comparator<ItemStack> {

        private final Comparator<ItemStack> original;
        private final Map<Item, Integer> ranks = new IdentityHashMap<>();
        private final List<ItemStack> items = new ArrayList<>();

        private FunctionalItemComparator(Comparator<ItemStack> original) {
            this.original = original;
            // Registration order already groups chips (T1 -> T3, normal -> reinforced) then catalyst tiers.
            // Read that same order instead of maintaining a second independent catalogue for NEI.
            for (ItemStack stack : CreativeTabManager.getItemsToAdd()) {
                Item item = stack.getItem();
                if (isFunctionalItem(item) && !ranks.containsKey(item)) {
                    ranks.put(item, ranks.size());
                    items.add(stack);
                }
            }
        }

        @Override
        public int compare(ItemStack left, ItemStack right) {
            Integer leftRank = ranks.get(left.getItem());
            Integer rightRank = ranks.get(right.getItem());
            if (leftRank == null && rightRank == null) return original.compare(left, right);

            // Use the same anchor against ALL items, or mixed group/ordinary comparisons are not transitive.
            // Resolve IDs here because joining another world/server can remap the registry after NEI boots.
            ItemStack anchor = items.get(0);
            int anchorId = Item.getIdFromItem(anchor.getItem());
            for (ItemStack stack : items) {
                int id = Item.getIdFromItem(stack.getItem());
                if (id < anchorId) {
                    anchor = stack;
                    anchorId = id;
                }
            }
            int result = original.compare(leftRank == null ? left : anchor, rightRank == null ? right : anchor);
            if (result != 0 || leftRank == null || rightRank == null) return result;
            return Integer.compare(leftRank, rightRank);
        }

        private static boolean isFunctionalItem(Item item) {
            return item instanceof VeinPyrolyzerChip || item instanceof GeothermalOverheatChip
                || item instanceof TCDSDenseSteamChip
                || item instanceof HubSingularityChip
                || item instanceof ReinforcedHubSingularityChip
                || item instanceof SteamTurbineCycleOverlimitChip
                || item instanceof RareGasSeparationChip
                || item instanceof AmmoniaCatalyst;
        }
    }
}
