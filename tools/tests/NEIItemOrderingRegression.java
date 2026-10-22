import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.client.NEIItemOrdering;
import com.miaokatze.gtsr.loader.ItemLoader;
import com.miaokatze.gtsr.register.CreativeTabManager;

import codechicken.nei.ItemSorter;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.event.NEIConfigsLoadedEvent;

/** Runs production ordering against NEI 2.8.145's actual ID/name/mod comparators. No game launch. */
public final class NEIItemOrderingRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("NEIItemOrderingRuntime", args[0]);
    }
}

class NEIItemOrderingRuntime {
    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        CreativeTabManager.getItemsToAdd().clear();
        for (String method : List.of("registerPyrolyzerChips", "registerGeothermalOverheatChip",
            "registerTcdsDenseSteamChip", "registerHubSingularityChip", "registerReinforcedHubSingularityChip",
            "registerSteamTurbineCycleOverlimitChip", "registerRareGasSeparationChip", "registerAmmoniaCatalysts")) {
            Method register = ItemLoader.class.getDeclaredMethod(method);
            register.setAccessible(true);
            register.invoke(null);
        }
        List<ItemStack> group = new ArrayList<>(CreativeTabManager.getItemsToAdd());
        if (group.size() != 16) throw new AssertionError("Expected 9 chips + 7 catalysts: " + group.size());
        List<ItemStack> others = new ArrayList<>();
        for (int i = 0; i < 16; i++) others.add(new ItemStack(new Item().setUnlocalizedName("ordinary" + i)));
        List<ItemStack> all = new ArrayList<>(group);
        all.addAll(others);
        Object registry = cpw.mods.fml.common.registry.GameData.getItemRegistry();
        Method raw = registry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        raw.setAccessible(true);
        List<String> registryNames = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            ItemStack stack = all.get(i);
            String name = Item.itemRegistry.getNameForObject(stack.getItem());
            if (name == null) name = "gtsr:ordering_probe_" + i;
            registryNames.add(name);
            raw.invoke(registry, 10000 + i, name, stack.getItem());
            ItemInfo.itemOwners.put(stack.getItem(), "gtsr");
            stack.setStackDisplayName(String.format("%02d", all.size() - i));
        }

        ItemSorter.entries.clear();
        ItemSorter.list.clear();
        String[] names = { "minecraft", "mod", "id", "default", "damage", "name" };
        for (int i = 0; i < names.length; i++) ItemSorter.add("nei.itemsort." + names[i], upstream(i));
        NEIItemOrdering hook = new NEIItemOrdering();
        hook.onConfigsLoaded(new NEIConfigsLoadedEvent());
        var wrapped = ItemSorter.find("nei.itemsort.id");
        hook.onConfigsLoaded(new NEIConfigsLoadedEvent());
        if (ItemSorter.find("nei.itemsort.id") != wrapped) throw new AssertionError("Wrapped twice");
        String config = ItemSorter.getSaveString(ItemSorter.list);
        if (!config.equals("nei.itemsort.minecraft,nei.itemsort.mod,nei.itemsort.id,nei.itemsort.default,"
            + "nei.itemsort.damage,nei.itemsort.name")) throw new AssertionError("Changed sort priorities: " + config);

        Random random = new Random(20261022L);
        int reproduced = 0;
        long triples = 0;
        for (int seed = 0; seed < 100; seed++) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) ids.add(10000 + i);
            Collections.shuffle(ids, random);
            for (int i = 0; i < all.size(); i++) {
                raw.invoke(registry, ids.get(i), registryNames.get(i), all.get(i).getItem());
            }
            List<ItemStack> oldOrder = new ArrayList<>(all);
            oldOrder.sort(upstream(2));
            if (!oldOrder.stream().filter(group::contains).toList().equals(group)) reproduced++;
            List<ItemStack> actual = new ArrayList<>(all);
            Collections.shuffle(actual, random);
            ItemSorter.sort(actual);
            if (!actual.stream().filter(group::contains).toList().equals(group)) {
                throw new AssertionError("Group order changed for remap " + seed);
            }
            int start = actual.indexOf(group.get(0));
            if (!actual.subList(start, start + group.size()).equals(group)) {
                throw new AssertionError("Group split by ordinary GTSR item for remap " + seed);
            }
            List<ItemStack> ordinaryOrder = new ArrayList<>(others);
            ordinaryOrder.sort(upstream(2));
            if (!actual.stream().filter(others::contains).toList().equals(ordinaryOrder)) {
                throw new AssertionError("Changed unrelated item order");
            }
            for (ItemStack a : all) for (ItemStack b : all) {
                int ab = Integer.signum(ItemSorter.instance.compare(a, b));
                if (ab != -Integer.signum(ItemSorter.instance.compare(b, a))) {
                    throw new AssertionError("Comparator is not antisymmetric");
                }
                for (ItemStack c : all) {
                    if (ab <= 0 && ItemSorter.instance.compare(b, c) <= 0
                        && ItemSorter.instance.compare(a, c) > 0) throw new AssertionError("Non-transitive comparator");
                    triples++;
                }
            }
            // The options screen reconstructs the active list; it must retain the replacement too.
            ItemSorter.list = ItemSorter.fromSaveString(config);
            ItemSorter.sort(actual);
            if (!actual.subList(actual.indexOf(group.get(0)), actual.indexOf(group.get(0)) + 16).equals(group)) {
                throw new AssertionError("Option reload lost ordering");
            }
        }
        if (reproduced == 0) throw new AssertionError("Native ID ordering did not reproduce the issue");
        ItemSorter.list = ItemSorter.fromSaveString("nei.itemsort.name," + config);
        List<ItemStack> named = new ArrayList<>(all);
        named.sort(upstream(5));
        List<ItemStack> actual = new ArrayList<>(all);
        ItemSorter.sort(actual);
        if (!actual.equals(named)) throw new AssertionError("Explicit name priority overridden");

        ItemSorter.list = ItemSorter.fromSaveString(config);
        ItemInfo.itemOwners.put(others.get(0).getItem(), "another_mod");
        ItemInfo.itemOwners.put(others.get(1).getItem(), "zz_mod");
        ItemSorter.sort(actual);
        if (actual.get(0) != others.get(0) || actual.get(actual.size() - 1) != others.get(1)) {
            throw new AssertionError("Changed mod grouping");
        }
        System.out.println("SOURCE NEI " + ItemSorter.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("RESULT NEI ordering native-bug=" + reproduced + "/100 remaps=100/100 triples=" + triples
            + " contiguous=PASS unrelated=PASS config-reload=PASS idempotent=PASS name-priority=PASS mod-priority=PASS");
        System.out.println("RESULT sequence=" + group.stream().map(s -> s.getItem().getUnlocalizedName()).toList());
    }

    private static Comparator<ItemStack> upstream(int index) throws Exception {
        Method method = ItemSorter.class.getDeclaredMethod("lambda$initConfig$" + index, ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return (left, right) -> {
            try { return (Integer) method.invoke(null, left, right); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        };
    }
}
