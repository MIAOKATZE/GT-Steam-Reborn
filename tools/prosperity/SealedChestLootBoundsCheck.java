package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.WeightedRandomChestContent;

/** Actual reward normalization/capture with native ItemStack/NBT. No registries, GT boot or gameplay. */
public final class SealedChestLootBoundsCheck {

    private static int checks;
    private static Method add;

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        add = SealedChestLoot.class.getDeclaredMethod("add", List.class, ItemStack.class, int.class);
        add.setAccessible(true);
        String scenario = args.length == 0 ? "normal" : args[0];
        if ("huge".equals(scenario)) huge();
        else if ("capture".equals(scenario)) captureBounds();
        else if ("candidates".equals(scenario)) candidates();
        else normal();
        System.out.println("PASS: " + checks + " sealed loot " + scenario + " checks");
    }

    private static void huge() throws Exception {
        List<ItemStack> plan = new ArrayList<>();
        ItemStack existing = new ItemStack(Items.stick, 63);
        plan.add(existing);
        CountedTag tag = new CountedTag();
        ItemStack huge = new ItemStack(Items.apple, Integer.MAX_VALUE);
        huge.setTagCompound(tag);
        check(!add(plan, huge, 53), "oversize candidate is rejected, not truncated");
        check(tag.copies <= 53, "splitting cannot allocate the rejected remainder");
        check(plan.size() == 1 && plan.get(0) == existing && existing.stackSize == 63,
            "rejected candidate does not mutate prior rewards");
        check(huge.stackSize == Integer.MAX_VALUE, "source quantity remains untouched");
    }

    private static void captureBounds() throws Exception {
        IInventory capture = capture();
        ItemStack one = new ItemStack(Items.stick);
        int emissions = 0;
        boolean rejected = false;
        try {
            for (; emissions < 10000; emissions++) capture.setInventorySlotContents(0, one);
        } catch (RuntimeException overflow) {
            check(overflow.getClass().getSimpleName().equals("RewardCapacityExceeded"), "dedicated candidate overflow");
            rejected = true;
        }
        check(rejected && emissions == 53 * 64, "capture stops at the first over-capacity emission");
        check(stacks(capture).size() == 53, "capture retains at most 53 detached stacks");
        check(one.stackSize == 1, "capture does not debit the emitted source");
    }

    private static void normal() throws Exception {
        List<ItemStack> plan = new ArrayList<>();
        ItemStack a = new ItemStack(Items.stick, 63), b = new ItemStack(Items.stick, 2);
        check(add(plan, a, 2) && add(plan, b, 2), "partial stacks merge and split");
        check(plan.size() == 2 && plan.get(0).stackSize == 64 && plan.get(1).stackSize == 1, "exact merged counts");
        check(a.stackSize == 63 && b.stackSize == 2 && plan.get(0) != a, "all inputs remain detached");
        plan.clear();
        check(add(plan, new ItemStack(Items.ender_pearl, 33), 3), "item-specific stack limits");
        check(plan.size() == 3 && plan.get(0).stackSize == 16 && plan.get(2).stackSize == 1, "16-stack splitting");
        plan.clear();
        ItemStack tagged = new ItemStack(Items.stick, 10);
        NBTTagCompound n = new NBTTagCompound(); n.setString("variant", "a"); tagged.setTagCompound(n);
        check(add(plan, tagged, 2) && add(plan, new ItemStack(Items.stick, 10), 2), "tag-distinct rewards accepted");
        check(plan.size() == 2 && plan.get(0).getTagCompound() != n, "NBT is copied, not merged into untagged reward");
        plan.clear();
        check(add(plan, new ItemStack(Items.stick, 63), 1), "first near-full stack accepted");
        ItemStack original = plan.get(0);
        check(!add(plan, new ItemStack(Items.stick, 2), 1), "ordinary oversize candidate still skips");
        check(plan.get(0) == original && original.stackSize == 63, "failed merge is atomic");
        check(add(plan, new ItemStack(Items.apple), 2), "later valid candidate still succeeds");
        check(!add(plan, null, 2), "null add contract retained");
        try {
            add(new ArrayList<ItemStack>(), new ItemStack(new Item().setMaxStackSize(0)), 53);
            throw new AssertionError("invalid maximum must abort, not become a capacity skip");
        } catch (IllegalStateException expected) { checks++; }
        IInventory capture = capture();
        Random actual = new Random(917), expected = new Random(917);
        WeightedRandomChestContent entry = new WeightedRandomChestContent(new ItemStack(Items.stick), 65, 65, 7);
        WeightedRandomChestContent.generateChestContents(actual, new WeightedRandomChestContent[] { entry }, capture, 1);
        IInventory discard = inventory();
        WeightedRandomChestContent.generateChestContents(expected, new WeightedRandomChestContent[] { entry }, discard, 1);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack emitted : stacks(capture)) check(add(result, emitted, 53), "ordinary native emission accepted");
        check(result.size() == 2 && result.get(0).stackSize == 64 && result.get(1).stackSize == 1,
            "all 65 native emissions survive repeated slot collisions");
        check(actual.nextLong() == expected.nextLong(), "valid native generation consumes unchanged RNG sequence");
    }

    @SuppressWarnings("unchecked")
    private static void candidates() throws Exception {
        Method generate = SealedChestLoot.class.getDeclaredMethod("dungeonCandidate", IInventory.class,
            Random.class, List.class, WeightedRandomChestContent.class, int.class);
        generate.setAccessible(true);
        List<ItemStack> prior = new ArrayList<>();
        ItemStack accepted = new ItemStack(Items.apple, 5); prior.add(accepted);
        WeightedRandomChestContent oversized = new WeightedRandomChestContent(new ItemStack(Items.stick), 10000, 10000, 1);
        Object rejected = generate.invoke(null, inventory(), new Random(1), prior, oversized, 2);
        check(rejected == null, "capacity sentinel rejects only the current native dungeon candidate");
        check(prior.size() == 1 && prior.get(0) == accepted && accepted.stackSize == 5,
            "rejected capture cannot leak a partial candidate into prior dungeon loot");
        WeightedRandomChestContent ordinary = new WeightedRandomChestContent(new ItemStack(Items.stick), 1, 1, 1);
        List<ItemStack> next = (List<ItemStack>) generate.invoke(null, inventory(), new Random(1), prior, ordinary, 2);
        check(next.size() == 2 && next.get(0).stackSize == 5 && next.get(1).stackSize == 1,
            "a subsequent valid dungeon candidate still succeeds exactly");
        WeightedRandomChestContent tooManySlots = new WeightedRandomChestContent(new ItemStack(Items.stick), 129, 129, 1);
        check(generate.invoke(null, inventory(), new Random(1), prior, tooManySlots, 2) == null,
            "ordinary target overflow remains a candidate skip rather than a chest abort");
        WeightedRandomChestContent invalid = new WeightedRandomChestContent(new ItemStack(new Item().setMaxStackSize(0)), 1, 1, 1);
        try {
            generate.invoke(null, inventory(), new Random(1), prior, invalid, 2);
            throw new AssertionError("invalid stack maximum must still abort generation");
        } catch (InvocationTargetException expected) {
            check(expected.getCause() instanceof IllegalStateException, "only capacity overflow is caught");
        }
    }

    private static boolean add(List<ItemStack> plan, ItemStack stack, int limit) throws Exception {
        try { return (Boolean) add.invoke(null, plan, stack, limit); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw failure;
        }
    }

    private static IInventory inventory() {
        return (IInventory) Proxy.newProxyInstance(SealedChestLootBoundsCheck.class.getClassLoader(),
            new Class<?>[] { IInventory.class }, (proxy, method, args) -> {
                if (method.getName().equals("getSizeInventory")) return 54;
                if (method.getName().equals("getInventoryStackLimit")) return 64;
                if (method.getReturnType() == boolean.class) return true;
                if (method.getReturnType() == int.class) return 0;
                if (method.getReturnType() == String.class) return "fixture";
                return null;
            });
    }

    private static IInventory capture() throws Exception {
        Class<?> type = Class.forName(SealedChestLoot.class.getName() + "$Capture");
        Constructor<?> constructor = type.getDeclaredConstructor(IInventory.class);
        constructor.setAccessible(true);
        return (IInventory) constructor.newInstance(inventory());
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> stacks(IInventory capture) throws Exception {
        Field field = capture.getClass().getDeclaredField("stacks"); field.setAccessible(true);
        return (List<ItemStack>) field.get(capture);
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }

    private static final class CountedTag extends NBTTagCompound {
        int copies;
        @Override public NBTBase copy() {
            if (++copies > 60) throw new AssertionError("unbounded reward split exceeded 60 ItemStack/NBT copies");
            return super.copy();
        }
    }
}
